<!-- 知识库检索执行时间线：只渲染后端真实推送的步骤，不补造未执行的阶段 -->
<template>
  <section v-if="trace && trace.steps.length" class="trace-panel" :class="{ 'is-open': expanded }">
    <button
      type="button"
      class="trace-head"
      :aria-expanded="expanded"
      :aria-controls="panelId"
      @click="toggle"
    >
      <Icon name="database" :size="15" class="trace-icon" aria-hidden="true" />
      <span class="trace-title">知识库检索</span>
      <span v-if="strategyLabel" class="trace-badge">{{ strategyLabel }}</span>
      <span class="trace-meta">{{ meta }}</span>
      <Icon
        name="chevron-down"
        :size="14"
        class="trace-chevron"
        :class="{ 'is-rotated': expanded }"
        aria-hidden="true"
      />
    </button>
    <div v-show="expanded" :id="panelId" class="trace-body" role="region" aria-label="知识库检索执行步骤">
      <ol class="trace-steps">
        <li v-for="(step, index) in trace.steps" :key="stepKey(step, index)" class="trace-step">
          <span class="trace-dot" :class="`is-${step.status || 'unknown'}`" aria-hidden="true"></span>
          <div class="trace-step-main">
            <div class="trace-step-top">
              <span class="trace-step-label">{{ step.label || typeLabel(step.type) }}</span>
              <span class="trace-step-state">{{ statusLabel(step.status) }}</span>
              <span v-if="stepMeta(step)" class="trace-step-meta">{{ stepMeta(step) }}</span>
            </div>
            <p v-if="step.detail" class="trace-step-detail">{{ step.detail }}</p>
          </div>
        </li>
      </ol>
      <p v-if="evidenceText" class="trace-evidence">{{ evidenceText }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'

export interface TraceStepView {
  stepId?: string
  type: string
  label?: string
  status?: string
  detail?: string
  durationMs?: number
  resultCount?: number
  sequence?: number
}

export interface TraceView {
  traceId?: string
  strategy?: string
  accessibleDocCount?: number
  resultCount?: number
  evidenceSufficient?: boolean
  durationMs?: number
  steps: TraceStepView[]
}

const props = withDefaults(defineProps<{
  trace: TraceView | null
  active?: boolean
}>(), { active: false })

const panelId = `trace-${useId()}`
const expanded = ref(props.active)
let userToggled = false

watch(() => props.active, (active) => {
  if (!userToggled) expanded.value = active
})

const strategyLabel = computed(() => {
  switch (props.trace?.strategy) {
    case 'vector': return '向量检索'
    case 'keyword': return '关键词检索'
    case 'permission_denied': return '权限受限'
    case 'none': return '未检索'
    default: return ''
  }
})

const meta = computed(() => {
  if (props.active) return '检索中…'
  const steps = props.trace?.steps.length ?? 0
  const duration = props.trace?.durationMs
  const parts = [`${steps} 个步骤`]
  if (typeof duration === 'number' && duration > 0) parts.push(formatDuration(duration))
  return parts.join(' · ')
})

const evidenceText = computed(() => {
  const trace = props.trace
  if (!trace || props.active) return ''
  if (trace.evidenceSufficient === undefined) return ''
  return trace.evidenceSufficient
    ? '证据判断：已检索到可用于回答的知识库内容'
    : '证据判断：未检索到可用资料，本次回答未引用知识库内容'
})

function typeLabel(type?: string) {
  switch (type) {
    case 'permission_filter': return '权限过滤'
    case 'vector_search': return '向量检索'
    case 'keyword_search': return '关键词检索'
    case 'evidence': return '证据判断'
    case 'retrieval': return '知识库检索'
    default: return type || '检索步骤'
  }
}

function statusLabel(status?: string) {
  switch (status) {
    case 'success': return '完成'
    case 'empty': return '无结果'
    case 'skipped': return '跳过'
    case 'error': return '失败'
    default: return status || ''
  }
}

function stepMeta(step: TraceStepView) {
  const parts: string[] = []
  if (typeof step.resultCount === 'number') parts.push(`${step.resultCount} 条`)
  if (typeof step.durationMs === 'number' && step.durationMs > 0) parts.push(formatDuration(step.durationMs))
  return parts.join(' · ')
}

function formatDuration(ms: number) {
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}

function stepKey(step: TraceStepView, index: number) {
  return `${step.stepId || step.type}-${step.sequence ?? index}`
}

function toggle() {
  userToggled = true
  expanded.value = !expanded.value
}
</script>

<style scoped>
.trace-panel {
  margin-bottom: 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  overflow: hidden;
}
.trace-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 36px;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  text-align: left;
  cursor: pointer;
}
.trace-head:hover { color: var(--kb-foreground); }
.trace-head:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
}
.trace-icon { flex-shrink: 0; }
.trace-title { font-weight: var(--kb-fw-label); white-space: nowrap; }
.trace-badge {
  padding: 1px 6px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-muted);
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
}
.trace-meta {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.trace-chevron {
  flex-shrink: 0;
  transition: transform .16s ease;
}
.trace-chevron.is-rotated { transform: rotate(180deg); }
.trace-body {
  max-height: 320px;
  overflow: auto;
  padding: 2px 12px 10px 12px;
}
.trace-steps {
  margin: 0;
  padding: 0;
  list-style: none;
}
.trace-step {
  position: relative;
  display: flex;
  gap: 10px;
  padding: 6px 0 6px 0;
}
.trace-step + .trace-step::before {
  content: '';
  position: absolute;
  left: 4px;
  top: -2px;
  width: 1px;
  height: 10px;
  background: var(--kb-border);
}
.trace-dot {
  margin-top: 6px;
  width: 9px;
  height: 9px;
  flex-shrink: 0;
  border-radius: var(--kb-radius-full);
  background: var(--kb-muted-foreground);
}
.trace-dot.is-success { background: var(--kb-state-success, var(--kb-primary)); }
.trace-dot.is-empty { background: var(--kb-state-warning, var(--kb-muted-foreground)); }
.trace-dot.is-error { background: var(--kb-state-error, var(--kb-destructive)); }
.trace-dot.is-skipped { background: var(--kb-border); }
.trace-step-main { min-width: 0; flex: 1; }
.trace-step-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
}
.trace-step-label { color: var(--kb-foreground); font-weight: var(--kb-fw-label); }
.trace-step-state { color: var(--kb-muted-foreground); font-size: 12px; }
.trace-step-meta {
  margin-left: auto;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.trace-step-detail {
  margin: 2px 0 0;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.trace-evidence {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px solid var(--kb-border);
  color: var(--kb-muted-foreground);
  font-size: 12px;
  line-height: 1.6;
}
@media (prefers-reduced-motion: reduce) {
  .trace-chevron { transition: none; }
}
</style>
