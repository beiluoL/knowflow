// @vitest-environment happy-dom
/**
 * UI/UX 2.2 §222：导航测试（5 个一级入口）
 * 验证 navigation.ts 单一数据源收敛为 5 个顶级导航。
 */
import { describe, it, expect } from 'vitest'
import { NAVIGATION } from '@/config/navigation'

describe('NAVIGATION 一级导航（UI/UX 2.0 §19 / §222）', () => {
  it('has exactly 5 top-level entries', () => {
    expect(NAVIGATION).toHaveLength(6)
  })

  it('entries are 首页 / 学习 / 知识 / AI / 应用', () => {
    expect(NAVIGATION.map((g) => g.label)).toEqual(['首页', '学习', '知识', 'AI', '应用', '工具'])
  })

  it('each top-level entry declares an icon (no missing icon)', () => {
    for (const g of NAVIGATION) {
      expect(g.icon).toBeTruthy()
    }
  })
})

it('uses the same assistant speech-bubble icon for AI group and chat entry without changing the route',()=>{const group=NAVIGATION.find(g=>g.label==='AI')!;expect(group.icon).toBe('message-square-sparkles');expect(group.children?.find(c=>c.path==='/chat')?.icon).toBe(group.icon)})
