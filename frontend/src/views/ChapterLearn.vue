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
              <span class="text-sm text-gray-500">{{ currentPathTitle }}</span>
              <Icon name="chevron-right" :size="16" />
              <span class="text-sm font-medium text-gray-800 truncate">{{ currentChapter?.title }}</span>
            </div>
          </div>
        </div>
        <div class="flex items-center gap-3 flex-shrink-0">
          <Badge variant="primary">第 {{ currentChapter?.sortOrder }} 章</Badge>
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
              v-for="chapter in pathChapters" :key="chapter.id"
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
                <span v-else>{{ chapter.sortOrder }}</span>
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
            <h1 class="text-2xl font-bold text-gray-800 mb-6">{{ currentChapter?.title }}</h1>
            <div v-html="renderedContent"></div>
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
            :disabled="currentChapter?.completed"
            class="px-6 py-2.5 bg-success-500 text-white rounded-lg hover:bg-success-600 transition-colors font-medium shadow-sm flex items-center gap-2 disabled:opacity-60"
          >
            <Icon name="check" :size="20" />
            {{ currentChapter?.completed ? '已完成' : '标记完成' }}
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
              <Icon name="trending-up" :size="20" />
              <h2 class="font-semibold text-gray-800">学习进度</h2>
            </div>
          </template>

          <div class="space-y-4">
            <div>
              <div class="flex justify-between text-sm mb-2">
                <span class="text-gray-500">路径进度</span>
                <span class="font-medium text-gray-700">{{ pathProgress }}%</span>
              </div>
              <Progress :percentage="pathProgress" variant="primary" />
            </div>
            <div class="text-sm text-gray-500">
              已完成 {{ completedChapterCount }} / {{ pathChapters.length }} 章
            </div>
          </div>
        </Card>

        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="lightbulb" :size="20" />
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
              <span class="font-medium text-gray-700">第 {{ currentChapter?.sortOrder }} 章</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">知识点数</span>
              <span class="font-medium text-gray-700">{{ knowledgePointCount }} 个</span>
            </div>
          </div>
        </Card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Progress from '@/components/ui/Progress.vue'
import { learningApi } from '@/api'
import type { LearningChapterVO, LearningPathVO } from '@/api/types'

const route = useRoute()
const router = useRouter()

const currentChapterId = computed(() => Number(route.params.id))
const currentChapter = ref<LearningChapterVO | null>(null)
const pathChapters = ref<LearningChapterVO[]>([])
const pathDetail = ref<LearningPathVO | null>(null)

const currentPathTitle = computed(() => pathDetail.value?.title || '')

const renderedContent = computed(() => renderMarkdown(currentChapter.value?.content || ''))

const knowledgePointCount = computed(
  () => (currentChapter.value?.content?.match(/^##\s+/gm) || []).length
)

const completedChapterCount = computed(() => pathChapters.value.filter((c) => c.completed).length)
const pathProgress = computed(() => {
  const total = pathChapters.value.length
  return total > 0 ? Math.round((completedChapterCount.value / total) * 100) : 0
})

const currentChapterIndex = computed(() => pathChapters.value.findIndex((c) => c.id === currentChapterId.value))
const hasPrevChapter = computed(() => currentChapterIndex.value > 0)
const hasNextChapter = computed(() => currentChapterIndex.value < pathChapters.value.length - 1)

const goBackToPath = () => {
  if (pathDetail.value) router.push(`/learning/path/${pathDetail.value.id}`)
  else router.push('/learning/paths')
}

const goToChapter = (chapterId: number) => router.push(`/learning/chapter/${chapterId}`)
const goToPrevChapter = () => {
  if (hasPrevChapter.value) {
    const prev = pathChapters.value[currentChapterIndex.value - 1]
    router.push(`/learning/chapter/${prev.id}`)
  }
}
const goToNextChapter = () => {
  if (hasNextChapter.value) {
    const next = pathChapters.value[currentChapterIndex.value + 1]
    router.push(`/learning/chapter/${next.id}`)
  }
}

const markComplete = async () => {
  if (!currentChapter.value) return
  try {
    await learningApi.completeChapter(currentChapter.value.id)
    await loadChapter(currentChapter.value.id)
  } catch {
    /* 忽略 */
  }
}

const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const renderMarkdown = (md: string): string => {
  if (!md) return '<p class="text-gray-400">本章暂无内容</p>'
  let html = ''
  let inCode = false
  let codeBuf: string[] = []
  for (const raw of md.split('\n')) {
    const line = raw.trimEnd()
    const fence = /^```(\w*)/.exec(line)
    if (fence) {
      if (!inCode) {
        inCode = true
        codeBuf = []
      } else {
        html += `<pre class="bg-gray-900 text-gray-100 p-4 rounded-lg overflow-x-auto text-sm leading-relaxed my-4"><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`
        inCode = false
      }
      continue
    }
    if (inCode) {
      codeBuf.push(line)
      continue
    }
    const h = /^(#{1,3})\s+(.*)$/.exec(line)
    if (h) {
      const text = h[2].replace(/[*`]/g, '').trim()
      html += `<h${h[1].length} class="font-bold text-gray-800 mt-6 mb-3">${escapeHtml(text)}</h${h[1].length}>`
      continue
    }
    if (/^[-*]\s+/.test(line)) {
      html += `<ul class="list-disc pl-6 my-3"><li>${escapeHtml(line.replace(/^[-*]\s+/, ''))}</li></ul>`
      continue
    }
    if (line === '') continue
    html += `<p class="text-gray-600 leading-relaxed mb-4">${escapeHtml(line)}</p>`
  }
  if (inCode) html += `<pre class="bg-gray-900 text-gray-100 p-4 rounded-lg overflow-x-auto text-sm"><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`
  return html.replace(/<\/ul><ul>/g, '')
}

const loadChapter = async (id: number) => {
  try {
    const c = await learningApi.chapterDetail(id)
    currentChapter.value = c
    if (c.pathId) {
      const [chapters, detail] = await Promise.all([
        learningApi.chapters(c.pathId),
        learningApi.pathDetail(c.pathId).catch(() => null),
      ])
      pathChapters.value = chapters
      pathDetail.value = detail
    }
  } catch {
    /* 忽略 */
  }
}

onMounted(() => loadChapter(currentChapterId.value))
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
