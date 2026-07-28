<template>
  <!-- 学习路径列表页：分类筛选 + 排序 + 2 列卡片网格 -->
  <div class="paths-page animate-fade-in">
    <!-- ===== 页头：标题 + 描述 ===== -->
    <div class="page-header">
      <h1 class="kb-h1">学习路径</h1>
      <p class="page-subtitle">系统化的学习路线，从入门到精通，循序渐进掌握完整技能栈。</p>
    </div>

    <!-- ===== 筛选栏：分类按钮组 + 排序按钮 ===== -->
    <div class="filter-bar">
      <span class="filter-label">分类：</span>
      <button
        v-for="tag in filterTags"
        :key="tag.value"
        class="filter-btn"
        :class="{ active: currentFilter === tag.value }"
        @click="currentFilter = tag.value"
      >
        {{ tag.label }}
      </button>
      <div class="sort-group">
        <button
          v-for="sort in sortOptions"
          :key="sort.value"
          class="filter-btn sort-btn"
          :class="{ active: currentSort === sort.value }"
          @click="currentSort = sort.value"
        >
          <Icon :name="sort.icon" :size="14" />
          <span>{{ sort.label }}</span>
        </button>
      </div>
    </div>

    <!-- ===== 加载态 ===== -->
    <div v-if="loading" class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text">加载中...</p>
    </div>

    <!-- ===== 空态 ===== -->
    <div v-else-if="filteredPaths.length === 0" class="state-area">
      <div class="empty-icon-box">
        <Icon name="route" :size="40" class="empty-icon" />
      </div>
      <p class="state-title">暂无学习路径</p>
      <p class="state-text">换个分类试试吧</p>
    </div>

    <!-- ===== 路径卡片网格（2 列） ===== -->
    <div v-else class="path-grid">
      <div
        v-for="path in filteredPaths"
        :key="path.id"
        class="path-card"
        @click="goToPathDetail(path.id)"
      >
        <!-- 封面区：浅渐变背景 + 居中图标 + 分类标签 -->
        <div class="path-cover" :style="getCoverStyle(path)">
          <Icon :name="getPathIconName(path.icon)" :size="64" class="cover-icon" :style="{ color: getThemeColor(path.id) }" />
          <span class="cover-tag" :style="{ color: getThemeColor(path.id) }">
            {{ getDifficultyLabel(path.difficulty) }}
          </span>
        </div>
        <!-- 卡片内容 -->
        <div class="path-body">
          <h3 class="path-title">{{ path.title }}</h3>
          <p class="path-desc">{{ path.description || '暂无描述' }}</p>

          <!-- 统计行：章节 / 时长 / 学习人数 -->
          <div class="path-stats">
            <span class="stat-item">
              <Icon name="book-open" :size="14" />
              <span>{{ path.chaptersCount }} 章节</span>
            </span>
            <span class="stat-item">
              <Icon name="clock" :size="14" />
              <span>{{ formatDuration(path.totalDuration) }}</span>
            </span>
            <span class="stat-item">
              <Icon name="users" :size="14" />
              <span>{{ formatCount(path.enrolledCount) }} 人</span>
            </span>
            <span class="stat-item stat-rating">
              <Icon name="star" :size="14" :fill="true" />
              <span>{{ getRating(path.id) }}</span>
            </span>
          </div>

          <!-- 进度行：进度条 + 百分比 + 状态/按钮 -->
          <div class="path-progress-row">
            <template v-if="path.progress > 0">
              <div class="progress-wrap">
                <div class="progress-track">
                  <div class="progress-fill" :style="{ width: `${path.progress}%`, background: getThemeColor(path.id) }"></div>
                </div>
                <span class="progress-text" :style="{ color: getThemeColor(path.id) }">{{ path.progress }}%</span>
              </div>
              <span class="status-learning">学习中</span>
            </template>
            <template v-else>
              <span class="status-not-started">未开始</span>
              <button class="start-btn" @click.stop="goToPathDetail(path.id)">开始学习</button>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 学习路径列表页：按难度筛选、按发布/受欢迎排序，2 列卡片网格展示。
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import type { LearningPathVO } from '@/api/types';

const router = useRouter();
const loading = ref(false);

// ===== 视图模型 =====
interface ViewPath {
  id: number;
  title: string;
  description?: string;
  icon: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  chaptersCount: number;
  totalDuration: number;
  enrolledCount: number;
  progress: number;
}

// 后端 level 文案 → 统一难度枚举
function levelToDifficulty(level?: string): ViewPath['difficulty'] {
  const map: Record<string, ViewPath['difficulty']> = {
    入门: 'beginner',
    进阶: 'intermediate',
    高级: 'advanced',
    beginner: 'beginner',
    intermediate: 'intermediate',
    advanced: 'advanced',
  };
  return map[level || ''] || 'beginner';
}

const rawPaths = ref<LearningPathVO[]>([]);

// 后端 VO → 视图模型
const paths = computed<ViewPath[]>(() =>
  rawPaths.value.map((p) => ({
    id: p.id,
    title: p.title,
    description: p.description,
    icon: pickIcon(p.id),
    difficulty: levelToDifficulty(p.level),
    chaptersCount: p.chapterCount || 0,
    totalDuration: p.totalDuration || 0,
    enrolledCount: p.enrolledCount || 0,
    progress: 0,
  }))
);

// ===== 筛选与排序 =====
const filterTags = [
  { value: 'all', label: '全部' },
  { value: 'beginner', label: '入门' },
  { value: 'intermediate', label: '进阶' },
  { value: 'advanced', label: '高级' },
];

const sortOptions = [
  { value: 'latest', label: '最新发布', icon: 'arrow-up-down' },
  { value: 'popular', label: '最受欢迎', icon: 'users' },
];

const currentFilter = ref('all');
const currentSort = ref('latest');

const filteredPaths = computed(() => {
  let list = paths.value;
  // 难度筛选
  if (currentFilter.value !== 'all') {
    list = list.filter((p) => p.difficulty === currentFilter.value);
  }
  // 排序：最新发布（按 id 倒序）/ 最受欢迎（按 enrolledCount 倒序）
  if (currentSort.value === 'latest') {
    list = [...list].sort((a, b) => b.id - a.id);
  } else if (currentSort.value === 'popular') {
    list = [...list].sort((a, b) => b.enrolledCount - a.enrolledCount);
  }
  return list;
});

// ===== 渲染辅助 =====
// 按难度返回中文标签
function getDifficultyLabel(difficulty: string): string {
  const labels: Record<string, string> = {
    beginner: '入门',
    intermediate: '进阶',
    advanced: '高级',
  };
  return labels[difficulty] || difficulty;
}

// 图标名映射
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

// 按 id 取模分配图标
function pickIcon(id: number): string {
  const icons = ['layout', 'server', 'brain', 'database', 'cloud', 'smartphone'];
  return icons[id % icons.length] || 'code';
}

// 主题色：按 id 循环分配（与设计稿一致）
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

// 浅渐变封面背景（基于主题色）
function getCoverStyle(path: ViewPath): Record<string, string> {
  const color = getThemeColor(path.id);
  // 将 css 变量映射到 rgba 不可行，直接用预定义的 rgba 渐变
  const gradients: Record<number, string> = {
    0: 'linear-gradient(135deg, rgba(59,111,224,0.12), rgba(59,111,224,0.04))',
    1: 'linear-gradient(135deg, rgba(16,185,129,0.12), rgba(16,185,129,0.04))',
    2: 'linear-gradient(135deg, rgba(168,85,247,0.12), rgba(168,85,247,0.04))',
    3: 'linear-gradient(135deg, rgba(245,158,11,0.12), rgba(245,158,11,0.04))',
    4: 'linear-gradient(135deg, rgba(239,68,68,0.12), rgba(239,68,68,0.04))',
    5: 'linear-gradient(135deg, rgba(14,165,233,0.12), rgba(14,165,233,0.04))',
  };
  return { background: gradients[path.id % 6] || gradients[0], '--theme-color': color };
}

// 分钟数格式化为「x小时y分」或「x分钟」
function formatDuration(minutes: number): string {
  if (!minutes) return '0 小时';
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins > 0 ? `${hours} 小时${mins} 分` : `${hours} 小时`;
}

// 学习人数格式化（1234 → 1,234）
function formatCount(n: number): string {
  if (!n) return '0';
  return n.toLocaleString('en-US');
}

// 评分（后端无字段，使用基于 id 的稳定值 4.5-4.9）
function getRating(id: number): string {
  const ratings = [4.9, 4.8, 4.9, 4.7, 4.6, 4.5];
  return ratings[id % ratings.length].toFixed(1);
}

function goToPathDetail(pathId: number): void {
  router.push(`/learning/path/${pathId}`);
}

// ===== 数据加载 =====
onMounted(async () => {
  loading.value = true;
  try {
    rawPaths.value = await learningApi.paths();
  } catch {
    rawPaths.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
/* ===== 页面容器 ===== */
.paths-page {
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

/* ===== 页头 ===== */
.page-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.page-subtitle {
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
}

/* ===== 筛选栏 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.filter-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.filter-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}
.sort-group {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

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
.empty-icon-box {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--kb-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-icon { color: var(--kb-muted-foreground); }
.state-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.state-text {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* ===== 卡片网格（2 列） ===== */
.path-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}
@media (max-width: 768px) {
  .path-grid { grid-template-columns: 1fr; }
}

/* 路径卡片 */
.path-card {
  display: block;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.15s;
}
.path-card:hover {
  box-shadow: 0 8px 24px rgba(59, 111, 224, 0.08);
  transform: translateY(-2px);
}

/* 封面区 */
.path-cover {
  height: 144px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.cover-icon {
  opacity: 0.7;
}
.cover-tag {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--kb-card);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

/* 卡片内容 */
.path-body {
  padding: 20px;
}
.path-title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--kb-foreground);
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.path-desc {
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 统计行 */
.path-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.stat-rating {
  color: var(--kb-warning);
}

/* 进度行 */
.path-progress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.progress-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}
.progress-track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
  min-width: 80px;
}
.progress-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}
.progress-text {
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}
.status-learning {
  font-size: 12px;
  color: var(--kb-accent);
  flex-shrink: 0;
}
.status-not-started {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.start-btn {
  font-size: 12px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}
.start-btn:hover { opacity: 0.9; }
</style>
