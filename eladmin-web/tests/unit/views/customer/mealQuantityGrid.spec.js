/* eslint-env jest */
import Vue from 'vue'
import ElementUI from 'element-ui'
import { mount } from '@vue/test-utils'
import CustomerMealQuantityGrid from '@/views/customer/mealStats/CustomerMealQuantityGrid.vue'

Vue.use(ElementUI)

const lunchCell = {
  orderId: 33,
  date: '2026-09-23',
  mealType: 'LUNCH',
  baseQuantity: 1,
  quantity: 1,
  soupQuantity: null,
  verifiedCount: 0,
  excluded: false
}

describe('CustomerMealQuantityGrid', () => {
  test('shows the quantity editor and excludes the selected lunch', async() => {
    const wrapper = mount(CustomerMealQuantityGrid, {
      propsData: { orders: [], cells: [], statsMonth: '2026-09' }
    })

    await wrapper.setData({ editingCell: Object.assign({}, lunchCell) })
    expect(wrapper.findAll('.quantity-cell-editor .el-input-number').length).toBe(1)
    expect(wrapper.text()).toContain('计划份数')

    const excludeButton = wrapper.findAll('.quantity-cell-editor__actions button').wrappers
      .find(button => button.text() === '排除该餐次')
    await excludeButton.trigger('click')

    expect(wrapper.emitted('cell-change')[0][0]).toMatchObject({
      orderId: 33,
      date: '2026-09-23',
      mealType: 'LUNCH',
      quantity: 0,
      excluded: true
    })
    wrapper.destroy()
  })
})
