<template>
  <!--
    学习周报页（P4-5 + P5 升级）：
    展示当前周报（含 summary / achievements / suggestions / 统计）与历史周报列表。
    支持手动生成本周周报，历史周报可展开查看详情。
    P5 升级：模式分布、AI 洞见、分享、热力图徽标、PDF 下载、历史 miniStats
  -->
  <div class="weekly-report-page animate-fade-in">
    <!-- 页头 -->
    <div class="wr-header">
      <div class="wr-header-left">
        <button type="button" class="wr-back-btn" title="返回" @click="router.back()">
          <Icon name="arrow-left" :size="18" />
        </button>
        <h1 class="wr-title">学习周报</h1>
        <!-- D. 本周热力图小圆徽标 -->
        <div class="wr-heat-badge" title="本周7天学习活跃度">
          <div
            v-for="(v, i) in weekHeatMini"
            :key="i"
            class="wr-heat-dot"
            :style="{ background: miniHeatColor(v) }"
          />
        </div>
      </div>
      <div class="wr-header-actions">
        <button
          type="button"
          class="wr-generate-btn"
          :disabled="generating"
          @click="handleGenerate"
        >
          <Icon name="sparkles" :size="16" />
          <span>{{ generating ? '生成中…' : '生成本周周报' }}</span>
        </button>
        <!-- F. 下载周报PDF按钮 -->
        <button
          type="button"
          class="wr-secondary-btn"
          :disabled="!currentReport"
          @click="handleDownloadPdf"
        >
          <Icon name="download" :size="16" />
          <span>下载PDF</span>
        </button>
        <!-- C. 分享按钮 -->
        <button
          type="button"
          class="wr-secondary-btn"
          :disabled="!currentReport && !metrics"
          @click="handleShare"
        >
          <Icon name="share-2" :size="16" />
          <span>分享</span>
        </button>
      </div>
    </div>

    <!-- 错误态 -->
    <div v-if="error" class="wr-error">
      <Icon name="alert-circle" :size="32" />
      <p>{{ error }}</p>
      <button type="button" class="wr-retry-btn" @click="loadAll">重新加载</button>
    </div>

    <!-- 加载态 -->
    <div v-else-if="loading" class="wr-skeleton">
      <div class="wr-skeleton-card" />
      <div class="wr-skeleton-card" />
    </div>

    <!-- 内容 -->
    <template v-else>
      <!-- 当前周报 -->
      <section class="wr-section">
        <h2 class="wr-section-title">
          <Icon name="calendar" :size="18" />
          <span>本周周报</span>
        </h2>

        <div v-if="currentReport" class="wr-card wr-current-card">
          <div class="wr-card-head">
            <div class="wr-period">
              <Icon name="calendar-range" :size="16" />
              <span>{{ currentReport.weekStart }} ~ {{ currentReport.weekEnd }}</span>
            </div>
          </div>

          <!-- 统计 -->
          <div class="wr-stats">
            <div class="wr-stat">
              <div class="wr-stat-icon" style="background: rgba(59,111,224,0.1); color: var(--kb-primary);">
                <Icon name="clock" :size="18" />
              </div>
              <div class="wr-stat-num tabular-nums">{{ currentReport.studyMinutes }}</div>
              <div class="wr-stat-label">学习分钟</div>
            </div>
            <div class="wr-stat">
              <div class="wr-stat-icon" style="background: rgba(16,185,129,0.1); color: var(--kb-accent);">
                <Icon name="check-circle" :size="18" />
              </div>
              <div class="wr-stat-num tabular-nums">{{ currentReport.checkinDays }}</div>
              <div class="wr-stat-label">打卡天数</div>
            </div>
            <div class="wr-stat">
              <div class="wr-stat-icon" style="background: rgba(245,158,11,0.1); color: var(--kb-warning);">
                <Icon name="layers" :size="18" />
              </div>
              <div class="wr-stat-num tabular-nums">{{ currentReport.flashcardReviewed }}</div>
              <div class="wr-stat-label">复习闪卡</div>
            </div>
          </div>

          <!-- A. 本周专注模式分布（stacked bar） -->
          <div class="wr-block">
            <h3 class="wr-block-title">
              <Icon name="pie-chart" :size="16" />
              <span>本周专注模式分布</span>
              <span v-if="metrics?.focusStats?.weekMinutes" class="wr-week-minutes tabular-nums">
                共 {{ metrics.focusStats.weekMinutes }} 分钟
              </span>
            </h3>
            <template v-if="metricsLoading">
              <div class="wr-stacked-skeleton" />
            </template>
            <template v-else-if="modeSegments.length">
              <div class="wr-stacked-bar">
                <div
                  v-for="seg in modeSegments"
                  :key="seg.mode"
                  class="wr-stacked-seg"
                  :style="{ width: seg.percent + '%', background: seg.color }"
                  :title="`${seg.label} ${seg.minutes}分钟 (${seg.percent.toFixed(1)}%)`"
                />
              </div>
              <div class="wr-stacked-legend">
                <div v-for="seg in modeSegments" :key="seg.mode" class="wr-legend-item">
                  <span class="wr-legend-dot" :style="{ background: seg.color }" />
                  <span class="wr-legend-label">{{ seg.label }}</span>
                  <span class="wr-legend-pct tabular-nums">{{ seg.percent.toFixed(0) }}%</span>
                </div>
              </div>
            </template>
            <div v-else class="wr-empty-mini">暂无模式数据</div>
          </div>

          <!-- 总结 -->
          <div v-if="currentReport.summary" class="wr-block">
            <h3 class="wr-block-title">
              <Icon name="file-text" :size="16" />
              <span>本周总结</span>
            </h3>
            <div class="wr-summary" v-html="renderedSummary"></div>
          </div>

          <!-- 成就 -->
          <div v-if="currentReport.achievements && currentReport.achievements.length" class="wr-block">
            <h3 class="wr-block-title">
              <Icon name="trophy" :size="16" />
              <span>本周成就</span>
            </h3>
            <ul class="wr-list">
              <li v-for="(item, idx) in currentReport.achievements" :key="`a-${idx}`" class="wr-list-item">
                <Icon name="award" :size="16" class="wr-list-icon" />
                <span>{{ item }}</span>
              </li>
            </ul>
          </div>

          <!-- 建议 -->
          <div v-if="currentReport.suggestions && currentReport.suggestions.length" class="wr-block">
            <h3 class="wr-block-title">
              <Icon name="lightbulb" :size="16" />
              <span>下周建议</span>
            </h3>
            <ul class="wr-list">
              <li v-for="(item, idx) in currentReport.suggestions" :key="`s-${idx}`" class="wr-list-item">
                <Icon name="arrow-right" :size="16" class="wr-list-icon" />
                <span>{{ item }}</span>
              </li>
            </ul>
          </div>

          <!-- B. AI 深度洞见 -->
          <div class="wr-block">
            <h3 class="wr-block-title">
              <Icon name="sparkles" :size="16" />
              <span>AI 深度洞见</span>
            </h3>
            <template v-if="metricsLoading && aiInsights.length === 0">
              <div class="wr-insight-skeleton">
                <div class="wr-insight-sk-card" />
                <div class="wr-insight-sk-card" />
                <div class="wr-insight-sk-card" />
              </div>
            </template>
            <template v-else>
              <div class="wr-insight-grid">
                <div
                  v-for="(it, idx) in aiInsights"
                  :key="idx"
                  class="wr-insight-card"
                  :class="it.tone"
                >
                  <div class="wr-insight-icon">
                    <Icon :name="it.icon" :size="18" />
                  </div>
                  <div class="wr-insight-body">
                    <div class="wr-insight-title">{{ it.title }}</div>
                    <div class="wr-insight-content">{{ it.content }}</div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 空态 -->
        <div v-else class="wr-empty">
          <Icon name="file-question" :size="40" />
          <p>本周暂无周报，点击右上角「生成本周周报」开始生成。</p>
        </div>
      </section>

      <!-- 历史周报 -->
      <section class="wr-section">
        <h2 class="wr-section-title">
          <Icon name="history" :size="18" />
          <span>历史周报</span>
          <span v-if="historyReports.length" class="wr-count-badge">{{ historyReports.length }}</span>
        </h2>

        <div v-if="historyReports.length === 0" class="wr-empty wr-empty-small">
          <Icon name="inbox" :size="32" />
          <p>暂无历史周报</p>
        </div>

        <div v-else class="wr-history-list">
          <div
            v-for="(report, idx) in historyReports"
            :key="report.id ?? idx"
            class="wr-history-item"
          >
            <button
              type="button"
              class="wr-history-head"
              :aria-expanded="expandedId === report.id"
              @click="toggleExpand(report.id)"
            >
              <div class="wr-history-period">
                <Icon name="calendar" :size="16" />
                <span>{{ report.weekStart }} ~ {{ report.weekEnd }}</span>
              </div>
              <div class="wr-history-meta">
                <span class="wr-meta-pill">
                  <Icon name="clock" :size="12" />
                  <span class="tabular-nums">{{ report.studyMinutes }} 分钟</span>
                </span>
                <span class="wr-meta-pill">
                  <Icon name="check-circle" :size="12" />
                  <span class="tabular-nums">{{ report.checkinDays }} 天</span>
                </span>
                <Icon
                  name="chevron-down"
                  :size="16"
                  :class="['wr-chevron', { 'wr-chevron-open': expandedId === report.id }]"
                />
              </div>
            </button>

            <!-- E. 历史周报 miniStats 横条 -->
            <div class="wr-history-mini-stats">
              <span class="wr-mini-stat">
                <Icon name="clock" :size="11" />
                <span class="tabular-nums">学习 {{ report.studyMinutes ?? 0 }} 分钟</span>
              </span>
              <span class="wr-mini-stat-sep" />
              <span class="wr-mini-stat">
                <Icon name="calendar-check" :size="11" />
                <span class="tabular-nums">打卡 {{ report.checkinDays ?? 0 }} 天</span>
              </span>
              <span class="wr-mini-stat-sep" />
              <span class="wr-mini-stat">
                <Icon name="layers" :size="11" />
                <span class="tabular-nums">闪卡 {{ report.flashcardReviewed ?? 0 }}</span>
              </span>
            </div>

            <transition name="wr-collapse">
              <div v-if="expandedId === report.id" class="wr-history-body">
                <div v-if="report.summary" class="wr-block">
                  <h3 class="wr-block-title">
                    <Icon name="file-text" :size="14" />
                    <span>总结</span>
                  </h3>
                  <div class="wr-summary" v-html="renderMarkdown(report.summary)"></div>
                </div>
                <div v-if="report.achievements && report.achievements.length" class="wr-block">
                  <h3 class="wr-block-title">
                    <Icon name="trophy" :size="14" />
                    <span>成就</span>
                  </h3>
                  <ul class="wr-list">
                    <li v-for="(item, i) in report.achievements" :key="`h-a-${i}`" class="wr-list-item">
                      <Icon name="award" :size="14" class="wr-list-icon" />
                      <span>{{ item }}</span>
                    </li>
                  </ul>
                </div>
                <div v-if="report.suggestions && report.suggestions.length" class="wr-block">
                  <h3 class="wr-block-title">
                    <Icon name="lightbulb" :size="14" />
                    <span>建议</span>
                  </h3>
                  <ul class="wr-list">
                    <li v-for="(item, i) in report.suggestions" :key="`h-s-${i}`" class="wr-list-item">
                      <Icon name="arrow-right" :size="14" class="wr-list-icon" />
                      <span>{{ item }}</span>
                    </li>
                  </ul>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </section>
    </template>

    <!-- 分享 fallback textbox -->
    <textarea
      ref="shareTaRef"
      class="wr-share-fallback-ta"
      readonly
      aria-hidden="true"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import { notify, getApiError } from '@/utils/toast';
import { renderMarkdown } from '@/utils/markdown';
import type { WeeklyReportVO } from '@/api/types';
import {
  aggregateWeeklyMetrics,
  generateAiInsights,
  generateShareableText,
  type AggregatedMetrics,
  type AiInsight,
} from '@/composables/useWeeklyReportEnhancer';

const router = useRouter();

const loading = ref(false);
const generating = ref(false);
const error = ref('');
const currentReport = ref<WeeklyReportVO | null>(null);
const historyReports = ref<WeeklyReportVO[]>([]);
const expandedId = ref<number | null>(null);

const metricsLoading = ref(false);
const metrics = ref<AggregatedMetrics | null>(null);

const shareTaRef = ref<HTMLTextAreaElement | null>(null);

const renderedSummary = computed(() =>
  currentReport.value?.summary ? renderMarkdown(currentReport.value.summary) : '',
);

const MODE_COLORS: Record<string, string> = {
  POMODORO: '#EF4444',
  FLOW: '#06B6D4',
  DEEP: '#8B5CF6',
  SPACED: '#10B981',
  BUDDY: '#F59E0B',
};
const MODE_LABELS: Record<string, string> = {
  POMODORO: '番茄',
  FLOW: '流',
  DEEP: '深度',
  SPACED: '间隔',
  BUDDY: '伙伴',
};

const modeSegments = computed(() => {
  const breakdown = metrics.value?.focusStats?.modeBreakdown ?? {};
  const total = Object.values(breakdown).reduce((s, n) => s + (n || 0), 0);
  const modes: Array<keyof typeof MODE_COLORS> = ['POMODORO', 'FLOW', 'DEEP', 'SPACED', 'BUDDY'];
  return modes
    .map((m) => ({
      mode: m,
      label: MODE_LABELS[m] ?? m,
      color: MODE_COLORS[m],
      minutes: breakdown[m] ?? 0,
      percent: total > 0 ? ((breakdown[m] ?? 0) / total) * 100 : 0,
    }))
    .filter((s) => s.percent > 0.1 || s.minutes > 0);
});

const weekHeatMini = computed<number[]>(() => {
  const days = metrics.value?.daily ?? [];
  if (!days || days.length === 0) return [0, 0, 0, 0, 0, 0, 0];
  const today = new Date();
  const result: number[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(today.getDate() - i);
    const dateStr = d.toISOString().slice(0, 10);
    const match = days.find((x) => x.date === dateStr);
    result.push(match?.count ?? 0);
  }
  return result;
});

function miniHeatColor(minutes: number): string {
  const clamped = Math.max(0, Math.min(120, minutes));
  const t = clamped / 120;
  if (t <= 0) return 'var(--kb-muted)';
  const alpha = 0.2 + t * 0.8;
  return `color-mix(in srgb, var(--kb-primary) ${alpha * 100}%, transparent)`;
}

const aiInsights = computed<AiInsight[]>(() => {
  return generateAiInsights(currentReport.value, metrics.value ?? {
    focusStats: null,
    daily: [],
    mastery: null,
    mistakes: null,
    user: null,
  });
});

function toggleExpand(id?: number): void {
  if (id == null) return;
  expandedId.value = expandedId.value === id ? null : id;
}

async function loadMetrics(): Promise<void> {
  metricsLoading.value = true;
  try {
    metrics.value = await aggregateWeeklyMetrics();
  } catch (e: unknown) {
    notify('增强数据加载失败：' + getApiError(e, '网络异常'), 'warning');
    metrics.value = null;
  } finally {
    metricsLoading.value = false;
  }
}

async function loadAll(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const [current, history] = await Promise.all([
      learningApi.getWeeklyReport().catch(() => null),
      learningApi.listWeeklyReports().catch(() => [] as WeeklyReportVO[]),
    ]);
    currentReport.value = current;
    historyReports.value = [...history].sort((a, b) =>
      (b.weekStart || '').localeCompare(a.weekStart || ''),
    );
    if (historyReports.value.length > 0 && historyReports.value[0].id != null) {
      expandedId.value = historyReports.value[0].id!;
    }
  } catch (e: unknown) {
    error.value = '周报加载失败：' + getApiError(e);
    notify('周报加载失败', 'error');
  } finally {
    loading.value = false;
  }
}

async function handleGenerate(): Promise<void> {
  if (generating.value) return;
  generating.value = true;
  try {
    const report = await learningApi.generateWeeklyReport();
    currentReport.value = report;
    try {
      const history = await learningApi.listWeeklyReports();
      historyReports.value = [...history].sort((a, b) =>
        (b.weekStart || '').localeCompare(a.weekStart || ''),
      );
    } catch {
      // ignore
    }
    // 生成完刷新增强数据
    void loadMetrics();
    notify('本周周报已生成', 'success');
  } catch (e: unknown) {
    notify('生成周报失败：' + getApiError(e), 'error');
  } finally {
    generating.value = false;
  }
}

async function handleShare(): Promise<void> {
  try {
    const text = generateShareableText(currentReport.value, metrics.value ?? {
      focusStats: null,
      daily: [],
      mastery: null,
      mistakes: null,
      user: null,
    });
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else if (shareTaRef.value) {
        const ta = shareTaRef.value;
        ta.value = text;
        ta.select();
        document.execCommand('copy');
        ta.value = '';
      } else {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.left = '-9999px';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      }
      notify('周报分享文案已复制到剪贴板，快去分享吧！', 'success');
    } catch (e: unknown) {
      notify(getApiError(e, '复制失败，请截图保存周报分享'), 'warning');
    }
  } catch (e: unknown) {
    notify('生成分享文案失败：' + getApiError(e), 'warning');
  }
}

function escapeXml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function triggerDownload(href: string, filename: string): void {
  const a = document.createElement('a');
  a.href = href;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

async function handleDownloadPdf(): Promise<void> {
  try {
    await nextTick();
    const r = currentReport.value;
    const m = metrics.value;
    if (!r && !m) {
      notify('暂无数据可导出', 'warning');
      return;
    }
    const minutes = r?.studyMinutes ?? m?.focusStats?.weekMinutes ?? 0;
    const days = r?.checkinDays ?? m?.user?.streakDays ?? 0;
    const cards = r?.flashcardReviewed ?? m?.mastery?.flashcardReviewed ?? 0;
    const title = 'KnowFlow 学习周报';
    const subtitle = `${r?.weekStart ?? '本周'} ~ ${r?.weekEnd ?? ''}`;
    const insights = aiInsights.value;

    const w = 720;
    const h = 980;
    const insightsHtml = insights
      .map((it, i) => {
        const y = 520 + i * 110;
        const toneColor = it.tone === 'positive' ? '#10B981' : it.tone === 'warning' ? '#F59E0B' : '#3B6FE0';
        const toneBg = it.tone === 'positive' ? 'rgba(16,185,129,0.08)' : it.tone === 'warning' ? 'rgba(245,158,11,0.08)' : 'rgba(59,111,224,0.08)';
        const contentLines = splitText(it.content, 32);
        const contentText = contentLines
          .slice(0, 2)
          .map((line, li) => `<text x="92" y="${y + 46 + li * 20}" font-size="13" fill="#4B5563" font-family="Noto Sans SC, -apple-system, sans-serif">${escapeXml(line)}</text>`)
          .join('\n');
        return `<rect x="40" y="${y}" width="${w - 80}" height="96" rx="12" fill="${toneBg}" stroke="${toneColor}22" stroke-width="1" />
<circle cx="66" cy="${y + 30}" r="18" fill="${toneColor}18" />
<text x="66" y="${y + 36}" text-anchor="middle" font-size="18" fill="${toneColor}" font-family="Noto Sans SC, sans-serif">★</text>
<text x="92" y="${y + 28}" font-size="14" font-weight="600" fill="#1A1D23" font-family="Noto Sans SC, -apple-system, sans-serif">${escapeXml(it.title)}</text>
${contentText}`;
      })
      .join('\n');

    const achievements = r?.achievements?.slice(0, 3) ?? [];
    const achHtml = achievements
      .map((a, i) => {
        const y = 380 + i * 32;
        return `<circle cx="58" cy="${y}" r="8" fill="rgba(245,158,11,0.18)" />
<text x="58" y="${y + 4}" text-anchor="middle" font-size="11" fill="#F59E0B">★</text>
<text x="78" y="${y + 4}" font-size="13" fill="#374151" font-family="Noto Sans SC, -apple-system, sans-serif">${escapeXml(a)}</text>`;
      })
      .join('\n');

    const svgMarkup = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
  <defs>
    <linearGradient id="bgGrad" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#FFFFFF"/>
      <stop offset="100%" stop-color="#F0F4FF"/>
    </linearGradient>
    <linearGradient id="heroGrad" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#3B6FE0"/>
      <stop offset="100%" stop-color="#6F9AF2"/>
    </linearGradient>
  </defs>

  <rect width="${w}" height="${h}" rx="0" fill="url(#bgGrad)"/>

  <rect x="0" y="0" width="${w}" height="170" rx="0" fill="url(#heroGrad)"/>
  <circle cx="${w - 60}" cy="40" r="60" fill="rgba(255,255,255,0.08)"/>
  <circle cx="${w - 160}" cy="110" r="30" fill="rgba(255,255,255,0.06)"/>

  <text x="40" y="58" font-size="14" font-weight="600" fill="rgba(255,255,255,0.85)" font-family="Noto Sans SC, -apple-system, sans-serif">KnowFlow · 沉浸工作台</text>
  <text x="40" y="98" font-size="32" font-weight="700" fill="#FFFFFF" font-family="Noto Serif SC, Georgia, serif">${escapeXml(title)}</text>
  <text x="40" y="128" font-size="14" fill="rgba(255,255,255,0.8)" font-family="Noto Sans SC, sans-serif">${escapeXml(subtitle)}</text>

  <rect x="40" y="190" width="${w - 80}" height="140" rx="16" fill="#FFFFFF" stroke="rgba(59,111,224,0.1)" stroke-width="1"/>
  <rect x="76" y="220" width="${(w - 80 - 80) / 3 - 12}" height="80" rx="12" fill="rgba(59,111,224,0.06)"/>
  <text x="${76 + ((w - 80 - 80) / 3 - 12) / 2}" y="252" text-anchor="middle" font-size="12" fill="#6B7280" font-family="Noto Sans SC, sans-serif">学习分钟</text>
  <text x="${76 + ((w - 80 - 80) / 3 - 12) / 2}" y="282" text-anchor="middle" font-size="24" font-weight="700" fill="#1A1D23" font-family="Noto Sans SC, sans-serif">${minutes}</text>

  <rect x="${76 + (w - 80 - 80) / 3}" y="220" width="${(w - 80 - 80) / 3 - 12}" height="80" rx="12" fill="rgba(16,185,129,0.06)"/>
  <text x="${76 + (w - 80 - 80) / 3 + ((w - 80 - 80) / 3 - 12) / 2}" y="252" text-anchor="middle" font-size="12" fill="#6B7280" font-family="Noto Sans SC, sans-serif">打卡天数</text>
  <text x="${76 + (w - 80 - 80) / 3 + ((w - 80 - 80) / 3 - 12) / 2}" y="282" text-anchor="middle" font-size="24" font-weight="700" fill="#1A1D23" font-family="Noto Sans SC, sans-serif">${days}</text>

  <rect x="${76 + 2 * ((w - 80 - 80) / 3)}" y="220" width="${(w - 80 - 80) / 3 - 12}" height="80" rx="12" fill="rgba(245,158,11,0.06)"/>
  <text x="${76 + 2 * ((w - 80 - 80) / 3) + ((w - 80 - 80) / 3 - 12) / 2}" y="252" text-anchor="middle" font-size="12" fill="#6B7280" font-family="Noto Sans SC, sans-serif">复习闪卡</text>
  <text x="${76 + 2 * ((w - 80 - 80) / 3) + ((w - 80 - 80) / 3 - 12) / 2}" y="282" text-anchor="middle" font-size="24" font-weight="700" fill="#1A1D23" font-family="Noto Sans SC, sans-serif">${cards}</text>

  ${achievements.length ? `<text x="40" y="360" font-size="16" font-weight="600" fill="#1A1D23" font-family="Noto Sans SC, sans-serif">🏆 本周成就</text>\n${achHtml}` : ''}

  <text x="40" y="500" font-size="16" font-weight="600" fill="#1A1D23" font-family="Noto Sans SC, sans-serif">✨ AI 深度洞见</text>
  ${insightsHtml}

  <text x="${w / 2}" y="${h - 32}" text-anchor="middle" font-size="12" font-weight="600" fill="#9AA1AC" letter-spacing="2" font-family="Noto Sans SC, sans-serif">knowflow.cn</text>
</svg>`;

    const encoded = encodeURIComponent(svgMarkup)
      .replace(/'/g, '%27')
      .replace(/"/g, '%22');
    const dataUrl = `data:image/svg+xml;charset=utf-8,${encoded}`;

    const filename = r?.weekStart
      ? `KnowFlow周报_${r.weekStart}_${r.weekEnd ?? ''}.svg`
      : 'KnowFlow周报.svg';
    triggerDownload(dataUrl, filename);
    notify('周报已导出，如需 PDF 请用浏览器打开后另存为', 'success');
  } catch (e: unknown) {
    notify(getApiError(e, '导出失败，请截图保存周报'), 'warning');
  }
}

function splitText(text: string, maxLen: number): string[] {
  const result: string[] = [];
  let remaining = text;
  while (remaining.length > maxLen) {
    result.push(remaining.slice(0, maxLen));
    remaining = remaining.slice(maxLen);
  }
  if (remaining) result.push(remaining);
  return result;
}

watch(
  currentReport,
  () => {
    if (currentReport.value && !metrics.value && !metricsLoading.value) {
      void loadMetrics();
    }
  },
);

onMounted(() => {
  void loadAll();
  void loadMetrics();
});
</script>

<style scoped>
.weekly-report-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 4px 0 32px;
  animation: wr-fade 0.4s ease-out;
}

@keyframes wr-fade {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: wr-fade 0.4s ease-out;
}

/* ========== 页头 ========== */
.wr-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.wr-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.wr-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.wr-back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.wr-back-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

.wr-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0;
}

/* D. 热力图小圆徽标 */
.wr-heat-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 18px;
  padding: 2px 4px;
  border-radius: 5px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.wr-heat-dot {
  width: 6px;
  height: 10px;
  border-radius: 2px;
  flex: 1;
  min-width: 6px;
}

.wr-generate-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 18px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  font-weight: 600;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.wr-generate-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.wr-generate-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.wr-secondary-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 600;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.15s ease;
}
.wr-secondary-btn:hover:not(:disabled) {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.wr-secondary-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* ========== 区块 ========== */
.wr-section {
  margin-bottom: 28px;
}

.wr-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 14px;
}

.wr-section-title svg {
  color: var(--kb-primary);
}

.wr-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
  font-size: 12px;
  font-weight: 600;
}

/* ========== 卡片 ========== */
.wr-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  padding: 20px 22px;
}

.wr-current-card {
  box-shadow: 0 4px 16px rgba(59, 111, 224, 0.04);
}

.wr-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.wr-period {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.wr-period svg {
  color: var(--kb-primary);
}

/* ========== 统计 ========== */
.wr-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 22px;
}

@media (max-width: 640px) {
  .wr-stats {
    grid-template-columns: 1fr;
  }
}

.wr-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 12px;
  border-radius: 12px;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
}

.wr-stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.wr-stat-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1;
}

.wr-stat-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* ========== 内容块 ========== */
.wr-block {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--kb-border);
}

.wr-block:first-of-type {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.wr-block-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 10px;
}

.wr-block-title svg {
  color: var(--kb-primary);
}

.wr-week-minutes {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
}

/* A. 模式分布 stacked bar */
.wr-stacked-skeleton {
  height: 52px;
  border-radius: 10px;
  background: linear-gradient(
    90deg,
    var(--kb-muted) 0%,
    color-mix(in srgb, var(--kb-muted) 60%, transparent) 50%,
    var(--kb-muted) 100%
  );
  background-size: 200% 100%;
  animation: wr-pulse 1.4s ease-in-out infinite;
}

.wr-stacked-bar {
  display: flex;
  width: 100%;
  height: 18px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--kb-muted);
  margin-bottom: 12px;
}
.wr-stacked-seg {
  height: 100%;
  transition: width 0.4s ease;
}
.wr-stacked-seg:first-child { border-radius: 999px 0 0 999px; }
.wr-stacked-seg:last-child { border-radius: 0 999px 999px 0; }

.wr-stacked-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
}
.wr-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-foreground);
}
.wr-legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}
.wr-legend-label {
  font-weight: 500;
}
.wr-legend-pct {
  color: var(--kb-muted-foreground);
  font-weight: 500;
}

.wr-empty-mini {
  padding: 16px 0;
  text-align: center;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* ========== 总结 / 列表 ========== */
.wr-summary {
  font-size: 14px;
  line-height: 1.7;
  color: var(--kb-card-foreground, var(--kb-foreground));
}

.wr-summary :deep(p) {
  margin: 0 0 8px;
}

.wr-summary :deep(p:last-child) {
  margin-bottom: 0;
}

.wr-summary :deep(ul),
.wr-summary :deep(ol) {
  margin: 6px 0 8px;
  padding-left: 22px;
}

.wr-summary :deep(li) {
  margin-bottom: 4px;
}

.wr-summary :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--kb-muted);
  font-size: 13px;
}

.wr-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wr-list-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-foreground);
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--kb-background);
}

.wr-list-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

/* B. AI 洞见 */
.wr-insight-skeleton {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.wr-insight-sk-card {
  height: 108px;
  border-radius: 12px;
  background: linear-gradient(
    90deg,
    var(--kb-muted) 0%,
    color-mix(in srgb, var(--kb-muted) 60%, transparent) 50%,
    var(--kb-muted) 100%
  );
  background-size: 200% 100%;
  animation: wr-pulse 1.4s ease-in-out infinite;
}
@media (max-width: 640px) {
  .wr-insight-skeleton {
    grid-template-columns: 1fr;
  }
}

.wr-insight-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
@media (max-width: 640px) {
  .wr-insight-grid {
    grid-template-columns: 1fr;
  }
}
.wr-insight-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-radius: 12px;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
}
.wr-insight-card.positive {
  border-color: rgba(16, 185, 129, 0.22);
  background: linear-gradient(180deg, rgba(16, 185, 129, 0.06), rgba(16, 185, 129, 0.02));
}
.wr-insight-card.warning {
  border-color: rgba(245, 158, 11, 0.22);
  background: linear-gradient(180deg, rgba(245, 158, 11, 0.06), rgba(245, 158, 11, 0.02));
}
.wr-insight-card.info {
  border-color: rgba(59, 111, 224, 0.22);
  background: linear-gradient(180deg, rgba(59, 111, 224, 0.06), rgba(59, 111, 224, 0.02));
}
.wr-insight-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
}
.wr-insight-card.positive .wr-insight-icon {
  color: var(--kb-accent);
  background: rgba(16, 185, 129, 0.14);
}
.wr-insight-card.warning .wr-insight-icon {
  color: var(--kb-warning);
  background: rgba(245, 158, 11, 0.14);
}
.wr-insight-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.wr-insight-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.wr-insight-content {
  font-size: 12px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
}

/* ========== 空态 / 错误 ========== */
.wr-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px 24px;
  color: var(--kb-muted-foreground);
  background: var(--kb-card);
  border: 1px dashed var(--kb-border);
  border-radius: 14px;
  text-align: center;
}

.wr-empty svg {
  opacity: 0.4;
}

.wr-empty p {
  font-size: 14px;
  margin: 0;
}

.wr-empty-small {
  padding: 32px 16px;
}

.wr-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 24px;
  color: var(--kb-muted-foreground);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  text-align: center;
}

.wr-error svg {
  color: var(--kb-destructive);
}

.wr-retry-btn {
  height: 34px;
  padding: 0 16px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 600;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.wr-retry-btn:hover {
  opacity: 0.9;
}

/* ========== 骨架屏 ========== */
.wr-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.wr-skeleton-card {
  height: 220px;
  border-radius: 14px;
  background: var(--kb-muted);
  animation: wr-pulse 1.4s ease-in-out infinite;
}

@keyframes wr-pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 0.3; }
}

/* ========== 历史周报 ========== */
.wr-history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wr-history-item {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  overflow: hidden;
  transition: border-color 0.15s ease;
}

.wr-history-item:hover {
  border-color: rgba(59, 111, 224, 0.3);
}

.wr-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 14px 18px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease;
}

.wr-history-head:hover {
  background: var(--kb-muted);
}

.wr-history-period {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.wr-history-period svg {
  color: var(--kb-primary);
}

.wr-history-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.wr-meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

.wr-chevron {
  color: var(--kb-muted-foreground);
  transition: transform 0.2s ease;
}

.wr-chevron-open {
  transform: rotate(180deg);
}

/* E. 历史 miniStats */
.wr-history-mini-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px 12px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  flex-wrap: wrap;
}
.wr-mini-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--kb-background);
  font-weight: 500;
}
.wr-mini-stat svg {
  opacity: 0.8;
}
.wr-mini-stat-sep {
  width: 1px;
  height: 12px;
  background: var(--kb-border);
}

.wr-history-body {
  padding: 4px 18px 18px;
  border-top: 1px solid var(--kb-border);
}

.wr-history-body .wr-block:first-child {
  margin-top: 14px;
}

/* 折叠过渡 */
.wr-collapse-enter-active,
.wr-collapse-leave-active {
  transition: opacity 0.2s ease, max-height 0.25s ease;
  overflow: hidden;
  max-height: 1200px;
}

.wr-collapse-enter-from,
.wr-collapse-leave-to {
  opacity: 0;
  max-height: 0;
}

/* 分享 textarea fallback */
.wr-share-fallback-ta {
  position: fixed;
  left: -99999px;
  top: -99999px;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}
</style>
