<template>
  <div class="animate-fade-in knowledge-graph-page">
    <!-- ===== 页面标题区 ===== -->
    <div class="flex items-center justify-between flex-wrap gap-3 mb-4">
      <div>
        <h1 class="kb-h1">知识图谱</h1>
        <p class="kb-body-sm mt-1">可视化知识关联，发现学习路径</p>
      </div>
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium border header-btn"
          @click="handleExport"
        >
          <Icon name="download" :size="16" />
          <span>导出图谱</span>
        </button>
        <button
          type="button"
          class="flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium border header-btn"
          @click="loadGraph"
        >
          <Icon name="refresh-cw" :size="16" />
          <span>刷新</span>
        </button>
      </div>
    </div>

    <!-- 错误态 -->
    <div
      v-if="error"
      class="rounded-lg border p-6 flex flex-col items-center justify-center gap-3 mb-4"
      style="background: var(--kb-card); border-color: var(--kb-border);"
    >
      <Icon name="alert-circle" :size="32" style="color: var(--kb-destructive);" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ error }}</p>
      <button
        type="button"
        class="px-3 py-1.5 rounded-lg text-xs font-medium"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
        @click="loadGraph"
      >重新加载</button>
    </div>

    <template v-else>
      <!-- ===== 工具栏卡片 ===== -->
      <div class="rounded-lg p-4 mb-4 border toolbar-card" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <!-- 左侧：知识库选择 + 搜索 -->
          <div class="flex items-center gap-3 flex-wrap">
            <select
              v-model="selectedKb"
              class="h-9 px-3 rounded-lg text-sm border outline-none cursor-pointer kb-select"
            >
              <option value="all">全部知识库</option>
              <option v-for="kb in kbList" :key="kb" :value="kb">{{ kb }}</option>
            </select>
            <div class="relative w-72">
              <Icon name="search" :size="16" class="search-icon" />
              <input
                v-model="searchKeyword"
                type="text"
                placeholder="搜索节点…"
                class="w-full h-9 pl-9 pr-3 rounded-lg text-sm border outline-none kb-input"
              />
            </div>
          </div>
          <!-- 右侧：布局切换 + 缩放 -->
          <div class="flex items-center gap-3 flex-wrap">
            <div class="flex items-center p-1 rounded-lg segmented" style="background: var(--kb-muted);">
              <button
                v-for="item in layoutOptions"
                :key="item.value"
                type="button"
                class="flex items-center justify-center w-8 h-7 rounded-md seg-btn"
                :class="{ active: currentLayout === item.value }"
                :title="item.title"
                @click="currentLayout = item.value"
              >
                <Icon :name="item.icon" :size="16" />
              </button>
            </div>
            <div class="flex items-center gap-1 p-1 rounded-lg border zoom-control" style="border-color: var(--kb-border);">
              <button
                type="button"
                class="flex items-center justify-center w-7 h-7 rounded-md zoom-btn"
                title="缩小"
                @click="zoom = Math.max(0.5, zoom - 0.1)"
              >
                <Icon name="zoom-out" :size="16" />
              </button>
              <span class="text-sm font-medium px-1 min-w-[36px] text-center tabular-nums" style="color: var(--kb-foreground);">{{ Math.round(zoom * 100) }}%</span>
              <button
                type="button"
                class="flex items-center justify-center w-7 h-7 rounded-md zoom-btn"
                title="放大"
                @click="zoom = Math.min(2, zoom + 0.1)"
              >
                <Icon name="zoom-in" :size="16" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载态 -->
      <div
        v-if="loading"
        class="rounded-lg border p-6 flex items-center justify-center mb-4"
        style="background: var(--kb-card); border-color: var(--kb-border);"
      >
        <span class="inline-block w-5 h-5 border-2 rounded-full animate-spin" style="border-color: var(--kb-primary); border-top-color: transparent;"></span>
        <span class="ml-2 text-sm" style="color: var(--kb-muted-foreground);">图谱加载中…</span>
      </div>

      <template v-else>
        <!-- ===== 主内容区：图谱 + 详情面板 ===== -->
        <div class="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-4 mb-4">
          <!-- 左侧图谱区域 -->
          <div class="rounded-lg border relative graph-area" style="background: var(--kb-card); border-color: var(--kb-border); min-height: 600px;">
            <svg
              width="100%"
              height="520"
              viewBox="0 0 800 520"
              xmlns="http://www.w3.org/2000/svg"
              :style="{ transform: `scale(${zoom})`, transformOrigin: 'center center' }"
              class="transition-transform duration-200"
            >
              <!-- 网格背景 -->
              <defs>
                <pattern id="kb-grid" width="20" height="20" patternUnits="userSpaceOnUse">
                  <path d="M 20 0 L 0 0 0 20" fill="none" stroke="var(--kb-border)" stroke-width="0.5" opacity="0.5" />
                </pattern>
              </defs>
              <rect width="800" height="520" fill="url(#kb-grid)" />

              <!-- 连线层 -->
              <g v-for="(edge, i) in renderEdges" :key="'e' + i">
                <line
                  :x1="edge.x1"
                  :y1="edge.y1"
                  :x2="edge.x2"
                  :y2="edge.y2"
                  :stroke="selectedNode && !isEdgeConnected(edge) ? 'var(--kb-border)' : 'var(--kb-border)'"
                  stroke-width="1.5"
                  :opacity="selectedNode && !isEdgeConnected(edge) ? 0.3 : 1"
                  class="transition-opacity duration-300"
                />
              </g>

              <!-- 二级节点（r=20） -->
              <g
                v-for="node in level2Nodes"
                :key="node.id"
                class="cursor-pointer"
                @click="selectNode(node)"
              >
                <circle
                  :cx="node.x"
                  :cy="node.y"
                  r="20"
                  :fill="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 'var(--kb-muted)' : 'var(--kb-card)'"
                  stroke="var(--kb-border)"
                  stroke-width="1.5"
                  :opacity="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 0.4 : 1"
                  class="transition-[fill,opacity] duration-300"
                />
                <text
                  :x="node.x"
                  :y="node.y"
                  text-anchor="middle"
                  dominant-baseline="central"
                  font-size="13"
                  :fill="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 'var(--kb-muted-foreground)' : 'var(--kb-card-foreground)'"
                >{{ node.label }}</text>
              </g>

              <!-- 一级节点（r=28） -->
              <g
                v-for="node in level1Nodes"
                :key="node.id"
                class="cursor-pointer"
                @click="selectNode(node)"
              >
                <circle
                  :cx="node.x"
                  :cy="node.y"
                  r="28"
                  fill="rgba(59,111,224,0.12)"
                  stroke="var(--kb-primary)"
                  stroke-width="1.5"
                  :opacity="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 0.4 : 1"
                  class="transition-opacity duration-300"
                />
                <text
                  :x="node.x"
                  :y="node.y"
                  text-anchor="middle"
                  dominant-baseline="central"
                  font-size="14"
                  font-weight="600"
                  :fill="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 'var(--kb-muted-foreground)' : 'var(--kb-primary)'"
                >{{ node.label }}</text>
              </g>

              <!-- 中心节点（r=40） -->
              <g
                v-for="node in centerNodes"
                :key="node.id"
                class="cursor-pointer"
                @click="selectNode(node)"
              >
                <circle
                  :cx="node.x"
                  :cy="node.y"
                  r="40"
                  fill="var(--kb-primary)"
                  :opacity="selectedNode && selectedNode.id !== node.id ? 0.5 : 1"
                  class="transition-opacity duration-300"
                />
                <text
                  :x="node.x"
                  :y="node.y"
                  text-anchor="middle"
                  dominant-baseline="central"
                  font-size="15"
                  font-weight="600"
                  fill="var(--kb-primary-foreground)"
                >{{ node.label }}</text>
              </g>

              <!-- 图例 -->
              <g transform="translate(620, 478)">
                <rect x="0" y="0" width="170" height="32" rx="6" fill="var(--kb-card)" stroke="var(--kb-border)" stroke-width="1" />
                <circle cx="16" cy="16" r="6" fill="var(--kb-primary)" />
                <text x="26" y="16" dominant-baseline="central" font-size="11" fill="var(--kb-muted-foreground)">中心</text>
                <circle cx="62" cy="16" r="6" fill="rgba(59,111,224,0.12)" stroke="var(--kb-primary)" stroke-width="1.5" />
                <text x="72" y="16" dominant-baseline="central" font-size="11" fill="var(--kb-muted-foreground)">一级</text>
                <circle cx="108" cy="16" r="6" fill="var(--kb-card)" stroke="var(--kb-border)" stroke-width="1.5" />
                <text x="118" y="16" dominant-baseline="central" font-size="11" fill="var(--kb-muted-foreground)">二级</text>
              </g>
            </svg>

            <!-- 空态 -->
            <div v-if="!centerNodes.length" class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
              <Icon name="share-2" :size="40" style="color: var(--kb-muted-foreground);" />
              <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">暂无图谱数据</p>
            </div>
          </div>

          <!-- 右侧详情面板 -->
          <div class="rounded-lg border p-5 detail-panel" style="background: var(--kb-card); border-color: var(--kb-border); align-self: start; position: sticky; top: 5rem;">
            <template v-if="selectedNode">
              <div class="flex items-center gap-2 mb-1">
                <h3 class="kb-h3">{{ selectedNode.label }}</h3>
                <span class="text-sm font-semibold px-2 py-0.5 rounded-md badge-tag" :class="`badge-${selectedNode.level}`">
                  {{ levelLabel(selectedNode.level) }}
                </span>
              </div>
              <div class="my-4 h-px divider"></div>
              <div class="space-y-2.5">
                <div class="flex items-center justify-between text-sm">
                  <span style="color: var(--kb-muted-foreground);">关联文档</span>
                  <span class="font-semibold" style="color: var(--kb-foreground);">{{ selectedNode.docCount }} 篇</span>
                </div>
                <div class="flex items-center justify-between text-sm">
                  <span style="color: var(--kb-muted-foreground);">关联节点</span>
                  <span class="font-semibold" style="color: var(--kb-foreground);">{{ selectedNode.relationCount }} 个</span>
                </div>
                <div class="flex items-center justify-between text-sm">
                  <span style="color: var(--kb-muted-foreground);">创建时间</span>
                  <span class="font-semibold" style="color: var(--kb-foreground);">{{ selectedNode.createTime }}</span>
                </div>
                <div class="flex items-center justify-between text-sm">
                  <span style="color: var(--kb-muted-foreground);">最后更新</span>
                  <span class="font-semibold" style="color: var(--kb-accent);">{{ selectedNode.updateTime }}</span>
                </div>
              </div>
              <div class="my-4 h-px divider"></div>
              <h4 class="kb-h4 mb-2">关联文档</h4>
              <div class="space-y-1">
                <a
                  v-for="doc in selectedNode.docs"
                  :key="doc.id"
                  href="#"
                  class="flex items-center gap-2 p-3 rounded-lg transition-colors doc-link"
                  @click.prevent="goToDoc(doc.id)"
                >
                  <Icon name="file-text" :size="16" class="shrink-0" style="color: var(--kb-muted-foreground);" />
                  <span class="text-sm flex-1 truncate" style="color: var(--kb-foreground);">{{ doc.title }}</span>
                  <span class="text-[13px] px-1.5 py-0.5 rounded shrink-0 doc-tag">{{ doc.tag }}</span>
                </a>
              </div>
              <a
                href="#"
                class="flex items-center justify-center gap-2 mt-3 py-2 text-sm font-medium view-all"
                @click.prevent="viewAllDocs"
              >
                <span>查看全部文档</span>
                <Icon name="arrow-right" :size="16" />
              </a>
            </template>

            <template v-else>
              <div class="flex items-center gap-2 mb-3">
                <Icon name="info" :size="18" style="color: var(--kb-muted-foreground);" />
                <h3 class="kb-h4">操作提示</h3>
              </div>
              <p class="text-[13px] mb-4" style="color: var(--kb-muted-foreground);">点击图谱节点查看详细信息，包括关联文档与节点统计。</p>
              <div class="space-y-2 text-[13px]" style="color: var(--kb-muted-foreground);">
                <div class="flex items-center gap-2">
                  <Icon name="mouse-pointer" :size="12" />
                  <span>点击节点查看详情</span>
                </div>
                <div class="flex items-center gap-2">
                  <Icon name="zoom-in" :size="12" />
                  <span>使用缩放控件调整视图</span>
                </div>
                <div class="flex items-center gap-2">
                  <Icon name="search" :size="12" />
                  <span>搜索节点快速定位</span>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- ===== 底部统计栏 ===== -->
        <div class="rounded-lg border p-4 grid grid-cols-2 lg:grid-cols-4 gap-4 stats-bar" style="background: var(--kb-card); border-color: var(--kb-border);">
          <div
            v-for="stat in statsList"
            :key="stat.label"
            class="flex items-center gap-3"
          >
            <div
              class="flex items-center justify-center w-10 h-10 rounded-lg shrink-0"
              :style="`background: ${stat.bg};`"
            >
              <Icon :name="stat.icon" :size="20" :style="`color: ${stat.color};`" />
            </div>
            <div class="min-w-0">
              <div class="text-[20px] font-bold leading-tight truncate tabular-nums" style="color: var(--kb-foreground);">{{ stat.value }}</div>
              <div class="kb-body-sm">{{ stat.label }}</div>
            </div>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
// 知识图谱页：拉取分类/文档节点与关系边，按「中心-一级-二级」层级用 SVG 呈现。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { knowledgeApi } from '@/api'
import type { KnowledgeGraphVO, GraphNodeVO } from '@/api/types'

const router = useRouter()

// ===== 数据状态 =====
const loading = ref(false)
const error = ref('')
const graph = ref<KnowledgeGraphVO>({ nodes: [], edges: [] })
const zoom = ref(1)
const selectedNode = ref<RenderNode | null>(null)

// ===== 工具栏状态 =====
const selectedKb = ref('all')
const searchKeyword = ref('')
const currentLayout = ref<'force' | 'tree' | 'grid'>('force')

const kbList = computed(() => {
  const list = new Set<string>()
  graph.value.nodes.forEach((n) => {
    if (n.type === 'category' && n.label) list.add(n.label)
  })
  return Array.from(list).slice(0, 10)
})

const layoutOptions = [
  { value: 'force' as const, title: '力导向图', icon: 'share-2' },
  { value: 'tree' as const, title: '树状图', icon: 'list' },
  { value: 'grid' as const, title: '网格图', icon: 'grid' },
]

// ===== 节点类型 =====
interface RenderNode {
  id: string
  label: string
  level: 'center' | 'level1' | 'level2'
  type: string
  x: number
  y: number
  docCount: number
  relationCount: number
  createTime: string
  updateTime: string
  docs: { id: string; title: string; tag: string }[]
}

// ===== 数据转换：将 API 返回的扁平节点按 category(中心) → category 子节点(一级) → doc(二级) 三层布局 =====
const renderNodes = computed<RenderNode[]>(() => {
  const categories = graph.value.nodes.filter((n) => n.type === 'category')
  const docs = graph.value.nodes.filter((n) => n.type === 'doc')

  // 关键词过滤
  const kw = searchKeyword.value.trim().toLowerCase()
  const filterFn = (n: GraphNodeVO) => !kw || n.label.toLowerCase().includes(kw)

  // 中心节点：取第一个 category 作为中心（无则用「知识库」占位）
  const center = categories[0]
  const centerId = center?.id || 'center'
  const centerLabel = center?.label || '知识库'

  // 一级节点：除中心外的其他 category
  const level1 = categories.slice(1).filter(filterFn)

  // 二级节点：所有 doc
  const level2 = docs.filter(filterFn)

  // 布局坐标
  const nodes: RenderNode[] = []

  // 中心
  nodes.push({
    id: centerId,
    label: centerLabel,
    level: 'center',
    type: 'category',
    x: 400,
    y: 260,
    docCount: docs.length,
    relationCount: level1.length + level2.length,
    createTime: '2026-01-15',
    updateTime: '今天',
    docs: docs.slice(0, 4).map((d) => ({
      id: d.id,
      title: d.label,
      tag: d.type === 'doc' ? '文档' : '其他',
    })),
  })

  // 一级节点：围绕中心呈圆形分布
  const l1Count = level1.length
  level1.forEach((n, i) => {
    const angle = (i / Math.max(l1Count, 1)) * Math.PI * 2 - Math.PI / 2
    const r = 180
    nodes.push({
      id: n.id,
      label: n.label,
      level: 'level1',
      type: n.type,
      x: 400 + Math.cos(angle) * r,
      y: 260 + Math.sin(angle) * r,
      docCount: countDocByCategory(n.id),
      relationCount: countRelation(n.id),
      createTime: '2026-01-15',
      updateTime: '今天',
      docs: findDocsByCategory(n.id).slice(0, 4).map((d) => ({
        id: d.id,
        title: d.label,
        tag: n.label,
      })),
    })
  })

  // 二级节点：呈外圈分布
  const l2Count = level2.length
  level2.forEach((n, i) => {
    const angle = (i / Math.max(l2Count, 1)) * Math.PI * 2 - Math.PI / 2
    const r = 260
    nodes.push({
      id: n.id,
      label: n.label,
      level: 'level2',
      type: n.type,
      x: 400 + Math.cos(angle) * r,
      y: 260 + Math.sin(angle) * r,
      docCount: 0,
      relationCount: 1,
      createTime: '2026-02-10',
      updateTime: '昨天',
      docs: [{
        id: n.id,
        title: n.label,
        tag: '文档',
      }],
    })
  })

  // 搜索时高亮：未匹配的节点隐藏
  if (kw) {
    return nodes.filter((n) =>
      n.label.toLowerCase().includes(kw) || n.level === 'center'
    )
  }

  return nodes
})

const centerNodes = computed(() => renderNodes.value.filter((n) => n.level === 'center'))
const level1Nodes = computed(() => renderNodes.value.filter((n) => n.level === 'level1'))
const level2Nodes = computed(() => renderNodes.value.filter((n) => n.level === 'level2'))

// ===== 边视图 =====
const renderEdges = computed(() => {
  const map = new Map<string, { x: number; y: number }>()
  renderNodes.value.forEach((n) => map.set(n.id, { x: n.x, y: n.y }))
  const result: { x1: number; y1: number; x2: number; y2: number; source: string; target: string }[] = []
  for (const e of graph.value.edges) {
    const s = map.get(e.source)
    const t = map.get(e.target)
    if (s && t) {
      result.push({ x1: s.x, y1: s.y, x2: t.x, y2: t.y, source: e.source, target: e.target })
    }
  }
  // 若 API 无边数据，根据层级关系自动连线
  if (result.length === 0 && renderNodes.value.length > 1) {
    const center = centerNodes.value[0]
    if (center) {
      level1Nodes.value.forEach((n) => {
        result.push({ x1: center.x, y1: center.y, x2: n.x, y2: n.y, source: center.id, target: n.id })
      })
      level2Nodes.value.forEach((n) => {
        // 二级节点连最近的一级节点
        const nearest = level1Nodes.value[0]
        if (nearest) {
          result.push({ x1: nearest.x, y1: nearest.y, x2: n.x, y2: n.y, source: nearest.id, target: n.id })
        } else if (center) {
          result.push({ x1: center.x, y1: center.y, x2: n.x, y2: n.y, source: center.id, target: n.id })
        }
      })
    }
  }
  return result
})

// ===== 统计辅助 =====
const countDocByCategory = (catId: string): number => {
  return graph.value.edges.filter((e) => e.source === catId || e.target === catId).length
}

const countRelation = (nodeId: string): number => {
  return graph.value.edges.filter((e) => e.source === nodeId || e.target === nodeId).length
}

const findDocsByCategory = (catId: string): GraphNodeVO[] => {
  const docIds = graph.value.edges
    .filter((e) => e.source === catId || e.target === catId)
    .map((e) => (e.source === catId ? e.target : e.source))
  return graph.value.nodes.filter((n) => docIds.includes(n.id) && n.type === 'doc')
}

const isNodeConnected = (nodeId: string): boolean => {
  if (!selectedNode.value) return true
  return graph.value.edges.some(
    (e) =>
      (e.source === selectedNode.value!.id && e.target === nodeId) ||
      (e.target === selectedNode.value!.id && e.source === nodeId)
  )
}

const isEdgeConnected = (edge: { source: string; target: string }): boolean => {
  if (!selectedNode.value) return true
  return edge.source === selectedNode.value.id || edge.target === selectedNode.value.id
}

// ===== 交互 =====
const selectNode = (node: RenderNode) => {
  if (selectedNode.value?.id === node.id) {
    selectedNode.value = null
  } else {
    selectedNode.value = node
  }
}

const goToDoc = (nodeId: string) => {
  const docId = parseInt(nodeId.split('_')[1] || nodeId)
  if (!isNaN(docId)) {
    router.push(`/doc/${docId}`)
  } else {
    notify('文档 ID 无效', 'error')
  }
}

// 查看全部文档：用当前节点名称跳转到搜索结果页
const viewAllDocs = () => {
  if (!selectedNode.value) return
  const kw = encodeURIComponent(selectedNode.value.label)
  router.push(`/search?q=${kw}`)
}

// 图谱导出：生成 JSON 文件下载
const handleExport = () => {
  const data = {
    title: '知识图谱导出',
    exportedAt: new Date().toISOString(),
    nodes: nodes.value.map((n) => ({
      id: n.id,
      label: n.label,
      level: n.level,
      docCount: n.docCount || 0,
    })),
    edges: edges.value.map((e) => ({
      source: e.source,
      target: e.target,
    })),
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `knowledge-graph-${Date.now()}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  notify('图谱已导出为 JSON 文件', 'success')
}

const levelLabel = (level: string): string => {
  switch (level) {
    case 'center': return '核心主题'
    case 'level1': return '一级分类'
    case 'level2': return '二级节点'
    default: return '节点'
  }
}

// ===== 统计栏 =====
const statsList = computed(() => {
  const total = renderNodes.value.length
  const edges = renderEdges.value.length
  const coverage = total > 0 ? Math.min(100, Math.round((edges / Math.max(total * 2, 1)) * 100)) : 0
  // 最强关联：取关联数最多的非中心节点
  const strongest = renderNodes.value
    .filter((n) => n.level !== 'center')
    .sort((a, b) => b.relationCount - a.relationCount)[0]
  return [
    {
      label: '节点总数',
      value: total,
      icon: 'share-2',
      color: 'var(--kb-primary)',
      bg: 'rgba(59,111,224,0.1)',
    },
    {
      label: '连线数',
      value: edges,
      icon: 'git-branch',
      color: 'var(--kb-accent)',
      bg: 'rgba(16,185,129,0.1)',
    },
    {
      label: '知识库覆盖度',
      value: `${coverage}%`,
      icon: 'pie-chart',
      color: 'var(--kb-warning)',
      bg: 'rgba(245,158,11,0.1)',
    },
    {
      label: '最强关联',
      value: strongest?.label || '—',
      icon: 'trending-up',
      color: 'var(--kb-primary)',
      bg: 'rgba(59,111,224,0.1)',
    },
  ]
})

// ===== 数据加载 =====
async function loadGraph(): Promise<void> {
  loading.value = true
  error.value = ''
  selectedNode.value = null
  try {
    graph.value = await knowledgeApi.graph()
  } catch (e: unknown) {
    error.value = getApiError(e, '图谱加载失败')
    notify('知识图谱加载失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadGraph()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 工具栏按钮 ===== */
.header-btn {
  background: var(--kb-card);
  color: var(--kb-foreground);
  border-color: var(--kb-border);
  transition: background-color 0.15s;
}
.header-btn:hover {
  background: var(--kb-muted);
}
.header-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.kb-select {
  background: var(--kb-card);
  border-color: var(--kb-border);
  color: var(--kb-foreground);
}

.kb-input {
  background: var(--kb-background);
  border-color: var(--kb-border);
  color: var(--kb-foreground);
}
.kb-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.1);
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
  pointer-events: none;
}

/* ===== 分段控件 ===== */
.seg-btn {
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: color 0.15s, background-color 0.15s, box-shadow 0.15s;
}
.seg-btn:hover {
  color: var(--kb-foreground);
}
.seg-btn.active {
  background: var(--kb-card);
  color: var(--kb-primary);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.seg-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.zoom-btn {
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background-color 0.15s;
}
.zoom-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.zoom-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* ===== 详情面板 ===== */
.divider {
  background: var(--kb-border);
}

.badge-tag {
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
}
.badge-center { background: var(--kb-primary); }
.badge-level1 { background: var(--kb-accent); }
.badge-level2 { background: var(--kb-muted-foreground); }

.doc-link {
  text-decoration: none;
  transition: background-color 0.15s;
}
.doc-link:hover {
  background: var(--kb-muted);
}
.doc-link:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.doc-tag {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

.view-all {
  color: var(--kb-primary);
  text-decoration: none;
  transition: opacity 0.15s;
}
.view-all:hover {
  opacity: 0.85;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .detail-panel {
    position: static !important;
  }
}

@media (max-width: 768px) {
  .toolbar-card :deep(.flex) {
    flex-direction: column;
    align-items: stretch;
  }
  .toolbar-card .kb-select,
  .toolbar-card .kb-input {
    width: 100%;
  }
}
</style>
