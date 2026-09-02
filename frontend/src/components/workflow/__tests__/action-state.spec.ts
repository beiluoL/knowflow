/**
 * Architecture-5：CTA 状态机 + 结果面板展示单测（§142-144）
 */
import { describe, it, expect } from 'vitest'
import { resolveAppActionState, isAppPlatformDisabled, type AppActionInput } from '../actionState'
import { formatRewardText, masteryUpdatedFromReward } from '../resultFormat'

function input(partial: Partial<AppActionInput> = {}): AppActionInput {
  return {
    isMiniApp: true,
    appStatus: 'ACTIVE',
    installed: false,
    enabled: false,
    productLoading: false,
    hasProduct: false,
    owned: false,
    priceCoin: undefined,
    coin: 0,
    ...partial,
  }
}

describe('CTA 状态机（§142-143）', () => {
  it('已拥有 → [打开]（OWNED）', () => {
    const s = resolveAppActionState(input({ installed: true, enabled: true }))
    expect(s.text).toBe('打开')
    expect(s.disabled).toBe(false)
  })

  it('未安装·免费 → [获取]（FREE）', () => {
    const s = resolveAppActionState(input({ installed: false, hasProduct: false }))
    expect(s.text).toBe('获取')
  })

  it('未安装·付费·Coin 够 → [兑换 · N Coin]（PAID/REDEEM）', () => {
    const s = resolveAppActionState(input({ installed: false, hasProduct: true, owned: false, priceCoin: 200, coin: 300 }))
    expect(s.text).toBe('兑换 · 200 Coin')
    expect(s.needsRedeem).toBe(true)
    expect(s.needsEarn).toBe(false)
  })

  it('未安装·付费·Coin 不足 → [去学习赚取] + 还差 N Coin（INSUFFICIENT）', () => {
    const s = resolveAppActionState(input({ installed: false, hasProduct: true, owned: false, priceCoin: 200, coin: 120 }))
    expect(s.text).toBe('去学习赚取')
    expect(s.hint).toBe('还差 80 Coin')
    expect(s.needsEarn).toBe(true)
    expect(s.needsRedeem).toBe(false)
  })

  it('App 平台停用 → [暂不可用] 且禁用（DISABLED）', () => {
    const s = resolveAppActionState(input({ appStatus: 'BETA', installed: true, enabled: true }))
    expect(s.text).toBe('暂不可用')
    expect(s.disabled).toBe(true)
  })

  it('已安装·停用 → [重新启用]（ENABLE）', () => {
    const s = resolveAppActionState(input({ installed: true, enabled: false }))
    expect(s.text).toBe('重新启用')
    expect(s.variant).toBe('secondary')
    expect(s.hint).toContain('无需再次购买')
  })

  it('商品加载中 → [加载中…] 禁用（防止付费 App 被误当免费安装 §306-308）', () => {
    const s = resolveAppActionState(input({ installed: false, productLoading: true }))
    expect(s.text).toBe('加载中…')
    expect(s.disabled).toBe(true)
  })

  it('非 Mini App 动作 → [开始]', () => {
    const s = resolveAppActionState(input({ isMiniApp: false }))
    expect(s.text).toBe('开始')
  })
})

describe('isAppPlatformDisabled（§166/§208）', () => {
  it('ACTIVE 可正常使用', () => {
    expect(isAppPlatformDisabled('ACTIVE')).toBe(false)
  })
  it('BETA/LEGACY 视为平台停用', () => {
    expect(isAppPlatformDisabled('BETA')).toBe(true)
    expect(isAppPlatformDisabled('LEGACY')).toBe(true)
  })
})

describe('结果面板展示（§80-82/§139-140/§144）', () => {
  it('有奖励 → 展示奖励文案', () => {
    expect(formatRewardText({ xp: 20, coin: 10 })).toBe('+20 XP · +10 Coin')
    expect(formatRewardText({ xp: 0, coin: 5 })).toBe('+5 Coin')
  })

  it('无奖励 → 不显示（不伪造 0）', () => {
    expect(formatRewardText(null)).toBeNull()
    expect(formatRewardText(undefined)).toBeNull()
    expect(formatRewardText({ xp: 0, coin: 0 })).toBeNull()
  })

  it('掌握度更新：真实事件链（有奖励）→ 显示；无奖励 → 不明示', () => {
    expect(masteryUpdatedFromReward({ xp: 4, coin: 1 })).toBe(true)
    expect(masteryUpdatedFromReward(null)).toBe(false)
    expect(masteryUpdatedFromReward({ xp: 0, coin: 0 })).toBe(false)
  })
})
