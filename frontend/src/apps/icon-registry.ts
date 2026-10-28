/**
 * UI/UX 3.3：App Icon Registry（集中映射 App key → 语义图标）
 *
 * 单一来源：所有 Mini App 的图标在此集中定义，组件（AppCard / Sidebar / Home）
 * 统一通过 AppDefinition.icon 消费，禁止在各页面各自乱选图标（§13）。
 *
 * 图标名均为项目 Icon.vue 内置的 lucide 风格 SVG（已逐一校验存在于 Icon.vue），
 * 不引入第二套图标库、不使用 emoji（§11-§12）。
 *
 * 语义匹配原则（§14）：用户不看文字也能大致猜到功能。
 * 若 Icon.vue 后续扩充图标集，只需在此调整映射，无需改动任何消费方。
 */
export const APP_ICONS: Record<string, string> = {
  capture: 'inbox',
  'spaced-review': 'repeat',
  flashcard: 'layers', // 闪卡：层叠卡片
  quiz: 'check-circle', // 练习：答题校验（替代旧 iconfont icon-kaoshi）
  mistake: 'alert-circle', // 错题：警示
  exam: 'graduation-cap', // 考试：智能考试中心
  'mock-interview': 'briefcase', // Phase 3H 模拟面试：岗位面试
  focus: 'timer', // 专注：番茄钟
  feynman: 'brain', // 费曼：理解
  cornell: 'notebook-pen', // 康奈尔：笔记
  'memory-palace': 'map', // 记忆宫殿：空间地图（替代旧 share-2）
  'knowledge-graph': 'share-2', // 知识图谱：节点连接（替代旧 iconfont icon-zhishitupubaocun）
  coding: 'code', // 代码练习：代码
  mindmap: 'git-branch', // 思维导图：分支
  drawing: 'palette', // 绘图：调色板
  'ai-writing': 'wand-2', // 智能写作：AI 魔法
  notes: 'file-text', // 笔记：文档
  'tech-english': 'book-open', // 专业英语：打开的书本
  // ── UI/UX V1.0 效率工具（工具工作区）
  tasks: 'list-todo', // 任务：待办清单
  'check-in': 'calendar-check', // 签到：日历打勾
  habits: 'check-check', // 习惯打卡：连续勾选
  calendar: 'calendar', // 日历：日历
  'today-plan': 'list-checks', // 今日计划：今日清单
}
