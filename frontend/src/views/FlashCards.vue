<template>
  <!-- 学习闪卡页：顶部标题栏 + 进度块 + 翻转卡 + 5 操作按钮 + 底部 4 列统计 -->
  <div class="flashcards-page animate-fade-in">
    <!-- ===== 顶部标题栏：返回 + 标题 + 分类下拉 + 随机排序 ===== -->
    <div class="page-header">
      <div class="title-group">
        <button
          type="button"
          class="back-btn"
          title="返回"
          @click="goBack"
        >
          <Icon name="arrow-left" :size="16" />
        </button>
        <div class="title-text">
          <h1 class="kb-h1">学习闪卡</h1>
          <p class="kb-body-sm">通过闪卡快速记忆知识点</p>
        </div>
      </div>
      <div class="action-group">
        <!-- 分类下拉 -->
        <select
          v-model="selectedCategory"
          class="category-select"
        >
          <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
        </select>
        <!-- 随机排序 -->
        <button
          type="button"
          class="btn-primary"
          @click="shuffleCards"
        >
          <Icon name="shuffle" :size="16" />
          <span>随机排序</span>
        </button>
      </div>
    </div>

    <!-- ===== 进度块：进度条 + 已记住/需复习/待学习 ===== -->
    <div v-if="!loading && filteredCards.length > 0" class="progress-block">
      <div class="progress-left">
        <div class="progress-meta">
          <span class="progress-label">学习进度</span>
          <span class="progress-value tabular-nums">{{ currentIndex + 1 }} / {{ filteredCards.length }}</span>
        </div>
        <div class="progress-track">
          <div
            class="progress-fill"
            :style="{ width: `${reviewProgress}%` }"
          ></div>
        </div>
      </div>
      <div class="progress-right">
        <div class="stat-cell">
          <div class="stat-num stat-known tabular-nums">{{ knownCount }}</div>
          <div class="kb-caption">已记住</div>
        </div>
        <div class="stat-cell">
          <div class="stat-num stat-review tabular-nums">{{ reviewCount }}</div>
          <div class="kb-caption">需复习</div>
        </div>
        <div class="stat-cell">
          <div class="stat-num stat-new tabular-nums">{{ newCount }}</div>
          <div class="kb-caption">待学习</div>
        </div>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text">加载中…</p>
    </div>

    <!-- 空态 -->
    <div v-else-if="filteredCards.length === 0" class="state-area">
      <Icon name="layers" :size="48" class="state-icon" />
      <p class="state-text">暂无闪卡</p>
    </div>

    <!-- ===== 闪卡翻转展示区 ===== -->
    <div v-else class="flip-area">
      <div
        class="flip-card"
        :class="{ flipped: isFlipped }"
        @click="flipCard"
      >
        <div class="flip-card-inner">
          <!-- 正面 - 问题 -->
          <div class="flip-card-face flip-card-front">
            <div class="face-tags">
              <span class="face-tag tag-category">{{ currentCard?.category }}</span>
              <span class="face-tag tag-sub">{{ getDifficultyLabel(currentCard?.difficulty) }}</span>
            </div>
            <h2 class="face-question">{{ currentCard?.question }}</h2>
            <p class="face-hint">请点击卡片查看答案</p>
            <div class="flip-pill">
              <Icon name="rotate-ccw" :size="16" />
              <span>点击翻转</span>
            </div>
          </div>
          <!-- 背面 - 答案 -->
          <div class="flip-card-face flip-card-back">
            <div class="back-header">
              <Icon name="lightbulb" :size="18" class="back-icon" />
              <span class="back-label">答案</span>
            </div>
            <div class="back-body">
              <h3 class="back-title">{{ currentCard?.question }}</h3>
              <p class="back-text">{{ currentCard?.answer }}</p>
              <div v-if="currentCard?.category" class="back-tags">
                <span class="back-tag">{{ currentCard.category }}</span>
                <span class="back-tag">{{ getDifficultyLabel(currentCard.difficulty) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 5 个操作按钮 ===== -->
    <div v-if="!loading && filteredCards.length > 0" class="action-bar">
      <button
        type="button"
        class="action-btn btn-outline"
        :disabled="currentIndex === 0"
        @click="prevCard"
      >
        <Icon name="skip-back" :size="16" />
        <span>上一张</span>
      </button>
      <button
        type="button"
        class="action-btn btn-danger"
        :disabled="reviewing"
        @click="rateCard('unknown')"
      >
        <Icon name="x" :size="16" />
        <span>不熟悉</span>
      </button>
      <button
        type="button"
        class="action-btn btn-warning"
        :disabled="reviewing"
        @click="rateCard('familiar')"
      >
        <Icon name="rotate-ccw" :size="16" />
        <span>模糊</span>
      </button>
      <button
        type="button"
        class="action-btn btn-success"
        :disabled="reviewing"
        @click="rateCard('mastered')"
      >
        <Icon name="check" :size="16" />
        <span>记住了</span>
      </button>
      <button
        type="button"
        class="action-btn btn-outline"
        :disabled="currentIndex >= filteredCards.length - 1"
        @click="nextCard"
      >
        <span>下一张</span>
        <Icon name="skip-forward" :size="16" />
      </button>
    </div>

    <!-- 反馈提示 -->
    <transition name="fade">
      <p v-if="feedback" class="feedback-text">{{ feedback }}</p>
    </transition>

    <!-- ===== 底部 4 列统计 ===== -->
    <div v-if="!loading && filteredCards.length > 0" class="stats-grid">
      <div class="stats-card">
        <div class="stats-icon-box stats-icon-primary">
          <Icon name="layers" :size="20" />
        </div>
        <div class="stats-value tabular-nums">{{ filteredCards.length }}</div>
        <div class="kb-body-sm">总卡片数</div>
      </div>
      <div class="stats-card">
        <div class="stats-icon-box stats-icon-accent">
          <Icon name="clock" :size="20" />
        </div>
        <div class="stats-value tabular-nums">{{ studyDuration }}</div>
        <div class="kb-body-sm">学习时长</div>
      </div>
      <div class="stats-card">
        <div class="stats-icon-box stats-icon-warning">
          <Icon name="flame" :size="20" />
        </div>
        <div class="stats-value tabular-nums">{{ accuracy }}%</div>
        <div class="kb-body-sm">正确率</div>
      </div>
      <div class="stats-card">
        <div class="stats-icon-box stats-icon-danger">
          <Icon name="repeat" :size="20" />
        </div>
        <div class="stats-value tabular-nums">{{ reviewCount }}</div>
        <div class="kb-body-sm">需复习</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 学习闪卡页：分类筛选 + 翻面查看 + 三档评分（不熟悉/模糊/记住了）。
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { learningApi } from '@/api'
import { mistakesApi } from '@/api/mistakes'
import type { FlashcardVO } from '@/api/types'
import { markReviewed, dateStr } from '@/utils/studySession'

interface FlashCard {
  id: number
  category: string
  difficulty: number
  question: string
  answer: string
}

type Rating = 'unknown' | 'familiar' | 'mastered'

const router = useRouter()

const loading = ref(false)
const reviewing = ref(false)
const feedback = ref('')
let feedbackTimer: ReturnType<typeof setTimeout> | undefined

const categories = ref<string[]>(['全部'])

const selectedCategory = ref('全部')
const currentIndex = ref(0)
const isFlipped = ref(false)
const cards = ref<FlashCard[]>([])

const todayCount = ref(0)
const correctCount = ref(0)
// 已记住 / 需复习 / 待学习 计数
const knownCount = ref(0)
const reviewCount = ref(0)
const newCount = ref(0)
// 学习时长（秒）
const studySeconds = ref(0)
let studyTimer: ReturnType<typeof setInterval> | undefined

// 将后端 FlashcardVO 映射为本地展示用的闪卡结构（缺省字段兜底）
const mapCard = (f: FlashcardVO): FlashCard => ({
  id: f.id,
  category: f.category || '通用',
  difficulty: f.difficulty || 1,
  question: f.front || '',
  answer: f.back || '',
})

// 根据所选分类过滤当前牌组
const filteredCards = computed(() => {
  return cards.value.filter((card) => {
    return selectedCategory.value === '全部' || card.category === selectedCategory.value
  })
})

const currentCard = computed(() => filteredCards.value[currentIndex.value] || null)

// 复习进度：以当前卡片序号在牌组中的位置计算百分比
const reviewProgress = computed(() => {
  if (filteredCards.value.length === 0) return 0
  return Math.round(((currentIndex.value + 1) / filteredCards.value.length) * 100)
})

const accuracy = computed(() => {
  if (todayCount.value === 0) return 0
  return Math.round((correctCount.value / todayCount.value) * 100)
})

// 学习时长格式化：秒 → "MM:SS"
const studyDuration = computed(() => {
  const mins = Math.floor(studySeconds.value / 60)
  const secs = studySeconds.value % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
})

// 待学习 = 总数 - 已记住 - 需复习
const newCountComputed = computed(() => {
  return Math.max(0, filteredCards.value.length - knownCount.value - reviewCount.value)
})

// 用 computed 替代 ref，保证待学习数实时同步
watch(newCountComputed, (v) => {
  newCount.value = v
})

watch(selectedCategory, () => {
  currentIndex.value = 0
  isFlipped.value = false
})

const getDifficultyLabel = (difficulty?: number) => {
  if (difficulty === 2) return '困难'
  if (difficulty === 3) return '高级'
  return '基础'
}

const flipCard = () => {
  isFlipped.value = !isFlipped.value
}

const prevCard = () => {
  if (currentIndex.value > 0) {
    isFlipped.value = false
    currentIndex.value--
  }
}

const nextCard = () => {
  if (currentIndex.value < filteredCards.value.length - 1) {
    isFlipped.value = false
    currentIndex.value++
  }
}

// 随机打乱当前牌组顺序
const shuffleCards = () => {
  const arr = cards.value.slice()
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
  }
  cards.value = arr
  currentIndex.value = 0
  isFlipped.value = false
}

// 返回上一页
const goBack = () => {
  router.back()
}

const ratingToQuality = (rating: Rating): number => {
  // 与后端 SM-2 reviewFlashcard 对齐：<3 重置，3=有印象，5=完全掌握
  if (rating === 'unknown') return 1
  if (rating === 'familiar') return 3
  return 5
}

const showFeedback = (msg: string) => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedback.value = msg
  feedbackTimer = setTimeout(() => {
    feedback.value = ''
  }, 1600)
}

// 提交评分：调用后端复习接口、本地标记已复习并更新计数，随后推进到下一张
const rateCard = async (rating: Rating) => {
  if (reviewing.value || !currentCard.value) return
  reviewing.value = true
  try {
    await learningApi.reviewFlashcard(currentCard.value.id, ratingToQuality(rating))
    markReviewed(dateStr(new Date()), String(currentCard.value.id))
    todayCount.value++
    // 更新统计计数
    if (rating === 'mastered') {
      knownCount.value++
      correctCount.value++
    } else if (rating === 'familiar') {
      reviewCount.value++
      correctCount.value++
    } else {
      reviewCount.value++
    }
    // 未完全掌握（不熟悉 / 有印象）归集到错题本，便于后续针对性复习；后端幂等避免重复
    if (rating !== 'mastered' && currentCard.value.question) {
      mistakesApi
        .add({
          question: currentCard.value.question,
          wrongAnswer: '未完全掌握该知识点',
          correctAnswer: currentCard.value.answer,
          category: currentCard.value.category,
          difficulty: currentCard.value.difficulty,
          source: 'flashcard',
        })
        .catch(() => {
          /* 归集失败不阻断复习 */
        })
    }
    if (rating === 'unknown') {
      showFeedback('已记录 · 明天再复习这张')
    } else if (rating === 'familiar') {
      showFeedback('已记录 · 继续保持')
    } else {
      showFeedback('已记录 · 掌握得不错')
    }
    if (currentIndex.value < filteredCards.value.length - 1) {
      isFlipped.value = false
      currentIndex.value++
    }
  } catch {
    // 网络异常时仍允许继续翻牌，避免卡死（本地计数已记）
    if (currentIndex.value < filteredCards.value.length - 1) {
      isFlipped.value = false
      currentIndex.value++
    }
  } finally {
    reviewing.value = false
  }
}

onUnmounted(() => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
  if (studyTimer) clearInterval(studyTimer)
})

onMounted(async () => {
  loading.value = true
  try {
    const list = await learningApi.flashcards()
    cards.value = list.map(mapCard)
    const uniqueCats = Array.from(new Set(list.map((f) => f.category || '通用')))
    categories.value = ['全部', ...uniqueCats]
    // 初始化待学习数
    newCount.value = filteredCards.value.length
    // 启动学习计时器
    studyTimer = setInterval(() => {
      studySeconds.value++
    }, 1000)
  } catch {
    cards.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ===== 页面容器 ===== */
.flashcards-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 顶部标题栏 ===== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.back-btn:hover {
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}
.back-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.title-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.title-text .kb-h1 {
  margin: 0;
}
.action-group {
  display: flex;
  align-items: center;
  gap: 12px;
}
.category-select {
  height: 36px;
  padding: 0 12px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 14px;
  outline: none;
  cursor: pointer;
}
.category-select:focus {
  border-color: var(--kb-primary);
}
.category-select:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border-radius: var(--kb-radius-md);
  border: none;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-primary:hover {
  opacity: 0.9;
}

/* ===== 进度块 ===== */
.progress-block {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 20px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
}
.progress-left {
  flex: 1;
  min-width: 0;
}
.progress-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.progress-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.progress-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-primary);
}
.progress-track {
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--kb-muted);
}
.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--kb-primary);
  transition: width 0.3s ease;
}
.progress-right {
  display: flex;
  align-items: center;
  gap: 24px;
}
.stat-cell {
  text-align: center;
}
.stat-num {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-known { color: var(--kb-accent); }
.stat-review { color: var(--kb-warning); }
.stat-new { color: var(--kb-destructive); }

/* ===== 状态区（加载/空） ===== */
.state-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 0;
  color: var(--kb-muted-foreground);
}
.state-icon {
  color: var(--kb-muted-foreground);
  opacity: 0.5;
}
.state-text {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.loading-spinner {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 3px solid var(--kb-muted);
  border-top-color: var(--kb-primary);
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 翻转卡 ===== */
.flip-area {
  display: flex;
  justify-content: center;
}
.flip-card {
  width: 100%;
  max-width: 900px;
  height: 360px;
  perspective: 1000px;
  cursor: pointer;
}
.flip-card-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  transform-style: preserve-3d;
}
.flip-card.flipped .flip-card-inner {
  transform: rotateY(180deg);
}
.flip-card-face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  border-radius: var(--kb-radius-lg);
  border: 2px solid var(--kb-border);
  background: var(--kb-card);
  display: flex;
  flex-direction: column;
  padding: 32px;
  overflow: hidden;
}
.flip-card-front {
  align-items: center;
  justify-content: center;
}
.flip-card-back {
  transform: rotateY(180deg);
  border-color: var(--kb-primary);
  justify-content: flex-start;
}
.face-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}
.face-tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
}
.tag-category {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.tag-sub {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-accent);
}
.face-question {
  font-size: 24px;
  font-weight: 700;
  text-align: center;
  color: var(--kb-foreground);
  margin: 0 0 16px 0;
  line-height: 1.4;
  word-break: keep-all;
  overflow-wrap: break-word;
}
.face-hint {
  text-align: center;
  font-size: 14px;
  color: var(--kb-muted-foreground);
  margin: 0 0 32px 0;
}
.flip-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 999px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: 14px;
}
/* 背面 */
.back-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.back-icon {
  color: var(--kb-warning);
}
.back-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-primary);
}
.back-body {
  flex: 1;
  overflow: auto;
}
.back-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 12px 0;
}
.back-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--kb-foreground);
  margin: 0 0 16px 0;
  white-space: pre-line;
}
.back-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.back-tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 13px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

/* ===== 操作按钮 ===== */
.action-bar {
  display: flex;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 24px;
  border-radius: var(--kb-radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
  border: none;
}
.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.action-btn:not(:disabled):focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.btn-outline {
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
}
.btn-outline:not(:disabled):hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.btn-danger {
  background: var(--kb-destructive);
  color: #fff;
}
.btn-warning {
  background: var(--kb-warning);
  color: #fff;
}
.btn-success {
  background: var(--kb-accent);
  color: #fff;
}

/* ===== 反馈提示 ===== */
.feedback-text {
  text-align: center;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  margin: 0;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ===== 底部 4 列统计 ===== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.stats-card {
  padding: 16px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  text-align: center;
}
.stats-icon-box {
  width: 40px;
  height: 40px;
  border-radius: var(--kb-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 8px;
}
.stats-icon-primary {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.stats-icon-accent {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-accent);
}
.stats-icon-warning {
  background: rgba(245, 158, 11, 0.08);
  color: var(--kb-warning);
}
.stats-icon-danger {
  background: rgba(239, 68, 68, 0.08);
  color: var(--kb-destructive);
}
.stats-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.2;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .action-group {
    width: 100%;
  }
  .category-select {
    flex: 1;
  }
  .progress-block {
    flex-direction: column;
    align-items: stretch;
  }
  .progress-right {
    justify-content: space-around;
    gap: 16px;
  }
  .flip-card {
    height: 320px;
  }
  .flip-card-face {
    padding: 24px;
  }
  .face-question {
    font-size: 20px;
  }
  .action-bar {
    gap: 8px;
  }
  .action-btn {
    height: 40px;
    padding: 0 14px;
    font-size: 13px;
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
