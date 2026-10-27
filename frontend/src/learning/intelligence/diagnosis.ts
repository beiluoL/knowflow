/**
 * Architecture-6：Learning Diagnosis（§8/§19-27/§32）
 *
 * 纯函数（§32-35）：只消费 LearningIntelligenceContext（真实数据），输出 Diagnosis[]。
 * 禁止在内部调用 API / fetch（§33-35）。
 *
 * 优先级（§43，数值越大越优先，与 HEALTHY 取反）：
 *   REVIEW_OVERDUE(100) > REPEATED_MISTAKE(95) > WEAK_KNOWLEDGE(80)
 *   > LOW_ACCURACY(70) > PATH_STALLED(60) > INACTIVE_LEARNING(50) > HEALTHY_PROGRESS(0)
 *
 * 诚实原则（§28-29/§68/§148/§239）：
 *   - 仅当上下文提供真实数据时，才推导对应 Diagnosis；
 *   - REPEATED_MISTAKE / LOW_ACCURACY / PATH_STALLED / INACTIVE_LEARNING 依赖可选字段，
 *     缺省即不触发，绝不编造「连续错 3 次」「正确率 23%」之类的数字。
 */
import type {
  LearningDiagnosis,
  LearningEvidence,
  LearningIntelligenceContext,
} from './types'
import { deriveSignals } from './signals'

/** 知识点名称兜底（name 可能为 null） */
function knowledgeName(k: { name?: string | null } | null | undefined): string {
  return k?.name || '这个知识点'
}

/** 把薄弱类型的枚举翻译成用户可读的「为什么」（真实数据，§61/§261） */
function weaknessDetail(types: string[] = []): string {
  if (types.includes('ERROR_RATE') || types.includes('HIGH_ERROR_RATE')) return '近期答题错误率偏高'
  if (types.includes('NO_REVIEW') || types.includes('REVIEW_GAP')) return '最近复习较少'
  if (types.includes('MASTERY_DECAY') || types.includes('MASTERY_DROP')) return '掌握度有所下降'
  if (types.includes('LOW_CONFIDENCE')) return '掌握信心不足'
  return ''
}

/**
 * 诊断学习状态（§19-27）。
 * @returns 按 priority 降序排列的 Diagnosis 列表；无任何问题时返回 [HEALTHY_PROGRESS]。
 */
export function diagnoseLearningState(ctx: LearningIntelligenceContext): LearningDiagnosis[] {
  const s = deriveSignals(ctx)
  const out: LearningDiagnosis[] = []

  // 1) REVIEW_OVERDUE：有待复习知识点（真实 status = REVIEW_REQUIRED）
  if (s.reviewCount > 0) {
    const top = ctx.reviewRequired[0]
    out.push({
      type: 'REVIEW_OVERDUE',
      title: s.reviewCount > 1 ? `${s.reviewCount} 个知识点需要复习` : `「${knowledgeName(top)}」需要复习`,
      reason: '这些知识点已进入复习队列，需要巩固记忆，避免遗忘。',
      evidence: [{ type: 'REVIEW_DUE', label: `${s.reviewCount} 个知识点处于待复习状态`, value: s.reviewCount }],
      knowledgeId: top.knowledgeId,
      knowledgeName: knowledgeName(top),
      severity: 'HIGH',
      priority: 100,
    })
  }

  // 2) WEAK_KNOWLEDGE：逐个薄弱知识点（真实掌握度 + 真实薄弱原因）
  for (const w of ctx.weakPoints) {
    const score = Math.round(w.masteryScore)
    const detail = weaknessDetail(w.weaknessTypes)
    const evidence: LearningEvidence[] = [
      { type: 'WEAK_MASTERY', label: `当前掌握度 ${score}%`, value: score },
    ]
    if (detail) evidence.push({ type: 'QUIZ_ACCURACY', label: detail })
    out.push({
      type: 'WEAK_KNOWLEDGE',
      title: `「${knowledgeName(w)}」掌握度偏弱`,
      reason: detail ? `当前掌握度 ${score}%，${detail}。` : `当前掌握度 ${score}%，建议重新学习巩固。`,
      evidence,
      knowledgeId: w.knowledgeId,
      knowledgeName: knowledgeName(w),
      severity: 'MEDIUM',
      priority: 80,
    })
  }

  // 3) REPEATED_MISTAKE：需真实 knowledgeId mapping（§67-69；缺则跳过，不伪造）
  for (const id of s.repeatedMistakeKnowledgeIds) {
    const w = ctx.weakPoints.find((x) => x.knowledgeId === id) ?? ctx.reviewRequired.find((x) => x.knowledgeId === id)
    out.push({
      type: 'REPEATED_MISTAKE',
      title: w ? `「${knowledgeName(w)}」近期重复出错` : '有知识点近期重复出错',
      reason: '这个知识点近期多次答错，可以优先弄清错因并巩固。',
      evidence: [{ type: 'MISTAKE_COUNT', label: '近期相关题目多次答错' }],
      knowledgeId: id,
      knowledgeName: w?.name ?? undefined,
      severity: 'HIGH',
      priority: 95,
    })
  }

  // 4) LOW_ACCURACY：需真实正确率聚合（§70-72；缺则跳过）
  for (const id of s.lowAccuracyKnowledgeIds) {
    const w = ctx.weakPoints.find((x) => x.knowledgeId === id) ?? ctx.reviewRequired.find((x) => x.knowledgeId === id)
    out.push({
      type: 'LOW_ACCURACY',
      title: w ? `「${knowledgeName(w)}」近期正确率偏低` : '有知识点近期正确率偏低',
      reason: '最近几次练习正确率不高，建议通过练习把正确率提上来。',
      evidence: [{ type: 'QUIZ_ACCURACY', label: '最近练习正确率偏低' }],
      knowledgeId: id,
      knowledgeName: w?.name ?? undefined,
      severity: 'MEDIUM',
      priority: 70,
    })
  }

  // 5) PATH_STALLED：需真实进度变化时间（§23；缺则跳过）
  if (s.stalledPathIds.length > 0 && ctx.currentPath && s.stalledPathIds.includes(ctx.currentPath.id)) {
    out.push({
      type: 'PATH_STALLED',
      title: `「${ctx.currentPath.title}」进度停滞`,
      reason: '这条学习路径最近一段时间没有推进，可以接着上次停下的地方继续。',
      evidence: [{ type: 'PATH_PROGRESS', label: `当前进度 ${s.pathProgress}%`, value: s.pathProgress }],
      severity: 'LOW',
      priority: 60,
    })
  }

  // 6) INACTIVE_LEARNING：需真实事件（§73-76；不制造焦虑，仅温和提示）
  if (s.daysSinceLastActivity != null && s.daysSinceLastActivity >= 7) {
    out.push({
      type: 'INACTIVE_LEARNING',
      title: '最近学习较少',
      reason: '最近一段时间没有学习记录，可以从今天的一项任务开始，保持节奏。',
      evidence: [
        { type: 'RECENT_ACTIVITY', label: `最近一次学习在 ${s.daysSinceLastActivity} 天前`, value: s.daysSinceLastActivity },
      ],
      severity: 'LOW',
      priority: 50,
    })
  }

  // 7) HEALTHY_PROGRESS：仅当以上均无（§25）
  if (out.length === 0) {
    out.push({
      type: 'HEALTHY_PROGRESS',
      title: '学习状态良好',
      reason: '当前复习、巩固与任务都在顺利推进，按自己的节奏继续即可。',
      evidence: [],
      severity: 'LOW',
      priority: 0,
    })
  }

  return out.sort((a, b) => b.priority - a.priority)
}
