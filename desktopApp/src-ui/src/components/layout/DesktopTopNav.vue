<template>
  <header
    class="fixed top-0 left-0 right-0 z-50 h-14 flex items-center px-4 sm:px-6 border-b"
    :style="{ background: 'var(--kb-card)', borderColor: 'var(--kb-border)' }"
  >
    <!-- Left: Logo -->
    <router-link
      to="/workbench"
      class="flex items-center shrink-0"
      style="color: var(--kb-primary); gap: var(--kb-nav-gap);"
    >
      <Icon name="brain" size="xl" />
      <span
        class="hidden sm:inline"
        :style="{ fontSize: 'var(--kb-logo-text-fs)', fontWeight: 'var(--kb-logo-text-fw)' }"
      >知识库</span>
    </router-link>

    <!-- Center: 学习闭环导航 -->
    <nav class="hidden lg:flex items-center gap-6 ml-8">
      <router-link
        v-for="it in navItems"
        :key="it.path"
        :to="it.path"
        class="flex items-center gap-2 transition-colors"
        :class="isActive(it.path) ? '' : 'hover:opacity-80'"
        :style="{
          color: isActive(it.path) ? 'var(--kb-primary)' : 'var(--kb-muted-foreground)',
          fontSize: 'var(--kb-nav-text-fs)',
          fontWeight: isActive(it.path) ? 600 : 'var(--kb-nav-text-fw)',
        }"
      >
        <Icon :name="it.icon" size="md" />
        <span>{{ it.label }}</span>
      </router-link>
    </nav>

    <!-- 移动/窄窗：折叠为下拉 -->
    <div class="relative lg:hidden ml-6">
      <button
        type="button"
        class="flex items-center gap-2"
        :style="{ color: 'var(--kb-muted-foreground)', fontSize: 'var(--kb-nav-text-fs)', fontWeight: 'var(--kb-nav-text-fw)' }"
        @click="menuOpen = !menuOpen"
      >
        <Icon name="menu" size="md" />
        <span>{{ currentLabel }}</span>
        <Icon name="chevron-down" size="sm" />
      </button>
      <div class="nav-dropdown" :class="{ 'is-open': menuOpen }" role="menu">
        <router-link
          v-for="it in navItems"
          :key="it.path"
          :to="it.path"
          class="flex items-center gap-2 px-3 py-2"
          :style="{ fontSize: 'var(--kb-dropdown-text-fs)' }"
          @click="menuOpen = false"
        >
          <Icon :name="it.icon" size="md" style="color: var(--kb-muted-foreground);" />
          {{ it.label }}
        </router-link>
      </div>
    </div>

    <div class="flex-1"></div>

    <!-- Right: 桌面端专属操作 -->
    <div class="flex items-center gap-2">
      <span
        class="hidden md:inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full"
        :style="{
          background: 'var(--kb-muted)',
          color: 'var(--kb-muted-foreground)',
          fontFamily: 'var(--font-mono)',
          fontSize: '11px',
        }"
      >
        <Icon name="hard-drive" size="xs" />
        本地离线
      </span>
      <button type="button" class="wb-icon-btn" title="检查更新" @click="checkUpdate">
        <Icon name="refresh-cw" size="md" :class="updating ? 'animate-spin' : ''" />
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
// 桌面端顶部导航：沿用 Web 端 CTopNav 的视觉语言（56px 固定栏、--kb-nav-* 令牌、
// 同款 Icon + 文字排布），导航项收敛为学习闭环六模块，右侧换成桌面专属的更新入口。
import { ref, computed } from 'vue';
import { useRoute } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { notify } from '@/utils/toast';

const route = useRoute();
const menuOpen = ref(false);
const updating = ref(false);

const navItems = [
  { path: '/workbench', label: '工作台', icon: 'brain' },
  { path: '/workbench/capture', label: '收集箱', icon: 'inbox' },
  { path: '/workbench/notes', label: '笔记', icon: 'notebook-pen' },
  { path: '/workbench/review', label: '复习', icon: 'repeat' },
  { path: '/workbench/palace', label: '记忆宫殿', icon: 'map-pin' },
  { path: '/workbench/recall', label: '主动回忆', icon: 'edit-2' },
  { path: '/workbench/story', label: '费曼故事', icon: 'wand-2' },
];

function isActive(path: string) {
  if (path === '/workbench') return route.path === '/workbench';
  return route.path.startsWith(path);
}

const currentLabel = computed(
  () => navItems.find((it) => isActive(it.path))?.label ?? '工作台',
);

async function checkUpdate() {
  if (updating.value) return;
  updating.value = true;
  try {
    const { invoke } = await import('@tauri-apps/api/core');
    const msg = await invoke<string>('check_for_update');
    notify(msg || '已是最新版本', 'success');
  } catch {
    notify('当前处于浏览器预览模式，更新检查仅在桌面应用内可用', 'info');
  } finally {
    updating.value = false;
  }
}
</script>

<style scoped>
/* 下拉面板：与 Web 端 CTopNav 的 .nav-dropdown 保持一致 */
.nav-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  min-width: 168px;
  padding: 6px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  box-shadow: var(--shadow-lg);
  opacity: 0;
  visibility: hidden;
  transform: translateY(-4px);
  transition: all 0.16s ease;
  z-index: 60;
}
.nav-dropdown.is-open {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}
.nav-dropdown a {
  border-radius: var(--kb-radius-sm);
  color: var(--kb-foreground);
}
.nav-dropdown a:hover {
  background: var(--kb-muted);
}
</style>
