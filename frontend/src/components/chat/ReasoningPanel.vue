<!-- ChatGPT 风格折叠思考面板：只展示 Provider 真实返回的推理内容或摘要，不伪造任何思考文本 -->
<template>
  <section
    v-if="visible"
    class="reasoning-panel"
    :class="[`is-${state}`, { 'is-open': expanded }]"
  >
    <button
      type="button"
      class="reasoning-head"
      :aria-expanded="expanded"
      :aria-controls="panelId"
      @click="toggle"
    >
      <Icon name="brain" :size="15" class="reasoning-icon" aria-hidden="true" />
      <span class="reasoning-title">{{ title }}</span>
      <span v-if="kindLabel" class="reasoning-badge">{{ kindLabel }}</span>
      <span v-if="durationText" class="reasoning-duration">{{ durationText }}</span>
      <Icon
        name="chevron-down"
        :size="14"
        class="reasoning-chevron"
        :class="{ 'is-rotated': expanded }"
        aria-hidden="true"
      />
    </button>
    <div
      v-show="expanded"
      :id="panelId"
      class="reasoning-body"
      role="region"
      aria-label="模型思考内容"
    >
      <ChatMarkdownRenderer v-if="reasoning" :content="reasoning" :streaming="state === 'thinking'" />
      <p v-else class="reasoning-empty">{{ emptyText }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import ChatMarkdownRenderer from './ChatMarkdownRenderer.vue'

export type ReasoningState = 'thinking' | 'done' | 'answering' | 'stopped' | 'failed' | 'idle'

const props = withDefaults(defineProps<{
  reasoning: string
  kind?: string
  state?: ReasoningState
  durationMs?: number | null
}>(), {
  kind: 'provider_text',
  state: 'idle',
  durationMs: null,
})

const panelId = `reasoning-${useId()}`
const expanded = ref(props.state === 'thinking' || props.state === 'answering')
/** 用户本次主动展开/折叠后，不再被状态变化自动覆盖 */
let userToggled = false

watch(() => props.state, (next) => {
  if (userToggled) return
  if (next === 'thinking') expanded.value = true
  else if (next === 'done' || next === 'stopped' || next === 'failed') expanded.value = false
})

/** 没有推理文本时不渲染面板，避免出现「思考完成但无内容」的假步骤 */
const visible = computed(() => Boolean(props.reasoning) || props.state === 'thinking')

const title = computed(() => {
  switch (props.state) {
    case 'thinking': return '正在思考…'
    case 'answering': return '思考已完成，正在回答…'
    case 'stopped': return '思考已停止'
    case 'failed': return '思考失败'
    case 'done': return '思考已完成'
    default: return '思考'
  }
})

/** 明确区分「推理摘要」与「模型推理内容」，不把摘要冒充完整思维链 */
const kindLabel = computed(() => {
  if (!props.reasoning) return ''
  return props.kind === 'summary' ? '思考摘要' : '模型推理内容'
})

const durationText = computed(() => {
  if (props.durationMs == null || props.durationMs <= 0) return ''
  if (props.state === 'thinking') return ''
  return `· ${(props.durationMs / 1000).toFixed(props.durationMs < 10_000 ? 1 : 0)} 秒`
})

const emptyText = computed(() => props.state === 'thinking'
  ? '模型暂未返回可展示的思考内容。'
  : '本次没有可展示的思考内容。')

function toggle() {
  userToggled = true
  expanded.value = !expanded.value
}
</script>

<style scoped>
.reasoning-panel {
  margin-bottom: 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  overflow: hidden;
}
.reasoning-head {
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
.reasoning-head:hover { color: var(--kb-foreground); }
.reasoning-head:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
}
.reasoning-icon { flex-shrink: 0; }
.is-thinking .reasoning-icon { animation: reasoning-pulse 1.6s ease-in-out infinite; }
.reasoning-title {
  font-weight: var(--kb-fw-label);
  white-space: nowrap;
}
.reasoning-badge {
  padding: 1px 6px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
}
.reasoning-duration {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.reasoning-chevron {
  margin-left: auto;
  flex-shrink: 0;
  transition: transform .16s ease;
}
.reasoning-chevron.is-rotated { transform: rotate(180deg); }
.reasoning-body {
  max-height: 320px;
  overflow: auto;
  padding: 0 12px 10px;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.reasoning-body :deep(.chat-markdown) {
  font-size: var(--kb-fs-body-sm);
  line-height: 1.7;
  color: var(--kb-muted-foreground);
}
.reasoning-body :deep(.chat-markdown p) { margin: 0 0 0.6em; }
.reasoning-empty {
  margin: 0;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
}
.is-failed .reasoning-title { color: var(--kb-danger-text); }
.is-failed .reasoning-icon { color: var(--kb-danger-text); }
@keyframes reasoning-pulse {
  0%, 100% { opacity: .55; }
  50% { opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .is-thinking .reasoning-icon { animation: none; }
  .reasoning-chevron { transition: none; }
}
</style>
