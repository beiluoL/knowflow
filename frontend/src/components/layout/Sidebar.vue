<template>
  <!-- 移动端遮罩 -->
  <div
    v-if="mobileOpen"
    class="fixed inset-0 bg-black/40 z-40 lg:hidden"
    @click="$emit('close')"
  />

  <aside
    class="fixed left-0 top-0 h-screen flex flex-col no-scrollbar overflow-y-auto z-50 transition-all duration-300 ease-in-out lg:translate-x-0"
    :class="[
      mobileOpen ? 'translate-x-0' : '-translate-x-full',
      collapsed ? 'w-[64px]' : 'w-60',
    ]"
    :style="sidebarStyle"
  >
    <!-- Logo -->
    <div
      class="h-14 shrink-0 flex items-center border-b"
      :class="collapsed ? 'justify-center px-0' : 'px-5'"
      style="border-color: var(--kb-border);"
    >
      <router-link
        to="/admin/overview"
        class="flex items-center min-w-0"
        :style="{ gap: 'var(--kb-nav-gap)' }"
        @click="$emit('close')"
      >
        <Icon name="icon-AIbeikezhushou" size="xl" class="text-primary-500 shrink-0" />
        <span
          v-if="!collapsed"
          class="truncate"
          :style="{ fontSize: 'var(--kb-logo-text-fs)', fontWeight: 'var(--kb-logo-text-fw)' }"
        >KnowFlow</span>
      </router-link>
    </div>

    <!-- Navigation -->
    <nav class="flex-1 overflow-y-auto no-scrollbar py-3" :class="collapsed ? 'px-2' : 'px-3'">
      <div v-for="group in visibleGroups" :key="group.title" class="mb-2">
        <p
          v-if="!collapsed"
          class="sidebar-group-label"
        >{{ group.title }}</p>
        <ul class="flex flex-col gap-0.5">
          <li v-for="item in group.items" :key="item.path">
            <router-link
              :to="item.path"
              class="sidebar-nav-item"
              :class="[
                isActive(item.path) ? 'active' : '',
                collapsed ? 'justify-center px-0' : '',
              ]"
              :title="collapsed ? item.label : ''"
              @click="$emit('close')"
            >
              <Icon :name="item.icon" size="md" class="shrink-0" />
              <span v-if="!collapsed" class="truncate">{{ item.label }}</span>
            </router-link>
          </li>
        </ul>
      </div>
    </nav>

    <!-- Bottom: Back to frontend + collapse -->
    <div class="shrink-0 border-t" style="border-color: var(--kb-border);">
      <router-link
        to="/"
        class="flex items-center px-5 py-3 transition-colors hover:bg-gray-100"
        :class="collapsed ? 'justify-center px-0' : ''"
        :style="{
          gap: 'var(--kb-sidebar-gap)',
          color: 'var(--kb-muted-foreground)',
          fontSize: 'var(--kb-sidebar-bottom-fs)',
        }"
        :title="collapsed ? '返回前台' : ''"
        @click="$emit('close')"
      >
        <Icon name="external-link" size="sm" class="shrink-0" />
        <span v-if="!collapsed">返回前台</span>
      </router-link>

      <button
        type="button"
        class="w-full flex items-center py-2.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 transition-colors border-t"
        :class="collapsed ? 'justify-center' : 'px-4'"
        :style="{
          gap: 'var(--kb-sidebar-gap)',
          borderColor: 'var(--kb-border)',
          fontSize: 'var(--kb-sidebar-bottom-fs)',
        }"
        :title="collapsed ? '展开菜单' : '收起菜单'"
        @click="$emit('toggle-collapse')"
      >
        <Icon :name="collapsed ? 'chevron-right' : 'chevron-left'" size="md" />
        <span v-if="!collapsed">收起菜单</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
// 布局组件：后台侧边栏，渲染按角色过滤的导航分组，并支持折叠与移动端抽屉。
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { useAuthStore } from '@/stores/auth';
import { useBackgroundStore } from '@/stores/background';

defineProps<{
  mobileOpen: boolean;
  collapsed: boolean;
}>();

const bgStore = useBackgroundStore();

// 背景激活时侧边栏切换为毛玻璃效果
const sidebarStyle = computed(() => {
  if (bgStore.isActive) {
    return {
      background: 'rgba(255, 255, 255, 0.78)',
      borderRight: '1px solid rgba(255, 255, 255, 0.3)',
      backdropFilter: 'blur(16px)',
      WebkitBackdropFilter: 'blur(16px)',
    }
  }
  return { background: 'var(--kb-sidebar)', borderRight: '1px solid var(--kb-border)' }
})

defineEmits<{
  close: [];
  'toggle-collapse': [];
}>();

interface NavItem {
  path: string;
  label: string;
  icon: string;
  adminOnly?: boolean;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const route = useRoute();
const auth = useAuthStore();
const isAdmin = computed(() => auth.isAdmin);

const allGroups: NavGroup[] = [
  {
    title: '数据与概览',
    items: [
      { path: '/admin/overview', label: '系统概览', icon: 'layout-dashboard', adminOnly: true },
    ],
  },
  {
    title: '内容管理',
    items: [
      { path: '/admin/knowledge', label: '知识库管理', icon: 'database', adminOnly: true },
      { path: '/admin/categories', label: '分类管理', icon: 'folder-tree', adminOnly: true },
      { path: '/admin/docs', label: '文档管理', icon: 'files', adminOnly: true },
      { path: '/admin/upload', label: '上传文档', icon: 'upload', adminOnly: true },
      { path: '/admin/tags', label: '知识库与标签', icon: 'tags', adminOnly: true },
      { path: '/admin/learning-paths', label: '学习路径管理', icon: 'route', adminOnly: true },
      { path: '/admin/flashcards', label: '知识卡片管理', icon: 'credit-card', adminOnly: true },
      { path: '/admin/code-questions', label: '代码题库管理', icon: 'code', adminOnly: true },
      { path: '/admin/icons', label: '图标管理', icon: 'palette', adminOnly: true },
      { path: '/admin/files', label: '文件管理', icon: 'folder', adminOnly: true },
    ],
  },
  {
    title: 'AI 与生成',
    items: [
      { path: '/admin/quiz', label: '智能出题', icon: 'file-question', adminOnly: true },
      { path: '/admin/writing', label: '智能写作', icon: 'pen-tool', adminOnly: true },
      { path: '/admin/chat-config', label: '对话配置', icon: 'settings', adminOnly: true },
    ],
  },
  {
    title: '用户与运营',
    items: [
      { path: '/admin/users', label: '用户管理', icon: 'users', adminOnly: true },
      { path: '/admin/community', label: '社区管理', icon: 'message-square', adminOnly: true },
    ],
  },
  {
    title: '导入与模板',
    items: [
      { path: '/import-templates', label: '规则模板', icon: 'layout', adminOnly: true },
      { path: '/obsidian/import', label: '模板导入', icon: 'upload', adminOnly: true },
    ],
  },
  {
    title: '系统设置',
    items: [{ path: '/admin/database', label: '数据库设置', icon: 'database', adminOnly: true }],
  },
];

const visibleGroups = computed(() =>
  allGroups
    .map((g) => ({
      ...g,
      items: g.items.filter((it) => !it.adminOnly || isAdmin.value),
    }))
    .filter((g) => g.items.length > 0),
);

// 高亮当前路由：精确匹配或以该路径为前缀的子路由均视为激活
function isActive(path: string): boolean {
  if (path === '/') return route.path === '/';
  return route.path === path || route.path.startsWith(path + '/');
}
</script>
