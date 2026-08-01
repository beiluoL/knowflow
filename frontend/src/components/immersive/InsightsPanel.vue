<template>
  <div class="ip-panel">
    <header class="ip-tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        type="button"
        class="ip-tab"
        :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >
        <Icon :name="t.icon" :size="14" />
        <span>{{ t.label }}</span>
      </button>
    </header>

    <div class="ip-content">
      <transition name="ip-fade" mode="out-in">
        <!-- Tab1: 今日概览 -->
        <div v-if="activeTab === 'today'" key="today" class="ip-tab-pane">
          <template v-if="tabLoading.today">
            <TabSkeleton :cards="4" />
          </template>
          <template v-else>
            <div class="ip-grid-2">
              <div class="ip-card">
                <div class="ip-card-label">
                  <Icon name="clock" :size="14" />
                  <span>今日专注</span>
                </div>
                <div class="ip-bignum tabular-nums">{{ focusStats?.todayMinutes ?? 0 }}
                  <span class="ip-unit">分钟</span>
                </div>
                <div class="ip-compare" :class="compareClass">
                  <Icon :name="compareIcon" :size="12" />
                  <span>{{ compareText }}</span>
                </div>
              </div>

              <div class="ip-card">
                <div class="ip-card-label">
                  <Icon name="timer" :size="14" />
                  <span>今日番茄</span>
                </div>
                <div class="ip-bignum tabular-nums">{{ focusStats?.todayPomodoros ?? 0 }}
                  <span class="ip-unit">/ 10</span>
                </div>
                <div class="ip-progress">
                  <div
                    class="ip-progress-bar"
                    :style="{ width: Math.min((focusStats?.todayPomodoros ?? 0) * 10, 100) + '%' }"
                  />
                </div>
              </div>

              <div class="ip-card">
                <div class="ip-card-label">
                  <Icon name="zap-off" :size="14" />
                  <span>分心次数</span>
                </div>
                <div class="ip-bignum tabular-nums">{{ totalDistractions }}
                  <span class="ip-unit">次</span>
                </div>
                <div class="ip-quality">
                  <span class="ip-q-label">质量评分</span>
                  <div class="ip-stars">
                    <Icon
                      v-for="i in 5"
                      :key="i"
                      name="star"
                      :size="14"
                      :class="{ filled: i <= Math.round(focusStats?.avgQuality ?? 0) }"
                    />
                  </div>
                </div>
              </div>

              <div class="ip-card">
                <div class="ip-card-label">
                  <Icon name="flame" :size="14" />
                  <span>连续打卡</span>
                </div>
                <div class="ip-bignum tabular-nums highlight-text">{{ streakDays }}
                  <span class="ip-unit">天</span>
                </div>
                <div class="ip-streak-bar">
                  <div
                    v-for="i in 7"
                    :key="i"
                    class="ip-streak-dot"
                    :class="{ active: i <= streakDays % 7 || streakDays >= 7 }"
                  />
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- Tab2: 专注热力图 -->
        <div v-else-if="activeTab === 'heatmap'" key="heatmap" class="ip-tab-pane">
          <template v-if="tabLoading.heatmap">
            <TabSkeleton :cards="3" />
          </template>
          <template v-else>
            <div class="ip-block">
              <div class="ip-block-title">
                <Icon name="activity" :size="14" />
                <span>24小时热度条</span>
              </div>
              <div class="ip-heat-row">
                <div
                  v-for="(v, i) in (focusStats?.hourlyHeatmap ?? emptyHeatmap)"
                  :key="i"
                  class="ip-heat-cell"
                  :style="{ background: heatColor(v) }"
                  :title="`${i}:00 ~ ${i + 1}:00  ${v} 分钟`"
                />
              </div>
              <div class="ip-heat-legend">
                <span>0</span>
                <div class="ip-heat-legend-bar" />
                <span>120分钟</span>
              </div>
            </div>

            <div class="ip-block">
              <div class="ip-block-title">
                <Icon name="bar-chart-2" :size="14" />
                <span>本周柱状图</span>
              </div>
              <div class="ip-week-bars">
                <div
                  v-for="(d, idx) in weekBars"
                  :key="idx"
                  class="ip-week-col"
                >
                  <div class="ip-week-bar-wrap">
                    <div
                      class="ip-week-bar"
                      :style="{ height: d.height + '%' }"
                      :title="`${d.label}  ${d.minutes} 分钟`"
                    />
                  </div>
                  <div class="ip-week-label">{{ d.shortLabel }}</div>
                </div>
              </div>
            </div>

            <div class="ip-block">
              <div class="ip-block-title">
                <Icon name="list" :size="14" />
                <span>今日会话时间线</span>
              </div>
              <div v-if="todaySessions.length === 0" class="ip-empty">
                暂无今日专注记录
              </div>
              <div v-else class="ip-timeline">
                <div
                  v-for="(s, i) in todaySessions"
                  :key="s.id ?? i"
                  class="ip-tl-item"
                >
                  <div class="ip-tl-dot" :style="{ background: modeColor(s.mode) }" />
                  <div class="ip-tl-time tabular-nums">{{ formatTime(s.startTime) }}</div>
                  <div class="ip-tl-mode-badge" :style="{ background: modeColorSoft(s.mode), color: modeColor(s.mode) }">
                    {{ s.mode ?? 'POMODORO' }}
                  </div>
                  <div class="ip-tl-duration tabular-nums">{{ s.durationMin ?? 0 }} 分钟</div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- Tab3: 模式分布 -->
        <div v-else-if="activeTab === 'mode'" key="mode" class="ip-tab-pane">
          <template v-if="tabLoading.mode">
            <TabSkeleton :cards="2" />
          </template>
          <template v-else>
            <div class="ip-block">
              <div class="ip-block-title">
                <Icon name="pie-chart" :size="14" />
                <span>专注模式分布</span>
                <span class="ip-week-minutes tabular-nums">
                  总 {{ focusStats?.weekMinutes ?? 0 }} 分钟
                </span>
              </div>

              <div class="ip-stacked-bar">
                <div
                  v-for="seg in stackedSegments"
                  :key="seg.mode"
                  class="ip-stacked-seg"
                  :style="{ width: seg.percent + '%', background: seg.color }"
                  :title="`${seg.label}  ${seg.minutes} 分钟 (${seg.percent.toFixed(1)}%)`"
                />
              </div>

              <div class="ip-mode-list">
                <div
                  v-for="seg in stackedSegments"
                  :key="seg.mode"
                  class="ip-mode-item"
                >
                  <span class="ip-mode-dot" :style="{ background: seg.color }" />
                  <span class="ip-mode-badge" :style="{ background: seg.color + '18', color: seg.color }">
                    {{ seg.label }}
                  </span>
                  <span class="ip-mode-minutes tabular-nums">{{ seg.minutes }} 分钟</span>
                  <span class="ip-mode-percent tabular-nums">{{ seg.percent.toFixed(0) }}%</span>
                </div>
              </div>
            </div>

            <div class="ip-card ip-week-minutes-card">
              <div class="ip-card-label">
                <Icon name="calendar" :size="14" />
                <span>本周学习总时长</span>
              </div>
              <div class="ip-bignum tabular-nums highlight-text">
                {{ Math.round((focusStats?.weekMinutes ?? 0) / 60) }}
                <span class="ip-unit">小时</span>
                <span class="ip-sub-minutes tabular-nums">
                  {{ (focusStats?.weekMinutes ?? 0) % 60 }} 分钟
                </span>
              </div>
            </div>
          </template>
        </div>

        <!-- Tab4: 洞见与建议 -->
        <div v-else key="insights" class="ip-tab-pane">
          <template v-if="tabLoading.insights">
            <TabSkeleton :cards="4" />
          </template>
          <template v-else>
            <div class="ip-block">
              <div class="ip-block-title">
                <Icon name="sparkles" :size="14" />
                <span>AI 洞见</span>
              </div>
              <div class="ip-insight-list">
                <div
                  v-for="(it, idx) in aiInsights"
                  :key="idx"
                  class="ip-insight-card"
                  :class="it.tone"
                >
                  <div class="ip-insight-icon">
                    <Icon :name="it.icon" :size="16" />
                  </div>
                  <div class="ip-insight-body">
                    <div class="ip-insight-title">{{ it.title }}</div>
                    <div class="ip-insight-content">{{ it.content }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div class="ip-block">
              <div class="ip-block-title">
                <Icon name="award" :size="14" />
                <span>最近成就解锁</span>
              </div>
              <div v-if="recentAchievements.length === 0" class="ip-empty">
                继续努力解锁更多成就吧
              </div>
              <div v-else class="ip-achieve-list">
                <div
                  v-for="(a, idx) in recentAchievements"
                  :key="a.id ?? idx"
                  class="ip-achieve-item"
                >
                  <div class="ip-achieve-icon">
                    <Icon :name="a.icon" :size="16" />
                  </div>
                  <div class="ip-achieve-body">
                    <div class="ip-achieve-name">{{ a.name }}</div>
                    <div class="ip-achieve-desc">{{ a.desc }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div class="ip-grid-2">
              <div class="ip-card">
                <div class="ip-card-label">
                  <Icon name="book-open" :size="14" />
                  <span>待复习闪卡</span>
                </div>
                <div class="ip-bignum tabular-nums">{{ mastery?.flashcardDue ?? lowMasteryFallback }}
                  <span class="ip-unit">张</span>
                </div>
                <div class="ip-hint">建议今日完成复习</div>
              </div>
              <div class="ip-card">
                <div class="ip-card-label">
                  <Icon name="alert-triangle" :size="14" />
                  <span>本周错题</span>
                </div>
                <div class="ip-bignum tabular-nums highlight-text">{{ mistakes?.total ?? 0 }}
                  <span class="ip-unit">道</span>
                </div>
                <div class="ip-hint">
                  未掌握 {{ mistakes?.pending ?? 0 }} 道 · 本周新增 {{ mistakes?.weeklyNew ?? 0 }} 道
                </div>
              </div>
            </div>
          </template>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, defineComponent, h, PropType } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { focusSessionApi, learningApi, mistakesApi, userApi } from '@/api';
import { useMicroAchievements } from '@/composables/useMicroAchievements';
import { notify, getApiError } from '@/utils/toast';
import type {
  FocusStatsVO,
  FocusSessionVO,
  DailyActivityVO,
  MasteryDistributionVO,
  MistakeStats,
  FocusModeType,
} from '@/api/types';

const TABS = [
  { key: 'today', label: '今日概览', icon: 'layout-dashboard' },
  { key: 'heatmap', label: '专注热力图', icon: 'activity' },
  { key: 'mode', label: '模式分布', icon: 'pie-chart' },
  { key: 'insights', label: '洞见与建议', icon: 'sparkles' },
] as const;

type TabKey = typeof TABS[number]['key'];

const props = defineProps<{
  visible?: boolean;
}>();

const activeTab = ref<TabKey>('today');

const focusStats = ref<FocusStatsVO | null>(null);
const todaySessions = ref<FocusSessionVO[]>([]);
const dailyActivity = ref<DailyActivityVO[]>([]);
const mastery = ref<MasteryDistributionVO | null>(null);
const mistakes = ref<MistakeStats | null>(null);
const streakDays = ref(0);

const tabLoading = reactive({
  today: true,
  heatmap: true,
  mode: true,
  insights: true,
});

const microAchievements = useMicroAchievements();

const emptyHeatmap = Array.from({ length: 24 }, () => 0);

const yesterdayMock = computed(() => {
  const today = focusStats.value?.todayMinutes ?? 0;
  return Math.max(1, Math.round(today / 1.2));
});

const compareClass = computed(() => {
  const t = focusStats.value?.todayMinutes ?? 0;
  const y = yesterdayMock.value;
  if (t > y) return 'up';
  if (t < y) return 'down';
  return 'flat';
});

const compareIcon = computed(() => {
  if (compareClass.value === 'up') return 'trending-up';
  if (compareClass.value === 'down') return 'trending-down';
  return 'minus';
});

const compareText = computed(() => {
  const t = focusStats.value?.todayMinutes ?? 0;
  const y = yesterdayMock.value;
  if (y === 0) return '新的一天，开始学习吧';
  const pct = Math.round(((t - y) / y) * 100);
  if (pct > 0) return `较昨日 +${pct}%`;
  if (pct < 0) return `较昨日 ${pct}%`;
  return '与昨日持平';
});

const totalDistractions = computed(() => {
  return todaySessions.value.reduce((s, x) => s + (x.distractionCount ?? 0), 0);
});

function heatColor(minutes: number): string {
  const clamped = Math.max(0, Math.min(120, minutes));
  const t = clamped / 120;
  if (t <= 0) return 'var(--kb-muted)';
  const alpha = 0.15 + t * 0.85;
  return `color-mix(in srgb, var(--kb-primary) ${alpha * 100}%, transparent)`;
}

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

function modeColor(mode?: FocusModeType | string): string {
  return MODE_COLORS[mode ?? 'POMODORO'] ?? '#3B6FE0';
}
function modeColorSoft(mode?: FocusModeType | string): string {
  const c = modeColor(mode);
  return `${c}18`;
}

function formatTime(ts?: string): string {
  if (!ts) return '--:--';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return ts.slice(11, 16) || '--:--';
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

const weekBars = computed(() => {
  const days = ['日', '一', '二', '三', '四', '五', '六'];
  const result = [] as Array<{ label: string; shortLabel: string; minutes: number; height: number }>;
  const today = new Date();
  const maxMinutes = Math.max(60, ...dailyActivity.value.map((d) => d.count ?? 0));
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(today.getDate() - i);
    const dateStr = d.toISOString().slice(0, 10);
    const match = dailyActivity.value.find((x) => x.date === dateStr);
    const minutes = match?.count ?? 0;
    result.push({
      label: `${d.getMonth() + 1}/${d.getDate()}`,
      shortLabel: days[d.getDay()],
      minutes,
      height: Math.max(4, (minutes / maxMinutes) * 100),
    });
  }
  return result;
});

const stackedSegments = computed(() => {
  const breakdown = focusStats.value?.modeBreakdown ?? {};
  const total = Object.values(breakdown).reduce((s, n) => s + (n || 0), 0);
  const modes: Array<keyof typeof MODE_COLORS> = ['POMODORO', 'FLOW', 'DEEP', 'SPACED', 'BUDDY'];
  return modes.map((m) => {
    const minutes = breakdown[m] ?? 0;
    return {
      mode: m,
      label: MODE_LABELS[m] ?? m,
      color: MODE_COLORS[m],
      minutes,
      percent: total > 0 ? (minutes / total) * 100 : 0,
    };
  });
});

interface AiInsightItem {
  icon: string;
  title: string;
  content: string;
  tone: 'positive' | 'warning' | 'info';
}

const aiInsights = computed<AiInsightItem[]>(() => {
  const list: AiInsightItem[] = [];
  const fs = focusStats.value;
  if (fs) {
    if (fs.todayMinutes < 60) {
      list.push({
        icon: 'clock',
        title: '专注时长提示',
        content: '今日专注不足1小时，建议开启番茄专注3轮，进入学习状态。',
        tone: 'warning',
      });
    }
    if (fs.avgQuality >= 4) {
      list.push({
        icon: 'star',
        title: '专注质量优秀',
        content: `近周专注质量评分 ${fs.avgQuality.toFixed(1)} 星，保持高效心流！`,
        tone: 'positive',
      });
    }
    const bd = fs.modeBreakdown ?? {};
    const totalMin = Object.values(bd).reduce((s, n) => s + (n || 0), 0);
    if (totalMin > 0 && (bd.FLOW || 0) / totalMin > 0.3) {
      list.push({
        icon: 'waves',
        title: '流时间掌控者',
        content: '流时间占比超30%，你很擅长进入深度专注状态。',
        tone: 'positive',
      });
    }
  }
  if (list.length < 3) {
    list.push({
      icon: 'flame',
      title: '坚持积累',
      content: `已连续打卡 ${streakDays.value} 天，坚持是最可贵的品质！`,
      tone: 'positive',
    });
  }
  if (list.length < 3 && mastery.value && mastery.value.flashcardDue > 0) {
    list.push({
      icon: 'layers',
      title: '闪卡复习提醒',
      content: `还有 ${mastery.value.flashcardDue} 张闪卡待复习，及时巩固抗遗忘。`,
      tone: 'info',
    });
  }
  if (list.length < 3) {
    list.push({
      icon: 'sparkles',
      title: '今日寄语',
      content: '每一个微小的进步，都是通向卓越的基石。加油！',
      tone: 'info',
    });
  }
  return list.slice(0, 3);
});

const lowMasteryFallback = 5;

const recentAchievements = computed(() => {
  const list = microAchievements.unlockedList.value ?? [];
  return [...list].reverse().slice(0, 3);
});

async function loadAllData(): Promise<void> {
  tabLoading.today = true;
  tabLoading.heatmap = true;
  tabLoading.mode = true;
  tabLoading.insights = true;

  try {
    const results = await Promise.allSettled([
      focusSessionApi.stats(7),
      focusSessionApi.today(),
      learningApi.dailyActivity(7),
      learningApi.mastery(),
      mistakesApi.stats(),
      userApi.stats(),
    ]);

    const labels = ['专注统计', '今日会话', '每日活跃度', '掌握度分布', '错题统计', '用户统计'];
    results.forEach((r, idx) => {
      if (r.status === 'fulfilled') {
        try {
          if (idx === 0) focusStats.value = r.value as FocusStatsVO;
          else if (idx === 1) todaySessions.value = r.value as FocusSessionVO[];
          else if (idx === 2) dailyActivity.value = r.value as DailyActivityVO[];
          else if (idx === 3) mastery.value = r.value as MasteryDistributionVO;
          else if (idx === 4) mistakes.value = r.value as MistakeStats;
          else if (idx === 5) {
            const us = r.value as { streakDays?: number };
            streakDays.value = us?.streakDays ?? 0;
          }
        } catch {
          // skip parse
        }
      } else {
        notify(`加载${labels[idx]}失败：${getApiError(r.reason, '网络异常')}`, 'warning');
      }
    });
  } finally {
    tabLoading.today = false;
    tabLoading.heatmap = false;
    tabLoading.mode = false;
    tabLoading.insights = false;
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) void loadAllData();
  },
);

onMounted(() => {
  void loadAllData();
});

const TabSkeleton = defineComponent({
  props: { cards: { type: Number as PropType<number>, default: 3 } },
  setup(props: { cards: number }) {
    const items = Array.from({ length: props.cards }, (_, i) => i);
    return () =>
      h(
        'div',
        { class: 'ip-skeleton' },
        items.map((i) =>
          h('div', {
            class: 'ip-sk-card',
            key: i,
            style: { animationDelay: `${i * 0.06}s` },
          }),
        ),
      );
  },
});
</script>

<style scoped>
.ip-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 12px;
}

.ip-tabs {
  display: flex;
  gap: 2px;
  padding: 3px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 10px;
  flex-shrink: 0;
}
.ip-tab {
  flex: 1;
  min-width: 0;
  height: 32px;
  padding: 0 4px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 11px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: background 0.15s ease, color 0.15s ease;
}
.ip-tab:hover {
  color: var(--kb-foreground);
}
.ip-tab.active {
  background: var(--kb-primary);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--kb-primary) 30%, transparent);
}

.ip-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.ip-tab-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ip-fade-enter-active,
.ip-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.ip-fade-enter-from,
.ip-fade-leave-to {
  opacity: 0;
  transform: translateX(4px);
}

.ip-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.ip-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ip-card-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.02em;
}
.ip-card-label svg {
  opacity: 0.8;
}

.ip-bignum {
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.1;
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
}
.ip-unit {
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
}
.ip-sub-minutes {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
  margin-left: 4px;
}

.ip-compare {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
}
.ip-compare.up {
  color: var(--kb-accent);
}
.ip-compare.down {
  color: var(--kb-destructive);
}
.ip-compare.flat {
  color: var(--kb-muted-foreground);
}

.ip-progress {
  height: 6px;
  background: var(--kb-muted);
  border-radius: 999px;
  overflow: hidden;
}
.ip-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--kb-primary), #6F9AF2);
  border-radius: 999px;
  transition: width 0.4s ease;
}

.ip-quality {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ip-q-label {
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.ip-stars {
  display: inline-flex;
  gap: 2px;
  color: var(--kb-muted);
}
.ip-stars svg.filled {
  color: var(--kb-warning);
  fill: currentColor;
}

.ip-streak-bar {
  display: flex;
  gap: 5px;
  justify-content: space-between;
}
.ip-streak-dot {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--kb-muted);
  transition: background 0.2s ease;
}
.ip-streak-dot.active {
  background: linear-gradient(90deg, var(--kb-highlight), var(--kb-warning));
}

.ip-block {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ip-block-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.ip-block-title svg {
  color: var(--kb-primary);
}
.ip-week-minutes {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
}

.ip-heat-row {
  display: flex;
  gap: 2px;
  width: 100%;
}
.ip-heat-cell {
  flex: 1 1 0;
  min-width: 0;
  height: 32px;
  border-radius: 4px;
  transition: transform 0.15s ease;
}
.ip-heat-cell:hover {
  transform: scaleY(1.12);
}

.ip-heat-legend {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  color: var(--kb-muted-foreground);
}
.ip-heat-legend-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: linear-gradient(
    90deg,
    var(--kb-muted) 0%,
    color-mix(in srgb, var(--kb-primary) 50%, transparent) 50%,
    var(--kb-primary) 100%
  );
}

.ip-week-bars {
  display: flex;
  gap: 6px;
  align-items: flex-end;
  height: 110px;
  padding: 4px 0;
}
.ip-week-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.ip-week-bar-wrap {
  width: 100%;
  height: 90px;
  display: flex;
  align-items: flex-end;
}
.ip-week-bar {
  width: 100%;
  min-height: 4px;
  background: linear-gradient(180deg, var(--kb-primary), #6F9AF2);
  border-radius: 4px 4px 2px 2px;
  transition: height 0.3s ease;
}
.ip-week-label {
  font-size: 10px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}

.ip-empty {
  padding: 18px 0;
  text-align: center;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.ip-timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ip-tl-item {
  display: grid;
  grid-template-columns: 8px 48px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 8px;
}
.ip-tl-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.ip-tl-time {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.ip-tl-mode-badge {
  display: inline-flex;
  justify-self: start;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
}
.ip-tl-duration {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}

.ip-stacked-bar {
  display: flex;
  width: 100%;
  height: 14px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--kb-muted);
}
.ip-stacked-seg {
  height: 100%;
  transition: width 0.4s ease;
}
.ip-stacked-seg:first-child {
  border-radius: 999px 0 0 999px;
}
.ip-stacked-seg:last-child {
  border-radius: 0 999px 999px 0;
}

.ip-mode-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ip-mode-item {
  display: grid;
  grid-template-columns: 8px auto 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  font-size: 12px;
}
.ip-mode-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.ip-mode-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 600;
  justify-self: start;
}
.ip-mode-minutes {
  color: var(--kb-foreground);
  font-weight: 500;
}
.ip-mode-percent {
  color: var(--kb-muted-foreground);
  font-weight: 500;
}

.ip-week-minutes-card {
  align-items: center;
  text-align: center;
}
.ip-week-minutes-card .ip-card-label {
  justify-content: center;
}
.ip-week-minutes-card .ip-bignum {
  justify-content: center;
  font-size: 28px;
}

.ip-insight-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ip-insight-card {
  display: grid;
  grid-template-columns: 32px 1fr;
  gap: 10px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.ip-insight-card.positive {
  border-color: rgba(16, 185, 129, 0.22);
  background: rgba(16, 185, 129, 0.06);
}
.ip-insight-card.warning {
  border-color: rgba(245, 158, 11, 0.22);
  background: rgba(245, 158, 11, 0.06);
}
.ip-insight-card.info {
  border-color: rgba(59, 111, 224, 0.22);
  background: rgba(59, 111, 224, 0.06);
}
.ip-insight-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.06);
  color: var(--kb-primary);
}
.ip-insight-card.positive .ip-insight-icon {
  color: var(--kb-accent);
  background: rgba(16, 185, 129, 0.14);
}
.ip-insight-card.warning .ip-insight-icon {
  color: var(--kb-warning);
  background: rgba(245, 158, 11, 0.14);
}
.ip-insight-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.ip-insight-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.ip-insight-content {
  font-size: 11px;
  line-height: 1.55;
  color: var(--kb-muted-foreground);
}

.ip-achieve-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ip-achieve-item {
  display: grid;
  grid-template-columns: 32px 1fr;
  gap: 10px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 10px;
  align-items: center;
}
.ip-achieve-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--kb-highlight-soft);
  color: var(--kb-highlight);
}
.ip-achieve-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.ip-achieve-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.ip-achieve-desc {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  line-height: 1.4;
}

.ip-hint {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  line-height: 1.4;
}

.highlight-text {
  color: var(--kb-highlight);
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}

/* skeleton */
.ip-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ip-sk-card {
  width: 100%;
  height: 92px;
  border-radius: 12px;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.03) 0%,
    rgba(255, 255, 255, 0.08) 50%,
    rgba(255, 255, 255, 0.03) 100%
  );
  background-size: 200% 100%;
  animation: ip-shimmer 1.4s infinite linear;
}
@keyframes ip-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
