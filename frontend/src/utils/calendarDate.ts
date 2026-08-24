// 日历日期工具：纯原生 Date 实现，周一开始（符合国内习惯与滴答清单体验）。

const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']

/** 周一为一周起点：返回 0(周一) ~ 6(周日)。 */
export function weekdayMondayBased(d: Date): number {
  return (d.getDay() + 6) % 7
}

export function weekdayLabels(): string[] {
  return WEEKDAY_LABELS
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

export function startOfDay(d: Date): Date {
  const r = new Date(d)
  r.setHours(0, 0, 0, 0)
  return r
}
export function endOfDay(d: Date): Date {
  const r = new Date(d)
  r.setHours(23, 59, 59, 999)
  return r
}
export function startOfWeek(d: Date): Date {
  const r = startOfDay(d)
  r.setDate(r.getDate() - weekdayMondayBased(r))
  return r
}
export function endOfWeek(d: Date): Date {
  const r = startOfWeek(d)
  r.setDate(r.getDate() + 6)
  return endOfDay(r)
}
export function startOfMonth(d: Date): Date {
  const r = startOfDay(d)
  r.setDate(1)
  return r
}
export function endOfMonth(d: Date): Date {
  const r = startOfMonth(d)
  r.setMonth(r.getMonth() + 1)
  r.setDate(0)
  return endOfDay(r)
}

export function addDays(d: Date, n: number): Date {
  const r = new Date(d)
  r.setDate(r.getDate() + n)
  return r
}
export function addMonths(d: Date, n: number): Date {
  const r = new Date(d)
  r.setMonth(r.getMonth() + n)
  return r
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

/** yyyy-MM-dd */
export function formatDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
/** yyyy-MM-dd HH:mm */
export function formatDateTime(d: Date): string {
  return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
/** ISO 本地时间 yyyy-MM-dd'T'HH:mm:ss，供后端 @DateTimeFormat(iso=DATE_TIME) 解析。 */
export function toISO(d: Date): string {
  return `${formatDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 由 yyyy-MM-dd 或 yyyy-MM-dd HH:mm 解析为本地 Date。 */
export function parseLocal(s: string): Date | null {
  if (!s) return null
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?/)
  if (!m) return null
  const [, y, mo, d, h, mi] = m
  return new Date(Number(y), Number(mo) - 1, Number(d), Number(h || 0), Number(mi || 0), 0, 0)
}

/** 事件归属日期：定时事件按 start_time，全天事件按 scheduled_date。 */
export function eventDate(e: { startTime?: string | null; scheduledDate?: string | null }): Date | null {
  const t = parseLocal(e.startTime || '')
  if (t) return t
  const d = parseLocal(e.scheduledDate || '')
  return d
}

/** 生成 [start, end] 闭区间内每一天的 Date 数组。 */
export function eachDay(start: Date, end: Date): Date[] {
  const out: Date[] = []
  let cur = startOfDay(start)
  const last = startOfDay(end)
  while (cur <= last) {
    out.push(cur)
    cur = addDays(cur, 1)
  }
  return out
}

/** 月视图标题，如「2026年8月」。 */
export function monthTitle(d: Date): string {
  return `${d.getFullYear()}年${d.getMonth() + 1}月`
}
/** 周视图标题，如「2026年8月17日 - 8月23日」。 */
export function weekTitle(start: Date, end: Date): string {
  if (start.getMonth() === end.getMonth()) {
    return `${start.getFullYear()}年${start.getMonth() + 1}月${start.getDate()}日 - ${end.getDate()}日`
  }
  return `${start.getFullYear()}年${start.getMonth() + 1}月${start.getDate()}日 - ${end.getMonth() + 1}月${end.getDate()}日`
}
/** 日视图标题，如「2026年8月24日 周一」。 */
export function dayTitle(d: Date): string {
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 周${WEEKDAY_LABELS[weekdayMondayBased(d)]}`
}

/** 将 Date 转为 <input type="datetime-local"> 需要的 value（yyyy-MM-ddTHH:mm）。 */
export function toDatetimeLocal(d: Date): string {
  return `${formatDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
/** 将 Date 转为 <input type="date"> 需要的 value（yyyy-MM-dd）。 */
export function toDateInput(d: Date): string {
  return formatDate(d)
}
