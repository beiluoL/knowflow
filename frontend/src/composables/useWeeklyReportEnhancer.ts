import { focusSessionApi, learningApi, mistakesApi, userApi } from '@/api';
import { notify, getApiError } from '@/utils/toast';
import type {
  FocusStatsVO,
  DailyActivityVO,
  MasteryDistributionVO,
  MistakeStats,
  UserStatsVO,
} from '@/api/types';

export interface WeeklyReportVO {
  id?: number;
  weekStart?: string;
  weekEnd?: string;
  studyMinutes?: number;
  checkinDays?: number;
  flashcardReviewed?: number;
  summary?: string;
  achievements?: string[];
  suggestions?: string[];
  createTime?: string;
}

export interface AggregatedMetrics {
  focusStats: FocusStatsVO | null;
  daily: DailyActivityVO[];
  mastery: MasteryDistributionVO | null;
  mistakes: MistakeStats | null;
  user: UserStatsVO | null;
}

export interface AiInsight {
  icon: string;
  title: string;
  content: string;
  tone: 'positive' | 'warning' | 'info';
}

export async function aggregateWeeklyMetrics(): Promise<AggregatedMetrics> {
  const metrics: AggregatedMetrics = {
    focusStats: null,
    daily: [],
    mastery: null,
    mistakes: null,
    user: null,
  };

  const results = await Promise.allSettled([
    focusSessionApi.stats(7),
    learningApi.dailyActivity(7),
    learningApi.mastery(),
    mistakesApi.stats(),
    userApi.stats(),
  ]);

  results.forEach((r, idx) => {
    if (r.status === 'fulfilled') {
      if (idx === 0) metrics.focusStats = r.value as FocusStatsVO;
      else if (idx === 1) metrics.daily = r.value as DailyActivityVO[];
      else if (idx === 2) metrics.mastery = r.value as MasteryDistributionVO;
      else if (idx === 3) metrics.mistakes = r.value as MistakeStats;
      else if (idx === 4) metrics.user = r.value as UserStatsVO;
    } else {
      const labels = ['专注统计', '每日活跃度', '掌握度分布', '错题统计', '用户统计'];
      notify(`加载${labels[idx]}失败：${getApiError(r.reason, '网络异常')}`, 'warning');
    }
  });

  return metrics;
}

export function generateAiInsights(
  report: WeeklyReportVO | null,
  metrics: AggregatedMetrics,
): AiInsight[] {
  const insights: AiInsight[] = [];
  const { focusStats, mastery, mistakes, daily } = metrics;

  if (focusStats) {
    if (focusStats.todayMinutes < 60) {
      insights.push({
        icon: 'clock',
        title: '专注时长建议',
        content: '本周日均专注不足1小时，建议每天开启番茄专注3轮，稳步提升学习时长。',
        tone: 'warning',
      });
    } else if (focusStats.weekMinutes >= 840) {
      insights.push({
        icon: 'flame',
        title: '本周学习达人',
        content: `本周累计专注 ${Math.round(focusStats.weekMinutes / 60)} 小时，超过大多数学习者，继续保持！`,
        tone: 'positive',
      });
    }

    if (focusStats.avgQuality >= 4) {
      insights.push({
        icon: 'star',
        title: '专注质量优秀',
        content: `近周专注质量评分 ${focusStats.avgQuality.toFixed(1)} 星，保持高效心流状态！`,
        tone: 'positive',
      });
    }

    const breakdown = focusStats.modeBreakdown || {};
    const totalMin = Object.values(breakdown).reduce((s, n) => s + (n || 0), 0);
    if (totalMin > 0 && (breakdown.FLOW || 0) / totalMin > 0.3) {
      insights.push({
        icon: 'waves',
        title: '流时间掌控者',
        content: '流(FLOW)时间占比超30%，你很擅长进入深度专注状态，学习效率出色。',
        tone: 'positive',
      });
    }
  }

  if (mistakes && mistakes.weeklyNew > 0) {
    insights.push({
      icon: 'alert-triangle',
      title: '本周错题提醒',
      content: `本周新增 ${mistakes.weeklyNew} 道错题，建议抽出30分钟集中复盘错题本。`,
      tone: 'warning',
    });
  }

  if (mastery && mastery.flashcardDue > 0) {
    insights.push({
      icon: 'layers',
      title: '闪卡复习提醒',
      content: `当前有 ${mastery.flashcardDue} 张闪卡待复习，及时巩固更能抵抗遗忘曲线。`,
      tone: 'info',
    });
  }

  if (report?.studyMinutes != null) {
    const avg = Math.round(report.studyMinutes / Math.max(report.checkinDays || 1, 1));
    if (insights.length < 3) {
      insights.push({
        icon: 'bar-chart-3',
        title: '本周学习节奏',
        content: report.checkinDays
          ? `本周共打卡 ${report.checkinDays} 天，日均学习 ${avg} 分钟，节奏稳定。`
          : '本周学习数据已记录，继续加油！',
        tone: 'info',
      });
    }
  }

  if (daily && daily.length > 0) {
    const activeDays = daily.filter((d) => (d.count || 0) > 0).length;
    if (activeDays >= 6 && insights.length < 3) {
      insights.push({
        icon: 'calendar-check',
        title: '满勤趋势',
        content: `本周近 ${activeDays} 天都有学习记录，坚持就是最大的胜利！`,
        tone: 'positive',
      });
    }
  }

  if (insights.length === 0) {
    insights.push({
      icon: 'sparkles',
      title: '学习小结',
      content: '本周学习之旅已记录，继续积累每一个微小的进步吧！',
      tone: 'info',
    });
  }

  return insights.slice(0, 3);
}

export function generateShareableText(
  report: WeeklyReportVO | null,
  metrics: AggregatedMetrics,
): string {
  const lines: string[] = [];
  lines.push('📚 KnowFlow 学习周报');
  lines.push('━━━━━━━━━━━━━━━━━');

  if (report?.weekStart && report?.weekEnd) {
    lines.push(`📅 ${report.weekStart} ~ ${report.weekEnd}`);
  } else {
    lines.push(`📅 ${new Date().toLocaleDateString('zh-CN')} 当周`);
  }
  lines.push('');

  const minutes = report?.studyMinutes ?? metrics.focusStats?.weekMinutes ?? 0;
  const days = report?.checkinDays ?? metrics.user?.streakDays ?? 0;
  const cards = report?.flashcardReviewed ?? metrics.mastery?.flashcardReviewed ?? 0;
  lines.push(`⏱ 本周学习：${minutes} 分钟`);
  lines.push(`✅ 打卡天数：${days} 天`);
  lines.push(`🎴 复习闪卡：${cards} 张`);

  if (metrics.focusStats?.avgQuality) {
    lines.push(`⭐ 专注质量：${metrics.focusStats.avgQuality.toFixed(1)} / 5`);
  }
  if (metrics.mistakes?.weeklyNew != null) {
    lines.push(`📝 新增错题：${metrics.mistakes.weeklyNew} 道`);
  }
  lines.push('');

  if (report?.achievements && report.achievements.length > 0) {
    lines.push('🏆 本周成就：');
    report.achievements.slice(0, 3).forEach((a) => lines.push(`  • ${a}`));
    lines.push('');
  }

  const insights = generateAiInsights(report, metrics);
  if (insights.length > 0) {
    lines.push('💡 AI 洞见：');
    insights.forEach((i) => lines.push(`  • ${i.content}`));
    lines.push('');
  }

  if (report?.suggestions && report.suggestions.length > 0) {
    lines.push('🎯 下周建议：');
    report.suggestions.slice(0, 2).forEach((s) => lines.push(`  • ${s}`));
    lines.push('');
  }

  lines.push('━━━━━━━━━━━━━━━━━');
  lines.push('一起在 KnowFlow 坚持学习，成就更好的自己 🚀');

  return lines.join('\n');
}
