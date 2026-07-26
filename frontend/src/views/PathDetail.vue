<template>
  <div class="space-y-6 animate-fade-in">
    <button
      @click="goBack"
      class="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-primary-500 transition-colors"
    >
      <Icon name="arrow-left" :size="16" />
      返回学习路径
    </button>

    <div
      v-if="currentPath"
      :class="[
        'relative overflow-hidden rounded-xl p-8 text-white bg-gradient-to-br',
        currentPath.coverGradient,
      ]"
    >
      <div class="absolute inset-0 bg-grid-white/10 [mask-image:linear-gradient(0deg,transparent,white)]" />
      <div class="absolute top-0 right-0 w-96 h-96 bg-white/10 rounded-full -translate-y-1/2 translate-x-1/2 blur-3xl" />
      <div class="absolute bottom-0 left-0 w-64 h-64 bg-white/10 rounded-full translate-y-1/2 -translate-x-1/2 blur-2xl" />

      <div class="relative z-10">
        <div class="flex items-start justify-between gap-6">
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-3">
              <Badge :variant="getDifficultyBadgeVariant(currentPath.difficulty)">
                {{ getDifficultyLabel(currentPath.difficulty) }}
              </Badge>
            </div>
            <h1 class="text-2xl md:text-3xl font-bold mb-3">{{ currentPath.title }}</h1>
            <p class="text-white/80 max-w-2xl">{{ currentPath.description }}</p>
          </div>
          <div class="hidden md:block">
            <Icon :name="getPathIconName(currentPath.icon)" :size="96" class="text-white/80 drop-shadow-lg" />
          </div>
        </div>

        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mt-8">
          <div class="bg-white/15 backdrop-blur-sm rounded-lg p-4">
            <div class="flex items-center gap-2 mb-1">
              <Icon name="book-open" :size="16" />
              <span class="text-white/80 text-sm">章节数</span>
            </div>
            <p class="text-2xl font-bold">{{ currentPath.chaptersCount }}</p>
          </div>
          <div class="bg-white/15 backdrop-blur-sm rounded-lg p-4">
            <div class="flex items-center gap-2 mb-1">
              <Icon name="clock" :size="16" />
              <span class="text-white/80 text-sm">总时长</span>
            </div>
            <p class="text-2xl font-bold">{{ formatDuration(currentPath.totalDuration) }}</p>
          </div>
          <div class="bg-white/15 backdrop-blur-sm rounded-lg p-4">
            <div class="flex items-center gap-2 mb-1">
              <Icon name="user" :size="16" />
              <span class="text-white/80 text-sm">学习进度</span>
            </div>
            <p class="text-2xl font-bold">{{ currentPath.progress }}%</p>
          </div>
          <div class="bg-white/15 backdrop-blur-sm rounded-lg p-4">
            <div class="flex items-center gap-2 mb-1">
              <Icon name="users" :size="16" />
              <span class="text-white/80 text-sm">报名人数</span>
            </div>
            <p class="text-2xl font-bold">{{ currentPath.enrolledCount }}</p>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="text-center py-16 text-gray-400">加载中...</div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6" v-if="currentPath">
      <div class="lg:col-span-2">
        <Card hoverable>
          <template #header>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="list" :size="20" />
                <h2 class="font-semibold text-gray-800">章节列表</h2>
              </div>
              <span class="text-sm text-gray-500">
                {{ completedChaptersCount }}/{{ pathChapters.length }} 已完成
              </span>
            </div>
          </template>

          <div class="space-y-2">
            <div
              v-for="chapter in pathChapters" :key="chapter.id"
              :class="[
                'flex items-center gap-4 p-4 rounded-lg cursor-pointer transition-all duration-200 group',
                chapter.completed
                  ? 'bg-gray-50'
                  : 'hover:bg-gray-50',
              ]"
              @click="goToChapter(chapter.id)"
            >
              <div
                :class="[
                  'w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 transition-all',
                  chapter.completed
                    ? 'bg-success-500 text-white'
                    : 'bg-gray-100 text-gray-500 group-hover:bg-gray-200',
                ]"
              >
                <Icon name="check" :size="20" v-if="chapter.completed" />
                <span v-else class="text-sm font-medium">{{ chapter.order }}</span>
              </div>

              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <h3
                    :class="[
                      'font-medium truncate',
                      chapter.completed
                        ? 'text-gray-500 line-through'
                        : 'text-gray-800',
                    ]"
                  >
                    {{ chapter.title }}
                  </h3>
                </div>
              </div>

              <div class="flex items-center gap-2 flex-shrink-0">
                <div class="flex items-center gap-1 text-sm text-gray-400">
                  <Icon name="clock" :size="16" />
                  <span>{{ chapter.duration }}分钟</span>
                </div>
                <Icon name="chevron-right" :size="20" />
              </div>
            </div>
          </div>
        </Card>
      </div>

      <div class="space-y-6">
        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="trending-up" :size="20" />
              <h2 class="font-semibold text-gray-800">学习进度</h2>
            </div>
          </template>

          <div class="space-y-4">
            <div class="text-center">
              <div class="relative w-28 h-28 mx-auto mb-3">
                <svg class="w-28 h-28 transform -rotate-90" viewBox="0 0 120 120">
                  <circle cx="60" cy="60" r="50" stroke="#E5E7EB" stroke-width="8" fill="none" />
                  <circle
                    cx="60" cy="60" r="50"
                    stroke="url(#detailProgressGradient)"
                    stroke-width="8" fill="none" stroke-linecap="round"
                    :stroke-dasharray="progressCircumference"
                    :stroke-dashoffset="progressDashoffset"
                    class="transition-all duration-1000 ease-out"
                  />
                  <defs>
                    <linearGradient id="detailProgressGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0%" style="stop-color:#3B6FE0" />
                      <stop offset="100%" style="stop-color:#6F9AF2" />
                    </linearGradient>
                  </defs>
                </svg>
                <div class="absolute inset-0 flex flex-col items-center justify-center">
                  <span class="text-2xl font-bold text-primary-600">{{ currentPath?.progress || 0 }}%</span>
                </div>
              </div>
              <p class="text-sm text-gray-500">
                已完成 {{ completedChaptersCount }} / {{ pathChapters.length }} 章
              </p>
            </div>

            <Button block size="lg" @click="continueLearning" :loading="enrolling">
              <Icon name="play" :size="20" />
              {{ hasStarted ? '继续学习' : '开始学习' }}
            </Button>
            <Button block variant="secondary" @click="enroll" :loading="enrolling" v-if="!hasStarted">
              报名该路径
            </Button>
          </div>
        </Card>

        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="lightbulb" :size="20" />
              <h2 class="font-semibold text-gray-800">学习建议</h2>
            </div>
          </template>

          <div class="space-y-3">
            <div class="flex items-start gap-3">
              <div class="w-6 h-6 rounded-full bg-primary-50 flex items-center justify-center flex-shrink-0 mt-0.5">
                <span class="text-xs font-medium text-primary-500">1</span>
              </div>
              <p class="text-sm text-gray-600">建议每天学习 1-2 个章节，保持学习节奏</p>
            </div>
            <div class="flex items-start gap-3">
              <div class="w-6 h-6 rounded-full bg-primary-50 flex items-center justify-center flex-shrink-0 mt-0.5">
                <span class="text-xs font-medium text-primary-500">2</span>
              </div>
              <p class="text-sm text-gray-600">学完每章后完成配套练习，巩固知识点</p>
            </div>
            <div class="flex items-start gap-3">
              <div class="w-6 h-6 rounded-full bg-primary-50 flex items-center justify-center flex-shrink-0 mt-0.5">
                <span class="text-xs font-medium text-primary-500">3</span>
              </div>
              <p class="text-sm text-gray-600">使用闪卡功能复习重要概念，加深记忆</p>
            </div>
          </div>
        </Card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 学习路径详情页：展示路径概览、章节列表、环形进度与报名/继续学习入口。
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import { learningApi } from '@/api'
import type { LearningPathVO, LearningChapterVO } from '@/api/types'

const route = useRoute()
const router = useRouter()

const gradients = [
  'from-blue-500 to-indigo-600',
  'from-emerald-500 to-teal-600',
  'from-purple-500 to-fuchsia-600',
  'from-orange-500 to-rose-600',
]
const icons = ['code', 'server', 'database', 'brain', 'layers', 'puzzle']

const pathId = computed(() => Number(route.params.id))
const pathDetail = ref<LearningPathVO | null>(null)
const chapters = ref<LearningChapterVO[]>([])
const enrolling = ref(false)

// 将后端返回的难度文案（入门/进阶/高级）映射为统一的枚举值
const levelToDifficulty = (level?: string): 'beginner' | 'intermediate' | 'advanced' => {
  const map: Record<string, 'beginner' | 'intermediate' | 'advanced'> = {
    入门: 'beginner', 进阶: 'intermediate', 高级: 'advanced',
    beginner: 'beginner', intermediate: 'intermediate', advanced: 'advanced',
  }
  return map[level || ''] || 'beginner'
}

interface ViewChapter {
  id: number
  title: string
  duration: number
  order: number
  completed: boolean
  isCurrent: boolean
}

// 将章节按 sortOrder 升序排序，并映射为视图模型
const pathChapters = computed<ViewChapter[]>(() =>
  chapters.value
    .slice()
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map((c) => ({
      id: c.id,
      title: c.title,
      duration: c.duration || 0,
      order: c.sortOrder || 0,
      completed: !!c.completed,
      isCurrent: false,
    }))
)

const completedChaptersCount = computed(() => pathChapters.value.filter((c) => c.completed).length)

const currentPath = computed(() => {
  if (!pathDetail.value) return null
  const total = pathChapters.value.length
  const progress = total > 0 ? Math.round((completedChaptersCount.value / total) * 100) : 0
  return {
    id: pathDetail.value.id,
    title: pathDetail.value.title,
    description: pathDetail.value.description,
    coverGradient: gradients[pathDetail.value.id % gradients.length] || gradients[0],
    icon: icons[pathDetail.value.id % icons.length] || 'code',
    difficulty: levelToDifficulty(pathDetail.value.level),
    chaptersCount: pathDetail.value.chapterCount || total,
    totalDuration: pathDetail.value.totalDuration || 0,
    progress,
    enrolledCount: pathDetail.value.enrolledCount || 0,
  }
})

const hasStarted = computed(() => (currentPath.value?.progress || 0) > 0)

const progressRadius = 50
// 圆环周长，用于 SVG stroke-dasharray 绘制进度环
const progressCircumference = 2 * Math.PI * progressRadius
// 进度偏移：周长减去已进度对应的弧长，dashoffset 越大空白处越多
const progressDashoffset = computed(() => {
  const progress = currentPath.value?.progress || 0
  return progressCircumference - (progress / 100) * progressCircumference
})

const getPathIconName = (iconName: string): string => {
  const iconNameMap: Record<string, string> = {
    code: 'code', fileCode: 'file-code', brain: 'brain',
    layers: 'layers', server: 'server', puzzle: 'puzzle',
  }
  return iconNameMap[iconName] || 'code'
}

const getDifficultyLabel = (difficulty: string) => {
  const labels: Record<string, string> = { beginner: '入门', intermediate: '进阶', advanced: '高级' }
  return labels[difficulty] || difficulty
}

const getDifficultyBadgeVariant = (difficulty: string) => {
  const variants: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'default'> = {
    beginner: 'success', intermediate: 'primary', advanced: 'danger',
  }
  return variants[difficulty] || 'default'
}

const formatDuration = (minutes: number) => {
  if (!minutes) return '0分钟'
  if (minutes < 60) return `${minutes}分钟`
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}小时${mins}分` : `${hours}小时`
}

const goBack = () => router.push('/learning/paths')

const goToChapter = (chapterId: number) => router.push(`/learning/chapter/${chapterId}`)

// 跳转至第一个未完成章节（无则跳首章）
const continueLearning = () => {
  const firstIncomplete = pathChapters.value.find((c) => !c.completed)
  const target = firstIncomplete || pathChapters.value[0]
  if (target) router.push(`/learning/chapter/${target.id}`)
}

const enroll = async () => {
  enrolling.value = true
  try {
    await learningApi.enroll(pathId.value)
  } catch {
    /* 忽略 */
  } finally {
    enrolling.value = false
  }
}

onMounted(async () => {
  try {
    pathDetail.value = await learningApi.pathDetail(pathId.value)
  } catch {
    pathDetail.value = null
  }
  try {
    chapters.value = await learningApi.chapters(pathId.value)
  } catch {
    chapters.value = []
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

.bg-grid-white\/10 {
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3e%3cg fill='none' fill-rule='evenodd'%3e%3cg fill='%23ffffff' fill-opacity='0.1'%3e%3cpath d='M0 0h32v32H0z'/%3e%3c/g%3e%3c/g%3e%3c/svg%3e");
}
</style>
