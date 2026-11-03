<template>
  <span class="job-status-badge" :class="`is-${status.toLowerCase()}`">
    {{ JOB_STATUS_LABELS[status] }}
  </span>
</template>

<script setup lang="ts">
import type { JobStatus } from '@/api/career/types'
import { JOB_STATUS_LABELS } from './career-labels'

defineProps<{ status: JobStatus }>()
</script>

<style scoped>
.job-status-badge {
  display: inline-flex;
  align-items: center;
  height: var(--kf-badge-height);
  padding: 0 var(--kf-badge-padding-x);
  border-radius: var(--kf-badge-radius);
  font-size: var(--kf-badge-font);
  line-height: 1;
  white-space: nowrap;
  color: var(--kf-muted-foreground);
  background: var(--kf-surface-2);
}
.job-status-badge.is-saved { color: var(--kf-primary); background: var(--kf-primary-subtle); }
.job-status-badge.is-to_apply { color: var(--kf-state-warning); background: color-mix(in srgb, var(--kf-state-warning) 10%, transparent); }
.job-status-badge.is-applied { color: var(--kf-primary); background: var(--kf-primary-subtle); }
.job-status-badge.is-written_test { color: var(--kf-state-warning); background: color-mix(in srgb, var(--kf-state-warning) 10%, transparent); }
.job-status-badge.is-interviewing { color: var(--kf-state-info); background: color-mix(in srgb, var(--kf-state-info) 10%, transparent); }
.job-status-badge.is-offer { color: var(--kf-state-success); background: color-mix(in srgb, var(--kf-state-success) 10%, transparent); }
.job-status-badge.is-rejected { color: var(--kf-state-error); background: color-mix(in srgb, var(--kf-state-error) 10%, transparent); }
</style>
