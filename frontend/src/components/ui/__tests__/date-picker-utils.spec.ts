import { describe, expect, it } from 'vitest'
import {
  addDays,
  addMonths,
  buildMonthGrid,
  coerceDateString,
  formatLocalDate,
  formatYmd,
  isSameMonth,
  parseDateString,
  todayDateString,
} from '../date-picker-utils'

describe('date-picker-utils', () => {
  it('parses strict calendar dates without UTC shifting', () => {
    expect(parseDateString('2026-10-24')).toEqual({ year: 2026, month: 10, day: 24 })
    expect(parseDateString('2026-02-29')).toBeNull()
    expect(parseDateString('2028-02-29')).toEqual({ year: 2028, month: 2, day: 29 })
    expect(parseDateString('2026-13-01')).toBeNull()
    expect(parseDateString('2026-10-32')).toBeNull()
    expect(parseDateString('')).toBeNull()
  })

  it('formats local dates by local calendar fields', () => {
    expect(formatLocalDate(new Date(2026, 9, 24, 23, 59))).toBe('2026-10-24')
    expect(formatLocalDate(new Date(2026, 0, 1, 0, 0))).toBe('2026-01-01')
    expect(formatYmd({ year: 2026, month: 3, day: 5 })).toBe('2026-03-05')
    expect(todayDateString(new Date(2026, 11, 31, 23, 30))).toBe('2026-12-31')
  })

  it('handles month-end, year-end and leap-day arithmetic', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29')
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28')
    expect(addMonths('2028-01-31', 1)).toBe('2028-02-29')
    expect(addMonths('2026-12-31', 1)).toBe('2027-01-31')
    expect(addMonths('2026-01-31', -1)).toBe('2025-12-31')
  })

  it('builds a stable 6×7 grid starting on the configured weekday', () => {
    const sundayStart = buildMonthGrid(2026, 10, 0)
    expect(sundayStart).toHaveLength(6)
    expect(sundayStart.every((week) => week.length === 7)).toBe(true)
    expect(sundayStart[0][0]).toBe('2026-09-27')
    expect(sundayStart.flat()).toContain('2026-10-24')
    expect(sundayStart.flat()).toContain('2026-11-07')

    const mondayStart = buildMonthGrid(2026, 10, 1)
    expect(mondayStart[0][0]).toBe('2026-09-28')
    expect(mondayStart[0][6]).toBe('2026-10-04')
  })

  it('coerces backend ISO values without timezone conversion', () => {
    expect(coerceDateString('2026-10-24T23:30:00+08:00')).toBe('2026-10-24')
    expect(coerceDateString('2026-10-24 08:00:00')).toBe('2026-10-24')
    expect(coerceDateString('not-a-date')).toBe('')
    expect(coerceDateString('2026-02-30T00:00:00')).toBe('')
  })

  it('detects the current month for grid de-emphasis', () => {
    expect(isSameMonth('2026-10-01', 2026, 10)).toBe(true)
    expect(isSameMonth('2026-09-30', 2026, 10)).toBe(false)
    expect(isSameMonth('invalid', 2026, 10)).toBe(false)
  })
})
