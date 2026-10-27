// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import {
  formatDateForDisplay,
  formatDateTimeForDisplay,
  formatDateTimePartsForDisplay,
  formatMonthForDisplay,
  formatTimeForDisplay,
  formatYearForDisplay,
  parseLocalDate,
  parseLocalDateTime,
  parseLocalMonth,
  parseLocalTime,
  parseLocalYear,
  serializeLocalDateTime,
  validateDateTime,
} from '../date-time-format'

describe('date-time-format', () => {
  it('formats year and month without inventing a day', () => {
    expect(parseLocalYear('2026')).toBe(2026)
    expect(formatYearForDisplay(2026)).toBe('2026年')
    const month = parseLocalMonth('2026-10')
    expect(month).toEqual({ year: 2026, month: 10 })
    expect(formatMonthForDisplay(month!)).toBe('2026年10月')
    expect(parseLocalMonth('2026-13')).toBeNull()
    expect(parseLocalMonth('2026-1')).toBeNull()
  })

  it('keeps pure dates timezone-free and rejects impossible dates', () => {
    expect(parseLocalDate('2026-10-25')).toEqual({ year: 2026, month: 10, day: 25 })
    expect(formatDateForDisplay('2026-10-25T00:00:00')).toBe('2026-10-25')
    expect(parseLocalDate('2026-02-29')).toBeNull()
    expect(parseLocalDate('2024-02-29')).not.toBeNull()
    expect(formatDateForDisplay('not-a-date')).toBe('')
  })

  it('formats times by precision and preserves stored seconds', () => {
    expect(parseLocalTime('00:00')).toEqual({ hour: 0, minute: 0, second: 0 })
    expect(parseLocalTime('23:59:59')).toEqual({ hour: 23, minute: 59, second: 59 })
    expect(parseLocalTime('24:00')).toBeNull()
    expect(formatTimeForDisplay('09:05', 'minute')).toBe('09:05')
    expect(formatTimeForDisplay('09:05:30', 'minute')).toBe('09:05:30')
    expect(formatTimeForDisplay('', 'minute')).toBe('')
  })

  it('renders datetime with a space while serializing with T', () => {
    const parts = parseLocalDateTime('2026-10-25T09:30')
    expect(parts).not.toBeNull()
    expect(formatDateTimePartsForDisplay(parts!, 'minute')).toBe('2026-10-25 09:30')
    expect(formatDateTimeForDisplay('2026-10-25T09:30')).toBe('2026-10-25 09:30')
    expect(formatDateTimeForDisplay('2026-10-25 09:30')).toBe('2026-10-25 09:30')
    expect(formatDateTimeForDisplay('2026-12-31T23:59:59')).toBe('2026-12-31 23:59:59')
    expect(serializeLocalDateTime(parts!, 'minute')).toBe('2026-10-25T09:30')
    expect(formatDateTimeForDisplay('2026-10-25T23:30:45Z')).toBe('')
  })

  it('handles year boundaries, month ends and empty values', () => {
    expect(formatDateTimeForDisplay('2026-12-31T23:30')).toBe('2026-12-31 23:30')
    expect(formatDateTimeForDisplay('2027-01-01T00:30')).toBe('2027-01-01 00:30')
    expect(formatDateTimeForDisplay('2026-01-31T00:00')).toBe('2026-01-31 00:00')
    expect(formatDateTimeForDisplay('2026-02-29T00:00')).toBe('')
    expect(formatDateTimeForDisplay('')).toBe('')
    expect(formatDateTimeForDisplay(null)).toBe('')
  })

  it('validates format, range and required semantics', () => {
    expect(validateDateTime('').valid).toBe(true)
    expect(validateDateTime('', { required: true })).toMatchObject({
      valid: false,
      error: 'empty',
    })
    expect(validateDateTime('2026-02-30T10:00').error).toBe('format')
    expect(validateDateTime('2026-10-25T10:00', { min: '2026-10-26' }).error).toBe('range')
    expect(validateDateTime('2026-10-25T10:00', { max: '2026-10-25' }).valid).toBe(true)
  })
})
