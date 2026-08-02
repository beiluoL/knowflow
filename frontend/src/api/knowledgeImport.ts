// 知识库目录批量导入请求层：封装 Obsidian/本地目录批量导入接口。
import { apiPost } from './request'
import type { KnowledgeImportResultVO } from './types'

export const knowledgeImportApi = {
  /**
   * 批量导入目录到知识库。
   * 前端通过 <input webkitdirectory> 选择目录，将文件（含相对路径）上传。
   *
   * @param formData 包含 files（文件数组）和导入选项的 FormData
   * @returns 导入结果（成功/跳过/失败计数 + 逐条明细日志）
   */
  importDirectory: (formData: FormData) =>
    apiPost<KnowledgeImportResultVO>('/knowledge/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000, // 5 分钟超时（大目录导入）
    }),
}
