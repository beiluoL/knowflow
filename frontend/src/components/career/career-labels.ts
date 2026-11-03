import type { JobStatus, MatchStatus } from '@/api/career/types'

/** 简历章节顺序与中文标签；与后端允许的 sections 键保持一致。 */
export const RESUME_SECTIONS = [
  { key: 'basics', label: '基本信息', hint: '姓名、年限、联系方式等（仅本人可见）' },
  { key: 'intention', label: '求职意向', hint: '目标岗位、城市、工作方式' },
  { key: 'education', label: '教育经历', hint: '学校、专业、学历、时间' },
  { key: 'experience', label: '工作经历', hint: '公司、职位、时间、职责与成果' },
  { key: 'projects', label: '项目经历', hint: '项目背景、职责、技术栈与可验证结果' },
  { key: 'skills', label: '技能清单', hint: '与岗位要求可直接比较的技术技能' },
  { key: 'certificates', label: '证书与荣誉', hint: '与岗位相关的证书、奖项' },
  { key: 'custom', label: '自定义章节', hint: '填写自定义标题与有原文证据的补充经历' },
  { key: 'selfIntro', label: '自我介绍', hint: '简短补充，不重复堆砌技能关键词' },
] as const

/** JD 结构化章节顺序；requiredSkills 是匹配评分的必要证据来源。 */
export const JOB_SECTIONS = [
  { key: 'basics', label: '岗位基本信息', hint: '企业、职位、地点、薪资、学历与年限' },
  { key: 'responsibilities', label: '工作职责', hint: 'JD 中明确的职责条目' },
  { key: 'requiredSkills', label: '必备技能', hint: 'JD 明确要求的技能，参与匹配评分' },
  { key: 'preferredSkills', label: '优先技能', hint: '加分项，不参与必备技能覆盖率' },
  { key: 'experienceRequirement', label: '经验要求', hint: '工作年限、行业经验' },
  { key: 'educationRequirement', label: '学历要求', hint: '学历、专业限制' },
  { key: 'projects', label: '项目经验要求', hint: 'JD 明确要求的项目类型' },
  { key: 'domain', label: '业务领域', hint: '行业或业务方向' },
  { key: 'other', label: '其他要求', hint: '语言、证书、软技能等' },
] as const

export const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  SAVED: '关注中',
  TO_APPLY: '待投递',
  APPLIED: '已投递',
  WRITTEN_TEST: '笔试',
  INTERVIEWING: '面试中',
  OFFER: '已录用',
  REJECTED: '已拒绝',
  CLOSED: '已结束',
}

export const JOB_STATUS_OPTIONS: { value: JobStatus; label: string }[] = (
  Object.entries(JOB_STATUS_LABELS) as [JobStatus, string][]
).map(([value, label]) => ({ value, label }))

export const MATCH_STATUS_LABELS: Record<MatchStatus, string> = {
  MATCHED: '已具备且有证据',
  PARTIAL: '部分具备',
  MISSING: '简历未体现',
  UNCERTAIN: '暂未匹配',
  NOT_APPLICABLE: '明确不符合',
}

export const MATCH_STATUS_CLASS: Record<MatchStatus, string> = {
  MATCHED: 'is-matched',
  PARTIAL: 'is-partial',
  MISSING: 'is-missing',
  UNCERTAIN: 'is-uncertain',
  NOT_APPLICABLE: 'is-neutral',
}

export const DIMENSION_LABELS: Record<string, string> = {
  requiredSkills: '必备技能',
  preferredSkills: '优先技能',
  experience: '工作年限与经历',
  education: '学历要求',
  projects: '项目证据',
  technicalDepth: '技术深度',
  responsibilities: '岗位职责',
  domain: '业务领域',
}

export const QUESTION_SOURCE_LABELS: Record<string, string> = {
  RESUME_PROJECT: '简历项目',
  JD_REQUIRED_SKILL: 'JD 必备技能',
  JD_GAP: '能力缺口',
  SYSTEM_DESIGN: '系统设计',
  USER_EDITED: '人工编辑',
}

export const QUESTION_PURPOSE_LABELS: Record<string, string> = {
  RESUME_EVIDENCE: '项目真实性',
  JOB_CORE: '岗位核心技能',
  GAP_VERIFICATION: '缺口验证',
  SYSTEM_DESIGN: '系统设计',
}

/** 只克隆可 JSON 序列化的业务数据；避免 structuredClone 对 Vue reactive Proxy 抛 DataCloneError。 */
export function clonePlain<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
