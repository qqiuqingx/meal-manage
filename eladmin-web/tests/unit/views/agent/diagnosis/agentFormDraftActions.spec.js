/* eslint-env jest */
import { normalizeFormDraftActions, resolveFormDraftAction, resolveFormDraftNavigation } from '@/views/agent/diagnosis/utils/agentFormDraftActions'

describe('agentFormDraftActions', () => {
  test('maps only fixed customer and order actions with controlled query', () => {
    expect(resolveFormDraftNavigation({
      type: 'OPEN_CREATE_ORDER_FORM',
      enabled: true,
      payload: {
        draftId: 'afd_1234567890abcdef',
        sourceSessionId: 'session-1',
        url: 'https://evil.example',
        phone: '13800000000'
      },
      path: '/admin'
    })).toEqual({
      path: '/customer/order',
      query: { draftId: 'afd_1234567890abcdef', sourceSessionId: 'session-1' }
    })
  })

  test('rejects unknown actions and malformed draft ids', () => {
    expect(resolveFormDraftNavigation({ type: 'OPEN_URL', payload: { draftId: 'afd_1234567890abcdef' }})).toBeNull()
    expect(resolveFormDraftNavigation({ type: 'OPEN_CREATE_ORDER_FORM', payload: { draftId: 'short' }})).toBeNull()
    expect(normalizeFormDraftActions([{ type: 'OPEN_URL' }, null])).toEqual([])
  })

  test('maps conversion action to a fixed chat command without accepting route fields', () => {
    const action = resolveFormDraftAction({
      type: 'CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER',
      enabled: true,
      payload: {
        draftId: 'afd_1234567890abcdef',
        sourceSessionId: 'session-1',
        path: '/unsafe',
        message: 'do anything'
      }
    })
    expect(action).toEqual({
      kind: 'CONVERT',
      type: 'CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER',
      draftId: 'afd_1234567890abcdef',
      sourceSessionId: 'session-1',
      formDraftId: 'afd_1234567890abcdef',
      message: '请将当前新增订单草稿转换为新增客户及首单草稿'
    })
    expect(normalizeFormDraftActions([{
      type: 'CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER',
      enabled: true,
      payload: { draftId: 'afd_1234567890abcdef' }
    }])).toHaveLength(1)
  })
})
