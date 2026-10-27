/**
 * Architecture-4.1：Evidence Resolver（§7）
 *
 * 职责：LearningEvent → BehaviorEvidence。
 * 纯函数、无副作用、不调 API；同一事件无论算多少次结果完全一致（可重算 = 不成为真相源）。
 *
 * 换算口径全部来自「后端已经写进 metadata 的真实数字」，本文件不重新评分（§8/§9/§11）：
 *   FEYNMAN_EXPRESSED         overall + 四维 score        → EXPLANATION
 *   SCENARIO_COMPLETED        coverage/score/matched      → APPLICATION
 *   CODE_SUBMITTED            passCount/total/passed      → APPLICATION
 *   QUESTION_ANSWERED         correct/score               → RECALL
 *   FLASHCARD_REVIEWED        quality(0~5)/lapsed         → RECALL
 *   RECALL_COMPLETED          score                       → RECALL
 *   FIRST_PRINCIPLES_DERIVED  completeness                → UNDERSTANDING
 *
 * 置信度口径（确定性，非模型）：
 *   0.9 = 机器可验证结果（代码测试通过率、题目对错、要点覆盖计数）
 *   0.6 = 作者自检结果（费曼四维：无机器参照系，如实降权）
 *   0.5 = 结构完整度（第一性原理：只能证明「填了」，不能证明「对」）
 *   0.4 = 单次主观评分（SM-2 quality：单卡自评，样本极薄）
 */
import type { LearningEventVO } from '@/api/types'
import type { BehaviorEvidence, EvidenceDimension, EvidenceResolveOptions, EvidenceSourceType } from './types'
import { clampScore, eventTime, num, parseEventMetadata } from './types'

/** 机器可验证结果的置信度 */
const CONF_VERIFIABLE = 0.9
/** 作者自检类结果的置信度 */
const CONF_SELF_CHECK = 0.6
/** 结构完整度类结果的置信度（只能证明填了，不能证明对） */
const CONF_STRUCTURAL = 0.5
/** 单次主观自评的置信度 */
const CONF_SELF_REPORT = 0.4

/** 能产出 Evidence 的事件类型集合（其余事件类型一律返回 null，不伪造） */
const SUPPORTED: Record<string, EvidenceSourceType> = {
  FEYNMAN_EXPRESSED: 'FEYNMAN_EXPRESSED',
  SCENARIO_COMPLETED: 'SCENARIO_COMPLETED',
  CODE_SUBMITTED: 'CODE_SUBMITTED',
  QUESTION_ANSWERED: 'QUESTION_ANSWERED',
  FLASHCARD_REVIEWED: 'FLASHCARD_REVIEWED',
  RECALL_COMPLETED: 'RECALL_COMPLETED',
  FIRST_PRINCIPLES_DERIVED: 'FIRST_PRINCIPLES_DERIVED',
}

/** 构造一条 Evidence（统一出口，保证 evidenceId 规则一致） */
function make(
  ev: LearningEventVO,
  sourceType: EvidenceSourceType,
  dimension: EvidenceDimension,
  score: number,
  confidence: number,
  label: string,
  knowledgeId: number | null,
): BehaviorEvidence {
  return {
    evidenceId: `evt:${ev.id}:${dimension}`,
    eventId: ev.id,
    userId: ev.userId,
    sourceType,
    sourceId: ev.resourceId ?? 0,
    dimension,
    score: clampScore(score),
    confidence,
    timestamp: ev.createTime ?? null,
    knowledgeId,
    label,
  }
}

/**
 * §8 + Architecture-4.2 §8 知识点绑定：metadata（真实）> 当前节点 > 目标节点 > 显式 context > 兜底。
 * 每一级都必须是真实来源；全部取不到时为 null，绝不猜测（§6/§33）。
 */
function realKnowledgeId(meta: Record<string, unknown> | null, opts?: EvidenceResolveOptions): number | null {
  if (meta) {
    const k = num(meta, 'knowledgeId')
    if (k != null && k > 0) return k
  }
  const chain = [
    opts?.nodeKnowledgeId,
    opts?.goalKnowledgeId,
    opts?.explicitKnowledgeId,
    opts?.defaultKnowledgeId,
  ]
  for (const v of chain) {
    if (v != null && v > 0) return v
  }
  return null
}

/**
 * §8 费曼表达 → EXPLANATION。
 * 复用事件里已算好的 overall（四维勾选 / 16 × 100），不重新评分。
 * 若 overall 缺失，则用四维分数均值兜底；都缺失则返回 null（不猜）。
 */
export function resolveFeynmanEvidence(
  ev: LearningEventVO,
  opts?: EvidenceResolveOptions,
): BehaviorEvidence | null {
  const meta = parseEventMetadata(ev)
  const overall = num(meta, 'overall')
  let score = overall
  if (score == null) {
    const dims = ['accuracy', 'clarity', 'completeness', 'structure']
      .map((k) => num(meta, k))
      .filter((v): v is number => v != null)
    if (dims.length === 0) return null
    score = dims.reduce((a, b) => a + b, 0) / dims.length
  }
  const topic = meta && typeof meta.topic === 'string' ? meta.topic.trim() : ''
  return make(
    ev,
    'FEYNMAN_EXPRESSED',
    'EXPLANATION',
    score,
    CONF_SELF_CHECK,
    topic ? `费曼表达：${topic}` : '费曼表达',
    realKnowledgeId(meta, opts),
  )
}

/**
 * §9 场景练习 → APPLICATION。
 * 优先用 score（服务端覆盖度换算分），缺失时用 matched/total 现算，再缺失返回 null。
 */
export function resolveScenarioEvidence(
  ev: LearningEventVO,
  opts?: EvidenceResolveOptions,
): BehaviorEvidence | null {
  const meta = parseEventMetadata(ev)
  let score = num(meta, 'score')
  if (score == null) {
    const matched = num(meta, 'matched')
    const total = num(meta, 'total')
    if (matched == null || total == null || total <= 0) return null
    score = (matched / total) * 100
  }
  const title = meta && typeof meta.scenarioTitle === 'string' ? meta.scenarioTitle.trim() : ''
  return make(
    ev,
    'SCENARIO_COMPLETED',
    'APPLICATION',
    score,
    CONF_VERIFIABLE,
    title ? `场景练习：${title}` : '场景练习',
    realKnowledgeId(meta, opts),
  )
}

/**
 * §12 编程提交 → APPLICATION（真实测试用例通过率，机器可验证）。
 * 无 total 或 total<=0（脏数据/无用例）→ 不产出 Evidence。
 */
export function resolveCodingEvidence(
  ev: LearningEventVO,
  opts?: EvidenceResolveOptions,
): BehaviorEvidence | null {
  const meta = parseEventMetadata(ev)
  // Phase 3G：判题平台失败（SYSTEM_ERROR / JUDGE_UNAVAILABLE）不是用户答错，禁止产出证据
  if (meta && meta.platformFailure === true) return null
  const total = num(meta, 'total')
  if (total == null || total <= 0) return null
  const passCount = num(meta, 'passCount')
  const score = passCount == null ? 0 : (passCount / total) * 100
  return make(
    ev,
    'CODE_SUBMITTED',
    'APPLICATION',
    score,
    CONF_VERIFIABLE,
    '编程练习',
    realKnowledgeId(meta, opts),
  )
}

/**
 * §10 答题 → RECALL。
 * correct 优先（布尔 → 100/0）；仅当 correct 缺失时才用 score 字段。
 */
export function resolveQuizEvidence(
  ev: LearningEventVO,
  opts?: EvidenceResolveOptions,
): BehaviorEvidence | null {
  const meta = parseEventMetadata(ev)
  let score: number | null = null
  if (meta) {
    if (typeof meta.correct === 'boolean') {
      score = meta.correct ? 100 : 0
    } else if (meta.correct != null) {
      const c = String(meta.correct).toLowerCase()
      if (c === 'true') score = 100
      else if (c === 'false') score = 0
    }
    if (score == null) score = num(meta, 'score')
  }
  if (score == null) return null
  return make(ev, 'QUESTION_ANSWERED', 'RECALL', score, CONF_VERIFIABLE, '答题练习', realKnowledgeId(meta, opts))
}

/**
 * §11 闪卡复习 → RECALL。
 * 复用 SM-2 真实 quality（0~5）→ 0~100，不重新实现 SM-2。
 * lapsed=true（本次遗忘）时按 quality 计分后额外封顶 40，反映「中断重来」的事实。
 */
export function resolveFlashcardEvidence(
  ev: LearningEventVO,
  opts?: EvidenceResolveOptions,
): BehaviorEvidence | null {
  const meta = parseEventMetadata(ev)
  const quality = num(meta, 'quality')
  if (quality == null) return null
  const q = Math.max(0, Math.min(5, quality))
  let score = (q / 5) * 100
  if (meta?.lapsed === true || meta?.lapsed === 'true') score = Math.min(score, 40)
  return make(
    ev,
    'FLASHCARD_REVIEWED',
    'RECALL',
    score,
    CONF_SELF_REPORT,
    '闪卡复习',
    realKnowledgeId(meta, opts),
  )
}

/** 主动回忆会话 → RECALL（后端 RECALL_COMPLETED metadata.score 为真实均分） */
export function resolveRecallEvidence(
  ev: LearningEventVO,
  opts?: EvidenceResolveOptions,
): BehaviorEvidence | null {
  const meta = parseEventMetadata(ev)
  const score = num(meta, 'score')
  if (score == null) return null
  return make(ev, 'RECALL_COMPLETED', 'RECALL', score, CONF_VERIFIABLE, '主动回忆', realKnowledgeId(meta, opts))
}

/**
 * §17 第一性原理 → UNDERSTANDING。
 * completeness = 填写完整的环节数 / 6 × 100（结构完整度，§16 明确定性：
 * 只能证明「你把推导过程写全了」，不能证明「你推对了」）。
 */
export function resolveFirstPrinciplesEvidence(
  ev: LearningEventVO,
  opts?: EvidenceResolveOptions,
): BehaviorEvidence | null {
  const meta = parseEventMetadata(ev)
  let score = num(meta, 'completeness')
  if (score == null) {
    const filled = num(meta, 'filled')
    const total = num(meta, 'total')
    if (filled == null || total == null || total <= 0) return null
    score = (filled / total) * 100
  }
  const topic = meta && typeof meta.topic === 'string' ? meta.topic.trim() : ''
  return make(
    ev,
    'FIRST_PRINCIPLES_DERIVED',
    'UNDERSTANDING',
    score,
    CONF_STRUCTURAL,
    topic ? `第一性原理：${topic}` : '第一性原理',
    realKnowledgeId(meta, opts),
  )
}

/**
 * §7 统一入口：单条事件 → 单条 Evidence（不支持的类型 / 脏数据一律 null）。
 * 一条事件只产出一个维度（保持「一次行为一份解释」的可解释性）。
 */
export function resolveEvidence(
  ev: LearningEventVO,
  opts?: EvidenceResolveOptions,
): BehaviorEvidence | null {
  if (!ev || ev.id == null || !SUPPORTED[ev.eventType]) return null
  switch (SUPPORTED[ev.eventType]) {
    case 'FEYNMAN_EXPRESSED':
      return resolveFeynmanEvidence(ev, opts)
    case 'SCENARIO_COMPLETED':
      return resolveScenarioEvidence(ev, opts)
    case 'CODE_SUBMITTED':
      return resolveCodingEvidence(ev, opts)
    case 'QUESTION_ANSWERED':
      return resolveQuizEvidence(ev, opts)
    case 'FLASHCARD_REVIEWED':
      return resolveFlashcardEvidence(ev, opts)
    case 'RECALL_COMPLETED':
      return resolveRecallEvidence(ev, opts)
    case 'FIRST_PRINCIPLES_DERIVED':
      return resolveFirstPrinciplesEvidence(ev, opts)
    default:
      return null
  }
}

/**
 * 批量解析（事件流 → 证据流），自动丢弃不可解析事件。
 * 结果按时间升序（时间相同按 eventId 升序），保证聚合稳定可复现。
 */
export function resolveEvidenceBatch(
  events: LearningEventVO[],
  opts?: EvidenceResolveOptions,
): BehaviorEvidence[] {
  const out: BehaviorEvidence[] = []
  for (const ev of events ?? []) {
    const e = resolveEvidence(ev, opts)
    if (e) out.push(e)
  }
  return out.sort((a, b) => {
    const ta = eventTime({ createTime: a.timestamp } as LearningEventVO) ?? 0
    const tb = eventTime({ createTime: b.timestamp } as LearningEventVO) ?? 0
    return ta - tb || a.eventId - b.eventId
  })
}
