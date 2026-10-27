/** 纯时间合同工具：只处理 HH:mm / HH:mm:ss，不附加日期、时区或 Z 后缀。 */
export type TimePrecision = 'minute' | 'second'

export interface TimeParts {
  hour: number
  minute: number
  second: number
}

const STRICT_TIME = /^(\d{2}):(\d{2})(?::(\d{2}))?$/
const LOOSE_TIME = /^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$/

const pad = (value: number): string => String(value).padStart(2, '0')

function inRange(parts: TimeParts): boolean {
  return (
    parts.hour >= 0 &&
    parts.hour <= 23 &&
    parts.minute >= 0 &&
    parts.minute <= 59 &&
    parts.second >= 0 &&
    parts.second <= 59
  )
}

/** 严格解析存储合同；非法值返回 null。 */
export function parseTimeString(value: string | null | undefined): TimeParts | null {
  if (!value) return null
  const match = STRICT_TIME.exec(value.trim())
  if (!match) return null
  const parts: TimeParts = {
    hour: Number(match[1]),
    minute: Number(match[2]),
    second: match[3] === undefined ? 0 : Number(match[3]),
  }
  return inRange(parts) ? parts : null
}

/** 宽松解析手工输入（允许 9:5 / 9:05:3），非法值返回 null。 */
export function parseTimeInput(value: string | null | undefined): TimeParts | null {
  if (!value) return null
  const match = LOOSE_TIME.exec(value.trim())
  if (!match) return null
  const parts: TimeParts = {
    hour: Number(match[1]),
    minute: Number(match[2]),
    second: match[3] === undefined ? 0 : Number(match[3]),
  }
  return inRange(parts) ? parts : null
}

export function formatTimeString(parts: TimeParts, precision: TimePrecision): string {
  const hm = `${pad(parts.hour)}:${pad(parts.minute)}`
  return precision === 'second' ? `${hm}:${pad(parts.second)}` : hm
}

/** 是否携带秒（非零秒视为必须保留的数据）。 */
export function hasMeaningfulSeconds(parts: TimeParts): boolean {
  return parts.second !== 0
}

export function timeToSeconds(parts: TimeParts): number {
  return parts.hour * 3600 + parts.minute * 60 + parts.second
}

export function timeFromSeconds(total: number): TimeParts {
  const safe = Math.max(0, Math.min(total, 86399))
  return {
    hour: Math.floor(safe / 3600),
    minute: Math.floor((safe % 3600) / 60),
    second: safe % 60,
  }
}

/** min/max 为 HH:mm 或 HH:mm:ss；未提供时视为不限制。 */
export function isTimeInRange(
  value: TimeParts,
  min?: string | null,
  max?: string | null,
): boolean {
  const target = timeToSeconds(value)
  const minParts = parseTimeString(min)
  const maxParts = parseTimeString(max)
  if (minParts && target < timeToSeconds(minParts)) return false
  if (maxParts && target > timeToSeconds(maxParts)) return false
  return true
}

export function buildTimeOptions(max: number): number[] {
  return Array.from({ length: max + 1 }, (_, index) => index)
}

export function normalizeTimeInput(
  raw: string,
): { value: string; parts: TimeParts } | { error: 'invalid' | 'range' } {
  const parts = parseTimeInput(raw)
  if (!parts) return { error: 'invalid' }
  return { value: formatTimeString(parts, 'minute'), parts }
}
