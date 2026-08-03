<script setup lang="ts">
/**
 * Agent 工具调用链可视化：以时间轴形式展示某个会话内的工具调用序列，
 * 顶部聚合各工具的调用次数、成功率与平均耗时，节点可展开查看入参与返回。
 */
import { ref, watch, computed } from 'vue'
import { codeAgentApi } from '@/api/codeAgent'
import type { AgentToolCallVO, AgentToolStatVO } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import { notify, getApiError } from '@/utils/toast'

const props = defineProps<{
  /** 目标会话 ID，为空时展示空态 */
  sessionId: number | null
}>()

const calls = ref<AgentToolCallVO[]>([])
const stats = ref<AgentToolStatVO[]>([])
const loading = ref(false)
/** 已展开详情的调用 ID */
const expandedIds = ref<Set<number>>(new Set())

const totalCalls = computed(() => stats.value.reduce((sum, s) => sum + s.total, 0))
const totalFailed = computed(() => stats.value.reduce((sum, s) => sum + s.failed, 0))

async function loadChain() {
  if (props.sessionId == null) {
    calls.value = []
    stats.value = []
    return
  }
  loading.value = true
  try {
    const [chain, stat] = await Promise.all([
      codeAgentApi.getCallChain(props.sessionId),
      codeAgentApi.getCallStats(props.sessionId),
    ])
    calls.value = chain
    stats.value = stat
  } catch (e: unknown) {
    notify(getApiError(e, '加载调用链失败'), 'error')
  } finally {
    loading.value = false
  }
}

function toggleExpand(id: number) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedIds.value = next
}

/** 美化 JSON 字符串，解析失败时原样返回 */
function prettyJson(raw?: string): string {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function statusIcon(status: string): string {
  if (status === 'success') return 'check-circle'
  if (status === 'cancelled') return 'x-circle'
  return 'alert-circle'
}

function formatTime(time?: string): string {
  if (!time) return ''
  return new Date(time).toLocaleTimeString('zh-CN', { hour12: false })
}

watch(() => props.sessionId, loadChain, { immediate: true })

defineExpose({ loadChain })
</script>

<template>
  <div class="call-chain">
    <div class="chain-header">
      <div class="header-text">
        <h3>工具调用链</h3>
        <p v-if="totalCalls > 0" class="hint">
          共 {{ totalCalls }} 次调用<span v-if="totalFailed > 0">，{{ totalFailed }} 次失败</span>
        </p>
        <p v-else class="hint">该会话尚未触发工具调用</p>
      </div>
      <Button size="sm" variant="ghost" :disabled="loading || sessionId == null" @click="loadChain">
        <Icon name="refresh-cw" size="xs" /> 刷新
      </Button>
    </div>

    <!-- 工具维度聚合统计 -->
    <div v-if="stats.length" class="stat-grid">
      <div v-for="s in stats" :key="s.tool" class="stat-card">
        <div class="stat-tool">{{ s.tool }}</div>
        <div class="stat-nums">
          <span class="stat-total">{{ s.total }} 次</span>
          <span class="stat-ok">成功 {{ s.success }}</span>
          <span v-if="s.failed > 0" class="stat-fail">失败 {{ s.failed }}</span>
        </div>
        <div class="stat-latency">平均 {{ s.avgLatencyMs }} ms</div>
      </div>
    </div>

    <div v-if="loading" class="chain-empty">加载中...</div>
    <div v-else-if="sessionId == null" class="chain-empty">
      <Icon name="git-branch" size="2xl" />
      <p>请先选择一个会话</p>
    </div>
    <div v-else-if="calls.length === 0" class="chain-empty">
      <Icon name="git-branch" size="2xl" />
      <p>暂无工具调用记录</p>
    </div>

    <ol v-else class="timeline">
      <li v-for="(call, idx) in calls" :key="call.id" class="timeline-item">
        <div class="timeline-marker" :class="call.status">
          <Icon :name="statusIcon(call.status)" size="xs" />
        </div>
        <div class="timeline-body">
          <button type="button" class="timeline-head" @click="toggleExpand(call.id)">
            <span class="step-no">#{{ idx + 1 }}</span>
            <span class="tool-name">{{ call.toolName }}</span>
            <span class="perm-tag" :class="call.permission.toLowerCase()">{{ call.permission }}</span>
            <span v-if="call.latencyMs != null" class="latency">{{ call.latencyMs }} ms</span>
            <span class="time">{{ formatTime(call.createTime) }}</span>
            <Icon :name="expandedIds.has(call.id) ? 'chevron-up' : 'chevron-down'" size="xxs" />
          </button>
          <div v-if="expandedIds.has(call.id)" class="timeline-detail">
            <div class="detail-block">
              <span class="detail-label">入参</span>
              <pre class="detail-code">{{ prettyJson(call.argsJson) }}</pre>
            </div>
            <div class="detail-block">
              <span class="detail-label">返回</span>
              <pre class="detail-code">{{ prettyJson(call.resultJson) }}</pre>
            </div>
          </div>
        </div>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.call-chain {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chain-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.header-text h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-text);
}

.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--kb-text-muted);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 8px;
}

.stat-card {
  padding: 10px 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface);
}

.stat-tool {
  font-family: var(--kb-font-mono, monospace);
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-text);
}

.stat-nums {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  font-size: 11px;
}

.stat-total {
  color: var(--kb-text-muted);
}

.stat-ok {
  color: var(--kb-success);
}

.stat-fail {
  color: var(--kb-danger);
}

.stat-latency {
  margin-top: 2px;
  font-size: 11px;
  color: var(--kb-text-muted);
}

.chain-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--kb-text-muted);
  font-size: 13px;
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.timeline-item {
  position: relative;
  display: flex;
  gap: 10px;
  padding-bottom: 10px;
}

/* 时间轴竖线：最后一项不再延伸 */
.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 11px;
  top: 24px;
  bottom: 0;
  width: 1px;
  background: var(--kb-border);
}

.timeline-marker {
  flex-shrink: 0;
  width: 23px;
  height: 23px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--kb-bg-subtle);
  color: var(--kb-text-muted);
}

.timeline-marker.success {
  background: var(--kb-success-soft);
  color: var(--kb-success);
}

.timeline-marker.failed {
  background: var(--kb-danger-soft);
  color: var(--kb-danger);
}

.timeline-marker.cancelled {
  background: var(--kb-warning-soft);
  color: var(--kb-warning);
}

.timeline-body {
  flex: 1;
  min-width: 0;
}

.timeline-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 5px 10px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface);
  cursor: pointer;
  text-align: left;
  font-size: 12px;
  color: var(--kb-text);
}

.timeline-head:hover {
  border-color: var(--kb-primary);
}

.step-no {
  color: var(--kb-text-muted);
  font-size: 11px;
}

.tool-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--kb-font-mono, monospace);
  font-weight: 600;
}

.perm-tag {
  padding: 1px 5px;
  border-radius: var(--kb-radius-sm);
  font-size: 10px;
}

.perm-tag.safe {
  background: var(--kb-success-soft);
  color: var(--kb-success);
}

.perm-tag.write {
  background: var(--kb-warning-soft);
  color: var(--kb-warning);
}

.perm-tag.dangerous {
  background: var(--kb-danger-soft);
  color: var(--kb-danger);
}

.latency,
.time {
  color: var(--kb-text-muted);
  font-size: 11px;
}

.timeline-detail {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.detail-label {
  font-size: 11px;
  color: var(--kb-text-muted);
}

.detail-code {
  margin: 0;
  padding: 8px 10px;
  max-height: 220px;
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
</style>
