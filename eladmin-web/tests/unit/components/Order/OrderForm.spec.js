/* eslint-env jest */
/* eslint-disable no-console */
jest.mock('@/api/customer/profile', () => ({}))
jest.mock('@/api/customer/order', () => ({}))
jest.mock('@/api/customer/package', () => ({}))
jest.mock('@/api/system/dictDetail', () => ({}))
jest.mock('@/api/dish', () => ({
  queryDishes: jest.fn()
}))
jest.mock('@/api/dishIngredient', () => ({
  queryIngredients: jest.fn()
}))

import * as dishApi from '@/api/dish'
import OrderForm, {
  createFirstOrderDefaultForm,
  DEFAULT_RICE_TYPE_OPTION_VALUE
} from '@/components/Order/OrderForm.vue'
import fs from 'fs'
import path from 'path'

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
    mode: 'firstOrder',
    hydratingForm: false,
    hydrationTimer: null,
    riceTypeOptions: [],
    startMealTypeOptions: [
      { label: '早餐开始', value: 'BREAKFAST' },
      { label: '午餐开始', value: 'LUNCH' },
      { label: '晚餐开始', value: 'DINNER' }
    ],
    $set(target, key, value) {
      target[key] = value
    },
    loadChildPackages: jest.fn()
  }

  ctx.beginFormHydration = OrderForm.methods.beginFormHydration.bind(ctx)
  ctx.normalizeFormNumbers = OrderForm.methods.normalizeFormNumbers.bind(ctx)
  ctx.syncCalendarSelectionFromDeliveryDates = OrderForm.methods.syncCalendarSelectionFromDeliveryDates.bind(ctx)
  ctx.syncSerializedDeliveryDates = OrderForm.methods.syncSerializedDeliveryDates.bind(ctx)
  ctx.ensureRiceTypeOption = OrderForm.methods.ensureRiceTypeOption.bind(ctx)
  ctx.ensureRiceTypeValue = OrderForm.methods.ensureRiceTypeValue.bind(ctx)
  ctx.syncStartMealType = OrderForm.methods.syncStartMealType.bind(ctx)
  ctx.ensureTrialOrderOption = OrderForm.methods.ensureTrialOrderOption.bind(ctx)
  ctx.initReplaceRuleOptions = OrderForm.methods.initReplaceRuleOptions.bind(ctx)

  return ctx
}

describe('OrderForm delivery date sync', () => {
  beforeEach(() => {
    dishApi.queryDishes.mockReset()
  })

  test('normalizes incoming deliveryDates for calendar data', () => {
    const selected = [{ date: '2026-04-24', mealTypes: ['BREAKFAST', 'DINNER'] }]
    const ctx = createContext({
      deliveryDates: JSON.stringify(selected)
    })

    OrderForm.watch.value.handler.call(ctx, ctx.form)

    expect(ctx.form.deliveryDatesWithMealTypes).toEqual(selected)
  })

  test('collapses amount info when a new form value is loaded', () => {
    const ctx = createContext()
    ctx.amountInfoActiveNames = ['amountInfo']

    OrderForm.watch.value.handler.call(ctx, ctx.form)

    expect(ctx.amountInfoActiveNames).toEqual([])
  })

  test('serializes calendar selections back to deliveryDates payload', () => {
    const selected = [{ date: '2026-04-24', mealTypes: ['BREAKFAST', 'DINNER'] }]
    const ctx = createContext()

    OrderForm.watch['form.deliveryDatesWithMealTypes'].handler.call(ctx, selected)

    expect(ctx.form.deliveryDates).toBe(JSON.stringify(selected))
  })

  test('does not overwrite saved totalAmount while hydrating order form', () => {
    const ctx = createContext({
      breakfastCount: 10,
      lunchDinnerCount: 13,
      breakfastPrice: 1,
      lunchDinnerPrice: 1,
      totalAmount: 99
    })
    ctx.mode = 'order'
    ctx.hydratingForm = true
    ctx.$emit = jest.fn()

    OrderForm.methods.calcTotalAmount.call(ctx)

    expect(ctx.form.totalAmount).toBe(99)
  })

  test('normalizes numeric form fields for input-number controls', () => {
    const ctx = createContext({
      totalAmount: '23',
      finalAmount: '10',
      breakfastPrice: '1',
      lunchDinnerPrice: '1'
    })

    OrderForm.methods.normalizeFormNumbers.call(ctx)

    expect(ctx.form.totalAmount).toBe(23)
    expect(ctx.form.finalAmount).toBe(10)
    expect(ctx.form.breakfastPrice).toBe(1)
    expect(ctx.form.lunchDinnerPrice).toBe(1)
  })

  test('uses the default rice option in a new order form', () => {
    expect(createFirstOrderDefaultForm().riceType).toBe(DEFAULT_RICE_TYPE_OPTION_VALUE)
    expect(DEFAULT_RICE_TYPE_OPTION_VALUE).toBe('默认')
  })

  test('prepends a visible default rice option before fetched rice types', async() => {
    dishApi.queryDishes.mockResolvedValue({
      content: [
        { name: '白米饭' },
        { name: '三色糙米' }
      ]
    })
    const ctx = createContext()

    await OrderForm.methods.loadRiceTypeOptions.call(ctx)

    expect(ctx.riceTypeOptions).toEqual([
      { label: '默认', value: DEFAULT_RICE_TYPE_OPTION_VALUE },
      { label: '白米饭', value: '白米饭' },
      { label: '三色糙米', value: '三色糙米' }
    ])
  })

  test('handles allergy Enter before Element UI creates a combined tag', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../../src/components/Order/OrderForm.vue'), 'utf8')

    expect(source).toContain('@keydown.native.capture="handleAllergyKeydown"')
    expect(source).not.toContain('@keydown.native="handleAllergyKeydown"')
  })
})
