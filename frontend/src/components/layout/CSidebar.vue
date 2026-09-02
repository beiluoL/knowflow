<template>
  <aside
    class="csidebar"
    :class="collapsed ? 'is-collapsed' : ''"
    :style="sidebarStyle"
    aria-label="主导航"
  >
    <!-- 一级导航 -->
    <nav class="csidebar-nav">
      <template v-for="group in groups" :key="group.label">
        <!-- 无二级：顶级直达（如「首页」） -->
        <router-link
          v-if="!group.children || group.children.length === 0"
          :to="group.path || '/'"
          class="csidebar-item csidebar-top"
          :class="{ 'is-active': isTopActive(group) }"
          :title="collapsed ? group.label : ''"
          :aria-label="group.label"
          :aria-current="isTopActive(group) ? 'page' : undefined"
          @click="$emit('navigate')"
        >
          <Icon :name="group.icon" :size="18" class="csidebar-icon" />
          <span v-if="!collapsed" class="csidebar-label">{{ group.label }}</span>
        </router-link>

        <!-- 有二级：可展开分组 -->
        <div v-else class="csidebar-group">
          <button
            type="button"
            class="csidebar-item csidebar-top"
            :class="{ 'is-parent-active': isGroupActive(group), 'is-active': isGroupCurrent(group) }"
            :title="collapsed ? group.label : ''"
            :aria-label="group.label"
            :aria-expanded="!collapsed && isOpen(group.label)"
            :aria-controls="`navgroup-${group.label}`"
            @click="onGroupClick(group)"
          >
            <Icon :name="group.icon" :size="18" class="csidebar-icon" />
            <span v-if="!collapsed" class="csidebar-label">{{ group.label }}</span>
            <Icon
              v-if="!collapsed"
              name="chevron-down"
              :size="14"
              class="csidebar-chevron"
              :class="isOpen(group.label) ? 'is-open' : ''"
            />
          </button>

          <!-- 二级导航：折叠态不渲染，避免图标堆叠 -->
          <div
            v-if="!collapsed && isOpen(group.label)"
            :id="`navgroup-${group.label}`"
            class="csidebar-sub"
          >
            <router-link
              v-for="child in group.children"
              :key="child.path"
              :to="child.path"
              class="csidebar-item csidebar-child"
              :class="{ 'is-active': isChildActive(child) }"
              :aria-current="isChildActive(child) ? 'page' : undefined"
              @click="$emit('navigate')"
            >
              <Icon :name="child.icon" :size="16" class="csidebar-icon" />
              <span class="csidebar-label">{{ child.label }}</span>
            </router-link>
          </div>
        </div>
      </template>
    </nav>

    <!-- 折叠开关 -->
    <div class="csidebar-footer">
      <button
        type="button"
        class="csidebar-item csidebar-collapse"
        :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="$emit('toggle-collapse')"
      >
        <Icon :name="collapsed ? 'chevron-right' : 'chevron-left'" :size="16" class="csidebar-icon" />
        <span v-if="!collapsed" class="csidebar-label">收起菜单</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
/**
 * C 端左侧主导航（Phase UI-2 新建）。
 *
 * 数据来自 @/config/navigation.ts 单一数据源，与路由、面包屑、高亮保持同源。
 *
 * 能力：
 *  - 一级/二级导航、当前项高亮（aria-current="page"）
 *  - 父级高亮：进入 /learning/review 时「学习」保持 active（isGroupActive）
 *  - 所在分组自动展开（isGroupActive → 默认展开）
 *  - 整体折叠（64px 纯图标），折叠后每个图标仍有 title + aria-label（§17/§54）
 *  - 键盘可达：分组用 button（Enter/Space 可展开），链接用 router-link（Enter 可跳转）
 *  - 颜色全部使用 --kb-* 令牌（§56），深色模式自动跟随
 *  - 分组展开状态持久化到 localStorage（§47，不新建状态系统）
 */
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { type NavGroup, type NavChild, findActiveGroup } from '@/config/navigation'
import { resolveNavigation } from '@/apps/navigation'
import { useAuthStore } from '@/stores/auth'
import { useBackgroundStore } from '@/stores/background'
import { useAppStore } from '@/stores/apps'

const props = defineProps<{ collapsed: boolean }>()

defineEmits<{
  'toggle-collapse': []
  navigate: []
}>()

const route = useRoute()
const auth = useAuthStore()
const bgStore = useBackgroundStore()
const appStore = useAppStore()

// UI/UX 3.0：动态导航（§7/§74）——AppRegistry + UserApp 实时推导，与移动端共用同一 Resolver
const groups = computed(() =>
  resolveNavigation(auth.isLoggedIn, auth.isAdmin, appStore.userApps),
)

/** 用户手动展开/收起的分组（键为分组名，值为是否强制收起） */
const STORAGE_KEY = 'csidebar-open-groups'
const openState = ref<Record<string, boolean>>(loadState())

function loadState(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Record<string, boolean>) : {}
  } catch {
    return {}
  }
}

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(openState.value))
  } catch {
    /* 隐私模式下 localStorage 不可用，忽略即可 */
  }
}

/**
 * 分组是否展开：
 *  - 用户显式设置过 → 以用户为准；
 *  - 否则：当前路由落在该分组内 → 自动展开（§19 父级高亮配套体验）。
 */
function isOpen(label: string): boolean {
  const g = groups.value.find((x) => x.label === label)
  if (!g) return false
  if (label in openState.value) return openState.value[label]
  return isGroupActive(g)
}

function onGroupClick(group: NavGroup) {
  openState.value = { ...openState.value, [group.label]: !isOpen(group.label) }
  persist()
}

function pathMatches(current: string, target: string): boolean {
  if (!target) return false
  if (target === '/') return current === '/'
  if (current === target) return true
  return current.startsWith(target.endsWith('/') ? target : `${target}/`)
}

/** 顶级直达项是否激活（如「首页」需精确匹配，避免所有路径都命中 /） */
function isTopActive(group: NavGroup): boolean {
  if (!group.path) return false
  if (group.path === '/') return route.path === '/'
  return pathMatches(route.path, group.path)
}

/** 分组内任一子项命中路由，或路由按 match 归属该工作区 → 父级高亮（§96-99） */
function isGroupActive(group: NavGroup): boolean {
  // 优先用 config 的「最长前缀优先」判定，保证 /workbench/notes 归知识、/workbench 归学习
  return findActiveGroup(route.path)?.label === group.label
}

/** 分组自身路径恰好等于当前路由 */
function isGroupCurrent(group: NavGroup): boolean {
  return !!group.path && route.path === group.path
}

function isChildActive(child: NavChild): boolean {
  return pathMatches(route.path, child.path)
}

// 背景激活时侧边栏切换为毛玻璃（与既有 Sidebar.vue 行为保持一致）
const sidebarStyle = computed(() => {
  if (bgStore.isActive) {
    return {
      background: 'rgba(255, 255, 255, 0.78)',
      borderRight: '1px solid rgba(255, 255, 255, 0.30)',
      backdropFilter: 'blur(16px)',
      WebkitBackdropFilter: 'blur(16px)',
    }
  }
  return {
    background: 'var(--kb-sidebar)',
    borderRight: '1px solid var(--kb-border)',
  }
})

// 路由变化时，若落在某分组内则清除该分组的「手动收起」记忆，保证自动展开
watch(
  () => route.path,
  () => {
    const g = groups.value.find((x) => isGroupActive(x))
    if (g && openState.value[g.label] === false) {
      openState.value = { ...openState.value, [g.label]: true }
      persist()
    }
  },
)
</script>

<style scoped>
.csidebar {
  display: flex;
  flex-direction: column;
  width: 240px;
  height: 100%;
  flex-shrink: 0;
  transition: width 200ms ease;
}

.csidebar.is-collapsed {
  width: 64px;
}

.csidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 8px;
}

.csidebar-footer {
  flex-shrink: 0;
  padding: 8px;
  border-top: 1px solid var(--kb-border);
}

.csidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: transparent;
  color: var(--kb-sidebar-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  line-height: 1.4;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background-color 200ms ease, color 200ms ease;
}

.csidebar-item:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.csidebar-item:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}

.csidebar-icon {
  flex-shrink: 0;
}

.csidebar-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.csidebar-top {
  font-weight: 500;
}

/* 父级高亮：当前路由落在分组内 */
.csidebar-top.is-parent-active {
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
}

/* 当前页面精确命中 */
.csidebar-item.is-active {
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  font-weight: 600;
}

.csidebar-chevron {
  flex-shrink: 0;
  transition: transform 200ms ease;
}

.csidebar-chevron.is-open {
  transform: rotate(180deg);
}

.csidebar-sub {
  margin: 2px 0 6px;
  padding-left: 12px;
  border-left: 1px solid var(--kb-border);
  margin-left: 18px;
}

.csidebar-child {
  padding: 6px 10px;
  font-weight: 400;
  color: var(--kb-muted-foreground);
}

.csidebar-child:hover {
  color: var(--kb-foreground);
}

/* 折叠态：图标居中，保证点击热区 */
.csidebar.is-collapsed .csidebar-item {
  justify-content: center;
  padding: 8px 0;
}

.csidebar.is-collapsed .csidebar-sub {
  display: none;
}

.csidebar-collapse {
  color: var(--kb-muted-foreground);
}
</style>
