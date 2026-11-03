// Voice Lab 路由：作为独立 Mini App 注册，复用 C 端壳层。
import type { RouteRecordRaw } from 'vue-router'

export const voiceLabRoutes: RouteRecordRaw[] = [
  {
    path: '/voice-lab',
    name: 'VoiceLab',
    component: () => import('@/views/voice/VoiceLabHome.vue'),
    meta: { layout: 'c', requiresAuth: true, appKey: 'voice-lab' },
  },
  {
    path: '/voice-lab/chat',
    name: 'VoiceLabChat',
    component: () => import('@/views/voice/VoiceLabChat.vue'),
    meta: { layout: 'c', requiresAuth: true, appKey: 'voice-lab' },
  },
  {
    path: '/voice-lab/interview',
    name: 'VoiceLabInterview',
    component: () => import('@/views/voice/VoiceLabInterview.vue'),
    meta: { layout: 'c', requiresAuth: true, appKey: 'voice-lab' },
  },
  {
    path: '/voice-lab/settings',
    name: 'VoiceLabSettings',
    component: () => import('@/views/voice/VoiceLabSettings.vue'),
    meta: { layout: 'c', requiresAuth: true, appKey: 'voice-lab' },
  },
  {
    path: '/voice-lab/history',
    name: 'VoiceLabHistory',
    component: () => import('@/views/voice/VoiceLabHistory.vue'),
    meta: { layout: 'c', requiresAuth: true, appKey: 'voice-lab' },
  },
]
