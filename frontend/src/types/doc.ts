export interface Category {
  id: string
  name: string
  icon?: string
  description?: string
  docCount: number
  parentId?: string
  children?: Category[]
  createdAt: string
  updatedAt: string
}

export interface Doc {
  id: string
  title: string
  summary: string
  content?: string
  categoryId: string
  categoryName?: string
  tags: string[]
  author: string
  authorAvatar?: string
  viewCount: number
  likeCount: number
  collectCount: number
  wordCount?: number
  readTime?: number
  status: 'draft' | 'published' | 'archived'
  createdAt: string
  updatedAt: string
}

export interface RecentDoc extends Doc {
  readProgress: number
  lastReadAt: string
}

export interface TocItem {
  id: string
  text: string
  level: number
  children?: TocItem[]
}

export interface DailyQuote {
  id: string
  content: string
  author: string
}

export interface LearningOverview {
  studyHours: number
  readDocs: number
  streakDays: number
  favorites: number
}

export interface DocListParams {
  page?: number
  pageSize?: number
  categoryId?: string
  keyword?: string
  tag?: string
  status?: Doc['status']
}

export interface DocListResponse {
  list: Doc[]
  total: number
  page: number
  pageSize: number
}

export interface FlashCard {
  id: string
  front: string
  back: string
  docId?: string
  categoryId?: string
  status: 'new' | 'learning' | 'reviewing' | 'mastered'
  nextReviewAt?: string
  reviewCount: number
  createdAt: string
  updatedAt: string
}

export interface LearningPath {
  id: string
  title: string
  description: string
  coverImage?: string
  categoryId?: string
  totalDocs: number
  completedDocs: number
  estimatedTime: number
  difficulty: 'beginner' | 'intermediate' | 'advanced'
  createdAt: string
  updatedAt: string
}

export interface ReviewPlan {
  id: string
  title: string
  description: string
  cardCount: number
  completedCount: number
  startTime?: string
  endTime?: string
  status: 'pending' | 'in_progress' | 'completed'
  createdAt: string
}
