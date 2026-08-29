<template>
  <div class="lev-page">
    <!-- ===== 页面标题 ===== -->
    <header class="lev-header">
      <div>
        <h1 class="kb-h1">学习行为事件</h1>
        <p class="kb-body-sm mt-1">统一记录你的每一次学习行为，是掌握度引擎、AI 教练与学习计划的数据底座（Phase 1）</p>
      </div>
      <button type="button" class="lev-refresh" :disabled="loading" @click="loadData">
        <Icon name="refresh-cw" :size="14" :class="{ spin: loading }" />
        <span>刷新</span>
      </button>
    </header>

    <!-- ===== 错误态 ===== -->
    <div v-if="error" class="lev-error">
      <Icon name="alert-circle" :size="28" style="color: var(--kb-destructive);" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ error }}</p>
      <button type="button" class="lev-retry" @click="loadData">重新加载</button>
    </div>

    <!-- ===== 加载态 ===== -->
    <div v-else-if="loading" class="lev-loading">
      <div v-for="i in 6" :key="i" class="lev-skel-row animate-pulse"></div>
    </div>

    <!-- ===== 内容 ===== -->
    <template v-else>
      <!-- 概览卡片 -->
      <section class="lev-stats">
        <StatCard
          icon="activity"
          icon-color="var(--kb-primary)"
          icon-bg="rgba(59,111,224,.1)"
          :icon-size="18"
          :value="page?.total ?? 0"
          label="累计事件"
          layout="horizontal"
        />
        <StatCard
          icon="list"
          icon-color="#10B981"
          icon-bg="rgba(16,185,129,.12)"
          :icon-size="18"
          :value="filteredLabel"
          label="当前筛选"
          layout="horizontal"
        />
        <StatCard
          icon="calendar"
          icon-color="#F59E0B"
          icon-bg="rgba(245,158,11,.12)"
          :icon-size="18"
          :value="records.length"
          label="本页条数"
          layout="horizontal"
        />
      </section>

      <!-- 筛选栏 -->
      <section class="lev-filter">
        <label class="lev-filter-label">
          <Icon name="filter" :size="14" />
          <span>事件类型</span>
        </label>
        <select v-model="eventType" class="lev-select" @change="onFilterChange">
          <option value="">全部类型</option>
          <option v-for="t in eventTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <span class="lev-filter-hint text-sm">共 {{ page?.total ?? 0 }} 条</span>
      </section>

      <!-- 空态 -->
      <div v-if="records.length === 0" class="lev-empty">
        <Icon name="inbox" :size="40" style="color: var(--kb-muted-foreground); opacity: .5;" />
        <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">
          {{ eventType ? '该类型暂时没有事件记录' : '还没有学习行为记录，去学习、做题或签到试试~' }}
        </p>
      </div>

      <!-- 事件表格 -->
      <div v-else class="lev-table-wrap">
        <table class="lev-table">
          <thead>
            <tr>
              <th style="width: 180px;">时间</th>
              <th style="width: 140px;">事件类型</th>
              <th style="width: 120px;">资源类型</th>
              <th style="width: 100px;">资源ID</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in records" :key="row.id">
              <td class="lev-time tabular-nums">{{ formatTime(row.createTime) }}</td>
              <td>
                <span class="lev-badge" :style="badgeStyle(row.eventType)">{{ eventLabel(row.eventType) }}</span>
              </td>
              <td class="lev-muted">{{ row.resourceType || '—' }}</td>
              <td class="lev-muted tabular-nums">{{ row.resourceId ?? '—' }}</td>
              <td class="lev-meta">{{ formatMeta(row.metadata) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <footer v-if="(page?.pages ?? 1) > 1" class="lev-pager">
        <button type="button" class="lev-page-btn" :disabled="current <= 1" @click="goPage(current - 1)">
          <Icon name="chevron-left" :size="14" /> 上一页
        </button>
        <span class="lev-page-info text-sm">
          第 {{ current }} / {{ page?.pages ?? 1 }} 页
        </span>
        <button type="button" class="lev-page-btn" :disabled="current >= (page?.pages ?? 1)" @click="goPage(current + 1)">
          下一页 <Icon name="chevron-right" :size="14" />
        </button>
      </footer>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { learningApi } from '@/api/learning'
import type { LearningEventVO, IPage } from '@/api/types'
import StatCard from '@/components/ui/StatCard.vue'
import Icon from '@/components/ui/Icon.vue'

// 事件类型中文标签（与后端 LearningEventType 一一对应）
const eventTypes: { value: string; label: string }[] = [
  { value: 'DOCUMENT_READ', label: '文档阅读' },
  { value: 'CHAPTER_START', label: '章节开始' },
  { value: 'CHAPTER_COMPLETE', label: '章节完成' },
  { value: 'QUESTION_ANSWERED', label: '题目作答' },
  { value: 'QUESTION_CORRECT', label: '题目答对' },
  { value: 'QUESTION_WRONG', label: '题目答错' },
  { value: 'CODE_SUBMITTED', label: '代码提交' },
  { value: 'CODE_PASSED', label: '代码通过' },
  { value: 'CODE_FAILED', label: '代码未过' },
  { value: 'FLASHCARD_REVIEWED', label: '闪卡复习' },
  { value: 'RECALL_COMPLETED', label: '主动回忆完成' },
  { value: 'AI_CHAT', label: 'AI 对话' },
  { value: 'KNOWLEDGE_VIEWED', label: '知识图谱查看' },
  { value: 'PATH_COMPLETED', label: '路径完成' },
  { value: 'CHECK_IN', label: '每日签到' },
  { value: 'MISTAKE', label: '错题归集' },
]

const labelMap = new Map(eventTypes.map((t) => [t.value, t.label]))
const eventLabel = (v: string) => labelMap.get(v) ?? v

// 事件类型 → 徽章配色（与整体 token 体系一致）
const badgeColors: Record<string, string> = {
  DOCUMENT_READ: '#3B6FE0',
  CHAPTER_START: '#3B6FE0',
  CHAPTER_COMPLETE: '#10B981',
  QUESTION_ANSWERED: '#6B7280',
  QUESTION_CORRECT: '#10B981',
  QUESTION_WRONG: '#EF4444',
  CODE_SUBMITTED: '#6B7280',
  CODE_PASSED: '#10B981',
  CODE_FAILED: '#EF4444',
  FLASHCARD_REVIEWED: '#3B6FE0',
  RECALL_COMPLETED: '#10B981',
  AI_CHAT: '#3B6FE0',
  KNOWLEDGE_VIEWED: '#8B5CF6',
  PATH_COMPLETED: '#10B981',
  CHECK_IN: '#F59E0B',
  MISTAKE: '#EF4444',
}
const badgeStyle = (v: string) => {
  const c = badgeColors[v] ?? '#6B7280'
  return { color: c, background: `${c}1A`, borderColor: `${c}33` }
}

const filteredLabel = computed(() => (eventType.value ? eventLabel(eventType.value) : '全部类型'))

const records = ref<LearningEventVO[]>([])
const page = ref<IPage<LearningEventVO> | null>(null)
const loading = ref(false)
const error = ref('')
const eventType = ref('')
const current = ref(1)
const size = 20

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const res = await learningApi.learningEvents({
      eventType: eventType.value || undefined,
      current: current.value,
      size,
    })
    page.value = res
    records.value = res.records ?? []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
    records.value = []
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  current.value = 1
  loadData()
}

function goPage(p: number) {
  current.value = p
  loadData()
}

function formatTime(t?: string | null) {
  if (!t) return '—'
  return t.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 19)
}

function formatMeta(meta?: string | null) {
  if (!meta) return '—'
  try {
    const obj = JSON.parse(meta)
    const parts = Object.entries(obj).map(([k, v]) => `${k}=${v}`)
    return parts.length ? parts.join('，') : '—'
  } catch {
    return meta
  }
}

onMounted(loadData)
</script>

<style scoped>
.lev-page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}
.lev-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.lev-refresh,
.lev-retry,
.lev-page-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  border-radius: 8px;
  padding: 7px 12px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.lev-refresh:hover:not(:disabled),
.lev-retry:hover,
.lev-page-btn:hover:not(:disabled) {
  background: var(--kb-primary-soft);
  border-color: var(--kb-primary);
}
.lev-refresh:disabled,
.lev-page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.spin {
  animation: lev-spin 0.8s linear infinite;
}
@keyframes lev-spin {
  to {
    transform: rotate(360deg);
  }
}

.lev-error,
.lev-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 16px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  text-align: center;
}

.lev-loading {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.lev-skel-row {
  height: 44px;
  border-radius: 10px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}

.lev-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.lev-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.lev-filter-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
.lev-select {
  height: 34px;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  padding: 0 10px;
  font-size: 13px;
  cursor: pointer;
}
.lev-select:focus {
  outline: none;
  border-color: var(--kb-ring);
  box-shadow: 0 0 0 3px var(--kb-primary-soft);
}
.lev-filter-hint {
  color: var(--kb-muted-foreground);
}

.lev-table-wrap {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  overflow: hidden;
}
.lev-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.lev-table thead th {
  text-align: left;
  padding: 12px 14px;
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  font-weight: 600;
  border-bottom: 1px solid var(--kb-border);
  white-space: nowrap;
}
.lev-table tbody td {
  padding: 11px 14px;
  border-bottom: 1px solid var(--kb-border);
  vertical-align: top;
}
.lev-table tbody tr:last-child td {
  border-bottom: none;
}
.lev-table tbody tr:hover {
  background: var(--kb-primary-soft);
}
.lev-time {
  color: var(--kb-foreground);
  white-space: nowrap;
}
.lev-muted {
  color: var(--kb-muted-foreground);
}
.lev-meta {
  color: var(--kb-muted-foreground);
  font-size: 12px;
  word-break: break-all;
}
.lev-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
  white-space: nowrap;
}

.lev-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 16px;
}
.lev-page-info {
  color: var(--kb-muted-foreground);
}
</style>
