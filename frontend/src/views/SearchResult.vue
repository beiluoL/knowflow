<template>
  <!-- 搜索结果页：搜索框 + 筛选按钮组 + 单容器分隔列表 + 分页器，支持关键词高亮（含 XSS 防护）与收藏 -->
  <div class="search-result-page animate-fade-in">
    <!-- ===== 未搜索态：引导标题 + 大搜索框 + 热门标签 ===== -->
    <div v-if="!hasSearched" class="search-hero">
      <h1 class="kb-h1 text-center">知识搜索</h1>
      <p class="hero-subtitle">输入关键词，在知识库中快速找到你需要的内容</p>
      <form class="hero-search-box" role="search" :aria-busy="heroSubmitting" @submit.prevent="submitHeroSearch">
        <Icon name="search" :size="20" class="hero-search-icon" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索知识库、文档、笔记..."
          class="hero-search-input kb-input kb-form-control"
          @keydown.enter="handleHeroEnter"
          @compositionstart="composing = true"
          @compositionend="composing = false"
          aria-label="搜索"
        />
        <button
          v-if="searchQuery"
          class="hero-clear-btn"
          type="button"
          @click="clearInput"
          aria-label="清空输入"
        >
          <Icon name="x" :size="16" />
        </button>
        <button class="hero-search-btn" type="submit" :disabled="!searchQuery.trim() || heroSubmitting" >
          {{ heroSubmitting ? '搜索中…' : '搜索' }}
        </button>
      </form>
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
            class="compact-search-input kb-input kb-form-control"
            @keydown.enter="handleSearch"
            @compositionstart="composing = true"
            @compositionend="composing = false"
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

      <p v-if="searchQuery.trim() !== (route.query.q || '')" class="search-draft-hint" role="status" aria-live="polite">按 Enter 搜索当前关键词</p>
      <!-- 筛选栏：类型 | 时间 | 排序（设计稿 filter-btn 按钮组） -->
      <div class="filter-bar">
        <div class="filter-group">
          <label v-for="filter in filterControls" :key="filter.key" class="filter-control">
            <span>{{ filter.label }}</span>
            <Select
              class="filter-select"
              variant="pill"
              :aria-label="filter.label"
              :model-value="filter.value"
              :options="filter.options.map(opt => ({ value: opt.key, label: opt.label }))"
              @update:model-value="setFilter(filter.key, String($event))"
            />
          </label>
          <button v-if="hasFilters || activeSort !== 'relevance'" class="filter-btn" @click="resetFilters">重置筛选</button>
        </div>
        <span v-if="!loading && !searchError" class="result-count" role="status" aria-live="polite">
          找到 <strong>{{ totalCount }}</strong> 个结果{{ relevanceCapped ? '（相关度优先展示最新 500 条候选，可改用「最新发布」查看更早结果）' : '' }}
        </span>
      </div>

      <!-- 加载态 -->
      <div v-if="loading" class="state-area" role="status" aria-live="polite">
        <div class="loading-spinner"></div>
        <p class="state-text">搜索中...</p>
      </div>

      <div v-else-if="searchError" class="state-area" role="alert">
        <p class="state-title">搜索未完成</p><p class="state-text">{{ searchError }}</p>
        <button class="filter-btn" @click="fetchPage(pageNum)">重试搜索</button>
      </div>
      <!-- 无结果 -->
      <div v-else-if="filteredResults.length === 0" class="state-area" role="status" aria-live="polite">
        <div class="empty-icon-box">
          <Icon name="file-question" :size="32" class="empty-icon" />
        </div>
        <p class="state-title">未找到相关结果</p>
        <p class="state-text">试试其他关键词或调整筛选条件</p>
      </div>

      <!-- 搜索结果列表（设计稿：单容器分隔列表） -->
      <div v-else class="result-list">
        <article
          v-for="doc in pagedResults"
          :key="doc.id"
          class="result-item"
        >
          <!-- 顶部：类型徽标 + 分类 -->
          <div class="result-meta-top">
            <span class="result-type-badge" :class="getTypeBadgeClass(doc)">
              {{ getTypeLabel(doc) }}
            </span>
            <span class="result-category">{{ doc.categoryName || '未分类' }}</span>
          </div>
          <!-- 标题（含关键词高亮） -->
          <h3 class="result-title"><a :href="`/doc/${doc.id}`" @click="goToDoc(doc.id, $event)" v-html="highlightKeyword(doc.title)"></a></h3>
          <!-- 摘要（优先展示正文命中片段，含关键词高亮） -->
          <p
            class="result-summary"
            v-html="highlightKeyword(getSummaryText(doc))"
          ></p>
          <!-- 底部：作者 + 日期 + 浏览数 + 收藏按钮 -->
          <div class="result-meta-bottom">
            <div class="result-meta-info">
              <span v-if="doc.author && doc.author !== '未知'" class="meta-item">
                <Icon name="user" :size="12" />
                <span>{{ doc.author }}</span>
              </span>
              <span v-if="doc.createTime" class="meta-item">
                <Icon name="calendar" :size="12" />
                <span>{{ formatDate(doc.createTime) }}</span>
              </span>
              <span v-if="doc.readCount != null" class="meta-item">
                <Icon name="eye" :size="12" />
                <span>{{ doc.readCount || 0 }} 次浏览</span>
              </span>
            </div>
            <button
              class="result-favorite-btn"
              :class="{ active: isFavorited(doc) }"
              @click.stop.prevent="toggleFavorite(doc)"
              :disabled="favoritePending.has(doc.id)"
              :aria-label="isFavorited(doc) ? '取消收藏' : '收藏'"
            >
              <Icon
                :name="isFavorited(doc) ? 'bookmark' : 'bookmark'"
                :size="14"
                :fill="isFavorited(doc)"
              />
            </button>
          </div>
        </article>
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
import { ref, computed, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { searchSummary, highlightSearchText } from '@/utils/searchText';
import Icon from '@/components/ui/Icon.vue';
import Select from '@/components/ui/Select.vue';
import { docsApi } from '@/api';
import type { DocVO } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { notify, getApiError } from '@/utils/toast';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

// ===== 状态 =====
const searchQuery = ref('');
const composing = ref(false);
const heroSubmitting = ref(false);
const searchError = ref('');
/** 相关度排序在服务端只对最新 500 条候选打分；命中更多时由后端诚实截断 total，此处给出说明。 */
const relevanceCapped = ref(false);
const favoritePending = ref(new Set<number>());
let requestVersion = 0;

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
// 后端返回的总数（类型/时间过滤已下推服务端，total 即过滤后的真实总数）
const serverTotal = ref(0);
const hasFilters = computed(() => activeTab.value !== 'all' || activeTime.value !== 'all');

/** 相关度打分候选窗口大小（与后端 RELEVANCE_CANDIDATE_LIMIT 一致，仅用于提示文案）。 */
const RELEVANCE_CANDIDATE_LIMIT = 500;

// 热门搜索词
const hotTags = ['Vue 3', 'TypeScript', 'React Hooks', '前端性能优化', '设计模式', '算法入门'];

/** 把时间筛选换算成服务端 ISO 日期下界（最近 7/30 天，含今天）。 */
function timeRangeFrom(): string | undefined {
  if (activeTime.value === 'all') return undefined;
  const days = activeTime.value === 'week' ? 6 : 29;
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

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

// 类型/时间过滤已全部下推服务端：当前页即服务端返回页，不再在浏览器内做本地过滤
// （旧实现最多拉 1000 条本地过滤，会漏结果、total 不准、相关度排序深页为空）。
const filteredResults = computed(() => allResults.value);

// 总条数：始终以服务端 total 为准
const totalCount = computed(() => serverTotal.value);

// 当前页数据：服务端已分页
const pagedResults = computed(() => filteredResults.value);

// 总页数：以服务端 total 为准
const totalPages = computed(() => Math.max(1, Math.ceil(serverTotal.value / pageSize.value)));

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

// 翻页：始终请求服务端对应页（含类型/时间过滤参数）。
function goToPage(p: number): void {
  if (p < 1 || p > totalPages.value || p === -1) return;
  pageNum.value = p;
  if (typeof route.query.q === 'string' && route.query.q.trim()) {
    fetchPage(p);
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function highlightKeyword(text: string): string {
  return highlightSearchText(text, typeof route.query.q === 'string' ? route.query.q : '');
}
function getSummaryText(doc: DocVO): string { return searchSummary(doc.highlight || doc.summary || ''); }
function writeQuery(keyword = searchQuery.value.trim()) {
  return router.push({ path: '/search', query: { q: keyword || undefined, type: activeTab.value === 'all' ? undefined : activeTab.value, time: activeTime.value === 'all' ? undefined : activeTime.value, sort: activeSort.value === 'relevance' ? undefined : activeSort.value } });
}
const filterControls = computed(() => [
 {key: 'type', label: '类型', value: activeTab.value, options: filterTabs.value},
 {key: 'time', label: '时间', value: activeTime.value, options: timeOptions},
 {key: 'sort', label: '排序', value: activeSort.value, options: sortOptions},
]);
function setFilter(key: string, value: string) {
 if (key === 'type') activeTab.value = value;
 if (key === 'time') activeTime.value = value;
 if (key === 'sort') activeSort.value = value;
 writeQuery(typeof route.query.q === 'string' ? route.query.q : '');
}
function resetFilters() { activeTab.value = 'all'; activeTime.value = 'all'; activeSort.value = 'relevance'; writeQuery(typeof route.query.q === 'string' ? route.query.q : ''); }

/**
 * 拉取搜索结果：始终走服务端分页与服务端过滤（类型/时间/排序全部下推），
 * 每次只请求当前页，total/页数/翻页均以后端为准。
 */
async function fetchPage(page: number): Promise<void> {
  const keyword = typeof route.query.q === 'string' ? route.query.q.trim() : '';
  if (!keyword) return;
  const version = ++requestVersion;
  searchError.value = '';
  relevanceCapped.value = false;
  loading.value = true;
  try {
    const sort = activeSort.value as 'relevance' | 'time' | 'view';
    const params: Parameters<typeof docsApi.list>[0] = {
      keyword,
      sort,
      pageNum: page,
      pageSize: pageSize.value,
    };
    if (activeTab.value !== 'all') params.type = activeTab.value as 'doc' | 'pdf' | 'markdown' | 'note';
    const from = timeRangeFrom();
    if (from) params.from = from;
    const res = await docsApi.list(params);
    if (version !== requestVersion) return;
    allResults.value = res.records || [];
    serverTotal.value = res.total || 0;
    relevanceCapped.value = sort === 'relevance' && serverTotal.value >= RELEVANCE_CANDIDATE_LIMIT;

    if (auth.isLoggedIn) {
      try {
        const favorites = await docsApi.favorites();
        if (version !== requestVersion) return;
        favoritedIds.value = new Set(favorites.map(d => d.id));
      } catch (e) {
        if (version === requestVersion) notify(getApiError(e, '收藏状态加载失败'), 'error');
      }
    }

  } catch (e: unknown) {
    if (version !== requestVersion) return;
    searchError.value = getApiError(e, '搜索失败或网络超时，请重试');
    allResults.value = [];
    serverTotal.value = 0;
    notify(getApiError(e, '搜索失败，请稍后再试'), 'error');
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}

// 首页使用原生表单提交；组合输入不触发导航，导航期间防止重复提交。
async function submitHeroSearch(): Promise<void> {
  const keyword = searchQuery.value.trim();
  if (!keyword || composing.value || heroSubmitting.value) return;
  heroSubmitting.value = true;
  try { await writeQuery(keyword); }
  catch (e: unknown) { notify(getApiError(e, '无法打开搜索结果，请重试'), 'error'); }
  finally { heroSubmitting.value = false; }
}
function handleHeroEnter(event: KeyboardEvent): void {
  if (composing.value || event.isComposing || event.keyCode === 229) return;
  event.preventDefault();
  void submitHeroSearch();
}

function handleSearch(event?: KeyboardEvent): void {
  if (composing.value || event?.isComposing || event?.keyCode === 229) return;
  writeQuery();
}
function searchTag(tag: string): void { searchQuery.value = tag; writeQuery(tag); }
function clearInput(): void { searchQuery.value = ''; writeQuery(''); }

function goToDoc(docId: number, event: MouseEvent): void {
  if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
  event.preventDefault();
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

async function toggleFavorite(doc: DocVO): Promise<void> {
  if (!auth.isLoggedIn) { notify('请先登录', 'warning'); return; }
  if (favoritePending.value.has(doc.id)) return;
  favoritePending.value.add(doc.id);
  try {
    await docsApi.toggleFavorite(doc.id);
    const favorites = await docsApi.favorites();
    favoritedIds.value = new Set(favorites.map(d => d.id));
    notify(favoritedIds.value.has(doc.id) ? '已收藏' : '已取消收藏', 'success');
  } catch (e) { notify(getApiError(e, '收藏失败，请重试'), 'error'); }
  finally { favoritePending.value.delete(doc.id); }
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

// URL 是已提交查询的唯一入口；输入草稿不触发请求。
watch(() => route.query, (query) => {
  requestVersion++;
  searchQuery.value = typeof query.q === 'string' ? query.q.trim() : '';
  activeTab.value = filterTabs.value.some(o => o.key === query.type) ? String(query.type) : 'all';
  activeTime.value = timeOptions.some(o => o.key === query.time) ? String(query.time) : 'all';
  activeSort.value = sortOptions.some(o => o.key === query.sort) ? String(query.sort) : 'relevance';
  pageNum.value = 1;
  hasSearched.value = !!searchQuery.value;
  allResults.value = []; serverTotal.value = 0; searchError.value = ''; loading.value = false;
  if (hasSearched.value) fetchPage(1);
}, { immediate: true });
onBeforeUnmount(() => { requestVersion++; });
</script>

<style scoped>
/* ===== 页面容器 ===== */
.search-result-page {
  max-width: none;
  margin-inline: 0;

  width: 100%;
  min-width: 0;
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
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
}
.hero-search-box {
  display: flex;
  align-items: stretch;
  height: var(--kb-search-hero-height);
  width: 100%;
  max-width: var(--kb-search-width);
  margin: 0 auto;
  padding: 0;
  gap: 0;
  box-sizing: border-box;
  border-radius: var(--kb-space-3);
  overflow: hidden;
  background: var(--kb-field-surface);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  border: 0;
}
.hero-search-box:focus-within {
  background: var(--kb-field-active);
}
.hero-search-icon {
  margin-inline: var(--kb-space-4) var(--kb-space-3);
  align-self: center;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.hero-search-input:not(.kb-form-control),
.hero-search-input:not(.kb-form-control):focus {
  height: 100%;
  background: transparent;
  border: 0;
  border-radius: 0;
  outline: none;
  box-shadow: none;
}
.hero-search-input,
.hero-search-input:focus {
  flex: 1;
  min-width: 0;
  padding: 0;
  font-size: var(--kb-fs-body-lg);
  color: var(--kb-foreground);
}
.hero-search-input::placeholder { color: var(--kb-muted-foreground); }
.hero-clear-btn {
  margin-inline: var(--kb-space-2);
  align-self: center;
  width: var(--kb-space-8);
  height: var(--kb-space-8);
  border-radius: 50%;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.hero-clear-btn:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.hero-clear-btn:active { background: var(--kb-muted); transform: scale(0.94); }
.hero-clear-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.hero-search-btn {
  flex: 0 0 var(--kb-search-hero-button-width);
  align-self: stretch;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  cursor: pointer;
  white-space: nowrap;
  font-size: var(--kb-fs-body-lg);
  font-weight: 500;
  transition: background-color 0.15s ease;
}
.hero-search-btn:hover:not(:disabled),
.hero-search-btn:active:not(:disabled) { background: var(--kb-primary-hover); }
.hero-search-btn:disabled { cursor: not-allowed; }
/* 位于 overflow:hidden 的搜索框内，焦点环内缩避免被裁切 */
.hero-search-btn:focus-visible {
  outline: 2px solid var(--kb-primary-foreground);
  outline-offset: -4px;
}

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
  font-size: var(--kb-fs-body-sm);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: color 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.hot-tag:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}
.hot-tag:active {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.98);
}
.hot-tag:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.hot-tags-label {
  margin-top: 24px;
  font-size: var(--kb-fs-caption);
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
  background: var(--kb-field-surface);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  border: 0;
}
/* 输入聚焦时整框给出反馈（输入框自身 outline: none） */
.compact-search-box:focus-within {
  background: var(--kb-field-active);
}
.compact-search-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
}
.compact-search-input:not(.kb-form-control) {
  background: transparent;
  border: none;
  outline: none;
}
.compact-search-input {
  flex: 1;
  min-width: 0;
  font-size: 15px;
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
  flex-shrink: 0;
  transition: background-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.compact-clear-btn:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.compact-clear-btn:active { background: var(--kb-muted); color: var(--kb-foreground); transform: scale(0.94); }
.compact-clear-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.compact-enter-kbd {
  font-size: var(--kb-fs-caption);
  font-family: var(--font-mono);
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
  white-space: nowrap;
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
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  color: var(--kb-foreground);
}
.filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: var(--kb-radius-sm);
  font-size: var(--kb-fs-caption);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.filter-btn:hover,
.filter-btn.active {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}
.filter-btn:active {
  background: rgba(59, 111, 224, 0.14);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
  transform: scale(0.98);
}
.filter-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.filter-divider {
  color: var(--kb-border);
  margin: 0 2px;
}
.result-count {
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
  white-space: nowrap;
}
.result-count strong {
  color: var(--kb-foreground);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
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
.state-text { margin-top: 12px; font-size: var(--kb-fs-body-md); }
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
  transition: background-color 0.15s ease;
}
.result-item:last-child { border-bottom: none; }
.result-item:hover { background: rgba(59, 111, 224, 0.02); }
.result-item:active { background: rgba(59, 111, 224, 0.06); }
/* 列表容器 overflow:hidden，焦点环内缩避免被裁切 */
.result-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
  background: rgba(59, 111, 224, 0.04);
}

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
  font-size: var(--kb-fs-xs);
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}
/* 语义色统一走 style.css 令牌（--kb-state-* 并未定义，会退化为继承色） */
.badge-pdf { background: rgba(239, 68, 68, 0.1); color: var(--kb-destructive); }
.badge-md { background: rgba(16, 185, 129, 0.1); color: var(--kb-accent); }
.badge-note { background: rgba(245, 158, 11, 0.1); color: var(--kb-warning); }
.badge-doc { background: rgba(59, 111, 224, 0.1); color: var(--kb-primary); }

.result-category {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 标题 */
.result-title {
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* hover 提升到整行，避免仅标题命中时反馈不一致 */
.result-title { transition: color 0.15s ease; }
.result-item:hover .result-title,
.result-item:focus-visible .result-title { color: var(--kb-primary); }

/* 摘要 */
.result-summary {
  font-size: var(--kb-fs-caption);
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
  flex: 1;
  min-width: 0;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  min-width: 0;
  font-variant-numeric: tabular-nums;
}
.meta-item > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  flex-shrink: 0;
  transition: background-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.result-favorite-btn:hover { background: var(--kb-muted); color: var(--kb-warning); }
.result-favorite-btn:active { background: var(--kb-muted); color: var(--kb-warning); transform: scale(0.92); }
.result-favorite-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.result-favorite-btn.active { color: var(--kb-warning); }

/* 关键词高亮 */
:deep(.kw-highlight) {
  background: rgba(245, 158, 11, 0.15);
  color: var(--kb-warning);
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 600;
}

/* ===== 分页器（设计稿 pagination-btn：32x32 方形按钮居中） ===== */
.pagination-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
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
  font-size: var(--kb-fs-body-sm);
  font-variant-numeric: tabular-nums;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  flex-shrink: 0;
  transition: color 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.pagination-btn:hover:not(:disabled):not(.ellipsis) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.pagination-btn:active:not(:disabled):not(.ellipsis):not(.active) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  transform: scale(0.96);
}
.pagination-btn.active:active { transform: scale(0.96); }
.pagination-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.pagination-btn.ellipsis:focus-visible { outline: none; }
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
  /* 窄屏收紧内边距，避免内容贴边或横向溢出 */
  .search-hero { padding-top: 32px; padding-bottom: 48px; }
  .compact-search-box { padding: 12px 16px; border: 0; }
  .result-item { padding: 16px; }
}
</style>

<style scoped>
.compact-search-box { height: var(--kb-search-height); padding: 0 var(--kb-space-4); border-radius: var(--kb-space-3); gap: var(--kb-space-3); border: 0; }
.compact-search-box:focus-within { background: var(--kb-field-active); }
.compact-search-wrap { max-width: var(--kb-search-width); margin-bottom: var(--kb-space-6); }
.compact-search-input:not(.kb-form-control), .compact-search-input:not(.kb-form-control):focus { border: 0; box-shadow: none; outline: none; border-radius: 0; height: 100%; }
.compact-search-input, .compact-search-input:focus { padding: 0; font-size: var(--kb-fs-body-lg); }
.compact-clear-btn { display: inline-flex; align-items: center; justify-content: center; width: var(--kb-space-8); height: var(--kb-space-8); padding: 0; }
.filter-control { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }
.filter-control .filter-select { width: auto; min-width: 0; }
.filter-group { gap: var(--kb-space-4); }
.search-draft-hint { margin-block: var(--kb-space-2); color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption); }
.result-summary { overflow-wrap: anywhere; }
.result-item { position: relative; }
.result-title a::after { content: ""; position: absolute; inset: 0; }
.result-favorite-btn { position: relative; z-index: 1; width: var(--kb-space-8); height: var(--kb-space-8); }
.filter-btn { min-height: var(--kb-search-filter-height); }
.result-item:focus-within { outline: 2px solid var(--kb-ring); outline-offset: -2px; }
:deep(.kw-highlight) { background: var(--kb-primary-soft); color: var(--kb-primary); }
</style>

<style scoped>
.hero-search-box:has(input:focus-visible){outline:0}.hero-search-box input.kb-form-control,.hero-search-box input.kb-form-control:focus,.hero-search-box input.kb-form-control:focus-visible{background:transparent;outline:0;box-shadow:none;border:0}
.compact-search-box:has(input:focus-visible){outline:0}.compact-search-box input.kb-form-control,.compact-search-box input.kb-form-control:focus,.compact-search-box input.kb-form-control:focus-visible{background:transparent;outline:0;box-shadow:none;border:0}
</style>
