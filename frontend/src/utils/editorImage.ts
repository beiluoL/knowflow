/**
 * 文档编辑器图片处理工具：上传、光标插入、拖拽/粘贴事件处理、HTML→Markdown 转换。
 *
 * 设计要点：
 *  - 复用后端通用上传接口 POST /api/study-groups/upload（字段 file，返回 fileUrl）
 *  - 拖拽/粘贴图片自动上传后在 textarea 光标位置插入 ![alt](url) Markdown 语法
 *  - 粘贴富文本 HTML 时转换为 Markdown（保留图片链接、基础格式）
 *  - 上传中在光标位置插入占位符，上传成功后替换为真实 URL
 */
import { apiPost } from '@/api/request'
import { isFileTooLarge } from '@/utils/uploadLimit'

/** 支持的图片 MIME 类型 */
const IMAGE_MIME_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/svg+xml', 'image/bmp', 'image/x-icon']

/** 上传接口返回结构 */
interface UploadResult {
  fileName: string
  fileUrl: string
  fileSize: number
  fileType: string
}

/** 判断文件是否为图片 */
export function isImageFile(file: File): boolean {
  if (file.type && IMAGE_MIME_TYPES.includes(file.type)) return true
  // 某些系统 SVG 无 MIME，按扩展名兜底
  return /\.(jpe?g|png|gif|webp|svg|bmp|ico)$/i.test(file.name)
}

/**
 * 上传图片到后端，返回可访问的 fileUrl。
 * 复用 /api/study-groups/upload 通用文件接口（需登录态）。
 */
export async function uploadImage(file: File): Promise<string> {
  if (isFileTooLarge(file)) {
    throw new Error('图片大小不能超过 50MB')
  }
  const formData = new FormData()
  formData.append('file', file)
  // 注意：不要手动设置 Content-Type，交由浏览器自动附加 boundary
  const res = await apiPost<UploadResult>('/study-groups/upload', formData)
  return res.fileUrl
}

/**
 * 在 textarea 光标位置插入文本，可选选中插入后的部分文本。
 * 返回新的光标位置 [start, end]。
 */
export function insertTextAtCursor(
  textarea: HTMLTextAreaElement,
  text: string,
  selectLength = 0,
): [number, number] {
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const before = textarea.value.slice(0, start)
  const after = textarea.value.slice(end)
  // 通过 setRangeText 修改内容（会同步触发 v-model 更新）
  textarea.setRangeText(text, start, end, 'end')
  // setRangeText 使用 'end' 策略后光标在插入文本末尾，但 v-model 可能未同步，
  // 需手动派发 input 事件让 Vue 更新 model
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
  // 选中文本（用于让用户立即编辑 alt 文本等）
  if (selectLength > 0) {
    const selStart = start + text.length - selectLength
    const selEnd = start + text.length
    textarea.setSelectionRange(selStart, selEnd)
  } else {
    const cursor = start + text.length
    textarea.setSelectionRange(cursor, cursor)
  }
  textarea.focus()
  return [start, start + text.length]
}

/**
 * 替换 textarea 中指定区间的文本（用于上传完成后替换占位符）。
 */
export function replaceRange(
  textarea: HTMLTextAreaElement,
  start: number,
  end: number,
  replacement: string,
): void {
  textarea.setRangeText(replacement, start, end, 'end')
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
  const cursor = start + replacement.length
  textarea.setSelectionRange(cursor, cursor)
  textarea.focus()
}

/**
 * 处理拖拽事件：提取所有图片文件，逐个上传并插入 Markdown。
 * @param onNotify 用于向 UI 层反馈状态（上传中/成功/失败）
 */
export async function handleImageDrop(
  e: DragEvent,
  textarea: HTMLTextAreaElement,
  onNotify?: (msg: string, type: 'info' | 'success' | 'error') => void,
): Promise<void> {
  const files = Array.from(e.dataTransfer?.files || [])
  const imageFiles = files.filter(isImageFile)
  if (imageFiles.length === 0) return
  // 阻止浏览器默认行为（打开图片）
  e.preventDefault()
  await uploadAndInsertImages(imageFiles, textarea, onNotify)
}

/**
 * 处理粘贴事件：
 *  1. 剪贴板含图片文件 → 上传并插入
 *  2. 剪贴板含 HTML（从网页/Word 复制） → 转 Markdown 插入
 *  3. 纯文本 → 走默认行为
 */
export async function handleImagePaste(
  e: ClipboardEvent,
  textarea: HTMLTextAreaElement,
  onNotify?: (msg: string, type: 'info' | 'success' | 'error') => void,
): Promise<void> {
  const items = Array.from(e.clipboardData?.items || [])
  // 优先处理图片文件
  const imageItems = items.filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
  if (imageItems.length > 0) {
    e.preventDefault()
    const files = imageItems
      .map((item) => item.getAsFile())
      .filter((f): f is File => !!f)
    await uploadAndInsertImages(files, textarea, onNotify)
    return
  }
  // 其次处理富文本 HTML → Markdown
  const htmlItem = items.find((item) => item.kind === 'string' && item.type === 'text/html')
  if (htmlItem) {
    htmlItem.getAsString((html) => {
      const md = htmlToMarkdown(html)
      if (md && md.trim()) {
        e.preventDefault()
        insertTextAtCursor(textarea, md)
      }
      // 否则走默认粘贴（纯文本）
    })
  }
}

/**
 * 批量上传图片并在 textarea 中插入 Markdown 图片语法。
 * 上传中先插入占位符，完成后替换为真实 URL；失败则替换为错误提示。
 */
async function uploadAndInsertImages(
  files: File[],
  textarea: HTMLTextAreaElement,
  onNotify?: (msg: string, type: 'info' | 'success' | 'error') => void,
): Promise<void> {
  for (const file of files) {
    const altText = file.name.replace(/\.[^.]+$/, '') || '图片'
    // 先插入占位符
    const placeholder = `![${altText}](上传中...)`
    const [start, end] = insertTextAtCursor(textarea, placeholder + '\n')
    onNotify?.(`正在上传图片 ${file.name}...`, 'info')
    try {
      const fileUrl = await uploadImage(file)
      replaceRange(textarea, start, end, `![${altText}](${fileUrl})\n`)
      onNotify?.(`图片 ${file.name} 上传成功`, 'success')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '上传失败'
      replaceRange(textarea, start, end, `<!-- 图片 ${altText} 上传失败：${msg} -->\n`)
      onNotify?.(`图片 ${file.name} 上传失败：${msg}`, 'error')
    }
  }
}

/**
 * 简单的 HTML → Markdown 转换，处理从网页/Word 复制的富文本。
 * 支持的标签：p, br, h1-h6, strong/b, em/i, ul/ol/li, a, img, blockquote, code, pre, hr。
 * 复杂表格等不在此处理，保留为纯文本。
 */
export function htmlToMarkdown(html: string): string {
  if (!html) return ''
  // 使用 DOMParser 解析 HTML，避免正则的脆弱性
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const body = doc.body

  // 提取纯文本兜底（如果结构过于复杂）
  const convertNode = (node: Node): string => {
    if (node.nodeType === Node.TEXT_NODE) {
      return node.textContent || ''
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return ''
    const el = node as Element
    const tag = el.tagName.toLowerCase()
    const inner = Array.from(el.childNodes).map(convertNode).join('')

    switch (tag) {
      case 'h1': return `\n# ${inner.trim()}\n`
      case 'h2': return `\n## ${inner.trim()}\n`
      case 'h3': return `\n### ${inner.trim()}\n`
      case 'h4': return `\n#### ${inner.trim()}\n`
      case 'h5': return `\n##### ${inner.trim()}\n`
      case 'h6': return `\n###### ${inner.trim()}\n`
      case 'p': return `\n${inner}\n`
      case 'br': return '\n'
      case 'strong':
      case 'b': return `**${inner}**`
      case 'em':
      case 'i': return `*${inner}*`
      case 's':
      case 'del':
      case 'strike': return `~~${inner}~~`
      case 'code': return `\`${inner}\``
      case 'pre': return `\n\`\`\`\n${el.textContent || ''}\n\`\`\`\n`
      case 'blockquote': return `\n> ${inner.trim().replace(/\n/g, '\n> ')}\n`
      case 'hr': return `\n---\n`
      case 'a': {
        const href = el.getAttribute('href') || ''
        return href ? `[${inner}](${href})` : inner
      }
      case 'img': {
        const src = el.getAttribute('src') || ''
        const alt = el.getAttribute('alt') || '图片'
        return src ? `![${alt}](${src})` : ''
      }
      case 'ul': return `\n${Array.from(el.children).map((li) => `- ${convertNode(li).trim()}`).join('\n')}\n`
      case 'ol': return `\n${Array.from(el.children).map((li, i) => `${i + 1}. ${convertNode(li).trim()}`).join('\n')}\n`
      case 'li': return inner
      case 'div': return `${inner}\n`
      case 'span': return inner
      default: return inner
    }
  }

  let md = Array.from(body.childNodes).map(convertNode).join('')
  // 清理多余空行
  md = md.replace(/\n{3,}/g, '\n\n').trim()
  return md
}

/**
 * 触发隐藏的文件选择器并上传选中的图片。
 * @returns 选中并上传的图片数量
 */
export function pickAndUploadImages(
  textarea: HTMLTextAreaElement,
  onNotify?: (msg: string, type: 'info' | 'success' | 'error') => void,
): Promise<void> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.multiple = true
    input.style.display = 'none'
    input.onchange = async () => {
      const files = Array.from(input.files || []).filter(isImageFile)
      if (files.length > 0) {
        await uploadAndInsertImages(files, textarea, onNotify)
      }
      document.body.removeChild(input)
      resolve()
    }
    input.oncancel = () => {
      if (input.parentNode) document.body.removeChild(input)
      resolve()
    }
    document.body.appendChild(input)
    input.click()
  })
}
