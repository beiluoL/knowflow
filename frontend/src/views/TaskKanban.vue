<template>
  <div class="kb-page">
    <header class="kb-head">
      <div>
        <h2 class="kb-h2">看板</h2>
        <p class="kb-sub">拖动任务卡片，在「待办 / 进行中 / 已完成」之间流转，状态实时保存。</p>
      </div>
      <div class="kb-progress">
        <div class="kb-progress-bar"><div class="kb-progress-fill" :style="{ width: donePercent + '%' }" /></div>
        <span class="kb-progress-text">{{ doneCount }} / {{ totalCount }} 已完成</span>
      </div>
    </header>

    <div v-if="loading" class="kb-loading"><Icon name="loader" :size="18" class="spin" /> 加载中…</div>
    <div v-else-if="error" class="kb-error">
      <Icon name="alert-circle" :size="22" style="color: var(--kb-destructive);" />
      <p>{{ error }}</p>
      <button class="kb-retry" @click="reload">重试</button>
    </div>

    <div v-else class="kb-board">
      <section
        v-for="col in columns"
        :key="col.key"
        class="kb-col"
        :class="{ over: dragOverStage === col.key }"
        @dragover.prevent="dragOverStage = col.key"
        @dragleave="onDragLeave(col.key)"
        @drop.prevent="onDrop(col.key)"
      >
        <div class="kb-col-head">
          <span class="kb-col-dot" :style="{ background: col.color }" />
          <span class="kb-col-title">{{ col.label }}</span>
          <span class="kb-col-count">{{ grouped[col.key].length }}</span>
        </div>

        <div class="kb-cards">
          <div
            v-for="t in grouped[col.key]"
            :key="t.id"
            class="kb-card"
            :class="{ done: t.status === 1 }"
            draggable="true"
            @dragstart="onDragStart(t, $event)"
            @dragend="onDragEnd"
          >
            <div class="kb-card-title">{{ t.title }}</div>
            <div class="kb-card-foot">
              <span v-if="t.important === 1" class="kb-chip imp" title="重要"><Icon name="star" :size="11" /> 重要</span>
              <span v-if="t.urgent === 1" class="kb-chip urg" title="紧急"><Icon name="alert-triangle" :size="11" /> 紧急</span>
              <span class="kb-card-actions">
                <button
                  class="kb-act"
                  :title="t.status === 1 ? '标记为未完成' : '标记为完成'"
                  @click="toggleDone(t)"
                >
                  <Icon :name="t.status === 1 ? 'rotate-ccw' : 'check'" :size="13" />
                </button>
                <button class="kb-act del" title="删除" @click="remove(t)">
                  <Icon name="trash-2" :size="13" />
                </button>
              </span>
            </div>
          </div>

          <div v-if="!grouped[col.key].length" class="kb-empty">拖动卡片到此处</div>
        </div>

        <button class="kb-add" @click="addInColumn(col)">
          <Icon name="plus" :size="14" /> 新建任务
        </button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
// 可拖拽看板：三列（待办 / 进行中 / 已完成），基于原生 HTML5 拖放实现卡片跨列移动，
// drop 时乐观更新本地状态并调用 updateTaskStage 实时保存阶段（后端同步完成态）。
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { listBoard, createTask, updateTaskStage, setTaskStatus, deleteTask, type TaskNode } from '@/api/task'
import { notify } from '@/utils/toast'

type Stage = 0 | 1 | 2

const columns = [
  { key: 0 as Stage, label: '待办', color: '#94A3B8' },
  { key: 1 as Stage, label: '进行中', color: '#3B6FE0' },
  { key: 2 as Stage, label: '已完成', color: '#10B981' },
]

const tasks = ref<TaskNode[]>([])
const loading = ref(false)
const error = ref('')
const dragId = ref<number | null>(null)
const dragOverStage = ref<Stage | null>(null)

const grouped = computed(() => {
  const map: Record<Stage, TaskNode[]> = { 0: [], 1: [], 2: [] }
  for (const t of tasks.value) {
    // 统一展示阶段：被勾选完成的任务归入「已完成」列
    const stage: Stage = t.status === 1 ? 2 : (t.stage as Stage)
    map[stage].push(t)
  }
  return map
})

const totalCount = computed(() => tasks.value.length)
const doneCount = computed(() => grouped.value[2].length)
const donePercent = computed(() => (totalCount.value ? Math.round((doneCount.value / totalCount.value) * 100) : 0))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    tasks.value = await listBoard()
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

function onDragStart(t: TaskNode, e: DragEvent) {
  dragId.value = t.id
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(t.id))
  }
}
function onDragEnd() {
  dragId.value = null
  dragOverStage.value = null
}
function onDragLeave(stage: Stage) {
  if (dragOverStage.value === stage) dragOverStage.value = null
}
async function onDrop(stage: Stage) {
  const id = dragId.value
  dragOverStage.value = null
  if (id == null) return
  const t = tasks.value.find((x) => x.id === id)
  if (!t || (t.status === 1 ? 2 : t.stage) === stage) {
    dragId.value = null
    return
  }
  // 乐观更新
  t.stage = stage
  if (stage === 2) t.status = 1
  else if (t.status === 1) t.status = 0
  dragId.value = null
  try {
    await updateTaskStage(id, stage)
  } catch (e) {
    notify((e as Error).message || '移动失败，已回滚', 'error')
    await reload()
  }
}

async function toggleDone(t: TaskNode) {
  const next = t.status === 1 ? 0 : 1
  t.status = next
  if (next === 1) t.stage = 2
  else if (t.stage === 2) t.stage = 0
  try {
    if (next === 1) await updateTaskStage(t.id, 2)
    else await setTaskStatus(t.id, 0)
  } catch (e) {
    notify((e as Error).message || '操作失败', 'error')
    await reload()
  }
}

async function remove(t: TaskNode) {
  if (!confirm('确定删除该任务？')) return
  try {
    await deleteTask(t.id)
    tasks.value = tasks.value.filter((x) => x.id !== t.id)
  } catch (e) {
    notify((e as Error).message || '删除失败', 'error')
  }
}

async function addInColumn(col: (typeof columns)[number]) {
  const v = prompt(`在「${col.label}」新建任务：`)
  if (!v || !v.trim()) return
  try {
    await createTask({ title: v.trim(), stage: col.key })
    await reload()
    notify('已创建', 'success')
  } catch (e) {
    notify((e as Error).message || '创建失败', 'error')
  }
}

onMounted(reload)
</script>

<style scoped>
.kb-page { display: flex; flex-direction: column; height: 100%; }
.kb-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 14px; flex-wrap: wrap; }
.kb-h2 { font-size: 18px; font-weight: 700; color: var(--kb-foreground); }
.kb-sub { font-size: 13px; color: var(--kb-muted-foreground); margin-top: 2px; }

.kb-progress { display: flex; align-items: center; gap: 10px; min-width: 200px; }
.kb-progress-bar { flex: 1 1 auto; height: 8px; border-radius: 999px; background: var(--kb-muted); overflow: hidden; }
.kb-progress-fill { height: 100%; border-radius: 999px; background: #10B981; transition: width 0.3s ease; }
.kb-progress-text { font-size: 12px; color: var(--kb-muted-foreground); white-space: nowrap; }

.kb-loading, .kb-error {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 60px 20px; color: var(--kb-muted-foreground); font-size: 14px;
}
.kb-error { color: var(--kb-destructive); }
.kb-retry { margin-top: 4px; padding: 6px 16px; border-radius: 8px; border: none; background: var(--kb-primary); color: #fff; font-size: 13px; cursor: pointer; }
.spin { animation: kb-spin 0.8s linear infinite; }
@keyframes kb-spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }

.kb-board { flex: 1 1 auto; display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; min-height: 0; }

.kb-col {
  display: flex; flex-direction: column; min-height: 0;
  background: var(--kb-card); border: 1px solid var(--kb-border); border-radius: 12px; padding: 12px;
  transition: border-color 0.12s ease, background 0.12s ease;
}
.kb-col.over { border-color: var(--kb-primary); background: rgba(59, 111, 224, 0.04); }

.kb-col-head { display: flex; align-items: center; gap: 8px; padding: 2px 2px 10px; }
.kb-col-dot { width: 9px; height: 9px; border-radius: 50%; }
.kb-col-title { font-size: 14px; font-weight: 600; color: var(--kb-foreground); flex: 1 1 auto; }
.kb-col-count { font-size: 11px; color: var(--kb-muted-foreground); background: var(--kb-muted); border-radius: 999px; min-width: 20px; text-align: center; padding: 1px 6px; }

.kb-cards { flex: 1 1 auto; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; margin-bottom: 8px; min-height: 40px; }

.kb-card {
  background: var(--kb-muted); border: 1px solid transparent; border-radius: 9px; padding: 10px;
  cursor: grab; transition: border-color 0.12s ease, box-shadow 0.12s ease, transform 0.06s ease;
}
.kb-card:hover { border-color: var(--kb-border); }
.kb-card:active { cursor: grabbing; }
.kb-card.done { opacity: 0.7; }
.kb-card-title { font-size: 13.5px; color: var(--kb-foreground); line-height: 1.45; word-break: break-word; }
.kb-card.done .kb-card-title { text-decoration: line-through; color: var(--kb-muted-foreground); }

.kb-card-foot { display: flex; align-items: center; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.kb-chip {
  display: inline-flex; align-items: center; gap: 3px; font-size: 10.5px; font-weight: 500;
  padding: 1px 7px; border-radius: 999px;
}
.kb-chip.imp { color: #F59E0B; background: rgba(245, 158, 11, 0.12); }
.kb-chip.urg { color: #EF4444; background: rgba(239, 68, 68, 0.12); }

.kb-card-actions { margin-left: auto; display: flex; gap: 2px; opacity: 0; transition: opacity 0.12s ease; }
.kb-card:hover .kb-card-actions { opacity: 1; }
.kb-act {
  width: 24px; height: 24px; border: none; background: var(--kb-card); color: var(--kb-muted-foreground);
  border-radius: 6px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; transition: all 0.12s ease;
}
.kb-act:hover { color: var(--kb-primary); }
.kb-act.del:hover { color: var(--kb-destructive); background: rgba(239, 68, 68, 0.1); }

.kb-empty {
  font-size: 12px; color: var(--kb-muted-foreground); text-align: center;
  padding: 18px 0; border: 1px dashed var(--kb-border); border-radius: 9px;
}

.kb-add {
  flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  width: 100%; padding: 8px; border-radius: 8px; border: 1px dashed var(--kb-border);
  background: transparent; color: var(--kb-muted-foreground); font-size: 12.5px; cursor: pointer; transition: all 0.12s ease;
}
.kb-add:hover { border-color: var(--kb-primary); color: var(--kb-primary); }

@media (max-width: 860px) {
  .kb-board { grid-template-columns: 1fr; grid-auto-rows: minmax(240px, auto); }
}
</style>
