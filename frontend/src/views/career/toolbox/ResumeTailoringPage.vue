<template>
  <CareerWorkbenchLayout subtitle="按岗位定制简历：只做顺序、术语与表达优化，每条建议绑定原文；采纳后才生成新版本。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/tools')">
        <Icon name="arrow-left" :size="14" /><span>返回工具箱</span>
      </Button>
    </template>

    <section class="tl-panel" aria-labelledby="tl-form">
      <div class="tl-head">
        <h2 id="tl-form" class="kf-text-section-title">生成定制建议</h2>
        <span class="kf-text-caption">原简历版本不会被覆盖</span>
      </div>
      <div class="tl-form">
        <label class="tl-field">
          <span>主简历（已确认）</span>
          <Select v-model="resumeId" :options="resumeOptions" placeholder="选择简历" aria-label="主简历" />
        </label>
        <label class="tl-field">
          <span>目标岗位（已确认）</span>
          <Select v-model="jobId" :options="jobOptions" placeholder="选择岗位" aria-label="目标岗位" />
        </label>
        <label class="tl-field">
          <span>定制深度</span>
          <Select v-model="mode" :options="modeOptions" aria-label="定制深度" />
        </label>
        <div class="tl-switch">
          <Checkbox v-model="allowModelProcessing" label="允许模型处理这份简历" />
          <p class="kf-text-caption">不勾选时只用确定性规则；勾选且已配置模型才会调用 AI。</p>
        </div>
        <Button :disabled="!resumeId || !jobId" :loading="generating" @click="generate">生成建议</Button>
      </div>
      <p v-if="notice" class="tl-notice" role="status">{{ notice }}</p>
    </section>

    <StateView v-if="generating" status="loading" type="card" :cols="2" :rows="3" />
    <ErrorState v-else-if="error" title="生成没有完成" :description="error" @retry="generate" />

    <template v-else-if="task">
      <section class="tl-panel" aria-labelledby="tl-result">
        <div class="tl-head">
          <h2 id="tl-result" class="kf-text-section-title">修改建议（{{ task.changes.length }}）</h2>
          <span class="kf-text-caption">
            {{ task.modelSource === 'AI' ? 'AI + 事实校验' : '确定性规则' }} · {{ MODE_LABELS[task.mode] }}
            · 状态 {{ task.status }}
          </span>
        </div>

        <EmptyState
          v-if="!task.changes.length"
          icon="check-circle"
          title="没有需要调整的内容"
          description="该岗位要求与简历已有内容没有可安全优化的差异；系统不会为了「看起来更匹配」而编造内容。"
        />

        <ul v-else class="tl-changes">
          <li v-for="change in task.changes" :key="change.id" class="tl-change">
            <div class="tl-change-head">
              <Checkbox
                :model-value="accepted.includes(change.id)"
                :aria-label="`采纳建议 ${change.id}`"
                @update:model-value="(checked: boolean) => toggle(change.id, checked)"
              />
              <span class="tl-type">{{ TYPE_LABELS[change.type] }}</span>
              <span class="tl-confidence">{{ change.quoteSource === 'JD' ? '岗位原文' : '简历原文' }}</span>
              <span class="tl-requirement">{{ change.requirement }}</span>
              <span class="tl-confidence">{{ change.confidence }}</span>
            </div>
            <div class="tl-diff">
              <p class="tl-quote">{{ change.originalQuote }}</p>
              <p v-if="change.proposedText" class="tl-proposed">{{ change.proposedText }}</p>
            </div>
            <p class="tl-reason">{{ change.reason }}</p>
          </li>
        </ul>

        <div class="tl-actions">
          <Button variant="secondary" :disabled="!task.changes.length" :loading="reviewing" @click="saveReview">
            保存采纳（{{ accepted.length }}）
          </Button>
          <Button :disabled="!accepted.length || task.status === 'APPLIED'" :loading="creating" @click="createVersion">
            生成新版本
          </Button>
          <Button
            v-if="task.resultVersion"
            variant="secondary"
            @click="router.push(`/career/templates/editor/${task.resumeId}?templateId=ats-classic`)"
          >去导出 PDF</Button>
        </div>
        <p class="tl-disclaimer" role="note">{{ task.disclaimer }}</p>
      </section>
    </template>

    <EmptyState
      v-else
      icon="wand-2"
      title="选择简历与岗位后生成建议"
      description="建议分为「前置相关经历」与「缺失技能提问」；不会新增公司、项目、指标或未出现过的技术。"
    />
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/**
 * AI 简历定制工作室。
 *
 * 与现有工具箱共用简历/岗位版本：生成不写库到简历，只有「生成新版本」才创建新的不可变版本。
 * 未授权模型时使用确定性规则，界面上明确说明来源。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { listResumes } from '@/api/career/resume'
import { listJobs } from '@/api/career/job'
import { createTailoredVersion, generateTailoring, reviewTailoringTask } from '@/api/career/tailoring'
import type { CareerRecord, TailoringMode, TailoringTask } from '@/api/career/types'
import { getApiError, notify } from '@/utils/toast'

const router = useRouter()
const route = useRoute()

const resumes = ref<CareerRecord[]>([])
const jobs = ref<CareerRecord[]>([])
const resumeId = ref<number | null>(null)
const jobId = ref<number | null>(null)
const mode = ref<TailoringMode>('LIGHT')
const allowModelProcessing = ref(false)
const generating = ref(false)
const reviewing = ref(false)
const creating = ref(false)
const error = ref('')
const notice = ref('')
const task = ref<TailoringTask | null>(null)
const accepted = ref<string[]>([])

const MODE_LABELS: Record<TailoringMode, string> = {
  LIGHT: '轻度优化',
  KEYWORD: '关键词优化',
  DEEP: '深度定制',
}
const TYPE_LABELS: Record<string, string> = {
  MOVE_TOP: '前置',
  REORDER: '调整顺序',
  RENAME: '术语对齐',
  REWRITE: '表达优化',
  QUESTION: '待确认',
}

const modeOptions = (Object.keys(MODE_LABELS) as TailoringMode[]).map((value) => ({
  label: MODE_LABELS[value], value,
}))
const resumeOptions = computed(() =>
  resumes.value.filter((item) => item.confirmed)
    .map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)
const jobOptions = computed(() =>
  jobs.value.filter((item) => item.confirmed)
    .map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)

function toggle(id: string, checked: boolean): void {
  const set = new Set(accepted.value)
  if (checked) set.add(id)
  else set.delete(id)
  accepted.value = [...set]
}

async function generate(): Promise<void> {
  if (!resumeId.value || !jobId.value) return
  const resume = resumes.value.find((item) => item.id === resumeId.value)
  const job = jobs.value.find((item) => item.id === jobId.value)
  if (!resume || !job) return
  generating.value = true
  error.value = ''
  notice.value = ''
  task.value = null
  accepted.value = []
  try {
    const result = await generateTailoring({
      resumeId: resume.id,
      resumeVersion: resume.currentVersion,
      jobId: job.id,
      jobVersion: job.currentVersion,
      mode: mode.value,
      allowModelProcessing: allowModelProcessing.value,
    })
    task.value = result
    notice.value = result.notice || ''
    accepted.value = result.changes.filter((change) => change.confidence === 'HIGH').map((change) => change.id)
  } catch (failure) {
    error.value = getApiError(failure, '定制建议没有生成')
  } finally {
    generating.value = false
  }
}

async function saveReview(): Promise<void> {
  if (!task.value) return
  reviewing.value = true
  try {
    task.value = await reviewTailoringTask(task.value.id, accepted.value)
    notify('已保存采纳结果', 'success')
  } catch (failure) {
    notify(getApiError(failure, '保存失败'), 'error')
  } finally {
    reviewing.value = false
  }
}

async function createVersion(): Promise<void> {
  if (!task.value) return
  creating.value = true
  try {
    const result = await createTailoredVersion(task.value.id)
    task.value = result
    notify(`已生成新版本 v${result.resultVersion}`, 'success')
  } catch (failure) {
    notify(getApiError(failure, '生成新版本失败'), 'error')
  } finally {
    creating.value = false
  }
}

onMounted(async () => {
  try {
    const [resumeList, jobList] = await Promise.all([listResumes(), listJobs()])
    resumes.value = resumeList
    jobs.value = jobList
    const queryResume = Number(route.query.resumeId)
    const queryJob = Number(route.query.jobId)
    resumeId.value = resumeList.find((item) => item.id === queryResume)?.id
      ?? resumeList.find((item) => item.confirmed)?.id ?? null
    jobId.value = jobList.find((item) => item.id === queryJob)?.id
      ?? jobList.find((item) => item.confirmed)?.id ?? null
  } catch (failure) {
    error.value = getApiError(failure, '简历与岗位列表加载失败')
  }
})
</script>

<style scoped>
.tl-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  min-width: 0;
}
.tl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.tl-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--kf-space-3);
  align-items: end;
}
.tl-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  min-width: 0;
}
.tl-switch {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.tl-notice {
  margin: 0;
  padding: var(--kf-space-2) var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tl-changes {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.tl-change {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
}
.tl-change-head {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.tl-type {
  font-size: var(--kf-fs-caption);
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
}
.tl-requirement {
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
}
.tl-confidence {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tl-diff {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tl-quote {
  margin: 0;
  padding: var(--kf-space-2);
  border-left: 3px solid var(--kf-border);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  white-space: pre-wrap;
}
.tl-proposed {
  margin: 0;
  padding: var(--kf-space-2);
  border-left: 3px solid var(--kf-primary);
  background: var(--kf-surface-1);
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
  white-space: pre-wrap;
}
.tl-reason {
  margin: 0;
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
.tl-actions {
  display: flex;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.tl-disclaimer {
  margin: 0;
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
@media (max-width: 1024px) {
  .tl-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .tl-form {
    grid-template-columns: minmax(0, 1fr);
  }
  .tl-panel {
    padding: var(--kf-space-3);
  }
}
</style>
