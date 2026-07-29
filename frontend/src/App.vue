<template>
  <router-view v-slot="{ Component, route }">
    <CLayout v-if="route.meta.layout === 'c'">
      <component :is="Component" :key="route.path" />
    </CLayout>
    <AppShell v-else-if="route.meta.layout === 'b'">
      <component :is="Component" :key="route.path" />
    </AppShell>
    <component v-else :is="Component" :key="route.path" />
  </router-view>
  <ToastHost />
</template>

<script setup lang="ts">
// 应用根组件：按路由 meta 挂载对应布局（前台/后台/默认），并渲染全局消息宿主。
import { onMounted } from 'vue';
import AppShell from '@/components/layout/AppShell.vue';
import CLayout from '@/components/layout/CLayout.vue';
import ToastHost from '@/components/ui/ToastHost.vue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();

onMounted(() => {
  // 启动时尝试恢复登录态（若本地存在 token）
  if (auth.isLoggedIn && !auth.user) {
    auth.fetchMe().catch(() => auth.logout());
  }
});
</script>
