<template>
  <div class="home-container ui-v2-home">
    <!-- ══════════ 顶部：欢迎语（§七：降低视觉权重，不再用巨大 Hero Banner 占据首屏）══════════ -->
    <header class="home-greeting">
      <div class="home-greeting-main">
        <p class="home-eyebrow">学习工作台</p>
        <h1 class="home-greeting-title">{{ greeting }}，{{ userName }}</h1>
        <p class="home-greeting-sub">{{ todayStr }}</p>
      </div>
      <div class="home-greeting-aside">
      <StudioVisual />
      <div class="home-greeting-meta">
        <span v-if="streakDays > 0" class="meta-pill">
          <Icon name="flame" :size="14" />
          <span class="tabular-nums">{{ streakDaysDisplay }}</span>
          <span>天连击</span>
        </span>
        <span v-if="auth.isLoggedIn && wallet" class="meta-pill">
          <Icon name="coins" :size="14" />
          <span class="tabular-nums">{{ formatCoin(wallet) }}</span>
          <span>Coin</span>
        </span>
        <span v-if="auth.isLoggedIn && wallet" class="meta-pill">
          <Icon name="zap" :size="14" />
          <span class="tabular-nums">{{ wallet.xp }}</span>
          <span>XP · Lv{{ wallet.level }}</span>
        </span>
      </div>
      </div>
    </header>


    <!-- 整体加载失败：只影响学习数据区块，给出明确错误与重试（§十七） -->
    <!-- UI/UX 5.1 §六：补齐「是否影响当前页面 / 怎么解决」，不再只给一句「学习数据加载失败」 -->
    <StateView
      v-if="learning.error"
      status="error"
      error-title="学习数据加载失败"
      :error-description="learning.error"
      error-scope="只是学习相关的区块没加载出来，游览其他页面不受影响。"
      error-hint="稍后重试；若持续出现请联系管理员。"
      @retry="reloadLearning"
    />

    <template v-else>
      <!-- UI/UX 5.1 §四：首屏加载用**内容形态**骨架（目标条 → 下一步卡 → 今日任务 → 路径）
           而不是一个居中转圈 —— 让用户先看到「这一页大概长什么样」。 -->
      <template v-if="learning.loading && !learning.loaded">
        <div class="home-skeleton" aria-busy="true" aria-label="首页内容加载中">
          <div class="hs-block hs-goal"></div>
          <div class="hs-block hs-next"></div>
          <div class="hs-row">
            <div class="hs-block hs-half"></div>
            <div class="hs-block hs-half"></div>
          </div>
        </div>
      </template>
      <template v-else>
      <!-- 行动先于辅助目标说明，DOM 与视觉顺序一致。仍消费唯一 learning Store。 -->
      <section class="home-section next-section" aria-label="下一步学习行动">
        <NextActionCard :action="nextAction" dismissible />
      </section>
      <!-- ══════════ 1. 当前目标（让「下一步」有上文：我为什么学）══════════ -->
      <section class="home-section home-goal-context">
        <div class="section-header">
          <h2 class="section-title">当前目标</h2>
          <router-link to="/learning/goals" class="section-link">
            <span>{{ activeGoal ? '管理目标' : '设定目标' }}</span>
            <Icon name="arrow-right" :size="14" />
          </router-link>
        </div>

        <StateView
          :status="goalStatus"
          type="line"
          :rows="2"
          empty-icon="target"
          empty-title="还没有进行中的学习目标"
          empty-variant="info"
          @retry="reloadLearning"
        >
          <template #empty-description>
            <p>设定一个学习目标，系统才知道你「为什么学、下一步该做什么」。</p>
          </template>
          <template #empty-action>
            <Button variant="primary" icon-name="flag" @click="goTo('/learning/goals')">
              设定学习目标
            </Button>
          </template>

          <div class="goal-strip">
            <span class="goal-strip-icon"><Icon name="target" :size="18" /></span>
            <div class="goal-strip-main">
              <div class="goal-strip-top">
                <router-link
                  :to="activeGoal?.pathId ? `/learning/path/${activeGoal.pathId}` : '/learning/goals'"
                  class="goal-strip-title"
                >{{ activeGoal?.title }}</router-link>
                <span v-if="activeGoal?.deadline" class="goal-deadline">
                  <Icon name="calendar" :size="12" />
                  {{ goalDeadlineText }}
                </span>
                <span v-if="activeGoal?.pathId" class="goal-pct tabular-nums">{{ goalPathPercent }}%</span>
              </div>
              <div class="goal-strip-sub">
                <template v-if="activeGoal?.pathId">
                  <Icon name="route" :size="13" />
                  <span>{{ activeGoal.pathTitle || '学习路线' }}</span>
                  <span v-if="goalLearningNodeTitle" class="goal-node">· 当前：{{ goalLearningNodeTitle }}</span>
                  <span v-if="goalKnowledgeTitle" class="goal-node">· 知识点：{{ goalKnowledgeTitle }}</span>
                </template>
                <template v-else>
                  <span>还没有关联学习路线 ——</span>
                  <router-link to="/learning/goals" class="goal-inline-link">生成 / 选择一条路线</router-link>
                </template>
              </div>
              <div v-if="activeGoal?.pathId" class="goal-progress">
                <div class="goal-progress-fill" :style="{ width: goalPathPercent + '%' }"></div>
              </div>
            </div>
          </div>
        </StateView>
      </section>

      <!-- ══════════ 3. 今日学习状态（真实数据汇总，只保留影响「今天学什么」的信息）══════════ -->
      <section class="home-section">
        <div class="section-header">
          <h2 class="section-title">今日学习状态</h2>
          <router-link to="/learning/report" class="section-link">
            <span>学习报告</span>
            <Icon name="arrow-right" :size="14" />
          </router-link>
        </div>
        <div class="status-grid">
          <div class="status-item">
            <div class="status-head">
              <Icon name="list-checks" :size="14" />
              <span>今日任务</span>
            </div>
            <p class="status-value tabular-nums">
              {{ doneTasks }}<span class="status-unit"> / {{ tasks.length }} 已完成</span>
            </p>
          </div>
          <div class="status-item">
            <div class="status-head">
              <Icon name="rotate-ccw" :size="14" />
              <span>待复习</span>
            </div>
            <p class="status-value tabular-nums">
              {{ reviewRequired.length }}<span class="status-unit"> 个知识点</span>
            </p>
          </div>
          <div class="status-item">
            <div class="status-head">
              <Icon name="trending-down" :size="14" />
              <span>薄弱知识</span>
            </div>
            <p class="status-value tabular-nums">
              {{ weakPoints.length }}<span class="status-unit"> 个知识点</span>
            </p>
          </div>
          <div class="status-item">
            <div class="status-head">
              <Icon name="route" :size="14" />
              <span>当前路径</span>
            </div>
            <p v-if="currentPath" class="status-value tabular-nums">
              {{ pathPercent }}<span class="status-unit">% 进度</span>
            </p>
            <p v-else class="status-value status-muted">—</p>
          </div>
        </div>
        <div v-if="tasks.length > 0" class="today-tasks">
          <button
            v-for="task in tasks.slice(0, 5)"
            :key="(task.source || 'LEARNING')+task.id"
            type="button"
            class="today-task"
            :class="{ done: task.status === 1 }"
            :aria-pressed="task.status === 1"
            @click="toggleTaskStatus(task)"
          >
            <span class="today-task-check" :aria-hidden="true">
              <Icon v-if="task.status === 1" name="check" :size="12" />
            </span>
            <span class="today-task-title">{{ task.title }}</span>
          </button>
        </div>
        <p v-if="todayRewardLoaded && (todayXp > 0 || todayCoin > 0)" class="today-reward-line">
          <Icon name="zap" :size="12" />
          今日已获得 <span class="tabular-nums">+{{ todayXp }} XP</span> ·
          <span class="tabular-nums">+{{ todayCoin }} Coin</span>（学习奖励）
        </p>
      </section>

      <!-- ══════════ 4. 当前路径 ══════════ -->
      <section class="home-section">
        <div class="section-header">
          <h2 class="section-title">当前路径</h2>
          <router-link to="/learning/paths" class="section-link">
            <span>浏览路径</span>
            <Icon name="arrow-right" :size="14" />
          </router-link>
        </div>
        <StateView
          :status="pathStatus"
          type="card"
          :rows="1"
          empty-icon="route"
          empty-title="还没有进行中的学习路径"
          @retry="reloadLearning"
        >
          <template #empty-description>
            <p>选一条路线并报名，首页就会显示你的进度和下一步。</p>
          </template>
          <template #empty-action>
            <Button variant="primary" icon-name="route" @click="goTo('/learning/paths')">
              浏览学习路径
            </Button>
          </template>

          <div v-if="currentPath" class="path-row">
            <div class="path-row-main">
              <span class="path-badge" :class="levelBadge(currentPath.level).cls">
                {{ levelBadge(currentPath.level).text }}
              </span>
              <h3 class="path-row-title">{{ currentPath.title }}</h3>
              <p class="path-row-desc">{{ currentPath.description || '系统化学习路径，循序渐进掌握技能。' }}</p>
            </div>
            <div class="path-row-progress">
              <div class="progress-head">
                <span class="tabular-nums">{{ pathPercent }}%</span>
                <span class="path-chapters tabular-nums">{{ currentPath.chapterCount || 0 }} 章</span>
              </div>
              <div class="progress-bar">
                <div class="progress-fill" :style="`width: ${pathPercent}%`"></div>
              </div>
              <Button variant="secondary" icon-name="play" @click="goTo(`/learning/path/${currentPath.id}`)">
                继续学习
              </Button>
            </div>
          </div>
        </StateView>
      </section>

      <!-- ══════════ 5. 最近学习 ══════════ -->
      <section class="home-section">
        <div class="section-header">
          <h2 class="section-title">最近学习</h2>
          <router-link to="/categories" class="section-link">
            <span>查看全部</span>
            <Icon name="arrow-right" :size="14" />
          </router-link>
        </div>
        <StateView
          :status="recentStatus"
          type="card"
          :rows="3"
          empty-icon="book-open"
          empty-title="暂无学习记录"
          @retry="reloadRecent"
        >
          <template #empty-description>
            <p>还没有读过任何文档。去知识库挑一篇开始，这里会显示你最近的进度。</p>
          </template>
          <template #empty-action>
            <Button variant="primary" icon-name="library" @click="goTo('/knowledge')">
              去知识库看看
            </Button>
          </template>

          <div class="recent-list">
            <router-link
              v-for="doc in continueDocs.slice(0, 6)"
              :key="doc.id"
              :to="`/doc/${doc.id}`"
              class="recent-item"
            >
              <span class="recent-thumb" :style="{ background: docIconBg(doc.id) }">
                <Icon name="file-text" :size="18" :style="{ color: docIconColor(doc.id) }" />
              </span>
              <span class="recent-main">
                <span class="recent-title">{{ doc.title }}</span>
                <span class="recent-category">
                  <Icon name="folder" :size="12" />
                  {{ doc.categoryName || '知识库' }}
                </span>
              </span>
              <span class="recent-time">{{ formatTime(doc.createTime) }}</span>
            </router-link>
          </div>
        </StateView>
      </section>

      <!-- ══════════ 6. 薄弱知识 ══════════ -->
      <section class="home-section">
        <div class="section-header">
          <h2 class="section-title">薄弱知识</h2>
          <router-link to="/learning/mastery" class="section-link">
            <span>掌握度详情</span>
            <Icon name="arrow-right" :size="14" />
          </router-link>
        </div>
        <StateView
          :status="weakStatus"
          type="list"
          :rows="3"
          empty-icon="trending-down"
          empty-title="暂无薄弱知识"
          empty-variant="success"
          @retry="reloadWeak"
        >
          <template #empty-description>
            <p>当前还没有足够的学习行为数据。</p>
            <p>完成几次练习后，系统会开始分析你的薄弱点。</p>
          </template>
          <template #empty-action>
            <Button variant="primary" icon-name="zap" @click="goTo('/learning/quiz')">
              开始学习
            </Button>
          </template>

          <div class="weak-list">
            <router-link
              v-for="w in weakPoints.slice(0, 5)"
              :key="w.knowledgeId"
              :to="`/learning/knowledge-mastery/${w.knowledgeId}`"
              class="weak-item"
            >
              <span class="weak-name">{{ w.name || '未命名知识点' }}</span>
              <span class="weak-score tabular-nums">{{ Math.round(w.masteryScore) }}%</span>
              <span class="weak-bar">
                <span class="weak-bar-fill" :style="{ width: Math.min(100, Math.max(0, w.masteryScore)) + '%' }"></span>
              </span>
            </router-link>
          </div>
        </StateView>
      </section>
      </template>
    </template>
    <InterviewSeedCard />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import StudioVisual from '@/components/ui/StudioVisual.vue';
import InterviewSeedCard from '@/components/Learning/InterviewSeedCard.vue';
import Button from '@/components/ui/Button.vue';
import StateView from '@/components/ui/StateView.vue';
import { useAsyncResource, type AsyncStatus } from '@/composables/useAsyncResource';
import { useAppStore } from '@/stores/apps';
import { useAuthStore } from '@/stores/auth';
import { useWalletStore } from '@/stores/wallet';
import { useLearningStore } from '@/stores/learning';
import NextActionCard from '@/components/workflow/NextActionCard.vue';
import { useCountUp } from '@/composables/useCountUp';
import { docsApi, userApi } from '@/api';
import { walletApi } from '@/api/wallet';
import { notify } from '@/utils/toast';
import { formatCoin } from '@/economy/format-balance';
import type {
  DocVO,
  LearningTaskVO,
  UserStatsVO,
} from '@/api/types';

const auth = useAuthStore();
const appStore = useAppStore();
const walletStore = useWalletStore();
/** UI/UX 5.0：全站唯一学习状态来源（§三：首页不再自算一套 NextAction） */
const learning = useLearningStore();
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

/** UI/UX 5.0 §三：NextAction 唯一来源 —— 首页不再自算，直接读 store（与学习中心/AI Coach 同源） */
const nextAction = computed(() => learning.nextAction);

/* ===== 统一三态（§十七：每个区块必须有 loading / empty / error，禁止白屏与空白卡片） ===== */
type Status = AsyncStatus

/** 学习核心数据首次加载中 */
const learningLoading = computed(() => learning.loading)

const goalStatus = computed<Status>(() => {
  if (learningLoading.value) return learning.loaded ? 'retrying' : 'loading'
  if (learning.goalError) return 'error'
  return learning.activeGoal ? 'ready' : 'empty'
})

const pathStatus = computed<Status>(() => {
  if (learningLoading.value) return learning.loaded ? 'retrying' : 'loading'
  if (learning.pathsError) return 'error'
  return learning.currentPath ? 'ready' : 'empty'
})

const weakStatus = computed<Status>(() => {
  if (learningLoading.value) return learning.loaded ? 'retrying' : 'loading'
  if (learning.weakError) return 'error'
  return learning.weakPoints.length > 0 ? 'ready' : 'empty'
})

const recentResource = useAsyncResource(() => docsApi.recent(), docs => docs.length === 0, '最近学习记录加载失败', 'section')
const recentStatus = recentResource.status

/** 重新拉取学习核心数据（错误态重试入口） */
function reloadLearning(): void {
  void learning.load(true)
}

const userName = computed(() => auth.user?.nickname || auth.user?.username || '探索者');
const userStats = ref<UserStatsVO | null>(null);

/** 「最近学习」文档：首页独有数据，独立 loading/error，不阻塞学习核心区块 */
const continueDocs = computed<DocVO[]>(() => recentResource.data.value ?? []);

async function loadRecentDocs(retry = false): Promise<void> {
  await recentResource.run(retry);
}

function reloadRecent(): void {
  void loadRecentDocs(true);
}

function reloadWeak(): void {
  void learning.reloadWeak();
}

/* ===== 学习核心数据：全部来自唯一 store，禁止本页再自行拉取/推导（§三） ===== */
const tasks = computed(() => learning.tasks);
const reviewRequired = computed(() => learning.reviewRequired);
const weakPoints = computed(() => learning.weakPoints);
const activeGoal = computed(() => learning.activeGoal);
const currentPath = computed(() => learning.currentPath);
const pathPercent = computed(() => learning.currentPathPercent);
const doneTasks = computed(() => learning.doneTaskCount);
/** Architecture-4.2：Goal → 当前知识点上下文（与学习中心 / AI Coach 同源） */
const goalKnowledgeTitle = computed(() => learning.context.knowledgeTitle);
const goalLearningNodeTitle = computed(() => learning.context.learningNodeTitle);

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

const streakDays = computed(() => userStats.value?.streakDays ?? 0);

// P1-3：数字 count-up 动画（连击天数）
const { display: streakDaysDisplay } = useCountUp(streakDays);

/** 供卡片的行为回调统一走路由跳转（ emit action，不在卡片内做业务） */
function goTo(path: string) {
  router.push(path);
}

/* ========== Architecture-4.0：当前学习目标（学习闭环起点）—— 数据取自唯一 store ========== */
const goalPathPercent = computed(() =>
  activeGoal.value?.pathId != null
    ? Math.min(100, Math.max(0, Math.round(activeGoal.value.pathProgress ?? 0)))
    : 0,
);

const goalDeadlineText = computed(() => {
  const d = activeGoal.value?.deadline;
  if (!d) return '';
  const t = new Date(d + 'T00:00:00');
  const diff = Math.ceil((t.getTime() - Date.now()) / 86400000);
  if (diff < 0) return `已逾期 ${-diff} 天`;
  if (diff === 0) return '今天截止';
  if (diff === 1) return '明天截止';
  return `${t.getMonth() + 1}/${t.getDate()} 截止`;
});

function levelBadge(level?: string) {
  switch (level) {
    case 'BEGINNER': return { cls: 'badge-beginner', text: '入门' };
    case 'ADVANCED': return { cls: 'badge-advanced', text: '高级' };
    default: return { cls: 'badge-intermediate', text: '进阶' };
  }
}

const docColors = [
  { bg: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' },
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

/**
 * 切换今日任务完成状态（§十八：点击后必须立刻看到结果）。
 * 状态流转收口到 learning store，首页只负责调用与反馈，不本地改数组。
 */
async function toggleTaskStatus(task: LearningTaskVO): Promise<void> {
  try {
    await learning.toggleTask(task);
    if (task.status !== 1) notify('任务已完成', 'success');
  } catch {
    notify('任务状态更新失败，请重试', 'error');
  }
}

onMounted(async () => {
  if (auth.isLoggedIn) {
    appStore.hydrate();
  }
  // UI/UX 5.0：学习核心数据统一由 learning store 加载（唯一真源，§三）；
  // 首页独有数据（最近学习 / 统计 / 钱包 / 今日奖励）并行加载，任一失败不影响其余区块。
  await Promise.allSettled([
    learning.load(),
    loadRecentDocs(),
    (async () => {
      if (!auth.isLoggedIn) return;
      try {
        userStats.value = await userApi.stats();
      } catch {
        userStats.value = null;
      }
    })(),
    loadTodayReward(),
    (async () => {
      if (!auth.isLoggedIn) return;
      void walletStore.fetchWallet(true);
    })(),
  ]);
});
</script>

<style scoped>
/*
 * UI/UX 5.0 §五/§六：统一视觉语言
 *   - 8pt 间距基线（--kb-space-*）
 *   - 卡片圆角统一 8~16px（--kb-radius-md / --kb-radius-lg），不再出现 20px 英雄区圆角
 *   - 主色只用于「主要操作」，图标默认中性色（§五：禁止业务分类各用一种彩色图标）
 *   - 能用普通 section 表达的不再包 Card（§六：减少卡片化）
 */
.home-container {
  padding: 0;
  max-width: none;
  margin-inline: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-6, 24px);
  animation: pageFadeIn 0.3s ease-out;

  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
}

@keyframes pageFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* ══════════ 顶部欢迎语：降权为一行标题 + 轻量 meta（§七：不再用巨大 Hero Banner）══════════ */
.home-greeting {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--kb-space-4, 16px);
  flex-wrap: wrap;
  padding-bottom: var(--kb-space-2, 8px);
}
.home-greeting-title {
  margin: 0;
  font-size: var(--kb-fs-h2);
  font-weight: 600;
  line-height: 1.3;
  color: var(--kb-foreground);
}
.home-eyebrow {
  margin: 0 0 var(--kb-space-2);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  letter-spacing: 0.08em;
}
.home-greeting-sub {
  margin: 2px 0 0;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-muted-foreground);
}
.home-greeting-meta {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2, 8px);
  flex-wrap: wrap;
}
.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-card);
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.meta-pill :deep(svg) {
  color: var(--kb-muted-foreground);
}

/* ══════════ 通用 section ══════════ */
.home-section {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-3, 12px);
}
.home-goal-context :deep(.py-16) {
  padding-top: var(--kb-space-4);
  padding-bottom: var(--kb-space-4);
}
.home-goal-context :deep(.w-16) {
  display: none;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-3, 12px);
}
.section-title {
  margin: 0;
  font-size: var(--kb-fs-h4, 16px);
  font-weight: 600;
  color: var(--kb-foreground);
}
.section-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-primary);
  text-decoration: none;
  transition: opacity 0.15s ease;
}
.section-link:hover {
  opacity: 0.8;
}

/* ══════════ 1. 当前目标（普通 section + 左侧品牌色竖线，不套 Card）══════════ */
.goal-strip {
  display: flex;
  align-items: flex-start;
  gap: var(--kb-space-3, 12px);
  padding: var(--kb-space-4, 16px);
  border: 1px solid var(--kb-border);
  border-left: 3px solid var(--kb-primary);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-card);
}
.goal-strip-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-primary-subtle);
  color: var(--kb-primary);
}
.goal-strip-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.goal-strip-top {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2, 8px);
  flex-wrap: wrap;
}
.goal-strip-title {
  font-size: var(--kb-fs-body-lg, 16px);
  font-weight: 600;
  color: var(--kb-foreground);
  text-decoration: none;
}
.goal-strip-title:hover {
  color: var(--kb-primary);
}
.goal-deadline,
.goal-pct {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.goal-pct {
  color: var(--kb-primary);
  font-weight: 600;
}
.goal-strip-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-muted-foreground);
}
.goal-node {
  color: var(--kb-muted-foreground);
}
.goal-inline-link {
  color: var(--kb-primary);
  text-decoration: none;
}
.goal-inline-link:hover {
  text-decoration: underline;
}
.goal-progress {
  height: 4px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
  margin-top: 2px;
}
.goal-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--kb-primary);
  transition: width 0.3s ease;
}

/* ══════════ 2. NextAction：首页视觉重心（§七）═════════ */
.next-section :deep(.next-action-card) {
  border-radius: var(--kb-radius-lg, 12px);
}

/* ══════════ 3. 今日学习状态 ══════════ */
.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
  overflow: hidden;
}
.status-item {
  padding: var(--kb-space-3, 12px) var(--kb-space-4, 16px);
  border-right: 1px solid var(--kb-border);
  padding: var(--kb-space-5, var(--kb-space-4));
}
.status-item:last-child { border-right: none; }
.status-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.status-head :deep(svg) {
  color: var(--kb-muted-foreground);
}
.status-value {
  margin: 6px 0 0;
  font-size: var(--kb-fs-h3, 20px);
  font-weight: 600;
  line-height: 1.2;
  color: var(--kb-foreground);
}
.status-unit {
  font-size: var(--kb-fs-caption, 12px);
  font-weight: 400;
  color: var(--kb-muted-foreground);
}
.status-muted {
  color: var(--kb-muted-foreground);
}
.today-tasks {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: var(--kb-space-2, 8px);
}
.today-task {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2, 8px);
  width: 100%;
  padding: 6px 8px;
  border: none;
  border-radius: var(--kb-radius-sm, 6px);
  background: transparent;
  text-align: left;
  cursor: pointer;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-foreground);
  transition: background-color 0.15s ease;
}
.today-task:hover {
  background: var(--kb-muted);
}
.today-task:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--kb-ring);
}
.today-task-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  border: 1px solid var(--kb-border);
  border-radius: 4px;
  color: transparent;
}
.today-task.done .today-task-check {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.today-task.done .today-task-title {
  color: var(--kb-muted-foreground);
  text-decoration: line-through;
}
.today-task-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.today-reward-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}

/* ══════════ 4. 当前路径 ══════════ */
.path-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-6, 24px);
  flex-wrap: wrap;
  padding: var(--kb-space-4, 16px);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md, 8px);
  background: var(--kb-card);
}
.path-row-main {
  flex: 1;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.path-row-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg, 16px);
  font-weight: 600;
  color: var(--kb-foreground);
}
.path-row-desc {
  margin: 0;
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-muted-foreground);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.path-row-progress {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-2, 8px);
  min-width: 200px;
}
.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.progress-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--kb-primary);
  transition: width 0.3s ease;
}
.path-badge {
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: var(--kb-radius-sm, 6px);
  font-size: var(--kb-fs-caption, 12px);
  font-weight: 500;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.badge-beginner {
  background: var(--kb-success-subtle);
  color: var(--kb-state-success);
}
.badge-intermediate {
  background: var(--kb-primary-subtle);
  color: var(--kb-primary);
}
.badge-advanced {
  background: var(--kb-warning-subtle);
  color: var(--kb-state-warning);
}

/* ══════════ 5. 最近学习 ══════════ */
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.recent-item {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3, 12px);
  padding: 10px var(--kb-space-3, 12px);
  border-radius: var(--kb-radius-md, 8px);
  text-decoration: none;
  transition: background-color 0.15s ease;
}
.recent-item:hover {
  background: var(--kb-muted);
}
.recent-item:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--kb-ring);
}
.recent-thumb {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: var(--kb-radius-md, 8px);
}
.recent-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.recent-title {
  font-size: var(--kb-fs-body-md, 14px);
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-category {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}
.recent-time {
  flex-shrink: 0;
  font-size: var(--kb-fs-caption, 12px);
  color: var(--kb-muted-foreground);
}

/* ══════════ 6. 薄弱知识 ══════════ */
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.weak-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 120px;
  align-items: center;
  gap: var(--kb-space-3, 12px);
  padding: 10px var(--kb-space-3, 12px);
  border-radius: var(--kb-radius-md, 8px);
  text-decoration: none;
  transition: background-color 0.15s ease;
}
.weak-item:hover {
  background: var(--kb-muted);
}
.weak-item:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--kb-ring);
}
.weak-name {
  font-size: var(--kb-fs-body-md, 14px);
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.weak-score {
  font-size: var(--kb-fs-body-sm, 13px);
  font-weight: 600;
  color: var(--kb-foreground);
}
.weak-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.weak-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  /* 薄弱 = 需要关注：用 warning 语义色，而非业务分类彩（§五） */
  background: var(--kb-warning);
}

/* ══════════ 响应式（§六：不错位、不溢出）═════════ */
@media (max-width: 900px) {
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .status-item:nth-child(2) { border-right: none; }
  .status-item:nth-child(-n + 2) { border-bottom: 1px solid var(--kb-border); }
}
@media (max-width: 640px) {
  .home-container {
    padding: 0;
    gap: var(--kb-space-4, 16px);
  }
  .weak-item {
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .weak-bar {
    display: none;
  }
  .path-row-progress {
    min-width: 100%;
  }
}

/* ══════════ UI/UX 5.1 §四：首页首屏内容形态骨架 ══════════
 * 形状对应真实区块：目标条（矮）→ 下一步卡（高）→ 今日任务/路径（两列）。
 * 目的：加载期不出现「巨大空白 + 页面跳动」。
 */
.home-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-4, 16px);
}
.hs-block {
  border-radius: var(--kb-radius-md);
  background: var(--kb-muted);
  animation: hs-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
.hs-goal { height: 64px; }
.hs-next { height: 132px; background: var(--kb-card); border: 1px solid var(--kb-border); }
.hs-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kb-space-4, 16px);
}
.hs-half { height: 160px; }
@keyframes hs-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
@media (max-width: 900px) {
  .hs-row { grid-template-columns: minmax(0, 1fr); }
  .hs-half { height: 120px; }
}
</style>
