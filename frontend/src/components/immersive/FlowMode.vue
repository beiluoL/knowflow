<template>
  <div class="flow-root">
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
      <div v-else class="task-empty">暂无今日任务，专注当下吧</div>
    </section>

    <section class="flow-center">
      <DualRingProgress
        :inner-progress="sessionProgress45"
        :outer-progress="sessionProgress90"
        inner-color="#0EA5E9"
        outer-color="#8B5CF6"
        :size="300"
      >
        <div class="center">
          <div class="time-display tabular-nums">{{ timeFormatted }}</div>
          <div class="mode-label">Flow 流时间</div>
          <div class="flow-stage" :style="{ color: flowStage.color }">
            <Icon :name="flowStage.icon" :size="14" />
            <span>{{ flowStage.label }}</span>
          </div>
        </div>
      </DualRingProgress>

      <div class="controls">
        <button
          type="button"
          class="ctrl-btn primary big"
          :disabled="isRunning"
          @click="handleStart"
        >
          <Icon name="play" :size="20" />
          <span>开始</span>
        </button>
        <button
          type="button"
          class="ctrl-btn"
          :disabled="!isRunning"
          @click="handlePause"
        >
          <Icon name="pause" :size="18" />
          <span>暂停</span>
        </button>
        <button
          type="button"
          class="ctrl-btn danger"
          :disabled="elapsedSec === 0 && !isRunning"
          @click="handleEnd"
        >
          <Icon name="square" :size="18" />
          <span>结束</span>
        </button>
      </div>

      <div class="motivation">
        <Icon name="sparkles" :size="14" />
        <span>{{ motivationText }}</span>
      </div>
    </section>

    <section class="flow-stats-panel">
      <div class="stat-card big">
        <div class="stat-label">已专注</div>
        <div class="stat-value tabular-nums">{{ totalFormatted }}</div>
      </div>

      <div class="stat-card">
        <div class="stat-label">今日完成任务</div>
        <div class="stat-value with-badge">
          <span class="tabular-nums">{{ completedCount }}</span>
          <Icon name="check" :size="16" />
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-label">进入 Flow 次数</div>
        <div class="stat-value tabular-nums">{{ flowEnteredCount }}</div>
      </div>

      <div class="stat-card progress-card">
        <div class="stat-label-row">
          <span class="stat-label">45 分钟徽章</span>
          <span class="progress-pct tabular-nums">{{ Math.round(sessionProgress45 * 100) }}%</span>
        </div>
        <div class="progress-bar">
          <div
            class="progress-fill"
            :style="{ width: Math.round(sessionProgress45 * 100) + '%', background: '#0EA5E9' }"
          />
        </div>
        <div class="stat-label-row" style="margin-top: 10px">
          <span class="stat-label">90 分钟徽章</span>
          <span class="progress-pct tabular-nums">{{ Math.round(sessionProgress90 * 100) }}%</span>
        </div>
        <div class="progress-bar">
          <div
            class="progress-fill"
            :style="{ width: Math.round(sessionProgress90 * 100) + '%', background: '#8B5CF6' }"
          />
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-label-row">
          <span class="stat-label">分心次数</span>
          <button type="button" class="distract-btn" @click="recordDistraction">
            <Icon name="alert-circle" :size="14" />
            <span>记录分心</span>
          </button>
        </div>
        <div class="stat-value tabular-nums">{{ distractionCount }}</div>
      </div>

      <div class="flow-tip-card">
        <div class="tip-title">
          <Icon name="lightbulb" :size="14" />
          <span>Flow 提示</span>
        </div>
        <p class="tip-text">
          保持专注，减少干扰源。每 5 分钟系统会切换鼓励语，保持你的流时间状态。
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import DualRingProgress from './DualRingProgress.vue';
import { useFocusSession } from '@/composables/useFocusSession';
import { useMicroAchievements } from '@/composables/useMicroAchievements';
import { learningApi, type LearningTaskVO } from '@/api';
import { notify, getApiError, confirmDialog } from '@/utils/toast';

const { isActive, start, end } = useFocusSession();
const { checkFlow } = useMicroAchievements();

const tasks = ref<LearningTaskVO[]>([]);
const taskLoading = ref(false);

const elapsedSec = ref(0);
const isRunning = ref(false);
const distractionCount = ref(0);
const flowEnteredCount = ref(0);

const hitFlow45 = ref(false);
const hitFlow90 = ref(false);

let tickTimer: number | null = null;
let lastFlowStageMin = -1;

const motivations = [
  '进入流状态，与知识共舞',
  '保持专注，灵感正在路上',
  '深度沉浸，每一秒都有价值',
  '你做得很好，继续保持节奏',
  '学习的时光总是短暂而充实',
  '心无旁骛，收获自然到来',
  '此刻的专注，是未来的礼物',
  '让知识在脑海中生根发芽',
  '一步一个脚印，稳步前进',
  '沉浸其中，享受学习的乐趣',
];

const motivationText = computed(() => {
  const slot = Math.floor(elapsedSec.value / 300);
  return motivations[slot % motivations.length];
});

const completedCount = computed(() => tasks.value.filter((t) => t.status === 1).length);

const sessionProgress45 = computed(() => {
  const v = elapsedSec.value / (45 * 60);
  return Math.min(1, Math.max(0, v));
});

const sessionProgress90 = computed(() => {
  const v = elapsedSec.value / (90 * 60);
  return Math.min(1, Math.max(0, v));
});

const timeFormatted = computed(() => formatHMS(elapsedSec.value));

const totalFormatted = computed(() => formatHM(elapsedSec.value));

const flowStage = computed(() => {
  const m = elapsedSec.value / 60;
  if (m < 15) {
    return { label: '热身阶段', color: '#F59E0B', icon: 'coffee' };
  }
  if (m <= 60) {
    return { label: 'Flow 状态', color: '#0EA5E9', icon: 'waves' };
  }
  return { label: '大师流', color: '#8B5CF6', icon: 'rocket' };
});

function formatHMS(sec: number) {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s
      .toString()
      .padStart(2, '0')}`;
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

function formatHM(sec: number) {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`;
  }
  return `${m.toString().padStart(2, '0')} 分钟`;
}

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

async function handleStart() {
  if (isRunning.value) return;
  isRunning.value = true;
  if (!isActive()) {
    try {
      await start('FLOW');
    } catch {
      /* useFocusSession already notified */
    }
  }
}

function handlePause() {
  if (!isRunning.value) return;
  isRunning.value = false;
}

async function handleEnd() {
  if (elapsedSec.value === 0 && !isRunning.value && !isActive()) {
    return;
  }
  const ok = await confirmDialog('确认结束本次 Flow 流时间？进度将自动保存');
  if (!ok) return;
  isRunning.value = false;
  if (isActive()) {
    try {
      await end({ distractionCount: distractionCount.value });
    } catch {
      /* useFocusSession already notified */
    }
  }
  checkFlow(Math.round(elapsedSec.value / 60));
  elapsedSec.value = 0;
  hitFlow45.value = false;
  hitFlow90.value = false;
  lastFlowStageMin = -1;
}

function recordDistraction() {
  distractionCount.value += 1;
  notify('已记录一次分心，下次加油', 'info');
}

onMounted(() => {
  void loadTasks();
  tickTimer = window.setInterval(() => {
    if (!isRunning.value) return;
    elapsedSec.value += 1;
    const min = Math.floor(elapsedSec.value / 60);
    const stageThresh = min < 15 ? -1 : min <= 60 ? 15 : 60;
    if (lastFlowStageMin !== stageThresh && min >= 15 && stageThresh !== -1) {
      if (lastFlowStageMin < 15 && min >= 15) {
        flowEnteredCount.value += 1;
        notify('进入 Flow 状态，保持住！', 'success');
      }
      lastFlowStageMin = stageThresh;
    }
    if (min >= 45 && !hitFlow45.value) {
      hitFlow45.value = true;
      checkFlow(45);
    }
    if (min >= 90 && !hitFlow90.value) {
      hitFlow90.value = true;
      checkFlow(90);
    }
  }, 1000);
});

onUnmounted(async () => {
  if (tickTimer) {
    clearInterval(tickTimer);
    tickTimer = null;
  }
  if (isActive()) {
    try {
      await end({ distractionCount: distractionCount.value });
    } catch {
      /* useFocusSession already notified */
    }
  }
});
</script>

<style scoped>
.flow-root {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 24px;
  padding: 88px 24px 100px;
  min-height: 0;
}

.tasks-section,
.flow-stats-panel {
  width: 260px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 18px;
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

.flow-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}
.center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.time-display {
  font-size: 56px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1;
  letter-spacing: -0.02em;
}
.mode-label {
  font-size: 14px;
  letter-spacing: 0.04em;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}
.flow-stage {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  margin-top: 4px;
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
  height: 44px;
  padding: 0 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, filter 0.15s ease;
}
.ctrl-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
}
.ctrl-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.ctrl-btn.primary {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.ctrl-btn.primary:hover:not(:disabled) {
  filter: brightness(1.05);
}
.ctrl-btn.primary.big {
  height: 52px;
  padding: 0 28px;
  font-size: 15px;
  border-radius: 14px;
}
.ctrl-btn.danger:hover:not(:disabled) {
  border-color: rgba(239, 68, 68, 0.5);
  color: #F87171;
}

.motivation {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.12), rgba(14, 165, 233, 0.1));
  border: 1px solid rgba(139, 92, 246, 0.22);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  max-width: 460px;
  text-align: center;
}

.flow-stats-panel {
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
.stat-card.big .stat-value {
  font-size: 36px;
}
.stat-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-bottom: 6px;
  letter-spacing: 0.02em;
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
.stat-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.progress-pct {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.progress-bar {
  height: 8px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--kb-primary), #0EA5E9);
  border-radius: 999px;
  transition: width 0.5s ease;
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

.flow-tip-card {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.08), rgba(139, 92, 246, 0.06));
  border: 1px solid rgba(14, 165, 233, 0.15);
  border-radius: 12px;
  padding: 14px;
}
.tip-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-primary);
  margin-bottom: 8px;
}
.tip-text {
  margin: 0;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1024px) {
  .flow-root {
    flex-direction: column;
    align-items: center;
    padding: 88px 16px 100px;
  }
  .tasks-section,
  .flow-stats-panel {
    width: 100%;
    max-width: 520px;
  }
}
</style>
