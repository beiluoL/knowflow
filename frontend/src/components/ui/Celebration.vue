<template>
  <!-- 全局「获得证书」庆祝弹窗宿主：全屏遮罩 + 彩带飘落 + 金色奖章卡片 -->
  <transition-group name="celebrate">
    <div
      v-for="item in celebrateState.items"
      :key="item.id"
      class="fixed inset-0 z-[70] flex items-center justify-center px-4 celebrate-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="获得证书"
    >
      <!-- 彩带层 -->
      <div class="confetti-layer" aria-hidden="true">
        <i
          v-for="n in CONFETTI_COUNT"
          :key="n"
          class="confetti-piece"
          :style="confettiStyle(n)"
        ></i>
      </div>

      <!-- 庆祝卡片 -->
      <div class="celebrate-card">
        <span class="card-glow" aria-hidden="true"></span>
        <button
          type="button"
          class="close-btn"
          aria-label="关闭"
          @click="dismissCelebration(item.id)"
        >
          <Icon name="x" :size="18" />
        </button>

        <div class="medal-wrap">
          <Icon name="award" :size="44" class="medal-icon" />
          <span class="medal-ring" aria-hidden="true"></span>
        </div>

        <p class="celebrate-kicker">CONGRATULATIONS</p>
        <h2 class="celebrate-title">恭喜获得证书</h2>
        <p class="celebrate-subtitle">你已完成学习路径《{{ item.cert.pathTitle || '该路径' }}》</p>

        <div class="cert-meta">
          <div class="cert-meta-row">
            <span class="meta-label">证书编号</span>
            <span class="meta-value">{{ item.cert.certNo || '—' }}</span>
          </div>
          <div class="cert-meta-row">
            <span class="meta-label">持证人</span>
            <span class="meta-value">{{ item.cert.userName || '—' }}</span>
          </div>
          <div class="cert-meta-row">
            <span class="meta-label">颁发时间</span>
            <span class="meta-value">{{ formatDate(item.cert.issueDate) }}</span>
          </div>
        </div>

        <div class="celebrate-actions">
          <button type="button" class="view-cert-btn" @click="goToCertificate(item)">
            <Icon name="award" :size="15" />
            <span>查看证书</span>
          </button>
          <button type="button" class="later-btn" @click="dismissCelebration(item.id)">
            稍后查看
          </button>
        </div>
      </div>
    </div>
  </transition-group>
</template>

<script setup lang="ts">
// 全局「获得证书」庆祝弹窗：参考 ToastHost 的全屏宿主结构。
// 用纯 CSS 彩带动效（@keyframes 飘落多彩细条）营造游戏化成功反馈，不引入外部特效库。
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { celebrateState, dismissCelebration, type CelebrationItem } from '@/utils/celebrate';

const router = useRouter();

/** 彩带数量（生成对应数量的飘落条带）。 */
const CONFETTI_COUNT = 24;

/** 彩带配色（有限庆祝色板，与设计稿 golden/green/blue/purple 一致）。 */
const CONFETTI_COLORS = ['#F5B940', '#10B981', '#3B6FE0', '#A855F7', '#F59E0B'];

// 彩带随机布局与动画参数（固定取值避免每次渲染抖动）
const confettiStyle = (n: number): Record<string, string> => {
  const left = (n * 37 + 11) % 100;
  const delay = (n * 0.16) % 1.6;
  const duration = 3.2 + (n % 3) * 0.35;
  const hue = n % CONFETTI_COLORS.length;
  return {
    left: `${left}%`,
    background: CONFETTI_COLORS[hue],
    animationDelay: `${delay}s`,
    animationDuration: `${duration}s`,
    transform: `rotate(${(n * 47) % 360}deg)`,
  };
};

// 展示证书颁发日期（后端为 LocalDateTime 字符串），解析失败则原样返回
const formatDate = (raw?: string): string => {
  if (!raw) return '—';
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  const pad = (v: number) => String(v).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

// 「查看证书」：关闭弹窗并跳转证书详情页
const goToCertificate = (item: CelebrationItem) => {
  dismissCelebration(item.id);
  router.push(`/certificate/${item.cert.id}`);
};
</script>

<style scoped>
/* ===== 全屏遮罩 ===== */
.celebrate-overlay {
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
}

/* ===== 彩带层 ===== */
.confetti-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.confetti-piece {
  position: absolute;
  top: -20px;
  width: 10px;
  height: 18px;
  border-radius: 2px;
  opacity: 0;
  animation-name: confetti-fall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}
@keyframes confetti-fall {
  0% {
    opacity: 0;
    transform: translateY(-24px) rotate(0deg);
  }
  10% {
    opacity: 1;
  }
  100% {
    opacity: 0.4;
    transform: translateY(110vh) rotate(720deg);
  }
}

/* ===== 庆祝卡片 ===== */
.celebrate-card {
  position: relative;
  width: 100%;
  max-width: 420px;
  padding: 36px 32px 30px;
  border-radius: 20px;
  text-align: center;
  overflow: hidden;
  border: 1px solid rgba(245, 185, 64, 0.45);
  background:
    linear-gradient(160deg, rgba(245, 185, 64, 0.12), rgba(255, 255, 255, 0.02) 45%),
    var(--kb-card);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35), 0 0 40px rgba(245, 185, 64, 0.18);
}
.card-glow {
  position: absolute;
  top: -90px;
  left: 50%;
  transform: translateX(-50%);
  width: 240px;
  height: 240px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(245, 185, 64, 0.28), transparent 70%);
  pointer-events: none;
}
.close-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.close-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

/* 金色奖章 */
.medal-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 84px;
  height: 84px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: linear-gradient(145deg, #f7c948, #b8860b);
  box-shadow: 0 8px 22px rgba(245, 185, 64, 0.4);
}
.medal-icon {
  color: #fff;
  animation: medal-pop 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.medal-ring {
  position: absolute;
  inset: -8px;
  border: 2px dashed rgba(245, 185, 64, 0.5);
  border-radius: 50%;
  animation: medal-spin 12s linear infinite;
}
@keyframes medal-pop {
  from {
    transform: scale(0);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
@keyframes medal-spin {
  to {
    transform: rotate(360deg);
  }
}

.celebrate-kicker {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.28em;
  color: var(--kb-warning);
}
.celebrate-title {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-foreground);
}
.celebrate-subtitle {
  margin: 0 0 18px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
}

/* 证书信息行 */
.cert-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.cert-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.meta-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.meta-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 操作区 */
.celebrate-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}
.view-cert-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #f5b940, #d99a1f);
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(245, 185, 64, 0.35);
  transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
}
.view-cert-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 22px rgba(245, 185, 64, 0.42);
}
.later-btn {
  height: 40px;
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: transparent;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.later-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

/* 入场/离场过渡 */
.celebrate-enter-active,
.celebrate-leave-active {
  transition: opacity 0.3s ease;
}
.celebrate-enter-active .celebrate-card,
.celebrate-leave-active .celebrate-card {
  transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.3s ease;
}
.celebrate-enter-from,
.celebrate-leave-to {
  opacity: 0;
}
.celebrate-enter-from .celebrate-card {
  transform: scale(0.8);
  opacity: 0;
}
.celebrate-leave-to .celebrate-card {
  transform: scale(0.92);
  opacity: 0;
}
</style>
