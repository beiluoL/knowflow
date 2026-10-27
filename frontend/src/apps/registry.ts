/**
 * Architecture-1：Mini App Registry（唯一数据源）
 *
 * 本阶段为 Built-in Mini Apps：所有 App 代码已存在于项目中（§4），
 * Registry 只管理「注册 / 安装 / 启用 / 可见」（§5）。
 *
 * 判定原则（§17-18）：不能只看页面名，必须验证真实 Route + 业务存在。
 * 以下 13 个 App 的 route 均已与 router/index.ts 逐一核对（2026-08-30）。
 * 未注册任何不存在的功能（§173-174）；重复功能（如两套笔记）只选 PRIMARY 注册（§175-176）。
 */
import type { AppCategory, AppDefinition, AppNavSection } from './types'
import { APP_ICONS } from './icon-registry'

/** 分类展示顺序与中文标签（§70） */
export const CATEGORY_ORDER: AppCategory[] = ['MEMORY', 'PRACTICE', 'UNDERSTANDING', 'ORGANIZATION', 'FOCUS', 'CODING', 'AI']

export const CATEGORY_LABELS: Record<AppCategory, string> = {
  MEMORY: '记忆',
  PRACTICE: '练习',
  UNDERSTANDING: '理解',
  ORGANIZATION: '整理',
  FOCUS: '专注',
  CODING: '实战',
  AI: 'AI',
}

export const APP_DEFINITIONS: AppDefinition[] = [
  {
    key: 'capture', name: '收集箱', description: '快速记录灵感与摘录，保留来源并整理成笔记。',
    icon: 'inbox', category: 'ORGANIZATION', categoryLabel: '整理',
    route: '/workbench/capture', version: '1.0.0', status: 'ACTIVE', defaultInstalled: false,
    capabilities: ['knowledge.read', 'knowledge.write'],
  },
  {
    key: 'spaced-review', name: '笔记复习', description: '把笔记变成问答卡，根据反馈安排下次复习。',
    icon: 'repeat', category: 'MEMORY', categoryLabel: '记忆',
    route: '/workbench/review', version: '1.0.0', status: 'ACTIVE', defaultInstalled: false,
    capabilities: ['review.read', 'review.write', 'learning-event.write'],
  },
  {
    key: 'flashcard',
    name: '闪卡',
    description: '通过主动回忆巩固知识，SRS 安排复习节奏。',
    icon: 'layers',
    category: 'MEMORY',
    categoryLabel: '记忆',
    route: '/learning/flashcards',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: true,
    capabilities: ['knowledge.read', 'mastery.read', 'review.read', 'review.write', 'learning-event.write'],
  },
  {
    key: 'quiz',
    name: '练习',
    description: '通过答题检验掌握，答错自动进入错题本。',
    icon: 'check-circle', // UI/UX 3.3：语义图标，统一由 icon-registry.ts 派生
    category: 'PRACTICE',
    categoryLabel: '练习',
    route: '/learning/quiz',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: true,
    capabilities: ['mastery.read', 'learning-event.write'],
  },
  {
    key: 'mistake',
    name: '错题',
    description: '回顾做错的题目，标记掌握、反复练习薄弱点。',
    icon: 'alert-circle',
    category: 'PRACTICE',
    categoryLabel: '练习',
    route: '/mistakes',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: true,
    capabilities: ['mastery.read', 'review.read'],
  },
  {
    key: 'exam',
    name: '考试',
    description: '智能组卷、限时模拟考、AI 辅助评卷与成绩分析，打通错题强化。',
    icon: 'graduation-cap',
    category: 'PRACTICE',
    categoryLabel: '练习',
    route: '/exam',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: true,
    capabilities: ['mastery.read', 'learning-event.write'],
  },
  {
    // Phase 3H：模拟面试基础模式（预置题库/追问树/量表自评，无 AI 完整可用）
    key: 'mock-interview',
    name: '模拟面试',
    description: 'Java + AI 岗位预置题库：倒计时作答、追问树、参考要点与量表自评，无 AI 也能完整完成。',
    icon: 'briefcase',
    category: 'PRACTICE',
    categoryLabel: '练习',
    route: '/interview',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: true,
    capabilities: [],
  },
  {
    key: 'focus',
    name: '专注',
    description: '番茄钟与沉浸式专注，保持学习节奏。',
    icon: 'timer',
    category: 'FOCUS',
    categoryLabel: '专注',
    route: '/learning/pomodoro',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: true,
    capabilities: ['learning-event.write'],
  },
  {
    key: 'feynman',
    name: '费曼',
    description: '用自己的话解释知识，检验是否真正理解。',
    icon: 'brain',
    category: 'UNDERSTANDING',
    categoryLabel: '理解',
    route: '/workbench/story',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read', 'learning-event.write'],
  },
  {
    key: 'cornell',
    name: '康奈尔',
    description: '康奈尔笔记法整理知识，输入 → 线索 → 总结。',
    icon: 'notebook-pen',
    category: 'ORGANIZATION',
    categoryLabel: '整理',
    route: '/workbench/notes',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read', 'knowledge.write'],
  },
  {
    key: 'memory-palace',
    name: '记忆宫殿',
    description: '用位置记忆法把知识点放进宫殿，强化长期记忆。',
    icon: 'map', // UI/UX 3.3：语义图标，统一由 icon-registry.ts 派生
    category: 'MEMORY',
    categoryLabel: '记忆',
    route: '/workbench/palace',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read', 'mastery.read', 'learning-event.write'],
  },
  {
    key: 'knowledge-graph',
    name: '知识图谱',
    description: '查看知识关系与掌握状态，发现薄弱环节。',
    icon: 'share-2', // UI/UX 3.3：语义图标，统一由 icon-registry.ts 派生
    category: 'UNDERSTANDING',
    categoryLabel: '理解',
    route: '/learning/knowledge-graph',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read', 'mastery.read'],
  },
  {
    key: 'coding',
    name: '代码练习',
    description: '统一题库中心：Java 算法题在线练习、草稿与安全判题。',
    icon: 'code',
    category: 'CODING',
    categoryLabel: '实战',
    route: '/coding/bank',
    version: '1.1.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read', 'learning-event.write'],
  },
  {
    key: 'mindmap',
    name: '思维导图',
    description: '把知识点结构化，理清脉络。',
    icon: 'git-branch',
    category: 'ORGANIZATION',
    categoryLabel: '整理',
    route: '/mindmap',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read', 'knowledge.write'],
  },
  {
    key: 'drawing',
    name: '绘图',
    description: '用图示表达概念，辅助理解复杂关系。',
    icon: 'palette',
    category: 'ORGANIZATION',
    categoryLabel: '整理',
    route: '/drawing',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read', 'knowledge.write'],
  },
  {
    key: 'ai-writing',
    name: '智能写作',
    description: 'AI 辅助写作与润色，沉淀学习输出。',
    icon: 'wand-2',
    category: 'AI',
    categoryLabel: 'AI',
    route: '/learning/writing',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['ai.chat'],
  },
  {
    key: 'notes',
    name: '笔记',
    description: '记录与整理学习笔记，随手沉淀思考。',
    icon: 'file-text',
    category: 'ORGANIZATION',
    categoryLabel: '整理',
    route: '/notes',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read', 'knowledge.write'],
  },
  {
    key: 'tech-english',
    name: '专业英语',
    description: 'Java + AI 技术词汇大厅、专业缩写百科与标准发音入口。',
    icon: 'book-open',
    category: 'UNDERSTANDING',
    categoryLabel: '理解',
    route: '/learning/english',
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: ['knowledge.read'],
  },
]

/** 新用户默认安装（§88：闪卡 / 练习 / 错题 / 专注） */
export const DEFAULT_APP_KEYS: string[] = APP_DEFINITIONS.filter((a) => a.defaultInstalled).map((a) => a.key)

/* ============================ 查询工具（§105，纯函数、可单测） ============================ */

export function getApps(): AppDefinition[] {
  return APP_DEFINITIONS
}

export function getAppByKey(key: string): AppDefinition | undefined {
  return APP_DEFINITIONS.find((a) => a.key === key)
}

export function getDefaultApps(): AppDefinition[] {
  return APP_DEFINITIONS.filter((a) => DEFAULT_APP_KEYS.includes(a.key))
}

/** 推荐：ACTIVE 且非默认安装（第一版官方固定推荐，不做 AI/行为推荐 —— §163-164） */
export function getRecommendedApps(): AppDefinition[] {
  return APP_DEFINITIONS.filter((a) => a.status !== 'LEGACY' && !a.defaultInstalled)
}

/** 按分类筛选（App 数量少，前端本地筛选即可 —— §75） */
export function getAppsByCategory(category: AppCategory | 'ALL'): AppDefinition[] {
  if (category === 'ALL') return APP_DEFINITIONS
  return APP_DEFINITIONS.filter((a) => a.category === category)
}

/* ============================ UI/UX 3.0：动态导航元数据（单一来源，§5/§18/§19） ============================
 * 导航分区只在此处集中定义，Sidebar / Home / LearningCenter 均不得各自硬编码分类（§19）。
 * - navSection：App 出现在哪个一级工作区的动态子菜单（学习 / 知识 / AI）；缺省 = 不进动态子菜单。
 * - order：同区内默认排序；用户 UserApp.sortOrder 优先（§18）。
 * - navVisible：是否允许进导航；focus 等沉浸式 App 置 false，由页面内入口承载（§13）。
 * 注意：Registry.category（MEMORY/PRACTICE/...）是「学习方法类型」，navSection 是「导航工作区」，
 * 二者不同维度，但 navSection 由 category 派生并在此集中映射（§19：严格使用 Registry 分类概念）。
 *
 * UI/UX 3.2：Sidebar 已收敛为「核心固定入口 + 动态 App」（§8/§28/§29），
 * 因此 navSection 是 App 进入导航的**唯一途径**——未在此登记的 App 只能从应用中心进入。
 */
export const APP_NAV_META: Record<string, { navSection: AppNavSection; order: number; navVisible?: boolean }> = {
  capture: { navSection: 'knowledge', order: 6 },
  'spaced-review': { navSection: 'learning', order: 7 },
  focus: { navSection: 'tools', order: 1 },
  // ── 学习工作区（固定入口「学习首页」之后的动态 App，3.2 §28）
  flashcard: { navSection: 'learning', order: 1 },
  quiz: { navSection: 'learning', order: 2 },
  exam: { navSection: 'learning', order: 8 },
  'mock-interview': { navSection: 'learning', order: 9 },
  mistake: { navSection: 'learning', order: 3 },
  coding: { navSection: 'learning', order: 4 },
  feynman: { navSection: 'learning', order: 5 },
  // memory-palace：3.1 遗留项（未配 navSection → navVisible=false 导致永远进不了菜单）。
  // 真实 category=MEMORY（记忆类学习方法），按 3.2 §71 归到 learning；真实 category 未改动。
  'memory-palace': { navSection: 'learning', order: 6 },
  // tech-english：Java + AI 专业英语（P0 词汇大厅，公开可浏览）
  'tech-english': { navSection: 'learning', order: 10 },
  // focus：保持沉浸式（§13/§74），不进动态子菜单，由顶栏番茄钟 + 学习首页卡片承载，避免「专注/Focus」重复
  // ── 知识工作区（固定入口「知识首页」之后的动态 App，3.2 §29）
  'knowledge-graph': { navSection: 'knowledge', order: 1 },
  cornell: { navSection: 'knowledge', order: 2 },
  mindmap: { navSection: 'knowledge', order: 3 },
  notes: { navSection: 'knowledge', order: 4 },
  drawing: { navSection: 'knowledge', order: 5 },
  // ── AI 工作区（固定入口「AI 助手」之后的动态 App，3.2 §31/§153）
  'ai-writing': { navSection: 'ai', order: 1 },
}

// 模块加载时一次性富化：getApps() 返回已含 navSection / navVisible / order（不修改既有 13 个字面量）
for (const app of APP_DEFINITIONS) {
  const meta = APP_NAV_META[app.key]
  app.navVisible = meta ? (meta.navVisible ?? true) : false
  app.navSection = meta?.navSection
  app.order = meta?.order ?? 999
}

// UI/UX 3.3：图标集中由 APP_ICONS 派生（单一来源，禁止各页面各自乱选，§13）。
// 若 Icon.vue 扩充图标集，只需改 icon-registry.ts，消费方（AppCard/Sidebar/Home）自动跟随。
for (const app of APP_DEFINITIONS) {
  const icon = APP_ICONS[app.key]
  if (icon) app.icon = icon
}
