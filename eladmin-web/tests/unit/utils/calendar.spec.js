/* eslint-env jest */
import { formatDate, MealTypeName, MealType, normalizeDeliveryDates } from '@/utils/calendar'

describe('calendar.js utilities', () => {
  describe('UI-04: formatDate returns yyyy-MM-dd string', () => {
    test('formatDate returns yyyy-MM-dd string (not UTC-shifted)', () => {
      // Use a specific local time date to avoid timezone ambiguity
      const result = formatDate(new Date('2026-04-15T00:00:00'))
      expect(result).toBe('2026-04-15')
    })

    test('formatDate pads single-digit month and day with leading zero', () => {
      const result = formatDate(new Date('2026-01-05T12:00:00'))
      expect(result).toBe('2026-01-05')
    })

    test('formatDate uses local getFullYear/getMonth/getDate (not UTC getters)', () => {
      // The formatDate function must use getFullYear, getMonth, getDate (local)
      // not getUTCFullYear, getUTCMonth, getUTCDate (UTC)
      const fs = require('fs')
      const path = require('path')
      const source = fs.readFileSync(
        path.resolve(__dirname, '../../../src/utils/calendar.js'),
        'utf8'
      )
      // Must use local getters for year, month, day
      expect(source).toContain('getFullYear()')
      expect(source).toContain('getMonth()')
      expect(source).toContain('getDate()')
      // Must NOT use UTC getters (would cause timezone bugs)
      expect(source).not.toContain('getUTCFullYear')
      expect(source).not.toContain('getUTCMonth')
      expect(source).not.toContain('getUTCDate')
    })
  })
})
