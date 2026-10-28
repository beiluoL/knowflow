<template>
  <div class="learning-center animate-fade-in ui-v2-center">
    <!-- ══════════ 页面头：一个 h1 + 一个 Primary CTA（§四：每个区域最多一个主按钮）══════════ -->
    <header class="lc-head">
      <div>
        <!-- 3.2 §28：与 Sidebar 固定入口同名，避免「菜单说学习首页、页面说学习中心」 -->
        <h1 class="lc-title">学习首页</h1>
        <p class="lc-subtitle">今天继续推进你的学习</p>
      </div>
      <Button variant="secondary" :icon-name="mainCta.icon" @click="startMainAction">{{ mainCta.text }}</Button>
    </header>


    <!-- 整体错误：明确错误 + 重试（§十七） -->
    <StateView v-if="statsStatus === 'error' || statsStatus === 'retrying'" :status="statsStatus" :error-title="statsError?.title" :error-scope="statsError?.scope" :error-hint="statsError?.hint" :show-retry="statsError?.retryable ?? true" @retry="loadStats(true)" />

    <!-- ══════════ 1. Goal：当前目标（让下面每一步都有「为什么」）══════════ -->
    <section class="lc-section lc-goal-section">
      <div class="lc-section-head">
        <h2 class="lc-section-title">当前目标</h2>
        <router-link to="/learning/goals" class="lc-link">
          <span>{{ activeGoal ? '管理目标' : '设定目标' }}</span>
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>
      <StateView v-if="learning.loading || learning.goalError" :status="learning.loading ? (learning.loaded ? 'retrying' : 'loading') : 'error'" error-title="学习目标加载失败" error-scope="仅当前目标不可用，其他已加载区块仍可使用。" error-hint="请检查服务与网络后重试。" :rows="1" @retry="reloadLearning" />
      <div v-else-if="activeGoal" class="lc-goal">
        <span class="lc-goal-icon"><Icon name="target" :size="16" /></span>
        <div class="lc-goal-main">
          <div class="lc-goal-top">
            <router-link
              :to="activeGoal.pathId ? `/learning/path/${activeGoal.pathId}` : '/learning/goals'"
              class="lc-goal-title"
            >{{ activeGoal.title }}</router-link>
            <span v-if="activeGoal.pathId && goalPathPercent != null" class="lc-goal-pct tabular-nums">{{ goalPathPercent }}%</span>
          </div>
          <div class="lc-goal-sub">
            <template v-if="activeGoal.pathId">
              <Icon name="route" :size="13" />
              <span>{{ activeGoal.pathTitle || '学习路线' }}</span>
              <span v-if="knowledgeContext.learningNodeTitle">· 当前：{{ knowledgeContext.learningNodeTitle }}</span>
              <span v-if="knowledgeContext.knowledgeTitle">· 知识点：{{ knowledgeContext.knowledgeTitle }}</span>
            </template>
            <template v-else>
              <span>还没有关联学习路线 ——</span>
              <router-link to="/learning/goals" class="lc-link-inline">生成 / 选择一条路线</router-link>
            </template>
          </div>
        </div>
      </div>
      <EmptyState
        v-else
        icon="target"
        title="还没有进行中的学习目标"
        variant="info"
      >
        <p>设定一个学习目标，系统才知道你「为什么学、下一步该做什么」。</p>
        <template #action>
          <Button variant="primary" icon-name="flag" @click="goTo('/learning/goals')">设定学习目标</Button>
        </template>
      </EmptyState>
    </section>

    <section class="lc-section lc-next-action" aria-label="下一步学习">
      <NextActionCard :action="nextAction" dismissible />
    </section>

    <section v-if="studyPackEnabled" class="lc-section" aria-label="课程资料学习">
      <div class="lc-section-head">
        <h2 class="lc-section-title">从课程资料开始</h2>
        <router-link to="/learning/study-packs" class="lc-link">上传资料，生成学习包</router-link>
      </div>
    </section>

    <AdaptiveLearningPanel />

    <!-- ══════════ 2. Today：今日任务 + 待复习（真实数据，可操作）══════════ -->
    <section ref="todaySection" class="lc-section">
      <div class="lc-section-head">
        <h2 class="lc-section-title">今日</h2>
        <div class="lc-head-actions">
          <span v-if="tasks.length > 0" class="lc-count tabular-nums">已完成 {{ doneCount }}/{{ tasks.length }}</span>
          <Button variant="ghost" size="sm" icon-name="plus" @click="showAddTask = !showAddTask">
            添加计划
          </Button>
        </div>
      </div>

      <!-- 今日进度（真实任务数据；无每日目标设置，不展示伪造百分比目标 §29） -->
      <div v-if="tasks.length > 0" class="lc-progress" role="progressbar"
           :aria-valuenow="donePercent" aria-valuemin="0" aria-valuemax="100" aria-label="今日任务完成进度">
        <div class="lc-progress-fill" :style="{ width: donePercent + '%' }"></div>
      </div>

      <!-- 添加计划表单 -->
      <div v-if="showAddTask" class="lc-add-task">
        <input v-model="newTask.title" type="text" placeholder="计划标题，如：阅读《JavaScript 高级程序设计》第3章"
               class="lc-input kb-input kb-form-control" @keydown.enter="handleCreateTask" />
        <textarea v-model="newTask.description" placeholder="描述（可选）" rows="2" class="lc-input kb-textarea kb-form-control"></textarea>
        <div class="lc-add-task-row">
          <select v-model="newTask.type" class="lc-select kb-select kb-form-control">
            <option value="study">学习</option>
            <option value="reading">阅读</option>
            <option value="practice">练习</option>
            <option value="review">复习</option>
            <option value="other">其他</option>
          </select>
          <div class="lc-deadline">
            <DateTimePicker
              v-model="newTask.deadline"
              placeholder="截止时间（可选）"
              clearable
            />
          </div>
          <div class="flex-1"></div>
          <Button variant="ghost" size="sm" @click="resetAddTask">取消</Button>
          <Button size="sm" :loading="creatingTask" :disabled="!newTask.title.trim()" @click="handleCreateTask">
            确认添加
          </Button>
        </div>
      </div>

      <!-- 任务列表 -->
      <StateView
        :status="taskStatus"
        type="list"
        :rows="3"
        empty-icon="list-checks"
        empty-title="今日还没有计划"
        error-title="今日计划加载失败"
        error-scope="只是今日计划这一块没加载出来，页面其他内容仍可正常使用。"
        error-hint="稍后重试；若持续出现请联系管理员。"
        @retry="reloadLearning"
      >
        <template #empty-description>
          <p>添加今天要完成的学习任务，完成后在这里勾选。</p>
        </template>
        <template #empty-action>
          <Button variant="primary" icon-name="plus" @click="showAddTask = true">添加计划</Button>
        </template>

        <div class="lc-tasks">
          <div v-for="task in tasks" :key="(task.source || 'LEARNING')+task.id" class="lc-task" tabindex="-1">
            <button
              type="button"
              class="lc-task-check"
              :disabled="!!task.completionRequirement || updatingTasks.has((task.source || 'LEARNING') + task.id)"
              :aria-label="`${task.status === 1 ? '标记为未完成' : '标记为已完成'}：${task.title}`"
              :class="{ done: task.status === 1 }"
              :aria-pressed="task.status === 1"
              :title="task.status === 1 ? '标记为未完成' : '标记为已完成'"
              @click="toggleTaskStatus(task)"
            >
              <Icon v-if="task.status === 1" name="check" :size="12" />
            </button>
            <div class="lc-task-main">
              <p class="lc-task-title" :class="{ done: task.status === 1 }">{{ task.title }}</p>
              <TaskCompletionAction :requirement="task.completionRequirement" :title="task.title" />
              <details v-if="task.description" class="lc-task-description">
                <summary>查看计划内容</summary>
                <p class="lc-task-desc">{{ task.description }}</p>
              </details>
            </div>
            <button type="button" class="lc-task-del" :aria-label="`删除计划：${task.title}`" title="删除" @click="handleDeleteTask(task)">
              <Icon name="trash-2" :size="14" />
            </button>
          </div>
        </div>
      </StateView>

      <!-- 待复习知识点（具体，可点击；首页只显示数量） -->
      <div v-if="reviewRequired.length > 0" class="lc-chips">
        <span class="lc-chips-label">待复习</span>
        <button v-for="k in reviewRequired.slice(0, 8)" :key="k.knowledgeId" type="button"
                class="lc-chip" :title="`查看「${k.name || '未命名知识点'}」掌握度`"
                @click="goTo(`/learning/knowledge-mastery/${k.knowledgeId}`)">
          {{ k.name || '未命名知识点' }}
        </button>
        <span v-if="reviewRequired.length > 8" class="lc-chip-more">+{{ reviewRequired.length - 8 }}</span>
        <Button variant="ghost" size="sm" icon-name="rotate-ccw" @click="goTo('/learning/review')">开始复习</Button>
      </div>
    </section>

    <!-- ══════════ 4. Path progress：当前路径 / 进行中课程 ══════════ -->
    <section class="lc-section">
      <div class="lc-section-head">
        <h2 class="lc-section-title">路径进度</h2>
        <router-link to="/learning/paths" class="lc-link">
          <span>查看全部</span>
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>

      <StateView
        :status="pathStatus"
        type="card"
        :rows="2"
        empty-icon="route"
        empty-title="还没有进行中的学习路径"
        error-title="学习路径加载失败"
        error-scope="只是路径进度这一块没加载出来，页面其他内容仍可正常使用。"
        error-hint="稍后重试；若持续出现请联系管理员。"
        @retry="reloadLearning"
      >
        <template #empty-description>
          <p>选一条路线并报名，这里会显示你的进度。</p>
        </template>
        <template #empty-action>
          <Button variant="primary" icon-name="route" @click="goTo('/learning/paths')">浏览学习路径</Button>
        </template>

        <div class="lc-paths">
          <button type="button" v-for="path in ongoingPaths" :key="path.id" class="lc-path" @click="goToPath(path.id)">
            <div class="lc-path-main">
              <span v-if="path.categoryName || path.level" class="lc-path-badge">{{ path.categoryName || path.level }}</span>
              <h3 class="lc-path-title">{{ path.title }}</h3>
              <p class="lc-path-desc">{{ path.description || '系统化学习路径，循序渐进掌握技能' }}</p>
              <div class="lc-path-meta">
                <span class="tabular-nums">{{ pathProgress(path) == null ? '进度未提供' : `${pathProgress(path)}%` }}</span>
                <span v-if="path.completedChapters != null && path.chapterCount != null" class="tabular-nums">{{ path.completedChapters }}/{{ path.chapterCount }} 章</span>
                <span v-else-if="path.chapterCount != null" class="tabular-nums">共 {{ path.chapterCount }} 章</span>
                <span v-if="path.totalDuration" class="tabular-nums">预计 {{ path.totalDuration }} 分钟</span>
                <span v-else-if="path.estimatedHours" class="tabular-nums">预计 {{ path.estimatedHours }} 小时</span>
              </div>
              <div v-if="pathProgress(path) != null" class="lc-path-bar">
                <div class="lc-path-bar-fill" :style="{ width: `${pathProgress(path)}%` }"></div>
              </div>
            </div>
          </button>
        </div>
      </StateView>
    </section>

    <InterviewSeedCard />

    <!-- ══════════ 5. Weak points：薄弱知识 + 待补强场景 ══════════ -->
    <section class="lc-section">
      <div class="lc-section-head">
        <h2 class="lc-section-title">薄弱知识</h2>
        <router-link to="/learning/mastery" class="lc-link">
          <span>掌握度详情</span>
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>

      <StateView
        :status="weakStatus"
        type="list"
        :rows="3"
        empty-icon="trending-down"
        empty-title="暂无薄弱知识"
        empty-variant="success"
        error-title="薄弱知识加载失败"
        error-scope="只是薄弱知识这一块没加载出来，页面其他内容仍可正常使用。"
        error-hint="稍后重试；若持续出现请联系管理员。"
        @retry="reloadLearning"
      >
        <template #empty-description>
          <p>当前还没有足够的学习行为数据。</p>
          <p>完成几次练习后，系统会开始分析你的薄弱点。</p>
        </template>
        <template #empty-action>
          <Button variant="primary" icon-name="zap" @click="goTo('/learning/quiz')">开始学习</Button>
        </template>

        <div class="lc-weak">
          <router-link
            v-for="w in weakPoints" :key="w.knowledgeId"
            :to="`/learning/knowledge-mastery/${w.knowledgeId}`" class="lc-weak-item"
          >
            <span class="lc-weak-name">{{ w.name || '未命名知识点' }}</span>
            <span class="lc-weak-score tabular-nums">{{ Math.round(w.masteryScore) }}%</span>
            <span class="lc-weak-bar">
              <span class="lc-weak-bar-fill" :style="{ width: Math.min(100, Math.max(0, w.masteryScore)) + '%' }"></span>
            </span>
          </router-link>
        </div>
      </StateView>

      <!-- Architecture-4.0-5：场景练习待补强（真实 SCENARIO_COMPLETED 事件驱动） -->
      <StateView v-if="scenarioStatus === 'error' || scenarioStatus === 'retrying'" :status="scenarioStatus" :error-title="scenarioError?.title" :error-scope="scenarioError?.scope" :error-hint="scenarioError?.hint" :show-retry="scenarioError?.retryable ?? true" @retry="loadScenarioDebt(true)" />
      <div v-if="scenarioStatus === 'ready' && scenarioDebt.length > 0" class="lc-chips">
        <span class="lc-chips-label">待补强场景</span>
        <button v-for="s in scenarioDebt.slice(0, 5)" :key="s.scenarioId" type="button"
                class="lc-chip" :title="`最近覆盖 ${s.matched}/${s.total} 个要点，回到场景补强再答`"
                @click="goTo(`/learning/scenario?id=${s.scenarioId}`)">
          {{ s.scenarioTitle }}
        </button>
        <span v-if="scenarioDebt.length > 5" class="lc-chip-more">+{{ scenarioDebt.length - 5 }}</span>
      </div>
    </section>

    <!-- ══════════ 次级：当前情况（Evidence）+ 学习热力图 + 工具 ══════════ -->
    <section v-if="strengthSummary.strongest" class="lc-section">
      <div class="lc-section-head">
        <h2 class="lc-section-title">当前情况</h2>
        <span v-if="latestEvidenceLabel" class="lc-count">最近：{{ latestEvidenceLabel }}</span>
      </div>
      <div class="lc-evidence">
        <div class="lc-evidence-row">
          <span class="lc-evidence-key">优势</span>
          <span class="lc-evidence-val good">
            {{ DIMENSION_LABEL[strengthSummary.strongest.dimension] }} {{ strengthSummary.strongest.score }} 分
          </span>
        </div>
        <div v-if="strengthSummary.weakest" class="lc-evidence-row">
          <span class="lc-evidence-key">需要加强</span>
          <span class="lc-evidence-val bad">
            {{ DIMENSION_LABEL[strengthSummary.weakest.dimension] }} {{ strengthSummary.weakest.score }} 分
          </span>
        </div>
        <div v-if="nextStrategyLabel" class="lc-evidence-row">
          <span class="lc-evidence-key">下一步策略</span>
          <span class="lc-evidence-val">{{ nextStrategyLabel }}</span>
        </div>
      </div>
    </section>

    <section class="lc-section">
      <div class="lc-section-head">
        <h2 class="lc-section-title">学习热力图</h2>
        <span v-if="heatmapStatus === 'ready'" class="lc-count">最近 {{ heatWeeks }} 周<span v-if="statsStatus === 'ready'"> · 本月 {{ monthHours }} 小时</span></span>
      </div>
      <StateView :status="heatmapStatus" :error-title="heatmapError?.title" :error-scope="heatmapError?.scope" :error-hint="heatmapError?.hint" :show-retry="heatmapError?.retryable ?? true" empty-title="还没有学习热力记录" empty-description="阅读或复习后，真实学习活动会显示在这里。" type="card" :rows="1" @retry="loadHeatmap(true)">
      <template #empty-action><Button @click="startMainAction">{{ mainCta.text }}</Button></template>
      <div class="lc-heat">
        <div v-for="(week, wi) in heatWeeksData" :key="wi" class="lc-heat-col">
          <div v-for="(cell, di) in week" :key="di" class="heatmap-cell"
               :class="cell ? `heatmap-${cell.level}` : 'opacity-0'"
               :title="cell ? `${cell.date} · ${cell.count} 次` : ''"></div>
        </div>
      </div>
      <p v-if="heatTotal === 0" class="lc-heat-empty">暂无学习记录，开始阅读或复习后会显示学习热力</p>
      </StateView>
    </section>

    <section class="lc-section">
      <div class="lc-section-head">
        <h2 class="lc-section-title">工具</h2>
      </div>
      <div class="lc-tools">
        <router-link v-for="(item, idx) in quickNavVisible" :key="idx" :to="item.to" class="lc-tool">
          <Icon :name="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </router-link>
      </div>
      <button v-if="quickNavRestCount > 0 || showAllQuickNav" type="button" class="lc-tools-more"
              :aria-expanded="showAllQuickNav" @click="showAllQuickNav = !showAllQuickNav">
        <Icon :name="showAllQuickNav ? 'chevron-up' : 'chevron-down'" :size="14" />
        <span>{{ showAllQuickNav ? '收起工具' : `更多工具（${quickNavRestCount}）` }}</span>
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import InterviewSeedCard from '@/components/Learning/InterviewSeedCard.vue'
import { displayPathProgress, selectVisiblePaths } from './learning-path-display'
// 学习中心（Phase UI-4 重构）：今日行动中心 —— 待复习 / 待巩固 / 当前路径 / 今日任务 + 动态主 CTA。
// 原有内容（数据概览/热力图/推荐下一步/本周概览）保留在下部，第一屏以「今日行动」为核心。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import DateTimePicker from '@/components/ui/DateTimePicker.vue'
import { parseLocalDateTime, serializeLocalDateTime } from '@/components/ui/date-time-format'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { userApi, learningApi } from '@/api'
import { useAppStore } from '@/stores/apps'
import { useLearningStore } from '@/stores/learning'
import StateView from '@/components/ui/StateView.vue'
import { useAsyncResource, type AsyncStatus } from '@/composables/useAsyncResource'
import EmptyState from '@/components/ui/EmptyState.vue'
import AdaptiveLearningPanel from '@/components/learning-actions/AdaptiveLearningPanel.vue'
import TaskCompletionAction from '@/components/learning-actions/TaskCompletionAction.vue'
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import { actionToCta } from '@/learning/route'
// UI/UX 5.1 §八：主 CTA 也必须携带学习上下文（与 NextActionCard 同源）
import { resolveContextualRoute } from '@/learning/action-contract'
import { scenarioPracticeDebt } from '@/learning/scenario-debt'
import { strongestAndWeakest, DIMENSION_LABEL } from '@/learning/evidence/diagnosis'
import { STRATEGY_LABEL } from '@/learning/evidence/strategy'
import type { NextLearningAction } from '@/learning/types';
import type {
  LearningTaskVO,
  LearningPathVO,
  LearningGoalVO,
  DailyActivityVO,
  LearningTaskInput,
  KnowledgeMasteryVO,
} from '@/api/types'
import type { ScenarioPracticeDebtItem } from '@/learning/scenario-debt'

const appStore = useAppStore()
/** UI/UX 5.0：全站唯一学习状态来源（§三） */
const learning = useLearningStore()
const updatingTasks = ref(new Set<string>())
const studyPackEnabled = import.meta.env.VITE_PRODUCT_STUDY_PACK === 'true'
const router = useRouter()
const statsResource = useAsyncResource(() => userApi.stats(), () => false, '学习统计加载失败', 'section')
const { status: statsStatus, error: statsError } = statsResource
const heatmapResource = useAsyncResource(() => learningApi.dailyActivity(120), data => data.length === 0 || data.every(day => day.count === 0), '学习热力图加载失败', 'section')
const { status: heatmapStatus, error: heatmapError } = heatmapResource
async function loadHeatmap(retry = false) {
  await heatmapResource.run(retry)
  if (heatmapStatus.value !== 'error' && heatmapResource.data.value) dailyActivity.value = heatmapResource.data.value
}

/* ===== UI/UX 5.0 §三：待复习 / 薄弱知识统一从 store 读，学习中心不再各拉一份 ===== */
const reviewRequired = computed<KnowledgeMasteryVO[]>(() => learning.reviewRequired)
const weakPoints = computed<KnowledgeMasteryVO[]>(() => learning.weakPoints)
const weakError = computed(() => learning.weakError)

/** 错误态重试：重新拉取唯一学习状态 */
async function reloadLearning(): Promise<void> {
  await learning.load(true)
}

/* ================= Architecture-4.0-5：场景练习待补强（真实 SCENARIO 事件驱动，Practice Debt） ================= */
/* ===== UI/UX 5.0 §十七：统一三态（loading / empty / error / ready），禁止白屏与空白卡片 ===== */
type LcStatus = AsyncStatus

const learningLoading = computed(() => learning.loading)

const taskStatus = computed<LcStatus>(() => {
  if (learningLoading.value) return learning.loaded ? 'retrying' : 'loading'
  if (learning.tasksError) return 'error'
  return tasks.value.length > 0 ? 'ready' : 'empty'
})

const pathStatus = computed<LcStatus>(() => {
  if (learningLoading.value) return learning.loaded ? 'retrying' : 'loading'
  if (learning.pathsError) return 'error'
  return ongoingPaths.value.length > 0 ? 'ready' : 'empty'
})

const weakStatus = computed<LcStatus>(() => {
  if (learningLoading.value) return learning.loaded ? 'retrying' : 'loading'
  if (weakError.value) return 'error'
  return weakPoints.value.length > 0 ? 'ready' : 'empty'
})

/** 目标关联路径进度（真实字段，无路径为 0） */
const goalPathPercent = computed(() => {
  return goalLinkedPath.value ? displayPathProgress(goalLinkedPath.value) : null
})

const scenarioDebt = ref<ScenarioPracticeDebtItem[]>([])
const scenarioResource = useAsyncResource(async () => {
  const page = await learningApi.learningEvents({ eventType: 'SCENARIO_COMPLETED', current: 1, size: 50 })
  return scenarioPracticeDebt(page.records ?? [])
}, list => list.length === 0, '场景练习记录加载失败', 'section')
const { status: scenarioStatus, error: scenarioError } = scenarioResource



/**
 * 场景练习待补强（4.0-5）：拉取最近 50 条 SCENARIO_COMPLETED 事件 → 纯函数筛选「最近一次未覆盖全部要点」的场景。
 * 失败独立显示错误与重试，不阻塞学习中心，也不伪装成无待补强场景。
 */
async function loadScenarioDebt(retry = false) {
  await scenarioResource.run(retry)
  if (scenarioStatus.value !== 'error' && scenarioResource.data.value) scenarioDebt.value = scenarioResource.data.value
}

/** 今日完成数：真实 tasks 中 status === 1 的数量（§28/§29，不伪造百分比目标） */
const doneCount = computed(() => tasks.value.filter((t) => t.status === 1).length)
const donePercent = computed(() => {
  if (tasks.value.length === 0) return 0
  return Math.round((doneCount.value / tasks.value.length) * 100)
})

/** 当前学习路径：唯一定义（目标关联路径优先，其次进行中路径），与首页一致（§三） */

/* ============ Architecture-4.1：Evidence & Strategy Engine（§4-§30） ============
 * 数据流：最近学习事件 → resolveEvidenceBatch（Event→Evidence）
 *   → aggregateEvidence（多次证据 + 时间衰减 + 样本上限）
 *   → diagnoseFromEvidence（能力诊断 + 确定性根因）
 *   → buildTopCandidate（Strategy + App 可用性 + Goal 上下文）
 *   → 作为 ctx.evidence 注入既有 resolveNextAction 统一排序（§25：不重写 NextAction）。
 * 无事件 / 无证据时整条链自动为空，既有行为完全不变（§36）。
 */
/* 事件流由 store 统一持有，学习中心不再单独拉取 */


/** 事件 → 证据（不可解析的事件自动丢弃）—— 与首页 / AI Coach 同源（§三） */
const evidences = computed(() => learning.evidences)
/** 证据 → 六维聚合（30 天窗口、时间衰减、样本上限） */
const evidenceAggregate = computed(() => learning.evidenceAggregate)
/** 聚合 → 能力诊断（无证据返回 []，绝不编造诊断） */
/** 诊断 → 策略 → 候选动作（过 App 可用性 + 绑定 Goal 上下文） */
/** 诊断 → 策略 → 候选动作：与首页 / AI Coach 同源（§三） */
const evidenceCandidate = computed(() => learning.evidenceCandidate)

/** §28 轻量状态：优势 / 需加强（只在有真实 Evidence 的维度里比较） */
const strengthSummary = computed(() => strongestAndWeakest(evidenceAggregate.value))
/** 最近一次证据的标签（用户自己填的主题，真实可见，用于「最近在练」上下文） */
const latestEvidenceLabel = computed(() => {
  const last = evidences.value[evidences.value.length - 1]
  return last ? last.label : null
})
/** Architecture-4.2 §7：由 activeGoal 推导出的「当前知识点」上下文（DAG 当前节点 → 章节知识点映射） */
const knowledgeContext = computed(() => {
  const c = goalContextRef.context.value
  return { knowledgeTitle: c.knowledgeTitle, learningNodeTitle: c.learningNodeTitle }
})
/** 下一步策略名（供状态卡展示，与 NextAction 同源） */
const nextStrategyLabel = computed(() => {
  const c = evidenceCandidate.value
  return c ? STRATEGY_LABEL[c.strategy] : null
})

/** UI/UX 5.0 §三：NextAction 唯一来源 —— 与首页 / AI Coach / 闪卡页完全同源 */
const nextAction = computed<NextLearningAction>(() => learning.nextAction)

/**
 * 动态主 CTA（§13/§42）：与「下一步」卡片共用同一优先级来源（§69），不再各自定义。
 *
 * UI/UX 5.1 §八 修正：文案仍来自 `actionToCta`，但路径改走 `resolveContextualRoute`。
 * 旧实现直接用 `actionToCta(action).path`（= `resolveActionRoute` 的**裸路由**），
 * 于是学习中心顶部这颗 Primary CTA 会把 knowledgeId 丢掉 —— 同一页上方的「下一步」卡片
 * 带上下文、下方主按钮不带，用户从两个入口进去拿到的状态不一致。
 */
const mainCta = computed(() => {
  const cta = actionToCta(nextAction.value)
  return { ...cta, path: resolveContextualRoute(nextAction.value, learning.nextActionContext) }
})

/** Architecture-6：需要关注（§6.6/§88-91/§157）——理解+计划层。
 *  复用学习中心已加载的真实数据，本地纯函数推导，不新增 API（§123-129）。最多展示 3 条（§157）。 */

const todaySection = ref<HTMLElement | null>(null)

function startMainAction() {
  if (nextAction.value.type !== 'TASK') {
    goTo(mainCta.value.path)
    return
  }
  // TASK lands on this page: activate the existing task UI instead of pushing the same route.
  const task = todaySection.value?.querySelector<HTMLElement>('.lc-task-check:not(.done)')?.closest<HTMLElement>('.lc-task')
  if (!task) return
  const details = task.querySelector<HTMLDetailsElement>('details')
  if (details) details.open = true
  task.focus({ preventScroll: true })
  task.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function goTo(path: string) {
  router.push(path)
}


const streakDays = ref(0)
const weekHours = ref(0)
const todayMinutes = ref(0)
const monthHours = ref(0)
const weekChapters = ref(0)
const weekPractices = ref(0)
const accuracy = ref(0)
const energy = ref(0)

/** UI/UX 5.0 §三：路径 / 任务 / 目标统一从 store 读，学习中心不再自持一份（避免与首页分叉） */
const ongoingPaths = computed<LearningPathVO[]>(() => selectVisiblePaths(learning.paths, learning.currentPath))
const tasks = computed<LearningTaskVO[]>(() => learning.tasks)
const activeGoal = computed<LearningGoalVO | null>(() => learning.activeGoal)
const goalLinkedPath = computed<LearningPathVO | null>(() => learning.goalPath)
/** Architecture-4.2：Goal → 当前知识点上下文（与首页 / AI Coach 同源） */
const goalContextRef = { context: computed(() => learning.context) }

const pathProgress = displayPathProgress


/**
 * 学习首页快捷入口（3.2 §36-40/§120-125：页面内部 Card 导航，不是 Sidebar 菜单）。
 *
 * UI/UX 3.2 起，学习 Sidebar 只保留「学习首页」一个固定入口（§8/§28），
 * 原先的固定二级菜单（今日计划 / 学习路径 / 复习计划 / 掌握度 / 学习报告 / 挑战 /
 * 学习工作台 / 番茄钟 / 专注模式 / 编程 Agent）全部下沉到本页卡片，
 * 功能与路由一律保留（§51-59），只换入口位置（§105-107 避免死功能）。
 *
 * 前 8 个默认展示，其余折叠进「更多工具」（§118：学习首页不做第二个 Sidebar、§183：前 N 个 + 更多）。
 */
const QUICK_NAV_LIMIT = 8

const quickNavItems = [
  // ── 学习核心能力（页面内部，始终可用）
  { label: '今日计划', icon: 'calendar-check', color: 'var(--kb-primary)', to: '/learning/today-plan' },
  { label: '学习路径', icon: 'route', color: 'var(--kb-primary)', to: '/learning/paths' },
  { label: '复习计划', icon: 'calendar-check', color: 'var(--kb-warning)', to: '/learning/review' },
  { label: '掌握分布', icon: 'pie-chart', color: '#06B6D4', to: '/learning/mastery' },
  { label: '学习报告', icon: 'bar-chart-2', color: 'var(--kb-accent)', to: '/learning/report' },
  { label: '番茄钟专注', icon: 'timer', color: 'var(--kb-danger)', to: '/learning/pomodoro' },
  { label: '编程 Agent', icon: 'cpu', color: '#8B5CF6', to: '/coding/agent' },
  { label: '学习工作台', icon: 'brain', color: '#8B5CF6', to: '/workbench' },
  // ── 更多工具（含 App 类，按启用态过滤；与 Sidebar 动态菜单同源，不冲突 §127）
  { label: '代码练习', icon: 'code', color: 'var(--kb-warning)', to: '/learning/code-practice' },
  { label: '编程挑战', icon: 'rocket', color: '#8B5CF6', to: '/challenge' },
  // Architecture-4.0-4：场景练习（Scenario Lab，Practice 节点，非 App 无需启用态过滤）
  { label: '场景练习', icon: 'pen-tool', color: '#06B6D4', to: '/learning/scenario' },
  // Architecture-4.0-7：费曼表达（Expression 节点：四维确定性自检评分，非 App 无需启用态过滤）
  { label: '费曼表达', icon: 'book-open', color: '#8B5CF6', to: '/learning/feynman' },
  // Architecture-4.1：第一性原理推导（Understanding 节点：六环节结构完整度，非 App 无需启用态过滤）
  { label: '第一性原理', icon: 'compass', color: '#8B5CF6', to: '/learning/first-principles' },
  { label: '闪卡复习', icon: 'layers', color: 'var(--kb-success)', to: '/learning/flashcards' },
  { label: '知识图谱', icon: 'share-2', color: '#8B5CF6', to: '/learning/knowledge-graph' },
  { label: '行为事件', icon: 'activity', color: 'var(--kb-primary)', to: '/learning/events' },
  { label: 'AI 问答', icon: 'message-square-sparkles', color: '#06B6D4', to: '/chat' },
]

// UI/UX 3.0 §34：学习中心快捷入口中「App 类链接」按 UserApp 启用态过滤（不硬编码展示已停用 App）。
// 番茄钟（focus）为沉浸式核心工具（§13），始终保持；其余 App 类链接仅启用时显示。
const QUICK_NAV_APP_ROUTE: Record<string, string> = {
  '/learning/flashcards': 'flashcard',
  '/learning/code-practice': 'coding',
  '/learning/knowledge-graph': 'knowledge-graph',
}
const quickNavItemsResolved = computed(() =>
  quickNavItems.filter((it) => {
    const key = QUICK_NAV_APP_ROUTE[it.to]
    if (!key) return true
    const st = appStore.stateOf(key)
    return st.installed && st.enabled
  }),
)

/** 是否展开「更多工具」 */
const showAllQuickNav = ref(false)

/** 默认只渲染前 N 个（§183），展开后渲染全部 */
const quickNavVisible = computed(() =>
  showAllQuickNav.value
    ? quickNavItemsResolved.value
    : quickNavItemsResolved.value.slice(0, QUICK_NAV_LIMIT),
)

/** 折叠区剩余数量（为 0 时不渲染切换按钮） */
const quickNavRestCount = computed(() =>
  Math.max(0, quickNavItemsResolved.value.length - QUICK_NAV_LIMIT),
)

// ===== 添加计划功能 =====
const showAddTask = ref(false)
const creatingTask = ref(false)
const newTask = ref<LearningTaskInput>({
  title: '',
  description: '',
  type: 'study',
  deadline: '',
})

const resetAddTask = () => {
  showAddTask.value = false
  newTask.value = { title: '', description: '', type: 'study', deadline: '' }
}

const handleCreateTask = async () => {
  if (!newTask.value.title.trim()) {
    notify('请输入计划标题', 'warning')
    return
  }
  creatingTask.value = true
  try {
    const payload: LearningTaskInput = {
      title: newTask.value.title.trim(),
      description: newTask.value.description?.trim() || undefined,
      type: newTask.value.type || 'study',
    }
    if (newTask.value.deadline) {
      // 后端 LearningTask.deadline 是 LocalDateTime：提交本地墙钟合同，
      // 不能把 toISOString() 的 UTC 时刻写进去，否则用户选的 09:00 会被存成 01:00。
      const parts = parseLocalDateTime(newTask.value.deadline)
      if (parts) payload.deadline = serializeLocalDateTime(parts, 'minute')
    }
    await learningApi.createTask(payload)
    notify('计划添加成功', 'success')
    resetAddTask()
    // 任务由 learning store 统一持有（§三）：重新拉取而非本地赋值
    await learning.load(true)
  } catch (e: unknown) {
    notify('添加失败：' + getApiError(e), 'error')
  } finally {
    creatingTask.value = false
  }
}

const toggleTaskStatus = async (task: LearningTaskVO) => {
  if (task.completionRequirement) { notify('此任务需要真实学习记录，请使用去练习入口', 'info'); return }
  const key = (task.source || 'LEARNING') + task.id
  if (updatingTasks.value.has(key)) return
  updatingTasks.value.add(key)
  const newStatus = task.status === 1 ? 0 : 1
  try {
    await learningApi.updateTaskStatus(task.id, newStatus, task.source)
    // Void接口不提供最终状态，按原Store回读，失败时不能本地假装完成。
    await learning.load(true)
    if (learning.tasksError) throw new Error(learning.tasksError)
    notify('已重新读取任务状态', 'success')
  } catch (e: unknown) {
    notify('操作失败：' + getApiError(e), 'error')
  } finally { updatingTasks.value.delete(key) }
}

const handleDeleteTask = async (task: LearningTaskVO) => {
  await confirmDialog(`确定删除计划「${task.title}」吗？`, { "title": "确认删除", "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", "variant": "danger", onConfirm: async () => {
          try {
              await learningApi.deleteTask(task.id, task.source);
              await learning.load(true);
              notify('已删除', 'success');
          }
          catch (e: unknown) {
              notify('删除失败：' + getApiError(e), 'error');
              throw e;
          }
      } });
}

const goToPath = (id: number) => {
  window.location.href = `/learning/path/${id}`
}

// 热力图数据
interface HeatCell {
  date: string
  count: number
  level: number
}

const dailyActivity = ref<DailyActivityVO[]>([])
const heatWeeksData = computed<(HeatCell | null)[][]>(() => {
  const list = dailyActivity.value
  if (!list.length) return []
  const max = Math.max(1, ...list.map((d) => d.count))
  const firstDow = (new Date(`${list[0].date}T00:00:00`).getDay() + 6) % 7
  const cells: (HeatCell | null)[] = []
  for (let i = 0; i < firstDow; i++) cells.push(null)
  for (const d of list) {
    const level = d.count === 0 ? 0 : Math.min(4, Math.ceil((d.count / max) * 4))
    cells.push({ date: d.date, count: d.count, level })
  }
  const weeks: (HeatCell | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  return weeks
})
const heatWeeks = computed(() => heatWeeksData.value.length || 16)
const heatTotal = computed(() => dailyActivity.value.reduce((sum, d) => sum + d.count, 0))

async function loadStats(retry = false): Promise<void> {
    await statsResource.run(retry)
    if (statsStatus.value !== 'ready' || !statsResource.data.value) return
    const stats = statsResource.data.value

    streakDays.value = stats.streakDays ?? 0
    // Phase UI-5（承接 UI-4 遗留 §59）：删除无依据的伪造统计。
    // 原实现用 totalStudyHours×6 / 70+闪卡数/10 / ×3 等推导"今日分钟/正确率/本周章节"，
    // 均无真实数据来源，违反"禁止伪造百分比"原则。
    // 现改为只呈现真实字段；无对应接口的一律置 0，交由模板 v-if 隐藏，而非凭空编造。
    weekHours.value = stats.totalStudyHours ?? 0
    monthHours.value = stats.totalStudyHours ?? 0
    todayMinutes.value = 0 // 无「今日分钟」接口，置 0 由模板隐藏
    weekChapters.value = 0 // 无「本周章节」接口，置 0 由模板隐藏
    weekPractices.value = 0 // 无「本周练习」接口，置 0 由模板隐藏
    accuracy.value = 0 // 无「正确率」接口，置 0 由模板隐藏
    energy.value = stats.energy ?? 0

}
async function loadData(): Promise<void> {
  // 核心学习状态仍由唯一 Store 加载；页面独有区块各自处理五态。
  await Promise.all([loadStats(), loadHeatmap(), learning.load(), loadScenarioDebt()])
}

onMounted(() => {
  void loadData()
})
</script>

<style scoped>
/*
 * UI/UX 5.0 §五/§六：统一视觉语言
 *   - 8pt 间距基线；圆角统一 8~16px
 *   - 图标默认中性色，主色只用于主要操作与品牌强调
 *   - 能用普通 section 表达的不再包 Card
 */
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.learning-center {
  padding: 24px;
  max-width: none;
  margin-inline: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-8, 32px);

  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
}

/* ══════════ 页面头 ══════════ */
.lc-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--kb-space-4, 16px);
  flex-wrap: wrap;
}
.lc-title {
  margin: 0;
  font-size: var(--kb-fs-h2, 24px);
  font-weight: 600;
  line-height: 1.3;
  color: var(--kb-foreground);
}
.lc-subtitle {
  margin: 2px 0 0;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-muted-foreground);
}

/* ══════════ 通用 section ══════════ */
.lc-section {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-3, 12px);
}
.lc-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-3, 12px);
  flex-wrap: wrap;
}
.lc-section-title {
  margin: 0;
  font-size: var(--kb-fs-h4, 16px);
  font-weight: 600;
  color: var(--kb-foreground);
}
.lc-head-actions {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2, 8px);
}
.lc-link,
.lc-link-inline {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-primary);
  text-decoration: none;
  transition: opacity 0.15s ease;
}
.lc-link:hover,
.lc-link-inline:hover {
  opacity: 0.8;
}
.lc-count {
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}

/* ══════════ 错误条 ══════════ */
.lc-error {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3, 12px);
  padding: var(--kb-space-3, 12px) var(--kb-space-4, 16px);
  border: 1px solid var(--kb-destructive);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-danger-subtle);
}
.lc-error :deep(svg) {
  color: var(--kb-destructive);
  flex-shrink: 0;
}
.lc-error-text {
  flex: 1;
  margin: 0;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-foreground);
}

/* ══════════ 1. 当前目标 ══════════ */
.lc-goal {
  display: flex;
  align-items: flex-start;
  gap: var(--kb-space-3, 12px);
  padding: var(--kb-space-4, 16px);
  border: 1px solid var(--kb-border);
  border-left: 3px solid var(--kb-primary);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-card);
}
.lc-goal-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-primary-subtle);
  color: var(--kb-primary);
}
.lc-goal-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.lc-goal-top {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2, 8px);
}
.lc-goal-title {
  font-size: var(--kb-fs-body-lg, 16px);
  font-weight: 600;
  color: var(--kb-foreground);
  text-decoration: none;
}
.lc-goal-title:hover {
  color: var(--kb-primary);
}
.lc-goal-pct {
  font-size: var(--kb-fs-caption, 12px);
  font-weight: 600;
  color: var(--kb-primary);
}
.lc-goal-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-muted-foreground);
}

/* ══════════ 2. 今日 ══════════ */
.lc-progress {
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.lc-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--kb-primary);
  transition: width 0.3s ease;
}
.lc-add-task {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-2, 8px);
  padding: var(--kb-space-3, 12px);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-card);
}
.lc-add-task-row {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2, 8px);
  flex-wrap: wrap;
}
/** 截止时间复用 DateTimePicker（FormField 内部 width:100%），此处只约束外层宽度。 */
.lc-deadline {
  flex: 0 0 auto;
  width: 200px;
  min-width: 0;
}
.lc-input:not(.kb-form-control) {
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-background);
}
.lc-input {
  width: 100%;
  padding: 8px var(--kb-space-3, 12px);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-sm, 13px);
}
.lc-input:not(.kb-form-control):focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}
.lc-select:not(.kb-form-control) {
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm, 6px);
  background: var(--kb-background);
}
.lc-select {
  padding: 6px 8px;
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-sm, 13px);
}
.lc-tasks {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.lc-task {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3, 12px);
  padding: 10px var(--kb-space-3, 12px);
  border-radius: var(--kb-radius-md, 8px);
  transition: background-color 0.15s ease;
}
.lc-task:hover {
  background: var(--kb-muted);
}
.lc-task-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  border: 1px solid var(--kb-border);
  border-radius: 4px;
  background: transparent;
  color: transparent;
  cursor: pointer;
}
.lc-task-check.done {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.lc-task-check:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}
.lc-task-main {
  flex: 1;
  min-width: 0;
}
.lc-task-title {
  margin: 0;
  font-size: var(--kb-fs-body-md, 14px);
  color: var(--kb-foreground);
}
.lc-task-title.done {
  color: var(--kb-muted-foreground);
  text-decoration: line-through;
}
.lc-task-desc {
  margin: 2px 0 0;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.lc-task-del {
  flex-shrink: 0;
  padding: 4px;
  border: none;
  border-radius: var(--kb-radius-sm, 6px);
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
}
.lc-task-del:hover {
  background: var(--kb-danger-subtle);
  color: var(--kb-destructive);
}

/* ══════════ chips（待复习 / 待补强场景）══════════ */
.lc-chips {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2, 8px);
  flex-wrap: wrap;
  padding-top: var(--kb-space-2, 8px);
  border-top: 1px solid var(--kb-border);
}
.lc-chips-label {
  font-size: var(--kb-fs-caption, 12px);
  font-weight: 500;
  color: var(--kb-muted-foreground);
}
.lc-chip {
  padding: 4px 10px;
  border: 1px solid var(--kb-border);
  border-radius: 999px;
  background: var(--kb-card);
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.lc-chip:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.lc-chip:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}
.lc-chip-more {
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}

/* ══════════ 4. 路径进度 ══════════ */
.lc-paths {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--kb-space-3, 12px);
}
.lc-path {
  padding: var(--kb-space-4, 16px);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-card);
  cursor: pointer;
  transition: box-shadow 0.2s ease;
}
.lc-path:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
.lc-path-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.lc-path-badge {
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: var(--kb-radius-sm, 6px);
  background: var(--kb-muted);
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.lc-path-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg, 16px);
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lc-path-desc {
  margin: 0;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-muted-foreground);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.lc-path-meta {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3, 12px);
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.lc-path-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.lc-path-bar-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--kb-primary);
  transition: width 0.3s ease;
}

/* ══════════ 5. 薄弱知识 ══════════ */
.lc-weak {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.lc-weak-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 140px;
  align-items: center;
  gap: var(--kb-space-3, 12px);
  padding: 10px var(--kb-space-3, 12px);
  border-radius: var(--kb-radius-md, 8px);
  text-decoration: none;
  transition: background-color 0.15s ease;
}
.lc-weak-item:hover {
  background: var(--kb-muted);
}
.lc-weak-name {
  font-size: var(--kb-fs-body-md, 14px);
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lc-weak-score {
  font-size: var(--kb-fs-body-sm, 13px);
  font-weight: 600;
  color: var(--kb-foreground);
}
.lc-weak-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.lc-weak-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--kb-warning);
}

/* ══════════ 当前情况（Evidence）═════════ */
.lc-evidence {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--kb-space-4, 16px);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-card);
}
.lc-evidence-row {
  display: flex;
  align-items: baseline;
  gap: var(--kb-space-3, 12px);
}
.lc-evidence-key {
  flex-shrink: 0;
  width: 72px;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.lc-evidence-val {
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-foreground);
}
.lc-evidence-val.good {
  color: var(--kb-success);
}
.lc-evidence-val.bad {
  color: var(--kb-warning);
}

/* ══════════ 热力图 ══════════ */
.lc-heat {
  display: flex;
  gap: 3px;
  flex-wrap: wrap;
}
.lc-heat-col {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.lc-heat-empty {
  margin: 0;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}

/* ══════════ 工具 ══════════ */
.lc-tools {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: var(--kb-space-2, 8px);
}
.lc-tool {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2, 8px);
  padding: 10px var(--kb-space-3, 12px);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-card);
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-foreground);
  text-decoration: none;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.lc-tool:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.lc-tool:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}
.lc-tools-more {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px var(--kb-space-3, 12px);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 8px);
  background: transparent;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-muted-foreground);
  cursor: pointer;
}
.lc-tools-more:hover {
  color: var(--kb-foreground);
}

/* ══════════ 响应式 ══════════ */
@media (max-width: 640px) {
  .learning-center {
    padding: 16px;
    gap: var(--kb-space-6, 24px);
  }
  .lc-weak-item {
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .lc-weak-bar {
    display: none;
  }
}
.lc-path { text-align: left; min-width: 0; font: inherit; color: inherit; }
.lc-path:focus-visible { outline: var(--kb-studio-focus) solid var(--kb-ring); outline-offset: var(--kb-space-1); }
.lc-task-description summary { color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-sm); cursor: pointer; }
.lc-task-description[open] summary { color: var(--kb-primary); }
</style>
