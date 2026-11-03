import type { RouteRecordRaw } from 'vue-router'

/**
 * Career Apps V2.0 独立路由。
 *
 * 路径顺序遵循「静态段先于参数段」：/resumes/new 必须早于 /resumes/:resumeId，
 * /interviews/new 必须早于 /interviews/:planId，/templates/editor/:resumeId 必须早于
 * /templates/:templateId，避免把静态段解析成资源 ID。
 *
 * meta.appKey 是「该页面归属哪个 App」的唯一声明：
 * 导航高亮、点击拦截、停用态都由它推导，页面自身不再判断。
 * 所有页面需要登录；后端仍会再次执行用户归属校验，路由守卫不是权限边界。
 */
export const careerRoutes: RouteRecordRaw[] = [
  {
    path: '/career',
    name: 'CareerWorkbench',
    component: () => import('@/views/career/CareerWorkbenchPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '求职工作台', appKey: 'career-workbench' },
  },
  {
    path: '/career/overview',
    name: 'CareerOverview',
    component: () => import('@/views/career/CareerOverviewPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '求职总览', appKey: 'career-overview' },
  },
  {
    path: '/career/overview/insights',
    name: 'CareerInsights',
    component: () => import('@/views/career/CareerInsightsPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '求职数据', appKey: 'career-overview' },
  },
  {
    path: '/career/discover',
    name: 'CareerDiscover',
    component: () => import('@/views/career/CareerDiscoverPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '岗位发现', appKey: 'career-discover' },
  },
  {
    path: '/career/resumes',
    name: 'CareerResumeList',
    component: () => import('@/views/career/ResumeListPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '简历中心', appKey: 'career-resume' },
  },
  {
    path: '/career/resumes/new',
    name: 'CareerResumeNew',
    component: () => import('@/views/career/ResumeEditorPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '新建简历', appKey: 'career-resume' },
  },
  {
    path: '/career/resumes/:resumeId',
    name: 'CareerResumeEditor',
    component: () => import('@/views/career/ResumeEditorPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '简历编辑', appKey: 'career-resume' },
  },
  {
    path: '/career/jobs',
    name: 'CareerJobList',
    component: () => import('@/views/career/JobListPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '岗位中心', appKey: 'career-jobs' },
  },
  {
    path: '/career/jobs/board',
    name: 'CareerJobBoard',
    component: () => import('@/views/career/JobBoardPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '投递看板', appKey: 'career-jobs' },
  },
  {
    path: '/career/jobs/compare',
    name: 'CareerJobCompare',
    component: () => import('@/views/career/JobComparePage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '岗位对比', appKey: 'career-jobs' },
  },
  {
    path: '/career/jobs/:jobId',
    name: 'CareerJobDetail',
    component: () => import('@/views/career/JobDetailPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '岗位详情', appKey: 'career-jobs' },
  },
  {
    path: '/career/matches',
    name: 'CareerMatchList',
    component: () => import('@/views/career/MatchListPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '智能匹配', appKey: 'career-matching' },
  },
  {
    path: '/career/matches/multi',
    name: 'CareerMultiMatch',
    component: () => import('@/views/career/CareerMultiMatchPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '多简历匹配', appKey: 'career-matching' },
  },
  {
    path: '/career/matches/:matchId',
    name: 'CareerMatchDetail',
    component: () => import('@/views/career/MatchDetailPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '匹配详情', appKey: 'career-matching' },
  },
  {
    path: '/career/interviews',
    name: 'CareerInterviewList',
    component: () => import('@/views/career/JobInterviewListPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '岗位面试', appKey: 'career-interviews' },
  },
  {
    path: '/career/interviews/new',
    name: 'CareerInterviewNew',
    component: () => import('@/views/career/JobInterviewSetupPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '创建面试方案', appKey: 'career-interviews' },
  },
  {
    path: '/career/interviews/:planId',
    name: 'CareerInterviewPlan',
    component: () => import('@/views/career/JobInterviewPlanPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '面试方案', appKey: 'career-interviews' },
  },
  {
    path: '/career/tools',
    name: 'CareerToolbox',
    component: () => import('@/views/career/toolbox/ResumeToolboxPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '简历工具箱', appKey: 'career-resume-toolbox' },
  },
  {
    path: '/career/tools/ats',
    name: 'CareerToolAts',
    component: () => import('@/views/career/toolbox/ResumeAtsCheckPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: 'ATS 可读性检查', appKey: 'career-resume-toolbox' },
  },
  {
    path: '/career/tools/content',
    name: 'CareerToolContent',
    component: () => import('@/views/career/toolbox/ResumeContentCheckPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '简历内容体检', appKey: 'career-resume-toolbox' },
  },
  {
    path: '/career/tools/jd',
    name: 'CareerToolJd',
    component: () => import('@/views/career/toolbox/ResumeJdOptimizePage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: 'JD 定向优化', appKey: 'career-resume-toolbox' },
  },
  {
    path: '/career/tools/compare',
    name: 'CareerToolCompare',
    component: () => import('@/views/career/toolbox/ResumeVersionComparePage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '版本对比', appKey: 'career-resume-toolbox' },
  },
  {
    path: '/career/tools/tailor',
    name: 'CareerToolTailor',
    component: () => import('@/views/career/toolbox/ResumeTailoringPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: 'AI 简历定制', appKey: 'career-resume-toolbox' },
  },
  {
    path: '/career/templates',
    name: 'CareerTemplateGallery',
    component: () => import('@/views/career/templates/ResumeTemplateGalleryPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '简历模板', appKey: 'career-resume-templates' },
  },
  {
    path: '/career/templates/editor/:resumeId',
    name: 'CareerTemplateEditor',
    component: () => import('@/views/career/templates/ResumeTemplateEditorPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '模板编辑', appKey: 'career-resume-templates' },
  },
  {
    path: '/career/templates/print/:resumeId',
    name: 'CareerTemplatePrint',
    component: () => import('@/views/career/templates/ResumeTemplatePrintPage.vue'),
    meta: { layout: 'none', requiresAuth: true, title: '导出 PDF', appKey: 'career-resume-templates' },
  },
  {
    path: '/career/templates/:templateId',
    name: 'CareerTemplatePreview',
    component: () => import('@/views/career/templates/ResumeTemplatePreviewPage.vue'),
    meta: { layout: 'c', requiresAuth: true, title: '模板预览', appKey: 'career-resume-templates' },
  },
]
