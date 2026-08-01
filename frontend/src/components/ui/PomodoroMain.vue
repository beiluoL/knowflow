<template>
  <div class="pmd-main">
    <!-- 头部：模式切换 + 声音/设置按钮 -->
    <div class="pmd-head">
      <div class="pmd-mode-tabs" role="tablist" aria-label="番茄钟模式切换">
        <button
          v-for="m in MODES"
          :key="m.value"
          type="button"
          role="tab"
          :aria-selected="store.runtime.currentMode === m.value"
          class="pmd-mode-tab"
          :class="{ active: store.runtime.currentMode === m.value }"
          :style="{
            color: store.runtime.currentMode === m.value ? m.color : 'var(--kb-muted-foreground)',
            background: store.runtime.currentMode === m.value ? m.softBg : 'transparent',
          }"
          @click="store.switchMode(m.value)"
        >
          <Icon :name="m.icon" size="sm" />
          <span>{{ m.label }}</span>
        </button>
      </div>
      <div class="pmd-actions">
        <button
          type="button"
          class="pmd-icon-btn"
          :class="{ 'is-on': store.settings.soundEnabled }"
          :title="store.settings.soundEnabled ? '关闭提示音' : '开启提示音'"
          @click="store.saveSettings({ soundEnabled: !store.settings.soundEnabled })"
        >
          <Icon :name="store.settings.soundEnabled ? 'volume-2' : 'volume-x'" size="sm" />
        </button>
        <button
          type="button"
          class="pmd-icon-btn"
          :class="{ 'is-on': store.runtime.settingsOpen }"
          title="番茄钟设置"
          @click="store.toggleSettings()"
        >
          <Icon name="settings" size="sm" />
        </button>
      </div>
    </div>

    <!-- 主体：环形进度 + 计时 + 控制按钮 -->
    <div class="pmd-body">
      <div class="pmd-ring-wrap">
        <svg viewBox="0 0 180 180" class="pmd-ring" aria-hidden="true">
          <circle cx="90" cy="90" r="78" fill="none" stroke="currentColor" stroke-opacity="0.08" stroke-width="10" />
          <circle
            cx="90"
            cy="90"
            r="78"
            fill="none"
            :stroke="store.modeColor.stroke"
            stroke-width="10"
            stroke-linecap="round"
            :stroke-dasharray="ringCircum"
            :stroke-dashoffset="ringCircum * (1 - store.progress)"
            transform="rotate(-90 90 90)"
            class="pmd-ring-progress"
          />
        </svg>
        <div class="pmd-center">
          <div class="pmd-mode-label" :style="{ color: store.modeColor.fg }">
            <Icon :name="currentModeIcon" size="xs" />
            <span>{{ store.modeLabel }}</span>
          </div>
          <div
            class="pmd-time"
            :class="{ 'is-running': store.runtime.isRunning }"
            :style="{ color: store.modeColor.fg }"
          >
            {{ store.timeFormatted }}
          </div>
          <div class="pmd-sub">
            <div class="pmd-rounds" :title="`本轮 ${store.runtime.roundsCompleted}/${store.settings.roundsPerSet} 个番茄`">
              <span
                v-for="i in store.settings.roundsPerSet"
                :key="i"
                class="pmd-dot"
                :class="{ filled: i <= store.runtime.roundsCompleted }"
                :style="i <= store.runtime.roundsCompleted ? { background: store.modeColor.stroke } : {}"
              />
              <span class="pmd-rounds-text">{{ store.runtime.roundsCompleted }}/{{ store.settings.roundsPerSet }}</span>
            </div>
            <div class="pmd-today" title="今日完成番茄数">
              <Icon name="timer" size="xs" />
              <span>今日 {{ store.runtime.totalPomodorosToday }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 控制按钮 -->
      <div class="pmd-controls">
        <button type="button" class="pmd-ctrl pmd-skip" title="跳过当前阶段" @click="store.skip()">
          <Icon name="skip-forward" size="sm" />
        </button>
        <button
          type="button"
          class="pmd-ctrl pmd-play"
          :style="{ background: store.modeColor.stroke }"
          :title="store.runtime.isRunning ? '暂停' : '开始'"
          @click="store.toggle()"
        >
          <Icon :name="store.runtime.isRunning ? 'pause' : 'play'" size="md" class="text-white" />
        </button>
        <button type="button" class="pmd-ctrl pmd-reset" title="重置当前阶段" @click="store.resetCurrentMode()">
          <Icon name="rotate-ccw" size="sm" />
        </button>
      </div>
    </div>

    <!-- 设置面板 -->
    <transition name="pmd-slide">
      <div v-if="store.runtime.settingsOpen" class="pmd-settings">
        <div class="pmd-settings-head">
          <h4>番茄钟设置</h4>
          <button type="button" class="pmd-icon-btn" title="关闭设置" @click="store.toggleSettings()">
            <Icon name="x" size="sm" />
          </button>
        </div>
        <div class="pmd-fields">
          <label class="pmd-field">
            <span>专注（分钟）</span>
            <input type="number" min="1" max="180" :value="form.focusMinutes" @input="onField('focusMinutes', $event)" />
          </label>
          <label class="pmd-field">
            <span>短休（分钟）</span>
            <input type="number" min="1" max="60" :value="form.shortBreakMinutes" @input="onField('shortBreakMinutes', $event)" />
          </label>
          <label class="pmd-field">
            <span>长休（分钟）</span>
            <input type="number" min="1" max="120" :value="form.longBreakMinutes" @input="onField('longBreakMinutes', $event)" />
          </label>
          <label class="pmd-field">
            <span>一组次数</span>
            <input type="number" min="1" max="12" :value="form.roundsPerSet" @input="onField('roundsPerSet', $event)" />
          </label>
          <label class="pmd-field pmd-switch-row">
            <span>声音提醒</span>
            <button
              type="button"
              class="pmd-switch"
              :class="{ on: form.soundEnabled }"
              @click="form.soundEnabled = !form.soundEnabled"
              :aria-pressed="form.soundEnabled"
            >
              <span class="pmd-switch-thumb" />
            </button>
          </label>
          <label class="pmd-field pmd-switch-row">
            <span>自动进入下一阶段</span>
            <button
              type="button"
              class="pmd-switch"
              :class="{ on: form.autoNext }"
              @click="form.autoNext = !form.autoNext"
              :aria-pressed="form.autoNext"
            >
              <span class="pmd-switch-thumb" />
            </button>
          </label>
        </div>
        <div class="pmd-settings-actions">
          <button type="button" class="pmd-btn pmd-btn-ghost" @click="resetForm()">恢复默认</button>
          <button type="button" class="pmd-btn pmd-btn-primary" @click="submitForm()">保存</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { usePomodoroStore, type PomodoroMode } from '@/stores/pomodoro';

const store = usePomodoroStore();

const MODES: { value: PomodoroMode; label: string; icon: string; color: string; softBg: string }[] = [
  { value: 'focus', label: '专注', icon: 'target', color: '#EF4444', softBg: 'rgba(239,68,68,0.10)' },
  { value: 'shortBreak', label: '短休', icon: 'coffee', color: '#10B981', softBg: 'rgba(16,185,129,0.10)' },
  { value: 'longBreak', label: '长休', icon: 'coffee-2', color: '#3B6FE0', softBg: 'rgba(59,111,224,0.10)' },
];

const currentModeIcon = computed(
  () => MODES.find((m) => m.value === store.runtime.currentMode)?.icon ?? 'timer',
);
const ringCircum = 2 * Math.PI * 78;

const form = reactive({
  focusMinutes: store.settings.focusMinutes,
  shortBreakMinutes: store.settings.shortBreakMinutes,
  longBreakMinutes: store.settings.longBreakMinutes,
  roundsPerSet: store.settings.roundsPerSet,
  soundEnabled: store.settings.soundEnabled,
  autoNext: store.settings.autoNext,
});

watch(
  () => store.runtime.settingsOpen,
  (open) => {
    if (open) {
      form.focusMinutes = store.settings.focusMinutes;
      form.shortBreakMinutes = store.settings.shortBreakMinutes;
      form.longBreakMinutes = store.settings.longBreakMinutes;
      form.roundsPerSet = store.settings.roundsPerSet;
      form.soundEnabled = store.settings.soundEnabled;
      form.autoNext = store.settings.autoNext;
    }
  },
);

type FieldKey = 'focusMinutes' | 'shortBreakMinutes' | 'longBreakMinutes' | 'roundsPerSet';
function onField(key: FieldKey, e: Event) {
  const target = e.target as HTMLInputElement;
  const val = Math.max(1, Number(target.value) || 1);
  form[key] = val;
}

function resetForm() {
  form.focusMinutes = 25;
  form.shortBreakMinutes = 5;
  form.longBreakMinutes = 15;
  form.roundsPerSet = 4;
  form.soundEnabled = true;
  form.autoNext = false;
}

function submitForm() {
  store.saveSettings({
    focusMinutes: clampInt(form.focusMinutes, 1, 180),
    shortBreakMinutes: clampInt(form.shortBreakMinutes, 1, 60),
    longBreakMinutes: clampInt(form.longBreakMinutes, 1, 120),
    roundsPerSet: clampInt(form.roundsPerSet, 1, 12),
    soundEnabled: form.soundEnabled,
    autoNext: form.autoNext,
  });
}

function clampInt(n: number, min: number, max: number): number {
  const v = Math.floor(Number(n) || min);
  return Math.min(max, Math.max(min, v));
}
</script>

<style scoped>
.pmd-main {
  width: 100%;
  color: var(--kb-foreground);
}

/* 头部 */
.pmd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  gap: 8px;
  border-bottom: 1px solid var(--kb-border);
}
.pmd-mode-tabs {
  display: inline-flex;
  padding: 2px;
  border-radius: 8px;
  background: var(--kb-muted);
  gap: 1px;
}
.pmd-mode-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease;
  color: var(--kb-muted-foreground);
  white-space: nowrap;
}
.pmd-mode-tab:hover {
  color: var(--kb-foreground);
}
.pmd-mode-tab.active {
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.pmd-actions {
  display: flex;
  align-items: center;
  gap: 1px;
}
.pmd-icon-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s ease;
}
.pmd-icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.pmd-icon-btn.is-on {
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
}

/* 主体 */
.pmd-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 16px 12px;
  gap: 12px;
}

.pmd-ring-wrap {
  position: relative;
  width: 160px;
  height: 160px;
  flex-shrink: 0;
}
.pmd-ring {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  color: var(--kb-foreground);
}
.pmd-ring-progress {
  transition: stroke-dashoffset 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}
.pmd-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.pmd-mode-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  opacity: 0.85;
}
.pmd-time {
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
  line-height: 1.1;
  transition: color 0.3s ease;
}
.pmd-time.is-running {
  text-shadow: 0 0 16px currentColor;
}
.pmd-sub {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--kb-muted-foreground);
  font-size: 10px;
  font-weight: 500;
}
.pmd-rounds {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.pmd-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--kb-border);
  transition: background 0.25s ease;
}
.pmd-dot.filled {
  box-shadow: 0 0 4px currentColor;
}
.pmd-rounds-text {
  margin-left: 2px;
  font-family: var(--font-mono);
  font-size: 10px;
}
.pmd-today {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

/* 控制按钮 */
.pmd-controls {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.pmd-ctrl {
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.pmd-ctrl:hover {
  color: var(--kb-foreground);
  border-color: var(--kb-primary);
  box-shadow: 0 3px 10px rgba(59, 111, 224, 0.12);
  transform: translateY(-1px);
}
.pmd-play {
  width: 52px;
  height: 52px;
  border: none;
  color: #fff;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.pmd-play:hover {
  color: #fff;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.2);
  transform: translateY(-2px) scale(1.04);
}

/* 设置面板 */
.pmd-settings {
  border-top: 1px solid var(--kb-border);
  background: var(--kb-background);
  padding: 12px 14px 14px;
}
.pmd-settings-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.pmd-settings-head h4 {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.pmd-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
  margin-bottom: 12px;
}
.pmd-field {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}
.pmd-field > input {
  height: 30px;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  padding: 0 8px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.pmd-field > input:focus {
  outline: none;
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}
.pmd-switch-row {
  flex-direction: row !important;
  align-items: center;
  justify-content: space-between;
  grid-column: 1 / -1;
}
.pmd-switch {
  position: relative;
  width: 36px;
  height: 20px;
  border-radius: 999px;
  border: none;
  background: var(--kb-border);
  cursor: pointer;
  padding: 0;
  transition: background 0.2s ease;
}
.pmd-switch.on {
  background: var(--kb-primary);
}
.pmd-switch-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s cubic-bezier(0.22, 1, 0.36, 1);
}
.pmd-switch.on .pmd-switch-thumb {
  transform: translateX(16px);
}

.pmd-settings-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
.pmd-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
}
.pmd-btn-primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.pmd-btn-primary:hover {
  filter: brightness(1.05);
  box-shadow: 0 3px 10px rgba(59, 111, 224, 0.2);
}
.pmd-btn-ghost {
  background: transparent;
  border-color: var(--kb-border);
  color: var(--kb-muted-foreground);
}
.pmd-btn-ghost:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

/* 过渡 */
.pmd-slide-enter-active,
.pmd-slide-leave-active {
  transition: all 0.22s cubic-bezier(0.22, 1, 0.36, 1);
  overflow: hidden;
}
.pmd-slide-enter-from,
.pmd-slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  margin-top: -1px;
}
.pmd-slide-enter-to,
.pmd-slide-leave-from {
  opacity: 1;
  max-height: 340px;
}
</style>
