/* eslint-env jest */
import { shallowMount } from '@vue/test-utils'
import ScheduleRecord from '@/views/meal/scheduleRecord/index.vue'
import { getMealPlanCustomers, getMealPlanCustomerAddresses } from '@/api/mealPlan'
import fs from 'fs'
import path from 'path'

jest.mock('@/api/mealPlan', () => ({
  getMealPlanList: jest.fn(),
  getMealPlanFullDetail: jest.fn(),
  getMealPlanCustomers: jest.fn(),
  generateMealPlan: jest.fn(),
  delMealPlan: jest.fn(),
  delMealPlanCustomers: jest.fn(),
  getMealPlanCustomerAddresses: jest.fn(),
  getManualReplaces: jest.fn(),
  saveManualReplaces: jest.fn()
}))

jest.mock('@/api/customer/profile', () => ({
  getProfiles: jest.fn()
}))

jest.mock('@/api/dish', () => ({
  queryDishes: jest.fn()
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
      'el-image': true,
      'el-pagination': true,
      'el-tooltip': {
        template: '<div class="el-tooltip" :data-content="content"><slot /></div>',
        props: ['content', 'placement', 'effect']
      }
    }
  })
}

describe('scheduleRecord special requirements display', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('customer management dialog uses customer code for the customer column', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../../src/views/meal/scheduleRecord/index.vue'), 'utf8')

    expect(source).toContain('<el-table-column label="客户编号" prop="customerCode" align="center" min-width="120">')
    expect(source).not.toContain('<el-table-column label="客户" prop="customerName" align="center" min-width="120"')
  })

  test('customer detail button and custom menu column are present in the dialog', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../../src/views/meal/scheduleRecord/index.vue'), 'utf8')

    expect(source).toContain('>客户详情</el-button>')
    expect(source).toContain('<el-dialog title="客户详情" :visible.sync="addressDialog.visible" width="980px">')
    expect(source).toContain('<el-table-column label="自定义菜单" align="center" width="100">')
    expect(source).toContain('@current-change="handleCustomerDialogPageChange"')
    expect(source).toContain('@current-change="handleAddressDialogPageChange"')
  })

  test('customer management dialog fetches paged records', async() => {
    getMealPlanCustomers.mockResolvedValueOnce({
      content: [{ id: 101, customerCode: 'A001' }],
      totalElements: 50
    })

    const wrapper = buildWrapper()
    wrapper.setData({
      customerDialog: {
        ...wrapper.vm.customerDialog,
        currentRecord: { id: 88 },
        page: 2,
        size: 20
      }
    })

    await wrapper.vm.fetchCustomerList()

    expect(getMealPlanCustomers).toHaveBeenCalledWith(88, { page: 2, size: 20 })
    expect(wrapper.vm.customerDialog.list).toEqual([{ id: 101, customerCode: 'A001' }])
    expect(wrapper.vm.customerDialog.total).toBe(50)

    wrapper.destroy()
  })

  test('customer detail dialog fetches paged records', async() => {
    getMealPlanCustomerAddresses.mockResolvedValueOnce({
      content: [{ customerId: 1, customerCode: 'A001' }],
      totalElements: 25
    })

    const wrapper = buildWrapper()
    wrapper.setData({
      planData: {
        mealPlan: { id: 66 }
      },
      addressDialog: {
        ...wrapper.vm.addressDialog,
        page: 3,
        size: 10
      }
    })

    await wrapper.vm.fetchAddressDialogList()

    expect(getMealPlanCustomerAddresses).toHaveBeenCalledWith(66, { page: 3, size: 10 })
    expect(wrapper.vm.addressDialog.list).toEqual([{ customerId: 1, customerCode: 'A001' }])
    expect(wrapper.vm.addressDialog.total).toBe(25)

    wrapper.destroy()
  })

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

  test('production date badge is shown together with first meal badge', async() => {
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
          firstMealOfOrder: true,
          nearProductionDate: true,
          productionDateDiffDays: 2,
          items: []
        }]
      }
    })

    await wrapper.vm.$nextTick()

    expect(wrapper.find('.code-first-badge').text()).toBe('首')
    expect(wrapper.find('.code-production-badge').text()).toBe('产')
    expect(wrapper.findAll('.el-tooltip').at(0).attributes('data-content')).toBe('生产后第2天')

    wrapper.destroy()
  })

  test('production date badge tooltip shows production day for same-day records', () => {
    const wrapper = buildWrapper()

    expect(wrapper.vm.getProductionDateBadgeTip({ productionDateDiffDays: 0 })).toBe('生产当天')
    expect(wrapper.vm.getProductionDateBadgeTip({ productionDateDiffDays: 3 })).toBe('生产后第3天')

    wrapper.destroy()
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

  test('customers without soup are highlighted instead of showing a 无汤 tag', () => {
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
          customerCode: 'A170',
          customerName: '张三',
          includeSoup: 0,
          items: [{
            dishType: 'MAIN',
            dishName: '凤梨牛肉粒',
            isReplaced: false,
            isAllergyFiltered: false
          }]
        }]
      }
    })

    return wrapper.vm.$nextTick().then(() => {
      expect(wrapper.text()).not.toContain('无汤')
      expect(wrapper.find('.code-text').classes()).toContain('code-text--soup-missing')

      wrapper.destroy()
    })
  })

  test('customers with soup are not circled when they only have replacement items', () => {
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
          customerCode: 'A003',
          customerName: '张三',
          includeSoup: 1,
          items: [{
            dishType: 'SOUP',
            dishName: '白萝卜猪腱子汤',
            isReplaced: false,
            isAllergyFiltered: false
          }, {
            dishType: 'SIDE',
            dishName: '秋葵虾滑',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }]
      }
    })

    return wrapper.vm.$nextTick().then(() => {
      const cell = wrapper.find('.code-cell')
      expect(cell.classes()).not.toContain('code-cell--replaced')
      expect(wrapper.find('.code-text').classes()).not.toContain('code-text--soup-missing')

      wrapper.destroy()
    })
  })

  test('replaced rice items are shown in the replacement section', () => {
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
          items: [{
            dishType: 'RICE',
            dishName: '白米饭',
            originalDishName: '普通杂粮米饭',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }, {
          id: 102,
          customerCode: 'B5600',
          customerName: '李四',
          items: [{
            dishType: 'SIDE',
            dishName: '秋葵虾滑',
            originalDishName: '青椒炒蛋',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }]
      }
    })

    return wrapper.vm.$nextTick().then(() => {
      expect(wrapper.vm.replacedDishes).toHaveLength(2)
      expect(wrapper.vm.replacedDishes.map(item => item.dishName)).toEqual(['白米饭', '秋葵虾滑'])

      wrapper.destroy()
    })
  })

  test('multiple replaced rice rows are compacted into one display row', () => {
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
        totalCustomers: 4,
        successCount: 4,
        failCount: 0,
        customers: [{
          id: 101,
          customerCode: 'A008',
          customerName: '张三',
          items: [{
            dishType: 'RICE',
            dishName: '普通杂粮米饭',
            originalDishName: '山姆有机杂粮米6',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }, {
          id: 102,
          customerCode: 'B325',
          customerName: '李四',
          items: [{
            dishType: 'RICE',
            dishName: '三色糙米',
            originalDishName: '山姆有机杂粮米6',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }, {
          id: 103,
          customerCode: 'F877',
          customerName: '王五',
          items: [{
            dishType: 'RICE',
            dishName: '白米饭',
            originalDishName: '山姆有机杂粮米6',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }, {
          id: 104,
          customerCode: 'A170',
          customerName: '赵六',
          items: [{
            dishType: 'SIDE',
            dishName: '秋葵虾滑',
            originalDishName: '青椒炒蛋',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }]
      }
    })

    return wrapper.vm.$nextTick().then(() => {
      expect(wrapper.vm.autoReplaceRows).toHaveLength(2)
      const riceRow = wrapper.vm.autoReplaceRows.find(item => item.compactGroup)
      expect(riceRow.dishType).toBe('RICE')
      expect(riceRow.items.map(item => item.dishName)).toEqual(['普通杂粮米饭', '三色糙米', '白米饭'])
      expect(riceRow.items.map(item => item.codeSnippet)).toEqual(['A008', 'B325', 'F877'])

      wrapper.destroy()
    })
  })

  test('rice row code details group customers by replaced rice name', () => {
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
        totalCustomers: 4,
        successCount: 4,
        failCount: 0,
        customers: [{
          id: 101,
          customerCode: 'A170',
          customerName: '张三',
          items: [{
            dishType: 'RICE',
            dishName: '白米饭',
            originalDishName: '普通杂粮米饭',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }, {
          id: 102,
          customerCode: 'A003',
          customerName: '李四',
          items: [{
            dishType: 'RICE',
            dishName: '白米饭',
            originalDishName: '普通杂粮米饭',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }, {
          id: 103,
          customerCode: 'B5600',
          customerName: '王五',
          items: [{
            dishType: 'RICE',
            dishName: '三色糙米',
            originalDishName: '普通杂粮米饭',
            isReplaced: true,
            isAllergyFiltered: false
          }]
        }, {
          id: 104,
          customerCode: 'A001',
          customerName: '赵六',
          items: [{
            dishType: 'RICE',
            dishName: '普通杂粮米饭',
            isReplaced: false,
            isAllergyFiltered: false
          }]
        }]
      }
    })

    return wrapper.vm.$nextTick().then(() => {
      const riceDish = wrapper.vm.regularDishes.find(dish => dish.dishName === '普通杂粮米饭')
      expect(riceDish.count).toBe(1)
      expect(riceDish.codeSnippet).toBe('A170, A003(白米饭), B5600(三色糙米)')

      wrapper.destroy()
    })
  })

  test('special requirement tags include add and remove dish instructions', async() => {
    const wrapper = buildWrapper()

    wrapper.setData({
      planData: {
        mealPlan: { id: 1, mealType: 'LUNCH', recordDate: '2026-05-06', generateTime: '2026-05-06 12:00:00', status: 'SUCCESS' },
        totalCustomers: 1,
        successCount: 1,
        failCount: 0,
        customers: [{
          id: 101,
          customerCode: 'A170',
          customerName: '张三',
          includeSoup: 1,
          specialRequirements: '不要米饭，加主菜，不要副菜，加素菜',
          items: []
        }]
      }
    })

    await wrapper.vm.$nextTick()

    expect(wrapper.vm.getSpecialRequirementTags(wrapper.vm.allCustomers[0])).toEqual([
      '不要米饭',
      '加主菜',
      '不要副菜',
      '加素菜'
    ])
    expect(wrapper.text()).toContain('不要米饭')
    expect(wrapper.text()).toContain('加主菜')
    expect(wrapper.text()).toContain('不要副菜')
    expect(wrapper.text()).toContain('加素菜')

    wrapper.destroy()
  })

  test('special requirement instructions are shown in matching menu summary rows', async() => {
    const wrapper = buildWrapper()

    wrapper.setData({
      planData: {
        mealPlan: { id: 1, mealType: 'LUNCH', recordDate: '2026-05-06', generateTime: '2026-05-06 12:00:00', status: 'SUCCESS' },
        totalCustomers: 2,
        successCount: 2,
        failCount: 0,
        customers: [{
          id: 101,
          customerCode: 'A170',
          customerName: '张三',
          includeSoup: 1,
          specialRequirements: '不要米饭，加主菜，不要副菜，加素菜',
          items: [{
            dishType: 'MAIN',
            dishName: '凤梨牛肉粒',
            isReplaced: false,
            isAllergyFiltered: false
          }, {
            dishType: 'SIDE',
            dishName: '秋葵虾滑',
            isReplaced: false,
            isAllergyFiltered: false
          }, {
            dishType: 'VEGETABLE',
            dishName: '上海青',
            isReplaced: false,
            isAllergyFiltered: false
          }, {
            dishType: 'RICE',
            dishName: '白米饭',
            isReplaced: false,
            isAllergyFiltered: false
          }]
        }, {
          id: 102,
          customerCode: 'B5600',
          customerName: '李四',
          includeSoup: 1,
          specialRequirements: '加 2 份米饭、不要主菜、加副菜、不要素菜',
          items: [{
            dishType: 'MAIN',
            dishName: '凤梨牛肉粒',
            isReplaced: false,
            isAllergyFiltered: false
          }, {
            dishType: 'SIDE',
            dishName: '秋葵虾滑',
            isReplaced: false,
            isAllergyFiltered: false
          }, {
            dishType: 'VEGETABLE',
            dishName: '上海青',
            isReplaced: false,
            isAllergyFiltered: false
          }, {
            dishType: 'RICE',
            dishName: '白米饭',
            isReplaced: false,
            isAllergyFiltered: false
          }]
        }]
      }
    })

    await wrapper.vm.$nextTick()

    const mainDish = wrapper.vm.regularDishes.find(dish => dish.dishType === 'MAIN')
    const sideDish = wrapper.vm.regularDishes.find(dish => dish.dishType === 'SIDE')
    const vegDish = wrapper.vm.regularDishes.find(dish => dish.dishType === 'VEGETABLE')
    const riceDish = wrapper.vm.regularDishes.find(dish => dish.dishType === 'RICE')

    expect(mainDish.codeSnippet).toBe('A170(加主菜), B5600(不要主菜)')
    expect(sideDish.codeSnippet).toBe('A170(不要副菜), B5600(加副菜)')
    expect(vegDish.codeSnippet).toBe('A170(加素菜), B5600(不要素菜)')
    expect(riceDish.codeSnippet).toBe('A170(不要米饭), B5600(加 2 份米饭)')

    wrapper.destroy()
  })

  test('first meal customers are placed before normal customers and keep group order', async() => {
    const wrapper = buildWrapper()

    wrapper.setData({
      planData: {
        mealPlan: { id: 1, mealType: 'LUNCH', recordDate: '2026-05-06', generateTime: '2026-05-06 12:00:00', status: 'SUCCESS' },
        totalCustomers: 3,
        successCount: 3,
        failCount: 0,
        customers: [{
          id: 101, customerCode: 'A003', customerName: '张三', firstMealOfOrder: false, includeSoup: 1, items: []
        }, {
          id: 102, customerCode: 'A001', customerName: '李四', firstMealOfOrder: true, includeSoup: 1, items: []
        }, {
          id: 103, customerCode: 'A002', customerName: '王五', firstMealOfOrder: true, includeSoup: 0, items: []
        }]
      }
    })

    await wrapper.vm.$nextTick()

    expect(wrapper.vm.allCustomers.map(item => item.customerCode)).toEqual(['A001', 'A002', 'A003'])
    expect(wrapper.findAll('.code-first-badge').at(0).text()).toBe('首')
    expect(wrapper.findAll('.code-cell').at(0).classes()).not.toContain('code-cell--first')
    expect(wrapper.findAll('.code-text').at(1).classes()).toContain('code-text--soup-missing')

    wrapper.destroy()
  })

  test('custom menu image url uses baseApi for relative paths', () => {
    const wrapper = buildWrapper()

    wrapper.setData({
      baseApi: '/prod-api'
    })

    expect(wrapper.vm.getCustomMenuImageUrl('/file/picture/menu.png')).toBe('/prod-api/file/picture/menu.png')
    expect(wrapper.vm.getCustomMenuImageUrl('https://cdn.example.com/menu.png')).toBe('https://cdn.example.com/menu.png')
    expect(wrapper.vm.getCustomMenuImageUrl('')).toBe('')

    wrapper.destroy()
  })
})
