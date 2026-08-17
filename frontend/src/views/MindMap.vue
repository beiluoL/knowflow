<template>
  <div class="mm-root">
    <!-- ============ 侧边：思维导图列表 ============ -->
    <aside class="mm-side">
      <div class="mm-side-head">
        <Icon name="git-branch" :size="18" style="color: var(--kb-primary)" />
        <span class="mm-side-title">我的思维导图</span>
      </div>
      <button class="mm-new-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" @click="newMap">
        <Icon name="file-plus" :size="16" /> 新建思维导图
      </button>
      <div class="mm-list">
        <p v-if="loadingList" class="mm-hint">加载中…</p>
        <p v-else-if="maps.length === 0" class="mm-hint">还没有思维导图，点击上方按钮创建。</p>
        <button
          v-for="m in maps"
          :key="m.id"
          class="mm-list-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)]"
          :class="{ active: currentId === m.id }"
          @click="selectMap(m.id)"
        >
          <Icon name="git-branch" :size="15" class="mm-list-ico" />
          <span class="mm-list-name">{{ m.title }}</span>
          <span class="mm-list-time">{{ formatTime(m.updateTime) }}</span>
          <span class="mm-list-del" title="删除" @click.stop="deleteMap(m.id)">
            <Icon name="trash-2" :size="14" />
          </span>
        </button>
      </div>
    </aside>

    <!-- ============ 主区：画布 ============ -->
    <main class="mm-main">
      <template v-if="currentId">
        <!-- 工具栏 -->
        <div class="mm-toolbar">
          <input
            v-model="title"
            class="mm-title focus-visible:outline-none"
            placeholder="思维导图标题"
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
                  :style="{ borderColor: n.color || '#3B6FE0' }"
                  tabindex="0"
                  @pointerdown="onNodeDown(n, $event)"
                  @dblclick="onNodeDblClick(n)"
                >
                  <textarea
                    v-if="editingId === n.id"
                    :id="'mm-edit-' + n.id"
                    class="mm-edit focus-visible:outline-none"
                    :value="n.text"
                    @pointerdown.stop
                    @dblclick.stop
                    @keydown.enter.prevent.stop="commitText(n.id, ($event.target as HTMLTextAreaElement).value)"
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
            <Icon name="git-branch" :size="40" style="color: #cbd5e1" />
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
        <Icon name="git-branch" :size="48" style="color: #cbd5e1" />
        <h2>思维导图</h2>
        <p>把零散的想法结构化：自由增删节点、拖拽布局、连线建立关系、折叠分支聚焦重点。</p>
        <button class="mm-new-btn lg" @click="newMap"><Icon name="file-plus" :size="16" /> 新建第一张思维导图</button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'
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

const NODE_W = 184
const NODE_H = 48
const PALETTE = ['#3B6FE0', '#8B5CF6', '#10B981', '#F59E0B', '#EF4444', '#0EA5E9']

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
const svgRef = ref<SVGSVGElement | null>(null)

let saveTimer: number | undefined

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
  try {
    maps.value = await listMindMaps()
  } catch (e: any) {
    notify(e?.message || '加载列表失败', 'error')
  } finally {
    loadingList.value = false
  }
}

async function selectMap(id: number) {
  if (dirty.value && currentId.value) await save()
  try {
    const d = await getMindMap(id)
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
  if (dirty.value && currentId.value) await save()
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
  if (!window.confirm('确定删除该思维导图？此操作不可恢复。')) return
  try {
    await deleteMindMap(id)
    notify('已删除', 'success')
    if (currentId.value === id) {
      currentId.value = null
      title.value = ''
      data.value = emptyData()
    }
    await loadList()
  } catch (e: any) {
    notify(e?.message || '删除失败', 'error')
  }
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

function deleteNode(id: string) {
  const node = nodeById(id)
  if (!node) return
  if (!window.confirm('删除该节点及其所有子节点？')) return
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
  const scale = Math.min(3, Math.max(0.2, Math.min((cw - pad * 2) / bw, (ch - pad * 2) / bh)))
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
  saving.value = true
  try {
    await updateMindMap(currentId.value, { title: title.value || '未命名思维导图', data: data.value })
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
.mm-root {
  display: flex;
  /* 全屏页外壳：导航 pt-14(56px) + 内容区 py-6(48px)，这里用视口高度扣除余量，
     保证 SVG 画布获得确定高度（百分比高度链成立），并截断溢出防止页面级滚动。 */
  height: calc(100vh - 150px);
  min-height: 420px;
  background: #f7f8fa;
  gap: 12px;
  padding: 12px;
  box-sizing: border-box;
  overflow: hidden;
}
.mm-side {
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
.mm-side-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 12px;
}
.mm-new-btn {
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
.mm-new-btn:hover {
  background: #2f5bc4;
}
.mm-new-btn.lg {
  width: auto;
  margin-top: 16px;
  padding: 11px 18px;
}
.mm-list {
  margin-top: 12px;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mm-hint {
  color: #98a2b3;
  font-size: 13px;
  padding: 8px 4px;
}
.mm-list-item {
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
.mm-list-item:hover {
  background: #f1f4f9;
}
.mm-list-item.active {
  border-color: var(--kb-primary, #3b6fe0);
  background: #eef3fe;
}
.mm-list-ico {
  color: var(--kb-primary, #3b6fe0);
  flex: 0 0 auto;
}
.mm-list-name {
  flex: 1;
  font-size: 13px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mm-list-time {
  font-size: 11px;
  color: #98a2b3;
  flex: 0 0 auto;
}
.mm-list-del {
  flex: 0 0 auto;
  color: #98a2b3;
  display: none;
  padding: 2px;
  border-radius: 6px;
}
.mm-list-item:hover .mm-list-del {
  display: inline-flex;
}
.mm-list-del:hover {
  color: #ef4444;
  background: #fee2e2;
}

.mm-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.04);
}
.mm-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #eef0f3;
}
.mm-title {
  flex: 1;
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  border: none;
  padding: 6px 8px;
  border-radius: 8px;
  background: #f9fafb;
}
.mm-title:focus {
  background: #eef3fe;
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
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 9px;
  color: #475467;
  cursor: pointer;
  transition: all 0.15s;
}
.mm-tool-btn:hover {
  background: #f1f4f9;
  color: var(--kb-primary, #3b6fe0);
  border-color: #d6deeb;
}
.mm-tool-btn.active {
  background: #eef3fe;
  color: var(--kb-primary, #3b6fe0);
  border-color: var(--kb-primary, #3b6fe0);
}
.mm-tool-btn.mm-save {
  color: #fff;
  background: var(--kb-primary, #3b6fe0);
  border-color: var(--kb-primary, #3b6fe0);
}
.mm-tool-btn.mm-save:hover {
  background: #2f5bc4;
}
.mm-tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.mm-tool-sep {
  width: 1px;
  height: 22px;
  background: #e5e7eb;
  margin: 0 4px;
}

.mm-canvas {
  position: relative;
  flex: 1;
  overflow: hidden;
  background:
    radial-gradient(circle, #e8ebf0 1px, transparent 1px) 0 0 / 22px 22px,
    #fbfcfe;
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
  stroke: #94a3b8;
  stroke-width: 2;
  cursor: pointer;
}
.mm-edge-free {
  stroke: #f59e0b;
  stroke-dasharray: 6 4;
}
.mm-edge-hl {
  stroke: var(--kb-primary, #3b6fe0);
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
  background: #fff;
  border: 2px solid var(--kb-primary, #3b6fe0);
  border-radius: 12px;
  padding: 0 10px;
  cursor: grab;
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.08);
  position: relative;
  user-select: none;
}
.mm-card.selected {
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.25);
}
.mm-card.is-source {
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.5);
}
.mm-card-text {
  font-size: 13px;
  color: #1f2937;
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  padding-right: 46px;
}
.mm-edit {
  flex: 1;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  background: transparent;
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
  background: #f1f4f9;
  border-radius: 6px;
  color: #475467;
  cursor: pointer;
}
.mm-card-btn:hover {
  background: #e2e8f0;
  color: var(--kb-primary, #3b6fe0);
}
.mm-card-btn.mm-danger:hover {
  background: #fee2e2;
  color: #ef4444;
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
  color: #98a2b3;
  text-align: center;
  pointer-events: none;
  padding: 24px;
}
.mm-blank {
  pointer-events: auto;
}
.mm-blank h2 {
  color: #1f2937;
  font-size: 20px;
  margin: 0;
}
.mm-blank p {
  max-width: 360px;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}

.mm-status {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 14px;
  border-top: 1px solid #eef0f3;
  font-size: 12px;
  color: #667085;
  background: #fbfcfe;
}
.mm-dirty {
  color: #f59e0b;
}
.mm-saved {
  color: #10b981;
}
.mm-sav-dot {
  color: #3b6fe0;
}
.mm-connect-hint {
  color: #f59e0b;
  margin-left: auto;
}
</style>
