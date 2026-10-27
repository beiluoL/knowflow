// 技术英语展示文案的唯一映射源：类型、领域、学习状态、关系与读法方式。
// 后端只存稳定英文枚举，中文展示全部在此收口，避免各组件各写一套。
import type { LearningStatus, ReadingMode, TermType, VerificationStatus } from '@/api/techEnglish'

export const TERM_TYPE_LABELS: Record<TermType, string> = {
  WORD: '单词',
  ACRONYM: '缩写',
  PHRASE: '短语',
}

export function termTypeLabel(type?: string | null): string {
  return (type && TERM_TYPE_LABELS[type as TermType]) || type || '未知'
}

export const DOMAIN_LABELS: Record<string, string> = {
  JAVA: 'Java 基础',
  JVM: 'JVM',
  CONCURRENCY: '并发编程',
  SPRING: 'Spring',
  DB: '数据库',
  CACHE: '缓存',
  MQ: '消息队列',
  DISTRIBUTED: '分布式系统',
  LLM: '大模型',
  RAG: 'RAG',
  AGENT: 'Agent',
  AI_ENG: 'AI 工程化',
}

export function domainLabel(domain?: string | null): string {
  return (domain && DOMAIN_LABELS[domain]) || domain || '未分类'
}

/**
 * 从文档分类 / 标签 / 标题推断技术领域，只用于多义项排序提示。
 * 未命中时返回 undefined，由服务端按词条自身数据排序，不伪造领域。
 */
export function inferTermDomain(text?: string | null): string | undefined {
  const meta = (text || '').toLowerCase()
  if (!meta) return undefined
  const rules: Array<[string, string]> = [
    ['mysql', 'DB'],
    ['数据库', 'DB'],
    ['redis', 'CACHE'],
    ['缓存', 'CACHE'],
    ['kafka', 'MQ'],
    ['rabbitmq', 'MQ'],
    ['消息队列', 'MQ'],
    ['rag', 'RAG'],
    ['embedding', 'RAG'],
    ['检索增强', 'RAG'],
    ['llm', 'LLM'],
    ['大模型', 'LLM'],
    ['agent', 'AGENT'],
    ['智能体', 'AGENT'],
    ['spring', 'SPRING'],
    ['jvm', 'JVM'],
    ['垃圾回收', 'JVM'],
    ['并发', 'CONCURRENCY'],
    ['concurren', 'CONCURRENCY'],
    ['分布式', 'DISTRIBUTED'],
    ['distributed', 'DISTRIBUTED'],
    ['java', 'JAVA'],
  ]
  const hit = rules.find(([keyword]) => meta.includes(keyword))
  return hit?.[1]
}

export const LEARNING_STATUS_LABELS: Record<LearningStatus, string> = {
  NEW: '未开始',
  LEARNING: '学习中',
  MASTERED: '已掌握',
}

export function learningStatusLabel(status?: string | null): string {
  return (status && LEARNING_STATUS_LABELS[status as LearningStatus]) || '未开始'
}

export const RELATION_LABELS: Record<string, string> = {
  RELATED: '相关',
  CONTRAST: '对比',
  CONTRAS: '对比',
  CONTRASTS: '对比',
  PREREQUISITE: '前置',
  CONFUSABLE: '易混淆',
  PART_OF: '组成',
  IS_A: '属于',
  EXPLAINS: '解释',
}

export function relationLabel(relation?: string | null): string {
  return (relation && RELATION_LABELS[relation]) || relation || '相关'
}

export const READING_MODE_LABELS: Record<ReadingMode, string> = {
  LETTER_BY_LETTER: '逐字母读',
  SPOKEN_WORD: '整词读',
  MIXED: '混合读',
}

export const VERIFICATION_LABELS: Record<VerificationStatus, string> = {
  VERIFIED: '已核验',
  PENDING: '待核验',
  UNAVAILABLE: '不可用',
}

/** 发音变体按钮文案：缩写区分「读缩写 / 读全称」，普通词区分美音 / 英音 */
export function variantLabel(variant: string, termType?: string | null): string {
  if (variant === 'ABBR') return '读缩写'
  if (variant === 'FULL_FORM') return '读全称'
  if (variant === 'WORD_US') return termType === 'PHRASE' ? '美音朗读' : '美音'
  if (variant === 'WORD_UK') return termType === 'PHRASE' ? '英音朗读' : '英音'
  return '朗读'
}
