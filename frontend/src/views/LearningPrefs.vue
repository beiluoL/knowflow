<template>
  <div class="prefs-page animate-fade-in">
    <!-- 页头 -->
    <div class="flex items-center justify-between flex-wrap gap-3 mb-6">
      <div class="flex items-center gap-3 min-w-0">
        <button type="button" class="back-btn" title="返回" aria-label="返回" @click="nav.goBack()">
          <Icon name="arrow-left" :size="18" />
        </button>
        <h1 class="kf-h1">学习偏好</h1>
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
      <div class="h-32 rounded-xl animate-pulse" style="background: var(--kf-muted);"></div>
      <div class="h-32 rounded-xl animate-pulse" style="background: var(--kf-muted);"></div>
    </div>

    <template v-else>
      <!-- 番茄钟设置 -->
      <section class="prefs-card">
        <div class="card-head">
          <Icon name="timer" :size="18" class="card-icon" />
          <h2 class="kf-h3">番茄钟专注</h2>
        </div>
        <p class="card-desc">设置默认的专注与休息时长，保存后同步至全局番茄钟。</p>
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">
          <div class="field">
            <label class="field-label">专注时长（分钟）</label>
            <input v-model.number="form.focusMinutes" type="number" min="5" max="90" class="field-input kf-input kf-form-control" />
          </div>
          <div class="field">
            <label class="field-label">短休时长（分钟）</label>
            <input v-model.number="form.shortBreak" type="number" min="1" max="20" class="field-input kf-input kf-form-control" />
          </div>
          <div class="field">
            <label class="field-label">长休时长（分钟）</label>
            <input v-model.number="form.longBreak" type="number" min="5" max="45" class="field-input kf-input kf-form-control" />
          </div>
          <div class="field">
            <label class="field-label">每组工作次数</label>
            <input v-model.number="form.rounds" type="number" min="2" max="8" class="field-input kf-input kf-form-control" />
          </div>
        </div>
      </section>

      <!-- 闪卡设置 -->
      <section class="prefs-card">
        <div class="card-head">
          <Icon name="layers" :size="18" class="card-icon" />
          <h2 class="kf-h3">闪卡复习</h2>
        </div>
        <p class="card-desc">控制闪卡大厅与沉浸复习的抽卡策略与数量。</p>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
          <div class="field">
            <label class="field-label">抽卡策略</label>
            <Select v-model="form.cardStrategy" class="prefs-select" aria-label="抽卡策略" :options="[{ value: 'RANDOM', label: '随机抽取' }, { value: 'DIFFICULTY', label: '按难度优先' }, { value: 'DUE', label: '仅今日待复习' }, { value: 'WEAK', label: '薄弱知识优先' }]" />
          </div>
          <div class="field">
            <label class="field-label">每次卡片数量（0=全部）</label>
            <input v-model.number="form.cardCount" type="number" min="0" max="100" class="field-input kf-input kf-form-control" />
          </div>
          <div class="field">
            <label class="field-label">难度过滤</label>
            <Select v-model="form.difficultyFilter" class="prefs-select" aria-label="难度过滤" :options="[{ value: '', label: '不限' }, { value: '1', label: '简单' }, { value: '2', label: '中等' }, { value: '3', label: '困难' }]" />
          </div>
        </div>
      </section>

      <!-- 阅读体验 -->
      <section class="prefs-card">
        <div class="card-head">
          <Icon name="book-open" :size="18" class="card-icon" />
          <h2 class="kf-h3">阅读体验</h2>
        </div>
        <p class="card-desc">章节正文的默认主题与字号（部分选项在下次打开章节时生效）。</p>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          <div class="field">
            <label class="field-label">阅读主题</label>
            <Select v-model="form.theme" class="prefs-select" aria-label="阅读主题" :options="[{ value: 'day', label: '日间' }, { value: 'eye', label: '护眼' }, { value: 'night', label: '夜间' }, { value: 'parchment', label: '羊皮纸' }]" />
          </div>
          <div class="field">
            <label class="field-label">字体大小</label>
            <Select v-model="form.fontSize" class="prefs-select" aria-label="字体大小" :options="[{ value: 'sm', label: '小' }, { value: 'md', label: '中' }, { value: 'lg', label: '大' }, { value: 'xl', label: '超大' }]" />
          </div>
        </div>
      </section>

      <!-- 提醒与反馈 -->
      <section class="prefs-card">
        <div class="card-head">
          <Icon name="bell" :size="18" class="card-icon" />
          <h2 class="kf-h3">提醒与反馈</h2>
        </div>
        <p class="card-desc">控制专注结束提醒音、桌面通知与每日学习提醒。</p>
        <div class="space-y-3 mt-4">
          <label class="switch-row kf-checkbox-row">
            <div class="switch-text">
              <span class="switch-label">声音提醒</span>
              <span class="switch-hint">专注/休息结束时播放提示音</span>
            </div>
            <Checkbox as="span"
              :checked="form.soundEnabled === 1"
              @native-change="form.soundEnabled = ($event.target as HTMLInputElement).checked ? 1 : 0" />
          </label>
          <label class="switch-row kf-checkbox-row">
            <div class="switch-text">
              <span class="switch-label">桌面通知</span>
              <span class="switch-hint">阶段切换时推送系统通知（需授权）</span>
            </div>
            <Checkbox as="span"
              :checked="form.notificationEnabled === 1"
              @native-change="form.notificationEnabled = ($event.target as HTMLInputElement).checked ? 1 : 0" />
          </label>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-2">
            <div class="field">
              <label class="field-label">每日学习提醒时间</label>
              <TimePicker
                v-model="form.reminderTime"
                class="prefs-time"
                precision="minute"
                placeholder="选择提醒时间"
                aria-label="每日学习提醒时间"
                @change="onReminderTimeChange"
              />
            </div>
            <div class="field">
              <label class="field-label">白噪音</label>
              <Select v-model="form.whiteNoise" class="prefs-select" aria-label="白噪音" :options="[{ value: '', label: '关闭' }, { value: 'rain', label: '雨声' }, { value: 'cafe', label: '咖啡馆' }, { value: 'wave', label: '海浪' }]" />
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
            <label class="switch-row kf-checkbox-row">
              <div class="switch-text">
                <span class="switch-label">每日本地提醒</span>
                <span class="switch-hint">
                  到点弹出系统通知（{{ hasPermission === 'granted' ? '已授权' : '未授权' }}）{{ reminderEnabled ? ' · 已开启' : '' }}
                </span>
              </div>
              <Checkbox as="span"
                :checked="reminderEnabled"
                @native-change="toggleReminder($event)" />
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
import Checkbox from '@/components/ui/Checkbox.vue'

/**
 * 学习偏好设置页
 * 加载后端 sys_user_pref 配置，保存时同步至全局番茄钟 store（focusMinutes/shortBreak/longBreak/rounds/sound）。
 */
import { ref, reactive, onMounted, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import Select from '@/components/ui/Select.vue';
import TimePicker from '@/components/ui/TimePicker.vue';
import { learningApi } from '@/api';
import { usePomodoroStore } from '@/stores/pomodoro';
import { useStudyReminder } from '@/composables/useStudyReminder';
import { notify, getApiError } from '@/utils/toast';
import type { UserLearningPrefInput } from '@/api/types';
import { usePageNavigation } from '@/composables/usePageNavigation'

const nav = usePageNavigation()
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
  max-width: none;
  margin-inline: 0;

  min-width: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.back-btn:hover {
  background: var(--kf-muted);
  color: var(--kf-primary);
}
.back-btn:active {
  background: var(--kf-muted);
  transform: scale(0.96);
}
.back-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

.header-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  height: 38px;
  padding: 0 var(--kf-space-4);
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
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
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}
.header-btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.prefs-card {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: 12px;
  padding: var(--kf-space-5) var(--kf-space-6);
  margin-bottom: var(--kf-space-4);
}
.card-head {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  min-width: 0;
}
.card-icon {
  color: var(--kf-primary);
  flex-shrink: 0;
}
.card-desc {
  margin-top: var(--kf-space-2);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  line-height: var(--kf-lh-body-md);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  min-width: 0;
}
.field-label {
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
}
.field-input:not(.kf-form-control) {
  height: 38px;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.field-input {
  width: 100%;
  min-width: 0;
  padding: 0 var(--kf-space-3);
  font-size: var(--kf-fs-body-md);
  font-variant-numeric: tabular-nums;
  color: var(--kf-foreground);
}
.prefs-select {
  width: 100%;
  min-width: 0;
}
.prefs-time {
  width: 100%;
  min-width: 0;
}
.field-input:not(.kf-form-control):hover {
  border-color: var(--kf-primary);
}
.field-input:not(.kf-form-control):focus,
.field-input:not(.kf-form-control):focus-visible {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
}

/* 开关行 */
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-checkbox-gap);
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.switch-row:hover {
  background: var(--kf-muted);
  border-color: var(--kf-primary);
}
.switch-row:active {
  background: color-mix(in srgb, var(--kf-primary) 8%, transparent);
}
.switch-row:has(input[type='checkbox']:focus-visible) {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}
.switch-text {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-1);
  flex: 1;
  min-width: 0;
}
.switch-label {
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-weight: 500;
  color: var(--kf-foreground);
}
.switch-hint {
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
.switch-row input[type='checkbox'] {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  cursor: pointer;
  accent-color: var(--kf-primary);
}
.switch-row input[type='checkbox']:focus-visible:not(.kf-form-control) {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

/* 学习提醒激活区 */
.reminder-block {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  margin-top: var(--kf-space-3);
}
.reminder-perm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--kf-space-3);
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-warning);
  border-radius: var(--kf-radius-sm);
  background: color-mix(in srgb, var(--kf-warning) 8%, transparent);
}
.perm-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  height: 32px;
  padding: 0 var(--kf-space-4);
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  border-radius: var(--kf-radius-sm);
  border: none;
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
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
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}
.push-btn {
  height: 32px;
  padding: 0 var(--kf-space-4);
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.push-btn:hover {
  background: var(--kf-muted);
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.push-btn:active {
  transform: scale(0.98);
  background: color-mix(in srgb, var(--kf-primary) 8%, transparent);
}
.push-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

/* 响应式：小屏收紧卡片内边距（px-4），桌面端保持 px-6 不变 */
@media (max-width: 640px) {
  .prefs-card {
    padding: var(--kf-space-4);
  }
}
</style>
