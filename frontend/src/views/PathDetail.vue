<template>
  <!-- 学习路径详情页：面包屑 + 路径头卡片 + 章节列表 -->
  <div class="path-detail-page animate-fade-in">
    <!-- ===== 面包屑 ===== -->
    <nav class="breadcrumb">
      <router-link to="/learning/center" class="crumb-link">学习中心</router-link>
      <Icon name="chevron-right" :size="14" class="crumb-sep" />
      <router-link to="/learning/paths" class="crumb-link">学习路径</router-link>
      <Icon name="chevron-right" :size="14" class="crumb-sep" />
      <span class="crumb-current">{{ currentPath?.title || '加载中...' }}</span>
    </nav>

    <!-- ===== 路径头卡片 ===== -->
    <div v-if="currentPath" class="path-header-card">
      <div class="path-header-body">
        <!-- 左侧：图标盒 -->
        <div class="path-icon-box" :style="getIconBoxStyle(currentPath.id)">
          <Icon :name="getPathIconName(currentPath.icon)" :size="40" :style="{ color: getThemeColor(currentPath.id) }" />
        </div>
        <!-- 右侧：信息区 -->
        <div class="path-info">
          <!-- 标签行 -->
          <div class="tag-row">
            <span class="tag tag-difficulty" :style="getDifficultyTagStyle(currentPath.difficulty)">
              {{ getDifficultyLabel(currentPath.difficulty) }}
            </span>
            <span v-if="hasStarted" class="tag tag-status-learning">学习中</span>
            <span v-else class="tag tag-status-not-started">未开始</span>
          </div>
          <!-- 标题 -->
          <h1 class="path-title">{{ currentPath.title }}</h1>
          <!-- 描述 -->
          <p class="path-desc">{{ currentPath.description || '暂无描述' }}</p>
          <!-- 统计行 -->
          <div class="path-stats">
            <span class="stat-item">
              <Icon name="book-open" :size="14" />
              <span>{{ currentPath.chaptersCount }} 章节</span>
            </span>
            <span class="stat-item">
              <Icon name="clock" :size="14" />
              <span>{{ formatDuration(currentPath.totalDuration) }}</span>
            </span>
            <span class="stat-item">
              <Icon name="users" :size="14" />
              <span>{{ formatCount(currentPath.enrolledCount) }} 人学习</span>
            </span>
            <span class="stat-item stat-rating">
              <Icon name="star" :size="14" :fill="true" />
              <span>{{ getRating(currentPath.id) }} 评分</span>
            </span>
          </div>
          <!-- 进度条 + 继续学习按钮 -->
          <div class="path-action-row">
            <div class="progress-block">
              <div class="progress-meta">
                <span class="progress-label">学习进度</span>
                <span class="progress-value" :style="{ color: getThemeColor(currentPath.id) }">
                  {{ completedChaptersCount }}/{{ currentPath.chaptersCount }} 章 ({{ currentPath.progress }}%)
                </span>
              </div>
              <div class="progress-track">
                <div
                  class="progress-fill"
                  :style="{ width: `${currentPath.progress}%`, background: getThemeColor(currentPath.id) }"
                ></div>
              </div>
            </div>
            <button class="primary-btn" @click="continueLearning">
              <Icon name="play" :size="14" />
              <span>{{ hasStarted ? '继续学习' : '开始学习' }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-else class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text">加载中...</p>
    </div>

    <!-- ===== 章节列表 ===== -->
    <div v-if="currentPath" class="chapter-card">
      <!-- 卡片头 -->
      <div class="chapter-card-header">
        <h2 class="chapter-card-title">章节列表</h2>
        <span class="chapter-card-meta">已完成 {{ completedChaptersCount }} / {{ pathChapters.length }} 章</span>
      </div>
      <!-- 章节项 -->
      <div class="chapter-list">
        <div
          v-for="chapter in pathChapters"
          :key="chapter.id"
          class="chapter-item"
          :class="{
            completed: chapter.completed,
            current: chapter.isCurrent,
            locked: !chapter.completed && !chapter.isCurrent && !chapter.unlocked,
          }"
          @click="handleChapterClick(chapter)"
        >
          <!-- 左侧圆形状态图标 -->
          <div class="chapter-status-icon" :class="getChapterStatusClass(chapter)">
            <Icon v-if="chapter.completed" name="check" :size="16" />
            <Icon v-else-if="chapter.isCurrent" name="play" :size="14" />
            <span v-else class="chapter-order">{{ chapter.order }}</span>
          </div>
          <!-- 中间：标题与描述 -->
          <div class="chapter-info">
            <div class="chapter-title-row">
              <span class="chapter-order-label">第 {{ pad(chapter.order) }} 章</span>
              <h4 class="chapter-title">{{ chapter.title }}</h4>
              <span v-if="chapter.isCurrent" class="current-badge">当前章节</span>
            </div>
            <p v-if="chapter.description" class="chapter-desc">{{ chapter.description }}</p>
          </div>
          <!-- 右侧：时长 + 状态图标 -->
          <span class="chapter-duration">
            <Icon name="clock" :size="14" />
            <span>{{ formatChapterDuration(chapter.duration) }}</span>
          </span>
          <Icon
            :name="isChapterLocked(chapter) ? 'lock' : 'chevron-right'"
            :size="16"
            class="chapter-arrow"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 学习路径详情页：路径头卡片（图标盒 + 标签 + 标题 + 描述 + 统计 + 进度 + 按钮）+ 章节列表（三态）。
import { computed, ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import { getApiError } from '@/utils/toast';
import type { LearningPathVO, LearningChapterVO } from '@/api/types';

const route = useRoute();
const router = useRouter();

const pathId = computed(() => Number(route.params.id));
const pathDetail = ref<LearningPathVO | null>(null);
const chapters = ref<LearningChapterVO[]>([]);

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
}

// 章节按 sortOrder 升序排序，并映射为视图模型
const pathChapters = computed<ViewChapter[]>(() => {
  // 找到第一个未完成章节作为「当前章节」
  const sorted = chapters.value.slice().sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
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
  }));
});

const completedChaptersCount = computed(() => pathChapters.value.filter((c) => c.completed).length);

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
  };
});

const hasStarted = computed(() => (currentPath.value?.progress || 0) > 0);

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

// ===== 主题色与渐变（与 LearningPaths 保持一致） =====
const themeColors = [
  'var(--kb-primary)',
  'var(--kb-accent)',
  '#A855F7',
  'var(--kb-warning)',
  'var(--kb-destructive)',
  '#0EA5E9',
];
function getThemeColor(id: number): string {
  return themeColors[id % themeColors.length] || themeColors[0];
}

const iconBoxGradients = [
  'linear-gradient(135deg, rgba(59,111,224,0.12), rgba(59,111,224,0.04))',
  'linear-gradient(135deg, rgba(16,185,129,0.12), rgba(16,185,129,0.04))',
  'linear-gradient(135deg, rgba(168,85,247,0.12), rgba(168,85,247,0.04))',
  'linear-gradient(135deg, rgba(245,158,11,0.12), rgba(245,158,11,0.04))',
  'linear-gradient(135deg, rgba(239,68,68,0.12), rgba(239,68,68,0.04))',
  'linear-gradient(135deg, rgba(14,165,233,0.12), rgba(14,165,233,0.04))',
];
function getIconBoxStyle(id: number): Record<string, string> {
  return { background: iconBoxGradients[id % iconBoxGradients.length] || iconBoxGradients[0] };
}

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
      return { background: 'rgba(16,185,129,0.08)', color: 'var(--kb-accent)' };
    case 'intermediate':
      return { background: 'rgba(59,111,224,0.08)', color: 'var(--kb-primary)' };
    case 'advanced':
      return { background: 'rgba(239,68,68,0.08)', color: 'var(--kb-destructive)' };
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
  return !chapter.completed && !chapter.isCurrent && !chapter.unlocked;
}

function handleChapterClick(chapter: ViewChapter): void {
  if (isChapterLocked(chapter)) return;
  router.push(`/learning/chapter/${chapter.id}`);
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

// ===== 跳转 =====
// 跳转至第一个未完成章节（无则跳首章）
function continueLearning(): void {
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
});
</script>

<style scoped>
/* ===== 页面容器 ===== */
.path-detail-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
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

/* ===== 路径头卡片 ===== */
.path-header-card {
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  overflow: hidden;
}
.path-header-body {
  display: flex;
  gap: 24px;
  padding: 24px;
}
@media (max-width: 768px) {
  .path-header-body {
    flex-direction: column;
    gap: 16px;
    padding: 16px;
  }
}

/* 左侧图标盒 */
.path-icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: var(--kb-radius-lg);
  flex-shrink: 0;
}
@media (max-width: 768px) {
  .path-icon-box { width: 56px; height: 56px; }
}

/* 右侧信息 */
.path-info {
  flex: 1;
  min-width: 0;
}
.tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.tag {
  font-size: 12px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 999px;
}
.tag-status-learning {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-accent);
}
.tag-status-not-started {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

.path-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}
.path-desc {
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
  margin-bottom: 12px;
}

/* 统计行 */
.path-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.stat-rating { color: var(--kb-warning); }

/* 进度 + 按钮 行 */
.path-action-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.progress-block {
  flex: 1;
  min-width: 240px;
  max-width: 480px;
}
.progress-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.progress-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.progress-value {
  font-size: 12px;
  font-weight: 600;
}
.progress-track {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.4s ease;
}

/* 主按钮 */
.primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 20px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
  flex-shrink: 0;
}
.primary-btn:hover { opacity: 0.9; }

/* ===== 加载 / 空态 ===== */
.state-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
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

/* ===== 章节列表卡片 ===== */
.chapter-card {
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  overflow: hidden;
}
.chapter-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--kb-border);
}
.chapter-card-title {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.01em;
  color: var(--kb-foreground);
}
.chapter-card-meta {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 章节项 */
.chapter-list { display: flex; flex-direction: column; }
.chapter-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  border-bottom: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
}
.chapter-item:last-child { border-bottom: none; }
.chapter-item:hover:not(.locked) { background: rgba(59, 111, 224, 0.02); }
.chapter-item.current { background: rgba(59, 111, 224, 0.04); }
.chapter-item.locked { cursor: not-allowed; opacity: 0.75; }

/* 状态图标 */
.chapter-status-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex-shrink: 0;
  color: #fff;
}
.chapter-status-icon.status-completed {
  background: var(--kb-accent);
}
.chapter-status-icon.status-current {
  background: var(--kb-primary);
}
.chapter-status-icon.status-pending {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.chapter-order {
  font-size: 12px;
  font-weight: 600;
}

/* 章节信息 */
.chapter-info {
  flex: 1;
  min-width: 0;
}
.chapter-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.chapter-order-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.chapter-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--kb-foreground);
}
.chapter-item.current .chapter-title { color: var(--kb-primary); }
.chapter-item.current .chapter-order-label { color: var(--kb-primary); font-weight: 500; }
.current-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.chapter-desc {
  font-size: 12px;
  line-height: 1.5;
  color: var(--kb-muted-foreground);
  margin-top: 4px;
}

/* 章节右侧 */
.chapter-duration {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.chapter-arrow {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.chapter-item.current .chapter-arrow { color: var(--kb-primary); }

/* ===== 移动端适配 ===== */
@media (max-width: 640px) {
  .chapter-item {
    padding: 12px 16px;
    gap: 12px;
  }
  .chapter-card-header {
    padding: 12px 16px;
  }
  .chapter-card-title { font-size: 18px; }
  .path-title { font-size: 22px; }
  .path-stats { gap: 12px; }
  .stat-item { font-size: 13px; }
  .path-action-row { flex-direction: column; align-items: stretch; }
  .progress-block { max-width: 100%; }
  .primary-btn { justify-content: center; }
}
</style>
