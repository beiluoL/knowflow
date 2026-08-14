<template>
  <!-- 收藏夹页：4 tab 筛选 + 卡片网格（类型图标 + 标题 + 摘要 + 分类标签 + 来源/时间） + 分页 -->
  <div class="favorites-page animate-fade-in">
    <!-- ===== 页头：标题 + 收藏数 ===== -->
    <div class="page-header">
      <div class="title-group">
        <h1 class="kb-h1">收藏夹</h1>
        <span class="count-pill">{{ total }} 个收藏</span>
      </div>
      <button type="button" class="btn-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="toggleBatchMode">
        <Icon name="check-square" :size="14" aria-hidden="true" />
        <span>{{ batchMode ? '取消' : '批量管理' }}</span>
      </button>
    </div>

    <!-- ===== 筛选栏：4 tab + 排序按钮（设计稿 filter-tab） ===== -->
    <div class="filter-bar">
      <div class="filter-tabs">
        <button
          v-for="tab in typeTabs"
          :key="tab.value"
          class="filter-tab focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ active: selectedType === tab.value }"
          @click="handleTypeChange(tab.value)"
        >
          {{ tab.label }}
          <span class="tab-count" :class="{ active: selectedType === tab.value }">{{ tab.count }}</span>
        </button>
        <div class="tab-divider"></div>
        <button
          class="filter-tab sort-tab focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ active: showSortMenu }"
          @click="toggleSortMenu"
        >
          <Icon name="arrow-up-down" :size="14" aria-hidden="true" />
          <span>{{ sortLabel }}</span>
        </button>
        <!-- 排序下拉菜单 -->
        <div v-if="showSortMenu" class="sort-menu" @click.stop>
          <button
            v-for="opt in sortOptions"
            :key="opt.value"
            class="sort-menu-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            :class="{ active: selectedSort === opt.value }"
            @click="selectSort(opt.value)"
          >
            <Icon v-if="selectedSort === opt.value" name="check" :size="14" />
            <span>{{ opt.label }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- ===== 加载态 ===== -->
    <div v-if="loading" class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text">加载中...</p>
    </div>

    <!-- ===== 空态 ===== -->
    <div v-else-if="filteredItems.length === 0" class="state-area">
      <div class="empty-icon-box">
        <Icon name="heart" :size="40" class="empty-icon" aria-hidden="true" />
      </div>
      <p class="state-title">暂无收藏</p>
      <p class="state-text">去逛逛知识库，收藏感兴趣的内容吧</p>
    </div>

    <!-- ===== 收藏卡片网格（设计稿 fav-card 3 列） ===== -->
    <div v-else class="fav-grid">
      <div
        v-for="item in pagedItems"
        :key="item.id"
        class="fav-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ selected: batchMode && selectedIds.has(item.id) }"
        role="button"
        tabindex="0"
        @click="handleItemClick(item)"
        @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
      >
        <!-- 顶部：类型图标 + 取消收藏按钮 -->
        <div class="fav-card-top">
          <div class="fav-icon" :class="getItemIconClass(item)">
            <Icon :name="getItemIcon(item)" :size="18" aria-hidden="true" />
          </div>
          <button
            class="fav-unstar-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            title="取消收藏"
            @click.stop="handleUnfavorite(item.id)"
          >
            <Icon name="bookmark" :size="14" :fill="true" aria-hidden="true" />
          </button>
        </div>
        <!-- 标题 -->
        <h3 class="fav-title">{{ item.title }}</h3>
        <!-- 摘要 -->
        <p class="fav-summary">{{ getItemSummary(item) }}</p>
        <!-- 分类标签 -->
        <div class="fav-tags">
          <span
            v-for="(tag, idx) in getItemTags(item)"
            :key="idx"
            class="fav-tag"
            :class="idx === 0 ? `tag-${getItemType(item)}` : 'tag-default'"
          >
            {{ tag }}
          </span>
        </div>
        <!-- 底部分隔线 + 来源 + 收藏时间 -->
        <div class="fav-card-bottom">
          <div class="fav-source">
            <Icon name="folder" :size="12" aria-hidden="true" />
            <span>{{ item.categoryName || '未分类' }}</span>
          </div>
          <span class="fav-time">{{ formatTime(item.favoriteTime) }}</span>
        </div>
      </div>
    </div>

    <!-- ===== 分页器 ===== -->
    <div v-if="!loading && filteredItems.length > 0" class="pagination-wrap">
      <button
        class="pagination-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :disabled="pageNum <= 1"
        @click="goToPage(pageNum - 1)"
        aria-label="上一页"
      >
        <Icon name="chevron-left" :size="16" aria-hidden="true" />
      </button>
      <button
        v-for="page in visiblePages"
        :key="page"
        class="pagination-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ active: page === pageNum, ellipsis: page === -1 }"
        :disabled="page === -1"
        @click="goToPage(page)"
      >
        {{ page === -1 ? '...' : page }}
      </button>
      <button
        class="pagination-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :disabled="pageNum >= totalPages"
        @click="goToPage(pageNum + 1)"
        aria-label="下一页"
      >
        <Icon name="chevron-right" :size="16" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
// 收藏夹页：按类型筛选（全部/文档/笔记/闪卡）、排序、分页展示收藏项，支持取消收藏（二次确认）。
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { docsApi } from '@/api/docs';
import { confirmDialog, getApiError, notify } from '@/utils/toast';
import type { DocVO } from '@/api/types';

const router = useRouter();

// ===== 状态 =====
const loading = ref(false);
const allItems = ref<DocVO[]>([]);
const selectedType = ref('all');
const selectedSort = ref('recent');
const showSortMenu = ref(false);
const pageNum = ref(1);
const pageSize = ref(12);
const batchMode = ref(false);
const selectedIds = ref<Set<number>>(new Set());

// 排序选项
const sortOptions = [
  { value: 'recent', label: '最近收藏' },
  { value: 'oldest', label: '最早收藏' },
  { value: 'title', label: '标题排序' },
];

const sortLabel = computed(() => {
  const opt = sortOptions.find((o) => o.value === selectedSort.value);
  return opt ? opt.label : '最近收藏';
});

// ===== 类型分类（基于 tags 字段做客户端分类） =====
// 返回每项的类型：'doc' | 'note' | 'flashcard'
function getItemType(item: DocVO): 'doc' | 'note' | 'flashcard' {
  const tags = (item.tags || '').toLowerCase();
  if (tags.includes('闪卡') || tags.includes('flashcard')) return 'flashcard';
  if (tags.includes('笔记') || tags.includes('note')) return 'note';
  return 'doc';
}

// 4 个 tab 配置 + 计数
const typeTabs = computed(() => {
  const counts = { all: 0, doc: 0, note: 0, flashcard: 0 };
  for (const item of allItems.value) {
    counts.all++;
    counts[getItemType(item)]++;
  }
  return [
    { value: 'all', label: '全部', count: counts.all },
    { value: 'doc', label: '文档', count: counts.doc },
    { value: 'note', label: '笔记', count: counts.note },
    { value: 'flashcard', label: '闪卡', count: counts.flashcard },
  ];
});

// 总条数
const total = computed(() => allItems.value.length);

// 按类型筛选 + 排序
const filteredItems = computed(() => {
  let results = [...allItems.value];
  // 类型筛选
  if (selectedType.value !== 'all') {
    results = results.filter((item) => getItemType(item) === selectedType.value);
  }
  // 排序
  if (selectedSort.value === 'recent') {
    results.sort((a, b) => new Date(b.favoriteTime || 0).getTime() - new Date(a.favoriteTime || 0).getTime());
  } else if (selectedSort.value === 'oldest') {
    results.sort((a, b) => new Date(a.favoriteTime || 0).getTime() - new Date(b.favoriteTime || 0).getTime());
  } else if (selectedSort.value === 'title') {
    results.sort((a, b) => (a.title || '').localeCompare(b.title || ''));
  }
  return results;
});

// 当前页数据
const pagedItems = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value;
  return filteredItems.value.slice(start, start + pageSize.value);
});

// 总页数
const totalPages = computed(() => Math.max(1, Math.ceil(filteredItems.value.length / pageSize.value)));

// 可见页码（带省略号）
const visiblePages = computed(() => {
  const total = totalPages.value;
  const cur = pageNum.value;
  const pages: number[] = [];
  if (total <= 7) {
    for (let i = 1; i <= total; i++) pages.push(i);
    return pages;
  }
  pages.push(1);
  if (cur > 4) pages.push(-1);
  const start = Math.max(2, cur - 1);
  const end = Math.min(total - 1, cur + 1);
  for (let i = start; i <= end; i++) pages.push(i);
  if (cur < total - 3) pages.push(-1);
  pages.push(total);
  return pages;
});

function goToPage(p: number): void {
  if (p < 1 || p > totalPages.value || p === -1) return;
  pageNum.value = p;
}

// ===== 数据加载 =====
async function fetchFavorites(): Promise<void> {
  loading.value = true;
  try {
    const data = await docsApi.favorites();
    allItems.value = data || [];
  } catch (e) {
    notify(getApiError(e, '收藏加载失败'), 'error');
    allItems.value = [];
  } finally {
    loading.value = false;
  }
}

// ===== 交互处理 =====
function handleTypeChange(type: string): void {
  selectedType.value = type;
  pageNum.value = 1;
}

function toggleSortMenu(): void {
  showSortMenu.value = !showSortMenu.value;
}

function selectSort(value: string): void {
  selectedSort.value = value;
  showSortMenu.value = false;
}

function toggleBatchMode(): void {
  batchMode.value = !batchMode.value;
  if (!batchMode.value) selectedIds.value.clear();
}

function handleItemClick(item: DocVO): void {
  if (batchMode.value) {
    if (selectedIds.value.has(item.id)) selectedIds.value.delete(item.id);
    else selectedIds.value.add(item.id);
  } else {
    router.push(`/doc/${item.id}`);
  }
}

async function handleUnfavorite(id: number): Promise<void> {
  if (!(await confirmDialog('确定取消收藏吗？'))) return;
  try {
    await docsApi.toggleFavorite(id);
    allItems.value = allItems.value.filter((f) => f.id !== id);
  } catch (e) {
    notify(getApiError(e, '取消收藏失败'), 'error');
  }
}

// ===== 卡片渲染辅助 =====
function getItemIcon(item: DocVO): string {
  const type = getItemType(item);
  if (type === 'flashcard') return 'layers';
  if (type === 'note') return 'notebook-pen';
  return 'file-text';
}

function getItemIconClass(item: DocVO): string {
  const type = getItemType(item);
  if (type === 'flashcard') return 'icon-flashcard';
  if (type === 'note') return 'icon-note';
  return 'icon-doc';
}

function getItemSummary(item: DocVO): string {
  return item.summary || item.content?.slice(0, 80) || '暂无描述';
}

function getItemTags(item: DocVO): string[] {
  const tags = (item.tags || '').split(',').map((t) => t.trim()).filter(Boolean);
  if (tags.length === 0) {
    // 若无 tags，至少返回分类名作为标签
    return item.categoryName ? [item.categoryName] : ['未分类'];
  }
  return tags.slice(0, 2);
}

// 收藏时间格式化为「今天 / x天前 / 具体日期」
function formatTime(time?: string): string {
  if (!time) return '-';
  const date = new Date(time);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  if (days < 1) return '今天';
  if (days === 1) return '昨天';
  if (days < 30) return `${days} 天前`;
  return date.toLocaleDateString();
}

// 关闭排序菜单（点击外部）
function handleDocumentClick(): void {
  showSortMenu.value = false;
}

// ===== 生命周期 =====
onMounted(() => {
  fetchFavorites();
  document.addEventListener('click', handleDocumentClick);
});

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick);
});
</script>

<style scoped>
/* ===== 页面容器 ===== */
.favorites-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 页头 ===== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}
.count-pill {
  font-size: 13px;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

/* 通用按钮 */
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-sidebar-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s;
}
.btn-secondary:hover { background: var(--kb-muted); }

/* ===== 筛选栏（设计稿 filter-tab + 排序按钮） ===== */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.filter-tabs {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-muted);
}
.filter-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}
.filter-tab:hover { color: var(--kb-foreground); }
.filter-tab.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.tab-count {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.tab-count.active {
  background: rgba(255, 255, 255, 0.2);
  color: var(--kb-primary-foreground);
}
.tab-divider {
  width: 1px;
  height: 20px;
  margin: 0 8px;
  background: var(--kb-border);
}
.sort-tab {
  background: var(--kb-card);
  color: var(--kb-foreground);
}
.sort-tab:hover { color: var(--kb-primary); }
.sort-tab.active { color: var(--kb-primary); }

/* 排序下拉菜单 */
.sort-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  min-width: 140px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  padding: 4px;
  z-index: 10;
}
.sort-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-foreground);
  text-align: left;
  transition: background 0.15s;
}
.sort-menu-item:hover { background: var(--kb-muted); }
.sort-menu-item.active { color: var(--kb-primary); font-weight: 500; }

/* ===== 状态区（加载/空） ===== */
.state-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  color: var(--kb-muted-foreground);
}
.loading-spinner {
  width: 28px;
  height: 28px;
  border: 2px solid var(--kb-muted);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.state-text { margin-top: 12px; font-size: 14px; }
.state-title { margin-top: 12px; font-size: 15px; font-weight: 500; color: var(--kb-foreground); }
.empty-icon-box {
  width: 64px;
  height: 64px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-icon { color: var(--kb-muted-foreground); opacity: 0.6; }

/* ===== 收藏卡片网格（设计稿 fav-card 3 列） ===== */
.fav-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
@media (max-width: 1024px) {
  .fav-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .fav-grid { grid-template-columns: 1fr; }
}

.fav-card {
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  padding: 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.15s, border-color 0.15s;
}
.fav-card:hover {
  box-shadow: 0 4px 16px rgba(59, 111, 224, 0.08);
  transform: translateY(-1px);
}
.fav-card.selected {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 2px rgba(59, 111, 224, 0.15);
}

/* 顶部：类型图标 + 取消收藏按钮 */
.fav-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}
.fav-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--kb-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.icon-doc { background: rgba(59, 111, 224, 0.1); color: var(--kb-primary); }
.icon-note { background: rgba(245, 158, 11, 0.1); color: var(--kb-state-warning); }
.icon-flashcard { background: rgba(16, 185, 129, 0.1); color: var(--kb-state-success); }

.fav-unstar-btn {
  padding: 6px;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-state-warning);
  transition: opacity 0.15s;
}
.fav-unstar-btn:hover { opacity: 0.7; }

/* 标题 */
.fav-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 摘要 */
.fav-summary {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 分类标签 */
.fav-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.fav-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
}
.tag-doc { background: rgba(59, 111, 224, 0.1); color: var(--kb-primary); }
.tag-note { background: rgba(245, 158, 11, 0.1); color: var(--kb-state-warning); }
.tag-flashcard { background: rgba(16, 185, 129, 0.1); color: var(--kb-state-success); }
.tag-default { background: var(--kb-muted); color: var(--kb-muted-foreground); }

/* 底部分隔线 + 来源 + 收藏时间 */
.fav-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--kb-border);
}
.fav-source {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.fav-time {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}

/* ===== 分页器 ===== */
.pagination-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 0;
}
.pagination-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s;
}
.pagination-btn:hover:not(:disabled):not(.ellipsis) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.pagination-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}
.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pagination-btn.ellipsis {
  border: none;
  background: transparent;
  cursor: default;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .filter-bar { flex-direction: column; align-items: flex-start; gap: 12px; }
  .filter-tabs { width: 100%; overflow-x: auto; }
}
</style>
