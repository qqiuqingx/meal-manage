/* eslint-env jest */
import AgentPresentationCard from '@/views/agent/diagnosis/components/AgentPresentationCard.vue'

function createContext(presentation, selectedView, card) {
  const context = {
    card: card || {
      type: 'SERVICE_CUSTOMER_LIST',
      sourceToolCallId: 'call-1',
      data: { items: [] }
    },
    presentation,
    warnings: ['TOOL_BUDGET_EXCEEDED'],
    partial: true,
    failedViews: [],
    selectedView: selectedView || presentation.defaultView
  }
  Object.keys(AgentPresentationCard.computed).forEach(name => {
    Object.defineProperty(context, name, { get: () => AgentPresentationCard.computed[name].call(context) })
  })
  Object.assign(context, AgentPresentationCard.methods)
  return context
}

describe('AgentPresentationCard', () => {
  test('keeps one view without rendering empty tabs', () => {
    const context = createContext({
      title: '服务客户订单',
      schemaVersion: 'v1',
      sourceToolCallId: 'call-1',
      cardType: 'SERVICE_CUSTOMER_LIST',
      decisionSource: 'SYSTEM',
      layout: 'TABS',
      defaultView: 'TABLE',
      availableViews: ['TABLE'],
      table: { dataPath: 'items', columns: [{ field: 'customerCode', label: '客户编号', format: 'TEXT' }] }
    })

    expect(context.validViews).toEqual(['TABLE'])
    expect(context.hasTabs).toBe(false)
    expect(context.currentView).toBe('TABLE')
    expect(context.hasNotice).toBe(true)
    expect(context.noticeText).toContain('TOOL_BUDGET_EXCEEDED')
  })

  test('uses default view and isolates failed view state per card', () => {
    const presentation = {
      schemaVersion: 'v1',
      sourceToolCallId: 'call-1',
      cardType: 'METRIC_RESULT',
      decisionSource: 'SYSTEM',
      layout: 'TABS',
      title: '指标',
      defaultView: 'BAR',
      availableViews: ['TABLE', 'BAR'],
      table: { dataPath: 'data.breakdown', columns: [{ field: 'label', label: '分组', format: 'TEXT' }] },
      chart: { type: 'BAR', dataPath: 'data.breakdown', dimensionField: 'label', metricFields: ['value'], dimensionLabel: '分组', metricLabels: ['数值'] }
    }
    const metricCard = {
      type: 'METRIC_RESULT',
      sourceToolCallId: 'call-1',
      data: { data: { breakdown: [] } }
    }
    const first = createContext(presentation, null, metricCard)
    const second = createContext(presentation, null, metricCard)
    first.partial = false
    first.warnings = []
    second.partial = false
    second.warnings = []

    expect(first.hasTabs).toBe(true)
    expect(first.currentView).toBe('BAR')
    first.handleChartError('BAR')
    expect(first.validViews).toEqual(['TABLE'])
    expect(first.currentView).toBe('TABLE')
    expect(second.validViews).toEqual(['TABLE', 'BAR'])
    expect(second.currentView).toBe('BAR')
  })

  test('rejects a descriptor associated with another card', () => {
    const context = createContext({
      schemaVersion: 'v1',
      sourceToolCallId: 'other-call',
      cardType: 'SERVICE_CUSTOMER_LIST',
      decisionSource: 'SYSTEM',
      title: '不应显示',
      layout: 'TABS',
      defaultView: 'TABLE',
      availableViews: ['TABLE'],
      table: { dataPath: 'items', columns: [{ field: 'customerCode', label: '客户编号', format: 'TEXT' }] }
    })

    expect(context.safePresentation).toBe(null)
    expect(context.validViews).toEqual([])
    expect(context.currentView).toBe(null)
  })
})
