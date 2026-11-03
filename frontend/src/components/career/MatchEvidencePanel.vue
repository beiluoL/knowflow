<template>
  <section class="match-evidence" aria-label="匹配证据">
    <div class="match-evidence-head">
      <div>
        <h3 class="kf-text-card-title">匹配证据</h3>
        <p class="kf-text-caption">每条判断都对应 JD 原文要求和简历原文证据；缺失证据只表示「简历未体现」。</p>
      </div>
      <router-link
        v-if="match.resume.id"
        class="match-evidence-link"
        :to="`/career/resumes/${match.resume.id}?version=${match.resume.version}`"
      >
        <Icon name="file-text" :size="14" />
        <span>查看简历 v{{ match.resume.version }}</span>
      </router-link>
    </div>

    <div class="match-evidence-grid">
      <article v-for="dimension in dimensions" :key="dimension.id" class="match-evidence-card">
        <div class="match-evidence-card-head">
          <span class="match-evidence-name">{{ DIMENSION_LABELS[dimension.id] || dimension.id }}</span>
          <span class="match-evidence-status" :class="statusClass(dimension.matchStatus)">
            {{ statusLabel(dimension.matchStatus) }}
          </span>
        </div>
        <p class="match-evidence-confidence">
          证据来源：{{ dimension.confidence === 'USER_CONFIRMED_NOT_VERIFIED' ? '用户确认原文，未做客观能力校准' : dimension.confidence }}
          <template v-if="dimension.coverage != null"> · 覆盖 {{ dimension.coverage.toFixed(1) }}%</template>
        </p>
        <div class="match-evidence-block">
          <p class="match-evidence-label">JD 原始要求</p>
          <p v-if="!dimension.requirementEvidence.length" class="match-evidence-empty">岗位未提供该维度要求</p>
          <blockquote v-for="quote in dimension.requirementEvidence" :key="quote">{{ quote }}</blockquote>
        </div>
        <div class="match-evidence-block">
          <p class="match-evidence-label">简历对应内容</p>
          <p v-if="!dimension.resumeEvidence.length" class="match-evidence-empty">简历未体现该维度证据</p>
          <blockquote v-for="quote in dimension.resumeEvidence" :key="quote">{{ quote }}</blockquote>
        </div>
        <p class="match-evidence-gap">{{ dimension.gapDescription }}</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'
import type { CareerMatch, MatchStatus } from '@/api/career/types'
import { DIMENSION_LABELS, MATCH_STATUS_CLASS, MATCH_STATUS_LABELS } from './career-labels'

const props = defineProps<{ match: CareerMatch }>()
const dimensions = props.match.dimensions ?? []

function statusClass(status: string): string {
  return MATCH_STATUS_CLASS[status as MatchStatus] ?? 'is-neutral'
}

function statusLabel(status: string): string {
  return MATCH_STATUS_LABELS[status as MatchStatus] ?? status
}
</script>

<style scoped>
.match-evidence { display: flex; flex-direction: column; gap: var(--kf-space-3); }
.match-evidence-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.match-evidence-head h3 { margin: 0 0 var(--kf-space-1); }
.match-evidence-head p { margin: 0; }
.match-evidence-link { display: inline-flex; align-items: center; gap: var(--kf-space-1); font-size: var(--kf-fs-body-sm); color: var(--kf-primary); text-decoration: none; }
.match-evidence-link:hover { text-decoration: underline; }
.match-evidence-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: var(--kf-space-3); }
.match-evidence-card { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-sm); background: var(--kf-surface-1); }
.match-evidence-card-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); }
.match-evidence-name { font-size: var(--kf-fs-body-sm); font-weight: 500; color: var(--kf-foreground); }
.match-evidence-status { padding: 1px var(--kf-space-2); border-radius: var(--kf-radius-xs); font-size: var(--kf-fs-caption); }
.match-evidence-status.is-matched { color: var(--kf-state-success); background: color-mix(in srgb, var(--kf-state-success) 10%, transparent); }
.match-evidence-status.is-partial { color: var(--kf-state-warning); background: color-mix(in srgb, var(--kf-state-warning) 10%, transparent); }
.match-evidence-status.is-missing { color: var(--kf-muted-foreground); background: var(--kf-surface-2); }
.match-evidence-status.is-uncertain { color: var(--kf-state-info); background: color-mix(in srgb, var(--kf-state-info) 10%, transparent); }
.match-evidence-status.is-neutral { color: var(--kf-muted-foreground); background: var(--kf-surface-2); }
.match-evidence-confidence { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.match-evidence-block { display: flex; flex-direction: column; gap: var(--kf-space-1); }
.match-evidence-label { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.match-evidence-block blockquote { margin: 0; padding: var(--kf-space-2); border-left: 2px solid var(--kf-border); background: var(--kf-surface-2); border-radius: 0 var(--kf-radius-xs) var(--kf-radius-xs) 0; font-size: var(--kf-fs-caption); color: var(--kf-foreground); }
.match-evidence-empty { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.match-evidence-gap { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
</style>
