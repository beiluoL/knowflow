<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">学习闪卡</h1>
        <p class="text-gray-500 mt-1">通过间隔重复，高效记忆知识点</p>
      </div>
    </div>

    <div class="flex flex-wrap gap-4">
      <div class="flex flex-wrap gap-2">
        <span class="text-sm text-gray-500 py-1.5">分类：</span>
        <button
          v-for="cat in categories"
          :key="cat"
          @click="selectedCategory = cat"
          :class="[
            'px-3 py-1.5 rounded-full text-sm font-medium transition-all duration-200',
            selectedCategory === cat
              ? 'bg-primary-500 text-white'
              : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200',
          ]"
        >
          {{ cat }}
        </button>
      </div>
      <div class="flex flex-wrap gap-2">
        <span class="text-sm text-gray-500 py-1.5">难度：</span>
        <button
          v-for="diff in difficulties"
          :key="diff"
          @click="selectedDifficulty = diff"
          :class="[
            'px-3 py-1.5 rounded-full text-sm font-medium transition-all duration-200',
            selectedDifficulty === diff
              ? 'bg-primary-500 text-white'
              : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200',
          ]"
        >
          {{ getDifficultyLabel(diff) }}
        </button>
      </div>
    </div>

    <div class="flex flex-col items-center py-8">
      <div class="text-sm text-gray-500 mb-6">
        <span class="font-medium text-primary-500">{{ currentIndex + 1 }}</span>
        <span> / {{ filteredCards.length }}</span>
      </div>

      <div class="relative w-full max-w-xl perspective-1000">
        <div
          class="card-flip h-72 cursor-pointer"
          :class="{ 'flipped': isFlipped }"
          @click="flipCard"
        >
          <div class="card-face card-front absolute inset-0">
            <Card hoverable class="h-full flex flex-col">
              <div class="flex items-center justify-between mb-4">
                <Badge variant="primary">{{ currentCard?.category }}</Badge>
                <Badge :variant="getDifficultyBadgeVariant(currentCard?.difficulty)">
                  {{ getDifficultyLabel(currentCard?.difficulty) }}
                </Badge>
              </div>
              <div class="flex-1 flex items-center justify-center">
                <div class="text-center">
                  <HelpCircle class="w-12 h-12 text-primary-300 mx-auto mb-4" />
                  <h3 class="text-xl font-semibold text-gray-800 leading-relaxed">
                    {{ currentCard?.question }}
                  </h3>
                </div>
              </div>
              <div class="text-center text-sm text-gray-400 mt-4">
                点击卡片查看答案
              </div>
            </Card>
          </div>

          <div class="card-face card-back absolute inset-0">
            <Card hoverable class="h-full flex flex-col">
              <div class="flex items-center justify-between mb-4">
                <Badge variant="success">答案</Badge>
                <Badge variant="primary">{{ currentCard?.category }}</Badge>
              </div>
              <div class="flex-1 overflow-y-auto">
                <div class="text-gray-700 leading-relaxed whitespace-pre-line">
                  {{ currentCard?.answer }}
                </div>
              </div>
              <div class="text-center text-sm text-gray-400 mt-4 pt-4 border-t border-gray-100">
                点击卡片返回问题
              </div>
            </Card>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-6 mt-8">
        <button
          @click="prevCard"
          :disabled="currentIndex === 0"
          :class="[
            'w-12 h-12 rounded-full flex items-center justify-center transition-all duration-200',
            currentIndex === 0
              ? 'bg-gray-100 text-gray-300 cursor-not-allowed'
              : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50 hover:border-gray-300 shadow-sm',
          ]"
        >
          <Icon name="chevron-left" :size="24" />
        </button>

        <button
          @click="flipCard"
          class="w-14 h-14 rounded-full bg-primary-500 text-white flex items-center justify-center hover:bg-primary-600 transition-colors shadow-lg hover:shadow-xl"
        >
          <Icon name="refresh-cw" :size="24" />
        </button>

        <button
          @click="nextCard"
          :disabled="currentIndex === filteredCards.length - 1"
          :class="[
            'w-12 h-12 rounded-full flex items-center justify-center transition-all duration-200',
            currentIndex === filteredCards.length - 1
              ? 'bg-gray-100 text-gray-300 cursor-not-allowed'
              : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50 hover:border-gray-300 shadow-sm',
          ]"
        >
          <Icon name="chevron-right" :size="24" />
        </button>
      </div>

      <div class="flex items-center gap-4 mt-8">
        <button
          @click="markAsUnknown"
          class="flex items-center gap-2 px-6 py-3 bg-red-50 text-red-600 rounded-xl hover:bg-red-100 transition-colors font-medium"
        >
          <Icon name="x" :size="20" />
          没记住
        </button>

        <button
          @click="shuffleCards"
          class="flex items-center gap-2 px-4 py-3 bg-gray-100 text-gray-600 rounded-xl hover:bg-gray-200 transition-colors"
        >
          <Icon name="shuffle" :size="20" />
        </button>

        <button
          @click="markAsKnown"
          class="flex items-center gap-2 px-6 py-3 bg-green-50 text-green-600 rounded-xl hover:bg-green-100 transition-colors font-medium"
        >
          <Icon name="check" :size="20" />
          记住了
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import { flashCards, flashCardCategories, flashCardDifficulties } from '@/data/learning'
import type { FlashCard } from '@/data/learning'

const categories = flashCardCategories
const difficulties = flashCardDifficulties

const selectedCategory = ref('全部')
const selectedDifficulty = ref('全部')
const currentIndex = ref(0)
const isFlipped = ref(false)
const cards = ref<FlashCard[]>([...flashCards])

const filteredCards = computed(() => {
  return cards.value.filter((card) => {
    const categoryMatch = selectedCategory.value === '全部' || card.category === selectedCategory.value
    const difficultyMatch = selectedDifficulty.value === '全部' || card.difficulty === selectedDifficulty.value
    return categoryMatch && difficultyMatch
  })
})

const currentCard = computed(() => {
  return filteredCards.value[currentIndex.value] || null
})

watch([selectedCategory, selectedDifficulty], () => {
  currentIndex.value = 0
  isFlipped.value = false
})

const getDifficultyLabel = (difficulty?: string) => {
  const labels: Record<string, string> = {
    easy: '简单',
    medium: '中等',
    hard: '困难',
    全部: '全部',
  }
  return labels[difficulty || ''] || difficulty
}

const getDifficultyBadgeVariant = (difficulty?: string) => {
  const variants: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'default'> = {
    easy: 'success',
    medium: 'warning',
    hard: 'danger',
  }
  return variants[difficulty || ''] || 'default'
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

const shuffleCards = () => {
  const shuffled = [...filteredCards.value]
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
  }
  cards.value = shuffled
  currentIndex.value = 0
  isFlipped.value = false
}

const markAsKnown = () => {
  if (currentIndex.value < filteredCards.value.length - 1) {
    nextCard()
  }
}

const markAsUnknown = () => {
  const card = currentCard.value
  if (card) {
    isFlipped.value = false
    if (currentIndex.value < filteredCards.value.length - 1) {
      nextCard()
    }
  }
}
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
</style>
