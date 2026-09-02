/**
 * Architecture-2：经济层常量与文案（§109/§286）。
 * 后端返回业务错误码（EconomyError），此处映射为友好文案；余额不足由前端按文案关键词识别并展示「去学习赚取」（§75/§96）。
 */

/** 后端经济层错误码（与 com.knowflow.store.EconomyError 对应） */
export const EconomyErrorCode = {
  INSUFFICIENT_BALANCE: 2001,
  ALREADY_OWNED: 2002,
  PRODUCT_DISABLED: 2003,
  APP_DISABLED: 2004,
  INVALID_PRODUCT: 2005,
  INVALID_APP: 2006,
} as const

/** 错误码 → 友好文案（§286） */
export const EconomyErrorText: Record<number, string> = {
  [EconomyErrorCode.INSUFFICIENT_BALANCE]: '余额不足',
  [EconomyErrorCode.ALREADY_OWNED]: '该应用已拥有',
  [EconomyErrorCode.PRODUCT_DISABLED]: '该应用暂时无法购买',
  [EconomyErrorCode.APP_DISABLED]: '该应用暂不可用',
  [EconomyErrorCode.INVALID_PRODUCT]: '商品不存在',
  [EconomyErrorCode.INVALID_APP]: '无效的应用标识',
}

/** 余额不足判断（后端 message 含「余额不足」即触发「去学习赚取」引导，§75/§317） */
export function isInsufficientBalance(message: string): boolean {
  return typeof message === 'string' && message.includes('余额不足')
}

/** 学习入口（余额不足引导跳转，§96） */
export const LEARN_ENTRY = '/learning/center'
