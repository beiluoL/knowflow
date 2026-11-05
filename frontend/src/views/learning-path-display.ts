import type { LearningPathVO } from '@/api/types'

/** 仅展示，当前路径由统一学习 Store 提供。 */
export function selectVisiblePaths(paths: LearningPathVO[], current: LearningPathVO | null | undefined): LearningPathVO[] {
  const others = paths.filter(path => path.enrolled && path.id !== current?.id)
  return (current ? [current, ...others] : others).slice(0, 4)
}

/** 缺少章节计数不等于已完成章节数为零。 */
export function displayPathProgress(path: LearningPathVO): number | null {
  const value = path.progress ?? (path.completedChapters != null && path.chapterCount ? path.completedChapters / path.chapterCount * 100 : null)
  return value == null ? null : Math.min(100, Math.max(0, Math.round(value)))
}
