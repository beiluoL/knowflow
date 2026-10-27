/**
 * Interaction OS（UI/UX 5.1）§七 / §八：统一 Learning Action Contract（纯函数、可单测）
 *
 * ── 问题（扫描真实代码得出）─────────────────────────────────────────────
 * 1. `NextActionCard.doAction()` 对非 MINI_APP 动作直接 `router.push(resolveActionRoute(action))`，
 *    而 `ACTION_ROUTES` 返回的是**裸路由**：
 *      QUIZ      → '/learning/quiz'        （丢掉 knowledgeId）
 *      FLASHCARD → '/learning/flashcards'  （丢掉 knowledgeId / chapterId）
 *    于是首页明明写着「用闪卡巩固「JVM G1 GC」」，点进去却是一屏通用闪卡，
 *    用户必须**重新挑一次知识点** —— 这正是 §八 禁止的「点击 NextAction 后重新从零选择知识点」。
 *    （只有 EVIDENCE_PRACTICE 恰好把 query 嵌在 action.route 里，所以只有它没丢。）
 *
 * 2. 各入口能消费的上下文参数并不一致，而代码里没有一处记录「谁消费什么」，
 *    导致既可能漏传（丢上下文），也可能乱传（伪造深链）。
 *
 * ── 本模块的职责 ────────────────────────────────────────────────────────
 * 把「动作 → 入口路由」的上下文传递收敛为**唯一实现**：
 *   `stampActionContext`      动作对象上盖章完整上下文（knowledgeId/name/goalId/learningNodeId）
 *   `resolveContextualRoute`  动作 → 带上下文 query 的真实路由（只带该页面真正消费的参数）
 *
 * ── 铁律 ────────────────────────────────────────────────────────────────
 * 1. 只追加**真实消费**的参数（`LEARNING_ENTRY_CONTEXT` 由真实页面代码核对得出），
 *    不制造假深链（沿用 `learning/route.ts` 既有纪律）。
 * 2. 白名单校验仍在 `resolveActionRoute` 内完成，本模块不绕过（§83-86）。
 * 3. 不新增全局状态、不新增表、不引入第二套上下文系统（§三十一）。
 */
import { resolveActionRoute, resourceEntryPath } from './route'
import type { NextLearningAction } from './types'

/** 学习入口可携带的上下文参数（query key） */
export type EntryContextParam =
  | 'knowledgeId'
  | 'knowledgeName'
  | 'chapterId'
  | 'goalId'
  | 'learningNodeId'

/** 完整学习上下文（来自 NextAction / 学习 store 的单一事实源） */
export interface LearningEntryContext {
  knowledgeId?: number | null
  knowledgeName?: string | null
  /** 当前学习目标 id（Architecture-4.0） */
  goalId?: number | null
  /** 当前 DAG 学习节点 id（章节 id，Architecture-4.2） */
  learningNodeId?: number | null
}

/**
 * 每个学习入口**真正消费**的上下文参数（真实代码核对结果，注释即证据）。
 *
 * 核对依据：
 *  - `/learning/flashcards`        FlashCardsHub.vue:842-845 读 knowledgeId / knowledgeName / chapterId / pathId
 *  - `/learning/quiz`              SmartQuiz.vue:467         读 knowledgeId / knowledgeName
 *  - `/learning/scenario`          ScenarioLab.vue:270,379   读 knowledgeId / knowledgeName
 *  - `/learning/feynman`           FeynmanLab.vue:261,295    读 knowledgeId / knowledgeName
 *  - `/learning/first-principles`  FirstPrinciplesLab.vue:270,279 读 knowledgeId / knowledgeName
 *  - `/learning/writing`           SmartWriting.vue`contextKnowledgeName` → MiniAppShell `knowledge-name`（5.1 补上消费）
 *  - `/learning/review`            ReviewPlan.vue`contextKnowledgeName` 上下文条 + `pushWithContext()` 转发到闪卡（5.1 补上消费）
 *
 * 注意：`goalId` / `learningNodeId` **当前没有任何页面读取**，因此**不写进 query**
 * （写进去只会是一条永不生效的假深链）。它们仍然挂在动作对象上供后续阶段消费。
 * 闪卡是唯一例外：`learningNodeId` 以 `chapterId` 的形式被真实消费（后端支持按章节过滤）。
 */
export const LEARNING_ENTRY_CONTEXT: Record<string, EntryContextParam[]> = {
  '/learning/flashcards': ['knowledgeId', 'knowledgeName', 'chapterId'],
  '/learning/quiz': ['knowledgeId', 'knowledgeName'],
  '/learning/scenario': ['knowledgeId', 'knowledgeName'],
  '/learning/feynman': ['knowledgeId', 'knowledgeName'],
  '/learning/first-principles': ['knowledgeId', 'knowledgeName'],
  '/learning/writing': ['knowledgeId', 'knowledgeName'],
  '/learning/review': ['knowledgeId', 'knowledgeName'],
  // ChapterResourceLinks validates and consumes this chapter context.
  '/doc/:id': ['chapterId'],
  '/learning/code-practice/:id': ['chapterId'],
  '/learning/knowledge-mastery/:id': ['chapterId'],
}

/** 从路径中取出不含 query/hash 的部分 */
function pathOnly(route: string): string {
  return route.split('?')[0].split('#')[0]
}

function toParams(route: string): URLSearchParams {
  const q = route.indexOf('?')
  return new URLSearchParams(q >= 0 ? route.slice(q + 1) : '')
}

/**
 * 动作对象上盖章上下文（§八：NextAction 必须携带 knowledgeId / goalId / learningNodeId）。
 * 动作自带的上下文优先（更具体），缺失时才用外部上下文补齐 —— 不覆盖已有真实值。
 */
export function stampActionContext(
  action: NextLearningAction,
  ctx: LearningEntryContext,
): NextLearningAction {
  const out: NextLearningAction = { ...action }
  if (out.knowledgeId == null && ctx.knowledgeId != null) out.knowledgeId = ctx.knowledgeId
  if (!out.knowledgeName && ctx.knowledgeName) out.knowledgeName = ctx.knowledgeName
  if (out.goalId == null && ctx.goalId != null) out.goalId = ctx.goalId
  if (out.learningNodeId == null && ctx.learningNodeId != null) {
    out.learningNodeId = ctx.learningNodeId
  }
  return out
}

/**
 * 构造某入口的上下文 query（只包含 `LEARNING_ENTRY_CONTEXT` 允许的参数）。
 * 无任何可传参数时返回空对象（调用方应直接用裸路由，避免 `?` 尾巴）。
 */
export function buildEntryContextQuery(
  entryPath: string,
  ctx: LearningEntryContext,
): Record<string, string> {
  const allowed = LEARNING_ENTRY_CONTEXT[resourceEntryPath(pathOnly(entryPath)) || pathOnly(entryPath)]
  if (!allowed || allowed.length === 0) return {}

  const out: Record<string, string> = {}
  const set = (param: EntryContextParam, value: string | number | null | undefined) => {
    if (!allowed.includes(param)) return
    if (value == null || value === '') return
    out[param] = String(value)
  }

  set('knowledgeId', ctx.knowledgeId)
  set('knowledgeName', ctx.knowledgeName)
  // 闪卡：后端只支持按章节过滤，而「当前学习节点」本身就是一个章节 → learningNodeId 即 chapterId
  set('chapterId', ctx.learningNodeId)

  return out
}

/**
 * 动作 → **带学习上下文**的实际路由（§七/§八 的唯一入口）。
 *
 * 行为：
 *  1. 先用既有 `resolveActionRoute` 做白名单解析（拿到底层裸路由 / 已带 query 的路由）；
 *  2. 保留该路由**自带**的 query（如 EVIDENCE_PRACTICE 已携带的上下文），不被覆盖；
 *  3. 追加当前入口真实消费的上下文参数；
 *  4. 动作上的上下文优先于外部 ctx（动作更具体）。
 */
export function resolveContextualRoute(
  action: NextLearningAction,
  ctx: LearningEntryContext = {},
): string {
  const base = resolveActionRoute(action)
  const target = pathOnly(base)
  // Existing NextAction behavior is retained; resource context is explicit user navigation.
  if (resourceEntryPath(target) && action.type !== 'EVIDENCE_PRACTICE') return base
  if (!LEARNING_ENTRY_CONTEXT[resourceEntryPath(target) || target]) return base

  const params = toParams(base)
  const merged = stampActionContext(action, ctx)
  const extra = buildEntryContextQuery(target, {
    knowledgeId: merged.knowledgeId,
    knowledgeName: merged.knowledgeName,
    goalId: merged.goalId,
    learningNodeId: merged.learningNodeId,
  })

  Object.entries(extra).forEach(([k, v]) => {
    // 路由自带 query 优先（它是该页面在动作层的既定上下文）
    if (!params.has(k)) params.set(k, v)
  })

  const qs = params.toString()
  return qs ? `${target}?${qs}` : target
}

/**
 * Mini App 入口的上下文 query（§229 既有能力 + §八 新契约合并）。
 *
 * Mini App 的 route 也可能是核心学习页（如 `flashcard` → `/learning/flashcards`），
 * 此时必须同时带上该页真正消费的 `chapterId`，否则「用闪卡巩固 JVM GC」依旧会丢过滤条件。
 * 非学习页（如 `feynman` → `/workbench/story`）只带既有的 knowledgeId/knowledgeName。
 */
export function buildMiniAppEntryQuery(
  entryPath: string,
  action: NextLearningAction,
  ctx: LearningEntryContext = {},
): Record<string, string> {
  const merged = stampActionContext(action, ctx)
  const out: Record<string, string> = {}
  // §229 既有约定：Mini App 顶部「正在学习：X」上下文条
  if (merged.knowledgeId != null) out.knowledgeId = String(merged.knowledgeId)
  if (merged.knowledgeName) out.knowledgeName = merged.knowledgeName
  // §八 契约：该页真正消费的额外上下文（闪卡 → chapterId）
  return { ...out, ...buildEntryContextQuery(entryPath, {
    knowledgeId: merged.knowledgeId,
    knowledgeName: merged.knowledgeName,
    goalId: merged.goalId,
    learningNodeId: merged.learningNodeId,
  }) }
}

/**
 * 人类可读的上下文描述（供「为什么是这张卡 / 正在学习什么」类文案与测试断言复用）。
 * 无上下文返回 null —— 不返回「未知知识点」这类虚构内容。
 */
export function describeEntryContext(ctx: LearningEntryContext): string | null {
  if (ctx.knowledgeName) return `正在学习：${ctx.knowledgeName}`
  if (ctx.knowledgeId != null) return `正在学习：知识点 #${ctx.knowledgeId}`
  return null
}

/** Query is a hint; ChapterResourceLinks checks the authenticated chapter relation. */
export function readChapterContext(query: Record<string, unknown>): number | null {
  const value = query.chapterId
  return typeof value === 'string' && /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value)) ? Number(value) : null
}
export function resolveChapterResourceRoute(kind: 'chapter'|'knowledge'|'document'|'code', id: number, chapterId?: number | null): string {
  if (!Number.isSafeInteger(id) || id <= 0) return '/learning/center'
  const prefixes = { chapter: '/learning/chapter', knowledge: '/learning/knowledge-mastery', document: '/doc', code: '/learning/code-practice' }
  return resolveContextualRoute(stampActionContext({type: 'EVIDENCE_PRACTICE', title: '章节资源', reason: '用户选择关联资源', route: `${prefixes[kind]}/${id}`}, {learningNodeId: chapterId}))
}

/** Flashcard filters are session inputs; remount when they change to avoid stale cards/events. */
export function learningEntryViewKey(route: {path:string;query:Record<string,unknown>}): string {
  if(route.path !== '/learning/flashcards') return route.path
  return route.path + JSON.stringify(['chapterId','pathId','knowledgeId','knowledgeName'].map(key=>route.query[key]??null))
}

/** Exact resource navigation; IDs are actual consumed resource selectors, not invented knowledge context. */
export function resolvePlanResourceSelector(kind: 'QUIZ'|'FLASHCARD', id: number, action: NextLearningAction, ctx: LearningEntryContext = {}): string {
  if (!Number.isSafeInteger(id) || id <= 0) return '/learning/center'
  const target = kind === 'QUIZ' ? '/learning/quiz' : '/learning/flashcards'
  const query = new URLSearchParams({ [kind === 'QUIZ' ? 'questionId' : 'cardId']: String(id) })
  return resolveContextualRoute(stampActionContext({ ...action, type: 'EVIDENCE_PRACTICE', route: `${target}?${query}` }, ctx), ctx)
}
