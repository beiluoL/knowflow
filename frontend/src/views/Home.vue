<template>
  <div class="animate-fade-in">
    <!-- 页面欢迎头 -->
    <header class="flex items-center justify-between gap-4 mb-8">
      <div class="min-w-0">
        <h1 class="text-[28px] font-bold tracking-tight text-gray-900" style="text-wrap: balance">
          欢迎回来，{{ userName }}
        </h1>
        <p class="mt-1 text-[12px] text-gray-500">今天想学些什么？从下方选择一个模块开始吧。</p>
      </div>
    </header>

    <!-- Hero Banner -->
    <section
      class="rounded-xl p-6 lg:p-8 mb-8"
      style="background: linear-gradient(135deg, #3B6FE0 0%, rgba(59,111,224,0.75) 100%)"
    >
      <div class="flex items-center justify-between gap-6 flex-wrap">
        <div class="min-w-0">
          <h2 class="text-[22px] font-semibold text-white" style="text-wrap: balance">
            一站式知识学习平台
          </h2>
          <p class="mt-2 text-[14px] leading-relaxed text-white/85">
            整合知识管理、智能学习、AI 辅助于一体，助你高效构建个人知识体系。
          </p>
        </div>
        <router-link
          to="/categories"
          class="shrink-0 inline-flex items-center gap-2 px-5 py-2.5 rounded-lg text-[14px] font-medium bg-white text-primary-600 hover:opacity-90 transition-opacity"
        >
          开始探索
          <Icon name="arrow-right" :size="16" />
        </router-link>
      </div>
    </section>

    <!-- Quick Stats -->
    <section class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
      <div
        v-for="stat in stats"
        :key="stat.label"
        class="bg-white border border-[#E2E6EC] rounded-lg p-4"
      >
        <div class="flex items-center gap-3">
          <div
            class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
            :style="{ background: stat.iconBg }"
          >
            <Icon :name="stat.icon" :size="18" :class="stat.iconColor" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-[20px] font-bold whitespace-nowrap text-gray-900">{{ stat.value }}</p>
            <p class="text-[12px] text-gray-500 truncate">{{ stat.label }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Feature Module Cards -->
    <section class="mb-8">
      <h3 class="text-[18px] font-semibold mb-4 text-gray-900">知识探索</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        <router-link
          v-for="mod in knowledgeModules"
          :key="mod.title"
          :to="mod.path"
          class="bg-white border border-[#E2E6EC] rounded-xl p-5 flex items-start gap-4 hover:shadow-md hover:border-primary-300 transition-all group cursor-pointer"
        >
          <div
            class="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
            :style="{ background: mod.iconBg }"
          >
            <Icon :name="mod.icon" :size="20" :class="mod.iconColor" />
          </div>
          <div class="flex-1 min-w-0">
            <h4 class="text-[15px] font-semibold text-gray-900">{{ mod.title }}</h4>
            <p class="mt-1 text-[12px] text-gray-500 line-clamp-2">{{ mod.desc }}</p>
          </div>
          <Icon
            name="chevron-right"
            :size="16"
            class="shrink-0 mt-1 text-gray-300 group-hover:text-primary-600 transition-colors"
          />
        </router-link>
      </div>

      <h3 class="text-[18px] font-semibold mb-4 text-gray-900">学习工具</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        <router-link
          v-for="mod in learningModules"
          :key="mod.title"
          :to="mod.path"
          class="bg-white border border-[#E2E6EC] rounded-xl p-5 flex items-start gap-4 hover:shadow-md hover:border-primary-300 transition-all group cursor-pointer"
        >
          <div
            class="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
            :style="{ background: mod.iconBg }"
          >
            <Icon :name="mod.icon" :size="20" :class="mod.iconColor" />
          </div>
          <div class="flex-1 min-w-0">
            <h4 class="text-[15px] font-semibold text-gray-900">{{ mod.title }}</h4>
            <p class="mt-1 text-[12px] text-gray-500 line-clamp-2">{{ mod.desc }}</p>
          </div>
          <Icon
            name="chevron-right"
            :size="16"
            class="shrink-0 mt-1 text-gray-300 group-hover:text-primary-600 transition-colors"
          />
        </router-link>
      </div>

      <h3 class="text-[18px] font-semibold mb-4 text-gray-900">创作 & 工具</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <router-link
          v-for="mod in toolModules"
          :key="mod.title"
          :to="mod.path"
          class="bg-white border border-[#E2E6EC] rounded-xl p-5 flex items-start gap-4 hover:shadow-md hover:border-primary-300 transition-all group cursor-pointer"
        >
          <div
            class="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
            :style="{ background: mod.iconBg }"
          >
            <Icon :name="mod.icon" :size="20" :class="mod.iconColor" />
          </div>
          <div class="flex-1 min-w-0">
            <h4 class="text-[15px] font-semibold text-gray-900">{{ mod.title }}</h4>
            <p class="mt-1 text-[12px] text-gray-500 line-clamp-2">{{ mod.desc }}</p>
          </div>
          <Icon
            name="chevron-right"
            :size="16"
            class="shrink-0 mt-1 text-gray-300 group-hover:text-primary-600 transition-colors"
          />
        </router-link>
      </div>
    </section>

    <!-- Two-column: Recent + Recommendations -->
    <section class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <!-- 最近学习 -->
      <div class="bg-white border border-[#E2E6EC] rounded-lg p-5">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-[18px] font-semibold text-gray-900">最近学习</h3>
          <router-link to="/learning/center" class="text-[13px] text-primary-600 hover:underline whitespace-nowrap">
            查看全部
          </router-link>
        </div>
        <div class="space-y-1">
          <div
            v-for="item in recentItems"
            :key="item.id"
            class="flex items-center gap-3 p-3 rounded-lg hover:bg-[#E8ECF1] transition-colors cursor-pointer"
            @click="goToDoc(item.id)"
          >
            <div
              class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0"
              :style="{ background: item.iconBg }"
            >
              <Icon :name="item.icon" :size="16" :class="item.iconColor" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-[13px] font-medium truncate text-gray-900">{{ item.title }}</p>
              <p class="text-[12px] text-gray-500 truncate">{{ item.time }}</p>
            </div>
          </div>
          <div v-if="recentItems.length === 0" class="px-3 py-8 text-center text-sm text-gray-400">
            暂无学习记录，去 <router-link to="/categories" class="text-primary-600 hover:underline">知识库</router-link> 看看吧
          </div>
        </div>
      </div>

      <!-- 热门推荐 -->
      <div class="bg-white border border-[#E2E6EC] rounded-lg p-5">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-[18px] font-semibold text-gray-900">热门推荐</h3>
          <router-link to="/categories" class="text-[13px] text-primary-600 hover:underline whitespace-nowrap">
            更多推荐
          </router-link>
        </div>
        <div class="space-y-1">
          <router-link
            v-for="doc in recommendedDocs"
            :key="doc.id"
            :to="`/doc/${doc.id}`"
            class="flex items-center gap-3 p-3 rounded-lg hover:bg-[#E8ECF1] transition-colors group"
          >
            <div
              class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0"
              :style="{ background: recommendIconBg(doc) }"
            >
              <Icon name="file-text" :size="16" :class="recommendIconColor(doc)" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-[13px] font-medium truncate text-gray-900">{{ doc.title }}</p>
              <p class="text-[12px] text-gray-500 truncate">{{ doc.categoryName || '知识库' }} / {{ doc.difficulty || '入门' }}</p>
            </div>
            <Icon name="chevron-right" :size="16" class="shrink-0 text-gray-400" />
          </router-link>
          <div v-if="recommendedDocs.length === 0" class="px-3 py-8 text-center text-sm text-gray-400">
            暂无推荐内容
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi, docsApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { DocVO } from '@/api/types'

const router = useRouter()
const auth = useAuthStore()

const categoryCount = ref(0)
const docCount = ref(0)
const recentDocs = ref<DocVO[]>([])
const recommendedDocs = ref<DocVO[]>([])
const userStats = ref({ studyHours: 0, readDocs: 0, streakDays: 0, favorites: 0, progress: 0 })

const userName = computed(() => auth.user?.nickname || auth.user?.username || '探索者')

const stats = computed(() => [
  { label: '知识库总数', value: categoryCount.value || 0, icon: 'database', iconBg: 'rgba(59,111,224,0.1)', iconColor: 'text-primary-600' },
  { label: '文档数量', value: docCount.value || 0, icon: 'file-text', iconBg: 'rgba(16,185,129,0.1)', iconColor: 'text-success-500' },
  { label: '学习进度', value: `${userStats.value.progress}%`, icon: 'trending-up', iconBg: 'rgba(245,158,11,0.1)', iconColor: 'text-warning-500' },
  { label: '学习天数', value: userStats.value.streakDays, icon: 'flame', iconBg: 'rgba(59,111,224,0.1)', iconColor: 'text-primary-600' },
])

const knowledgeModules = [
  { title: '知识库浏览', desc: '浏览和探索所有知识库，按分类快速查找文档内容。', path: '/categories', icon: 'book-open', iconBg: 'rgba(59,111,224,0.12)', iconColor: 'text-primary-600' },
  { title: '全部文档', desc: '浏览平台所有文档，支持筛选和排序快速定位。', path: '/docs', icon: 'files', iconBg: 'rgba(16,185,129,0.12)', iconColor: 'text-success-500' },
  { title: '分类浏览', desc: '按知识分类体系浏览，结构化查找所需内容。', path: '/categories', icon: 'folder-tree', iconBg: 'rgba(245,158,11,0.12)', iconColor: 'text-warning-500' },
  { title: '搜索中心', desc: '全文搜索知识库、文档、笔记，精准定位内容。', path: '/search', icon: 'search', iconBg: 'rgba(124,58,237,0.12)', iconColor: 'text-purple-600' },
  { title: '我的收藏', desc: '收藏的文档统一管理，随时回顾重要内容。', path: '/favorites', icon: 'heart', iconBg: 'rgba(249,115,22,0.12)', iconColor: 'text-orange-500' },
  { title: '社区讨论', desc: '与学习者交流讨论，分享见解和学习心得。', path: '/community', icon: 'message-square', iconBg: 'rgba(20,184,166,0.12)', iconColor: 'text-teal-500' },
]

const learningModules = [
  { title: '学习中心', desc: '个人学习概览，追踪学习进度和成就。', path: '/learning/center', icon: 'graduation-cap', iconBg: 'rgba(59,111,224,0.12)', iconColor: 'text-primary-600' },
  { title: '学习闪卡', desc: '利用间隔重复闪卡，高效记忆核心知识点。', path: '/learning/flashcards', icon: 'layers', iconBg: 'rgba(16,185,129,0.12)', iconColor: 'text-success-500' },
  { title: '错题本', desc: '自动收录错题，针对性复习巩固薄弱点。', path: '/mistakes', icon: 'alert-circle', iconBg: 'rgba(239,68,68,0.12)', iconColor: 'text-danger-500' },
  { title: '复习计划', desc: '科学安排复习节奏，艾宾浩斯记忆曲线辅助。', path: '/learning/review', icon: 'calendar', iconBg: 'rgba(245,158,11,0.12)', iconColor: 'text-warning-500' },
  { title: '学习路径', desc: '系统化学习路径规划，循序渐进掌握技能。', path: '/learning/paths', icon: 'map', iconBg: 'rgba(20,184,166,0.12)', iconColor: 'text-teal-500' },
  { title: '学习模式', desc: '专注学习模式，沉浸式阅读提升效率。', path: '/learning/mode', icon: 'book-check', iconBg: 'rgba(79,70,229,0.12)', iconColor: 'text-indigo-600' },
]

const toolModules = [
  { title: '智能问答', desc: '与 AI 对话，针对知识库内容进行智能问答。', path: '/chat', icon: 'message-circle', iconBg: 'rgba(124,58,237,0.12)', iconColor: 'text-purple-600' },
  { title: '智能出题', desc: 'AI 自动生成练习题，检验知识掌握程度。', path: '/learning/quiz', icon: 'brain', iconBg: 'rgba(249,115,22,0.12)', iconColor: 'text-orange-500' },
  { title: '智能写作', desc: 'AI 辅助写作，润色文章、生成大纲。', path: '/learning/writing', icon: 'pen-tool', iconBg: 'rgba(20,184,166,0.12)', iconColor: 'text-teal-500' },
  { title: '代码练习', desc: '在线编写和运行代码，巩固编程知识技能。', path: '/learning/code-practice', icon: 'code', iconBg: 'rgba(59,111,224,0.12)', iconColor: 'text-primary-600' },
  { title: '上传文档', desc: '上传本地文档到知识库，支持多种格式。', path: '/upload', icon: 'upload', iconBg: 'rgba(16,185,129,0.12)', iconColor: 'text-success-500' },
  { title: '消息中心', desc: '系统通知、学习提醒、社区消息一站查看。', path: '/notifications', icon: 'bell', iconBg: 'rgba(245,158,11,0.12)', iconColor: 'text-warning-500' },
]

const recentItems = computed(() =>
  recentDocs.value.slice(0, 5).map((d, idx) => ({
    id: d.id,
    title: d.title,
    time: formatTime(d.createTime),
    icon: recentIcons[idx % recentIcons.length].icon,
    iconBg: recentIcons[idx % recentIcons.length].bg,
    iconColor: recentIcons[idx % recentIcons.length].color,
  }))
)

const recentIcons = [
  { icon: 'book-open', bg: 'rgba(59,111,224,0.1)', color: 'text-primary-600' },
  { icon: 'check-circle', bg: 'rgba(16,185,129,0.1)', color: 'text-success-500' },
  { icon: 'layers', bg: 'rgba(124,58,237,0.1)', color: 'text-purple-600' },
  { icon: 'code', bg: 'rgba(20,184,166,0.1)', color: 'text-teal-500' },
]

const recommendColors = [
  { bg: 'rgba(59,111,224,0.1)', color: 'text-primary-600' },
  { bg: 'rgba(16,185,129,0.1)', color: 'text-success-500' },
  { bg: 'rgba(245,158,11,0.1)', color: 'text-warning-500' },
  { bg: 'rgba(79,70,229,0.1)', color: 'text-indigo-600' },
]

function recommendIconBg(_doc: DocVO) {
  return recommendColors[Math.abs(_doc.id) % recommendColors.length].bg
}
function recommendIconColor(_doc: DocVO) {
  return recommendColors[Math.abs(_doc.id) % recommendColors.length].color
}

function goToDoc(id: number) {
  router.push(`/doc/${id}`)
}

function formatTime(dateStr?: string) {
  if (!dateStr) return ''
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

onMounted(async () => {
  try {
    const tree = await categoriesApi.tree()
    categoryCount.value = tree.filter((c) => !c.parentId || c.parentId === 0).length
  } catch {
    categoryCount.value = 0
  }
  try {
    const page = await docsApi.list({ pageNum: 1, pageSize: 1 })
    docCount.value = page.total || 0
  } catch {
    docCount.value = 0
  }
  try {
    recentDocs.value = await docsApi.recent()
  } catch {
    recentDocs.value = []
  }
  try {
    recommendedDocs.value = await docsApi.recommend()
  } catch {
    recommendedDocs.value = []
  }
  if (auth.isLoggedIn) {
    try {
      const s = await userApi.stats()
      userStats.value = {
        studyHours: Number(s.totalStudyHours || 0),
        readDocs: s.readDocsCount || 0,
        streakDays: s.streakDays || 0,
        favorites: s.favoriteCount || 0,
        progress: s.readDocsCount && docCount.value ? Math.min(100, Math.round((s.readDocsCount / docCount.value) * 100)) : 0,
      }
    } catch {
      /* 保留默认 0 */
    }
  }
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
