<template>
  <div class="animate-fade-in space-y-8">
    <section class="rounded-xl overflow-hidden" style="background: linear-gradient(135deg, #3B6FE0 0%, rgba(59,111,224,0.8) 100%)">
      <div class="relative p-6 lg:p-8">
        <div class="absolute left-0 top-0 bottom-0 w-1 bg-white/30"></div>
        <div class="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-6 pl-2">
          <div class="min-w-0">
            <div class="flex items-center gap-2 mb-3">
              <Icon name="quote" :size="20" class="text-white/60" />
              <span class="text-[12px] font-medium text-white/70 tracking-wide">每日一句</span>
            </div>
            <p class="text-lg lg:text-xl font-medium text-white leading-relaxed" style="text-wrap: balance">
              {{ currentQuote.text }}
            </p>
            <p class="mt-2 text-[13px] text-white/70">
              —— {{ currentQuote.author }}
            </p>
          </div>
          <div class="flex gap-3 shrink-0">
            <router-link
              to="/docs"
              class="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg text-[14px] font-medium bg-white text-primary-600 hover:opacity-90 transition-opacity"
            >
              <Icon name="book-open" :size="16" />
              浏览全部
            </router-link>
            <router-link
              to="/categories"
              class="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg text-[14px] font-medium bg-white/20 text-white border border-white/30 hover:bg-white/30 transition-colors"
            >
              <Icon name="folder-tree" :size="16" />
              分类目录
            </router-link>
          </div>
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold text-gray-800">知识分类</h3>
        <router-link to="/categories" class="text-sm text-primary-500 hover:text-primary-600 transition-colors flex items-center gap-1">
          查看全部
          <Icon name="chevron-right" :size="14" />
        </router-link>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-4">
        <div
          v-for="category in topCategories" :key="category.id"
          class="bg-white border border-[#E2E6EC] rounded-lg p-4 cursor-pointer hover:border-primary-500/30 hover:shadow-md transition-all duration-200 group"
          @click="goToCategory(category.id)"
        >
          <div
            class="w-10 h-10 rounded-lg flex items-center justify-center mb-3"
            :style="{ background: category.color + '20' }"
          >
            <Icon :name="getCategoryIcon(category.icon)" :size="20" :style="{ color: category.color }" />
          </div>
          <h4 class="font-medium text-gray-800 mb-1 group-hover:text-primary-500 transition-colors">{{ category.name }}</h4>
          <p class="text-xs text-gray-400">{{ category.docCount || 0 }} 篇文档</p>
        </div>
      </div>
    </section>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <section class="lg:col-span-2">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-gray-800">精选推荐</h3>
          <router-link to="/docs" class="text-sm text-primary-500 hover:text-primary-600 transition-colors flex items-center gap-1">
            更多
            <Icon name="chevron-right" :size="14" />
          </router-link>
        </div>
        <div class="space-y-3">
          <div
            v-for="doc in featuredDocs" :key="doc.id"
            class="bg-white border border-[#E2E6EC] rounded-lg p-4 cursor-pointer hover:border-primary-500/30 hover:shadow-md transition-all duration-200 group"
            @click="goToDoc(doc.id)"
          >
            <div class="flex items-start gap-4">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-2">
                  <span class="inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium" style="background:rgba(59,111,224,0.1);color:#3B6FE0">
                    {{ doc.categoryName || '未分类' }}
                  </span>
                  <span
                    v-for="tag in tagList(doc.tags).slice(0, 2)" :key="tag"
                    class="inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-medium bg-gray-100 text-gray-500"
                  >
                    {{ tag }}
                  </span>
                </div>
                <h4 class="font-medium text-gray-800 mb-1 group-hover:text-primary-500 transition-colors">
                  {{ doc.title }}
                </h4>
                <p class="text-sm text-gray-500 line-clamp-1">
                  {{ doc.summary || '暂无摘要' }}
                </p>
              </div>
              <div class="flex flex-col items-end gap-2 shrink-0">
                <span class="text-xs text-gray-400">{{ formatDate(doc.createTime) }}</span>
                <div class="flex items-center gap-3 text-xs text-gray-400">
                  <span class="flex items-center gap-1">
                    <Icon name="eye" :size="14" />
                    {{ doc.viewCount || 0 }}
                  </span>
                  <span class="flex items-center gap-1">
                    <Icon name="heart" :size="14" />
                    {{ doc.favoriteCount || 0 }}
                  </span>
                </div>
              </div>
            </div>
          </div>
          <p v-if="featuredDocs.length === 0 && !loading" class="text-center py-12 text-gray-400">
            暂无推荐文档
          </p>
        </div>
      </section>

      <section class="space-y-6">
        <div class="bg-white border border-[#E2E6EC] rounded-lg p-5">
          <div class="flex items-center justify-between mb-4">
            <h3 class="font-semibold text-gray-800">热门标签</h3>
          </div>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="tag in hotTags" :key="tag.name"
              class="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-[12px] cursor-pointer transition-colors hover:opacity-80"
              :style="{ background: tag.color + '15', color: tag.color }"
            >
              <Icon name="tag" :size="12" />
              {{ tag.name }}
            </span>
          </div>
        </div>

        <div class="bg-white border border-[#E2E6EC] rounded-lg p-5">
          <div class="flex items-center justify-between mb-4">
            <h3 class="font-semibold text-gray-800">学习统计</h3>
          </div>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="book-open" :size="16" class="text-primary-500" />
                <span class="text-sm text-gray-600">已阅读文档</span>
              </div>
              <span class="text-lg font-bold text-gray-800">{{ stats.readCount }}</span>
            </div>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="clock" :size="16" class="text-warning-500" />
                <span class="text-sm text-gray-600">学习时长</span>
              </div>
              <span class="text-lg font-bold text-gray-800">{{ stats.studyHours }} 小时</span>
            </div>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="flame" :size="16" class="text-danger-500" />
                <span class="text-sm text-gray-600">连续学习</span>
              </div>
              <span class="text-lg font-bold text-gray-800">{{ stats.streakDays }} 天</span>
            </div>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="star" :size="16" class="text-warning-500" />
                <span class="text-sm text-gray-600">收藏文档</span>
              </div>
              <span class="text-lg font-bold text-gray-800">{{ stats.favoriteCount }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
// 知识库首页（分类导航）：每日金句、分类入口、精选推荐、热门标签与学习统计。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'

const router = useRouter()

const loading = ref(false)
const topCategories = ref<(CategoryVO & { color: string })[]>([])
const featuredDocs = ref<DocVO[]>([])

const famousQuotes = [
  { text: '学而不思则罔，思而不学则殆。', author: '孔子' },
  { text: '知之者不如好之者，好之者不如乐之者。', author: '孔子' },
  { text: '吾生也有涯，而知也无涯。', author: '庄子' },
  { text: '读书破万卷，下笔如有神。', author: '杜甫' },
  { text: '问渠那得清如许，为有源头活水来。', author: '朱熹' },
  { text: '业精于勤，荒于嬉；行成于思，毁于随。', author: '韩愈' },
  { text: '少壮不努力，老大徒伤悲。', author: '汉乐府' },
  { text: '黑发不知勤学早，白首方悔读书迟。', author: '颜真卿' },
  { text: '纸上得来终觉浅，绝知此事要躬行。', author: '陆游' },
  { text: '古人学问无遗力，少壮工夫老始成。', author: '陆游' },
  { text: '路漫漫其修远兮，吾将上下而求索。', author: '屈原' },
  { text: '不积跬步，无以至千里；不积小流，无以成江海。', author: '荀子' },
  { text: '锲而舍之，朽木不折；锲而不舍，金石可镂。', author: '荀子' },
  { text: '敏而好学，不耻下问。', author: '孔子' },
  { text: '三人行，必有我师焉。', author: '孔子' },
  { text: '温故而知新，可以为师矣。', author: '孔子' },
  { text: '知之为知之，不知为不知，是知也。', author: '孔子' },
  { text: '学而时习之，不亦说乎。', author: '孔子' },
  { text: '宝剑锋从磨砺出，梅花香自苦寒来。', author: '警世贤文' },
  { text: '书山有路勤为径，学海无涯苦作舟。', author: '韩愈' },
]

// 随机选取一条每日金句
const quoteIndex = ref(Math.floor(Math.random() * famousQuotes.length))

const currentQuote = computed(() => famousQuotes[quoteIndex.value])

const iconColors: Record<string, string> = {
  code: '#3B6FE0',
  server: '#10B981',
  database: '#F59E0B',
  brain: '#8B5CF6',
  settings: '#06B6D4',
  monitor: '#EC4899',
  wifi: '#84CC16',
  layers: '#F97316',
  'book-open': '#3B6FE0',
  folder: '#6B7280',
  shield: '#EF4444',
  'git-branch': '#F97316',
  'message-square': '#0EA5E9',
  target: '#14B8A6',
  'bar-chart-2': '#F59E0B',
  palette: '#EC4899',
  briefcase: '#64748B',
  cpu: '#8B5CF6',
  bot: '#06B6D4',
  lock: '#EF4444',
}

const getCategoryIcon = (iconName?: string): string => {
  const valid = [
    'code', 'server', 'database', 'brain', 'settings', 'monitor', 'wifi', 'layers',
    'book-open', 'folder', 'shield', 'git-branch', 'message-square', 'target',
    'bar-chart-2', 'palette', 'briefcase', 'cpu', 'bot', 'lock',
  ]
  return valid.includes(iconName || '') ? iconName! : 'folder'
}

const getCategoryColor = (iconName?: string): string => {
  return iconColors[iconName || ''] || '#6B7280'
}

const hotTags = [
  { name: 'Vue 3', color: '#3B6FE0' },
  { name: 'React', color: '#10B981' },
  { name: 'TypeScript', color: '#3178C6' },
  { name: 'Python', color: '#FFD43B' },
  { name: '算法', color: '#F59E0B' },
  { name: '设计模式', color: '#8B5CF6' },
  { name: 'MySQL', color: '#4479A1' },
  { name: 'Docker', color: '#2496ED' },
]

const stats = ref({
  readCount: 42,
  studyHours: 68,
  streakDays: 7,
  favoriteCount: 15,
})

const tagList = (tags?: string): string[] => {
  if (!tags) return []
  return tags.split(',').filter(Boolean)
}

const goToCategory = (id: number) => {
  router.push({ path: '/categories', query: { categoryId: String(id) } })
}

const goToDoc = (id: number) => {
  router.push(`/doc/${id}`)
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  })
}

const loadData = async () => {
  loading.value = true
  try {
    const [cats, docs] = await Promise.all([
      categoriesApi.tree(),
      docsApi.list({ pageSize: 6, sortBy: 'hot' } as any).then((r) => r.records || []),
    ])
    topCategories.value = cats.slice(0, 6).map((c) => ({
      ...c,
      color: getCategoryColor(c.icon),
    }))
    featuredDocs.value = docs.slice(0, 5)
  } catch {
    topCategories.value = []
    featuredDocs.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
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

.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
