<template>
  <header class="fixed top-0 left-0 right-0 z-50 h-14 flex items-center px-4 sm:px-6 border-b" :style="navStyle">
    <!-- Left：菜单开关（移动端） + Logo -->
    <button
      type="button"
      class="lg:hidden w-9 h-9 mr-1 rounded-lg flex items-center justify-center transition-colors hover:bg-[var(--kb-muted)]"
      aria-label="打开导航菜单"
      :aria-expanded="drawerOpen"
      @click="$emit('toggle-sidebar')"
    >
      <Icon :name="drawerOpen ? 'x' : 'menu'" size="xl" style="color: var(--kb-sidebar-foreground);" />
    </button>

    <router-link
      to="/"
      class="flex items-center shrink-0"
      style="color: var(--kb-primary); gap: var(--kb-nav-gap);"
      aria-label="KnowFlow 首页"
    >
      <Icon name="icon-AIbeikezhushou" size="xl" />
      <span
        class="hidden sm:inline"
        :style="{ fontSize: 'var(--kb-logo-text-fs)', fontWeight: 'var(--kb-logo-text-fw)' }"
      >KnowFlow</span>
    </router-link>

    <!-- Center：搜索（桌面） -->
    <div class="relative hidden md:block w-56 lg:w-72 ml-6">
      <Icon name="search" size="md" class="absolute left-3 top-1/2 -translate-y-1/2" style="color: var(--kb-muted-foreground);" />
      <input
        v-model="searchKw"
        type="text"
        placeholder="搜索知识库…"
        class="w-full h-9 pl-9 pr-12 rounded-lg border outline-none transition-colors focus:border-[var(--kb-primary)] focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
        style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground); font-size: var(--kb-fs-body-md);"
        @keydown.enter="submitSearch"
      />
      <span
        class="absolute right-3 top-1/2 -translate-y-1/2 px-1.5 py-0.5 rounded cursor-pointer transition-colors hover:bg-[var(--kb-primary)] hover:text-white"
        style="background: var(--kb-muted); color: var(--kb-muted-foreground); font-size: var(--kb-fs-xs); font-weight: var(--kb-fw-xs);"
        title="打开命令面板（⌘K / Ctrl+K）"
        @click="openPalette"
      >⌘K</span>
    </div>

    <!-- Right：搜索（移动） + AI 快捷 + 番茄钟 + 通知 + 头像 -->
    <div class="flex items-center gap-2 sm:gap-3 shrink-0 ml-auto">
      <button
        type="button"
        class="md:hidden w-9 h-9 rounded-lg flex items-center justify-center border transition-colors hover:bg-[var(--kb-muted)]"
        style="border-color: var(--kb-border);"
        aria-label="搜索"
        @click="goTo('/search')"
      >
        <Icon name="search" size="lg" style="color: var(--kb-muted-foreground);" />
      </button>

      <!-- AI 快捷入口：主导航已有「AI」分组，此处只保留 1 个直达按钮（§29） -->
      <router-link
        v-if="isLoggedIn"
        to="/chat"
        class="w-9 h-9 rounded-lg flex items-center justify-center border transition-colors hover:bg-[var(--kb-primary-soft)]"
        :class="isActive('/chat') ? 'is-ai-active' : ''"
        style="border-color: var(--kb-border);"
        title="AI 助手"
        aria-label="AI 助手"
        :aria-current="isActive('/chat') ? 'page' : undefined"
      >
        <Icon name="icon-agent" size="lg" :style="{ color: isActive('/chat') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)' }" />
      </router-link>

      <PomodoroFloating v-if="isLoggedIn" />

      <!-- 通知 -->
      <button
        v-if="isLoggedIn"
        type="button"
        class="relative p-2 rounded-lg transition-colors hover:bg-[var(--kb-muted)]"
        style="color: var(--kb-muted-foreground);"
        :aria-label="`消息通知，${unreadCount} 条未读`"
        @click="toggleNotifications"
      >
        <Icon name="bell" size="xl" />
        <span
          v-if="unreadCount > 0"
          class="absolute top-1.5 right-1.5 w-2 h-2 rounded-full"
          style="background: var(--kb-destructive);"
        ></span>
      </button>

      <!-- 头像 / 登录 -->
      <button
        v-if="isLoggedIn"
        type="button"
        class="w-8 h-8 rounded-full flex items-center justify-center shrink-0 transition-opacity hover:opacity-90"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground); font-size: var(--kb-fs-caption); font-weight: 600;"
        :aria-label="`用户菜单：${displayName}`"
        @click="toggleUserMenu"
      >
        {{ initials }}
      </button>
      <button
        v-else
        type="button"
        class="px-3 h-9 rounded-lg transition-colors"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground); font-size: var(--kb-fs-body-md); font-weight: 500;"
        @click="goTo('/login')"
      >
        登录
      </button>
    </div>

    <!-- 通知面板 -->
    <div v-if="isLoggedIn && showNotifications" class="fixed inset-0 z-40" @click="showNotifications = false"></div>
    <div
      v-if="isLoggedIn && showNotifications"
      class="fixed right-2 left-2 sm:left-auto sm:right-4 top-16 sm:w-80 rounded-lg shadow-xl z-50 border"
      style="background: var(--kb-card); border-color: var(--kb-border);"
    >
      <div class="flex items-center justify-between px-4 py-3 border-b" style="border-color: var(--kb-border);">
        <h3 style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); font-weight: 600;">通知</h3>
        <button
          v-if="unreadCount > 0"
          type="button"
          class="hover:underline"
          style="color: var(--kb-primary); font-size: var(--kb-fs-caption);"
          @click="handleMarkAllRead"
        >全部已读</button>
      </div>
      <div class="max-h-96 overflow-y-auto">
        <div
          v-for="notification in notificationStore.unreadList.slice(0, 5)"
          :key="notification.id"
          class="px-4 py-3 hover:bg-[var(--kb-muted)] border-b cursor-pointer transition-colors"
          style="border-color: var(--kb-muted);"
          @click="handleNotificationClick(notification.id)"
        >
          <div class="flex items-start gap-3">
            <div class="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0" :class="getNotifBg(notification.type)">
              <Icon :name="getNotifIcon(notification.type)" size="md" :class="getNotifColor(notification.type)" />
            </div>
            <div class="flex-1 min-w-0">
              <div style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); font-weight: 500;">{{ notification.title }}</div>
              <div class="mt-0.5 line-clamp-2" style="color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption); line-height: var(--kb-lh-caption);">{{ notification.content }}</div>
              <div class="mt-1" style="color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption);">{{ formatTime(notification.createTime) }}</div>
            </div>
          </div>
        </div>
        <div v-if="notificationStore.unreadList.length === 0" class="px-4 py-8 text-center" style="color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-md);">
          暂无未读通知
        </div>
      </div>
      <div class="px-4 py-2 border-t" style="border-color: var(--kb-border);">
        <button
          type="button"
          class="w-full text-center hover:underline"
          style="color: var(--kb-primary); font-size: var(--kb-fs-body-md);"
          @click="goTo('/notifications')"
        >查看全部通知</button>
      </div>
    </div>

    <!-- 用户菜单：UI/UX 2.0 将「我的」与「社区」全部能力收进头像下拉（§20-21/§110-112），不再设一级导航 -->
    <div v-if="showUserMenu" class="fixed inset-0 z-40" @click="showUserMenu = false"></div>
    <div
      v-if="showUserMenu"
      class="absolute right-2 sm:right-6 top-16 min-w-[208px] max-h-[80vh] overflow-y-auto rounded-lg shadow-lg py-1 z-50 border"
      style="background: var(--kb-card); border-color: var(--kb-border);"
      role="menu"
    >
      <p class="user-menu-title" style="color: var(--kb-muted-foreground);">个人</p>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/profile')">
        <Icon name="user-circle" size="md" style="color: var(--kb-muted-foreground);" />
        个人中心
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/wallet')">
        <Icon name="credit-card" size="md" style="color: var(--kb-muted-foreground);" />
        钱包
      </button>
      <div class="user-menu-sep" style="border-color: var(--kb-border);"></div>
      <p class="user-menu-title" style="color: var(--kb-muted-foreground);">学习</p>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/tasks')">
        <Icon name="target" size="md" style="color: var(--kb-muted-foreground);" />
        我的任务
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/task-list')">
        <Icon name="list-checks" size="md" style="color: var(--kb-muted-foreground);" />
        任务清单
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/calendar')">
        <Icon name="calendar" size="md" style="color: var(--kb-muted-foreground);" />
        日历
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/check-in')">
        <Icon name="flame" size="md" style="color: var(--kb-muted-foreground);" />
        每日打卡
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/habits')">
        <Icon name="repeat" size="md" style="color: var(--kb-muted-foreground);" />
        习惯打卡
      </button>
      <div class="user-menu-sep" style="border-color: var(--kb-border);"></div>
      <p class="user-menu-title" style="color: var(--kb-muted-foreground);">成长</p>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/achievements')">
        <Icon name="medal" size="md" style="color: var(--kb-muted-foreground);" />
        成就
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/kb-titles')">
        <Icon name="award" size="md" style="color: var(--kb-muted-foreground);" />
        称号
      </button>
      <div class="user-menu-sep" style="border-color: var(--kb-border);"></div>
      <p class="user-menu-title" style="color: var(--kb-muted-foreground);">内容</p>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/favorites')">
        <Icon name="bookmark" size="md" style="color: var(--kb-muted-foreground);" />
        收藏夹
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/notes')">
        <Icon name="notebook-pen" size="md" style="color: var(--kb-muted-foreground);" />
        笔记
      </button>
      <div class="user-menu-sep" style="border-color: var(--kb-border);"></div>
      <p class="user-menu-title" style="color: var(--kb-muted-foreground);">社区</p>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/community')">
        <Icon name="message-circle" size="md" style="color: var(--kb-muted-foreground);" />
        社区讨论
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/study-group')">
        <Icon name="users" size="md" style="color: var(--kb-muted-foreground);" />
        学习小组
      </button>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/messages')">
        <Icon name="message-square" size="md" style="color: var(--kb-muted-foreground);" />
        私信
      </button>
      <div class="user-menu-sep" style="border-color: var(--kb-border);"></div>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="goTo('/settings/background')">
        <Icon name="image" size="md" style="color: var(--kb-muted-foreground);" />
        背景设置
      </button>
      <button
        v-if="isAdmin"
        type="button"
        role="menuitem"
        class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]"
        style="color: var(--kb-primary); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);"
        @click="goTo('/admin/overview')"
      >
        <Icon name="settings" size="md" />
        管理后台
      </button>
      <div class="user-menu-sep" style="border-color: var(--kb-border);"></div>
      <button type="button" role="menuitem" class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-[var(--kb-muted)]" style="color: var(--kb-destructive); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);" @click="handleLogout">
        <Icon name="log-out" size="md" />
        退出登录
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * C 端顶部栏（Phase UI-2 重构）。
 *
 * 变更要点：
 *  1. 原先此处硬编码 8 个顶级导航（首页/学习工作台/知识库▾/学习▾/社区▾/AI助手▾/成长▾/我的▾）
 *     共 30 个二级项，Header 承担了本该由导航承担的职责（§26/§27）。
 *     现全部下沉到 CSidebar，Header 只保留：Logo + 搜索 + AI 快捷 + 通知 + 用户菜单。
 *  2. 导航数据改由 @/config/navigation.ts 统一提供，杜绝「Sidebar 一套、Router 一套」。
 *  3. Logo 文案由「知识库」统一为「KnowFlow」（与后台侧栏一致，§22 命名统一）。
 *  4. 移动端汉堡按钮改为向父级 CLayout 派发 toggle-sidebar，打开侧边栏抽屉。
 *  5. 所有颜色走 --kb-* 令牌（§56），深色模式自动跟随。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import PomodoroFloating from '@/components/ui/PomodoroFloating.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useBackgroundStore } from '@/stores/background'
import { useCommandPalette } from '@/composables/useCommandPalette'
import { notify } from '@/utils/toast'

defineProps<{ drawerOpen?: boolean }>()

defineEmits<{ 'toggle-sidebar': [] }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const palette = useCommandPalette()
const notificationStore = useNotificationStore()
const bgStore = useBackgroundStore()

const navStyle = computed(() => {
  if (bgStore.isActive) {
    return {
      background: 'rgba(255, 255, 255, 0.72)',
      borderColor: 'rgba(255, 255, 255, 0.30)',
      backdropFilter: 'blur(16px)',
      WebkitBackdropFilter: 'blur(16px)',
    }
  }
  return { background: 'var(--kb-card)', borderColor: 'var(--kb-border)' }
})

const showNotifications = ref(false)
const showUserMenu = ref(false)
const searchKw = ref('')

const isLoggedIn = computed(() => auth.isLoggedIn)
const isAdmin = computed(() => auth.isAdmin)
const displayName = computed(() => {
  const u = auth.user
  return u?.nickname || u?.username || '用户'
})
const initials = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || 'U'
  return name.slice(0, 2).toUpperCase()
})
const unreadCount = computed(() => notificationStore.unreadCount)

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(path + '/')
}

function goTo(path: string) {
  showNotifications.value = false
  showUserMenu.value = false
  router.push(path)
}

function submitSearch() {
  const kw = searchKw.value.trim()
  router.push({ path: '/search', query: kw ? { q: kw } : {} })
  searchKw.value = ''
}

function toggleNotifications() {
  showUserMenu.value = false
  showNotifications.value = !showNotifications.value
  if (showNotifications.value && isLoggedIn.value) {
    notificationStore.fetchList({ pageNum: 1, pageSize: 20 }).catch(() => {
      // 通知列表加载失败静默处理，不打扰用户
    })
  }
}

function toggleUserMenu() {
  showNotifications.value = false
  showUserMenu.value = !showUserMenu.value
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
  goTo('/notifications')
}

function handleLogout() {
  showUserMenu.value = false
  notificationStore.reset()
  auth.logout()
  notify('已退出登录', 'success')
  goTo('/login')
}

function getNotifIcon(type: string): string {
  switch (type) {
    case 'SYSTEM': return 'info'
    case 'LEARNING': return 'bell'
    case 'COMMUNITY': return 'message-circle'
    default: return 'bell'
  }
}

function getNotifBg(type: string): string {
  switch (type) {
    case 'SYSTEM': return 'bg-[var(--kb-primary-subtle)]'
    case 'LEARNING': return 'bg-[var(--kb-success-subtle)]'
    case 'COMMUNITY': return 'bg-[var(--kb-highlight-subtle)]'
    default: return 'bg-[var(--kb-muted)]'
  }
}

function getNotifColor(type: string): string {
  switch (type) {
    case 'SYSTEM': return 'text-[var(--kb-state-info)]'
    case 'LEARNING': return 'text-[var(--kb-state-success)]'
    case 'COMMUNITY': return 'text-[var(--kb-highlight)]'
    default: return 'text-[var(--kb-muted-foreground)]'
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

function openPalette() {
  palette.open()
}

watch(isLoggedIn, (loggedIn) => {
  if (loggedIn) notificationStore.fetchUnreadCount()
  else notificationStore.reset()
})

watch(() => route.fullPath, () => {
  showNotifications.value = false
  showUserMenu.value = false
})

onMounted(() => {
  if (isLoggedIn.value) notificationStore.fetchUnreadCount()
})
</script>

<style scoped>
.is-ai-active {
  background: var(--kb-primary-soft);
  border-color: var(--kb-primary) !important;
}

.user-menu-title {
  padding: 6px 12px 2px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.user-menu-sep {
  height: 0;
  margin: 4px 0;
  border-top: 1px solid;
}
</style>
