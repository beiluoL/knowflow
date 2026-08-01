<template>
  <!--
    macOS 风格导航栏番茄钟：
    - 收起态：导航栏中的小圆环图标（28px），显示进度环 + 微型倒计时
    - 展开态：点击弹出 popover 面板，包含计时、控制、设置
    - 跨页面保持状态（Pinia store + localStorage 持久化）
  -->
  <div class="pmd-nav-wrap" ref="wrapRef">
    <!-- 导航栏小图标按钮 -->
    <button
      type="button"
      class="pmd-nav-btn"
      :class="{ 'is-running': store.runtime.isRunning, 'is-active': showPanel }"
      :title="store.modeLabel + ' · ' + store.timeFormatted"
      @click="togglePanel"
      aria-label="番茄钟"
    >
      <!-- 微型环形进度（28px） -->
      <svg viewBox="0 0 32 32" class="pmd-nav-ring" aria-hidden="true">
        <circle cx="16" cy="16" r="13.5" fill="none" stroke="currentColor" stroke-opacity="0.12" stroke-width="2.5" />
        <circle
          cx="16"
          cy="16"
          r="13.5"
          fill="none"
          :stroke="store.modeColor.stroke"
          stroke-width="2.5"
          stroke-linecap="round"
          :stroke-dasharray="navRingCircum"
          :stroke-dashoffset="navRingCircum * (1 - store.progress)"
          transform="rotate(-90 16 16)"
          class="pmd-nav-ring-progress"
        />
      </svg>
      <!-- 中心番茄图标 / 倒计时 -->
      <span class="pmd-nav-center" :style="{ color: store.modeColor.fg }">
        <template v-if="store.runtime.isRunning || store.runtime.timeLeft < store.totalDuration">
          <span class="pmd-nav-time">{{ store.timeFormatted }}</span>
        </template>
        <template v-else>
          <svg viewBox="0 0 24 24" class="pmd-nav-tomato" fill="currentColor" aria-hidden="true">
            <path d="M12 2c-.5 0-1 .2-1.3.5L9 4.5C7.5 3.7 5.8 4 5 4.5c-.5.3-.7 1-.4 1.5.2.4.6.6 1 .6.1 0 .3 0 .4-.1.5-.3 1.4-.4 2.2-.1L7 7.5C5 9.5 4 12 4 14.5 4 18.6 7.6 22 12 22s8-3.4 8-7.5c0-2.5-1-5-3-7l-1.2-1.2c.8-.2 1.7-.1 2.2.1.1 0 .3.1.4.1.4 0 .8-.2 1-.6.3-.5.1-1.2-.4-1.5-.8-.5-2.5-.8-4 0L13.3 2.5C13 2.2 12.5 2 12 2z" />
          </svg>
        </template>
      </span>
      <!-- 运行中呼吸点 -->
      <span v-if="store.runtime.isRunning" class="pmd-nav-pulse" :style="{ background: store.modeColor.stroke }" />
    </button>

    <!-- popover 面板 -->
    <transition name="pmd-pop">
      <div v-if="showPanel" class="pmd-popover" role="dialog" aria-label="番茄钟">
        <!-- 小三角 -->
        <span class="pmd-pop-arrow" />
        <PomodoroMain />
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { usePomodoroStore } from '@/stores/pomodoro';
import PomodoroMain from './PomodoroMain.vue';

const store = usePomodoroStore();
const showPanel = ref(false);
const wrapRef = ref<HTMLElement | null>(null);

const navRingCircum = 2 * Math.PI * 13.5;

function togglePanel() {
  showPanel.value = !showPanel.value;
}

function handleClickOutside(e: MouseEvent) {
  if (!wrapRef.value) return;
  if (!wrapRef.value.contains(e.target as Node)) {
    showPanel.value = false;
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') showPanel.value = false;
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside);
  document.addEventListener('keydown', handleKeydown);
});
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
  document.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.pmd-nav-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
}

/* 导航栏小按钮（macOS menu bar 风格） */
.pmd-nav-btn {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease, border-color 0.15s ease;
  color: var(--kb-muted-foreground);
}
.pmd-nav-btn:hover {
  background: var(--kb-muted);
}
.pmd-nav-btn.is-active {
  background: var(--kb-muted);
  border-color: var(--kb-border);
}
.pmd-nav-btn.is-running {
  color: var(--kb-foreground);
}

.pmd-nav-ring {
  position: absolute;
  inset: 4px;
  width: calc(100% - 8px);
  height: calc(100% - 8px);
}
.pmd-nav-ring-progress {
  transition: stroke-dashoffset 0.6s ease;
}

.pmd-nav-center {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
  line-height: 1;
}
.pmd-nav-tomato {
  width: 16px;
  height: 16px;
  opacity: 0.85;
}
.pmd-nav-btn.is-running .pmd-nav-tomato {
  display: none;
}

/* 运行中呼吸点 */
.pmd-nav-pulse {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  animation: pmd-nav-breathe 2s ease-in-out infinite;
}
@keyframes pmd-nav-breathe {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.15); }
}

/* popover 面板 */
.pmd-popover {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 60;
  width: 340px;
  max-width: calc(100vw - 32px);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  box-shadow:
    0 12px 40px rgba(15, 23, 42, 0.16),
    0 4px 12px rgba(15, 23, 42, 0.08),
    0 0 0 1px rgba(0, 0, 0, 0.02);
  overflow: hidden;
  animation: pmd-pop-in 0.18s cubic-bezier(0.22, 1, 0.36, 1);
}
.pmd-pop-arrow {
  position: absolute;
  top: -6px;
  right: 14px;
  width: 12px;
  height: 12px;
  background: var(--kb-card);
  border-left: 1px solid var(--kb-border);
  border-top: 1px solid var(--kb-border);
  transform: rotate(45deg);
  border-radius: 3px 0 0 0;
}
@keyframes pmd-pop-in {
  from {
    opacity: 0;
    transform: translateY(-6px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 过渡 */
.pmd-pop-enter-active,
.pmd-pop-leave-active {
  transition: all 0.18s cubic-bezier(0.22, 1, 0.36, 1);
}
.pmd-pop-enter-from,
.pmd-pop-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.97);
}
</style>
