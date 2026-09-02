/**
 * Architecture-4：Next Action Resolver（唯一决策入口 §69/§179）
 *
 * 规则驱动（§52/§129/§131），优先级固定为：
 *   REVIEW（待复习） > TASK（今日任务） > WEAK（薄弱知识） > PATH（当前路径） > EXPLORE（兜底）
 *
 * 循环保护（§260-264）：最近刚完成的动作类型跳过，避免无限循环推荐同一个 App。
 * 纯函数、无副作用、可单测（§246）。
 */
import type {
  LearningActionContext,
  LearningActionType,
  NextLearningAction,
  LastCompletedAction,
} from './types'
import type { KnowledgeMasteryVO, LearningPathVO } from '@/api/types'
import type { AppDefinition } from '@/apps/types'

/** 循环保护时间窗：最近 30 分钟内完成的动作类型不再重复推荐（§263：不做复杂状态） */
const BLOCK_WINDOW_MS = 30 * 60 * 1000
const LAST_COMPLETED_KEY = 'knowflow:last-completed'

/** sessionStorage 不可用（隐私模式 / 单测 node 环境）时的内存降级 */
const memoryFallback = new Map<string, string>()

function storageGet(key: string): string | null {
  try {
    const v = sessionStorage.getItem(key)
    if (v != null) return v
  } catch {
    // 降级到内存
  }
  return memoryFallback.get(key) ?? null
}

function storageSet(key: string, value: string): void {
  try {
    sessionStorage.setItem(key, value)
    return
  } catch {
    // 降级到内存
  }
  memoryFallback.set(key, value)
}

/** 记录最近完成的学习动作（会话级，§264：仅内存/sessionStorage，不新增表） */
export function setLastCompleted(type: LearningActionType): void {
  storageSet(LAST_COMPLETED_KEY, JSON.stringify({ type, at: Date.now() }))
}

/** 读取最近完成动作（时间窗外返回 null） */
export function getLastCompleted(): LastCompletedAction | null {
  const raw = storageGet(LAST_COMPLETED_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as LastCompletedAction
    if (!parsed || typeof parsed.type !== 'string' || typeof parsed.at !== 'number') return null
    if (Date.now() - parsed.at > BLOCK_WINDOW_MS) return null
    return parsed
  } catch {
    return null
  }
}

/** 薄弱知识点可用的「重新学习」App 优先级（必须是已安装且启用，§130） */
const WEAK_APP_ORDER: string[] = ['feynman', 'flashcard']

/**
 * 解析下一步动作（§71/§177）。
 * @param ctx 学习上下文（页面已加载的真实数据，§72）
 * @param skip 额外跳过的动作类型（页面可补充，如刚完成 Quiz 后不允许再推荐 Quiz）
 */
export function resolveNextAction(
  ctx: LearningActionContext,
  skip: LearningActionType[] = [],
): NextLearningAction {
  // 循环保护（§260-264）：页面显式传入的 lastCompleted 优先，否则读会话级记录
  const lastType = ctx.lastCompleted?.type ?? getLastCompleted()?.type
  const skipped = new Set<LearningActionType>([...skip, ...(lastType ? [lastType] : [])])

  // 1. REVIEW：有待复习知识点 → 开始复习（§54-55/§130）
  if (!skipped.has('REVIEW') && ctx.reviewRequired.length > 0) {
    return buildReviewAction(ctx.reviewRequired)
  }

  // 2. TASK：有未完成今日任务 → 继续任务（§57-58/§248）
  if (!skipped.has('TASK')) {
    const pending = ctx.tasks.find((t) => t.status !== 1)
    if (pending) {
      return {
        type: 'TASK',
        title: `完成今日任务「${pending.title}」`,
        reason: '今日计划还未完成，先把它推进下去',
        route: '/learning/center',
      }
    }
  }

  // 3. WEAK：有薄弱知识点 → 重新学习（§56/§130/§249）
  if (!skipped.has('KNOWLEDGE') && !skipped.has('MINI_APP') && ctx.weakPoints.length > 0) {
    const weakest = [...ctx.weakPoints].sort((a, b) => a.masteryScore - b.masteryScore)[0]
    const appAction = buildWeakAppAction(weakest, ctx.installedApps ?? [], skipped)
    if (appAction) return appAction
    // 没有合适的 App 时降级为知识详情页（真实页面，§56）
    return {
      type: 'KNOWLEDGE',
      title: `重新学习「${weakest.name || '这个知识点'}」`,
      reason: weakReason(weakest),
      route: `/learning/knowledge-mastery/${weakest.knowledgeId}`,
      knowledgeId: weakest.knowledgeId,
      knowledgeName: weakest.name || undefined,
    }
  }

  // 4. PATH：有进行中的学习路径且未完成 → 继续路径（§57）
  if (!skipped.has('LEARNING_PATH') && isPathInProgress(ctx.currentPath)) {
    const path = ctx.currentPath as NonNullable<LearningActionContext['currentPath']>
    return {
      type: 'LEARNING_PATH',
      title: `继续学习「${path.title}」`,
      reason: `当前进度 ${Math.round(path.progress ?? 0)}%，接着上次继续`,
      route: `/learning/path/${path.id}`,
    }
  }

  // 5. EXPLORE：兜底（§79/§250）
  return {
    type: 'EXPLORE',
    title: '继续探索知识',
    reason: '今天的学习安排已完成，可以看看新的知识或路径',
    route: '/learning/paths',
  }
}

/** 构建「开始复习」动作（§55/§122：标题带知识点名，原因说明已进入复习队列） */
function buildReviewAction(reviewRequired: KnowledgeMasteryVO[]): NextLearningAction {
  const first = reviewRequired[0]
  const name = first.name || '知识点'
  const count = reviewRequired.length
  return {
    type: 'REVIEW',
    title: count > 1 ? `复习「${name}」等 ${count} 个知识点` : `复习「${name}」`,
    reason: count > 1 ? `${count} 个知识点已进入复习队列，需要巩固` : '该知识点已进入复习队列，需要巩固',
    route: '/learning/review',
    knowledgeId: first.knowledgeId,
  }
}

/** 薄弱知识点 → 优先推荐技能 App（费曼/闪卡），返回 null 表示无可用 App（降级知识详情） */
function buildWeakAppAction(
  weak: KnowledgeMasteryVO,
  installedApps: AppDefinition[],
  skipped: Set<LearningActionType>,
): NextLearningAction | null {
  const installed = new Set(installedApps.map((a) => a.key))
  for (const key of WEAK_APP_ORDER) {
    const actionType: LearningActionType = key === 'feynman' ? 'MINI_APP' : 'FLASHCARD'
    if (skipped.has(actionType)) continue
    if (!installed.has(key)) continue
    const app = installedApps.find((a) => a.key === key)
    if (!app) continue
    return {
      type: actionType,
      title: key === 'feynman' ? `用费曼法解释「${weak.name || '这个知识点'}」` : `用闪卡巩固「${weak.name || '这个知识点'}」`,
      reason: weakReason(weak),
      route: app.route,
      appKey: key,
      knowledgeId: weak.knowledgeId,
      knowledgeName: weak.name || undefined,
    }
  }
  return null
}

/** 薄弱原因（真实数据：掌握度 + 薄弱类型翻译，§61） */
function weakReason(weak: KnowledgeMasteryVO): string {
  const score = Math.round(weak.masteryScore)
  const types = weak.weaknessTypes || []
  const label = types.length > 0 ? types[0] : ''
  const detail =
    label === 'ERROR_RATE' || label === 'HIGH_ERROR_RATE'
      ? '近期错误较多'
      : label === 'NO_REVIEW' || label === 'REVIEW_GAP'
        ? '最近较少复习'
        : label === 'MASTERY_DECAY' || label === 'MASTERY_DROP'
          ? '掌握度有所下降'
          : ''
  return detail ? `当前掌握度 ${score}%，${detail}，建议重新学习` : `当前掌握度 ${score}%，建议重新学习`
}

/** 路径是否「进行中」：已报名且进度 0 < p < 100（§57） */
export function isPathInProgress(path: LearningPathVO | null | undefined): boolean {
  if (!path) return false
  if (!path.enrolled) return false
  const progress = path.progress ?? 0
  return progress > 0 && progress < 100
}
