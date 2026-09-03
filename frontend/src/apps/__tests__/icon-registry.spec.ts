/**
 * UI/UX 3.3：App Icon Registry 单测
 * 确保图标集中、语义正确、且不再依赖 iconfont 类（统一到项目内置 lucide 风格 SVG）。
 */
import { describe, it, expect } from 'vitest'
import { APP_ICONS } from '@/apps/icon-registry'
import { APP_DEFINITIONS, getAppByKey } from '@/apps/registry'

describe('App Icon Registry (UI/UX 3.3)', () => {
  it('covers every App definition', () => {
    for (const app of APP_DEFINITIONS) {
      expect(APP_ICONS[app.key], `missing icon for ${app.key}`).toBeTruthy()
    }
  })

  it('semantic fixes applied and no iconfont class leaks', () => {
    expect(APP_ICONS.quiz).toBe('check-circle')
    expect(APP_ICONS['knowledge-graph']).toBe('share-2')
    expect(APP_ICONS['memory-palace']).toBe('map')
    for (const value of Object.values(APP_ICONS)) {
      expect(value.startsWith('icon-'), `${value} should not be an iconfont class`).toBe(false)
    }
  })

  it('registry icons are derived from APP_ICONS (single source of truth)', () => {
    // registry.ts 模块加载时会把 APP_ICONS 派生到 AppDefinition.icon
    expect(getAppByKey('quiz')!.icon).toBe('check-circle')
    expect(getAppByKey('knowledge-graph')!.icon).toBe('share-2')
    expect(getAppByKey('memory-palace')!.icon).toBe('map')
    expect(getAppByKey('flashcard')!.icon).toBe('layers')
  })
})
