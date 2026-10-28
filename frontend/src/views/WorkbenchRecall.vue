<template>
  <div class="wb-page workbench-space animate-fade-in recall-page" :style="{ '--mc': 'var(--kf-state-warning)' }">
    <!-- ============ Module Hero ============ -->
    <section class="wb-hero">
      <div class="wb-hero-bg" aria-hidden="true">
        <span class="wb-blob"></span>
        <span class="wb-grid"></span>
      </div>
      <div class="wb-hero-inner">
        <div class="wb-hero-head">
          <div class="wb-hero-text">
            <span class="wb-eyebrow">
              <span class="wb-eyebrow-dot"></span>
              Step 03 · 复习 · Active Recall
            </span>
            <h1 class="wb-title">
              <Icon name="edit-2" :size="28" class="wb-title-icon" />
              主动回忆 · 三轮闭卷默写
            </h1>
            <p class="wb-subtitle">
              <strong>即时默写 → 补漏默写 → 1 小时后复测</strong>，三轮闭卷书写后比对原文，
              标注遗漏与改写，强制大脑提取让记忆真正扎根。
            </p>
          </div>
          <div class="recall-hero-actions">
            <router-link to="/workbench/review" class="kf-btn recall-ghost-btn">
              <Icon name="repeat" :size="15" aria-hidden="true" /> 间隔重复
            </router-link>
            <button class="kf-btn kf-btn-primary" data-test="recall-create" @click="openCreate">
              <Icon name="plus" :size="16" aria-hidden="true" /> 新建默写
            </button>
          </div>
        </div>

        <nav class="wb-loop-nav" aria-label="学习闭环">
          <router-link
            v-for="s in loopSteps"
            :key="s.key"
            :to="s.path"
            class="wb-loop-step"
            :class="{ 'is-current': s.key === 'review' }"
          >
            <span class="wb-loop-num">{{ s.num }}</span>
            <span class="wb-loop-name">{{ s.name }}</span>
          </router-link>
        </nav>
      </div>
    </section>

    <!-- ============ 会话列表 ============ -->
    <section class="recall-list-section">
      <div class="recall-list-head">
        <h2 class="wb-section-title">
          <Icon name="list" :size="18" style="color: var(--kf-state-warning);" />
          默写会话记录
          <span class="wb-section-hint">{{ visibleSessions.length }} / {{ sessions.length }} 个会话</span>
        </h2>
        <div class="recall-list-tools">
          <div class="recall-search">
            <Icon name="search" :size="14" aria-hidden="true" />
            <input
              v-model="keyword"
              type="search"
              placeholder="搜索标题或原文…"
              aria-label="搜索默写会话"
            />
          </div>
          <div class="recall-filters" role="group" aria-label="会话筛选">
            <button
              v-for="f in filters"
              :key="f.key"
              type="button"
              class="recall-filter"
              :class="{ 'is-active': filter === f.key }"
              :aria-pressed="filter === f.key"
              :data-test="`recall-filter-${f.key}`"
              @click="filter = f.key"
            >
              {{ f.label }}
              <span v-if="f.count != null" class="recall-filter-count">{{ f.count }}</span>
            </button>
          </div>
        </div>
      </div>

      <div v-if="loading" class="recall-grid">
        <div v-for="n in 3" :key="n" class="recall-card recall-skeleton">
          <div class="recall-skel-line" style="width: 55%; height: 16px;"></div>
          <div class="recall-skel-line" style="width: 85%; height: 12px; margin-top: 10px;"></div>
          <div class="recall-skel-line" style="width: 70%; height: 12px; margin-top: 8px;"></div>
        </div>
      </div>

      <div v-else-if="loadError" class="recall-empty" role="alert">
        <div class="recall-empty-icon"><Icon name="alert-circle" :size="34" /></div>
        <h3 class="recall-empty-title">会话列表加载失败</h3>
        <p class="recall-empty-desc">{{ loadError }}</p>
        <button class="kf-btn recall-ghost-btn" @click="loadSessions">
          <Icon name="refresh-cw" :size="14" aria-hidden="true" /> 重试
        </button>
      </div>

      <div v-else-if="visibleSessions.length === 0" class="recall-empty">
        <div class="recall-empty-icon"><Icon name="edit-2" :size="36" /></div>
        <h3 class="recall-empty-title">{{ sessions.length === 0 ? '还没有默写会话' : '没有符合条件的会话' }}</h3>
        <p class="recall-empty-desc">
          {{ sessions.length === 0 ? '粘贴一段要记忆的内容，开始三轮闭卷默写。' : '换个筛选条件或清空搜索再试试。' }}
        </p>
        <button v-if="sessions.length === 0" class="kf-btn kf-btn-primary" @click="openCreate">
          <Icon name="plus" :size="15" aria-hidden="true" /> 新建默写
        </button>
      </div>

      <div v-else class="recall-grid">
        <article
          v-for="s in visibleSessions"
          :key="s.id"
          class="recall-card"
          :class="{ 'is-archived': isArchived(s) }"
          data-test="recall-session-card"
        >
          <div class="recall-card-head">
            <span class="recall-status" :class="`is-${stateOf(s)}`">
              <span class="recall-status-dot"></span>{{ statusLabel(s) }}
            </span>
            <span class="recall-progress-text">{{ completedRounds(s) }}/3 轮</span>
            <button
              type="button"
              class="recall-more"
              :aria-label="`更多操作：${s.title || '未命名会话'}`"
              @click.stop="openMenu($event, s)"
            >
              <Icon name="more-horizontal" :size="16" aria-hidden="true" />
            </button>
          </div>

          <button type="button" class="recall-card-main" @click="primaryAction(s)">
            <h3 class="recall-card-title">{{ s.title || '未命名会话' }}</h3>
            <p class="recall-source-line">
              <Icon :name="s.sourceType === 'NOTE' ? 'book-open' : 'file-text'" :size="12" aria-hidden="true" />
              {{ s.sourceLabel || '粘贴原文' }}
            </p>
            <p class="recall-card-excerpt">{{ excerpt(s.sourceText) }}</p>
          </button>

          <div class="recall-round-chips">
            <span
              v-for="r in 3"
              :key="r"
              class="recall-round-chip"
              :class="{ 'is-null': roundScore(s, r) == null, 'is-current': currentRoundFor(s) === r }"
            >
              <span class="recall-round-label">R{{ r }}</span>
              <span class="recall-round-value">{{ roundScore(s, r) ?? '--' }}</span>
            </span>
          </div>

          <div class="recall-card-foot">
            <div class="recall-card-meta">
              <span><Icon name="clock" :size="12" aria-hidden="true" /> 最近 {{ formatTime(s.updateTime || s.createTime) }}</span>
              <span v-if="stateOf(s) === 'WAITING'" class="recall-due-hint">
                复测 {{ formatTime(s.round3DueTime) }} · {{ remainingText(s) }}
              </span>
              <span v-else-if="stateOf(s) === 'DUE'" class="recall-due-hint is-ready">
                第三轮已到期，可以开始复测
              </span>
            </div>
            <button
              type="button"
              class="recall-primary-link"
              :data-test="`recall-primary-${s.id}`"
              @click="primaryAction(s)"
            >
              {{ primaryLabel(s) }}
              <Icon name="chevron-right" :size="14" aria-hidden="true" />
            </button>
          </div>
        </article>
      </div>
    </section>

    <RecallSessionDialog
      :open="dialogOpen"
      :session="editingSession"
      @close="dialogOpen = false"
      @copy="copySession"
      @saved="onDialogSaved"
    />

    <ContextMenu
      :open="menuOpen"
      :x="menuX"
      :y="menuY"
      :items="menuItems"
      label="默写会话操作"
      @close="menuOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import ContextMenu from '@/components/ui/ContextMenu.vue'
import type { ContextMenuItem } from '@/components/ui/ContextMenu.vue'
import RecallSessionDialog from '@/components/workbench/RecallSessionDialog.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { listRecallSessions, copyRecallSession, archiveRecallSession, deleteRecallSession } from '@/api/workbench'
import type { WbRecallSession } from '@/api/types'
import './workbench-shared.css'
import '@/styles/workbench.css'

const route = useRoute()
const router = useRouter()

const sessions = ref<WbRecallSession[]>([])
const loading = ref(true)
const loadError = ref('')
const keyword = ref('')
const filter = ref<'all' | 'active' | 'waiting' | 'completed' | 'archived'>('all')
const dialogOpen = ref(false)
const editingSession = ref<WbRecallSession | null>(null)
const nowTick = ref(Date.now())
let timer: number | undefined

const loopSteps = [
  { key: 'input', num: '01', name: '输入', path: '/workbench/capture' },
  { key: 'organize', num: '02', name: '整理', path: '/workbench/notes' },
  { key: 'review', num: '03', name: '复习', path: '/workbench/review' },
  { key: 'output', num: '04', name: '输出', path: '/workbench/story' },
]

type RecallState = 'DRAFT' | 'ROUND1' | 'ROUND2' | 'WAITING' | 'DUE' | 'COMPLETED'

function isArchived(s: WbRecallSession) {
  return (s.archived ?? 0) === 1
}

function serverNow(s: WbRecallSession) {
  if (!s.serverNow) return nowTick.value
  const parsed = parseTime(s.serverNow)
  if (!parsed) return nowTick.value
  return parsed
}

function parseTime(value?: string) {
  if (!value) return 0
  return new Date(value.includes('T') ? value : value.replace(' ', 'T')).getTime()
}

function stateOf(s: WbRecallSession): RecallState {
  if (s.status === 'DRAFT') return 'DRAFT'
  if (s.status === 'COMPLETED') return 'COMPLETED'
  if (!s.round1Text) return 'ROUND1'
  if (!s.round2Text) return 'ROUND2'
  if (s.round3Text) return 'COMPLETED'
  const due = parseTime(s.round3DueTime)
  if (due && serverNow(s) < due) return 'WAITING'
  return 'DUE'
}

function statusLabel(s: WbRecallSession) {
  if (isArchived(s)) return '已归档'
  switch (stateOf(s)) {
    case 'DRAFT': return '草稿'
    case 'ROUND1': return '第一轮进行中'
    case 'ROUND2': return s.round2Draft ? '第二轮进行中' : '第一轮已提交 · 待补漏'
    case 'WAITING': return '等待延迟复测'
    case 'DUE': return s.round3Draft ? '第三轮进行中' : '第三轮已到期'
    default: return '已完成'
  }
}

function completedRounds(s: WbRecallSession) {
  return [s.round1Text, s.round2Text, s.round3Text].filter(Boolean).length
}

function currentRoundFor(s: WbRecallSession) {
  const state = stateOf(s)
  if (state === 'ROUND1' || state === 'DRAFT') return 1
  if (state === 'ROUND2') return 2
  if (state === 'WAITING' || state === 'DUE') return 3
  return 3
}

function roundScore(s: WbRecallSession, round: number) {
  if (round === 1) return s.round1Score
  if (round === 2) return s.round2Score
  return s.round3Score
}

function excerpt(text?: string) {
  const value = (text || '').replace(/\s+/g, ' ').trim()
  return value.length > 100 ? value.slice(0, 100) + '…' : value || '（空原文）'
}

function formatTime(value?: string) {
  const time = parseTime(value)
  if (!time) return '--'
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function remainingText(s: WbRecallSession) {
  const due = parseTime(s.round3DueTime)
  if (!due) return ''
  const remain = due - serverNow(s)
  if (remain <= 0) return '已到期'
  const minutes = Math.ceil(remain / 60000)
  if (minutes >= 60) {
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    return `剩余 ${h} 小时${m > 0 ? ` ${m} 分钟` : ''}`
  }
  return `剩余 ${minutes} 分钟`
}

function matchesFilter(s: WbRecallSession) {
  if (filter.value === 'archived') return isArchived(s)
  if (isArchived(s)) return false
  const state = stateOf(s)
  if (filter.value === 'active') return state !== 'COMPLETED'
  if (filter.value === 'waiting') return state === 'WAITING' || state === 'DUE'
  if (filter.value === 'completed') return state === 'COMPLETED'
  return true
}

const visibleSessions = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return sessions.value.filter((s) => {
    if (!matchesFilter(s)) return false
    if (!kw) return true
    return (s.title || '').toLowerCase().includes(kw) || (s.sourceText || '').toLowerCase().includes(kw)
  })
})

const counts = computed(() => {
  const live = sessions.value.filter((s) => !isArchived(s))
  return {
    all: sessions.value.length,
    active: live.filter((s) => stateOf(s) !== 'COMPLETED').length,
    waiting: live.filter((s) => ['WAITING', 'DUE'].includes(stateOf(s))).length,
    completed: live.filter((s) => stateOf(s) === 'COMPLETED').length,
    archived: sessions.value.filter(isArchived).length,
  }
})

const filters = computed(() => [
  { key: 'all' as const, label: '全部', count: counts.value.all },
  { key: 'active' as const, label: '进行中', count: counts.value.active },
  { key: 'waiting' as const, label: '待复测', count: counts.value.waiting },
  { key: 'completed' as const, label: '已完成', count: counts.value.completed },
  { key: 'archived' as const, label: '已归档', count: counts.value.archived },
])

function primaryLabel(s: WbRecallSession) {
  if (isArchived(s)) return '查看会话'
  const state = stateOf(s)
  if (state === 'DRAFT') return '继续编辑'
  if (state === 'COMPLETED') return '查看结果'
  if (state === 'DUE') return '开始第三轮'
  if (state === 'WAITING') return '查看复测进度'
  return '继续默写'
}

function primaryAction(s: WbRecallSession) {
  if (stateOf(s) === 'DRAFT' && !isArchived(s)) {
    openEdit(s)
    return
  }
  router.push(`/workbench/recall/${s.id}`)
}

async function loadSessions() {
  loading.value = true
  loadError.value = ''
  try {
    sessions.value = await listRecallSessions()
  } catch (e) {
    loadError.value = getApiError(e, '加载会话列表失败')
    notify(loadError.value, 'error')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingSession.value = null
  dialogOpen.value = true
}

function openEdit(s: WbRecallSession) {
  editingSession.value = s
  dialogOpen.value = true
}

async function onDialogSaved(payload: { id: number; started: boolean }) {
  dialogOpen.value = false
  editingSession.value = null
  await loadSessions()
  if (payload.started) {
    router.push(`/workbench/recall/${payload.id}`)
  }
}

/* ============================ 更多菜单 ============================ */
const menuOpen = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuSession = ref<WbRecallSession | null>(null)

const menuItems = computed<ContextMenuItem[]>(() => {
  const s = menuSession.value
  if (!s) return []
  const state = stateOf(s)
  const items: ContextMenuItem[] = [
    {
      key: 'open',
      label: state === 'COMPLETED' ? '查看结果' : state === 'DRAFT' ? '继续编辑' : '继续默写',
      icon: state === 'COMPLETED' ? 'list' : 'edit-2',
      onSelect: () => primaryAction(s),
    },
  ]
  if (state !== 'COMPLETED' || !isArchived(s)) {
    items.push({ key: 'edit', label: '编辑会话', icon: 'pencil', onSelect: () => openEdit(s) })
  }
  items.push({ key: 'copy', label: '复制为新会话', icon: 'copy', onSelect: () => copySessionById(s) })
  items.push({
    key: 'archive',
    label: isArchived(s) ? '取消归档' : '归档',
    icon: 'archive',
    onSelect: () => toggleArchive(s),
  })
  items.push({ key: 'sep', label: '', separator: true })
  items.push({ key: 'delete', label: '删除会话', icon: 'trash-2', danger: true, onSelect: () => removeSession(s) })
  return items
})

function openMenu(event: MouseEvent, s: WbRecallSession) {
  menuSession.value = s
  menuX.value = event.clientX
  menuY.value = event.clientY
  menuOpen.value = true
}

async function copySession() {
  if (editingSession.value) await copySessionById(editingSession.value)
}

async function copySessionById(s: WbRecallSession) {
  try {
    const id = await copyRecallSession(s.id)
    notify('已复制为新的草稿会话', 'success')
    await loadSessions()
    const copied = sessions.value.find((item) => item.id === id)
    if (copied) openEdit(copied)
  } catch (e) {
    notify(getApiError(e, '复制失败'), 'error')
  }
}

async function toggleArchive(s: WbRecallSession) {
  const target = isArchived(s) ? 0 : 1
  try {
    await archiveRecallSession(s.id, target)
    notify(target === 1 ? '会话已归档' : '会话已取消归档', 'success')
    await loadSessions()
  } catch (e) {
    notify(getApiError(e, '归档失败'), 'error')
  }
}

async function removeSession(s: WbRecallSession) {
  confirmDialog('删除后该会话的三轮默写记录将不再显示，确认删除？', {
    title: '删除默写会话',
    confirmText: '确认删除',
    cancelText: '取消',
    icon: 'trash-2',
    variant: 'danger',
  }).then(async (ok) => {
    if (!ok) return
    try {
      await deleteRecallSession(s.id)
      notify('会话已删除', 'success')
      await loadSessions()
    } catch (e) {
      notify(getApiError(e, '删除失败'), 'error')
    }
  })
}

onMounted(() => {
  loadSessions()
  timer = window.setInterval(() => { nowTick.value = Date.now() }, 30000)
  const queryId = Number(route.query.id)
  if (Number.isInteger(queryId) && queryId > 0) {
    router.replace(`/workbench/recall/${queryId}`)
  }
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
/* 页面沿用 workbench-space 的按钮/输入样式，但保留 .wb-page 原有的 24px 分区间距 */
.recall-page { gap: 24px; }
.recall-hero-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.recall-ghost-btn {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  color: var(--kf-foreground);
}
.recall-ghost-btn:hover { border-color: var(--kf-state-warning); color: var(--kf-state-warning); }
.recall-list-section { display: flex; flex-direction: column; gap: var(--kf-space-4); }
.recall-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-4);
  flex-wrap: wrap;
}
.recall-list-tools { display: flex; align-items: center; gap: var(--kf-space-3); flex-wrap: wrap; }
.recall-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 var(--kf-space-3);
  height: var(--kf-btn-height-sm);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  color: var(--kf-muted-foreground);
}
.recall-search input {
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kf-fs-caption);
  color: var(--kf-foreground);
  width: 180px;
}
.recall-search:focus-within { outline: 2px solid var(--kf-ring); outline-offset: 1px; }
.recall-filters { display: flex; gap: 4px; flex-wrap: wrap; }
.recall-filter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  cursor: pointer;
}
.recall-filter:hover { background: var(--kf-background); }
.recall-filter.is-active {
  background: var(--kf-primary-soft);
  border-color: color-mix(in srgb, var(--kf-primary) 30%, transparent);
  color: var(--kf-primary);
  font-weight: 600;
}
.recall-filter:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 1px; }
.recall-filter-count {
  font-family: var(--font-mono);
  font-size: 10px;
  opacity: 0.75;
}
.recall-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--kf-space-4);
}
.recall-card {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border-radius: var(--kf-radius-lg);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.recall-card:hover {
  border-color: color-mix(in srgb, var(--kf-state-warning) 35%, var(--kf-border));
  box-shadow: var(--shadow-card-hover);
}
.recall-card.is-archived { opacity: 0.72; }
.recall-card-head { display: flex; align-items: center; gap: var(--kf-space-2); }
.recall-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: var(--kf-background);
  color: var(--kf-muted-foreground);
  white-space: nowrap;
}
.recall-status.is-DRAFT { background: var(--kf-background); color: var(--kf-muted-foreground); }
.recall-status.is-ROUND1,
.recall-status.is-ROUND2 { background: var(--kf-primary-soft); color: var(--kf-primary); }
.recall-status.is-WAITING { background: color-mix(in srgb, var(--kf-state-warning) 12%, transparent); color: var(--kf-state-warning); }
.recall-status.is-DUE { background: color-mix(in srgb, var(--kf-state-error) 12%, transparent); color: var(--kf-state-error); }
.recall-status.is-COMPLETED { background: color-mix(in srgb, var(--kf-state-success) 12%, transparent); color: var(--kf-state-success); }
.recall-status-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.recall-progress-text {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.recall-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
}
.recall-more:hover { background: var(--kf-background); color: var(--kf-foreground); }
.recall-more:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 1px; }
.recall-card-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  flex: 1;
}
.recall-card-main:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 2px; border-radius: var(--kf-radius-sm); }
.recall-card-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: var(--kf-fs-h4);
  font-weight: 700;
  color: var(--kf-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recall-source-line {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.recall-card-excerpt {
  margin: 0;
  font-size: var(--kf-fs-caption);
  line-height: 1.6;
  color: var(--kf-muted-foreground);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.recall-round-chips { display: flex; gap: 6px; }
.recall-round-chip {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  padding: 3px 9px;
  border-radius: var(--kf-radius-sm);
  background: color-mix(in srgb, var(--kf-state-warning) 8%, transparent);
}
.recall-round-chip.is-null { background: var(--kf-background); opacity: 0.7; }
.recall-round-chip.is-current { box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--kf-state-warning) 45%, transparent); }
.recall-round-label { font-family: var(--font-mono); font-size: 9px; color: var(--kf-muted-foreground); }
.recall-round-value { font-family: var(--font-mono); font-size: 14px; font-weight: 700; color: var(--kf-state-warning); }
.recall-round-chip.is-null .recall-round-value { color: var(--kf-muted-foreground); }
.recall-card-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--kf-space-2);
  padding-top: var(--kf-space-2);
  border-top: 1px solid var(--kf-border);
}
.recall-card-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  color: var(--kf-muted-foreground);
}
.recall-card-meta span { display: inline-flex; align-items: center; gap: 4px; }
.recall-due-hint { color: var(--kf-state-warning); font-weight: 600; }
.recall-due-hint.is-ready { color: var(--kf-state-error); }
.recall-primary-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--kf-primary);
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.recall-primary-link:hover { text-decoration: underline; }
.recall-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kf-space-3);
  padding: 48px 20px;
  border-radius: var(--kf-radius-lg);
  border: 1px dashed var(--kf-border);
  background: var(--kf-card);
  text-align: center;
}
.recall-empty-icon { color: var(--kf-state-warning); }
.recall-empty-title { margin: 0; font-size: var(--kf-fs-h4); font-weight: 700; color: var(--kf-foreground); }
.recall-empty-desc { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); max-width: 420px; }
.recall-skeleton { min-height: 190px; }
.recall-skel-line {
  border-radius: 4px;
  background: linear-gradient(90deg, var(--kf-background), var(--kf-muted));
  animation: recall-pulse 1.4s ease-in-out infinite;
}
@keyframes recall-pulse { 0%, 100% { opacity: 0.55; } 50% { opacity: 1; } }
@media (max-width: 1200px) {
  .recall-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .recall-grid { grid-template-columns: 1fr; }
  .recall-list-head { align-items: flex-start; }
  .recall-search input { width: 130px; }
}
</style>
