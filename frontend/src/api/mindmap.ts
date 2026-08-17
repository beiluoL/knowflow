// 思维导图接口：整图 CRUD。data 为前端维护的整图 JSON（节点/连线/视图变换）。
import { apiGet, apiPost, apiPut, apiDelete } from './request'

/** 思维导图整图数据结构（前后端一致的 JSON 契约） */
export interface MindMapNode {
  id: string
  text: string
  x: number
  y: number
  parentId: string | null
  collapsed?: boolean
  color?: string
}

export interface MindMapEdge {
  id: string
  source: string
  target: string
}

export interface MindMapView {
  scale: number
  tx: number
  ty: number
}

export interface MindMapData {
  nodes: MindMapNode[]
  edges: MindMapEdge[]
  view: MindMapView
}

export interface MindMapSummary {
  id: number
  title: string
  createTime: string
  updateTime: string
}

export interface MindMapDetail {
  id: number
  userId: number
  title: string
  data: MindMapData
  createTime: string
  updateTime: string
}

export function listMindMaps() {
  return apiGet<MindMapSummary[]>('/mindmaps')
}

export function getMindMap(id: number) {
  return apiGet<MindMapDetail>(`/mindmaps/${id}`)
}

export function createMindMap(payload: { title: string; data?: MindMapData }) {
  return apiPost<number>('/mindmaps', payload)
}

export function updateMindMap(id: number, payload: { title?: string; data?: MindMapData }) {
  return apiPut<void>(`/mindmaps/${id}`, payload)
}

export function deleteMindMap(id: number) {
  return apiDelete<void>(`/mindmaps/${id}`)
}
