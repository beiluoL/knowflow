<template>
  <div class="learning-mode h-screen flex flex-col" style="background: var(--kb-background);">
    <!-- ===== 精简顶部栏 ===== -->
    <div class="h-14 flex items-center justify-between px-6 shrink-0 topbar">
      <button type="button" class="flex items-center gap-2 text-sm font-medium topbar-back" @click="exitLearning">
        <Icon name="arrow-left" :size="16" />
        <span>返回</span>
      </button>
      <div class="flex items-center gap-4">
        <span class="text-sm" style="color: var(--kb-muted-foreground);">{{ courseTitle }}</span>
        <span class="px-2 py-0.5 rounded text-xs progress-chip">第 {{ currentIndex + 1 }}/{{ total }} 题</span>
      </div>
      <div class="flex items-center gap-3">
        <button type="button" class="flex items-center gap-1.5 h-8 px-3 rounded-lg text-sm pause-btn" @click="togglePause">
          <Icon :name="isPaused ? 'play' : 'pause'" :size="14" />
          {{ isPaused ? '继续' : '暂停' }}
        </button>
        <button type="button" class="p-2 rounded-lg settings-trigger" @click="toggleSettings">
          <Icon name="settings" :size="16" />
        </button>
      </div>
    </div>

    <!-- ===== 主内容区 ===== -->
    <div class="flex-1 flex flex-col items-center justify-center px-6 relative overflow-y-auto">
      <!-- 加载态 -->
      <div v-if="loading" class="text-center">
        <div class="w-12 h-12 rounded-full border-4 animate-spin mx-auto mb-4" style="border-color: var(--kb-muted); border-top-color: var(--kb-primary);"></div>
        <p class="text-sm" style="color: var(--kb-muted-foreground);">正在准备学习内容...</p>
      </div>

      <!-- 空态 -->
      <div v-else-if="cards.length === 0" class="text-center">
        <div class="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-4" style="background: var(--kb-muted);">
          <Icon name="layers" :size="32" style="color: var(--kb-muted-foreground);" />
        </div>
        <h3 class="kb-h3 mb-2">暂无学习卡片</h3>
        <p class="kb-body-sm mb-4">请先在其他页面创建闪卡或选择学习路径</p>
        <button type="button" class="px-4 py-2 rounded-lg text-sm font-medium" style="background: var(--kb-primary); color: var(--kb-primary-foreground);" @click="exitLearning">返回学习中心</button>
      </div>

      <!-- 学习内容 -->
      <template v-else>
        <!-- 计时器 -->
        <div class="mb-8 text-center">
          <div class="relative inline-flex items-center justify-center mb-4">
            <svg class="w-56 h-56 -rotate-90" viewBox="0 0 200 200">
              <circle cx="100" cy="100" r="90" fill="none" stroke="var(--kb-muted)" stroke-width="4" />
              <circle
                cx="100" cy="100" r="90" fill="none" stroke="var(--kb-primary)" stroke-width="4"
                stroke-linecap="round"
                :stroke-dasharray="timerCircumference"
                :stroke-dashoffset="timerDashoffset"
                class="transition-all duration-1000 ease-linear"
              />
            </svg>
            <div class="absolute inset-0 flex flex-col items-center justify-center">
              <div class="timer-display" style="color: var(--kb-foreground);">{{ formatTime(timeLeft) }}</div>
              <div class="text-sm mt-1" style="color: var(--kb-muted-foreground);">专注时间</div>
            </div>
          </div>
        </div>

        <!-- 当前题目 / 背诵内容 -->
        <div class="w-full max-w-3xl mb-10">
          <div class="p-8 rounded-2xl border question-card" style="background: var(--kb-card); border-color: var(--kb-border);">
            <!-- 标签组 -->
            <div class="flex items-center gap-2 mb-4">
              <span
                v-for="(tag, idx) in currentTags"
                :key="idx"
                class="px-2.5 py-1 rounded-full text-xs font-medium"
                :style="{ background: `${tag.color}14`, color: tag.color }"
              >{{ tag.label }}</span>
            </div>
            <!-- 题目标题 -->
            <h2 class="text-2xl font-bold mb-4" style="color: var(--kb-foreground);">{{ currentCard?.front }}</h2>
            <!-- 引导文案 -->
            <p class="leading-relaxed" style="color: var(--kb-muted-foreground); font-size: 15px;">
              请在脑海中组织答案，回想相关知识点。准备好后点击下方按钮，也可查看答案与提示。
            </p>
            <!-- 展开的答案 -->
            <div v-if="showAnswer" class="mt-5 p-4 rounded-lg" style="background: rgba(16,185,129,0.06); border: 1px solid rgba(16,185,129,0.2);">
              <div class="flex items-center gap-2 mb-2">
                <Icon name="check-circle" :size="16" style="color: var(--kb-accent);" />
                <span class="text-xs font-medium" style="color: var(--kb-accent);">参考答案</span>
              </div>
              <p class="text-sm leading-relaxed" style="color: var(--kb-card-foreground);">{{ currentCard?.back }}</p>
            </div>
            <!-- 提示 -->
            <div v-else class="mt-5 p-4 rounded-lg" style="background: var(--kb-background);">
              <div class="flex items-center gap-2 mb-2">
                <Icon name="lightbulb" :size="16" style="color: var(--kb-warning);" />
                <span class="text-xs font-medium" style="color: var(--kb-warning);">提示</span>
              </div>
              <p class="text-sm" style="color: var(--kb-muted-foreground);">思考相关知识点的核心概念、应用场景与常见误区</p>
            </div>
          </div>
        </div>

        <!-- 大按钮 -->
        <div class="flex items-center gap-6 mb-8">
          <button
            type="button"
            class="flex items-center gap-3 h-16 px-12 rounded-2xl text-lg font-semibold border-2 review-btn"
            @click="handleReview"
          >
            <Icon name="eye" :size="24" />
            再看看
          </button>
          <button
            type="button"
            class="flex items-center gap-3 h-16 px-12 rounded-2xl text-lg font-semibold memorized-btn"
            @click="handleMemorized"
          >
            <Icon name="check-circle" :size="24" />
            我记住了
          </button>
        </div>

        <!-- 底部进度 -->
        <div class="w-full max-w-xl">
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm" style="color: var(--kb-muted-foreground);">学习进度</span>
            <span class="text-sm font-medium" style="color: var(--kb-primary);">{{ currentIndex + 1 }} / {{ total }}</span>
          </div>
          <div class="h-2 rounded-full overflow-hidden" style="background: var(--kb-muted);">
            <div class="h-full rounded-full transition-all duration-500" :style="{ width: `${progressPercent}%`, background: 'var(--kb-primary)' }"></div>
          </div>
        </div>
      </template>
    </div>

    <!-- ===== 右下角设置浮动面板 ===== -->
    <div class="fixed bottom-6 right-6">
      <div v-if="showSettings" class="mb-3 p-5 rounded-xl border shadow-xl w-72 settings-panel" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h4 class="text-sm font-semibold mb-4" style="color: var(--kb-foreground);">学习设置</h4>
        <div class="space-y-4">
          <!-- 学习目标时长 -->
          <div>
            <label class="text-xs font-medium mb-1.5 block" style="color: var(--kb-muted-foreground);">学习目标</label>
            <div class="flex items-center gap-2">
              <button
                v-for="d in durationOptions"
                :key="d"
                type="button"
                class="flex-1 h-8 rounded-lg text-xs font-medium border duration-btn"
                :class="{ active: settings.duration === d }"
                @click="settings.duration = d"
              >{{ d }} 分钟</button>
            </div>
          </div>
          <!-- 题目数量 -->
          <div>
            <label class="text-xs font-medium mb-1.5 block" style="color: var(--kb-muted-foreground);">题目数量</label>
            <select v-model="settings.questionCount" class="w-full h-9 rounded-lg border text-sm outline-none settings-select">
              <option :value="10">10 题</option>
              <option :value="20">20 题</option>
              <option :value="0">全部</option>
              <option :value="50">50 题</option>
            </select>
          </div>
          <!-- 退出按钮 -->
          <div class="pt-2 flex gap-2">
            <button type="button" class="flex-1 h-9 rounded-lg text-sm font-medium border" style="border-color: var(--kb-border); color: var(--kb-foreground); background: var(--kb-card);" @click="togglePause">
              {{ isPaused ? '继续' : '暂停' }}
            </button>
            <button type="button" class="flex-1 h-9 rounded-lg text-sm font-medium" style="background: var(--kb-destructive); color: var(--kb-destructive-foreground);" @click="exitLearning">退出学习</button>
          </div>
        </div>
      </div>
      <button type="button" class="w-12 h-12 rounded-full flex items-center justify-center shadow-lg border settings-fab" @click="toggleSettings">
        <Icon name="settings" :size="20" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 沉浸式学习模式
 * 设计稿对齐：精简顶栏 + 大圆环计时器 + 题目卡（含标签/提示/答案展开）+ 再看看/我记住了双按钮 + 底部进度条 + 右下角设置浮窗。
 * 数据来源：闪卡接口（learningApi.flashcards），按 pathId/chapterId 查询；无数据时展示空态。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import { notify } from '@/utils/toast';
import { addSession, dateStr } from '@/utils/studySession';
import type { FlashcardVO } from '@/api/types';

const route = useRoute();
const router = useRouter();

// ===== 数据状态 =====
const loading = ref(false);
const cards = ref<FlashcardVO[]>([]);
const currentIndex = ref(0);
const showAnswer = ref(false);

const currentCard = computed(() => cards.value[currentIndex.value] || null);
const total = computed(() => cards.value.length);
const progressPercent = computed(() => (total.value > 0 ? Math.round(((currentIndex.value + 1) / total.value) * 100) : 0));
const courseTitle = computed(() => (route.query.title as string) || '沉浸学习');

// 当前卡片的标签组（根据路径/章节推断）
const currentTags = computed(() => {
  const tags: { label: string; color: string }[] = [];
  if (route.query.category) {
    tags.push({ label: String(route.query.category), color: '#3B6FE0' });
  }
  tags.push({ label: '核心概念', color: '#10B981' });
  tags.push({ label: '重要', color: '#F59E0B' });
  return tags;
});

// ===== 计时器 =====
const TOTAL_SECONDS = 25 * 60;
const timeLeft = ref(TOTAL_SECONDS);
const isPaused = ref(false);
let timerInterval: number | null = null;

const timerRadius = 90;
const timerCircumference = 2 * Math.PI * timerRadius;
const timerDashoffset = computed(() => {
  const progress = timeLeft.value / TOTAL_SECONDS;
  return timerCircumference * (1 - progress);
});

const formatTime = (seconds: number) => {
  const m = Math.floor(seconds / 60).toString().padStart(2, '0');
  const s = (seconds % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
};

const startTimer = () => {
  if (timerInterval) clearInterval(timerInterval);
  timerInterval = window.setInterval(() => {
    if (!isPaused.value && timeLeft.value > 0) {
      timeLeft.value--;
    }
  }, 1000);
};

const togglePause = () => {
  isPaused.value = !isPaused.value;
};

// ===== 设置面板 =====
const showSettings = ref(false);
const settings = ref({
  duration: 25,
  questionCount: 0,
});
const durationOptions = [20, 25, 45];

const toggleSettings = () => {
  showSettings.value = !showSettings.value;
};

// ===== 学习流程 =====
const handleReview = () => {
  // "再看看"：展开答案，不进入下一题
  showAnswer.value = true;
};

const handleMemorized = () => {
  // "我记住了"：记录本次学习会话，进入下一题
  if (total.value === 0) return;
  // 记录学习会话（按卡片数估算分钟数，每题约 1 分钟）
  const minutes = Math.max(1, Math.round(60 / total.value));
  addSession(dateStr(new Date()), minutes);

  if (currentIndex.value < total.value - 1) {
    currentIndex.value++;
    showAnswer.value = false;
  } else {
    // 全部完成
    notify(`恭喜完成 ${total.value} 道学习卡片！`, 'success');
    exitLearning();
  }
};

const exitLearning = () => {
  router.push('/learning/center');
};

// ===== 数据加载 =====
const loadCards = async () => {
  loading.value = true;
  try {
    const pathId = route.query.pathId ? Number(route.query.pathId) : undefined;
    const chapterId = route.query.chapterId ? Number(route.query.chapterId) : undefined;
    const list = await learningApi.flashcards(pathId, chapterId).catch(() => [] as FlashcardVO[]);
    cards.value = settings.value.questionCount > 0 ? list.slice(0, settings.value.questionCount) : list;
    if (cards.value.length === 0) {
      notify('暂无可学习的卡片，请先创建闪卡', 'warning');
    }
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  void loadCards();
  startTimer();
});

onUnmounted(() => {
  if (timerInterval) clearInterval(timerInterval);
});
</script>

<style scoped>
/* 顶部栏 */
.topbar {
  background: var(--kb-card);
  border-bottom: 1px solid var(--kb-border);
}
.topbar-back {
  color: var(--kb-muted-foreground);
  transition: color 0.15s ease;
}
.topbar-back:hover {
  color: var(--kb-primary);
}
.progress-chip {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.pause-btn {
  border: 1px solid var(--kb-border);
  color: var(--kb-foreground);
  background: var(--kb-card);
  transition: background 0.15s ease;
}
.pause-btn:hover {
  background: var(--kb-muted);
}
.settings-trigger {
  color: var(--kb-muted-foreground);
  transition: background 0.15s ease, color 0.15s ease;
}
.settings-trigger:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

/* 计时器数字 */
.timer-display {
  font-size: 40px;
  font-weight: 700;
  font-family: 'Fira Code', 'Consolas', 'Monaco', monospace;
  letter-spacing: -0.02em;
}

/* 题目卡 */
.question-card {
  transition: box-shadow 0.2s ease;
}
.question-card:hover {
  box-shadow: 0 8px 24px 0 rgba(0, 0, 0, 0.06);
}

/* 大按钮 */
.review-btn {
  border-color: var(--kb-warning);
  color: var(--kb-warning);
  background: transparent;
  transition: transform 0.15s ease, background 0.15s ease;
}
.review-btn:hover {
  transform: scale(1.02);
  background: rgba(245, 158, 11, 0.06);
}
.memorized-btn {
  background: var(--kb-accent);
  color: var(--kb-accent-foreground);
  transition: transform 0.15s ease, opacity 0.2s ease;
}
.memorized-btn:hover {
  transform: scale(1.02);
  opacity: 0.92;
}

/* 设置浮窗 */
.settings-panel {
  animation: slideUp 0.2s ease-out;
}
@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.duration-btn {
  border-color: var(--kb-border);
  color: var(--kb-muted-foreground);
  background: var(--kb-card);
  transition: all 0.15s ease;
}
.duration-btn.active {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
}
.settings-select {
  background: var(--kb-background);
  border-color: var(--kb-border);
  color: var(--kb-foreground);
}
.settings-fab {
  background: var(--kb-card);
  border-color: var(--kb-border);
  color: var(--kb-muted-foreground);
  transition: background 0.15s ease, color 0.15s ease;
}
.settings-fab:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

/* 响应式：移动端按钮缩小 */
@media (max-width: 768px) {
  .review-btn,
  .memorized-btn {
    height: 56px;
    padding: 0 32px;
    font-size: 16px;
  }
}
</style>
