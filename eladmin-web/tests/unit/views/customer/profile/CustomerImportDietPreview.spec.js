/* eslint-env jest */
import { createLocalVue, mount } from '@vue/test-utils'
import { Button, Popover } from 'element-ui'
import CustomerImportDietPreview from '@/views/customer/profile/CustomerImportDietPreview.vue'

const localVue = createLocalVue()
localVue.component('el-button', Button)
localVue.component('el-popover', Popover)

const optionLabel = item => `${item.type} · ${item.name}`

describe('customer import diet summary', () => {
  test('keeps long raw text and every candidate in click-to-open details', async() => {
    const draft = {
      customerCode: 'F1596',
      medicalRequirements: '医嘱第一行\n医嘱第二行',
      postoperativeInfo: '术后三个月',
      dealTimeSource: '6.11',
      dietaryRestrictionsRaw: ['干净，卫生，新鲜\n过敏食物：芒果菠萝'],
      dietMatches: [
        {
          sourceKey: 'DIET:4:6:0', sourceRow: 4, side: 'DIETARY_RESTRICTIONS',
          rawText: '芒果', lookupText: '芒果', status: 'MULTI',
          selectedItems: [{ type: 'DISH', id: 1, name: '芒果' }, { type: 'INGREDIENT', id: 2, name: '芒果' }]
        },
        {
          sourceKey: 'DIET:4:6:1', sourceRow: 4, side: 'DIETARY_RESTRICTIONS',
          rawText: '菠萝', lookupText: '菠萝', status: 'UNMATCHED', selectedItems: []
        }
      ]
    }
    const wrapper = mount(CustomerImportDietPreview, { localVue, propsData: { draft, dietOnly: true, optionLabel }})
    const popover = wrapper.find(Popover)

    expect(popover.vm.showPopper).toBe(false)
    expect(wrapper.find('.import-diet-preview__counts').text()).toContain('同名多对象 1')
    expect(wrapper.find('.import-diet-preview__counts').text()).toContain('未匹配 1')
    expect(wrapper.findAll('.import-diet-preview__brief').at(1).text()).toBe('禁忌：芒果')

    wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()

    expect(popover.vm.showPopper).toBe(true)
    const details = wrapper.find('.import-diet-preview__details').text()
    expect(details).toContain(draft.dietaryRestrictionsRaw[0])
    expect(details).toContain('医嘱第一行\n医嘱第二行')
    expect(details).toContain('术后三个月')
    expect(details).toContain('不修改历史订单')
    expect(details).toContain('禁忌 第4行')
    expect(details).toContain('DISH · 芒果')
    expect(details).toContain('INGREDIENT · 芒果')
    expect(details).toContain('未匹配到字典对象，完整原文仍会保存')
    wrapper.destroy()
  })

  test('summarizes many matches without adding inline detail rows', () => {
    const matches = Array.from({ length: 80 }, (_, index) => ({
      sourceKey: `DIET:4:6:${index}`, sourceRow: 4, side: 'DIETARY_RESTRICTIONS',
      rawText: `食材${index}`, lookupText: `食材${index}`, status: 'UNIQUE',
      selectedItems: [{ type: 'INGREDIENT', id: index, name: `食材${index}` }]
    }))
    const wrapper = mount(CustomerImportDietPreview, {
      localVue, propsData: { draft: { dietMatches: matches }, optionLabel }
    })

    expect(wrapper.find('.import-diet-preview__counts').text()).toContain('唯一匹配 80')
    expect(wrapper.findAll('.import-diet-preview__brief')).toHaveLength(2)
    expect(wrapper.find('.import-diet-preview__brief[title="—"]').exists()).toBe(true)
    expect(wrapper.find('.import-diet-preview__details').findAll('.import-diet-preview__match')).toHaveLength(80)
    expect(wrapper.find(Popover).vm.showPopper).toBe(false)
    wrapper.destroy()
  })

  test('keeps medical and postoperative details when D/F are empty', async() => {
    const wrapper = mount(CustomerImportDietPreview, {
      localVue,
      propsData: {
        draft: { medicalRequirements: '医嘱', postoperativeInfo: '术后', dealTimeAtConfirmation: true },
        optionLabel
      }
    })
    expect(wrapper.text()).toContain('想吃和禁忌原文为空，无需匹配')
    wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.import-diet-preview__details').text()).toContain('确认时填入')
    expect(wrapper.find('.import-diet-preview__details').text()).toContain('术后')
    wrapper.destroy()
  })
})
