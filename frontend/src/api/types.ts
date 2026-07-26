// 后端接口数据类型（与 com.knowflow.vo / dto 对齐）

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

// ===== 用户 / 鉴权 =====
export interface UserVO {
  id: number
  username: string
  email?: string
  nickname?: string
  avatar?: string
  role?: string
  totalStudyHours?: number
  readDocsCount?: number
  streakDays?: number
  favoriteCount?: number
  level?: number
  exp?: number
  energy?: number
  createTime?: string
}

export interface UserStatsVO {
  userId: number
  totalStudyHours?: number
  readDocsCount?: number
  streakDays?: number
  favoriteCount?: number
  level?: number
  exp?: number
  energy?: number
  completedPaths?: number
  totalFlashcards?: number
}

export interface LoginResult {
  token: string
  user: UserVO
}

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  email?: string
  password: string
  nickname?: string
}

export interface UpdateProfilePayload {
  nickname?: string
  email?: string
  avatar?: string
}

// ===== 文档 =====
export interface DocVO {
  id: number
  title: string
  summary?: string
  cover?: string
  categoryId?: number
  categoryName?: string
  tags?: string
  viewCount?: number
  readCount?: number
  favoriteCount?: number
  wordCount?: number
  difficulty?: number
  status?: number
  createTime?: string
  favoriteTime?: string
}

export interface DocDetailVO extends DocVO {
  content?: string
  favorite?: boolean
  readProgress?: number
  author?: string
}

export interface DocPageResult {
  records: DocVO[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export interface DocQuery {
  keyword?: string
  categoryId?: number
  difficulty?: number
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface ReadProgressPayload {
  docId: number
  progress?: number
  readSeconds?: number
}

export interface DocInput {
  title: string
  content?: string
  summary?: string
  cover?: string
  categoryId?: number
  categoryPath?: string
  tags?: string
  difficulty?: number
  status?: number
}

// ===== 分类 =====
export interface CategoryVO {
  id: number
  name: string
  code?: string
  parentId?: number
  icon?: string
  description?: string
  sortOrder?: number
  docCount?: number
  createTime?: string
  memberCount?: number
  storageSize?: string
  children?: CategoryVO[]
}

export interface CategoryInput {
  name: string
  code?: string
  parentId?: number
  icon?: string
  description?: string
  sortOrder?: number
  status?: number
}

// ===== 学习 =====
export interface LearningPathVO {
  id: number
  title: string
  description?: string
  cover?: string
  level?: string
  chapterCount?: number
  totalDuration?: number
  enrolledCount?: number
  sortOrder?: number
  status?: number
  createTime?: string
}

export interface LearningChapterVO {
  id: number
  pathId: number
  title: string
  content?: string
  sortOrder?: number
  duration?: number
  docIds?: string
  flashcardIds?: string
  completed?: boolean
}

export interface FlashcardVO {
  id: number
  pathId?: number
  chapterId?: number
  front?: string
  back?: string
  category?: string
  difficulty?: number
  reviewCount?: number
  // 后端实体存在，但当前 VO 不一定下发；前端用 reviewCount 在本地推算复习间隔
  reviewInterval?: number
  nextReviewTime?: string
  lastReviewTime?: string
}

export interface LearningTaskVO {
  id: number
  title: string
  description?: string
  type?: string
  targetId?: number
  expReward?: number
  energyCost?: number
  deadline?: string
  status?: number
}

export interface LearningPathInput {
  title: string
  description?: string
  cover?: string
  level?: string
  chapterCount?: number
  totalDuration?: number
  sortOrder?: number
  status?: number
}

export interface ChapterInput {
  pathId: number
  title: string
  content?: string
  sortOrder?: number
  duration?: number
  docIds?: string
  flashcardIds?: string
}

export interface FlashcardInput {
  pathId?: number
  chapterId?: number
  front: string
  back: string
  category?: string
  difficulty?: number
}

// ===== 对话 / 消息 =====
export interface ConversationVO {
  id: number
  title?: string
  messageCount?: number
  lastMessage?: string
  createTime?: string
  updateTime?: string
}

export interface MessageVO {
  id: number
  conversationId: number
  role: string
  content: string
  docReferences?: string
  createTime?: string
}

export interface ChatSendPayload {
  conversationId?: number
  content: string
}

// ===== 错题 =====
export interface MistakeVO {
  id: number
  userId: number
  question: string
  wrongAnswer?: string
  correctAnswer?: string
  category?: string
  difficulty?: number
  reviewCount?: number
  lastReviewTime?: string
  mastered?: number
  source?: string
  createTime?: string
}

export interface MistakePageResult {
  records: MistakeVO[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export interface MistakeStats {
  total: number
  mastered: number
  pending: number
}

// ===== 社区 =====
export interface PostVO {
  id: number
  userId: number
  username?: string
  nickname?: string
  title: string
  content?: string
  category?: string
  tags?: string
  likeCount?: number
  commentCount?: number
  viewCount?: number
  isEssence?: number
  status?: number
  createTime?: string
}

export interface PostPageResult {
  records: PostVO[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

// ===== 社区评论 =====
export interface CommentVO {
  id: number
  postId?: number
  userId?: number
  content?: string
  username?: string
  nickname?: string
  avatar?: string
  createTime?: string
}

export interface CommentPageResult {
  records: CommentVO[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

// ===== 消息通知 =====
export interface NotificationVO {
  id: number
  userId: number
  type: string
  title: string
  content?: string
  isRead?: number
  relatedId?: number
  relatedType?: string
  createTime?: string
}

export interface NotificationPageResult {
  records: NotificationVO[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

// ===== 收藏 =====
export interface FavoriteItem {
  id: number
  type: 'doc' | 'flashcard' | 'path' | 'note'
  title: string
  source?: string
  favoriteTime?: string
}

// ===== 管理后台概览 =====
/** 用户增长趋势单点。 */
export interface UserGrowthPoint {
  day: string
  newUsers: number
  totalUsers: number
}

/** 内容健康度指标。 */
export interface HealthMetric {
  label: string
  value: number
  level: 'good' | 'warn' | 'bad'
  detail: string
  icon: string
}

/** 最近活动流单条记录。 */
export interface RecentActivity {
  id: number
  userName: string
  action: string
  time: string
  type: string
}

export interface AdminOverviewVO {
  totalUsers: number
  totalDocs: number
  totalCategories: number
  totalConversations: number
  totalLearningPaths: number
  todayActiveUsers?: number
  todayNewUsers?: number
  todayNewDocs?: number
  firstUserDate?: string
  userGrowth?: UserGrowthPoint[]
  healthMetrics?: HealthMetric[]
  recentActivities?: RecentActivity[]
}
