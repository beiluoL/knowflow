<template>
  <header
    class="fixed top-0 left-0 right-0 z-50 h-14 flex items-center px-4 sm:px-6 border-b"
    style="background: var(--kb-card); border-color: var(--kb-border);"
  >
    <!-- Left: Logo -->
    <router-link
      to="/"
      class="flex items-center shrink-0"
      style="color: var(--kb-primary); gap: var(--kb-nav-gap);"
      @click="closeAll()"
    >
      <Icon name="icon-AIbeikezhushou" size="xl" />
      <span class="hidden sm:inline" :style="{ fontSize: 'var(--kb-logo-text-fs)', fontWeight: 'var(--kb-logo-text-fw)' }">知识库</span>
    </router-link>

    <!-- Center: Navigation (desktop) -->
    <nav class="hidden lg:flex items-center gap-6 ml-8">
      <!-- 首页 -->
      <router-link
        to="/"
        class="flex items-center gap-2 transition-colors"
        :class="isNavActive('/') ? '' : 'hover:opacity-80'"
        :style="{ color: isNavActive('/') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isNavActive('/') ? 600 : 'var(--kb-nav-text-fw)' }"
      >
        <Icon name="home" size="md" />
        <span>首页</span>
      </router-link>

      <!-- 任务中心（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: openDropdown === 'task' ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('task')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'task'"
        >
          <Icon name="target" size="md" />
          <span>任务中心</span>
          <Icon name="chevron-down" size="sm" :class="openDropdown === 'task' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'task' }" role="menu">
          <router-link
            v-for="it in taskMenu"
            :key="it.path"
            :to="it.path"
            class="flex items-center gap-2 px-3 py-2"
            :style="{ fontSize: 'var(--kb-dropdown-text-fs)' }"
            @click="openDropdown = ''"
          >
            <Icon :name="it.icon" size="md" style="color: var(--kb-muted-foreground);" />
            {{ it.label }}
          </router-link>
        </div>
      </div>

      <!-- 知识库（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: openDropdown === 'knowledge' ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('knowledge')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'knowledge'"
        >
          <Icon name="icon-AIbeikezhushou" size="md" />
          <span>知识库</span>
          <Icon name="chevron-down" size="sm" :class="openDropdown === 'knowledge' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'knowledge' }" role="menu">
          <router-link
            v-for="it in knowledgeMenu"
            :key="it.path"
            :to="it.path"
            class="flex items-center gap-2 px-3 py-2"
            :style="{ fontSize: 'var(--kb-dropdown-text-fs)' }"
            @click="openDropdown = ''"
          >
            <Icon :name="it.icon" size="md" style="color: var(--kb-muted-foreground);" />
            {{ it.label }}
          </router-link>
        </div>
      </div>

      <!-- 学习中心（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: openDropdown === 'learning' ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('learning')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'learning'"
        >
          <Icon name="graduation-cap" size="md" />
          <span>学习中心</span>
          <Icon name="chevron-down" size="sm" :class="openDropdown === 'learning' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'learning' }" role="menu">
          <router-link
            v-for="it in learningMenu"
            :key="it.path"
            :to="it.path"
            class="flex items-center gap-2 px-3 py-2"
            :style="{ fontSize: 'var(--kb-dropdown-text-fs)' }"
            @click="openDropdown = ''"
          >
            <Icon :name="it.icon" size="md" style="color: var(--kb-muted-foreground);" />
            {{ it.label }}
          </router-link>
        </div>
      </div>

      <!-- AI 助手（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: openDropdown === 'ai' ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('ai')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'ai'"
        >
          <Icon name="icon-agent" size="md" />
          <span>AI 助手</span>
          <Icon name="chevron-down" size="sm" :class="openDropdown === 'ai' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'ai' }" role="menu">
          <router-link
            v-for="it in aiMenu"
            :key="it.path"
            :to="it.path"
            class="flex items-center gap-2 px-3 py-2"
            :style="{ fontSize: 'var(--kb-dropdown-text-fs)' }"
            @click="openDropdown = ''"
          >
            <Icon :name="it.icon" size="md" style="color: var(--kb-muted-foreground);" />
            {{ it.label }}
          </router-link>
        </div>
      </div>

      <!-- 社区讨论 -->
      <router-link
        to="/community"
        class="flex items-center gap-2 transition-colors"
        :class="isNavActive('/community') ? '' : 'hover:opacity-80'"
        :style="{ color: isNavActive('/community') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isNavActive('/community') ? 600 : 'var(--kb-nav-text-fw)' }"
      >
        <Icon name="users" size="md" />
        <span>社区讨论</span>
      </router-link>

      <!-- 学习小组 -->
      <router-link
        to="/study-group"
        class="flex items-center gap-2 transition-colors"
        :class="isNavActive('/study-group') ? '' : 'hover:opacity-80'"
        :style="{ color: isNavActive('/study-group') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isNavActive('/study-group') ? 600 : 'var(--kb-nav-text-fw)' }"
      >
        <Icon name="message-circle" size="md" />
        <span>学习小组</span>
      </router-link>

      <!-- 消息（单聊） -->
      <router-link
        to="/messages"
        class="flex items-center gap-2 transition-colors"
        :class="isNavActive('/messages') ? '' : 'hover:opacity-80'"
        :style="{ color: isNavActive('/messages') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isNavActive('/messages') ? 600 : 'var(--kb-nav-text-fw)' }"
      >
        <Icon name="message-square" size="md" />
        <span>消息</span>
      </router-link>

      <!-- 个人（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: openDropdown === 'personal' ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('personal')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'personal'"
        >
          <Icon name="user" size="md" />
          <span>个人</span>
          <Icon name="chevron-down" size="sm" :class="openDropdown === 'personal' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'personal' }" role="menu">
          <router-link
            v-for="it in personalMenu"
            :key="it.path"
            :to="it.path"
            class="flex items-center gap-2 px-3 py-2"
            :style="{ fontSize: 'var(--kb-dropdown-text-fs)' }"
            @click="openDropdown = ''"
          >
            <Icon :name="it.icon" size="md" style="color: var(--kb-muted-foreground);" />
            {{ it.label }}
          </router-link>
        </div>
      </div>
    </nav>

    <!-- Click-away overlay for dropdowns -->
    <div v-if="openDropdown" class="fixed inset-0 z-40" @click="openDropdown = ''"></div>

    <!-- Right: Search + Notifications + Avatar -->
    <div class="flex items-center gap-3 shrink-0 ml-auto">
      <!-- Search (desktop) -->
      <div class="relative hidden md:block w-48 lg:w-56">
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
          class="absolute right-3 top-1/2 -translate-y-1/2 px-1.5 py-0.5 rounded"
          style="background: var(--kb-muted); color: var(--kb-muted-foreground); font-size: var(--kb-fs-xs); font-weight: var(--kb-fw-xs);"
        >⌘K</span>
      </div>

      <!-- Search (mobile icon) -->
      <button
        type="button"
        class="md:hidden w-9 h-9 rounded-lg flex items-center justify-center border transition-colors hover:bg-gray-50"
        style="border-color: var(--kb-border);"
        @click="goTo('/search')"
        aria-label="搜索"
      >
        <Icon name="search" size="lg" style="color: var(--kb-muted-foreground);" />
      </button>

      <!-- Pomodoro（macOS 风格导航栏小图标） -->
      <PomodoroFloating v-if="isLoggedIn" />

      <!-- Notifications -->
      <button
        v-if="isLoggedIn"
        type="button"
        class="relative p-2 rounded-lg transition-colors hover:bg-gray-50"
        style="color: var(--kb-muted-foreground);"
        @click="toggleNotifications"
        :aria-label="`消息通知，${unreadCount} 条未读`"
      >
        <Icon name="bell" size="xl" />
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
        class="w-8 h-8 rounded-full flex items-center justify-center shrink-0 transition-opacity hover:opacity-90"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground); font-size: var(--kb-fs-caption); font-weight: 600;"
        @click="toggleUserMenu"
        :aria-label="`用户菜单：${displayName}`"
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

      <!-- Mobile nav toggle -->
      <button
        type="button"
        class="lg:hidden w-9 h-9 rounded-lg flex items-center justify-center transition-colors hover:bg-gray-50"
        @click="mobileOpen = !mobileOpen"
        aria-label="菜单"
      >
        <Icon :name="mobileOpen ? 'x' : 'menu'" size="xl" style="color: var(--kb-sidebar-foreground);" />
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
          class="px-4 py-3 hover:bg-gray-50 border-b cursor-pointer transition-colors"
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

    <!-- User Menu (avatar dropdown) -->
    <div v-if="showUserMenu" class="fixed inset-0 z-40" @click="showUserMenu = false"></div>
    <div
      v-if="showUserMenu"
      class="absolute right-2 sm:right-6 top-16 min-w-[180px] rounded-lg shadow-lg py-1 z-50 border"
      style="background: var(--kb-card); border-color: var(--kb-border);"
      role="menu"
    >
      <button
        type="button"
        role="menuitem"
        class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-gray-50"
        style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);"
        @click="goTo('/profile')"
      >
        <Icon name="user-circle" size="md" style="color: var(--kb-muted-foreground);" />
        个人设置
      </button>
      <button
        v-if="isAdmin"
        type="button"
        role="menuitem"
        class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-gray-50"
        style="color: var(--kb-primary); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);"
        @click="goTo('/admin/overview')"
      >
        <Icon name="settings" size="md" />
        管理后台
      </button>
      <div class="border-t my-1" style="border-color: var(--kb-border);"></div>
      <button
        type="button"
        role="menuitem"
        class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-gray-50"
        style="color: var(--kb-destructive); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);"
        @click="handleLogout"
      >
        <Icon name="log-out" size="md" />
        退出登录
      </button>
    </div>

    <!-- Mobile Nav Drawer -->
    <div v-if="mobileOpen" class="fixed inset-0 z-40 bg-black/40 lg:hidden" @click="mobileOpen = false"></div>
    <aside
      class="fixed right-0 top-0 h-screen w-72 max-w-[80vw] z-50 lg:hidden overflow-y-auto transition-transform duration-300 border-l"
      style="background: var(--kb-card); border-color: var(--kb-border);"
      :class="mobileOpen ? 'translate-x-0' : 'translate-x-full'"
    >
      <div class="flex items-center justify-between px-4 h-14 border-b" style="border-color: var(--kb-border);">
        <span style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); font-weight: 600;">导航</span>
        <button type="button" @click="mobileOpen = false" aria-label="关闭">
          <Icon name="x" size="xl" style="color: var(--kb-muted-foreground);" />
        </button>
      </div>
      <nav class="p-3 space-y-1">
        <router-link
          v-for="item in mobileLinks"
          :key="item.path"
          :to="item.path"
          class="flex items-center px-3 py-2.5 rounded-lg transition-colors hover:bg-gray-50"
          style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); gap: var(--kb-nav-gap);"
          @click="mobileOpen = false"
        >
          <Icon :name="item.icon" size="lg" style="color: var(--kb-muted-foreground);" />
          {{ item.label }}
        </router-link>
        <div class="pt-2 pb-1 px-3" style="color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption); font-weight: var(--kb-fw-caption); text-transform: uppercase; letter-spacing: 0.05em;">学习中心</div>
        <router-link
          v-for="it in learningMenu"
          :key="it.path"
          :to="it.path"
          class="flex items-center px-3 py-2.5 rounded-lg transition-colors hover:bg-gray-50"
          style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); gap: var(--kb-nav-gap);"
          @click="mobileOpen = false"
        >
          <Icon :name="it.icon" size="lg" style="color: var(--kb-muted-foreground);" />
          {{ it.label }}
        </router-link>
        <div class="pt-2 pb-1 px-3" style="color: var(--kb-muted-foreground); font-size: var(--kb-fs-caption); font-weight: var(--kb-fw-caption); text-transform: uppercase; letter-spacing: 0.05em;">个人</div>
        <router-link
          v-for="it in personalMenu"
          :key="it.path"
          :to="it.path"
          class="flex items-center px-3 py-2.5 rounded-lg transition-colors hover:bg-gray-50"
          style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); gap: var(--kb-nav-gap);"
          @click="mobileOpen = false"
        >
          <Icon :name="it.icon" size="lg" style="color: var(--kb-muted-foreground);" />
          {{ it.label }}
        </router-link>
      </nav>
    </aside>
  </header>
</template>

<script setup lang="ts">
// 布局组件：前台导航顶栏（与设计稿统一入口/首页导航结构对齐）。
// 导航分组：首页 / 统一入口 / 知识库(下拉) / 学习中心(下拉) / AI助手(下拉) / 社区讨论 / 个人(下拉)
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import PomodoroFloating from '@/components/ui/PomodoroFloating.vue';
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
const searchKw = ref('');

const isLoggedIn = computed(() => auth.isLoggedIn);
const isAdmin = computed(() => auth.isAdmin);
const displayName = computed(() => {
  const u = auth.user;
  return u?.nickname || u?.username || '用户';
});
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

const taskMenu: NavLink[] = [
  { path: '/tasks', label: '任务中心', icon: 'target' },
  { path: '/check-in', label: '每日打卡', icon: 'calendar-check' },
  { path: '/achievements', label: '成就系统', icon: 'trophy' },
  { path: '/kb-titles', label: '知识库称号', icon: 'award' },
];

const mobileLinks: NavLink[] = [
  { path: '/', label: '首页', icon: 'home' },
  { path: '/knowledge', label: '知识库', icon: 'icon-AIbeikezhushou' },
  { path: '/tasks', label: '任务中心', icon: 'target' },
  { path: '/check-in', label: '每日打卡', icon: 'calendar-check' },
  { path: '/achievements', label: '成就系统', icon: 'trophy' },
  { path: '/kb-titles', label: '知识库称号', icon: 'award' },
  { path: '/notes', label: '笔记管理', icon: 'notebook-pen' },
  { path: '/categories', label: '分类浏览', icon: 'folder-tree' },
  { path: '/search', label: '搜索知识', icon: 'search' },
  { path: '/learning/knowledge-graph', label: '知识图谱', icon: 'icon-zhishitupubaocun' },
  { path: '/challenge', label: '编程挑战', icon: 'rocket' },
  { path: '/community', label: '社区讨论', icon: 'users' },
  { path: '/study-group', label: '学习小组', icon: 'message-circle' },
  { path: '/messages', label: '消息', icon: 'message-square' },
  { path: '/chat', label: '智能问答', icon: 'icon-rengongzhineng1' },
];

const knowledgeMenu: NavLink[] = [
  { path: '/knowledge', label: '知识库', icon: 'icon-AIbeikezhushou' },
  { path: '/categories', label: '分类浏览', icon: 'folder-tree' },
  { path: '/search', label: '搜索知识', icon: 'icon-wendangsousuo' },
  { path: '/learning/knowledge-graph', label: '知识图谱', icon: 'icon-zhishitupubaocun' },
];

const learningMenu: NavLink[] = [
  { path: '/learning/center', label: '学习中心', icon: 'icon-xuexizhongxin' },
  { path: '/learning/paths', label: '学习路径', icon: 'route' },
  { path: '/learning/pomodoro', label: '番茄钟专注', icon: 'timer' },
  { path: '/learning/code-practice', label: '代码练习', icon: 'code' },
  { path: '/challenge', label: '编程挑战', icon: 'rocket' },
  { path: '/learning/flashcards', label: '学习闪卡', icon: 'layers' },
  { path: '/learning/my-flashcards', label: '我的闪卡', icon: 'bookmark' },
  { path: '/learning/review', label: '复习计划', icon: 'calendar-check' },
  { path: '/learning/mode', label: '沉浸学习', icon: 'moon' },
];

const aiMenu: NavLink[] = [
  { path: '/chat', label: '智能问答', icon: 'icon-rengongzhineng1' },
  { path: '/learning/writing', label: '智能写作', icon: 'wand-2' },
  { path: '/learning/quiz', label: '智能测验', icon: 'icon-kaoshi' },
];

const personalMenu: NavLink[] = [
  { path: '/profile', label: '个人中心', icon: 'user-circle' },
  { path: '/favorites', label: '收藏夹', icon: 'bookmark' },
  { path: '/notes', label: '笔记管理', icon: 'notebook-pen' },
  { path: '/mistakes', label: '错误本', icon: 'alert-circle' },
  { path: '/learning/report', label: '学习报告', icon: 'bar-chart-2' },
];

function isNavActive(path: string): boolean {
  if (path === '/') return route.path === '/';
  if (path === '/tasks') return ['/tasks', '/check-in', '/achievements', '/kb-titles'].includes(route.path);
  if (path === '/study-group') return route.path === '/study-group';
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

function submitSearch() {
  const kw = searchKw.value.trim();
  router.push({ path: '/search', query: kw ? { q: kw } : {} });
  searchKw.value = '';
}

function toggleNotifications() {
  showUserMenu.value = false;
  showNotifications.value = !showNotifications.value;
  if (showNotifications.value && isLoggedIn.value) {
    notificationStore.fetchList({ pageNum: 1, pageSize: 20 }).catch(() => {
      // 通知列表加载失败静默处理，不打扰用户
    });
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
  } catch {
    notify('操作失败', 'error');
  }
}

async function handleNotificationClick(id: number) {
  try {
    await notificationStore.markAsRead(id);
  } catch {
    // 标记已读失败静默处理
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

// 全局快捷键：Cmd/Ctrl + K 跳转到搜索页
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

<style scoped>
.nav-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  min-width: 180px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  padding: 4px;
  z-index: 100;
  opacity: 0;
  visibility: hidden;
  transform: translateY(-4px);
  transition: opacity 0.15s ease, transform 0.15s ease, visibility 0.15s;
}
.nav-dropdown.is-open {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}
.nav-dropdown a {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  color: var(--kb-foreground);
  text-decoration: none;
  transition: background-color 0.15s, color 0.15s;
}
.nav-dropdown a:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
</style>
