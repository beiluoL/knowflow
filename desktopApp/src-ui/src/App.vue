<template>
  <!-- 与 Web 端 CLayout（route.meta.layout === 'c'）结构一致：
       顶部 56px 固定导航 + pt-14 内容区；工作台页为 fullscreen，取消 max-w-7xl 居中限制。 -->
  <div class="min-h-screen" :style="{ background: 'var(--kb-background)' }">
    <DesktopTopNav />
    <main class="pt-14 kb-region-content">
      <div v-if="route.meta.fullscreen" class="w-full px-4 sm:px-6 py-6">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.path" />
        </router-view>
      </div>
      <div v-else class="max-w-7xl mx-auto px-4 sm:px-6 py-6">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.path" />
        </router-view>
      </div>
    </main>
  </div>
  <ToastHost />
</template>

<script setup lang="ts">
// 桌面端应用根组件：等价于 Web 端 App.vue + CLayout 的组合（去掉登录态恢复与番茄钟等 Web 专属逻辑）。
import { useRoute } from 'vue-router';
import DesktopTopNav from '@/components/layout/DesktopTopNav.vue';
import ToastHost from '@/components/ui/ToastHost.vue';

const route = useRoute();
</script>
