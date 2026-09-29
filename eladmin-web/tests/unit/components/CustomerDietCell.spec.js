/* eslint-env jest */
import { shallowMount } from '@vue/test-utils'
import CustomerDietCell from '@/components/CustomerDietCell.vue'

describe('CustomerDietCell', () => {
  test('shows complete raw blocks and matched objects directly', () => {
    const wrapper = shallowMount(CustomerDietCell, {
      propsData: {
        raw: ['不吃花生，', '也不能吃虾\n少量鱼可以'],
        items: [{ type: 'INGREDIENT', id: 3, name: '花生' }, { type: 'DISH_TAG', id: 8, name: '虾类' }]
      }
    })

    expect(wrapper.text()).toContain('不吃花生，')
    expect(wrapper.text()).toContain('也不能吃虾\n少量鱼可以')
    expect(wrapper.text().replace(/\s+/g, '')).toContain('配料：花生、菜品标签：虾类')
    wrapper.destroy()
  })

  test('shows placeholders when the customer has no diet data', () => {
    const wrapper = shallowMount(CustomerDietCell, { propsData: { raw: null, items: null }})
    expect(wrapper.text()).toContain('原文')
    expect(wrapper.text()).toContain('已确认对象')
    expect(wrapper.text().match(/—/g)).toHaveLength(2)
    wrapper.destroy()
  })
})
