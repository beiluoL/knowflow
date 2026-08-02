// 文件管理请求层：封装上传文件列表、删除、存储统计、目录配置读写接口。
import { apiGet, apiPost, apiPut, apiDelete } from './request'

/** 上传文件信息 */
export interface UploadFileVO {
  fileName: string
  fileUrl: string
  relativePath: string
  fileSize: number
  extension: string
  lastModified: number
  isImage: boolean
}

/** 存储统计 */
export interface UploadStatsVO {
  uploadDir: string
  totalFiles: number
  totalSize: number
  totalSizeReadable: string
  imageCount: number
  otherCount: number
}

/** 上传目录配置 */
export interface UploadConfigVO {
  uploadDir: string
  absoluteDir: string
  exists: boolean
}

/** 文件列表响应 */
export interface FileListResult {
  list: UploadFileVO[]
  total: number
}

const API_PREFIX = '/admin/files'

export const fileApi = {
  /** 文件列表（分页 + 类型筛选 + 关键词） */
  list: (params: {
    page?: number
    pageSize?: number
    type?: 'image' | 'other' | ''
    keyword?: string
  }) => apiGet<FileListResult>(API_PREFIX, params),

  /** 存储统计 */
  stats: () => apiGet<UploadStatsVO>(`${API_PREFIX}/stats`),

  /** 删除文件 */
  remove: (fileUrl: string) =>
    apiDelete<void>(API_PREFIX, { fileUrl }),

  /** 查询上传目录配置 */
  getConfig: () => apiGet<UploadConfigVO>(`${API_PREFIX}/config`),

  /** 修改上传目录配置（需重启后端生效） */
  updateConfig: (uploadDir: string) =>
    apiPut<{ uploadDir: string; absoluteDir: string; message: string }>(
      `${API_PREFIX}/config`,
      { uploadDir },
    ),
}
