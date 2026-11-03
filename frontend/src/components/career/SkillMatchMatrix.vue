<template>
  <section class="skill-matrix" aria-label="技能匹配矩阵">
    <div class="skill-matrix-head">
      <h3 class="kf-text-card-title">技能匹配矩阵</h3>
      <span class="kf-text-caption">按 JD 必须技能与优先技能逐项展示</span>
    </div>
    <div class="skill-matrix-group">
      <p class="skill-matrix-title">必备技能 · 参与覆盖率</p>
      <ul class="skill-matrix-list">
        <li v-for="dimension in match.required" :key="dimension.requirement" class="skill-matrix-row">
          <span class="skill-matrix-name">{{ dimension.requirement }}</span>
          <span class="skill-matrix-status" :class="MATCH_STATUS_CLASS[dimension.matchStatus]">
            {{ MATCH_STATUS_LABELS[dimension.matchStatus] }}
          </span>
          <span class="skill-matrix-evidence">{{ dimension.resumeEvidence.length }} 条简历证据</span>
        </li>
        <li v-if="!match.required.length" class="kf-text-caption">岗位没有已确认的必备技能。</li>
      </ul>
    </div>
    <div class="skill-matrix-group">
      <p class="skill-matrix-title">优先技能 · 不参与必备覆盖率</p>
      <ul class="skill-matrix-list">
        <li v-for="dimension in match.preferred" :key="dimension.requirement" class="skill-matrix-row">
          <span class="skill-matrix-name">{{ dimension.requirement }}</span>
          <span class="skill-matrix-status" :class="MATCH_STATUS_CLASS[dimension.matchStatus]">
            {{ MATCH_STATUS_LABELS[dimension.matchStatus] }}
          </span>
          <span class="skill-matrix-evidence">{{ dimension.resumeEvidence.length }} 条简历证据</span>
        </li>
        <li v-if="!match.preferred.length" class="kf-text-caption">岗位没有已确认的优先技能。</li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { CareerMatch } from '@/api/career/types'
import { MATCH_STATUS_CLASS, MATCH_STATUS_LABELS } from './career-labels'

defineProps<{ match: CareerMatch }>()
</script>

<style scoped>
.skill-matrix { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.skill-matrix-head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.skill-matrix-head h3 { margin: 0; }
.skill-matrix-group { display: flex; flex-direction: column; gap: var(--kf-space-1); }
.skill-matrix-title { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.skill-matrix-list { display: flex; flex-direction: column; gap: 1px; margin: 0; padding: 0; list-style: none; }
.skill-matrix-row { display: grid; grid-template-columns: minmax(0, 1fr) auto 130px; align-items: center; gap: var(--kf-space-2); padding: var(--kf-space-2) var(--kf-space-2); border-radius: var(--kf-radius-xs); background: var(--kf-surface-2); font-size: var(--kf-fs-body-sm); }
.skill-matrix-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--kf-foreground); }
.skill-matrix-status { padding: 1px var(--kf-space-2); border-radius: var(--kf-radius-xs); font-size: var(--kf-fs-caption); white-space: nowrap; }
.skill-matrix-status.is-matched { color: var(--kf-state-success); background: color-mix(in srgb, var(--kf-state-success) 10%, transparent); }
.skill-matrix-status.is-partial { color: var(--kf-state-warning); background: color-mix(in srgb, var(--kf-state-warning) 10%, transparent); }
.skill-matrix-status.is-missing { color: var(--kf-muted-foreground); background: var(--kf-surface-1); }
.skill-matrix-status.is-uncertain { color: var(--kf-state-info); background: color-mix(in srgb, var(--kf-state-info) 10%, transparent); }
.skill-matrix-status.is-neutral { color: var(--kf-muted-foreground); background: var(--kf-surface-1); }
.skill-matrix-evidence { text-align: right; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
@media (max-width: 640px) {
  .skill-matrix-row { grid-template-columns: minmax(0, 1fr) auto; }
  .skill-matrix-evidence { grid-column: 1 / -1; text-align: left; }
}
</style>
