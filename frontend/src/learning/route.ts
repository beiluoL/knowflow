/**
 * Architecture-4：Workflow 路由解析（本地白名单 §82-84）
 *
 * Next Action → Route 只能来自两类来源：
 *   1. 核心路由固定映射（REVIEW / QUIZ / FLASHCARD / KNOWLEDGE / PATH / TASK / EXPLORE）；
 *   2. Mini App 的 route（经本地静态 Registry 解析，§85）。
 * 禁止返回任意远程 URL / javascript:（§83/§86）。
 */
import { getAppByKey } from '@/apps/registry'
import type { LearningActionType, NextLearningAction } from './types'

/** 核心路由白名单（固定映射） */
export const ACTION_ROUTES: Record<Exclude<LearningActionType, 'MINI_APP'>, string> = {
  REVIEW: '/learning/review',
  QUIZ: '/learning/quiz',
  FLASHCARD: '/learning/flashcards',
  KNOWLEDGE: '/learning/mastery',
  LEARNING_PATH: '/learning/path/:id',
  TASK: '/learning/center',
  EXPLORE: '/learning/paths',
  EVIDENCE_PRACTICE: '/learning/center',
}

/**
 * Architecture-4.1 §25：Evidence Strategy 允许落地的核心练习页（本地静态白名单）。
 * 与 Mini App 路由同理（§82-84）：只接受白名单内的固定路径，禁止任意远程 URL / javascript:。
 */
export const EVIDENCE_PRACTICE_ROUTES: string[] = [
  '/learning/flashcards',
  '/learning/quiz',
  '/learning/review',
  '/learning/scenario',
  '/learning/feynman',
  '/learning/first-principles',
  '/learning/writing',
  '/learning/center',
]

/** Registered resource pages only; numeric IDs are canonical safe integers. */
export function resourceEntryPath(path: string): string | null {
  const match = /^(\/learning\/(?:chapter|knowledge-mastery|code-practice)|\/doc)\/([1-9]\d*)$/.exec(path)
  return match && Number.isSafeInteger(Number(match[2])) ? `${match[1]}/:id` : null
}

/** 动作 → 路由（白名单校验后返回；非法时回退到学习中心） */
export function resolveActionRoute(action: NextLearningAction): string {
  if (action.type === 'MINI_APP') {
    // Mini App 路由必须来自本地 Registry（§85），未知 appKey 一律回退
    const app = action.appKey ? getAppByKey(action.appKey) : undefined
    return app?.route ?? '/learning/center'
  }
  if (action.type === 'EVIDENCE_PRACTICE') {
    // Evidence 候选的路由必须落在本地白名单内，否则回退学习中心（§83）
    // 4.2：允许携带知识点深链 query（query 不影响白名单判定）
    const base = action.route.split('?')[0]
    return (EVIDENCE_PRACTICE_ROUTES.includes(base) || resourceEntryPath(base)) ? action.route : '/learning/center'
  }
  if (action.type === 'KNOWLEDGE' && action.knowledgeId != null) {
    return `/learning/knowledge-mastery/${action.knowledgeId}`
  }
  if (action.type === 'LEARNING_PATH') {
    const id = /\/learning\/path\/(\d+)/.exec(action.route)?.[1]
    if (id) return `/learning/path/${id}`
    return ACTION_ROUTES.LEARNING_PATH
  }
  return ACTION_ROUTES[action.type] ?? '/learning/center'
}

/**
 * Architecture-4.2 §15-§18：支持知识点深链的核心练习页（本地静态白名单）。
 * 只有这些页面实现了 `?knowledgeId=` 上下文；其余页面一律不加 query（不制造假深链）。
 */
export const KNOWLEDGE_DEEP_LINK_ROUTES: string[] = [
  '/learning/scenario',
  '/learning/feynman',
  '/learning/first-principles',
]

/**
 * §12 闪卡兼容方式（不伪造）：现有闪卡接口不支持按知识点过滤，
 * 但支持按章节过滤（/learning/flashcards?chapterId=N），而「当前学习节点」本身就是一个章节，
 * 因此用 chapterId = learningNodeId 实现「优先练当前节点的闪卡」这一真实能力。
 */
export const CHAPTER_FILTER_ROUTES: string[] = ['/learning/flashcards']

/** 纯函数：给路由追加真实上下文 query（无上下文则原样返回） */
export function appendKnowledgeQuery(
  path: string,
  knowledgeId?: number | null,
  knowledgeName?: string | null,
): string {
  if (knowledgeId == null || !KNOWLEDGE_DEEP_LINK_ROUTES.includes(path)) return path
  const q = new URLSearchParams()
  q.set('knowledgeId', String(knowledgeId))
  if (knowledgeName) q.set('knowledgeName', knowledgeName)
  return `${path}?${q.toString()}`
}

/** 纯函数：闪卡按当前章节过滤（§12 兼容方式） */
export function appendChapterQuery(path: string, chapterId?: number | null): string {
  if (chapterId == null || !CHAPTER_FILTER_ROUTES.includes(path)) return path
  return `${path}?chapterId=${chapterId}`
}

/** 动作 → 主 CTA（§69：Home / LearningCenter 共用同一优先级来源） */
export interface ActionCta {
  text: string
  icon: string
  path: string
}

/**
 * Mini App 上下文 query 构造（knowledgeId / knowledgeName 透传，§229 / §47-48）。
 * 纯函数、可单测：从 NextAction 取出真实知识点上下文，供路由 query 携带，
 * 使 Mini App 打开后能通过 route.query 显示「正在学习：X」（不伪造、不新增全局状态）。
 */
export function buildMiniAppContextQuery(action: NextLearningAction): Record<string, string> {
  const q: Record<string, string> = {}
  if (action.knowledgeId != null) q.knowledgeId = String(action.knowledgeId)
  if (action.knowledgeName) q.knowledgeName = action.knowledgeName
  return q
}

export function actionToCta(action: NextLearningAction): ActionCta {
  switch (action.type) {
    case 'REVIEW':
      return { text: '开始复习', icon: 'rotate-ccw', path: resolveActionRoute(action) }
    case 'TASK':
      return { text: '开始今日任务', icon: 'list-checks', path: resolveActionRoute(action) }
    case 'KNOWLEDGE':
      return { text: '重新学习', icon: 'book-open', path: resolveActionRoute(action) }
    case 'FLASHCARD':
      return { text: '闪卡巩固', icon: 'layers', path: resolveActionRoute(action) }
    case 'MINI_APP': {
      const app = action.appKey ? getAppByKey(action.appKey) : undefined
      return { text: `打开${app?.name ?? '应用'}`, icon: 'play', path: resolveActionRoute(action) }
    }
    case 'EVIDENCE_PRACTICE': {
      // §22-24：文案优先用 Strategy 名称（如「场景应用」「费曼表达」），由候选动作自带 title
      const label = action.behaviorDiagnosis ? action.title : '开始练习'
      return { text: label, icon: 'zap', path: resolveActionRoute(action) }
    }
    case 'LEARNING_PATH':
      return { text: '继续学习', icon: 'play', path: resolveActionRoute(action) }
    case 'QUIZ':
      return { text: '开始练习', icon: 'check-circle', path: resolveActionRoute(action) }
    default:
      return { text: '浏览学习路径', icon: 'route', path: resolveActionRoute(action) }
  }
}
