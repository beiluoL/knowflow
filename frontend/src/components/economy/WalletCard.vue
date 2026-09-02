<template>
  <div class="wallet-card" :style="{ borderColor: 'var(--kb-border)' }">
    <div class="wallet-head">
      <span class="level-badge" :style="{ background: 'var(--kb-primary-soft)', color: 'var(--kb-primary)' }">
        Lv.{{ wallet?.level ?? 1 }}
      </span>
      <span class="coin-badge" :style="{ background: 'var(--kb-warning-subtle)', color: 'var(--kb-warning)' }">
        <Icon name="credit-card" :size="14" />
        {{ wallet?.coin ?? 0 }} Coin
      </span>
    </div>
    <div class="xp-row">
      <div class="xp-bar">
        <div class="xp-fill" :style="{ width: `${xpPercent}%` }"></div>
      </div>
      <span class="xp-text">
        {{ wallet?.xp ?? 0 }} XP
        <template v-if="wallet"> / {{ wallet.nextLevelExp }} XP</template>
      </span>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
// Architecture-2：钱包卡片（只读展示，§112：不修改任何 xp/coin；数据来自 wallet store = 后端）。
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import type { WalletVO } from '@/economy/types'

const props = defineProps<{ wallet: WalletVO | null }>()

const xpPercent = computed(() => {
  if (!props.wallet || !props.wallet.nextLevelExp) return 0
  const floor = Math.max(0, props.wallet.nextLevelExp - 100)
  return Math.min(100, Math.round(((props.wallet.xp - floor) / 100) * 100))
})
</script>

<style scoped>
.wallet-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
}
.wallet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.level-badge,
.coin-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: var(--kb-fs-caption);
  font-weight: 600;
}
.xp-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.xp-bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.xp-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--kb-primary);
  transition: width 0.3s ease;
}
.xp-text {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  white-space: nowrap;
}
</style>
