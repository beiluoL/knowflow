/** 本地日期时间合同：YYYY-MM-DDTHH:mm / YYYY-MM-DDTHH:mm:ss，不附加时区或 Z。 */
import { parseDateString } from './date-picker-utils'
import {
  formatTimeString,
  hasMeaningfulSeconds,
  parseTimeString,
  type TimeParts,
  type TimePrecision,
} from './time-picker-utils'

export interface DateTimeParts extends TimeParts {
  date: string
}

const DATE_TIME = /^(\d{4}-\d{2}-\d{2})[T ](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$/

export function parseDateTimeString(value: string | null | undefined): DateTimeParts | null {
  if (!value) return null
  const match = DATE_TIME.exec(value.trim())
  if (!match) return null
  const date = match[1]
  if (!parseDateString(date)) return null
  const time = parseTimeString(
    `${match[2].padStart(2, '0')}:${match[3].padStart(2, '0')}${
      match[4] === undefined ? '' : `:${match[4].padStart(2, '0')}`
    }`,
  )
  if (!time) return null
  return { date, ...time }
}

export function formatDateTimeValue(parts: DateTimeParts, precision: TimePrecision): string {
  return `${parts.date}T${formatTimeString(parts, precision)}`
}

/** 已有非零秒时自动升级为秒精度，避免静默截断旧数据。 */
export function resolveDateTimePrecision(
  configured: TimePrecision,
  parts: DateTimeParts | null,
): TimePrecision {
  return configured === 'second' || (parts ? hasMeaningfulSeconds(parts) : false)
    ? 'second'
    : 'minute'
}
