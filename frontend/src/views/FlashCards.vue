<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">学习闪卡</h1>
        <p class="text-gray-500 mt-1">通过间隔重复，高效记忆知识点</p>
      </div>
    </div>

    <div class="flex flex-wrap gap-4" v-if="!loading">
      <!-- Deck Selector (设计稿：横滚 Tabs，仅分类) -->
      <div class="flex flex-nowrap gap-2 overflow-x-auto no-scrollbar w-full">
        <button
          v-for="cat in categories" :key="cat"
          @click="selectedCategory = cat"
          class="shrink-0 whitespace-nowrap rounded-lg px-3 py-1.5 text-[13px] font-medium transition-all"
          :style="selectedCategory === cat
            ? 'background: var(--kb-primary); color: var(--kb-primary-foreground);'
            : 'border: 1px solid var(--kb-border); color: var(--kb-muted-foreground);'"
        >
          {{ cat }}
        </button>
      </div>
    </div>

    <!-- 独立进度块 (设计稿：15/30 张 · 连续 7 天 + 进度条) -->
    <div v-if="!loading && filteredCards.length > 0" class="w-full max-w-xl mx-auto">
      <div class="flex items-center justify-between">
        <span class="text-[13px] font-medium text-gray-700">
          {{ currentIndex + 1 }}/{{ filteredCards.length }} 张 · 连续 {{ streakDays }} 天
        </span>
        <span class="text-[12px] text-primary-600">{{ reviewProgress }}%</span>
      </div>
      <div class="mt-1.5 h-1.5 w-full rounded-full bg-[#E8ECF1]">
        <div
          class="h-full rounded-full transition-all duration-300"
          :style="{ width: `${reviewProgress}%`, background: 'var(--kb-primary)' }"
        ></div>
      </div>
    </div>

    <div v-if="loading" class="text-center py-16 text-gray-400">加载中...</div>

    <div v-else-if="filteredCards.length === 0" class="text-center py-16 text-gray-400">
      暂无闪卡
    </div>

    <div v-else class="flex flex-col items-center py-4">
      <div class="relative w-full max-w-xl perspective-1000">
        <div
          class="card-flip h-72 cursor-pointer"
          :class="{ 'flipped': isFlipped }"
          @click="flipCard"
        >
          <div class="card-face card-front absolute inset-0">
            <div class="relative flex flex-col items-center rounded-2xl p-5 pt-6 h-full bg-white shadow-[0_1px_3px_rgba(0,0,0,0.08),0_4px_12px_rgba(0,0,0,0.04)]">
              <div class="absolute inset-x-0 top-0 h-[3px] rounded-t-2xl bg-primary-500"></div>
              <div class="flex w-full items-center justify-between">
                <span class="inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium" style="background:rgba(59,111,224,0.1);color:#3B6FE0">{{ currentCard?.category }}</span>
                <span class="text-[12px] font-medium text-gray-400">#{{ currentCard?.id }}</span>
              </div>
              <div class="mt-6 flex flex-1 items-center justify-center px-2 text-center">
                <p class="text-[17px] font-semibold text-gray-800 leading-relaxed" style="text-wrap:balance;word-break:keep-all;overflow-wrap:break-word">{{ currentCard?.question }}</p>
              </div>
              <div class="mt-6 flex items-center gap-1.5 text-[13px] text-gray-400">
                <span>点击查看答案</span>
                <Icon name="refresh-cw" :size="14" />
              </div>
            </div>
          </div>

          <div class="card-face card-back absolute inset-0">
            <div class="relative flex flex-col items-center rounded-2xl p-5 pt-6 h-full bg-white shadow-[0_1px_3px_rgba(0,0,0,0.08),0_4px_12px_rgba(0,0,0,0.04)]">
              <div class="absolute inset-x-0 top-0 h-[3px] rounded-t-2xl bg-success-500"></div>
              <div class="flex w-full items-center justify-between">
                <span class="inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium" style="background:rgba(16,185,129,0.1);color:#10B981">答案</span>
                <span class="text-[12px] font-medium text-gray-400">#{{ currentCard?.id }}</span>
              </div>
              <div class="mt-6 flex flex-1 items-center justify-center px-2 text-center overflow-y-auto">
                <p class="text-[15px] text-gray-700 leading-relaxed whitespace-pre-line">{{ currentCard?.answer }}</p>
              </div>
              <div class="mt-4 flex items-center gap-1.5 text-[13px] text-gray-400">
                <span>点击返回问题</span>
                <Icon name="refresh-cw" :size="14" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="mt-6 flex flex-col items-center px-4">
        <div class="flex items-center justify-center gap-6">
          <button
            @click="rateCard('unknown')"
            :disabled="reviewing"
            class="flex flex-col items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <span class="w-12 h-12 rounded-full flex items-center justify-center border-2 transition-transform hover:scale-110" style="border-color:#EF4444">
              <Icon name="x" :size="20" class="text-danger-500" />
            </span>
            <span class="text-[11px] font-medium text-danger-500">不熟悉</span>
          </button>
          <button
            @click="rateCard('familiar')"
            :disabled="reviewing"
            class="flex flex-col items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <span class="w-12 h-12 rounded-full flex items-center justify-center border-2 transition-transform hover:scale-110" style="border-color:#F59E0B">
              <Icon name="minus" :size="20" class="text-warning-500" />
            </span>
            <span class="text-[11px] font-medium text-warning-500">有印象</span>
          </button>
          <button
            @click="rateCard('mastered')"
            :disabled="reviewing"
            class="flex flex-col items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <span class="w-12 h-12 rounded-full flex items-center justify-center border-2 transition-transform hover:scale-110" style="border-color:#3B6FE0">
              <Icon name="check" :size="20" class="text-primary-500" />
            </span>
            <span class="text-[11px] font-medium text-primary-500">掌握了</span>
          </button>
        </div>
        <transition name="fade">
          <p v-if="feedback" class="mt-3 text-[12px] font-medium text-gray-500">{{ feedback }}</p>
        </transition>
        <button
          @click="nextCard"
          :disabled="currentIndex >= filteredCards.length - 1"
          class="mt-4 text-[13px] font-medium text-gray-400 hover:text-gray-600 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          跳过
        </button>
      </div>

      <div class="mt-6 flex items-center justify-center gap-1 text-[12px] text-gray-400">
        <span>今日 {{ todayCount }} 张</span>
        <span class="text-[#E2E6EC]">·</span>
        <span>正确率 {{ accuracy }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 学习闪卡页：按分类/难度筛选卡片，支持翻面查看与「不会/有印象/掌握」评分复习。
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
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

const loading = ref(false)
const reviewing = ref(false)
const feedback = ref('')
let feedbackTimer: ReturnType<typeof setTimeout> | undefined

const categories = ref<string[]>(['全部'])

const selectedCategory = ref('全部')
const selectedDifficulty = ref('全部')
const currentIndex = ref(0)
const isFlipped = ref(false)
const cards = ref<FlashCard[]>([])

const streakDays = ref(7)
const todayCount = ref(0)
const correctCount = ref(0)

// 将后端 FlashcardVO 映射为本地展示用的闪卡结构（缺省字段兜底）
const mapCard = (f: FlashcardVO): FlashCard => ({
  id: f.id,
  category: f.category || '通用',
  difficulty: f.difficulty || 1,
  question: f.front || '',
  answer: f.back || '',
})

// 根据所选分类与难度档位过滤当前牌组
const filteredCards = computed(() => {
  return cards.value.filter((card) => {
    const categoryMatch = selectedCategory.value === '全部' || card.category === selectedCategory.value
    const difficultyLabel = getDifficultyLabel(card.difficulty)
    const difficultyMatch = selectedDifficulty.value === '全部' || difficultyLabel === selectedDifficulty.value
    return categoryMatch && difficultyMatch
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

watch([selectedCategory, selectedDifficulty], () => {
  currentIndex.value = 0
  isFlipped.value = false
})

const getDifficultyLabel = (difficulty?: number) => {
  if (difficulty === 2) return '中等'
  if (difficulty === 3) return '困难'
  return '简单'
}

const flipCard = () => {
  isFlipped.value = !isFlipped.value
}

const nextCard = () => {
  if (currentIndex.value < filteredCards.value.length - 1) {
    isFlipped.value = false
    currentIndex.value++
  }
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

// 提交评分：调用后端复习接口、本地标记已复习并更新连续/正确计数，随后推进到下一张
const rateCard = async (rating: Rating) => {
  if (reviewing.value || !currentCard.value) return
  reviewing.value = true
  try {
    await learningApi.reviewFlashcard(currentCard.value.id, ratingToQuality(rating))
    markReviewed(dateStr(new Date()), String(currentCard.value.id))
    todayCount.value++
    if (rating === 'mastered' || rating === 'familiar') {
      correctCount.value++
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
      showFeedback('已记录 · 掌握得不错 🎉')
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
})

onMounted(async () => {
  loading.value = true
  try {
    const list = await learningApi.flashcards()
    cards.value = list.map(mapCard)
    const uniqueCats = Array.from(new Set(list.map((f) => f.category || '通用')))
    categories.value = ['全部', ...uniqueCats]
  } catch {
    cards.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.perspective-1000 {
  perspective: 1000px;
}

.card-flip {
  position: relative;
  width: 100%;
  transform-style: preserve-3d;
  transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.card-flip.flipped {
  transform: rotateY(180deg);
}

.card-face {
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
}

.card-front {
  transform: rotateY(0deg);
}

.card-back {
  transform: rotateY(180deg);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
