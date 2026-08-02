// 本地阅读器 API：通过后端代理读取本地文件系统，支持路径输入加载。
import { apiGet, apiPost } from './request'

/** 目录树节点 */
export interface TreeNode {
  name: string
  path: string
  type: 'dir' | 'doc'
  children: TreeNode[]
}

/** 扁平文档项 */
export interface FlatDoc {
  path: string
  name: string
}

/** 目录扫描结果 */
export interface ScanResult {
  absolutePath: string
  rootName: string
  docCount: number
  tree: TreeNode[]
  docs: FlatDoc[]
}

/** 路径解析结果 */
export interface ResolveResult {
  absolutePath: string
}

/** 文档内容 */
export interface DocContent {
  content: string
}

export const localReaderApi = {
  /** 路径解析：校验路径有效性并返回绝对路径 */
  resolve(path: string, relativeTo?: string): Promise<ResolveResult> {
    return apiPost<ResolveResult>('/local-reader/resolve', { path, relativeTo })
  },

  /** 扫描目录：返回目录树与扁平文档列表 */
  scan(absolutePath: string): Promise<ScanResult> {
    return apiGet<ScanResult>('/local-reader/scan', { path: absolutePath })
  },

  /** 读取 Markdown 文档内容 */
  getContent(rootAbsolutePath: string, path: string): Promise<DocContent> {
    return apiGet<DocContent>('/local-reader/content', {
      rootAbsolutePath,
      path,
    })
  },

  /**
   * 构建图片 URL（直接返回可用的 URL，由 img 标签加载）。
   * 图片通过后端 /api/local-reader/image 接口读取。
   */
  imageUrl(rootAbsolutePath: string, imagePath: string, docPath?: string): string {
    const params = new URLSearchParams({
      rootAbsolutePath,
      path: imagePath,
    })
    if (docPath) {
      params.append('docPath', docPath)
    }
    const token = localStorage.getItem('token') || ''
    return `/api/local-reader/image?${params.toString()}&token=${encodeURIComponent(token)}`
  },
}
