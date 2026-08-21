<template>
  <div class="rbt-page animate-fade-in">
    <!-- 页面头部 -->
    <header class="rbt-header">
      <div class="header-left">
        <h1 class="page-title">数据结构 · 红黑树</h1>
        <p class="page-subtitle">动态演示红黑树的底层实现流程，理解节点插入、旋转与变色的完整过程</p>
      </div>
      <div class="header-controls">
        <div class="input-group">
          <input
            v-model="inputValue"
            type="number"
            placeholder="输入节点值"
            class="node-input"
            @keyup.enter="handleInsert"
          />
          <Button variant="primary" @click="handleInsert" :disabled="isPlaying">
            <Icon name="plus" :size="16" />
            插入节点
          </Button>
        </div>
        <div class="action-group">
          <Button variant="secondary" @click="handleLoadSample" :disabled="isPlaying">
            <Icon name="layers" :size="16" />
            加载示例
          </Button>
          <Button variant="secondary" @click="handleClear" :disabled="isPlaying">
            <Icon name="trash-2" :size="16" />
            清空
          </Button>
          <Button variant="secondary" @click="handleValidate" :disabled="isPlaying">
            <Icon name="shield-check" :size="16" />
            校验规则
          </Button>
        </div>
      </div>
    </header>

    <!-- 主体区：左侧树可视化 + 右侧信息面板 -->
    <main class="rbt-main">
      <!-- 左侧：树可视化 -->
      <div class="tree-container">
        <!-- SVG 树 -->
        <div class="tree-svg-wrapper">
          <svg
            :viewBox="`0 0 ${bounds.width} ${bounds.height}`"
            class="tree-svg"
            preserveAspectRatio="xMidYMid meet"
          >
            <!-- 连线层 -->
            <g class="edges-layer">
              <path
                v-for="edge in edges"
                :key="`edge-${edge.from}-${edge.to}`"
                :d="edge.path"
                class="tree-edge"
                :class="{ 'highlight-edge': isNodeHighlighted(edge.from) || isNodeHighlighted(edge.to) }"
              />
            </g>

            <!-- 节点层 -->
            <g class="nodes-layer">
              <g
                v-for="node in currentNodes"
                :key="node.id"
                :transform="`translate(${node.x}, ${node.y})`"
                class="tree-node"
                :class="getNodeClass(node)"
              >
                <!-- 节点阴影 -->
                <circle
                  r="26"
                  :class="[
                    'node-shadow',
                    node.color === 'RED' ? 'shadow-red' : 'shadow-black',
                  ]"
                />
                <!-- 节点主体 -->
                <circle
                  r="24"
                  :class="[
                    'node-circle',
                    node.color === 'RED' ? 'node-red' : 'node-black',
                    { 'highlight-ring': isNodeHighlighted(node.id) },
                  ]"
                />
                <!-- 节点值 -->
                <text
                  class="node-value"
                  :class="{ 'value-white': node.color === 'BLACK' }"
                  text-anchor="middle"
                  dominant-baseline="central"
                >
                  {{ node.value }}
                </text>
              </g>
            </g>
          </svg>
        </div>

        <!-- 图例 -->
        <div class="tree-legend">
          <div class="legend-item">
            <span class="legend-dot red-dot" />
            <span>红色节点</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot black-dot" />
            <span>黑色节点</span>
          </div>
        </div>

        <!-- 节点统计 -->
        <div class="tree-stats">
          <span class="stat-item">
            <span class="red-dot-inline" />
            红节点: {{ redCount }}
          </span>
          <span class="stat-item">
            <span class="black-dot-inline" />
            黑节点: {{ blackCount }}
          </span>
          <span class="stat-item">
            <Icon name="git-branch" :size="14" />
            总节点: {{ currentNodes.length }}
          </span>
        </div>
      </div>

      <!-- 右侧：信息面板 -->
      <div class="info-panel">
        <!-- 当前步骤 -->
        <Card v-if="currentStep" class="step-card">
          <template #header>
            <div class="card-header">
              <Icon :name="stepIcon" :size="20" class="step-icon" />
              <h3>当前操作步骤</h3>
              <Badge :variant="stepBadgeVariant" class="step-badge">
                步骤 {{ currentStepIndex + 1 }} / {{ currentSteps.length }}
              </Badge>
            </div>
          </template>
          <div class="step-content">
            <p class="step-description">{{ currentStep.description }}</p>
            <div v-if="currentStep.ruleId" class="rule-tag">
              <Badge variant="warning" size="sm">
                规则 {{ currentStep.ruleId }}
              </Badge>
              <span class="rule-name">{{ RULE_DESCRIPTIONS[currentStep.ruleId].title }}</span>
            </div>
          </div>
        </Card>

        <!-- 播放控制 -->
        <Card class="control-card">
          <template #header>
            <div class="card-header">
              <Icon name="play" :size="18" />
              <h3>播放控制</h3>
            </div>
          </template>
          <div class="playback-controls">
            <div class="control-buttons">
              <button class="ctrl-btn" @click="goToStep(0)" :disabled="currentStepIndex <= 0">
                <Icon name="skip-back" :size="16" />
              </button>
              <button class="ctrl-btn" @click="prevStep" :disabled="currentStepIndex <= 0">
                <Icon name="chevron-left" :size="16" />
              </button>
              <button
                class="ctrl-btn play-btn"
                @click="togglePlay"
                :disabled="currentSteps.length === 0"
              >
                <Icon :name="isPlaying ? 'pause' : 'play'" :size="20" />
              </button>
              <button
                class="ctrl-btn"
                @click="nextStep"
                :disabled="currentStepIndex >= currentSteps.length - 1"
              >
                <Icon name="chevron-right" :size="16" />
              </button>
              <button
                class="ctrl-btn"
                @click="goToStep(currentSteps.length - 1)"
                :disabled="currentStepIndex >= currentSteps.length - 1"
              >
                <Icon name="skip-forward" :size="16" />
              </button>
            </div>
            <div class="progress-bar-wrapper">
              <div
                class="progress-track"
                @click="handleProgressClick"
                ref="progressRef"
              >
                <div
                  class="progress-fill"
                  :style="{ width: progressPercent + '%' }"
                />
                <div
                  v-for="(_, index) in currentSteps"
                  :key="index"
                  class="progress-marker"
                  :class="{ active: index === currentStepIndex }"
                  :style="{ left: `${(index / Math.max(1, currentSteps.length - 1)) * 100}%` }"
                  @click.stop="goToStep(index)"
                />
              </div>
            </div>
            <div class="speed-control">
              <span class="speed-label">速度</span>
              <button
                v-for="s in speeds"
                :key="s"
                :class="['speed-btn', { active: currentSpeed === s }]"
                @click="currentSpeed = s"
              >
                {{ s }}x
              </button>
            </div>
          </div>
        </Card>

        <!-- 红黑树五大规则 -->
        <Card class="rules-card">
          <template #header>
            <div class="card-header">
              <Icon name="book-open" :size="18" />
              <h3>红黑树五大规则</h3>
            </div>
          </template>
          <div class="rules-list">
            <div
              v-for="(rule, id) in RULE_DESCRIPTIONS"
              :key="id"
              :class="[
                'rule-item',
                {
                  violated: violatedRules.some((v) => v.ruleId === id),
                  highlighted: currentStep?.ruleId === id,
                },
              ]"
            >
              <span class="rule-num">{{ id }}</span>
              <div class="rule-text">
                <span class="rule-title">{{ rule.title }}</span>
                <span class="rule-detail">{{ rule.detail }}</span>
              </div>
              <Icon
                v-if="violatedRules.some((v) => v.ruleId === id)"
                name="alert-circle"
                :size="16"
                class="rule-warning"
              />
              <Icon
                v-else
                name="check-circle"
                :size="16"
                class="rule-ok"
              />
            </div>
          </div>
          <!-- 违规详情 -->
          <div v-if="violatedRules.length > 0" class="violations-panel">
            <p class="violations-title">
              <Icon name="alert-circle" :size="16" />
              规则违规检测（{{ violatedRules.length }} 个问题）
            </p>
            <ul class="violations-list">
              <li v-for="(v, i) in violatedRules" :key="i">
                <span class="violation-rule">规则 {{ v.ruleId }}</span>
                {{ v.description }}
              </li>
            </ul>
          </div>
          <div v-else class="no-violations">
            <Icon name="shield-check" :size="18" />
            <span>当前红黑树满足所有规则 ✅</span>
          </div>
        </Card>

        <!-- 操作历史 -->
        <Card v-if="currentSteps.length > 0" class="history-card">
          <template #header>
            <div class="card-header">
              <Icon name="list" :size="18" />
              <h3>操作历史</h3>
            </div>
          </template>
          <div class="history-list">
            <div
              v-for="(step, index) in currentSteps"
              :key="index"
              :class="[
                'history-item',
                { active: index === currentStepIndex },
              ]"
              @click="goToStep(index)"
            >
              <span class="history-step-num">{{ index + 1 }}</span>
              <span class="history-type">{{ stepTypeLabel(step.type) }}</span>
            </div>
          </div>
        </Card>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue';
import Card from '@/components/ui/Card.vue';
import Badge from '@/components/ui/Badge.vue';
import Button from '@/components/ui/Button.vue';
import Icon from '@/components/ui/Icon.vue';
import { notify } from '@/utils/toast';
import {
  RedBlackTree,
  type Step,
  type RBNode,
  type SnapshotNode,
  type StepType,
  type RuleId,
  type RuleViolation,
  RULE_DESCRIPTIONS,
} from '@/utils/redBlackTree';

// ===== 状态 =====
const tree = ref(new RedBlackTree());
const inputValue = ref('');
const currentSteps = ref<Step[]>([]);
const currentStepIndex = ref(-1);
const isPlaying = ref(false);
const currentSpeed = ref(1);
const progressRef = ref<HTMLElement | null>(null);
let playTimer: number | null = null;
let playAbortController: AbortController | null = null;
let currentPlayIndex = 0;

// ===== 当前树状态 =====
const currentNodes = ref<RBNode[]>([]);
const bounds = ref({ width: 500, height: 350 });
const highlightedNodeIds = ref<number[]>([]);

// ===== 播放控制 =====
function playCurrentStep() {
  if (!isPlaying.value) return;
  if (currentPlayIndex >= currentSteps.value.length) {
    stopPlay();
    return;
  }
  
  currentStepIndex.value = currentPlayIndex;
  goToStep(currentPlayIndex);
  currentPlayIndex++;
  
  const interval = Math.round(1500 / currentSpeed.value);
  playTimer = window.setTimeout(playCurrentStep, interval);
}

function startPlay() {
  if (currentSteps.value.length === 0) return;
  
  // 如果正在播放，先停止
  if (isPlaying.value) {
    stopPlay();
  }
  
  // 从头开始
  currentStepIndex.value = -1;
  currentNodes.value = [];
  bounds.value = { width: 500, height: 350 };
  highlightedNodeIds.value = [];
  isPlaying.value = true;
  currentPlayIndex = 0;
  
  // 使用 nextTick 确保 DOM 更新后再开始
  nextTick(() => {
    playCurrentStep();
  });
}

function stopPlay() {
  isPlaying.value = false;
  if (playTimer !== null) {
    clearTimeout(playTimer);
    playTimer = null;
  }
  if (playAbortController) {
    playAbortController.abort();
    playAbortController = null;
  }
}

// ===== 计算属性 =====
const currentStep = computed(() =>
  currentStepIndex.value >= 0 && currentStepIndex.value < currentSteps.value.length
    ? currentSteps.value[currentStepIndex.value]
    : null,
);

const progressPercent = computed(() => {
  if (currentSteps.value.length === 0) return 0;
  return ((currentStepIndex.value + 1) / currentSteps.value.length) * 100;
});

const redCount = computed(() =>
  currentNodes.value.filter((n) => n.color === 'RED').length,
);

const blackCount = computed(() =>
  currentNodes.value.filter((n) => n.color === 'BLACK' && n.value !== -1).length,
);

const violatedRules = ref<RuleViolation[]>([]);

const stepIcon = computed(() => {
  if (!currentStep.value) return 'hash';
  const icons: Record<StepType, string> = {
    insert: 'file-plus',
    'color-change': 'palette',
    'rotate-left': 'rotate-ccw',
    'rotate-right': 'rotate-ccw',
    'set-root': 'flag',
    'clear-nil': 'x-circle',
    'rule-violation': 'alert-circle',
    'rule-fixed': 'check-circle',
  };
  return icons[currentStep.value.type] || 'hash';
});

const stepBadgeVariant = computed(() => {
  if (!currentStep.value) return 'default'
  const variants: Record<StepType, string> = {
    insert: 'primary',
    'color-change': 'warning',
    'rotate-left': 'success',
    'rotate-right': 'success',
    'set-root': 'primary',
    'clear-nil': 'default',
    'rule-violation': 'danger',
    'rule-fixed': 'success',
  }
  return variants[currentStep.value.type] || 'default'
})

const speeds = [0.5, 1, 2];

// ===== 派生：边信息 =====
const edges = computed(() => {
  const result: { from: number; to: number; path: string }[] = [];
  for (const node of currentNodes.value) {
    if (node.left) {
      result.push({
        from: node.id,
        to: node.left.id,
        path: `M ${node.x} ${node.y} L ${node.left.x} ${node.left.y}`,
      });
    }
    if (node.right) {
      result.push({
        from: node.id,
        to: node.right.id,
        path: `M ${node.x} ${node.y} L ${node.right.x} ${node.right.y}`,
      });
    }
  }
  return result;
});

// ===== 方法 =====
function getNodeClass(node: RBNode) {
  return {
    'node-highlighted': isNodeHighlighted(node.id),
  };
}

function isNodeHighlighted(nodeId: number): boolean {
  return highlightedNodeIds.value.includes(nodeId);
}

function stepTypeLabel(type: StepType): string {
  const labels: Record<StepType, string> = {
    insert: '插入节点',
    'color-change': '颜色变更',
    'rotate-left': '左旋操作',
    'rotate-right': '右旋操作',
    'set-root': '设置根节点',
    'clear-nil': '清除 Nil',
    'rule-violation': '规则违规',
    'rule-fixed': '规则修复',
  };
  return labels[type] || type;
}

function updateTreeFromSnapshot(snapshot?: SnapshotNode[]) {
  if (!snapshot || snapshot.length === 0) {
    currentNodes.value = [];
    bounds.value = { width: 500, height: 350 };
    return;
  }
  // 将快照（扁平 ID 引用）转换为可视化用的 RBNode[]（对象引用）
  currentNodes.value = RedBlackTree.snapshotToRBNode(snapshot);
  // 计算边界
  const maxX = Math.max(...snapshot.map((n) => n.x)) + 80;
  const maxY = Math.max(...snapshot.map((n) => n.y)) + 80;
  bounds.value = { width: Math.max(500, maxX), height: Math.max(350, maxY) };
}

function handleInsert() {
  const value = parseInt(inputValue.value, 10);
  if (isNaN(value)) {
    notify('请输入有效的节点值', 'error');
    return;
  }
  if (value < 0 || value > 999) {
    notify('节点值范围应在 0-999 之间', 'error');
    return;
  }

  const steps = tree.value.insert(value);
  if (steps.length === 0) {
    notify('插入操作未产生步骤', 'warning');
    return;
  }

  // 追加到现有步骤
  currentSteps.value.push(...steps);
  currentStepIndex.value = currentSteps.value.length - steps.length;

  // 重置高亮
  highlightedNodeIds.value = [];
  updateTreeFromSnapshot(currentSteps.value[currentStepIndex.value]?.snapshot);

  // 设置当前步骤的高亮
  const step = currentSteps.value[currentStepIndex.value];
  if (step) {
    highlightedNodeIds.value = step.nodeIds;
  }

  inputValue.value = '';
  notify(`节点 ${value} 插入成功，产生 ${steps.length} 个操作步骤`, 'success');
}

function handleLoadSample() {
  if (isPlaying.value) return;
  tree.value.clear();
  currentSteps.value = [];
  currentStepIndex.value = -1;
  highlightedNodeIds.value = [];

  // 加载示例序列
  const sampleValues = [10, 5, 15, 3, 7, 12, 20, 2, 4, 8, 11, 14, 18, 25];
  notify(`开始加载 ${sampleValues.length} 个示例节点...`, 'info');

  let delay = 300;
  sampleValues.forEach((val) => {
    setTimeout(() => {
      const steps = tree.value.insert(val);
      if (steps.length > 0) {
        const globalStepIndex = currentSteps.value.length;
        currentSteps.value.push(...steps);

        // 自动播放：跳到新插入的第一个步骤
        if (globalStepIndex === 0) {
          currentStepIndex.value = 0;
          updateTreeFromSnapshot(currentSteps.value[0]?.snapshot);
          highlightedNodeIds.value = currentSteps.value[0]?.nodeIds ?? [];
        }
      }
    }, delay);
    delay += 400;
  });

  setTimeout(() => {
    notify('示例加载完成！点击播放按钮查看完整演示', 'success');
  }, delay);
}

function handleClear() {
  if (isPlaying.value) return;
  tree.value.clear();
  currentSteps.value = [];
  currentStepIndex.value = -1;
  highlightedNodeIds.value = [];
  currentNodes.value = [];
  bounds.value = { width: 500, height: 350 };
  violatedRules.value = [];
  notify('红黑树已清空', 'info');
}

function handleValidate() {
  const violations = tree.value.validate();
  violatedRules.value = violations;
  if (violations.length === 0) {
    notify('✅ 红黑树满足所有规则！', 'success');
  } else {
    notify(`⚠️ 检测到 ${violations.length} 个规则违规`, 'error');
  }
}

function goToStep(index: number) {
  if (index < 0 || index >= currentSteps.value.length) return;
  currentStepIndex.value = index;
  const step = currentSteps.value[index];
  if (step?.snapshot) {
    updateTreeFromSnapshot(step.snapshot);
  }
  highlightedNodeIds.value = step?.nodeIds ?? [];
}

function nextStep() {
  if (currentStepIndex.value < currentSteps.value.length - 1) {
    currentStepIndex.value++;
    goToStep(currentStepIndex.value);
  } else {
    stopPlay();
  }
}

function prevStep() {
  if (currentStepIndex.value > 0) {
    currentStepIndex.value--;
    goToStep(currentStepIndex.value);
  }
}

function togglePlay() {
  if (isPlaying.value) {
    stopPlay();
  } else {
    startPlay();
  }
}

function handleProgressClick(e: MouseEvent) {
  if (!progressRef.value || currentSteps.value.length === 0) return;
  const rect = progressRef.value.getBoundingClientRect();
  const ratio = (e.clientX - rect.left) / rect.width;
  const index = Math.round(ratio * (currentSteps.value.length - 1));
  goToStep(index);
}

// 监听当前步骤变化，自动更新高亮
watch(currentStepIndex, (newIndex) => {
  if (newIndex >= 0 && newIndex < currentSteps.value.length) {
    const step = currentSteps.value[newIndex];
    highlightedNodeIds.value = step?.nodeIds ?? [];
  }
});

onMounted(() => {
  // 初始化空树
  currentNodes.value = [];
});
</script>

<style scoped>
/* ===== 页面布局 ===== */
.rbt-page {
  min-height: calc(100vh - 60px);
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.rbt-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
}

.header-left {
  max-width: 600px;
}

.page-title {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', serif;
  font-size: 28px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 6px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

.header-controls {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.input-group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.node-input {
  width: 120px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-xs);
  font-size: 14px;
  color: var(--kb-foreground);
  background: white;
  outline: none;
  transition: border-color 0.2s;
}

.node-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px var(--kb-primary-soft);
}

.action-group {
  display: flex;
  gap: 8px;
}

/* ===== 主体区 ===== */
.rbt-main {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
  flex: 1;
  min-height: 0;
}

/* ===== 树可视化 ===== */
.tree-container {
  background: white;
  border-radius: var(--kb-radius-xs);
  box-shadow: var(--shadow-dropdown-2);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 500px;
}

.tree-svg-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #fafbfc 0%, #f4f6f9 100%);
  border-radius: var(--kb-radius-xs);
  border: 1px solid var(--kb-border);
  overflow: hidden;
  min-height: 400px;
  position: relative;
}

.tree-svg {
  width: 100%;
  height: 100%;
  max-height: 520px;
}

/* 连线 */
.tree-edge {
  stroke: #cbd5e1;
  stroke-width: 2;
  fill: none;
  transition: stroke 0.3s, stroke-width 0.3s;
}

.tree-edge.highlight-edge {
  stroke: var(--kb-primary);
  stroke-width: 3;
  filter: drop-shadow(0 0 4px var(--kb-primary-soft));
}

/* 节点 */
.tree-node {
  cursor: pointer;
  transition: transform 0.3s ease;
}

.node-shadow {
  fill: transparent;
}

.node-shadow.shadow-red {
  fill: #dc2626;
  opacity: 0.3;
}

.node-shadow.shadow-black {
  fill: #1e293b;
  opacity: 0.3;
}

.node-circle {
  stroke-width: 3;
  transition: fill 0.3s ease, stroke 0.3s ease, r 0.3s ease;
}

.node-circle.node-red {
  fill: #dc2626;
  stroke: #b91c1c;
}

.node-circle.node-black {
  fill: #1e293b;
  stroke: #0f172a;
}

.node-circle.highlight-ring {
  stroke: #fbbf24;
  stroke-width: 4;
  filter: drop-shadow(0 0 8px rgba(251, 191, 36, 0.6));
}

.node-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 14px;
  font-weight: 700;
  fill: white;
  user-select: none;
  pointer-events: none;
}

.node-value.value-white {
  fill: white;
}

/* 图例 */
.tree-legend {
  display: flex;
  gap: 20px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: var(--kb-radius-xs);
  border: 1px solid var(--kb-border);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.red-dot {
  background: #dc2626;
}

.black-dot {
  background: #1e293b;
}

/* ===== 节点统计 ===== */
.tree-stats {
  display: flex;
  gap: 16px;
  padding: 8px 16px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.red-dot-inline {
  width: 10px;
  height: 10px;
  background: #dc2626;
  border-radius: 50%;
}

.black-dot-inline {
  width: 10px;
  height: 10px;
  background: #1e293b;
  border-radius: 50%;
}

/* ===== 信息面板 ===== */
.info-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  max-height: calc(100vh - 120px);
}

/* 步骤卡片 */
.step-card {
  border-left: 4px solid var(--kb-primary);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.card-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
  flex: 1;
}

.step-icon {
  color: var(--kb-primary);
}

.step-badge {
  font-size: 12px;
}

.step-content {
  padding: 8px 0;
}

.step-description {
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-foreground);
  margin: 0 0 12px;
}

.rule-tag {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rule-name {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* 播放控制 */
.control-card {
  background: white;
  border-radius: var(--kb-radius-xs);
  box-shadow: var(--shadow-dropdown-2);
}

.playback-controls {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.control-buttons {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
}

.ctrl-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-xs);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: all 0.2s;
}

.ctrl-btn:hover:not(:disabled) {
  background: var(--kb-primary);
  color: white;
  border-color: var(--kb-primary);
}

.ctrl-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ctrl-btn.play-btn {
  width: 48px;
  height: 48px;
  background: var(--kb-primary);
  color: white;
  border-color: var(--kb-primary);
}

.ctrl-btn.play-btn:hover:not(:disabled) {
  background: var(--kb-primary-soft);
  border-color: var(--kb-primary);
}

.progress-bar-wrapper {
  padding: 8px 0;
}

.progress-track {
  position: relative;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  cursor: pointer;
  overflow: visible;
}

.progress-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, var(--kb-primary), #FF6B35);
  border-radius: 4px;
  transition: width 0.3s;
}

.progress-marker {
  position: absolute;
  top: 50%;
  width: 10px;
  height: 10px;
  background: white;
  border: 2px solid #94a3b8;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  cursor: pointer;
  transition: all 0.2s;
}

.progress-marker.active {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px var(--kb-primary-soft);
  transform: translate(-50%, -50%) scale(1.3);
}

.speed-control {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
}

.speed-label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.speed-btn {
  padding: 4px 10px;
  font-size: 12px;
  background: #f1f5f9;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-xs);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.2s;
}

.speed-btn.active {
  background: var(--kb-primary);
  color: white;
  border-color: var(--kb-primary);
}

/* ===== 规则面板 ===== */
.rules-card {
  background: white;
  border-radius: var(--kb-radius-xs);
  box-shadow: var(--shadow-dropdown-2);
}

.rules-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-xs);
  transition: all 0.2s;
  position: relative;
}

.rule-item.highlighted {
  background: var(--kb-primary-soft);
  border-color: var(--kb-primary);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.15);
}

.rule-item.violated {
  background: #fef2f2;
  border-color: #fca5a5;
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  75% { transform: translateX(3px); }
}

.rule-num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  background: var(--kb-primary);
  color: white;
  font-size: 11px;
  font-weight: 700;
  border-radius: 50%;
  flex-shrink: 0;
}

.rule-item.violated .rule-num {
  background: #dc2626;
}

.rule-item.highlighted .rule-num {
  background: var(--kb-primary);
  box-shadow: 0 0 0 3px var(--kb-primary-soft);
}

.rule-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rule-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.rule-detail {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
}

.rule-warning {
  color: #dc2626;
  flex-shrink: 0;
}

.rule-ok {
  color: #10b981;
  flex-shrink: 0;
}

/* 违规面板 */
.violations-panel {
  margin-top: 12px;
  padding: 12px;
  background: #fef2f2;
  border: 1px solid #fca5a5;
  border-radius: var(--kb-radius-xs);
}

.violations-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #dc2626;
  margin: 0 0 8px;
}

.violations-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.violations-list li {
  font-size: 12px;
  color: #7f1d1d;
  line-height: 1.5;
}

.violation-rule {
  display: inline-block;
  padding: 1px 6px;
  background: #dc2626;
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 3px;
  margin-right: 6px;
}

.no-violations {
  margin-top: 12px;
  padding: 12px;
  background: #f0fdf4;
  border: 1px solid #86efac;
  border-radius: var(--kb-radius-xs);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #166534;
}

/* ===== 操作历史 ===== */
.history-card {
  background: white;
  border-radius: var(--kb-radius-xs);
  box-shadow: var(--shadow-dropdown-2);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 200px;
  overflow-y: auto;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f8fafc;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-xs);
  cursor: pointer;
  transition: all 0.2s;
}

.history-item:hover {
  background: var(--kb-primary-soft);
  border-color: var(--kb-primary);
}

.history-item.active {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
}

.history-step-num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  background: #cbd5e1;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
  border-radius: 50%;
  flex-shrink: 0;
}

.history-item.active .history-step-num {
  background: white;
  color: var(--kb-primary);
}

.history-type {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.history-item.active .history-type {
  color: white;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .rbt-main {
    grid-template-columns: 1fr;
  }
  .info-panel {
    max-height: none;
  }
}
</style>
