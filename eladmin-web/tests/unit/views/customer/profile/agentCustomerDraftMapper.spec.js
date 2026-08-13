/* eslint-env jest */
import { mapAgentCustomerDraft } from '@/views/customer/profile/utils/agentCustomerDraftMapper'

describe('mapAgentCustomerDraft', () => {
  test('maps every registered customer, first-order and complex association field', () => {
    const result = mapAgentCustomerDraft({
      draftId: 'afd_customer_123456',
      draftType: 'CREATE_CUSTOMER_WITH_ORDER',
      schemaVersion: 'v1',
      status: 'CLAIMED',
      revision: 3,
      sourceSessionId: 'session-1',
      missingFields: ['customer.productionDate'],
      warnings: [{ code: 'FIELD_REVIEW_REQUIRED', message: '部分字段需要人工复核' }],
      customerWithOrderPayload: {
        customer: {
          customerCode: 'A101', customerName: '张三', phone: '13800000000', gestationalWeek: 32,
          allergyTags: ['牛奶'], excludedDishIds: [11], excludedDates: [{ date: '2026-08-20', mealTypes: ['LUNCH'] }],
          medicalRequirements: '少盐', specialRequirements: '放门口', productionDate: '2026-10-01', remark: '客户备注',
          addresses: [{ addressType: 'DEFAULT', addressDetail: '天府大道1号', contactName: '张三', contactPhone: '13800000000' }],
          imageUrl: 'must-not-pass'
        },
        order: {
          parentPackageId: 1, childPackageId: 2, breakfastCount: 10, lunchDinnerCount: 20,
          breakfastPrice: 12.5, lunchDinnerPrice: 28, totalAmount: 685, depositAmount: 100, finalAmount: 585,
          dealTime: '2026-08-12T10:00:00', firstDeliveryTime: '2026-08-13T08:00:00',
          startDate: '2026-08-13', startMealType: 'BREAKFAST', endDate: '2026-09-13', mealType: 'ALL', scheduleMode: 'SCHEDULE',
          deliveryDates: [{ date: '2026-08-13', mealTypes: ['BREAKFAST', 'LUNCH'] }], customerSource: 'WECHAT',
          trialConverted: true, trialOrderId: 99, mainDishCount: 2, sideDishCount: 1, vegCount: 2,
          riceCount: 1, riceType: '白米饭', soupCount: 1, remark: '订单备注',
          replaceRules: [{ sourceDishId: 7, targetDishId: 8, enabled: true, remark: '换菜' }],
          imageUrls: ['must-not-pass']
        }
      }
    }, { addresses: [], orderInfo: { riceCount: 1 }})

    expect(result.draftId).toBe('afd_customer_123456')
    expect(result.revision).toBe(3)
    expect(result.form.customerName).toBe('张三')
    expect(result.form.addresses[0]).toEqual({ addressType: 'DEFAULT', addressDetail: '天府大道1号', contactName: '张三', contactPhone: '13800000000' })
    expect(result.form.orderInfo.deliveryDatesWithMealTypes).toEqual([{ date: '2026-08-13', mealTypes: ['BREAKFAST', 'LUNCH'] }])
    expect(result.form.orderInfo.replaceRules[0]).toEqual({ sourceDishId: 7, targetDishId: 8, enabled: true, remark: '换菜' })
    expect(result.form.orderInfo.trialOrderId).toBe(99)
    expect(result.form.imageUrl).toBeUndefined()
    expect(result.form.orderInfo.imageUrls).toBeUndefined()
    expect(result.missingFields).toEqual(['customer.productionDate'])
  })

  test('rejects unknown type, version, status and missing typed payload', () => {
    const base = { draftType: 'CREATE_CUSTOMER_WITH_ORDER', schemaVersion: 'v1', status: 'CLAIMED', customerWithOrderPayload: {}}
    expect(mapAgentCustomerDraft({ ...base, draftType: 'CREATE_ORDER' }, {})).toBeNull()
    expect(mapAgentCustomerDraft({ ...base, schemaVersion: 'v2' }, {})).toBeNull()
    expect(mapAgentCustomerDraft({ ...base, status: 'SUBMITTED' }, {})).toBeNull()
    expect(mapAgentCustomerDraft({ ...base, customerWithOrderPayload: null }, {})).toBeNull()
  })
})
