<template>
  <div class="tr-node">
    <div class="tr-row" :style="{ paddingLeft: 12 + depth * 22 + 'px' }">
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
        <div v-if="task.scheduledDate && task.status !== 1" class="tr-tag">
          <Icon name="calendar" :size="11" /> {{ task.scheduledDate }}
        </div>
        <div v-else-if="task.someday && task.status !== 1" class="tr-tag someday">
          <Icon name="cloud" :size="11" /> 某天也许
        </div>
      </div>

      <div class="tr-actions">
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
// 任务树行（递归）：勾选完成 / 内联编辑 / 加子任务 / 安排到今天 / 某天也许 / 删除。
// 通过 inject 拿到父级提供的 reload，任何写操作后刷新当前视图。
import { ref, inject, nextTick, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { updateTask, setTaskStatus, createTask, deleteTask } from '@/api/task'
import type { TaskNode } from '@/api/task'
import { notify } from '@/utils/toast'

const props = defineProps<{ task: TaskNode; depth: number }>()
const reload = inject<() => void>('taskReload', () => {})

const editing = ref(false)
const draft = ref('')
const inputEl = ref<HTMLInputElement | null>(null)

function todayStr(): string {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const isToday = computed(() => !!props.task.scheduledDate && props.task.scheduledDate <= todayStr())

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
  if (!confirm('确定删除该任务及其所有子任务？')) return
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
</script>

<style scoped>
.tr-node { position: relative; }

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

.tr-body { flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
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
