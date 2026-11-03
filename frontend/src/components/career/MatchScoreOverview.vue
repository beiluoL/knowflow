<template>
  <section class="match-score" aria-label="匹配总览">
    <div class="match-score-main">
      <div>
        <p class="match-score-label">必备技能原文证据覆盖率</p>
        <p class="match-score-value">
          {{ match.score == null ? '证据不足' : `${match.score.toFixed(1)}%` }}
        </p>
        <p class="match-score-meaning">{{ match.scoreMeaning }}</p>
      </div>
      <div class="match-score-ring" :class="{ 'is-empty': match.score == null }" :style="ringStyle" role="img" :aria-label="match.score == null ? '证据不足，暂不评分' : `必备技能覆盖率 ${match.score.toFixed(1)}%`">
        <span>{{ match.score == null ? '—' : Math.round(match.score) }}</span>
      </div>
    </div>
    <div class="match-score-meta">
      <div class="match-score-meta-item">
        <span>综合参考分</span>
        <strong>{{ match.overallScore == null ? '未评估' : `${match.overallScore.toFixed(1)}%` }}</strong>
      </div>
      <div class="match-score-meta-item">
        <span>评估覆盖</span>
        <strong>{{ coverageLabel }}</strong>
      </div>
      <div class="match-score-meta-item">
        <span>算法版本</span>
        <strong>{{ match.policyVersion }}</strong>
      </div>
    </div>
    <div v-if="match.unassessedDimensions.length" class="match-score-unassessed">
      <p class="kf-text-caption">尚未确定、不能补分的维度</p>
      <div class="match-score-tags">
        <span v-for="id in match.unassessedDimensions" :key="id" class="match-score-tag">
          {{ DIMENSION_LABELS[id] || id }}
        </span>
      </div>
    </div>
    <p class="match-score-foot">
      匹配分来自已确认简历与 JD 的原文技能证据覆盖率，不是熟练度、招聘成功率或面试评分。
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CareerMatch } from '@/api/career/types'
import { DIMENSION_LABELS } from './career-labels'

const props = defineProps<{ match: CareerMatch }>()

const coverageLabel = computed(() => {
  const value = props.match.evaluationCoverage
  if (!value) return '0%'
  return `${Math.round(value * 100)}%`
})
const ringStyle = computed(() => ({
  '--score-angle': `${Math.round((props.match.score ?? 0) * 3.6)}deg`,
}))
</script>

<style scoped>
.match-score { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.match-score-main { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-4); flex-wrap: wrap; }
.match-score-label { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.match-score-value { margin: var(--kf-space-1) 0; font-size: var(--kf-fs-h1); line-height: var(--kf-lh-h1); font-weight: var(--kf-fw-h1); color: var(--kf-foreground); font-variant-numeric: tabular-nums; }
.match-score-meaning { margin: 0; max-width: 560px; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.match-score-ring { display: inline-flex; align-items: center; justify-content: center; width: 88px; height: 88px; border-radius: var(--kf-radius-full); background: conic-gradient(var(--kf-primary) 0deg, var(--kf-primary) calc(var(--score-angle, 0deg)), var(--kf-surface-2) 0deg); position: relative; flex: none; }
.match-score-ring::before { content: ''; position: absolute; inset: 10px; border-radius: var(--kf-radius-full); background: var(--kf-surface-1); }
.match-score-ring span { position: relative; font-size: var(--kf-fs-h4); font-weight: 600; color: var(--kf-foreground); }
.match-score-ring.is-empty { background: var(--kf-surface-2); }
.match-score-meta { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--kf-space-2); }
.match-score-meta-item { display: flex; flex-direction: column; gap: 2px; padding: var(--kf-space-2) var(--kf-space-3); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.match-score-meta-item strong { font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); font-weight: 500; }
.match-score-unassessed { display: flex; flex-direction: column; gap: var(--kf-space-1); }
.match-score-unassessed p { margin: 0; }
.match-score-tags { display: flex; flex-wrap: wrap; gap: var(--kf-space-1); }
.match-score-tag { padding: 2px var(--kf-space-2); border-radius: var(--kf-radius-xs); background: color-mix(in srgb, var(--kf-state-warning) 10%, transparent); color: var(--kf-state-warning); font-size: var(--kf-fs-caption); }
.match-score-foot { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
@media (max-width: 640px) {
  .match-score-meta { grid-template-columns: 1fr; }
}
</style>
