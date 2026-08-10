<template>
  <!-- 学习路径列表页：分类筛选 + 排序 + 2 列卡片网格 -->
  <div class="paths-page animate-fade-in">
    <!-- ===== 页头：标题 + 描述 + AI 入口 ===== -->
    <div class="page-header">
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="kb-h1">学习路径</h1>
          <p class="page-subtitle">系统化的学习路线，从入门到精通，循序渐进掌握完整技能栈。</p>
        </div>
        <button class="ai-path-btn" @click="showPersonalizedDialog = true">
          <Icon name="sparkles" :size="16" />
          <span>AI 个性化学习路径</span>
        </button>
      </div>
    </div>

    <!-- ===== AI 个性化路径生成弹窗 ===== -->
    <div v-if="showPersonalizedDialog" class="modal-overlay" @click.self="showPersonalizedDialog = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3 class="modal-title">
            <Icon name="sparkles" :size="18" />
            AI 生成个性化学习路径
          </h3>
          <button class="modal-close" @click="showPersonalizedDialog = false">
            <Icon name="x" :size="18" />
          </button>
        </div>
        <div class="modal-body">
          <!-- 输入表单 -->
          <div v-if="!personalizedPath && !personalizedLoading" class="space-y-4">
            <div>
              <label class="form-label">学习目标</label>
              <input v-model="pathGoal" type="text" class="form-input" placeholder="如：掌握 Spring Boot 后端开发、学习 Python 数据分析…" />
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="form-label">当前水平</label>
                <select v-model="pathLevel" class="form-input">
                  <option value="入门">入门 — 零基础或刚接触</option>
                  <option value="进阶">进阶 — 有一定基础</option>
                  <option value="高级">高级 — 熟练使用想深入</option>
                </select>
              </div>
              <div>
                <label class="form-label">每日学习时间</label>
                <select v-model="pathDailyMinutes" class="form-input">
                  <option :value="15">15 分钟</option>
                  <option :value="30">30 分钟</option>
                  <option :value="60">1 小时</option>
                  <option :value="120">2 小时</option>
                </select>
              </div>
            </div>
            <button class="generate-btn" @click="generatePersonalizedPath">
              <Icon name="sparkles" :size="16" />
              <span>生成我的学习路径</span>
            </button>
            <!-- 我的个性化路径历史：可查看 / 去学习 / 删除已生成的记录 -->
            <div v-if="personalizedHistory.length" class="history-section">
              <h5 class="history-title">我的个性化路径</h5>
              <div class="history-list">
                <div v-for="item in personalizedHistory" :key="item.id" class="history-item">
                  <button type="button" class="history-info" @click="viewHistoryPath(item)">
                    <span class="history-name">{{ item.title }}</span>
                    <span class="history-meta">{{ item.level }} · {{ item.chapters.length }} 章节 · {{ formatDuration(item.totalDuration) }}</span>
                  </button>
                  <div class="history-actions">
                    <button
                      v-if="item.relatedPathId"
                      class="history-icon-btn go"
                      title="去学习"
                      @click.stop="goToPathDetail(item.relatedPathId)"
                    >
                      <Icon name="arrow-right" :size="14" />
                    </button>
                    <button class="history-icon-btn" title="查看" @click.stop="viewHistoryPath(item)">
                      <Icon name="eye" :size="14" />
                    </button>
                    <button class="history-icon-btn danger" title="删除" @click.stop="removeHistoryPath(item)">
                      <Icon name="trash-2" :size="14" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <!-- 加载态 -->
          <div v-else-if="personalizedLoading" class="loading-area">
            <div class="loading-spinner"></div>
            <p class="state-text">AI 正在分析您的学习数据并生成个性化路径…</p>
          </div>
          <!-- 生成结果 -->
          <div v-else-if="personalizedPath" class="personalized-result">
            <div class="result-header">
              <div>
                <h4 class="result-title">{{ personalizedPath.title }}</h4>
                <p class="result-reason">{{ personalizedPath.reason }}</p>
              </div>
              <span class="result-badge">{{ personalizedPath.level }}</span>
            </div>
            <!-- 统计 -->
            <div class="result-stats">
              <div class="result-stat">
                <Icon name="book-open" :size="14" />
                <span>{{ personalizedPath.chapters.length }} 章节</span>
              </div>
              <div class="result-stat">
                <Icon name="clock" :size="14" />
                <span>{{ formatDuration(personalizedPath.totalDuration) }}</span>
              </div>
              <div class="result-stat">
                <Icon name="calendar" :size="14" />
                <span>每日 {{ personalizedPath.dailyDuration }} 分钟</span>
              </div>
            </div>
            <!-- 学习目标 -->
            <div v-if="personalizedPath.goals.length" class="result-section">
              <h5 class="result-section-title">学习目标</h5>
              <div class="goals-list">
                <span v-for="g in personalizedPath.goals" :key="g" class="goal-tag">{{ g }}</span>
              </div>
            </div>
            <!-- 章节列表 -->
            <div class="result-section">
              <h5 class="result-section-title">章节规划</h5>
              <div class="chapter-timeline">
                <div v-for="ch in personalizedPath.chapters" :key="ch.sortOrder" class="chapter-item">
                  <div class="chapter-dot">{{ ch.sortOrder }}</div>
                  <div class="chapter-info">
                    <div class="chapter-header">
                      <span class="chapter-title">{{ ch.title }}</span>
                      <span class="chapter-duration">{{ ch.duration }} 分钟</span>
                    </div>
                    <p class="chapter-content">{{ ch.content }}</p>
                    <p v-if="ch.focus" class="chapter-focus">
                      <Icon name="target" :size="12" />
                      <span>重点：{{ ch.focus }}</span>
                    </p>
                    <!-- AI 推断的章节前置依赖，采用路径后将生成依赖图谱 -->
                    <p v-if="ch.prerequisiteSortOrders?.length" class="chapter-prereq">
                      <Icon name="git-branch" :size="12" />
                      <span>前置：{{ formatPrerequisites(ch.prerequisiteSortOrders) }}</span>
                    </p>
                  </div>
                </div>
              </div>
            </div>
            <!-- 学习建议 -->
            <div v-if="personalizedPath.advice" class="result-section">
              <h5 class="result-section-title">AI 学习建议</h5>
              <p class="result-advice">{{ personalizedPath.advice }}</p>
            </div>
            <!-- 操作按钮 -->
            <div class="result-actions">
              <!-- 已采用：直接去学习；未采用：落地为真实路径并报名 -->
              <button
                v-if="personalizedPath.relatedPathId"
                class="adopt-btn"
                @click="goToPathDetail(personalizedPath.relatedPathId)"
              >
                <Icon name="arrow-right" :size="14" />
                <span>去学习</span>
              </button>
              <button
                v-else
                class="adopt-btn"
                :disabled="adopting || !personalizedPath.id"
                @click="adoptCurrentPath"
              >
                <Icon name="check" :size="14" />
                <span>{{ adopting ? '采用中…' : '采用此路径' }}</span>
              </button>
              <button class="regenerate-btn" :disabled="personalizedLoading" @click="regeneratePersonalizedPath">
                <Icon name="rotate-ccw" :size="14" :class="personalizedLoading ? 'animate-spin' : ''" />
                <span>{{ personalizedLoading ? '生成中…' : '重新生成' }}</span>
              </button>
              <button class="close-btn" @click="showPersonalizedDialog = false; personalizedPath = null">
                关闭
              </button>
            </div>
          </div>
        </div>
      </div>
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
        <Icon name="route" :size="32" class="empty-icon" />
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
        role="button"
        tabindex="0"
        @click="goToPathDetail(path.id)"
        @keydown.enter.self.prevent="($event.target as HTMLElement).click()"
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

          <!-- 进度行：进度条 + 百分比 + 状态/按钮（报名前→报名学习；报名后→开始学习/学习中） -->
          <div class="path-progress-row">
            <template v-if="path.enrolled">
              <!-- 已报名且已有进度：进度条 + 学习中 -->
              <template v-if="path.progress > 0">
                <div class="progress-wrap">
                  <div class="progress-track">
                    <div class="progress-fill" :style="{ width: `${path.progress}%`, background: getThemeColor(path.id) }"></div>
                  </div>
                  <span class="progress-text" :style="{ color: getThemeColor(path.id) }">{{ path.progress }}%</span>
                </div>
                <span class="status-learning">学习中</span>
              </template>
              <!-- 已报名但未开始：进入详情开始学习 -->
              <template v-else>
                <span class="status-not-started">未开始</span>
                <button class="start-btn" @click.stop="goToPathDetail(path.id)">开始学习</button>
              </template>
            </template>
            <!-- 未报名：列表内直接报名 -->
            <template v-else>
              <span class="status-not-started">未报名</span>
              <button
                class="enroll-btn"
                :disabled="enrollLoadingIds.has(path.id)"
                @click.stop="enrollPath(path)"
              >
                <Icon v-if="enrollLoadingIds.has(path.id)" name="loader" :size="14" class="spin" />
                {{ enrollLoadingIds.has(path.id) ? '报名中…' : '报名学习' }}
              </button>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 学习路径列表页：按难度筛选、按发布/受欢迎排序，2 列卡片网格展示。
import { ref, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import { notify, getApiError } from '@/utils/toast';
import type { LearningPathVO, PersonalizedPathVO } from '@/api/types';

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
  /** 当前登录用户是否已报名 */
  enrolled: boolean;
  /** 当前登录用户的学习进度百分比（0~100）；未报名时为 0 */
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
    enrolled: p.enrolled ?? false,
    progress: p.progress || 0,
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

// AI 推断的前置章节序号 → 「第 1 章、第 2 章」
function formatPrerequisites(sortOrders: number[]): string {
  return sortOrders.map((order) => `第 ${order} 章`).join('、');
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

// ===== AI 个性化学习路径 =====
const showPersonalizedDialog = ref(false);
const personalizedLoading = ref(false);
const personalizedPath = ref<PersonalizedPathVO | null>(null);
const pathGoal = ref('');
const pathLevel = ref('入门');
const pathDailyMinutes = ref(30);
// 我的个性化路径历史（AI 生成后持久化的记录，可查看 / 采用 / 删除）
const personalizedHistory = ref<PersonalizedPathVO[]>([]);
const adopting = ref(false);

async function generatePersonalizedPath() {
  if (!pathGoal.value.trim()) {
    notify('请输入学习目标', 'error');
    return;
  }
  personalizedLoading.value = true;
  try {
    personalizedPath.value = await learningApi.personalizedPath({
      goal: pathGoal.value.trim(),
      level: pathLevel.value,
      dailyMinutes: pathDailyMinutes.value,
    });
  } catch (e: unknown) {
    notify(getApiError(e, '个性化路径生成失败'), 'error');
  } finally {
    personalizedLoading.value = false;
  }
}

/** 重新生成个性化路径：删除旧缓存，AI 重新生成并持久化 */
async function regeneratePersonalizedPath() {
  if (personalizedLoading.value) return;
  personalizedLoading.value = true;
  try {
    personalizedPath.value = await learningApi.regeneratePersonalizedPath({
      goal: pathGoal.value.trim(),
      level: pathLevel.value,
      dailyMinutes: pathDailyMinutes.value,
    });
    notify('学习路径已重新生成', 'success');
  } catch (e: unknown) {
    notify(getApiError(e, '重新生成失败'), 'error');
  } finally {
    personalizedLoading.value = false;
  }
}

/** 正在报名中的路径 id 集合，用于禁用对应按钮、防止重复点击。 */
const enrollLoadingIds = ref<Set<number>>(new Set());

/** 列表内直接报名：成功后原地把 enrolled 置为 true，按钮变为「开始学习」。 */
async function enrollPath(path: ViewPath): Promise<void> {
  if (enrollLoadingIds.value.has(path.id)) return;
  enrollLoadingIds.value = new Set(enrollLoadingIds.value).add(path.id);
  try {
    await learningApi.enroll(path.id);
    // 报名成功：更新本地视图，按钮原地切换为「开始学习」
    const target = rawPaths.value.find((p) => p.id === path.id);
    if (target) {
      target.enrolled = true;
      if (target.progress === undefined) target.progress = 0;
    }
    notify('报名成功，开始学习吧！', 'success');
  } catch (e: unknown) {
    notify(getApiError(e, '报名失败'), 'error');
  } finally {
    const next = new Set(enrollLoadingIds.value);
    next.delete(path.id);
    enrollLoadingIds.value = next;
  }
}

function goToPathDetail(pathId: number): void {
  router.push(`/learning/path/${pathId}`);
}

/** 加载我的个性化路径历史（按创建时间倒序，用于弹窗内回显与管理） */
async function loadPersonalizedHistory(): Promise<void> {
  try {
    personalizedHistory.value = await learningApi.personalizedPaths();
  } catch {
    personalizedHistory.value = [];
  }
}

/** 采用当前展示的路径：落地为真实学习路径并自动报名，成功后跳转详情页开始学习 */
async function adoptCurrentPath(): Promise<void> {
  const target = personalizedPath.value;
  if (!target?.id || adopting.value) return;
  adopting.value = true;
  try {
    const res = await learningApi.adoptPersonalizedPath(target.id);
    notify('已采用该路径，开始学习吧！', 'success');
    showPersonalizedDialog.value = false;
    personalizedPath.value = null;
    goToPathDetail(res.pathId);
  } catch (e: unknown) {
    notify(getApiError(e, '采用路径失败'), 'error');
  } finally {
    adopting.value = false;
  }
}

/** 查看历史记录：回显到结果区，并同步输入条件便于「重新生成」 */
function viewHistoryPath(item: PersonalizedPathVO): void {
  personalizedPath.value = item;
  pathGoal.value = item.goals?.[0] || pathGoal.value;
  pathLevel.value = item.level || pathLevel.value;
}

/** 删除一条历史记录（物理删除缓存推荐，不影响已采用落地的学习路径） */
async function removeHistoryPath(item: PersonalizedPathVO): Promise<void> {
  if (!item.id) return;
  try {
    await learningApi.deletePersonalizedPath(item.id);
    personalizedHistory.value = personalizedHistory.value.filter((p) => p.id !== item.id);
    notify('已删除', 'success');
  } catch (e: unknown) {
    notify(getApiError(e, '删除失败'), 'error');
  }
}

// 打开弹窗时自动拉取历史记录，便于用户快速回显 / 采用最近生成的路径
watch(showPersonalizedDialog, (open) => {
  if (open) loadPersonalizedHistory();
});

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
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  color: var(--kb-muted-foreground);
}
/* 小屏：标题块可收缩，避免与右侧按钮挤压溢出 */
.page-header > .flex > div {
  min-width: 0;
  flex: 1 1 auto;
}

/* ===== 筛选栏 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-label {
  font-size: var(--kb-fs-body-md);
  font-weight: 500;
  color: var(--kb-foreground);
}
.filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease, border-color 0.15s ease, opacity 0.15s ease, transform 0.12s ease;
  white-space: nowrap;
}
.filter-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.filter-btn:active {
  transform: scale(0.98);
}
.filter-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.filter-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}
/* 激活态同样需要悬停反馈（.active 在源码顺序上会覆盖 :hover，故单列一条） */
.filter-btn.active:hover {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
  opacity: 0.9;
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
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
}
.state-text {
  font-size: var(--kb-fs-body-sm);
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
  transition: box-shadow 0.2s ease, transform 0.15s ease, border-color 0.15s ease;
}
.path-card:hover {
  box-shadow: 0 8px 24px rgba(59, 111, 224, 0.08);
  transform: translateY(-2px);
  border-color: var(--kb-primary);
}
.path-card:active {
  transform: translateY(0) scale(0.99);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.06);
}
.path-card:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-color: var(--kb-primary);
  /* 覆盖全局 [role='button']:focus-visible 的 6px 圆角，保持卡片自身圆角 */
  border-radius: var(--kb-radius-lg);
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
  font-size: var(--kb-fs-caption);
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
  /* 卡片标题对齐字号阶梯 h4（20px / 1.4） */
  font-size: var(--kb-fs-h4);
  font-weight: var(--kb-fw-h4);
  line-height: var(--kb-lh-h4);
  color: var(--kb-foreground);
  margin-bottom: 8px;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.path-desc {
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
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
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  /* 单项不内部换行，整行由 .path-stats 的 flex-wrap 负责折行 */
  white-space: nowrap;
  /* 数字列等宽，避免不同卡片间统计数字跳动 */
  font-variant-numeric: tabular-nums;
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
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.status-learning {
  font-size: var(--kb-fs-caption);
  color: var(--kb-accent);
  flex-shrink: 0;
}
.status-not-started {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.start-btn {
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  padding: 6px 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s ease, transform 0.12s ease;
}
.start-btn:hover { opacity: 0.9; }
.start-btn:active { transform: scale(0.98); }
.start-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 列表内「报名学习」按钮：强调色区分于「开始学习」 */
.enroll-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  padding: 6px 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-accent);
  color: var(--kb-accent-foreground);
  border: none;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s ease, transform 0.12s ease;
}
.enroll-btn:hover:not(:disabled) { opacity: 0.9; }
.enroll-btn:active:not(:disabled) { transform: scale(0.98); }
.enroll-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.enroll-btn:disabled { opacity: 0.6; cursor: not-allowed; }

/* 图标旋转（报名加载中） */
.spin { animation: spin 0.8s linear infinite; display: inline-block; }

/* ===== AI 个性化路径按钮 ===== */
.ai-path-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
  background: linear-gradient(135deg, var(--kb-primary), #6366f1);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease, transform 0.12s ease;
  white-space: nowrap;
}
.ai-path-btn:hover { opacity: 0.9; }
.ai-path-btn:active { transform: scale(0.98); }
.ai-path-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* ===== 弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: 16px;
}
.modal-content {
  background: var(--kb-card);
  border-radius: 12px;
  width: 100%;
  max-width: 640px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--kb-border);
}
.modal-title {
  display: flex;
  align-items: center;
  gap: 8px;
  /* 弹窗标题对齐字号阶梯 h4 */
  font-size: var(--kb-fs-h4);
  font-weight: var(--kb-fw-h4);
  line-height: var(--kb-lh-h4);
  color: var(--kb-foreground);
  min-width: 0;
}
.modal-close {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  padding: 4px;
  border-radius: var(--kb-radius-sm);
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease, transform 0.12s ease;
}
.modal-close:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.modal-close:active { transform: scale(0.94); }
.modal-close:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.modal-body {
  padding: 24px;
  overflow-y: auto;
}

/* 表单 */
.form-label {
  display: block;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}
.form-input {
  width: 100%;
  max-width: 100%;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-md);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.form-input:hover { border-color: var(--kb-primary); }
.form-input:focus,
.form-input:focus-visible {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
  outline: none;
}
.generate-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease, transform 0.12s ease;
}
.generate-btn:hover { opacity: 0.9; }
.generate-btn:active { transform: scale(0.99); }
.generate-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 加载 */
.loading-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 16px;
  gap: 16px;
}

/* 结果 */
.personalized-result { display: flex; flex-direction: column; gap: 20px; }
.result-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
/* 小屏：标题/理由块可收缩换行，避免与右侧徽章挤压溢出 */
.result-header > div {
  min-width: 0;
  flex: 1 1 auto;
}
/* 结果标题对齐字号阶梯 h4 */
.result-title { font-size: var(--kb-fs-h4); font-weight: var(--kb-fw-h4); line-height: var(--kb-lh-h4); color: var(--kb-foreground); margin-bottom: 4px; overflow-wrap: anywhere; }
.result-reason { font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); line-height: var(--kb-lh-body-md); overflow-wrap: anywhere; }
.result-badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
  white-space: nowrap;
}
.result-stats { display: flex; gap: 16px; flex-wrap: wrap; }
.result-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.result-section { display: flex; flex-direction: column; gap: 8px; }
.result-section-title {
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
}
.goals-list { display: flex; flex-wrap: wrap; gap: 8px; }
.goal-tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: var(--kb-fs-caption);
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

/* 时间线 */
.chapter-timeline { display: flex; flex-direction: column; gap: 0; }
.chapter-item {
  display: flex;
  gap: 12px;
  padding-bottom: 16px;
  position: relative;
}
.chapter-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 11px;
  top: 24px;
  bottom: 0;
  width: 2px;
  background: var(--kb-border);
}
.chapter-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--kb-fs-xs);
  font-weight: 700;
  flex-shrink: 0;
  z-index: 1;
  font-variant-numeric: tabular-nums;
}
.chapter-info { flex: 1; min-width: 0; }
.chapter-header { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 4px; }
.chapter-title { font-size: var(--kb-fs-body-md); font-weight: 500; color: var(--kb-foreground); min-width: 0; overflow-wrap: anywhere; }
.chapter-duration { font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); white-space: nowrap; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.chapter-content { font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); line-height: var(--kb-lh-body-sm); margin-bottom: 4px; overflow-wrap: anywhere; }
.chapter-focus {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-primary);
}
.chapter-prereq {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.result-advice {
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  line-height: var(--kb-lh-body-md);
  padding: 12px;
  background: var(--kb-muted);
  border-radius: 8px;
}
.result-actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
/* 采用此路径：主行动按钮，靠左占位与其余按钮区分 */
.adopt-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  margin-right: auto;
  transition: opacity 0.15s ease, transform 0.12s ease;
}
.adopt-btn:hover:not(:disabled) { opacity: 0.9; }
.adopt-btn:active:not(:disabled) { transform: scale(0.98); }
.adopt-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.adopt-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.regenerate-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.12s ease;
}
.regenerate-btn:hover:not(:disabled) { background: var(--kb-muted); border-color: var(--kb-primary); }
.regenerate-btn:active:not(:disabled) { transform: scale(0.98); }
.regenerate-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.regenerate-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.close-btn {
  padding: 8px 14px;
  border-radius: 8px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease, transform 0.12s ease;
}
.close-btn:hover { opacity: 0.9; }
.close-btn:active { transform: scale(0.98); }
.close-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* ===== 我的个性化路径历史 ===== */
.history-section { margin-top: 20px; border-top: 1px solid var(--kb-border); padding-top: 16px; }
.history-title { font-size: var(--kb-fs-body-sm); font-weight: 600; color: var(--kb-foreground); margin-bottom: 8px; }
.history-list { display: flex; flex-direction: column; gap: 8px; }
.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  transition: border-color 0.15s ease, background 0.15s ease;
}
.history-item:hover { border-color: var(--kb-primary); }
/* 整行文字区为按钮：保留原视觉，补齐 hover / active / focus-visible */
.history-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 0;
  cursor: pointer;
  flex: 1 1 auto;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  text-align: left;
  border-radius: var(--kb-radius-sm);
  transition: opacity 0.15s ease, transform 0.12s ease;
}
.history-info:hover .history-name { color: var(--kb-primary); }
.history-info:active { transform: scale(0.99); }
.history-info:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.history-name {
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  transition: color 0.15s ease;
}
.history-meta {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  font-variant-numeric: tabular-nums;
}
.history-actions { display: flex; gap: 4px; flex-shrink: 0; }
.history-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease, transform 0.12s ease;
}
.history-icon-btn:hover { background: var(--kb-muted); color: var(--kb-foreground); }
.history-icon-btn:active { transform: scale(0.94); }
.history-icon-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.history-icon-btn.go:hover { color: var(--kb-primary); border-color: var(--kb-primary); }
.history-icon-btn.danger:hover { color: var(--kb-destructive); border-color: var(--kb-destructive); }

/* 工具类 */
.space-y-4 > * + * { margin-top: 16px; }
.grid { display: grid; }
.grid-cols-2 { grid-template-columns: repeat(2, 1fr); }
.gap-3 { gap: 12px; }
.flex { display: flex; }
.items-center { align-items: center; }
.justify-between { justify-content: space-between; }
.flex-wrap { flex-wrap: wrap; }
.gap-3 { gap: 12px; }

/* ===== 小屏适配：仅收窄内边距与栅格，不影响桌面布局 ===== */
@media (max-width: 640px) {
  /* 弹窗内 2 列表单在窄屏改为单列，避免下拉被压缩截断 */
  .modal-content .grid-cols-2 { grid-template-columns: 1fr; }
  .modal-header { padding: 16px; }
  .modal-body { padding: 16px; }
  /* 主行动按钮不再强制占满左侧，允许与其余按钮自然换行 */
  .adopt-btn { margin-right: 0; }
}
</style>
