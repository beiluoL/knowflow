// 前端静态假数据（当前用户与通知），用于演示与离线开发。
import type { User, Notification } from '@/types'

export const mockUser: User = {
  id: '1',
  username: 'zhangsan',
  email: 'zhangsan@example.com',
  avatar: '',
  nickname: '张三',
  bio: '热爱技术，持续学习中~',
  role: 'user',
  stats: {
    totalDocs: 56,
    totalFlashcards: 328,
    totalReviews: 1256,
    studyDays: 89,
    streakDays: 15,
    level: 6,
    experience: 2450,
  },
  createdAt: '2024-01-15T08:00:00Z',
  updatedAt: '2024-07-15T10:30:00Z',
}

export const mockNotifications: Notification[] = [
  {
    id: '1',
    title: '复习提醒',
    content: '你今天有 20 张闪卡需要复习，快来完成今天的学习任务吧！',
    type: 'reminder',
    read: false,
    createdAt: '2024-07-16T08:00:00Z',
    link: '/review',
  },
  {
    id: '2',
    title: '成就解锁',
    content: '恭喜你！连续学习 15 天，获得「坚持不懈」徽章。',
    type: 'achievement',
    read: false,
    createdAt: '2024-07-15T18:30:00Z',
    link: '/profile/achievements',
  },
  {
    id: '3',
    title: '系统通知',
    content: '知识库 v2.0 版本已上线，新增 AI 问答功能，快来体验吧！',
    type: 'system',
    read: true,
    createdAt: '2024-07-14T10:00:00Z',
  },
  {
    id: '4',
    title: '学习路径更新',
    content: '你订阅的「前端工程师进阶路径」已更新 3 篇新文档。',
    type: 'reminder',
    read: true,
    createdAt: '2024-07-13T14:20:00Z',
    link: '/learning-paths/1',
  },
  {
    id: '5',
    title: '闪卡复习完成',
    content: '太棒了！你已完成本周所有闪卡复习任务。',
    type: 'achievement',
    read: true,
    createdAt: '2024-07-12T20:00:00Z',
  },
]
