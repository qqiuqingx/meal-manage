/* eslint-env jest */
import { mapAgentOrderDraft } from '@/views/customer/order/utils/agentOrderDraftMapper'

describe('mapAgentOrderDraft', () => {
  test('maps the registered order fields and complex controls without server or image fields', () => {
    const result = mapAgentOrderDraft({
      draftId: 'afd_order_123456789', draftType: 'CREATE_ORDER', schemaVersion: 'v1', status: 'CLAIMED',
      revision: 4, sourceSessionId: 'session-2', missingFields: ['startDate'], warnings: [],
      orderPayload: {
        customerId: 7, customerCode: 'A1007', parentPackageId: 1, childPackageId: 2,
        breakfastCount: 5, lunchDinnerCount: 15, breakfastPrice: 12, lunchDinnerPrice: 28,
        totalAmount: 480, depositAmount: 100, finalAmount: 450, dealTime: '2026-08-12T10:00:00',
        firstDeliveryTime: '2026-08-13T08:00:00', startDate: '2026-08-13', startMealType: 'LUNCH',
        endDate: '2026-09-01', mealType: 'LUNCH_DINNER', scheduleMode: 'SCHEDULE',
        deliveryDates: [{ date: '2026-08-13', mealTypes: ['LUNCH', 'DINNER'] }], customerSource: 'WECHAT',
        trialConverted: true, trialOrderId: 91, mainDishCount: 2, sideDishCount: 1, vegCount: 2,
        riceCount: 1, riceType: '白米饭', soupCount: 1, remark: '订单备注',
        replaceRules: [{ sourceDishId: 11, targetDishId: 12, enabled: true, remark: '换菜' }],
        id: 999, orderCode: 'MUST_NOT_PASS', verifiedCount: 20, customMenuImage: '/secret.jpg'
      }
    }, { status: 1, verifiedCount: 0, customMenuImage: null })

    expect(result.draftId).toBe('afd_order_123456789')
    expect(result.form.customerId).toBe(7)
    expect(result.form.status).toBe(1)
    expect(result.form.deliveryDatesWithMealTypes).toEqual([{ date: '2026-08-13', mealTypes: ['LUNCH', 'DINNER'] }])
    expect(result.form.replaceRules[0].sourceDishId).toBe(11)
    expect(result.form.id).toBeUndefined()
    expect(result.form.orderCode).toBeUndefined()
    expect(result.form.verifiedCount).toBe(0)
    expect(result.form.customMenuImage).toBeNull()
  })

  test('rejects drafts without a resolved customer or supported contract', () => {
    const base = { draftType: 'CREATE_ORDER', schemaVersion: 'v1', status: 'CLAIMED', orderPayload: { customerId: 7 }}
    expect(mapAgentOrderDraft({ ...base, orderPayload: {}}, {})).toBeNull()
    expect(mapAgentOrderDraft({ ...base, draftType: 'CREATE_CUSTOMER_WITH_ORDER' }, {})).toBeNull()
    expect(mapAgentOrderDraft({ ...base, schemaVersion: 'v2' }, {})).toBeNull()
    expect(mapAgentOrderDraft({ ...base, status: 'SUBMITTED' }, {})).toBeNull()
  })
})
