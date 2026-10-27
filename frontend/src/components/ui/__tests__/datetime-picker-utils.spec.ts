// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import {
  formatDateTimeValue,
  parseDateTimeString,
  resolveDateTimePrecision,
} from '../datetime-picker-utils'

describe('datetime picker utils', () => {
  it('parses minute and second local date-times', () => {
    expect(parseDateTimeString('2026-10-24T23:30')).toEqual({
      date: '2026-10-24',
      hour: 23,
      minute: 30,
      second: 0,
    })
    expect(parseDateTimeString('2026-10-24T23:30:45')).toEqual({
      date: '2026-10-24',
      hour: 23,
      minute: 30,
      second: 45,
    })
    expect(parseDateTimeString('2026-10-24 09:05')).toEqual({
      date: '2026-10-24',
      hour: 9,
      minute: 5,
      second: 0,
    })
  })

  it('rejects impossible dates, illegal times and timezone suffixes', () => {
    expect(parseDateTimeString('2026-02-30T10:00')).toBeNull()
    expect(parseDateTimeString('2026-13-01T10:00')).toBeNull()
    expect(parseDateTimeString('2026-10-24T24:00')).toBeNull()
    expect(parseDateTimeString('2026-10-24T23:60')).toBeNull()
    expect(parseDateTimeString('2026-10-24T23:30:60')).toBeNull()
    expect(parseDateTimeString('2026-10-24T23:30:45Z')).toBeNull()
    expect(parseDateTimeString('2026-10-24T23:30+08:00')).toBeNull()
    expect(parseDateTimeString('')).toBeNull()
  })

  it('handles leap years and month/year boundaries without timezone conversion', () => {
    expect(parseDateTimeString('2024-02-29T23:59:59')).toEqual({
      date: '2024-02-29',
      hour: 23,
      minute: 59,
      second: 59,
    })
    expect(parseDateTimeString('2023-02-29T00:00')).toBeNull()
    const crossYear = parseDateTimeString('2026-12-31T23:30')!
    expect(formatDateTimeValue(crossYear, 'minute')).toBe('2026-12-31T23:30')
    const nextDay = parseDateTimeString('2027-01-01T00:30')!
    expect(formatDateTimeValue(nextDay, 'minute')).toBe('2027-01-01T00:30')
  })

  it('formats without Z and keeps seconds when present', () => {
    const parts = { date: '2026-12-31', hour: 23, minute: 30, second: 45 }
    expect(formatDateTimeValue(parts, 'minute')).toBe('2026-12-31T23:30')
    expect(formatDateTimeValue(parts, 'second')).toBe('2026-12-31T23:30:45')
    expect(formatDateTimeValue(parts, 'second').endsWith('Z')).toBe(false)
  })

  it('upgrades precision for stored seconds but stays minute for plain values', () => {
    expect(resolveDateTimePrecision('minute', null)).toBe('minute')
    expect(
      resolveDateTimePrecision('minute', {
        date: '2026-10-24',
        hour: 1,
        minute: 2,
        second: 3,
      }),
    ).toBe('second')
    expect(
      resolveDateTimePrecision('minute', {
        date: '2026-10-24',
        hour: 1,
        minute: 2,
        second: 0,
      }),
    ).toBe('minute')
    expect(resolveDateTimePrecision('second', null)).toBe('second')
  })
})
