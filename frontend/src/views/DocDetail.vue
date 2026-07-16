<template>
  <div class="animate-fade-in">
    <div class="flex items-center gap-4 mb-6 pb-4 border-b border-gray-100">
      <button
        class="w-9 h-9 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 transition-colors"
        @click="goBack"
      >
        <Icon name="arrow-left" :size="16" />
      </button>
      <div class="flex items-center gap-2 text-sm text-gray-500">
        <router-link to="/" class="hover:text-primary-500 transition-colors">首页</router-link>
        <Icon name="chevron-right" :size="16" />
        <router-link
          :to="`/categories?categoryId=${doc.categoryId}`"
          class="hover:text-primary-500 transition-colors"
        >
          {{ doc.categoryName }}
        </router-link>
        <Icon name="chevron-right" :size="16" />
        <span class="text-gray-700 truncate max-w-xs">{{ doc.title }}</span>
      </div>
      <div class="ml-auto flex items-center gap-2">
        <button
          :class="[
            'w-9 h-9 rounded-lg border flex items-center justify-center transition-all duration-200',
            isCollected
              ? 'border-danger-200 bg-danger-50 text-danger-500'
              : 'border-gray-200 hover:bg-gray-50 text-gray-600',
          ]"
          @click="toggleCollect"
        >
          <Icon name="heart" :size="16" />
        </button>
        <button
          class="w-9 h-9 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 transition-colors text-gray-600"
          @click="handleShare"
        >
          <Icon name="share-2" :size="16" />
        </button>
      </div>
    </div>

    <div class="flex gap-8">
      <article class="flex-1 min-w-0">
        <Card padding="lg">
          <header class="mb-8 pb-6 border-b border-gray-100">
            <h1 class="text-2xl font-bold text-gray-800 mb-4">{{ doc.title }}</h1>
            <div class="flex items-center gap-4 flex-wrap">
              <div class="flex items-center gap-2">
                <Avatar :name="doc.author" size="sm" />
                <span class="text-sm text-gray-600">{{ doc.author }}</span>
              </div>
              <span class="text-sm text-gray-400">{{ formatDate(doc.createdAt) }}</span>
              <div class="flex items-center gap-1 text-sm text-gray-400">
                <Icon name="eye" :size="16" />
                <span>{{ doc.viewCount }}</span>
              </div>
              <div class="flex items-center gap-1 text-sm text-gray-400">
                <Icon name="thumbs-up" :size="16" />
                <span>{{ doc.likeCount }}</span>
              </div>
            </div>
            <div class="flex items-center gap-2 mt-4 flex-wrap">
              <Badge variant="primary">{{ doc.categoryName }}</Badge>
              <Badge v-for="tag in doc.tags" :key="tag" variant="default">
                {{ tag }}
              </Badge>
            </div>
          </header>

          <div
            ref="contentRef"
            class="prose prose-gray max-w-none doc-content"
            v-html="doc.content"
          ></div>
        </Card>
      </article>

      <aside class="hidden lg:block w-72 flex-shrink-0">
        <div class="sticky top-6 space-y-4">
          <Card>
            <template #header>
              <h3 class="font-medium text-gray-800 flex items-center gap-2">
                <Icon name="list" :size="16" />
                目录
              </h3>
            </template>
            <nav class="space-y-1 max-h-80 overflow-y-auto">
              <template v-for="item in flatToc" :key="item.id">
                <a
                  :href="`#${item.id}`"
                  :class="[
                    'block text-sm transition-all duration-200 rounded-md',
                    item.level === 2 ? 'py-1.5 px-3' : 'py-1 px-3 pl-6',
                    activeTocId === item.id
                      ? 'text-primary-600 bg-primary-50 font-medium'
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-50',
                  ]"
                  @click="scrollToSection(item.id, $event)"
                >
                  {{ item.text }}
                </a>
              </template>
            </nav>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-medium text-gray-800 flex items-center gap-2">
                <Icon name="info" :size="16" />
                文档信息
              </h3>
            </template>
            <div class="space-y-3">
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-500 flex items-center gap-2">
                  <Icon name="file-text" :size="16" />
                  字数
                </span>
                <span class="text-gray-700 font-medium">{{ doc.wordCount?.toLocaleString() }} 字</span>
              </div>
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-500 flex items-center gap-2">
                  <Icon name="clock" :size="16" />
                  阅读时长
                </span>
                <span class="text-gray-700 font-medium">{{ doc.readTime }} 分钟</span>
              </div>
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-500 flex items-center gap-2">
                  <Icon name="calendar" :size="16" />
                  创建时间
                </span>
                <span class="text-gray-700 font-medium">{{ formatDate(doc.createdAt) }}</span>
              </div>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-medium text-gray-800 flex items-center gap-2">
                <Icon name="bar-chart-2" :size="16" />
                阅读进度
              </h3>
            </template>
            <div class="space-y-3">
              <Progress :percentage="readProgress" variant="primary" label="已读" show-label />
              <p class="text-xs text-gray-500">
                已阅读 {{ Math.floor((doc.wordCount || 0) * readProgress / 100).toLocaleString() }} 字
              </p>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-medium text-gray-800 flex items-center gap-2">
                <Icon name="lightbulb" :size="16" />
                相关推荐
              </h3>
            </template>
            <div class="space-y-3">
              <div
                v-for="item in relatedDocs"
                :key="item.id"
                class="cursor-pointer group"
                @click="goToDoc(item.id)"
              >
                <h4 class="text-sm font-medium text-gray-700 group-hover:text-primary-500 transition-colors line-clamp-2 mb-1">
                  {{ item.title }}
                </h4>
                <div class="flex items-center gap-2 text-xs text-gray-400">
                  <Badge variant="primary" class="text-xs">{{ item.categoryName }}</Badge>
                  <span>{{ item.viewCount }} 阅读</span>
                </div>
              </div>
            </div>
          </Card>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Progress from '@/components/ui/Progress.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { detailedDoc, docToc, relatedDocs } from '@/data/docDetail'
import type { TocItem } from '@/types'

const route = useRoute()
const router = useRouter()

const doc = ref(detailedDoc)
const isCollected = ref(false)
const contentRef = ref<HTMLElement | null>(null)
const activeTocId = ref('')
const readProgress = ref(45)

const flatToc = computed(() => {
  const items: TocItem[] = []
  const flatten = (tocItems: TocItem[]) => {
    tocItems.forEach((item) => {
      items.push(item)
      if (item.children && item.children.length > 0) {
        flatten(item.children)
      }
    })
  }
  flatten(docToc)
  return items
})

const goBack = () => {
  router.back()
}

const toggleCollect = () => {
  isCollected.value = !isCollected.value
}

const handleShare = () => {
  alert('分享功能开发中')
}

const goToDoc = (docId: string) => {
  router.push(`/doc/${docId}`)
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

const scrollToSection = (id: string, event: Event) => {
  event.preventDefault()
  const element = document.getElementById(id)
  if (element) {
    element.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const handleScroll = () => {
  if (!contentRef.value) return

  const headings = contentRef.value.querySelectorAll('h2[id], h3[id]')
  let currentId = ''

  headings.forEach((heading) => {
    const rect = heading.getBoundingClientRect()
    if (rect.top <= 100) {
      currentId = heading.id
    }
  })

  if (currentId && currentId !== activeTocId.value) {
    activeTocId.value = currentId
  }

  const scrollTop = window.scrollY
  const docHeight = document.documentElement.scrollHeight - window.innerHeight
  if (docHeight > 0) {
    readProgress.value = Math.min(100, Math.round((scrollTop / docHeight) * 100))
  }
}

onMounted(() => {
  const docId = route.params.id as string
  if (docId) {
    console.log('加载文档:', docId)
  }

  window.addEventListener('scroll', handleScroll)
  handleScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
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

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

:deep(.doc-content) {
  color: #374151;
  line-height: 1.8;
  font-size: 15px;
}

:deep(.doc-content h2) {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1f2937;
  margin-top: 2rem;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #e5e7eb;
  scroll-margin-top: 80px;
}

:deep(.doc-content h3) {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
  scroll-margin-top: 80px;
}

:deep(.doc-content p) {
  margin-bottom: 1rem;
}

:deep(.doc-content ul) {
  list-style-type: disc;
  padding-left: 1.5rem;
  margin-bottom: 1rem;
}

:deep(.doc-content li) {
  margin-bottom: 0.5rem;
}

:deep(.doc-content blockquote) {
  border-left: 4px solid #3b6fe0;
  background: #eff4fe;
  padding: 1rem 1.25rem;
  margin: 1rem 0;
  border-radius: 0 8px 8px 0;
  color: #4b5563;
}

:deep(.doc-content blockquote p) {
  margin-bottom: 0;
}

:deep(.doc-content pre) {
  background: #1f2937;
  color: #e5e7eb;
  padding: 1rem 1.25rem;
  border-radius: 8px;
  overflow-x: auto;
  margin: 1rem 0;
  font-size: 14px;
  line-height: 1.6;
}

:deep(.doc-content code) {
  background: #f3f4f6;
  color: #dc2626;
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  font-size: 0.875em;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

:deep(.doc-content pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: inherit;
}

:deep(.doc-content table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1rem 0;
  font-size: 14px;
}

:deep(.doc-content th) {
  background: #f9fafb;
  font-weight: 600;
  text-align: left;
  padding: 0.75rem 1rem;
  border-bottom: 2px solid #e5e7eb;
}

:deep(.doc-content td) {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e5e7eb;
}

:deep(.doc-content tr:hover td) {
  background: #f9fafb;
}
</style>
