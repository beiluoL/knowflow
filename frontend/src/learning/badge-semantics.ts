/**
 * Interaction OS（UI/UX 5.1）§十八：统一数据状态标识（纯函数、可单测）
 *
 * ── 为什么需要它 ────────────────────────────────────────────────────────
 * 同一个语义状态在不同页面被各写一套颜色与文案：
 *   待复习 / 需复习 / 到期      →  三种叫法、三种底色
 *   薄弱  / 待加强 / 掌握度低    →  同上
 *   AI 生成 / 智能生成 / 生成   →  同上
 * 用户无法建立「同一种颜色 = 同一种状态」的直觉，这是「多个页面拼起来」的典型症状。
 *
 * 本模块把状态**收敛为唯一映射**：一个语义 → 一个语义色 + 一个中文标签。
 * 页面只允许 `badgeForX(...)`，禁止自行定义颜色/文案（§十八）。
 *
 * ── 语义色索引（与 Badge.vue 的 variant 一一对应）──────────────────────
 *   success  绿  已完成 / 已掌握
 *   warning  琥珀 需注意（薄弱）
 *   danger   红  异常
 *   info     蓝  进行中 / 提示
 *   learning 蓝（实心主色）学习中
 *   review   琥珀 待复习（到期待办）
 *   ai       暖橘 高光标识（令牌 --kf-highlight 的设计用途即「AI 推荐」）
 *   neutral  灰  未开始 / 未知
 *
 * ── 铁律 ────────────────────────────────────────────────────────────────
 * 1. 只映射**后端真实存在的状态**（`MasteryStatus`），不发明新状态。
 * 2. 不引入第二套状态系统（不新建 FlashcardMastery / ReviewState 之类）。
 */
import type { MasteryStatus } from '@/api/types'

/** 统一徽标语义（与 components/ui/Badge.vue 的 variant 同名） */
export type BadgeSemantic =
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'learning'
  | 'review'
  | 'ai'
  | 'neutral'

export interface StatusBadgeSpec {
  /** 用户可见文案（全站唯一叫法） */
  label: string
  /** 统一语义色 */
  semantic: BadgeSemantic
}

/**
 * 掌握状态 → 徽标（键即后端 `MasteryStatus` 枚举，一一对应、不多不少）。
 * 这样任何页面拿到 `knowledgeMastery.learningStatus` 都能直接取到同一个徽标。
 */
export const MASTERY_STATUS_BADGE: Record<MasteryStatus, StatusBadgeSpec> = {
  NOT_STARTED: { label: '未开始', semantic: 'neutral' },
  LEARNING: { label: '学习中', semantic: 'learning' },
  WEAK: { label: '薄弱', semantic: 'warning' },
  MASTERED: { label: '已掌握', semantic: 'success' },
  REVIEW_REQUIRED: { label: '待复习', semantic: 'review' },
}

/** 掌握状态 → 徽标（未知值降级为 neutral「未知」，不抛错、不伪造） */
export function badgeForMasteryStatus(status: MasteryStatus | null | undefined): StatusBadgeSpec {
  if (!status) return { label: '未知', semantic: 'neutral' }
  return MASTERY_STATUS_BADGE[status] ?? { label: '未知', semantic: 'neutral' }
}

/** AI 生成内容标识（知识整理产物 / AI 出题 / AI 推荐） */
export function badgeForAiGenerated(): StatusBadgeSpec {
  return { label: 'AI 生成', semantic: 'ai' }
}

/** 完成与否 */
export function badgeForCompleted(completed: boolean): StatusBadgeSpec {
  return completed
    ? { label: '已完成', semantic: 'success' }
    : { label: '未完成', semantic: 'neutral' }
}

/** 是否进入复习队列（闪卡/知识点到期） */
export function badgeForDue(due: boolean): StatusBadgeSpec {
  return due
    ? { label: '待复习', semantic: 'review' }
    : { label: '未到期', semantic: 'neutral' }
}

/**
 * 复习/学习进度描述（用于「今日学习 N 张」这类真实计数徽标）。
 * 计数为 0 时返回 neutral，避免把「0」渲染成警示色。
 */
export function badgeForCount(count: number, label: string, semantic: BadgeSemantic = 'review'): StatusBadgeSpec {
  return { label, semantic: count > 0 ? semantic : 'neutral' }
}
