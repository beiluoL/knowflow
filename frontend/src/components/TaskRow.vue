<template>
  <div class="tr-node" :class="{ dragging: isDragging, 'drop-target': isDropTarget }">
    <div
      class="tr-row"
      :style="{ paddingLeft: 12 + depth * 22 + 'px' }"
      :draggable="depth === 0"
      @dragstart="onDragStart"
      @dragend="onDragEnd"
      @dragover="onDragOver"
      @drop="onDrop"
      @dragleave="onDragLeave"
    >
      <span class="tr-grip" title="拖拽排序"><Icon name="grip-vertical" :size="12" /></span>

      <button
        class="tr-check"
        :class="{ done: task.status === 1 }"
        :aria-label="task.status === 1 ? '标记为未完成' : '标记为完成'"
        @click="toggle"
      >
        <Icon v-if="task.status === 1" name="check" :size="12" color="#fff" />
      </button>

      <div class="tr-body">
        <input
          v-if="editing"
          ref="inputEl"
          v-model="draft"
          class="tr-edit"
          @keyup.enter="saveEdit"
          @blur="saveEdit"
        />
        <div v-else class="tr-title" :class="{ done: task.status === 1 }" @dblclick="startEdit">
          {{ task.title }}
        </div>
        <!-- 标签 -->
        <span
          v-for="tag in task.tags || []"
          :key="tag.id"
          class="tr-chip"
          :style="{ color: tag.color, background: chipBg(tag.color) }"
        >{{ tag.name }}</span>
        <!-- 计划日期 -->
        <div v-if="task.scheduledDate && task.status !== 1" class="tr-tag">
          <Icon name="calendar" :size="11" /> {{ task.scheduledDate }}
        </div>
        <!-- 截止日期 -->
        <div v-else-if="task.dueDate && task.status !== 1" class="tr-tag" :class="{ overdue: isOverdue }">
          <Icon name="flag" :size="11" /> {{ task.dueDate }}
        </div>
        <div v-else-if="task.someday && task.status !== 1" class="tr-tag someday">
          <Icon name="cloud" :size="11" /> 某天也许
        </div>
      </div>

      <div class="tr-actions">
        <button title="打标签" @click="openTagPicker"><Icon name="tag" :size="14" /></button>
        <button title="设置截止日期" @click="setDue"><Icon name="flag" :size="14" /></button>
        <button title="添加子任务" @click="addSub"><Icon name="plus" :size="14" /></button>
        <button title="安排到今天 / 取消" :class="{ active: isToday }" @click="toggleToday">
          <Icon name="calendar" :size="14" />
        </button>
        <button title="也许某天" :class="{ active: task.someday }" @click="toggleSomeday">
          <Icon name="cloud" :size="14" />
        </button>
        <button title="删除" class="danger" @click="remove"><Icon name="trash-2" :size="14" /></button>
      </div>
    </div>

    <div v-if="task.children && task.children.length" class="tr-children">
      <TaskRow v-for="c in task.children" :key="c.id" :task="c" :depth="depth + 1" />
    </div>
  </div>
</template>

<script setup lang="ts">
// 任务树行（递归）：拖拽排序 / 勾选完成 / 内联编辑 / 截止日期 / 标签 / 加子任务 / 安排 / 某天 / 删除。
// 通过 inject 拿到父级提供的 reload 与 reorder，写操作后刷新当前视图。
import { ref, inject, nextTick, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { updateTask, setTaskStatus, createTask, deleteTask, setTaskTags, listTaskTags } from '@/api/task'
import type { TaskNode } from '@/api/task'
import { notify } from '@/utils/toast'
import { dialog } from '@/utils/dialog'

const props = defineProps<{ task: TaskNode; depth: number }>()
const reload = inject<() => void>('taskReload', () => {})
const onTaskDrop = inject<(draggedId: number, targetId: number) => void>('taskReorder', () => {})

const editing = ref(false)
const draft = ref('')
const inputEl = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const isDropTarget = ref(false)

function todayStr(): string {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const isToday = computed(() => !!props.task.scheduledDate && props.task.scheduledDate <= todayStr())
const isOverdue = computed(() => {
  if (!props.task.dueDate) return false
  return props.task.dueDate < todayStr()
})

async function toggle() {
  const next = props.task.status === 1 ? 0 : 1
  props.task.status = next // 乐观更新，刷新后由后端数据校准
  try {
    await setTaskStatus(props.task.id, next)
    reload()
  } catch (e) {
    props.task.status = props.task.status === 1 ? 0 : 1
    notify((e as Error).message || '操作失败', 'error')
  }
}

async function addSub() {
  try {
    await createTask({
      title: '新子任务',
      parentId: props.task.id,
      listId: props.task.listId,
      scheduledDate: props.task.scheduledDate || null,
      someday: props.task.someday,
    })
    reload()
  } catch (e) {
    notify((e as Error).message || '添加失败', 'error')
  }
}

async function toggleToday() {
  const next = isToday.value ? null : todayStr()
  try {
    await updateTask(props.task.id, { scheduledDate: next })
    props.task.scheduledDate = next
    reload()
  } catch (e) {
    notify((e as Error).message || '操作失败', 'error')
  }
}

async function setDue() {
  const due = await dialog.prompt({
    title: '设置截止日期',
    message: '请输入截止日期（yyyy-MM-dd），留空清除：',
    input: { placeholder: '如 2026-12-31', value: props.task.dueDate || '' },
  })
  if (due === null) return
  const v = due.trim()
  const next = v === '' ? null : v
  if (next && !/^\d{4}-\d{2}-\d{2}$/.test(next)) {
    notify('日期格式不正确，应为 yyyy-MM-dd', 'error')
    return
  }
  try {
    await updateTask(props.task.id, { dueDate: next })
    props.task.dueDate = next
    reload()
  } catch (e) {
    notify((e as Error).message || '操作失败', 'error')
  }
}

async function toggleSomeday() {
  const next = !props.task.someday
  try {
    await updateTask(props.task.id, { someday: next })
    props.task.someday = next
    reload()
  } catch (e) {
    notify((e as Error).message || '操作失败', 'error')
  }
}

async function remove() {
  if (!(await dialog.confirm({ title: '删除确认', message: '确定删除该任务及其所有子任务？', variant: 'danger' }))) return
  try {
    await deleteTask(props.task.id)
    reload()
  } catch (e) {
    notify((e as Error).message || '删除失败', 'error')
  }
}

function startEdit() {
  draft.value = props.task.title
  editing.value = true
  nextTick(() => inputEl.value?.focus())
}

async function saveEdit() {
  if (!editing.value) return
  editing.value = false
  const v = draft.value.trim()
  if (!v || v === props.task.title) return
  try {
    await updateTask(props.task.id, { title: v })
    props.task.title = v
  } catch (e) {
    notify((e as Error).message || '保存失败', 'error')
  }
}

// ===== 拖拽排序（仅顶层任务间） =====
function onDragStart(e: DragEvent) {
  if (props.depth !== 0) return
  e.dataTransfer?.setData('text/plain', String(props.task.id))
  e.dataTransfer!.effectAllowed = 'move'
  isDragging.value = true
}
function onDragEnd() {
  isDragging.value = false
  isDropTarget.value = false
}
function onDragOver(e: DragEvent) {
  if (props.depth !== 0) return
  e.preventDefault()
  e.dataTransfer!.dropEffect = 'move'
  isDropTarget.value = true
}
function onDragLeave() {
  isDropTarget.value = false
}
function onDrop(e: DragEvent) {
  if (props.depth !== 0) return
  e.preventDefault()
  isDropTarget.value = false
  const draggedId = Number(e.dataTransfer?.getData('text/plain'))
  if (!draggedId || draggedId === props.task.id) return
  onTaskDrop(draggedId, props.task.id)
}

// ===== 标签选择 =====
async function openTagPicker() {
  const allTags = await listTaskTags()
  if (!allTags.length) {
    const create = await dialog.confirm({
      title: '还没有标签',
      message: '是否立即新建一个标签？',
      variant: 'primary',
    })
    if (!create) return
    const name = await dialog.prompt({ title: '新建标签', input: { placeholder: '标签名' } })
    if (!name || !name.trim()) return
    try {
      const { createTaskTag } = await import('@/api/task')
      await createTaskTag({ name: name.trim() })
      await openTagPicker()
    } catch (e) {
      notify((e as Error).message || '创建标签失败', 'error')
    }
    return
  }
  const current = new Set((props.task.tags || []).map((t) => t.id))
  const lines = allTags.map((t, i) => `${i + 1}. ${t.name}${current.has(t.id) ? ' ✓' : ''}`).join('\n')
  const sel = await dialog.prompt({
    title: '选择标签',
    message: `输入序号（多个用逗号分隔），清空则输入 0：\n${lines}`,
    input: { placeholder: '如 1,3' },
  })
  if (sel === null) return
  const v = sel.trim()
  let nextIds: number[]
  if (v === '' || v === '0') {
    nextIds = []
  } else {
    const idxs = v.split(/[,，\s]+/).map((s) => parseInt(s.trim(), 10)).filter((n) => !isNaN(n) && n >= 1 && n <= allTags.length)
    nextIds = idxs.map((i) => allTags[i - 1].id)
    // 去重
    nextIds = [...new Set(nextIds)]
  }
  try {
    await setTaskTags(props.task.id, nextIds)
    props.task.tags = allTags.filter((t) => nextIds.includes(t.id))
    notify('标签已更新', 'success')
  } catch (e) {
    notify((e as Error).message || '标签更新失败', 'error')
  }
}

function chipBg(color: string): string {
  if (color.startsWith('var(')) return 'rgba(59,111,224,0.1)'
  return color + '1a'
}
</script>

<style scoped>
.tr-node { position: relative; }
.tr-node.dragging { opacity: 0.4; }
.tr-node.drop-target > .tr-row { box-shadow: inset 0 -2px 0 var(--kb-primary); }

.tr-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding-right: 12px;
  border-radius: 8px;
  transition: background 0.12s ease;
}
.tr-row:hover { background: var(--kb-muted); }
.tr-row:hover .tr-actions { opacity: 1; visibility: visible; }

.tr-grip {
  flex: 0 0 auto;
  width: 14px;
  color: var(--kb-muted-foreground);
  opacity: 0.4;
  cursor: grab;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.tr-grip:active { cursor: grabbing; }
.tr-row:hover .tr-grip { opacity: 0.8; }

.tr-check {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.8px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}
.tr-check:hover { border-color: var(--kb-primary); }
.tr-check.done {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
}

.tr-body { flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.tr-title {
  font-size: 14px;
  color: var(--kb-foreground);
  line-height: 1.4;
  cursor: text;
  word-break: break-word;
}
.tr-title.done { color: var(--kb-muted-foreground); text-decoration: line-through; }
.tr-edit {
  flex: 1 1 auto;
  min-width: 120px;
  font-size: 14px;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid var(--kb-primary);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
}
.tr-chip {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 999px;
  white-space: nowrap;
}
.tr-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--kb-muted);
}
.tr-tag.overdue { color: var(--kb-destructive); background: rgba(239,68,68,0.1); font-weight: 600; }
.tr-tag.someday { color: #8B5CF6; background: rgba(139, 92, 246, 0.1); }

.tr-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.12s ease;
}
.tr-actions button {
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s ease;
}
.tr-actions button:hover { background: var(--kb-card); color: var(--kb-foreground); }
.tr-actions button.active { color: var(--kb-primary); background: rgba(59, 111, 224, 0.1); }
.tr-actions button.danger:hover { color: var(--kb-destructive); background: rgba(239, 68, 68, 0.1); }

.tr-children { position: relative; }
</style>
