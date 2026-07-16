<template>
  <AppShell>
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
                  {{ stat.trend > 0 ? '+' : '' }}{{ stat.trend }}%
                </span>
                <span class="text-xs text-gray-400">较昨日</span>
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
              <h3 class="font-semibold text-gray-800">用户增长趋势</h3>
              <div class="flex items-center gap-2">
                <button
                  v-for="period in periods"
                  :key="period.value"
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
                v-for="(item, index) in userGrowthData"
                :key="index"
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
                  v-for="slice in categoryDistribution"
                  :key="slice.name"
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
                v-for="item in categoryDistribution"
                :key="item.name"
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
              v-for="(activity, index) in recentActivities"
              :key="activity.id"
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
              v-for="(doc, index) in hotDocs"
              :key="doc.id"
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
          </div>
        </Card>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import AppShell from '@/components/layout/AppShell.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'

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

const stats = [
  {
    label: '总用户数',
    value: '12,580',
    trend: 12.5,
    iconName: 'users',
    iconBg: 'bg-primary-50',
    iconColor: 'text-primary-500',
  },
  {
    label: '总文档数',
    value: '3,842',
    trend: 8.3,
    iconName: 'file-text',
    iconBg: 'bg-success-50',
    iconColor: 'text-success-500',
  },
  {
    label: '今日学习时长',
    value: '1,256h',
    trend: -2.1,
    iconName: 'clock',
    iconBg: 'bg-warning-50',
    iconColor: 'text-warning-500',
  },
  {
    label: '今日对话数',
    value: '892',
    trend: 15.7,
    iconName: 'message-square',
    iconBg: 'bg-danger-50',
    iconColor: 'text-danger-500',
  },
]

const userGrowthData = [
  { day: '周一', totalUsers: 60, newUsers: 15 },
  { day: '周二', totalUsers: 65, newUsers: 18 },
  { day: '周三', totalUsers: 72, newUsers: 22 },
  { day: '周四', totalUsers: 78, newUsers: 20 },
  { day: '周五', totalUsers: 85, newUsers: 25 },
  { day: '周六', totalUsers: 92, newUsers: 28 },
  { day: '周日', totalUsers: 100, newUsers: 30 },
]

const categoryDistribution = [
  { name: '前端开发', count: 1256, color: '#3B6FE0', start: 0, end: 32.7 },
  { name: '后端开发', count: 986, color: '#10B981', start: 32.7, end: 58.4 },
  { name: '人工智能', count: 654, color: '#F59E0B', start: 58.4, end: 75.4 },
  { name: '数据库', count: 486, color: '#EF4444', start: 75.4, end: 88.0 },
  { name: '其他', count: 460, color: '#6B7280', start: 88.0, end: 100 },
]

const totalDocs = 3842

const recentActivities: Activity[] = [
  {
    id: 1,
    user: '张三',
    action: '注册了新账号',
    time: '5 分钟前',
    iconName: 'user-plus',
    iconBg: 'bg-primary-50',
    iconColor: 'text-primary-500',
    badgeVariant: 'primary',
    badgeText: '新用户',
  },
  {
    id: 2,
    user: '李四',
    action: '上传了文档《Vue 3 组合式 API 详解》',
    time: '12 分钟前',
    iconName: 'upload',
    iconBg: 'bg-success-50',
    iconColor: 'text-success-500',
    badgeVariant: 'success',
    badgeText: '上传',
  },
  {
    id: 3,
    user: '王五',
    action: '开始学习《React 入门指南》',
    time: '25 分钟前',
    iconName: 'book-open',
    iconBg: 'bg-warning-50',
    iconColor: 'text-warning-500',
    badgeVariant: 'warning',
    badgeText: '学习',
  },
  {
    id: 4,
    user: '赵六',
    action: '发起了 AI 对话请求',
    time: '38 分钟前',
    iconName: 'message-circle',
    iconBg: 'bg-danger-50',
    iconColor: 'text-danger-500',
    badgeVariant: 'danger',
    badgeText: '对话',
  },
  {
    id: 5,
    user: '孙七',
    action: '注册了新账号',
    time: '1 小时前',
    iconName: 'user-plus',
    iconBg: 'bg-primary-50',
    iconColor: 'text-primary-500',
    badgeVariant: 'primary',
    badgeText: '新用户',
  },
]

const hotDocs = [
  { id: 1, title: 'Vue 3 组合式 API 完全指南', category: '前端开发', views: 2580 },
  { id: 2, title: 'TypeScript 高级类型详解', category: '前端开发', views: 2156 },
  { id: 3, title: 'Node.js 性能优化实战', category: '后端开发', views: 1890 },
  { id: 4, title: '大语言模型原理与应用', category: '人工智能', views: 1654 },
  { id: 5, title: 'MySQL 索引优化深入理解', category: '数据库', views: 1432 },
]
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
