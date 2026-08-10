<template>
  <div class="prefs-page animate-fade-in">
    <!-- 页头 -->
    <div class="flex items-center justify-between flex-wrap gap-3 mb-6">
      <div class="flex items-center gap-3 min-w-0">
        <button type="button" class="back-btn" title="返回" aria-label="返回" @click="router.back()">
          <Icon name="arrow-left" :size="18" />
        </button>
        <h1 class="kb-h1">学习偏好</h1>
      </div>
      <button
        type="button"
        class="header-btn-primary"
        :disabled="saving"
        @click="handleSave"
      >
        <Icon name="save" :size="16" />
        <span>{{ saving ? '保存中…' : '保存设置' }}</span>
      </button>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="space-y-4">
      <div class="h-32 rounded-xl animate-pulse" style="background: var(--kb-muted);"></div>
      <div class="h-32 rounded-xl animate-pulse" style="background: var(--kb-muted);"></div>
    </div>

    <template v-else>
      <!-- 番茄钟设置 -->
      <section class="prefs-card">
        <div class="card-head">
          <Icon name="timer" :size="18" class="card-icon" />
          <h2 class="kb-h3">番茄钟专注</h2>
        </div>
        <p class="card-desc">设置默认的专注与休息时长，保存后同步至全局番茄钟。</p>
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">
          <div class="field">
            <label class="field-label">专注时长（分钟）</label>
            <input v-model.number="form.focusMinutes" type="number" min="5" max="90" class="field-input" />
          </div>
          <div class="field">
            <label class="field-label">短休时长（分钟）</label>
            <input v-model.number="form.shortBreak" type="number" min="1" max="20" class="field-input" />
          </div>
          <div class="field">
            <label class="field-label">长休时长（分钟）</label>
            <input v-model.number="form.longBreak" type="number" min="5" max="45" class="field-input" />
          </div>
          <div class="field">
            <label class="field-label">每组工作次数</label>
            <input v-model.number="form.rounds" type="number" min="2" max="8" class="field-input" />
          </div>
        </div>
      </section>

      <!-- 闪卡设置 -->
      <section class="prefs-card">
        <div class="card-head">
          <Icon name="layers" :size="18" class="card-icon" />
          <h2 class="kb-h3">闪卡复习</h2>
        </div>
        <p class="card-desc">控制闪卡大厅与沉浸复习的抽卡策略与数量。</p>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
          <div class="field">
            <label class="field-label">抽卡策略</label>
            <select v-model="form.cardStrategy" class="field-input">
              <option value="RANDOM">随机抽取</option>
              <option value="DIFFICULTY">按难度优先</option>
              <option value="DUE">仅今日待复习</option>
              <option value="WEAK">薄弱知识优先</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label">每次卡片数量（0=全部）</label>
            <input v-model.number="form.cardCount" type="number" min="0" max="100" class="field-input" />
          </div>
          <div class="field">
            <label class="field-label">难度过滤</label>
            <select v-model="form.difficultyFilter" class="field-input">
              <option value="">不限</option>
              <option value="1">简单</option>
              <option value="2">中等</option>
              <option value="3">困难</option>
            </select>
          </div>
        </div>
      </section>

      <!-- 阅读体验 -->
      <section class="prefs-card">
        <div class="card-head">
          <Icon name="book-open" :size="18" class="card-icon" />
          <h2 class="kb-h3">阅读体验</h2>
        </div>
        <p class="card-desc">章节正文的默认主题与字号（部分选项在下次打开章节时生效）。</p>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          <div class="field">
            <label class="field-label">阅读主题</label>
            <select v-model="form.theme" class="field-input">
              <option value="day">日间</option>
              <option value="eye">护眼</option>
              <option value="night">夜间</option>
              <option value="parchment">羊皮纸</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label">字体大小</label>
            <select v-model="form.fontSize" class="field-input">
              <option value="sm">小</option>
              <option value="md">中</option>
              <option value="lg">大</option>
              <option value="xl">超大</option>
            </select>
          </div>
        </div>
      </section>

      <!-- 提醒与反馈 -->
      <section class="prefs-card">
        <div class="card-head">
          <Icon name="bell" :size="18" class="card-icon" />
          <h2 class="kb-h3">提醒与反馈</h2>
        </div>
        <p class="card-desc">控制专注结束提醒音、桌面通知与每日学习提醒。</p>
        <div class="space-y-3 mt-4">
          <label class="switch-row">
            <div class="switch-text">
              <span class="switch-label">声音提醒</span>
              <span class="switch-hint">专注/休息结束时播放提示音</span>
            </div>
            <input
              type="checkbox"
              :checked="form.soundEnabled === 1"
              @change="form.soundEnabled = ($event.target as HTMLInputElement).checked ? 1 : 0"
            />
          </label>
          <label class="switch-row">
            <div class="switch-text">
              <span class="switch-label">桌面通知</span>
              <span class="switch-hint">阶段切换时推送系统通知（需授权）</span>
            </div>
            <input
              type="checkbox"
              :checked="form.notificationEnabled === 1"
              @change="form.notificationEnabled = ($event.target as HTMLInputElement).checked ? 1 : 0"
            />
          </label>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-2">
            <div class="field">
              <label class="field-label">每日学习提醒时间</label>
              <input v-model="form.reminderTime" type="time" class="field-input" @change="onReminderTimeChange" />
            </div>
            <div class="field">
              <label class="field-label">白噪音</label>
              <select v-model="form.whiteNoise" class="field-input">
                <option value="">关闭</option>
                <option value="rain">雨声</option>
                <option value="cafe">咖啡馆</option>
                <option value="wave">海浪</option>
              </select>
            </div>
          </div>

          <!-- 学习提醒激活区（基于 Service Worker + Notification API） -->
          <div class="reminder-block">
            <div v-if="hasPermission !== 'granted'" class="reminder-perm">
              <div class="switch-text">
                <span class="switch-label">通知权限未开启</span>
                <span class="switch-hint">需授权浏览器通知才能弹出每日学习提醒</span>
              </div>
              <button type="button" class="perm-btn" @click="handleRequestPermission">
                <Icon name="bell" :size="14" />
                <span>开启通知</span>
              </button>
            </div>
            <label class="switch-row">
              <div class="switch-text">
                <span class="switch-label">每日本地提醒</span>
                <span class="switch-hint">
                  到点弹出系统通知（{{ hasPermission === 'granted' ? '已授权' : '未授权' }}）{{ reminderEnabled ? ' · 已开启' : '' }}
                </span>
              </div>
              <input
                type="checkbox"
                :checked="reminderEnabled"
                @change="toggleReminder($event)"
              />
            </label>
            <label class="switch-row">
              <div class="switch-text">
                <span class="switch-label">推送订阅</span>
                <span class="switch-hint">
                  {{ isSubscribed ? '已订阅，将接收服务器推送' : '订阅后接收服务器推送（需 VAPID 配置）' }}
                </span>
              </div>
              <button type="button" class="push-btn" @click="togglePush">
                {{ isSubscribed ? '取消订阅' : '订阅推送' }}
              </button>
            </label>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 学习偏好设置页
 * 加载后端 sys_user_pref 配置，保存时同步至全局番茄钟 store（focusMinutes/shortBreak/longBreak/rounds/sound）。
 */
import { ref, reactive, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import { usePomodoroStore } from '@/stores/pomodoro';
import { useStudyReminder } from '@/composables/useStudyReminder';
import { notify, getApiError } from '@/utils/toast';
import type { UserLearningPrefInput } from '@/api/types';

const router = useRouter();
const pomodoroStore = usePomodoroStore();
const {
  reminderTime: swReminderTime,
  hasPermission,
  isSubscribed,
  requestPermission,
  setReminder,
  clearReminder,
  subscribePush,
  unsubscribePush,
} = useStudyReminder();

// 本地提醒激活状态：由 composable 的 reminderTime 驱动
const reminderEnabled = ref(false);

const loading = ref(false);
const saving = ref(false);

const form = reactive<UserLearningPrefInput>({
  focusMinutes: 25,
  shortBreak: 5,
  longBreak: 15,
  rounds: 4,
  cardStrategy: 'RANDOM',
  cardCount: 20,
  difficultyFilter: '',
  theme: 'day',
  fontSize: 'md',
  soundEnabled: 1,
  notificationEnabled: 1,
  reminderTime: '',
  whiteNoise: '',
});

onMounted(async () => {
  loading.value = true;
  try {
    const prefs = await learningApi.getPrefs();
    Object.assign(form, {
      focusMinutes: prefs.focusMinutes ?? 25,
      shortBreak: prefs.shortBreak ?? 5,
      longBreak: prefs.longBreak ?? 15,
      rounds: prefs.rounds ?? 4,
      cardStrategy: prefs.cardStrategy ?? 'RANDOM',
      cardCount: prefs.cardCount ?? 20,
      difficultyFilter: prefs.difficultyFilter ?? '',
      theme: prefs.theme ?? 'day',
      fontSize: prefs.fontSize ?? 'md',
      soundEnabled: prefs.soundEnabled ?? 1,
      notificationEnabled: prefs.notificationEnabled ?? 1,
      reminderTime: prefs.reminderTime ?? '',
      whiteNoise: prefs.whiteNoise ?? '',
    });
  } catch (e) {
    notify(getApiError(e, '加载偏好失败，使用默认值'), 'warning');
  } finally {
    loading.value = false;
  }
});

const handleSave = async () => {
  if (saving.value) return;
  saving.value = true;
  try {
    await learningApi.savePrefs({ ...form });
    // 静默同步番茄钟设置（直接改 settings ref，触发 store 的 watch 持久化，避免重复 notify）
    pomodoroStore.settings.focusMinutes = form.focusMinutes ?? 25;
    pomodoroStore.settings.shortBreakMinutes = form.shortBreak ?? 5;
    pomodoroStore.settings.longBreakMinutes = form.longBreak ?? 15;
    pomodoroStore.settings.roundsPerSet = form.rounds ?? 4;
    pomodoroStore.settings.soundEnabled = (form.soundEnabled ?? 1) === 1;
    notify('学习偏好已保存', 'success');
  } catch (e) {
    notify(getApiError(e, '保存失败，请稍后重试'), 'error');
  } finally {
    saving.value = false;
  }
};

// ===== 学习提醒（Service Worker + Notification） =====
// composable 的 reminderTime 是调度状态来源：有值则已激活，空则未激活
watch(swReminderTime, (t) => {
  reminderEnabled.value = !!t;
  if (t) form.reminderTime = t;
});

const handleRequestPermission = async () => {
  await requestPermission();
};

const toggleReminder = async (e: Event) => {
  const checked = (e.target as HTMLInputElement).checked;
  if (checked) {
    const t = form.reminderTime || '20:00';
    form.reminderTime = t;
    await setReminder(t);
  } else {
    clearReminder();
  }
};

const onReminderTimeChange = async () => {
  // 已开启提醒时，修改时间即时重新调度
  if (reminderEnabled.value && form.reminderTime) {
    await setReminder(form.reminderTime);
  }
};

const togglePush = async () => {
  if (isSubscribed.value) {
    await unsubscribePush();
  } else {
    // 未配置 VAPID 时传入 undefined，composable 会给出预留提示
    await subscribePush();
  }
};
</script>

<style scoped>
.prefs-page {
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.back-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}
.back-btn:active {
  background: var(--kb-muted);
  transform: scale(0.96);
}
.back-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.header-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-2);
  height: 38px;
  padding: 0 var(--kb-space-4);
  border-radius: var(--kb-radius-sm);
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.header-btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}
.header-btn-primary:active:not(:disabled) {
  opacity: 0.95;
  transform: scale(0.98);
}
.header-btn-primary:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.header-btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.prefs-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  padding: var(--kb-space-5) var(--kb-space-6);
  margin-bottom: var(--kb-space-4);
}
.card-head {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  min-width: 0;
}
.card-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
}
.card-desc {
  margin-top: var(--kb-space-2);
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  line-height: var(--kb-lh-body-md);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-2);
  min-width: 0;
}
.field-label {
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  font-weight: 500;
  color: var(--kb-foreground);
}
.field-input {
  width: 100%;
  min-width: 0;
  height: 38px;
  padding: 0 var(--kb-space-3);
  font-size: var(--kb-fs-body-md);
  font-variant-numeric: tabular-nums;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
select.field-input {
  cursor: pointer;
}
.field-input:hover {
  border-color: var(--kb-primary);
}
.field-input:focus,
.field-input:focus-visible {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
}

/* 开关行 */
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-3);
  padding: var(--kb-space-3);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.switch-row:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
}
.switch-row:active {
  background: color-mix(in srgb, var(--kb-primary) 8%, transparent);
}
.switch-row:has(input[type='checkbox']:focus-visible) {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.switch-text {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-1);
  flex: 1;
  min-width: 0;
}
.switch-label {
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  font-weight: 500;
  color: var(--kb-foreground);
}
.switch-hint {
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  color: var(--kb-muted-foreground);
}
.switch-row input[type='checkbox'] {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  cursor: pointer;
  accent-color: var(--kb-primary);
}
.switch-row input[type='checkbox']:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 学习提醒激活区 */
.reminder-block {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-3);
  margin-top: var(--kb-space-3);
}
.reminder-perm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--kb-space-3);
  padding: var(--kb-space-3);
  border: 1px solid var(--kb-warning);
  border-radius: var(--kb-radius-sm);
  background: color-mix(in srgb, var(--kb-warning) 8%, transparent);
}
.perm-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-2);
  height: 32px;
  padding: 0 var(--kb-space-4);
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  cursor: pointer;
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.perm-btn:hover {
  opacity: 0.9;
}
.perm-btn:active {
  opacity: 0.95;
  transform: scale(0.98);
}
.perm-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.push-btn {
  height: 32px;
  padding: 0 var(--kb-space-4);
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.push-btn:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.push-btn:active {
  transform: scale(0.98);
  background: color-mix(in srgb, var(--kb-primary) 8%, transparent);
}
.push-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 响应式：小屏收紧卡片内边距（px-4），桌面端保持 px-6 不变 */
@media (max-width: 640px) {
  .prefs-card {
    padding: var(--kb-space-4);
  }
}
</style>
