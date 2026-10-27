/**
 * 日期时间显示格式与后端序列化合同的唯一边界层。
 * 用户可见文本使用 `YYYY-MM-DD HH:mm`，数据合同继续使用 `YYYY-MM-DDTHH:mm`。
 * 这里只做纯字符串转换，不做时区换算、不附加 Z、不扩展或截断已有秒。
 */
import { coerceDateString, parseDateString, pad2, type Ymd } from './date-picker-utils'
import {
  formatTimeString,
  parseTimeString,
  type TimeParts,
  type TimePrecision,
} from './time-picker-utils'
import {
  formatDateTimeValue,
  parseDateTimeString,
  resolveDateTimePrecision,
  type DateTimeParts,
} from './datetime-picker-utils'

export type { DateTimeParts, TimeParts, TimePrecision, Ymd }
export { resolveDateTimePrecision }

export interface YearMonthParts {
  year: number
  month: number
}

const YEAR_RE = /^(\d{4})$/
const MONTH_RE = /^(\d{4})-(\d{2})$/

/** 纯年份 `YYYY`；非法返回 null。 */
export function parseLocalYear(value: string | null | undefined): number | null {
  if (!value) return null
  const matched = YEAR_RE.exec(value.trim())
  if (!matched) return null
  return Number(matched[1])
}

/** 年月 `YYYY-MM`；非法月份返回 null。 */
export function parseLocalMonth(value: string | null | undefined): YearMonthParts | null {
  if (!value) return null
  const matched = MONTH_RE.exec(value.trim())
  if (!matched) return null
  const year = Number(matched[1])
  const month = Number(matched[2])
  if (month < 1 || month > 12) return null
  return { year, month }
}

/** 纯日期 `YYYY-MM-DD`，同时接受带时间部分的后端字符串，只取日期不做时区换算。 */
export function parseLocalDate(value: string | null | undefined): Ymd | null {
  const date = coerceDateString(value)
  return date ? parseDateString(date) : null
}

/** 纯时间 `HH:mm` / `HH:mm:ss`；非法时分秒返回 null。 */
export function parseLocalTime(value: string | null | undefined): TimeParts | null {
  return parseTimeString(value)
}

/** 本地日期时间 `YYYY-MM-DD[T ]HH:mm(:ss)`；拒绝 Z 与显式时区偏移。 */
export function parseLocalDateTime(
  value: string | null | undefined,
): DateTimeParts | null {
  return parseDateTimeString(value)
}

export function formatYearForDisplay(year: number): string {
  return `${year}年`
}

export function formatMonthForDisplay(parts: YearMonthParts): string {
  return `${parts.year}年${pad2(parts.month)}月`
}

/** 用户可见日期文本；无法解析时返回空字符串，绝不回显原始字符串。 */
export function formatDateForDisplay(value: string | null | undefined): string {
  const parts = parseLocalDate(value)
  return parts ? `${parts.year}-${pad2(parts.month)}-${pad2(parts.day)}` : ''
}

export function formatTimeForDisplay(
  value: string | null | undefined,
  precision: TimePrecision = 'minute',
): string {
  const parts = parseLocalTime(value)
  if (!parts) return ''
  const effective = precision === 'second' || parts.second !== 0 ? 'second' : 'minute'
  return formatTimeString(parts, effective)
}

/** 由已解析的日期时间片段生成用户可见文本（空格分隔，无 T）。 */
export function formatDateTimePartsForDisplay(
  parts: DateTimeParts,
  precision: TimePrecision,
): string {
  return `${parts.date} ${formatTimeString(parts, precision)}`
}

/**
 * 合同字符串 → 用户可见文本。传 `precision` 时按配置输出；已有非零秒会自动升级显示秒。
 * 非法输入返回空字符串，避免把原始 API 字符串直接暴露给用户。
 */
export function formatDateTimeForDisplay(
  value: string | null | undefined,
  precision: TimePrecision = 'minute',
): string {
  const parts = parseLocalDateTime(value)
  if (!parts) return ''
  return formatDateTimePartsForDisplay(
    parts,
    resolveDateTimePrecision(precision, parts),
  )
}

/** 用户可见文本 → 后端合同字符串；保留配置精度与已有秒。 */
export function serializeLocalDateTime(
  parts: DateTimeParts,
  precision: TimePrecision,
): string {
  return formatDateTimeValue(parts, precision)
}

export type DateTimeValidationError = 'empty' | 'format' | 'range'

export interface DateTimeValidationResult {
  valid: boolean
  parts: DateTimeParts | null
  error?: DateTimeValidationError
}

/** 校验本地日期时间：真实日期、合法时分秒、可选 min/max 日期边界。 */
export function validateDateTime(
  value: string | null | undefined,
  options: { min?: string; max?: string; required?: boolean } = {},
): DateTimeValidationResult {
  const raw = value?.trim() ?? ''
  if (!raw) {
    return options.required
      ? { valid: false, parts: null, error: 'empty' }
      : { valid: true, parts: null }
  }
  const parts = parseLocalDateTime(raw)
  if (!parts) return { valid: false, parts: null, error: 'format' }
  const min = coerceDateString(options.min)
  const max = coerceDateString(options.max)
  if ((min && parts.date < min) || (max && parts.date > max)) {
    return { valid: false, parts, error: 'range' }
  }
  return { valid: true, parts }
}
