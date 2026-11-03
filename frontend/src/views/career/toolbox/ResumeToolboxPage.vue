<template>
  <CareerWorkbenchLayout subtitle="把简历分析和优化集中到一处：ATS 可读性、内容体检、JD 定向优化、润色与版本对比。">
    <template #actions>
      <Button variant="secondary" size="sm" :disabled="loading" @click="loadHistory">
        <Icon name="refresh-cw" :size="14" /><span>刷新记录</span>
      </Button>
    </template>

    <section class="tb-tools" aria-label="简历工具">
      <button
        v-for="tool in tools"
        :key="tool.key"
        type="button"
        class="tb-tool"
        @click="openTool(tool)"
      >
        <span class="tb-tool-icon"><Icon :name="tool.icon" :size="20" /></span>
        <span class="tb-tool-name">{{ tool.name }}</span>
        <span class="tb-tool-desc">{{ tool.description }}</span>
        <span class="tb-tool-tags">
          <span v-for="tag in tool.tags" :key="tag" class="tb-tag">{{ tag }}</span>
        </span>
      </button>
    </section>

    <ResumeSpecialtyCheck />

    <section class="tb-panel" aria-labelledby="tb-history">
      <div class="tb-panel-head">
        <h2 id="tb-history" class="kf-text-section-title">最近分析</h2>
        <span class="kf-text-caption">结果绑定当时的简历版本，规则升级不会回算历史</span>
      </div>

      <StateView v-if="loading" status="loading" type="list" :rows="3" />
      <ErrorState
        v-else-if="error"
        title="分析记录加载失败"
        :description="error"
        @retry="loadHistory"
      />
      <EmptyState
        v-else-if="!history.length"
        icon="component"
        title="还没有分析记录"
        description="选择上方任一工具运行一次，结果会自动保存在这里。"
      >
        <template #action>
          <Button @click="router.push('/career/tools/ats')">先做一次 ATS 检查</Button>
        </template>
      </EmptyState>

      <ul v-else class="tb-history">
        <li v-for="run in history" :key="run.id" class="tb-run">
          <span class="tb-run-type">{{ TOOL_LABELS[run.toolType] }}</span>
          <div class="tb-run-main">
            <span class="tb-run-title">{{ run.summary.resumeTitle || `简历 #${run.resumeId}` }} · v{{ run.resumeVersion }}</span>
            <span class="tb-run-meta">
              <template v-if="run.toolType === 'JD'">
                目标岗位：{{ run.summary.jobTitle || `#${run.jobId}` }} · 覆盖率 {{ run.summary.coverage ?? '—' }}%
              </template>
              <template v-else>
                通过 {{ run.summary.pass ?? 0 }} · 注意 {{ run.summary.warn ?? 0 }} · 需修改 {{ run.summary.fail ?? 0 }} · 无法确认 {{ run.summary.unknown ?? 0 }}
              </template>
              · {{ formatAt(run.createdAt) }} · 规则 {{ run.analysisVersion }}
            </span>
          </div>
          <span class="tb-run-badge" :class="badgeClass(run)">{{ badgeText(run) }}</span>
        </li>
      </ul>
    </section>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/**
 * 简历工具箱首页：工具入口 + 真实分析历史。
 *
 * 「格式检查」不单独造一套能力：版式/字符/日期的可判断项已包含在 ATS 检查中，
 * 不可判断的项会明确显示「无法确认」，不在这里重复包装成第二个评分。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ResumeSpecialtyCheck from '@/components/career/ResumeSpecialtyCheck.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import StateView from '@/components/ui/StateView.vue'
import { listToolRuns } from '@/api/career/toolbox'
import type { ToolRunSummary } from '@/api/career/types'
import { getApiError } from '@/utils/toast'
import { notify } from '@/utils/toast'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const history = ref<ToolRunSummary[]>([])

const TOOL_LABELS: Record<string, string> = { ATS: 'ATS', CONTENT: '体检', JD: 'JD 优化' }

interface ToolEntry {
  key: string
  name: string
  description: string
  icon: string
  tags: string[]
  route: string
}

const tools: ToolEntry[] = [
  {
    key: 'ats',
    name: 'ATS 可读性检查',
    description: '检查正文能否提取、联系方式、章节结构、日期格式与关键词覆盖。',
    icon: 'check-circle',
    tags: ['文本提取', '结构', '关键词'],
    route: '/career/tools/ats',
  },
  {
    key: 'content',
    name: '简历内容体检',
    description: '完整性、职责描述、重复内容、日期矛盾与技术名称一致性。',
    icon: 'list-checks',
    tags: ['完整性', '一致性', '重复'],
    route: '/career/tools/content',
  },
  {
    key: 'jd',
    name: 'JD 定向优化',
    description: '复用确定性匹配，列出必备/优先关键词的已覆盖与缺失证据。',
    icon: 'target',
    tags: ['必备技能', '缺失项', '真实性'],
    route: '/career/tools/jd',
  },
  {
    key: 'polish',
    name: 'AI 简历润色',
    description: '只改写表达、不新增经历；每条建议绑定原文片段，需你手动采纳。',
    icon: 'wand-2',
    tags: ['原文绑定', '人工采纳'],
    route: '/career/resumes',
  },
  {
    key: 'compare',
    name: '版本对比',
    description: '任意两个版本的新增、删除与修改，内容始终不可变。',
    icon: 'git-branch',
    tags: ['版本', '差异'],
    route: '/career/tools/compare',
  },
  {
    key: 'tailor',
    name: 'AI 简历定制',
    description: '按岗位生成定制建议：只调整顺序与表达，每条绑定原文，采纳后生成新版本。',
    icon: 'wand-2',
    tags: ['原文绑定', '人工审核', '新版本'],
    route: '/career/tools/tailor',
  },
  {
    key: 'format',
    name: '格式与文件检查',
    description: '异常字符、日期写法、版式风险；无法判断的项会明确标注。',
    icon: 'file-text',
    tags: ['字符', '日期', '版式'],
    route: '/career/tools/ats?focus=format',
  },
]

function openTool(tool: ToolEntry): void {
  if (tool.key === 'polish') {
    notify('请在简历编辑器中选择片段后使用「AI 润色」', 'info')
  }
  router.push(tool.route)
}

function badgeText(run: ToolRunSummary): string {
  if (run.toolType === 'JD') return `${run.summary.coverage ?? 0}%`
  const overall = run.summary.overall
  if (overall === 'PASS') return '通过'
  if (overall === 'WARN') return '需注意'
  if (overall === 'FAIL') return '需修改'
  return '—'
}

function badgeClass(run: ToolRunSummary): string {
  if (run.toolType === 'JD') return 'is-neutral'
  const overall = run.summary.overall
  if (overall === 'PASS') return 'is-pass'
  if (overall === 'FAIL') return 'is-fail'
  return 'is-warn'
}

function formatAt(value: string): string {
  if (!value) return ''
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

async function loadHistory(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    history.value = await listToolRuns()
  } catch (failure) {
    error.value = getApiError(failure, '分析记录加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadHistory)
</script>

<style scoped>
.tb-tools {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--kf-space-3);
}
.tb-tool {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  text-align: left;
  cursor: pointer;
}
.tb-tool:hover {
  background: var(--kf-surface-2);
}
.tb-tool:focus-visible {
  outline: 2px solid var(--kf-primary);
  outline-offset: 2px;
}
.tb-tool-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
}
.tb-tool-name {
  font-size: var(--kf-fs-body);
  font-weight: 500;
  color: var(--kf-foreground);
}
.tb-tool-desc {
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
.tb-tool-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
}
.tb-tag {
  font-size: var(--kf-fs-caption);
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
}
.tb-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
.tb-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.tb-history {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}
.tb-run {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  padding: var(--kf-space-3) 0;
  border-bottom: 1px solid var(--kf-border);
}
.tb-run:last-child {
  border-bottom: 0;
}
.tb-run-type {
  flex: none;
  width: 64px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tb-run-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.tb-run-title {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tb-run-meta {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tb-run-badge {
  flex: none;
  font-size: var(--kf-fs-caption);
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
}
.tb-run-badge.is-pass {
  color: var(--kf-success, #2f855a);
}
.tb-run-badge.is-fail {
  color: var(--kf-danger, #c53030);
}
.tb-run-badge.is-warn {
  color: var(--kf-warning, #b7791f);
}
@media (max-width: 640px) {
  .tb-tool {
    padding: var(--kf-space-3);
  }
  .tb-run {
    flex-wrap: wrap;
  }
}
</style>
