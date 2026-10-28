<template>
  <article class="product-card" :style="{ borderColor: 'var(--kf-border)' }">
    <div class="pc-head">
      <span class="pc-icon" :style="{ background: 'var(--kf-primary-soft)', color: 'var(--kf-primary)' }" aria-hidden="true">
        <Icon name="gift" :size="18" />
      </span>
      <div class="min-w-0 flex-1">
        <h3 class="pc-name">{{ product.name }}</h3>
        <p class="pc-desc">{{ product.description }}</p>
      </div>
    </div>
    <div class="pc-foot">
      <span v-if="owned" class="pc-price owned">已拥有</span>
      <span v-else-if="product.priceCoin <= 0" class="pc-price free">免费</span>
      <span v-else class="pc-price">
        <Icon name="credit-card" :size="13" />
        {{ product.priceCoin }} Coin
      </span>
      <Button
        v-if="owned"
        size="sm"
        variant="secondary"
        @click="$emit('open', product)"
      >打开</Button>
      <Button
        v-else-if="product.priceCoin <= 0"
        size="sm"
        variant="primary"
        @click="$emit('get', product)"
      >获取</Button>
      <Button
        v-else-if="product.status !== 'ACTIVE'"
        size="sm"
        variant="ghost"
        disabled
      >已下架</Button>
      <Button
        v-else
        size="sm"
        variant="primary"
        :loading="purchasing"
        @click="$emit('purchase', product)"
      >兑换</Button>
    </div>
  </article>
</template>

<script setup lang="ts">
// Architecture-2：商品卡片（§110-111：纯展示 + emit，不直接 axios、不自己扣 Coin）。
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import type { ProductVO } from '@/economy/types'

defineProps<{
  product: ProductVO
  owned: boolean
  purchasing?: boolean
}>()

defineEmits<{
  (e: 'purchase', product: ProductVO): void
  (e: 'get', product: ProductVO): void
  (e: 'open', product: ProductVO): void
}>()
</script>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
}
.pc-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}
.pc-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: var(--kf-radius-md);
  flex-shrink: 0;
}
.pc-name {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
}
.pc-desc {
  margin-top: 2px;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.pc-price {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  color: var(--kf-warning);
}
.pc-price.owned {
  color: var(--kf-success);
}
.pc-price.free {
  color: var(--kf-success);
}
</style>
