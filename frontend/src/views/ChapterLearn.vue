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

          <!-- A-FB-01 相关概念 -->
          <div v-if="relatedConcepts.length > 0" class="mt-6 pt-4 related-section">
            <h4 class="outline-title">相关概念</h4>
            <div class="flex flex-wrap gap-2 px-2">
              <span
                v-for="concept in relatedConcepts"
                :key="concept"
                class="related-concept-chip"
              >{{ concept }}</span>
            </div>
          </div>

          <!-- A-FB-01 相关文档 -->
          <div v-if="relatedDocs.length > 0" class="mt-6 pt-4 related-section">
            <h4 class="outline-title">相关文档</h4>
            <div class="space-y-1 px-1">
              <router-link
                v-for="doc in relatedDocs"
                :key="doc.id"
                :to="`/doc/${doc.id}`"
                class="related-doc-item"
              >
                <span class="related-doc-title">{{ doc.title }}</span>
                <span v-if="doc.summary" class="related-doc-summary">{{ doc.summary }}</span>
              </router-link>
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

      <!-- 下一章：完成操作与其并排，位于右侧 -->
      <div class="flex items-center gap-2">
        <!-- 完成当前章节：未完成未锁定→可点击；已完成→状态徽标；已锁定→禁用 -->
        <template v-if="currentChapter">
          <button
            v-if="!currentChapter.completed && !currentChapter.locked"
            type="button"
            class="complete-btn-inline"
            :disabled="completing"
            @click="completeCurrentChapter"
          >
            <Icon name="check" :size="16" />
            <span>{{ completing ? '提交中...' : '标记本章已完成' }}</span>
          </button>
          <div v-else-if="currentChapter.completed" class="complete-status-inline">
            <Icon name="check-circle" :size="16" style="color: var(--kb-accent);" />
            <span>本章已完成</span>
          </div>
          <div v-else class="complete-status-inline locked">
            <Icon name="lock" :size="14" />
            <span>完成前置章节后解锁</span>
          </div>
        </template>

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
import { computed, ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi, docsApi } from '@/api';
import { notify, getApiError, confirmDialog } from '@/utils/toast';
import { celebrateCertificate } from '@/utils/celebrate';
import type { LearningChapterVO, LearningPathVO, DocVO, LearningCertificateVO } from '@/api/types';

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

// ===== 完成章节 =====
const completing = ref(false);

// 完成最后一章（路径达成 100%）后，查询该路径证书并触发全屏庆祝，避免无感获得证书
const checkPathCertificate = async () => {
  const pathId = currentChapter.value?.pathId;
  if (!pathId || hasNextChapter.value) return;
  try {
    const list = await learningApi.certificates();
    const cert: LearningCertificateVO | undefined = list.find((c) => c.pathId === pathId);
    if (cert?.id) {
      celebrateCertificate({
        id: cert.id,
        pathTitle: cert.pathTitle,
        userName: cert.userName,
        certNo: cert.certNo,
        issueDate: cert.issueDate,
      });
    }
  } catch {
    // 证书查询失败不阻塞学习，静默忽略
  }
};

// 标记当前章节完成；全部章节完成后后端自动颁发数字证书
const completeCurrentChapter = async () => {
  if (!currentChapter.value || currentChapter.value.completed) return;
  if (currentChapter.value.locked) {
    notify('请先完成前置章节', 'warning');
    return;
  }
  completing.value = true;
  try {
    await learningApi.completeChapter(currentChapter.value.id);
    // 更新当前章节与进度点状态
    currentChapter.value.completed = true;
    const idx = currentChapterIndex.value;
    if (idx >= 0) {
      pathChapters.value[idx] = { ...pathChapters.value[idx], completed: true };
    }
    stopVideoTimer();
    notify('章节已完成！', 'success');

    // 完成最后一章后检测证书并弹出全屏庆祝
    await checkPathCertificate();

    // 还有下一章则自动跳转
    if (hasNextChapter.value) {
      await nextTick();
      goToNextChapter();
    }
  } catch (e) {
    const msg = getApiError(e);
    // 未报名路径时引导报名：确认后先在章节页报名，再自动重新完成
    if (msg.includes('报名')) {
      const p = currentChapter.value?.pathId;
      const ok = await confirmDialog('尚未报名该学习路径，是否立即报名后完成本章？');
      if (ok && p) {
        completing.value = true;
        try {
          await learningApi.enroll(p);
          notify('报名成功，正在完成章节...', 'success');
          await learningApi.completeChapter(currentChapter.value!.id);
          currentChapter.value!.completed = true;
          const idx = currentChapterIndex.value;
          if (idx >= 0) {
            pathChapters.value[idx] = { ...pathChapters.value[idx], completed: true };
          }
          stopVideoTimer();
          notify('章节已完成！', 'success');
          // 完成最后一章后检测证书并弹出全屏庆祝
          await checkPathCertificate();
          if (hasNextChapter.value) {
            await nextTick();
            goToNextChapter();
          }
        } catch {
          notify('操作失败，请稍后重试', 'error');
        }
      }
    } else {
      notify(msg || '提交失败，请重试', 'error');
    }
  } finally {
    completing.value = false;
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
  let codeLang = '';
  let headingIdx = 0;
  for (const raw of md.split('\n')) {
    const line = raw.trimEnd();
    const fence = /^```(\w*)/.exec(line);
    if (fence) {
      if (!inCode) {
        inCode = true;
        codeLang = fence[1] || '';
        codeBuf = [];
      } else {
        // L-FORM-02 互动讲义：```xxx-run 代码块渲染为可运行组件；```quiz-run 渲染为内嵌测验
        if (codeLang === 'quiz-run') {
          html += `<div class="interactive-quiz" data-quiz="${escapeHtml(codeBuf.join('\n'))}"></div>`;
        } else if (codeLang.endsWith('-run')) {
          const code = escapeHtml(codeBuf.join('\n'));
          html += `<div class="interactive-code" data-lang="${escapeHtml(codeLang.replace(/-run$/, ''))}" data-code="${code}"></div>`;
        } else {
          html += `<pre><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`;
        }
        inCode = false;
        codeLang = '';
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
    // L-FORM-01：章节嵌入视频，约定语法 `[video](https://...)` 或 `[视频](https://...)`
    const videoMatch = /^\[(?:video|视频)\]\((.+?)\)\s*$/.exec(line.trim());
    if (videoMatch) {
      const url = videoMatch[1].trim();
      html += `<div class="chapter-video" data-video-url="${escapeHtml(url)}"></div>`;
      continue;
    }
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
    // A-FB-01：加载相关文档推荐
    await loadRelatedDocs(c);
  } catch {
    notify('章节加载失败', 'error');
  } finally {
    loading.value = false;
    // 渲染完成后滚动到顶部
    await nextTick();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
};

// ===== A-FB-01 相关概念/文档推荐 =====
const relatedDocs = ref<DocVO[]>([]);
const relatedConcepts = computed(() => {
  // 从章节标题 + 路径标题提取概念标签（按中文/英文分词关键词）
  const source = `${currentChapter.value?.title || ''} ${pathDetail.value?.title || ''}`;
  const words = source.split(/[\s·,，、/]+/).filter((w) => w.length >= 2);
  return words.slice(0, 6);
});

const loadRelatedDocs = async (chapter: LearningChapterVO | null) => {
  relatedDocs.value = [];
  const keyword = (chapter?.title || '').replace(/\s+/g, ' ').trim();
  if (!keyword) return;
  try {
    const page = await docsApi.list({ keyword, pageSize: 5 });
    // 过滤掉标题与章节标题完全一致的"自身"，避免推荐同一章节来源文档混淆
    relatedDocs.value = (page.records || []).filter((d) => d.title !== chapter?.title);
  } catch {
    relatedDocs.value = [];
  }
};

// ===== L-FORM-01 章节嵌入视频：外链 iframe + 观看进度追踪 =====
const videoProgress = ref(0);
let videoTimer: ReturnType<typeof setInterval> | null = null;

// 判断章节是否含视频（正文中出现约定 video 语法）
const hasVideo = computed(() => /\[(?:video|视频)\]\(.+?\)/.test(currentChapter.value?.content || ''));

// 将通用视频链接转换为可内嵌的 iframe 地址
const toEmbedUrl = (raw: string): string => {
  const url = raw.trim();
  // YouTube：watch?v= 或 youtu.be/
  const yt = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]+)/);
  if (yt) return `https://www.youtube.com/embed/${yt[1]}`;
  // Bilibili：BV 号视频
  const bv = url.match(/bilibili\.com\/video\/([A-Za-z0-9]+)/);
  if (bv) return `https://player.bilibili.com/player.html?bvid=${bv[1]}&page=1`;
  // 已是可嵌入地址或其它外链则原样嵌入
  return url;
};

// 上报视频观看进度（节流：每 15 秒一次）
const reportVideoProgress = async (progress: number) => {
  if (!currentChapterId.value) return;
  try {
    const current = await learningApi.updateVideoProgress(currentChapterId.value, progress);
    videoProgress.value = current;
    if (current >= 90 && !currentChapter.value?.completed) {
      notify('视频已看完，可完成本章节了', 'success');
      stopVideoTimer();
    }
  } catch {
    /* 进度上报失败不影响阅读，静默忽略 */
  }
};

const startVideoTimer = () => {
  stopVideoTimer();
  if (!hasVideo.value) return;
  // 基于观看停留时间估算进度：起始为已存进度，每 20 秒累加（约 3 分钟看完折算 100%）
  videoProgress.value = currentChapter.value?.videoProgress || 0;
  videoTimer = setInterval(() => {
    if (document.hidden) return;
    const next = Math.min(100, Math.round((videoProgress.value + 3.5) * 100) / 100);
    videoProgress.value = next;
    reportVideoProgress(next);
  }, 20000);
};

const stopVideoTimer = () => {
  if (videoTimer) {
    clearInterval(videoTimer);
    videoTimer = null;
  }
};

// ===== L-FORM-02 互动讲义：内嵌可运行代码（浏览器端执行 JS） =====
const runJavaScript = (code: string): { output: string; error: string | null } => {
  const logs: string[] = [];
  const originalLog = console.log;
  const originalWarn = console.warn;
  const originalError = console.error;
  const capture = (...args: unknown[]) => logs.push(args.map(String).join(' '));
  console.log = capture;
  console.warn = capture;
  console.error = capture;
  try {
    const fn = new Function(code);
    const result = fn();
    if (result !== undefined) logs.push(String(result));
    return { output: logs.join('\n') || '(无输出)', error: null };
  } catch (e) {
    return { output: logs.join('\n'), error: e instanceof Error ? e.message : String(e) };
  } finally {
    console.log = originalLog;
    console.warn = originalWarn;
    console.error = originalError;
  }
};

const bindInteractiveCode = (container: HTMLElement) => {
  if (container.querySelector('.code-runner')) return;
  const lang = container.getAttribute('data-lang') || 'js';
  const initCode = container.getAttribute('data-code') || '';
  container.innerHTML = `
    <div class="code-runner">
      <div class="code-runner-head">
        <span class="code-runner-lang">${escapeHtml(lang)} · 可运行</span>
        <button type="button" class="code-run-btn">运行</button>
      </div>
      <textarea class="code-run-input" spellcheck="false">${initCode}</textarea>
      <pre class="code-run-output"></pre>
    </div>`;
  const runBtn = container.querySelector<HTMLButtonElement>('.code-run-btn');
  const input = container.querySelector<HTMLTextAreaElement>('.code-run-input');
  const output = container.querySelector<HTMLElement>('.code-run-output');
  runBtn?.addEventListener('click', () => {
    if (!input || !output) return;
    const { output: out, error } = runJavaScript(input.value);
    output.textContent = error ? `⚠️ ${error}` : out;
    output.classList.toggle('has-error', Boolean(error));
  });
};

// ===== L-FORM-02 / A-FB-02 章节内嵌即时测验 =====
interface EmbeddedQuiz {
  question: string;
  options: string[];
  answer: string;
  explanation: string;
}

// 解析约定 quiz 文本：`Q:` 题干、`A./B./...` 选项、`答案:` 与 `解析:`
const parseQuiz = (raw: string): EmbeddedQuiz | null => {
  const lines = raw.split('\n').map((l) => l.trim()).filter(Boolean);
  const qLine = lines.find((l) => l.startsWith('Q:'));
  if (!qLine) return null;
  const question = qLine.replace(/^Q:\s*/, '');
  const options: string[] = [];
  for (const l of lines) {
    const m = /^([A-H])[.、．]\s*(.+)$/.exec(l);
    if (m) options.push(`${m[1]}. ${m[2]}`);
  }
  const ansLine = lines.find((l) => /^答案[:：]/.test(l));
  const ansMatch = ansLine ? /^答案[:：]\s*([A-H])/.exec(ansLine) : null;
  const answer = ansMatch ? ansMatch[1].toUpperCase() : '';
  const expLine = lines.find((l) => /^解析[:：]/.test(l));
  const explanation = expLine ? expLine.replace(/^解析[:：]\s*/, '') : '';
  if (!question || options.length === 0 || !answer) return null;
  return { question, options, answer, explanation };
};

const bindInteractiveQuiz = (container: HTMLElement) => {
  if (container.querySelector('.quiz-card')) return;
  const raw = container.getAttribute('data-quiz') || '';
  const quiz = parseQuiz(raw);
  if (!quiz) {
    container.innerHTML = '<pre class="quiz-invalid">测验格式有误，请检查 `Q:` / 选项 / `答案:` 定义</pre>';
    return;
  }
  const optionBtns = quiz.options.map((opt) =>
    `<button type="button" class="quiz-opt" data-value="${escapeHtml(opt[0])}"><span class="quiz-opt-label">${escapeHtml(opt)}</span></button>`,
  ).join('');
  container.innerHTML = `
    <div class="quiz-card">
      <div class="quiz-head">
        <span class="quiz-badge">即时测验</span>
      </div>
      <p class="quiz-question">${escapeHtml(quiz.question)}</p>
      <div class="quiz-options">${optionBtns}</div>
      <p class="quiz-feedback" style="display:none;"></p>
    </div>`;
  container.querySelectorAll<HTMLButtonElement>('.quiz-opt').forEach((btn) => {
    btn.addEventListener('click', () => {
      const chosen = btn.getAttribute('data-value') || '';
      const correct = chosen === quiz.answer;
      container.querySelectorAll<HTMLButtonElement>('.quiz-opt').forEach((b) => {
        b.classList.remove('quiz-correct', 'quiz-wrong');
        if (b.getAttribute('data-value') === quiz.answer) b.classList.add('quiz-correct');
        if (b === btn && !correct) b.classList.add('quiz-wrong');
        b.disabled = true;
      });
      const feedback = container.querySelector<HTMLElement>('.quiz-feedback');
      if (feedback) {
        feedback.style.display = 'block';
        feedback.textContent = correct
          ? `✅ 回答正确${quiz.explanation ? `：${quiz.explanation}` : ''}`
          : `❌ 回答错误，正确答案是 ${quiz.answer}${quiz.explanation ? `。${quiz.explanation}` : ''}`;
        feedback.classList.toggle('quiz-correct-msg', correct);
        feedback.classList.toggle('quiz-wrong-msg', !correct);
      }
    });
  });
};

// 正文渲染完成后，将视频占位容器替换为 iframe 播放器，并初始化互动代码块/测验
watch(renderedContent, () => {
  nextTick(() => {
    const holders = document.querySelectorAll<HTMLElement>('.chapter-video[data-video-url]');
    if (holders.length > 0) {
      holders.forEach((holder) => {
        if (holder.querySelector('iframe')) return;
        const url = holder.getAttribute('data-video-url') || '';
        const embed = document.createElement('iframe');
        embed.src = toEmbedUrl(url);
        embed.setAttribute('frameborder', '0');
        embed.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture');
        embed.setAttribute('allowfullscreen', '');
        embed.classList.add('chapter-video-frame');
        holder.appendChild(embed);
      });
    }
    const codeBlocks = document.querySelectorAll<HTMLElement>('.interactive-code[data-code]');
    codeBlocks.forEach(bindInteractiveCode);
    const quizBlocks = document.querySelectorAll<HTMLElement>('.interactive-quiz[data-quiz]');
    quizBlocks.forEach(bindInteractiveQuiz);
  });
  if (hasVideo.value) startVideoTimer();
});

onMounted(() => loadChapter(currentChapterId.value));

// 离开页面时停止进度计时，避免泄漏
onBeforeUnmount(() => stopVideoTimer());
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

/* L-FORM-01 章节嵌入视频 */
.chapter-video {
  position: relative;
  width: 100%;
  margin: 16px 0;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: #000;
  border: 1px solid var(--kb-border);
}
.chapter-video-frame {
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
}

/* L-FORM-02 互动讲义：内嵌可运行代码 */
.interactive-code {
  margin: 16px 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--kb-border);
}
.code-runner {
  display: flex;
  flex-direction: column;
}
.code-runner-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--kb-muted);
}
.code-runner-lang {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  font-family: 'Fira Code', 'Consolas', monospace;
}
.code-run-btn {
  padding: 4px 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.code-run-btn:hover {
  opacity: 0.9;
}
.code-run-input {
  width: 100%;
  min-height: 140px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  font-family: 'Fira Code', 'Consolas', monospace;
  color: var(--kb-foreground);
  background: var(--kb-card);
  border: none;
  border-top: 1px solid var(--kb-border);
  resize: vertical;
  outline: none;
}
.code-run-output {
  min-height: 40px;
  margin: 0;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  font-family: 'Fira Code', 'Consolas', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--kb-foreground);
  color: var(--kb-background);
  border-top: 1px solid var(--kb-border);
}
.code-run-output.has-error {
  color: #fca5a5;
}

/* L-FORM-02 / A-FB-02 章节内嵌即时测验 */
.interactive-quiz {
  margin: 16px 0;
}
.quiz-card {
  padding: 16px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
}
.quiz-head {
  margin-bottom: 8px;
}
.quiz-badge {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.1);
  border-radius: 999px;
}
.quiz-question {
  margin: 8px 0 12px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
  color: var(--kb-foreground);
}
.quiz-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.quiz-opt {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  font-size: 14px;
  text-align: left;
  color: var(--kb-card-foreground);
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.quiz-opt:hover:not(:disabled) {
  border-color: var(--kb-primary);
}
.quiz-opt:disabled {
  cursor: default;
}
.quiz-opt-label {
  line-height: 1.5;
}
.quiz-opt.quiz-correct {
  border-color: var(--kb-accent);
  background: rgba(16, 185, 129, 0.08);
}
.quiz-opt.quiz-wrong {
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}
.quiz-feedback {
  margin-top: 12px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
  border-radius: 6px;
}
.quiz-feedback.quiz-correct-msg {
  color: #047857;
  background: rgba(16, 185, 129, 0.1);
}
.quiz-feedback.quiz-wrong-msg {
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.1);
}
.quiz-invalid {
  padding: 12px;
  font-size: 13px;
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.08);
  border-radius: 6px;
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
/* A-FB-01 相关概念/文档 */
.related-section {
  border-top: 1px solid var(--kb-border);
}
.related-concept-chip {
  padding: 4px 10px;
  font-size: 12px;
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  border: 1px solid rgba(59, 111, 224, 0.15);
  border-radius: 999px;
}
.related-doc-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 6px;
  text-decoration: none;
  transition: background-color 0.15s ease;
}
.related-doc-item:hover {
  background: var(--kb-muted);
}
.related-doc-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-sidebar-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.related-doc-summary {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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

/* 底部栏「标记本章已完成」按钮：与下一章并排、适度强调（品牌绿描边+细阴影），区别于收藏/笔记的次要样式 */
.complete-btn-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-accent);
  background: var(--kb-card);
  border: 1.5px solid var(--kb-accent);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.18);
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease, opacity 0.15s ease;
}
.complete-btn-inline:hover:not(:disabled) {
  background: var(--kb-accent);
  color: #ffffff;
  transform: translateY(-1px);
}
.complete-btn-inline:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.complete-status-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-accent);
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.25);
}
.complete-status-inline.locked {
  color: var(--kb-muted-foreground);
  background: var(--kb-muted);
  border-color: var(--kb-border);
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
