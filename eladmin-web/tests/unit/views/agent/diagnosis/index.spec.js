/* eslint-env jest */
jest.mock('@/api/agentDiagnosis', () => ({
  chatMealPlan: jest.fn(),
  confirmActionDraft: jest.fn(),
  diagnoseMealPlan: jest.fn(),
  queryActionAudits: jest.fn(),
  queryAgentOperationStats: jest.fn(),
  queryAgentRuleGaps: jest.fn(),
  submitDiagnosisFeedback: jest.fn()
}))

const api = require('@/api/agentDiagnosis')
const AgentDiagnosis = require('@/views/agent/diagnosis/index.vue').default

function createCtx() {
  const data = AgentDiagnosis.data()
  const ctx = {
    ...data,
    $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
    $nextTick: fn => fn && fn(),
    $refs: { messageList: { scrollTop: 0, scrollHeight: 100 } },
    scrollToBottom: AgentDiagnosis.methods.scrollToBottom,
    addAssistantResponse: AgentDiagnosis.methods.addAssistantResponse,
    sendMessage: AgentDiagnosis.methods.sendMessage,
    sendQuickReply: AgentDiagnosis.methods.sendQuickReply,
    clearSession: AgentDiagnosis.methods.clearSession,
    togglePanel: AgentDiagnosis.methods.togglePanel,
    mealTypeText: AgentDiagnosis.methods.mealTypeText,
    missingSlotText: AgentDiagnosis.methods.missingSlotText,
    stageText: AgentDiagnosis.methods.stageText,
    slotLabel: AgentDiagnosis.methods.slotLabel,
    formatMissingSlots: AgentDiagnosis.methods.formatMissingSlots,
    levelTag: AgentDiagnosis.methods.levelTag,
    confidenceTag: AgentDiagnosis.methods.confidenceTag,
    riskTag: AgentDiagnosis.methods.riskTag,
    shortDigest: AgentDiagnosis.methods.shortDigest,
    compactJson: AgentDiagnosis.methods.compactJson,
    prettyJson: AgentDiagnosis.methods.prettyJson,
    isHighRisk: AgentDiagnosis.methods.isHighRisk,
    openActionConfirm: AgentDiagnosis.methods.openActionConfirm,
    submitActionConfirm: AgentDiagnosis.methods.submitActionConfirm,
    buildIdempotencyKey: AgentDiagnosis.methods.buildIdempotencyKey,
    openFeedbackDialog: AgentDiagnosis.methods.openFeedbackDialog,
    submitFeedback: AgentDiagnosis.methods.submitFeedback,
    extractReasonCodes: AgentDiagnosis.methods.extractReasonCodes,
    loadActionAudits: AgentDiagnosis.methods.loadActionAudits,
    loadOperationStats: AgentDiagnosis.methods.loadOperationStats,
    auditStatusTag: AgentDiagnosis.methods.auditStatusTag,
    percent: AgentDiagnosis.methods.percent,
    numberText: AgentDiagnosis.methods.numberText
  }
  Object.defineProperty(ctx, 'currentCustomer', {
    get() {
      return AgentDiagnosis.computed.currentCustomer.call(ctx)
    }
  })
  Object.defineProperty(ctx, 'slotConfidenceList', {
    get() {
      return AgentDiagnosis.computed.slotConfidenceList.call(ctx)
    }
  })
  Object.defineProperty(ctx, 'feedbackReasonOptions', {
    get() {
      return AgentDiagnosis.computed.feedbackReasonOptions.call(ctx)
    }
  })
  Object.defineProperty(ctx, 'topReasonCodes', {
    get() {
      return AgentDiagnosis.computed.topReasonCodes.call(ctx)
    }
  })
  return ctx
}

describe('AgentDiagnosis chat page logic', () => {
  beforeEach(() => {
    api.chatMealPlan.mockReset()
    api.confirmActionDraft.mockReset()
    api.queryActionAudits.mockReset()
    api.queryAgentOperationStats.mockReset()
    api.queryAgentRuleGaps.mockReset()
    api.submitDiagnosisFeedback.mockReset()
    api.queryActionAudits.mockResolvedValue({ content: [] })
  })

  test('shows initial welcome assistant message', () => {
    const ctx = createCtx()

    expect(ctx.messages).toHaveLength(1)
    expect(ctx.messages[0].role).toBe('assistant')
    expect(ctx.messages[0].content).toContain('请描述要排查的客户、日期和餐次')
    expect(ctx.conversationStage).toBe('COLLECTING_SLOTS')
    expect(ctx.sessionId).toBe(null)
  })

  test('sends user message and appends assistant question with missing slots', async () => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'NEED_MORE_INFO',
      assistantMessage: '请补充餐次：早餐、午餐还是晚餐？',
      slots: { customerCode: 'C10001', recordDate: '2026-05-22' },
      slotConfidence: { customer: 'HIGH', recordDate: 'HIGH' },
      missingSlots: ['MEAL_TYPE'],
      conversationStage: 'COLLECTING_SLOTS',
      quickReplies: ['早餐', '午餐', '晚餐']
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(api.chatMealPlan).toHaveBeenCalledWith({ sessionId: null, message: '查 C10001 今天' })
    expect(ctx.sessionId).toBe('session-1')
    expect(ctx.messages[1]).toMatchObject({ role: 'user', content: '查 C10001 今天' })
    expect(ctx.messages[2]).toMatchObject({
      role: 'assistant',
      content: '请补充餐次：早餐、午餐还是晚餐？',
      status: 'NEED_MORE_INFO',
      stage: 'COLLECTING_SLOTS',
      missingSlots: ['MEAL_TYPE']
    })
    expect(ctx.slotConfidence.customer).toBe('HIGH')
    expect(ctx.missingSlots).toEqual(['MEAL_TYPE'])
    expect(ctx.quickReplies).toEqual(['早餐', '午餐', '晚餐'])
    expect(api.queryActionAudits).toHaveBeenCalledWith({ sessionId: 'session-1', page: 0, size: 5 })
  })

  test('appends diagnosis result and keeps latest diagnosis context', async () => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'ANSWERED',
      assistantMessage: '已完成诊断，发现 2 个可能原因，请结合证据人工确认。',
      conversationStage: 'DIAGNOSED',
      slots: { customerCode: 'C10001', recordDate: '2026-05-22', mealType: 'LUNCH' },
      slotConfidence: { customer: 'HIGH', recordDate: 'HIGH', mealType: 'HIGH' },
      diagnosisResult: {
        summary: '命中客户排除日期',
        confidence: 'HIGH',
        nextActions: ['核对客户档案停送配置'],
        toolCallSummary: [{ eventType: 'TOOL_CALL', toolName: 'getCustomerProfile' }],
        reasons: [],
        mealType: 'LUNCH'
      },
      quickReplies: ['重新排查', '清空会话']
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天午餐'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    const assistant = ctx.messages[2]
    expect(assistant.result.summary).toBe('命中客户排除日期')
    expect(ctx.currentDiagnosis.summary).toBe('命中客户排除日期')
    expect(ctx.currentDiagnosis.nextActions).toEqual(['核对客户档案停送配置'])
    expect(ctx.conversationStage).toBe('DIAGNOSED')
    expect(ctx.quickReplies).toEqual(['重新排查', '清空会话'])
  })

  test('shows fallback reason and trace data in state', async () => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'ANSWERED',
      assistantMessage: '诊断数据不完整，需人工核对。',
      conversationStage: 'DIAGNOSED',
      diagnosisResult: {
        summary: '诊断数据不完整，需人工核对。',
        confidence: 'LOW',
        fallback: true,
        fallbackReason: '关键工具调用失败，诊断数据不完整，需人工核对。',
        nextActions: ['核对客户档案'],
        toolCallSummary: [{ eventType: 'TOOL_CALL', toolName: 'getMealPlan' }],
        diagnosisTrace: [{ eventType: 'MODEL_ROUND_COMPLETED', round: 1 }],
        reasons: []
      },
      quickReplies: ['重新排查', '清空会话']
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天午餐'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(ctx.currentDiagnosis.fallback).toBe(true)
    expect(ctx.currentDiagnosis.fallbackReason).toContain('关键工具调用失败')
    expect(ctx.currentDiagnosis.toolCallSummary).toHaveLength(1)
    expect(ctx.currentDiagnosis.diagnosisTrace).toHaveLength(1)
  })

  test('keeps read-only action drafts in diagnosis result', async () => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'ANSWERED',
      assistantMessage: '已完成诊断，生成动作草稿。',
      conversationStage: 'DIAGNOSED',
      diagnosisResult: {
        summary: '命中客户排除日期',
        confidence: 'HIGH',
        actionDrafts: [{
          actionCode: 'RESUME_CUSTOMER_DELIVERY',
          title: '恢复客户配送',
          riskLevel: 'MEDIUM',
          targetType: 'CUSTOMER',
          targetId: '1001',
          requiredPermission: 'customer:update',
          confirmApi: '/api/agent/action-drafts/confirm',
          afterPreview: { executeMode: 'MANUAL_CONFIRM_REQUIRED' }
        }],
        reasons: []
      },
      quickReplies: ['重新排查']
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天午餐'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(ctx.currentDiagnosis.actionDrafts).toHaveLength(1)
    expect(ctx.currentDiagnosis.actionDrafts[0].actionCode).toBe('RESUME_CUSTOMER_DELIVERY')
    expect(ctx.riskTag('MEDIUM')).toBe('warning')
    expect(ctx.compactJson(ctx.currentDiagnosis.actionDrafts[0].afterPreview)).toContain('MANUAL_CONFIRM_REQUIRED')
    expect(api.chatMealPlan).toHaveBeenCalledTimes(1)
  })

  test('submits action draft confirmation with idempotency key', async () => {
    api.confirmActionDraft.mockResolvedValue({
      auditId: 1,
      actionCode: 'CREATE_MANUAL_RECHECK_TASK',
      status: 'CONFIRMED',
      success: true,
      message: '动作确认已记录'
    })
    const ctx = createCtx()
    const draft = {
      actionCode: 'CREATE_MANUAL_RECHECK_TASK',
      title: '创建人工复核任务',
      riskLevel: 'LOW',
      targetType: 'RECHECK_TASK',
      targetId: '2026-05-22|LUNCH',
      afterPreview: { executeMode: 'MANUAL_CONFIRM_REQUIRED' }
    }
    const result = { requestId: 'req-1', customerId: 1001 }

    AgentDiagnosis.methods.openActionConfirm.call(ctx, draft, result)
    ctx.actionConfirmComment = '已电话核对'
    await AgentDiagnosis.methods.submitActionConfirm.call(ctx)

    expect(api.confirmActionDraft).toHaveBeenCalledWith({
      requestId: 'req-1',
      sessionId: null,
      idempotencyKey: 'req-1:CREATE_MANUAL_RECHECK_TASK:RECHECK_TASK:2026-05-22|LUNCH',
      actionDraft: draft,
      secondConfirmed: false,
      comment: '已电话核对'
    })
    expect(ctx.actionConfirmResult.success).toBe(true)
    expect(ctx.$message.success).toHaveBeenCalledWith('动作确认已记录')
    expect(api.queryActionAudits).not.toHaveBeenCalled()
  })

  test('requires second confirmation for high risk action drafts', () => {
    const ctx = createCtx()
    const draft = { actionCode: 'ADJUST_ORDER_EFFECTIVE_DATE', riskLevel: 'HIGH', targetType: 'ORDER', targetId: '1001' }

    AgentDiagnosis.methods.openActionConfirm.call(ctx, draft, { requestId: 'req-2' })

    expect(ctx.isHighRisk(draft)).toBe(true)
    expect(ctx.secondConfirmed).toBe(false)
    expect(ctx.buildIdempotencyKey(draft)).toBe('req-2:ADJUST_ORDER_EFFECTIVE_DATE:ORDER:1001')
  })

  test('submits diagnosis feedback with predicted and actual reason codes', async () => {
    api.submitDiagnosisFeedback.mockResolvedValue({ id: 10, status: 'SAVED', message: '诊断反馈已记录' })
    const ctx = createCtx()
    ctx.sessionId = 'session-1'
    const result = {
      requestId: 'req-1',
      customerId: 1001,
      customerName: '张三',
      recordDate: '2026-05-22',
      mealType: 'LUNCH',
      reasons: [{ code: 'CUSTOMER_EXCLUDE_DATE_HIT' }, { code: 'ORDER_EXPIRED' }]
    }

    AgentDiagnosis.methods.openFeedbackDialog.call(ctx, result, 'PARTIAL')
    ctx.feedbackForm.actualReasonCode = 'ORDER_EXPIRED'
    ctx.feedbackForm.comment = '真实原因是订单过期'
    await AgentDiagnosis.methods.submitFeedback.call(ctx)

    expect(api.submitDiagnosisFeedback).toHaveBeenCalledWith({
      requestId: 'req-1',
      sessionId: 'session-1',
      customerId: 1001,
      customerName: '张三',
      recordDate: '2026-05-22',
      mealType: 'LUNCH',
      predictedReasonCodes: ['CUSTOMER_EXCLUDE_DATE_HIT', 'ORDER_EXPIRED'],
      accepted: 'PARTIAL',
      actualReasonCode: 'ORDER_EXPIRED',
      comment: '真实原因是订单过期'
    })
    expect(ctx.feedbackDialogVisible).toBe(false)
    expect(ctx.$message.success).toHaveBeenCalledWith('诊断反馈已记录')
  })

  test('loads operation stats for dashboard', async () => {
    api.queryAgentOperationStats.mockResolvedValue({
      diagnosisCount: 8,
      fallbackRate: 0.25,
      feedbackAcceptedRate: 0.5,
      reasonCodeDistribution: {
        ORDER_EXPIRED: 3,
        CUSTOMER_EXCLUDE_DATE_HIT: 5
      }
    })
    const ctx = createCtx()

    await AgentDiagnosis.methods.loadOperationStats.call(ctx)

    expect(api.queryAgentOperationStats).toHaveBeenCalledWith({})
    expect(ctx.operationStats.diagnosisCount).toBe(8)
    expect(ctx.percent(ctx.operationStats.fallbackRate)).toBe('25%')
    expect(ctx.topReasonCodes[0]).toEqual({ code: 'CUSTOMER_EXCLUDE_DATE_HIT', count: 5 })
  })

  test('togglePanel expands tool summary section', () => {
    const ctx = createCtx()

    AgentDiagnosis.methods.togglePanel.call(ctx, 'toolSummaryExpanded')

    expect(ctx.toolSummaryExpanded).toBe(true)
  })

  test('clearSession resets session and workbench state', () => {
    const ctx = createCtx()
    ctx.sessionId = 'session-1'
    ctx.slots = { customerCode: 'C10001' }
    ctx.slotConfidence = { customer: 'HIGH' }
    ctx.missingSlots = ['MEAL_TYPE']
    ctx.currentDiagnosis = { summary: '命中规则' }
    ctx.messages.push({ role: 'user', content: '查 C10001' })

    AgentDiagnosis.methods.clearSession.call(ctx)

    expect(ctx.sessionId).toBe(null)
    expect(ctx.inputMessage).toBe('')
    expect(ctx.slots).toEqual({})
    expect(ctx.slotConfidence).toEqual({})
    expect(ctx.missingSlots).toEqual([])
    expect(ctx.currentDiagnosis).toBe(null)
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
