// 技术英语（Tech English）接口封装，字段与后端 com.knowflow.vo.TechVocabVOs 一一对应。
import { apiGet, apiPost, apiPut } from './request'
import type { PageResult } from './types'

/** 词条类型：单词 / 缩写 / 短语 */
export type TermType = 'WORD' | 'ACRONYM' | 'PHRASE'

/** 审核状态：草稿 / 已审核 / 已发布 / 已归档 */
export type ContentStatus = 'DRAFT' | 'REVIEWED' | 'PUBLISHED' | 'ARCHIVED'

/** 用户学习状态（展示层，不是复习调度的评分） */
export type LearningStatus = 'NEW' | 'LEARNING' | 'MASTERED'

/** 发音变体：美音 / 英音 / 缩写读法 / 全称读法 */
export type PronunciationVariant = 'WORD_US' | 'WORD_UK' | 'ABBR' | 'FULL_FORM'

/** 朗读方式：逐字母 / 整词 / 混合 */
export type ReadingMode = 'LETTER_BY_LETTER' | 'SPOKEN_WORD' | 'MIXED'

/** 读法核验状态 */
export type VerificationStatus = 'VERIFIED' | 'PENDING' | 'UNAVAILABLE'

export interface TermSummary {
  id: number
  headword: string
  termType: TermType
  domain: string
  meaningCn: string
  fullForm?: string | null
  className?: string | null
  ipaUs?: string | null
  ipaUk?: string | null
  hasVerifiedPronunciation: boolean
  pronunciationVariants: string[]
}

export interface PronunciationVO {
  id?: number | null
  variant: PronunciationVariant
  readingText?: string | null
  locale?: string | null
  readingMode?: ReadingMode | null
  audioUrl?: string | null
  audioProvider?: string | null
  licenseNote?: string | null
  verificationStatus: VerificationStatus
}

export interface ConceptLinkVO {
  targetType: string
  targetId?: number | null
  relationType: string
  label: string
  route?: string | null
}

export interface RelatedTermVO {
  id: number
  headword: string
  meaningCn: string
  domain: string
  relationType: string
}

export interface WordPart {
  word: string
  meaningCn: string
}

export interface TermDetail {
  id: number
  headword: string
  termType: TermType
  domain: string
  senseKey?: string | null
  fullForm?: string | null
  className?: string | null
  partOfSpeech?: string | null
  ipaUs?: string | null
  ipaUk?: string | null
  meaningCn: string
  definitionCn?: string | null
  definitionEn?: string | null
  wordBreakdown?: WordPart[] | null
  breakdownNote?: string | null
  interviewEn?: string | null
  exampleEn?: string | null
  exampleCn?: string | null
  aliases?: string[] | null
  contentStatus: ContentStatus
  referenceSource?: string | null
  licenseNote?: string | null
  reviewedAt?: string | null
  pronunciations: PronunciationVO[]
  conceptLinks: ConceptLinkVO[]
  relatedTerms: RelatedTermVO[]
  favorite?: boolean | null
  learningStatus?: LearningStatus | null
}

export interface UserStateVO {
  termId: number
  favorite: boolean
  learningStatus: LearningStatus
  updatedAt?: string | null
}

export interface MyTermVO {
  term: TermSummary
  favorite: boolean
  learningStatus: LearningStatus
  updatedAt?: string | null
}

export interface FiltersVO {
  total: number
  byType: Record<string, number>
  domains: { domain: string; count: number }[]
  myFavoriteCount?: number | null
  myLearningCount?: number | null
}

/** 管理端词条输入（与后端 TechVocabDtos.TermInput 对齐） */
export interface TermInput {
  headword: string
  termType: TermType
  domain: string
  senseKey?: string | null
  fullForm?: string | null
  className?: string | null
  partOfSpeech?: string | null
  ipaUs?: string | null
  ipaUk?: string | null
  meaningCn: string
  definitionCn?: string | null
  definitionEn?: string | null
  wordBreakdown?: WordPart[] | null
  breakdownNote?: string | null
  interviewEn?: string | null
  exampleEn?: string | null
  exampleCn?: string | null
  aliases?: string[] | null
  contentStatus: ContentStatus
  referenceSource?: string | null
  licenseNote?: string | null
  pronunciations?: PronunciationVO[] | null
}

export interface ImportPreviewItem {
  index: number
  headword: string
  domain: string
  action: string
  message?: string | null
}

export interface ImportPreviewVO {
  token: string
  newCount: number
  updateCount: number
  invalidCount: number
  items: ImportPreviewItem[]
  expiresAt?: string | null
}

export interface ImportResultVO {
  created: number
  updated: number
  skipped: number
  invalid: number
  replayed: boolean
}

export interface TermSearchParams {
  q?: string
  type?: string
  domain?: string
  pageNum?: number
  pageSize?: number
}

export interface MyTermParams {
  favorite?: boolean
  status?: string
  pageNum?: number
  pageSize?: number
}

/* ============================ P1 词汇训练 ============================ */

/** 复习队列条目：四种练习模式共用同一份服务端数据 */
export interface ReviewItemVO {
  termId: number
  cardId?: number | null
  headword: string
  termType: TermType
  domain: string
  meaningCn: string
  fullForm?: string | null
  className?: string | null
  definitionCn?: string | null
  ipaUs?: string | null
  ipaUk?: string | null
  pronunciations: PronunciationVO[]
  /** 缩写模式仅对有可靠英文全称的词条开放 */
  acronymMode: boolean
  /** 听音模式仅当存在已核验且可播放的发音时开放 */
  listeningMode: boolean
  learningStatus: LearningStatus
  favorite: boolean
  dueAt?: string | null
  reviewCount: number
}

export interface ReviewQueueVO {
  items: ReviewItemVO[]
  dueCount: number
  newCount: number
  limit: number
  hasReviewCards: boolean
}

export interface AddReviewResultVO {
  cardId: number
  /** false 表示该词条已有复习卡，本次为幂等命中 */
  created: boolean
}

export interface DomainProgressVO {
  domain: string
  total: number
  learned: number
}

/** 个人学习看板：全部来自服务端真实统计 */
export interface VocabProgressVO {
  learned: number
  dueToday: number
  completedToday: number
  addedToReview: number
  favoriteCount: number
  totalTerms: number
  domains: DomainProgressVO[]
}

export interface ReviewReceiptVO {
  termId: number
  cardId: number
  rating: number
  dueAt?: string | null
  state: string
  revision: number
  /** true 表示同一 requestId 命中去重，未重复记分 */
  replayed: boolean
}

export interface ReviewSubmitInput {
  rating: number
  /** 复用既有 FSRS 幂等协议：[A-Za-z0-9_-]{12,80} */
  requestId: string
  sessionId?: string | null
}

/* ============================ V3 专题学习 ============================ */

/** 专题概览：按现有技术方向聚合，不新增学习路径表 */
export interface TopicVO {
  domain: string
  total: number
  learned: number
  due: number
}

/** 专题内词条：learningStatus/dueAt 来自既有 FSRS 映射，不虚构进度 */
export interface TopicTermVO {
  termId: number
  headword: string
  termType: TermType
  meaningCn: string
  fullForm?: string | null
  ipaUs?: string | null
  ipaUk?: string | null
  pronunciations: PronunciationVO[]
  hasVerifiedPronunciation: boolean
  learningStatus: LearningStatus
  favorite: boolean
  hasReviewCard: boolean
  dueAt?: string | null
  reviewCount: number
}

export interface TopicDetailVO {
  domain: string
  total: number
  learned: number
  due: number
  terms: TopicTermVO[]
}

/* ============================ P2 划词与提词 ============================ */

/** 划词查询结果：rejectedReason 非空表示选区过长/跨段落，不执行查询 */
export interface LookupResultVO {
  query: string
  normalized?: string | null
  rejectedReason?: string | null
  /** true 表示同一词形存在多个义项，需要用户自行选择 */
  ambiguous: boolean
  matches: TermDetail[]
}

/** 候选提取条目（尚未落库） */
export interface ExtractedCandidateVO {
  headword: string
  termType: TermType
  domain: string
  fullForm?: string | null
  meaningCn?: string | null
  definitionCn?: string | null
  sourceContext?: string | null
  confidence?: number | null
  /** RULE / AI：如实说明本次提取方式 */
  extractor: 'RULE' | 'AI'
  existsInLibrary: boolean
  duplicateTermId?: number | null
  duplicateHeadword?: string | null
}

export interface ExtractionResultVO {
  extractor: 'RULE' | 'AI'
  aiAvailable: boolean
  degradationNote?: string | null
  totalScanned: number
  truncated: boolean
  items: ExtractedCandidateVO[]
}

export type CandidateReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** 个人/管理端候选词条 */
export interface CandidateVO {
  id: number
  headword: string
  termType: TermType
  domain: string
  fullForm?: string | null
  meaningCn?: string | null
  definitionCn?: string | null
  sourceType: string
  sourceId?: number | null
  sourceTitle?: string | null
  sourceContext?: string | null
  confidence?: number | null
  extractor: 'RULE' | 'AI'
  reviewStatus: CandidateReviewStatus
  duplicateTermId?: number | null
  termId?: number | null
  reviewNote?: string | null
}

export interface CandidateInput {
  headword: string
  termType: TermType
  domain: string
  fullForm?: string | null
  meaningCn?: string | null
  definitionCn?: string | null
  sourceType: string
  sourceId?: number | null
  sourceTitle?: string | null
  sourceContext?: string | null
  confidence?: number | null
  extractor: 'RULE' | 'AI'
}

export interface CandidateSaveResultVO {
  saved: number
  skipped: number
  items: CandidateVO[]
}

/** 提取请求：正文、文档、笔记三选一 */
export interface ExtractInput {
  text?: string
  documentId?: number
  noteId?: number
  scope?: 'ARTICLE' | 'SELECTION'
  types?: TermType[]
  limit?: number
  useAi?: boolean
}

export const techEnglishApi = {
  /** 公开词库检索（仅 PUBLISHED） */
  searchTerms(params: TermSearchParams) {
    return apiGet<PageResult<TermSummary>>('/tech-english/terms', params)
  },
  /** 词条详情；登录时带出个人收藏与学习状态 */
  getTerm(id: number) {
    return apiGet<TermDetail>(`/tech-english/terms/${id}`)
  },
  getPronunciations(id: number) {
    return apiGet<PronunciationVO[]>(`/tech-english/terms/${id}/pronunciations`)
  },
  getFilters() {
    return apiGet<FiltersVO>('/tech-english/filters')
  },
  /** V3 专题概览（匿名可读） */
  getTopics() {
    return apiGet<TopicVO[]>('/tech-english/topics')
  },
  /** V3 专题内词条与学习状态 */
  getTopic(domain: string) {
    return apiGet<TopicDetailVO>(`/tech-english/topics/${encodeURIComponent(domain)}`)
  },
  /** 我的收藏 / 学习记录（需登录） */
  getMyTerms(params: MyTermParams) {
    return apiGet<PageResult<MyTermVO>>('/tech-english/me/terms', params)
  },
  /** 幂等更新收藏与学习状态（需登录） */
  updateUserState(id: number, input: { favorite?: boolean; learningStatus?: LearningStatus }) {
    return apiPut<UserStateVO>(`/tech-english/me/terms/${id}`, input)
  },

  /* ---------- P1：加入复习 / 复习队列 / 评分 / 看板（均需登录） ---------- */

  /** 加入复习：同一词条只创建一张复习卡，重复调用返回 created=false */
  addToReview(id: number) {
    return apiPost<AddReviewResultVO>(`/tech-english/me/terms/${id}/review-card`)
  },
  /** 今日复习队列：到期优先，新词补充 */
  getReviewQueue(limit?: number) {
    return apiGet<ReviewQueueVO>('/tech-english/me/review-queue', { limit })
  },
  /** 提交复习评分：复用既有 FSRS 调度与 requestId 幂等协议 */
  submitReview(id: number, input: ReviewSubmitInput) {
    return apiPost<ReviewReceiptVO>(`/tech-english/me/terms/${id}/review`, input)
  },
  /** 个人技术英语学习看板 */
  getProgress() {
    return apiGet<VocabProgressVO>('/tech-english/me/progress')
  },

  /* ---------- P2：划词查询 / 个人候选（候选接口需登录） ---------- */

  /** 划词查询：只发送选区文本，不发送整篇文档 */
  lookupTerm(q: string, contextDomain?: string) {
    return apiGet<LookupResultVO>('/tech-english/lookup', { q, contextDomain })
  },
  /** 提取候选术语：未配置模型时服务端如实降级为规则提取 */
  extractCandidates(input: ExtractInput) {
    return apiPost<ExtractionResultVO>('/tech-english/me/candidates/extract', input)
  },
  /** 保存个人候选（服务端按用户+词形+来源去重） */
  saveCandidates(candidates: CandidateInput[]) {
    return apiPost<CandidateSaveResultVO>('/tech-english/me/candidates', { candidates })
  },
  getMyCandidates(status?: CandidateReviewStatus) {
    return apiGet<CandidateVO[]>('/tech-english/me/candidates', { status })
  },
}

export const adminTechEnglishApi = {
  /** 管理端检索：包含草稿与归档 */
  listTerms(params: TermSearchParams) {
    return apiGet<PageResult<TermSummary>>('/admin/tech-english/terms', params)
  },
  getTerm(id: number) {
    return apiGet<TermDetail>(`/admin/tech-english/terms/${id}`)
  },
  createTerm(input: TermInput) {
    return apiPost<TermDetail>('/admin/tech-english/terms', input)
  },
  updateTerm(id: number, input: TermInput) {
    return apiPut<TermDetail>(`/admin/tech-english/terms/${id}`, input)
  },
  /** 导入预览：只校验与统计，不写库 */
  previewImport(terms: TermInput[]) {
    return apiPost<ImportPreviewVO>('/admin/tech-english/import-preview', { terms })
  },
  /** 确认导入：同一 token 幂等，重复确认返回 replayed=true */
  confirmImport(token: string) {
    return apiPost<ImportResultVO>('/admin/tech-english/import-confirm', { token })
  },
  /** P2：候选词条列表（默认待审核） */
  listCandidates(status: CandidateReviewStatus = 'PENDING') {
    return apiGet<CandidateVO[]>('/admin/tech-english/candidates', { status })
  },
  /** P2：审核候选；approve=false 为拒绝，publish=true 审核通过即发布 */
  reviewCandidate(id: number, input: { approve: boolean; publish?: boolean; meaningCn?: string | null; domain?: string | null; reviewNote?: string | null }) {
    return apiPost<CandidateVO>(`/admin/tech-english/candidates/${id}/review`, input)
  },
}
