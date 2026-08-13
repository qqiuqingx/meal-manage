import { shallowMount } from '@vue/test-utils'
import AgentFormDraftCard from '@/views/agent/diagnosis/components/AgentFormDraftCard.vue'

describe('AgentFormDraftCard', () => {
  const stubs = { 'el-tag': true, 'el-button': true }
  const ready = {
    draftId: 'afd_1234567890abcdef',
    type: 'CREATE_CUSTOMER_WITH_ORDER',
    status: 'READY',
    revision: 2,
    recognizedFields: ['customer.customerName'],
    missingFields: ['customer.phone'],
    warnings: [{ code: 'FIELD_REVIEW_REQUIRED', message: '部分字段需要人工复核' }]
  }
  const actions = [{
    type: 'OPEN_CREATE_CUSTOMER_WITH_ORDER_FORM',
    label: '去新建客户',
    enabled: true,
    payload: { draftId: 'afd_1234567890abcdef' }
  }]

  test('shows safe ready summary and emits fixed action', () => {
    const wrapper = shallowMount(AgentFormDraftCard, { stubs, propsData: { summary: ready, actions }})
    expect(wrapper.text()).toContain('新增客户及首单草稿')
    expect(wrapper.text()).toContain('手机号')
    expect(wrapper.findAll('el-button-stub').length).toBe(1)
    wrapper.find('el-button-stub').vm.$emit('click')
    expect(wrapper.emitted().action[0][0].type).toBe('OPEN_CREATE_CUSTOMER_WITH_ORDER_FORM')
  })

  test('editable and archived drafts cannot navigate', () => {
    const editable = shallowMount(AgentFormDraftCard, {
      stubs,
      propsData: { summary: { ...ready, status: 'EDITABLE' }, actions }
    })
    expect(editable.findAll('el-button-stub').length).toBe(0)
    expect(editable.text()).toContain('继续在对话中补充')

    const archived = shallowMount(AgentFormDraftCard, { stubs, propsData: { summary: ready, actions, archived: true }})
    expect(archived.find('el-button-stub').attributes('disabled')).toBe('true')
  })

  test('shows conversion action only for editable order drafts', () => {
    const conversion = {
      type: 'CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER',
      label: '转为新建客户',
      enabled: true,
      payload: { draftId: 'afd_1234567890abcdef' }
    }
    const order = shallowMount(AgentFormDraftCard, {
      stubs,
      propsData: {
        summary: { ...ready, type: 'CREATE_ORDER', status: 'EDITABLE' },
        actions: [conversion]
      }
    })
    expect(order.findAll('el-button-stub').length).toBe(1)
    expect(order.text()).toContain('转为新建客户')

    const customer = shallowMount(AgentFormDraftCard, {
      stubs,
      propsData: { summary: { ...ready, status: 'EDITABLE' }, actions: [conversion] }
    })
    expect(customer.findAll('el-button-stub').length).toBe(0)
  })
})
