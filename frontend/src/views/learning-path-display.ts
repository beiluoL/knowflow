import type { LearningPathVO } from '@/api/types'

/** Presentation only: the current path is supplied by the unified learning Store. */
export function selectVisiblePaths(paths: LearningPathVO[], current: LearningPathVO | null | undefined): LearningPathVO[] {
  const others = paths.filter(path => path.enrolled && path.id !== current?.id)
  return (current ? [current, ...others] : others).slice(0, 4)
}

/** Missing chapter counts are not equivalent to zero completed chapters. */
export function displayPathProgress(path: LearningPathVO): number | null {
  const value = path.progress ?? (path.completedChapters != null && path.chapterCount ? path.completedChapters / path.chapterCount * 100 : null)
  return value == null ? null : Math.min(100, Math.max(0, Math.round(value)))
}
