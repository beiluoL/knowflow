// 学习工作台接口：输入（收集箱）→ 整理（康奈尔笔记）→ 复习（间隔重复+记忆宫殿）→ 输出（费曼故事）四模块闭环。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  WorkbenchOverview,
  WbCapture,
  WbCapturePayload,
  WbNote,
  WbNotePayload,
  WbReviewCard,
  WbReviewCardVO,
  WbReviewCardPayload,
  WbReviewGradePayload,
  WbReviewGradeResult,
  WbPalace,
  WbPalacePayload,
  WbPalaceLoci,
  WbPalaceLociPayload,
  WbStory,
  WbStoryPayload,
  CategoryVO,
  WbForgettingCurve,
  WbRecallSession,
  WbRecallSessionPayload,
} from './types'

// ============================ 总览 ============================
/** 工作台总览统计（四模块概览指标） */
export function getWorkbenchOverview() {
  return apiGet<WorkbenchOverview>('/workbench/overview')
}

// ============================ 模块一：收集箱 ============================
export function listCaptures(params?: { status?: string; categoryId?: number; keyword?: string }) {
  return apiGet<WbCapture[]>('/workbench/captures', params)
}
export function getCapture(id: number) {
  return apiGet<WbCapture>(`/workbench/captures/${id}`)
}
export function createCapture(payload: WbCapturePayload) {
  return apiPost<number>('/workbench/captures', payload)
}
export function updateCapture(id: number, payload: WbCapturePayload) {
  return apiPut<void>(`/workbench/captures/${id}`, payload)
}
export function deleteCapture(id: number) {
  return apiDelete<void>(`/workbench/captures/${id}`)
}
export function setCaptureStatus(id: number, status: string) {
  return apiPut<void>(`/workbench/captures/${id}/status`, undefined, { params: { status } })
}
export function toggleCaptureStar(id: number) {
  return apiPut<void>(`/workbench/captures/${id}/star`)
}

// ============================ 模块二：康奈尔笔记 ============================
export function listNotes(params?: { captureId?: number; categoryId?: number; keyword?: string }) {
  return apiGet<WbNote[]>('/workbench/notes', params)
}
export function getNote(id: number) {
  return apiGet<WbNote>(`/workbench/notes/${id}`)
}
export function createNote(payload: WbNotePayload) {
  return apiPost<number>('/workbench/notes', payload)
}
export function updateNote(id: number, payload: WbNotePayload) {
  return apiPut<void>(`/workbench/notes/${id}`, payload)
}
export function deleteNote(id: number) {
  return apiDelete<void>(`/workbench/notes/${id}`)
}

// ============================ 模块三：间隔重复 ============================
export function listReviews(params?: { categoryId?: number; noteId?: number }) {
  return apiGet<WbReviewCardVO[]>('/workbench/reviews', params)
}
export function drawReviews(limit = 20) {
  return apiGet<WbReviewCardVO[]>('/workbench/reviews/draw', { limit })
}
export function createReview(payload: WbReviewCardPayload) {
  return apiPost<number>('/workbench/reviews', payload)
}
export function updateReview(id: number, payload: WbReviewCardPayload) {
  return apiPut<void>(`/workbench/reviews/${id}`, payload)
}
export function deleteReview(id: number) {
  return apiDelete<void>(`/workbench/reviews/${id}`)
}
export function gradeReview(id: number, payload: WbReviewGradePayload) {
  return apiPost<WbReviewGradeResult>(`/workbench/reviews/${id}/grade`, payload)
}
export function toggleReviewSuspend(id: number) {
  return apiPut<void>(`/workbench/reviews/${id}/suspend`)
}

// ============================ 模块三扩展：记忆宫殿 ============================
export function listPalaces() {
  return apiGet<WbPalace[]>('/workbench/palaces')
}
export function getPalace(id: number) {
  return apiGet<WbPalace>(`/workbench/palaces/${id}`)
}
export function createPalace(payload: WbPalacePayload) {
  return apiPost<number>('/workbench/palaces', payload)
}
export function updatePalace(id: number, payload: WbPalacePayload) {
  return apiPut<void>(`/workbench/palaces/${id}`, payload)
}
export function deletePalace(id: number) {
  return apiDelete<void>(`/workbench/palaces/${id}`)
}
export function listLoci(palaceId: number) {
  return apiGet<WbPalaceLoci[]>(`/workbench/palaces/${palaceId}/loci`)
}
export function createLoci(payload: WbPalaceLociPayload) {
  return apiPost<number>('/workbench/loci', payload)
}
export function updateLoci(id: number, payload: WbPalaceLociPayload) {
  return apiPut<void>(`/workbench/loci/${id}`, payload)
}
export function deleteLoci(id: number) {
  return apiDelete<void>(`/workbench/loci/${id}`)
}

// ============================ 模块四：费曼故事 ============================
export function listStories(params?: { categoryId?: number; status?: string; keyword?: string }) {
  return apiGet<WbStory[]>('/workbench/stories', params)
}
export function getStory(id: number) {
  return apiGet<WbStory>(`/workbench/stories/${id}`)
}
export function createStory(payload: WbStoryPayload) {
  return apiPost<number>('/workbench/stories', payload)
}
export function updateStory(id: number, payload: WbStoryPayload) {
  return apiPut<void>(`/workbench/stories/${id}`, payload)
}
export function deleteStory(id: number) {
  return apiDelete<void>(`/workbench/stories/${id}`)
}

// ============================ 公共：知识库分类下拉 ============================
/** 获取真实 doc_category 分类树（用于收集箱/宫殿/位点等归类下拉） */
export function getCategoryTree() {
  return apiGet<CategoryVO[]>('/categories/tree')
}

// ============================ 复习：遗忘曲线可视化 ============================
/** 遗忘曲线：按日聚合复习量与遗忘率走势（基于 wb_review_log） */
export function getForgettingCurve(days = 30) {
  return apiGet<WbForgettingCurve>('/workbench/reviews/forgetting-curve', { days })
}

// ============================ 复习扩展：主动回忆（三轮闭卷默写） ============================
/** 主动回忆会话列表 */
export function listRecallSessions() {
  return apiGet<WbRecallSession[]>('/workbench/recall-sessions')
}
/** 主动回忆会话详情 */
export function getRecallSession(id: number) {
  return apiGet<WbRecallSession>(`/workbench/recall-sessions/${id}`)
}
/** 创建主动回忆会话（填原文与标题） */
export function createRecallSession(payload: WbRecallSessionPayload) {
  return apiPost<number>('/workbench/recall-sessions', payload)
}
/** 提交某轮默写内容（自动比对计分） */
export function submitRecallRound(id: number, payload: WbRecallSessionPayload) {
  return apiPost<WbRecallSession>(`/workbench/recall-sessions/${id}/submit`, payload)
}
/** 删除主动回忆会话 */
export function deleteRecallSession(id: number) {
  return apiDelete<void>(`/workbench/recall-sessions/${id}`)
}
