// 日期选择器工具：只处理本地日历日（YYYY-MM-DD），禁止用 toISOString 产生 UTC 偏移。

export interface Ymd {
  year: number
  month: number
  day: number
}

const DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/

export function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

/** 严格解析 YYYY-MM-DD；非法月份、非法日、2026-02-29 都会返回 null。 */
export function parseDateString(value: string | null | undefined): Ymd | null {
  if (!value) return null
  const matched = DATE_RE.exec(value.trim())
  if (!matched) return null
  const year = Number(matched[1])
  const month = Number(matched[2])
  const day = Number(matched[3])
  if (month < 1 || month > 12 || day < 1 || day > 31) return null
  const probe = new Date(year, month - 1, day)
  if (probe.getFullYear() !== year || probe.getMonth() !== month - 1 || probe.getDate() !== day) {
    return null
  }
  return { year, month, day }
}

export function formatYmd(parts: Ymd): string {
  return `${parts.year}-${pad2(parts.month)}-${pad2(parts.day)}`
}

/** 兼容后端完整 ISO 字符串，仅取前 10 位日期，不做时区换算。 */
export function coerceDateString(value: unknown): string {
  if (typeof value !== 'string') return ''
  const matched = /^(\d{4}-\d{2}-\d{2})/.exec(value.trim())
  return matched && parseDateString(matched[1]) ? matched[1] : ''
}

export function toLocalDate(value: string): Date | null {
  const parts = parseDateString(value)
  if (!parts) return null
  return new Date(parts.year, parts.month - 1, parts.day)
}

/** 使用本地年月日格式化，避免 toISOString 在 UTC+8 产生前一日。 */
export function formatLocalDate(date: Date): string {
  return formatYmd({
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate(),
  })
}

export function todayDateString(now: Date = new Date()): string {
  return formatLocalDate(now)
}

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

export function addDays(value: string, amount: number): string | null {
  const parts = parseDateString(value)
  if (!parts) return null
  return formatLocalDate(new Date(parts.year, parts.month - 1, parts.day + amount))
}

/** 月末自动收敛：2026-01-31 + 1 月 → 2026-02-28。 */
export function addMonths(value: string, amount: number): string | null {
  const parts = parseDateString(value)
  if (!parts) return null
  const absolute = parts.year * 12 + (parts.month - 1) + amount
  const year = Math.floor(absolute / 12)
  const month = (absolute % 12) + 1
  const day = Math.min(parts.day, daysInMonth(year, month))
  return formatYmd({ year, month, day })
}

/** 周日=0 … 周六=6，与原生 Date#getDay 一致。 */
export function weekdayOf(value: string): number | null {
  const date = toLocalDate(value)
  return date ? date.getDay() : null
}

/** 固定 6×7 网格，跨月补位日期也会真实返回，便于弱化展示与点选。 */
export function buildMonthGrid(year: number, month: number, weekStartsOn: number): string[][] {
  const first = new Date(year, month - 1, 1)
  const offset = (first.getDay() - weekStartsOn + 7) % 7
  const weeks: string[][] = []
  for (let row = 0; row < 6; row++) {
    const days: string[] = []
    for (let col = 0; col < 7; col++) {
      days.push(formatLocalDate(new Date(year, month - 1, 1 - offset + row * 7 + col)))
    }
    weeks.push(days)
  }
  return weeks
}

/** 同年同月判断，用于弱化非当前月日期。 */
export function isSameMonth(value: string, year: number, month: number): boolean {
  const parts = parseDateString(value)
  return !!parts && parts.year === year && parts.month === month
}
