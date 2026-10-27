/**
 * Architecture-4.2：Knowledge Context 解析器（§3~§8）
 *
 * ── 为什么是纯函数、为什么没有新增字段（§4/§5/§48）──
 * 扫描真实 schema 后确认 Goal → Knowledge 可以**完全靠现有数据推导**：
 *
 *   learning_goal.path_id
 *     → learning_chapter（pathId）
 *     → learning_chapter.docIds（逗号分隔）
 *     → resource_knowledge_mapping(resource_type='DOC', status='ACCEPTED')
 *     → kg_entity（知识点 ID + 真实名称）
 *
 * 因此本阶段 **0 新表 / 0 新字段 / 0 新迁移**（§48）。唯一新增的是一个只读查询端点
 * `GET /api/learning/chapters/{id}/knowledge`——因为「文档 → 知识点」映射在项目内
 * 没有任何现有端点暴露（§47 要求报告理由，见 api/learning.ts 注释）。
 *
 * ── 铁律（§6）──
 *   只能使用真实映射；取不到就返回 null，**绝不猜测**（禁止「GC 目标 → 猜 knowledgeId=1」）。
 *
 * ── Context 不改变评分（§22）──
 *   同一条 Evidence 无论从哪里读取（Home / 学习中心 / Goal Workspace）分数完全一致，
 *   Context 只决定「它属于谁」，不参与任何加权计算。
 */
import type {
  ChapterKnowledgeVO,
  ChapterNodeVO,
  KnowledgeMasteryVO,
  LearningGoalVO,
} from '@/api/types'

/** 统一学习上下文（§7：扩展既有概念，不新建 LearningContext2） */
export interface LearningContext {
  /** 当前目标 ID（无目标为 null） */
  goalId: number | null
  /** 当前目标标题 */
  goalTitle: string | null
  /** 目标关联的学习路径 ID */
  pathId: number | null
  /** 当前学习节点 ID（DAG 中第一个 available 章节） */
  learningNodeId: number | null
  /** 当前学习节点标题（如「GC 算法」） */
  learningNodeTitle: string | null
  /** 当前知识点 ID（真实映射；取不到为 null） */
  knowledgeId: number | null
  /** 当前知识点真实名称（禁止「知识点 123」占位文案） */
  knowledgeTitle: string | null
}

/** 上下文解析入参：全部可选，缺哪一项就少绑哪一项，不伪造 */
export interface LearningContextSource {
  /** 当前进行中的学习目标（/learning/goals/active） */
  goal?: LearningGoalVO | null
  /** 当前学习节点（由 path-node.ts 从真实 DAG 推导） */
  currentNode?: ChapterNodeVO | null
  /** 当前章节的知识点解析结果（/learning/chapters/{id}/knowledge） */
  chapterKnowledge?: ChapterKnowledgeVO | null
  /** 已加载的掌握度列表（仅用于补全知识点真实名称） */
  masteries?: KnowledgeMasteryVO[] | null
  /** 调用方显式指定的知识点（如页面 route.query.knowledgeId） */
  explicitKnowledgeId?: number | null
  /** 调用方显式指定的知识点名称（如 route.query.knowledgeName） */
  explicitKnowledgeName?: string | null
}

/** 空上下文（所有解析失败的统一返回值，字段全 null，调用方按 null 降级） */
export const EMPTY_LEARNING_CONTEXT: LearningContext = {
  goalId: null,
  goalTitle: null,
  pathId: null,
  learningNodeId: null,
  learningNodeTitle: null,
  knowledgeId: null,
  knowledgeTitle: null,
}

/** 从掌握度列表按 id 取真实名称（§19：取不到返回 null，禁止生成「知识点 N」文案） */
export function knowledgeNameOf(
  masteries: KnowledgeMasteryVO[] | null | undefined,
  id: number | null | undefined,
): string | null {
  if (id == null || !masteries) return null
  return masteries.find((m) => m.knowledgeId === id)?.name ?? null
}

/**
 * §4 解析学习上下文（纯函数、无副作用、可单测）。
 *
 * 知识点 ID 的确定顺序（全部为真实来源）：
 *   1. 调用方显式指定（页面深链 query，最具体）
 *   2. 当前 Learning Node 的真实章节→知识点映射
 *   3. 掌握度列表中可确认存在的前述知识点（仅用于补名称，不用于凭空产生 ID）
 *
 * 一个章节可能映射到多个知识点时的取舍（确定性、可解释）：
 *   优先取「已被 Mastery 引擎跟踪」的那一个（出现在用户掌握度列表中），
 *   否则取 id 最小的一个。两者都是确定性的，不涉及随机或语义猜测。
 */
export function resolveLearningContext(src: LearningContextSource = {}): LearningContext {
  const goalId = src.goal?.id ?? null
  const goalTitle = src.goal?.title ?? null
  const pathId = src.goal?.pathId ?? null
  const learningNodeId = src.currentNode?.id ?? null
  const learningNodeTitle = src.currentNode?.title ?? null

  // 章节映射只有在「解析的就是当前节点」时才可信，避免串线（§20）
  const ck = src.chapterKnowledge
  const mappingMatchesNode =
    ck != null && (learningNodeId == null || ck.chapterId === learningNodeId)
  const candidates = mappingMatchesNode ? (ck?.knowledge ?? []) : []

  const tracked = new Set((src.masteries ?? []).map((m) => m.knowledgeId))
  const fromMapping =
    candidates.find((k) => tracked.has(k.id)) ?? (candidates.length > 0 ? candidates[0] : null)

  const explicitId = src.explicitKnowledgeId ?? null
  const knowledgeId = explicitId ?? fromMapping?.id ?? null

  // 知识点名称必须与 knowledgeId 同源（§19：禁止把 A 的名称绑到 B 的 id）。
  // 仅当 mapping 解析出的 id 与最终 knowledgeId 一致时，才复用其真实名称；
  // 显式 id 覆盖了映射 id 却没有显式名称时，宁可回落到掌握度列表 / null，绝不张冠李戴。
  const mappingNameForId = fromMapping != null && fromMapping.id === knowledgeId ? fromMapping.name : null
  const knowledgeTitle =
    src.explicitKnowledgeName ??
    mappingNameForId ??
    knowledgeNameOf(src.masteries, knowledgeId) ??
    null

  return {
    goalId,
    goalTitle,
    pathId,
    learningNodeId,
    learningNodeTitle,
    knowledgeId,
    knowledgeTitle,
  }
}

/** 上下文是否包含可用的知识点（供 UI 决定是否展示「当前知识」区块） */
export function hasKnowledge(ctx: LearningContext | null | undefined): boolean {
  return ctx != null && ctx.knowledgeId != null
}

/**
 * §8 Evidence 绑定知识点的优先级链（供 resolver 使用，纯函数）。
 *   1. 事件 metadata 中真实携带的 knowledgeId
 *   2. 当前 Learning Node 知识点
 *   3. Goal 当前 Node 知识点
 *   4. 调用方显式 context
 *   5. null（禁止猜测）
 */
export function resolveEvidenceKnowledgeId(
  fromMetadata: number | null,
  ctx: LearningContext | null | undefined,
  explicit: number | null = null,
): number | null {
  if (fromMetadata != null && fromMetadata > 0) return fromMetadata
  if (ctx?.knowledgeId != null) return ctx.knowledgeId
  return explicit != null && explicit > 0 ? explicit : null
}
