<template>
  <!-- 学习路径详情页：面包屑 + 路径 Hero + 数据看板 + 依赖图谱 + 章节时间轴 -->
  <div class="path-detail-page animate-fade-in" :style="themeVars">
    <!-- ===== 面包屑 ===== -->
    <nav class="breadcrumb">
      <router-link to="/learning/center" class="crumb-link">学习中心</router-link>
      <Icon name="chevron-right" :size="14" class="crumb-sep" />
      <router-link to="/learning/paths" class="crumb-link">学习路径</router-link>
      <Icon name="chevron-right" :size="14" class="crumb-sep" />
      <span class="crumb-current">{{ currentPath?.title || '加载中...' }}</span>
    </nav>

    <!-- ===== 路径 Hero 卡片 ===== -->
    <section v-if="currentPath" class="hero-card">
      <!-- 顶部主题色装饰条与背景光晕 -->
      <span class="hero-accent"></span>
      <span class="hero-glow"></span>

      <div class="hero-body">
        <!-- 左：图标盒 -->
        <div class="hero-icon">
          <Icon :name="getPathIconName(currentPath.icon)" :size="34" />
        </div>

        <!-- 中：信息区 -->
        <div class="hero-info">
          <div class="tag-row">
            <span class="tag tag-difficulty" :style="getDifficultyTagStyle(currentPath.difficulty)">
              {{ getDifficultyLabel(currentPath.difficulty) }}
            </span>
            <span v-if="isFinished" class="tag tag-status-done">
              <Icon name="check" :size="11" />已完成
            </span>
            <span v-else-if="hasStarted" class="tag tag-status-learning">学习中</span>
            <span v-else-if="!isEnrolled" class="tag tag-status-not-enrolled">
              <Icon name="user-plus" :size="11" />未报名
            </span>
            <span v-else class="tag tag-status-not-started">未开始</span>
          </div>

          <h1 class="hero-title">{{ currentPath.title }}</h1>
          <p class="hero-desc">{{ currentPath.description || '暂无描述' }}</p>

          <div class="hero-chips">
            <span class="chip">
              <Icon name="book-open" :size="13" />
              <span>{{ currentPath.chaptersCount }} 章节</span>
            </span>
            <span class="chip">
              <Icon name="clock" :size="13" />
              <span>{{ formatDuration(currentPath.totalDuration) }}</span>
            </span>
            <span class="chip">
              <Icon name="users" :size="13" />
              <span>{{ formatCount(currentPath.enrolledCount) }} 人学习</span>
            </span>
            <span class="chip chip-rating">
              <Icon name="star" :size="13" />
              <span>{{ getRating(currentPath.id) }} 评分</span>
            </span>
          </div>
        </div>

        <!-- 右：环形进度 + 主行动按钮 -->
        <div class="hero-action">
          <div class="progress-ring">
            <svg viewBox="0 0 120 120" class="ring-svg">
              <circle class="ring-track" cx="60" cy="60" r="52" />
              <circle
                class="ring-fill"
                cx="60"
                cy="60"
                r="52"
                :stroke-dasharray="RING_CIRCUMFERENCE"
                :stroke-dashoffset="ringOffset"
              />
            </svg>
            <div class="ring-center">
              <span class="ring-num">{{ currentPath.progress }}<i>%</i></span>
              <span class="ring-label">{{ completedChaptersCount }}/{{ pathChapters.length }} 章</span>
            </div>
          </div>
          <button
            class="primary-btn"
            :class="{ 'enroll-btn': !isEnrolled, 'is-loading': enrolling }"
            :disabled="enrolling"
            @click="isEnrolled ? continueLearning() : handleEnroll()"
          >
            <Icon :name="!isEnrolled ? 'user-plus' : 'play'" :size="14" />
            <span>{{ enrolling ? '报名中…' : ctaText }}</span>
          </button>
          <router-link
            v-if="isFinished && certificateId"
            :to="`/certificate/${certificateId}`"
            class="cert-btn"
          >
            <Icon name="award" :size="14" />
            <span>查看证书</span>
          </router-link>
        </div>
      </div>
    </section>

    <!-- 加载态 -->
    <div v-else class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text">加载中...</p>
    </div>

    <!-- ===== 未报名引导横幅 ===== -->
    <div v-if="currentPath && !isEnrolled" class="enroll-banner">
      <Icon name="lock" :size="16" class="enroll-banner-icon" />
      <span class="enroll-banner-text">
        报名后即可解锁全部章节开始学习
      </span>
      <button class="enroll-banner-btn" :disabled="enrolling" @click="handleEnroll">
        <Icon name="user-plus" :size="13" />
        <span>{{ enrolling ? '报名中…' : '立即报名' }}</span>
      </button>
    </div>

    <!-- ===== 数据看板 ===== -->
    <div v-if="currentPath" class="metric-row">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <span class="metric-icon" :class="metric.tone">
          <Icon :name="metric.icon" :size="16" />
        </span>
        <span class="metric-text">
          <span class="metric-value">{{ metric.value }}</span>
          <span class="metric-label">{{ metric.label }}</span>
        </span>
      </div>
    </div>

    <!-- ===== 章节依赖关系图（DAG） ===== -->
    <section v-if="currentPath" class="panel dag-panel">
      <header class="panel-header">
        <h2 class="panel-title">
          <Icon name="git-branch" :size="17" class="panel-icon" />
          学习路径图谱
        </h2>
        <span class="panel-meta">基于章节前置依赖自动编排，点击节点直达章节</span>
      </header>

      <div v-if="dagLoading" class="state-area">
        <div class="loading-spinner"></div>
        <p class="state-text">图谱生成中...</p>
      </div>

      <div v-else-if="dagError" class="panel-error">
        <Icon name="alert-circle" :size="16" />
        <span>{{ dagError }}</span>
      </div>

      <div v-else class="panel-body">
        <DagGraph :data="dag" :height="420" :active-id="currentChapterId" @node-click="handleDagNodeClick" />
        <p v-if="dag && dag.nodes.length > 0 && !dagHasEdges" class="panel-hint">
          <Icon name="info" :size="13" />
          当前路径为线性顺序，暂无显式前置依赖分支。
        </p>
      </div>
    </section>

    <!-- ===== 章节时间轴 ===== -->
    <section v-if="currentPath" class="panel">
      <header class="panel-header">
        <h2 class="panel-title">
          <Icon name="list" :size="17" class="panel-icon" />
          章节列表
        </h2>
        <span class="panel-meta">已完成 {{ completedChaptersCount }} / {{ pathChapters.length }} 章</span>
      </header>

      <div class="timeline">
        <div
          v-for="(chapter, index) in pathChapters"
          :key="chapter.id"
          class="timeline-item"
          :class="{
            completed: chapter.completed,
            current: chapter.isCurrent,
            locked: isChapterLocked(chapter),
          }"
          :style="{ animationDelay: `${Math.min(index, 12) * 40}ms` }"
          @click="handleChapterClick(chapter)"
        >
          <!-- 左侧轴线与状态点 -->
          <div class="timeline-rail">
            <span class="rail-line rail-top" :class="{ hidden: index === 0 }"></span>
            <span class="rail-dot" :class="getChapterStatusClass(chapter)">
              <Icon v-if="chapter.completed" name="check" :size="15" />
              <Icon v-else-if="chapter.isCurrent" name="play" :size="13" />
              <Icon v-else-if="isChapterLocked(chapter)" name="lock" :size="13" />
              <span v-else class="rail-order">{{ chapter.order }}</span>
            </span>
            <span class="rail-line rail-bottom" :class="{ hidden: index === pathChapters.length - 1 }"></span>
          </div>

          <!-- 右侧章节卡片 -->
          <div class="timeline-card">
            <div class="tl-head">
              <span class="tl-order">第 {{ pad(chapter.order) }} 章</span>
              <h4 class="tl-title">{{ chapter.title }}</h4>
              <span v-if="chapter.isCurrent" class="tl-badge">当前章节</span>
              <span v-else-if="chapter.completed" class="tl-badge tl-badge-done">已完成</span>
            </div>
            <p v-if="chapter.description" class="tl-desc">{{ chapter.description }}</p>
            <div class="tl-foot">
              <span class="tl-meta">
                <Icon name="clock" :size="12" />
                <span>{{ formatChapterDuration(chapter.duration) }}</span>
              </span>
              <span v-if="chapter.prerequisiteTitles.length" class="tl-meta">
                <Icon name="git-branch" :size="12" />
                <span>前置：{{ chapter.prerequisiteTitles.join('、') }}</span>
              </span>
            </div>
          </div>

          <Icon
            :name="isChapterLocked(chapter) ? 'lock' : 'chevron-right'"
            :size="16"
            class="tl-arrow"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 学习路径详情页：Hero（图标 + 标签 + 标题 + 描述 + 环形进度 + CTA）、数据看板、
// DAG 依赖图谱（dagre 布局，可拖拽缩放）、章节时间轴（三态）。
import { computed, ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import DagGraph from '@/components/Learning/DagGraph.vue';
import { learningApi } from '@/api';
import { getApiError, notify } from '@/utils/toast';
import type { LearningPathVO, LearningChapterVO, ChapterDagVO, ChapterNodeVO } from '@/api/types';

const route = useRoute();
const router = useRouter();

const pathId = computed(() => Number(route.params.id));
const pathDetail = ref<LearningPathVO | null>(null);
const chapters = ref<LearningChapterVO[]>([]);

// L-PATH-03 DAG 可视化：章节依赖关系图
const dag = ref<ChapterDagVO | null>(null);
const dagLoading = ref(false);
const dagError = ref('');
const dagHasEdges = computed(() => (dag.value?.edges?.length || 0) > 0);

/** 环形进度条周长：2πr（r = 52）。 */
const RING_CIRCUMFERENCE = 2 * Math.PI * 52;

// 后端 level 文案 → 统一难度枚举
function levelToDifficulty(level?: string): 'beginner' | 'intermediate' | 'advanced' {
  const map: Record<string, 'beginner' | 'intermediate' | 'advanced'> = {
    入门: 'beginner',
    进阶: 'intermediate',
    高级: 'advanced',
    beginner: 'beginner',
    intermediate: 'intermediate',
    advanced: 'advanced',
  };
  return map[level || ''] || 'beginner';
}

// ===== 视图模型 =====
interface ViewChapter {
  id: number;
  title: string;
  description?: string;
  duration: number;
  order: number;
  completed: boolean;
  isCurrent: boolean;
  unlocked: boolean;
  /** 前置章节标题，用于时间轴展示依赖关系。 */
  prerequisiteTitles: string[];
}

// 章节按 sortOrder 升序排序，并映射为视图模型
const pathChapters = computed<ViewChapter[]>(() => {
  const sorted = chapters.value.slice().sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  const titleById = new Map(sorted.map((c) => [c.id, c.title]));
  // 找到第一个未完成章节作为「当前章节」
  const firstIncompleteIdx = sorted.findIndex((c) => !c.completed);
  return sorted.map((c, idx) => ({
    id: c.id,
    title: c.title,
    description: c.description,
    duration: c.duration || 0,
    order: c.sortOrder || idx + 1,
    completed: !!c.completed,
    isCurrent: idx === firstIncompleteIdx,
    // 已完成或当前章节或之前的章节都视为已解锁
    unlocked: idx <= (firstIncompleteIdx === -1 ? sorted.length - 1 : firstIncompleteIdx),
    prerequisiteTitles: parsePrerequisiteIds(c.prerequisiteChapterIds)
      .map((id) => titleById.get(id))
      .filter((t): t is string => !!t),
  }));
});

/** 解析逗号分隔的前置章节 ID 串。 */
function parsePrerequisiteIds(raw?: string): number[] {
  if (!raw) return [];
  return raw
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n) && n > 0);
}

const completedChaptersCount = computed(() => pathChapters.value.filter((c) => c.completed).length);
/** 当前应学章节 ID，用于图谱高亮。 */
const currentChapterId = computed(() => pathChapters.value.find((c) => c.isCurrent)?.id ?? null);
/** 未完成章节的剩余总时长（分钟）。 */
const remainingDuration = computed(() =>
  pathChapters.value.filter((c) => !c.completed).reduce((sum, c) => sum + c.duration, 0),
);

const currentPath = computed(() => {
  if (!pathDetail.value) return null;
  const total = pathChapters.value.length;
  const progress = total > 0 ? Math.round((completedChaptersCount.value / total) * 100) : 0;
  return {
    id: pathDetail.value.id,
    title: pathDetail.value.title,
    description: pathDetail.value.description,
    icon: pickIcon(pathDetail.value.id),
    difficulty: levelToDifficulty(pathDetail.value.level),
    chaptersCount: pathDetail.value.chapterCount || total,
    totalDuration: pathDetail.value.totalDuration || 0,
    progress,
    enrolledCount: pathDetail.value.enrolledCount || 0,
    enrolled: !!pathDetail.value.enrolled,
  };
});

const hasStarted = computed(() => (currentPath.value?.progress || 0) > 0);
const isFinished = computed(() => (currentPath.value?.progress || 0) >= 100);
/** 当前用户是否已报名（未报名时章节锁定、主按钮为「报名学习」）。 */
const isEnrolled = computed(() => currentPath.value?.enrolled ?? false);
/** 报名请求进行中，防止重复点击。 */
const enrolling = ref(false);
const ctaText = computed(() => {
  if (isFinished.value) return '复习路径';
  if (!isEnrolled.value) return '报名学习';
  return hasStarted.value ? '继续学习' : '开始学习';
});

/** G-CERT-01 当前路径已颁发证书的 ID（仅完成时展示查看证书入口）。 */
const certificateId = ref<number | null>(null);

// 路径完成时查询该路径对应的证书
const loadCertificate = async (pathId: number) => {
  try {
    const list = await learningApi.certificates();
    const mine = list.find((c) => c.pathId === pathId);
    certificateId.value = mine?.id ?? null;
  } catch {
    certificateId.value = null;
  }
};

/** 环形进度偏移量：进度越高偏移越小。 */
const ringOffset = computed(() => {
  const progress = currentPath.value?.progress || 0;
  return RING_CIRCUMFERENCE * (1 - progress / 100);
});

// ===== 数据看板 =====
interface Metric {
  icon: string;
  label: string;
  value: string;
  tone: string;
}
const metrics = computed<Metric[]>(() => [
  {
    icon: 'check-circle',
    label: '已完成章节',
    value: `${completedChaptersCount.value} / ${pathChapters.value.length}`,
    tone: 'tone-green',
  },
  { icon: 'clock', label: '剩余时长', value: formatDuration(remainingDuration.value), tone: 'tone-blue' },
  {
    icon: 'layers',
    label: '总时长',
    value: formatDuration(currentPath.value?.totalDuration || 0),
    tone: 'tone-purple',
  },
  {
    icon: 'trending-up',
    label: '完成度',
    value: `${currentPath.value?.progress || 0}%`,
    tone: 'tone-orange',
  },
]);

// ===== 图标分配 =====
function pickIcon(id: number): string {
  const icons = ['layout', 'server', 'brain', 'database', 'cloud', 'smartphone'];
  return icons[id % icons.length] || 'code';
}

function getPathIconName(iconName: string): string {
  const map: Record<string, string> = {
    code: 'code',
    fileCode: 'file-code',
    brain: 'brain',
    layers: 'layers',
    server: 'server',
    puzzle: 'puzzle',
    layout: 'layout',
    database: 'database',
    cloud: 'cloud',
    smartphone: 'smartphone',
  };
  return map[iconName] || 'code';
}

// ===== 主题色（与 LearningPaths 保持一致，按路径 id 取模） =====
/** RGB 三元组字符串，供 rgba(var(--theme-rgb), a) 使用。 */
const THEME_RGB_LIST = ['59,111,224', '16,185,129', '168,85,247', '245,158,11', '239,68,68', '14,165,233'];

const themeVars = computed<Record<string, string>>(() => {
  const id = currentPath.value?.id ?? 0;
  const rgb = THEME_RGB_LIST[id % THEME_RGB_LIST.length] || THEME_RGB_LIST[0];
  return { '--theme-rgb': rgb, '--theme': `rgb(${rgb})` };
});

// ===== 难度相关 =====
function getDifficultyLabel(difficulty: string): string {
  const labels: Record<string, string> = {
    beginner: '入门',
    intermediate: '进阶',
    advanced: '高级',
  };
  return labels[difficulty] || difficulty;
}

function getDifficultyTagStyle(difficulty: string): Record<string, string> {
  switch (difficulty) {
    case 'beginner':
      return { background: 'rgba(16,185,129,0.10)', color: 'var(--kb-accent)' };
    case 'intermediate':
      return { background: 'rgba(59,111,224,0.10)', color: 'var(--kb-primary)' };
    case 'advanced':
      return { background: 'rgba(239,68,68,0.10)', color: 'var(--kb-destructive)' };
    default:
      return { background: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' };
  }
}

// ===== 章节状态辅助 =====
function getChapterStatusClass(chapter: ViewChapter): string {
  if (chapter.completed) return 'status-completed';
  if (chapter.isCurrent) return 'status-current';
  return 'status-pending';
}

function isChapterLocked(chapter: ViewChapter): boolean {
  // 未报名时所有章节均锁定，报名后按前置/进度解锁
  if (!isEnrolled.value) return true;
  return !chapter.completed && !chapter.isCurrent && !chapter.unlocked;
}

function handleChapterClick(chapter: ViewChapter): void {
  if (isChapterLocked(chapter)) {
    if (!isEnrolled.value) notify('请先报名学习路径，解锁全部章节', 'warning');
    return;
  }
  router.push(`/learning/chapter/${chapter.id}`);
}

/** 图谱节点点击：未报名或未解锁节点不跳转。 */
function handleDagNodeClick(node: ChapterNodeVO): void {
  if (node.status === 'locked') {
    if (!isEnrolled.value) notify('请先报名学习路径，解锁全部章节', 'warning');
    return;
  }
  router.push(`/learning/chapter/${node.id}`);
}

// ===== 格式化 =====
function formatDuration(minutes: number): string {
  if (!minutes) return '0 小时';
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins > 0 ? `${hours} 小时${mins} 分` : `${hours} 小时`;
}

function formatChapterDuration(minutes: number): string {
  if (!minutes) return '-';
  if (minutes < 60) return `${minutes}分钟`;
  const hours = minutes / 60;
  return `${hours % 1 === 0 ? hours : hours.toFixed(1)}h`;
}

function formatCount(n: number): string {
  if (!n) return '0';
  return n.toLocaleString('en-US');
}

// 评分（后端无字段，使用基于 id 的稳定值 4.5-4.9）
function getRating(id: number): string {
  const ratings = [4.9, 4.8, 4.9, 4.7, 4.6, 4.5];
  return ratings[id % ratings.length].toFixed(1);
}

// 数字补零（1 → "01"）
function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

// ===== 报名与跳转 =====
/** 报名学习路径：成功后刷新报名状态、解锁章节，主按钮切换为「开始学习」。 */
async function handleEnroll(): Promise<void> {
  if (enrolling.value) return;
  enrolling.value = true;
  try {
    await learningApi.enroll(pathId.value);
    notify('报名成功，开始你的学习之旅吧！', 'success');
    // 重新拉取详情，刷新 enrolled 与进度
    pathDetail.value = await learningApi.pathDetail(pathId.value);
  } catch (e) {
    notify(getApiError(e, '报名失败'), 'error');
  } finally {
    enrolling.value = false;
  }
}

// 跳转至第一个未完成章节（无则跳首章）
function continueLearning(): void {
  if (!isEnrolled.value) {
    notify('请先报名学习路径，解锁全部章节', 'warning');
    return;
  }
  const firstIncomplete = pathChapters.value.find((c) => !c.completed);
  const target = firstIncomplete || pathChapters.value[0];
  if (target) router.push(`/learning/chapter/${target.id}`);
}

// ===== 数据加载 =====
onMounted(async () => {
  try {
    pathDetail.value = await learningApi.pathDetail(pathId.value);
  } catch (e) {
    console.warn(getApiError(e, '路径加载失败'));
    pathDetail.value = null;
  }
  try {
    chapters.value = await learningApi.chapters(pathId.value);
  } catch (e) {
    console.warn(getApiError(e, '章节加载失败'));
    chapters.value = [];
  }
  // L-PATH-03 DAG：章节依赖关系图
  dagLoading.value = true;
  try {
    dag.value = await learningApi.dag(pathId.value);
  } catch (e) {
    console.warn(getApiError(e, '图谱加载失败'));
    dagError.value = getApiError(e, '图谱加载失败');
  } finally {
    dagLoading.value = false;
  }
  // G-CERT-01 路径完成时加载证书入口
  await loadCertificate(pathId.value);
});
</script>

<style scoped>
/* ===== 页面容器 ===== */
.path-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 面包屑 ===== */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.crumb-link {
  font-weight: 500;
  color: var(--kb-muted-foreground);
  text-decoration: none;
  transition: color 0.15s;
}
.crumb-link:hover { color: var(--kb-primary); }
.crumb-sep { color: var(--kb-muted-foreground); }
.crumb-current {
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
}

/* ===== Hero 卡片 ===== */
.hero-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background:
    linear-gradient(135deg, rgba(var(--theme-rgb), 0.09), rgba(var(--theme-rgb), 0.01) 60%),
    var(--kb-card);
  box-shadow: var(--shadow-card);
}
.hero-accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, rgba(var(--theme-rgb), 1), rgba(var(--theme-rgb), 0.25));
}
/* 右上角柔和光晕，增加层次 */
.hero-glow {
  position: absolute;
  top: -120px;
  right: -80px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(var(--theme-rgb), 0.16), transparent 70%);
  pointer-events: none;
}

.hero-body {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 22px;
  padding: 26px 28px;
}

.hero-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68px;
  height: 68px;
  flex-shrink: 0;
  border-radius: var(--kb-radius-lg);
  color: #fff;
  background: linear-gradient(140deg, rgba(var(--theme-rgb), 1), rgba(var(--theme-rgb), 0.72));
  box-shadow: 0 8px 20px rgba(var(--theme-rgb), 0.28);
}

.hero-info {
  flex: 1;
  min-width: 0;
}
.tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 999px;
}
.tag-status-learning {
  background: rgba(16, 185, 129, 0.10);
  color: var(--kb-accent);
}
.tag-status-done {
  background: rgba(16, 185, 129, 0.14);
  color: var(--kb-accent);
}
.tag-status-not-started {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
/* 未报名标签：中性灰蓝，弱化提示「先报名再学习」 */
.tag-status-not-enrolled {
  background: rgba(100, 116, 139, 0.12);
  color: #64748b;
}

.hero-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}
.hero-desc {
  font-size: 14px;
  line-height: 1.65;
  color: var(--kb-muted-foreground);
  margin-bottom: 14px;
  max-width: 62ch;
}

.hero-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid var(--kb-border);
  background: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.chip-rating { color: var(--kb-warning); }

/* 右侧：环形进度 + CTA */
.hero-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.progress-ring {
  position: relative;
  width: 116px;
  height: 116px;
}
.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}
.ring-track {
  fill: none;
  stroke: rgba(var(--theme-rgb), 0.12);
  stroke-width: 9;
}
.ring-fill {
  fill: none;
  stroke: var(--theme);
  stroke-width: 9;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}
.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.ring-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  color: var(--theme);
  font-variant-numeric: tabular-nums;
}
.ring-num i {
  font-style: normal;
  font-size: 13px;
  font-weight: 600;
  margin-left: 1px;
}
.ring-label {
  font-size: 11px;
  color: var(--kb-muted-foreground);
}

.primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  min-width: 132px;
  height: 40px;
  padding: 0 20px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  font-weight: 500;
  background: var(--theme);
  color: #fff;
  border: none;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(var(--theme-rgb), 0.26);
  transition: transform 0.15s, box-shadow 0.15s, opacity 0.15s;
}
.primary-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(var(--theme-rgb), 0.32);
}
.primary-btn:active { transform: translateY(0); }
/* 未报名态主按钮：描边样式弱化「待办」语义，区别于实色「开始学习」 */
.primary-btn.enroll-btn {
  background: transparent;
  color: var(--theme);
  border: 1.5px solid var(--theme);
  box-shadow: none;
}
.primary-btn.enroll-btn:hover {
  background: rgba(var(--theme-rgb), 0.08);
  box-shadow: 0 6px 16px rgba(var(--theme-rgb), 0.18);
}
.primary-btn.is-loading {
  cursor: not-allowed;
  opacity: 0.7;
}

/* G-CERT-01 证书入口按钮 */
.cert-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  min-width: 132px;
  height: 40px;
  margin-top: 8px;
  padding: 0 20px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  font-weight: 500;
  background: rgba(245, 185, 64, 0.15);
  color: #b8860b;
  border: 1px solid rgba(245, 185, 64, 0.4);
  cursor: pointer;
  text-decoration: none;
  transition: background 0.15s, transform 0.15s;
}
.cert-btn:hover {
  background: rgba(245, 185, 64, 0.24);
  transform: translateY(-1px);
}

/* ===== 未报名引导横幅 ===== */
.enroll-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-radius: var(--kb-radius-md);
  border: 1px dashed rgba(var(--theme-rgb), 0.45);
  background: rgba(var(--theme-rgb), 0.06);
  color: var(--kb-muted-foreground);
}
.enroll-banner-icon {
  color: var(--theme);
  flex-shrink: 0;
}
.enroll-banner-text {
  flex: 1;
  font-size: 13px;
  line-height: 1.5;
}
.enroll-banner-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border: none;
  border-radius: var(--kb-radius-sm);
  background: var(--theme);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 3px 10px rgba(var(--theme-rgb), 0.26);
  transition: transform 0.15s, box-shadow 0.15s, opacity 0.15s;
}
.enroll-banner-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(var(--theme-rgb), 0.32);
}
.enroll-banner-btn:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

/* ===== 数据看板 ===== */
.metric-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.metric-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  transition: transform 0.18s, box-shadow 0.18s;
}
.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card-hover);
}
.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kb-radius-sm);
  flex-shrink: 0;
}
.tone-green { background: rgba(16, 185, 129, 0.10); color: var(--kb-accent); }
.tone-blue { background: rgba(59, 111, 224, 0.10); color: var(--kb-primary); }
.tone-purple { background: rgba(168, 85, 247, 0.10); color: #A855F7; }
.tone-orange { background: rgba(245, 158, 11, 0.10); color: var(--kb-warning); }
.metric-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.metric-value {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--kb-foreground);
  font-variant-numeric: tabular-nums;
}
.metric-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* ===== 通用面板 ===== */
.panel {
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 24px;
  border-bottom: 1px solid var(--kb-border);
}
.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.01em;
  color: var(--kb-foreground);
  margin: 0;
}
.panel-icon { color: var(--theme); }
.panel-meta {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  text-align: right;
}
.panel-body { padding: 16px 24px 20px; }
.panel-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.panel-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px;
  font-size: 13px;
  color: var(--kb-destructive);
}

/* ===== 加载 / 空态 ===== */
.state-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 24px;
  gap: 12px;
}
.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--kb-muted);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.state-text {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* ===== 章节时间轴 ===== */
.timeline {
  display: flex;
  flex-direction: column;
  padding: 8px 24px 16px;
}
.timeline-item {
  display: flex;
  align-items: stretch;
  gap: 16px;
  cursor: pointer;
  opacity: 0;
  animation: tlIn 0.35s ease-out forwards;
}
@keyframes tlIn {
  from { opacity: 0; transform: translateX(-8px); }
  to { opacity: 1; transform: translateX(0); }
}
.timeline-item.locked { cursor: not-allowed; }

/* 左侧轴 */
.timeline-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 34px;
  flex-shrink: 0;
}
.rail-line {
  width: 2px;
  flex: 1;
  min-height: 10px;
  background: var(--kb-border);
}
.rail-line.hidden { background: transparent; }
.timeline-item.completed .rail-line { background: rgba(16, 185, 129, 0.35); }
.rail-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  flex-shrink: 0;
  color: #fff;
  transition: transform 0.18s, box-shadow 0.18s;
}
.rail-dot.status-completed { background: var(--kb-accent); }
.rail-dot.status-current {
  background: var(--theme);
  box-shadow: 0 0 0 4px rgba(var(--theme-rgb), 0.16);
}
.rail-dot.status-pending {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.timeline-item:hover:not(.locked) .rail-dot { transform: scale(1.08); }
.rail-order {
  font-size: 12px;
  font-weight: 600;
}

/* 右侧卡片 */
.timeline-card {
  flex: 1;
  min-width: 0;
  margin: 6px 0;
  padding: 12px 14px;
  border-radius: var(--kb-radius-md);
  border: 1px solid transparent;
  transition: background 0.18s, border-color 0.18s, transform 0.18s;
}
.timeline-item:hover:not(.locked) .timeline-card {
  background: rgba(var(--theme-rgb), 0.04);
  border-color: rgba(var(--theme-rgb), 0.18);
  transform: translateX(2px);
}
.timeline-item.current .timeline-card {
  background: rgba(var(--theme-rgb), 0.06);
  border-color: rgba(var(--theme-rgb), 0.24);
}
.timeline-item.locked .timeline-card { opacity: 0.6; }

.tl-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tl-order {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.timeline-item.current .tl-order { color: var(--theme); font-weight: 500; }
.tl-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--kb-foreground);
  margin: 0;
}
.timeline-item.current .tl-title { color: var(--theme); }
.tl-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(var(--theme-rgb), 0.10);
  color: var(--theme);
}
.tl-badge-done {
  background: rgba(16, 185, 129, 0.10);
  color: var(--kb-accent);
}
.tl-desc {
  font-size: 12px;
  line-height: 1.55;
  color: var(--kb-muted-foreground);
  margin-top: 5px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tl-foot {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.tl-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.tl-arrow {
  align-self: center;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
  transition: transform 0.18s, color 0.18s;
}
.timeline-item:hover:not(.locked) .tl-arrow {
  color: var(--theme);
  transform: translateX(3px);
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .metric-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .hero-body {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    padding: 20px 16px;
  }
  .hero-icon { width: 56px; height: 56px; }
  .hero-action { flex-direction: row; align-items: center; justify-content: space-between; }
  .primary-btn { width: auto; flex: 1; }
}
@media (max-width: 640px) {
  .hero-title { font-size: 22px; }
  .progress-ring { width: 92px; height: 92px; }
  .ring-num { font-size: 20px; }
  .panel-header { padding: 12px 16px; }
  .panel-title { font-size: 16px; }
  .panel-meta { display: none; }
  .panel-body { padding: 12px 16px 16px; }
  .timeline { padding: 8px 12px 12px; }
  .timeline-item { gap: 10px; }
  .tl-arrow { display: none; }
}
</style>
