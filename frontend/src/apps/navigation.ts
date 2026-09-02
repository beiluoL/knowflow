/**
 * UI/UX 3.0：App-Driven Navigation Resolver（唯一导航解析来源，§7/§41）
 *
 * 数据流（§1）：
 *   AppRegistry（registry.ts，系统有哪些 App）
 *   + UserApp（stores/apps.ts，用户启用哪些 App）
 *   → resolveNavigation()（本文件）
 *   → NavigationTree（config/navigation.ts 的 NavGroup[]，含动态 App 子菜单）
 *
 * 设计原则：
 *   - 不硬编码 App key 数组（§20）；动态子菜单完全由 registry × UserApp 推导。
 *   - 一级导航固定 5 个（首页/学习/知识/AI/应用），由 config/navigation.ts 收口（§2/§73）。
 *   - 可见性（§41）：app.navVisible && 平台 ACTIVE && userApp.installed && userApp.enabled。
 *   - 排序（§18/§86）：UserApp.sortOrder 优先，缺省用 AppDefinition.order。
 *
 * ── UI/UX 3.2 Core Menu Minimalism（§60-67/§84）──────────────────────────────
 * 每个工作区的输出恒为：**1 个核心固定入口 + N 个 enabled App**（§28/§29/§31）。
 * 固定入口不参与 sortOrder（§84-85：核心入口永远稳定，App 由用户自己组织），
 * 因此排序只对动态部分生效，统一由本文件输出（§87），Sidebar / Mobile / Home 共用。
 */
import type { AppDefinition, UserAppState, AppNavSection } from './types'
import type { NavGroup, NavChild } from '@/config/navigation'
import { visibleGroups } from '@/config/navigation'
import { getApps } from './registry'
// §34/§175：平台停用判定复用 Architecture-5 的既有实现，不在此重复造一份规则
import { isAppPlatformDisabled } from '@/components/workflow/actionState'

export type { AppNavSection } from './types'

/** 动态 App 子菜单承载的一级工作区 */
export const NAV_SECTION_LABELS: Record<AppNavSection, string> = {
  learning: '学习',
  knowledge: '知识',
  ai: 'AI',
}

/** App 所属工作区中文标签（Toast 文案用，§225：用户语言、不暴露技术细节）；无分区返回 '' */
export function navSectionLabel(app: AppDefinition): string {
  return app.navSection ? NAV_SECTION_LABELS[app.navSection] : ''
}

/**
 * 应用是否应在导航中显示（§41）。
 *
 * 四个条件全部满足才可见：
 *   ① app.navVisible（Registry 允许进动态菜单，§13）
 *   ② 平台未停用（app.status === 'ACTIVE'，§34/§175：平台下架的应用不进菜单）
 *   ③ userApp.installed
 *   ④ userApp.enabled
 * 非法状态组合（installed=false 但 enabled=true）按 stores/apps.ts 归一化规则视为不可见（§17）。
 */
export function canShowInNavigation(app: AppDefinition, state?: UserAppState): boolean {
  if (!app.navVisible) return false
  if (isAppPlatformDisabled(app.status)) return false
  if (!state) return false
  if (!state.installed) return false
  if (!state.enabled) return false
  return true
}

/**
 * 按分区聚合「已启用 App」为导航子项（§9/§10/§90）。
 * 返回 learning / knowledge / ai 三个桶，各自按 order 升序。
 */
export function resolveAppNavChildren(
  userApps: Record<string, UserAppState>,
): Record<AppNavSection, NavChild[]> {
  const items: { section: AppNavSection; child: NavChild; order: number }[] = []

  for (const app of getApps()) {
    if (!app.navSection) continue
    const state = userApps[app.key]
    if (!canShowInNavigation(app, state)) continue
    const order = state?.sortOrder ?? app.order ?? 999
    items.push({
      section: app.navSection,
      order,
      child: { label: app.name, path: app.route, icon: app.icon, auth: true },
    })
  }

  items.sort((a, b) => a.order - b.order)

  const buckets: Record<AppNavSection, NavChild[]> = { learning: [], knowledge: [], ai: [] }
  for (const it of items) buckets[it.section].push(it.child)
  return buckets
}

function sectionOfGroup(label: string): AppNavSection | null {
  for (const [section, lbl] of Object.entries(NAV_SECTION_LABELS) as [AppNavSection, string][]) {
    if (lbl === label) return section
  }
  return null
}

/**
 * 唯一导航解析入口（§7）。
 * 输入 AppRegistry + UserApp → 输出完整 NavGroup[]（含动态 App 子菜单）。
 *
 * @param isLoggedIn 是否登录（未登录不注入需鉴权的 App 子菜单）
 * @param isAdmin    是否管理员
 * @param userApps   用户 App 状态（useAppStore.userApps）
 */
export function resolveNavigation(
  isLoggedIn: boolean,
  isAdmin: boolean,
  userApps: Record<string, UserAppState>,
): NavGroup[] {
  const base = visibleGroups(isLoggedIn, isAdmin)
  if (!isLoggedIn) return base // 未登录：仅固定入口，无 App 子菜单

  const children = resolveAppNavChildren(userApps)
  return base.map((g) => {
    const section = sectionOfGroup(g.label)
    if (!section) return g
    const apps = children[section]
    if (apps.length === 0) return g
    // 固定工作区入口在前，动态 App 在后（§9/§10）
    return { ...g, children: [...(g.children ?? []), ...apps] }
  })
}
