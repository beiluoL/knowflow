/**
 * Architecture-2：经济层类型（§108）。
 * Wallet = 当前状态；Ledger = 历史事实；Product = 商品；Entitlement = 拥有资格。
 */

/** 钱包（§43：xp/coin/level/nextLevelExp） */
export interface WalletVO {
  userId: number
  /** 经验值（成长，不可购买） */
  xp: number
  /** 金币（平台内消费，不可提现/转账） */
  coin: number
  /** 等级 */
  level: number
  /** 下一级所需累计 exp 阈值 */
  nextLevelExp: number
}

/** 流水（§113-114：只暴露用户可理解字段，无 eventId/sourceType/rewardRuleId） */
export interface LedgerVO {
  id: number
  currency: 'XP' | 'COIN'
  amount: number
  direction: 'CREDIT' | 'DEBIT'
  reason: string
  reasonLabel: string
  balanceAfter: number
  createTime: string
}

/** 商品（App = 能力，Product = 商品，§34） */
export interface ProductVO {
  id: number
  productKey: string
  name: string
  description?: string
  type: string
  appKey?: string
  priceCoin: number
  status: string
  version: string
  /** 当前用户是否已拥有（来自 Entitlement，§58/§284） */
  owned: boolean
}

/** 购买结果（§174） */
export interface PurchaseResultVO {
  orderNo: string
  productName: string
  priceCoin: number
  balanceAfter: number
}
