/* eslint-env jest */
import {
  getValidPresentationViews,
  hasIntegrityWarning,
  isSafePresentationField,
  validatePresentation
} from '@/views/agent/diagnosis/utils/agentPresentationValidation'

function card(data) {
  return { type: 'METRIC_RESULT', sourceToolCallId: 'call-1', data }
}

function descriptor(overrides) {
  return Object.assign({
    schemaVersion: 'v1',
    sourceToolCallId: 'call-1',
    cardType: 'METRIC_RESULT',
    decisionSource: 'SYSTEM',
    title: '运营指标',
    layout: 'TABS',
    defaultView: 'TABLE',
    availableViews: ['TABLE'],
    table: {
      dataPath: 'data.breakdown',
      columns: [{ field: 'label', label: '分组', format: 'TEXT' }]
    }
  }, overrides || {})
}

describe('agentPresentationValidation', () => {
  test('keeps valid views while removing only invalid views', () => {
    const result = validatePresentation(descriptor({
      availableViews: ['TABLE', 'BAR', 'NOT_ALLOWED'],
      chart: {
        type: 'BAR',
        dataPath: 'data.breakdown',
        dimensionField: 'label',
        metricFields: ['value'],
        dimensionLabel: '分组',
        metricLabels: ['数值']
      }
    }), card({ breakdown: [{ label: 'A', value: 1 }] }))

    expect(result.valid).toBe(true)
    expect(result.validViews).toEqual(['TABLE', 'BAR'])
    expect(result.invalidViews).toContain('NOT_ALLOWED')
  })

  test('validates schema and card association without rendering an unrelated descriptor', () => {
    const result = validatePresentation(descriptor({
      schemaVersion: 'v2',
      sourceToolCallId: 'other-call'
    }), card({ breakdown: [] }))

    expect(result.valid).toBe(false)
    expect(result.validViews).toEqual([])
    expect(result.errors).toEqual(expect.arrayContaining([
      'PRESENTATION_SCHEMA_VERSION_INVALID',
      'PRESENTATION_SOURCE_TOOL_CALL_INVALID'
    ]))
  })

  test('rejects sensitive paths and excessive chart metrics', () => {
    expect(isSafePresentationField('customerCode')).toBe(true)
    expect(isSafePresentationField('customerId')).toBe(false)
    expect(isSafePresentationField('data.orderId')).toBe(false)

    const result = validatePresentation(descriptor({
      availableViews: ['TABLE', 'BAR'],
      chart: {
        type: 'BAR',
        dataPath: 'data.breakdown',
        dimensionField: 'label',
        metricFields: ['value', 'second', 'third', 'fourth', 'fifth'],
        dimensionLabel: '分组',
        metricLabels: ['一', '二', '三', '四', '五']
      },
      table: {
        dataPath: 'data.breakdown',
        columns: [
          { field: 'customerId', label: '内部ID', format: 'TEXT' },
          { field: 'label', label: '分组', format: 'TEXT' }
        ]
      }
    }), card({ breakdown: [{ label: 'A', value: 1 }] }))

    expect(result.validViews).toEqual(['TABLE'])
    expect(result.presentation.table.columns).toEqual([{ field: 'label', label: '分组', format: 'TEXT' }])
  })

  test('hides charts for truncation or completeness warnings while keeping tables', () => {
    const value = descriptor({
      availableViews: ['TABLE', 'BAR'],
      chart: {
        type: 'BAR',
        dataPath: 'data.breakdown',
        dimensionField: 'label',
        metricFields: ['value'],
        dimensionLabel: '分组',
        metricLabels: ['数值']
      }
    })
    const data = { breakdown: [{ label: 'A', value: 1 }], truncated: true }

    expect(hasIntegrityWarning(['TOOL_BUDGET_EXCEEDED'])).toBe(true)
    expect(getValidPresentationViews(value, card(data), { warnings: [], partial: false })).toEqual(['TABLE'])
    expect(getValidPresentationViews(value, card({ breakdown: [{ label: 'A', value: 1 }] }), {
      warnings: ['RESULT_TRUNCATED'],
      partial: false
    })).toEqual(['TABLE'])
  })

  test('hides charts for prefixed integrity warnings but keeps presentation fallback warnings usable', () => {
    const value = descriptor({
      availableViews: ['TABLE', 'BAR'],
      chart: {
        type: 'BAR',
        dataPath: 'data.breakdown',
        dimensionField: 'label',
        metricFields: ['value'],
        dimensionLabel: '分组',
        metricLabels: ['数值']
      }
    })

    expect(getValidPresentationViews(value, card({ breakdown: [{ label: 'A', value: 1 }] }), {
      warnings: ['listMealPlans:TOOL_PERMISSION_DENIED'],
      partial: false
    })).toEqual(['TABLE'])
    expect(getValidPresentationViews(value, card({ breakdown: [{ label: 'A', value: 1 }] }), {
      warnings: ['PRESENTATION_FALLBACK_APPLIED'],
      partial: false
    })).toEqual(['TABLE', 'BAR'])
  })

  test('reports all views invalid without turning formatting failure into no-result data', () => {
    const result = validatePresentation(descriptor({
      defaultView: 'BAR',
      availableViews: ['BAR'],
      chart: {
        type: 'BAR',
        dataPath: 'data.missing',
        dimensionField: 'label',
        metricFields: ['value'],
        dimensionLabel: '分组',
        metricLabels: ['数值']
      },
      table: null
    }), card({ breakdown: [] }))

    expect(result.allViewsInvalid).toBe(true)
    expect(result.validViews).toEqual([])
    expect(result.presentation.availableViews).toEqual([])
  })
})
