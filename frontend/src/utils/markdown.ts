/**
 * 通用 Markdown 渲染工具
 * 支持：标题、段落、有序/无序列表、引用、代码块（含复制按钮）、表格、链接、图片、分隔线
 * 兼容：纯 Markdown、含 HTML 富文本的内容（HTML 片段经 DOMPurify 清洗后渲染）
 * 安全：对非 HTML 内容进行 XSS 防护（escapeHtml + sanitizeUrl）；对 HTML 富文本用 DOMPurify 白名单清洗，杜绝存储型 XSS
 */
import DOMPurify from 'dompurify'

/** 将各种转义字符（\n、\t、\"、\\ 等）和换行符统一解析为真实字符，防止格式错乱 */
export const normalizeEscapes = (s: string): string => {
  if (!s) return ''
  return s
    // 先把真实的 \r\n 转成 \n
    .replace(/\r\n/g, '\n')
    // 把剩余的 \r 转成 \n
    .replace(/\r/g, '\n')
    // 把字面量的 \\n 转成真实换行（处理被双重转义的情况）
    .replace(/\\n/g, '\n')
    // 把字面量的 \\t 转成真实制表符
    .replace(/\\t/g, '\t')
    // 把字面量的 \\r 转成真实换行
    .replace(/\\r/g, '\n')
    // 把字面量的 \\" 转成真实双引号
    .replace(/\\"/g, '"')
    // 把字面量的 \\' 转成真实单引号
    .replace(/\\'/g, "'")
    // 把孤立的 \\ 转成单个 \ (注意避免影响已经正确的 \n \t 等)
    .replace(/\\\\/g, '\\')
}

/** HTML 转义，防止 XSS（含引号转义，避免属性上下文注入） */
export const escapeHtml = (s: string): string =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')

/**
 * 链接 URL 协议白名单：仅允许 http/https 与相对路径/锚点，
 * 拒绝 javascript:/data:/vbscript: 及含引号或空白的危险字符
 */
export const sanitizeUrl = (url: string): string | null => {
  const trimmed = url.trim()
  if (/["'<>\\\s]/.test(trimmed)) {
    return null
  }
  if (trimmed.startsWith('/') || trimmed.startsWith('#') || trimmed.startsWith('./') || trimmed.startsWith('../')) {
    return trimmed
  }
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed
  }
  return null
}

/**
 * 清洗用户提供的 HTML 富文本：仅保留安全的标签/属性，
 * 剥离 <script>、事件处理器（onerror/onclick…）、javascript: 协议等，杜绝存储型 XSS。
 */
DOMPurify.addHook('afterSanitizeAttributes', (node: Element) => {
  // 强制所有链接新窗口打开并加 rel，防止 reverse tabnabbing
  if (node.tagName === 'A' && node.getAttribute('href')) {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

const sanitizeHtmlContent = (s: string): string =>
  DOMPurify.sanitize(s, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel'],
  })

/** 将标题文本转为可用于锚点的 slug（保留中英文） */
export const slugify = (text: string): string =>
  text
    .trim()
    .toLowerCase()
    .replace(/[^\w一-龥]+/g, '-')
    .replace(/^-+|-+$/g, '')

/** 行内格式化：加粗、行内代码、链接、裸链接。**关键：保留空格和换行** */
const inline = (s: string): string =>
  escapeHtml(s)
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/~~(.*?)~~/g, '<del>$1</del>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, text, url) => {
      const safe = sanitizeUrl(url)
      return safe ? `<a href="${safe}" target="_blank" rel="noopener noreferrer">${text}</a>` : _
    })
    .replace(/(?<!href=")(https?:\/\/[^\s<"]+)/g, (url) => {
      const safe = sanitizeUrl(url)
      return safe ? `<a href="${safe}" target="_blank" rel="noopener noreferrer">${url}</a>` : url
    })
    // 保留空格：把普通空格替换为 &nbsp;，防止 HTML 折叠
    .replace(/ /g, '&nbsp;')

/** 渲染代码块（带复制按钮的深色主题） */
const renderCodeBlock = (lang: string, code: string): string => {
  const language = lang || 'text'
  const encoded = encodeURIComponent(code)
  return `<div class="code-block-wrapper rounded-lg overflow-hidden my-4" style="background:#1E1E2E">
    <div class="flex items-center justify-between px-4 py-2">
      <span class="text-[12px] font-medium" style="color:#89B4FA">${language}</span>
      <button type="button" class="code-copy-btn inline-flex items-center gap-1 rounded px-2 py-1 text-[12px] transition-colors hover:bg-white/10" style="color:#A6ADC8" data-code="${encoded}" aria-label="复制代码">
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
        <span class="copy-label">复制</span>
      </button>
    </div>
    <pre class="overflow-x-auto px-4 pb-4 text-[13px] leading-relaxed no-scrollbar" style="color:#CDD6F4;font-family:'Menlo','Consolas','Monaco',monospace"><code>${escapeHtml(code)}</code></pre>
  </div>`
}

/** 判断是否为表格行 */
const isTableLine = (line: string): boolean => /^\|.*\|$/.test(line.trim())

/** 渲染表格 */
const renderTable = (rows: string[]): string => {
  if (rows.length < 2) return ''
  const headerCells = rows[0].trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim())
  const bodyRows = rows.slice(2).map((r) =>
    r.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim()),
  )
  let html = '<table><thead><tr>'
  headerCells.forEach((c) => {
    html += `<th>${inline(c)}</th>`
  })
  html += '</tr></thead><tbody>'
  bodyRows.forEach((row) => {
    html += '<tr>'
    row.forEach((c) => {
      html += `<td>${inline(c)}</td>`
    })
    html += '</tr>'
  })
  html += '</tbody></table>'
  return html
}

/**
 * 检测内容是否包含 HTML 富文本（而非纯 Markdown）。
 * 若以 < 开头并包含闭合标签，则视为 HTML 富文本。
 */
const containsHtml = (s: string): boolean => {
  // 去除 Markdown 代码块内的内容后再判断，避免代码块里的 < 被误判
  const stripped = s.replace(/```[\s\S]*?```/g, '')
  return /<[a-zA-Z][^>]*>[\s\S]*?<\/[a-zA-Z][^>]*>/.test(stripped) || /^\s*<(p|div|h[1-6]|ul|ol|table|blockquote|img|hr)\b/i.test(stripped)
}

/**
 * 完整 Markdown 渲染器：支持标题/段落/列表/引用/代码块/表格/分隔线
 * 若内容为 HTML 富文本，则原样返回（仅做基本的链接增强）。
 */
export const renderMarkdown = (md: string): string => {
  if (!md) return ''

  // 1. 先完整解析所有转义字符（\n, \t, \\ 等）
  const text = normalizeEscapes(md)

  // 富文本 HTML：经 DOMPurify 白名单清洗后返回（防止存储型 XSS）
  if (containsHtml(text)) {
    return sanitizeHtmlContent(text)
  }

  const lines = text.split('\n')
  let html = ''
  let inCode = false
  let codeBuf: string[] = []
  let codeLang = ''
  let paragraphBuf: string[] = []
  let inBlockquote = false
  let blockquoteBuf: string[] = []
  let tableBuf: string[] = []
  let inTable = false
  let listBuf: string[] = []
  let listType: 'ul' | 'ol' | null = null

  const flushParagraph = () => {
    if (paragraphBuf.length > 0) {
      // 使用 \n 而不是空格连接，保留原始换行
      html += `<p>${inline(paragraphBuf.join('\n'))}</p>`
      paragraphBuf = []
    }
  }

  const flushBlockquote = () => {
    if (inBlockquote && blockquoteBuf.length > 0) {
      html += `<blockquote><p>${inline(blockquoteBuf.join('\n'))}</p></blockquote>`
      blockquoteBuf = []
      inBlockquote = false
    }
  }

  const flushList = () => {
    if (listType && listBuf.length > 0) {
      html += `<${listType}>${listBuf.map((item) => `<li>${inline(item)}</li>`).join('')}</${listType}>`
      listBuf = []
      listType = null
    }
  }

  const flushTable = () => {
    if (inTable && tableBuf.length >= 2) {
      html += renderTable(tableBuf)
      tableBuf = []
      inTable = false
    }
  }

  const flushAll = () => {
    flushParagraph()
    flushBlockquote()
    flushList()
    flushTable()
  }

  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i]
    const line = raw.trimEnd()

    // 代码块围栏
    const fence = /^```(\w*)/.exec(line)
    if (fence) {
      flushAll()
      if (!inCode) {
        inCode = true
        codeBuf = []
        codeLang = fence[1] || ''
      } else {
        html += renderCodeBlock(codeLang, codeBuf.join('\n'))
        inCode = false
      }
      continue
    }
    if (inCode) {
      codeBuf.push(raw)
      continue
    }

    // 分隔线
    if (/^---+\s*$/.test(line) || /^\*\*\*+\s*$/.test(line)) {
      flushAll()
      html += '<hr />'
      continue
    }

    // 图片
    const img = /!\[([^\]]*)\]\(([^)]+)\)/.exec(line.trim())
    if (img && line.trim() === img[0]) {
      flushAll()
      const alt = img[1]
      const url = sanitizeUrl(img[2])
      if (url) html += `<img alt="${alt}" src="${url}" style="max-width:100%;border-radius:8px;margin:8px 0" />`
      continue
    }

    // 表格
    if (isTableLine(line)) {
      flushParagraph()
      flushBlockquote()
      flushList()
      if (!inTable) {
        inTable = true
        tableBuf = []
      }
      tableBuf.push(line)
      continue
    } else if (inTable) {
      flushTable()
    }

    // 引用
    if (/^>\s?/.test(line)) {
      flushParagraph()
      flushList()
      inBlockquote = true
      blockquoteBuf.push(line.replace(/^>\s?/, ''))
      continue
    } else if (inBlockquote) {
      flushBlockquote()
    }

    // 无序列表
    const ulMatch = /^[-*]\s+(.*)$/.exec(line)
    const olMatch = /^\d+\.\s+(.*)$/.exec(line)
    if (ulMatch) {
      flushParagraph()
      flushBlockquote()
      if (listType !== 'ul') {
        flushList()
        listType = 'ul'
      }
      listBuf.push(ulMatch[1])
      continue
    }
    if (olMatch) {
      flushParagraph()
      flushBlockquote()
      if (listType !== 'ol') {
        flushList()
        listType = 'ol'
      }
      listBuf.push(olMatch[1])
      continue
    } else if (listType) {
      flushList()
    }

    // 标题
    const h = /^(#{1,3})\s+(.*)$/.exec(line)
    if (h) {
      flushAll()
      const hText = h[2].replace(/[*`]/g, '').trim()
      const id = slugify(hText) || `h-${i}`
      html += `<h${h[1].length} id="${id}">${inline(hText)}</h${h[1].length}>`
      continue
    }

    // 空行
    if (line.trim() === '') {
      flushAll()
      continue
    }

    paragraphBuf.push(line.trim())
  }

  // 收尾
  if (inCode) html += renderCodeBlock(codeLang, codeBuf.join('\n'))
  flushAll()

  return html
}
