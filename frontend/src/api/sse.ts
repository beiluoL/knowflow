/**
 * SSE 文本流解析器（与具体业务事件无关）。
 *
 * 处理规范与真实网络状况：
 * - 事件之间以空行分隔，兼容 \n\n、\r\n\r\n 与跨 chunk 的分隔符
 * - 同一事件允许多行 data:，按规范以 \n 拼接
 * - 忽略以 ":" 开头的注释行与 id / retry 字段
 * - 分片到达时不完整的事件保留到下一次 push
 * - 流结束时可 flush 残留块（部分服务端结尾不补空行）
 */

export interface RawSseEvent {
  event: string
  data: string
}

const DATA = 'data'
const EVENT = 'event'

export class SseEventParser {
  private buffer = ''
  /** 上一个分片以 \r 结尾：等待下一个分片判断是否 \r\n，避免误判为事件分隔 */
  private pendingCr = false

  /** 追加一段原始文本，返回本次新解析出的完整事件。 */
  push(chunk: string): RawSseEvent[] {
    let text = chunk
    if (this.pendingCr) {
      text = '\n' + text
      this.pendingCr = false
    }
    let normalized = (this.buffer + text).replace(/\r\n/g, '\n')
    if (normalized.endsWith('\r')) {
      this.pendingCr = true
      normalized = normalized.slice(0, -1)
    }
    const blocks = normalized.split('\n\n')
    this.buffer = blocks.pop() ?? ''
    return blocks.map(parseBlock).filter((e): e is RawSseEvent => e !== null)
  }

  /** 流结束时读取残留内容（没有结尾空行的事件）。 */
  flush(): RawSseEvent[] {
    let rest = this.buffer
    this.buffer = ''
    if (this.pendingCr) {
      rest += '\n'
      this.pendingCr = false
    }
    const evt = parseBlock(rest)
    return evt ? [evt] : []
  }
}

function parseBlock(block: string): RawSseEvent | null {
  if (!block.trim()) return null
  let event = 'message'
  const dataLines: string[] = []
  for (const rawLine of block.split('\n')) {
    const line = rawLine.startsWith(':') ? '' : rawLine
    if (!line) continue
    const idx = line.indexOf(':')
    const field = idx === -1 ? line : line.slice(0, idx)
    let value = idx === -1 ? '' : line.slice(idx + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === EVENT) event = value
    else if (field === DATA) dataLines.push(value)
  }
  if (dataLines.length === 0) return null
  return {event, data: dataLines.join('\n')}
}

/** 解析 data 为 JSON；失败时按纯文本处理（兼容旧版字符串 payload）。 */
export function parseSseData(data: string): Record<string, unknown> {
  const text = data.trim()
  if (!text) return {}
  try {
    const parsed: unknown = JSON.parse(text)
    if (parsed && typeof parsed === 'object') return parsed as Record<string, unknown>
    return {content: String(parsed)}
  } catch {
    return {content: data}
  }
}
