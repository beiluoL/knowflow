<template>
  <div class="h-screen overflow-hidden flex" :style="rootStyle">
    <Sidebar
      :mobile-open="sidebarOpen"
      :collapsed="sidebarCollapsed"
      @close="sidebarOpen = false"
      @toggle-collapse="toggleCollapse"
    />
    <div
      v-if="sidebarOpen"
      class="fixed inset-0 z-40 bg-black/40 lg:hidden"
      @click="sidebarOpen = false"
    ></div>
    <div
      class="flex-1 flex flex-col min-h-0 transition-all duration-300 ease-in-out"
      :class="sidebarCollapsed ? 'lg:ml-[64px]' : 'lg:ml-60'"
    >
      <BTopbar @toggle-sidebar="sidebarOpen = !sidebarOpen" />
      <!-- fullscreen 模式：移除 padding 和 max-width，让编辑页面等全宽贴顶 -->
      <main v-if="isFullscreen" class="flex-1 overflow-y-auto kb-region-content">
        <slot />
      </main>
      <!-- 默认模式：带 padding 和最大宽度限制 -->
      <main v-else class="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8 kb-region-content">
        <div class="max-w-[1400px] mx-auto">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
// 布局组件：应用外壳布局，组合侧边栏与后台顶栏，提供主内容区插槽并持久化侧栏折叠状态。
// 全局背景激活时根容器透明，让 z-index:-1 的背景层可见。
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import Sidebar from './Sidebar.vue';
import BTopbar from './BTopbar.vue';
import { useBackgroundStore } from '@/stores/background';

const route = useRoute();
const bg = useBackgroundStore();
const sidebarOpen = ref(false);
const sidebarCollapsed = ref(false);

const rootStyle = computed(() => ({
  background: bg.isActive ? 'transparent' : 'var(--kb-background)',
}));

/** fullscreen 模式：路由 meta.fullscreen 为 true 时，main 移除 padding 和 max-width */
const isFullscreen = computed(() => route.meta.fullscreen === true);

function toggleCollapse() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
}

onMounted(() => {
  const saved = localStorage.getItem('sidebar-collapsed');
  if (saved !== null) {
    sidebarCollapsed.value = saved === 'true';
  }
});

watch(sidebarCollapsed, (val) => {
  localStorage.setItem('sidebar-collapsed', String(val));
});
</script>
