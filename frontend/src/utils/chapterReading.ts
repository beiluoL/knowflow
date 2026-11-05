import md, { renderMarkdown } from './markdown'
import { normalizeNewlines } from './string'
export interface ChapterHeading { id: string; text: string; sub: boolean; displayText?: string; prefix?: string }
/** 标题与正文共用解析器，围栏和转义语法不生成虚假锚点。 */
export function chapterReading(content: string, options: {numberHeadings?: boolean;inlineSourceImages?:Record<string,string>} = {}) {
  // 路由提供唯一页面标题，此处无需重复导入文档标题。
  let source = normalizeNewlines(content).replace(/^\s*# [^\n]*\n+/, '')
  // 仅提取编写好的 Seed 标题协议；普通文章
  // 完整保留学习目标章节和导言。
  const header = /^## 学习目标\n((?:- [^\n]+\n)+)\s*难度：[1-3]；预计 \d+ 分钟；面试重要度：([SABC])\s*\n\n([^\n]+)\n*/.exec(source)
  const objectives = header ? header[1].trim().split('\n').map(line=>line.slice(2)) : []
  const importance = header?.[2] ?? null
  const summary = header?.[3] ?? ''
  if (header) source = source.slice(header[0].length)
  const tokens = md.parse(source, {})
  const outline: ChapterHeading[] = []
  let unit = 0, point = 0
  const used = new Map<string, number>()
  tokens.forEach((token, i) => {
    if (token.type !== 'heading_open' || !['h2', 'h3'].includes(token.tag)) return
    const inline = tokens[i + 1]
    const text = (inline?.children ?? []).map(t => t.type === 'image' ? t.content : ['text', 'code_inline'].includes(t.type) ? t.content : '').join('')
    const sub = token.tag === 'h3'
    if (!sub) { unit++; point = 0 } else { point++ }
    const numbered = /^\d+(?:\.\d+)*[.)、]?\s+/.test(text)
    const prefix = options.numberHeadings && !numbered ? `${sub ? `${unit || 1}.${point}` : `${String(unit).padStart(2, '0')}.`} ` : ''
    const slug = text.replace(/^\d+(?:\.\d+)*[.)、]?\s+/, '').normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '') || 'section'
    const occurrence = (used.get(slug) ?? 0) + 1
    used.set(slug, occurrence)
    const id = options.numberHeadings ? `heading-${slug}${occurrence > 1 ? `-${occurrence}` : ''}` : `heading-${outline.length + 1}`
    outline.push({ id, text, sub, ...(options.numberHeadings ? {prefix, displayText: prefix + text} : {}) })
  })
  return { outline, html: resolveSourceImages(renderMarkdown(source, outline),options.inlineSourceImages), objectives, importance, summary }
}

/** 仅处理图片展示，原始 Markdown、围栏代码及阅读指纹保持不变。 */
function resolveSourceImages(html:string,aliases:Record<string,string> = {}) {
  return html.replace(/<img\b[^>]*>/g,tag=>tag.replace(/\bsrc="([^"]*)"/, (attribute,url:string)=>{
    const decodedUrl=url.replaceAll('&amp;','&')
    let target=aliases[decodedUrl]
    if(!target){try{target=aliases[decodeURIComponent(decodedUrl)]}catch{/* A malformed URL remains an unresolved source image. */}}
    return target&&/^data:image\/(?:png|jpeg|gif);base64,[A-Za-z0-9+/=]+$/.test(target)?`src="${target}"`:attribute
  }))
}
