/* eslint-env jest */
import { createLocalVue, shallowMount } from '@vue/test-utils'
import Vuex from 'vuex'
import CustomerMealStats from '@/views/customer/mealStats/index.vue'
import { getMealStats } from '@/api/customer/profile'
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
localVue.use(Vuex)
localVue.directive('loading', {})
const store = { getters: { baseApi: '', roles: ['admin'] }}

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

function makeOrder(orderId, customerId = 7) {
  return { orderId, customerId, customerCode: 'A007', customerName: '同一客户', orderCode: `ORD-${orderId}` }
}

function createDeferred() {
  let resolve
  const promise = new Promise(resolvePromise => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('CustomerMealStats order pagination', () => {
  let wrapper

  beforeEach(() => {
    getMealStats.mockReset().mockResolvedValue({ content: [], totalElements: 0 })
    getDepletionWarnings.mockReset().mockResolvedValue([])
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.destroy()
      wrapper = null
    }
  })

  async function mountPage() {
    wrapper = shallowMount(CustomerMealStats, {
      localVue,
      store,
      stubs: {
        'el-input': true,
        'el-date-picker': true,
        'el-button': true,
        'el-table': true,
        'el-table-column': true,
        'el-tooltip': true,
        'el-alert': true,
        'el-dialog': true,
        'el-tag': true,
        'el-image': true
      }
    })
    await flushPromises()
    getMealStats.mockClear()
    return wrapper.vm
  }

  test('appends separate order rows for the same customer without row grouping', async() => {
    const vm = await mountPage()
    const body = { scrollHeight: 1000, scrollTop: 550, clientHeight: 400 }
    vm.getTableScrollContainer = jest.fn(() => body)
    await wrapper.setData({ rows: [makeOrder(70)], page: { current: 1, size: 20, total: 2 }})
    getMealStats.mockResolvedValue({ content: [makeOrder(71)], totalElements: 2 })

    vm.handleTableScroll()
    vm.handleTableScroll()
    await flushPromises()

    expect(getMealStats).toHaveBeenCalledTimes(1)
    expect(getMealStats).toHaveBeenCalledWith(expect.objectContaining({ page: 2, size: 20 }))
    expect(vm.rows.map(row => row.orderId)).toEqual([70, 71])
    expect(vm.rows[0].customerId).toBe(vm.rows[1].customerId)
    expect(vm.rows).toHaveLength(2)
  })

  test('keeps existing rows after a failed append and retries the same page', async() => {
    const vm = await mountPage()
    const firstRow = makeOrder(80)
    await wrapper.setData({ rows: [firstRow], page: { current: 1, size: 20, total: 40 }})
    getMealStats
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce({ content: [makeOrder(81)], totalElements: 40 })

    vm.loadNextPage()
    await flushPromises()

    expect(vm.rows).toEqual([firstRow])
    expect(vm.loadError).toBe(true)
    expect(vm.page.current).toBe(1)

    vm.loadNextPage(true)
    await flushPromises()

    expect(getMealStats).toHaveBeenCalledTimes(2)
    expect(getMealStats).toHaveBeenNthCalledWith(1, expect.objectContaining({ page: 2 }))
    expect(getMealStats).toHaveBeenNthCalledWith(2, expect.objectContaining({ page: 2 }))
    expect(vm.rows.map(row => row.orderId)).toEqual([80, 81])
    expect(vm.loadError).toBe(false)
  })

  test('resets to page one and ignores an older append response after search changes', async() => {
    const vm = await mountPage()
    const staleRequest = createDeferred()
    const searchRequest = createDeferred()
    await wrapper.setData({ rows: [makeOrder(90)], page: { current: 1, size: 20, total: 40 }})
    getMealStats.mockReturnValueOnce(staleRequest.promise).mockReturnValueOnce(searchRequest.promise)

    vm.loadNextPage()
    await wrapper.setData({ query: { ...vm.query, customerName: '新客户' }})
    vm.handleQuery()

    searchRequest.resolve({ content: [makeOrder(100, 20)], totalElements: 1 })
    await flushPromises()
    staleRequest.resolve({ content: [makeOrder(91)], totalElements: 40 })
    await flushPromises()

    expect(getMealStats).toHaveBeenNthCalledWith(1, expect.objectContaining({ page: 2 }))
    expect(getMealStats).toHaveBeenNthCalledWith(2, expect.objectContaining({ page: 1, customerName: '新客户' }))
    expect(vm.rows.map(row => row.orderId)).toEqual([100])
    expect(vm.page.current).toBe(1)
    expect(vm.page.total).toBe(1)
  })
})
