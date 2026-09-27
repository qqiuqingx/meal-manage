/* eslint-env jest */
import { createLocalVue, shallowMount } from '@vue/test-utils'
import CustomerMealStats from '@/views/customer/mealStats/index.vue'
import { getMealStats } from '@/api/customer/profile'
import { getDepletionWarnings } from '@/api/mealPlan'

jest.mock('@/api/customer/profile', () => ({
  getMealStats: jest.fn(),
  saveMealScheduleAdjustments: jest.fn()
}))

jest.mock('@/api/mealPlan', () => ({
  getDepletionWarnings: jest.fn()
}))

const localVue = createLocalVue()
localVue.directive('loading', {})

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

function makeRow(customerId, mealBucket) {
  return {
    rowKey: `${customerId}-${mealBucket}`,
    customerId,
    mealBucket,
    firstRowInGroup: true,
    groupRowSpan: 1
  }
}

function createDeferred() {
  let resolve
  const promise = new Promise(resolvePromise => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('CustomerMealStats lazy loading', () => {
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
      stubs: {
        'el-input': true,
        'el-date-picker': true,
        'el-button': true,
        'el-table': true,
        'el-table-column': true,
        'el-tooltip': true,
        'el-alert': true,
        'el-dialog': true
      }
    })
    await flushPromises()
    getMealStats.mockClear()
    return wrapper.vm
  }

  test('loads another page near the bottom and joins a customer group across pages', async() => {
    const vm = await mountPage()
    const body = { scrollHeight: 1000, scrollTop: 550, clientHeight: 400 }
    vm.getTableScrollContainer = jest.fn(() => body)
    await wrapper.setData({
      rows: [makeRow(7, 'BREAKFAST')],
      page: { current: 1, size: 20, total: 40 }
    })
    getMealStats.mockResolvedValue({
      content: [makeRow(7, 'LUNCH_DINNER')],
      totalElements: 40
    })

    vm.handleTableScroll()
    vm.handleTableScroll()
    await flushPromises()

    expect(getMealStats).toHaveBeenCalledTimes(1)
    expect(getMealStats).toHaveBeenCalledWith(expect.objectContaining({ page: 2, size: 20 }))
    expect(vm.rows.map(row => row.mealBucket)).toEqual(['BREAKFAST', 'LUNCH_DINNER'])
    expect(vm.rows.map(row => [row.firstRowInGroup, row.groupRowSpan])).toEqual([[true, 2], [false, 0]])
  })

  test('keeps existing rows after a failed request and retries the same page', async() => {
    const vm = await mountPage()
    const firstRow = makeRow(8, 'LUNCH_DINNER')
    await wrapper.setData({
      rows: [firstRow],
      page: { current: 1, size: 20, total: 40 }
    })
    getMealStats
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce({ content: [makeRow(9, 'BREAKFAST')], totalElements: 40 })

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
    expect(vm.rows).toHaveLength(2)
    expect(vm.loadError).toBe(false)
  })

  test('resets to page one and ignores an older append response after search changes', async() => {
    const vm = await mountPage()
    const staleRequest = createDeferred()
    const searchRequest = createDeferred()
    await wrapper.setData({
      rows: [makeRow(10, 'BREAKFAST')],
      page: { current: 1, size: 20, total: 40 }
    })
    getMealStats
      .mockReturnValueOnce(staleRequest.promise)
      .mockReturnValueOnce(searchRequest.promise)

    vm.loadNextPage()
    await wrapper.setData({
      query: {
        ...vm.query,
        customerName: '新客户'
      }
    })
    vm.handleQuery()

    searchRequest.resolve({ content: [makeRow(20, 'LUNCH_DINNER')], totalElements: 1 })
    await flushPromises()
    staleRequest.resolve({ content: [makeRow(10, 'LUNCH_DINNER')], totalElements: 40 })
    await flushPromises()

    expect(getMealStats).toHaveBeenNthCalledWith(1, expect.objectContaining({ page: 2 }))
    expect(getMealStats).toHaveBeenNthCalledWith(2, expect.objectContaining({ page: 1, customerName: '新客户' }))
    expect(vm.rows).toEqual([expect.objectContaining({ customerId: 20 })])
    expect(vm.page.current).toBe(1)
    expect(vm.page.total).toBe(1)
  })
})
