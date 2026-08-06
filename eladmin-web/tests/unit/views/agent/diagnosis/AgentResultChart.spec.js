/* eslint-env jest */
jest.mock('echarts', () => ({ init: jest.fn() }))

import echarts from 'echarts'
import AgentResultChart from '@/views/agent/diagnosis/components/AgentResultChart.vue'

function createContext(data, chart) {
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
    $nextTick: fn => fn()
  }
  Object.keys(AgentResultChart.computed).forEach(name => {
    Object.defineProperty(context, name, { get: () => AgentResultChart.computed[name].call(context) })
  })
  Object.assign(context, AgentResultChart.methods)
  return context
}

describe('AgentResultChart', () => {
  beforeEach(() => {
    echarts.init.mockReset()
  })

  test('builds a fixed bar option from descriptor fields', () => {
    const context = createContext(
      { data: { breakdown: [{ label: '套餐 A', value: 3 }, { label: '套餐 B', value: 5 }] } },
      {
        type: 'BAR',
        dataPath: 'data.breakdown',
        dimensionField: 'label',
        metricFields: ['value'],
        dimensionLabel: '套餐',
        metricLabels: ['客户数']
      }
    )

    expect(context.chartOption).toMatchObject({
      animation: false,
      xAxis: { type: 'category', data: ['套餐 A', '套餐 B'] },
      series: [{ name: '客户数', type: 'bar', data: [3, 5] }]
    })
    expect(context.chartOption).not.toHaveProperty('formatter')
  })

  test('converts an over-category pie chart to a fixed bar chart', () => {
    const rows = Array.from({ length: 9 }, (_, index) => ({ label: `分类 ${index}`, value: index + 1 }))
    const context = createContext({ data: { breakdown: rows } }, {
      type: 'PIE',
      dataPath: 'data.breakdown',
      dimensionField: 'label',
      metricFields: ['value'],
      dimensionLabel: '分类',
      metricLabels: ['数量']
    })

    expect(context.chartType).toBe('BAR')
    expect(context.chartOption.series[0].type).toBe('bar')
  })

  test('does not initialize empty data and disposes on resize/destroy', () => {
    const context = createContext({ data: { breakdown: [] } }, {
      type: 'LINE',
      dataPath: 'data.breakdown',
      dimensionField: 'label',
      metricFields: ['value'],
      dimensionLabel: '日期',
      metricLabels: ['数量']
    })
    expect(context.renderChart()).toBe(false)
    expect(echarts.init).not.toHaveBeenCalled()

    const chartInstance = { setOption: jest.fn(), resize: jest.fn(), dispose: jest.fn() }
    echarts.init.mockReturnValue(chartInstance)
    const renderContext = createContext({ data: { breakdown: [{ label: 'A', value: 1 }] } }, {
      type: 'LINE',
      dataPath: 'data.breakdown',
      dimensionField: 'label',
      metricFields: ['value'],
      dimensionLabel: '日期',
      metricLabels: ['数量']
    })
    expect(renderContext.renderChart()).toBe(true)
    renderContext.handleResize()
    renderContext.disposeChart()
    expect(chartInstance.resize).toHaveBeenCalled()
    expect(chartInstance.dispose).toHaveBeenCalled()
  })

  test('falls back when chart resize throws', () => {
    const context = createContext({ data: { breakdown: [{ label: 'A', value: 1 }] } }, {
      type: 'BAR',
      dataPath: 'data.breakdown',
      dimensionField: 'label',
      metricFields: ['value'],
      dimensionLabel: '分组',
      metricLabels: ['数量']
    })
    const chartInstance = {
      resize: jest.fn(() => { throw new Error('resize failed') }),
      dispose: jest.fn()
    }
    context.chartInstance = chartInstance

    context.handleResize()

    expect(context.renderError).toBe(true)
    expect(context.chartInstance).toBe(null)
    expect(chartInstance.dispose).toHaveBeenCalled()
    expect(context.$emit).toHaveBeenCalledWith('render-error', 'BAR')
  })
})
