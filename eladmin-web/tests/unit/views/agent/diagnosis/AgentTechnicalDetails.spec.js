/* eslint-env jest */
import AgentTechnicalDetails from '@/views/agent/diagnosis/components/AgentTechnicalDetails.vue'

function createContext(message) {
  const context = { message }
  Object.keys(AgentTechnicalDetails.computed).forEach(name => {
    Object.defineProperty(context, name, {
      get: () => AgentTechnicalDetails.computed[name].call(context)
    })
  })
  return context
}

describe('AgentTechnicalDetails', () => {
  test('keeps technical fields available only behind the details entry', () => {
    const context = createContext({
      slotConfidence: { customer: 'HIGH', recordDate: 'MEDIUM' },
      warnings: ['getMealPlan:TOOL_OUTPUT_INVALID', 'TOOL_OUTPUT_INVALID'],
      toolTraceSummary: [{ toolName: 'getMealPlan', eventType: 'TOOL_CALL', resultCount: 1 }],
      result: {
        modelName: 'gpt-test',
        ruleVersionDigest: 'rules-20260810',
        diagnosisTrace: [{ eventType: 'MODEL_ROUND_COMPLETED', round: 1 }]
      }
    })

    expect(context.modelName).toBe('gpt-test')
    expect(context.ruleDigest).toBe('rules-20260810')
    expect(context.confidenceEntries).toEqual([
      { key: 'customer', label: '客户', value: 'HIGH' },
      { key: 'recordDate', label: '日期', value: 'MEDIUM' }
    ])
    expect(context.warningCodes).toEqual(['TOOL_OUTPUT_INVALID'])
    expect(context.toolSummary).toHaveLength(1)
    expect(context.diagnosisTrace).toHaveLength(1)
    expect(context.hasDetails).toBe(true)
  })

  test('does not create a details row for a message without technical data', () => {
    const context = createContext({})

    expect(context.hasDetails).toBe(false)
    expect(context.warningCodes).toEqual([])
  })
})
