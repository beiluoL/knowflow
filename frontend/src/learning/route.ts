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
}

/** 动作 → 路由（白名单校验后返回；非法时回退到学习中心） */
export function resolveActionRoute(action: NextLearningAction): string {
  if (action.type === 'MINI_APP') {
    // Mini App 路由必须来自本地 Registry（§85），未知 appKey 一律回退
    const app = action.appKey ? getAppByKey(action.appKey) : undefined
    return app?.route ?? '/learning/center'
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
    case 'LEARNING_PATH':
      return { text: '继续学习', icon: 'play', path: resolveActionRoute(action) }
    case 'QUIZ':
      return { text: '开始练习', icon: 'icon-kaoshi', path: resolveActionRoute(action) }
    default:
      return { text: '浏览学习路径', icon: 'route', path: resolveActionRoute(action) }
  }
}
