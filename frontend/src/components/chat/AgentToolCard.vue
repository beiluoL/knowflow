<!-- Agent 工具执行时间线：只渲染后端 Agent Runtime 真实推送的工具事件，不模拟任何工具调用 -->
<template>
  <section v-if="tools.length" class="tool-panel" :class="{ 'is-open': expanded }">
    <button
      type="button"
      class="tool-head"
      :aria-expanded="expanded"
      :aria-controls="panelId"
      @click="toggle"
    >
      <Icon name="cpu" :size="15" class="tool-icon" aria-hidden="true" />
      <span class="tool-title">工具执行</span>
      <span class="tool-badge">{{ tools.length }} 个工具</span>
      <span class="tool-meta">{{ meta }}</span>
      <Icon
        name="chevron-down"
        :size="14"
        class="tool-chevron"
        :class="{ 'is-rotated': expanded }"
        aria-hidden="true"
      />
    </button>
    <div v-show="expanded" :id="panelId" class="tool-body" role="region" aria-label="工具执行步骤">
      <ol class="tool-list">
        <li v-for="(tool, index) in tools" :key="toolKey(tool, index)" class="tool-item">
          <div class="tool-row">
            <span class="tool-dot" :class="`is-${tool.status || 'pending'}`" aria-hidden="true"></span>
            <span class="tool-name">{{ tool.toolName || tool.label || '工具调用' }}</span>
            <span class="tool-state">{{ statusLabel(tool.status) }}</span>
            <span v-if="tool.durationMs != null && tool.durationMs >= 0" class="tool-duration">{{ formatDuration(tool.durationMs) }}</span>
            <button
              v-if="hasDetails(tool)"
              type="button"
              class="tool-detail-toggle"
              :aria-expanded="isDetailOpen(tool, index)"
              @click="toggleDetail(tool, index)"
            >
              {{ isDetailOpen(tool, index) ? '收起详情' : '详情' }}
            </button>
          </div>
          <p v-if="tool.description" class="tool-desc">{{ tool.description }}</p>
          <div v-if="isDetailOpen(tool, index)" class="tool-detail">
            <template v-if="argsText(tool)">
              <span class="tool-detail-label">参数（已脱敏）</span>
              <pre class="tool-detail-pre">{{ argsText(tool) }}</pre>
            </template>
            <template v-if="tool.result">
              <span class="tool-detail-label">结果摘要</span>
              <pre class="tool-detail-pre">{{ tool.result }}</pre>
            </template>
            <template v-if="tool.error">
              <span class="tool-detail-label">错误</span>
              <pre class="tool-detail-pre">{{ tool.error }}</pre>
            </template>
          </div>
        </li>
      </ol>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import type { ToolEvent } from '@/api/chat'

const props = withDefaults(defineProps<{
  tools: ToolEvent[]
  active?: boolean
}>(), { active: false })

const panelId = `tool-${useId()}`
const expanded = ref(props.active || props.tools.some((t) => isRunning(t.status)))
const openDetails = ref<Set<string>>(new Set())

watch(() => props.active, (active) => { if (active) expanded.value = true })
// 新的工具事件到达且处于执行中时自动展开，让用户看到真实执行进度
watch(() => props.tools.map((t) => t.status).join(','), () => {
  if (props.tools.some((t) => isRunning(t.status))) expanded.value = true
})

const meta = computed(() => {
  if (props.active) return '执行中…'
  const failed = props.tools.filter((t) => t.status === 'failed' || t.status === 'timeout' || t.status === 'error').length
  return failed > 0 ? `${failed} 个失败` : '全部完成'
})

function isRunning(status?: string) {
  return status === 'running' || status === 'pending' || status === 'confirm'
}

function statusLabel(status?: string) {
  switch (status) {
    case 'pending': return '待执行'
    case 'running': return '执行中'
    case 'success': return '成功'
    case 'failed': return '失败'
    case 'error': return '失败'
    case 'timeout': return '超时'
    case 'cancelled': return '已取消'
    case 'confirm': return '需要确认'
    default: return status || ''
  }
}

function toolKey(tool: ToolEvent, index: number) {
  return `${tool.stepId || tool.toolName || 'tool'}-${tool.sequence ?? index}`
}

function hasDetails(tool: ToolEvent) {
  return Boolean(argsText(tool) || tool.result || tool.error)
}

function isDetailOpen(tool: ToolEvent, index: number) {
  return openDetails.value.has(toolKey(tool, index))
}

function toggleDetail(tool: ToolEvent, index: number) {
  const key = toolKey(tool, index)
  const next = new Set(openDetails.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  openDetails.value = next
}

/** 参数脱敏兜底：即使后端遗漏，前端也不展示明显的密钥字段 */
function argsText(tool: ToolEvent) {
  if (tool.args == null) return ''
  try {
    const raw = typeof tool.args === 'string' ? tool.args : JSON.stringify(tool.args)
    if (!raw) return ''
    const masked = raw.replace(/("(?:[^"]*(?:api[_-]?key|token|password|secret)[^"]*)"\s*:\s*)"[^"]*"/gi, '$1"***"')
    return masked.length > 600 ? `${masked.slice(0, 600)}…` : masked
  } catch {
    return ''
  }
}

function formatDuration(ms: number) {
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}

function toggle() {
  expanded.value = !expanded.value
}
</script>

<style scoped>
.tool-panel {
  margin-bottom: 12px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  overflow: hidden;
}
.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 36px;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  text-align: left;
  cursor: pointer;
}
.tool-head:hover { color: var(--kf-foreground); }
.tool-head:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}
.tool-icon { flex-shrink: 0; }
.tool-title { font-weight: var(--kf-fw-label); white-space: nowrap; }
.tool-badge {
  padding: 1px 6px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-muted);
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
}
.tool-meta {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.tool-chevron {
  flex-shrink: 0;
  transition: transform .16s ease;
}
.tool-chevron.is-rotated { transform: rotate(180deg); }
.tool-body {
  max-height: 320px;
  overflow: auto;
  padding: 2px 12px 10px;
}
.tool-list { margin: 0; padding: 0; list-style: none; }
.tool-item { padding: 6px 0; }
.tool-item + .tool-item { border-top: 1px dashed var(--kf-border); }
.tool-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
}
.tool-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: var(--kf-radius-full);
  background: var(--kf-border);
}
.tool-dot.is-running { background: var(--kf-primary); }
.tool-dot.is-success { background: var(--kf-state-success, var(--kf-primary)); }
.tool-dot.is-failed,
.tool-dot.is-error { background: var(--kf-state-error, var(--kf-destructive)); }
.tool-dot.is-timeout,
.tool-dot.is-confirm { background: var(--kf-state-warning, var(--kf-muted-foreground)); }
.tool-name { color: var(--kf-foreground); font-weight: var(--kf-fw-label); }
.tool-state { color: var(--kf-muted-foreground); font-size: 12px; }
.tool-duration {
  margin-left: auto;
  color: var(--kf-muted-foreground);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.tool-detail-toggle {
  min-height: 24px;
  padding: 0 6px;
  border: 0;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-primary);
  font-size: 12px;
  cursor: pointer;
}
.tool-detail-toggle:hover { background: var(--kf-muted); }
.tool-detail-toggle:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 1px; }
.tool-desc {
  margin: 2px 0 0 16px;
  color: var(--kf-muted-foreground);
  font-size: 12px;
  line-height: 1.6;
}
.tool-detail {
  margin: 6px 0 0 16px;
  padding: 8px 10px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-muted);
}
.tool-detail-label {
  display: block;
  margin-bottom: 2px;
  color: var(--kf-muted-foreground);
  font-size: 12px;
}
.tool-detail-pre {
  margin: 0 0 6px;
  max-height: 160px;
  overflow: auto;
  color: var(--kf-foreground);
  font-family: var(--kf-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.tool-detail-pre:last-child { margin-bottom: 0; }
@media (prefers-reduced-motion: reduce) {
  .tool-chevron { transition: none; }
}
</style>
