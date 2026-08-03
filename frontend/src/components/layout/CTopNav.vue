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

      <!-- 知识库（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: isDropdownActive('knowledge') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isDropdownActive('knowledge') ? 600 : 'var(--kb-nav-text-fw)' }"
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

      <!-- 学习（下拉，分三组：学习活动 / 学习辅助 / AI 工具） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: isDropdownActive('learning') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isDropdownActive('learning') ? 600 : 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('learning')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'learning'"
        >
          <Icon name="graduation-cap" size="md" />
          <span>学习</span>
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

      <!-- 社区（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: isDropdownActive('community') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isDropdownActive('community') ? 600 : 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('community')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'community'"
        >
          <Icon name="users" size="md" />
          <span>社区</span>
          <Icon name="chevron-down" size="sm" :class="openDropdown === 'community' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'community' }" role="menu">
          <router-link
            v-for="it in communityMenu"
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
          :style="{ color: isDropdownActive('ai') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isDropdownActive('ai') ? 600 : 'var(--kb-nav-text-fw)' }"
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

      <!-- 成长（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: isDropdownActive('growth') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isDropdownActive('growth') ? 600 : 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('growth')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'growth'"
        >
          <Icon name="trending-up" size="md" />
          <span>成长</span>
          <Icon name="chevron-down" size="sm" :class="openDropdown === 'growth' ? 'rotate-180 transition-transform duration-200' : 'transition-transform duration-200'" />
        </button>
        <div class="nav-dropdown" :class="{ 'is-open': openDropdown === 'growth' }" role="menu">
          <router-link
            v-for="it in growthMenu"
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

      <!-- 我的（下拉） -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 transition-colors"
          :style="{ color: isDropdownActive('personal') ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: isDropdownActive('personal') ? 600 : 'var(--kb-nav-text-fw)' }"
          @click="toggleDropdown('personal')"
          aria-haspopup="menu"
          :aria-expanded="openDropdown === 'personal'"
        >
          <Icon name="user" size="md" />
          <span>我的</span>
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
        type="button"
        role="menuitem"
        class="w-full flex items-center gap-2 px-3 py-2 transition-colors hover:bg-gray-50"
        style="color: var(--kb-foreground); font-size: var(--kb-dropdown-text-fs); gap: var(--kb-nav-gap);"
        @click="goTo('/settings/background')"
      >
        <Icon name="image" size="md" style="color: var(--kb-muted-foreground);" />
        背景设置
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
        <!-- 首页 + 知识库 -->
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
        <!-- 学习 -->
        <div class="mobile-group-title">学习</div>
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
        <!-- 社区 -->
        <div class="mobile-group-title">社区</div>
        <router-link
          v-for="it in communityMenu"
          :key="it.path"
          :to="it.path"
          class="flex items-center px-3 py-2.5 rounded-lg transition-colors hover:bg-gray-50"
          style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); gap: var(--kb-nav-gap);"
          @click="mobileOpen = false"
        >
          <Icon :name="it.icon" size="lg" style="color: var(--kb-muted-foreground);" />
          {{ it.label }}
        </router-link>
        <!-- AI 助手 -->
        <div class="mobile-group-title">AI 助手</div>
        <router-link
          v-for="it in aiMenu"
          :key="it.path"
          :to="it.path"
          class="flex items-center px-3 py-2.5 rounded-lg transition-colors hover:bg-gray-50"
          style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); gap: var(--kb-nav-gap);"
          @click="mobileOpen = false"
        >
          <Icon :name="it.icon" size="lg" style="color: var(--kb-muted-foreground);" />
          {{ it.label }}
        </router-link>
        <!-- 成长 -->
        <div class="mobile-group-title">成长</div>
        <router-link
          v-for="it in growthMenu"
          :key="it.path"
          :to="it.path"
          class="flex items-center px-3 py-2.5 rounded-lg transition-colors hover:bg-gray-50"
          style="color: var(--kb-foreground); font-size: var(--kb-fs-body-md); gap: var(--kb-nav-gap);"
          @click="mobileOpen = false"
        >
          <Icon :name="it.icon" size="lg" style="color: var(--kb-muted-foreground);" />
          {{ it.label }}
        </router-link>
        <!-- 我的 -->
        <div class="mobile-group-title">我的</div>
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
// 布局组件：前台导航顶栏。
// 导航分组：首页 / 知识库(下拉) / 学习(下拉) / 社区(下拉) / AI助手(下拉) / 成长(下拉) / 我的(下拉) + 顶栏工具区(搜索/通知/头像)
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

const mobileLinks: NavLink[] = [
  { path: '/', label: '首页', icon: 'home' },
  { path: '/knowledge', label: '知识库', icon: 'icon-AIbeikezhushou' },
  { path: '/categories', label: '分类浏览', icon: 'folder-tree' },
  { path: '/search', label: '搜索知识', icon: 'search' },
  { path: '/docs', label: '文档库', icon: 'file-text' },
];

const knowledgeMenu: NavLink[] = [
  { path: '/knowledge', label: '知识库', icon: 'icon-AIbeikezhushou' },
  { path: '/categories', label: '分类浏览', icon: 'folder-tree' },
  { path: '/search', label: '搜索知识', icon: 'icon-wendangsousuo' },
  { path: '/docs', label: '文档库', icon: 'file-text' },
  { path: '/learning/knowledge-graph', label: '知识图谱', icon: 'icon-zhishitupubaocun' },
];

// 学习下拉：学习活动
const learningMenu: NavLink[] = [
  { path: '/learning/center', label: '学习中心', icon: 'icon-xuexizhongxin' },
  { path: '/learning/paths', label: '学习路径', icon: 'route' },
  { path: '/learning/code-practice', label: '代码练习', icon: 'code' },
  { path: '/learning/flashcards', label: '闪卡大厅', icon: 'layers' },
  { path: '/learning/review', label: '复习计划', icon: 'calendar-check' },
];

const communityMenu: NavLink[] = [
  { path: '/community', label: '社区讨论', icon: 'users' },
  { path: '/study-group', label: '学习小组', icon: 'message-circle' },
  { path: '/messages', label: '私信', icon: 'message-square' },
];

const aiMenu: NavLink[] = [
  { path: '/chat', label: '智能问答', icon: 'icon-rengongzhineng1' },
  { path: '/learning/quiz', label: '智能测验', icon: 'icon-kaoshi' },
  { path: '/learning/writing', label: '智能写作', icon: 'wand-2' },
];

const growthMenu: NavLink[] = [
  { path: '/tasks', label: '任务中心', icon: 'target' },
  { path: '/check-in', label: '每日打卡', icon: 'calendar-check' },
  { path: '/achievements', label: '成就系统', icon: 'trophy' },
  { path: '/kb-titles', label: '知识库称号', icon: 'award' },
];

const personalMenu: NavLink[] = [
  { path: '/profile', label: '个人中心', icon: 'user-circle' },
  { path: '/favorites', label: '收藏夹', icon: 'bookmark' },
  { path: '/notes', label: '笔记管理', icon: 'notebook-pen' },
  { path: '/mistakes', label: '错误本', icon: 'alert-circle' },
];

// 当前路由所属的下拉分组 key，用于下拉按钮在子页面时保持高亮
const activeDropdownKey = computed<string>(() => {
  const p = route.path;
  // AI 助手优先匹配（/learning/quiz、/learning/writing 路由虽以 /learning 开头但归 AI 助手）
  if (['/chat', '/learning/quiz', '/learning/writing'].includes(p)) return 'ai';
  if (['/knowledge', '/categories', '/search', '/docs', '/learning/knowledge-graph'].some(s => p === s || p.startsWith(s + '/'))) return 'knowledge';
  if (p.startsWith('/learning')) return 'learning';
  if (['/community', '/study-group', '/messages'].some(s => p === s || p.startsWith(s + '/'))) return 'community';
  if (['/tasks', '/check-in', '/achievements', '/kb-titles'].includes(p)) return 'growth';
  if (['/profile', '/favorites', '/notes', '/mistakes'].some(s => p === s || p.startsWith(s + '/'))) return 'personal';
  return '';
});

function isDropdownActive(key: string): boolean {
  return openDropdown.value === key || activeDropdownKey.value === key;
}

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
.nav-dropdown-wide {
  min-width: 200px;
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
.nav-dropdown-divider {
  height: 1px;
  margin: 4px 8px;
  background: var(--kb-border);
}
.mobile-group-title {
  padding: 12px 12px 4px;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-caption);
  font-weight: var(--kb-fw-caption);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
</style>
