<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">学习路径</h1>
        <p class="text-gray-500 mt-1">系统规划你的学习旅程，循序渐进掌握技能</p>
      </div>
    </div>

    <div class="flex flex-wrap gap-2">
      <button
        v-for="tag in filterTags" :key="tag.value"
        @click="currentFilter = tag.value"
        :class="[
          'px-4 py-2 rounded-full text-sm font-medium transition-all duration-200',
          currentFilter === tag.value
            ? 'bg-primary-500 text-white shadow-md'
            : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200',
        ]"
      >
        {{ tag.label }}
      </button>
    </div>

    <div v-if="loading" class="text-center py-16 text-gray-400">加载中...</div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div
        v-for="(path, index) in filteredPaths" :key="path.id"
        class="group cursor-pointer transform transition-all duration-300 hover:-translate-y-1"
        :style="{ animationDelay: `${index * 80}ms` }"
        @click="goToPathDetail(path.id)"
      >
        <Card hoverable class="h-full flex flex-col" padding="none">
          <div
            :class="[
              'relative h-36 bg-gradient-to-br overflow-hidden rounded-t-lg',
              path.coverGradient,
            ]"
          >
            <div class="absolute inset-0 bg-grid-white/10 [mask-image:linear-gradient(0deg,transparent,white)]" />
            <div class="absolute -top-10 -right-10 w-32 h-32 bg-white/20 rounded-full blur-2xl" />
            <div class="absolute -bottom-8 -left-8 w-24 h-24 bg-white/15 rounded-full blur-xl" />
            <div class="relative z-10 h-full flex items-center justify-center">
              <Icon :name="getPathIconName(path.icon)" :size="64" class="text-white/90 drop-shadow-lg" />
            </div>
            <div class="absolute top-3 right-3">
              <Badge :variant="getDifficultyBadgeVariant(path.difficulty)">
                {{ getDifficultyLabel(path.difficulty) }}
              </Badge>
            </div>
          </div>

          <div class="p-5 flex-1 flex flex-col">
            <h3 class="font-semibold text-gray-800 mb-2 line-clamp-1 group-hover:text-primary-600 transition-colors">
              {{ path.title }}
            </h3>
            <p class="text-sm text-gray-500 mb-4 line-clamp-2 flex-1">
              {{ path.description }}
            </p>

            <div class="space-y-3">
              <Progress :percentage="path.progress" label="学习进度" show-label />

              <div class="flex items-center justify-between text-xs text-gray-500">
                <div class="flex items-center gap-1">
                  <Icon name="book-open" :size="20" />
                  <span>{{ path.chaptersCount }} 章节</span>
                </div>
                <div class="flex items-center gap-1">
                  <Icon name="clock" :size="20" />
                  <span>{{ formatDuration(path.totalDuration) }}</span>
                </div>
              </div>

              <Button
                variant="primary"
                size="sm"
                class="w-full"
              >
                <Icon name="play" :size="16" />
                {{ path.progress > 0 ? '继续学习' : '开始学习' }}
              </Button>
            </div>
          </div>
        </Card>
      </div>

      <div v-if="!loading && filteredPaths.length === 0" class="col-span-full text-center py-16 text-gray-400">
        暂无学习路径
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 学习路径列表页：按难度筛选、卡片网格展示路径并支持跳转详情。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Progress from '@/components/ui/Progress.vue'
import Button from '@/components/ui/Button.vue'
import { learningApi } from '@/api'
import type { LearningPathVO } from '@/api/types'

const router = useRouter()
const loading = ref(false)

interface ViewPath {
  id: number
  title: string
  description?: string
  coverGradient: string
  icon: string
  difficulty: 'beginner' | 'intermediate' | 'advanced'
  chaptersCount: number
  totalDuration: number
  progress: number
}

const gradients = [
  'from-blue-500 to-indigo-600',
  'from-emerald-500 to-teal-600',
  'from-purple-500 to-fuchsia-600',
  'from-orange-500 to-rose-600',
  'from-cyan-500 to-blue-600',
  'from-pink-500 to-rose-600',
]
const icons = ['code', 'server', 'database', 'brain', 'layers', 'puzzle']

// 将后端 level 文案映射为统一的难度枚举
const levelToDifficulty = (level?: string): ViewPath['difficulty'] => {
  const map: Record<string, ViewPath['difficulty']> = {
    入门: 'beginner',
    进阶: 'intermediate',
    高级: 'advanced',
    beginner: 'beginner',
    intermediate: 'intermediate',
    advanced: 'advanced',
  }
  return map[level || ''] || 'beginner'
}

const rawPaths = ref<LearningPathVO[]>([])

// 将后端 VO 映射为视图模型，并按 id 取模循环分配封面渐变与图标
const paths = computed<ViewPath[]>(() =>
  rawPaths.value.map((p) => ({
    id: p.id,
    title: p.title,
    description: p.description,
    coverGradient: gradients[p.id % gradients.length] || gradients[0],
    icon: icons[p.id % icons.length] || 'code',
    difficulty: levelToDifficulty(p.level),
    chaptersCount: p.chapterCount || 0,
    totalDuration: p.totalDuration || 0,
    progress: 0,
  }))
)

const filterTags = [
  { value: 'all', label: '全部' },
  { value: 'beginner', label: '入门' },
  { value: 'intermediate', label: '进阶' },
  { value: 'advanced', label: '高级' },
]

const currentFilter = ref('all')

const filteredPaths = computed(() => {
  if (currentFilter.value === 'all') return paths.value
  return paths.value.filter((p) => p.difficulty === currentFilter.value)
})

const getPathIconName = (iconName: string): string => {
  const iconNameMap: Record<string, string> = {
    code: 'code',
    fileCode: 'file-code',
    brain: 'brain',
    layers: 'layers',
    server: 'server',
    puzzle: 'puzzle',
  }
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

// 将分钟数格式化为「x小时y分」或「x分钟」
const formatDuration = (minutes: number) => {
  if (!minutes) return '0分钟'
  if (minutes < 60) return `${minutes}分钟`
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}小时${mins}分` : `${hours}小时`
}

const goToPathDetail = (pathId: number) => {
  router.push(`/learning/path/${pathId}`)
}

onMounted(async () => {
  loading.value = true
  try {
    rawPaths.value = await learningApi.paths()
  } catch {
    rawPaths.value = []
  } finally {
    loading.value = false
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
