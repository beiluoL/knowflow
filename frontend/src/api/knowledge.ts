// 知识图谱请求层：封装分类图谱、技术栈依赖图谱、概念图解的获取。
import { apiGet, apiPost } from './request'
import type { KnowledgeGraphVO, TechGraphVO, ConceptDiagramVO } from './types'

export const knowledgeApi = {
  /** 分类-文档层级图谱 */
  graph: () => apiGet<KnowledgeGraphVO>('/knowledge/graph'),

  /** 技术栈依赖图谱（AI 生成） */
  techGraph: (topic: string, categoryId?: number) => {
    const params = new URLSearchParams({ topic })
    if (categoryId) params.set('categoryId', String(categoryId))
    return apiGet<TechGraphVO>(`/knowledge/tech-graph?${params.toString()}`)
  },

  /** 概念可视化图解（优先读缓存，未命中则 AI 生成并持久化） */
  conceptDiagram: (concept: string) =>
    apiGet<ConceptDiagramVO>(`/knowledge/concept-diagram?concept=${encodeURIComponent(concept)}`),

  /** 重新生成概念图解（删除旧缓存，AI 重新生成并持久化） */
  regenerateConceptDiagram: (concept: string) =>
    apiPost<ConceptDiagramVO>(`/knowledge/concept-diagram/regenerate?concept=${encodeURIComponent(concept)}`),
}
