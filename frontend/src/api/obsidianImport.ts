// Obsidian 目录一键导入（四模块）请求层。
import { apiGet, apiPost } from './request'
import type { PathImportScanVO, CategoryVO } from './types'

/** 四模块标识：知识库 / 学习路径 / 闪卡 / 题库 */
export type ObsidianModule = 'knowledge' | 'path' | 'flashcard' | 'quiz'

/** 一键导入请求参数（对应后端 ObsidianImportDTO） */
export interface ObsidianImportParams {
  path: string
  targetCategoryId?: number
  modules?: ObsidianModule[]
  createSubCategories?: boolean
  autoTags?: boolean
  incremental?: boolean
  maxContentChars?: number
  pathTitle?: string
  level?: string
  /** 闪卡生成所用的规则模板 ID（import_template.id），缺省则用内置默认规则 */
  flashcardTemplateId?: number
  /** 题库生成所用的规则模板 ID（import_template.id），缺省则用内置默认规则 */
  quizTemplateId?: number
}

/** 导入结果（对应后端 ObsidianImportResultVO） */
export interface ObsidianImportResult {
  absolutePath: string
  categoryId: number
  categoryName: string
  docCount: number
  imageCount: number
  learningPathId?: number
  chapterCount: number
  flashcardCount: number
  quizCount: number
  generatedModules: ObsidianModule[]
  message?: string
}

export const obsidianImportApi = {
  /** 查询可导入的知识库列表（用于选目标知识库） */
  listEditableKbs: () => apiGet<CategoryVO[]>('/knowledge/import/editable-kbs'),

  /** 扫描本地路径，预览待导入的 Markdown 文件结构 */
  scan: (path: string, relativeTo?: string) =>
    apiGet<PathImportScanVO>('/obsidian/import/scan', {
      params: relativeTo ? { path, relativeTo } : { path },
    }),

  /** 一键导入并生成所选模块 */
  generate: (params: ObsidianImportParams) =>
    apiPost<ObsidianImportResult>('/obsidian/import/generate', params),
}
