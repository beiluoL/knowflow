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
  bio?: string
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
  displayName?: string
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
  content?: string
  cover?: string
  /** 原始文件名（上传型文档有值，含扩展名）。 */
  fileName?: string
  /** 原文件访问地址（上传型文档有值，纯文本创建型为 undefined）。用于前端判断文档类型。 */
  fileUrl?: string
  /** 原始文件字节大小（上传型文档有值）。 */
  fileSize?: number
  icon?: string
  categoryId?: number
  categoryName?: string
  author?: string
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
  /** 原始文件访问路径（/uploads/...），可空；用于原文下载/预览。 */
  fileUrl?: string
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
  icon?: string
  categoryId?: number
  categoryPath?: string
  tags?: string
  difficulty?: number
  status?: number
}

/** 文件型文档上传的元信息（与原始文件一同通过 multipart 提交）。 */
export interface DocUploadMeta {
  title: string
  summary?: string
  tags?: string
  categoryId?: number
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
  categoryName?: string
  completedChapters?: number
  estimatedHours?: number
  chapterCount?: number
  totalDuration?: number
  enrolledCount?: number
  /** 当前登录用户是否已报名该路径（详情页报名按钮状态用）。 */
  enrolled?: boolean
  /** 当前登录用户的学习进度百分比（0~100）；未报名时为 undefined。 */
  progress?: number
  sortOrder?: number
  status?: number
  createTime?: string
}

export interface LearningChapterVO {
  id: number
  pathId: number
  title: string
  content?: string
  description?: string
  sortOrder?: number
  duration?: number
  docIds?: string
  flashcardIds?: string
  completed?: boolean
  /** 是否锁定：存在未完成的前置章节时为 true（L-PATH 前置解锁）。 */
  locked?: boolean
  /** 前置章节 ID 列表，逗号分隔。 */
  prerequisiteChapterIds?: string
  /** L-FORM-01 视频观看进度百分比（0-100），用于恢复播放位置。 */
  videoProgress?: number
}

/** G-CERT-01 数字证书：路径完成后自动颁发的证书信息。 */
export interface LearningCertificateVO {
  id: number
  userId?: number
  pathId?: number
  /** 唯一证书验证码（可公开验证）。 */
  certNo?: string
  /** 路径标题快照。 */
  pathTitle?: string
  /** 持证用户名快照。 */
  userName?: string
  /** 颁发时间。 */
  issueDate?: string
  /** 是否当前用户本人持有。 */
  mine?: boolean
}

// ===== 学习路径 DAG（章节依赖关系图） =====
/** 节点状态：completed=已完成 / available=可学 / locked=未解锁。 */
export type ChapterNodeStatus = 'completed' | 'available' | 'locked'

export interface ChapterNodeVO {
  id: number
  title: string
  sortOrder?: number
  duration?: number
  status: ChapterNodeStatus
  prerequisiteChapterIds?: string
}

export interface ChapterEdgeVO {
  /** 前置章节 ID。 */
  source: number
  /** 依赖该前置的章节 ID。 */
  target: number
}

export interface ChapterDagVO {
  nodes: ChapterNodeVO[]
  edges: ChapterEdgeVO[]
}

export interface FlashcardVO {
  id: number
  userId?: number
  pathId?: number
  chapterId?: number
  categoryId?: number
  docId?: number
  front?: string
  back?: string
  category?: string
  difficulty?: number
  tags?: string
  sourceType?: 'MANUAL' | 'AI_DOC' | 'AI_KB' | 'IMPORT'
  reviewCount?: number
  reviewInterval?: number
  nextReviewTime?: string
  lastReviewTime?: string
  createTime?: string
  updateTime?: string
  categoryName?: string
  docTitle?: string
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

export interface LearningTaskInput {
  title: string
  description?: string
  type?: string
  deadline?: string
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
  categoryId?: number
  docId?: number
  front: string
  back: string
  category?: string
  difficulty?: number
  tags?: string
}

export interface FlashcardGenerateInput {
  categoryId?: number
  docId?: number
  count?: number
  difficultyPreference?: number
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
  /** 消息文本（与 images 至少提供一个） */
  content: string
  /** 指定对话模型（覆盖默认模型），由 /api/chat/models 下发 */
  model?: string
  /** 多模态图片 base64 数组（不含 data:image 前缀），后端支持 vision 模型识别 */
  images?: string[]
}

// ===== AI 文档增强 =====
/** 文档 AI 生成的复习闪卡（与后端 LearningFlashcard 实体对齐）。 */
export interface LearningFlashcard {
  id: number
  pathId?: number
  chapterId?: number
  front?: string
  back?: string
  category?: string
  difficulty?: number
  reviewCount?: number
  createdTime?: string
}

// ===== 学习可视化 =====
/** 单日学习活跃度（热力图单格）。 */
export interface DailyActivityVO {
  date: string
  count: number
}

/** 掌握分布看板数据。 */
export interface MasteryDistributionVO {
  flashcardTotal: number
  flashcardDiffEasy: number
  flashcardDiffMedium: number
  flashcardDiffHard: number
  flashcardDue: number
  flashcardReviewed: number
  mistakeMastered: number
  mistakePending: number
}

// ===== 知识图谱 =====
export interface GraphNodeVO {
  id: string
  label: string
  type: 'category' | 'doc'
  value: number
}

export interface GraphEdgeVO {
  source: string
  target: string
  relation: 'parent' | 'contains'
}

export interface KnowledgeGraphVO {
  nodes: GraphNodeVO[]
  edges: GraphEdgeVO[]
}

export interface TechNodeVO {
  id: string
  name: string
  category: 'LANGUAGE' | 'FRAMEWORK' | 'TOOL' | 'DATABASE' | 'ALGORITHM' | 'PLATFORM'
  categoryLabel: string
  description?: string
  difficulty?: number
  docCount?: number
}

export interface TechEdgeVO {
  source: string
  target: string
  relation: 'PREREQUISITE' | 'COMPONENT' | 'DEPENDS'
  strength?: number
  description?: string
}

export interface TechGraphVO {
  topic: string
  generatedAt: string
  nodes: TechNodeVO[]
  edges: TechEdgeVO[]
}

export interface ConceptDiagramVO {
  concept: string
  diagramType: 'FLOWCHART' | 'SEQUENCE' | 'CLASS' | 'ER' | 'PIE'
  mermaidCode: string
  description: string
  explanation: string
  difficulty?: number
  keyPoints?: string[]
  relatedConcepts?: string[]
  codeExample?: string
}

// ===== 实体关系知识图谱（A-RAG-04：AI 从文档抽取实体+关系） =====
export type KgEntityType = 'CONCEPT' | 'TECHNIQUE' | 'TERM' | 'PRINCIPLE' | 'TOOL' | 'OTHER'
export type KgRelationType = 'RELATED_TO' | 'PREREQUISITE' | 'IS_A' | 'PART_OF' | 'USES' | 'CONTRASTS'

export interface EntityNodeVO {
  id: number
  name: string
  type: KgEntityType
  description?: string
  categoryId?: number
  categoryName?: string
  weight?: number
}

export interface EntityEdgeVO {
  id: number
  source: number
  target: number
  relation: KgRelationType
  description?: string
  docId?: number
}

export interface EntityGraphVO {
  nodes: EntityNodeVO[]
  edges: EntityEdgeVO[]
  generatedAt?: string
}

export interface ExtractResultVO {
  docCount?: number
  entityCount?: number
  relationCount?: number
  message?: string
}

// ===== 个性化学习路径 =====
export interface RecommendChapter {
  title: string
  content: string
  duration: number
  sortOrder: number
  focus: string
  /** AI 推断的前置章节序号（引用同一规划中其它章节的 sortOrder），采用后落地为章节依赖 DAG */
  prerequisiteSortOrders?: number[]
}

export interface PersonalizedPathVO {
  id?: number
  title: string
  reason: string
  level: string
  totalDuration: number
  dailyDuration: number
  goals: string[]
  chapters: RecommendChapter[]
  advice: string
  relatedPathId?: number
  createTime?: string
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
  /** 本周新增错题数（周一至今） */
  weeklyNew: number
  /** 今日待复习数（未掌握且今日尚未复习） */
  dueToday: number
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

// ===== 用户 AI 配置 =====
export interface UserAiConfigVO {
  id?: number
  provider?: string
  apiKeyMasked?: string
  baseUrl?: string
  model?: string
  isActive?: number
}

export interface UserAiConfigPayload {
  provider: string
  apiKey: string
  baseUrl?: string
  model?: string
  isActive?: number
}

export interface PlatformModelVO {
  provider: string
  label: string
  baseUrl: string
  model: string
  subscriptionRequired: boolean
  priceInfo: string
}

// ===== 代码题库 =====
/** 单条测试用例（与后端 code_question.test_cases JSON 数组项对齐） */
export interface CodeTestCase {
  input: string
  expected: string
}

/** 代码题目 VO（与 com.knowflow.entity.CodeQuestion 对齐） */
export interface CodeQuestionVO {
  id: number
  title: string
  description?: string
  /** 难度：0 简单 / 1 中等 / 2 困难 */
  difficulty?: number
  /** 主语言：javascript / typescript / python / java / sql */
  language?: string
  tags?: string
  hint?: string
  exampleInput?: string
  exampleOutput?: string
  codeTemplate?: string
  /** 测试用例 JSON 字符串：[{input, expected}] */
  testCases?: string
  solutionHint?: string
  duration?: number
  sortOrder?: number
  /** 状态：0 草稿 / 1 已发布 */
  status?: number
  passCount?: number
  submitCount?: number
  createTime?: string
  updateTime?: string
}

/** 单条测试用例运行结果 */
export interface CodeTestCaseResult {
  passed: boolean
  input: string
  expected: string
  actual: string
  error?: string | null
}

/** 提交答案验证结果 */
export interface CodeSubmitResultVO {
  passed: boolean
  total: number
  passCount: number
  /** 累计提交次数 */
  submitCount?: number
  /** 累计通过次数 */
  passTotal?: number
  results?: CodeTestCaseResult[]
  /** 运行耗时（毫秒） */
  elapsed?: number
}

/** 题库管理：新增/编辑表单 */
export interface CodeQuestionInput {
  title: string
  description?: string
  difficulty?: number
  language?: string
  tags?: string
  hint?: string
  exampleInput?: string
  exampleOutput?: string
  codeTemplate?: string
  testCases?: string
  solutionHint?: string
  duration?: number
  sortOrder?: number
  status?: number
}

// ===== 成就/勋章系统 =====

/** 成就列表项（含用户解锁状态与进度） */
export interface AchievementItemVO {
  id: number
  code: string
  name: string
  description?: string
  icon: string
  category: string
  unlocked: boolean
  exp: number
  current: number
  target: number
  percent: number
  unlockedTime?: string | null
  rewardExp: number
}

/** 成就页整体数据（列表 + 统计 + 时间线） */
export interface AchievementPageVO {
  achievements: AchievementItemVO[]
  unlockedCount: number
  totalCount: number
  totalPercent: number
  totalAchievementExp: number
  recentUnlocks: RecentUnlockVO[]
}

/** 最近解锁时间线条目 */
export interface RecentUnlockVO {
  achievementId: number
  name: string
  description?: string
  icon: string
  category: string
  exp: number
  timeAgo: string
}

// ===== 全局排行榜 =====

/** 排行榜条目 */
export interface RankUserVO {
  rank: number
  userId: number
  nickname: string
  avatar?: string
  level?: number
  exp?: number
  streakDays?: number
  readDocsCount?: number
}

// ===== 编程挑战（闯关游戏化） =====

/** 挑战赛道列表项（含我的进度） */
export interface ChallengeVO {
  id: number
  title: string
  description?: string
  /** 主语言：javascript / typescript / python / java / sql */
  language?: string
  /** 难度：0 简单 / 1 中等 / 2 困难 */
  difficulty?: number
  /** 图标名（lucide） */
  icon?: string
  /** 主题色 */
  themeColor?: string
  tags?: string
  levelCount: number
  totalPoints: number
  playerCount?: number
  joined?: boolean
  clearedLevels: number
  earnedPoints: number
  earnedStars: number
  completed?: boolean
  /** 进度百分比 0-100 */
  progressPercent: number
}

/** 挑战关卡（含我的状态） */
export interface ChallengeLevelVO {
  id: number
  levelNo: number
  title: string
  description?: string
  difficulty?: number
  language?: string
  hint?: string
  exampleInput?: string
  exampleOutput?: string
  codeTemplate?: string
  /** 测试用例 JSON 字符串：[{input, expected}] */
  testCases?: string
  points: number
  locked: boolean
  passed: boolean
  /** 已获星级 0-3 */
  stars: number
  attempts: number
  pointsEarned: number
  /** 最近一次提交代码（恢复编辑器） */
  lastCode?: string
}

/** 挑战详情（含关卡地图） */
export interface ChallengeDetailVO {
  id: number
  title: string
  description?: string
  language?: string
  difficulty?: number
  icon?: string
  themeColor?: string
  tags?: string
  levelCount: number
  totalPoints: number
  joined?: boolean
  clearedLevels: number
  currentLevel: number
  earnedPoints: number
  earnedStars: number
  completed?: boolean
  levels: ChallengeLevelVO[]
}

/** 关卡提交结果 */
export interface ChallengeSubmitResultVO {
  passed: boolean
  firstPass: boolean
  /** 本关星级 0-3 */
  stars: number
  pointsEarned: number
  attempts: number
  passCount: number
  total: number
  totalStars: number
  totalPoints: number
  clearedLevels: number
  unlockedNext: boolean
  nextLevelNo?: number | null
  challengeCompleted: boolean
}

/** 排行榜条目 */
export interface ChallengeRankVO {
  rank: number
  userId: number
  nickname: string
  avatar?: string
  totalPoints: number
  totalStars: number
  clearedLevels: number
}

/** 我的挑战统计 */
export interface ChallengeStatsVO {
  joinedChallenges: number
  completedChallenges: number
  clearedLevels: number
  totalPoints: number
  totalStars: number
  myRank?: number | null
}

// ===== 学习小组 =====

/** 学习小组 VO */
export interface StudyGroupVO {
  id: number
  name: string
  description?: string
  icon?: string
  color?: string
  type: 'PUBLIC' | 'PRIVATE'
  ownerId: number
  ownerName?: string
  memberCount?: number
  announcement?: string
  learningPlanId?: number
  createTime?: string
  unreadCount?: number
  userRole?: 'OWNER' | 'ADMIN' | 'MEMBER'
}

/** 学习小组成员 VO */
export interface StudyGroupMemberVO {
  id: number
  groupId: number
  userId: number
  userName?: string
  userEmail?: string
  userAvatar?: string
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
  invitedByName?: string
  joinTime?: string
}

/** 学习小组消息 VO */
export interface GroupMessageVO {
  id: number
  groupId: number
  senderId: number
  senderName?: string
  senderAvatar?: string
  messageType: 'TEXT' | 'IMAGE' | 'FILE' | 'CODE'
  content?: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  codeLanguage?: string
  mentionUsers?: MentionedUser[]
  createTime?: string
  recalled?: boolean
  isMine?: boolean
  /** 对方是否已读（仅我发出的消息有意义） */
  read?: boolean
  /** 前端乐观发送状态（仅本地使用，服务端不返回） */
  sendStatus?: 'pending' | 'success' | 'error'
  /** 前端生成的临时 ID，用于乐观更新与去重（仅本地使用） */
  tempId?: string
  /** 发送失败时缓存的错误信息（仅本地使用） */
  errorMsg?: string
}

export interface MentionedUser {
  id: number
  name?: string
}

/** 创建小组请求 */
export interface StudyGroupCreatePayload {
  name: string
  description?: string
  icon?: string
  color?: string
  type?: 'PUBLIC' | 'PRIVATE'
  learningPlanId?: number
}

/** 发送消息请求 */
export interface GroupMessageSendPayload {
  groupId: number
  messageType?: 'TEXT' | 'IMAGE' | 'FILE' | 'CODE'
  content: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  codeLanguage?: string
  mentionUserIds?: number[]
}

/** 邀请成员请求 */
export interface GroupInvitePayload {
  groupId: number
  email?: string
  userId?: number
  role?: 'ADMIN' | 'MEMBER'
}

/** 分页消息结果 */
export interface GroupMessagePageResult {
  records: GroupMessageVO[]
  total: number
  current: number
  size: number
  pages: number
}

// ===== 单聊私信 =====

/** 私聊消息 VO */
export interface PrivateMessageVO {
  id: number
  conversationId: number
  senderId: number
  senderName?: string
  senderAvatar?: string
  messageType: 'TEXT' | 'IMAGE' | 'FILE' | 'CODE'
  content?: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  codeLanguage?: string
  createTime?: string
  recalled?: boolean
  isMine?: boolean
  /** 对方是否已读（仅我发出的消息有意义） */
  read?: boolean
}

/** 私聊会话 VO（面向当前用户，含对方信息） */
export interface PrivateConversationVO {
  id: number
  targetUserId: number
  targetUserName?: string
  targetUserAvatar?: string
  lastMessageId?: number
  lastMessageContent?: string
  lastMessageType?: string
  lastMessageTime?: string
  unreadCount?: number
  createTime?: string
}

/** 发送私聊消息请求 */
export interface PrivateMessageSendPayload {
  conversationId: number
  messageType?: 'TEXT' | 'IMAGE' | 'FILE' | 'CODE'
  content: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  codeLanguage?: string
}

/** 私聊分页消息结果 */
export interface PrivateMessagePageResult {
  records: PrivateMessageVO[]
  total: number
  current: number
  size: number
  pages: number
}
