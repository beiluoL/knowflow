<template>
  <GlobalBackground />
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
  <Celebration />
  <DialogHost />
  <CommandPalette />
</template>

<script setup lang="ts">
// 应用根组件：按路由 meta 挂载对应布局（前台/后台/默认），并渲染全局背景层、消息宿主与庆祝弹窗宿主。
import { onMounted, onBeforeUnmount } from 'vue';
import AppShell from '@/components/layout/AppShell.vue';
import CLayout from '@/components/layout/CLayout.vue';
import GlobalBackground from '@/components/GlobalBackground.vue';
import ToastHost from '@/components/ui/ToastHost.vue';
import Celebration from '@/components/ui/Celebration.vue';
import DialogHost from '@/components/ui/DialogHost.vue';
import CommandPalette from '@/components/ui/CommandPalette.vue';
import { useAuthStore } from '@/stores/auth';
import { usePomodoroStore } from '@/stores/pomodoro';
import { useCommandPalette } from '@/composables/useCommandPalette';

const auth = useAuthStore();
const pomodoro = usePomodoroStore();
const palette = useCommandPalette();

onMounted(() => {
  // 启动时尝试恢复登录态（若本地存在 token）
  if (auth.isLoggedIn && !auth.user) {
    auth.fetchMe().catch(() => auth.logout());
  }
  // 初始化番茄钟：绑定全局事件监听（可见性变化 / 关闭标签页校正时间）
  pomodoro.init();
  // F2：注册全局 ⌘K / Ctrl+K 命令面板快捷键
  palette.register();
});

onBeforeUnmount(() => {
  palette.unregister();
});
</script>
