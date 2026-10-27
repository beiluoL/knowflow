import { describe, expect, it } from 'vitest'
import { getAppByKey } from '@/apps/registry'
import { resolveAppNavChildren } from '@/apps/navigation'

describe('工作台独立应用注册', () => {
  it('收集箱安装启用后进入知识导航，停用后隐藏', () => {
    expect(getAppByKey('capture')?.route).toBe('/workbench/capture')
    expect(resolveAppNavChildren({ capture: { installed: true, enabled: true, sortOrder: 0 } }).knowledge.map(a => a.path)).toContain('/workbench/capture')
    expect(resolveAppNavChildren({ capture: { installed: true, enabled: false, sortOrder: 0 } }).knowledge.map(a => a.path)).not.toContain('/workbench/capture')
  })
  it('工作台复习独立于既有闪卡，按安装状态显示', () => {
    expect(getAppByKey('spaced-review')?.route).toBe('/workbench/review')
    expect(getAppByKey('flashcard')?.route).toBe('/learning/flashcards')
    expect(resolveAppNavChildren({ 'spaced-review': { installed: true, enabled: true, sortOrder: 0 } }).learning.map(a => a.path)).toContain('/workbench/review')
  })
})
