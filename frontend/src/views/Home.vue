<template>
  <div class="home-container">
    <section class="welcome-banner">
      <div class="banner-bg">
        <div class="banner-grid"></div>
        <div class="banner-blur"></div>
      </div>
      <div class="banner-content">
        <div class="banner-header">
          <div class="welcome-info">
            <span class="welcome-badge">
              <Icon name="sun" :size="14" />
              {{ todayStr }}
            </span>
            <h1 class="welcome-title">{{ greeting }}，{{ userName }}！</h1>
            <p class="welcome-stats">
              <span class="stat-item">
                <Icon name="clock" :size="14" />
                <span class="stat-value tabular-nums">{{ studyHours }}</span>
                <span class="stat-unit">小时</span>
              </span>
              <span class="stat-divider"></span>
              <span class="stat-item">
                <Icon name="target" :size="14" />
                <span class="stat-value tabular-nums">{{ readDocs }}</span>
                <span class="stat-unit">篇文档</span>
              </span>
            </p>
          </div>
          <div class="banner-actions">
            <router-link to="/learning/center" class="action-btn primary">
              <Icon name="play" :size="16" />
              <span>继续学习</span>
            </router-link>
            <router-link to="/notes" class="action-btn secondary">
              <Icon name="plus" :size="16" />
              <span>创建笔记</span>
            </router-link>
          </div>
        </div>
      </div>
    </section>

    <section class="progress-section">
      <div class="section-header">
        <h2 class="section-title">今日学习进度</h2>
        <span class="section-tag">
          <Icon name="trending-up" :size="14" />
          坚持学习
        </span>
      </div>
      <div class="progress-cards">
        <div class="progress-card course-progress">
          <div class="card-header">
            <Icon name="book-open" :size="14" />
            <span class="card-label">当前课程进度</span>
          </div>
          <div class="progress-ring-container">
            <svg class="progress-ring" width="80" height="80">
              <circle class="progress-ring-bg" cx="40" cy="40" r="34"></circle>
              <circle class="progress-ring-fill" cx="40" cy="40" r="34" :stroke-dasharray="ringCircumference" :stroke-dashoffset="ringOffset"></circle>
            </svg>
            <div class="ring-center">
              <span class="ring-value tabular-nums">{{ courseProgress }}%</span>
            </div>
          </div>
          <div class="card-body">
            <h3 class="card-title">{{ currentCourse.title }}</h3>
            <p class="card-subtitle">{{ currentCourse.subtitle }}</p>
            <button class="card-action">
              <Icon name="arrow-right" :size="12" />
              <span>继续学习</span>
            </button>
          </div>
        </div>

        <div class="progress-card streak-progress">
          <div class="card-header">
            <Icon name="flame" :size="14" />
            <span class="card-label">连续学习天数</span>
          </div>
          <div class="streak-display">
            <span class="streak-number tabular-nums">{{ streakDays }}</span>
            <span class="streak-unit">天</span>
            <span class="streak-badge">
              <Icon name="trending-up" :size="12" />
              坚持中
            </span>
          </div>
          <div class="streak-calendar">
            <div v-for="(d, i) in weekdays" :key="i" class="day-item">
              <div class="day-dot" :class="{ active: d.active }"></div>
              <span class="day-label">{{ d.label }}</span>
            </div>
          </div>
        </div>

        <div class="progress-card mastery-progress">
          <div class="card-header">
            <Icon name="brain" :size="14" />
            <span class="card-label">知识掌握度</span>
          </div>
          <div class="mastery-display">
            <span class="mastery-number tabular-nums">{{ masteryPoints }}</span>
            <span class="mastery-unit">个知识点</span>
          </div>
          <p class="mastery-percent tabular-nums">已掌握 {{ masteryPercent }}%</p>
          <div class="mastery-bar">
            <div class="mastery-fill" :style="`width: ${masteryPercent}%`"></div>
          </div>
          <div class="mastery-meta">
            <span>已掌握 {{ masteryPercent }}%</span>
            <span>目标 {{ masteryTarget }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="todos-section">
      <div class="section-header">
        <h2 class="section-title">今日待办与计划</h2>
        <span class="section-badge">已完成 {{ doneTasks }} / {{ tasks.length }}</span>
      </div>
      <div class="todos-grid">
        <div class="todos-list">
          <div v-for="task in tasks.slice(0, 5)" :key="task.id" class="todo-item" :class="{ done: task.status === 1 }">
            <button class="todo-check" :aria-label="task.status === 1 ? '标记为未完成' : '标记为已完成'" @click="toggleTaskStatus(task)">
              <Icon v-if="task.status === 1" name="check" :size="12" />
            </button>
            <div class="todo-content">
              <h4 class="todo-title">{{ task.title }}</h4>
              <p v-if="task.description" class="todo-desc">{{ task.description }}</p>
            </div>
            <span class="todo-status" :class="getStatusClass(task)">
              <Icon v-if="task.status === 1" name="check-circle-2" :size="12" />
              <Icon v-else-if="task.deadline" name="clock" :size="12" />
              {{ getStatusText(task) }}
            </span>
          </div>
          <div v-if="tasks.length === 0" class="empty-state">
            <Icon name="list-checks" :size="24" />
            <router-link to="/learning/center" class="empty-state-link">暂无待办，去学习中心添加计划吧</router-link>
          </div>
        </div>

        <div class="weekly-goal">
          <div class="goal-header">
            <Icon name="target" :size="14" />
            <span class="goal-label">本周学习目标</span>
          </div>
          <div class="goal-ring-container">
            <svg class="goal-ring" width="100" height="100">
              <circle class="goal-ring-bg" cx="50" cy="50" r="42"></circle>
              <circle class="goal-ring-fill" cx="50" cy="50" r="42" stroke-width="8" :stroke-dasharray="weekCircumference" :stroke-dashoffset="weekOffset"></circle>
            </svg>
            <div class="goal-center">
              <span class="goal-percent tabular-nums">{{ weekPercent }}%</span>
              <span class="goal-stats">{{ weekDone }} / {{ weekTarget }}h</span>
            </div>
          </div>
          <div class="goal-details">
            <div class="goal-detail">
              <span class="detail-label">已完成</span>
              <span class="detail-value tabular-nums">{{ weekDone }} 小时</span>
            </div>
            <div class="goal-detail">
              <span class="detail-label">剩余目标</span>
              <span class="detail-value tabular-nums">{{ Math.max(0, weekTarget - weekDone) }} 小时</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="paths-section">
      <div class="section-header">
        <h2 class="section-title">为你推荐</h2>
        <router-link to="/learning/paths" class="section-link">
          <span>查看更多</span>
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>
      <div class="paths-scroll">
        <router-link
          v-for="path in learningPaths.slice(0, 6)"
          :key="path.id"
          :to="`/learning/path/${path.id}`"
          class="path-card"
        >
          <div class="path-cover" :style="{ background: pathCover(path.id) }">
            <Icon name="route" :size="24" />
          </div>
          <div class="path-content">
            <div class="path-meta">
              <span class="path-badge" :class="levelBadge(path.level).cls">{{ levelBadge(path.level).text }}</span>
              <span class="path-chapters tabular-nums">{{ path.chapterCount || 0 }} 章</span>
            </div>
            <h3 class="path-title">{{ path.title }}</h3>
            <p class="path-desc">{{ path.description || '系统化学习路径，循序渐进掌握技能。' }}</p>
            <div class="path-progress">
              <span class="progress-text">{{ pathProgress(path.id) }}%</span>
              <div class="progress-bar">
                <div class="progress-fill" :style="`width: ${pathProgress(path.id)}%`"></div>
              </div>
            </div>
          </div>
        </router-link>
        <div v-if="learningPaths.length === 0" class="empty-state">
          <Icon name="book-open" :size="24" />
          <p>暂无推荐路径，浏览全部路径</p>
        </div>
      </div>
    </section>

    <section class="continue-section">
      <div class="section-header">
        <h2 class="section-title">继续学习</h2>
        <router-link to="/categories" class="section-link">
          <span>查看全部</span>
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>
      <div class="continue-grid">
        <router-link
          v-for="doc in continueDocs.slice(0, 6)"
          :key="doc.id"
          :to="`/doc/${doc.id}`"
          class="doc-card"
        >
          <div class="doc-thumb" :style="{ background: docIconBg(doc.id) }">
            <Icon name="file-text" :size="20" :style="{ color: docIconColor(doc.id) }" />
          </div>
          <div class="doc-info">
            <h3 class="doc-title">{{ doc.title }}</h3>
            <p class="doc-category">
              <Icon name="folder" :size="12" />
              {{ doc.categoryName || '知识库' }}
            </p>
          </div>
          <div class="doc-footer">
            <span class="doc-time">{{ formatTime(doc.createTime) }}</span>
            <span class="doc-progress tabular-nums">{{ docProgress(doc) }}%</span>
          </div>
          <div class="doc-progress-bar">
            <div class="progress-fill" :style="`width: ${docProgress(doc)}%`"></div>
          </div>
        </router-link>
        <div v-if="continueDocs.length === 0" class="empty-state">
          <Icon name="book-open" :size="24" />
          <p>暂无学习记录，去知识库看看吧</p>
        </div>
      </div>
    </section>

    <section class="ai-section">
      <div class="section-header">
        <h2 class="section-title">AI 学习建议</h2>
        <span class="section-hint">基于你的学习行为智能生成</span>
      </div>
      <div class="ai-cards">
        <router-link
          v-for="(rec, idx) in aiRecs"
          :key="idx"
          :to="rec.path"
          class="ai-card"
        >
          <div class="ai-icon" :style="{ background: rec.bg, color: rec.color }">
            <Icon :name="rec.icon" :size="16" />
          </div>
          <div class="ai-content">
            <h4 class="ai-title">{{ rec.title }}</h4>
            <p class="ai-desc">{{ rec.desc }}</p>
          </div>
          <button class="ai-action">开始</button>
        </router-link>
      </div>
    </section>

    <section class="report-section">
      <router-link to="/learning/center" class="report-btn">
        <Icon name="bar-chart-2" :size="16" />
        <span>查看完整学习报告</span>
        <Icon name="arrow-right" :size="14" />
      </router-link>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { useAuthStore } from '@/stores/auth';
import { docsApi, learningApi, userApi } from '@/api';
import { notify, getApiError } from '@/utils/toast';
import type { DocVO, LearningPathVO, LearningTaskVO, UserStatsVO } from '@/api/types';

const auth = useAuthStore();

const userName = computed(() => auth.user?.nickname || auth.user?.username || '探索者');
const userStats = ref<UserStatsVO | null>(null);
const tasks = ref<LearningTaskVO[]>([]);
const learningPaths = ref<LearningPathVO[]>([]);
const continueDocs = ref<DocVO[]>([]);
const pathProgressMap = ref<Record<number, { progress: number; completed: number; total: number }>>({});

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '凌晨好';
  if (h < 12) return '上午好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const todayStr = computed(() => {
  const d = new Date();
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · ${week[d.getDay()]}`;
});

const studyHours = computed(() => userStats.value?.totalStudyHours ?? 0);
const readDocs = computed(() => userStats.value?.readDocsCount ?? 0);
const streakDays = computed(() => userStats.value?.streakDays ?? 0);

const weekdays = computed(() => {
  const labels = ['一', '二', '三', '四', '五', '六', '日'];
  const today = new Date().getDay() === 0 ? 6 : new Date().getDay() - 1;
  return labels.map((label, i) => ({ label, active: i <= today && streakDays.value > 0 }));
});

const courseProgress = computed(() => {
  if (!userStats.value?.readDocsCount) return 0;
  return Math.min(100, Math.round((userStats.value.readDocsCount / Math.max(1, userStats.value.readDocsCount + 5)) * 100));
});

const currentCourse = computed(() => ({
  title: continueDocs.value[0]?.title || '开始你的第一篇文档',
  subtitle: continueDocs.value[0]?.categoryName || '点击继续学习',
}));

const masteryPoints = computed(() => userStats.value?.totalFlashcards ?? 0);
const masteryTarget = 200;
const masteryPercent = computed(() => Math.min(100, Math.round((masteryPoints.value / masteryTarget) * 100)));

const doneTasks = computed(() => tasks.value.filter((t) => t.status === 1).length);

const weekTarget = 14;
const weekDone = computed(() => Math.min(weekTarget, Math.round((userStats.value?.totalStudyHours ?? 0) % weekTarget) || 0));
const weekPercent = computed(() => Math.round((weekDone.value / weekTarget) * 100));

const ringCircumference = 2 * Math.PI * 34;
const ringOffset = computed(() => ringCircumference * (1 - courseProgress.value / 100));
const weekCircumference = 2 * Math.PI * 42;
const weekOffset = computed(() => weekCircumference * (1 - weekPercent.value / 100));

const aiRecs = [
  { title: '复习学习闪卡', desc: '利用间隔重复巩固记忆，建议今日完成闪卡复习', icon: 'refresh-cw', bg: 'rgba(59,111,224,0.08)', color: 'var(--kb-primary)', path: '/learning/flashcards' },
  { title: '尝试代码练习', desc: '根据你的学习路径，建议完成今日编程练习', icon: 'code', bg: 'rgba(16,185,129,0.1)', color: 'var(--kb-accent)', path: '/learning/code-practice' },
  { title: '阅读推荐文档', desc: '与你最近学习内容相关，推荐延伸阅读', icon: 'book-open', bg: 'rgba(245,158,11,0.1)', color: 'var(--kb-warning)', path: '/categories' },
  { title: '智能问答答疑', desc: '遇到不懂的知识点，向 AI 提问快速解惑', icon: 'message-circle', bg: 'rgba(59,111,224,0.08)', color: 'var(--kb-primary)', path: '/chat' },
];

const pathCovers = [
  'linear-gradient(135deg, var(--kb-primary), rgba(59,111,224,0.6))',
  'linear-gradient(135deg, var(--kb-accent), rgba(16,185,129,0.6))',
  'linear-gradient(135deg, var(--kb-warning), rgba(245,158,11,0.6))',
  'linear-gradient(135deg, var(--kb-primary), rgba(16,185,129,0.7))',
];

function pathCover(id: number) {
  return pathCovers[Math.abs(id) % pathCovers.length];
}

function levelBadge(level?: string) {
  switch (level) {
    case 'BEGINNER': return { cls: 'badge-beginner', text: '入门' };
    case 'ADVANCED': return { cls: 'badge-advanced', text: '高级' };
    default: return { cls: 'badge-intermediate', text: '进阶' };
  }
}

function pathProgress(id: number) {
  const p = pathProgressMap.value[id];
  return p ? p.progress : 0;
}

async function loadAllPathProgress() {
  if (learningPaths.value.length === 0) return;
  try {
    const results = await Promise.allSettled(
      learningPaths.value.map((p) => learningApi.chapters(p.id)),
    );
    learningPaths.value.forEach((path, idx) => {
      const res = results[idx];
      if (res.status === 'fulfilled') {
        const chapters = res.value;
        const total = chapters.length || (path.chapterCount ?? 0);
        const completed = chapters.filter((c) => c.completed).length;
        const progress = total > 0 ? Math.round((completed / total) * 100) : 0;
        pathProgressMap.value[path.id] = { progress, completed, total };
      }
    });
  } catch {}
}

const docColors = [
  { bg: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' },
  { bg: 'rgba(16,185,129,0.1)', color: 'var(--kb-accent)' },
  { bg: 'rgba(245,158,11,0.1)', color: 'var(--kb-warning)' },
  { bg: 'rgba(124,58,237,0.1)', color: '#7C3AED' },
  { bg: 'rgba(20,184,166,0.1)', color: '#14B8A6' },
  { bg: 'rgba(249,115,22,0.1)', color: '#F97316' },
];

function docIconBg(id: number) {
  return docColors[Math.abs(id) % docColors.length].bg;
}

function docIconColor(id: number) {
  return docColors[Math.abs(id) % docColors.length].color;
}

function docProgress(_doc: DocVO) {
  return 0;
}

function formatTime(dateStr?: string) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
  if (diffHours < 1) return '刚刚';
  if (diffHours < 24) return `${diffHours} 小时前`;
  if (diffDays < 7) return `${diffDays} 天前`;
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

function formatDeadline(deadline?: string) {
  if (!deadline) return '';
  const d = new Date(deadline);
  const now = new Date();
  const diffMs = d.getTime() - now.getTime();
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  if (diffHours < 0) return '已逾期';
  if (diffHours < 24) return `今日 ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

function getStatusClass(task: LearningTaskVO) {
  if (task.status === 1) return 'completed';
  if (task.deadline) return 'deadline';
  return 'pending';
}

function getStatusText(task: LearningTaskVO) {
  if (task.status === 1) return '已完成';
  if (task.deadline) return formatDeadline(task.deadline);
  return '待完成';
}

async function toggleTaskStatus(task: LearningTaskVO) {
  const newStatus = task.status === 1 ? 0 : 1;
  try {
    await learningApi.updateTaskStatus(task.id, newStatus);
    task.status = newStatus;
    if (newStatus === 1) notify('任务已完成', 'success');
  } catch (e: unknown) {
    notify('操作失败：' + getApiError(e), 'error');
  }
}

onMounted(async () => {
  if (auth.isLoggedIn) {
    try {
      userStats.value = await userApi.stats();
    } catch {
      userStats.value = null;
    }
    try {
      tasks.value = await learningApi.tasks();
    } catch {
      tasks.value = [];
    }
  }
  try {
    learningPaths.value = await learningApi.paths();
    loadAllPathProgress();
  } catch {
    learningPaths.value = [];
  }
  try {
    continueDocs.value = await docsApi.recent();
  } catch {
    continueDocs.value = [];
  }
});
</script>

<style scoped>
.home-container {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
  animation: pageFadeIn 0.6s ease-out;
}

@keyframes pageFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.welcome-banner {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  margin-bottom: 24px;
  animation: slideUp 0.6s ease-out;
}

@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.banner-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, var(--kb-primary) 0%, rgba(59,111,224,0.85) 100%);
}

.banner-grid {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 40px 40px;
}

.banner-blur {
  position: absolute;
  top: -50px;
  right: -50px;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255,255,255,0.15) 0%, transparent 70%);
  border-radius: 50%;
}

.banner-content {
  position: relative;
  z-index: 1;
  padding: 32px;
}

.banner-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
}

.welcome-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 20px;
  background: rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.9);
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 16px;
}

.welcome-title {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: -0.02em;
  margin-bottom: 16px;
}

.welcome-stats {
  display: flex;
  align-items: center;
  gap: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(255,255,255,0.9);
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
}

.stat-unit {
  font-size: 13px;
}

.stat-divider {
  width: 1px;
  height: 24px;
  background: rgba(255,255,255,0.3);
}

.banner-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s ease;
}

.action-btn.primary {
  background: #ffffff;
  color: var(--kb-primary);
}

.action-btn.primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.action-btn.secondary {
  background: rgba(255,255,255,0.15);
  color: #ffffff;
  border: 1px solid rgba(255,255,255,0.3);
}

.action-btn.secondary:hover {
  background: rgba(255,255,255,0.25);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-title {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.section-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.section-badge {
  padding: 4px 12px;
  border-radius: 12px;
  background: rgba(59,111,224,0.08);
  color: var(--kb-primary);
  font-size: 13px;
  font-weight: 500;
}

.section-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--kb-primary);
  font-weight: 500;
  text-decoration: none;
  transition: opacity 0.2s ease;
}

.section-link:hover {
  opacity: 0.8;
}

.section-hint {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.progress-cards {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

@media (min-width: 640px) {
  .progress-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .progress-cards {
    grid-template-columns: repeat(3, 1fr);
  }
}

.progress-card {
  background: var(--kb-card);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid var(--kb-border);
  transition: all 0.2s ease;
}

.progress-card:hover {
  box-shadow: 0 8px 24px rgba(59,111,224,0.06);
  border-color: rgba(59,111,224,0.2);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.card-header svg {
  color: var(--kb-primary);
}

.card-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
}

.progress-ring-container {
  position: relative;
  width: 80px;
  height: 80px;
  margin-bottom: 16px;
}

.progress-ring {
  transform: rotate(-90deg);
}

.progress-ring-bg {
  fill: none;
  stroke: var(--kb-muted);
  stroke-width: 6;
}

.progress-ring-fill {
  fill: none;
  stroke: var(--kb-primary);
  stroke-width: 6;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.8s ease;
}

.ring-center {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ring-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-primary);
}

.card-body {
  flex: 1;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 4px;
}

.card-subtitle {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin-bottom: 12px;
}

.card-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-primary);
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
}

.streak-display {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 16px;
}

.streak-number {
  font-size: 48px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1;
}

.streak-unit {
  font-size: 16px;
  color: var(--kb-muted-foreground);
}

.streak-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  background: rgba(16,185,129,0.1);
  color: var(--kb-accent);
  font-size: 12px;
  font-weight: 500;
  margin-left: auto;
}

.streak-calendar {
  display: flex;
  justify-content: space-between;
}

.day-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.day-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--kb-muted);
  transition: background 0.2s ease;
}

.day-dot.active {
  background: var(--kb-accent);
}

.day-label {
  font-size: 11px;
  color: var(--kb-muted-foreground);
}

.mastery-display {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.mastery-number {
  font-size: 36px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1;
}

.mastery-unit {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}

.mastery-percent {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-accent);
  margin-bottom: 12px;
}

.mastery-bar {
  height: 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  overflow: hidden;
  margin-bottom: 8px;
}

.mastery-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--kb-primary), var(--kb-accent));
  transition: width 0.8s ease;
}

.mastery-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.todos-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

@media (min-width: 1024px) {
  .todos-grid {
    grid-template-columns: 2fr 1fr;
  }
}

.todos-list {
  background: var(--kb-card);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid var(--kb-border);
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--kb-border);
  transition: background 0.2s ease;
}

.todo-item:last-child {
  border-bottom: none;
}

.todo-item:hover {
  background: var(--kb-muted);
  margin: 0 -24px;
  padding-left: 24px;
  padding-right: 24px;
}

.todo-check {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  border: 2px solid var(--kb-border);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
  transition: all 0.2s ease;
}

.todo-item.done .todo-check {
  background: var(--kb-accent);
  border-color: var(--kb-accent);
}

.todo-check:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 2px;
}

.todo-content {
  flex: 1;
  min-width: 0;
}

.todo-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 2px;
}

.todo-item.done .todo-title {
  text-decoration: line-through;
  color: var(--kb-muted-foreground);
}

.todo-desc {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.todo-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.todo-status.completed {
  color: var(--kb-accent);
}

.todo-status.deadline {
  color: var(--kb-warning);
}

.todo-status.pending {
  color: var(--kb-muted-foreground);
}

.weekly-goal {
  background: var(--kb-card);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid var(--kb-border);
}

.goal-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.goal-header svg {
  color: var(--kb-accent);
}

.goal-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
}

.goal-ring-container {
  position: relative;
  width: 100px;
  height: 100px;
  margin: 0 auto 20px;
}

.goal-ring {
  transform: rotate(-90deg);
}

.goal-ring-bg {
  fill: none;
  stroke: var(--kb-muted);
  stroke-width: 8;
}

.goal-ring-fill {
  fill: none;
  stroke: var(--kb-accent);
  stroke-linecap: round;
  transition: stroke-dashoffset 0.8s ease;
}

.goal-center {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.goal-percent {
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-foreground);
}

.goal-stats {
  font-size: 11px;
  color: var(--kb-muted-foreground);
}

.goal-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.goal-detail {
  display: flex;
  justify-content: space-between;
}

.detail-label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.detail-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-accent);
}

.paths-scroll {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 12px;
  margin-bottom: 24px;
}

.paths-scroll::-webkit-scrollbar {
  display: none;
}

.path-card {
  flex-shrink: 0;
  width: 280px;
  background: var(--kb-card);
  border-radius: 16px;
  border: 1px solid var(--kb-border);
  overflow: hidden;
  text-decoration: none;
  transition: all 0.2s ease;
}

.path-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(59,111,224,0.1);
  border-color: rgba(59,111,224,0.3);
}

.path-cover {
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
}

.path-content {
  padding: 20px;
}

.path-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.path-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 10px;
}

.badge-beginner {
  background: rgba(16,185,129,0.1);
  color: var(--kb-accent);
}

.badge-intermediate {
  background: rgba(59,111,224,0.1);
  color: var(--kb-primary);
}

.badge-advanced {
  background: rgba(245,158,11,0.1);
  color: var(--kb-warning);
}

.path-chapters {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.path-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.path-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  line-height: 1.6;
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.path-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-primary);
}

.progress-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--kb-muted);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--kb-primary);
  transition: width 0.6s ease;
}

.continue-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

@media (min-width: 640px) {
  .continue-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .continue-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.doc-card {
  display: flex;
  flex-direction: column;
  background: var(--kb-card);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid var(--kb-border);
  text-decoration: none;
  transition: all 0.2s ease;
}

.doc-card:hover {
  box-shadow: 0 8px 24px rgba(59,111,224,0.06);
  border-color: rgba(59,111,224,0.2);
}

.doc-thumb {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}

.doc-info {
  flex: 1;
  margin-bottom: 12px;
}

.doc-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}

.doc-category {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.doc-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.doc-time {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.doc-progress {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-primary);
}

.doc-progress-bar {
  height: 4px;
  border-radius: 2px;
  background: var(--kb-muted);
  overflow: hidden;
}

.doc-progress-bar .progress-fill {
  background: var(--kb-primary);
}

.ai-cards {
  background: var(--kb-card);
  border-radius: 16px;
  border: 1px solid var(--kb-border);
  overflow: hidden;
  margin-bottom: 24px;
}

.ai-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--kb-border);
  text-decoration: none;
  transition: all 0.2s ease;
}

.ai-card:last-child {
  border-bottom: none;
}

.ai-card:hover {
  background: rgba(59,111,224,0.04);
}

.ai-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-content {
  flex: 1;
  min-width: 0;
}

.ai-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 4px;
}

.ai-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.ai-action {
  padding: 8px 20px;
  border-radius: 8px;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 13px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ai-action:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.report-section {
  display: flex;
  justify-content: center;
  margin-bottom: 32px;
}

.report-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: 10px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  color: var(--kb-primary);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s ease;
}

.report-btn:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  gap: 12px;
  color: var(--kb-muted-foreground);
}

.empty-state svg {
  opacity: 0.5;
}

.empty-state-link {
  font-size: 14px;
  color: var(--kb-primary);
  text-decoration: none;
  transition: opacity 0.15s ease;
}
.empty-state-link:hover {
  opacity: 0.8;
  text-decoration: underline;
}
</style>
