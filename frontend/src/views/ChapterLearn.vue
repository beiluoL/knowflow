<template>
  <div class="chapter-learn">
    <!-- 主体：左侧正文 + 右侧大纲 -->
    <div class="flex">
      <!-- 左侧课程内容 -->
      <div class="flex-1 min-w-0">
        <!-- 面包屑 -->
        <nav class="flex items-center gap-2 text-sm px-6 py-3 breadcrumb-bar">
          <router-link to="/learning/center" class="font-medium breadcrumb-link">学习中心</router-link>
          <Icon name="chevron-right" :size="14" class="breadcrumb-sep" />
          <router-link to="/learning/paths" class="font-medium breadcrumb-link">学习路径</router-link>
          <Icon name="chevron-right" :size="14" class="breadcrumb-sep" />
          <router-link
            v-if="pathDetail"
            :to="`/learning/path/${pathDetail.id}`"
            class="font-medium breadcrumb-link"
          >{{ pathDetail.title }}</router-link>
          <Icon name="chevron-right" :size="14" class="breadcrumb-sep" />
          <span class="font-medium" style="color: var(--kb-foreground);">第 {{ currentChapter?.sortOrder || 1 }} 章</span>
        </nav>

        <!-- 进度点 -->
        <div v-if="pathChapters.length > 0" class="px-6 py-4 flex items-center gap-1 progress-bar-area">
          <template v-for="(ch, idx) in pathChapters" :key="ch.id">
            <!-- 连接线（非首项） -->
            <div
              v-if="idx > 0"
              class="progress-line"
              :style="{ background: ch.completed ? 'var(--kb-accent)' : 'var(--kb-muted)' }"
            ></div>
            <!-- 进度点 -->
            <div
              class="progress-dot"
              :class="{ 'is-current': ch.id === currentChapterId, 'is-done': ch.completed }"
              :title="ch.completed ? '已完成' : ch.id === currentChapterId ? '当前章节' : '未开始'"
              @click="goToChapter(ch.id)"
            ></div>
          </template>
          <div class="ml-3 text-[13px] font-medium" style="color: var(--kb-primary);">
            第 {{ currentChapter?.sortOrder || 1 }}/{{ pathChapters.length }} 章
          </div>
        </div>

        <!-- 课程内容正文 -->
        <div class="px-6 py-6 content-body" style="max-width: 960px;">
          <!-- 加载态 -->
          <div v-if="loading" class="space-y-4">
            <div class="h-8 rounded animate-pulse" style="background: var(--kb-muted);"></div>
            <div class="h-4 rounded animate-pulse w-3/4" style="background: var(--kb-muted);"></div>
            <div class="h-4 rounded animate-pulse w-full" style="background: var(--kb-muted);"></div>
            <div class="h-4 rounded animate-pulse w-5/6" style="background: var(--kb-muted);"></div>
          </div>
          <!-- 正文 -->
          <template v-else-if="currentChapter">
            <h2>{{ currentChapter.title }}</h2>
            <div v-html="renderedContent"></div>
          </template>
          <!-- 空态 -->
          <p v-else class="text-center py-12" style="color: var(--kb-muted-foreground);">本章暂无内容</p>
        </div>
      </div>

      <!-- 右侧大纲 -->
      <aside class="w-72 shrink-0 border-l outline-sidebar">
        <div class="sticky top-14 max-h-[calc(100vh-56px)] overflow-y-auto no-scrollbar p-4">
          <!-- 本章大纲 -->
          <h4 class="outline-title">本章大纲</h4>
          <div class="space-y-1">
            <a
              v-for="(item, idx) in outlineItems"
              :key="idx"
              class="outline-item"
              :class="{ active: idx === activeOutlineIndex, sub: item.sub }"
              :href="`#${item.id}`"
              @click.prevent="scrollToHeading(item.id)"
            >{{ item.text }}</a>
            <p v-if="outlineItems.length === 0" class="px-3 py-2 text-[13px]" style="color: var(--kb-muted-foreground);">暂无大纲</p>
          </div>

          <!-- 章节信息 -->
          <div class="mt-6 pt-4 chapter-info-section">
            <h4 class="outline-title">章节信息</h4>
            <div class="space-y-2 px-2">
              <div class="flex items-center justify-between text-[13px]">
                <span style="color: var(--kb-muted-foreground);">预计时长</span>
                <span class="font-medium" style="color: var(--kb-foreground);">{{ currentChapter?.duration || 0 }} 分钟</span>
              </div>
              <div class="flex items-center justify-between text-[13px]">
                <span style="color: var(--kb-muted-foreground);">难度等级</span>
                <span class="font-medium" style="color: var(--kb-warning);">{{ pathDetail?.level || '中等' }}</span>
              </div>
              <div class="flex items-center justify-between text-[13px]">
                <span style="color: var(--kb-muted-foreground);">章节序号</span>
                <span class="font-medium" style="color: var(--kb-foreground);">第 {{ currentChapter?.sortOrder || 1 }} 章</span>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 底部固定操作栏 -->
    <div class="fixed bottom-0 left-0 right-0 h-14 flex items-center justify-between px-6 border-t z-40 bottom-action-bar">
      <!-- 上一章 -->
      <button
        type="button"
        class="flex items-center gap-2 text-sm font-medium px-4 py-2 rounded-lg transition-colors bottom-prev"
        :disabled="!hasPrevChapter"
        @click="goToPrevChapter"
      >
        <Icon name="arrow-left" :size="16" />
        <span>上一章{{ prevChapterTitle ? `：${prevChapterTitle}` : '' }}</span>
      </button>

      <!-- 中间操作 -->
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bottom-icon-btn"
          @click="toggleFavorite"
        >
          <Icon :name="isFavorite ? 'bookmark-check' : 'bookmark'" :size="16" />
          {{ isFavorite ? '已收藏' : '收藏' }}
        </button>
        <button
          type="button"
          class="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bottom-icon-btn"
          @click="goToNotes"
        >
          <Icon name="notebook-pen" :size="16" />
          笔记
        </button>
      </div>

      <!-- 下一章 -->
      <button
        type="button"
        class="flex items-center gap-2 text-sm font-medium px-4 py-2 rounded-lg bottom-next"
        :disabled="!hasNextChapter"
        @click="goToNextChapter"
      >
        <span>下一章{{ nextChapterTitle ? `：${nextChapterTitle}` : '' }}</span>
        <Icon name="arrow-right" :size="16" />
      </button>
    </div>

    <!-- 给底部栏留空间 -->
    <div class="h-14"></div>
  </div>
</template>

<script setup lang="ts">
/**
 * 章节学习页（沉浸式双栏阅读）
 * 设计稿对齐：左侧面包屑 + 进度点 + Markdown 正文；右侧固定大纲 + 章节信息；底部固定操作栏（上一章/收藏/笔记/下一章）。
 */
import { computed, ref, onMounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import { notify } from '@/utils/toast';
import type { LearningChapterVO, LearningPathVO } from '@/api/types';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const currentChapterId = computed(() => Number(route.params.id));
const currentChapter = ref<LearningChapterVO | null>(null);
const pathChapters = ref<LearningChapterVO[]>([]);
const pathDetail = ref<LearningPathVO | null>(null);
const isFavorite = ref(false);
const activeOutlineIndex = ref(0);

// ===== 面包屑与章节导航 =====
const currentChapterIndex = computed(() =>
  pathChapters.value.findIndex((c) => c.id === currentChapterId.value),
);
const hasPrevChapter = computed(() => currentChapterIndex.value > 0);
const hasNextChapter = computed(() => currentChapterIndex.value < pathChapters.value.length - 1);
const prevChapterTitle = computed(() =>
  hasPrevChapter.value ? pathChapters.value[currentChapterIndex.value - 1]?.title : '',
);
const nextChapterTitle = computed(() =>
  hasNextChapter.value ? pathChapters.value[currentChapterIndex.value + 1]?.title : '',
);

const goToChapter = (chapterId: number) => router.push(`/learning/chapter/${chapterId}`);
const goToPrevChapter = () => {
  if (hasPrevChapter.value) {
    const prev = pathChapters.value[currentChapterIndex.value - 1];
    router.push(`/learning/chapter/${prev.id}`);
  }
};
const goToNextChapter = () => {
  if (hasNextChapter.value) {
    const next = pathChapters.value[currentChapterIndex.value + 1];
    router.push(`/learning/chapter/${next.id}`);
  }
};

// ===== 收藏与笔记 =====
const toggleFavorite = () => {
  isFavorite.value = !isFavorite.value;
  notify(isFavorite.value ? '已收藏本章' : '已取消收藏', 'success');
};
const goToNotes = () => router.push('/notes');

// ===== 大纲：从正文标题（h2/h3）提取 =====
interface OutlineItem {
  id: string;
  text: string;
  sub: boolean;
}
const outlineItems = computed<OutlineItem[]>(() => {
  if (!currentChapter.value?.content) return [];
  const items: OutlineItem[] = [];
  const lines = currentChapter.value.content.split('\n');
  let idx = 0;
  for (const line of lines) {
    const m = /^(#{2,3})\s+(.*)$/.exec(line.trim());
    if (m) {
      idx++;
      items.push({
        id: `heading-${idx}`,
        text: m[2].replace(/[*`]/g, '').trim(),
        sub: m[1].length === 3,
      });
    }
  }
  return items;
});

const scrollToHeading = (id: string) => {
  const el = document.getElementById(id);
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

// ===== Markdown 渲染 =====
const renderedContent = computed(() => renderMarkdown(currentChapter.value?.content || '', outlineItems.value));

// 转义 HTML 特殊字符，防止注入并正确显示代码内容
const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// 轻量 Markdown 渲染器：支持代码块（```）、h2/h3（带锚点 id）、无序/有序列表、行内 code 与段落
const renderMarkdown = (md: string, outline: OutlineItem[]): string => {
  if (!md) return '';
  let html = '';
  let inCode = false;
  let codeBuf: string[] = [];
  let headingIdx = 0;
  for (const raw of md.split('\n')) {
    const line = raw.trimEnd();
    const fence = /^```(\w*)/.exec(line);
    if (fence) {
      if (!inCode) {
        inCode = true;
        codeBuf = [];
      } else {
        html += `<pre><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`;
        inCode = false;
      }
      continue;
    }
    if (inCode) {
      codeBuf.push(line);
      continue;
    }
    const h = /^(#{2,3})\s+(.*)$/.exec(line);
    if (h) {
      headingIdx++;
      const text = h[2].replace(/[*`]/g, '').trim();
      const id = outline[headingIdx - 1]?.id || `heading-${headingIdx}`;
      html += `<h${h[1].length} id="${id}">${escapeHtml(text)}</h${h[1].length}>`;
      continue;
    }
    if (/^[-*]\s+/.test(line)) {
      html += `<ul><li>${escapeHtml(line.replace(/^[-*]\s+/, ''))}</li></ul>`;
      continue;
    }
    if (/^\d+\.\s+/.test(line)) {
      html += `<ol><li>${escapeHtml(line.replace(/^\d+\.\s+/, ''))}</li></ol>`;
      continue;
    }
    if (line === '') continue;
    // 行内 code
    const inline = escapeHtml(line).replace(/`([^`]+)`/g, '<code>$1</code>');
    html += `<p>${inline}</p>`;
  }
  if (inCode) html += `<pre><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`;
  // 合并相邻同类列表
  return html.replace(/<\/ul><ul>/g, '').replace(/<\/ol><ol>/g, '');
};

// ===== 数据加载 =====
const loadChapter = async (id: number) => {
  loading.value = true;
  try {
    const c = await learningApi.chapterDetail(id);
    currentChapter.value = c;
    if (c.pathId) {
      const [chapters, detail] = await Promise.all([
        learningApi.chapters(c.pathId),
        learningApi.pathDetail(c.pathId).catch(() => null),
      ]);
      pathChapters.value = chapters;
      pathDetail.value = detail;
    }
  } catch {
    notify('章节加载失败', 'error');
  } finally {
    loading.value = false;
    // 渲染完成后滚动到顶部
    await nextTick();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
};

onMounted(() => loadChapter(currentChapterId.value));
</script>

<style scoped>
/* 面包屑 */
.breadcrumb-bar {
  border-bottom: 1px solid var(--kb-border);
}
.breadcrumb-link {
  color: var(--kb-muted-foreground);
  transition: color 0.15s ease;
}
.breadcrumb-link:hover {
  color: var(--kb-primary);
}
.breadcrumb-sep {
  color: var(--kb-muted-foreground);
}

/* 进度点 */
.progress-bar-area {
  border-bottom: 1px solid var(--kb-border);
}
.progress-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--kb-muted);
  cursor: pointer;
  transition: transform 0.15s ease;
}
.progress-dot:hover {
  transform: scale(1.2);
}
.progress-dot.is-done {
  background: var(--kb-accent);
}
.progress-dot.is-current {
  background: var(--kb-primary);
  box-shadow: 0 0 0 2px var(--kb-card), 0 0 0 4px var(--kb-primary);
}
.progress-line {
  width: 24px;
  height: 1px;
  flex-shrink: 0;
}

/* 正文 Markdown 排版（与设计稿 .content-body 对齐） */
.content-body :deep(h2) {
  font-size: 20px;
  font-weight: 600;
  margin: 24px 0 12px;
  color: var(--kb-foreground);
  scroll-margin-top: 80px;
}
.content-body :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: var(--kb-foreground);
  scroll-margin-top: 80px;
}
.content-body :deep(p) {
  margin: 8px 0;
  line-height: 1.7;
  color: var(--kb-card-foreground);
}
.content-body :deep(code) {
  background: var(--kb-muted);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 14px;
  font-family: 'Fira Code', 'Consolas', 'Monaco', monospace;
}
.content-body :deep(pre) {
  background: var(--kb-foreground);
  color: var(--kb-background);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
  font-size: 14px;
  line-height: 1.6;
}
.content-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.content-body :deep(ul),
.content-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.content-body :deep(li) {
  margin: 4px 0;
  line-height: 1.6;
}

/* 右侧大纲 */
.outline-sidebar {
  background: var(--kb-sidebar);
  border-color: var(--kb-border);
}
.outline-title {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 12px;
  padding-left: 8px;
  color: var(--kb-muted-foreground);
}
.outline-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 14px;
  color: var(--kb-sidebar-foreground);
  text-decoration: none;
  transition: background-color 0.15s ease;
  cursor: pointer;
}
.outline-item:hover {
  background: var(--kb-muted);
}
.outline-item.active {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  font-weight: 500;
}
.outline-item.sub {
  padding-left: 32px;
  font-size: 13px;
}
.chapter-info-section {
  border-top: 1px solid var(--kb-border);
}

/* 底部操作栏 */
.bottom-action-bar {
  background: var(--kb-card);
  border-color: var(--kb-border);
}
.bottom-prev {
  color: var(--kb-muted-foreground);
  transition: background 0.15s ease;
}
.bottom-prev:not(:disabled):hover {
  background: var(--kb-muted);
}
.bottom-prev:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.bottom-icon-btn {
  color: var(--kb-muted-foreground);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  transition: background 0.15s ease, color 0.15s ease;
}
.bottom-icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}
.bottom-next {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  transition: opacity 0.2s ease;
}
.bottom-next:not(:disabled):hover {
  opacity: 0.9;
}
.bottom-next:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 滚动条隐藏 */
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

/* 响应式：移动端隐藏右侧大纲，底部操作栏简化 */
@media (max-width: 768px) {
  .outline-sidebar {
    display: none;
  }
  .bottom-prev span,
  .bottom-next span {
    display: none;
  }
}
</style>
