<template>
  <div class="break-overlay" @click.self>
    <div class="break-card">
      <div class="badge-row">
        <span class="badge" :class="breakType">
          {{ breakType === 'shortBreak' ? '☕ 短休息' : '🌿 长休息' }}
        </span>
      </div>

      <h2 class="break-title">{{ currentTip }}</h2>

      <div class="countdown-wrap">
        <div class="countdown-time">{{ timeFormatted }}</div>
        <div class="countdown-label">休息倒计时</div>
      </div>

      <div
        class="gif-placeholder"
        :style="{
          background:
            breakType === 'shortBreak'
              ? 'linear-gradient(135deg, #10B981 0%, #059669 50%, #047857 100%)'
              : 'linear-gradient(135deg, #6366F1 0%, #8B5CF6 50%, #EC4899 100%)',
        }"
      >
        <span class="gif-text">放松一下~</span>
        <span class="gif-subtext">💧🎯🧘‍♂️</span>
      </div>

      <button
        type="button"
        class="continue-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        @click="handleDismiss"
      >
        我已休息，继续学习
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { notify } from '@/utils/toast';

const props = defineProps<{
  breakType: 'shortBreak' | 'longBreak';
  durationSec: number;
}>();

const emit = defineEmits<{
  (e: 'dismiss'): void;
}>();

const SHORT_TIPS = [
  '起来喝口水，给身体补充水分~',
  '远眺窗外 20 秒，缓解眼疲劳',
  '站起来走走，舒展一下身体',
  '做几个深呼吸，放松紧绷的肩颈',
  '闭上眼睛，让眼睛休息一下',
  '转动脖子，左右各 10 次',
];

const LONG_TIPS = [
  '离开座位，去阳台或窗边晒晒太阳',
  '做一组简单的拉伸运动，放松全身',
  '和朋友聊聊天，换个心情再回来',
  '泡杯茶或咖啡，享受片刻宁静',
  '闭上眼睛冥想 5 分钟，清空杂念',
  '去洗把脸，清爽一下再继续',
];

const tipsPool = computed(() =>
  props.breakType === 'shortBreak' ? SHORT_TIPS : LONG_TIPS,
);
const currentTip = ref('');
const timeLeft = ref(props.durationSec);

let timer: number | null = null;

const timeFormatted = computed(() => {
  const s = Math.max(0, timeLeft.value);
  const mins = Math.floor(s / 60);
  const secs = s % 60;
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
});

function pickRandomTip() {
  const pool = tipsPool.value;
  const idx = Math.floor(Math.random() * pool.length);
  currentTip.value = pool[idx];
}

function handleDismiss() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
  notify('准备好啦，继续加油！', 'success');
  emit('dismiss');
}

onMounted(() => {
  pickRandomTip();
  timer = window.setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value -= 1;
    } else {
      handleDismiss();
    }
  }, 1000);
});

onUnmounted(() => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
});
</script>

<style scoped>
.break-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(0, 0, 0, 0.62);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  animation: overlayIn 0.2s ease;
}

@keyframes overlayIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.break-card {
  width: 100%;
  max-width: 460px;
  background: var(--kb-bg-1, #1e293b);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.1));
  border-radius: 20px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5);
  padding: 28px 26px;
  color: var(--kb-foreground, #f8fafc);
  text-align: center;
  animation: cardIn 0.28s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.badge-row {
  margin-bottom: 14px;
}
.badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 700;
  border-radius: 999px;
  letter-spacing: 0.02em;
}
.badge.shortBreak {
  background: rgba(16, 185, 129, 0.14);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.25);
}
.badge.longBreak {
  background: rgba(139, 92, 246, 0.16);
  color: #a78bfa;
  border: 1px solid rgba(139, 92, 246, 0.28);
}

.break-title {
  margin: 0 0 20px;
  font-size: 17px;
  line-height: 1.55;
  font-weight: 600;
  color: var(--kb-foreground, #f8fafc);
}

.countdown-wrap {
  margin-bottom: 22px;
}
.countdown-time {
  font-size: 56px;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1;
  color: var(--kb-primary, #3b82f6);
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 32px color-mix(in srgb, var(--kb-primary, #3b82f6) 30%, transparent);
}
.countdown-label {
  margin-top: 8px;
  font-size: 12px;
  color: var(--kb-muted-foreground, #94a3b8);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-weight: 600;
}

.gif-placeholder {
  width: 100%;
  max-width: 280px;
  height: 120px;
  margin: 0 auto 22px;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}
.gif-text {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
.gif-subtext {
  font-size: 20px;
  letter-spacing: 2px;
}

.continue-btn {
  width: 100%;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: var(--kb-primary-foreground, #fff);
  background: var(--kb-primary, #3b82f6);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: filter 0.15s ease, transform 0.1s ease;
  box-shadow: 0 8px 22px color-mix(in srgb, var(--kb-primary, #3b82f6) 35%, transparent);
}
.continue-btn:hover {
  filter: brightness(1.06);
}
.continue-btn:active {
  transform: scale(0.985);
}
</style>
