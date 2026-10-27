/**
 * UI/UX 5.0 — Learning OS：全局学习状态唯一真源（Pinia）
 *
 * ── 为什么要有这个 store ────────────────────────────────────────────────
 * 扫描真实代码后确认：NextAction 在 4 个页面各自算了一遍，且**传入的上下文各不相同**：
 *   - Home.vue            ：resolveNextAction({ review, weak, tasks, currentPath, goal, goalPath, installedApps })
 *                           → 没有 evidence
 *   - LearningCenterPage  ：resolveNextAction({ …同上…, evidence })          → 有 evidence
 *   - Chat.vue            ：resolveNextAction({ review, weak, tasks, currentPath, installedApps: APP_DEFINITIONS })
 *                           → 没有 goal / goalPath / evidence，且用的是「全部 App」而非「已启用 App」
 *   - FlashCardsHub.vue   ：resolveNextActionWithIntelligence({ … })          → 另一条链，没有 goal
 * 结果就是同一个用户状态下：首页说「继续探索知识」、学习首页说「费曼」、AI 又说第三件事。
 *
 * 本 store 把「原始数据 → Evidence → Diagnosis → Strategy → NextAction」整条链收口到一处，
 * 所有页面只读 `nextAction`，**任何页面不得再自行拼一套上下文**（§二/§三）。
 *
 * ── 铁律 ────────────────────────────────────────────────────────────────
 * 1. 数据是只读快照，真源永远是后端；本 store 不伪造、不兜底造数据，失败即置空并暴露 error。
 * 2. 用户隔离：切换/登出账号必须 reset()，杜绝 A 用户看到 B 用户的目标与掌握度。
 * 3. NextAction 只有一个计算入口（`nextAction` computed），页面不得二次决策。
 */
import { adaptivePathApi,type AdaptivePath } from '@/api/adaptivePath'
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { learningApi } from '@/api'
import { useAuthStore } from './auth'
import { useAppStore } from './apps'
import { resolveNextAction, isPathInProgress } from '@/learning/action-priority'
import { stampActionContext } from '@/learning/action-contract'
import type { LearningEntryContext } from '@/learning/action-contract'
import { resolveEvidenceBatch } from '@/learning/evidence/resolver'
import { aggregateEvidence } from '@/learning/evidence/aggregate'
import { diagnoseFromEvidence, strongestAndWeakest } from '@/learning/evidence/diagnosis'
import { buildTopCandidate } from '@/learning/evidence/action-candidate'
import type { EvidenceGoalContext } from '@/learning/evidence/action-candidate'
import { STRATEGY_LABEL } from '@/learning/evidence/strategy'
import { resolveCurrentChapterNode } from '@/learning/path-node'
import {
  EMPTY_LEARNING_CONTEXT,
  resolveLearningContext,
  type LearningContext,
} from '@/learning/context'
import type {
  ChapterKnowledgeVO,
  ChapterNodeVO,
  KnowledgeMasteryVO,
  LearningEventVO,
  LearningGoalVO,
  LearningPathVO,
  LearningTaskVO,
} from '@/api/types'
import type { NextLearningAction } from '@/learning/types'

/** 最近事件拉取条数：Evidence 聚合窗口为 30 天，100 条足够且避免 N+1（沿用学习中心既有口径） */
const RECENT_EVENT_SIZE = 100

export interface FlashcardSessionDraft {
  sessionId: string
  queueIds: number[]
  currentId: number | null
  createdAt: number
}

export const useLearningStore = defineStore('learning', () => {
  const auth = useAuthStore()
  const appStore = useAppStore()

  /* ============================ 原始真实数据 ============================ */
  const activeGoal = ref<LearningGoalVO | null>(null)
  const paths = ref<LearningPathVO[]>([])
  const tasks = ref<LearningTaskVO[]>([])
  const reviewRequired = ref<KnowledgeMasteryVO[]>([])
  const weakPoints = ref<KnowledgeMasteryVO[]>([])
  const masteries = ref<KnowledgeMasteryVO[]>([])
  const recentEvents = ref<LearningEventVO[]>([])

  /* ============================ 加载 / 错误状态 ============================ */
  /** 首次整体加载中（页面据此显示 LoadingState，而不是空白卡片） */
  const loading = ref(false)
  /** 是否至少成功加载过一次（区分「加载中」与「真的没有数据」） */
  const loaded = ref(false)
  /** 整体错误：任一核心请求失败即置位，页面显示 ErrorState + 重试 */
  const error = ref('')
  /** 各分项的独立错误，便于局部降级而不整页报错 */
  const reviewError = ref('')
  const weakError = ref('')
  const goalError = ref('')
  const pathsError = ref('')
  const tasksError = ref('')

  /* ============ Architecture-4.2：Goal → Node → Knowledge 上下文 ============ */
  const currentNode = ref<ChapterNodeVO | null>(null)
  const chapterKnowledge = ref<ChapterKnowledgeVO | null>(null)
  const contextLoading = ref(false)

  const adaptivePaths=ref<Record<number,AdaptivePath>>({})
  async function loadAdaptivePath(pathId:number){const actor=uid.value;const result=await adaptivePathApi.detail(pathId);if(actor!==uid.value||actor<=0)throw new Error('账号已变化，未展示旧账号建议');adaptivePaths.value={...adaptivePaths.value,[pathId]:result};return result}
  const uid = computed(() => auth.user?.id ?? 0)

  /* ============================ 派生：路径 / 目标 ============================ */

  /**
   * 当前学习路径（唯一定义，§三）。
   * 口径：优先「目标关联的已报名路径」，其次「已报名且有进度」的路径。
   * 原先 Home 用 `find(p => p.enrolled)`、LearningCenter 用 `ongoingPaths[0]`、Chat 用 `isPathInProgress`，
   * 三者不同 → 统一到此处，页面一律只读本值。
   */
  const currentPath = computed<LearningPathVO | null>(() => {
    const goalPathId = activeGoal.value?.pathId
    if (goalPathId) {
      const linked = paths.value.find((p) => p.id === goalPathId)
      if (linked) return linked
    }
    return paths.value.find((p) => isPathInProgress(p)) ?? null
  })

  /** 目标关联路径（给 resolveNextAction 的 goalPath，必须与 currentPath 同源） */
  const goalPath = computed<LearningPathVO | null>(() => {
    const goalPathId = activeGoal.value?.pathId
    if (!goalPathId) return null
    return paths.value.find((p) => p.id === goalPathId) ?? null
  })

  /** 当前路径进度百分比（0-100，无路径为 0） */
  const currentPathPercent = computed(() =>
    Math.min(100, Math.max(0, Math.round(currentPath.value?.progress ?? 0))),
  )

  /** 今日任务完成情况 */
  const doneTaskCount = computed(() => tasks.value.filter((t) => t.status === 1).length)
  const todayTaskPercent = computed(() =>
    tasks.value.length === 0 ? 0 : Math.round((doneTaskCount.value / tasks.value.length) * 100),
  )

  /* ==================== Evidence → Diagnosis → Strategy ==================== */
  /** 事件 → 证据（不可解析的事件自动丢弃） */
  const evidences = computed(() => resolveEvidenceBatch(recentEvents.value))
  /** 证据 → 六维聚合（30 天窗口、时间衰减、样本上限） */
  const evidenceAggregate = computed(() => aggregateEvidence(evidences.value))
  /** 聚合 → 能力诊断（无证据返回 []，绝不编造诊断） */
  const behaviorDiagnoses = computed(() => diagnoseFromEvidence(evidenceAggregate.value))

  /**
   * Architecture-4.2：Goal → 当前知识点上下文（纯函数，取不到即 null，绝不猜测）。
   * 全站统一：Home / 学习中心 / AI Coach 读到的是同一个 knowledgeId。
   */
  const context = computed<LearningContext>(() =>
    resolveLearningContext({
      goal: activeGoal.value,
      currentNode: currentNode.value,
      chapterKnowledge: chapterKnowledge.value,
      masteries: masteries.value,
    }),
  )

  /** Evidence 候选动作（绑定 Goal 上下文，过 App 可用性） */
  const evidenceCandidate = computed(() => {
    const c = context.value
    const ctx: EvidenceGoalContext = {
      goal: activeGoal.value,
      learningNodeId: c.learningNodeId,
      learningNodeTitle: c.learningNodeTitle,
      knowledgeId: c.knowledgeId,
      knowledgeName: c.knowledgeTitle,
    }
    return buildTopCandidate(
      evidenceAggregate.value,
      behaviorDiagnoses.value,
      appStore.enabledApps,
      ctx,
      masteries.value,
    )
  })

  /** 优势 / 需加强（只在有真实 Evidence 的维度里比较） */
  const strengthSummary = computed(() => strongestAndWeakest(evidenceAggregate.value))
  /** 推荐策略名（供「学习方式」展示，与 NextAction 同源） */
  const strategyLabel = computed(() => {
    const c = evidenceCandidate.value
    return c ? STRATEGY_LABEL[c.strategy] : null
  })

  /* ==================== 唯一 NextAction 决策入口（§三） ==================== */
  /**
   * 全站唯一 NextAction。
   * 上下文一次性给全：reviewRequired / weakPoints / tasks / currentPath / goal / goalPath
   * / installedApps / evidence —— 四个页面读到的结果因此必然一致。
   *
   * UI/UX 5.1 §八：解析完成后**统一盖章学习上下文**（knowledgeId / knowledgeName / goalId /
   * learningNodeId）。盖章放在这里而不是各页面，保证「同一个动作无论从哪个页面点，
   * 带到 App 的上下文完全一致」——页面不得再各自拼装。
   */
  const nextAction = computed<NextLearningAction>(() => {
    const resolved = resolveNextAction({
      reviewRequired: reviewRequired.value,
      weakPoints: weakPoints.value,
      tasks: tasks.value,
      currentPath: currentPath.value,
      goal: activeGoal.value,
      goalPath: goalPath.value,
      installedApps: appStore.enabledApps,
      evidence: evidenceCandidate.value,
    })
    return stampActionContext(resolved, nextActionContext.value)
  })

  /**
   * NextAction 的学习上下文（单一来源）。
   * goalId 来自真实 `activeGoal`；learningNodeId 来自 Architecture-4.2 的章节节点解析；
   * knowledgeId/knowledgeName 优先取解析后的上下文，绝不由页面编造。
   */
  const nextActionContext = computed<LearningEntryContext>(() => {
    const c = context.value
    return {
      knowledgeId: c.knowledgeId ?? null,
      knowledgeName: c.knowledgeTitle ?? null,
      goalId: activeGoal.value?.id ?? null,
      learningNodeId: c.learningNodeId ?? null,
    }
  })

  /** NextAction 指向的知识点（优先动作自带，其次当前上下文） */
  const nextActionKnowledgeId = computed<number | null>(
    () => nextAction.value.knowledgeId ?? context.value.knowledgeId ?? null,
  )
  const nextActionKnowledgeName = computed<string | null>(
    () => nextAction.value.knowledgeName ?? context.value.knowledgeTitle ?? null,
  )

  /* ============================ 数据加载 ============================ */

  /** 目标 → DAG 当前节点 → 章节知识点（真实映射，失败静默降级为 null，不阻塞） */
  async function loadContext(): Promise<void> {
    const goal = activeGoal.value
    currentNode.value = null
    chapterKnowledge.value = null
    if (!goal?.pathId) return
    contextLoading.value = true
    try {
      const dag = await learningApi.dag(goal.pathId)
      const node = resolveCurrentChapterNode(dag.nodes)
      currentNode.value = node
      if (node) {
        try {
          chapterKnowledge.value = await learningApi.chapterKnowledge(node.id)
        } catch {
          chapterKnowledge.value = null
        }
      }
    } catch {
      currentNode.value = null
      chapterKnowledge.value = null
    } finally {
      contextLoading.value = false
    }
  }

  /**
   * 加载全部学习核心数据。
   * 各分项独立错误隔离（Promise.allSettled）：某一项失败不会让整页白屏，
   * 但会置位对应 error 供页面显示 ErrorState / 重试。
   */
  async function load(force = false): Promise<void> {
    if (!auth.isLoggedIn) return
    if (loading.value) return
    if (loaded.value && !force) return
    loading.value = true
    error.value = ''
    try {
      const [goalR, pathsR, tasksR, reviewR, weakR, masteryR, eventsR] = await Promise.allSettled([
        learningApi.activeGoal(),
        learningApi.paths(),
        learningApi.tasks(),
        learningApi.knowledgeMasteryReviewRequired(),
        learningApi.knowledgeMasteryWeak(),
        learningApi.knowledgeMastery(),
        learningApi.learningEvents({ current: 1, size: RECENT_EVENT_SIZE }),
      ])

      activeGoal.value = goalR.status === 'fulfilled' ? goalR.value : null
      goalError.value = goalR.status === 'rejected' ? '学习目标加载失败' : ''

      paths.value = pathsR.status === 'fulfilled' ? pathsR.value : []
      pathsError.value = pathsR.status === 'rejected' ? '学习路径加载失败' : ''
      tasks.value = tasksR.status === 'fulfilled' ? tasksR.value : []
      tasksError.value = tasksR.status === 'rejected' ? '学习任务加载失败' : ''

      reviewRequired.value = reviewR.status === 'fulfilled' ? reviewR.value : []
      reviewError.value = reviewR.status === 'rejected' ? '待复习数据加载失败' : ''

      weakPoints.value = weakR.status === 'fulfilled' ? weakR.value : []
      weakError.value = weakR.status === 'rejected' ? '薄弱知识加载失败' : ''

      masteries.value = masteryR.status === 'fulfilled' ? masteryR.value : []
      recentEvents.value =
        eventsR.status === 'fulfilled' ? (eventsR.value.records ?? []) : []

      // 核心三项（目标/路径/掌握度）全失败才算整页错误，其余局部降级
      const coreFailed =
        goalR.status === 'rejected' && pathsR.status === 'rejected' && masteryR.status === 'rejected'
      if (coreFailed) error.value = '学习数据加载失败'
      loaded.value = true

      // Goal → Knowledge 上下文依赖 activeGoal，串行加载（2 次请求，非 N+1）
      await loadContext()
    } catch (e) {
      error.value = '学习数据加载失败'
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /** 便捷：仅刷新待复习（首页/学习中心局部重试） */
  async function reloadReview(): Promise<void> {
    reviewError.value = ''
    try {
      reviewRequired.value = await learningApi.knowledgeMasteryReviewRequired()
    } catch {
      reviewError.value = '待复习数据加载失败'
    }
  }

  /** 便捷：仅刷新薄弱知识 */
  async function reloadWeak(): Promise<void> {
    weakError.value = ''
    try {
      weakPoints.value = await learningApi.knowledgeMasteryWeak()
    } catch {
      weakError.value = '薄弱知识加载失败'
    }
  }

  /** 切换任务完成状态（乐观更新 + 失败回滚，状态只在本 store 内流转） */
  async function toggleTask(task: LearningTaskVO): Promise<void> {
    const next = task.status === 1 ? 0 : 1
    const prev = task.status
    const target = tasks.value.find((t) => t.id === task.id && (t.source ?? 'LEARNING') === (task.source ?? 'LEARNING'))
    if (!target) return
    target.status = next
    try {
      if (task.source === 'TASK') await learningApi.updateTaskStatus(task.id, next, 'TASK')
      else await learningApi.updateTaskStatus(task.id, next)
    } catch {
      target.status = prev
      throw new Error('任务状态更新失败')
    }
  }

  /** 目标变更后重算上下文（由页面在 CRUD 目标后调用） */
  async function refreshGoal(): Promise<void> {
    try {
      activeGoal.value = await learningApi.activeGoal()
      goalError.value = ''
    } catch {
      goalError.value = '学习目标加载失败'
    }
    paths.value = await learningApi.paths().catch(() => paths.value)
    await loadContext()
  }

  // UI resume draft only. Ratings are always reloaded from the server's original learning events.
  function readFlashcardSession(scope: string): FlashcardSessionDraft | null {
    if (!uid.value) return null
    try {
      const raw = sessionStorage.getItem(`knowflow:flashcards:v2:${uid.value}:${scope}`)
      if (!raw) return null
      const draft = JSON.parse(raw) as FlashcardSessionDraft
      if (!/^[A-Za-z0-9_-]{12,80}$/.test(draft.sessionId) || !Array.isArray(draft.queueIds) || draft.queueIds.length > 10000 || !draft.queueIds.every(id => Number.isSafeInteger(id) && id > 0) || new Set(draft.queueIds).size !== draft.queueIds.length || !Number.isFinite(draft.createdAt) || draft.createdAt > Date.now() || Date.now() - draft.createdAt > 86400000) return null
      return draft
    } catch { return null }
  }
  function saveFlashcardSession(scope: string, draft: FlashcardSessionDraft | null): void {
    if (!uid.value) return
    const key = `knowflow:flashcards:v2:${uid.value}:${scope}`
    try { if (draft) sessionStorage.setItem(key, JSON.stringify(draft)); else sessionStorage.removeItem(key) }
    catch { /* Storage restrictions do not change server review persistence. */ }
  }

  /** 用户隔离：切换账号 / 登出时清空，杜绝串号 */
  function reset(): void {
    adaptivePaths.value={}
    activeGoal.value = null
    paths.value = []
    tasks.value = []
    reviewRequired.value = []
    weakPoints.value = []
    masteries.value = []
    recentEvents.value = []
    currentNode.value = null
    chapterKnowledge.value = null
    loading.value = false
    loaded.value = false
    error.value = ''
    reviewError.value = ''
    weakError.value = ''
    goalError.value = ''
    pathsError.value = ''
    tasksError.value = ''
  }

  // 账号切换即重置（与 apps/wallet store 同一纪律）。
  // UI/UX 5.0 修复：lastUid 初始为 null 而非 0 —— 原实现在「store 创建于已登录态」时
  // lastUid=0，首次切换到其他用户因 `0 && …` 短路而永不 reset（真实用户隔离漏洞）。
  // immediate: true 让注册即记录当前 uid，后续任何变化都能正确判定。
  let lastUid: number | null = null
  watch(
    uid,
    (id) => {
      if (lastUid !== null && id !== lastUid) reset()
      lastUid = id
    },
    { immediate: true },
  )

  return {
    // 原始数据
    activeGoal,
    paths,
    tasks,
    reviewRequired,
    weakPoints,
    masteries,
    recentEvents,
    // 状态
    loading,
    loaded,
    error,
    reviewError,
    weakError,
    goalError,
    pathsError,
    tasksError,
    contextLoading,
    // 派生
    currentPath,
    goalPath,
    currentPathPercent,
    doneTaskCount,
    todayTaskPercent,
    context,
    evidences,
    evidenceAggregate,
    behaviorDiagnoses,
    evidenceCandidate,
    strengthSummary,
    strategyLabel,
    nextAction,
    nextActionContext,
    nextActionKnowledgeId,
    nextActionKnowledgeName,
    readFlashcardSession,
    saveFlashcardSession,
    adaptivePaths,
    loadAdaptivePath,
    // 行为
    load,
    reloadReview,
    reloadWeak,
    toggleTask,
    refreshGoal,
    reset,
    // 常量导出（供空态文案复用）
    EMPTY_LEARNING_CONTEXT,
  }
})
