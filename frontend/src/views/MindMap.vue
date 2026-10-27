<template>
  <MiniAppShell title="思维导图" back-label="返回" @back="goBack">
    <template #actions><button class="mm-directory-toggle" :aria-expanded="sidebarOpen" @click="sidebarOpen = !sidebarOpen"><Icon name="list" :size="18" />目录</button></template>
    <div class="mm-root" @keydown.esc="sidebarOpen = false" :class="{ 'side-open': sidebarOpen }">
    <!-- ============ 侧边：思维导图列表 ============ -->
    <aside class="mm-side" aria-label="我的思维导图">
      <div class="mm-side-head">
        <Icon name="git-branch" :size="18" style="color: var(--kb-primary)" />
        <span class="mm-side-title">我的思维导图</span>
      </div>
      <button :disabled="saving" :class="{ secondary: maps.length === 0 }" class="mm-new-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" @click="newMap">
        <Icon name="file-plus" :size="16" /> 新建思维导图
      </button>
      <div class="mm-list">
        <p v-if="loadingList" class="mm-hint">加载中…</p>
        <div v-else-if="listError" role="alert" class="mm-hint">列表加载失败 <button @click="loadList">重试</button></div>
        <p v-else-if="maps.length === 0" class="mm-hint">暂无思维导图</p>
        <div class="mm-list-row"
          v-for="m in maps"
          :key="m.id"
        ><button
          class="mm-list-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)]"
          :class="{ active: currentId === m.id }"
          @click="selectMap(m.id)"
        >
          <Icon name="git-branch" :size="15" class="mm-list-ico" />
          <span class="mm-list-name">{{ m.title }}</span>
          <span class="mm-list-time">{{ formatTime(m.updateTime) }}</span>
          </button><button class="mm-list-del" :aria-label="'删除' + m.title" title="删除" @click.stop="deleteMap(m.id)">
            <Icon name="trash-2" :size="14" />
          </button>
        </div>
      </div>
    </aside>

    <!-- ============ 主区：画布 ============ -->
    <main class="mm-main">
      <template v-if="currentId">
        <!-- 工具栏 -->
        <div class="mm-toolbar">
          <input
            v-model="title"
            class="mm-title kb-input kb-form-control"
            placeholder="思维导图标题"
            aria-label="思维导图标题"
            @input="markDirty"
            @blur="save"
          />
          <div class="mm-tools">
            <button class="mm-tool-btn" title="放大" @click="zoomBy(1.2)"><Icon name="zoom-in" :size="16" /></button>
            <button class="mm-tool-btn" title="缩小" @click="zoomBy(1 / 1.2)"><Icon name="zoom-out" :size="16" /></button>
            <button class="mm-tool-btn" title="适应画布" @click="fitView"><Icon name="maximize" :size="16" /></button>
            <button class="mm-tool-btn" title="自动布局（层级整理）" @click="autoLayout"><Icon name="list-tree" :size="16" /></button>
            <button class="mm-tool-btn" :class="{ active: connectMode }" title="连线模式：连接任意两个节点" @click="startConnect"><Icon name="git-branch" :size="16" /></button>
            <button class="mm-tool-btn" title="添加根主题" @click="addRoot"><Icon name="plus" :size="16" /></button>
            <span class="mm-tool-sep" />
            <button class="mm-tool-btn mm-save" :disabled="saving" title="保存" @click="save"><Icon name="save" :size="16" /></button>
          </div>
        </div>

        <!-- 画布 -->
        <div class="mm-canvas">
          <svg
            ref="svgRef"
            class="mm-svg"
            @wheel.prevent="onWheel"
            @pointerdown="onCanvasDown"
          >
            <g :transform="`translate(${data.view.tx},${data.view.ty}) scale(${data.view.scale})`">
              <!-- 连线（层级 + 自由连接） -->
              <path
                v-for="e in visibleEdges"
                :key="e.id"
                class="mm-edge"
                :class="{ 'mm-edge-free': !e.hierarchy, 'mm-edge-hl': connectMode && e.source === connectFrom }"
                :d="edgePath(e)"
                @click.stop="connectMode && deleteEdge(e.id)"
              />
              <!-- 节点 -->
              <foreignObject
                v-for="n in visibleNodes"
                :key="n.id"
                :x="n.x"
                :y="n.y"
                :width="NODE_W"
                :height="NODE_H"
                class="mm-fo"
              >
                <div
                  xmlns="http://www.w3.org/1999/xhtml"
                  class="mm-card focus-visible:outline-none"
                  :class="{ selected: selectedId === n.id, 'is-source': connectMode && connectFrom === n.id }"
                  :style="{ borderColor: n.color || 'var(--kb-primary)' }"
                  tabindex="0"
                  @pointerdown="onNodeDown(n, $event)"
                  @dblclick="onNodeDblClick(n)"
                  @keydown.enter.prevent="onNodeDblClick(n)"
                  :aria-label="n.text"
                >
                  <textarea
                    v-if="editingId === n.id"
                    :id="'mm-edit-' + n.id"
                    class="mm-edit kb-textarea kb-form-control"
                    v-model="n.text"
                    @input="markDirty"
                    @pointerdown.stop
                    @dblclick.stop
                    @keydown.enter="!$event.isComposing && ($event.preventDefault(), $event.stopPropagation(), commitText(n.id, ($event.target as HTMLTextAreaElement).value))"
                    @blur="commitText(n.id, ($event.target as HTMLTextAreaElement).value)"
                  />
                  <span v-else class="mm-card-text">{{ n.text }}</span>

                  <div class="mm-card-tools" @pointerdown.stop>
                    <button class="mm-card-btn" title="添加子节点" @click.stop="addChild(n.id)"><Icon name="plus" :size="13" /></button>
                    <button
                      v-if="hasChildren(n)"
                      class="mm-card-btn"
                      :title="n.collapsed ? '展开分支' : '折叠分支'"
                      @click.stop="toggleCollapse(n.id)"
                    ><Icon :name="n.collapsed ? 'chevron-right' : 'chevron-down'" :size="13" /></button>
                    <button class="mm-card-btn mm-danger" title="删除节点" @click.stop="deleteNode(n.id)"><Icon name="trash-2" :size="13" /></button>
                  </div>
                </div>
              </foreignObject>
            </g>
          </svg>

          <!-- 提示：空画布 -->
          <div v-if="visibleNodes.length === 0" class="mm-empty">
            <Icon name="git-branch" :size="40" style="color: var(--kb-muted-foreground)" />
            <p>画布为空。点击工具栏「＋」添加根主题，或双击节点编辑文字。</p>
          </div>
        </div>

        <!-- 状态栏 -->
        <div class="mm-status">
          <span>缩放 {{ Math.round(data.view.scale * 100) }}%</span>
          <span v-if="saving" class="mm-sav-dot">保存中…</span>
          <span v-else-if="dirty" class="mm-dirty">● 未保存（已开启自动保存）</span>
          <span v-else class="mm-saved">✓ 已保存</span>
          <span v-if="connectMode" class="mm-connect-hint">
            {{ connectFrom ? '已选起点：点击目标节点完成连线，点击空白处取消' : '连线模式：点击起始节点' }}
          </span>
        </div>
      </template>

      <!-- 未选择任何图 -->
      <div v-else class="mm-blank">
        <Icon name="git-branch" :size="48" style="color: var(--kb-muted-foreground)" />
        <h2>{{ maps.length ? '选择一张思维导图' : '把知识整理成思维导图' }}</h2>
        <p>从一个中心主题开始，添加分支节点，建立知识之间的联系。</p>
        <button v-if="!maps.length" :disabled="saving || loadingList || listError" class="mm-new-btn lg" @click="newMap"><Icon name="file-plus" :size="16" /> 新建思维导图</button>
      </div>
    </main>
  </div>
</MiniAppShell>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'
import { dialog } from '@/utils/dialog'
import {
  listMindMaps,
  getMindMap,
  createMindMap,
  updateMindMap,
  deleteMindMap,
  type MindMapData,
  type MindMapNode,
  type MindMapEdge,
  type MindMapSummary,
} from '@/api/mindmap'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'

const router = useRouter()
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/learning/center')
}

const NODE_W = 184
const NODE_H = 48
const PALETTE = ['var(--kb-primary)', '#8B5CF6', 'var(--kb-success)', 'var(--kb-warning)', 'var(--kb-danger)', '#0EA5E9']

interface RenderedEdge extends MindMapEdge {
  hierarchy?: boolean
}

const maps = ref<MindMapSummary[]>([])
const currentId = ref<number | null>(null)
const title = ref('')
const data = ref<MindMapData>(emptyData())
const selectedId = ref<string | null>(null)
const editingId = ref<string | null>(null)
const connectMode = ref(false)
const connectFrom = ref<string | null>(null)
const dirty = ref(false)
const saving = ref(false)
const loadingList = ref(false)
const listError = ref(false)
const sidebarOpen = ref(false)
const svgRef = ref<SVGSVGElement | null>(null)

let saveTimer: number | undefined
let revision = 0

function emptyData(): MindMapData {
  return { nodes: [], edges: [], view: { scale: 1, tx: 0, ty: 0 } }
}

function normalizeData(d: Partial<MindMapData> | undefined): MindMapData {
  return {
    nodes: Array.isArray(d?.nodes) ? d!.nodes.map((n) => ({ ...n, parentId: n.parentId ?? null, collapsed: !!n.collapsed })) : [],
    edges: Array.isArray(d?.edges) ? d!.edges : [],
    view: d?.view ? { scale: d.view.scale || 1, tx: d.view.tx || 0, ty: d.view.ty || 0 } : { scale: 1, tx: 0, ty: 0 },
  }
}

// ---------------- 列表 / 加载 ----------------
async function loadList() {
  loadingList.value = true
  listError.value = false
  try {
    maps.value = await listMindMaps()
  } catch (e: any) {
    listError.value = true
    notify(e?.message || '加载列表失败', 'error')
  } finally {
    loadingList.value = false
  }
}

async function selectMap(id: number) {
  if (saving.value) { notify('正在保存，请稍后再试', 'info'); return }
  if (dirty.value && currentId.value) { await save(); if (dirty.value) return }
  try {
    const d = await getMindMap(id)
    sidebarOpen.value = false
    currentId.value = d.id
    title.value = d.title
    data.value = normalizeData(d.data)
    selectedId.value = null
    editingId.value = null
    connectFrom.value = null
    connectMode.value = false
    dirty.value = false
    nextTick(fitView)
  } catch (e: any) {
    notify(e?.message || '加载失败', 'error')
  }
}

async function newMap() {
  if (saving.value) { notify('正在保存，请稍后再试', 'info'); return }
  if (dirty.value && currentId.value) { await save(); if (dirty.value) return }
  const rootId = 'n' + Date.now()
  const initial: MindMapData = {
    nodes: [{ id: rootId, text: '中心主题', x: 0, y: 0, parentId: null, collapsed: false, color: PALETTE[0] }],
    edges: [],
    view: { scale: 1, tx: 0, ty: 0 },
  }
  saving.value = true
  try {
    const id = await createMindMap({ title: '未命名思维导图', data: initial })
    title.value = '未命名思维导图'
    data.value = initial
    currentId.value = id
    selectedId.value = rootId
    dirty.value = false
    await loadList()
    nextTick(fitView)
  } catch (e: any) {
    notify(e?.message || '创建失败', 'error')
  } finally {
    saving.value = false
  }
}

async function deleteMap(id: number) {
  if (saving.value) { notify('正在保存，请稍后再试', 'info'); return }
  await dialog.confirm({ "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", title: '删除确认', message: '确定删除该思维导图？此操作不可恢复。', variant: 'danger', onConfirm: async () => {
          try {
              if (saving.value) {
                  notify('正在保存，请稍后再试', 'info');
                  return;
              }
              if (currentId.value === id && saveTimer)
                  clearTimeout(saveTimer);
              await deleteMindMap(id);
              notify('已删除', 'success');
              if (currentId.value === id) {
                  if (saveTimer)
                      clearTimeout(saveTimer);
                  dirty.value = false;
                  editingId.value = null;
                  currentId.value = null;
                  title.value = '';
                  data.value = emptyData();
              }
              await loadList();
          }
          catch (e: any) {
              notify(e?.message || '删除失败', 'error');
              throw e;
          }
      } });
}

// ---------------- 节点操作 ----------------
function nodeById(id: string): MindMapNode | undefined {
  return data.value.nodes.find((n) => n.id === id)
}
function childrenOf(id: string): MindMapNode[] {
  return data.value.nodes.filter((n) => n.parentId === id)
}
function hasChildren(n: MindMapNode): boolean {
  return data.value.nodes.some((c) => c.parentId === n.id)
}

function addChild(parentId: string) {
  const p = nodeById(parentId)
  if (!p) return
  const sibs = childrenOf(parentId)
  const id = 'n' + Date.now() + Math.floor(Math.random() * 1000)
  const node: MindMapNode = {
    id,
    text: '新节点',
    x: p.x + 240,
    y: p.y + sibs.length * 64,
    parentId,
    collapsed: false,
    color: p.color || PALETTE[0],
  }
  data.value.nodes.push(node)
  selectedId.value = id
  editingId.value = id
  markDirty()
  nextTick(() => {
    const el = document.getElementById('mm-edit-' + id) as HTMLTextAreaElement | null
    el?.focus()
    el?.select()
  })
}

function addRoot() {
  const id = 'n' + Date.now() + Math.floor(Math.random() * 1000)
  const offset = data.value.nodes.filter((n) => !n.parentId).length
  const node: MindMapNode = {
    id,
    text: '新主题',
    x: 0,
    y: offset * 120,
    parentId: null,
    collapsed: false,
    color: PALETTE[data.value.nodes.length % PALETTE.length],
  }
  data.value.nodes.push(node)
  selectedId.value = id
  editingId.value = id
  markDirty()
  nextTick(() => {
    const el = document.getElementById('mm-edit-' + id) as HTMLTextAreaElement | null
    el?.focus()
    el?.select()
  })
}

async function deleteNode(id: string) {
  const node = nodeById(id)
  if (!node) return
  if (!(await dialog.confirm({"confirmText":"确认删除","cancelText":"取消","icon":"trash-2", title: '删除确认', message: '删除该节点及其所有子节点？', variant: 'danger' }))) return
  const toRemove = new Set<string>([id])
  let changed = true
  while (changed) {
    changed = false
    for (const n of data.value.nodes) {
      if (n.parentId && toRemove.has(n.parentId) && !toRemove.has(n.id)) {
        toRemove.add(n.id)
        changed = true
      }
    }
  }
  data.value.nodes = data.value.nodes.filter((n) => !toRemove.has(n.id))
  data.value.edges = data.value.edges.filter((e) => !toRemove.has(e.source) && !toRemove.has(e.target))
  if (selectedId.value && toRemove.has(selectedId.value)) selectedId.value = null
  if (editingId.value && toRemove.has(editingId.value)) editingId.value = null
  markDirty()
}

function commitText(id: string, text: string) {
  const n = nodeById(id)
  if (n) {
    n.text = text
    markDirty()
  }
  editingId.value = null
}

function toggleCollapse(id: string) {
  const n = nodeById(id)
  if (n) {
    n.collapsed = !n.collapsed
    markDirty()
  }
}

// ---------------- 可见性 / 连线 ----------------
function isHidden(id: string): boolean {
  let cur = nodeById(id)
  let p = cur?.parentId ?? null
  while (p) {
    const pn = nodeById(p)
    if (!pn) break
    if (pn.collapsed) return true
    p = pn.parentId
  }
  return false
}

const visibleNodes = computed(() => data.value.nodes.filter((n) => !isHidden(n.id)))

const visibleEdges = computed<RenderedEdge[]>(() => {
  const edges: RenderedEdge[] = []
  for (const n of data.value.nodes) {
    if (n.parentId && !isHidden(n.id)) {
      const p = nodeById(n.parentId)
      if (p && !isHidden(p.id)) {
        edges.push({ id: 'h-' + n.id, source: n.parentId, target: n.id, hierarchy: true })
      }
    }
  }
  for (const e of data.value.edges) {
    if (!isHidden(e.source) && !isHidden(e.target)) edges.push(e)
  }
  return edges
})

function edgePath(e: RenderedEdge): string {
  const s = nodeById(e.source)
  const t = nodeById(e.target)
  if (!s || !t) return ''
  const x1 = s.x + NODE_W
  const y1 = s.y + NODE_H / 2
  const x2 = t.x
  const y2 = t.y + NODE_H / 2
  const dx = Math.max(40, Math.abs(x2 - x1) / 2)
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
}

// ---------------- 连线模式 ----------------
function startConnect() {
  connectMode.value = true
  connectFrom.value = null
  notify('连线模式：点击起始节点，再点击目标节点', 'info')
}
function onNodeClickConnect(id: string) {
  if (!connectFrom.value) {
    connectFrom.value = id
    return
  }
  if (connectFrom.value === id) {
    connectFrom.value = null
    return
  }
  const exists = data.value.edges.some((e) => e.source === connectFrom.value && e.target === id)
  if (!exists) {
    data.value.edges.push({ id: 'e' + Date.now(), source: connectFrom.value, target: id })
    markDirty()
  }
  connectFrom.value = null
}
function deleteEdge(id: string) {
  data.value.edges = data.value.edges.filter((e) => e.id !== id)
  markDirty()
}

// ---------------- 拖拽 / 平移 / 缩放 ----------------
function onNodeDown(n: MindMapNode, ev: PointerEvent) {
  ev.stopPropagation()
  if (editingId.value) return
  if (connectMode.value) {
    onNodeClickConnect(n.id)
    return
  }
  selectedId.value = n.id
  const startX = ev.clientX
  const startY = ev.clientY
  const ox = n.x
  const oy = n.y
  const scale = data.value.view.scale
  const move = (e: PointerEvent) => {
    n.x = ox + (e.clientX - startX) / scale
    n.y = oy + (e.clientY - startY) / scale
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    markDirty()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function onCanvasDown(ev: PointerEvent) {
  if (connectMode.value) {
    connectFrom.value = null
    return
  }
  selectedId.value = null
  const startX = ev.clientX
  const startY = ev.clientY
  const otx = data.value.view.tx
  const oty = data.value.view.ty
  const move = (e: PointerEvent) => {
    data.value.view.tx = otx + (e.clientX - startX)
    data.value.view.ty = oty + (e.clientY - startY)
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    markDirty()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function onWheel(ev: WheelEvent) {
  const svg = svgRef.value
  if (!svg) return
  const rect = svg.getBoundingClientRect()
  const sx = ev.clientX - rect.left
  const sy = ev.clientY - rect.top
  const v = data.value.view
  const wx = (sx - v.tx) / v.scale
  const wy = (sy - v.ty) / v.scale
  const factor = ev.deltaY < 0 ? 1.1 : 1 / 1.1
  const ns = Math.min(3, Math.max(0.2, v.scale * factor))
  v.tx = sx - wx * ns
  v.ty = sy - wy * ns
  v.scale = ns
  markDirty()
}

function zoomBy(factor: number) {
  const svg = svgRef.value
  if (!svg) return
  const rect = svg.getBoundingClientRect()
  const sx = rect.width / 2
  const sy = rect.height / 2
  const v = data.value.view
  const wx = (sx - v.tx) / v.scale
  const wy = (sy - v.ty) / v.scale
  const ns = Math.min(3, Math.max(0.2, v.scale * factor))
  v.tx = sx - wx * ns
  v.ty = sy - wy * ns
  v.scale = ns
  markDirty()
}

function fitView() {
  const ns = visibleNodes.value
  const svg = svgRef.value
  if (ns.length === 0 || !svg) return
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const n of ns) {
    minX = Math.min(minX, n.x)
    minY = Math.min(minY, n.y)
    maxX = Math.max(maxX, n.x + NODE_W)
    maxY = Math.max(maxY, n.y + NODE_H)
  }
  const rect = svg.getBoundingClientRect()
  const pad = 60
  const cw = rect.width
  const ch = rect.height
  const bw = Math.max(1, maxX - minX)
  const bh = Math.max(1, maxY - minY)
  const scale = Math.min(1, Math.max(0.2, Math.min((cw - pad * 2) / bw, (ch - pad * 2) / bh)))
  data.value.view.scale = scale
  data.value.view.tx = (cw - bw * scale) / 2 - minX * scale
  data.value.view.ty = (ch - bh * scale) / 2 - minY * scale
}

function autoLayout() {
  const childrenMap: Record<string, MindMapNode[]> = {}
  for (const n of data.value.nodes) {
    if (n.parentId) (childrenMap[n.parentId] ||= []).push(n)
  }
  let cursorY = 0
  const LEVEL_X = 240
  const GAP_Y = 72
  const place = (node: MindMapNode, depth: number) => {
    node.x = depth * LEVEL_X
    const kids = (childrenMap[node.id] || []).filter((k) => !isHidden(k.id))
    if (kids.length === 0) {
      node.y = cursorY
      cursorY += GAP_Y
    } else {
      kids.forEach((k) => place(k, depth + 1))
      node.y = (kids[0].y + kids[kids.length - 1].y) / 2
    }
  }
  data.value.nodes.filter((n) => !n.parentId).forEach((r) => place(r, 0))
  markDirty()
  nextTick(fitView)
}

function onNodeDblClick(n: MindMapNode) {
  editingId.value = n.id
  nextTick(() => {
    const el = document.getElementById('mm-edit-' + n.id) as HTMLTextAreaElement | null
    el?.focus()
    el?.select()
  })
}

// ---------------- 保存 ----------------
function markDirty() {
  revision += 1
  dirty.value = true
  if (saveTimer) clearTimeout(saveTimer)
  if (currentId.value) {
    saveTimer = window.setTimeout(() => {
      if (currentId.value && dirty.value) save()
    }, 1000)
  }
}

async function save() {
  if (!currentId.value || saving.value) return
  if (saveTimer) clearTimeout(saveTimer)
  const savedRevision = revision
  const savedData = JSON.parse(JSON.stringify(data.value)) as MindMapData
  let success = false
  saving.value = true
  try {
    await updateMindMap(currentId.value, { title: title.value || '未命名思维导图', data: savedData })
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
.mm-root {
  display: flex;
  /* 全屏页外壳：导航 pt-14(56px) + 内容区 py-6(48px)，这里用视口高度扣除余量，
     保证 SVG 画布获得确定高度（百分比高度链成立），并截断溢出防止页面级滚动。 */
  height: calc(100dvh - var(--kb-nav-height) - var(--kb-space-section) - var(--kb-space-section));
  min-height: 0;
  background: var(--kb-background);
  gap: var(--kb-space-3);
  padding: var(--kb-space-3);
  box-sizing: border-box;
  overflow: hidden;
}
.mm-side {
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
.mm-side-head {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  font-weight: 700;
  color: var(--kb-foreground);
  margin-bottom: var(--kb-space-3);
}
.mm-new-btn {
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
.mm-new-btn:hover {
  background: var(--kb-primary-hover);
}
.mm-new-btn.lg {
  width: auto;
  margin-top: 16px;
  padding: 11px 18px;
}
.mm-list {
  min-height: 0;
  margin-top: 12px;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mm-hint {
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  padding: 8px 4px;
}
.mm-list-item {
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
.mm-list-item:hover {
  background: var(--kb-muted);
}
.mm-list-item.active {
  border-color: var(--kb-primary, var(--kb-primary));
  background: var(--kb-primary-soft);
}
.mm-list-ico {
  color: var(--kb-primary, var(--kb-primary));
  flex: 0 0 auto;
}
.mm-list-name {
  flex: 1;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mm-list-time {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  flex: 0 0 auto;
}
.mm-list-del {
  flex: 0 0 auto;
  color: var(--kb-muted-foreground);
  display: none;
  padding: 2px;
  border-radius: 6px;
}
.mm-list-item:hover .mm-list-del {
  display: inline-flex;
}
.mm-list-del:hover {
  color: var(--kb-danger);
  background: var(--kb-danger-soft-strong);
}

.mm-main {
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
.mm-toolbar {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3);
  padding: 10px 14px;
  border-bottom: 1px solid var(--kb-border);
}
.mm-title:not(.kb-form-control) {
  border: none;
  border-radius: 8px;
  background: var(--kb-surface-2);
}
.mm-title {
  min-width: 0;
  width: 0;
  flex: 1;
  font-size: 16px;
  font-weight: 700;
  color: var(--kb-foreground);
  padding: 6px 8px;
}
.mm-title:not(.kb-form-control):focus {
  background: var(--kb-primary-soft);
}
.mm-tools {
  display: flex;
  align-items: center;
  gap: 4px;
}
.mm-tool-btn {
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
.mm-tool-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary, var(--kb-primary));
  border-color: var(--kb-border);
}
.mm-tool-btn.active {
  background: var(--kb-primary-soft);
  color: var(--kb-primary, var(--kb-primary));
  border-color: var(--kb-primary, var(--kb-primary));
}
.mm-tool-btn.mm-save {
  color: var(--kb-card);
  background: var(--kb-primary, var(--kb-primary));
  border-color: var(--kb-primary, var(--kb-primary));
}
.mm-tool-btn.mm-save:hover {
  background: var(--kb-primary-hover);
}
.mm-tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.mm-tool-sep {
  width: 1px;
  height: 22px;
  background: var(--kb-border);
  margin: 0 4px;
}

.mm-canvas {
  min-height: 0;
  position: relative;
  flex: 1;
  overflow: hidden;
  background:
    radial-gradient(circle, var(--kb-border) 1px, transparent 1px) 0 0 / 22px 22px,
    var(--kb-background);
}
.mm-svg {
  width: 100%;
  height: 100%;
  display: block;
  touch-action: none;
  cursor: grab;
}
.mm-svg:active {
  cursor: grabbing;
}
.mm-edge {
  fill: none;
  stroke: var(--kb-muted-foreground);
  stroke-width: 2;
  cursor: pointer;
}
.mm-edge-free {
  stroke: var(--kb-warning);
  stroke-dasharray: 6 4;
}
.mm-edge-hl {
  stroke: var(--kb-primary, var(--kb-primary));
  stroke-width: 3;
}
.mm-fo {
  overflow: visible;
}
.mm-card {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  background: var(--kb-card);
  border: 2px solid var(--kb-primary, var(--kb-primary));
  border-radius: var(--kb-radius-md);
  padding: 0 10px;
  cursor: grab;
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.08);
  position: relative;
  user-select: none;
}
.mm-card:focus-visible { outline: 2px solid var(--kb-ring); outline-offset: 2px; }
.mm-card:focus-within .mm-card-tools { display: flex; }
.mm-card.selected {
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.25);
}
.mm-card.is-source {
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.5);
}
.mm-card-text {
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-foreground);
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  padding-right: 46px;
}
.mm-edit:not(.kb-form-control) {
  border: none;
  outline: none;
  background: transparent;
}
.mm-edit {
  flex: 1;
  width: 100%;
  resize: none;
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
  color: var(--kb-foreground);
  font-family: inherit;
  line-height: 1.3;
  padding-right: 46px;
}
.mm-card-tools {
  position: absolute;
  top: 4px;
  right: 4px;
  display: none;
  gap: 2px;
}
.mm-card:hover .mm-card-tools,
.mm-card.selected .mm-card-tools {
  display: flex;
}
.mm-card-btn {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: var(--kb-muted);
  border-radius: 6px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
}
.mm-card-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary, var(--kb-primary));
}
.mm-card-btn.mm-danger:hover {
  background: var(--kb-danger-soft-strong);
  color: var(--kb-danger);
}

.mm-empty,
.mm-blank {
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
.mm-blank {
  pointer-events: auto;
}
.mm-blank h2 {
  color: var(--kb-foreground);
  font-size: var(--kb-fs-h4);
  margin: 0;
}
.mm-blank p {
  max-width: 360px;
  font-size: var(--kb-fs-body-md);
  line-height: 1.6;
  margin: 0;
}

.mm-status {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 14px;
  border-top: 1px solid var(--kb-border);
  font-size: 12px;
  color: var(--kb-muted-foreground);
  background: var(--kb-background);
}
.mm-dirty {
  color: var(--kb-warning);
}
.mm-saved {
  color: var(--kb-success);
}
.mm-sav-dot {
  color: var(--kb-primary);
}
.mm-connect-hint {
  color: var(--kb-warning);
  margin-left: auto;
}

.mm-new-btn.secondary { background: var(--kb-surface-2); color: var(--kb-foreground); border: 1px solid var(--kb-border); }
.mm-new-btn:disabled { opacity: .5; cursor: not-allowed; }
.mm-side { min-width: 0; }
.mm-blank { position: static; flex: 1; min-height: 0; gap: var(--kb-space-4); }
.mm-blank h2 { max-width: 100%; }
.mm-list-item { min-width: 0; flex-shrink: 0; }
.mm-tools, .mm-status { flex-wrap: wrap; }
.mm-directory-toggle { display: none; align-items: center; gap: var(--kb-space-2); height: var(--kb-btn-height-sm); padding: 0 var(--kb-space-3); background: var(--kb-card); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-sm); }
.mm-list-item:focus-within .mm-list-del { display: inline-flex; }
@media (max-width: 1100px) {
 .mm-directory-toggle { display: inline-flex; }
 .mm-root { position: relative; padding: 0; height: calc(100dvh - var(--kb-nav-height) - var(--kb-space-section) - var(--kb-space-section) - var(--kb-space-section)); }
 .mm-side { display: none; }
 .mm-root.side-open .mm-side { display: flex; position: absolute; z-index: 5; inset: 0 auto 0 0; max-width: 85%; box-shadow: var(--kb-shadow-lg); }
 .mm-toolbar { flex-wrap: wrap; }
 .mm-title { flex-basis: 100%; }
}
.mm-list-row { display: flex; align-items: center; gap: var(--kb-space-1); }
.mm-list-item { flex: 1; }
.mm-list-del { display: inline-flex; background: transparent; border: 0; }
.mm-root button:focus-visible { outline: 2px solid var(--kb-ring); outline-offset: 2px; }
</style>
