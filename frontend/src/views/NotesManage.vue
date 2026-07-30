<template>
  <div class="notes-layout">
    <!-- 左栏：文件夹 + 笔记列表 -->
    <aside
      class="notes-sidebar"
      :class="{ collapsed: sidebarCollapsed }"
    >
      <!-- 侧边栏头部：标题 + 折叠按钮 + 新建 -->
      <div class="sidebar-header">
        <button
          type="button"
          class="collapse-btn"
          @click="sidebarCollapsed = !sidebarCollapsed"
          title="折叠侧边栏"
        >
          <Icon :name="sidebarCollapsed ? 'chevron-right' : 'chevron-left'" :size="14" />
        </button>
        <template v-if="!sidebarCollapsed">
          <h2 class="sidebar-title">
            <Icon name="bookmark" :size="16" />
            <span>我的笔记</span>
          </h2>
          <button
            type="button"
            class="new-note-btn"
            title="新建笔记"
            @click="router.push('/notes/new')"
          >
            <Icon name="plus" :size="14" />
          </button>
        </template>
      </div>

      <!-- 标签分组 -->
      <template v-if="!sidebarCollapsed">
        <div class="sidebar-section">
          <p class="section-label">标签分组</p>
          <div class="tag-list">
            <button
              v-for="tag in tags"
              :key="tag"
              type="button"
              class="tag-btn"
              :class="{ active: activeTag === tag }"
              @click="activeTag = tag"
            >
              <Icon :name="getTagIcon(tag)" :size="14" class="tag-icon" />
              <span class="flex-1 truncate">{{ tag }}</span>
              <span class="tag-count">{{ getTagCount(tag) }}</span>
            </button>
          </div>
        </div>

        <div class="sidebar-divider"></div>

        <!-- 笔记搜索 -->
        <div class="sidebar-section">
          <div class="search-box">
            <Icon name="search" :size="14" class="search-icon" />
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索笔记..."
              class="search-input"
            />
          </div>
        </div>

        <!-- 笔记列表 -->
        <div class="note-list-section">
          <div class="list-header">
            <span class="list-title">{{ activeTag }}</span>
            <span class="list-count">{{ filteredNotes.length }} 篇</span>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="note-list-loading">
            <div
              v-for="i in 4"
              :key="i"
              class="skeleton-item"
            >
              <div class="skeleton-line w-3/4 mb-2"></div>
              <div class="skeleton-line w-full mb-2"></div>
              <div class="skeleton-line w-1/3"></div>
            </div>
          </div>

          <!-- 空态 -->
          <div v-else-if="filteredNotes.length === 0" class="note-list-empty">
            <Icon name="inbox" :size="24" class="empty-icon" />
            <p>{{ searchKeyword ? '没有匹配的笔记' : '暂无笔记' }}</p>
          </div>

          <!-- 笔记列表 -->
          <div v-else class="note-list">
            <button
              v-for="note in filteredNotes"
              :key="note.id"
              type="button"
              class="note-item"
              :class="{ active: selectedId === note.id }"
              @click="selectNote(note.id)"
            >
              <div class="note-item-icon" :style="{ background: tagColor(note.tag).backgroundColor }">
                <Icon :name="getTagIcon(note.tag)" :size="13" :style="{ color: tagColor(note.tag).color }" />
              </div>
              <div class="note-item-main">
                <h4 class="note-item-title">{{ note.title || '无标题笔记' }}</h4>
                <p class="note-item-summary">{{ note.summary || '暂无内容...' }}</p>
                <div class="note-item-meta">
                  <span class="note-item-tag" :style="{ backgroundColor: tagColor(note.tag).backgroundColor, color: tagColor(note.tag).color }">{{ note.tag }}</span>
                  <span class="note-item-time">{{ note.time }}</span>
                </div>
              </div>
            </button>
          </div>
        </div>
      </template>

      <!-- 折叠态下仅显示图标 -->
      <template v-else>
        <div class="collapsed-actions">
          <button
            v-for="tag in tags"
            :key="tag"
            type="button"
            class="collapsed-icon-btn"
            :class="{ active: activeTag === tag }"
            :title="tag"
            @click="activeTag = tag"
          >
            <Icon :name="getTagIcon(tag)" :size="16" />
          </button>
          <div class="collapsed-divider"></div>
          <button
            type="button"
            class="collapsed-icon-btn"
            title="新建笔记"
            @click="router.push('/notes/new')"
          >
            <Icon name="plus" :size="16" />
          </button>
        </div>
      </template>
    </aside>

    <!-- 右栏：笔记详情 -->
    <section class="notes-content">
      <!-- 内容顶部工具栏 -->
      <div class="content-toolbar">
        <div class="toolbar-left">
          <span class="breadcrumb-item">我的笔记</span>
          <Icon name="chevron-right" :size="12" class="sep" />
          <span class="breadcrumb-item" :style="{ color: 'var(--kb-primary)' }">{{ current?.tag || activeTag }}</span>
          <template v-if="current">
            <Icon name="chevron-right" :size="12" class="sep" />
            <span class="breadcrumb-current">{{ current.title || '无标题笔记' }}</span>
          </template>
        </div>
        <div class="toolbar-right" v-if="current">
          <span class="toolbar-meta">
            <Icon name="edit-3" :size="13" />
            {{ current.words }} 字
          </span>
          <span class="toolbar-meta">
            <Icon name="clock" :size="13" />
            {{ current.time }}
          </span>
          <button
            type="button"
            class="toolbar-btn primary"
            @click="router.push(`/notes/${current.id}/edit`)"
          >
            <Icon name="edit" :size="14" />
            <span>编辑</span>
          </button>
          <button
            type="button"
            class="toolbar-btn ghost"
            title="新建笔记"
            @click="router.push('/notes/new')"
          >
            <Icon name="plus" :size="14" />
          </button>
        </div>
        <div class="toolbar-right" v-else>
          <button
            type="button"
            class="toolbar-btn primary"
            @click="router.push('/notes/new')"
          >
            <Icon name="plus" :size="14" />
            <span>新建笔记</span>
          </button>
        </div>
      </div>

      <!-- 内容主体 -->
      <div class="content-body">
        <!-- 空态 -->
        <div v-if="!current && !loading" class="content-empty">
          <div class="empty-illustration">
            <Icon name="file-text" :size="48" class="empty-illustration-icon" />
          </div>
          <h3 class="empty-title">选择或创建一篇笔记</h3>
          <p class="empty-desc">从左侧选择笔记开始阅读，或点击右上角新建笔记</p>
          <button
            type="button"
            class="empty-new-btn"
            @click="router.push('/notes/new')"
          >
            <Icon name="plus" :size="16" />
            新建笔记
          </button>
        </div>

        <!-- 加载中 -->
        <div v-else-if="loading || !current" class="content-loading">
          <div class="skeleton-block w-1/3 mb-6"></div>
          <div class="skeleton-block w-full mb-4"></div>
          <div class="skeleton-block w-full mb-4"></div>
          <div class="skeleton-block w-2/3"></div>
        </div>

        <!-- 笔记详情 -->
        <article v-else class="note-detail">
          <!-- 标签 -->
          <div class="detail-tags">
            <span
              v-for="t in (current.tags?.length ? current.tags : [current.tag])"
              :key="t"
              class="detail-tag"
            >
              <Icon name="hash" :size="12" />
              {{ t }}
            </span>
          </div>

          <!-- 标题 -->
          <h1 class="detail-title">{{ current.title || '无标题笔记' }}</h1>

          <!-- 正文 -->
          <div
            class="detail-body kb-md-body"
            v-html="renderedContent"
          ></div>

          <!-- 关联文档 -->
          <div v-if="current.related && current.related.length > 0" class="detail-related">
            <div class="related-header">
              <Icon name="link-2" :size="16" />
              <span>关联文档</span>
            </div>
            <div class="related-list">
              <a
                v-for="(rel, idx) in current.related"
                :key="idx"
                href="#"
                class="related-item"
                @click.prevent="goTo('/knowledge')"
              >
                <div class="related-icon">
                  <Icon name="file-text" :size="15" />
                </div>
                <div class="related-info">
                  <p class="related-title">{{ rel.title }}</p>
                  <p class="related-category">{{ rel.category }}</p>
                </div>
                <Icon name="arrow-right" :size="14" class="related-arrow" />
              </a>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { renderMarkdown } from '@/utils/markdown';

const router = useRouter();

interface NoteItem {
  id: number;
  title: string;
  summary: string;
  tag: string;
  time: string;
}

interface NoteDetail extends NoteItem {
  tags: string[];
  words: number;
  content: string;
  related: { title: string; category: string }[];
}

const tags = ['全部', 'Python', 'AI', '前端', '算法'];

interface TagStyle {
  backgroundColor: string;
  color: string;
}

function tagColor(tag: string): TagStyle {
  switch (tag) {
    case 'Python': return { backgroundColor: 'rgba(16,185,129,0.12)', color: '#10B981' };
    case 'AI': return { backgroundColor: 'rgba(59,111,224,0.12)', color: '#3B6FE0' };
    case '前端': return { backgroundColor: 'rgba(245,158,11,0.12)', color: '#F59E0B' };
    case '算法': return { backgroundColor: 'rgba(239,68,68,0.12)', color: '#EF4444' };
    case '全部': return { backgroundColor: 'rgba(107,114,128,0.1)', color: '#6B7280' };
    default: return { backgroundColor: 'rgba(107,114,128,0.1)', color: '#6B7280' };
  }
}

function getTagIcon(tag: string): string {
  switch (tag) {
    case '全部': return 'layers';
    case 'Python': return 'code';
    case 'AI': return 'brain';
    case '前端': return 'layout';
    case '算法': return 'bar-chart-2';
    default: return 'file-text';
  }
}

const sampleNotes: NoteDetail[] = [
  {
    id: 1,
    title: 'Transformer 注意力机制详解',
    summary: 'Multi-Head Attention 的计算流程和代码实现…',
    tag: 'AI',
    time: '示例',
    tags: ['AI', '深度学习'],
    words: 320,
    content: `## Multi-Head Attention 核心原理

自注意力机制（Self-Attention）是 Transformer 的核心组件。它允许模型在处理每个位置时，直接关注输入序列中的所有位置，从而捕捉长距离依赖关系。

\`Attention(Q, K, V) = softmax(QK^T / sqrt(d_k)) * V\`

### 关键步骤

1. 将输入 X 通过三个线性变换得到 Q、K、V 矩阵
2. 计算 Q 和 K 的点积，除以 sqrt(d_k) 进行缩放
3. 对结果应用 softmax 获得注意力权重
4. 用注意力权重对 V 加权求和得到输出
5. 多头并行计算后拼接并通过线性层融合

> 这是一篇示例笔记，点击右上角「编辑」可进入编辑页修改内容。`,
    related: [
      { title: 'Transformer 架构详解与代码实现', category: '人工智能 / 深度学习' },
      { title: 'BERT 模型原理与应用', category: '人工智能 / NLP' },
    ],
  },
];

const allNotes = ref<NoteDetail[]>([]);
const loading = ref(true);
const activeTag = ref('全部');
const searchKeyword = ref('');
const selectedId = ref<number | null>(null);
const sidebarCollapsed = ref(false);

onMounted(() => {
  setTimeout(() => {
    const listRaw = localStorage.getItem('note-list');
    const userNotes: NoteDetail[] = [];
    if (listRaw) {
      try {
        const parsed = JSON.parse(listRaw) as Array<{
          id: number;
          title: string;
          summary?: string;
          tag?: string;
          tags?: string[];
          time: string;
          words?: number;
          content?: string;
        }>;
        for (const n of parsed) {
          userNotes.push({
            id: n.id,
            title: n.title,
            summary: n.summary || (n.content ? n.content.slice(0, 80) : ''),
            tag: n.tag || n.tags?.[0] || '未分类',
            time: n.time,
            tags: n.tags || (n.tag ? [n.tag] : []),
            words: n.words ?? (n.content?.length ?? 0),
            content: n.content || '',
            related: [],
          });
        }
      } catch {
        // ignore
      }
    }
    allNotes.value = [...userNotes, ...sampleNotes];
    selectedId.value = allNotes.value[0]?.id ?? null;
    loading.value = false;
  }, 300);
});

const filteredNotes = computed<NoteItem[]>(() =>
  allNotes.value
    .filter((n) => {
      const matchTag = activeTag.value === '全部' || n.tag === activeTag.value || (n.tags || []).includes(activeTag.value);
      const kw = searchKeyword.value.trim().toLowerCase();
      const matchKw = !kw
        || (n.title || '').toLowerCase().includes(kw)
        || (n.summary || '').toLowerCase().includes(kw);
      return matchTag && matchKw;
    })
    .map((n) => ({ id: n.id, title: n.title, summary: n.summary, tag: n.tag, time: n.time })),
);

const current = computed<NoteDetail | null>(() =>
  allNotes.value.find((n) => n.id === selectedId.value) ?? null,
);

const renderedContent = computed(() => renderMarkdown(current.value?.content || ''));

function getTagCount(tag: string): number {
  if (tag === '全部') return allNotes.value.length;
  return allNotes.value.filter((n) => n.tag === tag || (n.tags || []).includes(tag)).length;
}

function selectNote(id: number): void {
  selectedId.value = id;
}

function goTo(path: string): void {
  router.push(path);
}
</script>

<style scoped>
/* 全屏布局：抵消 CLayout px/py */
.notes-layout {
  display: flex;
  margin: -24px -24px 0;
  height: calc(100vh - 56px);
  min-height: calc(100vh - 56px);
  overflow: hidden;
}

/* ========== 左侧边栏 ========== */
.notes-sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--kb-card);
  border-right: 1px solid var(--kb-border);
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease, min-width 0.2s ease;
  overflow: hidden;
}

.notes-sidebar.collapsed {
  width: 56px;
  min-width: 56px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 12px;
  border-bottom: 1px solid var(--kb-border);
  flex-shrink: 0;
}

.collapse-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
  flex-shrink: 0;
}

.collapse-btn:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}

.sidebar-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
  min-width: 0;
}

.sidebar-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-note-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: opacity 0.15s;
  flex-shrink: 0;
}

.new-note-btn:hover {
  opacity: 0.9;
}

/* 侧边栏区块 */
.sidebar-section {
  padding: 12px 12px 0;
  flex-shrink: 0;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--kb-muted-foreground);
  margin: 0 0 8px;
  padding: 0 4px;
}

/* 标签分组 */
.tag-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tag-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 6px;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
  text-align: left;
  min-width: 0;
}

.tag-btn:hover {
  background: var(--kb-muted);
}

.tag-btn.active {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  font-weight: 500;
}

.tag-icon {
  flex-shrink: 0;
  opacity: 0.8;
}

.tag-count {
  font-size: 11px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: var(--kb-muted);
  padding: 1px 6px;
  border-radius: 10px;
  flex-shrink: 0;
}

.tag-btn.active .tag-count {
  background: rgba(59, 111, 224, 0.15);
  color: var(--kb-primary);
}

.sidebar-divider {
  height: 1px;
  background: var(--kb-border);
  margin: 12px 12px 0;
}

/* 搜索框 */
.search-box {
  position: relative;
}

.search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
}

.search-input {
  width: 100%;
  height: 34px;
  padding-left: 32px;
  padding-right: 10px;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  font-size: 13px;
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.12s, box-shadow 0.12s;
}

.search-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 2px rgba(59, 111, 224, 0.1);
}

/* 笔记列表区块 */
.note-list-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  margin-top: 12px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 8px;
}

.list-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
}

.list-count {
  font-size: 11px;
  color: var(--kb-muted-foreground);
}

.note-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0 8px 12px;
}

.note-list-loading,
.note-list-empty {
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.note-list-empty {
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  gap: 8px;
}

.empty-icon {
  opacity: 0.6;
}

.skeleton-item {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--kb-background);
  animation: pulse 1.5s ease-in-out infinite;
}

.skeleton-line {
  height: 11px;
  background: var(--kb-muted);
  border-radius: 4px;
}

/* 笔记列表项 */
.note-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s;
  text-align: left;
  width: 100%;
  margin-bottom: 2px;
}

.note-item:hover {
  background: var(--kb-muted);
}

.note-item.active {
  background: rgba(59, 111, 224, 0.06);
  border-color: rgba(59, 111, 224, 0.18);
}

.note-item-icon {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}

.note-item-main {
  flex: 1;
  min-width: 0;
}

.note-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-item-summary {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 0 0 6px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.note-item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.note-item-tag {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 4px;
}

.note-item-time {
  font-size: 11px;
  color: var(--kb-muted-foreground);
}

/* 折叠态 */
.collapsed-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  gap: 4px;
}

.collapsed-icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
}

.collapsed-icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.collapsed-icon-btn.active {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}

.collapsed-divider {
  width: 20px;
  height: 1px;
  background: var(--kb-border);
  margin: 6px 0;
}

/* ========== 右侧内容区 ========== */
.notes-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--kb-background);
}

/* 工具栏 */
.content-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 32px;
  border-bottom: 1px solid var(--kb-border);
  background: var(--kb-card);
  flex-shrink: 0;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  min-width: 0;
}

.breadcrumb-item {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.breadcrumb-current {
  color: var(--kb-foreground) !important;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.sep {
  opacity: 0.5;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.toolbar-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--kb-muted);
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.toolbar-btn.primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}

.toolbar-btn.primary:hover {
  opacity: 0.92;
}

.toolbar-btn.ghost {
  background: var(--kb-card);
  color: var(--kb-foreground);
  border-color: var(--kb-border);
}

.toolbar-btn.ghost:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

/* 内容主体 */
.content-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 32px 56px 60px;
}

/* 空态 */
.content-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 40px;
  text-align: center;
}

.empty-illustration {
  width: 88px;
  height: 88px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.08), rgba(139, 92, 246, 0.08));
  border: 1px solid rgba(59, 111, 224, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.empty-illustration-icon {
  color: var(--kb-primary);
  opacity: 0.7;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

.empty-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

.empty-new-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  margin-top: 8px;
  transition: opacity 0.15s;
}

.empty-new-btn:hover {
  opacity: 0.9;
}

/* 加载中 */
.content-loading {
  max-width: 720px;
  margin: 0 auto;
}

.skeleton-block {
  height: 14px;
  background: var(--kb-card);
  border-radius: 6px;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 笔记详情 */
.note-detail {
  max-width: 720px;
  margin: 0 auto;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.detail-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}

.detail-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--kb-foreground);
  margin: 0 0 28px;
  word-break: break-word;
}

.detail-body {
  background: transparent;
  padding: 0;
  border: none;
  margin-bottom: 36px;
}

/* 关联文档 */
.detail-related {
  border-top: 1px solid var(--kb-border);
  padding-top: 20px;
}

.related-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  margin-bottom: 12px;
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.related-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  text-decoration: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.related-item:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.06);
}

.related-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.related-info {
  flex: 1;
  min-width: 0;
}

.related-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-category {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

.related-arrow {
  color: var(--kb-muted-foreground);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s, transform 0.15s;
  flex-shrink: 0;
}

.related-item:hover .related-arrow {
  opacity: 1;
  color: var(--kb-primary);
  transform: translateX(2px);
}

/* Markdown 正文样式 */
.kb-md-body :deep(h1) {
  font-size: 22px;
  font-weight: 700;
  margin: 16px 0 12px;
  color: var(--kb-foreground);
}
.kb-md-body :deep(h2) {
  font-size: 18px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: var(--kb-foreground);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--kb-border);
}
.kb-md-body :deep(h3) {
  font-size: 15px;
  font-weight: 600;
  margin: 16px 0 8px;
  color: var(--kb-foreground);
}
.kb-md-body :deep(p) {
  font-size: 14px;
  line-height: 1.85;
  margin: 10px 0;
  color: var(--kb-foreground);
}
.kb-md-body :deep(ul),
.kb-md-body :deep(ol) {
  padding-left: 22px;
  margin: 10px 0;
  color: var(--kb-foreground);
}
.kb-md-body :deep(li) {
  font-size: 14px;
  line-height: 1.85;
  margin: 4px 0;
}
.kb-md-body :deep(code) {
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}
.kb-md-body :deep(pre) {
  margin: 14px 0;
  padding: 14px 18px;
  border-radius: 10px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  overflow-x: auto;
}
.kb-md-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: var(--kb-foreground);
  font-size: 13px;
}
.kb-md-body :deep(blockquote) {
  margin: 14px 0;
  padding: 10px 16px;
  border-left: 3px solid var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
  color: var(--kb-muted-foreground);
  font-size: 14px;
  border-radius: 0 8px 8px 0;
}
.kb-md-body :deep(blockquote p) {
  margin: 4px 0;
  color: var(--kb-muted-foreground);
}
.kb-md-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--kb-border);
  margin: 24px 0;
}
.kb-md-body :deep(a) {
  color: var(--kb-primary);
  text-decoration: none;
}
.kb-md-body :deep(a:hover) {
  text-decoration: underline;
}
.kb-md-body :deep(strong) {
  font-weight: 600;
  color: var(--kb-foreground);
}

@media (max-width: 768px) {
  .notes-layout {
    margin: -16px -16px 0;
  }
  .content-toolbar {
    padding: 10px 16px;
  }
  .content-body {
    padding: 20px 20px 40px;
  }
  .detail-title {
    font-size: 22px;
  }
  .notes-sidebar {
    width: 240px;
    min-width: 240px;
    position: absolute;
    z-index: 20;
    height: 100%;
    transform: translateX(0);
    transition: transform 0.2s ease;
  }
  .notes-sidebar.collapsed {
    width: 240px;
    min-width: 240px;
    transform: translateX(-100%);
    position: absolute;
  }
}
</style>
