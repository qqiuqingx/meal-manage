/* eslint-env jest */
import { shallowMount } from '@vue/test-utils'
import CustomerDietInfo from '@/components/CustomerDietInfo.vue'

describe('CustomerDietInfo', () => {
  function mountInfo(customer) {
    return shallowMount(CustomerDietInfo, {
      propsData: { customer },
      stubs: {
        'el-collapse': { template: '<div><slot /></div>' },
        'el-collapse-item': { template: '<section><header><slot name="title" /></header><slot /></section>' },
        'el-tag': { template: '<span class="el-tag"><slot /></span>' }
      }
    })
  }

  test('shows shared fields in the agreed order and keeps raw blocks separate from confirmed objects', () => {
    const wrapper = mountInfo({
      medicalRequirements: '少盐',
      dishRequirementsRaw: ['不吃香菜，芹菜'],
      dishRequirements: [{ type: 'INGREDIENT', id: 1, name: '香菜' }],
      dietaryRestrictionsRaw: ['海鲜（除鲈鱼外）'],
      dietaryRestrictions: [{ type: 'DISH_TAG', id: 2, name: '海鲜' }],
      postoperativeInfo: '4个月'
    })

    const text = wrapper.text()
    expect(text.indexOf('医嘱')).toBeLessThan(text.indexOf('菜品特殊要求'))
    expect(text.indexOf('菜品特殊要求')).toBeLessThan(text.indexOf('禁忌食物'))
    expect(text).toContain('原文')
    expect(text).toContain('已确认对象')
    expect(text).toContain('不吃香菜，芹菜')
    expect(text).toContain('配料：香菜')
    expect(text).toContain('菜品标签：海鲜')
    expect(text).toContain('4个月')
    wrapper.destroy()
  })

  test('shows placeholders for empty shared fields', () => {
    const wrapper = mountInfo({})
    expect(wrapper.text().match(/—/g).length).toBeGreaterThanOrEqual(4)
    wrapper.destroy()
  })
})
