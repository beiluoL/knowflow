<template>
  <MiniAppShell title="绘图" back-label="返回" @back="goBack">
    <template #actions><button class="dw-directory-toggle" :aria-expanded="sidebarOpen" @click="sidebarOpen = !sidebarOpen"><Icon name="list" :size="18" />目录</button></template>
    <div class="dw-root" @keydown.esc="sidebarOpen = false" :class="{ 'side-open': sidebarOpen }">
    <!-- ============ 侧边：流程图列表 ============ -->
    <aside class="dw-side" aria-label="我的流程图">
      <div class="dw-side-head">
        <Icon name="pen-tool" :size="18" style="color: var(--kb-primary)" />
        <span class="dw-side-title">我的流程图</span>
      </div>
      <button :disabled="saving" :class="{ secondary: drawings.length === 0 }" class="dw-new-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" @click="newDrawing">
        <Icon name="file-plus" :size="16" /> 新建流程图
      </button>
      <div class="dw-list">
        <p v-if="loadingList" class="dw-hint">加载中…</p>
        <div v-else-if="listError" role="alert" class="dw-hint">列表加载失败 <button @click="loadList">重试</button></div>
        <p v-else-if="drawings.length === 0" class="dw-hint">暂无流程图</p>
        <div class="dw-list-row"
          v-for="d in drawings"
          :key="d.id"
        ><button
          class="dw-list-item focus-visible:out-0"
          :class="{ active: currentId === d.id }"
          @click="selectDrawing(d.id)"
        >
          <Icon name="pen-tool" :size="15" class="dw-list-ico" />
          <span class="dw-list-name">{{ d.title }}</span>
          <span class="dw-list-time">{{ formatTime(d.updateTime) }}</span>
          </button><button class="dw-list-del" :aria-label="'删除' + d.title" title="删除" @click.stop="deleteDrawingItem(d.id)">
            <Icon name="trash-2" :size="14" />
          </button>
        </div>
      </div>
    </aside>

    <!-- ============ 主区：画布 ============ -->
    <main class="dw-main">
      <template v-if="currentId">
        <!-- 工具栏 -->
        <div class="dw-toolbar">
          <input
            v-model="title"
            class="dw-title kb-input kb-form-control"
            placeholder="流程图标题"
            aria-label="流程图标题"
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
                  class="dw-node-edit kb-input kb-form-control"
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
            <Icon name="pen-tool" :size="40" style="color: var(--kb-muted-foreground)" />
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
        <Icon name="pen-tool" :size="48" style="color: var(--kb-muted-foreground)" />
        <h2>流程图</h2>
        <p>用节点和连线把流程画出来：拖拽添加、自由连线、双击编辑文字，数据自动保存。</p>
        <button v-if="!drawings.length" :disabled="saving || loadingList || listError" class="dw-new-btn lg" @click="newDrawing"><Icon name="file-plus" :size="16" /> 新建第一个流程图</button>
      </div>
    </main>
  </div>
</MiniAppShell>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
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
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'

const router = useRouter()
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/learning/center')
}

const flow = useVueFlow()
function fitView() { void flow.fitView({ maxZoom: 1, padding: 0.2 }) }

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
const listError = ref(false)
const sidebarOpen = ref(false)

let saveTimer: number | undefined
let revision = 0

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
  listError.value = false
  try {
    drawings.value = await listDrawings()
  } catch (e: any) {
    listError.value = true
    notify(e?.message || '加载列表失败', 'error')
  } finally {
    loadingList.value = false
  }
}

async function selectDrawing(id: number) {
  if (saving.value) { notify('正在保存，请稍后再试', 'info'); return }
  if (dirty.value && currentId.value) { await save(); if (dirty.value) return }
  try {
    const d = await getDrawing(id)
    sidebarOpen.value = false
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
  if (saving.value) { notify('正在保存，请稍后再试', 'info'); return }
  if (dirty.value && currentId.value) { await save(); if (dirty.value) return }
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
  if (saving.value) { notify('正在保存，请稍后再试', 'info'); return }
  await dialog.confirm({ "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", title: '删除确认', message: '确定删除该流程图？此操作不可恢复。', variant: 'danger', onConfirm: async () => {
          try {
              if (saving.value) {
                  notify('正在保存，请稍后再试', 'info');
                  return;
              }
              if (currentId.value === id && saveTimer)
                  clearTimeout(saveTimer);
              await deleteDrawing(id);
              notify('已删除', 'success');
              if (currentId.value === id) {
                  if (saveTimer)
                      clearTimeout(saveTimer);
                  dirty.value = false;
                  editingId.value = null;
                  currentId.value = null;
                  title.value = '';
                  nodes.value = [];
                  edges.value = [];
              }
              await loadList();
          }
          catch (e: any) {
              notify(e?.message || '删除失败', 'error');
              throw e;
          }
      } });
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
  revision += 1
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
  if (saveTimer) clearTimeout(saveTimer)
  const savedRevision = revision
  let success = false
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
    success = true
    dirty.value = revision !== savedRevision
    await loadList()
  } catch (e: any) {
    notify(e?.message || '保存失败', 'error')
  } finally {
    saving.value = false
    if (success && dirty.value) markDirty()
  }
}

function formatTime(t?: string): string {
  if (!t) return ''
  return t.slice(0, 10)
}

onBeforeRouteLeave(async () => {
  if (saving.value) { notify('正在保存，请稍后再试', 'info'); return false }
  if (dirty.value) await save()
  return !dirty.value
})
onBeforeUnmount(() => { if (saveTimer) clearTimeout(saveTimer) })
onMounted(loadList)
</script>

<style scoped>
.dw-root {
  display: flex;
  height: calc(100dvh - var(--kb-nav-height) - var(--kb-space-section) - var(--kb-space-section));
  min-height: 0;
  background: var(--kb-background);
  gap: var(--kb-space-3);
  padding: var(--kb-space-3);
  box-sizing: border-box;
  overflow: hidden;
}
.dw-side {
  width: var(--kb-canvas-list-width);
  flex: 0 0 var(--kb-canvas-list-width);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  display: flex;
  flex-direction: column;
  padding: var(--kb-space-4);
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.04);
}
.dw-side-head {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  font-weight: 700;
  color: var(--kb-foreground);
  margin-bottom: var(--kb-space-3);
}
.dw-new-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 9px 12px;
  border-radius: var(--kb-radius-md);
  border: none;
  background: var(--kb-primary, var(--kb-primary));
  color: var(--kb-card);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.dw-new-btn:hover {
  background: var(--kb-primary-hover);
}
.dw-new-btn.lg {
  width: auto;
  margin-top: 16px;
  padding: 11px 18px;
}
.dw-list {
  min-height: 0;
  margin-top: 12px;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dw-hint {
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  padding: 8px 4px;
}
.dw-list-item {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  padding: 9px 10px;
  border-radius: var(--kb-radius-md);
  border: 1px solid transparent;
  background: var(--kb-surface-2);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}
.dw-list-item:hover {
  background: var(--kb-muted);
}
.dw-list-item.active {
  border-color: var(--kb-primary, var(--kb-primary));
  background: var(--kb-primary-soft);
}
.dw-list-ico {
  color: var(--kb-primary, var(--kb-primary));
  flex: 0 0 auto;
}
.dw-list-name {
  flex: 1;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dw-list-time {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  flex: 0 0 auto;
}
.dw-list-del {
  flex: 0 0 auto;
  color: var(--kb-muted-foreground);
  display: none;
  padding: 2px;
  border-radius: 6px;
}
.dw-list-item:hover .dw-list-del {
  display: inline-flex;
}
.dw-list-del:hover {
  color: var(--kb-danger);
  background: var(--kb-danger-soft-strong);
}

.dw-main {
  position: relative;
  min-width: 0;
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.04);
}
.dw-toolbar {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3);
  padding: 10px 14px;
  border-bottom: 1px solid var(--kb-border);
}
.dw-title:not(.kb-form-control) {
  border: none;
  border-radius: 8px;
  background: var(--kb-surface-2);
}
.dw-title {
  min-width: 0;
  width: 0;
  flex: 1;
  font-size: 16px;
  font-weight: 700;
  color: var(--kb-foreground);
  padding: 6px 8px;
}
.dw-title:not(.kb-form-control):focus {
  background: var(--kb-primary-soft);
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
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  border-radius: 9px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s;
}
.dw-tool-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary, var(--kb-primary));
  border-color: var(--kb-border);
}
.dw-tool-btn.dw-save {
  color: var(--kb-card);
  background: var(--kb-primary, var(--kb-primary));
  border-color: var(--kb-primary, var(--kb-primary));
}
.dw-tool-btn.dw-save:hover {
  background: var(--kb-primary-hover);
}
.dw-tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.dw-tool-sep {
  width: 1px;
  height: 22px;
  background: var(--kb-border);
  margin: 0 4px;
}

.dw-canvas {
  min-height: 0;
  position: relative;
  flex: 1;
  overflow: hidden;
  background: var(--kb-background);
}
.dw-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--kb-muted-foreground);
  text-align: center;
  pointer-events: none;
  padding: var(--kb-space-6);
}

.dw-node {
  display: flex;
  align-items: center;
  min-width: 96px;
  max-width: 220px;
  padding: 8px 12px;
  box-sizing: border-box;
  background: var(--kb-card);
  border: 2px solid var(--kb-primary, var(--kb-primary));
  border-radius: var(--kb-radius-md);
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.08);
  position: relative;
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
  color: var(--kb-foreground);
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
.dw-node-edit:not(.kb-form-control) {
  border: none;
  outline: none;
  background: transparent;
}
.dw-node-edit {
  flex: 1;
  width: 100%;
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
  color: var(--kb-foreground);
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
  background: var(--kb-danger-soft-strong);
  color: var(--kb-danger);
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
  border-top: 1px solid var(--kb-border);
  font-size: 12px;
  color: var(--kb-muted-foreground);
  background: var(--kb-background);
}
.dw-dirty {
  color: var(--kb-warning);
}
.dw-saved {
  color: var(--kb-success);
}
.dw-sav-dot {
  color: var(--kb-primary);
}
.dw-tip {
  margin-left: auto;
  color: var(--kb-muted-foreground);
}

.dw-blank {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--kb-muted-foreground);
  text-align: center;
  padding: var(--kb-space-6);
}
.dw-blank h2 {
  color: var(--kb-foreground);
  font-size: var(--kb-fs-h4);
  margin: 0;
}
.dw-blank p {
  max-width: 360px;
  font-size: var(--kb-fs-body-md);
  line-height: 1.6;
  margin: 0;
}

.dw-new-btn.secondary { background: var(--kb-surface-2); color: var(--kb-foreground); border: 1px solid var(--kb-border); }
.dw-new-btn:disabled { opacity: .5; cursor: not-allowed; }
.dw-side { min-width: 0; }
.dw-blank { position: static; flex: 1; min-height: 0; gap: var(--kb-space-4); }
.dw-blank h2 { max-width: 100%; }
.dw-list-item { min-width: 0; flex-shrink: 0; }
.dw-tools, .dw-status { flex-wrap: wrap; }
.dw-directory-toggle { display: none; align-items: center; gap: var(--kb-space-2); height: var(--kb-btn-height-sm); padding: 0 var(--kb-space-3); background: var(--kb-card); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-sm); }
.dw-list-item:focus-within .dw-list-del { display: inline-flex; }
@media (max-width: 1100px) {
 .dw-directory-toggle { display: inline-flex; }
 .dw-root { position: relative; padding: 0; height: calc(100dvh - var(--kb-nav-height) - var(--kb-space-section) - var(--kb-space-section) - var(--kb-space-section)); }
 .dw-side { display: none; }
 .dw-root.side-open .dw-side { display: flex; position: absolute; z-index: 5; inset: 0 auto 0 0; max-width: 85%; box-shadow: var(--kb-shadow-lg); }
 .dw-toolbar { flex-wrap: wrap; }
 .dw-title { flex-basis: 100%; }
}
.dw-list-row { display: flex; align-items: center; gap: var(--kb-space-1); }
.dw-list-item { flex: 1; }
.dw-list-del { display: inline-flex; background: transparent; border: 0; }
.dw-root button:focus-visible { outline: 2px solid var(--kb-ring); outline-offset: 2px; }
</style>
