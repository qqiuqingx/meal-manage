/* eslint-env jest */
import {
  KNOWN_CARD_TYPES,
  createLegacyPresentation,
  createSafeLegacyCard,
  mapLegacyCards
} from '@/views/agent/diagnosis/utils/agentPresentationCompatibility'

describe('agentPresentationCompatibility', () => {
  test('covers all known card types with local Chinese descriptors', () => {
    KNOWN_CARD_TYPES.forEach(cardType => {
      const presentation = createLegacyPresentation({ type: cardType, data: {} }, `call-${cardType}`)

      expect(presentation).not.toBe(null)
      expect(presentation.schemaVersion).toBe('v1')
      expect(presentation.sourceToolCallId).toBe(`call-${cardType}`)
      expect(presentation.decisionSource).toBe('SYSTEM')
      expect(presentation.layout).toBe('TABS')
      expect(presentation.title).not.toBe(cardType)
    })
  })

  test('keeps legacy maskedName value while exposing the fixed customerName column', () => {
    const mapped = mapLegacyCards([{
      type: 'SERVICE_CUSTOMER_LIST',
      data: { items: [{ customerCode: 'C1001', maskedName: '历史掩码' }] }
    }])

    expect(mapped.cards[0].sourceToolCallId).toBe('legacy-card-1')
    expect(mapped.cards[0].data.items[0].maskedName).toBe('历史掩码')
    expect(mapped.cards[0].data.items[0].customerName).toBe('历史掩码')
    expect(mapped.presentations[0].table.columns[1]).toEqual({
      field: 'customerName',
      label: '姓名',
      format: 'TEXT'
    })
  })

  test('converts an unknown old card into a bounded safe table', () => {
    const mapped = mapLegacyCards([{
      type: 'FUTURE_CARD',
      data: {
        items: [{
          label: '安全值',
          status: 'ACTIVE',
          customerId: 1001,
          orderId: 2002,
          phone: '13800138000',
          address: '北京市某地址',
          amount: 99,
          token: 'secret-token',
          permission: 'admin',
          sql: 'select * from users'
        }]
      }
    }])

    expect(mapped.presentations[0].table.dataPath).toBe('legacyItems')
    expect(mapped.presentations[0].table.columns.map(column => column.field)).toEqual(['label', 'status'])
    expect(mapped.cards[0].data.legacyItems[0]).toEqual({ label: '安全值', status: 'ACTIVE' })
    expect(JSON.stringify(mapped.cards[0].data)).not.toContain('customerId')
  })

  test('uses a stable unavailable message when an unknown card has no safe values', () => {
    const mapped = createSafeLegacyCard({ type: 'FUTURE_CARD', data: { customerId: 1001, phone: '13800138000' } }, 'call-unknown')

    expect(mapped.presentation.availableViews).toEqual(['TEXT'])
    expect(mapped.presentation.summary.fields).toEqual([{ field: 'message', label: '提示', format: 'TEXT' }])
    expect(mapped.card.data.legacySummary.message).toBe('展示格式暂不可用')
  })
})
