/* eslint-env jest */
import BusinessOrderListCard from '@/views/agent/diagnosis/components/businessOrderListCard.vue'

describe('BusinessOrderListCard', () => {
  test('uses deal time first and falls back to create time', () => {
    const orderTime = BusinessOrderListCard.methods.orderTime

    expect(orderTime({
      dealTime: '2026-08-04T09:30:00',
      createTime: '2026-08-04T09:20:00'
    })).toBe('2026-08-04T09:30:00')
    expect(orderTime({ createTime: '2026-08-04T09:20:00' }))
      .toBe('2026-08-04T09:20:00')
    expect(orderTime({})).toBe('-')
  })
})
