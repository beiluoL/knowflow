<template>
  <aside
    class="csidebar"
    data-test="pc-primary-navigation"
    :class="collapsed ? 'is-collapsed' : ''"
    :style="sidebarStyle"
    aria-label="主导航"
  >
    <button type="button" class="ui-v2-drawer-close lg:hidden" aria-label="关闭导航菜单" @click="$emit('navigate')"><Icon name="x" :size="20" /><span>关闭导航</span></button>
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

          <!-- 展开态：带文字的二级列表（用户可手动收起） -->
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
              <Icon :name="child.icon" :size="18" class="csidebar-icon" />
              <span class="csidebar-label">{{ child.label }}</span>
            </router-link>
          </div>

          <!-- 折叠态：图标 rail（仍显示 App 图标，tooltip 显示全称，§10） -->
          <template v-else-if="collapsed">
            <router-link
              v-for="child in group.children"
              :key="child.path"
              :to="child.path"
              class="csidebar-item csidebar-child csidebar-subrail-item"
              :class="{ 'is-active': isChildActive(child) }"
              :title="child.label"
              :aria-label="child.label"
              :aria-current="isChildActive(child) ? 'page' : undefined"
              @click="$emit('navigate')"
            >
              <Icon :name="child.icon" :size="18" class="csidebar-icon" />
            </router-link>
          </template>
        </div>
      </template>
    </nav>

    <RecentChapterLearning v-if="!collapsed && route.name==='ChapterLearn' && auth.isLoggedIn" />
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
 *  - 颜色全部使用 --kf-* 令牌（§56），深色模式自动跟随
 *  - 分组展开状态持久化到 localStorage（§47，不新建状态系统）
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import RecentChapterLearning from '@/components/Learning/RecentChapterLearning.vue'
import { type NavGroup, type NavChild, findActiveGroup } from '@/config/navigation'
import { resolveNavigation } from '@/apps/navigation'
import { useAuthStore } from '@/stores/auth'
import { useBackgroundStore } from '@/stores/background'
import { useAppStore } from '@/stores/apps'

const props = defineProps<{ collapsed: boolean }>()

const emit = defineEmits<{
  'toggle-collapse': []
  navigate: []
}>()

const route = useRoute()
const router = useRouter()
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

/**
 * 分组标题点击（3.3.1 §9：折叠态不得出现「死了的按钮」）。
 *  - 折叠态：子项已渲染为图标 rail，展开/收起子项在此状态下没有任何视觉反馈，
 *    因此改为直达该工作区首页（学习→学习首页 / 知识→知识首页 / AI→AI 助手）。
 *  - 展开态：保持原行为，切换子项展开 / 收起。
 */
function onGroupClick(group: NavGroup) {
  if (props.collapsed) {
    const target = group.children?.[0]?.path ?? group.path ?? '/'
    void router.push(target)
    emit('navigate')
    return
  }
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
    background: 'var(--kf-sidebar)',
    borderRight: '1px solid var(--kf-border)',
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
  width: var(--kf-sidebar-width);
  height: 100%;
  flex-shrink: 0;
  transition: width var(--kf-shell-transition) ease;
}

.csidebar.is-collapsed {
  width: var(--kf-sidebar-collapsed-width);
}

.csidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: var(--kf-nav-item-gap);
  padding: var(--kf-space-3) var(--kf-space-2);
}

.csidebar-footer {
  flex-shrink: 0;
  padding: var(--kf-space-3);
  border-top: 1px solid var(--kf-border);
}

.csidebar-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: var(--kf-nav-item-height);
  padding: 0 var(--kf-space-3);
  /* 3.3.1 §7：240px→64px 过渡期间裁掉溢出文字，避免内容“飞出”/闪烁 */
  overflow: hidden;
  border-radius: var(--kf-radius-md);
  border: none;
  background: transparent;
  color: var(--kf-sidebar-foreground);
  font-size: var(--kf-fs-body-md);
  font-weight: var(--kf-fw-label);
  line-height: 1.4;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background-color 200ms ease, color 200ms ease;
}

.csidebar-item:hover {
  background: var(--kf-muted);
  color: var(--kf-foreground);
}

.csidebar-item:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kf-ring);
}

.csidebar-icon {
  width: var(--kf-icon-slot);
  height: var(--kf-icon-slot);
  flex: 0 0 var(--kf-icon-slot);
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
  color: var(--kf-primary);
  background: var(--kf-primary-soft);
}

/* 当前页面精确命中 */
.csidebar-item.is-active {
  color: var(--kf-primary);
  background: var(--kf-primary-soft);
  font-weight: 600;
}

/* 3.3.1 §12：激活态不能只有一层浅背景——补一条主色左侧指示条，一眼可辨 */
.csidebar-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  border-radius: 0 2px 2px 0;
  background: var(--kf-primary);
}

.csidebar-chevron {
  flex-shrink: 0;
  transition: transform 200ms ease;
}

.csidebar-chevron.is-open {
  transform: rotate(180deg);
}

.csidebar-sub {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 2px 0 6px;
  padding-left: 8px;
  border-left: 1px solid var(--kf-border);
  margin-left: 19px;
}

.csidebar-child {
  min-height: var(--kf-nav-item-height-sm);
  padding: 0 var(--kf-space-2);
  gap: 10px;
  font-weight: 400;
  color: var(--kf-muted-foreground);
}

.csidebar-child .csidebar-icon {
  width: var(--kf-icon-md);
  height: var(--kf-icon-md);
  flex-basis: var(--kf-icon-md);
}

.csidebar-child:hover {
  color: var(--kf-foreground);
}

/* 折叠态：图标居中，保证点击热区 */
.csidebar.is-collapsed .csidebar-item {
  justify-content: center;
  padding: 0;
}

.csidebar.is-collapsed .csidebar-sub {
  display: none;
}

/* 折叠态图标 rail：单个 App 图标的点击热区，tooltip 由 :title 提供 */
.csidebar-subrail-item {
  justify-content: center;
  padding: 0;
  min-height: 34px;
}
.csidebar-subrail-item .csidebar-label {
  display: none;
}

.csidebar-collapse {
  color: var(--kf-muted-foreground);
}
</style>
