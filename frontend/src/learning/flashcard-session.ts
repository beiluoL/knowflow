/**
 * 闪卡学习会话（Flashcard Session）——纯函数层。
 *
 * ── 为什么是纯函数 + 零新增状态（§三十一）──
 * 本模块**不新建任何学习引擎**：不引入 FlashcardMastery / FlashcardEvidence /
 * FlashcardReviewState / FlashcardNextAction。它只做三件事：
 *   1. 把后端 FlashcardVO 映射为可排序的会话卡片（保留 knowledgeId 上下文等真实字段）；
 *   2. 按「真实数据」确定性推导队列顺序与「为什么是这张卡」徽章；
 *   3. 三档自评 → SM-2 quality 的收敛映射 + 会话结果汇总。
 *
 * 复习调度、事件写入、证据推导全部复用既有链路：
 *   POST /learning/flashcards/{id}/review（SrsService，SM-2）
 *     → LearningEvent(FLASHCARD_REVIEWED) → Evidence(RECALL) → Mastery / NextAction
 * 本模块**不写任何状态**，也不参与评分。
 *
 * ── 诚实边界 ──
 *   只使用后端真实返回的字段；取不到就不产出（返回 null 或省略徽章），绝不猜测。
 *   例如：闪卡表没有 knowledge_id / mastered_level 列，因此本模块**不会**凭空
 *   给卡片编一个知识点；知识点只能来自调用方确知的上下文（route query / 章节映射）。
 */
import type { FlashcardVO } from '@/api/types'

/** 三档自评（用户只需回答「你记得吗」） */
export type FlashcardRating = 'unknown' | 'familiar' | 'mastered'

/**
 * 三档自评 → SM-2 quality（0~5）。
 *
 * 与后端 `LearningServiceImpl.FLASHCARD_PASS_QUALITY = 3` 的语义严格对齐：
 *   unknown=1  < 3 → 视为遗忘（lapsed，间隔重置）
 *   familiar=3 = 3 → 及格边界（有印象）
 *   mastered=5     → 完全掌握
 * 前端**不做任何 SM-2 计算**，只负责把三档语义映射为既有接口的入参。
 */
export const RATING_QUALITY: Record<FlashcardRating, number> = {
  unknown: 1,
  familiar: 3,
  mastered: 5,
}

/** 自评 → quality（导出函数以便测试与复用，禁止在页面里重写映射） */
export function ratingToQuality(rating: FlashcardRating): number {
  return RATING_QUALITY[rating]
}

/** quality → 自评（反向映射，供测试与展示对齐使用） */
export function qualityToRating(quality: number): FlashcardRating {
  if (quality >= 5) return 'mastered'
  if (quality >= 3) return 'familiar'
  return 'unknown'
}

/** 三档自评的中文标签（展示与提示统一文案） */
export const RATING_LABEL: Record<FlashcardRating, string> = {
  unknown: '不会',
  familiar: '模糊',
  mastered: '记得',
}

/** 会话队列优先级（§十六：数字越小越先出现） */
export type QueueRank = 1 | 2 | 3 | 4 | 5

/** 「为什么是这张卡」徽章（§十五：轻量，不做大面积展示） */
export interface QueueBadge {
  label: string
  kind: 'next' | 'due' | 'weak' | 'path' | 'node' | 'new'
}

/**
 * 会话卡片：在 FlashcardVO 的最小投影上补充**真实存在**的排序字段。
 * 全部字段都来自后端返回或调用方确知的上下文，没有一个是前端编造的。
 */
export interface SessionCard {
  id: number
  question: string
  answer: string
  category: string
  difficulty: number
  /** 归属章节（可为 null：闪卡可只挂在路径上） */
  chapterId: number | null
  /** 归属学习路径（可为 null） */
  pathId: number | null
  /** 来源文档（可为 null） */
  docId: number | null
  /** 来源文档标题（后端联表返回时可读） */
  docTitle: string | null
  /** 来源：MANUAL / AI_DOC / AI_KB / IMPORT */
  sourceType: string | null
  /** 自定义标签 */
  tags: string[]
  /** 下次应复习时间（ISO；后端为 null 表示尚无 SRS 排期） */
  nextReviewTime: string | null
  /** 已复习次数（0 = 新卡） */
  reviewCount: number
  /** 确定性优先级 */
  rank: QueueRank
  /** 「为什么是这张卡」 */
  badges: QueueBadge[]
}

/** 队列上下文：全部可选，缺哪一项就少一个排序依据（不伪造） */
export interface QueueContext {
  /** NextAction / 深链指定的真实知识点 ID（来自 route.query.knowledgeId） */
  knowledgeId?: number | null
  /** 当前学习节点章节 ID（来自 route.query.chapterId / NextAction，对应后端 chapterId 过滤） */
  chapterId?: number | null
  /** 当前学习路径 ID */
  pathId?: number | null
  /** 掌握度判定为薄弱的知识点 ID 集合（真实 weakPoints 数据） */
  weakKnowledgeIds?: number[] | null
  /**
   * 章节 → 知识点映射（真实 chapterKnowledge 端点结果）。
   * 仅有该映射时才能把「卡片所属章节」与「薄弱知识点」关联起来；
   * 缺失时**不产出**薄弱徽章（不猜）。
   */
  knowledgeByChapter?: Record<number, number[]> | null
  /** 注入当前时间以保证排序与「待复习」判定可测（默认 new Date()） */
  now?: Date
}

/** 卡片是否到期需要复习（真实依据：后端 nextReviewTime 已过） */
export function isDue(card: Pick<SessionCard, 'nextReviewTime'>, now: Date = new Date()): boolean {
  if (!card.nextReviewTime) return false
  const t = new Date(card.nextReviewTime).getTime()
  return Number.isFinite(t) && t <= now.getTime()
}

/** 卡片是否属于指定章节 */
function inChapter(card: SessionCard, chapterId?: number | null): boolean {
  return chapterId != null && card.chapterId === chapterId
}

/**
 * 单卡优先级（§十六：NextAction 指定知识点 > 待复习 > 当前学习计划 > 其他新卡）。
 *
 * 判定全部基于真实字段：
 *   rank 1 「NextAction 推荐」：调用方确知知识点，且该卡位于同一学习节点章节
 *   rank 2 「待复习」          ：后端 nextReviewTime 已到期
 *   rank 3 「薄弱知识」        ：章节→知识点映射中命中薄弱知识点集合
 *   rank 4 「当前路径」        ：卡片 pathId 与当前学习路径一致
 *   rank 5  其他               ：新卡 / 无归属
 */
export function cardRank(card: SessionCard, ctx: QueueContext = {}): QueueRank {
  const now = ctx.now ?? new Date()
  if (ctx.knowledgeId != null && inChapter(card, ctx.chapterId)) return 1
  if (isDue(card, now)) return 2
  if (isWeakCard(card, ctx)) return 3
  if (ctx.pathId != null && card.pathId === ctx.pathId) return 4
  return 5
}

/** 卡片是否落在「薄弱知识点」上（仅在存在真实章节→知识点映射时才判定） */
export function isWeakCard(card: SessionCard, ctx: QueueContext = {}): boolean {
  const weak = ctx.weakKnowledgeIds
  const map = ctx.knowledgeByChapter
  if (!weak || weak.length === 0 || !map || card.chapterId == null) return false
  const ids = map[card.chapterId]
  if (!ids || ids.length === 0) return false
  return ids.some((id) => weak.includes(id))
}

/** 「为什么是这张卡」徽章（最多 2 条，避免徽章墙；§十五 要求轻量） */
export function cardBadges(card: SessionCard, ctx: QueueContext = {}): QueueBadge[] {
  const out: QueueBadge[] = []
  if (ctx.knowledgeId != null && inChapter(card, ctx.chapterId)) {
    out.push({ label: 'NextAction 推荐', kind: 'next' })
  }
  if (isDue(card, ctx.now ?? new Date())) out.push({ label: '待复习', kind: 'due' })
  if (isWeakCard(card, ctx)) out.push({ label: '薄弱知识', kind: 'weak' })
  if (out.length < 2 && ctx.pathId != null && card.pathId === ctx.pathId) {
    out.push({ label: '当前路径', kind: 'path' })
  }
  if (out.length < 2 && card.reviewCount === 0) out.push({ label: '新卡', kind: 'new' })
  return out.slice(0, 2)
}

/** 队列中「待复习」的卡片数（真实到期计数，不含「学过的卡」这类模糊口径） */
export function dueCount(cards: SessionCard[], now: Date = new Date()): number {
  return cards.filter((c) => isDue(c, now)).length
}

/** FlashcardVO → SessionCard（只投影与复制真实字段，不编造） */
export function toSessionCard(vo: FlashcardVO): SessionCard {
  return {
    id: vo.id,
    question: vo.front || '',
    answer: vo.back || '',
    category: vo.category || '通用',
    difficulty: vo.difficulty || 1,
    chapterId: vo.chapterId ?? null,
    pathId: vo.pathId ?? null,
    docId: vo.docId ?? null,
    docTitle: vo.docTitle ?? null,
    sourceType: vo.sourceType ?? null,
    tags: splitTags(vo.tags),
    nextReviewTime: vo.nextReviewTime ?? null,
    reviewCount: vo.reviewCount ?? 0,
    rank: 5,
    badges: [],
  }
}

/** 逗号 / 中文逗号分隔的标签串 → 去空数组 */
export function splitTags(raw?: string | null): string[] {
  if (!raw) return []
  return raw
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean)
}

/**
 * 构建确定性学习队列（§十六：必须先按真实优先级排序，再交给会话逐张消费）。
 *
 * 排序规则（全确定性，反复调用结果一致）：
 *   1. rank 升序（NextAction 推荐 → 待复习 → 薄弱 → 当前路径 → 其他）
 *   2. 同 rank 内：到期时间早的优先（nextReviewTime 为 null 视为最晚）
 *   3. 再相同：id 升序（稳定，可测）
 */
export function sortQueue(cards: SessionCard[], ctx: QueueContext = {}): SessionCard[] {
  const decorated = cards.map((c) => ({
    card: { ...c, rank: cardRank(c, ctx), badges: cardBadges(c, ctx) },
  }))
  decorated.sort((a, b) => {
    if (a.card.rank !== b.card.rank) return a.card.rank - b.card.rank
    const ta = a.card.nextReviewTime ? new Date(a.card.nextReviewTime).getTime() : Number.POSITIVE_INFINITY
    const tb = b.card.nextReviewTime ? new Date(b.card.nextReviewTime).getTime() : Number.POSITIVE_INFINITY
    const va = Number.isFinite(ta) ? ta : Number.POSITIVE_INFINITY
    const vb = Number.isFinite(tb) ? tb : Number.POSITIVE_INFINITY
    if (va !== vb) return va - vb
    return a.card.id - b.card.id
  })
  return decorated.map((d) => d.card)
}

/** FlashcardVO 列表 → 已排序会话队列（一步到位，供页面直接使用） */
export function createSessionQueue(list: FlashcardVO[] | null | undefined, ctx: QueueContext = {}): SessionCard[] {
  if (!list || list.length === 0) return []
  return sortQueue(list.map(toSessionCard), ctx)
}

/** 会话结果汇总（§二十：完成页展示真实分布，不美化） */
export interface SessionSummary {
  total: number
  mastered: number
  familiar: number
  unknown: number
  /**
   * 本次自评表现 0~100 = 平均 quality / 5 × 100。
   * **语义是「本次自评」，不是掌握度**——不得当作 Mastery 展示（§三十七）。
   */
  selfRatedScore: number | null
}

/** 汇总本次会话的自评分布（无评分时 selfRatedScore 为 null，不显示 0） */
export function summarizeSession(ratings: FlashcardRating[]): SessionSummary {
  const total = ratings.length
  let mastered = 0
  let familiar = 0
  let unknown = 0
  for (const r of ratings) {
    if (r === 'mastered') mastered++
    else if (r === 'familiar') familiar++
    else unknown++
  }
  const selfRatedScore =
    total === 0 ? null : Math.round(((mastered * 5 + familiar * 3 + unknown * 1) / (total * 5)) * 100)
  return { total, mastered, familiar, unknown, selfRatedScore }
}

/** 会话是否已完成（已评分张数覆盖队列） */
export function isSessionComplete(ratedCount: number, queueLength: number): boolean {
  return queueLength > 0 && ratedCount >= queueLength
}

/** 会话是否进行中（用于「退出确认」判定：至少评价过一张且未完成） */
export function isSessionInProgress(ratedCount: number, queueLength: number): boolean {
  return ratedCount > 0 && !isSessionComplete(ratedCount, queueLength)
}

/** 键盘按键 → 自评（不涉及任何评分计算，仅语义映射） */
export function keyToRating(key: string): FlashcardRating | null {
  if (key === '1') return 'unknown'
  if (key === '2') return 'familiar'
  if (key === '3') return 'mastered'
  return null
}

/** 键盘快捷键提示文案（§十八：轻量提示，统一来源，便于测试） */
export const KEYBOARD_HINT = 'Space 翻卡 · 1 不会 · 2 模糊 · 3 记得 · ← → 切换 · Esc 退出'

/**
 * 事件应携带的知识点 ID（§十二/§十四：只透传真实值）。
 * 优先调用方显式上下文，其次队列上下文，都没有则 null（后端不写该字段）。
 */
export function resolveEventKnowledgeId(
  explicitKnowledgeId?: number | null,
  ctx?: QueueContext | null,
): number | null {
  if (explicitKnowledgeId != null && explicitKnowledgeId > 0) return explicitKnowledgeId
  const fromCtx = ctx?.knowledgeId
  return fromCtx != null && fromCtx > 0 ? fromCtx : null
}
