<template>
  <header
    class="h-14 shrink-0 flex items-center justify-between px-4 sm:px-6 border-b"
    style="background: var(--kb-card); border-color: var(--kb-border);"
  >
    <!-- Left: mobile toggle + search -->
    <div class="flex items-center gap-3 flex-1 min-w-0 max-w-md">
      <button
        type="button"
        class="w-9 h-9 rounded-lg flex items-center justify-center border transition-colors hover:bg-gray-50 lg:hidden"
        style="border-color: var(--kb-border);"
        @click="$emit('toggle-sidebar')"
        aria-label="切换侧边栏"
      >
        <Icon name="menu" :size="18" class="text-gray-600" />
      </button>

      <div class="relative flex-1 min-w-0">
        <Icon name="search" :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索功能、文档或用户..."
          class="topbar-search"
          @keyup.enter="handleSearch"
          aria-label="搜索"
        />
      </div>
    </div>

    <!-- Right: notifications + admin -->
    <div class="flex items-center gap-4 shrink-0 ml-4 sm:ml-6">
      <button
        type="button"
        class="relative p-2 rounded-lg transition-colors hover:bg-gray-50"
        style="color: var(--kb-sidebar-foreground);"
        @click="toggleNotifications"
        :aria-label="`消息通知，${unreadCount} 条未读`"
      >
        <Icon name="bell" :size="20" />
        <span
          v-if="unreadCount > 0"
          class="absolute top-1.5 right-1.5 w-2 h-2 rounded-full"
          style="background: var(--kb-destructive);"
        ></span>
      </button>

      <div class="flex items-center gap-2.5 pl-4 border-l" style="border-color: var(--kb-border);">
        <div
          class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold shrink-0"
          style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
        >{{ initials }}</div>
        <div class="hidden sm:block min-w-0">
          <p class="text-[13px] font-medium truncate text-gray-800">{{ displayName }}</p>
          <p class="text-[11px] truncate text-gray-500">{{ roleLabel }}</p>
        </div>
        <Icon name="chevron-down" :size="14" class="text-gray-400 hidden sm:block" />
      </div>
    </div>

    <!-- Notification Panel -->
    <div v-if="showNotifications" class="fixed inset-0 z-40" @click="showNotifications = false"></div>
    <div
      v-if="showNotifications"
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
  </header>
</template>

<script setup lang="ts">
// 布局组件：后台顶栏，负责管理员搜索、通知面板与用户信息区。
import { ref, computed, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { useAuthStore } from '@/stores/auth';
import { useNotificationStore } from '@/stores/notification';
import { notify } from '@/utils/toast';

defineEmits<{
  'toggle-sidebar': [];
}>();

const router = useRouter();
const auth = useAuthStore();
const notificationStore = useNotificationStore();

const searchQuery = ref('');
const showNotifications = ref(false);

const displayName = computed(() => {
  const u = auth.user;
  return u?.nickname || u?.username || '管理员';
});
const roleLabel = computed(() => (auth.isAdmin ? '系统管理员' : '用户'));
const initials = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || 'A';
  return name.slice(0, 2).toUpperCase();
});
const unreadCount = computed(() => notificationStore.unreadCount);

function handleSearch() {
  if (!searchQuery.value.trim()) return;
  router.push({ path: '/search', query: { q: searchQuery.value.trim() } });
}

function goTo(path: string) {
  showNotifications.value = false;
  router.push(path);
}

function toggleNotifications() {
  showNotifications.value = !showNotifications.value;
  if (showNotifications.value) {
    notificationStore.fetchList({ pageNum: 1, pageSize: 20 }).catch((e) => console.error(e));
  }
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

watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      notificationStore.fetchUnreadCount();
    } else {
      notificationStore.reset();
    }
  },
);

onMounted(() => {
  if (auth.isLoggedIn) {
    notificationStore.fetchUnreadCount();
  }
});
</script>
