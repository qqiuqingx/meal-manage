/* eslint-env jest */
import { createLocalVue, shallowMount, mount } from '@vue/test-utils'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import CustomerOrder from '@/views/customer/order/index.vue'
import { getOrders, updateInline } from '@/api/customer/order'
import { get } from '@/api/system/dictDetail'

jest.mock('@/api/customer/order', () => ({ getOrders: jest.fn(), updateInline: jest.fn() }))
jest.mock('@/api/customer/profile', () => ({ getDietOptions: jest.fn() }))
jest.mock('@/api/system/dictDetail', () => ({ get: jest.fn() }))
jest.mock('@/api/mealRefund', () => ({ refundMeal: jest.fn() }))
jest.mock('@/api/data', () => ({ initData: jest.fn(), download: jest.fn() }))
jest.mock('@/utils/auth', () => ({ getToken: () => '' }))
jest.mock('@/utils/request', () => jest.fn())

const localVue = createLocalVue()
localVue.use(Vuex)
localVue.use(ElementUI)
localVue.directive('loading', {})
const flush = () => new Promise(resolve => setTimeout(resolve, 0))
const orders = (start, count = 20) => Array.from({ length: count }, (_, i) => ({ id: start + i, customerId: 7, status: 1 }))
function deferred() {
  let resolve
  const promise = new Promise(callback => { resolve = callback })
  return { promise, resolve }
}

// 保留真实 CRUD 生命周期，替换表格渲染并提供可触发的滚动容器。
const tableStub = {
  props: ['data', 'height'],
  created() {
    const table = this
    this.bodyWrapper = {
      scrollTop: 0, scrollLeft: 0, clientHeight: 400,
      get scrollHeight() { return table.data.length * 40 },
      addEventListener: jest.fn(), removeEventListener: jest.fn()
    }
    this.headerWrapper = { scrollLeft: 0 }
  },
  methods: { clearSelection: jest.fn() },
  render(h) { return h('div') }
}

describe('CustomerOrder lazy loading with real CRUD', () => {
  let wrapper
  let vm
  beforeEach(() => {
    getOrders.mockReset().mockResolvedValue({ content: orders(1), totalElements: 60 })
    updateInline.mockReset().mockResolvedValue({})
    get.mockReset().mockResolvedValue({ content: [] })
  })
  afterEach(() => {
    if (wrapper) wrapper.destroy()
    wrapper = null
  })
  async function mountPage(realTable = false) {
    wrapper = (realTable ? mount : shallowMount)(CustomerOrder, {
      localVue,
      store: new Vuex.Store({ getters: { roles: () => ['admin'], baseApi: () => '', imagesUploadApi: () => '' }}),
      mocks: { checkPer: () => true, $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }},
      stubs: realTable ? { crudOperation: true, rrOperation: true, OrderForm: true, 'el-dialog': true } : { 'el-table': tableStub, 'el-table-column': true, 'el-dialog': true }
    })
    vm = wrapper.vm
    await flush()
  }

  test('loads once and appends on scroll without duplicate requests or lost selection', async() => {
    await mountPage()
    expect(getOrders).toHaveBeenCalledTimes(1)
    expect(getOrders).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    expect(wrapper.find('el-pagination-stub').exists()).toBe(false)
    const firstRow = vm.crud.data[0]
    vm.crud.selectionChangeHandler([firstRow])
    vm.getTableScrollContainer().scrollTop = 350
    getOrders.mockResolvedValue({ content: orders(21), totalElements: 60 })
    vm.handleTableScroll()
    vm.handleTableScroll()
    await flush()
    expect(getOrders).toHaveBeenCalledTimes(2)
    expect(getOrders).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 20 }))
    expect(vm.crud.data.map(row => row.id)).toEqual(orders(1, 40).map(row => row.id))
    expect(vm.crud.selections).toEqual([firstRow])
    expect(vm.crud.getDataStatus(21)).toEqual({ delete: 0, edit: 0 })
  })

  test('real Element UI table retains selected rows on append and clears them on reset', async() => {
    await mountPage(true)
    const table = vm.$refs.table
    table.toggleRowSelection(vm.crud.data[0], true)
    expect(vm.crud.selections.map(row => row.id)).toEqual([1])
    getOrders.mockResolvedValueOnce({ content: orders(21), totalElements: 60 })
    await vm.loadNextPage()
    await flush()
    expect(table.selection.map(row => row.id)).toEqual([1])
    expect(vm.crud.selections.map(row => row.id)).toEqual([1])
    getOrders.mockResolvedValueOnce({ content: orders(100, 1), totalElements: 1 })
    vm.crud.resetQuery()
    await flush()
    expect(table.selection).toEqual([])
    expect(vm.crud.selections).toEqual([])
  })

  test('uses submitted filters and ignores older responses after a search', async() => {
    await mountPage()
    const stale = deferred()
    const search = deferred()
    getOrders.mockReturnValueOnce(stale.promise).mockReturnValueOnce(search.promise)
    vm.query.customerName = '新客户'
    vm.loadNextPage()
    expect(getOrders).toHaveBeenNthCalledWith(2, expect.objectContaining({ page: 2, customerName: undefined }))
    vm.crud.toQuery()
    expect(vm.crud.data).toEqual([])
    expect(vm.getTableScrollContainer().scrollTop).toBe(0)
    search.resolve({ content: orders(100, 1), totalElements: 1 })
    await flush()
    stale.resolve({ content: orders(21), totalElements: 60 })
    await flush()
    expect(getOrders).toHaveBeenNthCalledWith(3, expect.objectContaining({ page: 1, customerName: '新客户' }))
    expect(vm.crud.data.map(row => row.id)).toEqual([100])
    expect(vm.crud.page.total).toBe(1)
  })

  test('retains orders on append failure and retries the same page', async() => {
    await mountPage()
    getOrders.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({ content: orders(21), totalElements: 60 })
    await vm.loadNextPage()
    expect(vm.loadError).toBe(true)
    expect(vm.crud.data).toHaveLength(20)
    expect(vm.crud.page.page).toBe(1)
    vm.handleTableScroll()
    expect(getOrders).toHaveBeenCalledTimes(2)
    await vm.retryLoadOrders()
    expect(getOrders).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))
    expect(vm.crud.data).toHaveLength(40)
    expect(vm.loadError).toBe(false)
  })

  test('retries initial loading and stops after short or empty responses', async() => {
    getOrders.mockRejectedValueOnce(new Error('network'))
    await mountPage()
    expect(vm.loadError).toBe(true)
    expect(vm.crud.page.page).toBe(0)
    getOrders.mockResolvedValueOnce({ content: orders(1, 2), totalElements: 60 })
    await vm.retryLoadOrders()
    await flush()
    expect(getOrders).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
    expect(vm.hasMoreOrders).toBe(false)
    expect(getOrders).toHaveBeenCalledTimes(2)
    getOrders.mockResolvedValueOnce({ content: orders(1), totalElements: 60 })
    vm.crud.refresh()
    await flush()
    getOrders.mockResolvedValueOnce({ content: [], totalElements: 60 })
    await vm.loadNextPage()
    expect(vm.crud.data).toHaveLength(20)
    expect(vm.hasMoreOrders).toBe(false)
  })

  test('refreshes the loaded range after inline save and preserves scroll positions', async() => {
    await mountPage()
    getOrders.mockResolvedValueOnce({ content: orders(21), totalElements: 60 })
    await vm.loadNextPage()
    const body = vm.getTableScrollContainer()
    body.scrollTop = 800
    body.scrollLeft = 24
    vm.$refs.table.headerWrapper.scrollLeft = 24
    vm.query.customerName = '未提交条件'
    getOrders.mockResolvedValueOnce({ content: orders(1, 40), totalElements: 60 })
    await vm.saveInlineValue(vm.crud.data[30], 'mainDishCount', 2, 1)
    expect(updateInline).toHaveBeenCalledWith(31, { field: 'mainDishCount', value: 2, expectedValue: 1 })
    expect(getOrders).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 40, customerName: undefined }))
    expect(vm.crud.data).toHaveLength(40)
    expect(vm.crud.page.page).toBe(2)
    expect(body.scrollTop).toBe(800)
    expect(body.scrollLeft).toBe(24)
    getOrders.mockResolvedValueOnce({ content: orders(41), totalElements: 60 })
    await vm.loadNextPage()
    expect(getOrders).toHaveBeenLastCalledWith(expect.objectContaining({ page: 3, size: 20 }))
    expect(vm.hasMoreOrders).toBe(false)
  })

  test('fills the viewport and removes listeners when leaving or destroying', async() => {
    await mountPage()
    const body = vm.getTableScrollContainer()
    body.clientHeight = 1200
    getOrders.mockResolvedValueOnce({ content: orders(21), totalElements: 60 })
    vm.updateTableHeight()
    await flush()
    expect(vm.crud.data).toHaveLength(40)
    vm.deactivateOrderList()
    expect(body.removeEventListener).toHaveBeenCalledWith('scroll', vm.handleTableScroll)
    vm.handleTableScroll()
    expect(getOrders).toHaveBeenCalledTimes(2)
    vm.activateOrderList()
    await flush()
    expect(body.addEventListener).toHaveBeenCalledTimes(2)
    wrapper.destroy()
    expect(body.removeEventListener).toHaveBeenCalledTimes(2)
    wrapper = null
  })
})
