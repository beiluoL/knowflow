<script setup lang="ts">
/**
 * 高危工具二次确认弹窗：收到 SSE 的 tool-confirm 事件后弹出，
 * 展示待执行工具与入参，并倒计时提示超时（超时后端按拒绝处理）。
 */
import { ref, watch, onUnmounted, computed } from 'vue'
import type { AgentToolEvent } from '@/api/codeAgent'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'

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
  <ConfirmDialog v-if="event" open title="高危工具确认" icon="alert-circle" variant="danger" confirm-text="允许执行" cancel-text="拒绝" :close-on-overlay="false" @confirm="decide(true)" @cancel="decide(false)">
    <p>Agent 请求执行高危工具 <code>{{event.tool}}</code>，该操作可能影响数据或系统状态，请确认是否放行。</p>
    <div class="tool-args"><span>调用参数</span><pre>{{prettyArgs}}</pre></div>
    <p class="countdown" role="status">{{remaining}} 秒后自动拒绝</p>
  </ConfirmDialog>
</template>
<style scoped>
.tool-args{display:grid;gap:var(--kb-space-2);margin-top:var(--kb-space-4)}.tool-args span,.countdown{font-size:var(--kb-fs-caption);color:var(--kb-muted-foreground)}.tool-args pre{max-height:35dvh;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;padding:var(--kb-space-3);background:var(--kb-muted);border-radius:var(--kb-radius-md)}code,pre{font-family:var(--font-mono)}.countdown{margin-top:var(--kb-space-3)}
</style>
