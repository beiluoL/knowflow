<script setup lang="ts">
import { computed } from 'vue'
import Button from '@/components/ui/Button.vue'
const props = defineProps<{ scores: Record<string, number | null>; names: Record<string, string>; weights?: Record<string, number> }>()
const emit = defineEmits<{ select: [dimension: string] }>()
const axes = computed(() => Object.entries(props.scores).filter(([key]) => props.weights?.[key] !== 0).map(([key, score], index, values) => {
  const angle = index * Math.PI * 2 / values.length - Math.PI / 2
  const valid = typeof score === 'number' && Number.isFinite(score) && score >= 0 && score <= 100
  return { key, score, x: 100 + Math.cos(angle) * 75, y: 100 + Math.sin(angle) * 75,
    px: valid ? 100 + Math.cos(angle) * score * 0.75 : null,
    py: valid ? 100 + Math.sin(angle) * score * 0.75 : null }
}))
// 只有维度全部已评价时绘制闭合区域，禁止将缺失维度补成零分。
const polygon = computed(() => axes.value.length && axes.value.every(a => a.px !== null)
  ? axes.value.map(a => `${a.px},${a.py}`).join(' ') : '')
</script>
<template>
  <section class="score-radar" aria-label="实际评价维度">
    <svg viewBox="0 0 200 200" role="img" aria-label="能力维度雷达，缺失维度不补零分">
      <line v-for="axis in axes" :key="axis.key" x1="100" y1="100" :x2="axis.x" :y2="axis.y" />
      <polygon v-if="polygon" :points="polygon" />
      <template v-for="axis in axes" :key="axis.key">
        <circle v-if="axis.px !== null && axis.py !== null" :cx="axis.px" :cy="axis.py" r="3" />
      </template>
    </svg>
    <div class="score-radar-actions">
      <Button v-for="axis in axes" :key="axis.key" variant="secondary" size="sm" @click="emit('select', axis.key)">
        {{ names[axis.key] || axis.key }}：{{ axis.score ?? '尚未评估' }}
      </Button>
    </div>
  </section>
</template>
<style scoped>
.score-radar { display: flex; gap: var(--kf-space-4); flex-wrap: wrap; align-items: center; }
.score-radar svg { width: min(100%, 14rem); color: var(--kf-primary); }
.score-radar line { stroke: var(--kf-border); }
.score-radar polygon { fill: currentColor; fill-opacity: 0.15; stroke: currentColor; }
.score-radar circle { fill: currentColor; }
.score-radar-actions { display: flex; flex-wrap: wrap; gap: var(--kf-space-2); }
</style>
