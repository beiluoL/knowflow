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
      <span class="tr-grip" title="拖拽排序"><Icon name="menu" :size="12" /></span>

      <button
        class="tr-check" :disabled="pending || !!task.completionRequirement" :aria-busy="pending"
        :class="{ done: task.status === 1 }"
        :aria-label="task.status === 1 ? '标记为未完成' : '标记为完成'"
        @click="toggle"
      >
        <Icon v-if="task.status === 1" name="check" :size="12" color="var(--kf-primary-foreground)" />
      </button>

      <div class="tr-body">
        <input
          v-if="editing"
          ref="inputEl"
          v-model="draft"
          class="tr-edit kf-input kf-form-control" aria-label="编辑任务标题" @keydown.esc="editing = false"
          @keyup.enter="saveEdit"
          @blur="saveEdit"
        />
        <div v-else class="tr-title" role="button" tabindex="0" :title="task.title" :aria-label="`编辑任务标题：${task.title}`" :class="{ done: task.status === 1 }" @keydown.enter="startEdit" @dblclick="startEdit">
          {{ task.title }}
        </div>
        <span v-if="pending" class="tr-status is-doing" role="status">正在提交…</span>
        <span v-else class="tr-status" :class="statusInfo.cls">{{ statusInfo.label }}</span>
        <span v-if="task.important" class="tr-tag is-important">重要</span>
        <span v-if="task.urgent" class="tr-tag is-urgent">紧急</span>
        <span v-if="task.learningPlanRef" class="tr-tag" :title="task.learningPlanRef">来自学习计划</span>
        <TaskCompletionAction :requirement="task.completionRequirement" :title="task.title" />
        <!-- 标签 -->
        <span
          v-for="tag in task.tags || []"
          :key="tag.id"
          class="tr-chip"
          :style="{ color: tag.color, background: chipBg(tag.color) }"
        >{{ tag.name }}</span>
        <!-- 时间 / 日期信息固定在行尾，避免随标签数量漂移 -->
        <span class="tr-dates">
          <span v-if="task.estimatedMinutes" class="tr-date">
            <Icon name="timer" :size="11" />{{ task.estimatedMinutes }} 分钟
          </span>
          <span v-if="task.scheduledDate && task.status !== 1" class="tr-date">
            <Icon name="calendar" :size="11" />{{ task.scheduledDate }}
          </span>
          <span v-if="task.dueDate && task.status !== 1" class="tr-date" :class="{ overdue: isOverdue }">
            <Icon name="flag" :size="11" />{{ task.dueDate }}
          </span>
          <span v-else-if="task.someday && task.status !== 1" class="tr-date someday">
            <Icon name="cloud" :size="11" />某天也许
          </span>
        </span>
      </div>

      <div class="tr-actions" :inert="pending || undefined">
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

    <SelectDialog :open="tagOpen" :model-value="tagIds" :options="tagOptions" title="选择标签" description="选择多个标签，确定后保存；取消保留原标签。" multiple :loading="tagLoading" :load-error="tagLoadError" :error="tagSaveError" :busy="tagSaving" @confirm="saveTags" @cancel="closeTags" @retry="loadTags">
      <template #empty><template v-if="!allTags.length"><p>还没有标签</p><Button variant="secondary" @click="createFirstTag">新建标签</Button></template><p v-else>无匹配项</p></template>
    </SelectDialog>
    <div v-if="task.children && task.children.length" class="tr-children">
      <TaskRow v-for="c in task.children" :key="c.id" :task="c" :depth="depth + 1" />
    </div>
  </div>
</template>

<script setup lang="ts">
// 任务树行（递归）：拖拽排序 / 勾选完成 / 内联编辑 / 截止日期 / 标签 / 加子任务 / 安排 / 某天 / 删除。
// 通过 inject 拿到父级提供的 reload 与 reorder，写操作后刷新当前视图。
import { ref, inject, nextTick, computed, watch, onBeforeUnmount } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import SelectDialog from '@/components/ui/SelectDialog.vue'
import Button from '@/components/ui/Button.vue'
import TaskCompletionAction from '@/components/learning-actions/TaskCompletionAction.vue'
import { updateTask, setTaskStatus, createTask, deleteTask, setTaskTags, listTaskTags } from '@/api/task'
import type { TaskTagVO, TaskNode } from '@/api/task'
import { notify } from '@/utils/toast'
import { dialog } from '@/utils/dialog'

const props = defineProps<{ task: TaskNode; depth: number }>()
const reload = inject<() => void>('taskReload', () => {})
const onTaskDrop = inject<(draggedId: number, targetId: number) => void>('taskReorder', () => {})

const editing = ref(false)
const pending = ref(false)
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
/** 状态只用文字 + 浅色标签表达，不新造后端状态。 */
const statusInfo = computed(() => {
  if (props.task.status === 1) return { label: '已完成', cls: 'is-done' }
  if (isOverdue.value) return { label: '已延期', cls: 'is-overdue' }
  if (props.task.stage === 1) return { label: '进行中', cls: 'is-doing' }
  return { label: '待开始', cls: 'is-todo' }
})

async function toggle() {
  if (pending.value) return
  if (props.task.completionRequirement) { notify('此任务需要真实学习记录，请使用去练习入口', 'info'); return }
  pending.value = true
  const next = props.task.status === 1 ? 0 : 1
  try {
    if (next === 1 && !(await dialog.confirm({"confirmText":"确认完成","cancelText":"返回编辑","icon":"check-circle","variant":"default", title: '完成任务', message: '确认提交完成？系统会按原有任务证明和完成规则校验。' }))) return
    await setTaskStatus(props.task.id, next)
    // 不先改 props；仅在服务成功后重新读取真实任务状态。
    await reload()
  } catch (e) { notify((e as Error).message || '操作失败，任务状态未确认', 'error') }
  finally { pending.value = false }
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
  await dialog.confirm({
    title:'删除确认', message:'确定删除该任务及其所有子任务？', variant:'danger',
    confirmText:'确认删除', cancelText:'取消', icon:'trash-2',
    onConfirm:async()=>{
      try { await deleteTask(props.task.id); reload() }
      catch (e) { notify((e as Error).message || '删除失败','error'); throw e }
    },
  })
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

// ===== 标签选择：草稿只在确认后写入服务器。 =====
const tagOpen=ref(false),tagLoading=ref(false),tagSaving=ref(false)
const tagLoadError=ref(''),tagSaveError=ref(''),tagIds=ref<number[]>([]),allTags=ref<TaskTagVO[]>([])
const tagOptions=computed(()=>allTags.value.map(tag=>({value:tag.id,label:tag.name,color:tag.color})))
let tagEpoch=0
function closeTags(){if(tagSaving.value)return;tagEpoch++;tagOpen.value=false;tagLoading.value=false}
async function loadTags(){
  const epoch=++tagEpoch,id=props.task.id
  tagLoading.value=true;tagLoadError.value=''
  try{const tags=await listTaskTags();if(epoch===tagEpoch&&props.task.id===id&&tagOpen.value)allTags.value=tags}
  catch(error){if(epoch===tagEpoch)tagLoadError.value=(error as Error).message||'标签加载失败'}
  finally{if(epoch===tagEpoch)tagLoading.value=false}
}
function openTagPicker(){if(pending.value||tagSaving.value)return;tagIds.value=(props.task.tags||[]).map(tag=>tag.id);allTags.value=[];tagSaveError.value='';tagOpen.value=true;void loadTags()}
async function saveTags(ids:number[]){
  if(tagSaving.value)return
  const epoch=tagEpoch,id=props.task.id
  tagIds.value=[...ids];tagSaving.value=true;tagSaveError.value=''
  try{await setTaskTags(id,ids);if(epoch!==tagEpoch||props.task.id!==id)return;props.task.tags=allTags.value.filter(tag=>ids.includes(tag.id));tagOpen.value=false;notify('标签已更新','success')}
  catch(error){if(epoch===tagEpoch)tagSaveError.value=(error as Error).message||'标签更新失败'}
  finally{if(epoch===tagEpoch)tagSaving.value=false}
}
async function createFirstTag(){
  const epoch=tagEpoch
  // 保留已有无标签时的新建能力，先关闭选择弹窗以免叠加两个焦点域。
  closeTags()
  const name=await dialog.prompt({title:'新建标签',input:{placeholder:'标签名'}})
  if(!name?.trim()||tagEpoch!==epoch+1)return
  try{const {createTaskTag}=await import('@/api/task');await createTaskTag({name:name.trim()});if(tagEpoch===epoch+1)openTagPicker()}
  catch(error){if(tagEpoch===epoch+1)notify((error as Error).message||'创建标签失败','error')}
}
watch(()=>props.task.id,()=>{tagEpoch++;tagOpen.value=false;tagLoading.value=false;tagSaving.value=false})
onBeforeUnmount(()=>{tagEpoch++})

function chipBg(color: string): string {
  if (color.startsWith('var(')) return 'rgba(59,111,224,0.1)'
  return color + '1a'
}
</script>

<style scoped>
.tr-node { position: relative; }
.tr-node.dragging { opacity: 0.4; }
.tr-node.drop-target > .tr-row { box-shadow: inset 0 -2px 0 var(--kf-primary); }

.tr-row {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  min-height: 46px;
  padding-right: var(--kf-space-3);
  border-bottom: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  transition: background 0.12s ease;
}
.tr-row:hover { background: var(--kf-muted); }
.tr-row:hover .tr-actions, .tr-row:focus-within .tr-actions { opacity: 1; visibility: visible; }

.tr-grip {
  flex: 0 0 auto;
  width: 14px;
  color: var(--kf-muted-foreground);
  opacity: 0;
  cursor: grab;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.12s ease;
}
.tr-row:hover .tr-grip, .tr-row:focus-within .tr-grip { opacity: 0.5; }
.tr-grip:active { cursor: grabbing; }

.tr-check {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.8px solid var(--kf-border);
  background: var(--kf-card);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.tr-check:hover { border-color: var(--kf-primary); }
.tr-check.done {
  background: var(--kf-primary);
  border-color: var(--kf-primary);
}

.tr-body { flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 6px; flex-wrap: nowrap; }
.tr-title {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 52%;
  font-size: var(--kf-fs-body-md);
  color: var(--kf-foreground);
  line-height: 1.45;
  cursor: text;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tr-title.done { color: var(--kf-muted-foreground); text-decoration: line-through; }
.tr-edit:not(.kf-form-control) {
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-primary);
  background: var(--kf-card);
  outline: none;
}
.tr-edit {
  flex: 1 1 auto;
  min-width: 120px;
  font-size: var(--kf-fs-body-md);
  padding: 4px 8px;
  color: var(--kf-foreground);
}
.tr-chip {
  display: inline-flex;
  align-items: center;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--kf-fs-xs);
  font-weight: 600;
  padding: 1px 8px;
  border-radius: var(--kf-radius-full);
  white-space: nowrap;
}
.tr-status {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: var(--kf-radius-full);
  font-size: var(--kf-fs-xs);
  font-weight: 500;
  white-space: nowrap;
}
.tr-status.is-todo { background: var(--kf-muted); color: var(--kf-muted-foreground); }
.tr-status.is-doing { background: var(--kf-primary-subtle); color: var(--kf-primary); }
.tr-status.is-done { background: var(--kf-success-soft); color: var(--kf-success); }
.tr-status.is-overdue { background: var(--kf-danger-soft); color: var(--kf-destructive); }

.tr-tag {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 20px;
  padding: 0 7px;
  font-size: var(--kf-fs-xs);
  color: var(--kf-muted-foreground);
  border-radius: var(--kf-radius-full);
  background: var(--kf-muted);
  white-space: nowrap;
}
.tr-tag.is-important { color: var(--kf-warning); background: var(--kf-warning-soft); }
.tr-tag.is-urgent { color: var(--kf-destructive); background: var(--kf-danger-soft); font-weight: 600; }

/* 行尾时间与日期：固定位置，不与标题、标签争夺空间 */
.tr-dates {
  flex: 0 0 auto;
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  font-size: var(--kf-fs-xs);
  color: var(--kf-muted-foreground);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.tr-date { display: inline-flex; align-items: center; gap: 3px; }
.tr-date.overdue { color: var(--kf-destructive); font-weight: 600; }
.tr-date.someday { color: var(--kf-accent); }

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
  color: var(--kf-muted-foreground);
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s ease;
}
.tr-actions button:hover { background: var(--kf-card); color: var(--kf-foreground); }
.tr-actions button.active { color: var(--kf-primary); background: rgba(59, 111, 224, 0.1); }
.tr-actions button.danger:hover { color: var(--kf-destructive); background: rgba(239, 68, 68, 0.1); }

.tr-children { position: relative; }

.tr-body { min-width:0; }.tr-title { overflow-wrap:anywhere; }.tr-check:disabled { opacity:.6; cursor:wait; }
.tr-node :is(button,input,[role="button"]):focus-visible:not(.kf-form-control, .kf-input, .kf-textarea, .kf-select, .kf-select-trigger) { outline:var(--kf-field-focus-width) solid var(--kf-ring); outline-offset:var(--kf-space-1); }
@media(max-width:768px) { .tr-row { flex-wrap:wrap; padding-block:var(--kf-space-3); }.tr-actions { width:100%; justify-content:flex-end; opacity:1; visibility:visible; }.tr-body { flex:1; flex-wrap:wrap; }.tr-title { max-width:100%; flex:1 1 100%; white-space:normal; }.tr-dates { margin-left:0; }.tr-actions button { min-width:var(--kf-btn-height-sm); min-height:var(--kf-btn-height-sm); } }
</style>
