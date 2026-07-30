// 上传文件大小限制（需与后端 spring.servlet.multipart.max-file-size 保持一致，当前 50MB）。
// 用于前端在发起请求前做即时拦截，给用户秒级反馈，避免等到请求被服务端拒绝才报错。
export const MAX_UPLOAD_FILE_SIZE = 50 * 1024 * 1024

/** 文件是否超过大小限制 */
export function isFileTooLarge(file: File): boolean {
  return file.size > MAX_UPLOAD_FILE_SIZE
}

/** 人类可读的大小描述，如 1.5MB */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}
