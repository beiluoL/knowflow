<template>
  <div class="learning-report animate-fade-in">
    <!-- 页头 -->
    <PageHeader
      title="学习报告"
      :crumbs="[{ label: '学习首页', to: '/learning/center' }, { label: '学习报告' }]"
      title-color="text-foreground"
    >
      <template #actions>
        <div class="flex items-center gap-1 p-1 rounded-lg border" style="border-color: var(--kb-border); background: var(--kb-card);">
          <button
            v-for="opt in periodOptions"
            :key="opt.value"
            type="button"
            class="px-3 py-1.5 text-sm font-medium rounded-md whitespace-nowrap transition duration-150 hover:opacity-90 hover:bg-[var(--kb-muted)] active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
            :aria-pressed="period === opt.value"
            :style="period === opt.value
              ? { background: 'var(--kb-primary)', color: 'var(--kb-primary-foreground)' }
              : { color: 'var(--kb-muted-foreground)' }"
            @click="switchPeriod(opt.value)"
          >{{ opt.label }}</button>
        </div>
      </template>
    </PageHeader>

    <EvidenceReportPanel v-if="evidenceReportEnabled" />
    <StateView :status="reportStatus" :error-title="reportError?.title" :error-scope="reportError?.scope" :error-hint="reportError?.hint" :show-retry="reportError?.retryable ?? true" empty-title="还没有学习报告" empty-description="先阅读、复习或练习，真实学习记录会形成你的报告。" @retry="loadData(true)">
    <template #empty-action><router-link class="kb-btn kb-btn-primary" to="/learning/center">返回学习首页</router-link></template>
    <template #skeleton><div class="mt-6 space-y-4">
      <SkeletonList :rows="1" type="card" :cols="3" />
      <SkeletonList :rows="3" type="line" />
    </div></template>

    <!-- 内容 -->
    <template #default><div v-if="report">
      <!-- 周期信息 -->
      <p class="mt-4 text-sm" style="color: var(--kb-muted-foreground);">
        统计周期：<span class="tabular-nums">{{ report.startDate }}</span> 至 <span class="tabular-nums">{{ report.endDate }}</span>
      </p>

      <div class="report-context"><details class="report-method"><summary>统计口径：周期、累计与快照</summary><p>签到、答题、代码提交按所选周期统计；阅读时长与阅读文档为累计记录，已掌握卡片和错题为当前快照。闪卡复习计“最后复习落在期内的卡片数”，不是复习日志次数。</p><p>日活固定最近 30 天，周趋势固定最近 8 周；阅读累计秒数按文档最后阅读日期归入日/周，不能当作精确逐日投入时长。历史代码通过数来自旧提交记录，不代表当前可信判题结果。</p></details><RouterLink class="report-mastery-link" to="/learning/mastery">查看知识点掌握度与证据</RouterLink></div>
      <!-- 概览卡片 -->
      <section class="mt-4 grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <div
          v-for="card in overviewCards"
          :key="card.label"
          class="border rounded-[10px] p-5 bg-card border-border report-card"
        >
          <div class="flex items-center justify-between mb-3">
            <div class="w-9 h-9 rounded-lg flex items-center justify-center" :style="{ background: `color-mix(in srgb, ${card.color} 10%, var(--kb-card))` }">
              <Icon :name="card.icon" :size="18" :style="{ color: card.color }" />
            </div>
          </div>
          <p class="text-[28px] font-bold tabular-nums leading-none" :style="{ color: card.color }">{{ card.value }}</p>
          <p class="mt-2 text-[length:var(--kb-fs-body-sm)] truncate" style="color: var(--kb-muted-foreground);">{{ card.label }}</p>
          <p v-if="card.sub" class="mt-1 text-[length:var(--kb-fs-caption)] truncate" style="color: var(--kb-muted-foreground);">{{ card.sub }}</p>
        </div>
      </section>

      <!-- 活跃度柱状图 + 知识库掌握度 -->
      <section class="mt-6 grid grid-cols-1 lg:grid-cols-3 gap-4">
        <!-- 每日学习活跃度 -->
        <div class="lg:col-span-2 border rounded-[10px] p-5 bg-card border-border">
          <div class="flex items-center justify-between flex-wrap gap-2 mb-4">
            <h2 class="report-h2 min-w-0">阅读时长与学习活动</h2>
            <span class="text-sm tabular-nums" style="color: var(--kb-muted-foreground);">
              最近 30 天 · 共 <span style="color: var(--kb-primary);">{{ dailyTotalMinutes }}</span> 分钟
            </span>
          </div>
          <EmptyState
            v-if="!report.dailyActivity.some(d => d.minutes > 0 || (d.count ?? 0) > 0)"
            icon="bar-chart-2"
            title="暂无学习记录"
            variant="info"
          >
            <p>开始阅读文档或复习闪卡后会显示每日学习活跃度</p>
          </EmptyState>
          <div v-else class="daily-chart" role="img" :aria-label="`近30天阅读记录合计${dailyTotalMinutes}分钟，具体数据可在下方展开`">
            <div
              v-for="(d, idx) in report.dailyActivity"
              :key="idx"
              class="daily-bar-wrap"
            >
              <div class="daily-bar-track">
                <div
                  class="daily-bar"
                  :style="{ height: `${dailyBarHeight(d.minutes)}%` }"
                  :title="`${d.date} · ${d.minutes} 分钟 / ${d.count} 次活动`"
                ></div>
              </div>
              <small v-if="(d.count ?? 0) > 0" class="daily-count">{{ d.count }}次</small>
              <span v-if="idx % 5 === 0" class="daily-bar-label">{{ d.date.slice(5) }}</span>
            </div>
          </div>
        </div>

        <!-- 知识库掌握度 -->
        <div class="border rounded-[10px] p-5 bg-card border-border">
          <div class="flex items-center justify-between flex-wrap gap-2 mb-4">
            <h2 class="report-h2 min-w-0">卡片与错题掌握快照</h2>
            <span class="text-sm shrink-0" style="color: var(--kb-muted-foreground);">Top 5</span>
          </div>
          <EmptyState
            v-if="(report.categoryMastery ?? []).length === 0"
            icon="award"
            title="暂无卡片与错题快照"
            variant="info"
          >
            <p>按已有闪卡复习与错题标记统计，不等同于知识点掌握引擎评分。</p>
          </EmptyState>
          <ul v-else class="space-y-4">
            <li v-for="(item, idx) in report.categoryMastery ?? []" :key="idx">
              <div class="flex items-center justify-between gap-2 mb-2">
                <span class="text-sm font-medium truncate min-w-0 flex-1" style="color: var(--kb-foreground);">{{ item.categoryName }}</span>
                <span class="text-sm font-semibold tabular-nums shrink-0" style="color: var(--kb-primary);">{{ item.percent }}%</span>
              </div>
              <div class="w-full h-2 rounded-full overflow-hidden" style="background: var(--kb-muted);">
                <div
                  class="h-full rounded-full transition-[width] duration-500"
                  :style="{ width: `${item.percent}%`, background: 'linear-gradient(90deg, var(--kb-primary), var(--kb-accent))' }"
                ></div>
              </div>
              <p class="mt-1 text-[length:var(--kb-fs-caption)] tabular-nums" style="color: var(--kb-muted-foreground);">
                已掌握 {{ item.mastered }} / {{ item.total }}
              </p>
            </li>
          </ul>
        </div>
      </section>

      <!-- 周趋势 -->
      <section class="mt-6 border rounded-[10px] p-5 bg-card border-border">
        <div class="flex items-center justify-between flex-wrap gap-2 mb-4">
          <h2 class="report-h2 min-w-0">最近 8 周趋势</h2>
          <div class="flex items-center flex-wrap gap-x-4 gap-y-2 text-sm">
            <span class="flex items-center gap-2">
              <span class="w-3 h-3 rounded-sm shrink-0" style="background: var(--kb-primary);"></span>
              <span style="color: var(--kb-muted-foreground);">阅读记录时长(分钟)</span>
            </span>
            <span class="flex items-center gap-2">
              <span class="w-3 h-3 rounded-sm shrink-0" style="background: var(--kb-accent);"></span>
              <span style="color: var(--kb-muted-foreground);">签到天数</span>
            </span>
          </div>
        </div>
        <EmptyState
          v-if="!report.weeklyTrend.some(w => w.studyMinutes > 0 || w.checkinDays > 0)"
          icon="trending-up"
          title="暂无周趋势数据"
          variant="info"
        >
          <p>坚持学习一段时间后会显示周趋势</p>
        </EmptyState>
        <div v-else class="weekly-chart">
          <div
            v-for="(w, idx) in report.weeklyTrend"
            :key="idx"
            class="weekly-col"
          >
            <div class="weekly-bars">
              <div
                class="weekly-bar weekly-bar-minutes"
                :style="{ height: `${weeklyBarHeight(w.studyMinutes, weeklyMaxMinutes)}%` }"
                :title="`${w.weekStart} · 学习 ${w.studyMinutes} 分钟`"
              ></div>
              <div
                class="weekly-bar weekly-bar-checkins"
                :style="{ height: `${weeklyBarHeight(w.checkinDays, weeklyMaxCheckins)}%` }"
                :title="`${w.weekStart} · 签到 ${w.checkinDays} 天`"
              ></div>
            </div>
            <span class="weekly-label">{{ w.weekStart.slice(5) }}</span>
          </div>
        </div>
      </section>

      <details class="report-data"><summary>查看图表原始数据与单位</summary><p>日活单位：分钟 / 次；周趋势单位：分钟 / 天。两种周指标分别缩放，不能直接比较柱高。</p><div class="report-data-scroll"><table><caption>最近30天阅读与活动</caption><thead><tr><th>日期</th><th>阅读记录分钟</th><th>活动次数</th></tr></thead><tbody><tr v-for="item in report.dailyActivity" :key="item.date"><td>{{ item.date }}</td><td>{{ item.minutes }}</td><td>{{ item.count }}</td></tr></tbody></table><table><caption>最近8周</caption><thead><tr><th>周一日期</th><th>阅读记录分钟</th><th>签到天数</th></tr></thead><tbody><tr v-for="item in report.weeklyTrend" :key="item.weekStart"><td>{{ item.weekStart }}</td><td>{{ item.studyMinutes }}</td><td>{{ item.checkinDays }}</td></tr></tbody></table></div></details>
      <NextActionCard v-if="learningStore.loaded && !learningStore.error && learningStore.nextAction" :action="learningStore.nextAction" />
      <!-- 详细数据汇总 -->
      <section class="mt-6 grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="border rounded-[10px] p-5 bg-card border-border">
          <h3 class="report-h3 mb-3">闪卡 & 错题</h3>
          <dl class="space-y-2 text-sm">
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">期内复习过的卡片</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-foreground);">{{ report.flashcardReviewed }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">已掌握闪卡</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-accent);">{{ report.flashcardMastered }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">错题总数</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-foreground);">{{ report.mistakeCount }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">已掌握错题</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-accent);">{{ report.mistakeMastered }}</dd></div>
          </dl>
        </div>
        <div class="border rounded-[10px] p-5 bg-card border-border">
          <h3 class="report-h3 mb-3">代码 & 测验</h3>
          <dl class="space-y-2 text-sm">
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">代码提交次数</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-foreground);">{{ report.codeSubmissions }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">代码通过次数</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-accent);">{{ report.codePassed }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">测验答题数</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-foreground);">{{ report.quizAnswered }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">测验答对数</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-accent);">{{ report.quizCorrect }}</dd></div>
          </dl>
        </div>
        <div class="border rounded-[10px] p-5 bg-card border-border">
          <h3 class="report-h3 mb-3">累计阅读与签到</h3>
          <dl class="space-y-2 text-sm">
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">累计阅读文档数</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-foreground);">{{ report.docsRead }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">阅读记录时长(分钟)</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-primary);">{{ report.studyMinutes }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">签到天数</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-foreground);">{{ report.checkinDays }}</dd></div>
            <div class="flex justify-between gap-2"><dt class="min-w-0 truncate" style="color: var(--kb-muted-foreground);">当前连续打卡</dt><dd class="tabular-nums font-medium shrink-0" style="color: var(--kb-warning);">{{ report.continuousDays }} 天</dd></div>
          </dl>
        </div>
      </section>
    </div></template>
    </StateView>
    <KfLearningEvidencePanel />
  </div>
</template>

<script setup lang="ts">
import EvidenceReportPanel from '@/components/learning-adaptive/EvidenceReportPanel.vue'
const evidenceReportEnabled=import.meta.env.VITE_PRODUCT_ADAPTIVE_LEARNING==='true'
// 学习报告页：按周期聚合签到/闪卡/错题/代码/阅读/测验数据，含活跃度柱状图、掌握度进度条、周趋势。
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import { useLearningStore } from '@/stores/learning'
import PageHeader from '@/components/ui/PageHeader.vue'
import KfLearningEvidencePanel from '@/components/Learning/KfLearningEvidencePanel.vue'
import Icon from '@/components/ui/Icon.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import StateView from '@/components/ui/StateView.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { learningApi } from '@/api'
import type { LearningReportData } from '@/api/types'

type Period = 'week' | 'month' | 'all'

const periodOptions: { value: Period; label: string }[] = [
  { value: 'week', label: '近 7 天' },
  { value: 'month', label: '近 30 天' },
  { value: 'all', label: '全部' },
]

const route = useRoute()
const learningStore = useLearningStore()

// 从路由参数读取 period，保持用户跳转上下文
function resolveInitialPeriod(): Period {
  const p = route.query.period as string
  if (p === 'week' || p === 'month' || p === 'all') return p
  return 'month'
}

const period = ref<Period>(resolveInitialPeriod())
const reportResource = useAsyncResource(() => learningApi.getReport(period.value), value => !value, '学习报告加载失败')
const { status: reportStatus, error: reportError } = reportResource
const report = computed<LearningReportData | null>(() => reportResource.data.value)

const overviewCards = computed(() => {
  const r = report.value
  if (!r) return []
  return [
    { label: '签到天数', value: r.checkinDays, icon: 'calendar-check', color: 'var(--kb-primary)', sub: `连续 ${r.continuousDays} 天` },
    { label: '当前连续打卡', value: r.continuousDays, icon: 'flame', color: 'var(--kb-warning)', sub: '保持下去' },
    { label: '累计阅读时长(分)', value: r.studyMinutes, icon: 'clock', color: 'var(--kb-accent)', sub: `阅读 ${r.docsRead} 篇` },
    { label: '期内复习过的卡片', value: r.flashcardReviewed, icon: 'layers', color: 'var(--kb-primary)', sub: `当前掌握快照 ${r.flashcardMastered}` },
    { label: '当前已掌握错题', value: r.mistakeMastered, icon: 'check-circle', color: 'var(--kb-accent)', sub: `期内新增 ${r.mistakeCount} 题` },
    { label: '代码提交', value: r.codeSubmissions, icon: 'code', color: 'var(--kb-highlight)', sub: `通过 ${r.codePassed}` },
  ]
})

const dailyTotalMinutes = computed(() =>
  report.value?.dailyActivity.reduce((sum, d) => sum + d.minutes, 0) ?? 0,
)
const dailyMaxMinutes = computed(() =>
  Math.max(1, ...(report.value?.dailyActivity.map((d) => d.minutes) ?? [1])),
)

function dailyBarHeight(minutes: number): number {
  if (minutes <= 0) return 0
  return Math.max(4, Math.round((minutes / dailyMaxMinutes.value) * 100))
}

const weeklyMaxMinutes = computed(() =>
  Math.max(1, ...(report.value?.weeklyTrend.map((w) => w.studyMinutes) ?? [1])),
)
const weeklyMaxCheckins = computed(() =>
  Math.max(1, ...(report.value?.weeklyTrend.map((w) => w.checkinDays) ?? [1])),
)

function weeklyBarHeight(value: number, max: number): number {
  if (value <= 0) return 0
  return Math.max(4, Math.round((value / max) * 100))
}

function switchPeriod(p: Period) {
  if (p === period.value) return
  period.value = p
  void loadData()
}

async function loadData(retry = false): Promise<void> {
  await reportResource.run(retry)
}

onMounted(() => {
  void loadData()
  void learningStore.load()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 标题字体：衬线 */
.report-h2 {
  font-size: var(--kb-fs-h4);
  font-weight: var(--kb-fw-h4);
  line-height: var(--kb-lh-h4);
  color: var(--kb-foreground);
  font-family: var(--font-serif, 'Noto Serif SC', Georgia, serif);
}
.report-h3 {
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  line-height: var(--kb-lh-h4);
  color: var(--kb-foreground);
  font-family: var(--font-serif, 'Noto Serif SC', Georgia, serif);
}

/* 概览卡片悬浮反馈 */
.report-card {
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}
.report-card:hover {
  box-shadow: var(--shadow-card-hover);
}

/* ========== 每日活跃度柱状图 ========== */
.daily-chart {
  display: flex;
  align-items: flex-end;
  gap: var(--kb-space-1);
  height: 180px;
  padding-top: var(--kb-space-2);
  overflow-x: auto;
}
.daily-bar-wrap {
  flex: 1 0 0;
  min-width: 6px;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  position: relative;
}
.daily-bar-track {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.daily-bar {
  width: 70%;
  min-height: 2px;
  border-radius: 4px 4px 0 0;
  background: var(--kb-primary);
  transition: height 0.3s ease, opacity 0.15s ease;
  cursor: pointer;
}
.daily-bar:hover {
  opacity: 0.75;
}
.daily-bar:active {
  opacity: 0.6;
}
.daily-bar:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.daily-bar-label {
  position: absolute;
  bottom: -18px;
  font-size: var(--kb-fs-xs);
  line-height: var(--kb-lh-xs);
  color: var(--kb-muted-foreground);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* ========== 周趋势柱状图 ========== */
.weekly-chart {
  display: flex;
  align-items: flex-end;
  gap: var(--kb-space-3);
  height: 200px;
  padding-top: var(--kb-space-2);
  overflow-x: auto;
}
.weekly-col {
  flex: 1 0 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}
.weekly-bars {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: var(--kb-space-1);
}
.weekly-bar {
  width: 18px;
  min-height: 2px;
  border-radius: 4px 4px 0 0;
  transition: height 0.3s ease, opacity 0.15s ease;
  cursor: pointer;
}
.weekly-bar:hover {
  opacity: 0.75;
}
.weekly-bar:active {
  opacity: 0.6;
}
.weekly-bar:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.weekly-bar-minutes {
  background: var(--kb-primary);
}
.weekly-bar-checkins {
  background: var(--kb-accent);
}
.weekly-label {
  margin-top: var(--kb-space-2);
  font-size: var(--kb-fs-xs);
  line-height: var(--kb-lh-xs);
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .weekly-bar {
    width: 12px;
  }
  .weekly-chart {
    gap: var(--kb-space-2);
  }
}

.report-method, .report-data { margin-block:var(--kb-space-5); padding:var(--kb-space-4); border:1px solid var(--kb-border); border-radius:var(--kb-radius-md); background:var(--kb-card); color:var(--kb-muted-foreground); font-size:var(--kb-fs-body-sm); line-height:var(--kb-lh-body-md); }
.report-context { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:var(--kb-space-3); }.report-context .report-method { margin-block:var(--kb-space-4); flex:1; min-width:0; }.report-method summary { cursor:pointer; color:var(--kb-foreground); }.report-mastery-link { color:var(--kb-primary); font-size:var(--kb-fs-body-sm); }.report-method p { margin-top:var(--kb-space-3); }.report-method h2 { color:var(--kb-foreground); font-weight:600; margin-bottom:var(--kb-space-2); }.report-method a { display:inline-block; margin-top:var(--kb-space-2); color:var(--kb-primary); }.report-data-scroll { max-width:100%; overflow:auto; }.report-data table { width:100%; margin-block:var(--kb-space-3); border-collapse:collapse; }.report-data th,.report-data td { padding:var(--kb-space-2); text-align:left; border-bottom:1px solid var(--kb-border); }.report-data summary { cursor:pointer; color:var(--kb-foreground); }.daily-count { font-size:var(--kb-fs-caption); color:var(--kb-muted-foreground); white-space:nowrap; }
@media(prefers-reduced-motion:reduce) { .learning-report * { animation:none; transition:none; } }
</style>
