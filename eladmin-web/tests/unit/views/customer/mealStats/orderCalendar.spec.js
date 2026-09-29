/* eslint-env jest */
import { createLocalVue, shallowMount } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Vuex from 'vuex'
import CustomerMealStats from '@/views/customer/mealStats/index.vue'
import { getMealStats, getOrderMealCalendar, saveOrderMealCalendar } from '@/api/customer/profile'
import { getDepletionWarnings } from '@/api/mealPlan'

jest.mock('@/api/customer/profile', () => ({
  getMealStats: jest.fn(),
  getOrderMealCalendar: jest.fn(),
  saveOrderMealCalendar: jest.fn()
}))

jest.mock('@/api/mealPlan', () => ({
  getDepletionWarnings: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.use(Vuex)
const store = new Vuex.Store({ getters: { baseApi: () => '', roles: () => ['admin'] }})

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

describe('CustomerMealStats order calendar flow', () => {
  let wrapper

  beforeEach(() => {
    getMealStats.mockReset().mockResolvedValue({ content: [], totalElements: 0 })
    getDepletionWarnings.mockReset().mockResolvedValue([])
    getOrderMealCalendar.mockReset()
    saveOrderMealCalendar.mockReset()
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.destroy()
      wrapper = null
    }
  })

  test('loads the clicked order and retains hidden zero overrides in the save snapshot', async() => {
    getOrderMealCalendar.mockResolvedValue({
      orderId: 42,
      customerId: 7,
      orderCode: 'ORD-42',
      customerCode: 'A007',
      customerName: '客户甲',
      statsMonth: '2026-09',
      status: 1,
      editable: true,
      revision: 'rev-42',
      cells: [{
        orderId: 42,
        date: '2026-09-23',
        mealType: 'LUNCH',
        baseQuantity: 1,
        quantity: 0,
        soupQuantity: null,
        manualOverride: true,
        customerExcluded: false,
        orderExcluded: true,
        generatedCount: 0,
        failedCount: 0,
        verifiedCount: 0
      }],
      overrides: [{ date: '2026-09-23', mealType: 'LUNCH', quantity: 0, soupQuantity: null, remark: '停餐' }]
    })
    saveOrderMealCalendar.mockResolvedValue({ orderId: 42, statsMonth: '2026-09', revision: 'rev-43' })
    wrapper = shallowMount(CustomerMealStats, { localVue, store })
    await flushPromises()

    wrapper.vm.query.statsMonth = '2026-09'
    wrapper.vm.openOrderCalendar({
      orderId: 42,
      customerId: 7,
      orderCode: 'ORD-42',
      customerCode: 'A007',
      customerName: '客户甲'
    })
    await flushPromises()

    expect(getOrderMealCalendar).toHaveBeenCalledWith(42, '2026-09')
    expect(wrapper.vm.calendarData.orderId).toBe(42)
    expect(wrapper.vm.calendarDraftOverrides).toEqual([
      { date: '2026-09-23', mealType: 'LUNCH', quantity: 0, soupQuantity: null, remark: '停餐' }
    ])

    await wrapper.vm.saveCalendar()

    expect(saveOrderMealCalendar).toHaveBeenCalledWith(42, {
      statsMonth: '2026-09',
      expectedRevision: 'rev-42',
      overrides: [{ date: '2026-09-23', mealType: 'LUNCH', quantity: 0, soupQuantity: null, remark: '停餐' }]
    })
  })

  test('keeps the edited order calendar draft after a 409 response', async() => {
    saveOrderMealCalendar.mockRejectedValue({
      response: { status: 409, data: { message: '版本已变化' }}
    })
    wrapper = shallowMount(CustomerMealStats, { localVue, store })
    await flushPromises()
    wrapper.vm.selectedRow = { orderId: 55 }
    wrapper.vm.calendarMonth = '2026-09'
    wrapper.vm.calendarData = { orderId: 55, editable: true }
    wrapper.vm.calendarRevision = 'stale'
    wrapper.vm.calendarLoaded = true
    wrapper.vm.calendarDialogVisible = true
    wrapper.vm.calendarDraftOverrides = [{
      date: '2026-09-23', mealType: 'DINNER', quantity: 2, soupQuantity: 1, remark: '手动调整'
    }]

    await wrapper.vm.saveCalendar()

    expect(wrapper.vm.calendarDialogVisible).toBe(true)
    expect(wrapper.vm.calendarDraftOverrides).toEqual([
      { date: '2026-09-23', mealType: 'DINNER', quantity: 2, soupQuantity: 1, remark: '手动调整' }
    ])
  })
})
