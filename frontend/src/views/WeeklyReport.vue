<template>
  <!--
    学习周报页（P4-5）：
    展示当前周报（含 summary / achievements / suggestions / 统计）与历史周报列表。
    支持手动生成本周周报，历史周报可展开查看详情。
  -->
  <div class="weekly-report-page animate-fade-in">
    <!-- 页头 -->
    <div class="wr-header">
      <div class="wr-header-left">
        <button type="button" class="wr-back-btn" title="返回" @click="router.back()">
          <Icon name="arrow-left" :size="18" />
        </button>
        <h1 class="wr-title">学习周报</h1>
      </div>
      <button
        type="button"
        class="wr-generate-btn"
        :disabled="generating"
        @click="handleGenerate"
      >
        <Icon name="sparkles" :size="16" />
        <span>{{ generating ? '生成中…' : '生成本周周报' }}</span>
      </button>
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
  </div>
</template>

<script setup lang="ts">
// 学习周报页：加载当前周报与历史周报，支持手动生成本周周报。
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import { notify, getApiError } from '@/utils/toast';
import { renderMarkdown } from '@/utils/markdown';
import type { WeeklyReportVO } from '@/api/types';

const router = useRouter();

const loading = ref(false);
const generating = ref(false);
const error = ref('');
const currentReport = ref<WeeklyReportVO | null>(null);
const historyReports = ref<WeeklyReportVO[]>([]);
const expandedId = ref<number | null>(null);

const renderedSummary = computed(() =>
  currentReport.value?.summary ? renderMarkdown(currentReport.value.summary) : '',
);

function toggleExpand(id?: number): void {
  if (id == null) return;
  expandedId.value = expandedId.value === id ? null : id;
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
    // 历史周报按 weekStart 倒序
    historyReports.value = [...history].sort((a, b) =>
      (b.weekStart || '').localeCompare(a.weekStart || ''),
    );
    // 默认展开第一条历史
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
    // 刷新历史列表
    try {
      const history = await learningApi.listWeeklyReports();
      historyReports.value = [...history].sort((a, b) =>
        (b.weekStart || '').localeCompare(a.weekStart || ''),
      );
    } catch {
      // 历史刷新失败不影响主流程
    }
    notify('本周周报已生成', 'success');
  } catch (e: unknown) {
    notify('生成周报失败：' + getApiError(e), 'error');
  } finally {
    generating.value = false;
  }
}

onMounted(() => {
  void loadAll();
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
</style>
