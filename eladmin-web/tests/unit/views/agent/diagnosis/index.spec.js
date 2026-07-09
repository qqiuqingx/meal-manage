/* eslint-env jest */
jest.mock('@/api/agentDiagnosis', () => ({
  archiveChatSession: jest.fn(),
  chatMealPlan: jest.fn(),
  confirmActionDraft: jest.fn(),
  createChatSession: jest.fn(),
  diagnoseMealPlan: jest.fn(),
  getChatSession: jest.fn(),
  queryActionAudits: jest.fn(),
  queryChatSessions: jest.fn(),
  queryAgentOperationStats: jest.fn(),
  queryAgentRuleGaps: jest.fn(),
  submitDiagnosisFeedback: jest.fn(),
  updateChatSessionTitle: jest.fn()
}))

const api = require('@/api/agentDiagnosis')
const AgentDiagnosis = require('@/views/agent/diagnosis/index.vue').default

function createCtx() {
  const data = AgentDiagnosis.data()
  const ctx = {
    ...data,
    $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
    $prompt: jest.fn(),
    $nextTick: fn => fn && fn(),
    $refs: { messageList: { scrollTop: 0, scrollHeight: 100 }},
    extractPageContent: AgentDiagnosis.methods.extractPageContent,
    scrollToBottom: AgentDiagnosis.methods.scrollToBottom,
    loadSessions: AgentDiagnosis.methods.loadSessions,
    handleSessionChange: AgentDiagnosis.methods.handleSessionChange,
    createSession: AgentDiagnosis.methods.createSession,
    archiveCurrentSession: AgentDiagnosis.methods.archiveCurrentSession,
    renameCurrentSession: AgentDiagnosis.methods.renameCurrentSession,
    addAssistantResponse: AgentDiagnosis.methods.addAssistantResponse,
    sendMessage: AgentDiagnosis.methods.sendMessage,
    sendQuickReply: AgentDiagnosis.methods.sendQuickReply,
    clearSession: AgentDiagnosis.methods.clearSession,
    resetSessionState: AgentDiagnosis.methods.resetSessionState,
    applySessionDetail: AgentDiagnosis.methods.applySessionDetail,
    restoreSessionMessages: AgentDiagnosis.methods.restoreSessionMessages,
    findDiagnosisMessage: AgentDiagnosis.methods.findDiagnosisMessage,
    mapSessionMessages: AgentDiagnosis.methods.mapSessionMessages,
    sessionOptionLabel: AgentDiagnosis.methods.sessionOptionLabel,
    generateClientMessageId: AgentDiagnosis.methods.generateClientMessageId,
    formatSessionTime: AgentDiagnosis.methods.formatSessionTime,
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
    handleStaleDraftResponse: AgentDiagnosis.methods.handleStaleDraftResponse,
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
  Object.defineProperty(ctx, 'topFailureTypes', {
    get() {
      return AgentDiagnosis.computed.topFailureTypes.call(ctx)
    }
  })
  Object.defineProperty(ctx, 'topFallbackSources', {
    get() {
      return AgentDiagnosis.computed.topFallbackSources.call(ctx)
    }
  })
  Object.defineProperty(ctx, 'filteredSessions', {
    get() {
      return AgentDiagnosis.computed.filteredSessions.call(ctx)
    }
  })
  return ctx
}

describe('AgentDiagnosis chat page logic', () => {
  beforeEach(() => {
    api.chatMealPlan.mockReset()
    api.createChatSession.mockReset()
    api.getChatSession.mockReset()
    api.queryChatSessions.mockReset()
    api.archiveChatSession.mockReset()
    api.updateChatSessionTitle.mockReset()
    api.confirmActionDraft.mockReset()
    api.queryActionAudits.mockReset()
    api.queryAgentOperationStats.mockReset()
    api.queryAgentRuleGaps.mockReset()
    api.submitDiagnosisFeedback.mockReset()
    api.queryActionAudits.mockResolvedValue({ content: [] })
    api.createChatSession.mockResolvedValue({ sessionId: 'session-1', title: '新会话' })
  })

  test('shows initial welcome assistant message', () => {
    const ctx = createCtx()

    expect(ctx.messages).toHaveLength(1)
    expect(ctx.messages[0].role).toBe('assistant')
    expect(ctx.messages[0].content).toContain('请描述要排查的客户、日期和餐次')
    expect(ctx.conversationStage).toBe('COLLECTING_SLOTS')
    expect(ctx.sessionId).toBe(null)
  })

  test('sends user message and appends assistant question with missing slots', async() => {
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

    expect(api.createChatSession).toHaveBeenCalledWith({})
    expect(api.chatMealPlan.mock.calls[0][0].sessionId).toBe('session-1')
    expect(api.chatMealPlan.mock.calls[0][0].message).toBe('查 C10001 今天')
    expect(api.chatMealPlan.mock.calls[0][0].clientMessageId).toContain('msg-')
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

  test('appends diagnosis result and keeps latest diagnosis context', async() => {
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

  test('shows fallback reason and trace data in state', async() => {
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

  test('keeps read-only action drafts in diagnosis result', async() => {
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

  test('submits action draft confirmation with idempotency key', async() => {
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

  test('shows stale draft warning and appends assistant prompt when action draft expired', async() => {
    api.confirmActionDraft.mockResolvedValue({
      auditId: 2,
      actionCode: 'ADJUST_ORDER_EFFECTIVE_DATE',
      status: 'STALE_DRAFT',
      success: false,
      message: '业务数据已变化，请重新排查后再确认动作。',
      failureReason: '诊断草稿生成后订单有效期已变化'
    })
    const ctx = createCtx()
    ctx.sessionId = 'session-1'
    ctx.slots = { customerCode: 'C10001' }
    const draft = {
      actionCode: 'ADJUST_ORDER_EFFECTIVE_DATE',
      title: '调整订单有效期',
      riskLevel: 'HIGH',
      targetType: 'ORDER',
      targetId: '2001'
    }

    AgentDiagnosis.methods.openActionConfirm.call(ctx, draft, { requestId: 'req-3' })
    ctx.secondConfirmed = true
    await AgentDiagnosis.methods.submitActionConfirm.call(ctx)

    expect(ctx.actionConfirmResult.status).toBe('STALE_DRAFT')
    expect(ctx.$message.warning).toHaveBeenCalledWith('业务数据已变化，请重新排查后再确认动作。')
    expect(ctx.quickReplies).toEqual(['重新排查', '清空会话'])
    expect(ctx.messages[ctx.messages.length - 1].status).toBe('STALE_DRAFT')
    expect(ctx.actionConfirmDialogVisible).toBe(false)
  })

  test('requires second confirmation for high risk action drafts', () => {
    const ctx = createCtx()
    const draft = { actionCode: 'ADJUST_ORDER_EFFECTIVE_DATE', riskLevel: 'HIGH', targetType: 'ORDER', targetId: '1001' }

    AgentDiagnosis.methods.openActionConfirm.call(ctx, draft, { requestId: 'req-2' })

    expect(ctx.isHighRisk(draft)).toBe(true)
    expect(ctx.secondConfirmed).toBe(false)
    expect(ctx.buildIdempotencyKey(draft)).toBe('req-2:ADJUST_ORDER_EFFECTIVE_DATE:ORDER:1001')
  })

  test('submits diagnosis feedback with predicted and actual reason codes', async() => {
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

  test('loads operation stats for dashboard', async() => {
    api.queryAgentOperationStats.mockResolvedValue({
      diagnosisCount: 8,
      fallbackRate: 0.25,
      feedbackAcceptedRate: 0.5,
      fallbackSourceDistribution: { ELADMIN_CLIENT: 2 },
      failureTypeDistribution: { AGENT_SERVICE_TIMEOUT: 2 },
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
    expect(ctx.topFailureTypes[0]).toEqual({ code: 'AGENT_SERVICE_TIMEOUT', count: 2 })
    expect(ctx.topFallbackSources[0]).toEqual({ code: 'ELADMIN_CLIENT', count: 2 })
  })

  test('togglePanel expands tool summary section', () => {
    const ctx = createCtx()

    AgentDiagnosis.methods.togglePanel.call(ctx, 'toolSummaryExpanded')

    expect(ctx.toolSummaryExpanded).toBe(true)
  })

  test('clearSession creates a new session and resets workbench state', async() => {
    const ctx = createCtx()
    ctx.sessionId = 'session-1'
    ctx.activeSessionId = 'session-1'
    ctx.slots = { customerCode: 'C10001' }
    ctx.slotConfidence = { customer: 'HIGH' }
    ctx.missingSlots = ['MEAL_TYPE']
    ctx.currentDiagnosis = { summary: '命中规则' }
    ctx.messages.push({ role: 'user', content: '查 C10001' })

    await AgentDiagnosis.methods.clearSession.call(ctx)

    expect(api.createChatSession).toHaveBeenCalledWith({})
    expect(ctx.sessionId).toBe('session-1')
    expect(ctx.activeSessionId).toBe('session-1')
    expect(ctx.inputMessage).toBe('')
    expect(ctx.slots).toEqual({})
    expect(ctx.slotConfidence).toEqual({})
    expect(ctx.missingSlots).toEqual([])
    expect(ctx.currentDiagnosis).toBe(null)
    expect(ctx.messages).toHaveLength(1)
    expect(ctx.messages[0].role).toBe('assistant')
  })

  test('quick reply fills and sends message', async() => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'NEED_MORE_INFO',
      assistantMessage: '请补充餐次：早餐、午餐还是晚餐？',
      quickReplies: ['午餐']
    })
    const ctx = createCtx()

    await AgentDiagnosis.methods.sendQuickReply.call(ctx, '午餐')

    expect(api.chatMealPlan.mock.calls[0][0].sessionId).toBe('session-1')
    expect(api.chatMealPlan.mock.calls[0][0].message).toBe('午餐')
  })

  test('loads session detail and maps persisted messages back into page state', async() => {
    api.getChatSession.mockResolvedValue({
      sessionId: 'session-2',
      stage: 'DIAGNOSED',
      currentSlots: { customerCode: 'C10002', recordDate: '2026-07-08', mealType: 'DINNER', slotConfidence: { customer: 'HIGH' }},
      latestDiagnosisResult: { summary: '命中客户排除日期', confidence: 'HIGH' },
      messages: [
        { role: 'USER', content: '查 C10002 晚餐', conversationStage: 'COLLECTING_SLOTS' },
        { role: 'ASSISTANT', content: '已完成诊断', conversationStage: 'DIAGNOSED', diagnosisResult: { summary: '命中客户排除日期' }, slots: { customerCode: 'C10002' }}
      ]
    })
    const ctx = createCtx()

    await AgentDiagnosis.methods.handleSessionChange.call(ctx, 'session-2')

    expect(ctx.activeSessionId).toBe('session-2')
    expect(ctx.currentDiagnosis.summary).toBe('命中客户排除日期')
    expect(ctx.messages).toHaveLength(2)
    expect(ctx.messages[0].role).toBe('user')
    expect(ctx.messages[1].result.summary).toBe('命中客户排除日期')
  })

  test('restores latest diagnosis result onto matching assistant message after refresh', () => {
    const ctx = createCtx()

    AgentDiagnosis.methods.applySessionDetail.call(ctx, {
      sessionId: 'session-3',
      stage: 'DIAGNOSED',
      currentSlots: { customerCode: 'C10003', recordDate: '2026-07-09', mealType: 'LUNCH' },
      latestDiagnosisResult: {
        requestId: 'req-3',
        summary: '命中客户排除日期',
        confidence: 'HIGH',
        mealType: 'LUNCH'
      },
      messages: [
        { role: 'USER', requestId: 'req-3', content: '查 C10003 明天午餐', conversationStage: 'COLLECTING_SLOTS' },
        { role: 'ASSISTANT', requestId: 'req-3', content: '已完成诊断', conversationStage: 'DIAGNOSED' }
      ]
    })

    expect(ctx.currentDiagnosis.summary).toBe('命中客户排除日期')
    expect(ctx.messages).toHaveLength(2)
    expect(ctx.messages[1].result.summary).toBe('命中客户排除日期')
  })

  test('appends synthetic assistant diagnosis message when refresh payload lacks assistant result carrier', () => {
    const ctx = createCtx()

    AgentDiagnosis.methods.applySessionDetail.call(ctx, {
      sessionId: 'session-4',
      stage: 'DIAGNOSED',
      currentSlots: { customerCode: 'C10004', recordDate: '2026-07-09', mealType: 'DINNER' },
      latestDiagnosisResult: {
        requestId: 'req-4',
        summary: '命中订单过期',
        confidence: 'HIGH',
        mealType: 'DINNER'
      },
      messages: [
        { role: 'USER', requestId: 'req-4', content: '查 C10004 明天晚餐', conversationStage: 'COLLECTING_SLOTS' }
      ]
    })

    expect(ctx.messages).toHaveLength(2)
    expect(ctx.messages[1].role).toBe('assistant')
    expect(ctx.messages[1].result.summary).toBe('命中订单过期')
  })

  test('filters sessions by keyword and formats session label fields', () => {
    const ctx = createCtx()
    ctx.sessions = [
      { sessionId: 'session-1', title: 'C10001 午餐排查', customerCode: 'C10001', mealType: 'LUNCH', lastSummary: '命中排除日期', lastMessageTime: '2026-07-08 12:30:00' },
      { sessionId: 'session-2', title: 'C10002 晚餐排查', customerCode: 'C10002', mealType: 'DINNER', lastSummary: '订单过期' }
    ]
    ctx.sessionKeyword = '10001'

    expect(ctx.filteredSessions).toHaveLength(1)
    expect(ctx.filteredSessions[0].sessionId).toBe('session-1')
    expect(ctx.formatSessionTime('2026-07-08 12:30:00')).toBe('07-08 12:30')
    expect(ctx.sessionOptionLabel({ customerCode: 'C10003', recordDate: '2026-07-08', mealType: 'LUNCH' })).toContain('C10003')
  })

  test('renames current session through session title api', async() => {
    api.updateChatSessionTitle.mockResolvedValue({})
    const ctx = createCtx()
    ctx.activeSessionId = 'session-1'
    ctx.sessions = [{ sessionId: 'session-1', title: '旧标题', customerCode: 'C10001', mealType: 'LUNCH' }]
    ctx.$prompt.mockResolvedValue({ value: '新标题' })
    ctx.loadSessions = jest.fn().mockResolvedValue()

    await AgentDiagnosis.methods.renameCurrentSession.call(ctx)

    expect(api.updateChatSessionTitle).toHaveBeenCalledWith('session-1', { title: '新标题' })
    expect(ctx.$message.success).toHaveBeenCalledWith('会话标题已更新')
    expect(ctx.loadSessions).toHaveBeenCalledWith(false)
  })
})
