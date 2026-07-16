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
              <Icon name="user" :size="16" s />
              <span class="text-white/80 text-sm">学习进度</span>
            </div>
            <p class="text-2xl font-bold">{{ currentPath.progress }}%</p>
          </div>
          <div class="bg-white/15 backdrop-blur-sm rounded-lg p-4">
            <div class="flex items-center gap-2 mb-1">
              <Icon name="target" :size="16" />
              <span class="text-white/80 text-sm">适合人群</span>
            </div>
            <p class="text-sm font-medium text-white/90 line-clamp-1">{{ currentPath.suitableFor }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
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
              v-for="chapter in pathChapters"
              :key="chapter.id"
              :class="[
                'flex items-center gap-4 p-4 rounded-lg cursor-pointer transition-all duration-200 group',
                chapter.isCurrent
                  ? 'bg-primary-50 ring-1 ring-primary-200'
                  : 'hover:bg-gray-50',
              ]"
              @click="goToChapter(chapter.id)"
            >
              <div
                :class="[
                  'w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 transition-all',
                  chapter.completed
                    ? 'bg-success-500 text-white'
                    : chapter.isCurrent
                    ? 'bg-primary-500 text-white'
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
                        : chapter.isCurrent
                        ? 'text-primary-700'
                        : 'text-gray-800',
                    ]"
                  >
                    {{ chapter.title }}
                  </h3>
                  <span
                    v-if="chapter.isCurrent"
                    class="px-2 py-0.5 text-xs bg-primary-100 text-primary-600 rounded-full flex-shrink-0"
                  >
                    进行中
                  </span>
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
                  <circle
                    cx="60"
                    cy="60"
                    r="50"
                    stroke="#E5E7EB"
                    stroke-width="8"
                    fill="none"
                  />
                  <circle
                    cx="60"
                    cy="60"
                    r="50"
                    stroke="url(#detailProgressGradient)"
                    stroke-width="8"
                    fill="none"
                    stroke-linecap="round"
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
                  <span class="text-2xl font-bold text-primary-600">
                    {{ currentPath?.progress || 0 }}%
                  </span>
                </div>
              </div>
              <p class="text-sm text-gray-500">
                已完成 {{ completedChaptersCount }} / {{ pathChapters.length }} 章
              </p>
            </div>

            <Button block size="lg" @click="continueLearning">
              <Icon name="play" :size="20" Circle />
              {{ hasStarted ? '继续学习' : '开始学习' }}
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import { learningPaths, chapters } from '@/data/learning'
const route = useRoute()
const router = useRouter()

const pathId = computed(() => route.params.id as string)

const currentPath = computed(() => {
  return learningPaths.find((p) => p.id === pathId.value)
})

const pathChapters = computed(() => {
  return chapters.filter((c) => c.pathId === pathId.value).sort((a, b) => a.order - b.order)
})

const completedChaptersCount = computed(() => {
  return pathChapters.value.filter((c) => c.completed).length
})

const hasStarted = computed(() => {
  return (currentPath.value?.progress || 0) > 0
})

const progressRadius = 50
const progressCircumference = 2 * Math.PI * progressRadius

const progressDashoffset = computed(() => {
  const progress = currentPath.value?.progress || 0
  return progressCircumference - (progress / 100) * progressCircumference
})

const iconNameMap: Record<string, string> = {
  code: 'code',
  fileCode: 'file-code',
  brain: 'brain',
  layers: 'layers',
  server: 'server',
  puzzle: 'puzzle',
}

const getPathIconName = (iconName: string): string => {
  return iconNameMap[iconName] || 'code'
}

const getDifficultyLabel = (difficulty: string) => {
  const labels: Record<string, string> = {
    beginner: '入门',
    intermediate: '进阶',
    advanced: '高级',
  }
  return labels[difficulty] || difficulty
}

const getDifficultyBadgeVariant = (difficulty: string) => {
  const variants: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'default'> = {
    beginner: 'success',
    intermediate: 'primary',
    advanced: 'danger',
  }
  return variants[difficulty] || 'default'
}

const formatDuration = (minutes: number) => {
  if (minutes < 60) {
    return `${minutes}分钟`
  }
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}h${mins}m` : `${hours}h`
}

const goBack = () => {
  router.push('/learning/paths')
}

const goToChapter = (chapterId: string) => {
  router.push(`/learning/chapter/${chapterId}`)
}

const continueLearning = () => {
  const currentChapter = pathChapters.value.find((c) => c.isCurrent)
  const firstIncomplete = pathChapters.value.find((c) => !c.completed)
  const targetChapter = currentChapter || firstIncomplete || pathChapters.value[0]
  if (targetChapter) {
    router.push(`/learning/chapter/${targetChapter.id}`)
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

.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bg-grid-white\/10 {
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3e%3cg fill='none' fill-rule='evenodd'%3e%3cg fill='%23ffffff' fill-opacity='0.1'%3e%3cpath d='M0 0h32v32H0z'/%3e%3c/g%3e%3c/g%3e%3c/svg%3e");
}
</style>
