// Phase 1 面试 Skill Registry：服务端白名单技能与 JD 确定性推荐
import { apiGet, apiPost } from './request'

export type SkillDifficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'SENIOR'

export interface InterviewSkill {
  id: string
  name: string
  category: string
  level: string
  roles: string[]
  minYears: number
  maxYears: number
  difficulty: SkillDifficulty
  trackCode: string
  tags: string[]
  jdKeywords: string[]
  instructions: string
  questionRules: string[]
  followupRules: string[]
  rubric: Record<string, string>
  references: string[]
}

export interface InterviewTrackMeta {
  code: string
  name: string
  description: string
  questionPoolSize: number
}

export interface SkillMatch {
  skill: InterviewSkill
  score: number
  matchedKeywords: string[]
}

export interface SkillCatalog {
  version: string
  skills: InterviewSkill[]
  tracks: InterviewTrackMeta[]
}

export interface SkillRecommendResponse {
  skills: SkillMatch[]
  detectedKeywords: string[]
  note?: string
}

export function listInterviewSkills() {
  return apiGet<SkillCatalog>('/interview/skills')
}

export function recommendInterviewSkills(body: {
  jd?: string
  role?: string
  difficulty?: SkillDifficulty
  years?: number
}) {
  return apiPost<SkillRecommendResponse>('/interview/skills/recommend', body)
}
