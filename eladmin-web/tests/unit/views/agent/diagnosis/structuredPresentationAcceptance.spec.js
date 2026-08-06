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

const api = require('@/api/agentDiagnosis')
const AgentDiagnosis = require('@/views/agent/diagnosis/index.vue').default
const AgentPresentationCard = require('@/views/agent/diagnosis/components/AgentPresentationCard.vue').default
const AgentResultChart = require('@/views/agent/diagnosis/components/AgentResultChart.vue').default
const AgentResultSummary = require('@/views/agent/diagnosis/components/AgentResultSummary.vue').default
const AgentResultTable = require('@/views/agent/diagnosis/components/AgentResultTable.vue').default
const {
  formatFieldValue
} = require('@/views/agent/diagnosis/utils/agentPresentationFormatters')
const {
  mapLegacyCards
} = require('@/views/agent/diagnosis/utils/agentPresentationCompatibility')
const {
  validatePresentation
} = require('@/views/agent/diagnosis/utils/agentPresentationValidation')

/** 构造服务客户固定展示描述，保持与 Agent 服务的六列契约一致。 */
function serviceCustomerPresentation() {
  return {
    schemaVersion: 'v1',
    sourceToolCallId: 'call-service-customers',
    cardType: 'SERVICE_CUSTOMER_LIST',
    decisionSource: 'SYSTEM',
    title: '服务客户下单明细',
    layout: 'TABS',
    defaultView: 'TABLE',
    availableViews: ['TABLE'],
    table: {
      dataPath: 'items',
      columns: [
        { field: 'customerCode', label: '客户编号', format: 'TEXT' },
        { field: 'customerName', label: '姓名', format: 'TEXT' },
        { field: 'orderCode', label: '订单编号', format: 'TEXT' },
        { field: 'orderTime', label: '下单时间', format: 'DATE_TIME' },
        { field: 'status', label: '订单状态', format: 'STATUS' },
        { field: 'parentPackageName', label: '套餐', format: 'TEXT' }
      ]
    }
  }
}

/** 构造可同时展示摘要、表格和柱状图的指标描述。 */
function metricPresentation(overrides) {
  return Object.assign({
    schemaVersion: 'v1',
    sourceToolCallId: 'call-metric',
    cardType: 'METRIC_RESULT',
    decisionSource: 'SYSTEM',
    title: '运营指标',
    layout: 'TABS',
    defaultView: 'TEXT',
    availableViews: ['TEXT', 'TABLE', 'BAR'],
    summary: {
      dataPath: 'data',
      fields: [
        { field: 'metric', label: '指标', format: 'TEXT' },
        { field: 'total', label: '总数', format: 'NUMBER' }
      ]
    },
    table: {
      dataPath: 'data.breakdown',
      columns: [
        { field: 'label', label: '分组', format: 'TEXT' },
        { field: 'value', label: '数值', format: 'NUMBER' }
      ]
    },
    chart: {
      type: 'BAR',
      dataPath: 'data.breakdown',
      dimensionField: 'label',
      metricFields: ['value'],
      dimensionLabel: '分组',
      metricLabels: ['数值']
    }
  }, overrides || {})
}

/** 以组件 computed/methods 组成轻量前端验收上下文，避免引入真实页面副作用。 */
function tableContext(data, descriptor) {
  const context = { data, descriptor, table: null, card: null, pageSize: 10, currentPages: {} }
  Object.defineProperty(context, 'sourceData', { get: () => AgentResultTable.computed.sourceData.call(context) })
  Object.defineProperty(context, 'tableDescriptor', { get: () => AgentResultTable.computed.tableDescriptor.call(context) })
  Object.defineProperty(context, 'pageSizeNumber', { get: () => AgentResultTable.computed.pageSizeNumber.call(context) })
  Object.defineProperty(context, 'sections', { get: () => AgentResultTable.computed.sections.call(context) })
  Object.assign(context, AgentResultTable.methods)
  return context
}

/** 构造摘要组件上下文并返回固定字段计算结果。 */
function summaryContext(data, descriptor) {
  const context = { data, descriptor, summary: null, card: null }
  Object.defineProperty(context, 'sourceData', { get: () => AgentResultSummary.computed.sourceData.call(context) })
  Object.defineProperty(context, 'summaryDescriptor', { get: () => AgentResultSummary.computed.summaryDescriptor.call(context) })
  Object.defineProperty(context, 'summaryData', { get: () => AgentResultSummary.computed.summaryData.call(context) })
  Object.defineProperty(context, 'summaryRow', { get: () => AgentResultSummary.computed.summaryRow.call(context) })
  Object.defineProperty(context, 'fields', { get: () => AgentResultSummary.computed.fields.call(context) })
  return context
}

/** 构造图表组件上下文，直接验证固定 option 和类别边界。 */
function chartContext(data, chart) {
  const context = {
    data,
    chart,
    descriptor: null,
    card: null,
    visible: true,
    chartInstance: null,
    renderError: false,
    $refs: { chart: {} },
    $emit: jest.fn(),
    $nextTick: callback => callback && callback()
  }
  Object.keys(AgentResultChart.computed).forEach(name => {
    Object.defineProperty(context, name, { get: () => AgentResultChart.computed[name].call(context) })
  })
  Object.assign(context, AgentResultChart.methods)
  return context
}

/** 构造诊断页方法上下文，用于验证消息接收和历史恢复路径。 */
function pageContext() {
  const context = AgentDiagnosis.data()
  context.$message = { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
  context.$nextTick = callback => callback && callback()
  context.$refs = { messageList: { scrollTop: 0, scrollHeight: 0 } }
  Object.assign(context, AgentDiagnosis.methods)
  return context
}

describe('structured presentation cross-module acceptance', () => {
  beforeEach(() => {
    Object.values(api).forEach(mockedApi => {
      if (mockedApi && mockedApi.mockReset) mockedApi.mockReset()
    })
  })

  test('keeps service-customer facts readable with the required table order and time fallback', () => {
    const card = {
      type: 'SERVICE_CUSTOMER_LIST',
      sourceToolCallId: 'call-service-customers',
      data: {
        items: [
          {
            customerCode: 'C10001',
            customerName: '张三',
            orderCode: 'O-001',
            orderTime: '2026-08-01T09:30:00+08:00',
            dealTime: '2026-08-01T08:30:00+08:00',
            createTime: '2026-07-31T18:00:00+08:00',
            status: 'ACTIVE',
            parentPackageName: '标准套餐'
          },
          {
            customerCode: 'C10002',
            customerName: '李四',
            orderCode: 'O-002',
            orderTime: null,
            dealTime: null,
            createTime: '2026-08-02T10:00:00+08:00',
            status: 'ACTIVE',
            parentPackageName: '轻食套餐'
          }
        ]
      }
    }
    const descriptor = serviceCustomerPresentation()
    const validation = validatePresentation(descriptor, card, { warnings: [], partial: false })
    const table = tableContext(card.data, descriptor)

    expect(validation.validViews).toEqual(['TABLE'])
    expect(validation.presentation.table.columns.map(column => column.field)).toEqual([
      'customerCode', 'customerName', 'orderCode', 'orderTime', 'status', 'parentPackageName'
    ])
    expect(table.sections[0].rows).toHaveLength(2)
    expect(table.sections[0].columns.map(column => column.field)).toEqual([
      'customerCode', 'customerName', 'orderCode', 'orderTime', 'status', 'parentPackageName'
    ])
    expect(table.cellValue(table.sections[0].rows[0], table.sections[0].columns[3]))
      .toBe('2026-08-01 09:30:00')
    expect(table.cellValue(table.sections[0].rows[1], table.sections[0].columns[3]))
      .toBe('2026-08-02 10:00:00')
    expect(formatFieldValue(table.sections[0].rows[0], 'orderTime', 'DATE_TIME'))
      .not.toBe('2026-08-01 08:30:00')
    expect(table.cellClass('customerCode')).toBe('customer-code')
    expect(table.cellClass('customerName')).toBe('customer-name')

    const page = pageContext()
    AgentDiagnosis.methods.addAssistantResponse.call(page, {
      sessionId: 'session-acceptance',
      status: 'ANSWERED',
      conversationStage: 'ANSWERED',
      assistantMessage: '已查询到 2 笔进行中的客户订单。',
      cards: [card],
      presentations: [descriptor]
    })
    expect(page.messages[1].content).toBe('已查询到 2 笔进行中的客户订单。')
    expect(page.messages[1].content).not.toContain('customerCode')
    expect(JSON.stringify(descriptor)).not.toContain('张三')

    const presentationCard = {
      card,
      presentation: descriptor,
      warnings: [],
      partial: false,
      failedViews: [],
      selectedView: 'TABLE'
    }
    Object.keys(AgentPresentationCard.computed).forEach(name => {
      Object.defineProperty(presentationCard, name, {
        get: () => AgentPresentationCard.computed[name].call(presentationCard)
      })
    })
    Object.assign(presentationCard, AgentPresentationCard.methods)
    expect(presentationCard.currentView).toBe('TABLE')
    expect(presentationCard.hasTabs).toBe(false)
  })

  test('renders a single-value summary and a complete breakdown chart only from supplied fields', () => {
    const metricCard = {
      type: 'METRIC_RESULT',
      sourceToolCallId: 'call-metric',
      data: {
        data: {
          metric: 'ORDER_COUNT',
          total: 12,
          breakdown: [{ label: '套餐 A', value: 7 }, { label: '套餐 B', value: 5 }]
        }
      }
    }
    const summaryDescriptor = metricPresentation({
      availableViews: ['TEXT'],
      defaultView: 'TEXT',
      table: null,
      chart: null
    })
    const summary = summaryContext(metricCard.data, summaryDescriptor)
    expect(summary.fields).toEqual([
      { key: 'metric-0', label: '指标', value: 'ORDER_COUNT' },
      { key: 'total-1', label: '总数', value: '12' }
    ])
    expect(validatePresentation(summaryDescriptor, metricCard, { warnings: [], partial: false }).validViews)
      .toEqual(['TEXT'])

    const descriptor = metricPresentation()
    const validation = validatePresentation(descriptor, metricCard, { warnings: [], partial: false })
    expect(validation.validViews).toEqual(['TEXT', 'TABLE', 'BAR'])
    const chart = chartContext(metricCard.data, descriptor.chart)
    expect(chart.chartType).toBe('BAR')
    expect(chart.chartOption.series[0].data).toEqual([7, 5])
    expect(chart.chartOption.series[0].type).toBe('bar')

    const pie = chartContext(metricCard.data, Object.assign({}, descriptor.chart, { type: 'PIE' }))
    expect(pie.chartType).toBe('PIE')
    const nineCategories = Array.from({ length: 9 }, (_, index) => ({ label: `分类 ${index}`, value: index + 1 }))
    const pieOverLimit = chartContext({ data: { breakdown: nineCategories } }, Object.assign({}, descriptor.chart, {
      type: 'PIE'
    }))
    expect(pieOverLimit.chartType).toBe('BAR')
  })

  test('removes chart view for truncated data while retaining table or summary facts', () => {
    const descriptor = metricPresentation({ defaultView: 'BAR' })
    const card = {
      type: 'METRIC_RESULT',
      sourceToolCallId: 'call-metric',
      data: { data: { metric: 'ORDER_COUNT', total: 12, breakdown: [{ label: '套餐 A', value: 7 }] }, truncated: true }
    }
    const result = validatePresentation(descriptor, card, { warnings: [], partial: false })
    expect(result.validViews).toEqual(['TEXT', 'TABLE'])
    expect(result.presentation.availableViews).toEqual(['TEXT', 'TABLE'])

    const context = {
      card,
      presentation: descriptor,
      warnings: [],
      partial: false,
      failedViews: [],
      selectedView: 'BAR'
    }
    Object.keys(AgentPresentationCard.computed).forEach(name => {
      Object.defineProperty(context, name, { get: () => AgentPresentationCard.computed[name].call(context) })
    })
    expect(context.currentView).toBe('TEXT')
    expect(context.validViews).toEqual(['TEXT', 'TABLE'])
  })

  test('keeps unknown-card business facts readable after unsafe fields and invalid descriptors are removed', () => {
    const mapped = mapLegacyCards([{
      type: 'FUTURE_CARD',
      data: {
        items: [{
          label: '安全事实',
          customerName: '张三',
          status: 'ACTIVE',
          customerId: 1001,
          orderId: 2001,
          phone: '13800138000',
          address: '北京市某街道',
          amount: 99,
          token: 'secret-token',
          sql: 'select * from orders'
        }]
      }
    }])
    const serialized = JSON.stringify(mapped)
    expect(serialized).toContain('安全事实')
    expect(serialized).toContain('张三')
    ;['customerId', 'orderId', '13800138000', '北京市某街道', 'amount', 'secret-token', 'select * from orders']
      .forEach(value => expect(serialized).not.toContain(value))
    expect(mapped.presentations[0].table.columns.map(column => column.field)).toEqual([
      'label', 'customerName', 'status'
    ])

    const invalid = metricPresentation({
      availableViews: ['TABLE', 'BAR'],
      defaultView: 'TABLE',
      table: {
        dataPath: 'data.breakdown',
        columns: [{ field: 'label', label: '分组', format: 'TEXT' }]
      },
      chart: Object.assign({}, metricPresentation().chart, { dimensionField: 'customerId' })
    })
    const businessCard = {
      type: 'METRIC_RESULT',
      sourceToolCallId: 'call-metric',
      data: { data: { breakdown: [{ label: '套餐 A', value: 7 }] } }
    }
    const result = validatePresentation(invalid, businessCard, { warnings: [], partial: false })
    expect(result.validViews).toEqual(['TABLE'])
    expect(result.presentation.table.columns).toEqual([{ field: 'label', label: '分组', format: 'TEXT' }])
  })

  test('restores an old cards-only snapshot locally without realtime query or model call', async() => {
    api.getChatSession.mockResolvedValue({
      sessionId: 'session-old',
      stage: 'ANSWERED',
      messages: [{
        role: 'ASSISTANT',
        content: '历史查询结果',
        status: 'ANSWERED',
        businessResult: {
          cards: [{
            type: 'SERVICE_CUSTOMER_LIST',
            data: { items: [{ customerCode: 'C10001', maskedName: '历史掩码', orderCode: 'O-001' }] }
          }]
        }
      }]
    })
    const context = pageContext()

    await AgentDiagnosis.methods.handleSessionChange.call(context, 'session-old')

    expect(api.getChatSession).toHaveBeenCalledWith('session-old')
    expect(api.chatMealPlan).not.toHaveBeenCalled()
    expect(api.createChatSession).not.toHaveBeenCalled()
    expect(api.queryChatSessions).not.toHaveBeenCalled()
    expect(context.messages[0].cards[0].data.items[0].customerName).toBe('历史掩码')
    expect(context.messages[0].presentations[0].decisionSource).toBe('SYSTEM')
    expect(context.messages[0].presentations[0].table.columns.map(column => column.field)).toEqual([
      'customerCode', 'customerName', 'orderCode', 'orderTime', 'status', 'parentPackageName'
    ])
  })
})
