/* eslint-env jest */
import {
  businessWarningMessage,
  hasBusinessIntegrityWarning,
  parseAgentWarning
} from '@/views/agent/diagnosis/utils/agentWarningMessages'

describe('agentWarningMessages', () => {
  test('extracts the last stable code from tool-prefixed warnings', () => {
    expect(parseAgentWarning('listMealPlans:TOOL_PERMISSION_DENIED')).toEqual({
      raw: 'listMealPlans:TOOL_PERMISSION_DENIED',
      source: 'listMealPlans',
      code: 'TOOL_PERMISSION_DENIED'
    })
    expect(parseAgentWarning('RESULT_TRUNCATED')).toEqual({
      raw: 'RESULT_TRUNCATED',
      source: '',
      code: 'RESULT_TRUNCATED'
    })
  })

  test('uses business priority and does not expose unknown technical codes', () => {
    expect(businessWarningMessage([
      'listMealPlans:TOOL_BUDGET_EXCEEDED',
      'searchCustomerProfiles:TOOL_PERMISSION_DENIED'
    ], true)).toContain('缺少该类业务数据的查询权限')
    expect(businessWarningMessage(['UNKNOWN_INTERNAL_CODE'], false)).not.toContain('UNKNOWN_INTERNAL_CODE')
  })

  test('distinguishes integrity warnings from presentation fallback warnings', () => {
    expect(hasBusinessIntegrityWarning(['PRESENTATION_FALLBACK_APPLIED'])).toBe(false)
    expect(hasBusinessIntegrityWarning(['listMealPlans:RESULT_TRUNCATED'])).toBe(true)
    expect(businessWarningMessage(['PRESENTATION_FALLBACK_APPLIED'], false)).toContain('业务结果仍可查看')
    expect(businessWarningMessage([], true)).toContain('结果可能不完整')
  })
})
