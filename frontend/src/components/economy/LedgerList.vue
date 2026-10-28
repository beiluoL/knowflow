<template>
  <div class="ledger-list" :style="{ borderColor: 'var(--kf-border)' }">
    <div v-if="loading" class="ledger-empty">加载中…</div>
    <div v-else-if="items.length === 0" class="ledger-empty">
      还没有流水记录，去学习会获得 XP 与 Coin。
    </div>
    <ul v-else class="ledger-items">
      <li v-for="item in items" :key="item.id" class="ledger-item">
        <span class="ledger-label">
          <span class="ledger-reason">{{ item.reasonLabel }}</span>
          <span class="ledger-time">{{ formatTime(item.createTime) }}</span>
        </span>
        <span
          class="ledger-amount"
          :class="item.amount >= 0 ? 'plus' : 'minus'"
        >{{ item.amount >= 0 ? '+' : '' }}{{ item.amount }} {{ item.currency }}</span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
// Architecture-2：流水列表（§113-114：只显示 +20 Coin / 完成章节 这类可读文案，不暴露内部字段）。
import type { LedgerVO } from '@/economy/types'

defineProps<{ items: LedgerVO[]; loading?: boolean }>()

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.ledger-list {
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  overflow: hidden;
}
.ledger-empty {
  padding: 28px 16px;
  text-align: center;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.ledger-items {
  list-style: none;
  margin: 0;
  padding: 0;
}
.ledger-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kf-border);
}
.ledger-item:last-child {
  border-bottom: none;
}
.ledger-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.ledger-reason {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.ledger-time {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ledger-amount {
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  white-space: nowrap;
}
.ledger-amount.plus {
  color: var(--kf-success);
}
.ledger-amount.minus {
  color: var(--kf-danger);
}
</style>
