import type { WalletVO } from '@/economy/types'

/** 统一 Coin 展示；无限能力由后端钱包契约提供。 */
export function formatCoin(wallet: Pick<WalletVO, 'coin' | 'unlimited'> | null): string {
  if (wallet?.unlimited) return '∞'
  return String(wallet?.coin ?? 0)
}
