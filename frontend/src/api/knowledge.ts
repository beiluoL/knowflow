// 知识图谱请求层：封装分类图谱、技术栈依赖图谱、概念图解、实体关系图谱的获取。
import { apiGet, apiPost } from './request'
import type {
  KnowledgeGraphVO,
  TechGraphVO,
  ConceptDiagramVO,
  EntityGraphVO,
  ExtractResultVO,
  KnowledgeProcessRequest,
  KnowledgeProcessResultVO,
  KnowledgeProcessAcceptRequest,
  KnowledgeAcceptResultVO,
  LearningContentGenerateRequest,
  LearningContentGenerationResultVO,
  LearningContentAcceptRequest,
  LearningContentAcceptResultVO,
} from './types'

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

  /** 实体关系知识图谱（A-RAG-04：AI 抽取实体+关系构建的真正知识图谱） */
  entityGraph: (categoryId?: number, docId?: number) => {
    const params = new URLSearchParams()
    if (categoryId) params.set('categoryId', String(categoryId))
    if (docId) params.set('docId', String(docId))
    const qs = params.toString()
    return apiGet<EntityGraphVO>(`/knowledge/entity-graph${qs ? `?${qs}` : ''}`)
  },

  /** 触发抽取并构建知识图谱（按分类或全部已发布文档），返回抽取统计 */
  buildEntityGraph: (categoryId?: number) => {
    const params = new URLSearchParams()
    if (categoryId) params.set('categoryId', String(categoryId))
    const qs = params.toString()
    return apiPost<ExtractResultVO>(`/knowledge/extract${qs ? `?${qs}` : ''}`)
  },

  // ===== Architecture-4.3：Knowledge Processing 2.0 =====

  /** 处理资料：AI 抽取 → 校验 → 返回候选（零写库）。候选需经 accept 审核后才进入 Knowledge Core。 */
  processMaterial: (data: KnowledgeProcessRequest) =>
    apiPost<KnowledgeProcessResultVO>('/knowledge/process', data),

  /** 接受候选：确定性校验后写入 Knowledge Core，可选关联学习节点（幂等） */
  acceptProcess: (data: KnowledgeProcessAcceptRequest) =>
    apiPost<KnowledgeAcceptResultVO>('/knowledge/process/accept', data),

  // ===== Architecture-4.4：Learning Content Generator =====

  /** 生成学习内容 Draft：Knowledge → 闪卡/费曼/场景（零写库，默认 DRAFT 状态） */
  generateContent: (data: LearningContentGenerateRequest) =>
    apiPost<LearningContentGenerationResultVO>('/knowledge/content/generate', data),

  /** 接受审核后的内容：确定性去重后写入既有学习实体（幂等，零学习事件） */
  acceptContent: (data: LearningContentAcceptRequest) =>
    apiPost<LearningContentAcceptResultVO>('/knowledge/content/accept', data),
}
