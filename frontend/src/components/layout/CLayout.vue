<template>
  <div class="min-h-screen" :style="rootStyle">
    <!-- Header：固定顶栏（Logo + 搜索 + AI 快捷 + 通知 + 用户菜单） -->
    <CTopNav :drawer-open="drawerOpen" @toggle-sidebar="drawerOpen = !drawerOpen" />

    <!-- 移动端抽屉遮罩 -->
    <div
      v-if="drawerOpen"
      class="clayout-overlay"
      @click="drawerOpen = false"
    />

    <div class="clayout-body">
      <!-- 桌面侧边栏（>=1024px 常驻）；移动端以抽屉形式出现 -->
      <CSidebar
        class="clayout-sidebar"
        :class="drawerOpen ? 'is-drawer-open' : ''"
        :collapsed="collapsed && !drawerOpen"
        @toggle-collapse="toggleCollapse"
        @navigate="drawerOpen = false"
      />

      <!-- 主内容区 -->
      <main
        class="clayout-main"
        :class="[collapsed ? 'is-collapsed' : '', { 'has-bottom-nav': showBottomNav }]"
      >
        <!-- 全屏页（AI/编辑器/图谱/工作台等）：取消 max-width 限制，由业务页自管宽度 -->
        <div v-if="route.meta.fullscreen" class="w-full px-4 sm:px-6 py-6">
          <slot />
        </div>
        <!-- 常规页：保持居中 + 最大宽度 -->
        <div v-else class="clayout-container">
          <slot />
        </div>
      </main>
    </div>

    <!-- 移动端底部导航（<1024px 显示，避免手机上塞入完整侧边栏） -->
    <nav
      v-if="showBottomNav"
      class="clayout-bottomnav"
      aria-label="底部导航"
    >
      <template v-for="item in bottomNav" :key="item.label">
        <router-link
          v-if="!item.children || item.children.length === 0"
          :to="item.path || '/'"
          class="bottomnav-item"
          :class="{ 'is-active': isBottomActive(item) }"
          :aria-current="isBottomActive(item) ? 'page' : undefined"
        >
          <Icon :name="item.icon" :size="20" />
          <span class="bottomnav-label">{{ item.label }}</span>
        </router-link>
        <button
          v-else
          type="button"
          class="bottomnav-item"
          :class="{ 'is-parent-active': isGroupActive(item) }"
          :aria-label="item.label"
          :aria-expanded="drawerOpen"
          @click="drawerOpen = true"
        >
          <Icon :name="item.icon" :size="20" />
          <span class="bottomnav-label">{{ item.label }}</span>
        </button>
      </template>
    </nav>
  </div>
</template>

<script setup lang="ts">
/**
 * C 端 App Shell（Phase UI-2 重构）。
 *
 * 结构（§36）：Header（固定） + Sidebar（固定） + Main（滚动）
 *  - Header 固定 h-14（--kb-nav-height），不随页面滚动（§48 避免三层滚动）
 *  - Sidebar 固定 left-0 / top-14 / bottom-0，仅 >=1024px 常驻
 *  - 只有 Main 区域滚动
 *  - 移动端（<1024px）：侧边栏改为抽屉，底部提供最多 5 个顶级入口（§31-§33）
 *
 * 布局参数统一收口到 --kb-* 令牌（§56），深色模式自动跟随。
 * 折叠状态沿用 localStorage 持久化（§47），不新建状态系统。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CTopNav from './CTopNav.vue'
import CSidebar from './CSidebar.vue'
import Icon from '@/components/ui/Icon.vue'
import { type NavGroup } from '@/config/navigation'
import { resolveNavigation } from '@/apps/navigation'
import { useAuthStore } from '@/stores/auth'
import { useBackgroundStore } from '@/stores/background'
import { useAppStore } from '@/stores/apps'

const route = useRoute()
const auth = useAuthStore()
const bg = useBackgroundStore()
const appStore = useAppStore()

const drawerOpen = ref(false)
const collapsed = ref(false)

const rootStyle = computed(() => ({
  background: bg.isActive ? 'transparent' : 'var(--kb-background)',
}))

/** 底部导航：取标记 mobilePrimary 的顶级入口，最多 5 个（§32）；与桌面侧边栏共用同一 Resolver（§21） */
const bottomNav = computed<NavGroup[]>(() => {
  const all = resolveNavigation(auth.isLoggedIn, auth.isAdmin, appStore.userApps)
  const primaries = all.filter((g) => g.mobilePrimary)
  return primaries.slice(0, 5)
})

/** 登录后才显示底部导航（未登录用户看到的是公开浏览态） */
const showBottomNav = computed(() => auth.isLoggedIn)

function toggleCollapse() {
  collapsed.value = !collapsed.value
}

function pathMatches(current: string, target: string): boolean {
  if (!target) return false
  if (current === target) return true
  return current.startsWith(target.endsWith('/') ? target : `${target}/`)
}

function isBottomActive(group: NavGroup): boolean {
  if (!group.path) return false
  if (group.path === '/') return route.path === '/'
  return pathMatches(route.path, group.path)
}

function isGroupActive(group: NavGroup): boolean {
  return !!group.children?.some((c) => pathMatches(route.path, c.path))
}

// 折叠状态持久化
onMounted(() => {
  const saved = localStorage.getItem('csidebar-collapsed')
  if (saved !== null) collapsed.value = saved === 'true'
  // UI/UX 3.0：在常驻 Shell 挂载时同步用户 App 状态（§47/§49），
  // 保证任意深链直达时侧边栏动态菜单已就绪；未登录不拉取（§46：localStorage 仅缓存，后端为真源）
  if (auth.isLoggedIn && Object.keys(appStore.userApps).length === 0) {
    void appStore.hydrate()
  }
})

watch(collapsed, (val) => {
  localStorage.setItem('csidebar-collapsed', String(val))
})

// 路由变化时关闭移动端抽屉
watch(() => route.fullPath, () => {
  drawerOpen.value = false
})
</script>

<style scoped>
/* Header 为 fixed，故主体整体下移其高度 */
.clayout-body {
  padding-top: var(--kb-nav-height);
}

/* 桌面：为固定侧边栏预留宽度；移动端：不预留 */
.clayout-main {
  min-height: calc(100vh - var(--kb-nav-height));
  transition: padding-left 200ms ease;
}

.clayout-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 16px;
}

@media (min-width: 640px) {
  .clayout-container {
    padding: 24px;
  }
}

/* >=1024px：侧边栏常驻，主内容留出对应宽度 */
@media (min-width: 1024px) {
  .clayout-main {
    padding-left: 240px;
  }

  .clayout-main.is-collapsed {
    padding-left: 64px;
  }
}

.clayout-sidebar {
  position: fixed;
  top: var(--kb-nav-height);
  left: 0;
  bottom: 0;
  z-index: 40;
  /* 移动端默认收进屏幕外，抽屉打开时滑入 */
  transform: translateX(-100%);
  transition: transform 200ms ease;
}

@media (min-width: 1024px) {
  .clayout-sidebar {
    transform: translateX(0);
  }
}

.clayout-sidebar.is-drawer-open {
  transform: translateX(0);
}

.clayout-overlay {
  position: fixed;
  inset: 0;
  z-index: 35;
  background: rgba(0, 0, 0, 0.4);
}

@media (min-width: 1024px) {
  .clayout-overlay {
    display: none;
  }
}

/* ===== 移动端底部导航 ===== */
.clayout-bottomnav {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 45;
  display: flex;
  align-items: stretch;
  border-top: 1px solid var(--kb-border);
  background: var(--kb-card);
  padding-bottom: env(safe-area-inset-bottom, 0);
}

@media (min-width: 1024px) {
  .clayout-bottomnav {
    display: none;
  }
}

.bottomnav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: 52px;
  padding: 6px 4px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-xs);
  text-decoration: none;
  cursor: pointer;
  transition: color 200ms ease, background-color 200ms ease;
}

.bottomnav-item:hover {
  color: var(--kb-foreground);
}

.bottomnav-item:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--kb-ring);
}

.bottomnav-item.is-active,
.bottomnav-item.is-parent-active {
  color: var(--kb-primary);
}

.bottomnav-label {
  line-height: 1.2;
}

/* 有底部导航时，主内容补出底部空间，避免内容被遮挡 */
.clayout-main.has-bottom-nav {
  padding-bottom: 68px;
}

@media (min-width: 1024px) {
  .clayout-main.has-bottom-nav {
    padding-bottom: 0;
  }
}
</style>
