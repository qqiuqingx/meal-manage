/* eslint-env jest */
import { createLocalVue, shallowMount, mount } from '@vue/test-utils'
import Vuex from 'vuex'
import VueRouter from 'vue-router'
import ElementUI from 'element-ui'
import CustomerOrder from '@/views/customer/order/index.vue'
import AppMain from '@/layout/components/AppMain.vue'
import { filterAsyncRouter } from '@/store/modules/permission'
import tagsView from '@/store/modules/tagsView'
import { getOrders, updateInline } from '@/api/customer/order'
import { get } from '@/api/system/dictDetail'

jest.mock('@/api/customer/order', () => ({ getOrders: jest.fn(), updateInline: jest.fn() }))
jest.mock('@/api/customer/profile', () => ({ getDietOptions: jest.fn() }))
jest.mock('@/api/system/dictDetail', () => ({ get: jest.fn() }))
jest.mock('@/api/mealRefund', () => ({ refundMeal: jest.fn() }))
jest.mock('@/api/data', () => ({ initData: jest.fn(), download: jest.fn() }))
jest.mock('@/utils/auth', () => ({ getToken: () => '' }))
jest.mock('@/utils/request', () => jest.fn())
jest.mock('@/layout/index', () => ({}))
jest.mock('@/components/ParentView', () => ({}))
jest.mock('@/router/routers', () => ({ constantRouterMap: [] }))

const localVue = createLocalVue()
localVue.use(Vuex)
localVue.use(VueRouter)
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
  methods: {
    clearSelection: jest.fn(),
    doLayout: jest.fn(),
    syncPostion() { this.headerWrapper.scrollLeft = this.bodyWrapper.scrollLeft }
  },
  render(h) { return h('div') }
}
const horizontalScrollStub = {
  methods: { updateMetrics: jest.fn() },
  render(h) { return h('div') }
}
// 仅省略过渡动画，保留 AppMain 内原始 keep-alive 节点及其缓存键。
const transitionStub = { functional: true, render: (h, context) => context.children[0] }

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
      sync: false,
      store: new Vuex.Store({ getters: { roles: () => ['admin'], baseApi: () => '', imagesUploadApi: () => '' }}),
      mocks: { checkPer: () => true, $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }},
      stubs: realTable ? { crudOperation: true, rrOperation: true, OrderForm: true, 'el-dialog': true } : { 'el-table': tableStub, 'el-table-column': true, 'el-dialog': true, TableHorizontalScroll: horizontalScrollStub }
    })
    vm = wrapper.vm
    await flush()
  }

  async function mountCachedPage(realTable = false) {
    const routes = filterAsyncRouter([{
      path: '/', component: 'Layout', children: [
        { path: 'orders', name: 'CustomerOrder', component: 'customer/order/index', meta: { title: '订单管理', noCache: true }},
        { path: 'other', name: 'Dish', component: 'meal/dish/index', meta: { title: '菜品管理', noCache: true }}
      ]
    }])
    routes[0].component = { render: h => h(AppMain) }
    routes[0].children[0].component = CustomerOrder
    routes[0].children[1].component = {
      data: () => ({ edited: false }),
      render(h) { return h('button', { on: { click: () => { this.edited = true } }}, this.edited ? '菜品编辑完成' : '编辑菜品') }
    }
    const store = new Vuex.Store({
      state: { settings: { showFooter: false }},
      getters: { roles: () => ['admin'], baseApi: () => '', imagesUploadApi: () => '' },
      modules: { tagsView: { ...tagsView, state: { visitedViews: [], cachedViews: [] }}}
    })
    const router = new VueRouter({
      routes
    })
    router.afterEach(route => store.dispatch('tagsView/addView', route))
    router.push('/orders')
    wrapper = mount({
      template: '<router-view />'
    }, {
      localVue,
      router,
      sync: false,
      store,
      mocks: { checkPer: () => true, $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }},
      stubs: realTable ? { transition: transitionStub, 'el-dialog': true, crudOperation: true, rrOperation: true, OrderForm: true } : { transition: transitionStub, 'el-table': tableStub, 'el-table-column': true, 'el-dialog': true, crudOperation: true, rrOperation: true, OrderForm: true, TableHorizontalScroll: horizontalScrollStub }
    })
    await flush()
    vm = wrapper.find(CustomerOrder).vm
    return router
  }

  test('restores table position and loaded state after switching cached tabs repeatedly', async() => {
    const router = await mountCachedPage()
    getOrders.mockResolvedValueOnce({ content: orders(21), totalElements: 60 })
    await vm.loadNextPage()
    vm.crud.selectionChangeHandler([vm.crud.data[30]])
    vm.query.customerName = '未提交的筛选'
    const body = vm.getTableScrollContainer()
    for (const top of [800, 500]) {
      body.scrollTop = top
      body.scrollLeft = 240
      router.push('/other')
      await flush()
      await wrapper.find('button').trigger('click')
      expect(wrapper.find('button').text()).toBe('菜品编辑完成')
      expect(vm.listActive).toBe(false)
      // 模拟表格 DOM 离开文档后浏览器丢失滚动位置。
      body.scrollTop = 0
      body.scrollLeft = 0
      router.push('/orders')
      await flush()
      expect(wrapper.find(CustomerOrder).vm === vm).toBe(true)
      expect(body.scrollTop).toBe(top)
      expect(body.scrollLeft).toBe(240)
      expect(vm.$refs.table.headerWrapper.scrollLeft).toBe(240)
    }
    expect(vm.crud.data).toHaveLength(40)
    expect(vm.crud.page.page).toBe(2)
    expect(vm.crud.selections.map(row => row.id)).toEqual([31])
    expect(vm.query.customerName).toBe('未提交的筛选')
    expect(getOrders).toHaveBeenCalledTimes(2)
  })

  test('destroys the cached order page when its tag closes and loads fresh data on reopening', async() => {
    const router = await mountCachedPage()
    const originalVm = vm
    vm.getTableScrollContainer().scrollTop = 300
    router.push('/other')
    await flush()
    const orderTag = wrapper.vm.$store.state.tagsView.visitedViews.find(view => view.path === '/orders')
    await wrapper.vm.$store.dispatch('tagsView/delView', orderTag)
    await flush()
    expect(originalVm._isDestroyed).toBe(true)
    getOrders.mockResolvedValueOnce({ content: orders(100), totalElements: 60 })
    router.push('/orders')
    await flush()
    const reopenedVm = wrapper.find(CustomerOrder).vm
    expect(reopenedVm === originalVm).toBe(false)
    expect(reopenedVm.getTableScrollContainer().scrollTop).toBe(0)
    expect(reopenedVm.crud.data[0].id).toBe(100)
    expect(getOrders).toHaveBeenCalledTimes(2)
  })

  test('waits for the table body layout before restoring position or loading more', async() => {
    const router = await mountCachedPage()
    getOrders.mockResolvedValueOnce({ content: orders(21), totalElements: 60 })
    await vm.loadNextPage()
    const table = vm.$refs.table
    const body = table.bodyWrapper
    let scrollTop = 0
    Object.defineProperty(body, 'scrollTop', {
      configurable: true,
      get: () => scrollTop,
      set: value => { scrollTop = Math.max(0, Math.min(value, body.scrollHeight - body.clientHeight)) }
    })
    body.scrollTop = 800
    router.push('/other')
    await flush()
    // 从下滑的长页面返回时，Element UI 主体会暂时按内容高度展开。
    body.clientHeight = body.scrollHeight
    body.scrollTop = 0
    table.doLayout = jest.fn(() => {
      vm.$nextTick(() => { body.clientHeight = 400 })
    })
    router.push('/orders')
    await flush()
    expect(wrapper.find(CustomerOrder).vm === vm).toBe(true)
    expect(body.scrollTop).toBe(800)
    expect(body.clientHeight).toBe(400)
    expect(vm.crud.data).toHaveLength(40)
    expect(getOrders).toHaveBeenCalledTimes(2)
  })

  test('does not restore an old vertical position after refreshing an inactive cached list', async() => {
    const router = await mountCachedPage()
    const body = vm.getTableScrollContainer()
    body.scrollTop = 300
    router.push('/other')
    await flush()
    getOrders.mockResolvedValueOnce({ content: orders(100), totalElements: 60 })
    vm.crud.refresh()
    await flush()
    router.push('/orders')
    await flush()
    expect(body.scrollTop).toBe(0)
    expect(vm.crud.data[0].id).toBe(100)
    expect(getOrders).toHaveBeenCalledTimes(2)
  })

  test('keeps real Element UI fixed columns and the horizontal slider aligned on return', async() => {
    window.localStorage.setItem('customer-order-fixed-column-count', '2')
    try {
      const router = await mountCachedPage(true)
      const table = vm.$refs.table
      const body = table.bodyWrapper
      Object.defineProperties(body, {
        scrollHeight: { configurable: true, value: 1600 },
        clientHeight: { configurable: true, value: 400 },
        scrollWidth: { configurable: true, value: 1200 },
        clientWidth: { configurable: true, value: 600 }
      })
      body.scrollTop = 300
      body.scrollLeft = 240
      router.push('/other')
      await flush()
      body.scrollTop = 0
      body.scrollLeft = 0
      router.push('/orders')
      await flush()
      expect(body.scrollTop).toBe(300)
      expect(body.scrollLeft).toBe(240)
      expect(table.$refs.headerWrapper.scrollLeft).toBe(240)
      expect(table.$refs.fixedBodyWrapper.scrollTop).toBe(300)
      expect(vm.$refs.horizontalScroll.scrollPosition).toBe(240)
      expect(vm.$refs.horizontalScroll.$refs.range.value).toBe('240')
      expect(getOrders).toHaveBeenCalledTimes(1)
    } finally {
      window.localStorage.removeItem('customer-order-fixed-column-count')
    }
  })

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

  test('restores fixed columns, adapts to viewport width and focuses visible inline editors', async() => {
    window.localStorage.setItem('customer-order-fixed-column-count', '2')
    const focus = jest.spyOn(HTMLInputElement.prototype, 'focus')
    getOrders.mockResolvedValueOnce({ content: [{ ...orders(1, 1)[0], phone: '13800000000', specialRequirements: '少盐' }], totalElements: 1 })
    try {
      await mountPage(true)
      const table = vm.$refs.table
      const fixedKeys = () => table.store.states.fixedColumns.map(column => column.type === 'selection' ? 'selection' : column.property || column.columnKey)
      expect(fixedKeys()).toEqual(['selection', 'phone', 'addresses'])
      table.toggleRowSelection(vm.crud.data[0], true)
      await wrapper.setData({ tableWidth: 360 })
      await flush()
      expect(fixedKeys()).toEqual(['selection', 'phone'])
      expect(vm.crud.selections.map(row => row.id)).toEqual([1])
      await wrapper.setData({ tableWidth: 900 })
      await flush()
      expect(fixedKeys()).toEqual(['selection', 'phone', 'addresses'])
      expect(window.localStorage.getItem('customer-order-fixed-column-count')).toBe('2')
      vm.beginInlineEdit(vm.crud.data[0], 'phone')
      await flush()
      expect(focus).toHaveBeenCalled()
      expect(focus.mock.instances[focus.mock.instances.length - 1].closest('td.is-hidden')).toBeNull()
      expect(focus.mock.instances[focus.mock.instances.length - 1].closest('.el-table__fixed')).not.toBeNull()
      vm.beginInlineEdit(vm.crud.data[0], 'specialRequirements')
      await flush()
      expect(focus.mock.instances[focus.mock.instances.length - 1].closest('td.is-hidden')).toBeNull()
      expect(focus.mock.instances[focus.mock.instances.length - 1].closest('.el-table__body-wrapper')).not.toBeNull()
    } finally {
      focus.mockRestore()
      window.localStorage.removeItem('customer-order-fixed-column-count')
    }
  })

  test('shows only month and day for deal time without changing the order data', async() => {
    const dealTime = '2026-09-30T08:45:00'
    getOrders.mockResolvedValueOnce({ content: [
      { ...orders(1, 1)[0], dealTime },
      { ...orders(2, 1)[0], dealTime: null }
    ], totalElements: 2 })
    await mountPage(true)
    const index = vm.$refs.table.store.states.columns.findIndex(column => column.property === 'dealTime')
    const rows = wrapper.findAll('.el-table__body-wrapper .el-table__body tbody tr')
    expect(rows.at(0).findAll('td').at(index).text()).toBe('09-30')
    expect(rows.at(1).findAll('td').at(index).text()).toBe('-')
    expect(vm.crud.data[0].dealTime).toBe(dealTime)
  })

  test('renders full long text and edits completed orders through the real table', async() => {
    const specialRequirements = '特殊要求'.repeat(40) + '\n保留第二行'
    const medicalRequirements = '基本情况'.repeat(40) + '\n保留第二行'
    const completed = { ...orders(1, 1)[0], status: 2, specialRequirements, medicalRequirements }
    getOrders.mockResolvedValueOnce({ content: [completed], totalElements: 1 })
    await mountPage(true)
    const columns = vm.$refs.table.store.states.columns
    const cells = wrapper.findAll('.el-table__body-wrapper .el-table__body tbody tr:first-child td')
    const specialIndex = columns.findIndex(column => column.property === 'specialRequirements')
    const medicalIndex = columns.findIndex(column => column.property === 'medicalRequirements')
    expect(columns[specialIndex].showOverflowTooltip).toBeFalsy()
    expect(columns[medicalIndex].showOverflowTooltip).toBeFalsy()
    expect(cells.at(specialIndex).find('.multiline-cell').text()).toBe(specialRequirements)
    expect(cells.at(medicalIndex).find('.multiline-cell').text()).toBe(medicalRequirements)
    await cells.at(specialIndex).find('.inline-value').trigger('click')
    await cells.at(specialIndex).find('input').setValue('修改后的特殊要求')
    getOrders.mockResolvedValueOnce({ content: [{ ...completed, specialRequirements: '修改后的特殊要求' }], totalElements: 1 })
    await cells.at(specialIndex).find('input').trigger('blur')
    await flush()
    expect(updateInline).toHaveBeenCalledWith(1, {
      field: 'specialRequirements', value: '修改后的特殊要求', expectedValue: specialRequirements
    })
    expect(vm.crud.data[0].status).toBe(2)
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
