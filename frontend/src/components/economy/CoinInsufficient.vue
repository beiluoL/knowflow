<template>
  <div class="coin-insufficient" :style="{ borderColor: 'var(--kf-border)' }">
    <div class="ci-title">余额不足</div>
    <p class="ci-desc">
      当前 <strong>{{ have }}</strong> Coin，需要 <strong>{{ need }}</strong> Coin，还差
      <strong>{{ missing }}</strong> Coin
    </p>
    <div class="ci-actions">
      <Button size="sm" variant="primary" @click="$emit('learn')">去学习赚取</Button>
      <Button size="sm" variant="ghost" @click="$emit('close')">稍后再说</Button>
    </div>
  </div>
</template>

<script setup lang="ts">
// Architecture-2：余额不足引导（§56/§96：学习 → 奖励 → Coin → 再购买 的闭环入口）。
import Button from '@/components/ui/Button.vue'

defineProps<{
  have: number
  need: number
  missing: number
}>()

defineEmits<{
  (e: 'learn'): void
  (e: 'close'): void
}>()
</script>

<style scoped>
.coin-insufficient {
  padding: 16px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ci-title {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-warning);
}
.ci-desc {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  line-height: 1.6;
}
.ci-actions {
  display: flex;
  gap: 8px;
}
</style>
