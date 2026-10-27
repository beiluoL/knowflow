// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import {
  buildTimeOptions,
  formatTimeString,
  hasMeaningfulSeconds,
  isTimeInRange,
  parseTimeInput,
  parseTimeString,
  timeFromSeconds,
  timeToSeconds,
} from '../time-picker-utils'

describe('time picker utils', () => {
  it('parses HH:mm and HH:mm:ss strictly', () => {
    expect(parseTimeString('09:05')).toEqual({ hour: 9, minute: 5, second: 0 })
    expect(parseTimeString('23:59:59')).toEqual({ hour: 23, minute: 59, second: 59 })
    expect(parseTimeString('9:05')).toBeNull()
    expect(parseTimeString('24:00')).toBeNull()
    expect(parseTimeString('12:60')).toBeNull()
    expect(parseTimeString('12:30:60')).toBeNull()
    expect(parseTimeString('')).toBeNull()
    expect(parseTimeString(null)).toBeNull()
  })

  it('accepts loose manual input and rejects illegal ranges', () => {
    expect(parseTimeInput('9:5')).toEqual({ hour: 9, minute: 5, second: 0 })
    expect(parseTimeInput('9:05:3')).toEqual({ hour: 9, minute: 5, second: 3 })
    expect(parseTimeInput('23:59:59')).toEqual({ hour: 23, minute: 59, second: 59 })
    expect(parseTimeInput('25:00')).toBeNull()
    expect(parseTimeInput('12:61')).toBeNull()
    expect(parseTimeInput('12:30:61')).toBeNull()
  })

  it('formats to minute or second precision with zero padding', () => {
    const parts = { hour: 9, minute: 5, second: 3 }
    expect(formatTimeString(parts, 'minute')).toBe('09:05')
    expect(formatTimeString(parts, 'second')).toBe('09:05:03')
    expect(formatTimeString({ hour: 0, minute: 0, second: 0 }, 'second')).toBe('00:00:00')
    expect(formatTimeString({ hour: 23, minute: 59, second: 59 }, 'second')).toBe('23:59:59')
  })

  it('keeps meaningful seconds visible instead of silently truncating', () => {
    expect(hasMeaningfulSeconds({ hour: 1, minute: 2, second: 3 })).toBe(true)
    expect(hasMeaningfulSeconds({ hour: 1, minute: 2, second: 0 })).toBe(false)
  })

  it('compares min/max boundaries by seconds', () => {
    expect(isTimeInRange({ hour: 9, minute: 0, second: 0 }, '09:00', '18:00')).toBe(true)
    expect(isTimeInRange({ hour: 8, minute: 59, second: 59 }, '09:00', '18:00')).toBe(false)
    expect(isTimeInRange({ hour: 18, minute: 0, second: 0 }, '09:00', '18:00')).toBe(true)
    expect(isTimeInRange({ hour: 18, minute: 1, second: 0 }, '09:00', '18:00')).toBe(false)
    expect(isTimeInRange({ hour: 0, minute: 0, second: 0 }, undefined, undefined)).toBe(true)
  })

  it('round-trips seconds and builds bounded option lists', () => {
    expect(timeToSeconds({ hour: 23, minute: 59, second: 59 })).toBe(86399)
    expect(timeFromSeconds(86399)).toEqual({ hour: 23, minute: 59, second: 59 })
    expect(timeFromSeconds(0)).toEqual({ hour: 0, minute: 0, second: 0 })
    expect(buildTimeOptions(23)).toHaveLength(24)
    expect(buildTimeOptions(59)).toHaveLength(60)
  })
})
