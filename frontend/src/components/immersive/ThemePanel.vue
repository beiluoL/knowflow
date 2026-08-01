<template>
  <div
    v-show="visible"
    ref="panelRef"
    class="theme-panel"
    @click.stop
  >
    <div class="panel-header">
      <span class="panel-title">个性化偏好</span>
      <button type="button" class="close-btn" @click="$emit('close')">
        <Icon name="x" :size="14" />
      </button>
    </div>

    <div class="panel-section">
      <div class="section-label">沉浸主题</div>
      <div class="theme-scroll">
        <button
          v-for="theme in IMMERSIVE_THEMES"
          :key="theme.id"
          type="button"
          class="theme-card"
          :class="{ active: currentTheme === theme.id }"
          @click="handleSelectTheme(theme.id)"
        >
          <div class="theme-preview" :style="{ background: theme.preview }" />
          <span class="theme-name">{{ theme.name }}</span>
        </button>
      </div>
    </div>

    <div class="panel-section">
      <div class="section-label">强调色</div>
      <div class="accent-row">
        <button
          v-for="accent in ACCENT_PALETTE"
          :key="accent.id"
          type="button"
          class="accent-dot"
          :class="{ active: currentAccent === accent.id }"
          :style="{ background: accent.color }"
          :title="accent.name"
          @click="handleSelectAccent(accent.id)"
        />
      </div>
    </div>

    <div class="panel-section">
      <div class="section-label">字号</div>
      <div class="fontsize-row">
        <button
          v-for="fs in FONT_SIZES"
          :key="fs.id"
          type="button"
          class="fontsize-btn"
          :class="{ active: currentFontSize === fs.id }"
          @click="handleSelectFontSize(fs.id)"
        >
          {{ fs.label }}
        </button>
      </div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import {
  useImmersiveTheme,
  IMMERSIVE_THEMES,
  ACCENT_PALETTE,
  FONT_SIZES,
  RHYTHM_PRESETS_UI,
  applyImmersiveThemeTo,
  setAccent,
  setFontSize,
  setRhythmPreset,
  type ImmersiveThemeId,
  type AccentId,
  type FontSizeId,
  type RhythmPreset,
} from '@/composables/useImmersiveTheme';

const props = defineProps<{
  visible: boolean;
  rootEl?: HTMLElement | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const panelRef = ref<HTMLElement | null>(null);
const { currentTheme, currentAccent, currentFontSize, currentRhythm } = useImmersiveTheme();

function handleSelectTheme(id: ImmersiveThemeId) {
  applyImmersiveThemeTo(props.rootEl ?? null, id);
  currentTheme.value = id;
}

function handleSelectAccent(id: AccentId) {
  setAccent(id);
}

function handleSelectFontSize(id: FontSizeId) {
  setFontSize(id);
}

function handleSelectRhythm(id: RhythmPreset) {
  setRhythmPreset(id);
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
.theme-panel {
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
.panel-section:last-child {
  margin-bottom: 0;
}
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground, #94a3b8);
  margin-bottom: 10px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.theme-scroll,
.rhythm-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}
.theme-scroll::-webkit-scrollbar,
.rhythm-scroll::-webkit-scrollbar {
  height: 6px;
}
.theme-scroll::-webkit-scrollbar-thumb,
.rhythm-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}

.theme-card {
  flex-shrink: 0;
  width: 92px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.02);
  border: 2px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.12s ease;
}
.theme-card:hover {
  transform: translateY(-1px);
}
.theme-card.active {
  border-color: var(--kb-primary, #3b82f6);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--kb-primary, #3b82f6) 25%, transparent);
}
.theme-preview {
  width: 100%;
  height: 54px;
  border-radius: 8px;
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
}
.theme-name {
  text-align: center;
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-foreground, #f8fafc);
}

.accent-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.accent-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  transition: transform 0.12s ease, border-color 0.15s ease;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
}
.accent-dot:hover {
  transform: scale(1.08);
}
.accent-dot.active {
  border-color: var(--kb-foreground, #fff);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--kb-primary, #3b82f6) 40%, transparent);
}

.fontsize-row {
  display: flex;
  gap: 8px;
}
.fontsize-btn {
  flex: 1;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-muted-foreground, #94a3b8);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.fontsize-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--kb-foreground, #f8fafc);
}
.fontsize-btn.active {
  color: var(--kb-primary-foreground, #fff);
  background: var(--kb-primary, #3b82f6);
  border-color: var(--kb-primary, #3b82f6);
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

@media (max-width: 480px) {
  .theme-panel {
    right: 16px;
    left: 16px;
    width: auto;
    top: 72px;
  }
}
</style>
