import { describe, expect, it } from 'vitest'
import { formatCoin } from '@/economy/format-balance'

describe('formatCoin', () => {
  it('无限钱包显示无穷符号', () => {
    expect(formatCoin({ coin: 0, unlimited: true })).toBe('∞')
  })

  it('普通与空钱包显示真实余额', () => {
    expect(formatCoin({ coin: 120, unlimited: false })).toBe('120')
    expect(formatCoin(null)).toBe('0')
  })
})
