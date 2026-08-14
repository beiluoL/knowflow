<template>
  <div class="pomo-root">
    <section class="tasks-section">
      <h3 class="section-title">
        <Icon name="check-circle" :size="16" aria-hidden="true" />
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
                class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                :checked="task.status === 1"
                @change="toggleTask(task)"
              />
            <span class="task-title">{{ task.title }}</span>
          </label>
        </li>
      </ul>
      <div v-else class="task-empty">暂无今日任务，专注当下吧</div>
    </section>

    <section class="timer-section">
      <DualRingProgress
        :inner-progress="store.progress"
        :outer-progress="store.setProgress"
        :inner-color="store.modeColor.stroke"
        outer-color="#10B981"
        :size="260"
      >
        <div class="center">
          <div class="time-display tabular-nums">{{ store.timeFormatted }}</div>
          <div class="mode-label" :style="{ color: store.modeColor.fg }">
            {{ store.modeLabel }}
          </div>
          <div class="rounds">
            {{ store.runtime.roundsCompleted }}/{{ store.settings.roundsPerSet }}
          </div>
        </div>
      </DualRingProgress>

      <div class="controls">
        <button type="button" class="ctrl-btn primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="handleToggle">
          <Icon :name="store.runtime.isRunning ? 'pause' : 'play'" :size="18" aria-hidden="true" />
          <span>{{ store.runtime.isRunning ? '暂停' : '开始' }}</span>
        </button>
        <button type="button" class="ctrl-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="store.skip()">
          <Icon name="skip-forward" :size="16" aria-hidden="true" />
          <span>跳过</span>
        </button>
        <button type="button" class="ctrl-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="store.resetCurrentMode()">
          <Icon name="rotate-ccw" :size="16" aria-hidden="true" />
          <span>重置</span>
        </button>
      </div>

      <div class="pomodoro-dots">
        <span
          v-for="i in store.settings.roundsPerSet"
          :key="i"
          class="dot"
          :class="{ done: i <= store.runtime.roundsCompleted }"
        />
      </div>
    </section>

    <section class="stats-panel">
      <div class="stat-card">
        <div class="stat-label">当前专注时长</div>
        <div class="stat-value tabular-nums">{{ activeTimeFormatted }}</div>
      </div>

      <div class="stat-card">
        <div class="stat-label">今日完成番茄</div>
        <div class="stat-value with-badge">
          <span class="tabular-nums">{{ store.runtime.totalPomodorosToday }}</span>
          <span class="badge">🍅</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-label-row">
          <span class="stat-label">分心次数</span>
          <button type="button" class="distract-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="recordDistraction">
            <Icon name="alert-circle" :size="14" aria-hidden="true" />
            <span>记录分心</span>
          </button>
        </div>
        <div class="stat-value tabular-nums">{{ distractionCount }}</div>
      </div>

      <div class="ai-card">
        <div class="ai-title">
          <Icon name="sparkles" :size="14" aria-hidden="true" />
          <span>AI 今日建议</span>
        </div>
        <p class="ai-text">
          建议先完成优先级高的任务，保持专注节奏。每完成一个番茄可起身走动 1 分钟，避免久坐。
        </p>
      </div>

        <button
          type="button"
          class="noise-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ 'is-on': noisePlayingState.value }"
          :title="noiseBtnTitle"
          @click="toggleNoise"
        >
        <Icon :name="noisePlayingState.value ? 'music' : 'bell-off'" :size="16" aria-hidden="true" />
        <span>白噪音</span>
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import DualRingProgress from './DualRingProgress.vue';
import { usePomodoroStore, type NoiseType } from '@/stores/pomodoro';
import { useFocusSession } from '@/composables/useFocusSession';
import { learningApi } from '@/api';
import { notify, getApiError } from '@/utils/toast';
import type { LearningTaskVO } from '@/api/types';
import {
  NOISE_TYPES,
  playNoiseManual,
  stopNoise,
  setNoiseVolume,
  isNoisePlaying,
} from '@/composables/useNoiseAudio';

const store = usePomodoroStore();
const { isActive, start, end } = useFocusSession();

const emit = defineEmits<{
  'toggle-noise': [];
}>();

const tasks = ref<LearningTaskVO[]>([]);
const taskLoading = ref(false);
const distractionCount = ref(0);
const sessionStartTs = ref<number>(0);

/** 白噪音播放状态（轮询保持同步，因为 useNoiseAudio 内部为非响应式单例） */
const noisePlayingState = ref(false);
let noisePollTimer: number | null = null;
function startNoisePolling() {
  noisePlayingState.value = isNoisePlaying();
  noisePollTimer = window.setInterval(() => {
    noisePlayingState.value = isNoisePlaying();
  }, 150);
}
function stopNoisePolling() {
  if (noisePollTimer != null) {
    clearInterval(noisePollTimer);
    noisePollTimer = null;
  }
}

/** 白噪音按钮 tooltip 文案 */
const noiseBtnTitle = computed(() => {
  if (noisePlayingState.value) {
    const t = store.settings.noiseType;
    const meta = t != null ? NOISE_TYPES.find((n) => n.id === t)?.label : '';
    return `停止白噪音${meta ? '（' + meta + '）' : ''}`;
  }
  if (store.settings.noiseType != null) {
    const meta = NOISE_TYPES.find((n) => n.id === store.settings.noiseType as NoiseType);
    return `播放白噪音${meta ? '（' + meta.label + '）' : ''}`;
  }
  return '播放白噪音（默认雨声）';
});

const completedCount = computed(() => tasks.value.filter((t) => t.status === 1).length);

const activeTimeFormatted = computed(() => {
  if (!isActive() || sessionStartTs.value <= 0) return '00:00';
  const totalSec = Math.floor((Date.now() - sessionStartTs.value) / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s
      .toString()
      .padStart(2, '0')}`;
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
});

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
  task.status = next;
  try {
    await learningApi.updateTaskStatus(task.id, next);
  } catch (e: unknown) {
    task.status = prev;
    notify(getApiError(e, '更新任务失败'), 'error');
  }
};

const handleToggle = async () => {
  const wasRunning = store.runtime.isRunning;
  store.toggle();
  if (!wasRunning && !isActive()) {
    try {
      await start('POMODORO');
      sessionStartTs.value = Date.now();
    } catch (e: unknown) {
      /* useFocusSession 已 notify */
    }
  }
};

const recordDistraction = () => {
  distractionCount.value += 1;
  notify('已记录一次分心，下次加油', 'info');
};

const toggleNoise = () => {
  if (noisePlayingState.value) {
    stopNoise();
    noisePlayingState.value = false;
  } else {
    // 未选择类型：先默认开雨声 + 更新 store
    if (store.settings.noiseType == null) {
      store.updateNoiseSettings({ noiseType: 'rain' });
    }
    const t: NoiseType = (store.settings.noiseType as NoiseType | null) ?? 'rain';
    setNoiseVolume(store.settings.noiseVolume);
    playNoiseManual(t);
    noisePlayingState.value = true;
  }
  emit('toggle-noise');
};

watch(
  () => store.runtime.roundsCompleted,
  (newVal, oldVal) => {
    if (newVal > oldVal) {
      notify(`第 ${newVal} 个番茄完成！`, 'success');
    }
  },
);

let tickTimer: number | null = null;

onMounted(() => {
  store.init();
  void loadTasks();
  startNoisePolling();
  tickTimer = window.setInterval(() => {
    if (isActive()) {
      /* 触发 computed 重新计算 */
      activeTimeFormatted.value;
    }
  }, 1000);
});

onUnmounted(async () => {
  stopNoisePolling();
  if (tickTimer) {
    clearInterval(tickTimer);
    tickTimer = null;
  }
  if (isActive()) {
    try {
      await end({ distractionCount: distractionCount.value });
    } catch (e: unknown) {
      /* useFocusSession 已 notify */
    }
  }
});
</script>

<style scoped>
.pomo-root {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 24px;
  padding: 88px 24px 100px;
  min-height: 0;
}

.tasks-section,
.stats-panel {
  width: 260px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 18px 18px;
  backdrop-filter: blur(8px);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 14px;
}
.section-title .task-count {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
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
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  transition: background 0.15s ease;
}
.task-item:hover {
  background: rgba(255, 255, 255, 0.06);
}
.task-item.done .task-title {
  color: var(--kb-muted-foreground);
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
  accent-color: var(--kb-primary);
  cursor: pointer;
}
.task-title {
  font-size: 13px;
  color: var(--kb-foreground);
  line-height: 1.4;
}
.task-empty {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  padding: 4px 2px;
}

.timer-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 22px;
}
.center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.time-display {
  font-size: 52px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1;
  letter-spacing: -0.02em;
}
.mode-label {
  font-size: 14px;
  letter-spacing: 0.04em;
}
.rounds {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
  margin-top: 2px;
}
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
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
.ctrl-btn.primary {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.ctrl-btn.primary:hover {
  filter: brightness(1.05);
}
.pomodoro-dots {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  transition: background 0.3s ease, transform 0.3s ease;
}
.dot.done {
  background: var(--kb-accent);
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
}

.stats-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.stat-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 12px 14px;
}
.stat-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}
.stat-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.2;
}
.stat-value.with-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.badge {
  font-size: 18px;
}
.distract-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 500;
  color: var(--kb-warning);
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.distract-btn:hover {
  background: rgba(245, 158, 11, 0.18);
}
.ai-card {
  background: linear-gradient(135deg, rgba(79, 134, 249, 0.08), rgba(52, 211, 153, 0.06));
  border: 1px solid rgba(79, 134, 249, 0.15);
  border-radius: 12px;
  padding: 14px;
}
.ai-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-primary);
  margin-bottom: 8px;
}
.ai-text {
  margin: 0;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}
.noise-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: filter 0.15s ease;
}
.noise-btn:hover {
  filter: brightness(1.05);
}
.noise-btn.is-on {
  background: var(--kb-highlight, #FF6B35);
  box-shadow: 0 0 0 2px rgba(255, 107, 53, 0.2);
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1024px) {
  .pomo-root {
    flex-direction: column;
    align-items: center;
    padding: 88px 16px 100px;
  }
  .tasks-section,
  .stats-panel {
    width: 100%;
    max-width: 520px;
  }
}
</style>
