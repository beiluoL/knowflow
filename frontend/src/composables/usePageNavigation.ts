/**
 * UI/UX 3.3：统一页面导航模型（Page Navigation Model）
 *
 * 解决「页面之间缺乏返回路径」问题：所有页面共用同一套
 * 「返回 / 父级 / 兜底」逻辑，不再各自写 `router.back()`。
 * 直接 URL / 刷新 / 书签 / 新标签页进入时，`router.back()` 会把用户带离应用，
 * 本 composable 提供可控的兜底。
 *
 * 返回优先级（§5）：
 *   1. 用户真实 history 可返回（window.history.state.back 存在）→ router.back()
 *   2. 否则回退到「当前页面的合理父级」（见 back / goToParent）
 *   3. 最终兜底 → Home('/')
 *
 * 父级判定复用 config/navigation.ts 的 findActiveGroup（最长前缀优先），
 * App 归属复用 registry.ts 的 APP_DEFINITIONS.route，保证与侧栏 / 面包屑同源。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { findActiveGroup, type Crumb } from '@/config/navigation'
import { APP_DEFINITIONS } from '@/apps/registry'

export interface NavBackTarget {
  /** 返回按钮展示的「上一层」名称（如 学习 / 闪卡） */
  label: string
  /** 点击返回后跳转的路径 */
  to: string
}

export function usePageNavigation() {
  const route = useRoute()
  const router = useRouter()

  /** 当前路由所属一级工作区（最长前缀优先） */
  const group = computed(() => findActiveGroup(route.path))

  /** 工作区首页路径：取该组第一个固定子项（学习首页/知识首页/AI 助手）；无则取组自身 path */
  const workspaceHome = computed<string>(() => {
    const g = group.value
    if (!g) return '/'
    return g.children?.[0]?.path ?? g.path ?? '/'
  })

  /** 命中当前路由（或为其前缀）的 App 定义（动态 App 归属） */
  const app = computed(() => {
    return APP_DEFINITIONS.find((a) => {
      if (!a.route) return false
      return route.path === a.route || route.path.startsWith(`${a.route}/`)
    })
  })

  /** 是否身处某 App 的更深层页面（App 路由之下的子路由，如 /learning/flashcards/123） */
  const isDeepInApp = computed(
    () => !!app.value && route.path !== app.value.route && route.path.startsWith(`${app.value.route}/`),
  )

  /**
   * 返回目标（Back 按钮的「上一层」），遵循 §6 规则：
   *  - App 深层页 → 回 App 根（label = App 名，如 闪卡）
   *  - App 根页   → 回工作区首页（label = 工作区名，如 学习）
   *  - 工作区下沉页（如 /learning/paths）→ 回工作区首页
   *  - 工作区首页本身 → null（无需返回）
   */
  const back = computed<NavBackTarget | null>(() => {
    if (isDeepInApp.value && app.value) {
      return { label: app.value.name, to: app.value.route }
    }
    if (app.value) {
      return { label: group.value?.label ?? '返回', to: workspaceHome.value }
    }
    if (group.value) {
      if (route.path === workspaceHome.value) return null
      return { label: group.value.label, to: workspaceHome.value }
    }
    return null
  })

  /** 统一面包屑（供 PageHeader 使用）：[工作区] → [App]；末级由页面 title 体现 */
  const crumbs = computed<Crumb[]>(() => {
    const result: Crumb[] = []
    if (group.value) result.push({ label: group.value.label, to: workspaceHome.value })
    if (app.value) result.push({ label: app.value.name, to: app.value.route })
    return result
  })

  /**
   * 真正返回：优先真实 history，其次页面自身的上下文兜底，最后才是工作区父级。
   *
   * UI/UX 3.3.1 §16：对「真正存在上下文」的页面（文档列表 → 文档详情 → 编辑），
   * 直接 URL / 刷新 / 书签进入时也必须回到正确的上一层，而不是粗暴回到工作区首页。
   * 因此支持传入 fallback（如 DocEdit 传 `/doc/123`、DocDetail 传 `/docs`）。
   *
   * @param fallback 无 history 可返回时的上下文兜底路径；不传则回退到工作区父级
   */
  function goBack(fallback?: string): void {
    const h = window.history.state as { back?: string } | null
    if (h && h.back) {
      router.back()
      return
    }
    if (fallback && fallback !== route.path) {
      router.push(fallback)
      return
    }
    goToParent()
  }

  /** 回退到合理父级 */
  function goToParent(): void {
    const target = back.value?.to ?? '/'
    if (route.path === target) {
      router.push('/')
      return
    }
    router.push(target)
  }

  /** 直接回到工作区首页 */
  function goToWorkspace(): void {
    router.push(workspaceHome.value)
  }

  return { group, app, workspaceHome, isDeepInApp, back, crumbs, goBack, goToParent, goToWorkspace }
}
