/**
 * Architecture-5：Next Action CTA 状态机（纯函数，可单测 §142-143）
 *
 * 统一 CTA 文案（§110-112）：免费→获取；付费→兑换；已拥有→打开；停用→重新启用；不可用→暂不可用。
 * 与 NextActionCard.vue 共用，保证「逻辑可测、展示组件薄」。
 */
import type { AppStatus } from '@/apps/types'

/** CTA 输入（全部为展示态推导所需的最小数据） */
export interface AppActionInput {
  isMiniApp: boolean
  /** App 平台状态（Registry status） */
  appStatus?: AppStatus
  installed: boolean
  enabled: boolean
  /** 商品信息加载中（付费判定未完成前禁止当作免费安装 §306-308） */
  productLoading: boolean
  /** 是否存在付费商品（存在且未拥有 = 付费） */
  hasProduct: boolean
  /** 是否已拥有（Entitlement，服务端真源） */
  owned: boolean
  priceCoin?: number
  /** 当前 Coin 余额（服务端真源） */
  coin: number
  /** 服务端授予的无限 Coin 能力 */
  unlimitedCoin?: boolean
}

/** CTA 输出（展示层直接消费） */
export interface AppActionState {
  text: string
  icon: string
  variant: 'primary' | 'secondary'
  disabled: boolean
  hint: string
  /** 需要跳转 App Store 兑换（未安装付费且 Coin 足够） */
  needsRedeem: boolean
  /** 余额不足（→ 去学习赚取，§91-92） */
  needsEarn: boolean
}

/** 平台停用（App.status !== ACTIVE） */
export function isAppPlatformDisabled(status?: AppStatus): boolean {
  return !!status && status !== 'ACTIVE'
}

export function resolveAppActionState(input: AppActionInput): AppActionState {
  const base: AppActionState = {
    text: '开始',
    icon: 'arrow-right',
    variant: 'primary',
    disabled: false,
    hint: '',
    needsRedeem: false,
    needsEarn: false,
  }

  if (!input.isMiniApp) return base

  // App 平台停用：禁止购买/进入（§166/§208）
  if (isAppPlatformDisabled(input.appStatus)) {
    return { ...base, text: '暂不可用', icon: 'alert-circle', disabled: true, hint: '该应用暂不可用' }
  }
  // 已安装已启用 → 打开（§164）
  if (input.installed && input.enabled) {
    return { ...base, text: '打开', icon: 'play' }
  }
  // 已安装停用 → 重新启用（无需再购买，§165/§207）
  if (input.installed) {
    return { ...base, text: '重新启用', icon: 'rotate-ccw', variant: 'secondary', hint: '无需再次购买，重新启用即可' }
  }
  // 商品信息加载中：禁止直接免费安装付费 App（§306-308）
  if (input.productLoading) {
    return { ...base, text: '加载中…', disabled: true }
  }
  // 付费（存在商品且未拥有）
  if (input.hasProduct && !input.owned) {
    const price = input.priceCoin ?? 0
    const needCoin = input.unlimitedCoin ? 0 : Math.max(0, price - input.coin)
    if (needCoin > 0) {
      return {
        ...base,
        text: '去学习赚取',
        icon: 'graduation-cap',
        hint: `还差 ${needCoin} Coin`,
        needsEarn: true,
      }
    }
    return { ...base, text: `兑换 · ${price} Coin`, icon: 'download', needsRedeem: true }
  }
  // 免费（无商品 或 已拥有）→ 获取后打开（§89/§308）
  return { ...base, text: '获取', icon: 'download' }
}
