/* eslint-env jest */
jest.mock('@/api/agentDiagnosis', () => ({
  chatMealPlan: jest.fn(),
  diagnoseMealPlan: jest.fn()
}))

const api = require('@/api/agentDiagnosis')
const AgentDiagnosis = require('@/views/agent/diagnosis/index.vue').default

function createCtx() {
  const data = AgentDiagnosis.data()
  return {
    ...data,
    $message: { warning: jest.fn(), error: jest.fn() },
    $nextTick: fn => fn && fn(),
    $refs: { messageList: { scrollTop: 0, scrollHeight: 100 } },
    scrollToBottom: AgentDiagnosis.methods.scrollToBottom,
    addAssistantResponse: AgentDiagnosis.methods.addAssistantResponse,
    sendMessage: AgentDiagnosis.methods.sendMessage,
    sendQuickReply: AgentDiagnosis.methods.sendQuickReply,
    clearSession: AgentDiagnosis.methods.clearSession,
    mealTypeText: AgentDiagnosis.methods.mealTypeText,
    levelTag: AgentDiagnosis.methods.levelTag,
    shortDigest: AgentDiagnosis.methods.shortDigest
  }
}

describe('AgentDiagnosis chat page logic', () => {
  beforeEach(() => {
    api.chatMealPlan.mockReset()
  })

  test('shows initial welcome assistant message', () => {
    const ctx = createCtx()

    expect(ctx.messages).toHaveLength(1)
    expect(ctx.messages[0].role).toBe('assistant')
    expect(ctx.messages[0].content).toContain('请描述要排查的客户、日期和餐次')
    expect(ctx.sessionId).toBe(null)
  })

  test('sends user message and appends assistant question', async () => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'NEED_MORE_INFO',
      assistantMessage: '请补充餐次：早餐、午餐还是晚餐？',
      slots: { customerCode: 'C10001', recordDate: '2026-05-22' },
      quickReplies: ['早餐', '午餐', '晚餐']
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(api.chatMealPlan).toHaveBeenCalledWith({ sessionId: null, message: '查 C10001 今天' })
    expect(ctx.sessionId).toBe('session-1')
    expect(ctx.messages[1]).toMatchObject({ role: 'user', content: '查 C10001 今天' })
    expect(ctx.messages[2]).toMatchObject({ role: 'assistant', content: '请补充餐次：早餐、午餐还是晚餐？', status: 'NEED_MORE_INFO' })
    expect(ctx.quickReplies).toEqual(['早餐', '午餐', '晚餐'])
  })

  test('appends diagnosis result card on answered response', async () => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'ANSWERED',
      assistantMessage: '已完成诊断，发现 2 个可能原因，请结合证据人工确认。',
      slots: { customerCode: 'C10001', recordDate: '2026-05-22', mealType: 'LUNCH' },
      diagnosisResult: { summary: '命中客户排除日期', reasons: [], mealType: 'LUNCH' },
      quickReplies: ['继续追问', '重新排查', '清空会话']
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天午餐'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    const assistant = ctx.messages[2]
    expect(assistant.result.summary).toBe('命中客户排除日期')
    expect(ctx.quickReplies).toEqual(['继续追问', '重新排查', '清空会话'])
  })

  test('clearSession resets session and messages', () => {
    const ctx = createCtx()
    ctx.sessionId = 'session-1'
    ctx.messages.push({ role: 'user', content: '查 C10001' })

    AgentDiagnosis.methods.clearSession.call(ctx)

    expect(ctx.sessionId).toBe(null)
    expect(ctx.inputMessage).toBe('')
    expect(ctx.messages).toHaveLength(1)
    expect(ctx.messages[0].role).toBe('assistant')
  })

  test('quick reply fills and sends message', async () => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'NEED_MORE_INFO',
      assistantMessage: '请补充餐次：早餐、午餐还是晚餐？',
      quickReplies: ['午餐']
    })
    const ctx = createCtx()

    await AgentDiagnosis.methods.sendQuickReply.call(ctx, '午餐')

    expect(api.chatMealPlan).toHaveBeenCalledWith({ sessionId: null, message: '午餐' })
  })
})
