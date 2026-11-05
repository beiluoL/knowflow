import request from '@/api/request'

/** 本地上传资源复用文档读取的 JWT 客户端，不向远程 URL 附加凭据。 */
export async function fetchDocumentAsset(url: string): Promise<Blob> {
  if (!url.startsWith('/uploads/') || /[\\%?#]/.test(url) || url.split('/').some(part => part === '.' || part === '..')) {
    throw new Error('文件地址无效')
  }
  return await request.get(url, { baseURL: '', responseType: 'blob' }) as unknown as Blob
}
