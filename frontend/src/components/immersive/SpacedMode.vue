<template>
  <div class="spaced-root">
    <section class="review-queue">
      <h3 class="section-title">
        <Icon name="list-ordered" :size="16" aria-hidden="true" />
        <span>今日复习队列</span>
      </h3>
      <div v-if="loading" class="queue-empty">加载中…</div>
      <div v-else-if="!queue.length" class="queue-empty">暂无待复习卡片，太棒了！</div>
      <ul v-else class="queue-list">
        <li
          v-for="(card, idx) in queue"
          :key="card.id"
          class="queue-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ active: idx === currentIndex, done: idx < currentIndex }"
          role="button"
          tabindex="0"
          @click="jumpToCard(idx)"
          @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
        >
          <div class="queue-card-name">{{ truncate(card.front ?? '未命名卡', 26) }}</div>
          <Badge :variant="masteryBadge(card).variant" class="queue-badge">
            {{ masteryBadge(card).label }}
          </Badge>
        </li>
      </ul>
      <div class="queue-actions">
        <button
          type="button"
          class="act-btn primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :disabled="!queue.length || currentIndex >= queue.length"
          @click="toggleTimer"
        >
          <Icon :name="timerRunning ? 'pause' : 'play'" :size="14" />
          <span>{{ timerRunning ? '暂停复习' : '开始复习' }}</span>
        </button>
      </div>
      <div class="queue-foot">
        <span>剩余 {{ Math.max(0, queue.length - currentIndex) }} 张</span>
        <span>已完成 {{ reviewedCount }} 张</span>
      </div>
      <div class="queue-timer">
        <Icon name="clock" :size="14" aria-hidden="true" />
        <span class="tabular-nums">{{ formatElapsed(reviewElapsedSec) }}</span>
      </div>
    </section>

    <section class="flashcard-area">
      <div class="card-stage" :style="{ perspective: '1400px' }">
        <div
          class="flashcard focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ flipped: isFlipped, loading: loadingCard }"
          role="button"
          tabindex="0"
          @click="handleCardClick"
          @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
        >
          <div class="card-face card-front">
            <div v-if="currentCard" class="card-q">
              {{ currentCard.front ?? '（无正面内容）' }}
            </div>
            <div v-else class="card-empty">
              <Icon name="check-circle-2" :size="48" aria-hidden="true" />
              <p>本轮复习已全部完成！</p>
            </div>
            <div v-if="currentCard" class="flip-hint">点击查看答案</div>
          </div>
          <div class="card-face card-back">
            <div v-if="currentCard" class="card-a">
              {{ currentCard.back ?? '（无背面内容）' }}
            </div>
            <div v-if="currentCard" class="rating-row">
              <button
                v-for="r in ratings"
                :key="r.quality"
                type="button"
                  class="rate-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                  :class="`rate-${r.quality}`"
                  @click.stop="submitRating(r.quality)"
              >
                <span class="rate-emoji">{{ r.emoji }}</span>
                <span class="rate-label">{{ r.label }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="spaced-stats-panel">
      <div class="stat-card big">
        <div class="stat-label">今日已复习</div>
        <div class="stat-value tabular-nums">{{ reviewedCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">完美率</div>
        <div class="stat-value tabular-nums">{{ perfectRatio }}%</div>
      </div>
      <div class="stat-card progress-card">
        <div class="stat-label-row">
          <span class="stat-label">复习进度</span>
          <span class="progress-pct tabular-nums">{{ progressPercent }}%</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }" />
        </div>
      </div>
      <div class="stat-card sm2-card">
        <div class="stat-label">SM-2 复习估算</div>
        <p class="sm2-hint">{{ sm2Hint }}</p>
      </div>
      <div class="next-ach-card">
        <div class="na-title">
          <Icon name="lock" :size="14" aria-hidden="true" />
          <span>完成本轮可解锁</span>
        </div>
        <div v-if="nextLockedAchievement" class="na-item">
          <div class="na-icon" :style="{ background: '#0EA5E9' }">
            <Icon :name="nextLockedAchievement.icon" :size="22" />
          </div>
          <div class="na-info">
            <div class="na-name">{{ nextLockedAchievement.name }}</div>
            <div class="na-desc">{{ nextLockedAchievement.desc }}</div>
          </div>
        </div>
        <div v-else class="na-all-done">
          <Icon name="trophy" :size="20" aria-hidden="true" />
          <span>本轮微成就已全部解锁！</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import Badge from '@/components/ui/Badge.vue';
import { learningApi, achievementApi, type FlashcardVO } from '@/api';
import { useFocusSession } from '@/composables/useFocusSession';
import { useMicroAchievements } from '@/composables/useMicroAchievements';
import { notify, getApiError } from '@/utils/toast';

const { isActive, start, end } = useFocusSession();
const { checkReview, allDefs } = useMicroAchievements();

const queue = ref<FlashcardVO[]>([]);
const loading = ref(false);
const loadingCard = ref(false);
const currentIndex = ref(0);
const isFlipped = ref(false);

const reviewedCount = ref(0);
const perfectCount = ref(0);
const lastQuality = ref<number | null>(null);

const reviewElapsedSec = ref(0);
const timerRunning = ref(false);
let tickTimer: number | null = null;

const ratings = [
  { quality: 1, emoji: '😅', label: '重来' },
  { quality: 3, emoji: '🤔', label: '模糊' },
  { quality: 4, emoji: '👍', label: '记得' },
  { quality: 5, emoji: '🎉', label: '完美' },
];

const currentCard = computed(() => {
  if (currentIndex.value < queue.value.length) {
    return queue.value[currentIndex.value];
  }
  return null;
});

const perfectRatio = computed(() => {
  if (reviewedCount.value === 0) return 0;
  return Math.round((perfectCount.value / reviewedCount.value) * 100);
});

const progressPercent = computed(() => {
  const total = queue.value.length;
  if (!total) return 0;
  return Math.min(100, Math.round((reviewedCount.value / total) * 100));
});

const sm2Hint = computed(() => {
  const q = lastQuality.value;
  if (q === null) return '选择卡片背面的评级按钮以继续复习。';
  const map: Record<number, string> = {
    1: `本次 quality 1：今天稍后会再次出现（已重置学习阶段）`,
    3: `本次 quality 3：预计 ${Math.max(1, Math.round(1.5))} 天后再见`,
    4: `本次 quality 4：预计 ${Math.max(3, Math.round(6 * 1.3))} 天后再见`,
    5: `本次 quality 5：预计 ${Math.max(6, Math.round(12 * 1.3))} 天后再见`,
  };
  return map[q] ?? '';
});

const nextLockedAchievement = computed(() => {
  const reviewDefs = allDefs.value.filter(
    (d) =>
      d.id === 'REVIEW_10' ||
      d.id === 'REVIEW_20' ||
      d.id === 'PERFECT_QUALITY' ||
      d.id === 'FIRST_FOCUS',
  );
  for (const d of reviewDefs) {
    if (!d.unlockedAt) return d;
  }
  return null;
});

function truncate(s: string, n: number) {
  if (!s) return '';
  s = s.replace(/\s+/g, ' ').trim();
  return s.length > n ? s.slice(0, n) + '…' : s;
}

function masteryBadge(card: FlashcardVO): { variant: 'default' | 'primary' | 'success' | 'warning'; label: string } {
  const rc = card.reviewCount ?? 0;
  const interval = card.reviewInterval ?? 0;
  if (rc === 0) return { variant: 'default', label: '新卡' };
  if (interval < 1) return { variant: 'warning', label: '学习' };
  if (interval < 7) return { variant: 'primary', label: '复习' };
  return { variant: 'success', label: '已掌握' };
}

function formatElapsed(sec: number) {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s
      .toString()
      .padStart(2, '0')}`;
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

async function loadQueue() {
  loading.value = true;
  try {
    if (typeof (learningApi as unknown as { dueFlashcards?: () => Promise<FlashcardVO[]> }).dueFlashcards === 'function') {
      const due = await (learningApi as unknown as { dueFlashcards: () => Promise<FlashcardVO[]> }).dueFlashcards();
      if (due && Array.isArray(due) && due.length > 0) {
        queue.value = due.slice(0, 100);
        return;
      }
    }
    const all = await learningApi.flashcards();
    const now = Date.now();
    queue.value = all
      .filter((c) => {
        if (!c.nextReviewTime) return true;
        return new Date(c.nextReviewTime).getTime() <= now;
      })
      .slice(0, 100);
  } catch (e: unknown) {
    notify(getApiError(e, '加载复习队列失败'), 'warning');
    queue.value = [];
  } finally {
    loading.value = false;
  }
}

function toggleTimer() {
  if (currentIndex.value >= queue.value.length) return;
  timerRunning.value = !timerRunning.value;
  if (timerRunning.value && !isActive()) {
    start('SPACED').catch(() => {
      /* useFocusSession already notified */
    });
  }
}

function jumpToCard(idx: number) {
  if (idx < 0 || idx >= queue.value.length) return;
  currentIndex.value = idx;
  isFlipped.value = false;
}

function handleCardClick() {
  if (!currentCard.value) return;
  isFlipped.value = !isFlipped.value;
}

async function submitRating(quality: number) {
  const card = currentCard.value;
  if (!card) return;
  loadingCard.value = true;
  try {
    await learningApi.reviewFlashcard(card.id, quality);
    reviewedCount.value += 1;
    lastQuality.value = quality;
    if (quality === 5) perfectCount.value += 1;
    checkReview(reviewedCount.value, perfectCount.value);
    if (reviewedCount.value > 0 && reviewedCount.value % 10 === 0) {
      achievementApi.myAchievements().catch(() => undefined);
    }
    currentIndex.value += 1;
    await nextTick();
    isFlipped.value = false;
    if (currentIndex.value >= queue.value.length) {
      notify('本轮复习已完成！', 'success');
      timerRunning.value = false;
    }
  } catch (e: unknown) {
    notify(getApiError(e, '提交复习失败'), 'error');
  } finally {
    loadingCard.value = false;
  }
}

watch(currentIndex, () => {
  isFlipped.value = false;
});

onMounted(() => {
  void loadQueue();
  tickTimer = window.setInterval(() => {
    if (timerRunning.value) {
      reviewElapsedSec.value += 1;
    }
  }, 1000);
});

onUnmounted(async () => {
  if (tickTimer) {
    clearInterval(tickTimer);
    tickTimer = null;
  }
  if (isActive()) {
    try {
      await end({ completedPomodoros: reviewedCount.value });
    } catch {
      /* useFocusSession already notified */
    }
  }
});
</script>

<style scoped>
.spaced-root {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 24px;
  padding: 88px 24px 100px;
  min-height: 0;
}

.review-queue,
.spaced-stats-panel {
  width: 260px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 18px;
  backdrop-filter: blur(8px);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 14px;
}

.queue-empty {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  padding: 16px 4px;
  text-align: center;
}

.queue-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 380px;
  overflow-y: auto;
  padding-right: 4px;
}
.queue-list::-webkit-scrollbar {
  width: 4px;
}
.queue-list::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 999px;
}

.queue-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
  border: 1px solid transparent;
}
.queue-item:hover {
  background: rgba(255, 255, 255, 0.07);
}
.queue-item.active {
  background: color-mix(in srgb, var(--kb-primary) 18%, transparent);
  border-color: color-mix(in srgb, var(--kb-primary) 35%, rgba(255, 255, 255, 0.06));
}
.queue-item.done {
  opacity: 0.5;
}
.queue-card-name {
  font-size: 13px;
  color: var(--kb-foreground);
  line-height: 1.4;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.queue-badge {
  flex-shrink: 0;
}

.queue-actions {
  margin-top: 14px;
  display: flex;
}
.act-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.act-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
.act-btn.primary {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.act-btn.primary:hover {
  filter: brightness(1.05);
}
.act-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.queue-foot {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.queue-timer {
  margin-top: 8px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  width: 100%;
  justify-content: center;
}

.flashcard-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 520px;
}

.card-stage {
  width: 100%;
  max-width: 520px;
  height: 420px;
}

.flashcard {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transition: transform 0.65s cubic-bezier(0.16, 1, 0.3, 1);
  cursor: pointer;
}
.flashcard.flipped {
  transform: rotateY(180deg);
}
.flashcard.loading {
  pointer-events: none;
  opacity: 0.7;
}

.card-face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  background: linear-gradient(
    160deg,
    rgba(255, 255, 255, 0.08) 0%,
    rgba(255, 255, 255, 0.03) 100%
  );
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 22px;
  padding: 32px;
  display: flex;
  flex-direction: column;
  box-shadow:
    0 24px 64px rgba(0, 0, 0, 0.32),
    0 0 0 1px rgba(255, 255, 255, 0.03) inset;
}
.card-back {
  transform: rotateY(180deg);
}

.card-q,
.card-a {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 20px;
  line-height: 1.7;
  color: var(--kb-foreground);
  padding: 16px 8px;
  overflow: hidden;
  word-break: break-word;
}
.card-a {
  font-size: 17px;
  text-align: left;
  align-items: flex-start;
  justify-content: flex-start;
}

.flip-hint {
  text-align: center;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  padding: 8px 0 2px;
}

.card-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--kb-muted-foreground);
}
.card-empty p {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.rating-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  margin-top: 12px;
}
.rate-btn {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.rate-btn:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.08);
}
.rate-emoji {
  font-size: 22px;
  line-height: 1;
}
.rate-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
}
.rate-1:hover {
  border-color: rgba(239, 68, 68, 0.4);
}
.rate-3:hover {
  border-color: rgba(245, 158, 11, 0.4);
}
.rate-4:hover {
  border-color: rgba(59, 130, 246, 0.4);
}
.rate-5:hover {
  border-color: rgba(16, 185, 129, 0.4);
}

.spaced-stats-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 12px 14px;
}
.stat-card.big .stat-value {
  font-size: 36px;
}
.stat-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.2;
}

.stat-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.progress-pct {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.progress-bar {
  height: 8px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--kb-primary), #0EA5E9);
  border-radius: 999px;
  transition: width 0.5s ease;
}

.sm2-card .sm2-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
}

.next-ach-card {
  margin-top: auto;
  background: linear-gradient(
    135deg,
    rgba(139, 92, 246, 0.1),
    rgba(14, 165, 233, 0.08)
  );
  border: 1px solid rgba(139, 92, 246, 0.2);
  border-radius: 12px;
  padding: 14px;
}
.na-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-primary);
  margin-bottom: 12px;
}
.na-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.na-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  opacity: 0.55;
}
.na-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.na-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--kb-foreground);
}
.na-desc {
  font-size: 11px;
  line-height: 1.4;
  color: var(--kb-muted-foreground);
}
.na-all-done {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #10B981;
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1024px) {
  .spaced-root {
    flex-direction: column;
    align-items: center;
    padding: 88px 16px 100px;
  }
  .review-queue,
  .spaced-stats-panel {
    width: 100%;
    max-width: 520px;
  }
  .flashcard-area {
    min-height: 440px;
  }
}
</style>
