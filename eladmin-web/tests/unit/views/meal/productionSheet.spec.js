/* eslint-env jest */
import { shallowMount } from '@vue/test-utils'
import ProductionSheet from '@/views/meal/productionSheet/index.vue'

jest.mock('@/api/mealPlan', () => ({
  getMealPlanList: jest.fn(),
  getMealPlanFullDetail: jest.fn()
}))

jest.mock('@/utils/calendar', () => ({
  MealTypeName: { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }
}))

function buildWrapper() {
  return shallowMount(ProductionSheet, {
    mocks: { $route: { query: {} }},
    directives: { loading: {}},
    stubs: { 'el-date-picker': true, 'el-select': true, 'el-option': true, 'el-button': true, 'el-divider': true, 'el-tag': true }
  })
}

test('production sheet places first meal customers before normal customers', async() => {
  const wrapper = buildWrapper()
  wrapper.setData({
    planData: {
      mealPlan: { id: 1, mealType: 'DINNER', recordDate: '2026-05-06', generateTime: '2026-05-06 18:00:00', status: 'SUCCESS' },
      totalCustomers: 3,
      successCount: 3,
      failCount: 0,
      customers: [
        { id: 1, customerCode: 'C003', customerName: '甲', firstMealOfOrder: false, items: [] },
        { id: 2, customerCode: 'C001', customerName: '乙', firstMealOfOrder: true, items: [] },
        { id: 3, customerCode: 'C002', customerName: '丙', firstMealOfOrder: true, items: [] }
      ]
    }
  })

  await wrapper.vm.$nextTick()

  expect(wrapper.vm.allCustomers.map(item => item.customerCode)).toEqual(['C001', 'C002', 'C003'])
  expect(wrapper.findAll('.code-first-badge').at(0).text()).toBe('首')
  expect(wrapper.findAll('.code-cell').at(0).classes()).not.toContain('code-cell--first')

  wrapper.destroy()
})
