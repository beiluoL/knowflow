<template>
  <CareerWorkbenchLayout subtitle="今天要做的事、最近在改的简历与岗位、待处理的匹配与面试方案，集中在这里。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/resumes/new')">
        <Icon name="plus" :size="14" /><span>新建简历</span>
      </Button>
      <Button size="sm" @click="router.push('/career/jobs?import=1')">
        <Icon name="briefcase" :size="14" /><span>导入 JD</span>
      </Button>
    </template>

    <StateView v-if="loading" status="loading" type="card" :cols="4" :rows="2" />
    <StateView
      v-else-if="error"
      status="error"
      :error-description="error"
      retry-text="重新加载工作台"
      @retry="load"
    />

    <template v-else>
      <section class="wb-panel" aria-labelledby="wb-tasks">
        <div class="wb-panel-head">
          <h2 id="wb-tasks" class="kf-text-section-title">今天要做</h2>
          <span class="kf-text-caption">根据真实数据状态生成，不是推荐算法</span>
        </div>
        <ul class="wb-tasks">
          <li v-for="task in tasks" :key="task.id" class="wb-task">
            <span class="wb-task-index" aria-hidden="true">{{ task.id === firstTaskId ? '1' : '·' }}</span>
            <div class="wb-task-main">
              <p class="wb-task-title">{{ task.title }}</p>
              <p class="wb-task-desc">{{ task.description }}</p>
            </div>
            <Button size="sm" variant="secondary" @click="router.push(task.route)">去处理</Button>
          </li>
        </ul>
        <p v-if="!tasks.length" class="kf-text-caption">当前没有待处理事项，可以从右侧快捷入口继续推进。</p>
      </section>

      <div class="wb-grid">
        <section class="wb-panel" aria-labelledby="wb-resumes">
          <div class="wb-panel-head">
            <h2 id="wb-resumes" class="kf-text-section-title">最近简历</h2>
            <Button variant="text" size="sm" @click="router.push('/career/resumes')">全部简历</Button>
          </div>
          <ul v-if="resumes.length" class="wb-list">
            <li v-for="item in resumes" :key="item.id" class="wb-row">
              <button type="button" class="wb-row-btn" @click="router.push(`/career/resumes/${item.id}`)">
                <span class="wb-row-title">{{ item.title }}</span>
                <span class="wb-row-meta">
                  v{{ item.currentVersion }} · {{ item.confirmed ? '已确认' : '待确认' }}
                  <template v-if="item.isDefault"> · 默认</template>
                </span>
              </button>
              <Icon name="chevron-right" :size="14" class="wb-row-arrow" />
            </li>
          </ul>
          <EmptyState
            v-else
            icon="file-text"
            title="还没有简历"
            description="上传 PDF/DOCX 或粘贴原文，解析确认后才会参与匹配。"
          >
            <template #action>
              <Button size="sm" @click="router.push('/career/resumes/new')">创建第一份简历</Button>
            </template>
          </EmptyState>
        </section>

        <section class="wb-panel" aria-labelledby="wb-jobs">
          <div class="wb-panel-head">
            <h2 id="wb-jobs" class="kf-text-section-title">最近岗位</h2>
            <Button variant="text" size="sm" @click="router.push('/career/jobs')">岗位中心</Button>
          </div>
          <ul v-if="jobs.length" class="wb-list">
            <li v-for="item in jobs" :key="item.id" class="wb-row">
              <button type="button" class="wb-row-btn" @click="router.push(`/career/jobs/${item.id}`)">
                <span class="wb-row-title">{{ item.title }}</span>
                <span class="wb-row-meta">
                  v{{ item.currentVersion }} · {{ item.confirmed ? '已确认' : '待确认' }}
                  <template v-if="item.jobStatus"> · {{ JOB_STATUS_LABELS[item.jobStatus.status] }}</template>
                </span>
              </button>
              <Icon name="chevron-right" :size="14" class="wb-row-arrow" />
            </li>
          </ul>
          <EmptyState
            v-else
            icon="briefcase"
            title="还没有目标岗位"
            description="导入真实 JD 并确认岗位要求，才能进行智能匹配。"
          >
            <template #action>
              <Button size="sm" @click="router.push('/career/jobs?import=1')">导入第一个 JD</Button>
            </template>
          </EmptyState>
        </section>
      </div>

      <div class="wb-grid">
        <section class="wb-panel" aria-labelledby="wb-matches">
          <div class="wb-panel-head">
            <h2 id="wb-matches" class="kf-text-section-title">待处理匹配</h2>
            <Button variant="text" size="sm" @click="router.push('/career/matches?create=1')">新建匹配</Button>
          </div>
          <ul v-if="matches.length" class="wb-list">
            <li v-for="item in matches" :key="item.id" class="wb-row">
              <button type="button" class="wb-row-btn" @click="router.push(`/career/matches/${item.id}`)">
                <span class="wb-row-title">{{ item.jobTitle }}</span>
                <span class="wb-row-meta">
                  {{ item.resumeTitle }} v{{ item.resumeVersion }} · 技能覆盖率 {{ item.overallScore ?? '—' }}%
                </span>
              </button>
              <Icon name="chevron-right" :size="14" class="wb-row-arrow" />
            </li>
          </ul>
          <EmptyState
            v-else
            icon="target"
            title="还没有匹配结果"
            description="选择已确认的简历与岗位版本，生成可回溯的证据快照。"
          />
        </section>

        <section class="wb-panel" aria-labelledby="wb-plans">
          <div class="wb-panel-head">
            <h2 id="wb-plans" class="kf-text-section-title">待审核面试方案</h2>
            <Button variant="text" size="sm" @click="router.push('/career/interviews')">岗位面试</Button>
          </div>
          <ul v-if="plans.length" class="wb-list">
            <li v-for="item in plans" :key="item.id" class="wb-row">
              <button type="button" class="wb-row-btn" @click="router.push(`/career/interviews/${item.id}`)">
                <span class="wb-row-title">{{ item.jobTitle }}</span>
                <span class="wb-row-meta">
                  {{ item.questionCount }} 题 · {{ item.durationMinutes }} 分钟 · {{ item.difficulty }}
                </span>
              </button>
              <Icon name="chevron-right" :size="14" class="wb-row-arrow" />
            </li>
          </ul>
          <EmptyState
            v-else
            icon="mic"
            title="还没有面试方案"
            description="从匹配结果创建岗位定制面试，逐题审核后开始。"
          />
        </section>
      </div>

      <section class="wb-panel" aria-labelledby="wb-quick">
        <div class="wb-panel-head">
          <h2 id="wb-quick" class="kf-text-section-title">快捷操作</h2>
          <span class="kf-text-caption">只跳转到已启用的应用</span>
        </div>
        <div class="wb-quick">
          <button
            v-for="action in quickActions"
            :key="action.key"
            type="button"
            class="wb-quick-item"
            @click="router.push(action.route)"
          >
            <span class="wb-quick-icon"><Icon :name="action.icon" :size="18" /></span>
            <span class="wb-quick-label">{{ action.label }}</span>
            <span class="wb-quick-desc">{{ action.description }}</span>
          </button>
        </div>
      </section>
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/**
 * 求职工作台（日常操作中心）。
 *
 * 与「求职总览」的分工：本页只回答「今天做什么」，不展示趋势与分布；
 * 数据来自现有 dashboard 聚合与真实列表接口，不新增后端统计逻辑。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StateView from '@/components/ui/StateView.vue'
import { getCareerDashboard } from '@/api/career/dashboard'
import { listResumes } from '@/api/career/resume'
import { listJobs } from '@/api/career/job'
import { listMatches } from '@/api/career/match'
import { listPlans } from '@/api/career/interview'
import type { CareerDashboard, CareerDashboardTask, CareerRecord, MatchSummary, PlanSummary } from '@/api/career/types'
import { getApiError } from '@/utils/toast'
import { useAppStore } from '@/stores/apps'
import { getAppByKey } from '@/apps/registry'
import { JOB_STATUS_LABELS } from '@/components/career/career-labels'

const router = useRouter()
const apps = useAppStore()

const loading = ref(true)
const error = ref('')
const dashboard = ref<CareerDashboard | null>(null)
const resumes = ref<CareerRecord[]>([])
const jobs = ref<CareerRecord[]>([])
const matches = ref<MatchSummary[]>([])
const plans = ref<PlanSummary[]>([])

const tasks = computed<CareerDashboardTask[]>(() => dashboard.value?.tasks ?? [])
const firstTaskId = computed(() => tasks.value[0]?.id ?? '')

interface QuickAction {
  key: string
  label: string
  description: string
  route: string
  icon: string
}

/** 只展示已启用应用对应的快捷入口，避免把用户带到已停用的页面。 */
const quickActions = computed<QuickAction[]>(() => {
  const candidates: QuickAction[] = [
    { key: 'career-resume', label: '写简历', description: '新建或继续编辑', route: '/career/resumes', icon: 'file-text' },
    { key: 'career-jobs', label: '导入岗位', description: '粘贴 JD 或链接', route: '/career/jobs?import=1', icon: 'briefcase' },
    { key: 'career-matching', label: '跑一次匹配', description: '看技能差距', route: '/career/matches?create=1', icon: 'target' },
    { key: 'career-resume-toolbox', label: '简历体检', description: 'ATS 与内容检查', route: '/career/tools', icon: 'component' },
    { key: 'career-resume-templates', label: '换模板', description: '排版与导出', route: '/career/templates', icon: 'layout' },
    { key: 'career-overview', label: '看数据', description: '趋势与分布', route: '/career/overview', icon: 'layout-dashboard' },
  ]
  return candidates.filter((item) => {
    const app = getAppByKey(item.key)
    const state = apps.stateOf(item.key)
    return !!app && state.installed && state.enabled
  })
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [dashboardData, resumeList, jobList, matchList, planList] = await Promise.all([
      getCareerDashboard(),
      listResumes(),
      listJobs(),
      listMatches(),
      listPlans(),
    ])
    dashboard.value = dashboardData
    resumes.value = resumeList.slice(0, 4)
    jobs.value = jobList.slice(0, 4)
    matches.value = matchList.slice(0, 3)
    plans.value = planList.slice(0, 3)
  } catch (failure) {
    error.value = getApiError(failure, '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  apps.hydrateCache()
  void load()
})
</script>

<style scoped>
.wb-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  min-width: 0;
}
.wb-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.wb-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-4);
}
.wb-tasks {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.wb-task {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
}
.wb-task-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
  font-size: var(--kf-fs-caption);
  flex: none;
}
.wb-task-main {
  flex: 1;
  min-width: 0;
}
.wb-task-title {
  margin: 0;
  font-size: var(--kf-fs-body);
  line-height: var(--kf-lh-body);
  color: var(--kf-foreground);
  font-weight: 500;
}
.wb-task-desc {
  margin: 2px 0 0;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  color: var(--kf-muted-foreground);
}
.wb-list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}
.wb-row {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  border-bottom: 1px solid var(--kf-border);
}
.wb-row:last-child {
  border-bottom: 0;
}
.wb-row-btn {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: var(--kf-space-3) var(--kf-space-1);
  background: transparent;
  border: 0;
  text-align: left;
  cursor: pointer;
  border-radius: var(--kf-radius-sm);
}
.wb-row-btn:hover {
  background: var(--kf-surface-2);
}
.wb-row-btn:focus-visible {
  outline: 2px solid var(--kf-primary);
  outline-offset: 2px;
}
.wb-row-title {
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  color: var(--kf-foreground);
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wb-row-meta {
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
.wb-row-arrow {
  color: var(--kf-muted-foreground);
  flex: none;
}
.wb-quick {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--kf-space-3);
}
.wb-quick-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-1);
  cursor: pointer;
  text-align: left;
}
.wb-quick-item:hover {
  border-color: var(--kf-border-strong, var(--kf-border));
  background: var(--kf-surface-2);
}
.wb-quick-item:focus-visible {
  outline: 2px solid var(--kf-primary);
  outline-offset: 2px;
}
.wb-quick-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
}
.wb-quick-label {
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
}
.wb-quick-desc {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
@media (max-width: 1024px) {
  .wb-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 640px) {
  .wb-panel {
    padding: var(--kf-space-3);
  }
  .wb-task {
    flex-wrap: wrap;
  }
}
</style>
