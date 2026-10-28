import { parseLearningActionMessage } from '@/api/learning-actions'

export interface ChatExportReference { title?: string; summary?: string; content?: string }
export interface ChatExportSnapshot {
  title: string
  messages: readonly { role: string; content: string; createTime?: string | number; docReferences?: string | readonly ChatExportReference[] }[]
  exportedAt?: string | number | Date
}
export type ChatExportFormat = 'md' | 'txt'

function visibleContent(content: string): string {
  const action = parseLearningActionMessage(content)
  if (action) return [action.text, ...((action.tasks ?? []).map(task => `- ${task.title}`))].filter(Boolean).join('\n')
  // Never dump internal envelopes when validation rejects a stale or malformed action.
  try {
    const value = JSON.parse(content)
    if (value?.kind === 'KNOWFLOW_LEARNING_ACTION') return typeof value.text === 'string' ? value.text : '学习操作消息'
  } catch { /* Ordinary chat text is not JSON. */ }
  return content
}

function plainText(content: string): string {
  const code: string[] = []
  return content
    .replace(/```[^\n]*\n([\s\S]*?)```/g, (_, text: string) => `\u0000${code.push(text.replace(/\n$/, '')) - 1}\u0000`)
    .replace(/<(script|style)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, '')
    .replace(/<\/?[A-Za-z][^>]*>/g, '')
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/^\s*>\s?/gm, '')
    .replace(/(\*\*|__|~~)([\s\S]*?)\1/g, '$2')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*([^*\n]+)\*/g, '$1')
    .replace(/\u0000(\d+)\u0000/g, (_, index: string) => code[Number(index)] ?? '')
}

export function serializeChatExport(snapshot: ChatExportSnapshot, format: ChatExportFormat): string {
  const timestamp = snapshot.exportedAt instanceof Date ? snapshot.exportedAt.toISOString() : String(snapshot.exportedAt ?? new Date().toISOString())
  const lines = [`${format === 'md' ? '# ' : ''}${snapshot.title || '聊天记录'}`, `导出时间：${timestamp}`, '']
  for (const message of snapshot.messages) {
    const role = ({ user: '用户', assistant: 'AI 助手', system: '系统' } as Record<string, string>)[message.role] ?? message.role
    lines.push(`${format === 'md' ? '## ' : ''}${role}${message.createTime ? ` · ${message.createTime}` : ''}`, '', visibleContent(message.content), '')
    if (message.docReferences) {
      const refs = typeof message.docReferences === 'string' ? message.docReferences : message.docReferences.map(ref => [ref.title, ref.summary ?? ref.content].filter(Boolean).join('：')).join('\n')
      if (refs.trim()) lines.push('引用资料：', refs, '')
    }
  }
  const text = lines.join('\n')
  return format === 'txt' ? plainText(text) : text
}

export function sanitizeChatFilename(title: string, extension: string): string {
  const name = title.normalize('NFKC').replace(/[<>:"/\\|?*\u0000-\u001f\u007f]/g, '').replace(/^\.+|[.\s]+$/g, '').trim().slice(0, 100) || '聊天记录'
  const ext = extension.replace(/[^a-z0-9]/gi, '').toLowerCase() || 'txt'
  return `${/^(con|prn|aux|nul|com[1-9]|lpt[1-9])$/i.test(name) ? `_${name}` : name}.${ext}`
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  try {
    anchor.href = url
    anchor.download = filename
    document.body.appendChild(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    // Let the browser consume the URL before releasing its backing memory.
    setTimeout(() => URL.revokeObjectURL(url), 0)
  }
}

export function downloadChatText(snapshot: ChatExportSnapshot, format: ChatExportFormat): void {
  downloadBlob(new Blob([serializeChatExport(snapshot, format)], { type: `${format === 'md' ? 'text/markdown' : 'text/plain'};charset=utf-8` }), sanitizeChatFilename(snapshot.title, format))
}

/** Fixed-size pages contain the entire snapshot, independent of the chat viewport. */
export async function exportChatPng(snapshot: ChatExportSnapshot): Promise<void> {
  const text = serializeChatExport(snapshot, 'txt')
  if (text.length > 100000) throw new Error('对话超过 PNG 导出上限（100000 字符），请使用 Markdown 或 TXT')
  await document.fonts?.ready
  const canvas = document.createElement('canvas')
  canvas.width = 1000
  const context = canvas.getContext('2d')
  if (!context) throw new Error('浏览器不支持 PNG 导出')
  const styles = getComputedStyle(document.documentElement)
  const fontFamily = styles.getPropertyValue('--kf-font-family').trim() || getComputedStyle(document.body).fontFamily || 'sans-serif'
  const font = `18px ${fontFamily}`
  context.font = font
  const lines: string[] = []
  for (const paragraph of text.replace(/\t/g, '    ').split('\n')) {
    let line = ''
    for (const char of Array.from(paragraph)) {
      if (line && context.measureText(line + char).width > 904) { lines.push(line); line = '' }
      line += char
    }
    lines.push(line)
  }
  const linesPerPage = 44
  const pageCount = Math.ceil(lines.length / linesPerPage)
  if (pageCount > 20) throw new Error('对话超过 PNG 导出上限（20 页），请使用 Markdown 或 TXT')
  // Render all pages before initiating downloads so rendering failures cannot leave partial exports.
  const pages: Blob[] = []
  for (let page = 0; page < pageCount; page++) {
    canvas.height = 1400
    context.fillStyle = styles.getPropertyValue('--kf-background').trim() || 'white'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.fillStyle = styles.getPropertyValue('--kf-foreground').trim() || 'black'
    context.font = font
    context.textBaseline = 'top'
    lines.slice(page * linesPerPage, (page + 1) * linesPerPage).forEach((line, index) => context.fillText(line, 48, 48 + index * 29))
    context.fillText(`${page + 1} / ${pageCount}`, 48, 1350)
    pages.push(await new Promise<Blob>((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('PNG 编码失败')), 'image/png')))
  }
  pages.forEach((blob, page) => downloadBlob(blob, sanitizeChatFilename(`${snapshot.title}${pages.length > 1 ? `-${page + 1}` : ''}`, 'png')))
}
