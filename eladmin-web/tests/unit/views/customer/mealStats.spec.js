/* eslint-env jest */
import Vue from 'vue'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import { createLocalVue, mount } from '@vue/test-utils'
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
localVue.use(Vuex)
localVue.use(ElementUI)
const store = new Vuex.Store({
  getters: {
    baseApi: () => '/api',
    roles: () => ['admin']
  }
})

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

function deferred() {
  let resolve
  const promise = new Promise(resolvePromise => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function row(orderId, values = {}) {
  return {
    orderId,
    customerId: 1,
    orderCode: `ORD-${orderId}`,
    customerCode: 'A001',
    customerName: '张三',
    phone: '13800138000',
    addressText: '地址：示例路',
    specialRequirements: '米饭加量',
    scheduleModeText: '每日',
    mealTypeText: '早餐、午晚餐',
    specification: '主1 / 副1 / 素1',
    soupCount: 1,
    breakfastCount: 5,
    lunchDinnerCount: 10,
    totalCount: 15,
    verifiedCount: 3,
    scheduledCount: 7,
    remainingCount: 8,
    estimatedRemainingCount: 6,
    status: 1,
    statusLabel: '进行中',
    medicalRequirements: '少盐',
    dealTime: '2026-09-20 14:05:00',
    postoperativeInfo: '4个月',
    dishRequirements: [],
    dishRequirementsRaw: [],
    allergyTags: ['花生'],
    dietaryRestrictions: [],
    dietaryRestrictionsRaw: [],
    customMenuImage: '/uploads/menu.png',
    ...values
  }
}

function calendar(orderId, overrides = []) {
  return {
    orderId,
    customerId: 1,
    orderCode: `ORD-${orderId}`,
    customerCode: 'A001',
    customerName: '张三',
    statsMonth: '2026-09',
    status: 1,
    mealType: 'ALL',
    breakfastCount: 1,
    lunchDinnerCount: 2,
    availableBreakfastCount: 1,
    availableLunchDinnerCount: 2,
    defaultIncludesSoup: true,
    editable: true,
    readOnlyReason: null,
    revision: `revision-${orderId}`,
    cells: [],
    overrides
  }
}

describe('CustomerMealStats order page', () => {
  let wrapper

  beforeEach(() => {
    getMealStats.mockReset().mockResolvedValue({ content: [], totalElements: 0 })
    getOrderMealCalendar.mockReset()
    saveOrderMealCalendar.mockReset().mockResolvedValue({})
    getDepletionWarnings.mockReset().mockResolvedValue([])
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.destroy()
      wrapper = null
    }
  })

  async function mountPage() {
    wrapper = mount(CustomerMealStats, {
      localVue,
      store,
      stubs: {
        'customer-meal-quantity-grid': true,
        CustomerDietCell: true
      }
    })
    await flushPromises()
    return wrapper
  }

  test('renders the 24 contracted business columns in order before the calendar action', async() => {
    const expected = [
      '手机号', '地址', '客户编号', '客户姓名', '特殊要求', '排餐模式', '餐次', '规格', '含汤',
      '早餐', '午晚', '合计', '核销', '已排餐', '剩余', '预计剩余', '状态', '基本情况',
      '成单时间', '术后天数', '菜品特殊要求', '过敏食物', '禁忌食物', '自定义菜单', '操作'
    ]
    getMealStats.mockResolvedValue({ content: [row(10)], totalElements: 1 })
    const page = await mountPage()
    await Vue.nextTick()

    const header = page.find('.el-table__header-wrapper:not(.el-table__fixed-header-wrapper)')
    const labels = header.findAll('th .cell').wrappers.map(cell => cell.text().trim()).filter(Boolean)
    expect(labels).toEqual(expected)
    expect(page.text()).toContain('餐数为当前订单累计值')
  })

  test('ignores an earlier order calendar response after switching to another order', async() => {
    const firstRequest = deferred()
    const secondRequest = deferred()
    getOrderMealCalendar.mockReturnValueOnce(firstRequest.promise).mockReturnValueOnce(secondRequest.promise)
    const page = await mountPage()
    const first = row(10)
    const second = row(11)

    page.vm.openOrderCalendar(first)
    page.vm.openOrderCalendar(second)
    secondRequest.resolve(calendar(11))
    await flushPromises()
    firstRequest.resolve(calendar(10))
    await flushPromises()

    expect(getOrderMealCalendar).toHaveBeenNthCalledWith(1, 10, page.vm.calendarMonth)
    expect(getOrderMealCalendar).toHaveBeenNthCalledWith(2, 11, page.vm.calendarMonth)
    expect(page.vm.calendarData.orderId).toBe(11)
  })

  test('saves only the selected order and preserves zero quantity in the override snapshot', async() => {
    const page = await mountPage()
    page.vm.selectedRow = row(22)
    page.vm.calendarMonth = '2026-09'
    page.vm.calendarData = calendar(22)
    page.vm.calendarLoaded = true
    page.vm.calendarRevision = 'revision-22'
    page.vm.calendarDraftOverrides = [{
      date: '2026-09-23',
      mealType: 'LUNCH',
      quantity: 0,
      soupQuantity: null,
      remark: '停餐'
    }]
    page.vm.calendarDialogVisible = true

    await page.vm.saveCalendar()

    expect(saveOrderMealCalendar).toHaveBeenCalledWith(22, {
      statsMonth: '2026-09',
      expectedRevision: 'revision-22',
      overrides: [{ date: '2026-09-23', mealType: 'LUNCH', quantity: 0, soupQuantity: null, remark: '停餐' }]
    })
  })
})
