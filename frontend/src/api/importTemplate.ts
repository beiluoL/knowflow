// 导入规则模板管理（驱动 Obsidian 目录一键导入的闪卡/题库抽取）请求层。
import { apiGet, apiPost, apiPut, apiDelete } from './request'

/** 模板类型 */
export type TemplateType = 'FLASHCARD' | 'QUIZ' | 'PATH'

/** 模板内容结构（JSON），由编辑器生成 */
export interface ImportTemplateContent {
  /** 字段结构定义 */
  fieldSchema: Array<{
    key: string
    label: string
    type: 'text' | 'markdown' | 'json' | 'number'
    required: boolean
    source: string
  }>
  /** 抽取规则 */
  rules: {
    headingLevel?: number
    maxPerDoc?: number
    questionTypes?: string[]
  }
  /** 校验规则 */
  validation: Array<{ field: string; rule: string; value?: number }>
  /** 展示样式 */
  style: {
    cardLayout?: 'qa' | 'flip' | 'list'
    showImage?: boolean
    theme?: 'light' | 'dark'
  }
  /** 数据源绑定 */
  sourceBinding: {
    mode: 'heading' | 'tag' | 'keyword'
    pattern: string
  }
}

/** 模板视图对象（列表/详情返回） */
export interface ImportTemplateVO {
  id: number
  userId: number
  name: string
  type: TemplateType
  description?: string
  content: string
  enabled: number
  isDefault: number
  isPreset: number
  createTime?: string
  updateTime?: string
  fieldCount?: number
  validationCount?: number
  ruleSummary?: string
}

/** 创建/更新请求体 */
export interface ImportTemplatePayload {
  name: string
  type: TemplateType
  description?: string
  content: string
  enabled?: number
}

export const importTemplateApi = {
  /** 模板列表（可按类型/启用状态过滤） */
  list: (params?: { type?: TemplateType; enabled?: number }) =>
    apiGet<ImportTemplateVO[]>('/import-templates', { params }),

  /** 模板详情 */
  detail: (id: number) => apiGet<ImportTemplateVO>(`/import-templates/${id}`),

  /** 创建模板（归属当前用户） */
  create: (payload: ImportTemplatePayload) =>
    apiPost<ImportTemplateVO>('/import-templates', payload),

  /** 更新模板（仅本人非预设模板） */
  update: (id: number, payload: ImportTemplatePayload) =>
    apiPut<ImportTemplateVO>(`/import-templates/${id}`, payload),

  /** 删除模板（预设不可删） */
  remove: (id: number) => apiDelete(`/import-templates/${id}`),

  /** 启用/停用切换 */
  toggle: (id: number) => apiPost(`/import-templates/${id}/toggle`),

  /** 设为默认模板（同类型唯一） */
  setDefault: (id: number) => apiPost(`/import-templates/${id}/default`),
}
