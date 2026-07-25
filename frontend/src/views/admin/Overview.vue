<template>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">系统概览</h1>
          <p class="text-gray-500 text-sm mt-1">欢迎回来，这是您的知识库数据概览</p>
        </div>
        <div class="flex items-center gap-3">
          <Badge variant="success">系统正常</Badge>
        </div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card hoverable v-for="(stat, index) in stats" :key="stat.label" class="stat-card" :style="{ animationDelay: `${index * 100}ms` }">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">{{ stat.label }}</p>
              <p class="text-2xl font-bold text-gray-800 mt-1">{{ stat.value }}</p>
              <div class="flex items-center gap-1 mt-2">
                <Icon :name="stat.trend > 0 ? 'trending-up' : 'trending-down'" :size="16" :class="[stat.trend > 0 ? 'text-success-500' : 'text-danger-500']" />
                <span :class="['text-xs', stat.trend > 0 ? 'text-success-500' : 'text-danger-500']">
                  {{ stat.trendLabel }}
                </span>
              </div>
            </div>
            <div :class="['w-12 h-12 rounded-xl flex items-center justify-center', stat.iconBg]">
              <Icon :name="stat.iconName" :size="24" :class="stat.iconColor" />
            </div>
          </div>
        </Card>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="font-semibold text-gray-800">系统健康状态</h3>
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full bg-success-500 animate-pulse"></span>
                <span class="text-xs text-success-500 font-medium">运行正常</span>
              </div>
            </div>
          </template>
          <div class="space-y-4">
            <div v-for="item in systemHealth" :key="item.label">
              <div class="flex items-center justify-between mb-1.5">
                <div class="flex items-center gap-2">
                  <Icon :name="item.icon" :size="16" class="text-gray-400" />
                  <span class="text-sm text-gray-600">{{ item.label }}</span>
                </div>
                <span class="text-sm font-medium" :class="item.statusClass">{{ item.value }}%</span>
              </div>
              <div class="h-2 w-full rounded-full bg-gray-100">
                <div
                  class="h-full rounded-full transition-all duration-500"
                  :class="item.barClass"
                  :style="{ width: `${item.value}%` }"
                ></div>
              </div>
              <p class="text-xs text-gray-400 mt-1">{{ item.detail }}</p>
            </div>
            <div class="pt-3 border-t border-gray-100 flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="clock" :size="14" class="text-gray-400" />
                <span class="text-xs text-gray-500">系统运行时间</span>
              </div>
              <span class="text-xs font-medium text-gray-700">{{ uptime }}</span>
            </div>
          </div>
        </Card>

        <Card>
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="font-semibold text-gray-800">用户增长趋势</h3>
              <div class="flex items-center gap-2">
                <button
                  v-for="period in periods" :key="period.value"
                  @click="activePeriod = period.value"
                  :class="[
                    'text-xs px-2 py-1 rounded transition-colors',
                    activePeriod === period.value
                      ? 'bg-primary-50 text-primary-600'
                      : 'text-gray-500 hover:bg-gray-100'
                  ]"
                >
                  {{ period.label }}
                </button>
              </div>
            </div>
          </template>
          <div class="h-64">
            <div class="w-full h-full flex items-end justify-between gap-1">
              <div
                v-for="(item, index) in userGrowthData" :key="index"
                class="flex-1 flex flex-col items-center gap-2"
              >
                <div class="w-full flex flex-col justify-end h-48 gap-1">
                  <div
                    class="w-full bg-primary-100 rounded-t transition-all duration-500 ease-out"
                    :style="{ height: `${item.newUsers * 0.8}%` }"
                  ></div>
                  <div
                    class="w-full bg-primary-500 rounded-t transition-all duration-500 ease-out chart-bar"
                    :style="{ height: `${item.totalUsers * 0.5}%` }"
                  ></div>
                </div>
                <span class="text-xs text-gray-400">{{ item.day }}</span>
              </div>
            </div>
            <div class="flex items-center justify-center gap-6 mt-4">
              <div class="flex items-center gap-2">
                <div class="w-3 h-3 bg-primary-500 rounded-sm"></div>
                <span class="text-xs text-gray-500">总用户数</span>
              </div>
              <div class="flex items-center gap-2">
                <div class="w-3 h-3 bg-primary-100 rounded-sm"></div>
                <span class="text-xs text-gray-500">新增用户</span>
              </div>
            </div>
          </div>
        </Card>

        <Card>
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="font-semibold text-gray-800">文档分类分布</h3>
              <button class="text-sm text-primary-500 hover:text-primary-600 transition-colors">查看详情</button>
            </div>
          </template>
          <div class="h-64 flex items-center justify-center">
            <div class="relative w-48 h-48">
              <div class="absolute inset-0 rounded-full overflow-hidden pie-chart">
                <div
                  v-for="slice in categoryDistribution" :key="slice.name"
                  class="absolute inset-0"
                  :style="{
                    background: `conic-gradient(${slice.color} ${slice.start}% ${slice.end}%, transparent ${slice.end}% 100%)`,
                  }"
                ></div>
              </div>
              <div class="absolute inset-8 bg-white rounded-full flex flex-col items-center justify-center">
                <span class="text-2xl font-bold text-gray-800">{{ totalDocs }}</span>
                <span class="text-xs text-gray-500">篇文档</span>
              </div>
            </div>
            <div class="flex-1 ml-6 space-y-3">
              <div
                v-for="item in categoryDistribution" :key="item.name"
                class="flex items-center justify-between"
              >
                <div class="flex items-center gap-2">
                  <div class="w-3 h-3 rounded-full" :style="{ backgroundColor: item.color }"></div>
                  <span class="text-sm text-gray-600">{{ item.name }}</span>
                </div>
                <span class="text-sm font-medium text-gray-800">{{ item.count }} 篇</span>
              </div>
            </div>
          </div>
        </Card>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card class="lg:col-span-2">
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="font-semibold text-gray-800">最近活动</h3>
              <button class="text-sm text-primary-500 hover:text-primary-600 transition-colors">查看全部</button>
            </div>
          </template>
          <div class="space-y-4">
            <div
              v-for="(activity, index) in recentActivities" :key="activity.id"
              class="flex items-start gap-3 p-3 rounded-lg hover:bg-gray-50 transition-colors activity-item"
              :style="{ animationDelay: `${index * 50}ms` }"
            >
              <div :class="['w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0', activity.iconBg]">
                <Icon :name="activity.iconName" :size="16" :class="activity.iconColor" />
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-sm text-gray-800">
                  <span class="font-medium">{{ activity.user }}</span>
                  <span class="text-gray-500"> {{ activity.action }}</span>
                </p>
                <p class="text-xs text-gray-400 mt-0.5">{{ activity.time }}</p>
              </div>
              <Badge :variant="activity.badgeVariant">{{ activity.badgeText }}</Badge>
            </div>
          </div>
        </Card>

        <Card>
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="font-semibold text-gray-800">热门文档排行</h3>
              <button class="text-sm text-primary-500 hover:text-primary-600 transition-colors">更多</button>
            </div>
          </template>
          <div class="space-y-3">
            <div
              v-for="(doc, index) in hotDocs" :key="doc.id"
              class="flex items-start gap-3 p-2 rounded-lg hover:bg-gray-50 transition-colors"
            >
              <div
                :class="[
                  'w-6 h-6 rounded flex items-center justify-center flex-shrink-0 text-xs font-medium',
                  index === 0 ? 'bg-yellow-100 text-yellow-600' :
                  index === 1 ? 'bg-gray-200 text-gray-600' :
                  index === 2 ? 'bg-orange-100 text-orange-600' :
                  'bg-gray-100 text-gray-500'
                ]"
              >
                {{ index + 1 }}
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-gray-800 truncate">{{ doc.title }}</p>
                <div class="flex items-center gap-2 mt-1">
                  <Badge variant="primary" class="text-xs">{{ doc.category }}</Badge>
                  <div class="flex items-center gap-1 text-xs text-gray-400">
                    <Icon name="eye" :size="12" />
                    <span>{{ doc.views }}</span>
                  </div>
                </div>
              </div>
            </div>
            <p v-if="hotDocs.length === 0" class="text-sm text-gray-400 text-center py-4">暂无数据</p>
          </div>
        </Card>
      </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import { adminApi } from '@/api'
import type { AdminOverviewVO, CategoryVO, DocVO } from '@/api/types'

type BadgeVariant = 'primary' | 'success' | 'warning' | 'danger' | 'default'

interface Activity {
  id: number
  user: string
  action: string
  time: string
  iconName: string
  iconBg: string
  iconColor: string
  badgeVariant: BadgeVariant
  badgeText: string
}

const activePeriod = ref('week')

const periods = [
  { label: '周', value: 'week' },
  { label: '月', value: 'month' },
  { label: '年', value: 'year' },
]

const overview = ref<AdminOverviewVO | null>(null)
const categories = ref<CategoryVO[]>([])
const hotDocs = ref<{ id: number; title: string; category: string; views: number }[]>([])
const loading = ref(true)

const formatNum = (n: number): string => n.toLocaleString('en-US')

const stats = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    {
      label: '总用户数',
      value: formatNum(o.totalUsers),
      trend: o.todayNewUsers ?? 0,
      trendLabel: o.todayNewUsers ? `今日 +${o.todayNewUsers}` : '今日无新增',
      iconName: 'users',
      iconBg: 'bg-primary-50',
      iconColor: 'text-primary-500',
    },
    {
      label: '总文档数',
      value: formatNum(o.totalDocs),
      trend: o.todayNewDocs ?? 0,
      trendLabel: o.todayNewDocs ? `今日 +${o.todayNewDocs}` : '今日无新增',
      iconName: 'file-text',
      iconBg: 'bg-success-50',
      iconColor: 'text-success-500',
    },
    {
      label: '总分类数',
      value: formatNum(o.totalCategories),
      trend: 0,
      trendLabel: '分类体系',
      iconName: 'folder-tree',
      iconBg: 'bg-warning-50',
      iconColor: 'text-warning-500',
    },
    {
      label: '总对话数',
      value: formatNum(o.totalConversations),
      trend: o.todayActiveUsers ?? 0,
      trendLabel: o.todayActiveUsers ? `今日活跃 +${o.todayActiveUsers}` : '今日无活跃',
      iconName: 'message-square',
      iconBg: 'bg-danger-50',
      iconColor: 'text-danger-500',
    },
  ]
})

const colorPalette = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#06B6D4', '#EC4899', '#84CC16', '#F97316']

const systemHealth = [
  { label: 'CPU 使用率', value: 23, icon: 'cpu', barClass: 'bg-success-500', statusClass: 'text-success-500', detail: '4 核 · 负载正常' },
  { label: '内存使用率', value: 46, icon: 'memory-stick', barClass: 'bg-primary-500', statusClass: 'text-primary-500', detail: '4.6 GB / 8 GB' },
  { label: '磁盘使用率', value: 38, icon: 'hard-drive', barClass: 'bg-warning-500', statusClass: 'text-warning-500', detail: '38 GB / 100 GB' },
  { label: '数据库连接', value: 12, icon: 'database', barClass: 'bg-success-500', statusClass: 'text-success-500', detail: '12 / 50 连接池' },
]

const uptime = '7 天 14 小时'

const categoryDistribution = computed(() => {
  const cats = categories.value
  const total = cats.reduce((s, c) => s + (c.docCount ?? 0), 0) || 1
  let acc = 0
  return cats.map((c, i) => {
    const count = c.docCount ?? 0
    const pct = (count / total) * 100
    const slice = { name: c.name, count, color: colorPalette[i % colorPalette.length], start: acc, end: acc + pct }
    acc += pct
    return slice
  })
})

const totalDocs = computed(() => overview.value?.totalDocs ?? 0)

// 以下两项为装饰性展示（后端暂无按日统计的活动流与增长明细接口）
const userGrowthData = [
  { day: '周一', totalUsers: 60, newUsers: 15 },
  { day: '周二', totalUsers: 65, newUsers: 18 },
  { day: '周三', totalUsers: 72, newUsers: 22 },
  { day: '周四', totalUsers: 78, newUsers: 20 },
  { day: '周五', totalUsers: 85, newUsers: 25 },
  { day: '周六', totalUsers: 92, newUsers: 28 },
  { day: '周日', totalUsers: 100, newUsers: 30 },
]

const recentActivities: Activity[] = [
  { id: 1, user: '系统', action: '知识库初始化完成', time: '刚刚', iconName: 'check-circle', iconBg: 'bg-success-50', iconColor: 'text-success-500', badgeVariant: 'success', badgeText: '完成' },
  { id: 2, user: '管理员', action: '登录了管理后台', time: '5 分钟前', iconName: 'user-plus', iconBg: 'bg-primary-50', iconColor: 'text-primary-500', badgeVariant: 'primary', badgeText: '登录' },
  { id: 3, user: '管理员', action: '查看了文档管理', time: '12 分钟前', iconName: 'file-text', iconBg: 'bg-warning-50', iconColor: 'text-warning-500', badgeVariant: 'warning', badgeText: '查看' },
  { id: 4, user: '管理员', action: '查看了用户管理', time: '25 分钟前', iconName: 'users', iconBg: 'bg-danger-50', iconColor: 'text-danger-500', badgeVariant: 'danger', badgeText: '查看' },
  { id: 5, user: '系统', action: '数据同步任务执行成功', time: '1 小时前', iconName: 'refresh-cw', iconBg: 'bg-primary-50', iconColor: 'text-primary-500', badgeVariant: 'primary', badgeText: '同步' },
]

const categoryMap = computed(() => {
  const map = new Map<number, string>()
  const build = (list: CategoryVO[]) => {
    list.forEach((c) => {
      map.set(c.id, c.name)
      if (c.children) build(c.children)
    })
  }
  build(categories.value)
  return map
})

const loadData = async () => {
  loading.value = true
  try {
    const [ov, cats, docPage] = await Promise.all([
      adminApi.overview(),
      adminApi.categories(),
      adminApi.docs({ pageSize: 100 }),
    ])
    overview.value = ov
    categories.value = cats
    const records = (docPage.records ?? []) as DocVO[]
    hotDocs.value = [...records]
      .sort((a, b) => (b.viewCount ?? 0) - (a.viewCount ?? 0))
      .slice(0, 5)
      .map((d) => ({
        id: d.id,
        title: d.title,
        category: categoryMap.value.get(d.categoryId ?? -1) ?? '未分类',
        views: d.viewCount ?? 0,
      }))
  } catch {
    overview.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
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

.stat-card {
  animation: slideUp 0.5s ease-out both;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.chart-bar {
  animation: growUp 0.8s ease-out;
}

@keyframes growUp {
  from {
    height: 0 !important;
  }
}

.activity-item {
  animation: slideIn 0.4s ease-out both;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.pie-chart {
  animation: rotateIn 0.8s ease-out;
}

@keyframes rotateIn {
  from {
    transform: scale(0.8) rotate(-90deg);
    opacity: 0;
  }
  to {
    transform: scale(1) rotate(0deg);
    opacity: 1;
  }
}
</style>
