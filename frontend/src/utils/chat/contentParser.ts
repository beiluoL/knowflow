/**
 * 聊天消息内容解析层。
 *
 * 目标：
 *  - 使用 markdown-it 解析，禁止再用正则手写 Markdown；
 *  - 顶层代码围栏拆成独立组件数据（代码块 / JSON / HTML 预览）；
 *  - 完整消息本身是合法 JSON 时交给 JSON Viewer；
 *  - 输出 HTML 统一经 DOMPurify 清洗，链接与图片地址做协议白名单校验；
 *  - 流式未完成时不做 JSON.parse，避免半截内容导致崩溃。
 */
import DOMPurify from 'dompurify'
import md from '@/utils/markdown'

const SAFE_LINK = /^(https?:\/\/|mailto:|#|\/)/i
const SAFE_IMAGE = /^(https?:\/\/|\/|blob:|data:image\/(png|jpe?g|gif|webp|svg\+xml);base64,)/i

/** 链接协议白名单：仅 http(s)、mailto、页内锚点与站内绝对路径。 */
export function isSafeLinkHref(href: string): boolean {
  const value = (href || '').trim()
  if (!value) return false
  return SAFE_LINK.test(value)
}

/** 图片地址白名单：仅 http(s)、站内路径、blob 与图片 base64 data URI。 */
export function isSafeImageSrc(src: string): boolean {
  const value = (src || '').trim()
  if (!value) return false
  return SAFE_IMAGE.test(value)
}

let hookInstalled = false
function ensureSanitizeHook(): void {
  if (hookInstalled) return
  hookInstalled = true
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    const element = node as Element
    const tag = (element.tagName || '').toUpperCase()
    if (tag === 'A') {
      const href = element.getAttribute('href') || ''
      if (href && !isSafeLinkHref(href)) element.removeAttribute('href')
      element.setAttribute('rel', 'noopener noreferrer')
      if (/^https?:\/\//i.test(href)) element.setAttribute('target', '_blank')
    }
    if (tag === 'IMG') {
      const src = element.getAttribute('src') || ''
      if (src && !isSafeImageSrc(src)) element.removeAttribute('src')
      element.setAttribute('loading', 'lazy')
    }
  })
}

/** 清洗聊天 Markdown 生成的 HTML（DOMPurify + 协议白名单） */
export function sanitizeChatHtml(html: string): string {
  if (!html) return ''
  ensureSanitizeHook()
  return DOMPurify.sanitize(html, { ADD_ATTR: ['target', 'rel', 'loading'] })
}

export interface ChatCodeBlockData {
  kind: 'code'
  lang: string
  code: string
}

export interface ChatMarkdownBlock {
  kind: 'markdown'
  html: string
}

export type ChatBlock = ChatMarkdownBlock | ChatCodeBlockData

export interface StandaloneJson {
  value: unknown
  raw: string
}

export interface ParsedChatContent {
  blocks: ChatBlock[]
  /** 仅当整条消息是合法 JSON 对象/数组时非空 */
  json: unknown | null
  jsonRaw: string
}

const EMPTY_RESULT: ParsedChatContent = { blocks: [], json: null, jsonRaw: '' }

/**
 * 整条消息是否为合法 JSON（对象或数组）。
 * 不做「文本中夹带 JSON」的猜测，避免把普通回答误判为 JSON。
 */
export function tryParseStandaloneJson(src: string): StandaloneJson | null {
  const raw = (src || '').trim()
  if (!raw || raw.includes('```')) return null
  if (raw.length > 500_000) return null
  const first = raw[0]
  const last = raw[raw.length - 1]
  const looksObject = first === '{' && last === '}'
  const looksArray = first === '[' && last === ']'
  if (!looksObject && !looksArray) return null
  try {
    const value = JSON.parse(raw)
    if (value && typeof value === 'object') return { value, raw }
  } catch {
    return null
  }
  return null
}

/**
 * 解析聊天消息为可渲染块。
 * @param src 原始 Markdown / 纯文本
 * @param streaming 流式生成中：跳过 JSON 识别，代码围栏未闭合时按代码块渲染
 */
export function parseChatContent(src: string, options: { streaming?: boolean } = {}): ParsedChatContent {
  const source = src || ''
  if (!source.trim()) return EMPTY_RESULT

  if (!options.streaming) {
    const json = tryParseStandaloneJson(source)
    if (json) return { blocks: [], json: json.value, jsonRaw: json.raw }
  }

  let tokens: ReturnType<typeof md.parse>
  try {
    tokens = md.parse(source, {})
  } catch {
    return { blocks: [{ kind: 'markdown', html: sanitizeChatHtml(`<p>${escapeHtml(source)}</p>`) }], json: null, jsonRaw: '' }
  }

  const blocks: ChatBlock[] = []
  let buffer: typeof tokens = []

  const flush = () => {
    if (buffer.length === 0) return
    const group = buffer
    buffer = []
    try {
      const html = sanitizeChatHtml(md.renderer.render(group, md.options, {}))
      if (html.trim()) blocks.push({ kind: 'markdown', html })
    } catch {
      const text = group.map((t) => t.content || '').join('\n')
      if (text.trim()) blocks.push({ kind: 'markdown', html: sanitizeChatHtml(`<p>${escapeHtml(text)}</p>`) })
    }
  }

  for (const token of tokens) {
    if (token.level === 0 && token.type === 'fence') {
      flush()
      blocks.push({ kind: 'code', lang: (token.info || '').trim(), code: token.content || '' })
      continue
    }
    buffer.push(token)
  }
  flush()

  return { blocks, json: null, jsonRaw: '' }
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export interface GradingDimension {
  name: string
  score?: number | string
  comment?: string
}

export interface GradingSummary {
  score: number | string
  grade?: string
  gradeDescription?: string
  overallComment?: string
  dimensions: GradingDimension[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * 识别明确的评分 Schema：必须含 score，且含 grade 或 dimensions 之一。
 * 仅做结构校验，不通过字符串包含 "score" 判断。
 */
export function detectGradingJson(value: unknown): GradingSummary | null {
  if (!isRecord(value)) return null
  const rawScore = value.score
  const hasScore = typeof rawScore === 'number' || (typeof rawScore === 'string' && rawScore.trim() !== '')
  if (!hasScore) return null
  const hasGrade = typeof value.grade === 'string' && value.grade.trim() !== ''
  const rawDims = value.dimensions
  const hasDimensions = Array.isArray(rawDims)
  if (!hasGrade && !hasDimensions) return null

  const dimensions: GradingDimension[] = hasDimensions
    ? rawDims.filter(isRecord).map((dim) => ({
        name: String(dim.name ?? dim.dimension ?? dim.label ?? '维度'),
        score: typeof dim.score === 'number' || typeof dim.score === 'string' ? dim.score : undefined,
        comment: typeof dim.comment === 'string' ? dim.comment : typeof dim.description === 'string' ? dim.description : undefined,
      }))
    : []

  return {
    score: rawScore as number | string,
    grade: typeof value.grade === 'string' ? value.grade : undefined,
    gradeDescription: typeof value.gradeDescription === 'string' ? value.gradeDescription : undefined,
    overallComment: typeof value.overallComment === 'string' ? value.overallComment : undefined,
    dimensions,
  }
}
