<template>
  <!-- 搜索结果页：搜索框 + 筛选按钮组 + 单容器分隔列表 + 分页器，支持关键词高亮（含 XSS 防护）与收藏 -->
  <div class="search-result-page animate-fade-in">
    <!-- ===== 未搜索态：引导标题 + 大搜索框 + 热门标签 ===== -->
    <div v-if="!hasSearched" class="search-hero">
      <h1 class="kb-h1 text-center">知识搜索</h1>
      <p class="hero-subtitle">输入关键词，在知识库中快速找到你需要的内容</p>
      <div class="hero-search-box">
        <Icon name="search" :size="20" class="hero-search-icon" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索知识库、文档、笔记..."
          class="hero-search-input"
          @keyup.enter="handleSearch"
          aria-label="搜索"
        />
        <button
          v-if="searchQuery"
          class="hero-clear-btn"
          @click="clearInput"
          aria-label="清空输入"
        >
          <Icon name="x" :size="16" />
        </button>
        <button class="hero-search-btn" @click="handleSearch">搜索</button>
      </div>
      <!-- 热门搜索标签 -->
      <div class="hot-tags-grid">
        <button
          v-for="tag in hotTags"
          :key="tag"
          class="hot-tag"
          @click="searchTag(tag)"
        >
          {{ tag }}
        </button>
      </div>
      <p class="hot-tags-label">热门搜索</p>
    </div>

    <!-- ===== 已搜索态：紧凑搜索框 + 筛选 + 列表 + 分页 ===== -->
    <template v-else>
      <!-- 紧凑搜索框（设计稿规范） -->
      <div class="compact-search-wrap">
        <div class="compact-search-box">
          <Icon name="search" :size="18" class="compact-search-icon" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="输入关键词继续搜索..."
            class="compact-search-input"
            @keyup.enter="handleSearch"
            aria-label="搜索"
          />
          <button
            v-if="searchQuery"
            class="compact-clear-btn"
            @click="clearInput"
            aria-label="清空"
          >
            <Icon name="x" :size="14" />
          </button>
          <kbd class="compact-enter-kbd">Enter</kbd>
        </div>
      </div>

      <!-- 筛选栏：类型 | 时间 | 排序（设计稿 filter-btn 按钮组） -->
      <div class="filter-bar">
        <div class="filter-group">
          <span class="filter-label">筛选：</span>
          <button
            v-for="tab in filterTabs"
            :key="tab.key"
            class="filter-btn"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
          <span class="filter-divider">|</span>
          <button
            v-for="opt in timeOptions"
            :key="opt.key"
            class="filter-btn"
            :class="{ active: activeTime === opt.key }"
            @click="activeTime = opt.key"
          >
            {{ opt.label }}
          </button>
          <span class="filter-divider">|</span>
          <button
            v-for="opt in sortOptions"
            :key="opt.key"
            class="filter-btn"
            :class="{ active: activeSort === opt.key }"
            @click="activeSort = opt.key"
          >
            {{ opt.label }}
          </button>
        </div>
        <span class="result-count">
          找到 <strong>{{ totalCount }}</strong> 个结果
        </span>
      </div>

      <!-- 加载态 -->
      <div v-if="loading" class="state-area">
        <div class="loading-spinner"></div>
        <p class="state-text">搜索中...</p>
      </div>

      <!-- 无结果 -->
      <div v-else-if="filteredResults.length === 0" class="state-area">
        <div class="empty-icon-box">
          <Icon name="file-question" :size="40" class="empty-icon" />
        </div>
        <p class="state-title">未找到相关结果</p>
        <p class="state-text">试试其他关键词或调整筛选条件</p>
      </div>

      <!-- 搜索结果列表（设计稿：单容器分隔列表） -->
      <div v-else class="result-list">
        <a
          v-for="doc in pagedResults"
          :key="doc.id"
          href="javascript:void(0)"
          class="result-item"
          @click="goToDoc(doc.id)"
        >
          <!-- 顶部：类型徽标 + 分类 -->
          <div class="result-meta-top">
            <span class="result-type-badge" :class="getTypeBadgeClass(doc)">
              {{ getTypeLabel(doc) }}
            </span>
            <span class="result-category">{{ doc.categoryName || '未分类' }}</span>
          </div>
          <!-- 标题（含关键词高亮） -->
          <h3 class="result-title" v-html="highlightKeyword(doc.title)"></h3>
          <!-- 摘要（优先展示正文命中片段，含关键词高亮） -->
          <p
            class="result-summary"
            v-html="highlightKeyword(getSummaryText(doc))"
          ></p>
          <!-- 底部：作者 + 日期 + 浏览数 + 收藏按钮 -->
          <div class="result-meta-bottom">
            <div class="result-meta-info">
              <span class="meta-item">
                <Icon name="user" :size="12" />
                <span>{{ doc.author || '未知' }}</span>
              </span>
              <span class="meta-item">
                <Icon name="calendar" :size="12" />
                <span>{{ formatDate(doc.createTime) }}</span>
              </span>
              <span class="meta-item">
                <Icon name="eye" :size="12" />
                <span>{{ doc.readCount || 0 }} 次浏览</span>
              </span>
            </div>
            <button
              class="result-favorite-btn"
              :class="{ active: isFavorited(doc) }"
              @click.stop="toggleFavorite(doc)"
              :aria-label="isFavorited(doc) ? '取消收藏' : '收藏'"
            >
              <Icon
                :name="isFavorited(doc) ? 'bookmark' : 'bookmark'"
                :size="14"
                :fill="isFavorited(doc)"
              />
            </button>
          </div>
        </a>
      </div>

      <!-- 分页器（设计稿 pagination-btn：32x32 方形按钮居中） -->
      <div v-if="!loading && filteredResults.length > 0" class="pagination-wrap">
        <button
          class="pagination-btn"
          :disabled="pageNum <= 1"
          @click="goToPage(pageNum - 1)"
          aria-label="上一页"
        >
          <Icon name="chevron-left" :size="16" />
        </button>
        <button
          v-for="page in visiblePages"
          :key="page"
          class="pagination-btn"
          :class="{ active: page === pageNum, ellipsis: page === -1 }"
          :disabled="page === -1"
          @click="goToPage(page)"
        >
          {{ page === -1 ? '...' : page }}
        </button>
        <button
          class="pagination-btn"
          :disabled="pageNum >= totalPages"
          @click="goToPage(pageNum + 1)"
          aria-label="下一页"
        >
          <Icon name="chevron-right" :size="16" />
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
// 搜索结果页：按关键词检索文档/笔记，支持类型/时间筛选、相关度/时间/阅读量排序、关键词高亮（XSS 防护）与收藏。
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { docsApi } from '@/api';
import type { DocVO } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { notify, getApiError } from '@/utils/toast';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

// ===== 状态 =====
const searchQuery = ref('');
const activeTab = ref('all');
const activeSort = ref('relevance');
const activeTime = ref('all');
const loading = ref(false);
const hasSearched = ref(false);
const allResults = ref<DocVO[]>([]);
const favoritedIds = ref<Set<number>>(new Set());

// 分页状态
const pageNum = ref(1);
const pageSize = ref(10);
// 后端返回的总数
const serverTotal = ref(0);
// 类型/时间为纯前端筛选（后端无对应字段），排序已下推后端，不计入此判断
const hasFilters = computed(() => activeTab.value !== 'all' || activeTime.value !== 'all');

// 热门搜索词
const hotTags = ['Vue 3', 'TypeScript', 'React Hooks', '前端性能优化', '设计模式', '算法入门'];

/** 前端筛选场景下单批拉取条数 */
const BATCH_SIZE = 100;
/** 前端筛选场景下最多加载的文档数，超出部分不再拉取以保护浏览器内存 */
const MAX_CLIENT_FILTER_DOCS = 1000;

// 筛选项配置
const filterTabs = computed(() => [
  { key: 'all', label: '全部类型' },
  { key: 'doc', label: '文档' },
  { key: 'pdf', label: 'PDF' },
  { key: 'markdown', label: 'Markdown' },
  { key: 'note', label: '笔记' },
]);

const timeOptions = [
  { key: 'all', label: '全部时间' },
  { key: 'week', label: '最近一周' },
  { key: 'month', label: '最近一月' },
];

// 排序 key 与后端 DocQueryDTO.sort 取值一一对应，由后端执行排序
const sortOptions = [
  { key: 'relevance', label: '相关性排序' },
  { key: 'time', label: '最新发布' },
  { key: 'view', label: '最多阅读' },
];

// 仅做类型/时间的前端过滤；排序由后端完成，此处不再二次排序，
// 否则会打乱后端的相关度（含语义召回）排名。
const filteredResults = computed(() => {
  let results = [...allResults.value];

  // 类型筛选：依据 tags 字段匹配
  if (activeTab.value !== 'all') {
    results = results.filter((d) => {
      const tags = (d.tags || '').toLowerCase();
      if (activeTab.value === 'pdf') return tags.includes('pdf');
      if (activeTab.value === 'markdown') return tags.includes('markdown') || tags.includes('md');
      if (activeTab.value === 'note') return tags.includes('笔记') || tags.includes('note');
      if (activeTab.value === 'doc') return !tags.includes('笔记') && !tags.includes('note');
      return true;
    });
  }

  // 时间筛选：按 createTime 距今天数
  if (activeTime.value !== 'all') {
    const now = Date.now();
    const days = activeTime.value === 'week' ? 7 : 30;
    results = results.filter((d) => {
      if (!d.createTime) return false;
      return (now - new Date(d.createTime).getTime()) / 86400000 <= days;
    });
  }

  return results;
});

// 总条数
const totalCount = computed(() =>
  hasFilters.value ? filteredResults.value.length : serverTotal.value,
);

// 当前页数据
const pagedResults = computed(() => {
  if (hasFilters.value) {
    const start = (pageNum.value - 1) * pageSize.value;
    return filteredResults.value.slice(start, start + pageSize.value);
  }
  // 无筛选：后端已分页，直接用 allResults
  return filteredResults.value;
});

// 总页数
const totalPages = computed(() => {
  if (hasFilters.value) {
    return Math.max(1, Math.ceil(filteredResults.value.length / pageSize.value));
  }
  return Math.max(1, Math.ceil(serverTotal.value / pageSize.value));
});

// 可见页码（带省略号，最多展示 5 个数字按钮）
const visiblePages = computed(() => {
  const total = totalPages.value;
  const cur = pageNum.value;
  const pages: number[] = [];
  if (total <= 7) {
    for (let i = 1; i <= total; i++) pages.push(i);
    return pages;
  }
  pages.push(1);
  if (cur > 4) pages.push(-1); // 省略号
  const start = Math.max(2, cur - 1);
  const end = Math.min(total - 1, cur + 1);
  for (let i = start; i <= end; i++) pages.push(i);
  if (cur < total - 3) pages.push(-1); // 省略号
  pages.push(total);
  return pages;
});

// 翻页：边界内才允许跳转。无前端筛选时需向后端请求对应页，有筛选时本地切片即可。
function goToPage(p: number): void {
  if (p < 1 || p > totalPages.value || p === -1) return;
  pageNum.value = p;
  if (!hasFilters.value && searchQuery.value.trim()) {
    fetchPage(p);
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ===== XSS 防护：先 HTML 转义再注入高亮标签 =====
function escapeHtml(str: string): string {
  return (str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * 关键词高亮：先整体 HTML 转义，再用字面量匹配包裹 <mark>。
 * 用 indexOf 循环而非动态正则，规避用户输入构造正则带来的 ReDoS 与转义遗漏风险。
 */
function highlightKeyword(text: string): string {
  const safe = escapeHtml(text || '');
  const kw = escapeHtml(searchQuery.value.trim());
  if (!kw) return safe;

  const lowerSafe = safe.toLowerCase();
  const lowerKw = kw.toLowerCase();
  let result = '';
  let cursor = 0;
  let idx = lowerSafe.indexOf(lowerKw);
  while (idx >= 0) {
    result += safe.slice(cursor, idx);
    result += `<mark class="kw-highlight">${safe.slice(idx, idx + kw.length)}</mark>`;
    cursor = idx + kw.length;
    idx = lowerSafe.indexOf(lowerKw, cursor);
  }
  return result + safe.slice(cursor);
}

/** 摘要展示：优先用后端返回的正文命中片段，回退到文档摘要 */
function getSummaryText(doc: DocVO): string {
  return doc.highlight || doc.summary || '';
}

// ===== 搜索逻辑 =====
async function doSearch(keyword: string): Promise<void> {
  if (!keyword.trim()) {
    allResults.value = [];
    hasSearched.value = false;
    return;
  }
  hasSearched.value = true;
  pageNum.value = 1;
  await fetchPage(1);
  router.replace({ path: '/search', query: { q: keyword } });
}

/**
 * 拉取搜索结果。
 * - 无前端筛选：走后端分页，每次只取当前页。
 * - 有前端筛选（类型/时间）：后端无对应字段，需拉全量再本地过滤。
 *   这里按 total 分批取完，避免旧实现「固定 pageSize=100 后本地切片」
 *   在结果超 100 条时静默丢弃数据、导致分页与计数失真的问题。
 */
async function fetchPage(page: number): Promise<void> {
  const keyword = searchQuery.value.trim();
  if (!keyword) return;
  loading.value = true;
  try {
    const sort = activeSort.value as 'relevance' | 'time' | 'view';
    if (!hasFilters.value) {
      const res = await docsApi.list({ keyword, sort, pageNum: page, pageSize: pageSize.value });
      allResults.value = res.records || [];
      serverTotal.value = res.total || 0;
    } else {
      allResults.value = await fetchAllPages(keyword, sort);
      serverTotal.value = allResults.value.length;
    }
    favoritedIds.value = new Set(
      allResults.value.filter((d) => d.favoriteCount && d.favoriteCount > 0).map((d) => d.id),
    );
  } catch (e: unknown) {
    allResults.value = [];
    serverTotal.value = 0;
    notify(getApiError(e, '搜索失败，请稍后再试'), 'error');
  } finally {
    loading.value = false;
  }
}

/** 分批拉取全部结果，上限 MAX_CLIENT_FILTER_DOCS 条，防止极端数据量拖垮浏览器 */
async function fetchAllPages(
  keyword: string,
  sort: 'relevance' | 'time' | 'view',
): Promise<DocVO[]> {
  const first = await docsApi.list({ keyword, sort, pageNum: 1, pageSize: BATCH_SIZE });
  const records = [...(first.records || [])];
  const total = Math.min(first.total || 0, MAX_CLIENT_FILTER_DOCS);
  const totalBatches = Math.ceil(total / BATCH_SIZE);
  if (totalBatches <= 1) return records;

  // 并发拉取剩余批次，缩短等待时间
  const rest = await Promise.all(
    Array.from({ length: totalBatches - 1 }, (_, i) =>
      docsApi.list({ keyword, sort, pageNum: i + 2, pageSize: BATCH_SIZE }),
    ),
  );
  rest.forEach((r) => records.push(...(r.records || [])));
  return records.slice(0, MAX_CLIENT_FILTER_DOCS);
}

function handleSearch(): void {
  doSearch(searchQuery.value);
}

function searchTag(tag: string): void {
  searchQuery.value = tag;
  doSearch(tag);
}

function clearInput(): void {
  searchQuery.value = '';
}

function goToDoc(docId: number): void {
  router.push(`/doc/${docId}`);
}

// ===== 类型标签 =====
function getTypeLabel(doc: DocVO): string {
  const tags = (doc.tags || '').toLowerCase();
  if (tags.includes('pdf')) return 'PDF';
  if (tags.includes('markdown') || tags.includes('md')) return 'MD';
  if (tags.includes('笔记') || tags.includes('note')) return 'Note';
  return '文档';
}

function getTypeBadgeClass(doc: DocVO): string {
  const tags = (doc.tags || '').toLowerCase();
  if (tags.includes('pdf')) return 'badge-pdf';
  if (tags.includes('markdown') || tags.includes('md')) return 'badge-md';
  if (tags.includes('笔记') || tags.includes('note')) return 'badge-note';
  return 'badge-doc';
}

// ===== 收藏 =====
function isFavorited(doc: DocVO): boolean {
  return favoritedIds.value.has(doc.id);
}

function toggleFavorite(doc: DocVO): void {
  if (!auth.isLoggedIn) {
    notify('请先登录', 'warning');
    return;
  }
  if (favoritedIds.value.has(doc.id)) {
    favoritedIds.value.delete(doc.id);
    notify('已取消收藏', 'info');
  } else {
    favoritedIds.value.add(doc.id);
    notify('已收藏', 'success');
  }
}

// ===== 工具函数 =====
function formatDate(dateStr?: string): string {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).replace(/\//g, '-');
}

// ===== 生命周期 =====
onMounted(async () => {
  const query = route.query.q as string;
  if (query) {
    searchQuery.value = query;
    await doSearch(query);
  }
});

// 监听路由 query 变化（支持从其他页面跳转过来时自动搜索）
watch(
  () => route.query.q,
  (newQ) => {
    if (newQ && typeof newQ === 'string' && newQ !== searchQuery.value) {
      searchQuery.value = newQ;
      doSearch(newQ);
    }
  },
);

// 筛选/排序变化时重置到第一页重新拉取：
// 排序已下推后端，类型/时间筛选会切换「后端分页 ↔ 全量本地过滤」策略，两者都需重新请求。
watch([activeTab, activeTime, activeSort], () => {
  if (hasSearched.value && searchQuery.value.trim()) {
    pageNum.value = 1;
    fetchPage(1);
  }
});
</script>

<style scoped>
/* ===== 页面容器 ===== */
.search-result-page {
  max-width: 1000px;
  margin: 0 auto;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 未搜索态：引导区 ===== */
.search-hero {
  padding-top: 48px;
  padding-bottom: 80px;
  text-align: center;
}
.hero-subtitle {
  margin-top: 8px;
  margin-bottom: 32px;
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.hero-search-box {
  display: flex;
  align-items: stretch;
  max-width: 640px;
  margin: 0 auto;
  border-radius: var(--kb-radius-lg);
  overflow: hidden;
  border: 2px solid var(--kb-primary);
  box-shadow: 0 8px 24px rgba(59, 111, 224, 0.1);
  background: var(--kb-card);
}
.hero-search-icon {
  margin-left: 20px;
  align-self: center;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.hero-search-input {
  flex: 1;
  padding: 14px 16px;
  font-size: 15px;
  background: transparent;
  border: none;
  outline: none;
  color: var(--kb-foreground);
}
.hero-search-input::placeholder { color: var(--kb-muted-foreground); }
.hero-clear-btn {
  margin-right: 8px;
  align-self: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}
.hero-clear-btn:hover { background: var(--kb-muted); }
.hero-search-btn {
  padding: 0 32px;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: opacity 0.15s;
}
.hero-search-btn:hover { opacity: 0.9; }

.hot-tags-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 480px;
  margin: 32px auto 0;
}
@media (min-width: 768px) {
  .hot-tags-grid { grid-template-columns: repeat(3, 1fr); }
}
.hot-tag {
  padding: 10px 16px;
  border-radius: var(--kb-radius-md);
  font-size: 13px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s;
}
.hot-tag:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}
.hot-tags-label {
  margin-top: 24px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* ===== 已搜索态：紧凑搜索框（设计稿规范） ===== */
.compact-search-wrap {
  max-width: 640px;
  margin: 0 auto 32px;
}
.compact-search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.compact-search-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
}
.compact-search-input {
  flex: 1;
  font-size: 15px;
  background: transparent;
  border: none;
  outline: none;
  color: var(--kb-foreground);
}
.compact-search-input::placeholder { color: var(--kb-muted-foreground); }
.compact-clear-btn {
  padding: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  border-radius: 4px;
  transition: all 0.15s;
}
.compact-clear-btn:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.compact-enter-kbd {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

/* ===== 筛选栏 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.filter-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s;
}
.filter-btn:hover,
.filter-btn.active {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}
.filter-divider {
  color: var(--kb-border);
  margin: 0 2px;
}
.result-count {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.result-count strong {
  color: var(--kb-foreground);
  font-weight: 600;
}

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

/* ===== 搜索结果列表（设计稿：单容器分隔列表） ===== */
.result-list {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  overflow: hidden;
  margin-bottom: 24px;
}
.result-item {
  display: block;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kb-border);
  text-decoration: none;
  cursor: pointer;
  transition: background-color 0.15s;
}
.result-item:last-child { border-bottom: none; }
.result-item:hover { background: rgba(59, 111, 224, 0.02); }

/* 顶部：类型徽标 + 分类 */
.result-meta-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.result-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
}
.badge-pdf { background: rgba(239, 68, 68, 0.1); color: var(--kb-state-error); }
.badge-md { background: rgba(16, 185, 129, 0.1); color: var(--kb-state-success); }
.badge-note { background: rgba(245, 158, 11, 0.1); color: var(--kb-state-warning); }
.badge-doc { background: rgba(59, 111, 224, 0.1); color: var(--kb-primary); }

.result-category {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 标题 */
.result-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-title:hover { color: var(--kb-primary); }

/* 摘要 */
.result-summary {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 8px;
}

/* 底部：作者 + 日期 + 浏览数 + 收藏按钮 */
.result-meta-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.result-meta-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.result-favorite-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  border-radius: 4px;
  transition: all 0.15s;
}
.result-favorite-btn:hover { background: var(--kb-muted); color: var(--kb-state-warning); }
.result-favorite-btn.active { color: var(--kb-state-warning); }

/* 关键词高亮 */
:deep(.kw-highlight) {
  background: rgba(245, 158, 11, 0.15);
  color: var(--kb-state-warning);
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 600;
}

/* ===== 分页器（设计稿 pagination-btn：32x32 方形按钮居中） ===== */
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
  .filter-bar { flex-direction: column; align-items: flex-start; }
  .filter-group { width: 100%; }
  .result-meta-info { gap: 8px; }
  .result-meta-bottom { flex-wrap: wrap; }
}
</style>
