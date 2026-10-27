/**
 * Architecture-4.1-§22~§24：Strategy Resolver + App Availability。
 *
 * §22 Diagnosis → Strategy（能力维度 → 学习策略，不是 Diagnosis → App 一对一硬绑）：
 *   LOW_RECALL        → ACTIVE_RECALL     （主动回忆）
 *   LOW_UNDERSTANDING → FIRST_PRINCIPLES  （回到基本原理重新推导）
 *   LOW_EXPLANATION   → FEYNMAN           （讲出来）
 *   LOW_APPLICATION   → APPLICATION       （场景 / 编程，§23 一个策略多个 App）
 *   LOW_EXPRESSION    → WRITING_REVIEW    （书面表达）
 *   LOW_CONSISTENCY   → SPACED_REVIEW     （把节奏稳定下来）
 *
 * §23 一个 Strategy 可以落到多个 App，不一对一绑定。
 * §24 解析后必须再过一遍 UserApp / App Registry 可用性，全部不可用必须 fallback，
 *     绝不允许 NextAction 指向一个被停用的 App。
 */
import type { AppDefinition } from '@/apps/types'
import { getAppByKey } from '@/apps/registry'
import { appendChapterQuery, appendKnowledgeQuery } from '../route'
import type { BehaviorDiagnosis, BehaviorDiagnosisType } from './diagnosis'

/** 学习策略类型（§22） */
export type EvidenceStrategyType =
  | 'ACTIVE_RECALL'
  | 'FIRST_PRINCIPLES'
  | 'FEYNMAN'
  | 'APPLICATION'
  | 'WRITING_REVIEW'
  | 'SPACED_REVIEW'
  | 'CONTINUE'

/** §22 Diagnosis → Strategy */
export function resolveEvidenceStrategy(d: BehaviorDiagnosis): EvidenceStrategyType {
  switch (d.type) {
    case 'LOW_RECALL':
      return 'ACTIVE_RECALL'
    case 'LOW_UNDERSTANDING':
      return 'FIRST_PRINCIPLES'
    case 'LOW_EXPLANATION':
      return 'FEYNMAN'
    case 'LOW_APPLICATION':
      return 'APPLICATION'
    case 'LOW_EXPRESSION':
      return 'WRITING_REVIEW'
    case 'LOW_CONSISTENCY':
      return 'SPACED_REVIEW'
  }
}

/** 直接按诊断类型解析（便捷重载，纯函数） */
export function strategyOfDiagnosisType(type: BehaviorDiagnosisType): EvidenceStrategyType {
  return resolveEvidenceStrategy({ type } as BehaviorDiagnosis)
}

/**
 * 用户可读的策略说明（真实、具体，禁止「AI 判定…」）。
 * 注：与 intelligence/strategy.ts 的同名函数语义不同（那个是「状态诊断 → 建议」，
 * 这个是「能力诊断 → 策略」），故加 Evidence 前缀避免导出冲突。
 */
export function evidenceStrategyAdvice(s: EvidenceStrategyType): string {
  switch (s) {
    case 'ACTIVE_RECALL':
      return '用闪卡或答题做主动回忆，把记住的东西反复取出来用。'
    case 'FIRST_PRINCIPLES':
      return '把结论拆回基本事实与约束，从底层机制重新推导一遍。'
    case 'FEYNMAN':
      return '把这个知识点讲给「完全不懂的人」听，卡住的地方就是还没懂的地方。'
    case 'APPLICATION':
      return '到真实场景或代码里用一次，知识只有被用出来才算掌握。'
    case 'WRITING_REVIEW':
      return '把想法写成一段结构完整的文字，训练表达的组织能力。'
    case 'SPACED_REVIEW':
      return '把练习节奏固定下来，短而频繁比一次堆量更有效。'
    case 'CONTINUE':
      return '当前各项能力没有明显短板，按现在的节奏继续。'
  }
}

/** 策略中文名（状态卡 / CTA 文案用） */
export const STRATEGY_LABEL: Record<EvidenceStrategyType, string> = {
  ACTIVE_RECALL: '主动回忆',
  FIRST_PRINCIPLES: '第一性原理',
  FEYNMAN: '费曼表达',
  APPLICATION: '场景应用',
  WRITING_REVIEW: '写作复盘',
  SPACED_REVIEW: '间隔复习',
  CONTINUE: '继续学习',
}

/* ============================ §23/§24 策略 → 可用目标 ============================ */

/** App 型目标（必须过 UserApp / Registry 可用性） */
export interface AppTarget {
  kind: 'APP'
  appKey: string
  label: string
}

/** 核心页面型目标（学习中心内建能力，不依赖 App 启用状态） */
export interface RouteTarget {
  kind: 'ROUTE'
  path: string
  label: string
}

export type StrategyTarget = AppTarget | RouteTarget

/**
 * §23 策略 → 候选目标（按优先级排列）。
 * 核心页面排在 App 之前：它们不依赖用户是否安装，永远可用（§24 少一层不确定性）。
 */
export const STRATEGY_TARGETS: Record<EvidenceStrategyType, StrategyTarget[]> = {
  ACTIVE_RECALL: [
    { kind: 'ROUTE', path: '/learning/flashcards', label: '闪卡复习' },
    { kind: 'APP', appKey: 'flashcard', label: '闪卡' },
    { kind: 'APP', appKey: 'quiz', label: '练习' },
  ],
  FIRST_PRINCIPLES: [{ kind: 'ROUTE', path: '/learning/first-principles', label: '第一性原理' }],
  FEYNMAN: [
    { kind: 'ROUTE', path: '/learning/feynman', label: '费曼表达' },
    { kind: 'APP', appKey: 'feynman', label: '费曼' },
  ],
  APPLICATION: [
    { kind: 'ROUTE', path: '/learning/scenario', label: '场景练习' },
    { kind: 'APP', appKey: 'coding', label: '代码练习' },
  ],
  WRITING_REVIEW: [
    { kind: 'ROUTE', path: '/learning/writing', label: '智能写作' },
    { kind: 'APP', appKey: 'ai-writing', label: '智能写作' },
  ],
  SPACED_REVIEW: [
    { kind: 'ROUTE', path: '/learning/review', label: '复习计划' },
    { kind: 'APP', appKey: 'flashcard', label: '闪卡' },
  ],
  CONTINUE: [{ kind: 'ROUTE', path: '/learning/center', label: '学习中心' }],
}

/** §24 全不可用时的最终兜底：学习中心（核心页面，永远存在） */
export const FALLBACK_TARGET: RouteTarget = { kind: 'ROUTE', path: '/learning/center', label: '学习中心' }

/**
 * Architecture-4.2 §15-§18：目标落地时的真实上下文（知识点 / 当前学习节点）。
 * 只影响「打开哪一个具体知识点」，不影响策略选择本身（§29 策略针对 Context 选 App，
 * §32/§33 Context 不提升优先级）。
 */
export interface TargetContext {
  knowledgeId?: number | null
  knowledgeName?: string | null
  learningNodeId?: number | null
}

/**
 * §24 过滤出「真正可用」的目标：
 *   - ROUTE：核心页面，直接可用；
 *   - APP：必须在传入的已安装 App 列表中（UserApp 启用态 + Registry 白名单双重校验）。
 * 全部不可用时返回 [FALLBACK_TARGET]，绝不返回 disabled App。
 */
export function availableTargets(
  strategy: EvidenceStrategyType,
  installedApps: AppDefinition[] = [],
): StrategyTarget[] {
  const enabled = new Set(installedApps.map((a) => a.key))
  const out = STRATEGY_TARGETS[strategy].filter(
    (t) => t.kind === 'ROUTE' || (enabled.has(t.appKey) && !!getAppByKey(t.appKey)),
  )
  return out.length > 0 ? out : [FALLBACK_TARGET]
}

/** 取第一个可用目标（供 CTA 使用） */
export function firstAvailableTarget(
  strategy: EvidenceStrategyType,
  installedApps: AppDefinition[] = [],
): StrategyTarget {
  return availableTargets(strategy, installedApps)[0]
}

/**
 * 目标 → 路由（APP 走 Registry 白名单解析，未知 key 回落学习中心）。
 *
 * Architecture-4.2 §15：带知识点上下文时，路由追加真实 query ——
 *   /learning/scenario?knowledgeId=123（场景只展示该知识点）
 *   /learning/feynman?knowledgeId=123（顶部显示当前知识）
 *   /learning/flashcards?chapterId=12（§12 兼容：闪卡按当前章节过滤）
 * 不新增第二套路由体系，query 一律由本地白名单决定（§15）。
 */
export function targetRoute(t: StrategyTarget, ctx: TargetContext = {}): string {
  const base = t.kind === 'ROUTE' ? t.path : getAppByKey(t.appKey)?.route ?? FALLBACK_TARGET.path
  const withKnowledge = appendKnowledgeQuery(base, ctx.knowledgeId, ctx.knowledgeName)
  if (withKnowledge !== base) return withKnowledge
  return appendChapterQuery(base, ctx.learningNodeId)
}
