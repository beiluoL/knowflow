<template>
  <header class="sticky top-0 z-30 h-14 kb-region-topbar flex items-center justify-between px-4 lg:px-8">
    <div class="flex items-center gap-3">
      <button
        type="button"
        class="w-9 h-9 rounded-lg flex items-center justify-center border border-gray-200 hover:bg-gray-50 transition-colors lg:hidden"
        @click="$emit('toggle-sidebar')"
        aria-label="切换侧边栏"
      >
        <Icon name="menu" :size="18" class="text-gray-600" />
      </button>

      <div class="relative hidden sm:block">
        <Icon name="search" :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索知识库、文档..."
          class="w-56 lg:w-72 h-9 pl-9 pr-4 rounded-lg text-[13px] border border-gray-200 bg-white text-gray-800 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-all"
          @keyup.enter="handleSearch"
          aria-label="搜索"
        />
      </div>

      <button
        type="button"
        class="w-9 h-9 rounded-lg flex items-center justify-center border border-gray-200 bg-white hover:bg-gray-50 transition-colors sm:hidden"
        @click="goTo('/search')"
        aria-label="搜索"
      >
        <Icon name="search" :size="18" class="text-gray-500" />
      </button>
    </div>

    <div class="flex items-center gap-3 shrink-0">
      <!-- P3-1：主题切换 -->
      <button
        type="button"
        class="w-9 h-9 rounded-lg flex items-center justify-center border border-gray-200 bg-white hover:bg-gray-50 transition-colors"
        :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
        :title="isDark ? '切换到浅色模式' : '切换到深色模式'"
        @click="toggleTheme"
      >
        <Icon :name="isDark ? 'sun' : 'moon'" :size="18" class="text-gray-500" />
      </button>

      <button
        v-if="isLoggedIn"
        type="button"
        class="relative w-9 h-9 rounded-lg flex items-center justify-center border border-gray-200 bg-white hover:bg-gray-50 transition-colors"
        @click="handleToggleNotifications"
        :aria-label="`消息通知，${unreadCount} 条未读`"
        :aria-expanded="showNotifications"
      >
        <Icon name="bell" :size="18" class="text-gray-500" />
        <span v-if="unreadCount > 0" class="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-danger-500" />
      </button>

      <div class="relative">
        <button
          type="button"
          class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0 bg-primary-500/15 text-primary-600 hover:bg-primary-500/25 transition-colors"
          aria-haspopup="menu"
          :aria-expanded="showUserMenu"
          :aria-label="isLoggedIn ? '用户菜单' : '登录 / 注册'"
          @click="showUserMenu = !showUserMenu"
        >
          <Icon name="user" :size="18" />
        </button>

        <div v-if="showUserMenu" class="fixed inset-0 z-40" @click="showUserMenu = false" />
        <div
          v-if="showUserMenu"
          class="absolute right-0 top-full mt-2 w-56 bg-white border border-gray-200 rounded-lg shadow-lg py-2 z-50"
          role="menu"
        >
          <div class="px-4 py-3 border-b border-gray-100">
            <div class="text-sm font-medium text-gray-800">{{ displayName }}</div>
            <div v-if="displayEmail" class="text-xs text-gray-500 mt-0.5">{{ displayEmail }}</div>
          </div>
          <div class="py-1">
            <button
              v-for="item in userMenuItems"
              :key="item.path"
              type="button"
              role="menuitem"
              class="w-full flex items-center gap-3 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
              @click="goTo(item.path)"
            >
              <Icon :name="item.iconName" :size="16" class="text-gray-400" />
              {{ item.label }}
            </button>
          </div>
          <div class="border-t border-gray-100 pt-1">
            <button
              v-if="isLoggedIn"
              type="button"
              role="menuitem"
              class="w-full flex items-center gap-3 px-4 py-2 text-sm text-danger-600 hover:bg-danger-50 transition-colors"
              @click="handleLogout"
            >
              <Icon name="log-out" :size="16" />
              退出登录
            </button>
            <button
              v-else
              type="button"
              role="menuitem"
              class="w-full flex items-center gap-3 px-4 py-2 text-sm text-primary-600 hover:bg-primary-50 transition-colors"
              @click="goTo('/login')"
            >
              <Icon name="log-in" :size="16" />
              登录 / 注册
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="isLoggedIn && showNotifications" class="fixed inset-0 z-40" @click="showNotifications = false" />
    <div
      v-if="isLoggedIn && showNotifications"
      class="fixed right-2 left-2 sm:left-auto sm:right-4 top-16 sm:w-80 bg-white border border-gray-200 rounded-lg shadow-xl z-50"
    >
      <div class="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <h3 class="font-semibold text-gray-800">通知</h3>
        <button
          v-if="unreadCount > 0"
          class="text-sm text-primary-600 hover:underline"
          @click="handleMarkAllRead"
        >全部已读</button>
      </div>
      <div class="max-h-96 overflow-y-auto">
        <div
          v-for="notification in notificationStore.unreadList.slice(0, 5)"
          :key="notification.id"
          class="px-4 py-3 hover:bg-gray-50 border-b border-gray-50 cursor-pointer transition-colors bg-primary-50/30"
          @click="handleNotificationClick(notification.id)"
        >
          <div class="flex items-start gap-3">
            <div
              class="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0"
              :class="getNotificationIconBg(notification.type)"
            >
              <Icon :name="getNotificationIcon(notification.type)" :size="16" :class="getNotificationIconColor(notification.type)" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-medium text-gray-800 text-sm">{{ notification.title }}</div>
              <div class="text-gray-500 text-xs mt-0.5 line-clamp-2">{{ notification.content }}</div>
              <div class="text-gray-400 text-xs mt-1">{{ formatTime(notification.createTime) }}</div>
            </div>
          </div>
        </div>
        <div
          v-if="notificationStore.unreadList.length === 0"
          class="px-4 py-8 text-center text-sm text-gray-400"
        >
          暂无未读通知
        </div>
      </div>
      <div class="px-4 py-2 border-t border-gray-100">
        <button
          type="button"
          class="w-full text-center text-sm text-primary-600 hover:underline"
          @click="goTo('/notifications')"
        >查看全部通知</button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
// 布局组件：前台顶栏，负责搜索、用户菜单与通知面板的展示与交互。
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { notify } from '@/utils/toast'
import { useTheme } from '@/composables/useTheme'

defineEmits<{
  'toggle-sidebar': []
}>()

const router = useRouter()
const auth = useAuthStore()
const notificationStore = useNotificationStore()

// P3-1：主题切换
const { theme, toggleTheme } = useTheme()
const isDark = computed(() => theme.value === 'dark')

const searchQuery = ref('')
const showNotifications = ref(false)
const showUserMenu = ref(false)

const isLoggedIn = computed(() => auth.isLoggedIn)
const displayName = computed(() => {
  const u = auth.user
  if (!u) return '游客'
  return u.nickname || u.username || '用户'
})
const displayEmail = computed(() => {
  const u = auth.user
  return u?.email || u?.username || ''
})
const unreadCount = computed(() => notificationStore.unreadCount)

const userMenuItems = [
  { path: '/profile', label: '个人中心', iconName: 'user' },
  { path: '/learning/center', label: '学习中心', iconName: 'graduation-cap' },
  { path: '/learning/focus', label: '沉浸工作台', iconName: 'zap' },
  { path: '/notifications', label: '消息中心', iconName: 'bell' },
  { path: '/favorites', label: '我的收藏', iconName: 'heart' },
]

function handleSearch() {
  if (!searchQuery.value.trim()) return
  router.push({ path: '/search', query: { q: searchQuery.value.trim() } })
}

function goTo(path: string) {
  showUserMenu.value = false
  showNotifications.value = false
  router.push(path)
}

function handleLogout() {
  showUserMenu.value = false
  notificationStore.reset()
  auth.logout()
  notify('已退出登录', 'success')
  router.push('/login')
}

async function handleToggleNotifications() {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value && isLoggedIn.value) {
    try {
      await notificationStore.fetchList({ pageNum: 1, pageSize: 20 })
    } catch {
      // 通知列表加载失败静默处理
    }
  }
}

async function handleMarkAllRead() {
  try {
    await notificationStore.markAllAsRead()
    notify('已全部标为已读', 'success')
  } catch {
    notify('操作失败', 'error')
  }
}

async function handleNotificationClick(id: number) {
  try {
    await notificationStore.markAsRead(id)
  } catch {
    // 标记已读失败静默处理
  }
  showNotifications.value = false
  router.push('/notifications')
}

function getNotificationIcon(type: string): string {
  switch (type) {
    case 'SYSTEM':
      return 'info'
    case 'LEARNING':
      return 'bell'
    case 'COMMUNITY':
      return 'message-circle'
    default:
      return 'bell'
  }
}

function getNotificationIconBg(type: string): string {
  switch (type) {
    case 'SYSTEM':
      return 'bg-blue-50'
    case 'LEARNING':
      return 'bg-green-50'
    case 'COMMUNITY':
      return 'bg-purple-50'
    default:
      return 'bg-gray-100'
  }
}

function getNotificationIconColor(type: string): string {
  switch (type) {
    case 'SYSTEM':
      return 'text-primary-500'
    case 'LEARNING':
      return 'text-success-500'
    case 'COMMUNITY':
      return 'text-purple-500'
    default:
      return 'text-gray-500'
  }
}

function formatTime(time?: string): string {
  if (!time) return ''
  const date = new Date(time)
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

watch(isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    notificationStore.fetchUnreadCount()
  } else {
    notificationStore.reset()
  }
})

onMounted(() => {
  if (isLoggedIn.value) {
    notificationStore.fetchUnreadCount()
  }
})
</script>
