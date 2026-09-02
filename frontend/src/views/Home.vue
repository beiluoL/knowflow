<template>
  <div class="home-container">
    <section class="welcome-banner">
      <div class="banner-bg">
        <div class="banner-grid"></div>
        <div class="banner-blur"></div>
        <div class="banner-blur-warm"></div>
      </div>
      <div class="banner-content">
        <div class="banner-header">
          <div class="welcome-info">
            <span class="welcome-badge">
              <Icon name="sun" :size="14" />
              {{ todayStr }}
            </span>
            <h1 class="welcome-title">{{ greeting }}，{{ userName }}！</h1>
            <p class="welcome-tagline">{{ dynamicTagline }}</p>
            <div class="welcome-stats">
              <div v-if="streakDays > 0" class="stat-pill highlight-badge">
                <Icon name="flame" :size="14" />
                <span class="stat-value tabular-nums">{{ streakDaysDisplay }}</span>
                <span class="stat-unit">天连击</span>
              </div>
              <!-- Architecture-5 §62：Coin / XP 轻量展示（真实钱包，学习驱动经济，不喧宾夺主 §60-63） -->
              <div v-if="auth.isLoggedIn && wallet" class="stat-pill coin-pill">
                <Icon name="coins" :size="14" />
                <span class="stat-value tabular-nums">{{ wallet.coin }}</span>
                <span class="stat-unit">Coin</span>
              </div>
              <div v-if="auth.isLoggedIn && wallet" class="stat-pill xp-pill">
                <Icon name="zap" :size="14" />
                <span class="stat-value tabular-nums">{{ wallet.xp }}</span>
                <span class="stat-unit">XP · Lv{{ wallet.level }}</span>
              </div>
            </div>
          </div>
          <div class="banner-cta">
            <router-link :to="dynamicCta.primary.path" class="action-btn primary">
              <Icon :name="dynamicCta.primary.icon" :size="16" />
              <span>{{ dynamicCta.primary.text }}</span>
            </router-link>
            <router-link :to="dynamicCta.secondary.path" class="action-btn secondary">
              <Icon :name="dynamicCta.secondary.icon" :size="16" />
              <span>{{ dynamicCta.secondary.text }}</span>
            </router-link>
            <router-link to="/learning/focus" class="action-btn focus">
              <Icon name="zap" :size="16" />
              <span>沉浸工作台</span>
            </router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- Architecture-5：今日学习状态（真实数据汇总 §215-216/§223）——
         只保留能帮助用户「今天学什么」做决定的信息（§54/§57），
         删除原先伪造的课程进度公式与周目标（§55）。 -->
    <section class="status-section reveal-item" style="--reveal-index: 1;">
      <div class="status-grid">
        <div class="status-item">
          <div class="status-head">
            <Icon name="list-checks" :size="14" />
            <span>今日任务</span>
          </div>
          <p class="status-value tabular-nums">{{ doneTasks }}<span class="status-unit"> / {{ tasks.length }} 已完成</span></p>
        </div>
        <div class="status-item">
          <div class="status-head">
            <Icon name="rotate-ccw" :size="14" />
            <span>待复习</span>
          </div>
          <p class="status-value tabular-nums">{{ reviewRequired.length }}<span class="status-unit"> 个知识点</span></p>
        </div>
        <div class="status-item">
          <div class="status-head">
            <Icon name="trending-down" :size="14" />
            <span>薄弱知识</span>
          </div>
          <p class="status-value tabular-nums">{{ weakPoints.length }}<span class="status-unit"> 个知识点</span></p>
        </div>
        <div class="status-item">
          <div class="status-head">
            <Icon name="route" :size="14" />
            <span>当前路径</span>
          </div>
          <p v-if="currentPath" class="status-value tabular-nums">{{ pathPercent }}<span class="status-unit">% 进度</span></p>
          <p v-else class="status-value status-muted">—</p>
        </div>
      </div>
      <!-- Architecture-5 §35-38：今日奖励（reward_ledger 真实数据，轻量展示 §109） -->
      <p v-if="todayRewardLoaded && (todayXp > 0 || todayCoin > 0)" class="today-reward-line">
        <Icon name="zap" :size="12" />
        今日已获得 <span class="tabular-nums">+{{ todayXp }} XP</span> · <span class="tabular-nums">+{{ todayCoin }} Coin</span>（学习奖励）
      </p>
    </section>

    <!-- Architecture-4/5：下一步（第一视觉 §5/§28/§118）——
         用户打开首页第一个看到的是「现在该做什么」，而不是功能拼盘（§217-220）。 -->
    <section class="next-section reveal-item" style="--reveal-index: 2;">
      <NextActionCard :action="nextAction" dismissible />
    </section>

    <!-- Architecture-7：问问学习教练 CTA（§79-82，不抢占 NextAction 首屏） -->
    <section class="reveal-item" style="--reveal-index: 2.2;">
      <div class="flex items-center gap-3 rounded-2xl border px-4 py-3" style="border-color: var(--kb-border); background: rgba(59,111,224,0.05);">
        <Icon name="graduation-cap" :size="20" style="color: var(--kb-primary); flex-shrink: 0;" />
        <div class="flex-1 min-w-0">
          <p class="text-sm font-semibold" style="color: var(--kb-foreground);">问问学习教练</p>
          <p class="text-[12px] mt-0.5" style="color: var(--kb-muted-foreground);">基于你的真实学习数据给出可执行的建议，不会改动任何学习记录。</p>
        </div>
        <router-link to="/chat?coach=1" class="inline-flex items-center gap-1 px-3 h-9 rounded-lg text-sm font-medium transition-opacity hover:opacity-90" style="background: var(--kb-primary); color: var(--kb-primary-foreground);">
          去问问
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>
    </section>

    <!-- Architecture-6：学习状态（第二层理解，§82-87/§196-217）——
         用真实诊断回答「为什么 / 现在该关注什么」，不展示内部枚举（§59-61），不新增导航（§199-201）。 -->
    <section class="state-section reveal-item" style="--reveal-index: 1.4;">
      <LearningStateCard :diagnoses="homeDiagnoses" mode="home" />
    </section>

    <section class="todos-section reveal-item" style="--reveal-index: 3;">
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
      </div>
    </section>

    <!-- Architecture-5：待复习 + 薄弱知识 并排（决策信息 §23/§57，具体知识点在学习中心展开） -->
    <div class="summary-grid reveal-item" style="--reveal-index: 4;">
      <section class="review-section">
        <ReviewDueCard
          :items="reviewRequired"
          :loading="reviewLoading"
          :error="reviewError"
          @retry="loadReviewRequired"
          @start="goTo('/learning/review')"
          @continue="goTo('/learning/center')"
        />
      </section>
      <section class="weak-section">
        <WeakPointCard
          :items="weakPoints"
          :loading="weakLoading"
          :error="weakError"
          @retry="loadWeakPoints"
          @view-all="goTo('/learning/mastery')"
        />
      </section>
    </div>

    <!-- Architecture-5：当前学习路径（真实 enrolled 路径 + 后端进度 §57） -->
    <section class="current-path-section reveal-item" style="--reveal-index: 5;">
      <div class="section-header">
        <h2 class="section-title">当前学习路径</h2>
        <router-link to="/learning/paths" class="section-link">
          <span>查看更多</span>
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>
      <div v-if="currentPath" class="path-card current-path-card">
        <div class="path-cover" :style="{ background: pathCover(currentPath.id) }">
          <Icon name="route" :size="24" />
        </div>
        <div class="path-content">
          <div class="path-meta">
            <span class="path-badge" :class="levelBadge(currentPath.level).cls">{{ levelBadge(currentPath.level).text }}</span>
            <span class="path-chapters tabular-nums">{{ currentPath.chapterCount || 0 }} 章</span>
          </div>
          <h3 class="path-title">{{ currentPath.title }}</h3>
          <p class="path-desc">{{ currentPath.description || '系统化学习路径，循序渐进掌握技能。' }}</p>
          <div class="path-progress">
            <span class="progress-text tabular-nums">{{ pathPercent }}%</span>
            <div class="progress-bar">
              <div class="progress-fill" :style="`width: ${pathPercent}%`"></div>
            </div>
          </div>
          <router-link :to="`/learning/path/${currentPath.id}`" class="path-continue">
            <Icon name="play" :size="14" />
            <span>继续学习</span>
          </router-link>
        </div>
      </div>
      <div v-else class="current-path-empty">
        <Icon name="route" :size="24" />
        <p>还没有进行中的学习路径</p>
        <router-link to="/learning/paths" class="section-link">
          <span>浏览学习路径</span>
          <Icon name="arrow-right" :size="14" />
        </router-link>
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
          </div>
        </router-link>
        <div v-if="continueDocs.length === 0" class="empty-state">
          <Icon name="book-open" :size="24" />
          <p>暂无学习记录，去知识库看看吧</p>
        </div>
      </div>
    </section>

    <!-- Architecture-1：我的应用（Registry 驱动，只显示已安装+启用的前 6 个 —— §162） -->
    <section class="apps-section reveal-item" style="--reveal-index: 7;">
      <div class="section-header">
        <h2 class="section-title">我的应用</h2>
        <router-link to="/apps" class="section-more">应用中心 →</router-link>
      </div>
      <div v-if="homeApps.length > 0" class="home-apps">
        <router-link
          v-for="app in homeApps"
          :key="app.key"
          :to="app.route"
          class="home-app"
          :title="app.description"
        >
          <span class="home-app-icon"><Icon :name="app.icon" :size="18" /></span>
          <span class="home-app-name">{{ app.name }}</span>
        </router-link>
      </div>
      <div v-else class="home-apps-empty">
        <p>还没有安装学习应用</p>
        <router-link to="/apps" class="section-more">去应用中心看看 →</router-link>
      </div>
    </section>

    <section class="report-section">
      <router-link
        :to="{
          path: '/learning/report',
          query: {
            period: (userStats?.streakDays ?? 0) >= 7 ? 'week' : 'month',
            source: 'home'
          }
        }"
        class="report-btn"
      >
        <Icon name="bar-chart-2" :size="16" />
        <span>查看完整学习报告</span>
        <Icon name="arrow-right" :size="14" />
      </router-link>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import ReviewDueCard from '@/components/home/ReviewDueCard.vue';
import WeakPointCard from '@/components/home/WeakPointCard.vue';
import { useAppStore } from '@/stores/apps';
import { useAuthStore } from '@/stores/auth';
import { useWalletStore } from '@/stores/wallet';
import NextActionCard from '@/components/workflow/NextActionCard.vue';
import LearningStateCard from '@/components/workflow/LearningStateCard.vue';
import { resolveNextAction } from '@/learning/action-priority';
import { actionToCta } from '@/learning/route';
import { diagnoseLearningState } from '@/learning/intelligence/diagnosis';
import type { NextLearningAction } from '@/learning/types';
import type { LearningDiagnosis } from '@/learning/intelligence/types';
import { docsApi, learningApi, userApi } from '@/api';
import { walletApi } from '@/api/wallet';
import { notify, getApiError } from '@/utils/toast';
import { useCountUp } from '@/composables/useCountUp';
import type {
  DocVO,
  KnowledgeMasteryVO,
  LearningPathVO,
  LearningTaskVO,
  UserStatsVO,
} from '@/api/types';

const auth = useAuthStore();
const appStore = useAppStore();
const walletStore = useWalletStore();
const router = useRouter();

/** Architecture-5：真实钱包（Coin/XP/Level 服务端真源 §39/§181） */
const wallet = computed(() => walletStore.wallet);

/* ===== Architecture-5 §35-38：今日奖励（reward_ledger 真实数据，非前端累加 §40） ===== */
const todayXp = ref(0)
const todayCoin = ref(0)
const todayRewardLoaded = ref(false)

async function loadTodayReward(): Promise<void> {
  if (!auth.isLoggedIn) return
  try {
    const ledger = await walletApi.getLedger({ page: 1, size: 100 })
    const today = new Date().toDateString()
    let xp = 0
    let coin = 0
    for (const l of ledger) {
      if (l.direction !== 'CREDIT') continue
      const d = new Date(l.createTime)
      if (d.toDateString() !== today) continue
      if (l.currency === 'XP') xp += l.amount
      else if (l.currency === 'COIN') coin += l.amount
    }
    todayXp.value = xp
    todayCoin.value = coin
  } catch {
    // 奖励流水失败静默，不阻断首页（§211）
  } finally {
    todayRewardLoaded.value = true
  }
}

/** Architecture-1：首页「我的应用」——只取已安装且启用的前 6 个（Registry 驱动，§139/§162） */
const homeApps = computed(() => appStore.enabledApps.slice(0, 6));

/** Architecture-4：唯一 Next Action 决策入口（§69/§179）——Review > Task > Weak > Path > Explore */
const nextAction = computed<NextLearningAction>(() =>
  resolveNextAction({
    reviewRequired: reviewRequired.value,
    weakPoints: weakPoints.value,
    tasks: tasks.value,
    currentPath: learningPaths.value.find((p) => p.enrolled) ?? null,
    installedApps: appStore.enabledApps,
  }),
);

/** Architecture-6：学习状态诊断（§6.5/§123-125）——前端本地纯函数推导，复用首页已加载的真实数据，
 *  不新增 API（§124-129）。生产环境仅 reviewRequired / weakPoints 触发 REVIEW_OVERDUE / WEAK_KNOWLEDGE，
 *  其余诊断需真实 mapping 才触发，绝不伪造（§68/§148/§239）。 */
const homeDiagnoses = computed<LearningDiagnosis[]>(() =>
  diagnoseLearningState({
    reviewRequired: reviewRequired.value,
    weakPoints: weakPoints.value,
    tasks: tasks.value,
    currentPath: currentPath.value,
  }),
);

const userName = computed(() => auth.user?.nickname || auth.user?.username || '探索者');
const userStats = ref<UserStatsVO | null>(null);
const tasks = ref<LearningTaskVO[]>([]);
const learningPaths = ref<LearningPathVO[]>([]);
const continueDocs = ref<DocVO[]>([]);

/** Architecture-5：当前学习路径 = 已报名且已开始的路径（后端填充 progress，§45/§57） */
const currentPath = computed(() => learningPaths.value.find((p) => p.enrolled && (p.progress ?? 0) > 0) ?? null);
const pathPercent = computed(() => Math.min(100, Math.round(currentPath.value?.progress ?? 0)));

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
const streakDays = computed(() => userStats.value?.streakDays ?? 0);

// P1-3：数字 count-up 动画（连击天数）
const { display: streakDaysDisplay } = useCountUp(streakDays);

// P1-1：动态 CTA —— 基于用户学习状态给出最相关的下一步动作
const dynamicTagline = computed(() => {
  if (!auth.isLoggedIn) return '登录后开启你的智能学习之旅';
  if (studyHours.value === 0) return '从第一篇文档开始，构建你的知识图谱';
  if (continueDocs.value.length > 0) return '继续上次的进度，保持学习节奏';
  if (streakDays.value >= 7) return '坚持就是胜利，你已连续学习一周';
  return '今天的知识点，正在等你探索';
});

const dynamicCta = computed(() => {
  // 未登录 → 引导探索
  if (!auth.isLoggedIn) {
    return {
      primary: { text: '探索学习路径', icon: 'route', path: '/learning/paths' },
      secondary: { text: '浏览知识库', icon: 'book-open', path: '/categories' },
    };
  }
  // 已登录 → 与「下一步」卡片共用同一优先级来源（Architecture-4 §69/§180），不再各自定义
  const primary = actionToCta(nextAction.value);
  const secondary = primary.path === '/learning/center'
    ? { text: '浏览学习路径', icon: 'route', path: '/learning/paths' }
    : { text: '查看待办', icon: 'list', path: '/learning/center' };
  return { primary, secondary };
});

/* ========== 今日待复习（真实接口 /learning/mastery/review-required）========== */
const reviewRequired = ref<KnowledgeMasteryVO[]>([]);
const reviewLoading = ref(false);
const reviewError = ref('');

async function loadReviewRequired() {
  if (!auth.isLoggedIn) return;
  reviewLoading.value = true;
  reviewError.value = '';
  try {
    reviewRequired.value = await learningApi.knowledgeMasteryReviewRequired();
  } catch (e) {
    reviewError.value = getApiError(e, '待复习数据加载失败');
  } finally {
    reviewLoading.value = false;
  }
}

/* ========== 薄弱知识点（真实接口 /learning/mastery/weak）========== */
const weakPoints = ref<KnowledgeMasteryVO[]>([]);
const weakLoading = ref(false);
const weakError = ref('');

async function loadWeakPoints() {
  if (!auth.isLoggedIn) return;
  weakLoading.value = true;
  weakError.value = '';
  try {
    weakPoints.value = await learningApi.knowledgeMasteryWeak();
  } catch (e) {
    weakError.value = getApiError(e, '薄弱知识点加载失败');
  } finally {
    weakLoading.value = false;
  }
}

/** 供卡片的行为回调统一走路由跳转（ emit action，不在卡片内做业务） */
function goTo(path: string) {
  router.push(path);
}

const doneTasks = computed(() => tasks.value.filter((t) => t.status === 1).length);

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
    appStore.hydrate();
  }
  // Architecture-5 §47/§126：全部接口并行加载，任一失败不影响其余区块（各卡片自带 ErrorState）
  await Promise.allSettled([
    (async () => {
      if (!auth.isLoggedIn) return;
      try {
        userStats.value = await userApi.stats();
      } catch {
        userStats.value = null;
      }
    })(),
    (async () => {
      if (!auth.isLoggedIn) return;
      try {
        tasks.value = await learningApi.tasks();
      } catch {
        tasks.value = [];
      }
    })(),
    (async () => {
      try {
        learningPaths.value = await learningApi.paths();
      } catch {
        learningPaths.value = [];
      }
    })(),
    (async () => {
      try {
        continueDocs.value = await docsApi.recent();
      } catch {
        continueDocs.value = [];
      }
    })(),
    loadReviewRequired(),
    loadWeakPoints(),
    loadTodayReward(),
    (async () => {
      if (!auth.isLoggedIn) return;
      void walletStore.fetchWallet(true);
    })(),
  ]);
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
  /* 不对称渐变：主色偏左上，右下留出暖橘呼吸空间 */
  background: linear-gradient(135deg, var(--kb-primary) 0%, rgba(59,111,224,0.92) 55%, rgba(59,111,224,0.85) 100%);
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
  /* 网格不对称偏移，打破规整感 */
  mask-image: linear-gradient(135deg, black 30%, transparent 80%);
  -webkit-mask-image: linear-gradient(135deg, black 30%, transparent 80%);
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

/* 暖橘色光晕：signature accent 在 Hero 区的呼吸点 */
.banner-blur-warm {
  position: absolute;
  bottom: -80px;
  left: 20%;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(255,107,53,0.18) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

.banner-content {
  position: relative;
  z-index: 1;
  padding: 36px 32px;
}

.banner-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
}

.welcome-info {
  flex: 1 1 320px;
  min-width: 0;
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
  backdrop-filter: blur(8px);
}

.welcome-title {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 900;
  color: #ffffff;
  letter-spacing: -0.03em;
  line-height: 1.15;
  margin-bottom: 8px;
  text-wrap: balance;
}

/* Hero 副标题：杂志感引文风格 */
.welcome-tagline {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 400;
  color: rgba(255,255,255,0.78);
  margin-bottom: 20px;
  line-height: 1.5;
  max-width: 420px;
}

.welcome-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* 统计 pill：胶囊式浮动卡片，替代原来的平铺文字 */
.stat-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.9);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,0.15);
  transition: background 0.2s ease, transform 0.2s ease;
}

.stat-pill:hover {
  background: rgba(255,255,255,0.18);
  transform: translateY(-1px);
}

/* 连击 pill 使用 signature highlight 色 */
.stat-pill.highlight-badge {
  background: var(--kb-highlight-soft);
  color: var(--kb-highlight);
  border-color: var(--kb-highlight-border);
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
}

.stat-pill.highlight-badge .stat-value {
  color: var(--kb-highlight);
}

.stat-unit {
  font-size: 12px;
  opacity: 0.85;
}

.banner-cta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
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

.action-btn.focus {
  background: rgba(255,255,255,0.08);
  color: #a5b4fc;
  border: 1px solid rgba(165,180,252,0.35);
}

.action-btn.focus:hover {
  background: rgba(165,180,252,0.18);
  border-color: rgba(165,180,252,0.6);
  transform: translateY(-1px);
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

/* Architecture-1：我的应用区（Registry 驱动） */
.section-more {
  font-size: 13px;
  color: var(--kb-primary);
  text-decoration: none;
  transition: opacity 0.15s;
}
.section-more:hover {
  opacity: 0.75;
}
.apps-section {
  margin-top: 32px;
}
/* Architecture-4：下一步卡片 */
.next-section {
  margin-bottom: 24px;
}
/* Architecture-6：学习状态卡片（第二层） */
.state-section {
  margin-bottom: 24px;
}
.home-apps {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}
.home-app {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  text-decoration: none;
  transition: border-color 0.15s;
}
.home-app:hover {
  border-color: var(--kb-primary-muted);
}
.home-app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  flex-shrink: 0;
}
.home-app-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.home-apps-empty {
  padding: 20px;
  border: 1px dashed var(--kb-border);
  border-radius: var(--kb-radius-md);
  text-align: center;
  color: var(--kb-muted-foreground);
  font-size: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}
@media (max-width: 640px) {
  .home-apps {
    grid-template-columns: repeat(2, 1fr);
  }
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

/* ========== Architecture-5：今日学习状态（§215-216） ========== */
.status-section {
  margin-bottom: 20px;
}
.status-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.status-item {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 14px 16px;
}
.status-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-caption);
  font-weight: 600;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.02em;
  margin-bottom: 8px;
}
.status-head svg {
  color: var(--kb-primary);
}
.status-value {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.2;
}
.status-unit {
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  color: var(--kb-muted-foreground);
}
.status-muted {
  color: var(--kb-muted-foreground);
  font-size: 18px;
}
.today-reward-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 0;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
}
.today-reward-line svg {
  color: var(--kb-warning);
}
.today-reward-line span {
  color: var(--kb-warning);
  font-weight: 600;
}
@media (max-width: 768px) {
  .status-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 欢迎横幅内的 Coin / XP 轻量展示（§62） */
.stat-pill.coin-pill {
  background: rgba(245, 158, 11, 0.1);
  color: var(--kb-warning);
}
.stat-pill.xp-pill {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

/* ========== Architecture-5：待复习 + 薄弱并排 ========== */
.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}
@media (max-width: 768px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}

/* ========== Architecture-5：当前学习路径（§57） ========== */
.current-path-section {
  margin-bottom: 20px;
}
.current-path-card {
  width: auto;
  display: flex;
  align-items: stretch;
}
.current-path-card .path-cover {
  width: 120px;
  height: auto;
  flex-shrink: 0;
}
.current-path-card .path-content {
  flex: 1;
  padding: 20px;
}
.path-continue {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  padding: 8px 18px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: opacity 0.15s ease;
}
.path-continue:hover {
  opacity: 0.9;
}
.current-path-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px;
  background: var(--kb-card);
  border: 1px dashed var(--kb-border);
  border-radius: var(--kb-radius-md);
  color: var(--kb-muted-foreground);
}
.current-path-empty svg {
  opacity: 0.5;
}
</style>
