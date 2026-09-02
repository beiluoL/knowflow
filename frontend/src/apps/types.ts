/**
 * Architecture-1：Mini App 核心类型定义
 *
 * KnowFlow 从「功能集合」收敛为「Core + Mini Apps」：
 *   - Core：User / Knowledge / LearningEvent / KnowledgeMastery / Learning Path / AI / Settings
 *   - Mini App：围绕一个明确学习任务的独立工具（闪卡/练习/错题/专注/费曼/康奈尔…）
 *
 * 本文件只定义「类型与边界」（§61-62），不实现沙箱/动态执行（§4 禁止动态代码下载与 eval）。
 * Mini App 不拥有独立 User / Knowledge / Mastery / SRS，统一通过 Core 能力访问（§10-14）。
 */

export type AppCategory =
  | 'MEMORY' // 记忆
  | 'PRACTICE' // 练习
  | 'UNDERSTANDING' // 理解
  | 'ORGANIZATION' // 整理
  | 'FOCUS' // 专注
  | 'CODING' // 实战
  | 'AI' // AI

/**
 * 应用导航分区（UI/UX 3.0）：对应顶部一级工作区中承载「动态 App 子菜单」的入口。
 * 仅 学习 / 知识 / AI 三个工作区动态挂载已启用 App；首页/应用不在此列。
 * 「应用」页本身是 App 管理中心，不展开为子菜单。
 */
export type AppNavSection = 'learning' | 'knowledge' | 'ai'

/** App 状态（§71）：LEGACY 不进推荐/默认安装，但历史数据保留（§111） */
export type AppStatus = 'ACTIVE' | 'BETA' | 'LEGACY'

/** 应用能力声明（§26）——第一版仅作元数据，不实现沙箱权限（§27） */
export type AppCapability =
  | 'knowledge.read'
  | 'knowledge.write'
  | 'mastery.read'
  | 'review.read'
  | 'review.write'
  | 'learning-event.write'
  | 'learning-path.read'
  | 'learning-path.write'
  | 'ai.chat'

/** 应用定义（Manifest，只含元数据、不含代码 —— §103/§104） */
export interface AppDefinition {
  /** 全局唯一 key，同时用于用户状态 */
  key: string
  /** 中文名称（§96：统一中文，禁止中英混杂） */
  name: string
  /** 一句话描述：解决什么问题（§97） */
  description: string
  /** 图标名（必须存在于 Icon.vue，禁止 emoji —— §95） */
  icon: string
  category: AppCategory
  categoryLabel: string
  /** 应用真实入口路由（沿用既有业务路由，不新建 /apps/xxx —— §84/§148） */
  route: string
  version: string
  status: AppStatus
  /** 新用户默认安装（§88） */
  defaultInstalled: boolean
  /** 能力声明（仅元数据） */
  capabilities: AppCapability[]
  /**
   * UI/UX 3.0：是否允许出现在动态导航子菜单（§41）。
   * focus 等沉浸式 App 置 false，由固定入口承载（§13）。
   * 该字段由 registry.ts 在模块加载时统一富化（APP_NAV_META），13 个 App 字面量不重复书写。
   */
  navVisible?: boolean
  /** UI/UX 3.0：导航分区（所属一级工作区）。缺省不进入动态子菜单（如 focus） */
  navSection?: AppNavSection
  /** UI/UX 3.0：同区内排序（§18）；UserApp.sortOrder 优先，缺省用此值 */
  order?: number
}

/** 用户对某个应用的状态（§22/§108：installed / enabled / sort_order） */
export interface UserAppState {
  installed: boolean
  enabled: boolean
  sortOrder: number
}

/**
 * Mini App 上下文契约（§61-62）。
 * 第一版只声明边界；App 不持有独立 Router（§63）、不持有独立 Store（§64）、
 * 不持有独立 API 层（§65），统一走 Vue Router / Pinia / src/api。
 */
export interface MiniAppContext {
  app: AppDefinition
  user: { id: number; nickname: string } | null
  /** 统一由 Vue Router 导航 */
  navigate: (to: string) => void
}

/** 后端 user_app 返回的用户应用状态（Architecture-3，§122：Backend Source of Truth）。 */
export interface UserAppVO {
  appKey: string
  installed: boolean
  enabled: boolean
  sortOrder: number
}
