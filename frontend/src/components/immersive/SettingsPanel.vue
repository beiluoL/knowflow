<template>
  <div
    v-show="visible"
    ref="panelRef"
    class="settings-panel"
    @click.stop
  >
    <div class="panel-header">
      <span class="panel-title">沉浸工作台设置</span>
      <button type="button" class="close-btn" @click="emit('close')">
        <Icon name="x" :size="14" />
      </button>
    </div>

    <div class="panel-section">
      <div class="section-label">节奏模板</div>
      <div class="rhythm-scroll">
        <button
          v-for="rp in RHYTHM_PRESETS_UI"
          :key="rp.id"
          type="button"
          class="rhythm-card"
          :class="{ active: currentRhythm === rp.id }"
          @click="handleSelectRhythm(rp.id)"
        >
          <div class="rhythm-head">
            <span class="rhythm-name">{{ rp.name }}</span>
            <span class="rhythm-tag">{{ rp.focus }}-{{ rp.shortBreak }}-{{ rp.longBreak }}-{{ rp.rounds }}</span>
          </div>
          <p class="rhythm-desc">{{ rp.description }}</p>
        </button>
      </div>
    </div>

    <div class="panel-divider" />

    <div class="setting-row">
      <div class="setting-info">
        <span class="setting-title">休息引导</span>
        <span class="setting-desc">番茄结束后弹出休息提醒卡片</span>
      </div>
      <button
        type="button"
        class="toggle-switch"
        :class="{ on: breakGuideEnabled }"
        :aria-pressed="breakGuideEnabled"
        @click="breakGuideEnabled = !breakGuideEnabled"
      >
        <span class="toggle-knob" />
      </button>
    </div>

    <div class="setting-row">
      <div class="setting-info">
        <span class="setting-title">自动开始下一阶段</span>
        <span class="setting-desc">阶段倒计时结束后自动开始</span>
      </div>
      <button
        type="button"
        class="toggle-switch"
        :class="{ on: pomodoroStore.settings.autoNext }"
        :aria-pressed="pomodoroStore.settings.autoNext"
        @click="toggleAutoNext"
      >
        <span class="toggle-knob" />
      </button>
    </div>

    <div class="setting-row">
      <div class="setting-info">
        <span class="setting-title-with-icon">
          <Icon :name="pomodoroStore.settings.soundEnabled ? 'volume-2' : 'volume-x'" :size="14" />
          <span class="setting-title">声音提醒</span>
        </span>
        <span class="setting-desc">阶段结束时播放提示音</span>
      </div>
      <button
        type="button"
        class="toggle-switch"
        :class="{ on: pomodoroStore.settings.soundEnabled }"
        :title="pomodoroStore.settings.soundEnabled ? '点击关闭：阶段结束不播放提示音' : '点击开启：阶段结束播放提示音'"
        :aria-pressed="pomodoroStore.settings.soundEnabled"
        @click="toggleSoundEnabled"
      >
        <span class="toggle-knob" />
      </button>
    </div>

    <div class="setting-row">
      <div class="setting-info">
        <span class="setting-title">长休息番茄轮次</span>
        <span class="setting-desc">完成 N 轮专注后进入长休息</span>
      </div>
      <div class="number-adjust">
        <button
          type="button"
          class="num-btn minus"
          :disabled="pomodoroStore.settings.roundsPerSet <= 2"
          @click="decrementRounds"
        >
          -
        </button>
        <span class="num-value">{{ pomodoroStore.settings.roundsPerSet }}</span>
        <button
          type="button"
          class="num-btn plus"
          :disabled="pomodoroStore.settings.roundsPerSet >= 10"
          @click="incrementRounds"
        >
          +
        </button>
      </div>
    </div>

    <div class="setting-row">
      <div class="setting-info">
        <span class="setting-title">退出沉浸恢复默认主题</span>
        <span class="setting-desc">关闭沉浸模式时还原全局主题</span>
      </div>
      <button
        type="button"
        class="toggle-switch"
        :class="{ on: resetThemeOnExit }"
        :aria-pressed="resetThemeOnExit"
        @click="resetThemeOnExit = !resetThemeOnExit"
      >
        <span class="toggle-knob" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { usePomodoroStore } from '@/stores/pomodoro';
import {
  useImmersiveTheme,
  RHYTHM_PRESETS_UI,
  setRhythmPreset,
  type RhythmPreset,
} from '@/composables/useImmersiveTheme';

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const panelRef = ref<HTMLElement | null>(null);
const pomodoroStore = usePomodoroStore();
const { currentRhythm, breakGuideEnabled, resetThemeOnExit } = useImmersiveTheme();

function handleSelectRhythm(id: RhythmPreset) {
  setRhythmPreset(id);
}

function toggleAutoNext() {
  pomodoroStore.saveSettings({ autoNext: !pomodoroStore.settings.autoNext });
}

function toggleSoundEnabled() {
  pomodoroStore.saveSettings({ soundEnabled: !pomodoroStore.settings.soundEnabled });
}

function incrementRounds() {
  const next = Math.min(10, pomodoroStore.settings.roundsPerSet + 1);
  pomodoroStore.saveSettings({ roundsPerSet: next });
}

function decrementRounds() {
  const next = Math.max(2, pomodoroStore.settings.roundsPerSet - 1);
  pomodoroStore.saveSettings({ roundsPerSet: next });
}

function onDocClick(e: MouseEvent) {
  if (!props.visible) return;
  if (panelRef.value && !panelRef.value.contains(e.target as Node)) {
    emit('close');
  }
}

function onEsc(e: KeyboardEvent) {
  if (!props.visible) return;
  if (e.key === 'Escape') {
    emit('close');
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      document.addEventListener('click', onDocClick, true);
      document.addEventListener('keydown', onEsc);
    } else {
      document.removeEventListener('click', onDocClick, true);
      document.removeEventListener('keydown', onEsc);
    }
  },
);

onMounted(() => {
  if (props.visible) {
    document.addEventListener('click', onDocClick, true);
    document.addEventListener('keydown', onEsc);
  }
});

onUnmounted(() => {
  document.removeEventListener('click', onDocClick, true);
  document.removeEventListener('keydown', onEsc);
});
</script>

<style scoped>
.settings-panel {
  position: fixed;
  top: 76px;
  right: 24px;
  z-index: 70;
  width: 420px;
  max-width: calc(100vw - 48px);
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  background: var(--kb-bg-2, rgba(15, 23, 42, 0.72));
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 16px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.35);
  padding: 16px;
  color: var(--kb-foreground, #f8fafc);
  animation: panelIn 0.18s ease;
}

@keyframes panelIn {
  from {
    opacity: 0;
    transform: translateY(-6px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--kb-muted-foreground, #94a3b8);
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.close-btn:hover {
  background: rgba(255, 255, 255, 0.06);
  color: var(--kb-foreground, #f8fafc);
}

.panel-section {
  margin-bottom: 18px;
}
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground, #94a3b8);
  margin-bottom: 10px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.rhythm-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}
.rhythm-scroll::-webkit-scrollbar {
  height: 6px;
}
.rhythm-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}

.rhythm-card {
  flex-shrink: 0;
  width: 178px;
  text-align: left;
  padding: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.rhythm-card:hover {
  background: rgba(255, 255, 255, 0.06);
  transform: translateY(-1px);
}
.rhythm-card.active {
  border-color: var(--kb-primary, #3b82f6);
  background: color-mix(in srgb, var(--kb-primary, #3b82f6) 10%, transparent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--kb-primary, #3b82f6) 30%, transparent);
}
.rhythm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.rhythm-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground, #f8fafc);
}
.rhythm-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.08);
  color: var(--kb-muted-foreground, #94a3b8);
}
.rhythm-desc {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--kb-muted-foreground, #94a3b8);
}

.panel-divider {
  height: 1px;
  background: var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  margin: 14px 0 10px;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px;
}
.setting-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
  padding-right: 12px;
}
.setting-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground, #f8fafc);
}
.setting-title-with-icon {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.setting-desc {
  font-size: 11px;
  line-height: 1.5;
  color: var(--kb-muted-foreground, #94a3b8);
}

.toggle-switch {
  position: relative;
  flex-shrink: 0;
  width: 42px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  cursor: pointer;
  transition: background 0.2s ease;
}
.toggle-switch.on {
  background: var(--kb-primary, #3b82f6);
}
.toggle-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25);
  transition: transform 0.2s ease;
}
.toggle-switch.on .toggle-knob {
  transform: translateX(18px);
}
.toggle-switch:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.number-adjust {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
}
.num-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground, #f8fafc);
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.num-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
}
.num-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.num-value {
  min-width: 28px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground, #f8fafc);
}

@media (max-width: 480px) {
  .settings-panel {
    right: 16px;
    left: 16px;
    width: auto;
    top: 72px;
  }
}
</style>
