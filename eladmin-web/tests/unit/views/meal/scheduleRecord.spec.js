/* eslint-env jest */
import { shallowMount } from '@vue/test-utils'
import ScheduleRecord from '@/views/meal/scheduleRecord/index.vue'

jest.mock('@/api/mealPlan', () => ({
  getMealPlanList: jest.fn(),
  getMealPlanFullDetail: jest.fn(),
  generateMealPlan: jest.fn(),
  delMealPlan: jest.fn(),
  delMealPlanCustomers: jest.fn(),
  getMealPlanCustomerAddresses: jest.fn()
}))

jest.mock('@/api/customer/profile', () => ({
  getProfiles: jest.fn()
}))

jest.mock('@/api/mealVerification', () => ({
  verifyMeal: jest.fn()
}))

jest.mock('@/utils/calendar', () => ({
  MealTypeName: {
    BREAKFAST: '早餐',
    LUNCH: '午餐',
    DINNER: '晚餐'
  }
}))

function buildWrapper() {
  return shallowMount(ScheduleRecord, {
    mocks: {
      $route: { query: {}},
      $message: {
        warning: jest.fn(),
        error: jest.fn(),
        success: jest.fn()
      },
      $confirm: jest.fn(() => Promise.resolve())
    },
    directives: {
      loading: {}
    },
    stubs: {
      'el-date-picker': true,
      'el-select': true,
      'el-option': true,
      'el-button': true,
      'el-divider': true,
      'el-dialog': true,
      'el-form': true,
      'el-form-item': true,
      'el-table': true,
      'el-table-column': true,
      'el-tag': true,
      'el-tooltip': {
        template: '<div class="el-tooltip" :data-content="content"><slot /></div>',
        props: ['content', 'placement', 'effect']
      }
    }
  })
}

describe('scheduleRecord special requirements display', () => {
  test('special requirements are provided by tooltip instead of rendering below the customer code', () => {
    const wrapper = buildWrapper()

    wrapper.setData({
      planData: {
        mealPlan: {
          id: 1,
          mealType: 'LUNCH',
          recordDate: '2026-05-02',
          generateTime: '2026-05-02 12:00:00',
          status: 'SUCCESS'
        },
        totalCustomers: 1,
        successCount: 1,
        failCount: 0,
        customers: [{
          id: 101,
          customerCode: 'C001',
          customerName: '张三',
          specialRequirements: '少盐少油',
          items: []
        }]
      }
    })

    return wrapper.vm.$nextTick().then(() => {
      expect(wrapper.find('.code-text').text()).toBe('C001')
      expect(wrapper.find('.code-remark').exists()).toBe(false)

      const tooltip = wrapper.find('.el-tooltip')
      expect(tooltip.exists()).toBe(true)
      expect(tooltip.attributes('data-content')).toBe('少盐少油')

      wrapper.destroy()
    })
  })

  test('soup row code details include customers without soup', () => {
    const wrapper = buildWrapper()

    wrapper.setData({
      planData: {
        mealPlan: {
          id: 1,
          mealType: 'LUNCH',
          recordDate: '2026-05-02',
          generateTime: '2026-05-02 12:00:00',
          status: 'SUCCESS'
        },
        totalCustomers: 2,
        successCount: 2,
        failCount: 0,
        customers: [{
          id: 101,
          customerCode: 'A170',
          customerName: '张三',
          includeSoup: 0,
          items: [{
            dishType: 'MAIN',
            dishName: '凤梨牛肉粒',
            isReplaced: false,
            isAllergyFiltered: false
          }]
        }, {
          id: 102,
          customerCode: 'B5600',
          customerName: '李四',
          includeSoup: 1,
          items: [{
            dishType: 'SOUP',
            dishName: '白萝卜猪腱子汤',
            isReplaced: false,
            isAllergyFiltered: false
          }]
        }]
      }
    })

    return wrapper.vm.$nextTick().then(() => {
      const soupDish = wrapper.vm.regularDishes.find(dish => dish.dishType === 'SOUP')
      expect(soupDish.count).toBe(1)
      expect(soupDish.codeSnippet).toBe('A170')

      wrapper.destroy()
    })
  })
})
