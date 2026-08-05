// Obsidian 目录一键导入（四模块）请求层。
import { apiGet, apiPost } from './request'
import type { PathImportScanVO, CategoryVO } from './types'

// 复用知识库导入的 SSE 进度回调类型与执行器（事件格式完全一致）
import { runSseImport, type ImportStreamCallbacks } from './knowledgeImport'

/** 四模块标识：知识库 / 学习路径 / 闪卡 / 题库 */
export type ObsidianModule = 'knowledge' | 'path' | 'flashcard' | 'quiz'

/** 扫描参数：目录路径或指定文件列表二选一 */
export interface ObsidianImportScanParams {
  path?: string
  relativeTo?: string
  /** 文件选择导入模式：显式指定的文件绝对路径列表 */
  filePaths?: string[]
}

/** 一键导入请求参数（对应后端 ObsidianImportDTO） */
export interface ObsidianImportParams extends ObsidianImportScanParams {
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

  /** 扫描本地路径或指定文件，预览待导入的 Markdown 文件结构 */
  scan: (params: ObsidianImportScanParams) =>
    apiGet<PathImportScanVO>('/obsidian/import/scan', params),

  /** 一键导入并生成所选模块（同步，无进度） */
  generate: (params: ObsidianImportParams) =>
    apiPost<ObsidianImportResult>('/obsidian/import/generate', params),

  /**
   * 流式导入（SSE 进度推送）。复用知识库导入的 SSE 执行器，事件类型完全一致：
   * start / fileStart / fileDone / complete / cancel / error。
   *
   * @param params    导入参数（path 或 filePaths + 模块 + 选项 + 模板）
   * @param callbacks 进度回调
   * @returns 取消函数
   */
  importStream: (
    params: ObsidianImportParams,
    callbacks: ImportStreamCallbacks,
  ): { cancel: () => Promise<void> } =>
    runSseImport(
      '/api/obsidian/import/stream',
      JSON.stringify(params),
      callbacks,
      { 'Content-Type': 'application/json' },
    ),
}
