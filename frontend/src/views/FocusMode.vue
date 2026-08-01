<template>
  <!--
    P3-5 深度学习模式（FocusMode）
    全屏沉浸式学习页面：整合番茄钟、白噪音、夜间主题、隐藏所有导航干扰。
    meta.layout='none' → 无顶栏无侧栏。
  -->
  <div class="focus-root">
    <!-- 顶部栏：退出 + 品牌 -->
    <header class="focus-topbar">
      <button type="button" class="exit-btn" @click="exitFocus">
        <Icon name="log-out" :size="16" />
        <span>退出专注</span>
      </button>
      <div class="brand">
        <Icon name="moon" :size="16" />
        <span>KnowFlow · 深度学习</span>
      </div>
    </header>

    <!-- 主体 -->
    <main class="focus-main">
      <!-- 番茄钟进度环 + 时间 -->
      <section class="timer-section">
        <div class="ring-wrap">
          <svg class="ring-svg" viewBox="0 0 200 200" aria-hidden="true">
            <circle cx="100" cy="100" r="90" fill="none" stroke="rgba(255,255,255,0.08)" stroke-width="6" />
            <circle
              cx="100"
              cy="100"
              r="90"
              fill="none"
              :stroke="pomodoroStore.modeColor.stroke"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="ringCircumference"
              :stroke-dashoffset="ringDashoffset"
              class="ring-progress"
            />
          </svg>
          <div class="ring-center">
            <div class="time-display">{{ pomodoroStore.timeFormatted }}</div>
            <div class="mode-label">{{ pomodoroStore.modeLabel }}</div>
          </div>
        </div>

        <!-- 控制按钮 -->
        <div class="controls">
          <button type="button" class="ctrl-btn primary" @click="pomodoroStore.toggle()">
            <Icon :name="pomodoroStore.runtime.isRunning ? 'pause' : 'play'" :size="18" />
            <span>{{ pomodoroStore.runtime.isRunning ? '暂停' : '开始' }}</span>
          </button>
          <button type="button" class="ctrl-btn" @click="pomodoroStore.skip()">
            <Icon name="skip-forward" :size="16" />
            <span>跳过</span>
          </button>
          <button type="button" class="ctrl-btn" @click="pomodoroStore.resetCurrentMode()">
            <Icon name="rotate-ccw" :size="16" />
            <span>重置</span>
          </button>
        </div>

        <!-- 模式切换提示 -->
        <div class="mode-hint">
          第 {{ Math.min(pomodoroStore.runtime.roundsCompleted + 1, pomodoroStore.settings.roundsPerSet) }}
          / {{ pomodoroStore.settings.roundsPerSet }} 个番茄
        </div>
      </section>

      <!-- 白噪音播放器（嵌入式，组件自身浮动定位） -->
      <WhiteNoisePlayer />

      <!-- 今日任务 -->
      <section class="tasks-section">
        <h3 class="section-title">
          <Icon name="check-circle" :size="16" />
          <span>今日任务</span>
          <span v-if="tasks.length" class="task-count">{{ completedCount }}/{{ tasks.length }}</span>
        </h3>
        <div v-if="taskLoading" class="task-empty">加载中…</div>
        <ul v-else-if="tasks.length" class="task-list">
          <li
            v-for="task in tasks"
            :key="task.id"
            class="task-item"
            :class="{ done: task.status === 1 }"
          >
            <label class="task-check">
              <input
                type="checkbox"
                :checked="task.status === 1"
                @change="toggleTask(task)"
              />
              <span class="task-title">{{ task.title }}</span>
            </label>
          </li>
        </ul>
        <div v-else class="task-empty">暂无今日任务，专注当下吧 ✨</div>
      </section>

      <!-- 激励文案 -->
      <footer class="quote-bar">
        <p class="quote-text">“{{ currentQuote }}”</p>
      </footer>
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * 深度学习模式：进入时切换深色主题，退出时恢复。
 * 番茄钟状态读取全局 usePomodoroStore；任务列表来自 learningApi.tasks()。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import WhiteNoisePlayer from '@/components/WhiteNoisePlayer.vue';
import { learningApi } from '@/api';
import { usePomodoroStore } from '@/stores/pomodoro';
import { useTheme } from '@/composables/useTheme';
import { notify, getApiError } from '@/utils/toast';
import type { LearningTaskVO } from '@/api/types';

const router = useRouter();
const pomodoroStore = usePomodoroStore();
const { theme, setTheme } = useTheme();

// 进入前记录原主题，退出时恢复
const originalTheme = theme.value;

// ===== 番茄钟进度环 =====
const ringRadius = 90;
const ringCircumference = 2 * Math.PI * ringRadius;
const ringDashoffset = computed(() => ringCircumference * (1 - pomodoroStore.progress));

// ===== 今日任务 =====
const tasks = ref<LearningTaskVO[]>([]);
const taskLoading = ref(false);

const completedCount = computed(() => tasks.value.filter((t) => t.status === 1).length);

const loadTasks = async () => {
  taskLoading.value = true;
  try {
    tasks.value = await learningApi.tasks();
  } catch (e: unknown) {
    notify(getApiError(e, '加载任务失败'), 'warning');
    tasks.value = [];
  } finally {
    taskLoading.value = false;
  }
};

const toggleTask = async (task: LearningTaskVO) => {
  const next = task.status === 1 ? 0 : 1;
  const prev = task.status;
  task.status = next; // 乐观更新
  try {
    await learningApi.updateTaskStatus(task.id, next);
  } catch (e: unknown) {
    task.status = prev; // 回滚
    notify(getApiError(e, '更新任务失败'), 'error');
  }
};

// ===== 激励文案 =====
const QUOTES = [
  '保持专注，未来可期。',
  '每一分钟的专注，都是未来的礼物。',
  '慢一点没关系，停下来才可惜。',
  '深度工作，是稀缺的超能力。',
  '此刻的努力，是未来的底气。',
  '心无旁骛，方能行远。',
  '今天的番茄，明天的成就。',
  '专注当下，水到渠成。',
];
const currentQuote = ref(QUOTES[0]);

// ===== 退出 =====
const exitFocus = () => {
  router.push('/learning/center');
};

onMounted(() => {
  // 切换深色主题
  setTheme('dark');
  // 确保番茄钟已启动（绑定全局事件监听）
  pomodoroStore.init();
  // 随机激励语
  currentQuote.value = QUOTES[Math.floor(Math.random() * QUOTES.length)];
  // 加载任务
  void loadTasks();
});

onUnmounted(() => {
  // 恢复原主题
  setTheme(originalTheme);
});
</script>

<style scoped>
.focus-root {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a, #1e293b);
  color: #f8fafc;
  display: flex;
  flex-direction: column;
}

/* 顶部栏 */
.focus-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px;
}
.exit-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.exit-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.02em;
}

/* 主体 */
.focus-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 36px;
  padding: 12px 28px 48px;
}

/* 番茄钟 */
.timer-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}
.ring-wrap {
  position: relative;
  width: 240px;
  height: 240px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.ring-svg {
  width: 240px;
  height: 240px;
  transform: rotate(-90deg);
}
.ring-progress {
  transition: stroke-dashoffset 1s linear, stroke 0.3s ease;
}
.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.time-display {
  font-size: 56px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  color: #f8fafc;
  line-height: 1;
}
.mode-label {
  font-size: 14px;
  color: #94a3b8;
  letter-spacing: 0.04em;
}

/* 控制按钮 */
.controls {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ctrl-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 18px;
  font-size: 14px;
  font-weight: 600;
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
.ctrl-btn.primary {
  background: #3b6fe0;
  border-color: #3b6fe0;
  color: #fff;
}
.ctrl-btn.primary:hover {
  background: #2f5fc7;
}
.mode-hint {
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.04em;
}

/* 任务区 */
.tasks-section {
  width: 100%;
  max-width: 520px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 18px 20px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #f8fafc;
  margin-bottom: 12px;
}
.section-title .task-count {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}
.task-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.task-item {
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  transition: background 0.15s ease;
}
.task-item:hover {
  background: rgba(255, 255, 255, 0.06);
}
.task-item.done .task-title {
  color: #94a3b8;
  text-decoration: line-through;
}
.task-check {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.task-check input[type='checkbox'] {
  width: 16px;
  height: 16px;
  accent-color: #3b6fe0;
  cursor: pointer;
}
.task-title {
  font-size: 14px;
  color: #f8fafc;
  line-height: 1.4;
}
.task-empty {
  font-size: 13px;
  color: #94a3b8;
  padding: 4px 2px;
}

/* 激励文案 */
.quote-bar {
  text-align: center;
}
.quote-text {
  font-size: 15px;
  color: #94a3b8;
  letter-spacing: 0.02em;
  font-style: italic;
}

/* 响应式 */
@media (max-width: 640px) {
  .ring-wrap,
  .ring-svg {
    width: 200px;
    height: 200px;
  }
  .time-display {
    font-size: 44px;
  }
  .ctrl-btn {
    height: 36px;
    padding: 0 14px;
    font-size: 13px;
  }
}
</style>
