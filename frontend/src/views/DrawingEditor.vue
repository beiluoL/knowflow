<template>
  <div class="dw-root">
    <!-- ============ 侧边：流程图列表 ============ -->
    <aside class="dw-side">
      <div class="dw-side-head">
        <Icon name="pen-tool" :size="18" style="color: var(--kb-primary)" />
        <span class="dw-side-title">我的流程图</span>
      </div>
      <button class="dw-new-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" @click="newDrawing">
        <Icon name="file-plus" :size="16" /> 新建流程图
      </button>
      <div class="dw-list">
        <p v-if="loadingList" class="dw-hint">加载中…</p>
        <p v-else-if="drawings.length === 0" class="dw-hint">还没有流程图，点击上方按钮创建。</p>
        <button
          v-for="d in drawings"
          :key="d.id"
          class="dw-list-item focus-visible:out-0"
          :class="{ active: currentId === d.id }"
          @click="selectDrawing(d.id)"
        >
          <Icon name="pen-tool" :size="15" class="dw-list-ico" />
          <span class="dw-list-name">{{ d.title }}</span>
          <span class="dw-list-time">{{ formatTime(d.updateTime) }}</span>
          <span class="dw-list-del" title="删除" @click.stop="deleteDrawingItem(d.id)">
            <Icon name="trash-2" :size="14" />
          </span>
        </button>
      </div>
    </aside>

    <!-- ============ 主区：画布 ============ -->
    <main class="dw-main">
      <template v-if="currentId">
        <!-- 工具栏 -->
        <div class="dw-toolbar">
          <input
            v-model="title"
            class="dw-title focus-visible:outline-none"
            placeholder="流程图标题"
            @input="markDirty"
            @blur="save"
          />
          <div class="dw-tools">
            <button class="dw-tool-btn" title="添加节点" @click="addNode"><Icon name="plus" :size="16" /></button>
            <button class="dw-tool-btn" title="适应画布" @click="fitView()"><Icon name="maximize" :size="16" /></button>
            <span class="dw-tool-sep" />
            <button class="dw-tool-btn dw-save" :disabled="saving" title="保存" @click="save"><Icon name="save" :size="16" /></button>
          </div>
        </div>

        <!-- 画布 -->
        <div class="dw-canvas">
          <VueFlow
            v-model:nodes="nodes"
            v-model:edges="edges"
            :min-zoom="0.2"
            :max-zoom="3"
            fit-view-on-init
            :default-edge-options="{ type: 'smoothstep' }"
            @connect="onConnect"
            @node-drag-stop="markDirty"
            @nodes-change="markDirty"
            @edges-change="markDirty"
          >
            <template #node-flow="np">
              <div class="dw-node" :class="{ editing: editingId === np.id }">
                <Handle type="target" :position="Position.Left" />
                <input
                  v-if="editingId === np.id"
                  :value="np.data?.label"
                  class="dw-node-edit focus-visible:outline-none"
                  @mousedown.stop
                  @dblclick.stop
                  @blur="editingId = null"
                  @keyup.enter="editingId = null"
                  @input="onLabelInput(np.id, ($event.target as HTMLInputElement).value)"
                />
                <span v-else class="dw-node-label" @dblclick="editingId = np.id">{{ np.data?.label }}</span>
                <button class="dw-node-del" title="删除节点" @click.stop="removeNodeById(np.id)">
                  <Icon name="trash-2" :size="13" />
                </button>
                <Handle type="source" :position="Position.Right" />
              </div>
            </template>
            <Background :gap="22" :size="1.4" pattern-color="#e3e8f0" />
            <Controls />
          </VueFlow>

          <!-- 提示：空画布 -->
          <div v-if="nodes.length === 0" class="dw-empty">
            <Icon name="pen-tool" :size="40" style="color: #cbd5e1" />
            <p>画布为空。点击工具栏「＋」添加第一个节点，拖拽节点右侧圆点连线。</p>
          </div>
        </div>

        <!-- 状态栏 -->
        <div class="dw-status">
          <span v-if="saving" class="dw-sav-dot">保存中…</span>
          <span v-else-if="dirty" class="dw-dirty">● 未保存（已开启自动保存）</span>
          <span v-else class="dw-saved">✓ 已保存</span>
          <span class="dw-tip">双击节点可编辑文字 · 从节点右侧圆点拖到另一节点即可连线</span>
        </div>
      </template>

      <!-- 未选择任何图 -->
      <div v-else class="dw-blank">
        <Icon name="pen-tool" :size="48" style="color: #cbd5e1" />
        <h2>流程图</h2>
        <p>用节点和连线把流程画出来：拖拽添加、自由连线、双击编辑文字，数据自动保存。</p>
        <button class="dw-new-btn lg" @click="newDrawing"><Icon name="file-plus" :size="16" /> 新建第一个流程图</button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { VueFlow, useVueFlow, Position, Handle } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'
import { dialog } from '@/utils/dialog'
import {
  listDrawings,
  getDrawing,
  createDrawing,
  updateDrawing,
  deleteDrawing,
  type DrawingData,
  type DrawingSummary,
} from '@/api/drawing'

const { fitView } = useVueFlow()

// vue-flow 节点/连线在模板中走 v-model，类型用宽松数组避免 vue-flow 泛型过深实例化报错
const nodes = ref<any[]>([])
const edges = ref<any[]>([])

const drawings = ref<DrawingSummary[]>([])
const currentId = ref<number | null>(null)
const title = ref('')
const editingId = ref<string | null>(null)
const dirty = ref(false)
const saving = ref(false)
const loadingList = ref(false)

let saveTimer: number | undefined

function normalizeData(d: Partial<DrawingData> | undefined): DrawingData {
  const ns = Array.isArray(d?.nodes) ? d!.nodes : []
  const es = Array.isArray(d?.edges) ? d!.edges : []
  return {
    nodes: ns.map((n) => ({
      id: String(n.id),
      position: (n.position as { x: number; y: number }) || { x: 0, y: 0 },
      data: { label: String((n.data as { label?: unknown })?.label ?? '') },
      type: 'flow',
    })),
    edges: es.map((e) => ({ id: String(e.id), source: String(e.source), target: String(e.target) })),
  }
}

// ---------------- 列表 / 加载 ----------------
async function loadList() {
  loadingList.value = true
  try {
    drawings.value = await listDrawings()
  } catch (e: any) {
    notify(e?.message || '加载列表失败', 'error')
  } finally {
    loadingList.value = false
  }
}

async function selectDrawing(id: number) {
  if (dirty.value && currentId.value) await save()
  try {
    const d = await getDrawing(id)
    currentId.value = d.id
    title.value = d.title
    const dd = normalizeData(d.data)
    nodes.value = dd.nodes
    edges.value = dd.edges
    editingId.value = null
    dirty.value = false
    nextTick(() => fitView())
  } catch (e: any) {
    notify(e?.message || '加载失败', 'error')
  }
}

async function newDrawing() {
  if (dirty.value && currentId.value) await save()
  const startId = 'n' + Date.now()
  const initial: DrawingData = {
    nodes: [{ id: startId, position: { x: 160, y: 140 }, data: { label: '开始' }, type: 'flow' }],
    edges: [],
  }
  saving.value = true
  try {
    const id = await createDrawing({ title: '未命名流程图', type: 'flowchart', data: initial })
    currentId.value = id
    title.value = '未命名流程图'
    const dd = normalizeData(initial)
    nodes.value = dd.nodes
    edges.value = dd.edges
    editingId.value = startId
    dirty.value = false
    await loadList()
    nextTick(() => fitView())
  } catch (e: any) {
    notify(e?.message || '创建失败', 'error')
  } finally {
    saving.value = false
  }
}

async function deleteDrawingItem(id: number) {
  if (!(await dialog.confirm({ title: '删除确认', message: '确定删除该流程图？此操作不可恢复。', variant: 'danger' }))) return
  try {
    await deleteDrawing(id)
    notify('已删除', 'success')
    if (currentId.value === id) {
      currentId.value = null
      title.value = ''
      nodes.value = []
      edges.value = []
    }
    await loadList()
  } catch (e: any) {
    notify(e?.message || '删除失败', 'error')
  }
}

// ---------------- 画布交互 ----------------
function addNode() {
  const id = 'n' + Date.now() + Math.floor(Math.random() * 1000)
  const offset = nodes.value.length * 28
  nodes.value.push({
    id,
    position: { x: 220 + offset, y: 140 + offset },
    data: { label: '新节点' },
    type: 'flow',
  })
  editingId.value = id
  markDirty()
}

function onConnect(conn: { source: string; target: string }) {
  edges.value.push({
    id: 'e' + Date.now(),
    source: conn.source,
    target: conn.target,
  })
  markDirty()
}

function onLabelInput(id: string, val: string) {
  const n = nodes.value.find((x) => x.id === id)
  if (n) n.data = { ...(n.data as Record<string, unknown>), label: val }
  markDirty()
}

function removeNodeById(id: string) {
  nodes.value = nodes.value.filter((n) => n.id !== id)
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  if (editingId.value === id) editingId.value = null
  markDirty()
}

// ---------------- 保存 ----------------
function markDirty() {
  dirty.value = true
  if (saveTimer) clearTimeout(saveTimer)
  if (currentId.value) {
    saveTimer = window.setTimeout(() => {
      if (currentId.value && dirty.value) save()
    }, 1200)
  }
}

async function save() {
  if (!currentId.value || saving.value) return
  saving.value = true
  try {
    const data: DrawingData = {
      nodes: nodes.value.map((n) => ({
        id: String(n.id),
        position: (n.position as { x: number; y: number }) || { x: 0, y: 0 },
        data: { label: String((n.data as { label?: unknown })?.label ?? '') },
        type: 'flow',
      })),
      edges: edges.value.map((e) => ({
        id: String(e.id),
        source: String(e.source),
        target: String(e.target),
      })),
    }
    await updateDrawing(currentId.value, { title: title.value || '未命名流程图', data })
    dirty.value = false
    await loadList()
  } catch (e: any) {
    notify(e?.message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

function formatTime(t?: string): string {
  if (!t) return ''
  return t.slice(0, 10)
}

onMounted(loadList)
</script>

<style scoped>
.dw-root {
  display: flex;
  height: calc(100vh - 150px);
  min-height: 420px;
  background: #f7f8fa;
  gap: 12px;
  padding: 12px;
  box-sizing: border-box;
  overflow: hidden;
}
.dw-side {
  width: 264px;
  flex: 0 0 264px;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  padding: 14px;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.04);
}
.dw-side-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 12px;
}
.dw-new-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 9px 12px;
  border-radius: 10px;
  border: none;
  background: var(--kb-primary, #3b6fe0);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.dw-new-btn:hover {
  background: #2f5bc4;
}
.dw-new-btn.lg {
  width: auto;
  margin-top: 16px;
  padding: 11px 18px;
}
.dw-list {
  margin-top: 12px;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dw-hint {
  color: #98a2b3;
  font-size: 13px;
  padding: 8px 4px;
}
.dw-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: #f9fafb;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}
.dw-list-item:hover {
  background: #f1f4f9;
}
.dw-list-item.active {
  border-color: var(--kb-primary, #3b6fe0);
  background: #eef3fe;
}
.dw-list-ico {
  color: var(--kb-primary, #3b6fe0);
  flex: 0 0 auto;
}
.dw-list-name {
  flex: 1;
  font-size: 13px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dw-list-time {
  font-size: 11px;
  color: #98a2b3;
  flex: 0 0 auto;
}
.dw-list-del {
  flex: 0 0 auto;
  color: #98a2b3;
  display: none;
  padding: 2px;
  border-radius: 6px;
}
.dw-list-item:hover .dw-list-del {
  display: inline-flex;
}
.dw-list-del:hover {
  color: #ef4444;
  background: #fee2e2;
}

.dw-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.04);
}
.dw-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #eef0f3;
}
.dw-title {
  flex: 1;
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  border: none;
  padding: 6px 8px;
  border-radius: 8px;
  background: #f9fafb;
}
.dw-title:focus {
  background: #eef3fe;
}
.dw-tools {
  display: flex;
  align-items: center;
  gap: 4px;
}
.dw-tool-btn {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 9px;
  color: #475467;
  cursor: pointer;
  transition: all 0.15s;
}
.dw-tool-btn:hover {
  background: #f1f4f9;
  color: var(--kb-primary, #3b6fe0);
  border-color: #d6deeb;
}
.dw-tool-btn.dw-save {
  color: #fff;
  background: var(--kb-primary, #3b6fe0);
  border-color: var(--kb-primary, #3b6fe0);
}
.dw-tool-btn.dw-save:hover {
  background: #2f5bc4;
}
.dw-tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.dw-tool-sep {
  width: 1px;
  height: 22px;
  background: #e5e7eb;
  margin: 0 4px;
}

.dw-canvas {
  position: relative;
  flex: 1;
  overflow: hidden;
  background: #fbfcfe;
}
.dw-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #98a2b3;
  text-align: center;
  pointer-events: none;
  padding: 24px;
}

.dw-node {
  display: flex;
  align-items: center;
  min-width: 96px;
  max-width: 220px;
  padding: 8px 12px;
  box-sizing: border-box;
  background: #fff;
  border: 2px solid var(--kb-primary, #3b6fe0);
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.08);
  position: relative;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}
.dw-node.editing {
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.25);
}
.dw-node-label {
  flex: 1;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  user-select: none;
}
.dw-node-edit {
  flex: 1;
  width: 100%;
  border: none;
  outline: none;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  background: transparent;
  font-family: inherit;
}
.dw-node-del {
  position: absolute;
  top: -10px;
  right: -10px;
  width: 20px;
  height: 20px;
  display: none;
  align-items: center;
  justify-content: center;
  border: none;
  background: #fee2e2;
  color: #ef4444;
  border-radius: 50%;
  cursor: pointer;
}
.dw-node:hover .dw-node-del {
  display: inline-flex;
}

.dw-status {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 14px;
  border-top: 1px solid #eef0f3;
  font-size: 12px;
  color: #667085;
  background: #fbfcfe;
}
.dw-dirty {
  color: #f59e0b;
}
.dw-saved {
  color: #10b981;
}
.dw-sav-dot {
  color: #3b6fe0;
}
.dw-tip {
  margin-left: auto;
  color: #98a2b3;
}

.dw-blank {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #98a2b3;
  text-align: center;
  padding: 24px;
}
.dw-blank h2 {
  color: #1f2937;
  font-size: 20px;
  margin: 0;
}
.dw-blank p {
  max-width: 360px;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}
</style>
