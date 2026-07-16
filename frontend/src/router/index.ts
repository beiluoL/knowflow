import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
  },
  {
    path: '/doc/:id',
    name: 'DocDetail',
    component: () => import('@/views/DocDetail.vue'),
  },
  {
    path: '/categories',
    name: 'Categories',
    component: () => import('@/views/Categories.vue'),
  },
  {
    path: '/search',
    name: 'SearchResult',
    component: () => import('@/views/SearchResult.vue'),
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
  },
  {
    path: '/upload',
    name: 'UploadDoc',
    component: () => import('@/views/UploadDoc.vue'),
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
  },
  {
    path: '/admin/overview',
    name: 'AdminOverview',
    component: () => import('@/views/admin/Overview.vue'),
  },
  {
    path: '/admin/docs',
    name: 'AdminDocs',
    component: () => import('@/views/admin/DocManagement.vue'),
  },
  {
    path: '/admin/knowledge',
    name: 'AdminKnowledge',
    component: () => import('@/views/admin/KnowledgeMgmt.vue'),
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/admin/UserManagement.vue'),
  },
  {
    path: '/admin/chat-config',
    name: 'AdminChatConfig',
    component: () => import('@/views/admin/ChatConfig.vue'),
  },
  {
    path: '/admin/flashcards',
    name: 'AdminFlashcards',
    component: () => import('@/views/admin/FlashcardMgmt.vue'),
  },
  {
    path: '/learning',
    name: 'Learning',
    redirect: '/learning/center',
  },
  {
    path: '/learning/center',
    name: 'LearningCenter',
    component: () => import('@/views/LearningCenter.vue'),
  },
  {
    path: '/learning/paths',
    name: 'LearningPaths',
    component: () => import('@/views/LearningPaths.vue'),
  },
  {
    path: '/learning/path/:id',
    name: 'PathDetail',
    component: () => import('@/views/PathDetail.vue'),
  },
  {
    path: '/learning/chapter/:id',
    name: 'ChapterLearn',
    component: () => import('@/views/ChapterLearn.vue'),
  },
  {
    path: '/learning/flashcards',
    name: 'FlashCards',
    component: () => import('@/views/FlashCards.vue'),
  },
  {
    path: '/learning/review',
    name: 'ReviewPlan',
    component: () => import('@/views/ReviewPlan.vue'),
  },
  {
    path: '/learning/mode',
    name: 'LearningMode',
    component: () => import('@/views/LearningMode.vue'),
  },
  {
    path: '/learning/code-practice',
    name: 'CodePractice',
    component: () => import('@/views/CodePractice.vue'),
  },
  {
    path: '/learning/quiz',
    name: 'SmartQuiz',
    component: () => import('@/views/SmartQuiz.vue'),
  },
  {
    path: '/learning/writing',
    name: 'SmartWriting',
    component: () => import('@/views/SmartWriting.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
