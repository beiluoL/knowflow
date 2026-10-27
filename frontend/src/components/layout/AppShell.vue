<template>
  <div class="h-screen overflow-hidden flex" :style="rootStyle">
    <Sidebar
      :mobile-open="sidebarOpen"
      :collapsed="sidebarCollapsed"
      :mobile-hidden="!wide && !sidebarOpen"
      @close="sidebarOpen = false"
      @toggle-collapse="toggleCollapse"
    />
    <div
      data-test="admin-content" :inert="(!wide && sidebarOpen) || undefined"
      class="flex-1 flex flex-col min-h-0 min-w-0 transition-all duration-300 ease-in-out"
      :class="sidebarCollapsed ? 'lg:ml-[64px]' : 'lg:ml-60'"
    >
      <BTopbar @toggle-sidebar="toggleSidebar" />
      <!-- fullscreen 模式：移除 padding 和 max-width，让编辑页面等全宽贴顶 -->
      <main v-if="isFullscreen" class="flex-1 overflow-y-auto kb-region-content">
        <slot />
      </main>
      <!-- 默认页面占满右侧可用区域，保留统一内边距 -->
      <main v-else class="flex-1 overflow-y-auto p-4 sm:p-6 kb-region-content">
        <div class="w-full min-w-0">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
// 布局组件：应用外壳布局，组合侧边栏与后台顶栏，提供主内容区插槽并持久化侧栏折叠状态。
// 全局背景激活时根容器透明，让 z-index:-1 的背景层可见。
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { useRoute } from 'vue-router';
import Sidebar from './Sidebar.vue';
import BTopbar from './BTopbar.vue';
import { useBackgroundStore } from '@/stores/background';

const route = useRoute();
const bg = useBackgroundStore();
const sidebarOpen = ref(false);
const wide = ref(window.innerWidth >= 1024);
let trigger: HTMLElement | null = null;
function toggleSidebar() { trigger = document.activeElement as HTMLElement; sidebarOpen.value = !sidebarOpen.value; }
function onResize() { wide.value = window.innerWidth >= 1024; if (wide.value) sidebarOpen.value = false; }
function onDrawerKey(event: KeyboardEvent) {
  if (wide.value || !sidebarOpen.value) return;
  if (event.key === 'Escape') { event.preventDefault(); sidebarOpen.value = false; return; }
  if (event.key !== 'Tab') return;
  const nodes = Array.from(document.querySelectorAll<HTMLElement>('[data-test="admin-sidebar"] a, [data-test="admin-sidebar"] button')).filter(el => !el.hasAttribute('disabled') && el.getClientRects().length > 0);
  const first = nodes[0], last = nodes[nodes.length-1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
}
// 收起目录不可聚焦；打开与关闭在DOM更新后转移焦点，不改角色或路由权限。
watch(sidebarOpen, async open => { await nextTick(); if (open && !wide.value) document.querySelector<HTMLElement>('[data-test="admin-sidebar"] a')?.focus(); else if (!open && trigger?.isConnected) trigger.focus(); });
watch(() => route.fullPath, () => { sidebarOpen.value = false; });
onMounted(() => { window.addEventListener('resize', onResize); document.addEventListener('keydown', onDrawerKey); });
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); document.removeEventListener('keydown', onDrawerKey); });
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

<style scoped>
@media(prefers-reduced-motion:reduce) { :deep(*) { transition:none !important; animation:none !important; } }
</style>
