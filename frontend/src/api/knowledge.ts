// 知识图谱请求层：封装图谱节点/关系数据的获取。
import { apiGet } from './request'
import type { KnowledgeGraphVO } from './types'

export const knowledgeApi = {
  graph: () => apiGet<KnowledgeGraphVO>('/knowledge/graph'),
}
