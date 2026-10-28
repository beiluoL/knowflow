<template>
  <div class="min-h-screen pc-studio ui-v2-shell" :class="{'chapter-shell':route.name==='ChapterLearn','notes-shell':isNotes,'notes-shell-wide':isNotes && notesLayout!=='normal'}" data-test="pc-shell" :style="rootStyle" @keydown="onShellKeydown">
    <a class="ui-v2-skip" href="#knowflow-main" @click="focusMain">跳到主内容</a>
    <!-- Header：固定顶栏（Logo + 搜索 + AI 快捷 + 通知 + 用户菜单） -->
    <CTopNav :inert="isMobileDrawer || undefined" :drawer-open="drawerOpen" :sidebar-collapsed="collapsed" @toggle-sidebar="drawerOpen = !drawerOpen" @toggle-collapse="toggleCollapse" @show-guide="guide.reopen()" />
    <WelcomeGuide @start="router.push('/workbench/capture')" />

    <!-- 移动端抽屉遮罩 -->
    <div
      v-if="drawerOpen"
      class="clayout-overlay"
      @click="drawerOpen = false"
    />

    <div class="clayout-body">
      <!-- 桌面侧边栏（>=1024px 常驻）；移动端以抽屉形式出现 -->
      <CSidebar
        ref="sidebarRef"
        id="knowflow-navigation"
        :inert="!isDesktop && !drawerOpen || undefined"
        :role="isMobileDrawer ? 'dialog' : undefined"
        :aria-modal="isMobileDrawer ? 'true' : undefined"
        data-test="pc-sidebar"
        class="clayout-sidebar"
        :class="drawerOpen ? 'is-drawer-open' : ''"
        :collapsed="collapsed && !drawerOpen"
        @toggle-collapse="toggleCollapse"
        @navigate="drawerOpen = false"
      />

      <!-- 主内容区 -->
      <main
        id="knowflow-main"
        tabindex="-1"
        :inert="isMobileDrawer || undefined"
        data-test="pc-main"
        class="clayout-main"
        :class="[collapsed ? 'is-collapsed' : '', { 'has-bottom-nav': showBottomNav }]"
      >
        <!-- 页面统一占满侧栏右侧；正文与画布内部布局仍由业务组件管理。 -->
        <div class="clayout-container" data-test="pc-content">
          <slot />
        </div>
      </main>
    </div>

    <!-- 移动端底部导航（<1024px 显示，避免手机上塞入完整侧边栏） -->
    <nav
      v-if="showBottomNav"
      class="clayout-bottomnav"
      :inert="isMobileDrawer || undefined"
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
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WelcomeGuide from '@/components/onboarding/WelcomeGuide.vue'
import { useOnboardingStore } from '@/stores/onboarding'
import CTopNav from './CTopNav.vue'
import CSidebar from './CSidebar.vue'
import Icon from '@/components/ui/Icon.vue'
import { type NavGroup } from '@/config/navigation'
import { resolveNavigation } from '@/apps/navigation'
import { useAuthStore } from '@/stores/auth'
import { useBackgroundStore } from '@/stores/background'
import { useAppStore } from '@/stores/apps'
import {NOTES_LAYOUT_KEY,type NotesLayoutMode} from '@/notes/workspace-layout'
import {NOTE_ROUTE_NAMES} from '@/notes/model'
import '@/styles/pc-studio.css'
import '@/styles/ui-v2-phase-a.css'

const notesLayout=ref<NotesLayoutMode>('normal');provide(NOTES_LAYOUT_KEY,notesLayout)
const route = useRoute()
const isNotes=computed(()=>NOTE_ROUTE_NAMES.has(String(route.name)))
watch(isNotes,value=>{if(!value)notesLayout.value='normal'})
const router=useRouter(),guide=useOnboardingStore()
const auth = useAuthStore()
watch(()=>[auth.user?.id,auth.user?.createTime,auth.user?.role,auth.isLoggedIn],()=>guide.syncUser(auth.isLoggedIn?auth.user:null),{immediate:true})
const bg = useBackgroundStore()
const appStore = useAppStore()

const drawerOpen = ref(false)
const collapsed = ref(false)
const isDesktop = ref(typeof window === 'undefined' || window.innerWidth >= 1024)
const isMobileDrawer = computed(() => !isDesktop.value && drawerOpen.value)
const sidebarRef = ref<{ $el: HTMLElement } | null>(null)
let media: MediaQueryList | undefined
let previousFocus: HTMLElement | null = null
function updateViewport() {
  isDesktop.value = media?.matches ?? window.innerWidth >= 1024
  if (isDesktop.value) drawerOpen.value = false
}
function focusMain() {
  document.getElementById('knowflow-main')?.focus()
}
function onShellKeydown(event: KeyboardEvent) {
  if (!isMobileDrawer.value) return
  if (event.key === 'Escape') { event.preventDefault(); drawerOpen.value = false; return }
  if (event.key !== 'Tab') return
  const controls = [...(sidebarRef.value?.$el.querySelectorAll<HTMLElement>('a[href],button:not(:disabled),[tabindex="0"]') ?? [])].filter(el => !el.closest('[inert]'))
  const first = controls[0], last = controls.at(-1)
  if (!first || !last) return
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
watch(isMobileDrawer, async open => {
  if (open) {
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    if (isMobileDrawer.value) sidebarRef.value?.$el.querySelector<HTMLElement>('button,a[href]')?.focus()
  } else {
    await nextTick()
    if (previousFocus?.isConnected) previousFocus.focus()
    previousFocus = null
  }
})


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
  media = window.matchMedia('(min-width: 1024px)')
  updateViewport()
  media.addEventListener('change', updateViewport)

  const saved = localStorage.getItem('csidebar-collapsed')
  if (saved !== null) collapsed.value = saved === 'true'
  // UI/UX 3.0：在常驻 Shell 挂载时同步用户 App 状态（§47/§49），
  // 保证任意深链直达时侧边栏动态菜单已就绪；未登录不拉取（§46：localStorage 仅缓存，后端为真源）
  if (auth.isLoggedIn && Object.keys(appStore.userApps).length === 0) {
    void appStore.hydrate()
  }
})

onBeforeUnmount(() => media?.removeEventListener('change', updateViewport))

watch(collapsed, (val) => {
  localStorage.setItem('csidebar-collapsed', String(val))
})

// 路由变化时关闭移动端抽屉
watch(() => route.fullPath, () => {
  drawerOpen.value = false
})
</script>

<style scoped>
/* 笔记工作台独占顶栏下方视口，模式切换只影响布局，不卸载编辑器。 */
.pc-studio.notes-shell .clayout-container {padding:0; height:calc(100dvh - var(--kb-nav-height));overflow:hidden}
.pc-studio.notes-shell .clayout-main {height:calc(100dvh - var(--kb-nav-height));min-height:0;overflow:hidden}
.pc-studio.notes-shell-wide .clayout-sidebar {display:none}
.pc-studio.notes-shell-wide .clayout-main {padding-left:0}
.pc-studio.notes-shell .clayout-bottomnav {display:none}
.pc-studio.notes-shell .clayout-main.has-bottom-nav {padding-bottom:0}

/* Header 为 fixed，故主体整体下移其高度 */
.clayout-body {
  padding-top: var(--kb-nav-height);
}

/* 桌面：为固定侧边栏预留宽度；移动端：不预留 */
.clayout-main {
  min-height: calc(100vh - var(--kb-nav-height));
  min-width: 0;
  transition: padding-left 200ms ease;
}

.clayout-container {
  width: 100%;
  max-width: none;
  min-width: 0;
  padding: var(--kb-page-padding-y) var(--kb-page-padding-x);
}

/* >=1024px：侧边栏常驻，主内容留出对应宽度 */
@media (min-width: 1024px) {
  .clayout-main {
    padding-left: var(--kb-sidebar-width);
  }

  .clayout-main.is-collapsed {
    padding-left: var(--kb-sidebar-collapsed-width);
  }
}

.clayout-sidebar {
  height: calc(100dvh - var(--kb-nav-height));
  position: fixed;
  top: var(--kb-nav-height);
  left: 0;
  bottom: 0;
  z-index: 40;
  /* 宽度由 .csidebar 自身定义（var(--kb-sidebar-width) / var(--kb-sidebar-collapsed-width)）；
     不使用 fit-content，否则 scoped CSS 优先级会覆盖 .csidebar 的 width 导致宽度坍缩 */
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
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(2px);
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
