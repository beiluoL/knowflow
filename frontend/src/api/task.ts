// Things3 式任务清单接口封装。
import { apiGet, apiPost, apiPut, apiDelete } from './request'

export type SmartList = 'inbox' | 'today' | 'upcoming' | 'someday' | 'logbook' | 'all'

/** 任务节点（含嵌套子任务）。 */
export interface TaskNode {
  id: number
  listId: number | null
  parentId: number
  title: string
  notes: string
  status: number
  /** yyyy-MM-dd 或 null */
  scheduledDate: string | null
  dueDate: string | null
  someday: boolean
  /** 重要程度 0/1（四象限视图用）。 */
  important: number
  /** 紧急程度 0/1（四象限视图用）。 */
  urgent: number
  /** 看板阶段 0 待办 / 1 进行中 / 2 已完成。 */
  stage: number
  sortOrder: number
  children: TaskNode[]
  hasChildren: boolean
}

/** 清单 / 项目 / 领域。 */
export interface TaskListVO {
  id: number
  name: string
  kind: 'area' | 'project' | 'list'
  parentId: number
  color: string
  icon: string
  sortOrder: number
  taskCount: number
  doneCount: number
}

export interface TaskPayload {
  title?: string
  listId?: number | null
  parentId?: number | null
  notes?: string
  scheduledDate?: string | null
  dueDate?: string | null
  someday?: boolean
  important?: number
  urgent?: number
  stage?: number
  sortOrder?: number
  status?: number
}

export interface TaskListPayload {
  name: string
  kind?: 'area' | 'project' | 'list'
  parentId?: number | null
  color?: string
  icon?: string
  sortOrder?: number
}

// ===== 任务 =====
export function listTasks(smart: SmartList) {
  return apiGet<TaskNode[]>('/tasks', { smart })
}
export function listTasksByList(listId: number) {
  return apiGet<TaskNode[]>(`/tasks/list/${listId}`)
}
export function createTask(payload: TaskPayload) {
  return apiPost<number>('/tasks', payload)
}
export function updateTask(id: number, payload: TaskPayload) {
  return apiPut<void>(`/tasks/${id}`, payload)
}
export function setTaskStatus(id: number, status: number) {
  return apiPut<void>(`/tasks/${id}/status`, {}, { params: { status } })
}
export function deleteTask(id: number) {
  return apiDelete<void>(`/tasks/${id}`)
}

// ===== 看板 / 四象限 =====
/** 看板数据：当前用户全部顶层任务（parent_id=0）扁平列表。 */
export function listBoard() {
  return apiGet<TaskNode[]>('/tasks/board')
}
/** 更新看板阶段（0 待办 / 1 进行中 / 2 已完成），后端自动同步完成态。 */
export function updateTaskStage(id: number, stage: number) {
  return apiPut<void>(`/tasks/${id}/stage`, {}, { params: { stage } })
}

// ===== 清单 =====
export function listTaskLists() {
  return apiGet<TaskListVO[]>('/task-lists')
}
export function createTaskList(payload: TaskListPayload) {
  return apiPost<number>('/task-lists', payload)
}
export function updateTaskList(id: number, payload: TaskListPayload) {
  return apiPut<void>(`/task-lists/${id}`, payload)
}
export function deleteTaskList(id: number) {
  return apiDelete<void>(`/task-lists/${id}`)
}
