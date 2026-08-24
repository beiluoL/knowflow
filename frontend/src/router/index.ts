// 前端路由表与登录守卫配置。
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { notify } from '@/utils/toast';

/**
 * 布局约定（与设计稿对齐）：
 * - meta.layout = 'c'  → C 端：固定顶部导航 + 全屏内容（无侧边栏）
 * - meta.layout = 'b'  → B 端：左侧边栏 + 顶部栏 + 可滚动内容
 * - meta.layout = 'none' / 缺省 → 无布局壳（登录/入口/404/沉浸式）
 */
const routes: RouteRecordRaw[] = [
  // ===== 通用系统页面（无布局） =====
  // UnifiedPortal 已移除：/portal 重定向到任务中心（作为新的工作台首页）
  {
    path: '/portal',
    redirect: '/tasks',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { layout: 'none', backgroundOverride: 'none' },
  },
  {
    path: '/register',
    redirect: { path: '/login', query: { tab: 'register' } },
  },
  {
    path: '/redirect',
    name: 'Redirect',
    component: () => import('@/views/Redirect.vue'),
    meta: { layout: 'none', backgroundOverride: 'none' },
  },
  // 第三方 OAuth 登录回调中转页：后端 /api/auth/oauth/{provider}/callback 完成后
  // 带 ?token=xxx 或 ?error=xxx 重定向到此页，前端写入会话后跳回首页
  {
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: () => import('@/views/OAuthCallback.vue'),
    meta: { layout: 'none', backgroundOverride: 'none' },
  },

  // ===== C 端：发现与浏览 =====
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { layout: 'c' },
  },
  {
    path: '/knowledge',
    name: 'KnowledgeHome',
    component: () => import('@/views/KnowledgeHome.vue'),
    meta: { layout: 'c', fullscreen: true },
  },
  {
    path: '/knowledge/upload',
    name: 'KnowledgeUpload',
    component: () => import('@/views/KnowledgeUpload.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/knowledge/import',
    name: 'KnowledgeImport',
    component: () => import('@/views/KnowledgeImport.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/obsidian/import',
    name: 'ObsidianImport',
    component: () => import('@/views/ObsidianImport.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/import-templates',
    name: 'ImportTemplates',
    component: () => import('@/views/ImportTemplates.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/knowledge/reader',
    name: 'LocalReader',
    component: () => import('@/views/LocalReader.vue'),
    meta: { layout: 'none', requiresAuth: true, backgroundOverride: 'none' },
  },
  {
    path: '/knowledge/new',
    name: 'KnowledgeCreate',
    component: () => import('@/views/KnowledgeCreate.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/study-group',
    name: 'StudyGroup',
    component: () => import('@/views/StudyGroup.vue'),
    meta: { layout: 'c', fullscreen: true, requiresAuth: true },
  },
  {
    path: '/messages',
    name: 'Messages',
    component: () => import('@/views/Messages.vue'),
    meta: { layout: 'c', fullscreen: true, requiresAuth: true },
  },
  {
    path: '/doc/:id',
    name: 'DocDetail',
    component: () => import('@/views/DocDetail.vue'),
    meta: { layout: 'c' },
  },
  {
    path: '/certificate/:id',
    name: 'CertificateDetail',
    component: () => import('@/views/Certificate.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/categories',
    name: 'Categories',
    component: () => import('@/views/Categories.vue'),
    meta: { layout: 'c', fullscreen: true },
  },
  {
    path: '/search',
    name: 'SearchResult',
    component: () => import('@/views/SearchResult.vue'),
    meta: { layout: 'c' },
  },
  {
    path: '/docs',
    name: 'Docs',
    component: () => import('@/views/Docs.vue'),
    meta: { layout: 'c' },
  },

  // ===== C 端：学习中心 =====
  {
    path: '/learning',
    name: 'Learning',
    redirect: '/learning/center',
  },
  // F-01 修复：/learning/center 指向真实学习总览页（LearningReport.vue，页面标题「学习中心」）
  {
    path: '/learning/center',
    name: 'LearningCenter',
    component: () => import('@/views/LearningReport.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/learning/paths',
    name: 'LearningPaths',
    component: () => import('@/views/LearningPaths.vue'),
    meta: { layout: 'c' },
  },
  {
    path: '/learning/path/:id',
    name: 'PathDetail',
    component: () => import('@/views/PathDetail.vue'),
    meta: { layout: 'c' },
  },
  {
    path: '/learning/chapter/:id',
    name: 'ChapterLearn',
    component: () => import('@/views/ChapterLearn.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/learning/code-practice',
    name: 'CodePractice',
    component: () => import('@/views/CodePractice.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/learning/code-practice/:id',
    name: 'CodePlayground',
    component: () => import('@/views/CodePlayground.vue'),
    meta: { layout: 'none', requiresAuth: true, backgroundOverride: 'none' },
  },
  {
    path: '/challenge',
    name: 'Challenge',
    component: () => import('@/views/Challenge.vue'),
    meta: { layout: 'c', fullscreen: true },
  },
  {
    path: '/challenge/:id',
    name: 'ChallengePlay',
    component: () => import('@/views/ChallengePlay.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  // 闪卡大厅：整合「学习闪卡」与「我的闪卡」为统一入口
  {
    path: '/learning/flashcards',
    name: 'FlashCardsHub',
    component: () => import('@/views/FlashCardsHub.vue'),
    meta: { layout: 'c', fullscreen: true, requiresAuth: true },
  },
  // 旧入口兼容重定向到统一大厅
  {
    path: '/learning/my-flashcards',
    redirect: '/learning/flashcards',
  },
  // 兼容旧组件命名（如外部链接直接指向 FlashCards 名称）
  {
    path: '/learning/flashcards-old',
    redirect: '/learning/flashcards',
  },
  {
    path: '/learning/knowledge-graph',
    name: 'KnowledgeGraph',
    component: () => import('@/views/KnowledgeGraph.vue'),
    meta: { layout: 'c', fullscreen: true },
  },
  // C① 掌握分布看板
  {
    path: '/learning/mastery',
    name: 'MasteryDashboard',
    component: () => import('@/views/MasteryDashboard.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/learning/review',
    name: 'ReviewPlan',
    component: () => import('@/views/ReviewPlan.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  // 沉浸式学习模式：无顶栏
  {
    path: '/learning/mode',
    name: 'LearningMode',
    component: () => import('@/views/LearningMode.vue'),
    meta: { layout: 'none', requiresAuth: true, backgroundOverride: 'none' },
  },
  // P7 沉浸工作台：5 种专注模式 + 个性化 + 洞察
  {
    path: '/learning/focus',
    name: 'FocusMode',
    component: () => import('@/views/FocusMode.vue'),
    meta: { layout: 'none', requiresAuth: true, backgroundOverride: 'none' },
  },
  // 数据结构可视化演示：红黑树
  {
    path: '/learning/data-structures/red-black-tree',
    name: 'RedBlackTree',
    component: () => import('@/views/RedBlackTree.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  // 学习报告独立页面
  {
    path: '/learning/report',
    name: 'LearningReport',
    component: () => import('@/views/LearningReportView.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  // F-01 修复：番茄钟独立路由（LearningCenter.vue 实为番茄钟专注页）
  {
    path: '/learning/pomodoro',
    name: 'LearningPomodoro',
    component: () => import('@/views/LearningCenter.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },

  // ===== C 端：AI 助手（共享工具页，C 端入口） =====
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/coding/agent',
    name: 'CodeAgent',
    component: () => import('@/views/CodeAgent.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/learning/quiz',
    name: 'SmartQuiz',
    component: () => import('@/views/SmartQuiz.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/learning/writing',
    name: 'SmartWriting',
    component: () => import('@/views/SmartWriting.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },

  // ===== C 端：个人空间 =====
  {
    path: '/tasks',
    name: 'TaskCenter',
    component: () => import('@/views/TaskCenter.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/calendar',
    name: 'Calendar',
    component: () => import('@/views/CalendarView.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/achievements',
    name: 'Achievement',
    component: () => import('@/views/Achievement.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/kb-titles',
    name: 'KBTitle',
    component: () => import('@/views/KBTitle.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  // ===== C 端：学习工作台（输入 → 整理 → 复习 → 输出 四模块闭环）=====
  {
    path: '/workbench',
    name: 'Workbench',
    component: () => import('@/views/Workbench.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/capture',
    name: 'WorkbenchCapture',
    component: () => import('@/views/WorkbenchCapture.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/notes',
    name: 'WorkbenchNotes',
    component: () => import('@/views/WorkbenchNotes.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/notes/:id',
    name: 'WorkbenchNoteEdit',
    component: () => import('@/views/WorkbenchNoteEdit.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/review',
    name: 'WorkbenchReview',
    component: () => import('@/views/WorkbenchReview.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/palace',
    name: 'WorkbenchPalace',
    component: () => import('@/views/WorkbenchPalace.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/recall',
    name: 'WorkbenchRecall',
    component: () => import('@/views/WorkbenchRecall.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/palace/:id',
    name: 'WorkbenchPalaceEdit',
    component: () => import('@/views/WorkbenchPalaceEdit.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/story',
    name: 'WorkbenchStory',
    component: () => import('@/views/WorkbenchStory.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/workbench/story/:id',
    name: 'WorkbenchStoryEdit',
    component: () => import('@/views/WorkbenchStoryEdit.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/mindmap',
    name: 'MindMap',
    component: () => import('@/views/MindMap.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/drawing',
    name: 'Drawing',
    component: () => import('@/views/DrawingEditor.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/task-list',
    name: 'TaskManager',
    component: () => import('@/views/TaskManager.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/settings/background',
    name: 'BackgroundSettings',
    component: () => import('@/views/BackgroundSettings.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('@/views/Favorites.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/notes',
    name: 'NotesManage',
    component: () => import('@/views/NotesManage.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/notes/new',
    name: 'NoteCreate',
    component: () => import('@/views/NoteEdit.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/notes/:id/edit',
    name: 'NoteEdit',
    component: () => import('@/views/NoteEdit.vue'),
    meta: { layout: 'c', requiresAuth: true, fullscreen: true },
  },
  {
    path: '/check-in',
    name: 'CheckIn',
    component: () => import('@/views/CheckIn.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/mistakes',
    name: 'Mistakes',
    component: () => import('@/views/Mistakes.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/notifications',
    name: 'Notifications',
    component: () => import('@/views/Notifications.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/community',
    name: 'Community',
    component: () => import('@/views/Community.vue'),
    meta: { layout: 'c' },
  },
  {
    path: '/community/post/new',
    name: 'PostCreate',
    component: () => import('@/views/PostEdit.vue'),
    meta: { layout: 'c', requiresAuth: true },
  },
  {
    path: '/community/post/:id',
    name: 'PostDetail',
    component: () => import('@/views/PostDetail.vue'),
    meta: { layout: 'c' },
  },

  // ===== B 端：管理后台 =====
  {
    path: '/admin/overview',
    name: 'AdminOverview',
    component: () => import('@/views/admin/Overview.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/knowledge',
    name: 'AdminKnowledge',
    component: () => import('@/views/admin/KnowledgeMgmt.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/categories',
    name: 'AdminCategories',
    component: () => import('@/views/admin/CategoryMgmt.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/docs',
    name: 'AdminDocs',
    component: () => import('@/views/admin/DocManagement.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/docs/new',
    name: 'AdminDocCreate',
    component: () => import('@/views/DocCreate.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/docs/:id/edit',
    name: 'AdminDocEdit',
    component: () => import('@/views/DocEdit.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true, fullscreen: true },
  },
  {
    path: '/admin/upload',
    name: 'AdminUpload',
    component: () => import('@/views/UploadDoc.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/tags',
    name: 'AdminTags',
    component: () => import('@/views/admin/TagManagement.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/flashcards',
    name: 'AdminFlashcards',
    component: () => import('@/views/admin/FlashcardMgmt.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/learning-paths',
    name: 'AdminLearningPaths',
    component: () => import('@/views/admin/LearningPathMgmt.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/learning/chapters/new',
    name: 'AdminChapterCreate',
    component: () => import('@/views/admin/ChapterEdit.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true, fullscreen: true },
  },
  {
    path: '/admin/learning/chapters/:id/edit',
    name: 'AdminChapterEdit',
    component: () => import('@/views/admin/ChapterEdit.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true, fullscreen: true },
  },
  {
    path: '/admin/writing',
    name: 'AdminWriting',
    component: () => import('@/views/SmartWriting.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/chat-config',
    name: 'AdminChatConfig',
    component: () => import('@/views/admin/ChatConfig.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/icons',
    name: 'AdminIcons',
    component: () => import('@/views/admin/IconManagement.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/files',
    name: 'AdminFiles',
    component: () => import('@/views/admin/FileMgmt.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/code-questions',
    name: 'AdminCodeQuestions',
    component: () => import('@/views/admin/CodeQuestionMgmt.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/quiz',
    name: 'AdminQuiz',
    component: () => import('@/views/admin/QuizMgmt.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/admin/UserManagement.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/database',
    name: 'AdminDatabase',
    component: () => import('@/views/admin/DatabaseSettings.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/admin/community',
    name: 'AdminCommunity',
    component: () => import('@/views/admin/CommunityManage.vue'),
    meta: { layout: 'b', requiresAuth: true, requiresAdmin: true },
  },

  // ===== 兼容旧入口（重定向到 B 端） =====
  { path: '/docs/new', redirect: '/admin/docs/new' },
  { path: '/docs/:id/edit', redirect: (to) => `/admin/docs/${to.params.id}/edit` },
  { path: '/upload', redirect: '/admin/upload' },

  // ===== 404 =====
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { layout: 'none', backgroundOverride: 'none' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();

  if (!to.meta.requiresAuth) {
    return true;
  }

  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  if (!auth.user) {
    try {
      await auth.fetchMe();
    } catch {
      auth.logout();
      return { path: '/login', query: { redirect: to.fullPath } };
    }
  }

  if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') {
    notify('需要管理员权限才能访问', 'error');
    return { path: '/' };
  }

  return true;
});

export default router;
