/* eslint-env jest */
import Vue from 'vue'
import ElementUI from 'element-ui'
import { mount } from '@vue/test-utils'
import CustomerMealQuantityGrid from '@/views/customer/mealStats/CustomerMealQuantityGrid.vue'

Vue.use(ElementUI)

const order = {
  orderId: 33,
  orderCode: 'ORD-33',
  status: 1,
  statusLabel: '进行中',
  mealType: 'LUNCH_DINNER',
  lunchDinnerCount: 10,
  availableLunchDinnerCount: 8
}

const lunchCell = {
  orderId: 33,
  date: '2026-09-23',
  mealType: 'LUNCH',
  baseQuantity: 1,
  quantity: 1,
  soupQuantity: null,
  defaultIncludesSoup: true,
  verifiedCount: 0,
  customerExcluded: false,
  orderExcluded: false,
  manualOverride: false
}

describe('CustomerMealQuantityGrid', () => {
  test('sets a stop that applies to this order cell only', async() => {
    const wrapper = mount(CustomerMealQuantityGrid, {
      propsData: { order, cells: [lunchCell], overrides: [], statsMonth: '2026-09', editable: true }
    })

    await wrapper.setData({ editingCell: Object.assign({}, lunchCell) })
    expect(wrapper.findAll('.quantity-cell-editor .el-input-number').length).toBe(1)

    const stopButton = wrapper.findAll('.quantity-cell-editor__actions button').wrappers
      .find(button => button.text() === '订单停餐')
    await stopButton.trigger('click')

    expect(wrapper.emitted('override-upsert')[0][0]).toEqual({
      date: '2026-09-23',
      mealType: 'LUNCH',
      quantity: 0,
      soupQuantity: null,
      remark: ''
    })
    wrapper.destroy()
  })

  test('restore default removes the override instead of setting a quantity', async() => {
    const wrapper = mount(CustomerMealQuantityGrid, {
      propsData: {
        order,
        cells: [{ ...lunchCell, quantity: 2, manualOverride: true }],
        overrides: [{ date: lunchCell.date, mealType: 'LUNCH', quantity: 2, soupQuantity: 1, remark: '加份' }],
        statsMonth: '2026-09',
        editable: true
      }
    })
    await wrapper.setData({ editingCell: { ...lunchCell, quantity: 2, manualOverride: true }})

    const restoreButton = wrapper.findAll('.quantity-cell-editor__actions button').wrappers
      .find(button => button.text() === '恢复默认')
    await restoreButton.trigger('click')

    expect(wrapper.emitted('override-remove')[0][0]).toMatchObject({
      date: '2026-09-23',
      mealType: 'LUNCH',
      baseQuantity: 1
    })
    wrapper.destroy()
  })

  test('shows historical quantities as read-only without claiming meal verification', async() => {
    const historicalLunch = { ...lunchCell, quantity: 2, importedHistory: true }
    const historicalDinner = { ...lunchCell, mealType: 'DINNER', importedHistory: true }
    const wrapper = mount(CustomerMealQuantityGrid, {
      propsData: {
        order,
        cells: [historicalLunch, historicalDinner],
        overrides: [],
        statsMonth: '2026-09',
        editable: true
      }
    })

    const historyButtons = wrapper.findAll('.quantity-cell--history')
    expect(historyButtons.length).toBeGreaterThanOrEqual(2)
    expect(historyButtons.at(0).text()).toContain('2 份')
    expect(historyButtons.at(0).text()).toContain('历史导入（只读）')
    expect(historyButtons.at(0).text()).not.toContain('核 2')
    expect(historyButtons.at(0).attributes('disabled')).toBe('disabled')
    expect(historyButtons.wrappers.every(button => button.attributes('disabled') === 'disabled')).toBe(true)
    await historyButtons.at(0).trigger('click')
    expect(wrapper.find('.quantity-cell-editor').exists()).toBe(false)
    expect(wrapper.emitted('override-upsert')).toBeUndefined()
    wrapper.destroy()
  })

  test('shows archived real progress without allowing quantity edits', () => {
    const wrapper = mount(CustomerMealQuantityGrid, {
      propsData: {
        order, cells: [{ ...lunchCell, quantity: 0, generatedCount: 1, readOnly: true }],
        overrides: [], statsMonth: '2026-09', editable: true
      }
    })
    expect(wrapper.text()).toContain('1 份（已排）')
    expect(wrapper.vm.isEditable(lunchCell.date, 'LUNCH')).toBe(false)
    wrapper.destroy()
  })

  test('keeps customer-wide stop visible and non-editable', () => {
    const wrapper = mount(CustomerMealQuantityGrid, {
      propsData: {
        order,
        cells: [{ ...lunchCell, quantity: 0, customerExcluded: true }],
        overrides: [],
        statsMonth: '2026-09',
        editable: true
      }
    })

    expect(wrapper.text()).toContain('客户统一停餐')
    expect(wrapper.find('.quantity-cell').attributes('disabled')).toBe('disabled')
    wrapper.destroy()
  })
})
