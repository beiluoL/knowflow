/**
 * Career Workbench 数据契约。
 *
 * 字段与后端 `CareerRecordService` / `CareerWorkflowService` / `CareerWorkspaceService`
 * 的真实返回保持一致；不把缺失字段补造成分数或能力结论。
 */
export type CareerKind = 'RESUME' | 'JOB'
export type MatchStatus = 'MATCHED' | 'PARTIAL' | 'MISSING' | 'UNCERTAIN' | 'NOT_APPLICABLE'
export type JobStatus =
  | 'SAVED'
  | 'TO_APPLY'
  | 'APPLIED'
  | 'WRITTEN_TEST'
  | 'INTERVIEWING'
  | 'OFFER'
  | 'REJECTED'
  | 'CLOSED'

export interface CareerClaim {
  value: string
  quote: string
  fields?: Record<string, string>
}

export interface CareerRecord {
  id: number
  kind: CareerKind
  title: string
  currentVersion: number
  isDefault: boolean
  version: number
  source: string
  confirmed: boolean
  sections: Record<string, CareerClaim[]>
  importStatus?: 'EXTRACTED' | 'NEEDS_TEXT' | 'REVIEW_REQUIRED'
  importWarning?: string
  jobStatus?: JobPipeline
}

export interface CareerEdit {
  expectedVersion?: number
  title: string
  source: string
  sections: Record<string, CareerClaim[]>
}

export interface CareerVersion {
  version: number
  confirmed: boolean
  createdAt: string
}

export interface JobPipeline {
  recordId: number
  status: JobStatus
  note: string
  appliedAt: string | null
  updatedAt: string | null
}

export interface MatchDimension {
  requirement: string
  resumeEvidence: string[]
  matchStatus: MatchStatus
  gapDescription: string
  evidenceSource: string
}

export interface MatchDimensionReport {
  id: string
  requirement: string
  resumeEvidence: string[]
  matchStatus: string
  confidence: string
  gapDescription: string
  requirementEvidence: string[]
  coverage: number | null
}

export interface SkillMapping {
  requirement: string
  skillIds: string[]
  knowledgeIds: number[]
  mappingStatus: 'REGISTERED' | 'UNMAPPED' | 'NEEDS_CONFIRMATION'
}

export interface CareerMatch {
  id: number
  resume: CareerRecord
  job: CareerRecord
  required: MatchDimension[]
  preferred: MatchDimension[]
  score: number | null
  policyVersion: string
  scoreMeaning: string
  skillMappings: SkillMapping[]
  dimensions: MatchDimensionReport[]
  weights: Record<string, number>
  overallScore: number | null
  evaluationCoverage: number
  unassessedDimensions: string[]
  createdAt?: string | null
}

export interface MatchSummary {
  id: number
  resumeTitle: string
  resumeVersion: number
  resumeId: number
  jobTitle: string
  jobVersion: number
  jobId: number
  score: number | null
  overallScore: number | null
  policyVersion: string
  scoreMeaning: string
  evaluationCoverage: number | null
  createdAt: string
}

export interface MatchCorrection {
  requirement: string
  matchStatus: MatchStatus
  resumeEvidence: string[]
  reason: string
}

export interface CareerPlanQuestion {
  questionId: string
  title: string
  questionPurpose: string
  evidenceReference: string
  expectedKnowledgePoints: string[]
  targetSkill?: string
  source?: string
  scoringDimension?: string
  followupDirections?: string[]
  skillIds?: string[]
  resumeEvidence?: string
  requirement?: string
  regeneratedFrom?: string
}

export interface CareerPlan {
  id: number
  matchId: number
  resume: CareerRecord
  job: CareerRecord
  questions: CareerPlanQuestion[]
  durationMinutes: number
  difficulty: string
  interviewType?: string
  projectDepth?: string
  focusSkills?: string[]
  generationSource: string
  previousPlanId?: number
  editedAt?: string
}

export interface CareerPlanSettings {
  matchId: number
  projectCount: number
  coreCount: number
  gapCount: number
  designCount: number
  durationMinutes: number
  difficulty: string
  focusSkills: string[]
  interviewType: string
  projectDepth: string
}

export interface PlanSummary {
  id: number
  matchId: number
  jobId?: number
  jobTitle: string
  jobVersion: number
  resumeId?: number
  resumeVersion?: number
  questionCount: number
  durationMinutes: number
  difficulty: string
  generationSource: string
  previousPlanId?: number
  createdAt: string
}

export interface CareerInterviewSummary {
  planId: number
  sessionId: number
  jobTitle: string
  state: string
  stateLabel: string
  difficulty: string
  role: string
  createdAt: string
}

export interface CareerDashboardTask {
  id: string
  title: string
  description: string
  route: string
}

export interface CareerActivity {
  type: string
  title: string
  description: string
  at: string
}

export interface PipelineCount {
  status: JobStatus
  label: string
  count: number
}

export interface CareerDashboard {
  counts: {
    resumes: number
    jobs: number
    favoriteJobs: number
    matches: number
    interviews: number
  }
  pipeline: PipelineCount[]
  tasks: CareerDashboardTask[]
  recentActivity: CareerActivity[]
}

export interface PolishSuggestion {
  original: string
  suggestion: string
  reason: string
}

export interface PolishResult {
  version: number
  suggestions: PolishSuggestion[]
  modified: boolean
}

/* ============================ 职业应用 V2.0 ============================ */

/** 求职总览（数据分析中心）：所有数字来自本人真实记录，样本不足返回 null。 */
export interface CareerOverview {
  counts: {
    resumes: number
    jobs: number
    matches: number
    applied: number
    offers: number
    plans: number
  }
  pipeline: PipelineCount[]
  trends: OverviewWeek[]
  skills: OverviewSkills
  interview: {
    sessions: number
    completed: number
    selfScoreAvg: number | null
    scored: number
  }
  activity: CareerActivity[]
}

export interface OverviewWeek {
  label: string
  start: string
  resumeVersions: number
  jobVersions: number
  matches: number
}

export interface OverviewSkills {
  resume: string | null
  overall: number | null
  jobSample: number
  top: { skill: string; required: number; covered: number; coverage: number }[]
}

export type ToolCheckStatus = 'PASS' | 'WARN' | 'FAIL' | 'UNKNOWN'
export type ToolRisk = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH'

export interface ToolCheckItem {
  id: string
  label: string
  status: ToolCheckStatus
  risk: ToolRisk
  evidence: string
  suggestion: string
}

export interface ToolSummary {
  pass: number
  warn: number
  fail: number
  unknown: number
  overall: ToolCheckStatus
}

export interface ToolCheckResult {
  tool: 'ATS' | 'CONTENT'
  resumeId: number
  resumeTitle: string
  resumeVersion: number
  jobId: number | null
  jobVersion: number | null
  analysisVersion: string
  checkedAt: string
  summary: ToolSummary
  items: ToolCheckItem[]
  disclaimer: string
}

export interface JdDimension {
  skill: string
  status: MatchStatus
  evidence: string[]
  note: string
}

export interface JdOptimizeResult {
  tool: 'JD'
  resumeId: number
  resumeTitle: string
  resumeVersion: number
  jobId: number
  jobTitle: string
  jobVersion: number
  analysisVersion: string
  checkedAt: string
  coverage: number
  required: JdDimension[]
  preferred: JdDimension[]
  extraSkills: string[]
  suggestions: { skill: string; type: string; text: string }[]
  matchedCount: number
  requiredCount: number
  disclaimer: string
}

export interface ToolRunSummary {
  id: number
  resumeId: number
  resumeVersion: number
  jobId: number | null
  jobVersion: number | null
  toolType: 'ATS' | 'CONTENT' | 'JD'
  analysisVersion: string
  createdAt: string
  summary: {
    pass?: number
    warn?: number
    fail?: number
    unknown?: number
    overall?: ToolCheckStatus
    coverage?: number
    resumeTitle?: string
    jobTitle?: string
  }
}

export interface CompareChange {
  type: 'ADDED' | 'REMOVED' | 'MODIFIED'
  label: string
  before: string | null
  after: string | null
}

export interface CompareResult {
  tool: 'COMPARE'
  resumeId: number
  resumeTitle: string
  from: number
  to: number
  fromConfirmed: boolean
  toConfirmed: boolean
  changes: Record<string, CompareChange[]>
  sectionCounts: Record<string, { from: number; to: number }>
  analysisVersion: string
  checkedAt: string
  disclaimer: string
}

export type ResumeFontFamily = 'sans' | 'serif' | 'mono'

/** 排版设计：与简历内容版本完全独立，只影响渲染。 */
export interface ResumeTheme {
  templateId: string
  fontFamily: ResumeFontFamily
  fontSize: number
  lineHeight: number
  accentColor: string
  pageSize: 'A4' | 'Letter'
  sectionGap: number
  margin: { top: number; right: number; bottom: number; left: number }
  sectionOrder: string[]
  visibleSections: string[]
}

export interface ResumeDesign {
  id: number
  name: string
  templateId: string
  templateVersion: string
  isDefault: boolean
  theme: ResumeTheme
  createdAt: string
  updatedAt: string
}

export interface ResumeTemplateMeta {
  id: string
  name: string
  version: string
}

/* ============================ 职业功能 V3.0 ============================ */

export interface JobSourceMeta {
  sourceId: string
  displayName: string
  enabled: boolean
  attributionRequired: boolean
  attributionText: string | null
  termsUrl: string | null
  documentation: string | null
  cacheTtlSeconds: number
  lastPolicyReviewedAt: string
  note: string | null
}

export interface NormalizedJob {
  sourceId: string
  sourceName: string
  externalId: string | null
  title: string
  company: string | null
  location: string | null
  salaryText: string | null
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  salaryPeriod: string | null
  experience: string | null
  education: string | null
  jobType: string | null
  remoteType: string | null
  description: string | null
  tags: string[]
  requiredSkills: string[]
  preferredSkills: string[]
  sourceUrl: string | null
  applicationUrl: string | null
  postedAt: string | null
  fetchedAt: string | null
  attribution: string | null
  freshness: string | null
}

export interface JobSourceStatus {
  sourceId: string
  displayName: string
  status: 'SUCCESS' | 'DEGRADED' | 'FAILED' | 'THROTTLED'
  stale: boolean
  message: string | null
  count: number
}

export interface JobSearchResponse {
  items: NormalizedJob[]
  sources: JobSourceStatus[]
  partial: boolean
  attribution: string[]
  page: number
  size: number
  total: number
}

export interface SearchHistoryItem {
  id: number
  keyword: string
  filters: Record<string, unknown>
  saved: boolean
  lastUsedAt: string
}

export interface MultiResumeCandidate {
  resumeId: number
  resumeTitle: string
  resumeVersion: number
  matchId: number
  jobId?: number
  jobTitle?: string
  score: number | null
  overallScore: number | null
  coverage: number | null
  requiredCount: number
  matchedCount: number
  recommended: boolean
  tie?: boolean
  reason: string
}

export interface MultiResumeResult {
  jobId: number
  jobVersion: number
  candidates: MultiResumeCandidate[]
  recommendedResumeId: number | null
  scoreMeaning: string
  policyVersion: string
}

export interface BatchMatchResult {
  resumeId: number
  resumeVersion: number
  jobs: MultiResumeCandidate[]
  failures: { jobId: number; message: string }[]
  partial: boolean
  scoreMeaning: string
}

export type TailoringMode = 'LIGHT' | 'KEYWORD' | 'DEEP'
export type TailoringChangeType = 'MOVE_TOP' | 'REORDER' | 'RENAME' | 'REWRITE' | 'QUESTION'

export interface TailoringChange {
  id: string
  type: TailoringChangeType
  section: string
  requirement: string
  originalQuote: string
  quoteSource?: 'RESUME' | 'JD'
  proposedText: string | null
  reason: string
  confidence: string
  applied: boolean
}

export interface TailoringTask {
  id: number
  resumeId: number
  resumeVersion: number
  jobId: number
  jobVersion: number
  mode: TailoringMode
  status: 'GENERATED' | 'APPLIED' | 'DISCARDED'
  modelSource: 'RULE' | 'AI'
  promptVersion: string
  resultVersion: number | null
  createdAt: string
  changes: TailoringChange[]
  disclaimer: string
  notice?: string | null
  record?: CareerRecord
}

export interface InsightsNameCount {
  name: string
  count: number
  ratio: number
}

export interface CareerInsights {
  sampleSize: number
  scope: string
  generatedAt: string
  skills: { required: InsightsNameCount[]; preferred: InsightsNameCount[] }
  salary: {
    parsed: number
    unparsed: number
    min: number | null
    median: number | null
    max: number | null
    note: string
  }
  cities: InsightsNameCount[]
  companies: InsightsNameCount[]
  experience: InsightsNameCount[]
  education: InsightsNameCount[]
  personal: {
    resumes: number
    jobs: number
    matches: number
    applied: number
    interviewing: number
    offers: number
    pipeline: Record<string, number>
    conversion: { applyToInterview: number | null; applyToOffer: number | null }
    rateNote: string
  }
}

