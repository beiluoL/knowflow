<template>
  <div class="animate-fade-in">
    <div class="bg-white border-b border-gray-200 -mx-6 -mt-6 px-6 py-4 mb-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4 min-w-0">
          <button
            @click="goBackToPath"
            class="flex items-center gap-1 text-sm text-gray-500 hover:text-primary-500 transition-colors flex-shrink-0"
          >
            <Icon name="arrow-left" :size="16" />
            返回
          </button>
          <div class="h-5 w-px bg-gray-200 flex-shrink-0" />
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="text-sm text-gray-500">{{ currentPath?.title }}</span>
              <Icon name="chevron-right" :size="16" />
              <span class="text-sm font-medium text-gray-800 truncate">{{ currentChapter?.title }}</span>
            </div>
          </div>
        </div>
        <div class="flex items-center gap-3 flex-shrink-0">
          <Badge variant="primary">第 {{ currentChapter?.order }} 章</Badge>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-4 gap-6">
      <div class="hidden lg:block">
        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="list" :size="20" />
              <h2 class="font-semibold text-gray-800">章节目录</h2>
            </div>
          </template>

          <div class="space-y-1 max-h-[calc(100vh-220px)] overflow-y-auto pr-1">
            <div
              v-for="chapter in pathChapters"
              :key="chapter.id"
              :class="[
                'flex items-center gap-3 p-3 rounded-lg cursor-pointer transition-all duration-200',
                chapter.id === currentChapterId
                  ? 'bg-primary-50 text-primary-700'
                  : 'hover:bg-gray-50 text-gray-600',
              ]"
              @click="goToChapter(chapter.id)"
            >
              <div
                :class="[
                  'w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 text-xs font-medium',
                  chapter.completed
                    ? 'bg-success-500 text-white'
                    : chapter.id === currentChapterId
                    ? 'bg-primary-500 text-white'
                    : 'bg-gray-100 text-gray-500',
                ]"
              >
                <Icon name="check" :size="20" v-if="chapter.completed" />
                <span v-else>{{ chapter.order }}</span>
              </div>
              <span
                :class="[
                  'text-sm truncate',
                  chapter.completed ? 'line-through text-gray-400' : '',
                ]"
              >
                {{ chapter.title }}
              </span>
            </div>
          </div>
        </Card>
      </div>

      <div class="lg:col-span-2 space-y-6">
        <Card hoverable>
          <div class="prose max-w-none">
            <h1 class="text-2xl font-bold text-gray-800 mb-6">
              {{ currentChapter?.content.title }}
            </h1>

            <div
              v-for="(section, index) in currentChapter?.content.sections"
              :key="index"
              class="mb-8 last:mb-0"
            >
              <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center gap-2">
                <span class="w-1 h-6 bg-primary-500 rounded-full" />
                {{ section.heading }}
              </h2>
              <p class="text-gray-600 leading-relaxed mb-4">
                {{ section.content }}
              </p>
              <div
                v-if="section.code"
                class="relative group"
              >
                <div class="absolute top-3 right-3 flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button
                    @click="copyCode(section.code!)"
                    class="p-1.5 rounded-md bg-gray-700/50 hover:bg-gray-700 text-gray-300 hover:text-white transition-colors"
                  >
                    <Icon name="copy" :size="16" />
                  </button>
                </div>
                <pre class="bg-gray-900 text-gray-100 p-4 rounded-lg overflow-x-auto text-sm leading-relaxed">
<code>{{ section.code }}</code></pre>
              </div>
            </div>
          </div>
        </Card>

        <div class="flex items-center justify-between gap-4">
          <button
            @click="goToPrevChapter"
            :disabled="!hasPrevChapter"
            :class="[
              'flex items-center gap-2 px-5 py-2.5 rounded-lg transition-all',
              hasPrevChapter
                ? 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 hover:border-gray-300 shadow-sm'
                : 'bg-gray-50 text-gray-400 cursor-not-allowed',
            ]"
          >
            <Icon name="chevron-left" :size="20" />
            <span>上一章</span>
          </button>

          <button
            @click="markComplete"
            class="px-6 py-2.5 bg-success-500 text-white rounded-lg hover:bg-success-600 transition-colors font-medium shadow-sm flex items-center gap-2"
          >
            <Icon name="check" :size="20" Circle />
            标记完成
          </button>

          <button
            @click="goToNextChapter"
            :disabled="!hasNextChapter"
            :class="[
              'flex items-center gap-2 px-5 py-2.5 rounded-lg transition-all',
              hasNextChapter
                ? 'bg-primary-500 text-white hover:bg-primary-600 shadow-sm'
                : 'bg-gray-100 text-gray-400 cursor-not-allowed',
            ]"
          >
            <span>下一章</span>
            <Icon name="chevron-right" :size="20" />
          </button>
        </div>
      </div>

      <div class="space-y-6">
        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <BarChart3 class="w-5 h-5 text-primary-500" />
              <h2 class="font-semibold text-gray-800">学习进度</h2>
            </div>
          </template>

          <div class="space-y-4">
            <div>
              <div class="flex justify-between text-sm mb-2">
                <span class="text-gray-500">路径进度</span>
                <span class="font-medium text-gray-700">{{ currentPath?.progress }}%</span>
              </div>
              <Progress :percentage="currentPath?.progress || 0" variant="primary" />
            </div>
            <div class="text-sm text-gray-500">
              已完成 {{ completedChapterIndex }} / {{ pathChapters.length }} 章
            </div>
          </div>
        </Card>

        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="lightbulb" :size="20" />
              <h2 class="font-semibold text-gray-800">本章知识点</h2>
            </div>
          </template>

          <div class="space-y-2">
            <div
              v-for="(point, index) in currentChapter?.content.knowledgePoints"
              :key="index"
              class="flex items-center gap-2 text-sm text-gray-600"
            >
              <div class="w-1.5 h-1.5 rounded-full bg-primary-400 flex-shrink-0" />
              <span>{{ point }}</span>
            </div>
          </div>
        </Card>

        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="clock" :size="20" />
              <h2 class="font-semibold text-gray-800">本章信息</h2>
            </div>
          </template>

          <div class="space-y-3 text-sm">
            <div class="flex justify-between">
              <span class="text-gray-500">预计时长</span>
              <span class="font-medium text-gray-700">{{ currentChapter?.duration }} 分钟</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">章节序号</span>
              <span class="font-medium text-gray-700">第 {{ currentChapter?.order }} 章</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">知识点数</span>
              <span class="font-medium text-gray-700">{{ currentChapter?.content.knowledgePoints.length }} 个</span>
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
import Progress from '@/components/ui/Progress.vue'
import { learningPaths, chapters } from '@/data/learning'

const route = useRoute()
const router = useRouter()

const currentChapterId = computed(() => route.params.id as string)

const currentChapter = computed(() => {
  return chapters.find((c) => c.id === currentChapterId.value)
})

const currentPath = computed(() => {
  if (!currentChapter.value) return null
  return learningPaths.find((p) => p.id === currentChapter.value?.pathId)
})

const pathChapters = computed(() => {
  if (!currentChapter.value) return []
  return chapters
    .filter((c) => c.pathId === currentChapter.value?.pathId)
    .sort((a, b) => a.order - b.order)
})

const currentChapterIndex = computed(() => {
  return pathChapters.value.findIndex((c) => c.id === currentChapterId.value)
})

const completedChapterIndex = computed(() => {
  return pathChapters.value.filter((c) => c.completed).length
})

const hasPrevChapter = computed(() => {
  return currentChapterIndex.value > 0
})

const hasNextChapter = computed(() => {
  return currentChapterIndex.value < pathChapters.value.length - 1
})

const goBackToPath = () => {
  if (currentPath.value) {
    router.push(`/learning/path/${currentPath.value.id}`)
  } else {
    router.push('/learning/paths')
  }
}

const goToChapter = (chapterId: string) => {
  router.push(`/learning/chapter/${chapterId}`)
}

const goToPrevChapter = () => {
  if (hasPrevChapter.value) {
    const prevChapter = pathChapters.value[currentChapterIndex.value - 1]
    router.push(`/learning/chapter/${prevChapter.id}`)
  }
}

const goToNextChapter = () => {
  if (hasNextChapter.value) {
    const nextChapter = pathChapters.value[currentChapterIndex.value + 1]
    router.push(`/learning/chapter/${nextChapter.id}`)
  }
}

const copyCode = (code: string) => {
  navigator.clipboard.writeText(code)
}

const markComplete = () => {
  if (currentChapter.value) {
    currentChapter.value.completed = true
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

.prose h1 {
  margin-top: 0;
}

.prose h2 {
  margin-top: 1.5em;
  margin-bottom: 0.75em;
}

.prose p {
  margin-bottom: 1em;
}

.prose pre {
  margin: 1em 0;
}

pre {
  margin: 0;
}

code {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
}
</style>
