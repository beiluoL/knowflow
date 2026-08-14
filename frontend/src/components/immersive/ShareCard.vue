<template>
  <Teleport to="body">
    <transition name="sc-fade">
      <div
        v-if="visible"
        class="sharecard-overlay"
        @click.self="handleClose"
      >
        <div class="sharecard-panel">
          <button type="button" class="close-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="handleClose" aria-label="关闭">
            <Icon name="x" :size="18" aria-hidden="true" />
          </button>

          <h2 class="panel-title">
            <Icon name="share-2" :size="18" aria-hidden="true" />
            生成分享卡
          </h2>

          <div class="card-preview" ref="cardRef">
            <div
              class="share-card"
              :style="{
                background: `linear-gradient(160deg, ${data.themeColors.bg1} 0%, ${data.themeColors.bg2} 100%)`,
                color: '#ffffff',
              }"
            >
              <header class="card-header">
                <div class="brand">
                  <span class="brand-name">KnowFlow · 沉浸工作台</span>
                </div>
                <span class="date-str">{{ data.dateStr }}</span>
              </header>

              <section class="user-section">
                <div class="user-avatar">
                  <span>{{ initialOf(data.userName) }}</span>
                </div>
                <div class="user-info">
                  <div class="user-name">{{ data.userName || '知流学习者' }}</div>
                  <div v-if="data.streak" class="streak-badge">
                    <Icon name="flame" :size="12" aria-hidden="true" />
                    连续 {{ data.streak }} 天
                  </div>
                </div>
              </section>

              <section class="hero-section">
                <div class="hero-numbers tabular-nums">
                  专注 {{ data.minutes }} 分钟
                </div>
                <div class="hero-sub">
                  完成 <strong>{{ data.pomodoros }}</strong> 个番茄
                  <span class="mode-badge" :style="{ background: data.themeColors.primary }">
                    {{ data.modeLabel }}
                  </span>
                </div>
              </section>

              <section class="stats-row">
                <div class="stat-box">
                  <div class="stat-label">今日排名</div>
                  <div class="stat-val tabular-nums">#{{ data.dailyRank ?? '—' }}</div>
                </div>
                <div class="stat-box">
                  <div class="stat-label">微成就</div>
                  <div class="stat-val tabular-nums">{{ data.microAchievementsUnlocked ?? 0 }}</div>
                </div>
                <div class="stat-box">
                  <div class="stat-label">连续天数</div>
                  <div class="stat-val tabular-nums">{{ data.streak ?? 0 }}天</div>
                </div>
              </section>

              <section v-if="data.achievements?.length" class="achievements-row">
                <div
                  v-for="(a, idx) in data.achievements.slice(0, 6)"
                  :key="idx"
                  class="ach-icon"
                  :title="a.name"
                >
                  <Icon :name="a.icon" :size="18" />
                </div>
              </section>

              <footer class="card-footer">
                <span class="watermark">knowflow.cn</span>
              </footer>
            </div>
          </div>

          <div class="actions">
            <button type="button" class="action-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="handleDownload">
              <Icon name="download" :size="16" aria-hidden="true" />
              下载图片
            </button>
            <button type="button" class="action-btn primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="handleCopyText">
              <Icon name="copy" :size="16" aria-hidden="true" />
              复制文案
            </button>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { notify, getApiError } from '@/utils/toast';

interface AchievementItem {
  name: string;
  icon: string;
}

interface ShareCardData {
  mode: string;
  modeLabel: string;
  minutes: number;
  pomodoros: number;
  achievements?: AchievementItem[];
  themeColors: {
    primary: string;
    bg1: string;
    bg2: string;
  };
  userName?: string;
  avatar?: string;
  dateStr: string;
  dailyRank?: number;
  streak?: number;
  microAchievementsUnlocked?: number;
}

interface Props {
  visible: boolean;
  data: ShareCardData;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  'close': [];
}>();

const cardRef = ref<HTMLElement | null>(null);

const initialOf = (name?: string) => {
  if (!name) return 'K';
  const ch = name.trim().charAt(0);
  return ch ? ch.toUpperCase() : 'K';
};

const handleClose = () => {
  emit('update:visible', false);
  emit('close');
};

const buildCopyText = () => {
  const { modeLabel, minutes, pomodoros, dailyRank, streak } = props.data;
  let text = `我在 KnowFlow 专注了 ${minutes} 分钟，完成 ${pomodoros} 个番茄 🍅！`;
  if (modeLabel) text += `（${modeLabel}模式）`;
  if (dailyRank) text += ` 今日排名 #${dailyRank}，`;
  if (streak) text += `连续学习 ${streak} 天，`;
  text += '快来一起学习～';
  return text;
};

const handleCopyText = async () => {
  const text = buildCopyText();
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    }
    notify('分享文案已复制到剪贴板', 'success');
  } catch (e: unknown) {
    notify(getApiError(e, '复制失败，请手动复制'), 'warning');
  }
};

const escapeXml = (s: string) =>
  s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');

const buildSvgCard = () => {
  const d = props.data;
  const primary = d.themeColors.primary;
  const bg1 = d.themeColors.bg1;
  const bg2 = d.themeColors.bg2;
  const w = 375;
  const h = 520;
  const userName = d.userName || '知流学习者';
  const achIcons = (d.achievements || []).slice(0, 6);

  const achSprites = achIcons
    .map((_, i) => {
      const x = 40 + i * 50;
      const y = 400;
      return `<circle cx="${x + 20}" cy="${y + 20}" r="18" fill="rgba(255,255,255,0.12)" />
<text x="${x + 20}" y="${y + 25}" text-anchor="middle" font-size="18" fill="#ffffff" font-family="inherit">★</text>`;
    })
    .join('\n');

  const htmlContent = `
<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="${escapeXml(bg1)}" />
      <stop offset="100%" stop-color="${escapeXml(bg2)}" />
    </linearGradient>
    <filter id="soft" x="-10%" y="-10%" width="120%" height="120%">
      <feGaussianBlur stdDeviation="2" />
    </filter>
  </defs>
  <rect width="${w}" height="${h}" rx="20" fill="url(#bg)" />

  <text x="28" y="44" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="13" font-weight="600" fill="rgba(255,255,255,0.85)">KnowFlow · 沉浸工作台</text>
  <text x="${w - 28}" y="44" text-anchor="end" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="12" fill="rgba(255,255,255,0.6)">${escapeXml(d.dateStr)}</text>

  <circle cx="58" cy="110" r="28" fill="rgba(255,255,255,0.18)" />
  <text x="58" y="118" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="22" font-weight="700" fill="#ffffff">${escapeXml(initialOf(userName))}</text>
  <text x="100" y="106" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="18" font-weight="700" fill="#ffffff">${escapeXml(userName)}</text>
  ${d.streak ? `<rect x="100" y="116" rx="10" ry="10" width="96" height="22" fill="rgba(245,158,11,0.25)" />
<text x="110" y="131" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="12" font-weight="600" fill="#fbbf24">🔥 连续 ${d.streak} 天</text>` : ''}

  <text x="${w / 2}" y="220" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="40" font-weight="700" fill="#ffffff">专注 ${d.minutes} 分钟</text>
  <text x="${w / 2}" y="256" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="16" fill="rgba(255,255,255,0.85)">完成 <tspan font-weight="700">${d.pomodoros}</tspan> 个番茄</text>
  <rect x="${w / 2 + 52}" y="240" rx="10" ry="10" width="${8 + d.modeLabel.length * 14}" height="22" fill="${escapeXml(primary)}" />
  <text x="${w / 2 + 56}" y="255" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="12" font-weight="600" fill="#ffffff">${escapeXml(d.modeLabel)}</text>

  <rect x="28" y="304" width="${w - 56}" height="74" rx="14" fill="rgba(255,255,255,0.08)" />
  <text x="76" y="328" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="11" fill="rgba(255,255,255,0.65)">今日排名</text>
  <text x="76" y="360" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="20" font-weight="700" fill="#ffffff">#${d.dailyRank ?? '—'}</text>
  <line x1="124" y1="314" x2="124" y2="368" stroke="rgba(255,255,255,0.12)" />
  <text x="${w / 2}" y="328" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="11" fill="rgba(255,255,255,0.65)">微成就</text>
  <text x="${w / 2}" y="360" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="20" font-weight="700" fill="#ffffff">${d.microAchievementsUnlocked ?? 0}</text>
  <line x1="${w - 124}" y1="314" x2="${w - 124}" y2="368" stroke="rgba(255,255,255,0.12)" />
  <text x="${w - 76}" y="328" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="11" fill="rgba(255,255,255,0.65)">连续天数</text>
  <text x="${w - 76}" y="360" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="20" font-weight="700" fill="#ffffff">${d.streak ?? 0}天</text>

  ${achSprites}

  <text x="${w / 2}" y="${h - 28}" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" font-size="12" font-weight="600" fill="rgba(255,255,255,0.45)" letter-spacing="2">knowflow.cn</text>
</svg>`;
  return htmlContent.trim();
};

const handleDownload = async () => {
  await nextTick();
  try {
    const svgMarkup = buildSvgCard();
    const svgBlob = new Blob([svgMarkup], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(svgBlob);

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = 750;
        canvas.height = 1040;
        const ctx = canvas.getContext('2d');
        if (!ctx) throw new Error('canvas ctx unavailable');
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        canvas.toBlob((blob) => {
          if (!blob) {
            fallbackSvgDownload(svgMarkup);
            return;
          }
          const dlUrl = URL.createObjectURL(blob);
          triggerDownload(dlUrl, 'share-card.png');
          setTimeout(() => URL.revokeObjectURL(dlUrl), 2000);
          notify('分享卡已导出为图片', 'success');
        }, 'image/png');
      } catch (e: unknown) {
        fallbackSvgDownload(svgMarkup);
      } finally {
        URL.revokeObjectURL(url);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      fallbackSvgDownload(svgMarkup);
    };
    img.src = url;
  } catch (e: unknown) {
    notify(getApiError(e, '导出失败，尝试截图保存分享'), 'warning');
  }
};

const fallbackSvgDownload = (svgMarkup: string) => {
  try {
    const encoded = encodeURIComponent(svgMarkup)
      .replace(/'/g, '%27')
      .replace(/"/g, '%22');
    const dataUrl = `data:image/svg+xml;charset=utf-8,${encoded}`;

    const w = window.open('', '_blank');
    if (w) {
      w.document.write(
        `<!doctype html><html><head><title>分享卡 - 请右键另存为图片</title></head>
<body style="margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;background:#1e293b;">
<p style="position:fixed;top:12px;left:0;right:0;text-align:center;font-family:sans-serif;font-size:13px;color:#cbd5e1;">请右键图片 → 「图片另存为…」保存分享卡</p>
<img src="${dataUrl}" alt="KnowFlow分享卡" style="max-width:90vw;max-height:85vh;border-radius:20px;box-shadow:0 24px 60px rgba(0,0,0,0.5);" />
</body></html>`,
      );
      notify('已打开预览窗口，请右键另存为图片', 'info');
      return;
    }
    triggerDownload(dataUrl, 'share-card.svg');
    notify('已下载 SVG 格式分享卡', 'success');
  } catch (e: unknown) {
    notify(getApiError(e, '导出失败，请手动截图保存分享卡'), 'warning');
  }
};

const triggerDownload = (href: string, filename: string) => {
  const a = document.createElement('a');
  a.href = href;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
};

watch(
  () => props.visible,
  (v) => {
    if (v) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
  },
);
</script>

<style scoped>
.sharecard-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.sharecard-panel {
  position: relative;
  width: 100%;
  max-width: 440px;
  background: rgba(15, 23, 42, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 20px;
  padding: 20px 20px 18px;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.45);
}

.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--kb-muted-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.close-btn:hover {
  background: rgba(255, 255, 255, 0.09);
  color: var(--kb-foreground);
  border-color: rgba(255, 255, 255, 0.08);
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  color: var(--kb-foreground);
}

.card-preview {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}

.share-card {
  position: relative;
  width: 375px;
  max-width: 100%;
  aspect-ratio: 375 / 520;
  border-radius: 20px;
  padding: 28px 28px 24px;
  box-sizing: border-box;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26px;
}
.brand-name {
  font-size: 13px;
  font-weight: 600;
  opacity: 0.9;
}
.date-str {
  font-size: 12px;
  opacity: 0.7;
}

.user-section {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 36px;
}
.user-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.user-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.user-name {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}
.streak-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #fbbf24;
  background: rgba(245, 158, 11, 0.22);
  border-radius: 999px;
  width: fit-content;
}

.hero-section {
  text-align: center;
  margin-bottom: 28px;
}
.hero-numbers {
  font-size: 40px;
  font-weight: 700;
  color: #fff;
  line-height: 1.1;
  margin-bottom: 10px;
}
.hero-sub {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.9);
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.hero-sub strong {
  font-weight: 700;
  font-size: 18px;
}
.mode-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.stats-row {
  display: flex;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 14px 0;
  margin-bottom: 20px;
}
.stat-box {
  flex: 1;
  text-align: center;
  position: relative;
}
.stat-box + .stat-box::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10%;
  bottom: 10%;
  width: 1px;
  background: rgba(255, 255, 255, 0.12);
}
.stat-label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.65);
  margin-bottom: 6px;
}
.stat-val {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}

.achievements-row {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-bottom: auto;
}
.ach-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.card-footer {
  text-align: center;
  margin-top: 18px;
}
.watermark {
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.45);
  letter-spacing: 0.18em;
}

.actions {
  display: flex;
  gap: 10px;
}
.action-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 42px;
  padding: 0 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s ease, filter 0.15s ease;
}
.action-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
.action-btn.primary {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground, #fff);
}
.action-btn.primary:hover {
  filter: brightness(1.06);
  background: var(--kb-primary);
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}

.sc-fade-enter-active,
.sc-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.sc-fade-enter-active .sharecard-panel,
.sc-fade-leave-active .sharecard-panel {
  transition: transform 0.22s cubic-bezier(0.2, 0.9, 0.3, 1.2), opacity 0.18s ease;
}
.sc-fade-enter-from,
.sc-fade-leave-to {
  opacity: 0;
}
.sc-fade-enter-from .sharecard-panel,
.sc-fade-leave-to .sharecard-panel {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}

@media (max-width: 520px) {
  .share-card {
    width: 100%;
    padding: 22px 20px;
  }
  .hero-numbers {
    font-size: 34px;
  }
}
</style>
