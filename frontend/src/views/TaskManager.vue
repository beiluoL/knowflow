<template>
  <div class="tm-page">
    <header class="tm-page-head"><div><h1>任务中心</h1><p>安排待办，执行学习任务；完成状态以服务端记录为准。</p></div><RouterLink to="/learning/today-plan" class="tm-plan-link">查看今日计划 <Icon name="arrow-right" :size="14" /></RouterLink></header>
    <!-- 顶部模式切换：列表 / 四象限 / 看板 -->
    <div class="tm-toolbar">
      <div class="tm-modes" role="group" aria-label="任务视图">
        <button class="tm-mode" :class="{ active: mode === 'list' }" :aria-pressed="mode === 'list'" @click="mode = 'list'">
          <Icon name="list" :size="15" /> <span>列表</span>
        </button>
        <button class="tm-mode" :class="{ active: mode === 'quadrant' }" :aria-pressed="mode === 'quadrant'" @click="mode = 'quadrant'">
          <Icon name="layout-grid" :size="15" /> <span>四象限</span>
        </button>
        <button class="tm-mode" :class="{ active: mode === 'board' }" :aria-pressed="mode === 'board'" @click="mode = 'board'">
          <Icon name="layout" :size="15" /> <span>看板</span>
        </button>
      </div>
    </div>

    <!-- 列表模式：保留原有左栏 + 任务树 -->
    <div v-if="mode === 'list'" class="tm-list-wrap">
    <!-- 左栏：智能列表 + 清单 / 项目 / 领域 -->
    <aside class="tm-side">
      <div class="tm-side-section">
        <div class="tm-side-title">智能列表</div>
        <button
          v-for="s in smartItems"
          :key="s.key"
          class="tm-nav"
          :class="{ active: viewMode === 'smart' && smart === s.key }"
          @click="selectSmart(s.key)"
        >
          <Icon
            :name="s.icon"
            :size="15"
            :color="viewMode === 'smart' && smart === s.key ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)'"
          />
          <span class="tm-nav-label">{{ s.label }}</span>
          <span class="tm-nav-count">{{ counts[s.key] ?? '—' }}</span>
        </button>
      </div>

      <div class="tm-side-section">
        <div class="tm-side-head">
          <span class="tm-side-title">清单 / 项目</span>
          <button class="tm-add-list" title="新建清单" aria-label="新建清单" @click="createList"><Icon name="plus" :size="14" /></button>
        </div>
        <button
          v-for="l in lists"
          :key="l.id"
          class="tm-nav"
          :class="{ active: viewMode === 'list' && selectedListId === l.id }"
          @click="selectList(l)"
        >
          <Icon :name="listIcon(l)" :size="15" :color="l.color || 'var(--kb-muted-foreground)'" />
          <span class="tm-nav-label">{{ l.name }}</span>
          <span class="tm-nav-count">{{ l.taskCount }}</span>
        </button>
        <div v-if="!lists.length" class="tm-side-empty">还没有清单，点右上角 + 新建</div>
      </div>

      <!-- 标签 -->
      <div class="tm-side-section">
        <div class="tm-side-head">
          <span class="tm-side-title">标签</span>
          <button class="tm-add-list" title="新建标签" aria-label="新建标签" @click="createTag"><Icon name="tag" :size="14" /></button>
        </div>
        <div v-for="t in tags" :key="t.id" class="tag-nav-wrap"><button
          class="tm-nav tag-nav"
          :class="{ active: viewMode === 'tag' && selectedTagId === t.id }"
          @click="selectTag(t)"
        >
          <Icon name="tag" :size="15" :color="t.color || 'var(--kb-muted-foreground)'" />
          <span class="tm-nav-label">{{ t.name }}</span>
          <span class="tm-nav-count">{{ t.taskCount }}</span>
        </button><button class="tag-del" :aria-label="`删除标签 ${t.name}`" title="删除标签" @click="deleteTag(t)">
            <Icon name="x" :size="11" />
          </button>
        </div>
        <div v-if="!tags.length" class="tm-side-empty">还没有标签，点右上角 # 新建</div>
      </div>
    </aside>

    <!-- 右栏：任务列表 -->
    <section class="tm-content">
      <header class="tm-content-head">
        <div>
          <h1 class="tm-h1">{{ currentTitle }}</h1>
          <p class="tm-sub">
            {{
              viewMode === 'smart' && smart === 'logbook'
                ? `已完成 ${flatten(tasks).filter(t => t.status === 1).length} 项`
                : `共 ${openCount} 个未完成任务`
            }}
          </p>
        </div>
      </header>

      <p v-if="auxError" class="tm-aux-error" role="alert">{{ auxError }} <button @click="reload">重试导航数据</button></p>
      <label class="tm-search"><Icon name="search" :size="16" /><input class="kb-input kb-form-control" v-model="keyword" type="search" aria-label="搜索当前任务" placeholder="搜索当前列表标题或备注" /></label>
      <p class="tm-sub" role="status" aria-live="polite">{{ loading ? '正在加载任务…' : error ? '任务加载中断' : `当前显示 ${flatten(visibleTasks).length} 项` }}</p>
      <div v-if="loading" class="tm-loading" role="status">
        <Icon name="loader" :size="18" class="spin" /> 加载中…
      </div>

      <div v-else-if="error" class="tm-error">
        <Icon name="alert-circle" :size="22" style="color: var(--kb-destructive);" />
        <p>{{ error }}</p>
        <button class="tm-retry" @click="reload">重试</button>
      </div>

      <template v-else>
        <div v-if="!visibleTasks.length" class="tm-empty">
          <Icon name="check-circle" :size="34" style="color: var(--kb-muted-foreground); opacity: 0.5;" />
          <p>{{ keyword ? '没有匹配的任务，试试其他关键词。' : '当前列表没有任务。可以添加待办，或切换其他列表。' }}</p>
        </div>

        <div class="tm-tasks">
          <TaskRow v-for="t in visibleTasks" :key="t.id" :task="t" :depth="0" />
        </div>

        <div v-if="viewMode !== 'tag' && !(viewMode === 'smart' && smart === 'logbook')" class="tm-add">
          <Icon name="plus" :size="15" style="color: var(--kb-muted-foreground);" />
          <input
            v-model="newTitle"
            class="tm-add-input kb-input kb-form-control" aria-label="新任务标题" :disabled="creating"
            :placeholder="addPlaceholder"
            @keyup.enter="onCreateEnter"
          /><Button size="sm" :loading="creating" :disabled="!newTitle.trim()" @click="addTask">添加任务</Button>
        </div>
        <p v-if="createError" class="tm-aux-error" role="alert">{{ createError }}；输入已保留，可重试。</p>
      </template>
    </section>
    </div>

    <div v-else-if="mode === 'quadrant'" class="tm-feature"><TaskQuadrant /></div>
    <div v-else-if="mode === 'board'" class="tm-feature"><TaskKanban /></div>
  </div>
</template>

<script setup lang="ts">
// Things3 式任务清单：左栏智能列表 + 清单/项目/领域，右栏任务树（勾选/内联编辑/子任务/安排/某天也许/删除）。
import { ref, computed, onMounted, onBeforeUnmount, provide } from 'vue'
import { useRoute } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import TaskRow from '@/components/TaskRow.vue'
import TaskQuadrant from '@/views/TaskQuadrant.vue'
import TaskKanban from '@/views/TaskKanban.vue'
import {
  listTasks,
  listTasksByList,
  createTask,
  listTaskLists,
  createTaskList,
  listTaskTags,
  createTaskTag,
  deleteTaskTag,
  reorderTasks,
  type TaskNode,
  type TaskListVO,
  type TaskTagVO,
  type SmartList,
} from '@/api/task'
import { notify, getApiError } from '@/utils/toast'
import { dialog } from '@/utils/dialog'

const smartItems = [
  { key: 'inbox', label: '收件箱', icon: 'inbox' },
  { key: 'today', label: '今天', icon: 'calendar' },
  { key: 'upcoming', label: '即将到来', icon: 'flag' },
  { key: 'anytime', label: '待办', icon: 'layers' },
  { key: 'someday', label: '某天也许', icon: 'cloud' },
  { key: 'logbook', label: '日志', icon: 'check-circle' },
  { key: 'all', label: '全部', icon: 'list-checks' },
] as const

const viewMode = ref<'smart' | 'list' | 'tag'>('smart')
const route = useRoute()
const smart = ref<SmartList>(smartItems.some(s=>s.key===route.query.smart) ? route.query.smart as SmartList : 'inbox')
const selectedListId = ref<number | null>(null)
const selectedTagId = ref<number | null>(null)
// 顶部模式：列表 / 四象限 / 看板
const mode = ref<'list' | 'quadrant' | 'board'>('list')
const tasks = ref<TaskNode[]>([])
const lists = ref<TaskListVO[]>([])
const tags = ref<TaskTagVO[]>([])
const counts = ref<Record<string, number>>({})
const loading = ref(false)
const error = ref('')
const newTitle = ref('')
const creating = ref(false)
const createError = ref('')
const auxError = ref('')
const keyword = ref('')
let taskRequest = 0
const visibleTasks = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  if (!query) return tasks.value
  const filter = (nodes: TaskNode[]): TaskNode[] => nodes.flatMap(node => {
    if ((node.title + ' ' + (node.notes || '')).toLocaleLowerCase().includes(query)) return [node]
    const children = filter(node.children || [])
    return children.length ? [{ ...node, children }] : []
  })
  return filter(tasks.value)
})
function onCreateEnter(event: KeyboardEvent) { if (!event.isComposing && event.keyCode !== 229) void addTask() }
onBeforeUnmount(() => { taskRequest++ })

const currentTitle = computed(() => {
  if (viewMode.value === 'list') {
    const l = lists.value.find((x) => x.id === selectedListId.value)
    return l ? l.name : '清单'
  }
  if (viewMode.value === 'tag') {
    const t = tags.value.find((x) => x.id === selectedTagId.value)
    return t ? `#${t.name}` : '标签'
  }
  const s = smartItems.find((x) => x.key === smart.value)
  return s ? s.label : '任务'
})

const addPlaceholder = computed(() => {
  if (viewMode.value === 'list') return '添加任务到该清单…'
  if (viewMode.value === 'tag') return '标签视图下不支持直接添加，请切换到清单或智能列表…'
  if (smart.value === 'today') return '添加今天要做的任务…'
  if (smart.value === 'logbook') return '这是已完成任务的归档'
  return '添加任务…'
})

const openCount = computed(() => countOpen(tasks.value))

function countOpen(nodes: TaskNode[]): number {
  let n = 0
  for (const t of nodes) {
    const children = t.children || []
    n += (t.status !== 1 ? 1 : 0) + countOpen(children)
  }
  return n
}

function listIcon(l: TaskListVO): string {
  if (l.icon) return l.icon
  if (l.kind === 'area') return 'layers'
  if (l.kind === 'project') return 'folder'
  return 'list'
}

function flatten(nodes: TaskNode[]): TaskNode[] {
  const out: TaskNode[] = []
  const walk = (ns: TaskNode[]) =>
    ns.forEach((n) => {
      out.push(n)
      if (n.children) walk(n.children)
    })
  walk(nodes)
  return out
}

function computeCounts(all: TaskNode[], logbook: TaskNode[]) {
  const today = todayStr()
  const allFlat = flatten(all)
  let inbox = 0
  let todayC = 0
  let up = 0
  let any = 0
  let some = 0
  for (const t of allFlat) {
    if (t.status === 1) continue
    if (t.listId == null && !t.someday) inbox++
    if (t.scheduledDate && t.scheduledDate <= today) todayC++
    if (t.scheduledDate && t.scheduledDate > today) up++
    // 待办：已归属清单但未安排日期，且非某天也许
    if (t.listId != null && !t.scheduledDate && !t.someday) any++
    if (t.someday) some++
  }
  counts.value = {
    inbox,
    today: todayC,
    upcoming: up,
    anytime: any,
    someday: some,
    logbook: flatten(logbook).length,
    all: allFlat.length,
  }
}

async function loadLists() {
  try { lists.value = await listTaskLists() } catch { auxError.value = '清单加载失败' }
}

async function loadTags() {
  try {
    tags.value = await listTaskTags()
  } catch {
    auxError.value = '标签加载失败'
  }
}

async function loadTasks() {
  const request = ++taskRequest
  loading.value = true; error.value = ''
  try {
    let rows: TaskNode[]
    if (viewMode.value === 'tag' && selectedTagId.value != null) rows = filterByTag(await listTasks('all'), selectedTagId.value)
    else if (viewMode.value === 'list' && selectedListId.value != null) rows = await listTasksByList(selectedListId.value)
    else rows = await listTasks(smart.value)
    // 慢请求不能把用户当前选择覆盖回旧列表。
    if (request === taskRequest) tasks.value = rows
  } catch (e) { if (request === taskRequest) error.value = getApiError(e, '任务加载失败') }
  finally { if (request === taskRequest) loading.value = false }
}

/** 按标签过滤任务树：保留含该标签的节点及其祖先链；子任务无该标签也一并保留以保持上下文。 */
function filterByTag(nodes: TaskNode[], tagId: number): TaskNode[] {
  const out: TaskNode[] = []
  for (const n of nodes) {
    const children = n.children ? filterByTag(n.children, tagId) : []
    const hasTag = (n.tags || []).some((t) => t.id === tagId)
    if (hasTag || children.length > 0) {
      out.push({ ...n, children })
    }
  }
  return out
}

async function reload() {
  auxError.value = ''
  await Promise.all([loadTasks(), loadLists(), loadTags()])
}

async function loadCounts() {
  try {
    const [all, logbook] = await Promise.all([listTasks('all'), listTasks('logbook')])
    computeCounts(all, logbook)
  } catch {
    counts.value = {}; auxError.value = '任务数量暂时无法加载'
  }
}

function selectSmart(s: SmartList) {
  viewMode.value = 'smart'
  smart.value = s
  selectedTagId.value = null
  loadTasks()
}

function selectList(l: TaskListVO) {
  viewMode.value = 'list'
  selectedListId.value = l.id
  selectedTagId.value = null
  loadTasks()
}

function selectTag(t: TaskTagVO) {
  viewMode.value = 'tag'
  selectedTagId.value = t.id
  loadTasks()
}

async function createTag() {
  const name = await dialog.prompt({
    title: '新建标签',
    message: '请输入标签名称：',
    input: { placeholder: '标签名', maxlength: 20 },
  })
  if (name === null || !name.trim()) return
  try {
    await createTaskTag({ name: name.trim() })
    await loadTags()
    notify('标签已创建', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '创建标签失败'), 'error')
  }
}

async function deleteTag(t: TaskTagVO) {
  await dialog.confirm({ "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2",
      title: '删除标签',
      message: `确定删除标签「${t.name}」？关联任务不会被删除。`,
      variant: 'danger',
      onConfirm: async () => {
          try {
              await deleteTaskTag(t.id);
              if (selectedTagId.value === t.id) {
                  selectedTagId.value = null;
                  viewMode.value = 'smart';
                  smart.value = 'inbox';
              }
              await loadTags();
              notify('标签已删除', 'success');
              if (selectedTagId.value === null)
                  loadTasks();
          }
          catch (e: unknown) {
              notify(getApiError(e, '删除标签失败'), 'error');
              throw e;
          }
      } });
}

function todayStr(): string {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

async function addTask() {
  if (creating.value || viewMode.value === 'tag') return
  const v = newTitle.value.trim()
  if (!v) return
  creating.value = true; createError.value = ''
  try {
    await createTask({
      title: v,
      listId: viewMode.value === 'list' ? selectedListId.value : null,
      scheduledDate: viewMode.value === 'smart' && smart.value === 'today' ? todayStr() : null,
    })
    if (newTitle.value.trim() === v) newTitle.value = ''
    await reload(); void loadCounts()
  } catch (e) { createError.value = getApiError(e, '添加失败') }
  finally { creating.value = false }
}

async function createList() {
  const name = await dialog.prompt({
    title: '新建清单',
    message: '请输入清单名称：',
    input: { placeholder: '清单名称', maxlength: 40 },
  })
  if (name === null || !name.trim()) return
  try {
    await createTaskList({ name: name.trim(), kind: 'list' })
    await loadLists()
    notify('清单已创建', 'success')
  } catch (e) {
    notify((e as Error).message || '创建失败', 'error')
  }
}

// ===== 拖拽排序（仅智能列表 / 清单视图下，顶层任务之间） =====
async function handleReorder(draggedId: number, targetId: number) {
  if (keyword.value.trim()) { notify('请清除搜索后再排序', 'info'); return }
  const dragIdx = tasks.value.findIndex((t) => t.id === draggedId)
  const targetIdx = tasks.value.findIndex((t) => t.id === targetId)
  if (dragIdx === -1 || targetIdx === -1 || dragIdx === targetIdx) return
  // 乐观更新：先移动，再持久化
  const [moved] = tasks.value.splice(dragIdx, 1)
  tasks.value.splice(targetIdx, 0, moved)
  const items = tasks.value.map((t, i) => ({ id: t.id, sortOrder: i }))
  try {
    await reorderTasks(items)
  } catch (e) {
    notify((e as Error).message || '排序失败，已恢复', 'error')
    reload()
  }
}

// 供递归 TaskRow 在写操作后刷新当前视图与计数
provide('taskReload', async () => {
  await reload()
  loadCounts()
})
provide('taskReorder', handleReorder)

onMounted(async () => {
  await Promise.all([loadLists(), loadTasks(), loadCounts(), loadTags()])
})
</script>

<style scoped>
.tm-page {
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 104px);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  overflow: hidden;
}

/* ===== 顶部模式切换 ===== */
.tm-toolbar { flex: 0 0 auto; padding: 12px 16px; border-bottom: 1px solid var(--kb-border); }
.tm-modes { display: inline-flex; gap: 4px; background: var(--kb-muted); padding: 3px; border-radius: 10px; }
.tm-mode {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px; border: none; background: transparent;
  border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 500;
  color: var(--kb-muted-foreground); transition: all 0.12s ease;
}
.tm-mode:hover { color: var(--kb-foreground); }
.tm-mode.active { background: var(--kb-card); color: var(--kb-primary); box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06); }

/* 列表模式容器（左栏 + 右栏横向排列） */
.tm-list-wrap { flex: 1 1 auto; min-height: 0; display: flex; }

/* 四象限 / 看板特性视图容器 */
.tm-feature { flex: 1 1 auto; min-height: 0; padding: 16px; }

/* ===== 左栏 ===== */
.tm-side {
  flex: 0 0 248px;
  border-right: 1px solid var(--kb-border);
  padding: 16px 12px;
  overflow-y: auto;
  background: var(--kb-card);
}
.tm-side-section + .tm-side-section { margin-top: 18px; }
.tm-side-title {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--kb-muted-foreground);
  text-transform: uppercase;
  padding: 0 8px 6px;
}
.tm-side-head { display: flex; align-items: center; justify-content: space-between; }
.tm-add-list {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.tm-add-list:hover { background: var(--kb-muted); color: var(--kb-foreground); }

.tm-nav {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 8px;
  margin-top: 2px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  color: var(--kb-foreground);
  transition: background 0.12s ease;
}
.tm-nav:hover { background: var(--kb-muted); }
.tm-nav.active { background: rgba(59, 111, 224, 0.1); }
.tm-nav-label { flex: 1 1 auto; font-size: 13.5px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tm-nav.active .tm-nav-label { color: var(--kb-primary); }
.tm-nav-count {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  background: var(--kb-muted);
  border-radius: 999px;
  min-width: 20px;
  text-align: center;
  padding: 1px 6px;
}
.tag-nav .tag-del {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.12s ease, color 0.12s ease;
}
.tag-nav:hover .tag-del { opacity: 0.7; }
.tag-nav .tag-del:hover { opacity: 1; color: var(--kb-destructive); background: rgba(239, 68, 68, 0.1); }
.tm-side-empty { font-size: 12px; color: var(--kb-muted-foreground); padding: 8px; line-height: 1.5; }

/* ===== 右栏 ===== */
.tm-content {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow-y: auto;
  padding: 22px 26px;
}
.tm-content-head { margin-bottom: 14px; }
.tm-h1 { font-size: 22px; font-weight: 700; color: var(--kb-foreground); }
.tm-sub { font-size: 13px; color: var(--kb-muted-foreground); margin-top: 2px; }

.tm-loading, .tm-error, .tm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 60px 20px;
  color: var(--kb-muted-foreground);
  font-size: 14px;
}
.tm-error { color: var(--kb-destructive); }
.tm-retry {
  margin-top: 4px;
  padding: 6px 16px;
  border-radius: 8px;
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 13px;
  cursor: pointer;
}
.spin { animation: tm-spin 0.8s linear infinite; }
@keyframes tm-spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }

.tm-tasks { margin-top: 6px; }

.tm-add {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  transition: border-color 0.15s ease;
  border: 0;
}
.tm-add:focus-within {
  background: var(--kb-field-active); }
.tm-add-input:not(.kb-form-control) {
  border: none;
  outline: none;
  background: transparent;
}
.tm-add-input {
  flex: 1 1 auto;
  font-size: 14px;
  color: var(--kb-foreground);
}
.tm-add-input::placeholder { color: var(--kb-muted-foreground); }

/* 响应式：窄屏时左栏收窄 */
@media (max-width: 720px) {
  .tm-page { height: calc(100vh - 104px); flex-direction: column; }
  .tm-side { flex: 0 0 auto; border-right: none; border-bottom: 1px solid var(--kb-border); display: flex; gap: 4px; overflow-x: auto; }
  .tm-side-section { display: flex; gap: 4px; }
  .tm-side-title, .tm-side-head, .tm-side-empty { display: none; }
  .tm-nav { width: auto; }
}

.tm-page-head { display:flex; align-items:center; justify-content:space-between; gap:var(--kb-space-4); flex-wrap:wrap; padding:var(--kb-space-5); border-bottom:1px solid var(--kb-border); }
.tm-page-head h1 { font-size:var(--kb-fs-h2); font-weight:700; color:var(--kb-foreground); }
.tm-page-head p { color:var(--kb-muted-foreground); font-size:var(--kb-fs-body-sm); margin-top:var(--kb-space-2); }
.tm-plan-link { display:flex; align-items:center; gap:var(--kb-space-2); color:var(--kb-primary); font-size:var(--kb-fs-body-sm); }
.tm-search { display:flex; align-items:center; gap:var(--kb-space-2); padding:var(--kb-space-3); border:1px solid var(--kb-border); border-radius:var(--kb-radius-md); background:var(--kb-card); margin-block:var(--kb-space-3); }
.tm-search input, .tm-add-input { min-width:0; flex:1; background:transparent; color:var(--kb-foreground); outline:none; border:0; box-shadow:none; }
.tm-aux-error { color:var(--kb-danger); font-size:var(--kb-fs-body-sm); margin-block:var(--kb-space-2); }
.tag-nav-wrap { display:flex; align-items:center; }.tag-nav-wrap .tm-nav { min-width:0; }.tag-del { color:var(--kb-muted-foreground); }
.tm-page :is(button,a,input):focus-visible:not(.kb-form-control, .kb-input, .kb-textarea, .kb-select, .kb-select-trigger) { outline:var(--kb-field-focus-width) solid var(--kb-ring); outline-offset:var(--kb-space-1); }
@media(max-width:768px) { .tm-list-wrap { flex-direction:column; }.tm-side { width:100%; max-height:12rem; flex-shrink:0; border-right:0; border-bottom:1px solid var(--kb-border); }.tm-side-section { min-width:0; }.tm-content { padding:var(--kb-space-4); }.tm-add { flex-wrap:wrap; border: 0; } }
@media(prefers-reduced-motion:reduce) { .tm-page * { transition:none; animation:none; } }
</style>

<style scoped>
.tm-add:has(input:focus-visible){outline:0}.tm-add input.kb-form-control,.tm-add input.kb-form-control:focus,.tm-add input.kb-form-control:focus-visible{background:transparent;outline:0;box-shadow:none;border:0}
</style>
