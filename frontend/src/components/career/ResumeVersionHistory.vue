<template>
  <section class="version-history" aria-label="版本历史">
    <div class="version-history-head">
      <h3 class="kf-text-card-title">版本历史</h3>
      <span class="kf-text-caption">共 {{ versions.length }} 个不可变版本</span>
    </div>
    <p v-if="!versions.length" class="kf-text-caption">尚未加载版本历史。</p>
    <ul v-else class="version-history-list">
      <li v-for="version in versions" :key="version.version">
        <button
          type="button"
          class="version-history-item"
          :class="{ 'is-active': version.version === activeVersion }"
          @click="emit('select', version.version)"
        >
          <span class="version-history-no">v{{ version.version }}</span>
          <span class="version-history-meta">{{ version.confirmed ? '已确认' : '待确认' }}</span>
          <span class="version-history-date">{{ formatDateTime(version.createdAt) }}</span>
          <span v-if="version.version === currentVersion" class="version-history-current">当前</span>
        </button>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import type { CareerVersion } from '@/api/career/types'
import { formatDateTime } from './career-labels'

defineProps<{ versions: CareerVersion[]; currentVersion: number; activeVersion: number }>()
const emit = defineEmits<{ select: [version: number] }>()
</script>

<style scoped>
.version-history {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
}
.version-history-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-2);
}
.version-history-list {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}
.version-history-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--kf-space-2);
  width: 100%;
  min-height: 36px;
  padding: 0 var(--kf-space-2);
  border: 1px solid transparent;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  text-align: left;
  cursor: pointer;
}
.version-history-item:hover {
  background: var(--kf-surface-2);
}
.version-history-item.is-active {
  border-color: var(--kf-primary-tint);
  background: var(--kf-primary-subtle);
  color: var(--kf-foreground);
}
.version-history-item:focus-visible {
  outline: 2px solid var(--kf-primary);
  outline-offset: 1px;
}
.version-history-no {
  font-weight: 600;
  color: var(--kf-foreground);
}
.version-history-date {
  font-size: var(--kf-fs-caption);
}
.version-history-current {
  grid-column: 3;
  font-size: var(--kf-fs-caption);
  color: var(--kf-primary);
}
</style>
