<script setup lang="ts">
/**
 * 高危工具二次确认弹窗：收到 SSE 的 tool-confirm 事件后弹出，
 * 展示待执行工具与入参，并倒计时提示超时（超时后端按拒绝处理）。
 */
import { ref, watch, onUnmounted, computed } from 'vue'
import type { AgentToolEvent } from '@/api/codeAgent'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'

const props = defineProps<{
  /** 待确认事件；为 null 表示不展示 */
  event: AgentToolEvent | null
}>()

const emit = defineEmits<{
  (e: 'resolve', payload: { callId: string; approved: boolean }): void
}>()

/** 剩余确认秒数 */
const remaining = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const prettyArgs = computed(() => {
  if (!props.event) return ''
  try {
    return JSON.stringify(JSON.parse(props.event.args), null, 2)
  } catch {
    return props.event.args
  }
})

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(
  () => props.event,
  (ev) => {
    clearTimer()
    if (!ev) return
    remaining.value = ev.timeoutSeconds ?? 60
    timer = setInterval(() => {
      remaining.value -= 1
      if (remaining.value <= 0) {
        clearTimer()
        // 倒计时结束等同拒绝，避免弹窗滞留
        emit('resolve', { callId: ev.callId, approved: false })
      }
    }, 1000)
  },
  { immediate: true },
)

onUnmounted(clearTimer)

function decide(approved: boolean) {
  if (!props.event) return
  clearTimer()
  emit('resolve', { callId: props.event.callId, approved })
}
</script>

<template>
  <div v-if="event" class="confirm-mask">
    <div class="confirm-dialog" role="alertdialog" aria-modal="true">
      <div class="confirm-header">
        <Icon name="alert-circle" size="md" aria-hidden="true" />
        <h3>高危工具确认</h3>
      </div>
      <p class="confirm-desc">
        Agent 请求执行高危工具
        <code class="tool-code">{{ event.tool }}</code>
        ，该操作可能影响数据或系统状态，请确认是否放行。
      </p>
      <div class="args-block">
        <span class="args-label">调用参数</span>
        <pre class="args-code">{{ prettyArgs }}</pre>
      </div>
      <div class="confirm-footer">
        <span class="countdown">{{ remaining }} 秒后自动拒绝</span>
        <div class="actions">
          <Button size="sm" variant="ghost" @click="decide(false)">拒绝</Button>
          <button type="button" class="danger-btn transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" @click="decide(true)">允许执行</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  padding: 16px;
}

.confirm-dialog {
  width: 100%;
  max-width: 480px;
  padding: 20px;
  border-radius: var(--kb-radius-lg);
  background: var(--kb-surface);
  box-shadow: var(--kb-shadow-lg);
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kb-danger);
}

.confirm-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.confirm-desc {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--kb-text);
}

.tool-code {
  padding: 1px 5px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-danger-soft);
  color: var(--kb-danger);
  font-family: var(--kb-font-mono, monospace);
  font-size: 12px;
}

.args-block {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.args-label {
  font-size: 11px;
  color: var(--kb-text-muted);
}

.args-code {
  margin: 0;
  padding: 10px;
  max-height: 200px;
  overflow: auto;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-bg-subtle);
  font-family: var(--kb-font-mono, monospace);
  font-size: 11px;
  line-height: 1.6;
  color: var(--kb-text);
  white-space: pre-wrap;
  word-break: break-all;
}

.confirm-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

.countdown {
  font-size: 12px;
  color: var(--kb-text-muted);
}

.actions {
  display: flex;
  gap: 8px;
}

/* 危险操作按钮：Button 组件无 danger 变体，此处单独实现 */
.danger-btn {
  padding: 6px 14px;
  border: none;
  border-radius: var(--kb-radius-md);
  background: var(--kb-danger);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.danger-btn:hover {
  opacity: 0.88;
}
</style>
