// 钱包模块请求层：余额与流水查询（只读，§43-44；禁止 POST /wallet，§45）。
import { apiGet } from './request'
import type { WalletVO, LedgerVO } from '@/economy/types'

export const walletApi = {
  getWallet: () => apiGet<WalletVO>('/wallet'),
  getLedger: (params: { page?: number; size?: number; currency?: string } = {}) =>
    apiGet<LedgerVO[]>('/wallet/ledger', params),
}
