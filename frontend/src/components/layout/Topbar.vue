<template>
  <header class="h-16 bg-white border-b border-gray-100 flex items-center justify-between px-6 sticky top-0 z-20">
    <div class="flex items-center gap-4">
      <button
        @click="$emit('toggle-sidebar')"
        class="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-md transition-colors lg:hidden"
      >
        <Icon name="menu" :size="20" />
      </button>

      <div class="relative w-96 max-w-md">
        <Icon name="search" :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索文档、分类、标签..."
          class="w-full pl-10 pr-4 py-2 text-sm bg-gray-50 border border-gray-200 rounded-md focus:outline-none focus:border-primary-500 focus:bg-white focus:ring-2 focus:ring-primary-100 transition-all"
          @keyup.enter="$emit('search', searchQuery)"
        />
        <kbd class="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">
          ⌘K
        </kbd>
      </div>
    </div>

    <div class="flex items-center gap-2">
      <button
        class="relative p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-md transition-colors"
        @click="showNotifications = !showNotifications"
      >
        <Icon name="bell" :size="20" />
        <span
          v-if="unreadCount > 0"
          class="absolute top-1.5 right-1.5 w-2 h-2 bg-danger-500 rounded-full"
        />
      </button>

      <div class="relative">
        <button
          class="flex items-center gap-3 p-1.5 hover:bg-gray-100 rounded-lg transition-colors"
          @click="showUserMenu = !showUserMenu"
        >
          <Avatar :name="user.nickname" size="sm" :status="'online'" show-status />
          <div class="text-left hidden sm:block">
            <div class="text-sm font-medium text-gray-800">{{ user.nickname }}</div>
            <div class="text-xs text-gray-500">Lv.{{ user.stats.level }}</div>
          </div>
          <Icon name="chevron-down" :size="16" class="text-gray-400 hidden sm:block" />
        </button>

        <div
          v-if="showUserMenu"
          class="absolute right-0 top-full mt-2 w-56 bg-white border border-gray-100 rounded-lg shadow-lg py-2 z-50"
        >
          <div class="px-4 py-3 border-b border-gray-100">
            <div class="font-medium text-gray-800">{{ user.nickname }}</div>
            <div class="text-sm text-gray-500">{{ user.email }}</div>
          </div>
          <div class="py-1">
            <a
              v-for="item in userMenuItems"
              :key="item.path"
              :href="item.path"
              class="flex items-center gap-3 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
            >
              <Icon :name="item.iconName" :size="16" class="text-gray-400" />
              {{ item.label }}
            </a>
          </div>
          <div class="border-t border-gray-100 pt-1">
            <button class="w-full flex items-center gap-3 px-4 py-2 text-sm text-danger-600 hover:bg-danger-50 transition-colors">
              <Icon name="log-out" :size="16" />
              退出登录
            </button>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="showNotifications"
      class="fixed right-4 top-20 w-80 bg-white border border-gray-100 rounded-lg shadow-xl z-50"
    >
      <div class="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <h3 class="font-semibold text-gray-800">通知</h3>
        <button class="text-sm text-primary-500 hover:text-primary-600">全部已读</button>
      </div>
      <div class="max-h-96 overflow-y-auto">
        <div
          v-for="notification in notifications"
          :key="notification.id"
          class="px-4 py-3 hover:bg-gray-50 border-b border-gray-50 cursor-pointer transition-colors"
          :class="{ 'bg-primary-50/30': !notification.read }"
        >
          <div class="flex items-start gap-3">
              <div
                class="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0"
                :class="notificationIconBg(notification.type)"
              >
                <Icon :name="notificationIconName(notification.type)" :size="16" :class="notificationIconColor(notification.type)" />
              </div>
            <div class="flex-1 min-w-0">
              <div class="font-medium text-gray-800 text-sm">{{ notification.title }}</div>
              <div class="text-gray-500 text-xs mt-0.5 line-clamp-2">{{ notification.content }}</div>
              <div class="text-gray-400 text-xs mt-1">{{ formatTime(notification.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>
      <div class="px-4 py-3 border-t border-gray-100 text-center">
        <a href="/notifications" class="text-sm text-primary-500 hover:text-primary-600">查看全部通知</a>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { mockUser, mockNotifications } from '@/data/user'
import type { Notification } from '@/types'

defineEmits<{
  'toggle-sidebar': []
  search: [query: string]
}>()

const searchQuery = ref('')
const showNotifications = ref(false)
const showUserMenu = ref(false)
const user = ref(mockUser)
const notifications = ref(mockNotifications)

const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

const userMenuItems = [
  { path: '/profile', label: '个人中心', iconName: 'user' },
  { path: '/profile/collections', label: '我的收藏', iconName: 'book-marked' },
  { path: '/settings', label: '设置', iconName: 'settings' },
]

const notificationIconName = (type: Notification['type']) => {
  const icons: Record<Notification['type'], string> = {
    system: 'alert-circle',
    reminder: 'message-square',
    achievement: 'trophy',
  }
  return icons[type]
}

const notificationIconBg = (type: Notification['type']) => {
  const bgs = {
    system: 'bg-primary-100',
    reminder: 'bg-warning-100',
    achievement: 'bg-success-100',
  }
  return bgs[type]
}

const notificationIconColor = (type: Notification['type']) => {
  const colors = {
    system: 'text-primary-600',
    reminder: 'text-warning-600',
    achievement: 'text-success-600',
  }
  return colors[type]
}

const formatTime = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN')
}
</script>
