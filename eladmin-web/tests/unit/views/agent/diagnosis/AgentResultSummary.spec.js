/* eslint-env jest */
import AgentResultSummary from '@/views/agent/diagnosis/components/AgentResultSummary.vue'

function createContext(data, descriptor) {
  const context = { data, descriptor, summary: null, card: null }
  Object.defineProperty(context, 'sourceData', { get: () => AgentResultSummary.computed.sourceData.call(context) })
  Object.defineProperty(context, 'summaryDescriptor', { get: () => AgentResultSummary.computed.summaryDescriptor.call(context) })
  Object.defineProperty(context, 'summaryData', { get: () => AgentResultSummary.computed.summaryData.call(context) })
  Object.defineProperty(context, 'summaryRow', { get: () => AgentResultSummary.computed.summaryRow.call(context) })
  return context
}

describe('AgentResultSummary', () => {
  test('renders controlled key/value fields without executing descriptor content', () => {
    const context = createContext(
      { data: { metric: '订单数', total: 12, queriedAt: '2026-08-05T10:00:00' } },
      {
        summary: {
          dataPath: 'data',
          fields: [
            { field: 'metric', label: '指标', format: 'TEXT' },
            { field: 'total', label: '总数', format: 'NUMBER' },
            { field: 'queriedAt', label: '查询时间', format: 'DATE_TIME' },
            { field: 'metric.constructor', label: '危险字段', format: 'TEXT' },
            { field: 'metric', label: '<script>', format: 'TEXT' }
          ]
        }
      }
    )

    const fields = AgentResultSummary.computed.fields.call(context)

    expect(fields).toEqual([
      { key: 'metric-0', label: '指标', value: '订单数' },
      { key: 'total-1', label: '总数', value: '12' },
      { key: 'queriedAt-2', label: '查询时间', value: '2026-08-05 10:00:00' }
    ])
  })

  test('shows an empty summary when the controlled path has no object', () => {
    const context = createContext({}, { summary: { dataPath: 'data', fields: [{ field: 'metric', label: '指标', format: 'TEXT' }] } })
    expect(AgentResultSummary.computed.fields.call(context)[0].value).toBe('-')
  })
})
