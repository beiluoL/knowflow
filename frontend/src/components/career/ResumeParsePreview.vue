<template>
  <section class="parse-preview" aria-label="结构化结果预览">
    <div class="parse-preview-head">
      <div>
        <h3 class="kf-text-card-title">结构化内容预览 · v{{ record.version }}</h3>
        <p class="kf-text-caption">
          结构化内容保存在待确认版本。请逐项核对原文证据后再确认；原有历史版本不会被覆盖。
        </p>
      </div>
      <span class="parse-preview-badge" :class="{ 'is-confirmed': record.confirmed }">
        {{ record.confirmed ? '已确认' : '待确认' }}
      </span>
    </div>
    <div class="parse-preview-grid">
      <div v-for="section in usedSections" :key="section.key" class="parse-preview-section">
        <p class="parse-preview-label">{{ section.label }} · {{ record.sections[section.key]?.length ?? 0 }} 项</p>
        <ul>
          <li v-for="claim in record.sections[section.key]" :key="claim.quote + claim.value">
            <span>{{ claim.value }}</span>
            <blockquote>{{ claim.quote }}</blockquote>
          </li>
        </ul>
      </div>
    </div>
    <div class="parse-preview-actions">
      <Button variant="secondary" size="sm" :disabled="busy" @click="emit('edit')">
        <Icon name="check" :size="14" /><span>逐项修改</span>
      </Button>
      <Button size="sm" :disabled="busy || record.confirmed" @click="emit('confirm')">
        <Icon name="check-circle" :size="14" /><span>{{ record.confirmed ? '此版本已确认' : '确认解析结果' }}</span>
      </Button>
    </div>
  </section>
</template>

<script setup lang="ts">
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import type { CareerRecord } from '@/api/career/types'
import { RESUME_SECTIONS } from './career-labels'

const props = defineProps<{ record: CareerRecord; busy?: boolean }>()
const emit = defineEmits<{ confirm: []; edit: [] }>()

const usedSections = RESUME_SECTIONS.filter(section => (props.record.sections[section.key]?.length ?? 0) > 0)
</script>

<style scoped>
.parse-preview {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
.parse-preview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-3);
}
.parse-preview-badge {
  height: var(--kf-badge-height);
  padding: 0 var(--kf-badge-padding-x);
  border-radius: var(--kf-badge-radius);
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-badge-font);
  line-height: var(--kf-badge-height);
  white-space: nowrap;
}
.parse-preview-badge.is-confirmed {
  background: color-mix(in srgb, var(--kf-state-success) 10%, transparent);
  color: var(--kf-state-success);
}
.parse-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--kf-space-3);
}
.parse-preview-section ul {
  margin: var(--kf-space-1) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
}
.parse-preview-label {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.parse-preview-section li {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-1);
  padding: var(--kf-space-2);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  font-size: var(--kf-fs-body-sm);
}
.parse-preview-section blockquote {
  margin: 0;
  padding-left: var(--kf-space-2);
  border-left: 2px solid var(--kf-border);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
}
.parse-preview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kf-space-2);
}
</style>
