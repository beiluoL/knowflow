// 绘图编辑器接口：整图 CRUD。data 为前端维护的 vue-flow 整图 JSON（nodes/edges）。
import { apiGet, apiPost, apiPut, apiDelete } from './request'

/** vue-flow 节点（精简契约，前后端一致） */
export interface DrawingNode {
  id: string
  position: { x: number; y: number }
  data: { label: string; [k: string]: unknown }
  type?: string
  [k: string]: unknown
}

/** vue-flow 连线（精简契约） */
export interface DrawingEdge {
  id: string
  source: string
  target: string
  [k: string]: unknown
}

/** 整图数据结构（vue-flow 契约） */
export interface DrawingData {
  nodes: DrawingNode[]
  edges: DrawingEdge[]
}

export interface DrawingSummary {
  id: number
  title: string
  type: string
  createTime: string
  updateTime: string
}

export interface DrawingDetail {
  id: number
  userId: number
  title: string
  type: string
  data: DrawingData
  createTime: string
  updateTime: string
}

export function listDrawings() {
  return apiGet<DrawingSummary[]>('/drawings')
}

export function getDrawing(id: number) {
  return apiGet<DrawingDetail>(`/drawings/${id}`)
}

export function createDrawing(payload: { title: string; type?: string; data?: DrawingData }) {
  return apiPost<number>('/drawings', payload)
}

export function updateDrawing(id: number, payload: { title?: string; data?: DrawingData }) {
  return apiPut<void>(`/drawings/${id}`, payload)
}

export function deleteDrawing(id: number) {
  return apiDelete<void>(`/drawings/${id}`)
}
