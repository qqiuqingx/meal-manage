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

function createCtx() {
  const data = AgentDiagnosis.data()
  const ctx = {
    ...data,
    $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
    $prompt: jest.fn(),
    $router: { push: jest.fn() },
    $nextTick: fn => fn && fn(),
    $refs: { messageList: { scrollTop: 0, scrollHeight: 100 }},
    extractPageContent: AgentDiagnosis.methods.extractPageContent,
    scrollToBottom: AgentDiagnosis.methods.scrollToBottom,
    loadSessions: AgentDiagnosis.methods.loadSessions,
    mergeSessionSummaries: AgentDiagnosis.methods.mergeSessionSummaries,
    extractPageTotal: AgentDiagnosis.methods.extractPageTotal,
    handleSessionKeywordInput: AgentDiagnosis.methods.handleSessionKeywordInput,
    searchSessionsNow: AgentDiagnosis.methods.searchSessionsNow,
    handleSessionViewChange: AgentDiagnosis.methods.handleSessionViewChange,
    loadMoreSessions: AgentDiagnosis.methods.loadMoreSessions,
    formatSessionTime: AgentDiagnosis.methods.formatSessionTime,
    handleSessionChange: AgentDiagnosis.methods.handleSessionChange,
    createSession: AgentDiagnosis.methods.createSession,
    archiveCurrentSession: AgentDiagnosis.methods.archiveCurrentSession,
    renameCurrentSession: AgentDiagnosis.methods.renameCurrentSession,
    addAssistantResponse: AgentDiagnosis.methods.addAssistantResponse,
    sendMessage: AgentDiagnosis.methods.sendMessage,
    sendQuickReply: AgentDiagnosis.methods.sendQuickReply,
    retryMessage: AgentDiagnosis.methods.retryMessage,
    isSessionUnavailableError: AgentDiagnosis.methods.isSessionUnavailableError,
    normalizeAssistantMessage: AgentDiagnosis.methods.normalizeAssistantMessage,
    selectCustomerCandidate: AgentDiagnosis.methods.selectCustomerCandidate,
    isLatestAssistant: AgentDiagnosis.methods.isLatestAssistant,
    isCandidateCard: AgentDiagnosis.methods.isCandidateCard,
    navigationTargets: AgentDiagnosis.methods.navigationTargets,
    firstBusinessRow: AgentDiagnosis.methods.firstBusinessRow,
    copyAssistantMessage: AgentDiagnosis.methods.copyAssistantMessage,
    navigateTarget: AgentDiagnosis.methods.navigateTarget,
    queryWarningText: AgentDiagnosis.methods.queryWarningText,
    hasBusinessQueryResult: AgentDiagnosis.methods.hasBusinessQueryResult,
    clearSession: AgentDiagnosis.methods.clearSession,
    resetSessionState: AgentDiagnosis.methods.resetSessionState,
    applySessionDetail: AgentDiagnosis.methods.applySessionDetail,
    restoreSessionMessages: AgentDiagnosis.methods.restoreSessionMessages,
    findDiagnosisMessage: AgentDiagnosis.methods.findDiagnosisMessage,
    mapSessionMessages: AgentDiagnosis.methods.mapSessionMessages,
    sessionOptionLabel: AgentDiagnosis.methods.sessionOptionLabel,
    generateClientMessageId: AgentDiagnosis.methods.generateClientMessageId,
    mealTypeText: AgentDiagnosis.methods.mealTypeText,
    missingSlotText: AgentDiagnosis.methods.missingSlotText,
    stageText: AgentDiagnosis.methods.stageText,
    slotLabel: AgentDiagnosis.methods.slotLabel,
    levelTag: AgentDiagnosis.methods.levelTag,
    confidenceTag: AgentDiagnosis.methods.confidenceTag,
    openFeedbackDialog: AgentDiagnosis.methods.openFeedbackDialog,
    submitFeedback: AgentDiagnosis.methods.submitFeedback,
    extractReasonCodes: AgentDiagnosis.methods.extractReasonCodes
  }
  Object.defineProperty(ctx, 'feedbackReasonOptions', {
    get() {
      return AgentDiagnosis.computed.feedbackReasonOptions.call(ctx)
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
    api.submitDiagnosisFeedback.mockReset()
    api.createChatSession.mockResolvedValue({ sessionId: 'session-1', title: '新会话' })
  })

  test('shows initial welcome assistant message', () => {
    const ctx = createCtx()

    expect(ctx.messages).toHaveLength(1)
    expect(ctx.messages[0].role).toBe('assistant')
    expect(ctx.messages[0].content).toContain('你可以查询客户、订单、排餐、核销、退餐或运营统计')
    expect(ctx.conversationStage).toBe('READY')
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
      quickReplies: ['早餐', '午餐', '晚餐'],
      conversationStage: 'NEED_MORE_INFO'
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(api.createChatSession).not.toHaveBeenCalled()
    expect(api.chatMealPlan.mock.calls[0][0].sessionId).toBe(null)
    expect(api.chatMealPlan.mock.calls[0][0].message).toBe('查 C10001 今天')
    expect(api.chatMealPlan.mock.calls[0][0].clientMessageId).toContain('msg-')
    expect(ctx.sessionId).toBe('session-1')
    expect(ctx.messages[1]).toMatchObject({ role: 'user', content: '查 C10001 今天' })
    expect(ctx.messages[1].clientMessageId).toContain('msg-')
    expect(ctx.messages[2]).toMatchObject({
      role: 'assistant',
      content: '请补充餐次：早餐、午餐还是晚餐？',
      status: 'NEED_MORE_INFO',
      stage: 'NEED_MORE_INFO',
      missingSlots: ['MEAL_TYPE'],
      quickReplies: ['早餐', '午餐', '晚餐']
    })
    expect(ctx.slotConfidence.customer).toBe('HIGH')
    expect(ctx.missingSlots).toEqual(['MEAL_TYPE'])
    expect(ctx.messages[2].clientMessageId).toContain('msg-')
  })

  test('appends diagnosis result and keeps latest diagnosis context', async() => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'ANSWERED',
      assistantMessage: '已完成诊断，发现 2 个可能原因，请结合证据人工确认。',
      conversationStage: 'ANSWERED',
      slots: { customerCode: 'C10001', recordDate: '2026-05-22', mealType: 'LUNCH' },
      slotConfidence: { customer: 'HIGH', recordDate: 'HIGH', mealType: 'HIGH' },
      diagnosisResult: {
        summary: '命中客户排除日期',
        confidence: 'HIGH',
        nextActions: ['核对客户档案停送配置'],
        toolCallSummary: [{ eventType: 'TOOL_CALL', toolName: 'getCustomerProfile' }],
        reasons: [],
        mealType: 'LUNCH'
      }
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天午餐'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    const assistant = ctx.messages[2]
    expect(assistant.result.summary).toBe('命中客户排除日期')
    expect(ctx.currentDiagnosis.summary).toBe('命中客户排除日期')
    expect(ctx.currentDiagnosis.nextActions).toEqual(['核对客户档案停送配置'])
    expect(ctx.conversationStage).toBe('ANSWERED')
  })

  test('stores presentations and associates each card by sourceToolCallId', () => {
    const ctx = createCtx()
    const presentation = {
      schemaVersion: 'v1',
      sourceToolCallId: 'call-service-customers',
      cardType: 'SERVICE_CUSTOMER_LIST',
      title: '服务客户订单',
      defaultView: 'TABLE',
      availableViews: ['TABLE'],
      table: { dataPath: 'items', columns: [{ field: 'customerCode', label: '客户编号', format: 'TEXT' }] }
    }
    AgentDiagnosis.methods.addAssistantResponse.call(ctx, {
      sessionId: 'session-1',
      assistantMessage: '已查询到服务客户。',
      cards: [{ sourceToolCallId: 'call-service-customers', type: 'SERVICE_CUSTOMER_LIST', data: { items: [] }}],
      presentations: [presentation]
    })

    expect(ctx.messages[1].presentations).toEqual([presentation])
    expect(AgentDiagnosis.methods.presentationForCard.call(ctx, ctx.messages[1].cards[0], ctx.messages[1].presentations))
      .toBe(presentation)
    expect(AgentDiagnosis.methods.presentationForCard.call(ctx, ctx.messages[1].cards[0], [])).toBe(null)
  })

  test('shows fallback reason and trace data in state', async() => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'ANSWERED',
      assistantMessage: '诊断数据不完整，需人工核对。',
      conversationStage: 'ANSWERED',
      diagnosisResult: {
        summary: '诊断数据不完整，需人工核对。',
        confidence: 'LOW',
        fallback: true,
        fallbackReason: '关键工具调用失败，诊断数据不完整，需人工核对。',
        nextActions: ['核对客户档案'],
        toolCallSummary: [{ eventType: 'TOOL_CALL', toolName: 'getMealPlan' }],
        diagnosisTrace: [{ eventType: 'MODEL_ROUND_COMPLETED', round: 1 }],
        reasons: []
      }
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天午餐'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(ctx.currentDiagnosis.fallback).toBe(true)
    expect(ctx.currentDiagnosis.fallbackReason).toContain('关键工具调用失败')
    expect(ctx.currentDiagnosis.toolCallSummary).toHaveLength(1)
    expect(ctx.currentDiagnosis.diagnosisTrace).toHaveLength(1)
  })

  test('keeps historical action drafts out of the read-only query interaction', async() => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'ANSWERED',
      assistantMessage: '已完成诊断，生成动作草稿。',
      conversationStage: 'ANSWERED',
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
      }
    })
    const ctx = createCtx()
    ctx.inputMessage = '查 C10001 今天午餐'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(ctx.currentDiagnosis.actionDrafts).toHaveLength(1)
    expect(AgentDiagnosis.methods.openActionConfirm).toBeUndefined()
    expect(api.chatMealPlan).toHaveBeenCalledTimes(1)
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

    expect(api.createChatSession).not.toHaveBeenCalled()
    expect(ctx.sessionId).toBe(null)
    expect(ctx.activeSessionId).toBe(null)
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
      assistantMessage: '请补充餐次：早餐、午餐还是晚餐？'
    })
    const ctx = createCtx()

    await AgentDiagnosis.methods.sendQuickReply.call(ctx, '午餐')

    expect(api.chatMealPlan.mock.calls[0][0].sessionId).toBe(null)
    expect(api.chatMealPlan.mock.calls[0][0].message).toBe('午餐')
  })

  test('loads the active session page from the server with trimmed keyword and total', async() => {
    api.queryChatSessions.mockResolvedValue({
      content: [
        { sessionId: 'session-1', title: '午餐排查', customerCode: 'C10001' },
        { sessionId: 'session-2', title: '晚餐排查', orderCode: 'O10002' }
      ],
      totalElements: 25
    })
    const ctx = createCtx()
    ctx.sessionKeyword = '  C10001  '

    await AgentDiagnosis.methods.loadSessions.call(ctx, { reset: true, selectCurrent: false })

    expect(api.queryChatSessions).toHaveBeenCalledWith({
      archived: false,
      page: 0,
      size: 20,
      keyword: 'C10001'
    })
    expect(ctx.sessionTotal).toBe(25)
    expect(ctx.sessionHasMore).toBe(true)
    expect(ctx.sessions).toHaveLength(2)
  })

  test('appends the next page and deduplicates session IDs', async() => {
    api.queryChatSessions.mockResolvedValue({
      content: [
        { sessionId: 'session-1', title: '旧会话' },
        { sessionId: 'session-2', title: '新会话' }
      ],
      totalElements: 2
    })
    const ctx = createCtx()
    ctx.sessions = [{ sessionId: 'session-1', title: '旧会话' }]
    ctx.sessionPage = 0
    ctx.sessionTotal = 2
    ctx.sessionHasMore = true

    await AgentDiagnosis.methods.loadMoreSessions.call(ctx)

    expect(api.queryChatSessions).toHaveBeenCalledWith({ archived: false, page: 1, size: 20 })
    expect(ctx.sessions.map(session => session.sessionId)).toEqual(['session-1', 'session-2'])
    expect(ctx.sessionHasMore).toBe(false)
  })

  test('ignores a stale session search response', async() => {
    let resolveOld
    api.queryChatSessions
      .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce({ content: [{ sessionId: 'new-session' }], totalElements: 1 })
    const ctx = createCtx()

    const oldRequest = AgentDiagnosis.methods.loadSessions.call(ctx, { reset: true, selectCurrent: false })
    const newRequest = AgentDiagnosis.methods.loadSessions.call(ctx, { reset: true, selectCurrent: false })
    await newRequest
    resolveOld({ content: [{ sessionId: 'old-session' }], totalElements: 1 })
    await oldRequest

    expect(ctx.sessions.map(session => session.sessionId)).toEqual(['new-session'])
  })

  test('archives the current session and loads the next active session', async() => {
    api.archiveChatSession.mockResolvedValue()
    const ctx = createCtx()
    ctx.activeSessionId = 'session-1'
    ctx.activeSessionArchived = false
    ctx.resetSessionState = jest.fn()
    ctx.loadSessions = jest.fn().mockResolvedValue()

    await AgentDiagnosis.methods.archiveCurrentSession.call(ctx)

    expect(api.archiveChatSession).toHaveBeenCalledWith('session-1', true)
    expect(ctx.resetSessionState).toHaveBeenCalledWith()
    expect(ctx.loadSessions).toHaveBeenCalledWith({ reset: true, selectCurrent: true })
  })

  test('restores an archived session by switching back to the active view', async() => {
    api.archiveChatSession.mockResolvedValue()
    const ctx = createCtx()
    ctx.activeSessionId = 'session-archived'
    ctx.activeSessionArchived = true
    ctx.sessionArchivedView = true
    ctx.loadSessions = jest.fn().mockResolvedValue()

    await AgentDiagnosis.methods.archiveCurrentSession.call(ctx)

    expect(api.archiveChatSession).toHaveBeenCalledWith('session-archived', false)
    expect(ctx.sessionArchivedView).toBe(false)
    expect(ctx.activeSessionArchived).toBe(false)
    expect(ctx.loadSessions).toHaveBeenCalledWith({ reset: true, selectCurrent: true })
  })

  test('does not send from an archived read-only session', async() => {
    const ctx = createCtx()
    ctx.activeSessionId = 'session-archived'
    ctx.activeSessionArchived = true
    ctx.inputMessage = '查 C10001'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(api.chatMealPlan).not.toHaveBeenCalled()
    expect(ctx.messages).toHaveLength(1)
  })

  test('ignores a second send while the first request is loading', async() => {
    const ctx = createCtx()
    ctx.activeSessionId = 'session-1'
    ctx.loading = true
    ctx.inputMessage = '查 C10001'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    expect(api.chatMealPlan).not.toHaveBeenCalled()
    expect(ctx.messages).toHaveLength(1)
  })

  test('retries an error without adding another user bubble', async() => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      clientMessageId: 'msg-retry-new',
      status: 'ANSWERED',
      conversationStage: 'ANSWERED',
      assistantMessage: '重试成功。'
    })
    const ctx = createCtx()
    ctx.activeSessionId = 'session-1'
    const errorMessage = {
      role: 'assistant',
      sessionId: 'session-1',
      status: 'ERROR',
      retryText: '查 C10001 今天',
      content: '服务暂不可用'
    }
    ctx.messages.push(errorMessage)
    const before = ctx.messages.length

    await AgentDiagnosis.methods.retryMessage.call(ctx, errorMessage)

    expect(ctx.messages).toHaveLength(before + 1)
    expect(ctx.messages[ctx.messages.length - 1].content).toBe('重试成功。')
    expect(api.chatMealPlan).toHaveBeenCalledWith(expect.objectContaining({
      sessionId: 'session-1',
      message: '查 C10001 今天'
    }))
    expect(api.chatMealPlan.mock.calls[0][0].clientMessageId).not.toBe(errorMessage.clientMessageId)
    expect(errorMessage.retrying).toBe(false)
  })

  test('preserves the original text on a network error for the retry action', async() => {
    api.chatMealPlan.mockRejectedValue(new Error('network'))
    const ctx = createCtx()
    ctx.activeSessionId = 'session-1'
    ctx.inputMessage = '查 C10001 今天'

    await AgentDiagnosis.methods.sendMessage.call(ctx)

    const assistant = ctx.messages[ctx.messages.length - 1]
    expect(assistant).toMatchObject({ status: 'ERROR', retryText: '查 C10001 今天' })
    expect(ctx.messages.filter(message => message.role === 'user')).toHaveLength(1)
  })

  test('explains that a missing retry session requires a new session', async() => {
    api.chatMealPlan.mockRejectedValue({ response: { status: 404 }})
    const ctx = createCtx()
    ctx.activeSessionId = 'archived-session'
    const errorMessage = {
      role: 'assistant',
      sessionId: 'archived-session',
      status: 'ERROR',
      retryText: '查 C10001',
      content: '服务暂不可用'
    }
    ctx.messages.push(errorMessage)

    await AgentDiagnosis.methods.retryMessage.call(ctx, errorMessage)

    expect(ctx.messages[ctx.messages.length - 1]).toMatchObject({
      status: 'ERROR',
      content: '原会话已归档或不存在，请新建会话后重试。',
      retryText: ''
    })
    expect(ctx.$message.warning).toHaveBeenCalledWith('原会话已不可用，请新建会话后重试')
  })

  test('selecting customer candidate sends customer code', async() => {
    api.chatMealPlan.mockResolvedValue({
      sessionId: 'session-1',
      status: 'ANSWERED',
      assistantMessage: '客户已选择。'
    })
    const ctx = createCtx()
    ctx.activeSessionId = 'session-1'

    await AgentDiagnosis.methods.selectCustomerCandidate.call(ctx, {
      customerId: 1001,
      customerCode: 'B1001',
      customerName: '张三'
    })

    expect(api.chatMealPlan.mock.calls[0][0].sessionId).toBe('session-1')
    expect(api.chatMealPlan.mock.calls[0][0].message).toBe('客户编号 B1001')
  })

  test('does not send a candidate that lacks a customer code', async() => {
    const ctx = createCtx()
    await AgentDiagnosis.methods.selectCustomerCandidate.call(ctx, { customerId: 1001, customerName: '张三' })

    expect(api.chatMealPlan).not.toHaveBeenCalled()
  })

  test('builds fixed navigation targets from safe business fields only', () => {
    const ctx = createCtx()
    const targets = AgentDiagnosis.methods.navigationTargets.call(ctx, {
      result: {
        customerCode: 'C10001',
        orderCode: 'O10001',
        recordDate: '2026-08-10',
        mealType: 'LUNCH',
        customerId: 1001,
        customerName: '张三',
        route: '/unsafe'
      },
      slots: {}
    })

    expect(targets).toEqual([
      { kind: 'CUSTOMER_PROFILE', label: '客户档案', payload: { customerCode: 'C10001' } },
      { kind: 'CUSTOMER_ORDER', label: '客户订单', payload: { orderCode: 'O10001', customerCode: 'C10001' } },
      { kind: 'MEAL_PLAN', label: '排餐详情', payload: { date: '2026-08-10', mealType: 'LUNCH' } }
    ])
    AgentDiagnosis.methods.navigateTarget.call(ctx, targets[0])
    expect(ctx.$router.push).toHaveBeenCalledWith({
      path: '/customer/profile',
      query: { customerCode: 'C10001' }
    })
  })

  test('copies assistant business text without technical details', async() => {
    const writeText = jest.fn().mockResolvedValue()
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    const ctx = createCtx()

    await AgentDiagnosis.methods.copyAssistantMessage.call(ctx, {
      status: 'ANSWERED',
      content: '业务结论'
    })

    expect(writeText).toHaveBeenCalledWith('业务结论')
    expect(ctx.$message.success).toHaveBeenCalledWith('结论已复制')
  })

  test('uses non-disclosing message for business query permission denial', () => {
    const ctx = createCtx()

    const message = AgentDiagnosis.methods.queryWarningText.call(ctx, {
      partial: true,
      warnings: ['listMealPlans:TOOL_PERMISSION_DENIED']
    })

    expect(message).toContain('缺少该类业务数据的查询权限')
    expect(message).toContain('未返回对象是否存在')
  })

  test('keeps presentation fallback warnings separate from business completeness', () => {
    const ctx = createCtx()

    expect(AgentDiagnosis.methods.queryWarningText.call(ctx, {
      partial: false,
      warnings: ['PRESENTATION_FALLBACK_APPLIED']
    })).toContain('业务结果仍可查看')
  })

  test('does not expose a tool trace as a standalone business query result', () => {
    const ctx = createCtx()

    expect(ctx.hasBusinessQueryResult({
      toolTraceSummary: [{ toolName: 'listMealPlans', status: 'SUCCESS', resultCount: 20 }]
    })).toBe(false)
    expect(ctx.hasBusinessQueryResult({ cards: [{ type: 'MEAL_PLAN_LIST' }] })).toBe(true)
    expect(ctx.hasBusinessQueryResult({ warnings: ['TOOL_BUDGET_EXCEEDED'] })).toBe(true)
  })

  test('loads session detail and maps persisted messages back into page state', async() => {
    api.getChatSession.mockResolvedValue({
      sessionId: 'session-2',
      stage: 'ANSWERED',
      currentSlots: { customerCode: 'C10002', recordDate: '2026-07-08', mealType: 'DINNER', slotConfidence: { customer: 'HIGH' }},
      latestDiagnosisResult: { summary: '命中客户排除日期', confidence: 'HIGH' },
      messages: [
        { role: 'USER', content: '查 C10002 晚餐', conversationStage: 'READY' },
        { role: 'ASSISTANT', content: '已完成诊断', conversationStage: 'ANSWERED', diagnosisResult: { summary: '命中客户排除日期' }, slots: { customerCode: 'C10002' }}
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

  test('restores persisted presentations without recalculating the descriptor', () => {
    const ctx = createCtx()
    const presentation = {
      sourceToolCallId: 'call-restore',
      cardType: 'METRIC_RESULT',
      title: '运营指标',
      defaultView: 'BAR',
      availableViews: ['TABLE', 'BAR']
    }
    const mapped = AgentDiagnosis.methods.mapSessionMessages.call(ctx, [{
      role: 'ASSISTANT',
      content: '已恢复业务查询',
      businessResult: {
        cards: [{ sourceToolCallId: 'call-restore', type: 'METRIC_RESULT', data: {} }],
        presentations: [presentation],
        warnings: ['PRESENTATION_RULE_MISSING']
      }
    }])

    expect(mapped[0].presentations).toEqual([presentation])
    expect(mapped[0].cards[0].sourceToolCallId).toBe('call-restore')
    expect(mapped[0].warnings).toEqual(['PRESENTATION_RULE_MISSING'])
  })

  test('restores protocol fields and message ids from persisted business snapshot', () => {
    const ctx = createCtx()
    const mapped = AgentDiagnosis.methods.mapSessionMessages.call(ctx, [{
      role: 'ASSISTANT',
      requestId: 'req-restore',
      clientMessageId: 'msg-restore',
      content: '请补充餐次。',
      status: 'NEED_MORE_INFO',
      conversationStage: 'NEED_MORE_INFO',
      missingSlots: ['MEAL_TYPE'],
      quickReplies: ['早餐', '午餐', '晚餐'],
      businessResult: {
        missingSlots: ['MEAL_TYPE'],
        quickReplies: ['早餐', '午餐', '晚餐']
      }
    }])

    expect(mapped[0]).toMatchObject({
      requestId: 'req-restore',
      clientMessageId: 'msg-restore',
      missingSlots: ['MEAL_TYPE'],
      quickReplies: ['早餐', '午餐', '晚餐']
    })
    expect(AgentDiagnosis.methods.missingSlotText.call(ctx, 'CUSTOMER_OR_ORDER')).toBe('客户或订单')
    expect(AgentDiagnosis.methods.missingSlotText.call(ctx, 'DATE_RANGE')).toBe('日期范围')
  })

  test('restores latest diagnosis result onto matching assistant message after refresh', () => {
    const ctx = createCtx()

    AgentDiagnosis.methods.applySessionDetail.call(ctx, {
      sessionId: 'session-3',
      stage: 'ANSWERED',
      currentSlots: { customerCode: 'C10003', recordDate: '2026-07-09', mealType: 'LUNCH' },
      latestDiagnosisResult: {
        requestId: 'req-3',
        summary: '命中客户排除日期',
        confidence: 'HIGH',
        mealType: 'LUNCH'
      },
      messages: [
        { role: 'USER', requestId: 'req-3', content: '查 C10003 明天午餐', conversationStage: 'READY' },
        { role: 'ASSISTANT', requestId: 'req-3', content: '已完成诊断', conversationStage: 'ANSWERED' }
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
      stage: 'ANSWERED',
      currentSlots: { customerCode: 'C10004', recordDate: '2026-07-09', mealType: 'DINNER' },
      latestDiagnosisResult: {
        requestId: 'req-4',
        summary: '命中订单过期',
        confidence: 'HIGH',
        mealType: 'DINNER'
      },
      messages: [
        { role: 'USER', requestId: 'req-4', content: '查 C10004 明天晚餐', conversationStage: 'READY' }
      ]
    })

    expect(ctx.messages).toHaveLength(2)
    expect(ctx.messages[1].role).toBe('assistant')
    expect(ctx.messages[1].result.summary).toBe('命中订单过期')
  })

  test('filters sessions by keyword and formats session labels', () => {
    const ctx = createCtx()
    ctx.sessions = [
      { sessionId: 'session-1', title: 'C10001 午餐排查', customerCode: 'C10001', mealType: 'LUNCH', lastSummary: '命中排除日期', lastMessageTime: '2026-07-08 12:30:00' },
      { sessionId: 'session-2', title: 'C10002 晚餐排查', customerCode: 'C10002', mealType: 'DINNER', lastSummary: '订单过期' }
    ]
    ctx.sessionKeyword = '10001'

    expect(ctx.filteredSessions).toHaveLength(2)
    expect(ctx.filteredSessions[0].sessionId).toBe('session-1')
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
    expect(ctx.loadSessions).toHaveBeenCalledWith({ reset: true, selectCurrent: false })
  })
})
