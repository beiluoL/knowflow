<template>
  <transition name="sidebar-slide">
    <aside v-if="visible" class="graph-sidebar">
      <header class="sidebar-head">
        <div class="title-row">
          <Icon name="network" :size="16" aria-hidden="true" />
          <span class="sidebar-title">知识图谱</span>
        </div>
        <button type="button" class="close-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="$emit('close')">
          <Icon name="x" :size="14" />
        </button>
      </header>

      <nav class="tab-nav">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          class="tab-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ active: activeTab === tab.id }"
          @click="activeTab = tab.id"
        >
          <Icon :name="tab.icon" :size="14" aria-hidden="true" />
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <section class="tab-content">
        <div v-if="activeTab === 'concept'" class="tab-panel">
          <div class="concept-input-row">
            <input
              v-model="conceptInput"
              type="text"
              class="concept-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              placeholder="输入概念名，生成图解"
              @keyup.enter="handleGenerateConcept"
            />
        <button
          type="button"
          class="generate-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :disabled="conceptLoading"
          @click="handleGenerateConcept"
        >
              <Icon v-if="!conceptLoading" name="sparkles" :size="14" aria-hidden="true" />
              <Icon v-else name="loader" :size="14" class="spin" aria-hidden="true" />
              <span>生成</span>
            </button>
          </div>

          <div v-if="conceptLoading" class="skeleton-wrap">
            <SkeletonList :rows="5" />
          </div>
          <div v-else-if="conceptDiagram" class="concept-result">
            <div class="concept-head">
              <span class="diagram-type">{{ conceptDiagram.diagramType }}</span>
              <span class="concept-name">{{ conceptDiagram.concept }}</span>
            </div>

            <div v-if="conceptDiagram.keyPoints?.length" class="kp-section">
              <div class="section-label">关键点</div>
              <div class="chips-wrap">
                <span
                  v-for="(kp, idx) in conceptDiagram.keyPoints"
                  :key="idx"
                  class="chip kp-chip"
                >{{ kp }}</span>
              </div>
            </div>

            <div class="desc-section">
              <div class="section-label">描述</div>
              <p class="desc-text">{{ conceptDiagram.description }}</p>
            </div>

            <div v-if="conceptDiagram.explanation" class="expl-section">
              <button
                type="button"
                class="expand-toggle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                @click="explExpanded = !explExpanded"
              >
                <Icon :name="explExpanded ? 'chevron-down' : 'chevron-right'" :size="14" aria-hidden="true" />
                <span>详细解释</span>
              </button>
              <div v-show="explExpanded" class="expl-text">
                {{ conceptDiagram.explanation }}
              </div>
            </div>

            <div v-if="conceptDiagram.relatedConcepts?.length" class="rel-section">
              <div class="section-label">相关概念</div>
              <div class="chips-wrap">
                <span
                  v-for="(rc, idx) in conceptDiagram.relatedConcepts"
                  :key="idx"
                  class="chip rel-chip focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                  role="button"
                  tabindex="0"
                  @click="handleRelatedClick(rc)"
                  @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
                >{{ rc }}</span>
              </div>
            </div>

            <div v-if="conceptDiagram.mermaidCode" class="mermaid-section">
              <div class="section-label">Mermaid 代码</div>
              <pre class="mermaid-pre focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" role="button" tabindex="0" @click="handleCopyMermaid" @keydown.enter.prevent.self="($event.target as HTMLElement).click()">{{ conceptDiagram.mermaidCode }}</pre>
              <p class="mermaid-hint">点击代码可复制，可粘贴到 Mermaid 查看器渲染</p>
            </div>
          </div>
          <div v-else class="empty-state">
            <Icon name="lightbulb" :size="32" aria-hidden="true" />
            <span>输入一个概念，生成可视化图解</span>
          </div>
        </div>

        <div v-else-if="activeTab === 'entity'" class="tab-panel">
          <div v-if="entityLoading" class="skeleton-wrap">
            <SkeletonList :rows="8" />
          </div>
          <div v-else-if="entityGraph && entityGraph.nodes.length" class="entity-result">
            <div class="stat-row">
              <div class="stat-item">
                <span class="stat-num">{{ entityGraph.nodes.length }}</span>
                <span class="stat-label">节点</span>
              </div>
              <div class="stat-item">
                <span class="stat-num">{{ entityGraph.edges.length }}</span>
                <span class="stat-label">关系</span>
              </div>
            </div>
            <div class="node-list">
              <div
                v-for="node in entityNodesPreview"
                :key="node.id"
                class="entity-node-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                role="button"
                tabindex="0"
                @click="handleEntityNodeClick(node.name)"
                @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
              >
                <div class="node-head">
                  <span class="node-name">{{ node.name }}</span>
                  <span class="type-badge" :class="'type-' + node.type.toLowerCase()">{{ node.type }}</span>
                </div>
                <p v-if="node.description" class="node-desc">{{ truncate(node.description, 80) }}</p>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <Icon name="database" :size="32" aria-hidden="true" />
            <span>暂未抽取实体知识图谱</span>
          </div>
        </div>

        <div v-else-if="activeTab === 'doc'" class="tab-panel">
          <div v-if="docLoading" class="skeleton-wrap">
            <SkeletonList :rows="8" />
          </div>
          <div v-else-if="docGraph && docGraph.nodes.length" class="doc-result">
            <div class="section-label">节点列表（前 20）</div>
            <ul class="doc-node-list">
              <li
                v-for="node in docNodesPreview"
                :key="node.id"
                class="doc-node-item"
                :style="{ paddingLeft: (getDepth(node.id) * 12 + 12) + 'px' }"
              >
                <Icon
                  :name="node.type === 'category' ? 'folder' : 'file-text'"
                  :size="14"
                  :color="node.type === 'category' ? 'var(--kb-warning)' : 'var(--kb-primary)'"
                  aria-hidden="true"
                />
                <span class="doc-node-name">{{ node.label }}</span>
                <span class="doc-node-type">{{ node.type }}</span>
              </li>
            </ul>
            <div class="section-label" style="margin-top: 16px;">关系列表（前 15）</div>
            <ul class="doc-edge-list">
              <li
                v-for="(edge, idx) in docEdgesPreview"
                :key="idx"
                class="doc-edge-item"
              >
                <span class="edge-rel" :class="'rel-' + edge.relation">{{ edge.relation }}</span>
                <span class="edge-text">{{ getNodeLabel(edge.source) }} → {{ getNodeLabel(edge.target) }}</span>
              </li>
            </ul>
          </div>
          <div v-else class="empty-state">
            <Icon name="folder-tree" :size="32" aria-hidden="true" />
            <span>暂无文档层级图谱</span>
          </div>
        </div>
      </section>
    </aside>
  </transition>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import SkeletonList from '@/components/ui/SkeletonList.vue';
import {
  knowledgeApi,
  type ConceptDiagramVO,
  type EntityGraphVO,
  type KnowledgeGraphVO,
  type EntityNodeVO,
} from '@/api';
import { notify, getApiError } from '@/utils/toast';

interface Props {
  visible: boolean;
  docId?: number;
  categoryId?: number;
}
const props = defineProps<Props>();
const emit = defineEmits<{ (e: 'close'): void }>();

const tabs = [
  { id: 'concept', label: '概念图解', icon: 'lightbulb' },
  { id: 'entity', label: '实体图谱', icon: 'database' },
  { id: 'doc', label: '文档层级', icon: 'folder-tree' },
];

const activeTab = ref<'concept' | 'entity' | 'doc'>('concept');

const conceptInput = ref('');
const conceptLoading = ref(false);
const conceptDiagram = ref<ConceptDiagramVO | null>(null);
const explExpanded = ref(false);

const entityLoading = ref(false);
const entityGraph = ref<EntityGraphVO | null>(null);

const docLoading = ref(false);
const docGraph = ref<KnowledgeGraphVO | null>(null);

const entityNodesPreview = computed(() => (entityGraph.value?.nodes ?? []).slice(0, 30));
const docNodesPreview = computed(() => (docGraph.value?.nodes ?? []).slice(0, 20));
const docEdgesPreview = computed(() => (docGraph.value?.edges ?? []).slice(0, 15));

function truncate(s: string, n: number) {
  return s.length > n ? s.slice(0, n) + '…' : s;
}

function getNodeLabel(id: string) {
  const n = docGraph.value?.nodes.find(x => x.id === id);
  return n?.label ?? id;
}

function getDepth(id: string): number {
  if (!docGraph.value) return 0;
  let depth = 0;
  let current = id;
  const visited = new Set<string>();
  while (true) {
    if (visited.has(current)) break;
    visited.add(current);
    const parentEdge = docGraph.value.edges.find(e => e.target === current && e.relation === 'parent');
    if (!parentEdge) break;
    depth++;
    current = parentEdge.source;
  }
  return Math.min(depth, 5);
}

async function handleGenerateConcept() {
  const concept = conceptInput.value.trim();
  if (!concept) {
    notify('请输入概念名', 'warning');
    return;
  }
  conceptLoading.value = true;
  conceptDiagram.value = null;
  try {
    conceptDiagram.value = await knowledgeApi.conceptDiagram(concept);
  } catch (e: unknown) {
    notify(getApiError(e, '生成概念图解失败'), 'error');
  } finally {
    conceptLoading.value = false;
  }
}

function handleRelatedClick(rc: string) {
  conceptInput.value = rc;
  void handleGenerateConcept();
}

function handleEntityNodeClick(name: string) {
  activeTab.value = 'concept';
  conceptInput.value = name;
  void handleGenerateConcept();
}

function handleCopyMermaid() {
  if (!conceptDiagram.value?.mermaidCode) return;
  try {
    if (navigator?.clipboard?.writeText) {
      void navigator.clipboard.writeText(conceptDiagram.value.mermaidCode);
      notify('Mermaid 代码已复制', 'success');
    } else {
      notify('浏览器不支持剪贴板复制，请手动选择复制', 'info');
    }
  } catch {
    notify('复制失败，请手动选择复制', 'warning');
  }
}

async function loadEntityGraph() {
  entityLoading.value = true;
  try {
    entityGraph.value = await knowledgeApi.entityGraph(props.categoryId, props.docId);
  } catch (e: unknown) {
    notify(getApiError(e, '加载实体图谱失败'), 'warning');
    entityGraph.value = null;
  } finally {
    entityLoading.value = false;
  }
}

async function loadDocGraph() {
  docLoading.value = true;
  try {
    docGraph.value = await knowledgeApi.graph();
  } catch (e: unknown) {
    notify(getApiError(e, '加载文档层级失败'), 'warning');
    docGraph.value = null;
  } finally {
    docLoading.value = false;
  }
}

watch(
  () => props.visible,
  (v) => {
    if (!v) return;
    if (activeTab.value === 'entity' && !entityGraph.value) {
      void loadEntityGraph();
    }
    if (activeTab.value === 'doc' && !docGraph.value) {
      void loadDocGraph();
    }
  },
);

watch(activeTab, (tab) => {
  if (!props.visible) return;
  if (tab === 'entity' && !entityGraph.value) {
    void loadEntityGraph();
  }
  if (tab === 'doc' && !docGraph.value) {
    void loadDocGraph();
  }
});
</script>

<style scoped>
.sidebar-slide-enter-active,
.sidebar-slide-leave-active {
  transition: transform 0.3s ease, opacity 0.25s ease;
}
.sidebar-slide-enter-from,
.sidebar-slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

.graph-sidebar {
  position: fixed;
  right: 0;
  top: 64px;
  bottom: 88px;
  width: 320px;
  z-index: 60;
  background: var(--kb-bg-2, rgba(15, 23, 42, 0.72));
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border-left: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  display: flex;
  flex-direction: column;
  box-shadow: -12px 0 32px rgba(0, 0, 0, 0.3);
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
}
.title-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--kb-foreground);
  font-weight: 600;
  font-size: 14px;
}
.sidebar-title {
  font-size: 15px;
}
.close-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.close-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--kb-foreground);
}

.tab-nav {
  display: flex;
  gap: 4px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
}
.tab-btn {
  flex: 1;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.tab-btn:hover {
  background: rgba(255, 255, 255, 0.04);
  color: var(--kb-foreground);
}
.tab-btn.active {
  background: color-mix(in srgb, var(--kb-primary) 14%, transparent);
  color: var(--kb-primary);
  border-color: color-mix(in srgb, var(--kb-primary) 25%, transparent);
}

.tab-content {
  flex: 1;
  overflow-y: auto;
  padding: 14px 14px 16px;
}
.tab-content::-webkit-scrollbar {
  width: 6px;
}
.tab-content::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}

.tab-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.concept-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
}
.concept-input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  font-size: 13px;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  outline: none;
  transition: border-color 0.15s;
}
.concept-input:focus {
  border-color: var(--kb-primary);
}
.generate-btn {
  height: 38px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--kb-primary);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: filter 0.15s;
  flex-shrink: 0;
}
.generate-btn:hover:not(:disabled) {
  filter: brightness(1.06);
}
.generate-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.concept-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.concept-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.diagram-type {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--kb-primary) 16%, transparent);
  color: var(--kb-primary);
  font-weight: 600;
  letter-spacing: 0.02em;
}
.concept-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--kb-foreground);
}
.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.04em;
  margin-bottom: 6px;
}
.chips-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 999px;
  line-height: 1;
}
.kp-chip {
  background: rgba(14, 165, 233, 0.12);
  color: #38BDF8;
  border: 1px solid rgba(14, 165, 233, 0.22);
}
.rel-chip {
  background: rgba(139, 92, 246, 0.1);
  color: #A78BFA;
  border: 1px solid rgba(139, 92, 246, 0.2);
  cursor: pointer;
  transition: all 0.15s;
}
.rel-chip:hover {
  background: rgba(139, 92, 246, 0.2);
}
.desc-text {
  margin: 0;
  font-size: 13px;
  color: var(--kb-foreground);
  line-height: 1.6;
}
.expand-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  background: none;
  border: none;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  margin-bottom: 6px;
}
.expand-toggle:hover {
  color: var(--kb-primary);
}
.expl-text {
  padding: 10px 12px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 10px;
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
}
.mermaid-pre {
  max-height: 180px;
  overflow: auto;
  padding: 10px 12px;
  margin: 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: #94A3B8;
  background: #0B1120;
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  cursor: pointer;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-all;
}
.mermaid-pre:hover {
  border-color: var(--kb-primary);
}
.mermaid-hint {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}

.entity-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.stat-row {
  display: flex;
  gap: 12px;
}
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 10px;
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--kb-primary);
  line-height: 1.1;
}
.stat-label {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  margin-top: 4px;
}
.node-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.entity-node-card {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.entity-node-card:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: color-mix(in srgb, var(--kb-primary) 30%, transparent);
}
.node-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.node-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.type-badge {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 0.02em;
  flex-shrink: 0;
}
.type-concept { background: rgba(59, 130, 246, 0.15); color: #60A5FA; }
.type-technique { background: rgba(16, 185, 129, 0.15); color: #34D399; }
.type-term { background: rgba(245, 158, 11, 0.15); color: #FBBF24; }
.type-principle { background: rgba(239, 68, 68, 0.15); color: #F87171; }
.type-tool { background: rgba(139, 92, 246, 0.15); color: #A78BFA; }
.type-other { background: rgba(148, 163, 184, 0.15); color: #94A3B8; }
.node-desc {
  margin: 0;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
}

.doc-result {
  display: flex;
  flex-direction: column;
}
.doc-node-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.doc-node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  font-size: 12.5px;
  color: var(--kb-foreground);
  border-radius: 8px;
  transition: background 0.15s;
}
.doc-node-item:hover {
  background: rgba(255, 255, 255, 0.04);
}
.doc-node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-node-type {
  font-size: 10px;
  color: var(--kb-muted-foreground);
  opacity: 0.7;
  flex-shrink: 0;
}
.doc-edge-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.doc-edge-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 8px;
  font-size: 11.5px;
}
.edge-rel {
  flex-shrink: 0;
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 6px;
  font-weight: 600;
}
.rel-parent { background: rgba(59, 130, 246, 0.15); color: #60A5FA; }
.rel-contains { background: rgba(16, 185, 129, 0.15); color: #34D399; }
.edge-text {
  color: var(--kb-foreground);
  opacity: 0.88;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skeleton-wrap {
  padding: 4px 0;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px 16px;
  color: var(--kb-muted-foreground);
  font-size: 12.5px;
  text-align: center;
  opacity: 0.75;
}

@media (max-width: 640px) {
  .graph-sidebar {
    width: 88vw;
  }
}
</style>
