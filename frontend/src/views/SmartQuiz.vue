<template>
  <AppShell>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">智能出题</h1>
          <p class="text-gray-500 text-sm mt-1">AI 根据你的学习情况智能生成题目</p>
        </div>
        <Button icon-name="zap" @click="generateQuiz">重新生成</Button>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2 space-y-6">
          <Card v-if="currentQuiz">
            <template #header>
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-3">
                  <Badge variant="primary">AI 生成</Badge>
                  <span class="text-sm text-gray-500">{{ currentQuiz.category }}</span>
                </div>
                <span class="text-sm text-gray-400">{{ currentIndex + 1 }} / {{ quizzes.length }}</span>
              </div>
            </template>

            <div class="space-y-6">
              <div>
                <h3 class="text-lg font-medium text-gray-800 mb-4">{{ currentQuiz.question }}</h3>
                <div class="space-y-3">
                  <button
                    v-for="(option, index) in currentQuiz.options"
                    :key="index"
                    class="w-full text-left px-4 py-3 rounded-lg border-2 transition-all"
                    :class="getOptionClass(index)"
                    :disabled="hasAnswered"
                    @click="selectAnswer(index)"
                  >
                    <div class="flex items-center gap-3">
                      <span
                        class="w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium flex-shrink-0"
                        :class="getOptionLabelClass(index)"
                      >
                        {{ String.fromCharCode(65 + index) }}
                      </span>
                      <span class="text-sm">{{ option }}</span>
                    </div>
                  </button>
                </div>
              </div>

              <div v-if="hasAnswered" class="bg-gray-50 rounded-lg p-4">
                <div class="flex items-start gap-3">
                  <Icon
                    :name="isCorrect ? 'check-circle' : 'alert-circle'"
                    :size="20"
                    :class="isCorrect ? 'text-green-500' : 'text-red-500'"
                    class="flex-shrink-0 mt-0.5"
                  />
                  <div>
                    <p class="font-medium" :class="isCorrect ? 'text-green-700' : 'text-red-700'">
                      {{ isCorrect ? '回答正确！' : '回答错误' }}
                    </p>
                    <p class="text-sm text-gray-600 mt-1">{{ currentQuiz.explanation }}</p>
                  </div>
                </div>
              </div>

              <div class="flex items-center justify-between pt-4 border-t">
                <Button
                  variant="secondary"
                  :disabled="currentIndex === 0"
                  @click="currentIndex--"
                >
                  上一题
                </Button>
                <Button
                  v-if="!hasAnswered"
                  :disabled="selectedAnswer === -1"
                  @click="submitAnswer"
                >
                  提交答案
                </Button>
                <Button
                  v-else
                  :disabled="currentIndex === quizzes.length - 1"
                  @click="nextQuestion"
                >
                  下一题
                </Button>
              </div>
            </div>
          </Card>
        </div>

        <div class="space-y-6">
          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">测验统计</h3>
            </template>
            <div class="space-y-4">
              <div class="text-center">
                <div class="relative w-24 h-24 mx-auto mb-3">
                  <svg class="w-24 h-24 transform -rotate-90" viewBox="0 0 100 100">
                    <circle cx="50" cy="50" r="45" stroke="#E5E7EB" stroke-width="8" fill="none" />
                    <circle
                      cx="50" cy="50" r="45"
                      stroke="#3B6FE0"
                      stroke-width="8"
                      fill="none"
                      stroke-linecap="round"
                      :stroke-dasharray="283"
                      :stroke-dashoffset="283 - (283 * stats.accuracy / 100)"
                      class="transition-all duration-500"
                    />
                  </svg>
                  <div class="absolute inset-0 flex items-center justify-center">
                    <span class="text-xl font-bold text-gray-800">{{ stats.accuracy }}%</span>
                  </div>
                </div>
                <p class="text-sm text-gray-500">正确率</p>
              </div>
              <div class="grid grid-cols-3 gap-2 text-center">
                <div class="p-2 bg-green-50 rounded-lg">
                  <p class="text-lg font-bold text-green-600">{{ stats.correct }}</p>
                  <p class="text-xs text-gray-500">正确</p>
                </div>
                <div class="p-2 bg-red-50 rounded-lg">
                  <p class="text-lg font-bold text-red-600">{{ stats.wrong }}</p>
                  <p class="text-xs text-gray-500">错误</p>
                </div>
                <div class="p-2 bg-gray-50 rounded-lg">
                  <p class="text-lg font-bold text-gray-600">{{ stats.remaining }}</p>
                  <p class="text-xs text-gray-500">剩余</p>
                </div>
              </div>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">题目导航</h3>
            </template>
            <div class="grid grid-cols-5 gap-2">
              <button
                v-for="(quiz, index) in quizzes"
                :key="quiz.id"
                class="w-10 h-10 rounded-lg text-sm font-medium transition-all"
                :class="[
                  currentIndex === index ? 'bg-primary-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
                  quiz.userAnswer !== undefined ? (quiz.userAnswer === quiz.correctAnswer ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700') : ''
                ]"
                @click="currentIndex = index"
              >
                {{ index + 1 }}
              </button>
            </div>
          </Card>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import AppShell from '@/components/layout/AppShell.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'

const currentIndex = ref(0)
const selectedAnswer = ref(-1)
const hasAnswered = ref(false)

interface Quiz {
  id: string
  question: string
  options: string[]
  correctAnswer: number
  category: string
  explanation: string
  userAnswer?: number
}

const quizzes = ref<Quiz[]>([
  {
    id: '1',
    question: '在 Vue 3 中，以下哪个选项是 Composition API 的核心函数？',
    options: ['data()', 'setup()', 'methods()', 'computed()'],
    correctAnswer: 1,
    category: '前端开发',
    explanation: 'setup() 是 Composition API 的入口函数，在组件创建之前执行，用于设置响应式数据和逻辑。',
  },
  {
    id: '2',
    question: 'JavaScript 中，以下哪种数据类型不是原始类型？',
    options: ['String', 'Number', 'Object', 'Boolean'],
    correctAnswer: 2,
    category: '前端开发',
    explanation: 'Object 是引用类型，而 String、Number、Boolean 都是原始类型。',
  },
  {
    id: '3',
    question: '在 CSS 中，以下哪个属性用于设置元素的弹性布局？',
    options: ['display: block', 'display: flex', 'display: grid', 'display: inline'],
    correctAnswer: 1,
    category: '前端开发',
    explanation: 'display: flex 用于创建弹性容器，实现灵活的布局方案。',
  },
])

const currentQuiz = computed(() => quizzes.value[currentIndex.value])
const isCorrect = computed(() => selectedAnswer.value === currentQuiz.value?.correctAnswer)

const stats = computed(() => {
  const answered = quizzes.value.filter(q => q.userAnswer !== undefined)
  const correct = answered.filter(q => q.userAnswer === q.correctAnswer).length
  return {
    accuracy: answered.length > 0 ? Math.round(correct / answered.length * 100) : 0,
    correct,
    wrong: answered.length - correct,
    remaining: quizzes.value.length - answered.length,
  }
})

const getOptionClass = (index: number) => {
  if (!hasAnswered.value) {
    return selectedAnswer.value === index
      ? 'border-primary-500 bg-primary-50'
      : 'border-gray-200 hover:border-gray-300'
  }
  if (index === currentQuiz.value.correctAnswer) return 'border-green-500 bg-green-50'
  if (index === selectedAnswer.value && index !== currentQuiz.value.correctAnswer) return 'border-red-500 bg-red-50'
  return 'border-gray-200 opacity-50'
}

const getOptionLabelClass = (index: number) => {
  if (!hasAnswered.value) {
    return selectedAnswer.value === index
      ? 'bg-primary-500 text-white'
      : 'bg-gray-100 text-gray-600'
  }
  if (index === currentQuiz.value.correctAnswer) return 'bg-green-500 text-white'
  if (index === selectedAnswer.value) return 'bg-red-500 text-white'
  return 'bg-gray-100 text-gray-600'
}

const selectAnswer = (index: number) => {
  if (!hasAnswered.value) {
    selectedAnswer.value = index
  }
}

const submitAnswer = () => {
  hasAnswered.value = true
  quizzes.value[currentIndex.value].userAnswer = selectedAnswer.value
}

const nextQuestion = () => {
  if (currentIndex.value < quizzes.value.length - 1) {
    currentIndex.value++
    selectedAnswer.value = -1
    hasAnswered.value = false
  }
}

const generateQuiz = () => {
  alert('正在生成新的智能题目...')
  currentIndex.value = 0
  selectedAnswer.value = -1
  hasAnswered.value = false
  quizzes.value.forEach(q => q.userAnswer = undefined)
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
