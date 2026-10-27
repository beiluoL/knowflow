import request from '@/api/request'

/** Fetch locally hosted uploads with the same JWT client as document reads. Never attach it to remote URLs. */
export async function fetchDocumentAsset(url: string): Promise<Blob> {
  if (!url.startsWith('/uploads/') || /[\\%?#]/.test(url) || url.split('/').some(part => part === '.' || part === '..')) {
    throw new Error('文件地址无效')
  }
  return await request.get(url, { baseURL: '', responseType: 'blob' }) as unknown as Blob
}
