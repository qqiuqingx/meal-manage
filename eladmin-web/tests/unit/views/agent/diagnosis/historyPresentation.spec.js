/* eslint-env jest */
jest.mock('@/api/agentDiagnosis', () => ({
  archiveChatSession: jest.fn(),
  chatMealPlan: jest.fn(),
  createChatSession: jest.fn(),
  getChatSession: jest.fn(),
  queryChatSessions: jest.fn(),
  submitDiagnosisFeedback: jest.fn(),
  updateChatSessionTitle: jest.fn()
}))

const AgentDiagnosis = require('@/views/agent/diagnosis/index.vue').default

function createContext() {
  const context = AgentDiagnosis.data()
  context.$nextTick = callback => callback && callback()
  context.$refs = { messageList: { scrollTop: 0, scrollHeight: 0 } }
  return context
}

describe('history presentation recovery', () => {
  test('generates local known-card presentation only when persisted presentations are absent', () => {
    const context = createContext()
    const mapped = AgentDiagnosis.methods.mapSessionMessages.call(context, [{
      role: 'ASSISTANT',
      content: '历史查询',
      businessResult: {
        cards: [{ type: 'SERVICE_CUSTOMER_LIST', data: { items: [{ customerCode: 'C1', maskedName: '旧掩码' }] } }]
      }
    }])

    expect(mapped[0].presentations).toHaveLength(1)
    expect(mapped[0].presentations[0].title).toBe('服务客户订单')
    expect(mapped[0].cards[0].data.items[0].customerName).toBe('旧掩码')
  })

  test('keeps a persisted decision source and default view unchanged', () => {
    const context = createContext()
    const presentation = {
      schemaVersion: 'v1',
      sourceToolCallId: 'call-1',
      cardType: 'METRIC_RESULT',
      decisionSource: 'LLM',
      title: '历史指标',
      layout: 'TABS',
      defaultView: 'BAR',
      availableViews: ['TABLE', 'BAR'],
      table: { dataPath: 'data.breakdown', columns: [{ field: 'label', label: '分组', format: 'TEXT' }] },
      chart: {
        type: 'BAR',
        dataPath: 'data.breakdown',
        dimensionField: 'label',
        metricFields: ['value'],
        dimensionLabel: '分组',
        metricLabels: ['数值']
      }
    }
    const mapped = AgentDiagnosis.methods.mapSessionMessages.call(context, [{
      role: 'ASSISTANT',
      content: '历史指标',
      businessResult: {
        cards: [{ sourceToolCallId: 'call-1', type: 'METRIC_RESULT', data: { breakdown: [{ label: 'A', value: 1 }] } }],
        presentations: [presentation]
      }
    }])

    expect(mapped[0].presentations[0]).toBe(presentation)
    expect(mapped[0].presentations[0].decisionSource).toBe('LLM')
    expect(mapped[0].presentations[0].defaultView).toBe('BAR')
  })

  test('keeps other cards, tool summaries and warnings when one legacy card is unusable', () => {
    const context = createContext()
    const mapped = AgentDiagnosis.methods.mapSessionMessages.call(context, [{
      role: 'ASSISTANT',
      content: '部分查询',
      businessResult: {
        cards: [
          { type: 'SERVICE_CUSTOMER_LIST', data: { items: [{ customerCode: 'C1', maskedName: '旧掩码' }] } },
          { type: 'FUTURE_CARD', data: { customerId: 1001, phone: '13800138000' } }
        ],
        toolTraceSummary: [{ toolName: 'searchServiceCustomers', status: 'SUCCESS' }],
        warnings: ['TOOL_BUDGET_EXCEEDED'],
        partial: true
      }
    }])

    expect(mapped[0].cards).toHaveLength(2)
    expect(mapped[0].presentations).toHaveLength(2)
    expect(mapped[0].toolTraceSummary).toHaveLength(1)
    expect(mapped[0].warnings).toEqual(['TOOL_BUDGET_EXCEEDED'])
    expect(mapped[0].partial).toBe(true)
  })
})
