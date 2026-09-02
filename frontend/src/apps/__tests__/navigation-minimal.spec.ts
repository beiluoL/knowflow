// @vitest-environment happy-dom
/**
 * UI/UX 3.2 Core Menu Minimalism（§140-153）
 *
 * 核心断言：Sidebar = 「核心工作区固定入口」 + 「用户 enabled 的 App」，
 * 任何固定二级业务菜单都不再进入 Resolver 输出；同时 Route 全部保留（§51）。
 */
import { describe, it, expect } from 'vitest'
import { resolveNavigation } from '@/apps/navigation'
import { getAppByKey } from '@/apps/registry'
import { findGroupByPath, findActiveGroup } from '@/config/navigation'
import router from '@/router'
import type { UserAppState } from '@/apps/types'

function state(installed: boolean, enabled: boolean, sortOrder = 0): UserAppState {
  return { installed, enabled, sortOrder }
}

/** 取某个工作区的子项路径列表 */
function pathsOf(label: string, userApps: Record<string, UserAppState>): string[] {
  const g = resolveNavigation(true, false, userApps).find((x) => x.label === label)
  return (g?.children ?? []).map((c) => c.path)
}

/** 取未登录态下某个工作区的子项路径列表 */
function pathsOfGuest(label: string): string[] {
  const g = resolveNavigation(false, false, {}).find((x) => x.label === label)
  return (g?.children ?? []).map((c) => c.path)
}

/* ============================================================================
 * §146/§147/§141：核心固定入口恒定存在
 * ==========================================================================*/
describe('核心固定入口（3.2 §135/§141/§146/§147）', () => {
  it('一级导航恒为 5 个：首页 / 学习 / 知识 / AI / 应用', () => {
    expect(resolveNavigation(true, false, {}).map((g) => g.label)).toEqual([
      '首页',
      '学习',
      '知识',
      'AI',
      '应用',
    ])
  })

  it('未启用任何 App 时，学习区只有「学习首页」', () => {
    expect(pathsOf('学习', {})).toEqual(['/learning/center'])
  })

  it('未启用任何 App 时，知识区只有「知识首页」', () => {
    expect(pathsOf('知识', {})).toEqual(['/knowledge'])
  })

  it('未启用任何 App 时，AI 区只有「AI 助手」', () => {
    expect(pathsOf('AI', {})).toEqual(['/chat'])
  })

  it('固定入口标签为 学习首页 / 知识首页 / AI 助手（§28/§29/§31）', () => {
    const labels = resolveNavigation(true, false, {})
      .filter((g) => g.children)
      .map((g) => g.children![0].label)
    expect(labels).toEqual(['学习首页', '知识首页', 'AI 助手'])
  })
})

/* ============================================================================
 * §142-§145：启用 / 停用即时反映到导航
 * ==========================================================================*/
describe('启用 → 出现，停用 → 消失（3.2 §142-§145）', () => {
  it('启用 Quiz：学习出现 /learning/quiz', () => {
    expect(pathsOf('学习', { quiz: state(true, true) })).toContain('/learning/quiz')
  })

  it('停用 Quiz：学习不再出现 /learning/quiz', () => {
    expect(pathsOf('学习', { quiz: state(true, false) })).not.toContain('/learning/quiz')
  })

  it('启用 Knowledge Graph：知识出现 /learning/knowledge-graph', () => {
    expect(pathsOf('知识', { 'knowledge-graph': state(true, true) })).toContain('/learning/knowledge-graph')
  })

  it('停用 Knowledge Graph：知识不再出现', () => {
    expect(pathsOf('知识', { 'knowledge-graph': state(true, false) })).not.toContain('/learning/knowledge-graph')
  })

  it('停用后「学习首页」仍在（§146：固定入口不受 App 状态影响）', () => {
    const paths = pathsOf('学习', { quiz: state(true, false), flashcard: state(true, false) })
    expect(paths).toEqual(['/learning/center'])
  })

  it('未安装（installed=false）的 App 不进导航', () => {
    expect(pathsOf('学习', { feynman: state(false, true) })).not.toContain('/workbench/story')
  })
})

/* ============================================================================
 * §148/§149：旧的固定二级业务菜单不得再成为 Resolver children
 * ==========================================================================*/
describe('已移除的固定二级菜单（3.2 §148/§149）', () => {
  const REMOVED_LEARNING = [
    '/learning/today-plan', // 今日计划
    '/learning/paths', // 学习路径
    '/learning/review', // 复习计划
    '/learning/mastery', // 掌握度
    '/learning/report', // 学习报告
    '/challenge', // 挑战
    '/workbench', // 学习工作台
    '/learning/pomodoro', // 番茄钟
    '/learning/focus', // 专注模式
  ]

  const REMOVED_KNOWLEDGE = [
    '/docs', // 文档库
    '/categories', // 分类浏览
    '/search', // 搜索
    '/community', // 社区讨论
    '/study-group', // 学习小组
    '/messages', // 私信
  ]

  const REMOVED_AI = ['/coding/agent'] // 编程 Agent

  /**
   * 覆盖「所有 App 全启用」的极端场景：即使把所有 App 都打开，
   * 这些路径也不应出现在导航里 —— 证明它们确实是被删除的固定菜单，而不是漏配的 App。
   */
  function allAppsEnabled(): Record<string, UserAppState> {
    return {
      flashcard: state(true, true),
      quiz: state(true, true),
      mistake: state(true, true),
      coding: state(true, true),
      feynman: state(true, true),
      'memory-palace': state(true, true),
      focus: state(true, true),
      'knowledge-graph': state(true, true),
      cornell: state(true, true),
      mindmap: state(true, true),
      notes: state(true, true),
      drawing: state(true, true),
      'ai-writing': state(true, true),
    }
  }

  it('学习工作区不再出现任何旧固定菜单', () => {
    const paths = pathsOf('学习', allAppsEnabled())
    for (const p of REMOVED_LEARNING) expect(paths).not.toContain(p)
  })

  it('知识工作区不再出现任何旧固定菜单', () => {
    const paths = pathsOf('知识', allAppsEnabled())
    for (const p of REMOVED_KNOWLEDGE) expect(paths).not.toContain(p)
  })

  it('AI 工作区不再出现 编程 Agent 固定菜单', () => {
    const paths = pathsOf('AI', allAppsEnabled())
    for (const p of REMOVED_AI) expect(paths).not.toContain(p)
  })

  it('每个工作区的固定入口恒为 1 个（§28/§29/§31）', () => {
    const minimal = resolveNavigation(true, false, {})
    for (const label of ['学习', '知识', 'AI']) {
      expect(minimal.find((g) => g.label === label)!.children).toHaveLength(1)
    }
  })
})

/* ============================================================================
 * §150：Route 未被误删（删菜单 ≠ 删功能）
 * ==========================================================================*/
describe('Route 仍然保留（3.2 §51/§150）', () => {
  const EXPECTED_ROUTES = [
    '/learning/center',
    '/learning/today-plan',
    '/learning/paths',
    '/learning/review',
    '/learning/mastery',
    '/learning/report',
    '/learning/pomodoro',
    '/learning/focus',
    '/challenge',
    '/workbench',
    '/knowledge',
    '/docs',
    '/categories',
    '/search',
    '/community',
    '/study-group',
    '/messages',
    '/chat',
    '/coding/agent',
    '/mistakes',
    '/learning/quiz',
    '/learning/flashcards',
    '/learning/knowledge-graph',
    '/mindmap',
    '/drawing',
    '/notes',
    '/workbench/palace',
    '/workbench/story',
    '/learning/writing',
  ]

  it('被移除导航入口的页面，其 Route 全部仍可解析', () => {
    const known = new Set(router.getRoutes().map((r) => r.path))
    const missing = EXPECTED_ROUTES.filter((p) => !known.has(p))
    expect(missing).toEqual([])
  })
})

/* ============================================================================
 * §151：Memory Palace 归位
 * ==========================================================================*/
describe('Memory Palace（3.2 §71/§151）', () => {
  it('navSection = learning（3.1 遗留项已补齐）', () => {
    const palace = getAppByKey('memory-palace')!
    expect(palace.navSection).toBe('learning')
    expect(palace.navVisible).toBe(true)
  })

  it('真实 category 仍为 MEMORY，未被擅自修改（§72）', () => {
    expect(getAppByKey('memory-palace')!.category).toBe('MEMORY')
  })

  it('启用后进入学习导航，停用后消失', () => {
    expect(pathsOf('学习', { 'memory-palace': state(true, true) })).toContain('/workbench/palace')
    expect(pathsOf('学习', { 'memory-palace': state(true, false) })).not.toContain('/workbench/palace')
  })
})

/* ============================================================================
 * §152：Focus 不重复
 * ==========================================================================*/
describe('Focus 不重复（3.2 §16/§74/§152）', () => {
  it('focus App 不进动态菜单（navVisible=false）', () => {
    expect(getAppByKey('focus')!.navVisible).toBe(false)
  })

  it('导航中不出现「专注」与 /learning/pomodoro 重复项', () => {
    const all = resolveNavigation(true, false, { focus: state(true, true) })
    const allPaths = all.flatMap((g) => (g.children ?? []).map((c) => c.path))
    expect(allPaths.filter((p) => p === '/learning/pomodoro')).toHaveLength(0)
  })
})

/* ============================================================================
 * §153：AI Writing 只有启用后才进 AI 二级菜单
 * ==========================================================================*/
describe('AI Writing（3.2 §31/§153）', () => {
  it('未启用：AI 区只有 AI 助手', () => {
    expect(pathsOf('AI', {})).toEqual(['/chat'])
  })

  it('启用后：AI 区出现 /learning/writing，且 AI 助手仍在首位', () => {
    const paths = pathsOf('AI', { 'ai-writing': state(true, true) })
    expect(paths).toEqual(['/chat', '/learning/writing'])
  })

  it('停用后：AI 区回到只有 AI 助手', () => {
    expect(pathsOf('AI', { 'ai-writing': state(true, false) })).toEqual(['/chat'])
  })
})

/* ============================================================================
 * §84-§87：排序 —— 固定入口不参与 sortOrder，动态 App 在固定入口之后
 * ==========================================================================*/
describe('排序（3.2 §84-§87）', () => {
  it('固定入口永远排第一，即使 App sortOrder 为 0', () => {
    const paths = pathsOf('学习', {
      quiz: state(true, true, 0),
      mistake: state(true, true, 0),
      flashcard: state(true, true, 0),
    })
    expect(paths[0]).toBe('/learning/center')
  })

  it('动态 App 之间按 sortOrder 升序', () => {
    const paths = pathsOf('学习', {
      quiz: state(true, true, 9),
      mistake: state(true, true, 1),
      flashcard: state(true, true, 5),
    })
    expect(paths).toEqual(['/learning/center', '/mistakes', '/learning/flashcards', '/learning/quiz'])
  })

  it('未设 sortOrder 时回落到 registry.order', () => {
    // flashcard(order=1) < quiz(order=2) < mistake(order=3)
    const paths = pathsOf('学习', {
      quiz: state(true, true, 0),
      mistake: state(true, true, 0),
      flashcard: state(true, true, 0),
    })
    expect(paths).toEqual(['/learning/center', '/learning/flashcards', '/learning/quiz', '/mistakes'])
  })
})

/* ============================================================================
 * §46：用户隔离 —— 未登录不注入任何 App 子菜单
 * ==========================================================================*/
describe('未登录（3.2 §46）', () => {
  it('未登录：不注入任何 App 子菜单（只保留无需登录的固定入口）', () => {
    const groups = resolveNavigation(false, false, { flashcard: state(true, true) })
    const allPaths = groups.flatMap((g) => (g.children ?? []).map((c) => c.path))
    // 任何 App 路由都不得出现
    expect(allPaths).not.toContain('/learning/flashcards')
    expect(allPaths).not.toContain('/learning/quiz')
    expect(allPaths).not.toContain('/learning/knowledge-graph')
    // auth:true 的固定入口（学习首页 / AI 助手）对游客隐藏，无需登录的知识首页保留
    expect(allPaths).not.toContain('/learning/center')
    expect(allPaths).not.toContain('/chat')
    expect(pathsOfGuest('知识')).toEqual(['/knowledge'])
  })

  it('未登录：学习/AI 的 children 为空（auth 入口被过滤，非结构缺失）', () => {
    const groups = resolveNavigation(false, false, {})
    expect(groups.find((g) => g.label === '学习')!.children).toHaveLength(0)
    expect(groups.find((g) => g.label === 'AI')!.children).toHaveLength(0)
  })
})

/* ============================================================================
 * §96-99：被下沉功能仍归属原工作区（match 字段 → 父级高亮 / 面包屑 / 标题）
 * ==========================================================================*/
describe('工作区归属 match（3.2 §48/§96-§99）', () => {
  it('学习路径 / 复习 / 掌握度 / 报告 归属「学习」', () => {
    for (const p of ['/learning/paths', '/learning/review', '/learning/mastery', '/learning/report', '/challenge', '/learning/today-plan']) {
      expect(findGroupByPath(p)?.label).toBe('学习')
    }
  })

  it('文档库 / 分类 / 搜索 / 社区 归属「知识」', () => {
    for (const p of ['/docs', '/categories', '/search', '/community', '/study-group', '/messages']) {
      expect(findGroupByPath(p)?.label).toBe('知识')
    }
  })

  it('最长前缀优先：/workbench/notes（康奈尔）归「知识」而非「学习」', () => {
    expect(findGroupByPath('/workbench/notes')?.label).toBe('知识')
  })

  it('最长前缀优先：/workbench/palace（记忆宫殿）归「知识」而非「学习」', () => {
    expect(findGroupByPath('/workbench/palace')?.label).toBe('知识')
  })

  it('/workbench 本身仍归「学习」（学习工作台）', () => {
    expect(findGroupByPath('/workbench')?.label).toBe('学习')
  })

  it('App 路由也归属正确工作区（标题/高亮不漂移）', () => {
    expect(findGroupByPath('/learning/flashcards')?.label).toBe('学习')
    expect(findGroupByPath('/learning/knowledge-graph')?.label).toBe('知识')
    expect(findGroupByPath('/learning/writing')?.label).toBe('AI')
  })

  it('findActiveGroup 与 findGroupByPath 行为一致', () => {
    expect(findActiveGroup('/learning/paths')?.label).toBe('学习')
    expect(findActiveGroup('/docs')?.label).toBe('知识')
  })
})
