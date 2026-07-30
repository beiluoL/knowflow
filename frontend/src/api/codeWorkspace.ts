// 代码工作区请求层（SC1-IDE-02 可重置实验沙箱）：对接后端 /api/code/workspace 文件 CRUD 与重置。
import { apiGet, apiPost, apiDelete } from './request'

export interface CodeWorkspaceFileDTO {
  /** 相对工作区根目录的路径（单层文件名） */
  path: string
  /** 文件名 */
  name: string
  /** 字节大小 */
  size: number
  /** 由扩展名推断的语言 */
  language?: string
  /** 文件文本内容 */
  content: string
}

export interface CodeWorkspaceSavePayload {
  path: string
  content: string
}

export const codeWorkspaceApi = {
  /** 列出我的工作区文件（含内容） */
  list: () => apiGet<CodeWorkspaceFileDTO[]>('/code/workspace/files'),
  /** 新建或覆盖写入工作区文件 */
  save: (payload: CodeWorkspaceSavePayload) => apiPost<CodeWorkspaceFileDTO>('/code/workspace/files', payload),
  /** 删除工作区文件 */
  remove: (path: string) =>
    apiDelete<void>(`/code/workspace/files?path=${encodeURIComponent(path)}`),
  /** 重置工作区（清空全部文件） */
  reset: () => apiPost<void>('/code/workspace/reset'),
}
