<template>
  <div class="space-y-8 animate-fade-in">
    <section class="relative overflow-hidden rounded-xl bg-gradient-to-br from-primary-500 via-primary-600 to-primary-700 p-8 text-white">
      <div class="absolute inset-0 bg-grid-white/10 [mask-image:linear-gradient(0deg,transparent,white)]" />
      <div class="absolute top-0 right-0 w-96 h-96 bg-white/10 rounded-full -translate-y-1/2 translate-x-1/2 blur-3xl" />
      <div class="absolute bottom-0 left-0 w-64 h-64 bg-white/10 rounded-full translate-y-1/2 -translate-x-1/2 blur-2xl" />
      <div class="relative z-10">
        <h1 class="text-3xl font-bold mb-2">{{ greeting }}，张三 👋</h1>
        <p class="text-primary-100 mb-6">今天也要继续学习哦~</p>
        <div class="bg-white/15 backdrop-blur-sm rounded-lg p-4 max-w-xl">
          <div class="flex items-start gap-3">
            <Icon name="quote" :size="20" class="text-primary-200 flex-shrink-0 mt-0.5" />
            <div>
              <p class="text-white/90 leading-relaxed">{{ dailyQuote.content }}</p>
              <p class="text-primary-200 text-sm mt-2">—— {{ dailyQuote.author }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section>
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-semibold text-gray-800">快速访问</h2>
        <router-link to="/categories" class="text-sm text-primary-500 hover:text-primary-600 transition-colors">
          全部分类
        </router-link>
      </div>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div
          v-for="(category, index) in categories"
          :key="category.id"
          class="group cursor-pointer"
          :style="{ animationDelay: `${index * 50}ms` }"
          @click="goToCategory(category.id)"
        >
          <Card hoverable class="h-full">
            <div class="flex flex-col items-center text-center">
              <div
                :class="[
                  'w-12 h-12 rounded-xl flex items-center justify-center mb-3 transition-all duration-300 group-hover:scale-110',
                  categoryColors[index % categoryColors.length].bg,
                ]"
              >
                <Icon
                  :name="getCategoryIconName(category.icon || 'code')"
                  :size="24"
                  :class="categoryColors[index % categoryColors.length].text"
                />
              </div>
              <h3 class="font-medium text-gray-800 mb-1">{{ category.name }}</h3>
              <p class="text-xs text-gray-500">{{ category.docCount }} 篇文档</p>
            </div>
          </Card>
        </div>
      </div>
    </section>

    <section>
      <h2 class="text-lg font-semibold text-gray-800 mb-4">学习概览</h2>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card hoverable>
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">学习时长</p>
              <p class="text-2xl font-bold text-gray-800 mt-1">{{ learningOverview.studyHours }}h</p>
              <p class="text-primary-500 text-xs mt-2">本周 +12h</p>
            </div>
            <div class="w-12 h-12 rounded-lg bg-primary-50 flex items-center justify-center">
              <Icon name="clock" :size="24" class="text-primary-500" />
            </div>
          </div>
        </Card>

        <Card hoverable>
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">已读文档</p>
              <p class="text-2xl font-bold text-gray-800 mt-1">{{ learningOverview.readDocs }} 篇</p>
              <p class="text-success-500 text-xs mt-2">本周 +5</p>
            </div>
            <div class="w-12 h-12 rounded-lg bg-success-50 flex items-center justify-center">
              <Icon name="file-text" :size="24" class="text-success-500" />
            </div>
          </div>
        </Card>

        <Card hoverable>
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">连续学习</p>
              <p class="text-2xl font-bold text-gray-800 mt-1">{{ learningOverview.streakDays }} 天</p>
              <p class="text-warning-500 text-xs mt-2">继续加油！</p>
            </div>
            <div class="w-12 h-12 rounded-lg bg-warning-50 flex items-center justify-center">
              <Icon name="flame" :size="24" class="text-warning-500" />
            </div>
          </div>
        </Card>

        <Card hoverable>
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">收藏数</p>
              <p class="text-2xl font-bold text-gray-800 mt-1">{{ learningOverview.favorites }} 篇</p>
              <p class="text-danger-500 text-xs mt-2">我的收藏</p>
            </div>
            <div class="w-12 h-12 rounded-lg bg-danger-50 flex items-center justify-center">
              <Icon name="heart" :size="24" class="text-danger-500" />
            </div>
          </div>
        </Card>
      </div>
    </section>

    <section>
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-semibold text-gray-800">最近浏览</h2>
        <router-link to="/categories" class="text-sm text-primary-500 hover:text-primary-600 transition-colors">
          查看更多
        </router-link>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <Card
          v-for="doc in recentDocs.slice(0, 6)"
          :key="doc.id"
          hoverable
          class="cursor-pointer"
          @click="goToDoc(doc.id)"
        >
          <div class="flex flex-col h-full">
            <div class="flex items-center gap-2 mb-3">
              <Badge variant="primary">{{ doc.categoryName }}</Badge>
            </div>
            <h3 class="font-medium text-gray-800 mb-2 line-clamp-2">{{ doc.title }}</h3>
            <p class="text-sm text-gray-500 mb-4 line-clamp-2 flex-1">{{ doc.summary }}</p>
            <div class="mb-3">
              <Progress :percentage="doc.readProgress" variant="primary" label="阅读进度" show-label />
            </div>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Avatar :name="doc.author" size="sm" />
                <span class="text-xs text-gray-500">{{ doc.author }}</span>
              </div>
              <span class="text-xs text-gray-400">{{ formatLastRead(doc.lastReadAt) }}</span>
            </div>
          </div>
        </Card>
      </div>
    </section>

    <section>
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-semibold text-gray-800">推荐阅读</h2>
        <a href="#" class="text-sm text-primary-500 hover:text-primary-600 transition-colors">换一批</a>
      </div>
      <div class="space-y-3">
        <Card
          v-for="(doc, index) in recommendedDocs"
          :key="doc.id"
          hoverable
          class="cursor-pointer"
          @click="goToDoc(doc.id)"
        >
          <div class="flex items-start gap-4">
            <div class="w-8 h-8 rounded-lg bg-gray-100 flex items-center justify-center flex-shrink-0 mt-0.5">
              <span class="text-sm font-medium text-gray-500">{{ index + 1 }}</span>
            </div>
            <div class="flex-1 min-w-0">
              <h3 class="font-medium text-gray-800 mb-1">{{ doc.title }}</h3>
              <p class="text-sm text-gray-500 line-clamp-1 mb-2">{{ doc.summary }}</p>
              <div class="flex items-center gap-3 flex-wrap">
                <Badge variant="primary">{{ doc.categoryName }}</Badge>
                <div class="flex items-center gap-1">
                  <span
                    v-for="tag in doc.tags.slice(0, 2)"
                    :key="tag"
                    class="text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded"
                  >
                    {{ tag }}
                  </span>
                </div>
                <div class="flex items-center gap-1 text-xs text-gray-400 ml-auto">
                  <Icon name="clock" :size="14" />
                  <span>{{ doc.readTime }} 分钟阅读</span>
                </div>
              </div>
            </div>
          </div>
        </Card>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Progress from '@/components/ui/Progress.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { categories } from '@/data/categories'
import { dailyQuotes, learningOverview, recentDocs, recommendedDocs } from '@/data/home'

const router = useRouter()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const dailyQuote = computed(() => {
  const dayOfYear = Math.floor(
    (Date.now() - new Date(new Date().getFullYear(), 0, 0).getTime()) / 86400000
  )
  return dailyQuotes[dayOfYear % dailyQuotes.length]
})

const categoryColors = [
  { bg: 'bg-primary-50', text: 'text-primary-500' },
  { bg: 'bg-success-50', text: 'text-success-500' },
  { bg: 'bg-warning-50', text: 'text-warning-500' },
  { bg: 'bg-danger-50', text: 'text-danger-500' },
]

const getCategoryIconName = (iconName: string): string => {
  const validIcons = ['code', 'server', 'database', 'brain', 'settings', 'git-branch', 'monitor', 'wifi']
  return validIcons.includes(iconName) ? iconName : 'code'
}

const goToCategory = (categoryId: string) => {
  router.push(`/categories?categoryId=${categoryId}`)
}

const goToDoc = (docId: string) => {
  router.push(`/doc/${docId}`)
}

const formatLastRead = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffHours < 1) return '刚刚'
  if (diffHours < 24) return `${diffHours} 小时前`
  if (diffDays < 7) return `${diffDays} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
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

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bg-grid-white\/10 {
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3e%3cg fill='none' fill-rule='evenodd'%3e%3cg fill='%23ffffff' fill-opacity='0.1'%3e%3cpath d='M0 0h32v32H0z'/%3e%3c/g%3e%3c/g%3e%3c/svg%3e");
}
</style>
