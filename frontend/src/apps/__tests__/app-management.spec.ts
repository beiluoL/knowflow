// @vitest-environment happy-dom
/**
 * UI/UX 3.1 §273-281：App Management 单测
 * 覆盖：App State Matrix（§300）/ Navigation Matrix（§301）/ App Lifecycle（§302）/ 重新启用无需购买（§280）。
 */
import { describe, it, expect } from 'vitest'
import {
  resolveNavigation,
  resolveAppNavChildren,
  canShowInNavigation,
  navSectionLabel,
} from '@/apps/navigation'
import { getAppByKey, getApps, APP_NAV_META } from '@/apps/registry'
import { isAppPlatformDisabled } from '@/components/workflow/actionState'
import type { AppCategory, AppDefinition, AppNavSection, UserAppState } from '@/apps/types'

function st(installed: boolean, enabled: boolean, sortOrder = 0): UserAppState {
  return { installed, enabled, sortOrder }
}

/** 在解析结果中查找某个工作区的子菜单路径 */
function menuPaths(section: '学习' | '知识' | 'AI', userApps: Record<string, UserAppState>): string[] {
  const group = resolveNavigation(true, false, userApps).find((g) => g.label === section)
  return (group?.children ?? []).map((c) => c.path)
}

describe('App State Matrix（§300）', () => {
  const flashcard = getAppByKey('flashcard')!

  it('installed=false, enabled=false → 导航隐藏 / 未获取', () => {
    expect(canShowInNavigation(flashcard, st(false, false))).toBe(false)
    expect(resolveAppNavChildren({ flashcard: st(false, false) }).learning).toHaveLength(0)
  })

  it('installed=true, enabled=false → 导航隐藏（我的应用「已停用」）', () => {
    expect(canShowInNavigation(flashcard, st(true, false))).toBe(false)
    expect(menuPaths('学习', { flashcard: st(true, false) })).not.toContain('/learning/flashcards')
  })

  it('installed=true, enabled=true → 导航可见（我的应用「已启用」）', () => {
    expect(canShowInNavigation(flashcard, st(true, true))).toBe(true)
    expect(menuPaths('学习', { flashcard: st(true, true) })).toContain('/learning/flashcards')
  })

  it('installed=false + enabled=true（非法组合）→ 归一化为不可见（§152）', () => {
    // 前端 store 会强制 enabled=false；Resolver 侧同样视为不可见，防御后端脏数据
    expect(canShowInNavigation(flashcard, st(false, true))).toBe(false)
  })
})

describe('Navigation Matrix（§301）', () => {
  it('learning 分区 App 进入「学习」菜单', () => {
    const learningKeys = Object.entries(APP_NAV_META)
      .filter(([, meta]) => meta.navSection === 'learning')
      .map(([key]) => key)
    expect(learningKeys).toContain('flashcard')
    for (const key of learningKeys) {
      const app = getAppByKey(key)!
      expect(menuPaths('学习', { [key]: st(true, true) })).toContain(app.route)
    }
  })

  it('knowledge 分区 App 进入「知识」菜单', () => {
    const keys = Object.entries(APP_NAV_META)
      .filter(([, meta]) => meta.navSection === 'knowledge')
      .map(([key]) => key)
    expect(keys).toContain('knowledge-graph')
    for (const key of keys) {
      const app = getAppByKey(key)!
      expect(menuPaths('知识', { [key]: st(true, true) })).toContain(app.route)
    }
  })

  it('ai 分区 App 进入「AI」菜单（§94-97：由 Registry 分区决定，不为 UI 改分类）', () => {
    expect(menuPaths('AI', { 'ai-writing': st(true, true) })).toContain('/learning/writing')
  })

  it('启用但分区不同的 App 不会串到别的菜单', () => {
    const paths = menuPaths('知识', { flashcard: st(true, true) })
    expect(paths).not.toContain('/learning/flashcards')
  })
})

describe('Toast 工作区标签（§225）', () => {
  it('learning/knowledge/ai 分别得到中文工作区名', () => {
    expect(navSectionLabel(getAppByKey('feynman')!)).toBe('学习')
    expect(navSectionLabel(getAppByKey('knowledge-graph')!)).toBe('知识')
    expect(navSectionLabel(getAppByKey('ai-writing')!)).toBe('AI')
  })

  it('focus 安装后的入口属于工具', () => {
    expect(navSectionLabel(getAppByKey('focus')!)).toBe('工具')
  })
})

describe('App Lifecycle（§302）', () => {
  it('Discover → 获取 → 启用 → 进菜单 → 停用 → 隐藏 → 重新启用 → 回菜单', () => {
    // 1. 未获取
    let userApps: Record<string, UserAppState> = { feynman: st(false, false) }
    expect(menuPaths('学习', userApps)).not.toContain('/workbench/story')

    // 2. 获取（installed+enabled）
    userApps = { feynman: st(true, true) }
    expect(menuPaths('学习', userApps)).toContain('/workbench/story')

    // 3. 停用（只改 enabled=false，installed 保留 → 数据不删 §16/§31）
    userApps = { feynman: st(true, false) }
    expect(menuPaths('学习', userApps)).not.toContain('/workbench/story')

    // 4. 重新启用（无需再次获取/购买 §34-36/§280）
    userApps = { feynman: st(true, true) }
    expect(menuPaths('学习', userApps)).toContain('/workbench/story')
  })

  it('停用不删除任何数据：installed 仍为 true', () => {
    const disabled = st(true, false)
    expect(disabled.installed).toBe(true)
    expect(disabled.enabled).toBe(false)
  })
})

describe('排序 → 导航（§69-86）', () => {
  it('sortOrder 决定菜单顺序，且不使用 App 名字母序（§72）', () => {
    const userApps: Record<string, UserAppState> = {
      feynman: st(true, true, 0),
      flashcard: st(true, true, 1),
      quiz: st(true, true, 2),
    }
    const dynamic = resolveAppNavChildren(userApps).learning.map((c) => c.path)
    // 三个动态 App 在「学习」子菜单尾部，顺序按 sortOrder
    const tail = dynamic
    expect(tail).toEqual(['/workbench/story', '/learning/flashcards', '/learning/quiz'])
  })

  it('无 sortOrder 时回落到 registry.order（§71/§73）', () => {
    const userApps: Record<string, UserAppState> = {
      quiz: st(true, true, 0),
      mistake: st(true, true, 0),
    }
    const paths = resolveAppNavChildren(userApps).learning.map((c) => c.path)
    // quiz(order=2) 在 mistake(order=3) 之前
    expect(paths.indexOf('/learning/quiz')).toBeLessThan(paths.indexOf('/mistakes'))
  })
})

describe('平台停用可见性（§34/§175）', () => {
  const flashcard = getAppByKey('flashcard')!
  /** 构造「平台下架」的 App：只有 status 不同，其余字段与真实 Registry 一致 */
  const withStatus = (status: AppDefinition['status']): AppDefinition => ({ ...flashcard, status })

  it('status=LEGACY → 即使 installed+enabled 也不进导航（§34）', () => {
    expect(canShowInNavigation(withStatus('LEGACY'), st(true, true))).toBe(false)
  })

  it('status=BETA → 同样不进导航（只有 ACTIVE 可见）', () => {
    // 与 CTA 同源：actionState 对 BETA 同样判定为「暂不可用」，导航不得自造另一套规则（§175）
    expect(canShowInNavigation(withStatus('BETA'), st(true, true))).toBe(false)
  })

  it('status=ACTIVE + installed + enabled → 可见（回归保护）', () => {
    expect(flashcard.status).toBe('ACTIVE')
    expect(canShowInNavigation(flashcard, st(true, true))).toBe(true)
  })

  it('导航与 CTA 共用同一判定函数，不重复实现（§34）', () => {
    expect(isAppPlatformDisabled('LEGACY')).toBe(true)
    expect(isAppPlatformDisabled('BETA')).toBe(true)
    expect(isAppPlatformDisabled('ACTIVE')).toBe(false)
    expect(isAppPlatformDisabled(undefined)).toBe(false)
  })

  it('不变量：当前 Registry 中「允许进导航的 App」不存在已停用残留', () => {
    for (const app of getApps()) {
      if (app.navVisible && app.navSection) expect(app.status).toBe('ACTIVE')
    }
  })
})

describe('用户隔离（§46/§180）', () => {
  it('用户 A 启用、用户 B 未启用 → 同一份 Registry 解析出不同菜单', () => {
    const a: Record<string, UserAppState> = { feynman: st(true, true) }
    const b: Record<string, UserAppState> = {}
    expect(menuPaths('学习', a)).toContain('/workbench/story')
    expect(menuPaths('学习', b)).not.toContain('/workbench/story')
  })

  it('解析是纯函数：B 的启用不会污染 A 的解析结果', () => {
    const treeA = resolveNavigation(true, false, { feynman: st(true, false) })
    const treeB = resolveNavigation(true, false, { feynman: st(true, true) })
    const hasStory = (t: ReturnType<typeof resolveNavigation>): boolean =>
      t.find((g) => g.label === '学习')!.children!.some((c) => c.path === '/workbench/story')

    expect(hasStory(treeB)).toBe(true)
    expect(hasStory(treeA)).toBe(false)
    // 再次解析 A，结果不变（无共享可变状态）
    expect(hasStory(resolveNavigation(true, false, { feynman: st(true, false) }))).toBe(false)
  })

  it('未登录 → 不注入任何动态 App 子菜单（§46）', () => {
    const groups = resolveNavigation(false, false, { feynman: st(true, true) })
    expect(groups.find((g) => g.label === '学习')!.children!.some((c) => c.path === '/workbench/story')).toBe(false)
  })
})

describe('Category → Workspace 映射（§177）', () => {
  /**
   * 各工作区允许的 Registry 分类（现状快照）。
   * 新增 App 若越界，需要同时更新 APP_NAV_META 与本表 —— 保证分类语义不被 UI 随意改写（§19）。
   */
  const ALLOWED: Record<AppNavSection, AppCategory[]> = {
    learning: ['MEMORY', 'PRACTICE', 'UNDERSTANDING', 'CODING'],
    knowledge: ['ORGANIZATION', 'UNDERSTANDING'],
    ai: ['AI'],
    tools: ['FOCUS', 'PRODUCTIVITY'],
  }

  it('每个进导航的 App，其 category 都落在其工作区允许范围内', () => {
    for (const app of getApps()) {
      if (!app.navVisible || !app.navSection) continue
      expect(ALLOWED[app.navSection]).toContain(app.category)
    }
  })

  it('映射集中在 APP_NAV_META（单一来源），UI 层不各写一份', () => {
    for (const app of getApps()) {
      if (!app.navVisible) continue
      expect(APP_NAV_META[app.key]).toBeDefined()
      expect(APP_NAV_META[app.key].navSection).toBe(app.navSection)
    }
  })

  it('同一 category 可分属不同工作区：费曼(理解)→学习，知识图谱(理解)→知识', () => {
    expect(getAppByKey('feynman')!.category).toBe('UNDERSTANDING')
    expect(getAppByKey('feynman')!.navSection).toBe('learning')
    expect(getAppByKey('knowledge-graph')!.category).toBe('UNDERSTANDING')
    expect(getAppByKey('knowledge-graph')!.navSection).toBe('knowledge')
  })

  it('注册表工作区包含获批的工具分区', () => {
    const sections = new Set(Object.values(APP_NAV_META).map((m) => m.navSection))
    expect([...sections].sort()).toEqual(['ai', 'knowledge', 'learning', 'tools'])
  })
})
