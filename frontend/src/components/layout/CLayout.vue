<template>
  <div class="min-h-screen" style="background: var(--kb-background);">
    <CTopNav />
    <main class="pt-14 kb-region-content">
      <!-- 全屏页（笔记/写作/代码/聊天/仪表盘等）：取消 max-w-7xl 居中限制，由业务页自管宽度 -->
      <div v-if="route.meta.fullscreen" class="w-full px-4 sm:px-6 py-6">
        <slot />
      </div>
      <!-- 常规页：保持 1280px 居中 -->
      <div v-else class="max-w-7xl mx-auto px-4 sm:px-6 py-6">
        <slot />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
// 布局组件：前台基础布局，组合顶部导航与内容区。
// 路由 meta.fullscreen = true 时取消 max-w-7xl 居中限制，由业务页自管宽度。
import { useRoute } from 'vue-router';
import CTopNav from './CTopNav.vue';

const route = useRoute();
</script>
