<template>
  <header
    class="fixed top-0 left-0 right-0 z-50 h-14 flex items-center px-4 sm:px-6 border-b"
    style="background: var(--kb-card); border-color: var(--kb-border);"
  >
    <!-- Left: Logo -->
    <router-link
      to="/"
      class="flex items-center gap-2 shrink-0"
      @click="closeAll()"
    >
      <Icon name="book-open" :size="20" class="text-primary-500" />
      <span class="text-sm font-semibold text-primary-600 hidden sm:inline">知识库</span>
    </router-link>

    <!-- Center: Navigation (desktop) -->
    <nav class="hidden lg:flex flex-1 items-center justify-center gap-6">
      <router-link
        v-for="item in navLinks"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-1.5 text-sm font-medium transition-colors"
        :class="isNavActive(item.path) ? 'text-primary-600 font-semibold' : 'text-gray-700 hover:text-primary-600'"
      >
        <Icon :name="item.icon" :size="16" />
        {{ item.label }}
      </router-link>

      <!-- AI Assistant Dropdown -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-1 text-sm font-medium transition-colors"
          :class="openDropdown === 'ai' ? 'text-primary-600' : 'text-gray-700 hover:text-primary-600'"
          @click="toggleDropdown('ai')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'ai'"
        >
          AI 助手
          <Icon name="chevron-down" :size="14" :class="openDropdown === 'ai' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'ai' }" role="menu">
          <router-link
            v-for="it in aiMenu"
            :key="it.path"
            :to="it.path"
            class="nav-link"
            @click="openDropdown = ''"
          >
            <Icon :name="it.icon" :size="16" class="text-gray-400" />
            {{ it.label }}
          </router-link>
        </div>
      </div>

      <!-- Personal Dropdown -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-1 text-sm font-medium transition-colors"
          :class="openDropdown === 'personal' ? 'text-primary-600' : 'text-gray-700 hover:text-primary-600'"
          @click="toggleDropdown('personal')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'personal'"
        >
          <Icon name="user" :size="16" />
          个人
          <Icon name="chevron-down" :size="14" :class="openDropdown === 'personal' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'personal' }" role="menu">
          <router-link
            v-for="it in personalMenu"
            :key="it.path"
            :to="it.path"
            class="nav-link"
            @click="openDropdown = ''"
          >
            <Icon :name="it.icon" :size="16" class="text-gray-400" />
            {{ it.label }}
          </router-link>
        </div>
      </div>
    </nav>

    <!-- Click-away overlay for dropdowns -->
    <div v-if="openDropdown" class="fixed inset-0 z-40" @click="openDropdown = ''"></div>

    <!-- Right: Search + Notifications + Avatar -->
    <div class="flex items-center gap-2 sm:gap-3 shrink-0 ml-auto lg:ml-0">
      <!-- Search (desktop) -->
      <button
        type="button"
        class="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-lg border h-9 w-48 lg:w-64 transition-colors hover:border-primary-400"
        style="background: var(--kb-background); border-color: var(--kb-border);"
        @click="goTo('/search')"
        aria-label="搜索"
      >
        <Icon name="search" :size="16" class="text-gray-400" />
        <span class="text-sm text-gray-400 truncate">搜索知识库...</span>
        <span
          class="ml-auto shrink-0 text-xs px-1.5 py-0.5 rounded"
          style="background: var(--kb-muted); color: var(--kb-muted-foreground);"
        >Cmd+K</span>
      </button>

      <!-- Search (mobile icon) -->
      <button
        type="button"
        class="md:hidden w-9 h-9 rounded-lg flex items-center justify-center border transition-colors hover:bg-gray-50"
        style="border-color: var(--kb-border);"
        @click="goTo('/search')"
        aria-label="搜索"
      >
        <Icon name="search" :size="18" class="text-gray-500" />
      </button>

      <!-- Notifications -->
      <button
        v-if="isLoggedIn"
        type="button"
        class="relative w-9 h-9 rounded-lg flex items-center justify-center transition-colors hover:bg-gray-50"
        style="color: var(--kb-sidebar-foreground);"
        @click="toggleNotifications"
        :aria-label="`消息通知，${unreadCount} 条未读`"
      >
        <Icon name="bell" :size="18" />
        <span
          v-if="unreadCount > 0"
          class="absolute top-1.5 right-1.5 w-2 h-2 rounded-full"
          style="background: var(--kb-destructive);"
        ></span>
      </button>

      <!-- Avatar / Login -->
      <button
        v-if="isLoggedIn"
        type="button"
        class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold shrink-0 transition-opacity hover:opacity-90"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
        @click="toggleUserMenu"
        :aria-label="`用户菜单：${displayName}`"
      >
        {{ initials }}
      </button>
      <button
        v-else
        type="button"
        class="px-3 h-9 rounded-lg text-sm font-medium transition-colors"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
        @click="goTo('/login')"
      >
        登录
      </button>

      <!-- Mobile nav toggle -->
      <button
        type="button"
        class="lg:hidden w-9 h-9 rounded-lg flex items-center justify-center transition-colors hover:bg-gray-50"
        @click="mobileOpen = !mobileOpen"
        aria-label="菜单"
      >
        <Icon :name="mobileOpen ? 'x' : 'menu'" :size="20" class="text-gray-600" />
      </button>
    </div>

    <!-- Notification Panel -->
    <div
      v-if="isLoggedIn && showNotifications"
      class="fixed inset-0 z-40"
      @click="showNotifications = false"
    ></div>
    <div
      v-if="isLoggedIn && showNotifications"
      class="fixed right-2 left-2 sm:left-auto sm:right-4 top-16 sm:w-80 rounded-lg shadow-xl z-50 border"
      style="background: var(--kb-card); border-color: var(--kb-border);"
    >
      <div class="flex items-center justify-between px-4 py-3 border-b" style="border-color: var(--kb-border);">
        <h3 class="font-semibold text-gray-800 text-sm">通知</h3>
        <button
          v-if="unreadCount > 0"
          type="button"
          class="text-xs text-primary-600 hover:underline"
          @click="handleMarkAllRead"
        >全部已读</button>
      </div>
      <div class="max-h-96 overflow-y-auto">
        <div
          v-for="notification in notificationStore.unreadList.slice(0, 5)"
          :key="notification.id"
          class="px-4 py-3 hover:bg-gray-50 border-b cursor-pointer transition-colors"
          style="border-color: var(--kb-muted);"
          @click="handleNotificationClick(notification.id)"
        >
          <div class="flex items-start gap-3">
            <div class="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0" :class="getNotifBg(notification.type)">
              <Icon :name="getNotifIcon(notification.type)" :size="16" :class="getNotifColor(notification.type)" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-medium text-gray-800 text-sm">{{ notification.title }}</div>
              <div class="text-gray-500 text-xs mt-0.5 line-clamp-2">{{ notification.content }}</div>
              <div class="text-gray-400 text-xs mt-1">{{ formatTime(notification.createTime) }}</div>
            </div>
          </div>
        </div>
        <div v-if="notificationStore.unreadList.length === 0" class="px-4 py-8 text-center text-sm text-gray-400">
          暂无未读通知
        </div>
      </div>
      <div class="px-4 py-2 border-t" style="border-color: var(--kb-border);">
        <button
          type="button"
          class="w-full text-center text-sm text-primary-600 hover:underline"
          @click="goTo('/notifications')"
        >查看全部通知</button>
      </div>
    </div>

    <!-- User Menu -->
    <div v-if="showUserMenu" class="fixed inset-0 z-40" @click="showUserMenu = false"></div>
    <div
      v-if="showUserMenu"
      class="absolute right-2 sm:right-6 top-16 w-56 rounded-lg shadow-lg py-2 z-50 border"
      style="background: var(--kb-card); border-color: var(--kb-border);"
      role="menu"
    >
      <div class="px-4 py-3 border-b" style="border-color: var(--kb-border);">
        <div class="text-sm font-medium text-gray-800">{{ displayName }}</div>
        <div class="text-xs text-gray-500 mt-0.5">{{ displayEmail }}</div>
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
          <Icon :name="item.icon" :size="16" class="text-gray-400" />
          {{ item.label }}
        </button>
      </div>
      <div class="border-t pt-1" style="border-color: var(--kb-border);">
        <button
          type="button"
          role="menuitem"
          class="w-full flex items-center gap-3 px-4 py-2 text-sm text-danger-600 hover:bg-danger-50 transition-colors"
          @click="handleLogout"
        >
          <Icon name="log-out" :size="16" />
          退出登录
        </button>
      </div>
    </div>

    <!-- Mobile Nav Drawer -->
    <div v-if="mobileOpen" class="fixed inset-0 z-40 bg-black/40 lg:hidden" @click="mobileOpen = false"></div>
    <aside
      class="fixed right-0 top-0 h-screen w-72 max-w-[80vw] z-50 lg:hidden overflow-y-auto transition-transform duration-300 border-l"
      style="background: var(--kb-card); border-color: var(--kb-border);"
      :class="mobileOpen ? 'translate-x-0' : 'translate-x-full'"
    >
      <div class="flex items-center justify-between px-4 h-14 border-b" style="border-color: var(--kb-border);">
        <span class="font-semibold text-gray-800">导航</span>
        <button type="button" @click="mobileOpen = false" aria-label="关闭">
          <Icon name="x" :size="20" class="text-gray-500" />
        </button>
      </div>
      <nav class="p-3 space-y-1">
        <router-link
          v-for="item in navLinks"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors"
          @click="mobileOpen = false"
        >
          <Icon :name="item.icon" :size="18" class="text-gray-400" />
          {{ item.label }}
        </router-link>
        <div class="pt-2 pb-1 px-3 text-xs font-medium uppercase tracking-wider text-gray-400">AI 助手</div>
        <router-link
          v-for="it in aiMenu"
          :key="it.path"
          :to="it.path"
          class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors"
          @click="mobileOpen = false"
        >
          <Icon :name="it.icon" :size="18" class="text-gray-400" />
          {{ it.label }}
        </router-link>
        <div class="pt-2 pb-1 px-3 text-xs font-medium uppercase tracking-wider text-gray-400">个人</div>
        <router-link
          v-for="it in personalMenu"
          :key="it.path"
          :to="it.path"
          class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors"
          @click="mobileOpen = false"
        >
          <Icon :name="it.icon" :size="18" class="text-gray-400" />
          {{ it.label }}
        </router-link>
      </nav>
    </aside>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { useAuthStore } from '@/stores/auth';
import { useNotificationStore } from '@/stores/notification';
import { notify } from '@/utils/toast';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const notificationStore = useNotificationStore();

const openDropdown = ref('');
const showNotifications = ref(false);
const showUserMenu = ref(false);
const mobileOpen = ref(false);

const isLoggedIn = computed(() => auth.isLoggedIn);
const displayName = computed(() => {
  const u = auth.user;
  return u?.nickname || u?.username || '用户';
});
const displayEmail = computed(() => auth.user?.email || auth.user?.username || '');
const initials = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || 'U';
  return name.slice(0, 2).toUpperCase();
});
const unreadCount = computed(() => notificationStore.unreadCount);

interface NavLink {
  path: string;
  label: string;
  icon: string;
}

const navLinks: NavLink[] = [
  { path: '/', label: '首页', icon: 'home' },
  { path: '/knowledge', label: '知识库', icon: 'book-open' },
  { path: '/learning/center', label: '学习中心', icon: 'graduation-cap' },
];

const aiMenu: NavLink[] = [
  { path: '/chat', label: '智能问答', icon: 'message-circle' },
  { path: '/learning/writing', label: '智能写作', icon: 'pen-tool' },
  { path: '/learning/quiz', label: '智能出题', icon: 'file-question' },
];

const personalMenu: NavLink[] = [
  { path: '/profile', label: '个人中心', icon: 'user' },
  { path: '/favorites', label: '收藏夹', icon: 'bookmark' },
  { path: '/notes', label: '笔记管理', icon: 'notebook-pen' },
  { path: '/mistakes', label: '错误本', icon: 'alert-circle' },
  { path: '/learning/pomodoro', label: '番茄钟专注', icon: 'timer' },
  { path: '/notifications', label: '消息中心', icon: 'bell' },
  { path: '/community', label: '社区讨论', icon: 'users' },
];

const userMenuItems: NavLink[] = [
  { path: '/profile', label: '个人中心', icon: 'user' },
  { path: '/learning/center', label: '学习中心', icon: 'graduation-cap' },
  { path: '/notifications', label: '消息中心', icon: 'bell' },
  { path: '/favorites', label: '我的收藏', icon: 'heart' },
];

function isNavActive(path: string): boolean {
  if (path === '/') return route.path === '/';
  return route.path === path || route.path.startsWith(path + '/');
}

function toggleDropdown(key: string) {
  openDropdown.value = openDropdown.value === key ? '' : key;
}

function closeAll() {
  openDropdown.value = '';
  showNotifications.value = false;
  showUserMenu.value = false;
}

function goTo(path: string) {
  showNotifications.value = false;
  showUserMenu.value = false;
  mobileOpen.value = false;
  openDropdown.value = '';
  router.push(path);
}

function toggleNotifications() {
  showUserMenu.value = false;
  showNotifications.value = !showNotifications.value;
  if (showNotifications.value && isLoggedIn.value) {
    notificationStore.fetchList({ pageNum: 1, pageSize: 20 }).catch((e) => console.error(e));
  }
}

function toggleUserMenu() {
  showNotifications.value = false;
  showUserMenu.value = !showUserMenu.value;
}

async function handleMarkAllRead() {
  try {
    await notificationStore.markAllAsRead();
    notify('已全部标为已读', 'success');
  } catch (e) {
    console.error(e);
    notify('操作失败', 'error');
  }
}

async function handleNotificationClick(id: number) {
  try {
    await notificationStore.markAsRead(id);
  } catch (e) {
    console.error(e);
  }
  showNotifications.value = false;
  goTo('/notifications');
}

function handleLogout() {
  showUserMenu.value = false;
  notificationStore.reset();
  auth.logout();
  notify('已退出登录', 'success');
  goTo('/login');
}

function getNotifIcon(type: string): string {
  switch (type) {
    case 'SYSTEM': return 'info';
    case 'LEARNING': return 'bell';
    case 'COMMUNITY': return 'message-circle';
    default: return 'bell';
  }
}

function getNotifBg(type: string): string {
  switch (type) {
    case 'SYSTEM': return 'bg-blue-50';
    case 'LEARNING': return 'bg-green-50';
    case 'COMMUNITY': return 'bg-purple-50';
    default: return 'bg-gray-100';
  }
}

function getNotifColor(type: string): string {
  switch (type) {
    case 'SYSTEM': return 'text-primary-500';
    case 'LEARNING': return 'text-success-500';
    case 'COMMUNITY': return 'text-purple-500';
    default: return 'text-gray-500';
  }
}

function formatTime(time?: string): string {
  if (!time) return '';
  const date = new Date(time);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / (1000 * 60));
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 7) return `${days}天前`;
  return date.toLocaleDateString('zh-CN');
}

function onKeyDown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault();
    goTo('/search');
  }
}

watch(isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    notificationStore.fetchUnreadCount();
  } else {
    notificationStore.reset();
  }
});

watch(() => route.fullPath, () => {
  closeAll();
  mobileOpen.value = false;
});

onMounted(() => {
  window.addEventListener('keydown', onKeyDown);
  if (isLoggedIn.value) {
    notificationStore.fetchUnreadCount();
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown);
});
</script>
