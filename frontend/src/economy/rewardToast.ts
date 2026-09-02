/**
 * Architecture-4/5：轻量奖励反馈（§109/§138/§198）
 *
 * 原则：Reward 由后端计算（§142），前端绝不自行 credit（§302-305）。
 * 做法：动作前后各拉一次真实钱包，diff 即为本次学习行为获得的真实奖励，
 * 以轻量 toast 呈现（+20 XP · +10 Coin），并返回 diff 供完成面板展示（Architecture-5 §80/§144）。
 *
 * 若 API 无法提供前后值，不伪造（§139-140）：diff 为 0 时静默（toast 仍可携带成功文案）。
 */

/** 本次学习行为的真实奖励（钱包 diff） */
export interface RewardDiff {
  xp: number
  coin: number
}

import { useWalletStore } from '@/stores/wallet'
import { notify } from '@/utils/toast'

/**
 * 执行一个会产生学习行为的动作，用真实钱包 diff 计算并返回本次奖励。
 * @param task 真实学习动作（如 quiz 提交 / 闪卡评分），内部会触发后端 LearningEvent → Reward
 * @param successText 动作成功后的文案（可选，奖励 toast 会附在其后）
 * @returns 本次真实奖励 diff（无奖励返回 null）
 */
export async function withRewardFeedback(
  task: () => Promise<void>,
  successText?: string,
): Promise<RewardDiff | null> {
  const walletStore = useWalletStore()
  // 动作前先拿真实基线（避免「0 → 大额」误判）
  await walletStore.fetchWallet(true)
  const beforeCoin = walletStore.wallet?.coin ?? 0
  const beforeXp = walletStore.wallet?.xp ?? 0

  await task()

  // 动作后刷新真实余额，计算本次真实奖励
  await walletStore.fetchWallet(true)
  const after = walletStore.wallet
  if (!after) return null
  const dCoin = after.coin - beforeCoin
  const dXp = after.xp - beforeXp
  const diff: RewardDiff = { xp: dXp, coin: dCoin }
  if (dCoin > 0 || dXp > 0) {
    const parts: string[] = []
    if (dXp > 0) parts.push(`+${dXp} XP`)
    if (dCoin > 0) parts.push(`+${dCoin} Coin`)
    notify(successText ? `${successText} · 奖励 ${parts.join(' · ')}` : `学习奖励 ${parts.join(' · ')}`, 'success')
  } else if (successText) {
    notify(successText, 'success')
  }
  return diff
}
