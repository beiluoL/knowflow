/**
 * 全站导航配置 —— 单一数据源（Single Source of Truth）
 *
 * Phase UI-2 建立。目的：
 *   1. 原先 C 端导航在 `components/layout/CTopNav.vue` 里硬编码了 6 个菜单数组
 *      （knowledgeMenu / learningMenu / communityMenu / aiMenu / growthMenu / personalMenu）
 *      外加 mobileLinks，导致「导航结构」与「路由」两套命名各说各话，
 *      且出现 8 个顶级入口、学习工作台重复出现等问题。
 *   2. 本文件统一收口，桌面侧边栏（CSidebar）与移动端抽屉/底部导航共用同一份数据，
 *      保证「路由 → 导航 → 面包屑 → 高亮」四者永远一致。
 *
 * 组织原则（按「用户任务」而非数据库表/Controller）：
 *   我要学习 → 学习
 *   我要管理知识 → 知识
 *   我要问 AI → AI
 *   我要交流 → 社区
 *   我要看自己的东西 → 我的
 *
 * 图标：统一使用项目既有 Icon.vue 内置图标集（lucide 风格 SVG + iconfont 前缀），
 *      不引入 emoji、不新增图标库。所有 name 均已校验在 Icon.vue 中存在。
 */

export interface NavChild {
  /** 菜单显示名（同时用于面包屑末级） */
  label: string
  /** 真实路由，必须与 router/index.ts 中的 path 一致 */
  path: string
  /** 图标名 */
  icon: string
  /** 是否需要登录才展示 */
  auth?: boolean
  /** 仅管理员可见 */
  adminOnly?: boolean
}

export interface NavGroup {
  /** 一级导航名 */
  label: string
  /** 一级导航图标 */
  icon: string
  /** 一级导航自身可点击时提供（如首页 /）；为分组容器时留空 */
  path?: string
  /** 二级导航 */
  children?: NavChild[]
  auth?: boolean
  adminOnly?: boolean
  /** 移动端底部导航是否收录（底部空间有限，只放最高频） */
  mobilePrimary?: boolean
  /**
   * UI/UX 3.2：工作区的「路由归属范围」——**只用于父级高亮 / 自动展开 / 面包屑，不渲染为菜单项**。
   *
   * 背景：Sidebar 收敛为「1 个核心入口 + enabled App」后，被下沉到首页卡片的功能
   * （/learning/paths、/docs、/community…）不再有二级菜单，导致进入这些页面时
   * 父级工作区不高亮、不自动展开、面包屑错归到「首页」。
   * 本字段声明「这些路由仍然属于该工作区」，从而保住导航上下文（§96-99）。
   */
  match?: string[]
}

/**
 * C 端主导航：UI/UX 2.0 收敛为 5 个顶级入口（§19/§109）。
 *
 * 收敛原则（减少入口，不减少能力，§25-26/§124）：
 *  - 原 7 个顶级（首页/学习/知识/AI/应用/社区/我的）→ 5 个（首页/学习/知识/AI/应用）。
 *  - 「社区」3 项（讨论/小组/私信）并入顶栏用户菜单，不再占任何工作区 Sidebar（3.2 §23/§25）。
 *  - 「我的」10 项（任务/日历/打卡/成就/钱包/设置…）全部收进顶栏头像下拉菜单（§20-21/§110-112），
 *    不再作为一级导航；其路由保持不变，深链仍可用（§259-267）。
 *  - 移动端底部导航只保留 4 个最高频：首页/学习/知识/应用（§105/§207）；
 *    AI 经顶栏「AI 助手」按钮进入（§107），不占底部 Tab。
 *
 * ── UI/UX 3.2 Core Menu Minimalism ────────────────────────────────────────────
 * 每个工作区只保留 **一个核心固定入口**，其余全部交给 resolveNavigation() 按 UserApp 动态注入：
 *   学习 → 学习首页   知识 → 知识首页   AI → AI 助手   应用 → 应用中心（顶级直达）
 * 被移除的固定二级入口（功能与路由全部保留，仅换入口位置，见 UI-UX-3.2 执行报告 §4）：
 *   今日计划/学习路径/复习计划/掌握度/学习报告/挑战/学习工作台/番茄钟/专注模式 → 学习首页卡片
 *   文档库/分类浏览/搜索                                              → 知识首页（搜索框/分类树/查看全部）
 *   社区讨论/学习小组/私信                                            → 顶栏用户菜单「社区」分组
 *   编程 Agent                                                       → 学习首页卡片（工具区）
 * 硬约束（3.2 §51-59）：只删导航入口，不删 Route / 页面 / API / 数据库 / 业务能力。
 */
export const NAVIGATION: NavGroup[] = [
  {
    label: '首页',
    icon: 'home',
    path: '/',
    mobilePrimary: true,
  },
  {
    label: '学习',
    icon: 'graduation-cap',
    mobilePrimary: true,
    // 3.2 §28：学习工作区 = 学习首页 + enabled learning Apps（后者由 resolver 注入）
    children: [
      { label: '学习首页', path: '/learning/center', icon: 'layout-dashboard', auth: true },
    ],
    // 3.2 §48/§105-106：被下沉到「学习首页」卡片、但路由仍属学习工作区的页面。
    // 只用于父级高亮 / 自动展开 / 面包屑归属，**不渲染为菜单**（§48）。
    // 含 learning App 路由：由 resolver 注入侧栏子项，此处补「工作区归属」使标题/高亮正确。
    match: [
      '/learning/paths',
      '/learning/review',
      '/learning/mastery',
      '/learning/report',
      '/workbench',
      '/learning/pomodoro',
      '/learning/focus',
      '/challenge',
      '/learning/code-practice',
      '/coding/agent',
      '/learning/flashcards',
      '/learning/quiz',
      '/mistakes',
      '/workbench/story',
      '/learning/today-plan',
    ],
  },
  {
    label: '知识',
    icon: 'library',
    mobilePrimary: true,
    // 3.2 §29/§19：知识首页即知识工作区主入口，不再并列「知识库」造成重复
    children: [
      { label: '知识首页', path: '/knowledge', icon: 'icon-AIbeikezhushou' },
    ],
    // 3.2 §48/§105-106：被下沉到「知识首页」的入口（文档库/分类/搜索/社区…）+ knowledge App 路由。
    // 注意 /workbench/notes、/workbench/palace 比 学习工作区的 /workbench 更具体，
    // 由 findGroupByPath 的「最长前缀优先」规则归到知识（§99）。
    match: [
      '/docs',
      '/categories',
      '/search',
      '/community',
      '/study-group',
      '/messages',
      '/workbench/notes',
      '/workbench/palace',
      '/mindmap',
      '/drawing',
      '/notes',
      '/learning/knowledge-graph',
    ],
  },
  {
    label: 'AI',
    icon: 'bot',
    // 移动端 AI 经顶栏「AI 助手」按钮进入（§105/§107），不占底部 Tab
    // 3.2 §31：AI 工作区 = AI 助手 + enabled AI Apps（如智能写作，由 resolver 注入）
    children: [
      { label: 'AI 助手', path: '/chat', icon: 'icon-rengongzhineng1', auth: true },
    ],
    // 3.2 §48/§153：AI App 路由归属 AI 工作区（智能写作 /learning/writing 由 resolver 注入子项，此处补归属）
    match: ['/learning/writing'],
  },
  {
    // Architecture-1：应用中心入口。Mini App 不逐个展开为二级导航（§48-50），
    // 已安装/启用的 App 在 /apps 与首页「我的应用」中管理；原业务路由（/learning/flashcards 等）保持不变（§135）。
    label: '应用',
    icon: 'grid',
    path: '/apps',
    auth: true,
    mobilePrimary: true,
  },
]

/* ============================ 查询工具 ============================ */

/**
 * 判断某路由是否「属于」某个分组。
 * 精确匹配，或以该路径为前缀的子路由（/learning/paths/3 属于 /learning/paths）。
 */
function pathMatches(current: string, target: string): boolean {
  if (!target) return false
  // 顶级入口「首页」path='/' 只精确匹配，否则所有路由都会被它抢走（§96）
  if (target === '/') return current === '/'
  if (current === target) return true
  return current.startsWith(target.endsWith('/') ? target : `${target}/`)
}

/** 某分组是否「归属」当前路由：自身 path / 任一二级 / match 前缀（§48/§96-99） */
export function groupMatchesPath(group: NavGroup, path: string): boolean {
  if (group.path && pathMatches(path, group.path)) return true
  if (group.children?.some((c) => pathMatches(path, c.path))) return true
  if (group.match?.some((m) => pathMatches(path, m))) return true
  return false
}

/** 归属匹配的「特异性」：命中的最长路径段长度，冲突时取最具体的分组（如 /workbench/notes → 知识 而非 学习） */
function matchSpecificity(group: NavGroup, path: string): number {
  let best = 0
  if (group.path && pathMatches(path, group.path)) best = Math.max(best, group.path.length)
  for (const c of group.children ?? []) if (pathMatches(path, c.path)) best = Math.max(best, c.path.length)
  for (const m of group.match ?? []) if (pathMatches(path, m)) best = Math.max(best, m.length)
  return best
}

/** 根据当前 path 找到所属分组（用于父级高亮 / 面包屑），冲突时取最具体者 */
export function findGroupByPath(path: string): NavGroup | null {
  let best: NavGroup | null = null
  let bestSpec = 0
  for (const g of NAVIGATION) {
    if (!groupMatchesPath(g, path)) continue
    const spec = matchSpecificity(g, path)
    if (spec > bestSpec) {
      best = g
      bestSpec = spec
    }
  }
  return best
}

/** UI/UX 3.2：当前路由所属工作区（供侧栏父级高亮复用，避免与 buildCrumbs 逻辑漂移） */
export function findActiveGroup(path: string): NavGroup | null {
  return findGroupByPath(path)
}

/** 根据当前 path 找到命中的二级项 */
export function findChildByPath(path: string): { group: NavGroup; child: NavChild } | null {
  for (const g of NAVIGATION) {
    const child = g.children?.find((c) => pathMatches(path, c.path))
    if (child) return { group: g, child }
  }
  return null
}

export interface Crumb {
  label: string
  to?: string
}

/**
 * 由路由自动生成面包屑。
 * 命中二级导航 → [分组] / [子项]；命中顶级 → [分组]。
 * 未命中导航配置（如详情页）→ 返回 null，由页面自行用 PageHeader 的 crumbs 补充末级。
 */
export function buildCrumbs(path: string): Crumb[] | null {
  const hit = findChildByPath(path)
  if (hit) {
    return [
      { label: hit.group.label, to: hit.group.path },
      { label: hit.child.label, to: hit.child.path },
    ]
  }
  const g = findGroupByPath(path)
  if (g) return [{ label: g.label, to: g.path }]
  return null
}

/** 移动端底部导航：只收录标记 mobilePrimary 的顶级入口 */
export function mobilePrimaryNav(): NavGroup[] {
  return NAVIGATION.filter((g) => g.mobilePrimary)
}

/** 按登录态过滤可见项（避免向未登录用户展示需要登录的入口） */
export function visibleGroups(isLoggedIn: boolean, isAdmin = false): NavGroup[] {
  return NAVIGATION.filter((g) => {
    if (g.adminOnly && !isAdmin) return false
    if (g.auth && !isLoggedIn) return false
    return true
  }).map((g) => ({
    ...g,
    children: g.children
      ?.filter((c) => {
        if (c.adminOnly && !isAdmin) return false
        if (c.auth && !isLoggedIn) return false
        return true
      })
      .map((c) => ({ ...c })),
  }))
}
