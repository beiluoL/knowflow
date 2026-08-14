<template>
  <!--
    进度分享卡片生成器：弹窗内用 Canvas 绘制 375x600 竖版学习进度卡片，
    支持「保存图片」下载为 PNG。v-model 控制显隐，点击遮罩可关闭。
  -->
  <Teleport to="body">
    <transition name="sc-fade">
      <div
        v-if="modelValue"
        class="sc-overlay"
        role="dialog"
        aria-modal="true"
        aria-label="进度分享卡片"
        @click.self="close"
        @keydown.esc="close"
      >
        <div class="sc-dialog">
          <header class="sc-header">
            <h3 class="sc-title">进度分享卡片</h3>
            <button type="button" class="sc-icon-btn transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" aria-label="关闭" @click="close">
              <Icon name="x" :size="18" aria-hidden="true" />
            </button>
          </header>

          <div class="sc-canvas-wrap">
            <canvas ref="canvasRef" width="375" height="600" class="sc-canvas" />
          </div>

          <footer class="sc-actions">
            <button type="button" class="sc-btn sc-btn-ghost transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" @click="close">关闭</button>
            <button type="button" class="sc-btn sc-btn-primary" :disabled="saving" @click="saveImage">
              <Icon name="download" :size="16" aria-hidden="true" />
              <span>{{ saving ? '生成中…' : '保存图片' }}</span>
            </button>
          </footer>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
// 进度分享卡片生成器：用 Canvas API 绘制可下载的学习进度分享图。
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { notify, getApiError } from '@/utils/toast';

interface ShareCardProps {
  /** 是否显示弹窗模式（true 时作为对话框覆盖层） */
  modelValue: boolean;
  /** 用户名 */
  userName: string;
  /** 连续学习天数 */
  streakDays: number;
  /** 今日学习分钟 */
  todayMinutes: number;
  /** 本周学习小时 */
  weekHours: number;
  /** 学习路径数 */
  pathCount?: number;
  /** 闪卡掌握数 */
  flashcardMastered?: number;
  /** 用户头像 URL（可选） */
  avatarUrl?: string;
}

const props = withDefaults(defineProps<ShareCardProps>(), {
  pathCount: 0,
  flashcardMastered: 0,
  avatarUrl: '',
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const canvasRef = ref<HTMLCanvasElement | null>(null);
const avatarImg = ref<HTMLImageElement | null>(null);
const saving = ref(false);

/** Canvas 逻辑尺寸（竖版卡片） */
const CARD_W = 375;
const CARD_H = 600;

/** 仿二维码点阵（8x8 固定图案，保证每次绘制一致） */
const QR_PATTERN = [
  '10100101',
  '01011010',
  '10101101',
  '01010110',
  '10101010',
  '01011001',
  '10100110',
  '01011010',
];

/** 当日日期文案（YYYY年M月D日） */
const todayStr = computed(() => {
  const d = new Date();
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
});

/** 用户名首字母（用于头像回退） */
const initial = computed(() => {
  const n = props.userName?.trim();
  return n ? n.charAt(0).toUpperCase() : 'K';
});

/** 圆角矩形路径（兼容性优于 ctx.roundRect） */
function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
}

/** 绘制完整卡片 */
function drawCard(): void {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  canvas.width = CARD_W;
  canvas.height = CARD_H;
  ctx.clearRect(0, 0, CARD_W, CARD_H);

  // 1. 背景渐变（#3B6FE0 → #6F9AF2）
  const grad = ctx.createLinearGradient(0, 0, CARD_W, CARD_H);
  grad.addColorStop(0, '#3B6FE0');
  grad.addColorStop(1, '#6F9AF2');
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, CARD_W, CARD_H);

  // 2. 装饰几何图形
  ctx.fillStyle = 'rgba(255,255,255,0.08)';
  ctx.beginPath();
  ctx.arc(CARD_W - 36, 96, 72, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(28, CARD_H - 56, 92, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = 'rgba(255,255,255,0.06)';
  ctx.beginPath();
  ctx.arc(CARD_W - 80, CARD_H - 130, 42, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = 'rgba(255,255,255,0.22)';
  const dots: Array<[number, number]> = [
    [44, 168],
    [60, 188],
    [76, 172],
    [CARD_W - 64, 268],
    [CARD_W - 48, 288],
    [CARD_W - 76, 296],
  ];
  for (const [dx, dy] of dots) {
    ctx.beginPath();
    ctx.arc(dx, dy, 2, 0, Math.PI * 2);
    ctx.fill();
  }

  ctx.textBaseline = 'middle';

  // 3. 顶部：Logo + 日期
  ctx.fillStyle = '#FFFFFF';
  ctx.font = "700 22px Georgia, 'Times New Roman', serif";
  ctx.textAlign = 'left';
  ctx.fillText('KnowFlow', 24, 48);

  ctx.font = "12px -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";
  ctx.fillStyle = 'rgba(255,255,255,0.82)';
  ctx.textAlign = 'right';
  ctx.fillText(todayStr.value, CARD_W - 24, 48);

  // 4. 头像 + 用户名
  const ax = CARD_W / 2;
  const ay = 120;
  const ar = 26;
  if (avatarImg.value) {
    ctx.save();
    ctx.beginPath();
    ctx.arc(ax, ay, ar, 0, Math.PI * 2);
    ctx.closePath();
    ctx.clip();
    ctx.drawImage(avatarImg.value, ax - ar, ay - ar, ar * 2, ar * 2);
    ctx.restore();
  } else {
    ctx.fillStyle = 'rgba(255,255,255,0.25)';
    ctx.beginPath();
    ctx.arc(ax, ay, ar, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.font = "700 22px Georgia, 'Times New Roman', serif";
    ctx.textAlign = 'center';
    ctx.fillText(initial.value, ax, ay);
  }
  ctx.fillStyle = '#fff';
  ctx.font = "600 14px -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";
  ctx.textAlign = 'center';
  ctx.fillText(props.userName || '学习者', ax, ay + 44);

  // 5. 中部：连续学习天数大号数字
  ctx.fillStyle = '#fff';
  ctx.font = "700 72px Georgia, 'Times New Roman', serif";
  ctx.textAlign = 'center';
  ctx.fillText(String(props.streakDays ?? 0), ax, 224);

  ctx.font = "13px -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";
  ctx.fillStyle = 'rgba(255,255,255,0.85)';
  ctx.fillText('已连续学习（天）', ax, 262);

  // 6. 三个统计卡片：今日分钟 / 本周小时 / 闪卡掌握
  const cards = [
    { value: String(props.todayMinutes ?? 0), label: '今日 · 分钟' },
    { value: String(props.weekHours ?? 0), label: '本周 · 小时' },
    { value: String(props.flashcardMastered ?? 0), label: '闪卡 · 掌握' },
  ];
  const cw = 105;
  const ch = 92;
  const gap = 12;
  const cy = 296;
  const totalW = cw * 3 + gap * 2;
  let cx = (CARD_W - totalW) / 2;
  for (const c of cards) {
    ctx.fillStyle = 'rgba(255,255,255,0.15)';
    roundRect(ctx, cx, cy, cw, ch, 12);
    ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.25)';
    ctx.lineWidth = 1;
    roundRect(ctx, cx, cy, cw, ch, 12);
    ctx.stroke();

    ctx.fillStyle = '#fff';
    ctx.font = "700 24px Georgia, 'Times New Roman', serif";
    ctx.textAlign = 'center';
    ctx.fillText(c.value, cx + cw / 2, cy + 38);

    ctx.font = "11px -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";
    ctx.fillStyle = 'rgba(255,255,255,0.82)';
    ctx.fillText(c.label, cx + cw / 2, cy + 66);
    cx += cw + gap;
  }

  // 7. 激励文案
  ctx.fillStyle = '#fff';
  ctx.font = "600 16px -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";
  ctx.textAlign = 'center';
  ctx.fillText('坚持学习，未来可期', CARD_W / 2, 420);

  // 8. 二维码占位区（白底 + 点阵 + 三个定位角 + 文案）
  const qrSize = 72;
  const qrx = (CARD_W - qrSize) / 2;
  const qry = 450;
  ctx.fillStyle = '#fff';
  roundRect(ctx, qrx, qry, qrSize, qrSize, 8);
  ctx.fill();

  // 点阵
  ctx.fillStyle = '#3B6FE0';
  const cell = 6;
  const gridOff = (qrSize - cell * QR_PATTERN.length) / 2;
  for (let r = 0; r < QR_PATTERN.length; r++) {
    for (let c = 0; c < QR_PATTERN[r].length; c++) {
      if (QR_PATTERN[r][c] === '1') {
        ctx.fillRect(qrx + gridOff + c * cell, qry + gridOff + r * cell, cell, cell);
      }
    }
  }
  // 三个定位角
  const drawFinder = (fx: number, fy: number) => {
    ctx.fillStyle = '#3B6FE0';
    ctx.fillRect(fx, fy, 16, 16);
    ctx.fillStyle = '#fff';
    ctx.fillRect(fx + 4, fy + 4, 8, 8);
  };
  drawFinder(qrx + 5, qry + 5);
  drawFinder(qrx + qrSize - 21, qry + 5);
  drawFinder(qrx + 5, qry + qrSize - 21);

  ctx.fillStyle = 'rgba(255,255,255,0.9)';
  ctx.font = "12px -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";
  ctx.textAlign = 'center';
  ctx.fillText('扫码使用 KnowFlow', CARD_W / 2, qry + qrSize + 22);
}

/** 异步加载头像图（设置 crossOrigin 避免污染 canvas 导致 toBlob 失败） */
function loadAvatar(url?: string): void {
  avatarImg.value = null;
  if (!url) return;
  const img = new Image();
  img.crossOrigin = 'anonymous';
  img.onload = () => {
    avatarImg.value = img;
    if (props.modelValue) drawCard();
  };
  img.onerror = () => {
    avatarImg.value = null;
  };
  img.src = url;
}

/** 保存为 PNG 并触发下载 */
async function saveImage(): Promise<void> {
  const canvas = canvasRef.value;
  if (!canvas) return;
  saving.value = true;
  try {
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
    if (!blob) {
      notify('生成图片失败，请重试', 'error');
      return;
    }
    const url = URL.createObjectURL(blob);
    const safeName = (props.userName || 'share').replace(/[^\w\u4e00-\u9fa5-]/g, '-');
    const a = document.createElement('a');
    a.href = url;
    a.download = `knowflow-${safeName}-${Date.now()}.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    notify('图片已保存到本地', 'success');
  } catch (e: unknown) {
    notify(getApiError(e, '保存失败，请重试'), 'error');
  } finally {
    saving.value = false;
  }
}

function close(): void {
  emit('update:modelValue', false);
}

function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape' && props.modelValue) close();
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      void nextTick(drawCard);
    }
  },
);

watch(
  [
    () => props.userName,
    () => props.streakDays,
    () => props.todayMinutes,
    () => props.weekHours,
    () => props.flashcardMastered,
    () => props.pathCount,
  ],
  () => {
    if (props.modelValue) drawCard();
  },
);

watch(() => props.avatarUrl, (url) => loadAvatar(url), { immediate: true });

onMounted(() => {
  document.addEventListener('keydown', handleKeydown);
  if (props.modelValue) void nextTick(drawCard);
});

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.sc-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}

.sc-dialog {
  width: 100%;
  max-width: 440px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.28);
  overflow: hidden;
}

.sc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--kb-border);
}

.sc-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.sc-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.sc-icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.sc-canvas-wrap {
  padding: 18px;
  overflow: auto;
  display: flex;
  justify-content: center;
  background: var(--kb-muted);
}

.sc-canvas {
  width: 375px;
  max-width: 100%;
  height: auto;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.18);
}

.sc-actions {
  display: flex;
  gap: 10px;
  padding: 14px 18px;
  border-top: 1px solid var(--kb-border);
}

.sc-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, opacity 0.15s ease;
}

.sc-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.sc-btn-ghost {
  flex: 0 0 auto;
  background: transparent;
  color: var(--kb-muted-foreground);
  border-color: var(--kb-border);
}

.sc-btn-ghost:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.sc-btn-primary {
  flex: 1 1 auto;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.sc-btn-primary:hover:not(:disabled) {
  opacity: 0.92;
}

/* 过渡 */
.sc-fade-enter-active,
.sc-fade-leave-active {
  transition: opacity 0.22s ease;
}

.sc-fade-enter-active .sc-dialog,
.sc-fade-leave-active .sc-dialog {
  transition: transform 0.26s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.22s ease;
}

.sc-fade-enter-from,
.sc-fade-leave-to {
  opacity: 0;
}

.sc-fade-enter-from .sc-dialog,
.sc-fade-leave-to .sc-dialog {
  transform: scale(0.92);
  opacity: 0;
}
</style>
