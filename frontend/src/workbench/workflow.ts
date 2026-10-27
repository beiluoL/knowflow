import { getAppByKey } from '@/apps/registry'
import type { WorkbenchOverview } from '@/api/types'

export type WorkbenchStage = 'capture' | 'notes' | 'review' | 'story'
// 流程元数据，不重复应用分类；路由、图标与描述均来自 Registry。
export const WORKBENCH_STAGES: { key: WorkbenchStage; appKey: string; label: string; metric: keyof WorkbenchOverview; metricLabel: string; help: string }[] = [
  { key: 'capture', appKey: 'capture', label: '收集箱', metric: 'captureInbox', metricLabel: '待整理', help: '先记录，再整理。保留摘录和来源，整理为笔记后原条目仍会保留。' },
  { key: 'notes', appKey: 'cornell', label: '整理笔记', metric: 'noteTotal', metricLabel: '篇笔记', help: '用线索栏提问，在笔记栏记录要点，再用自己的话总结。可以直接新建，不必先收集。' },
  { key: 'review', appKey: 'spaced-review', label: '复习', metric: 'reviewDue', metricLabel: '今日到期', help: '先尝试回忆，再看答案并自评。系统依据反馈安排下次复习；排程与自评不等于实际掌握度。' },
  { key: 'story', appKey: 'feynman', label: '输出', metric: 'storyDraft', metricLabel: '篇未发布', help: '用讲解、故事或实例把知识讲给他人听。讲不清的地方可以返回笔记补充，也可以创建复习问题。' },
]
export function getWorkbenchStages() {
  return WORKBENCH_STAGES.map(stage => ({ ...stage, app: getAppByKey(stage.appKey)! }))
}
export function positiveId(value: unknown): number | undefined {
  if (Array.isArray(value) || value === null || value === '' || value === undefined) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}
