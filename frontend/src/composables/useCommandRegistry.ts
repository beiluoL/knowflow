/**
 * F2：命令面板 · 命令注册中心
 *
 * 设计：
 * - 维护一份精选命令列表（页面导航 + 快速操作 + 搜索建议），不依赖路由 meta 改造。
 * - 命令分为三类：navigation（带 path，由 palette 通过 router.push 跳转）、action（带 run 回调）、search（输入非空时生成）。
 * - 支持运行时动态注册/注销命令（供各页面挂载局部动作，如「新建笔记」「导出日历」）。
 * - 搜索为简单 includes 匹配（title/subtitle/keywords/aliases），不引入 fuse.js。
 *
 * 用法：
 *   import { useCommandRegistry } from '@/composables/useCommandRegistry'
 *   const { commands, registerCommand } = useCommandRegistry()
 */
import { computed, ref } from 'vue'

export type CommandCategory =
  | 'navigation'
  | 'learning'
  | 'ai'
  | 'workspace'
  | 'personal'
  | 'admin'
  | 'action'
  | 'search'

export interface Command {
  /** 唯一 id */
  id: string
  /** 显示标题 */
  title: string
  /** 副标题/描述（可选） */
  subtitle?: string
  /** 图标名（Icon 组件 name，可选） */
  icon?: string
  /** 分组类别 */
  category: CommandCategory
  /** 搜索关键词（title/subtitle 之外补充匹配） */
  keywords?: string[]
  /** 别名（拼音/英文等） */
  aliases?: string[]
  /** 快捷键展示（如 ['Ctrl', 'N']） */
  shortcut?: string[]
  /** 导航命令的路由路径（与 run 二选一） */
  path?: string
  /** 动作命令的执行回调（与 path 二选一） */
  run?: () => void | Promise<void>
  /** 是否需要登录（默认 true） */
  requiresAuth?: boolean
  /** 是否需要管理员（默认 false） */
  requiresAdmin?: boolean
}

/** 精选导航 + 操作命令（不依赖路由 meta，集中维护避免改动 80+ 路由） */
const staticCommands: Command[] = [
  // ===== 导航 =====
  { id: 'nav-home', title: '首页', icon: 'home', category: 'navigation', path: '/', aliases: ['shouye', 'main'] },
  { id: 'nav-knowledge', title: '知识库', subtitle: '浏览全部文档', icon: 'book-open', category: 'navigation', path: '/knowledge', aliases: ['zhishiku', 'docs'] },
  { id: 'nav-categories', title: '分类目录', icon: 'folder', category: 'navigation', path: '/categories', aliases: ['fenlei'] },
  { id: 'nav-search', title: '搜索', subtitle: '全文搜索知识库', icon: 'search', category: 'navigation', path: '/search', aliases: ['sousuo'] },
  { id: 'nav-docs', title: '文档列表', icon: 'file-text', category: 'navigation', path: '/docs' },
  { id: 'nav-community', title: '社区', icon: 'users', category: 'navigation', path: '/community', aliases: ['shequ'] },

  // ===== 学习中心 =====
  { id: 'learn-today', title: '今日计划', subtitle: 'AI编排的三段日程', icon: 'calendar-check', category: 'learning', path: '/learning/today-plan', aliases: ['jinri', 'jihua', 'today'] },
  { id: 'learn-center', title: '学习中心', subtitle: '学习总览与报告', icon: 'graduation-cap', category: 'learning', path: '/learning/center', aliases: ['xuexi'] },
  { id: 'learn-paths', title: '学习路径', icon: 'route', category: 'learning', path: '/learning/paths' },
  { id: 'learn-mastery', title: '掌握分布看板', icon: 'bar-chart-2', category: 'learning', path: '/learning/mastery' },
  { id: 'learn-review', title: '复习计划', icon: 'refresh-cw', category: 'learning', path: '/learning/review' },
  { id: 'learn-flashcards', title: '闪卡大厅', icon: 'layers', category: 'learning', path: '/learning/flashcards', aliases: ['shanka', 'flashcard'] },
  { id: 'learn-graph', title: '知识图谱', icon: 'share-2', category: 'learning', path: '/learning/knowledge-graph', aliases: ['tupu', 'graph'] },
  { id: 'learn-pomodoro', title: '番茄钟专注', icon: 'clock', category: 'learning', path: '/learning/pomodoro' },
  { id: 'learn-focus', title: '沉浸专注模式', icon: 'target', category: 'learning', path: '/learning/focus' },
  { id: 'learn-report', title: '学习报告', icon: 'file-text', category: 'learning', path: '/learning/report' },

  // ===== AI 助手 =====
  { id: 'ai-chat', title: 'AI 对话', subtitle: '通用 AI 助手', icon: 'message-square', category: 'ai', path: '/chat', aliases: ['duihua', 'chat'] },
  { id: 'ai-codeagent', title: '编程 Agent', icon: 'code', category: 'ai', path: '/coding/agent' },
  { id: 'ai-quiz', title: '智能测验', icon: 'help-circle', category: 'ai', path: '/learning/quiz' },
  { id: 'ai-writing', title: '智能写作', icon: 'pen-tool', category: 'ai', path: '/learning/writing' },

  // ===== 工作台 =====
  { id: 'wb-home', title: '学习工作台', subtitle: '输入→整理→复习→输出', icon: 'briefcase', category: 'workspace', path: '/workbench' },
  { id: 'wb-capture', title: '快速捕获', icon: 'zap', category: 'workspace', path: '/workbench/capture' },
  { id: 'wb-notes', title: '笔记管理', icon: 'file-text', category: 'workspace', path: '/workbench/notes' },
  { id: 'wb-review', title: '复习工作台', icon: 'refresh-cw', category: 'workspace', path: '/workbench/review' },
  { id: 'wb-palace', title: '记忆宫殿', icon: 'home', category: 'workspace', path: '/workbench/palace' },
  { id: 'wb-recall', title: '回忆练习', icon: 'brain', category: 'workspace', path: '/workbench/recall' },
  { id: 'wb-story', title: '故事联想法', icon: 'book', category: 'workspace', path: '/workbench/story' },
  { id: 'wb-mindmap', title: '思维导图', icon: 'share-2', category: 'workspace', path: '/mindmap', aliases: ['daotu'] },
  { id: 'wb-drawing', title: '画板', icon: 'pen-tool', category: 'workspace', path: '/drawing' },
  { id: 'notes-new', title: '新建笔记', icon: 'plus', category: 'workspace', path: '/notes/new', aliases: ['xinjian'] },
  { id: 'notes-manage', title: '笔记列表', icon: 'file-text', category: 'workspace', path: '/notes' },

  // ===== 个人空间 =====
  { id: 'ps-tasks', title: '任务中心', icon: 'check-square', category: 'personal', path: '/tasks', aliases: ['renwu'] },
  { id: 'ps-tasklist', title: '任务清单', icon: 'list', category: 'personal', path: '/task-list' },
  { id: 'ps-calendar', title: '日历', icon: 'calendar', category: 'personal', path: '/calendar', aliases: ['rili'] },
  { id: 'ps-habits', title: '习惯打卡', icon: 'repeat', category: 'personal', path: '/habits', aliases: ['xiguan'] },
  { id: 'ps-checkin', title: '每日打卡', icon: 'check-circle', category: 'personal', path: '/check-in' },
  { id: 'ps-achievements', title: '成就', icon: 'award', category: 'personal', path: '/achievements' },
  { id: 'ps-favorites', title: '收藏夹', icon: 'star', category: 'personal', path: '/favorites' },
  { id: 'ps-mistakes', title: '错题本', icon: 'x-circle', category: 'personal', path: '/mistakes' },
  { id: 'ps-notifications', title: '通知中心', icon: 'bell', category: 'personal', path: '/notifications' },
  { id: 'ps-profile', title: '个人主页', icon: 'user', category: 'personal', path: '/profile' },

  // ===== 管理后台 =====
  { id: 'admin-overview', title: '管理总览', icon: 'layout', category: 'admin', path: '/admin/overview', requiresAdmin: true },
  { id: 'admin-knowledge', title: '知识管理', icon: 'book-open', category: 'admin', path: '/admin/knowledge', requiresAdmin: true },
  { id: 'admin-docs', title: '文档管理', icon: 'file-text', category: 'admin', path: '/admin/docs', requiresAdmin: true },
  { id: 'admin-users', title: '用户管理', icon: 'users', category: 'admin', path: '/admin/users', requiresAdmin: true },
  { id: 'admin-community', title: '社区管理', icon: 'message-square', category: 'admin', path: '/admin/community', requiresAdmin: true },

  // ===== 快速操作 =====
  {
    id: 'act-new-doc',
    title: '新建文档',
    icon: 'file-plus',
    category: 'action',
    shortcut: ['Ctrl', 'N'],
    path: '/knowledge/new',
    aliases: ['xinjianwendang'],
  },
  {
    id: 'act-new-chat',
    title: '发起新对话',
    icon: 'message-square',
    category: 'action',
    path: '/chat',
  },
  {
    id: 'act-upload',
    title: '上传文档',
    icon: 'upload',
    category: 'action',
    path: '/knowledge/upload',
  },
  {
    id: 'act-import-obsidian',
    title: '导入 Obsidian 笔记',
    icon: 'download',
    category: 'action',
    path: '/obsidian/import',
  },
]

/** 动态注册的命令（页面运行时挂载的局部动作） */
const dynamicCommands = ref<Command[]>([])

/** 注册一个动态命令，返回注销函数 */
export function registerCommand(cmd: Command): () => void {
  const exists = dynamicCommands.value.findIndex((c) => c.id === cmd.id)
  if (exists >= 0) {
    dynamicCommands.value.splice(exists, 1, cmd)
  } else {
    dynamicCommands.value.push(cmd)
  }
  return () => {
    const idx = dynamicCommands.value.findIndex((c) => c.id === cmd.id)
    if (idx >= 0) dynamicCommands.value.splice(idx, 1)
  }
}

/** 合并静态 + 动态命令 */
const allCommands = computed<Command[]>(() => [...staticCommands, ...dynamicCommands.value])

/** 搜索建议命令（输入非空时生成） */
function buildSearchCommand(query: string): Command {
  return {
    id: 'search-suggest',
    title: `搜索"${query}"`,
    subtitle: '在知识库中全文检索',
    icon: 'search',
    category: 'search',
    path: `/search?q=${encodeURIComponent(query)}`,
  }
}

/** 简单 includes 模糊匹配（不区分大小写，覆盖 title/subtitle/keywords/aliases） */
function matchCommand(cmd: Command, q: string): boolean {
  const lower = q.toLowerCase()
  if (cmd.title.toLowerCase().includes(lower)) return true
  if (cmd.subtitle?.toLowerCase().includes(lower)) return true
  if (cmd.aliases?.some((a) => a.toLowerCase().includes(lower))) return true
  if (cmd.keywords?.some((k) => k.toLowerCase().includes(lower))) return true
  return false
}

/** 按查询过滤 + 排序：匹配 title 优先于 subtitle/aliases */
export function useCommandRegistry() {
  const commands = allCommands

  /** 过滤命令；query 为空时返回全部（按 category 分组） */
  function filter(query: string, opts?: { isLoggedIn?: boolean; isAdmin?: boolean }): Command[] {
    const isLoggedIn = opts?.isLoggedIn ?? true
    const isAdmin = opts?.isAdmin ?? false

    const base = commands.value.filter((c) => {
      if (c.requiresAdmin && !isAdmin) return false
      if (c.requiresAuth === false) return true
      return isLoggedIn ? true : c.requiresAuth !== true
    })

    if (!query.trim()) return base

    const q = query.trim()
    const matched = base.filter((c) => matchCommand(c, q))
    // 精确 title 匹配排在前面
    matched.sort((a, b) => {
      const at = a.title.toLowerCase().includes(q.toLowerCase()) ? 0 : 1
      const bt = b.title.toLowerCase().includes(q.toLowerCase()) ? 0 : 1
      return at - bt
    })

    // 追加一条「搜索建议」
    matched.push(buildSearchCommand(q))
    return matched
  }

  return { commands, filter, registerCommand }
}
