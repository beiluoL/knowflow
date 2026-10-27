/**
 * Architecture-5：学习结果面板展示纯函数（可单测 §144）
 *
 * 原则（§80-82/§139-140）：无真实数据就不显示该行，绝不伪造「0」或假快照。
 */

/** 奖励 diff → 展示文案（无奖励返回 null，面板不显示该行） */
export function formatRewardText(reward: { xp: number; coin: number } | null | undefined): string | null {
  if (!reward) return null
  if (reward.xp <= 0 && reward.coin <= 0) return null
  const parts: string[] = []
  if (reward.xp > 0) parts.push(`+${reward.xp} XP`)
  if (reward.coin > 0) parts.push(`+${reward.coin} Coin`)
  return parts.join(' · ')
}

/** 掌握度是否「已更新」（由真实 LearningEvent 链推断；无前后快照只显示「已更新」§139-140） */
export function masteryUpdatedFromReward(reward: { xp: number; coin: number } | null | undefined): boolean {
  // 事件触发 → Reward 产生，掌握度引擎会随事件重算；无奖励（事件未触发）则不明示
  return !!(reward && (reward.xp > 0 || reward.coin > 0))
}
