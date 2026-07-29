// 管理后台请求层：封装概览、文档/用户/分类/闪卡/学习路径管理等接口调用。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  AdminOverviewVO,
  CategoryVO,
  CategoryInput,
  DocPageResult,
  DocInput,
  UserVO,
  LearningPathVO,
  LearningPathInput,
  LearningChapterVO,
  ChapterInput,
  LearningFlashcard,
  FlashcardInput,
  CodeQuestionVO,
  CodeQuestionInput,
} from './types'

/** 智能题库题目 VO */
export interface QuizQuestionVO {
  id: number
  title: string
  content: string
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'FILL_BLANK' | 'TRUE_FALSE' | 'SHORT_ANSWER'
  options?: string | null
  answer: string
  explanation?: string
  difficulty: number
  categoryId?: number
  docId?: number
  tags?: string
  source: 'AI' | 'MANUAL'
  status: number
  sortOrder?: number
  createTime?: string
  updateTime?: string
}

/** 智能题库题目输入 */
export interface QuizQuestionInput {
  title: string
  content: string
  questionType: string
  options?: string | null
  answer: string
  explanation?: string
  difficulty?: number
  categoryId?: number
  docId?: number
  tags?: string
  source?: string
  status?: number
  sortOrder?: number
}

export interface IconVO {
  id: number
  name: string
  type: string // 'custom' | 'iconfont' | 'svg'
  content: string
  color?: string
  createTime?: string
}

export interface PageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  role?: string
  status?: number
  categoryId?: number
  difficulty?: number
}

export interface AiGeneratePathPayload {
  topic: string
  description?: string
  level?: string
  chapterCount?: number
  categoryId?: number
}

/** 知识库成员 VO（与后端 AdminKbMemberController.MemberVO 对齐） */
export interface KbMemberVO {
  id: number
  categoryId: number
  userId?: number
  username?: string
  nickname?: string
  email?: string
  avatar?: string
  role: 'OWNER' | 'EDITOR' | 'READER'
  inviteCode?: string
  inviteEmail?: string
  status: number
  joinTime?: string
  createTime?: string
}

/** 新增/邀请知识库成员请求体 */
export interface AddKbMemberReq {
  categoryId: number
  userId?: number
  keyword?: string
  email?: string
  role?: 'OWNER' | 'EDITOR' | 'READER'
}

/** 批量导入结果 VO */
export interface ImportResultVO {
  total: number
  success: number
  failed: number
  successNames: string[]
  failedNames: string[]
}

export const adminApi = {
  overview: () => apiGet<AdminOverviewVO>('/admin/overview'),

  // 文档管理
  docs: (params: PageQuery = {}) => apiGet<DocPageResult>('/admin/docs', params),
  createDoc: (data: DocInput) => apiPost<void>('/admin/docs', data),
  updateDoc: (id: number, data: DocInput) => apiPut<void>(`/admin/docs/${id}`, data),
  removeDoc: (id: number) => apiDelete<void>(`/admin/docs/${id}`),
  publishDoc: (id: number) => apiPut<void>(`/admin/docs/${id}/publish`),
  draftDoc: (id: number) => apiPut<void>(`/admin/docs/${id}/draft`),
  deprecateDoc: (id: number) => apiPut<void>(`/admin/docs/${id}/deprecate`),
  // 批量操作
  batchDeleteDocs: (ids: number[]) =>
    apiDelete<void>('/admin/docs/batch', undefined, { data: ids }),
  batchMoveDocs: (data: { docIds: number[]; categoryId: number }) =>
    apiPut<void>('/admin/docs/batch/move', data),

  // 用户管理
  users: (params: PageQuery = {}) => apiGet<{ records: UserVO[]; total: number }>('/admin/users', params),
  createUser: (data: Partial<UserVO> & { password?: string }) => apiPost<void>('/admin/users', data),
  updateUser: (id: number, data: Partial<UserVO>) => apiPut<void>(`/admin/users/${id}`, data),
  removeUser: (id: number) => apiDelete<void>(`/admin/users/${id}`),

  // 分类管理
  categories: () => apiGet<CategoryVO[]>('/admin/categories'),
  categoryTree: () => apiGet<CategoryVO[]>('/admin/categories/tree'),
  createCategory: (data: CategoryInput) => apiPost<CategoryVO>('/admin/categories', data),
  updateCategory: (id: number, data: CategoryInput) => apiPut<void>(`/admin/categories/${id}`, data),
  removeCategory: (id: number) => apiDelete<void>(`/admin/categories/${id}`),

  // 闪卡管理
  flashcards: (params: PageQuery = {}) =>
    apiGet<{ records: unknown[]; total: number }>('/admin/learning/flashcards', params),
  createFlashcard: (data: FlashcardInput) => apiPost<void>('/admin/learning/flashcards', data),
  updateFlashcard: (id: number, data: FlashcardInput) => apiPut<void>(`/admin/learning/flashcards/${id}`, data),
  removeFlashcard: (id: number) => apiDelete<void>(`/admin/learning/flashcards/${id}`),

  // 学习路径管理
  learningPaths: () => apiGet<LearningPathVO[]>('/admin/learning/paths'),
  createLearningPath: (data: LearningPathInput) => apiPost<LearningPathVO>('/admin/learning/paths', data),
  updateLearningPath: (id: number, data: LearningPathInput) => apiPut<void>(`/admin/learning/paths/${id}`, data),
  removeLearningPath: (id: number) => apiDelete<void>(`/admin/learning/paths/${id}`),
  publishLearningPath: (id: number) => apiPut<void>(`/admin/learning/paths/${id}/publish`),
  unpublishLearningPath: (id: number) => apiPut<void>(`/admin/learning/paths/${id}/unpublish`),

  // 章节管理
  learningChapters: (pathId?: number) =>
    apiGet<LearningChapterVO[]>('/admin/learning/chapters', { pathId }),
  createChapter: (data: ChapterInput) => apiPost<LearningChapterVO>('/admin/learning/chapters', data),
  updateChapter: (id: number, data: ChapterInput) => apiPut<void>(`/admin/learning/chapters/${id}`, data),
  removeChapter: (id: number) => apiDelete<void>(`/admin/learning/chapters/${id}`),

  // AI 自动生成
  aiGeneratePath: (data: AiGeneratePathPayload) =>
    apiPost<LearningPathVO>('/admin/learning/ai/generate-path', data),
  aiGenerateChapterContent: (id: number, docIds?: number[]) =>
    apiPost<LearningChapterVO>(`/admin/learning/chapters/${id}/ai-generate-content`, docIds ? { docIds } : undefined),
  aiGenerateFlashcards: (pathId: number) =>
    apiPost<LearningFlashcard[]>(`/admin/learning/paths/${pathId}/ai-generate-flashcards`),
  // AI 从知识库/文档生成知识卡片（不依赖学习路径）
  aiGenerateFlashcardsFromKb: (data: { categoryId?: number; docId?: number; count?: number }) =>
    apiPost<LearningFlashcard[]>('/admin/learning/flashcards/ai-generate', data),

  // 知识库与文档（用于AI生成时选择）
  learningCategories: () => apiGet<CategoryVO[]>('/admin/learning/categories'),
  learningDocs: (categoryId?: number, limit?: number) =>
    apiGet<DocVO[]>('/admin/learning/docs', { categoryId, limit }),

  // 图标管理
  icons: () => apiGet<IconVO[]>('/admin/icons'),
  createIcon: (data: { name: string; type: string; content: string; color?: string }) => apiPost<IconVO>('/admin/icons', data),
  deleteIcon: (id: number) => apiDelete<void>(`/admin/icons/${id}`),

  // 代码题库管理
  codeQuestions: (params: { keyword?: string; difficulty?: number; language?: string; status?: number } = {}) =>
    apiGet<CodeQuestionVO[]>('/admin/code-questions', params),
  codeQuestionDetail: (id: number) => apiGet<CodeQuestionVO>(`/admin/code-questions/${id}`),
  createCodeQuestion: (data: CodeQuestionInput) => apiPost<CodeQuestionVO>('/admin/code-questions', data),
  updateCodeQuestion: (id: number, data: CodeQuestionInput) => apiPut<void>(`/admin/code-questions/${id}`, data),
  removeCodeQuestion: (id: number) => apiDelete<void>(`/admin/code-questions/${id}`),
  publishCodeQuestion: (id: number) => apiPut<void>(`/admin/code-questions/${id}/publish`),
  unpublishCodeQuestion: (id: number) => apiPut<void>(`/admin/code-questions/${id}/unpublish`),

  // ========== 知识库成员与权限 ==========
  kbMembers: (categoryId: number) => apiGet<KbMemberVO[]>(`/admin/kb-members/category/${categoryId}`),
  searchKbMembers: (categoryId: number, keyword: string) =>
    apiGet<KbMemberVO[]>(`/admin/kb-members/search-users`, { categoryId, keyword }),
  addKbMember: (data: AddKbMemberReq) => apiPost<KbMemberVO>('/admin/kb-members', data),
  changeKbMemberRole: (memberId: number, role: string) =>
    apiPut<void>(`/admin/kb-members/${memberId}/role`, { role }),
  removeKbMember: (memberId: number) => apiDelete<void>(`/admin/kb-members/${memberId}`),

  // ========== 知识库：批量导入 / 导出 ==========
  importKbDocs: (categoryId: number, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return apiPost<ImportResultVO>(`/admin/categories/${categoryId}/import-docs`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    })
  },
  exportKb: (categoryId: number, format: 'zip' = 'zip') => {
    const url = `/api/admin/categories/${categoryId}/export?format=${format}`
    const token = localStorage.getItem('token')
    return fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  },

  // ========== 智能题库管理 ==========
  quizQuestions: (params: {
    page?: number; pageSize?: number; keyword?: string;
    questionType?: string; difficulty?: number; source?: string; status?: number; categoryId?: number
  } = {}) => apiGet<{ records: QuizQuestionVO[]; total: number }>('/admin/quiz-questions', params),
  quizQuestionDetail: (id: number) => apiGet<QuizQuestionVO>(`/admin/quiz-questions/${id}`),
  createQuizQuestion: (data: QuizQuestionInput) => apiPost<QuizQuestionVO>('/admin/quiz-questions', data),
  updateQuizQuestion: (id: number, data: QuizQuestionInput) => apiPut<void>(`/admin/quiz-questions/${id}`, data),
  removeQuizQuestion: (id: number) => apiDelete<void>(`/admin/quiz-questions/${id}`),
  publishQuizQuestion: (id: number) => apiPut<void>(`/admin/quiz-questions/${id}/publish`),
  unpublishQuizQuestion: (id: number) => apiPut<void>(`/admin/quiz-questions/${id}/unpublish`),
  aiGenerateQuizQuestions: (data: { categoryId?: number; docId?: number; questionType?: string; count?: number }) =>
    apiPost<QuizQuestionVO[]>('/admin/quiz-questions/ai-generate', data, { timeout: 120000 }),
}
