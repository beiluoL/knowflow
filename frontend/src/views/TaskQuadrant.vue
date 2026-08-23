<template>
  <div class="qm-page">
    <!-- 顶部说明 + 全局新建 -->
    <header class="qm-head">
      <div>
        <h2 class="qm-h2">四象限视图</h2>
        <p class="qm-sub">按「重要 / 紧急」划分任务，优先处理左上，减少右下消耗。</p>
      </div>
    </header>

    <div v-if="loading" class="qm-loading"><Icon name="loader" :size="18" class="spin" /> 加载中…</div>
    <div v-else-if="error" class="qm-error">
      <Icon name="alert-circle" :size="22" style="color: var(--kb-destructive);" />
      <p>{{ error }}</p>
      <button class="qm-retry" @click="reload">重试</button>
    </div>

    <!-- 2x2 象限矩阵 -->
    <div v-else class="qm-grid">
      <section
        v-for="q in quadrants"
        :key="q.key"
        class="qm-quad"
        :class="q.cls"
      >
        <div class="qm-quad-head">
          <div class="qm-quad-title">
            <Icon :name="q.icon" :size="16" :color="q.color" />
            <span>{{ q.label }}</span>
          </div>
          <span class="qm-quad-count">{{ grouped[q.key].length }}</span>
        </div>
        <p class="qm-quad-tip">{{ q.tip }}</p>

        <div class="qm-cards">
          <div v-for="t in grouped[q.key]" :key="t.id" class="qm-card" :class="{ done: t.status === 1 }">
            <button class="qm-check" :class="{ done: t.status === 1 }" @click="complete(t)">
              <Icon v-if="t.status === 1" name="check" :size="12" color="#fff" />
            </button>
            <div class="qm-card-body">
              <div class="qm-card-title" :class="{ done: t.status === 1 }">{{ t.title }}</div>
            </div>
            <div class="qm-card-flags">
              <button
                class="qm-flag"
                :class="{ on: t.important === 1 }"
                title="重要"
                @click="toggleImportant(t)"
              >
                <Icon name="star" :size="13" />
              </button>
              <button
                class="qm-flag"
                :class="{ on: t.urgent === 1 }"
                title="紧急"
                @click="toggleUrgent(t)"
              >
                <Icon name="alert-triangle" :size="13" />
              </button>
              <button class="qm-flag del" title="删除" @click="remove(t)">
                <Icon name="trash-2" :size="13" />
              </button>
            </div>
          </div>

          <div v-if="!grouped[q.key].length" class="qm-empty">暂无任务</div>
        </div>

        <button class="qm-add" @click="addInQuadrant(q)">
          <Icon name="plus" :size="14" /> 在此象限新建
        </button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
// 艾森豪威尔四象限：按 important / urgent 将开放任务划分至 4 个区域，
// 支持快速切换重要 / 紧急标记、完成与删除。
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { listTasks, createTask, updateTask, setTaskStatus, deleteTask, type TaskNode } from '@/api/task'
import { notify } from '@/utils/toast'

type QKey = 'q1' | 'q2' | 'q3' | 'q4'

const tasks = ref<TaskNode[]>([])
const loading = ref(false)
const error = ref('')

const quadrants = [
  { key: 'q1' as QKey, label: '重要且紧急', icon: 'alert-octagon', color: '#EF4444', cls: 'q-important-urgent', tip: '立即处理 · 优先做' },
  { key: 'q2' as QKey, label: '重要不紧急', icon: 'star', color: '#3B6FE0', cls: 'q-important', tip: '制定计划 · 重点做' },
  { key: 'q3' as QKey, label: '紧急不重要', icon: 'zap', color: '#F59E0B', cls: 'q-urgent', tip: '尽量委托 · 快速做' },
  { key: 'q4' as QKey, label: '不重要不紧急', icon: 'minus-circle', color: '#94A3B8', cls: 'q-normal', tip: '减少投入 · 有空做' },
]

const grouped = computed(() => {
  const map: Record<QKey, TaskNode[]> = { q1: [], q2: [], q3: [], q4: [] }
  for (const t of tasks.value) {
    if (t.status === 1) continue // 已完成不进入象限
    if (t.important === 1 && t.urgent === 1) map.q1.push(t)
    else if (t.important === 1 && t.urgent !== 1) map.q2.push(t)
    else if (t.important !== 1 && t.urgent === 1) map.q3.push(t)
    else map.q4.push(t)
  }
  return map
})

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const all = await listTasks('all')
    tasks.value = all
      .map((n) => ({ ...n, children: [] as TaskNode[] }))
      .filter((n) => n.parentId === 0)
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleImportant(t: TaskNode) {
  const next = t.important === 1 ? 0 : 1
  t.important = next
  try {
    await updateTask(t.id, { important: next })
  } catch (e) {
    t.important = t.important === 1 ? 0 : 1
    notify((e as Error).message || '操作失败', 'error')
  }
}

async function toggleUrgent(t: TaskNode) {
  const next = t.urgent === 1 ? 0 : 1
  t.urgent = next
  try {
    await updateTask(t.id, { urgent: next })
  } catch (e) {
    t.urgent = t.urgent === 1 ? 0 : 1
    notify((e as Error).message || '操作失败', 'error')
  }
}

async function complete(t: TaskNode) {
  try {
    await setTaskStatus(t.id, 1)
    tasks.value = tasks.value.filter((x) => x.id !== t.id)
    notify('已完成', 'success')
  } catch (e) {
    notify((e as Error).message || '操作失败', 'error')
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

async function addInQuadrant(q: (typeof quadrants)[number]) {
  const v = prompt(`在「${q.label}」新建任务：`)
  if (!v || !v.trim()) return
  const important = q.key === 'q1' || q.key === 'q2' ? 1 : 0
  const urgent = q.key === 'q1' || q.key === 'q3' ? 1 : 0
  try {
    await createTask({ title: v.trim(), important, urgent })
    await reload()
    notify('已创建', 'success')
  } catch (e) {
    notify((e as Error).message || '创建失败', 'error')
  }
}

onMounted(reload)
</script>

<style scoped>
.qm-page { display: flex; flex-direction: column; height: 100%; }
.qm-head { margin-bottom: 14px; }
.qm-h2 { font-size: 18px; font-weight: 700; color: var(--kb-foreground); }
.qm-sub { font-size: 13px; color: var(--kb-muted-foreground); margin-top: 2px; }

.qm-loading, .qm-error {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 60px 20px; color: var(--kb-muted-foreground); font-size: 14px;
}
.qm-error { color: var(--kb-destructive); }
.qm-retry {
  margin-top: 4px; padding: 6px 16px; border-radius: 8px; border: none;
  background: var(--kb-primary); color: #fff; font-size: 13px; cursor: pointer;
}
.spin { animation: qm-spin 0.8s linear infinite; }
@keyframes qm-spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }

.qm-grid {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 12px;
  min-height: 0;
}

.qm-quad {
  display: flex;
  flex-direction: column;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  padding: 14px;
  min-height: 0;
  border-top: 3px solid var(--kb-border);
}
.qm-quad.q-important-urgent { border-top-color: #EF4444; }
.qm-quad.q-important { border-top-color: #3B6FE0; }
.qm-quad.q-urgent { border-top-color: #F59E0B; }
.qm-quad.q-normal { border-top-color: #94A3B8; }

.qm-quad-head { display: flex; align-items: center; justify-content: space-between; }
.qm-quad-title { display: flex; align-items: center; gap: 7px; font-size: 14.5px; font-weight: 600; color: var(--kb-foreground); }
.qm-quad-count {
  font-size: 11px; color: var(--kb-muted-foreground);
  background: var(--kb-muted); border-radius: 999px; min-width: 20px; text-align: center; padding: 1px 6px;
}
.qm-quad-tip { font-size: 11.5px; color: var(--kb-muted-foreground); margin: 3px 0 10px; }

.qm-cards { flex: 1 1 auto; overflow-y: auto; display: flex; flex-direction: column; gap: 7px; margin-bottom: 8px; }

.qm-card {
  display: flex; align-items: center; gap: 9px;
  padding: 8px 10px; border-radius: 9px;
  background: var(--kb-muted); border: 1px solid transparent;
  transition: background 0.12s ease, border-color 0.12s ease;
}
.qm-card:hover { border-color: var(--kb-border); }
.qm-card.done { opacity: 0.6; }

.qm-check {
  flex: 0 0 auto; width: 17px; height: 17px; border-radius: 50%;
  border: 1.8px solid var(--kb-border); background: var(--kb-card);
  cursor: pointer; display: inline-flex; align-items: center; justify-content: center; transition: all 0.15s ease;
}
.qm-check:hover { border-color: var(--kb-primary); }
.qm-check.done { background: var(--kb-primary); border-color: var(--kb-primary); }

.qm-card-body { flex: 1 1 auto; min-width: 0; }
.qm-card-title { font-size: 13.5px; color: var(--kb-foreground); line-height: 1.4; word-break: break-word; }
.qm-card-title.done { color: var(--kb-muted-foreground); text-decoration: line-through; }

.qm-card-flags { flex: 0 0 auto; display: flex; align-items: center; gap: 2px; }
.qm-flag {
  width: 24px; height: 24px; border: none; background: transparent;
  color: var(--kb-muted-foreground); border-radius: 6px; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center; transition: all 0.12s ease;
}
.qm-flag:hover { background: var(--kb-card); color: var(--kb-foreground); }
.qm-flag.on { color: #F59E0B; }
.qm-flag.del:hover { color: var(--kb-destructive); background: rgba(239, 68, 68, 0.1); }

.qm-empty { font-size: 12px; color: var(--kb-muted-foreground); text-align: center; padding: 16px 0; }

.qm-add {
  flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  width: 100%; padding: 7px; border-radius: 8px;
  border: 1px dashed var(--kb-border); background: transparent;
  color: var(--kb-muted-foreground); font-size: 12.5px; cursor: pointer; transition: all 0.12s ease;
}
.qm-add:hover { border-color: var(--kb-primary); color: var(--kb-primary); }

@media (max-width: 720px) {
  .qm-grid { grid-template-columns: 1fr; grid-template-rows: none; grid-auto-rows: minmax(220px, auto); }
}
</style>
