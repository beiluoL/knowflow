<template>
  <div class="h-screen overflow-hidden flex" style="background: var(--kb-background);">
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
      <main class="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">
        <div class="max-w-[1400px] mx-auto">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
// 布局组件：应用外壳布局，组合侧边栏与后台顶栏，提供主内容区插槽并持久化侧栏折叠状态。
import { ref, onMounted, watch } from 'vue';
import Sidebar from './Sidebar.vue';
import BTopbar from './BTopbar.vue';

const sidebarOpen = ref(false);
const sidebarCollapsed = ref(false);

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
