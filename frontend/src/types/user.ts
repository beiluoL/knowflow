export interface User {
  id: string
  username: string
  email: string
  avatar?: string
  nickname?: string
  bio?: string
  role: 'user' | 'admin'
  stats: UserStats
  createdAt: string
  updatedAt: string
}

export interface UserStats {
  totalDocs: number
  totalFlashcards: number
  totalReviews: number
  studyDays: number
  streakDays: number
  level: number
  experience: number
}

export interface Notification {
  id: string
  title: string
  content: string
  type: 'system' | 'reminder' | 'achievement'
  read: boolean
  createdAt: string
  link?: string
}

export interface LoginParams {
  email: string
  password: string
}

export interface RegisterParams {
  username: string
  email: string
  password: string
  confirmPassword: string
}

export interface LoginResponse {
  token: string
  user: User
}
