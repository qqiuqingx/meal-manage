/* eslint-env jest */
/* eslint-disable no-console */
jest.mock('@/api/customer/profile', () => ({}))
jest.mock('@/api/customer/package', () => ({}))
jest.mock('@/api/system/dictDetail', () => ({}))

import OrderForm, { createFirstOrderDefaultForm } from '@/components/Order/OrderForm.vue'

const originalLog = console.log

beforeAll(() => {
  console.log = jest.fn()
})

afterAll(() => {
  console.log = originalLog
})

function createContext(overrides = {}) {
  const form = {
    ...createFirstOrderDefaultForm(),
    ...overrides
  }
  const ctx = {
    form,
    $set(target, key, value) {
      target[key] = value
    },
    loadChildPackages: jest.fn()
  }

  ctx.syncCalendarSelectionFromDeliveryDates = OrderForm.methods.syncCalendarSelectionFromDeliveryDates.bind(ctx)
  ctx.syncSerializedDeliveryDates = OrderForm.methods.syncSerializedDeliveryDates.bind(ctx)

  return ctx
}

describe('OrderForm delivery date sync', () => {
  test('normalizes incoming deliveryDates for calendar data', () => {
    const selected = [{ date: '2026-04-24', mealTypes: ['BREAKFAST', 'DINNER'] }]
    const ctx = createContext({
      deliveryDates: JSON.stringify(selected)
    })

    OrderForm.watch.value.handler.call(ctx, ctx.form)

    expect(ctx.form.deliveryDatesWithMealTypes).toEqual(selected)
  })

  test('serializes calendar selections back to deliveryDates payload', () => {
    const selected = [{ date: '2026-04-24', mealTypes: ['BREAKFAST', 'DINNER'] }]
    const ctx = createContext()

    OrderForm.watch['form.deliveryDatesWithMealTypes'].handler.call(ctx, selected)

    expect(ctx.form.deliveryDates).toBe(JSON.stringify(selected))
  })
})
