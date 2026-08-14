<template>
  <!--
    章节依赖关系图（DAG）
    - 布局：dagre 分层有向图布局（纯计算，不依赖 DOM）
    - 渲染：SVG 画连线 + HTML 绝对定位画节点卡片，两层共用同一套 translate/scale 变换
      （相比 foreignObject，HTML 层可直接复用项目 kb-* 主题与文本省略等能力）
  -->
  <div class="dag-graph" :class="{ expanded }">
    <!-- ===== 工具栏：缩放 / 适应画布 / 全屏 ===== -->
    <div class="dag-toolbar">
      <div class="dag-legend">
        <span class="legend-item"><i class="legend-dot dot-completed"></i>已完成</span>
        <span class="legend-item"><i class="legend-dot dot-available"></i>可学习</span>
        <span class="legend-item"><i class="legend-dot dot-locked"></i>未解锁</span>
      </div>
      <div class="dag-tools">
        <span class="zoom-label">{{ Math.round(scale * 100) }}%</span>
        <button type="button" class="tool-btn transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" title="缩小" @click="zoomByCenter(1 / ZOOM_STEP)">
          <Icon name="zoom-out" :size="15" aria-hidden="true" />
        </button>
        <button type="button" class="tool-btn transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" title="放大" @click="zoomByCenter(ZOOM_STEP)">
          <Icon name="zoom-in" :size="15" aria-hidden="true" />
        </button>
        <button type="button" class="tool-btn transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" title="适应画布" @click="fitView">
          <Icon name="target" :size="15" aria-hidden="true" />
        </button>
        <button type="button" class="tool-btn transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" :title="expanded ? '退出全屏' : '全屏查看'" @click="toggleExpand">
          <Icon name="maximize" :size="15" aria-hidden="true" />
        </button>
      </div>
    </div>

    <!-- ===== 画布 ===== -->
    <div
      ref="viewportRef"
      class="dag-viewport"
      :class="{ dragging }"
      :style="{ height: expanded ? undefined : `${height}px` }"
      @wheel.prevent="onWheel"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <!-- 连线层 -->
      <svg class="edge-layer" aria-hidden="true">
        <defs>
          <marker
            v-for="s in ARROW_STATUSES"
            :id="`${uid}-arrow-${s}`"
            :key="s"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M0,0 L10,5 L0,10 z" :class="`arrow arrow-${s}`" />
          </marker>
        </defs>
        <g :transform="`translate(${translateX},${translateY}) scale(${scale})`">
          <path
            v-for="edge in layoutEdges"
            :key="edge.key"
            :d="edge.path"
            class="dag-edge"
            :class="[`edge-${edge.status}`, { active: isEdgeActive(edge), faded: isEdgeFaded(edge) }]"
            :marker-end="`url(#${uid}-arrow-${isEdgeActive(edge) ? 'active' : edge.status})`"
          />
        </g>
      </svg>

      <!-- 节点层 -->
      <div
        class="node-layer"
        :style="{ transform: `translate(${translateX}px, ${translateY}px) scale(${scale})` }"
      >
        <div
          v-for="(node, index) in layoutNodes"
          :key="node.id"
          class="dag-node"
          :class="[
            `node-${node.status}`,
            { current: node.id === activeId, faded: isNodeFaded(node), hovered: node.id === hoverId },
          ]"
          :style="{
            left: `${node.x}px`,
            top: `${node.y}px`,
            width: `${NODE_WIDTH}px`,
            height: `${NODE_HEIGHT}px`,
            animationDelay: `${Math.min(index, 20) * 35}ms`,
          }"
          :data-node-id="node.id"
          @mouseenter="hoverId = node.id"
          @mouseleave="hoverId = null"
        >
          <span class="node-badge">
            <Icon v-if="node.status === 'completed'" name="check" :size="13" />
            <Icon v-else-if="node.status === 'locked'" name="lock" :size="12" />
            <span v-else class="badge-order">{{ node.sortOrder }}</span>
          </span>
          <span class="node-body">
            <span class="node-title">{{ node.title }}</span>
            <span class="node-meta">
              <Icon name="clock" :size="11" />
              <span>{{ formatDuration(node.duration) }}</span>
              <span v-if="node.prerequisiteCount > 0" class="node-dep">· {{ node.prerequisiteCount }} 个前置</span>
            </span>
          </span>
        </div>
      </div>

      <!-- 悬浮详情卡片 -->
      <div v-if="tooltip" class="dag-tooltip" :style="{ left: `${tooltip.left}px`, top: `${tooltip.top}px` }">
        <div class="tip-title">第 {{ tooltip.sortOrder }} 章 · {{ tooltip.title }}</div>
        <div class="tip-row">
          <span class="tip-status" :class="`tip-${tooltip.status}`">{{ STATUS_TEXT[tooltip.status] }}</span>
          <span class="tip-duration">{{ formatDuration(tooltip.duration) }}</span>
        </div>
        <div v-if="tooltip.prerequisiteTitles.length" class="tip-deps">
          前置：{{ tooltip.prerequisiteTitles.join('、') }}
        </div>
      </div>

      <!-- 空态 -->
      <div v-if="layoutNodes.length === 0" class="dag-empty">
        <Icon name="git-branch" :size="26" />
        <span>暂无章节依赖数据</span>
      </div>
    </div>

    <p class="dag-tips">
      <Icon name="move" :size="13" aria-hidden="true" />
      <span>拖拽平移 · 滚轮缩放 · 点击节点进入章节</span>
    </p>
  </div>
</template>

<script setup lang="ts">
/**
 * DagGraph：学习路径章节依赖关系图。
 * 使用 dagre 做分层布局，自行渲染 SVG 连线与 HTML 节点，支持拖拽平移、滚轮缩放、
 * 悬停高亮相邻链路、全屏查看。
 */
import { computed, onBeforeUnmount, onMounted, nextTick, ref, watch } from 'vue';
import { Graph, layout } from '@dagrejs/dagre';
import type { NodeLabel, EdgeLabel, Point } from '@dagrejs/dagre';
import Icon from '@/components/ui/Icon.vue';
import type { ChapterDagVO, ChapterEdgeVO, ChapterNodeStatus, ChapterNodeVO } from '@/api/types';

interface Props {
  /** 图数据（节点 + 边）。 */
  data: ChapterDagVO | null;
  /** 画布高度（非全屏态），单位 px。 */
  height?: number;
  /** 当前章节 ID，用于高亮描边。 */
  activeId?: number | null;
}

const props = withDefaults(defineProps<Props>(), {
  height: 400,
  activeId: null,
});

const emit = defineEmits<{
  (e: 'node-click', node: ChapterNodeVO): void;
}>();

// ===== 常量 =====
const NODE_WIDTH = 210;
const NODE_HEIGHT = 64;
const ZOOM_STEP = 1.2;
const MIN_SCALE = 0.3;
const MAX_SCALE = 2.2;
/** 拖拽位移超过该阈值则视为平移而非点击。 */
const CLICK_SLOP = 4;
const ARROW_STATUSES = ['completed', 'available', 'locked', 'active'] as const;
const STATUS_TEXT: Record<ChapterNodeStatus, string> = {
  completed: '已完成',
  available: '可学习',
  locked: '未解锁',
};

/** SVG marker id 需全局唯一，避免同页多图相互覆盖。 */
const uid = `dag-${Math.random().toString(36).slice(2, 8)}`;

// ===== 布局结果 =====
interface LayoutNode {
  id: number;
  title: string;
  sortOrder: number;
  duration: number;
  status: ChapterNodeStatus;
  prerequisiteCount: number;
  /** 左上角坐标（图坐标系）。 */
  x: number;
  y: number;
  /** 中心点，用于 tooltip 定位。 */
  cx: number;
  cy: number;
}

interface LayoutEdge {
  key: string;
  source: number;
  target: number;
  status: ChapterNodeStatus;
  path: string;
}

const layoutNodes = ref<LayoutNode[]>([]);
const layoutEdges = ref<LayoutEdge[]>([]);
const graphWidth = ref(0);
const graphHeight = ref(0);

// ===== 视图变换 =====
const viewportRef = ref<HTMLDivElement | null>(null);
const scale = ref(1);
const translateX = ref(0);
const translateY = ref(0);
const dragging = ref(false);
const expanded = ref(false);
const hoverId = ref<number | null>(null);
/** 用户是否手动调整过视图，若是则窗口尺寸变化时不再自动适应。 */
const userAdjusted = ref(false);

let pointerStart: { x: number; y: number; tx: number; ty: number; nodeId: number | null } | null = null;
let pointerMoved = false;
let resizeObserver: ResizeObserver | null = null;

// ===== 布局计算 =====
function buildLayout(data: ChapterDagVO | null): void {
  const nodes = data?.nodes ?? [];
  const edges = data?.edges ?? [];
  if (nodes.length === 0) {
    layoutNodes.value = [];
    layoutEdges.value = [];
    graphWidth.value = 0;
    graphHeight.value = 0;
    return;
  }

  const g = new Graph({ directed: true, multigraph: false, compound: false });
  g.setGraph({ rankdir: 'LR', ranksep: 84, nodesep: 26, edgesep: 16, marginx: 28, marginy: 28 });
  g.setDefaultEdgeLabel(() => ({}));

  const nodeMap = new Map<number, ChapterNodeVO>();
  nodes.forEach((n) => {
    nodeMap.set(n.id, n);
    g.setNode(String(n.id), { width: NODE_WIDTH, height: NODE_HEIGHT });
  });
  const validEdges: ChapterEdgeVO[] = edges.filter(
    (e) => nodeMap.has(e.source) && nodeMap.has(e.target) && e.source !== e.target,
  );
  validEdges.forEach((e) => g.setEdge(String(e.source), String(e.target)));

  layout(g);

  layoutNodes.value = nodes.map((n) => {
    const pos = g.node(String(n.id)) as NodeLabel | undefined;
    const cx = pos?.x ?? 0;
    const cy = pos?.y ?? 0;
    return {
      id: n.id,
      title: n.title,
      sortOrder: n.sortOrder ?? 0,
      duration: n.duration ?? 0,
      status: n.status,
      prerequisiteCount: validEdges.filter((e) => e.target === n.id).length,
      x: cx - NODE_WIDTH / 2,
      y: cy - NODE_HEIGHT / 2,
      cx,
      cy,
    };
  });

  layoutEdges.value = validEdges.map((e) => {
    const label = g.edge(String(e.source), String(e.target)) as EdgeLabel | undefined;
    const target = nodeMap.get(e.target);
    return {
      key: `${e.source}-${e.target}`,
      source: e.source,
      target: e.target,
      status: target?.status ?? 'locked',
      path: buildSmoothPath(label?.points ?? []),
    };
  });

  const meta = g.graph();
  graphWidth.value = meta.width ?? 0;
  graphHeight.value = meta.height ?? 0;
}

/** 折线点集 → Catmull-Rom 平滑三次贝塞尔路径。 */
function buildSmoothPath(points: Point[]): string {
  if (points.length < 2) return '';
  if (points.length === 2) {
    return `M${points[0].x},${points[0].y} L${points[1].x},${points[1].y}`;
  }
  let d = `M${points[0].x},${points[0].y}`;
  for (let i = 0; i < points.length - 1; i += 1) {
    const p0 = points[i - 1] ?? points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2] ?? p2;
    const c1x = p1.x + (p2.x - p0.x) / 6;
    const c1y = p1.y + (p2.y - p0.y) / 6;
    const c2x = p2.x - (p3.x - p1.x) / 6;
    const c2y = p2.y - (p3.y - p1.y) / 6;
    d += ` C${c1x},${c1y} ${c2x},${c2y} ${p2.x},${p2.y}`;
  }
  return d;
}

// ===== 高亮联动 =====
/** 悬停节点时，与之直接相连的节点集合（含自身）。 */
const highlightNodeIds = computed<Set<number>>(() => {
  const set = new Set<number>();
  if (hoverId.value === null) return set;
  set.add(hoverId.value);
  layoutEdges.value.forEach((e) => {
    if (e.source === hoverId.value) set.add(e.target);
    if (e.target === hoverId.value) set.add(e.source);
  });
  return set;
});

function isEdgeActive(edge: LayoutEdge): boolean {
  return hoverId.value !== null && (edge.source === hoverId.value || edge.target === hoverId.value);
}
function isEdgeFaded(edge: LayoutEdge): boolean {
  return hoverId.value !== null && !isEdgeActive(edge);
}
function isNodeFaded(node: LayoutNode): boolean {
  return hoverId.value !== null && !highlightNodeIds.value.has(node.id);
}

// ===== 悬浮详情 =====
const tooltip = computed(() => {
  if (hoverId.value === null) return null;
  const node = layoutNodes.value.find((n) => n.id === hoverId.value);
  if (!node) return null;
  const prerequisiteTitles = layoutEdges.value
    .filter((e) => e.target === node.id)
    .map((e) => layoutNodes.value.find((n) => n.id === e.source)?.title)
    .filter((t): t is string => !!t);
  return {
    title: node.title,
    sortOrder: node.sortOrder,
    duration: node.duration,
    status: node.status,
    prerequisiteTitles,
    // 图坐标 → 屏幕坐标，卡片显示在节点正下方
    left: node.cx * scale.value + translateX.value,
    top: (node.cy + NODE_HEIGHT / 2) * scale.value + translateY.value + 10,
  };
});

// ===== 缩放 / 平移 =====
function clampScale(v: number): number {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, v));
}

/** 以视口内某点为锚点缩放，保持该点在屏幕上不动。 */
function zoomAt(factor: number, anchorX: number, anchorY: number): void {
  const next = clampScale(scale.value * factor);
  if (next === scale.value) return;
  const ratio = next / scale.value;
  translateX.value = anchorX - (anchorX - translateX.value) * ratio;
  translateY.value = anchorY - (anchorY - translateY.value) * ratio;
  scale.value = next;
  userAdjusted.value = true;
}

function zoomByCenter(factor: number): void {
  const el = viewportRef.value;
  if (!el) return;
  zoomAt(factor, el.clientWidth / 2, el.clientHeight / 2);
}

function onWheel(ev: WheelEvent): void {
  const el = viewportRef.value;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  zoomAt(ev.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP, ev.clientX - rect.left, ev.clientY - rect.top);
}

function onPointerDown(ev: PointerEvent): void {
  if (ev.button !== 0) return;
  // 记录按下时命中的节点，供 pointerup 判定「点击」而非「拖拽」
  const target = ev.target as HTMLElement | null;
  const nodeEl = target?.closest('.dag-node') as HTMLElement | null;
  const nodeId = nodeEl ? Number(nodeEl.dataset.nodeId) : null;
  pointerStart = { x: ev.clientX, y: ev.clientY, tx: translateX.value, ty: translateY.value, nodeId };
  pointerMoved = false;
  dragging.value = true;
  // 捕获指针，使拖拽时即使移出元素也能持续接收 move 事件
  (ev.currentTarget as HTMLElement).setPointerCapture(ev.pointerId);
}

function onPointerMove(ev: PointerEvent): void {
  if (!pointerStart) return;
  const dx = ev.clientX - pointerStart.x;
  const dy = ev.clientY - pointerStart.y;
  if (Math.abs(dx) > CLICK_SLOP || Math.abs(dy) > CLICK_SLOP) pointerMoved = true;
  translateX.value = pointerStart.tx + dx;
  translateY.value = pointerStart.ty + dy;
  if (pointerMoved) userAdjusted.value = true;
}

function onPointerUp(ev: PointerEvent): void {
  if (!pointerStart) return;
  const start = pointerStart;
  pointerStart = null;
  dragging.value = false;
  const el = ev.currentTarget as HTMLElement;
  if (el.hasPointerCapture(ev.pointerId)) el.releasePointerCapture(ev.pointerId);
  // pointer capture 会把 click 事件重定向到容器，因此改用 pointerup 判定点击：
  // 未发生拖拽且按下时命中节点 → 视为节点点击，触发跳转
  if (!pointerMoved && start.nodeId !== null) {
    const raw = props.data?.nodes.find((n) => n.id === start.nodeId);
    if (raw) emit('node-click', raw);
  }
}

/** 自动缩放并居中，使整图完整显示。 */
function fitView(): void {
  const el = viewportRef.value;
  if (!el || graphWidth.value === 0 || graphHeight.value === 0) return;
  const cw = el.clientWidth;
  const ch = el.clientHeight;
  if (cw === 0 || ch === 0) return;
  const next = clampScale(Math.min(cw / graphWidth.value, ch / graphHeight.value, 1));
  scale.value = next;
  translateX.value = (cw - graphWidth.value * next) / 2;
  translateY.value = (ch - graphHeight.value * next) / 2;
  userAdjusted.value = false;
}

function toggleExpand(): void {
  expanded.value = !expanded.value;
  nextTick(fitView);
}

function onKeydown(ev: KeyboardEvent): void {
  if (ev.key === 'Escape' && expanded.value) toggleExpand();
}

// ===== 格式化 =====
function formatDuration(minutes: number): string {
  if (!minutes) return '待定';
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = minutes / 60;
  return `${hours % 1 === 0 ? hours : hours.toFixed(1)} 小时`;
}

// ===== 生命周期 =====
watch(
  () => props.data,
  (val) => {
    buildLayout(val);
    nextTick(fitView);
  },
  { immediate: true, deep: true },
);

onMounted(() => {
  nextTick(fitView);
  const el = viewportRef.value;
  if (el && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      if (!userAdjusted.value) fitView();
    });
    resizeObserver.observe(el);
  }
  window.addEventListener('keydown', onKeydown);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  window.removeEventListener('keydown', onKeydown);
});
</script>

<style scoped>
/* ===== 容器 ===== */
.dag-graph {
  --dag-completed: var(--kb-accent);
  --dag-available: var(--kb-primary);
  --dag-locked: #94a3b8;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dag-graph.expanded {
  position: fixed;
  inset: 0;
  z-index: 900;
  padding: 16px;
  background: var(--kb-background);
}
.dag-graph.expanded .dag-viewport {
  flex: 1;
  height: auto;
}

/* ===== 工具栏 ===== */
.dag-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.dag-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend-dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
}
.dot-completed { background: var(--dag-completed); }
.dot-available { background: var(--dag-available); }
.dot-locked { background: var(--dag-locked); }

.dag-tools {
  display: flex;
  align-items: center;
  gap: 6px;
}
.zoom-label {
  min-width: 40px;
  text-align: right;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--kb-muted-foreground);
}
.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}
.tool-btn:hover {
  color: var(--kb-primary);
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
}

/* ===== 画布 ===== */
.dag-viewport {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 10px);
  background-color: var(--kb-background);
  /* 点阵底纹，营造「画布」质感 */
  background-image: radial-gradient(circle, rgba(100, 116, 139, 0.18) 1px, transparent 1px);
  background-size: 18px 18px;
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.dag-viewport.dragging { cursor: grabbing; }

.edge-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
}
.node-layer {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  will-change: transform;
}

/* ===== 连线 ===== */
.dag-edge {
  fill: none;
  stroke-width: 1.8;
  transition: opacity 0.2s, stroke-width 0.2s;
}
.edge-completed { stroke: var(--dag-completed); opacity: 0.75; }
.edge-available {
  stroke: var(--dag-available);
  opacity: 0.85;
  stroke-dasharray: 6 6;
  animation: dag-dash-flow 0.9s linear infinite;
}
.edge-locked { stroke: var(--dag-locked); opacity: 0.5; }
.dag-edge.active { stroke-width: 2.6; opacity: 1; }
.dag-edge.faded { opacity: 0.12; }
@keyframes dag-dash-flow {
  to { stroke-dashoffset: -12; }
}

.arrow { stroke: none; }
.arrow-completed { fill: var(--dag-completed); }
.arrow-available { fill: var(--dag-available); }
.arrow-locked { fill: var(--dag-locked); }
.arrow-active { fill: var(--kb-primary); }

/* ===== 节点 ===== */
.dag-node {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  box-sizing: border-box;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
  cursor: pointer;
  opacity: 0;
  animation: dag-node-in 0.32s ease-out forwards;
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s, opacity 0.2s;
}
@keyframes dag-node-in {
  from { opacity: 0; transform: translateY(6px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.dag-node:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}
.dag-node.faded { opacity: 0.28; }

.node-completed { border-color: rgba(16, 185, 129, 0.45); }
.node-completed .node-badge { background: var(--dag-completed); color: #fff; }
.node-available { border-color: rgba(59, 111, 224, 0.45); }
.node-available .node-badge { background: var(--dag-available); color: #fff; }
.node-locked { background: var(--kb-muted); }
.node-locked .node-badge { background: #e2e8f0; color: var(--dag-locked); }
.node-locked .node-title { color: var(--kb-muted-foreground); }

.dag-node.current {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.16);
}

.node-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  flex-shrink: 0;
}
.badge-order {
  font-size: 12px;
  font-weight: 600;
}

.node-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}
.node-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.node-dep { white-space: nowrap; }

/* ===== 悬浮详情 ===== */
.dag-tooltip {
  position: absolute;
  z-index: 5;
  max-width: 260px;
  transform: translateX(-50%);
  padding: 8px 10px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.14);
  pointer-events: none;
  animation: dag-tip-in 0.15s ease-out;
}
@keyframes dag-tip-in {
  from { opacity: 0; transform: translateX(-50%) translateY(-4px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}
.tip-title {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--kb-foreground);
}
.tip-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.tip-status {
  padding: 1px 6px;
  border-radius: 999px;
  font-weight: 500;
}
.tip-completed { background: rgba(16, 185, 129, 0.12); color: var(--dag-completed); }
.tip-available { background: rgba(59, 111, 224, 0.12); color: var(--dag-available); }
.tip-locked { background: var(--kb-muted); color: var(--kb-muted-foreground); }
.tip-deps {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--kb-muted-foreground);
}

/* ===== 空态 / 提示 ===== */
.dag-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  opacity: 0.7;
}
.dag-tips {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

@media (max-width: 640px) {
  .dag-toolbar { justify-content: flex-start; }
  .dag-legend { gap: 10px; }
}
</style>
