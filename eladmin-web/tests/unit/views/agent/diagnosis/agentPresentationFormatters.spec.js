/* eslint-env jest */
import {
  formatFieldValue,
  formatValue,
  isSafePath,
  readPath
} from '@/views/agent/diagnosis/utils/agentPresentationFormatters'

describe('agentPresentationFormatters', () => {
  test('reads dot paths and fixed array paths only', () => {
    const source = {
      data: {
        groups: [
          { items: [{ label: 'A' }] },
          { items: [{ label: 'B' }] }
        ]
      }
    }

    expect(readPath(source, 'data.groups[].items')).toEqual([{ label: 'A' }, { label: 'B' }])
    expect(readPath(source, 'data.groups[].items[].label')).toEqual(['A', 'B'])
    expect(readPath(source, 'data.groups[0].items')).toBeUndefined()
  })

  test('rejects expressions and prototype-chain paths', () => {
    expect(isSafePath('data.items')).toBe(true)
    expect(isSafePath('data.items[0]')).toBe(false)
    expect(isSafePath('data.items.filter')).toBe(true)
    expect(isSafePath('data.__proto__.polluted')).toBe(false)
    expect(isSafePath('data.items.constructor')).toBe(false)
    expect(readPath({ data: { items: [{ value: 1 }] } }, 'data.items.map(value)')).toBeUndefined()
  })

  test('formats supported values and empty values safely', () => {
    expect(formatValue(null, 'TEXT')).toBe('-')
    expect(formatValue('2026-08-05T09:30:00', 'DATE_TIME')).toBe('2026-08-05 09:30:00')
    expect(formatValue('2026-08-05T09:30:00', 'DATE')).toBe('2026-08-05')
    expect(formatValue('LUNCH', 'MEAL_TYPE')).toBe('午餐')
    expect(formatValue('ACTIVE', 'STATUS')).toBe('进行中')
    expect(formatValue('CUSTOM_STATUS', 'STATUS')).toBe('CUSTOM_STATUS')
    expect(formatValue(12, 'NUMBER')).toBe('12')
    expect(formatValue(false, 'BOOLEAN')).toBe('否')
  })

  test('uses orderTime first and falls back to dealTime then createTime', () => {
    expect(formatFieldValue({ orderTime: '2026-08-05 10:00:00', dealTime: '2026-08-05 09:00:00' }, 'orderTime', 'DATE_TIME'))
      .toBe('2026-08-05 10:00:00')
    expect(formatFieldValue({ dealTime: '2026-08-05 09:00:00', createTime: '2026-08-05 08:00:00' }, 'orderTime', 'DATE_TIME'))
      .toBe('2026-08-05 09:00:00')
    expect(formatFieldValue({ createTime: '2026-08-05 08:00:00' }, 'orderTime', 'DATE_TIME'))
      .toBe('2026-08-05 08:00:00')
    expect(formatFieldValue({}, 'orderTime', 'DATE_TIME')).toBe('-')
  })

  test('formats current and historical metric codes as business labels', () => {
    expect(formatFieldValue({ metric: 'VERIFICATION_RECORD_COUNT' }, 'metric', 'TEXT')).toBe('核销记录总数')
    expect(formatFieldValue({ metric: 'DAILY_VERIFIED_CUSTOMER_COUNT' }, 'metric', 'TEXT')).toBe('当日已核销客户数')
    expect(formatFieldValue({ metric: 'FUTURE_METRIC' }, 'metric', 'TEXT')).toBe('FUTURE_METRIC')
  })
})
