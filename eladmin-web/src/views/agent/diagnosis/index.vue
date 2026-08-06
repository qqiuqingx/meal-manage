<template>
  <div class="app-container agent-diagnosis">
    <div class="workspace-shell">
      <div class="workspace-header">
        <div>
          <div class="title">智能客服助手</div>
          <div class="subtitle">支持客户、订单、排餐、核销和退餐只读查询；排餐诊断结论请结合证据人工确认。</div>
        </div>
        <div class="header-actions">
          <el-button size="small" plain icon="el-icon-plus" :loading="sessionCreating" @click="clearSession">新建会话</el-button>
          <el-button size="small" plain :disabled="!activeSessionId" :loading="sessionRenameLoading" @click="renameCurrentSession">改名</el-button>
          <el-button size="small" plain :disabled="!activeSessionId" @click="archiveCurrentSession">归档会话</el-button>
          <el-button size="small" plain @click="sendQuickReply('重新排查')">重新排查</el-button>
          <el-button size="small" plain icon="el-icon-refresh" :loading="sessionsLoading" @click="loadSessions(false)">刷新会话</el-button>
        </div>
      </div>

      <div class="workspace-body">
        <aside class="session-panel">
          <div class="session-toolbar">
            <el-input
              v-model="sessionKeyword"
              size="small"
              clearable
              placeholder="搜索客户编号或标题"
            >
              <i slot="prefix" class="el-input__icon el-icon-search" />
            </el-input>
          </div>
          <div class="session-list">
            <div
              v-for="session in filteredSessions"
              :key="session.sessionId"
              class="session-item"
              :class="{ 'session-item-active': session.sessionId === activeSessionId }"
              @click="handleSessionChange(session.sessionId)"
            >
              <div class="session-item-title">{{ session.title || sessionOptionLabel(session) }}</div>
            </div>
            <el-empty v-if="!sessionsLoading && !filteredSessions.length" description="暂无会话" :image-size="64" />
          </div>
        </aside>

        <div class="chat-panel">
          <div ref="messageList" class="message-list">
            <div
              v-for="(message, index) in messages"
              :key="index"
              class="message-row"
              :class="message.role === 'user' ? 'message-row-user' : 'message-row-assistant'"
            >
              <div class="message-bubble" :class="message.role === 'user' ? 'user-bubble' : 'assistant-bubble'">
                <div
                  v-if="message.role === 'assistant'"
                  class="message-content markdown-body"
                  v-html="renderAssistantMessage(message.content)"
                />
                <div v-else class="message-content">{{ message.content }}</div>
                <div v-if="message.stage || (message.missingSlots && message.missingSlots.length)" class="message-meta">
                  <el-tag v-if="message.stage" size="mini" type="info">阶段：{{ stageText(message.stage) }}</el-tag>
                  <el-tag
                    v-for="slot in message.missingSlots || []"
                    :key="slot"
                    size="mini"
                    type="warning"
                  >
                    待补充：{{ missingSlotText(slot) }}
                  </el-tag>
                </div>
                <div v-if="message.slots" class="slot-line">
                  <el-tag v-if="message.slots.customerId || message.slots.customerCode" size="mini">客户：{{ message.slots.customerCode || message.slots.customerId }}</el-tag>
                  <el-tag v-if="message.slots.recordDate" size="mini" type="info">日期：{{ message.slots.recordDate }}</el-tag>
                  <el-tag v-if="message.slots.mealType" size="mini" type="info">餐次：{{ mealTypeText(message.slots.mealType) }}</el-tag>
                </div>
                <div v-if="message.slotConfidence && Object.keys(message.slotConfidence).length" class="confidence-line">
                  <span v-for="(value, key) in message.slotConfidence" :key="key" class="confidence-item">
                    {{ slotLabel(key) }}：<el-tag size="mini" :type="confidenceTag(value)">{{ value }}</el-tag>
                  </span>
                </div>
                <div v-if="message.result" class="result-panel">
                  <div class="result-header">
                    <span>AI 诊断结果</span>
                    <div class="result-tags">
                      <el-tag size="small" :type="message.result.fallback ? 'warning' : 'success'">
                        {{ message.result.fallback ? '兜底结果' : 'AI 建议' }}
                      </el-tag>
                      <el-tag v-if="message.result.confidence" size="small" :type="confidenceTag(message.result.confidence)">
                        {{ message.result.confidence }}
                      </el-tag>
                    </div>
                  </div>
                  <el-alert
                    title="以下为 AI 基于当前业务数据和规则生成的诊断建议，请结合证据人工确认。"
                    type="warning"
                    :closable="false"
                    show-icon
                  />
                  <el-alert
                    v-if="message.result.fallbackReason"
                    class="fallback-alert"
                    :title="message.result.fallbackReason"
                    type="warning"
                    :closable="false"
                    show-icon
                  />
                  <div class="summary">{{ message.result.summary || '暂无诊断摘要' }}</div>
                  <div class="meta">
                    <span>客户：{{ message.result.customerName || message.result.customerId || '-' }}</span>
                    <span>日期：{{ message.result.recordDate || '-' }}</span>
                    <span>餐次：{{ mealTypeText(message.result.mealType) }}</span>
                    <span>规则版本：{{ shortDigest(message.result.ruleVersionDigest) }}</span>
                    <span>模型：{{ message.result.modelName || '-' }}</span>
                  </div>
                  <div class="result-links">
                    <el-button
                      size="mini"
                      plain
                      icon="el-icon-user"
                      :disabled="!hasCustomerTarget(message.result)"
                      @click="openCustomerProfile(message.result)"
                    >
                      客户档案
                    </el-button>
                    <el-button
                      size="mini"
                      plain
                      icon="el-icon-document"
                      :disabled="!hasCustomerTarget(message.result)"
                      @click="openCustomerOrders(message.result)"
                    >
                      客户订单
                    </el-button>
                    <el-button
                      size="mini"
                      plain
                      icon="el-icon-date"
                      :disabled="!message.result.recordDate || !message.result.mealType"
                      @click="openMealPlan(message.result)"
                    >
                      排餐详情
                    </el-button>
                  </div>
                  <div class="feedback-actions">
                    <el-button size="mini" plain icon="el-icon-check" @click="openFeedbackDialog(message.result, 'ACCEPTED')">采纳</el-button>
                    <el-button size="mini" plain icon="el-icon-warning-outline" @click="openFeedbackDialog(message.result, 'PARTIAL')">部分正确</el-button>
                    <el-button size="mini" plain icon="el-icon-close" @click="openFeedbackDialog(message.result, 'REJECTED')">不采纳</el-button>
                  </div>

                  <div v-if="message.result.nextActions && message.result.nextActions.length" class="action-block">
                    <div class="block-title">建议动作</div>
                    <ul class="action-list">
                      <li v-for="(action, actionIndex) in message.result.nextActions" :key="actionIndex">{{ action }}</li>
                    </ul>
                  </div>

                  <el-empty v-if="!message.result.reasons || message.result.reasons.length === 0" description="暂无原因明细" />
                  <el-collapse v-else>
                    <el-collapse-item v-for="reason in message.result.reasons" :key="reason.code" :name="reason.code">
                      <template slot="title">
                        <el-tag :type="levelTag(reason.level)" size="small">{{ reason.level || 'LOW' }}</el-tag>
                        <span class="reason-title">{{ reason.title || reason.code }}</span>
                        <el-tag v-if="reason.confidence" size="mini" :type="confidenceTag(reason.confidence)">{{ reason.confidence }}</el-tag>
                      </template>
                      <div class="reason-desc">{{ reason.description }}</div>
                      <div class="reason-suggestion"><el-tag size="mini" type="warning">{{ reason.suggestionType || 'AI_SUGGESTION' }}</el-tag> 建议：{{ reason.suggestion || '请人工继续核对。' }}</div>
                      <div v-if="reason.ruleIds && reason.ruleIds.length" class="reason-ruleids">
                        规则：{{ reason.ruleIds.join(' / ') }}
                      </div>
                      <ul v-if="reason.nextActions && reason.nextActions.length" class="action-list compact-list">
                        <li v-for="(action, reasonActionIndex) in reason.nextActions" :key="reasonActionIndex">{{ action }}</li>
                      </ul>
                      <el-table v-if="reason.evidence && reason.evidence.length" :data="reason.evidence" size="mini" border>
                        <el-table-column prop="label" label="证据" width="180" />
                        <el-table-column prop="value" label="值" />
                      </el-table>
                    </el-collapse-item>
                  </el-collapse>

                  <div class="inspector-actions">
                    <el-button size="mini" plain @click="togglePanel('toolSummaryExpanded')">
                      {{ toolSummaryExpanded ? '收起工具摘要' : '展开工具摘要' }}
                    </el-button>
                    <el-button size="mini" plain @click="togglePanel('traceExpanded')">
                      {{ traceExpanded ? '收起诊断链路' : '展开诊断链路' }}
                    </el-button>
                  </div>

                  <div v-if="toolSummaryExpanded && message.result.toolCallSummary && message.result.toolCallSummary.length" class="trace-block">
                    <div class="block-title">工具调用摘要</div>
                    <el-table :data="message.result.toolCallSummary" size="mini" border>
                      <el-table-column prop="toolName" label="工具" width="180" />
                      <el-table-column prop="eventType" label="事件" width="150" />
                      <el-table-column prop="resultCount" label="结果数" width="90" />
                      <el-table-column prop="costMs" label="耗时(ms)" width="110" />
                      <el-table-column prop="errorType" label="错误类型" />
                    </el-table>
                  </div>

                  <div v-if="traceExpanded && message.result.diagnosisTrace && message.result.diagnosisTrace.length" class="trace-block">
                    <div class="block-title">诊断链路</div>
                    <el-table :data="message.result.diagnosisTrace" size="mini" border>
                      <el-table-column prop="eventType" label="事件" width="180" />
                      <el-table-column prop="round" label="轮次" width="80" />
                      <el-table-column prop="toolName" label="工具" width="180" />
                      <el-table-column prop="toolNames" label="工具摘要" />
                      <el-table-column prop="costMs" label="耗时(ms)" width="110" />
                    </el-table>
                  </div>
                </div>
                <div v-if="(message.cards && message.cards.length) || (message.presentations && message.presentations.length) || (message.toolTraceSummary && message.toolTraceSummary.length) || message.partial || (message.warnings && message.warnings.length)" class="insight-section business-query-card">
                  <div class="block-title">业务查询结果</div>
                  <el-alert
                    v-if="message.partial || (message.warnings && message.warnings.length)"
                    :title="queryWarningText(message)"
                    type="warning"
                    :closable="false"
                    show-icon
                  />
                  <div v-if="message.queriedAt" class="query-time">查询时间：{{ message.queriedAt }}</div>
                  <div v-if="message.cards && message.cards.length" class="unified-tool-cards">
                    <AgentPresentationCard
                      v-for="(card, cardIndex) in message.cards"
                      :key="card.sourceToolCallId || cardIndex"
                      :card="card"
                      :presentation="presentationForCard(card, message.presentations)"
                      :warnings="message.warnings"
                      :partial="message.partial"
                      :queried-at="message.queriedAt"
                    />
                  </div>
                  <el-table v-if="message.toolTraceSummary && message.toolTraceSummary.length" :data="message.toolTraceSummary" size="mini" border>
                    <el-table-column prop="toolName" label="工具" width="190" />
                    <el-table-column prop="status" label="状态" width="100" />
                    <el-table-column prop="resultCount" label="结果数" width="90" />
                  </el-table>
                </div>
              </div>
            </div>
            <div v-if="loading" class="message-row message-row-assistant">
              <div class="message-bubble assistant-bubble">
                <i class="el-icon-loading" />
                正在排查...
              </div>
            </div>
          </div>

          <div class="chat-footer">
            <div class="composer">
              <el-input
                v-model="inputMessage"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                resize="none"
                placeholder="例如：B3303 还有多少餐、今天午餐排了吗、这笔订单什么时候到期"
                @keyup.enter.native.exact.prevent="sendMessage"
              />
              <el-button type="primary" :loading="loading" icon="el-icon-s-promotion" @click="sendMessage">发送</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      title="诊断反馈"
      :visible.sync="feedbackDialogVisible"
      width="560px"
      append-to-body
    >
      <div class="feedback-form">
        <el-radio-group v-model="feedbackForm.accepted" size="small">
          <el-radio-button label="ACCEPTED">采纳</el-radio-button>
          <el-radio-button label="PARTIAL">部分正确</el-radio-button>
          <el-radio-button label="REJECTED">不采纳</el-radio-button>
        </el-radio-group>
        <el-select
          v-model="feedbackForm.actualReasonCode"
          class="feedback-control"
          filterable
          allow-create
          clearable
          placeholder="真实原因"
        >
          <el-option
            v-for="code in feedbackReasonOptions"
            :key="code"
            :label="code"
            :value="code"
          />
        </el-select>
        <el-input
          v-model="feedbackForm.comment"
          class="feedback-control"
          type="textarea"
          :rows="3"
          maxlength="300"
          show-word-limit
          placeholder="备注"
        />
        <div class="feedback-predicted">预测原因：{{ feedbackReasonOptions.join(' / ') || '-' }}</div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="feedbackDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackLoading" @click="submitFeedback">提交反馈</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import {
  archiveChatSession,
  chatMealPlan,
  createChatSession,
  getChatSession,
  queryChatSessions,
  submitDiagnosisFeedback,
  updateChatSessionTitle
} from '@/api/agentDiagnosis'
import AgentPresentationCard from './components/AgentPresentationCard.vue'
import { mapLegacyCards } from './utils/agentPresentationCompatibility'
import { renderAssistantMessage as renderAssistantMessageHtml } from './utils/assistantMessageRenderer'

function welcomeMessage() {
  return {
    role: 'assistant',
    content: '你好，我是智能客服助手。你可以查询客户、订单、排餐、核销、退餐或运营统计，例如“B3303 还有多少餐”或“今天待核销客户有多少？”。',
    status: 'ANSWERED',
    stage: 'READY',
    missingSlots: []
  }
}

export default {
  name: 'AgentDiagnosis',
  components: {
    AgentPresentationCard
  },
  data() {
    return {
      loading: false,
      sessionId: null,
      activeSessionId: null,
      sessions: [],
      sessionsLoading: false,
      sessionCreating: false,
      sessionRenameLoading: false,
      sessionKeyword: '',
      inputMessage: '',
      slots: {},
      slotConfidence: {},
      missingSlots: [],
      conversationStage: 'READY',
      currentDiagnosis: null,
      toolSummaryExpanded: false,
      traceExpanded: false,
      feedbackDialogVisible: false,
      feedbackLoading: false,
      feedbackResult: null,
      feedbackDiagnosisResult: null,
      feedbackForm: {
        accepted: 'ACCEPTED',
        actualReasonCode: '',
        comment: ''
      },
      messages: [welcomeMessage()]
    }
  },
  computed: {
    feedbackReasonOptions() {
      return this.extractReasonCodes(this.feedbackDiagnosisResult)
    },
    filteredSessions() {
      const keyword = (this.sessionKeyword || '').trim().toLowerCase()
      if (!keyword) {
        return this.sessions || []
      }
      return (this.sessions || []).filter(session => {
        const title = (session.title || '').toLowerCase()
        const customerCode = (session.customerCode || '').toLowerCase()
        const summary = (session.lastSummary || '').toLowerCase()
        return title.includes(keyword) || customerCode.includes(keyword) || summary.includes(keyword)
      })
    }
  },
  mounted() {
    this.loadSessions(true)
  },
  methods: {
    async loadSessions(selectCurrent) {
      this.sessionsLoading = true
      try {
        const response = await queryChatSessions({ archived: false, page: 0, size: 20 })
        this.sessions = this.extractPageContent(response)
        if (selectCurrent) {
          if (this.activeSessionId && this.sessions.some(item => item.sessionId === this.activeSessionId)) {
            await this.handleSessionChange(this.activeSessionId)
          } else if (this.sessions.length) {
            await this.handleSessionChange(this.sessions[0].sessionId)
          } else {
            this.resetSessionState()
          }
        }
      } catch (e) {
        this.sessions = this.sessions || []
      } finally {
        this.sessionsLoading = false
      }
    },
    async handleSessionChange(sessionId) {
      if (!sessionId) {
        this.resetSessionState()
        return
      }
      this.sessionsLoading = true
      try {
        const detail = await getChatSession(sessionId)
        this.applySessionDetail(detail)
      } catch (e) {
        this.$message.error('会话加载失败')
      } finally {
        this.sessionsLoading = false
      }
    },
    async createSession() {
      this.sessionCreating = true
      try {
        const session = await createChatSession({})
        if (session && session.sessionId) {
          this.sessions = [session].concat((this.sessions || []).filter(item => item.sessionId !== session.sessionId))
          this.activeSessionId = session.sessionId
          this.sessionId = session.sessionId
          this.resetSessionState(session.sessionId)
        } else {
          this.resetSessionState()
        }
      } catch (e) {
        this.$message.error('新建会话失败')
      } finally {
        this.sessionCreating = false
      }
    },
    async archiveCurrentSession() {
      if (!this.activeSessionId) {
        return
      }
      try {
        await archiveChatSession(this.activeSessionId, true)
        this.$message.success('会话已归档')
        this.activeSessionId = null
        this.sessionId = null
        await this.loadSessions(true)
      } catch (e) {
        this.$message.error('会话归档失败')
      }
    },
    async renameCurrentSession() {
      if (!this.activeSessionId) {
        return
      }
      const current = (this.sessions || []).find(item => item.sessionId === this.activeSessionId) || {}
      try {
        const result = await this.$prompt('请输入会话标题', '会话改名', {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          inputValue: current.title || this.sessionOptionLabel(current),
          inputPattern: /\S+/,
          inputErrorMessage: '会话标题不能为空'
        })
        this.sessionRenameLoading = true
        await updateChatSessionTitle(this.activeSessionId, { title: result.value })
        this.$message.success('会话标题已更新')
        await this.loadSessions(false)
      } catch (e) {
        if (e && e.value !== undefined) {
          this.$message.error('会话改名失败')
        }
      } finally {
        this.sessionRenameLoading = false
      }
    },
    async sendMessage() {
      const message = (this.inputMessage || '').trim()
      if (!message) {
        this.$message.warning('请输入排查诉求')
        return
      }
      if (!this.activeSessionId) {
        await this.createSession()
      }
      const clientMessageId = this.generateClientMessageId()
      this.messages.push({ role: 'user', content: message })
      this.inputMessage = ''
      this.loading = true
      this.scrollToBottom()
      try {
        const response = await chatMealPlan({ sessionId: this.activeSessionId, clientMessageId, message })
        this.addAssistantResponse(response)
        this.loadSessions(false)
      } catch (e) {
        this.messages.push({
          role: 'assistant',
          content: '智能排查服务暂不可用，请稍后重试或先人工核对。',
          status: 'ERROR',
          stage: 'ERROR',
          missingSlots: []
        })
        this.$message.error('智能排查服务暂不可用，请稍后重试')
      } finally {
        this.loading = false
        this.scrollToBottom()
      }
    },
    sendQuickReply(reply) {
      if (reply === '清空会话') {
        this.clearSession()
        return Promise.resolve()
      }
      this.inputMessage = reply
      return this.sendMessage()
    },
    selectCustomerCandidate(candidate) {
      if (!candidate || (!candidate.customerCode && !candidate.customerId)) {
        return Promise.resolve()
      }
      this.inputMessage = candidate.customerCode
        ? `客户编号 ${candidate.customerCode}`
        : `客户ID ${candidate.customerId}`
      return this.sendMessage()
    },
    queryWarningText(message) {
      const warnings = message.warnings || []
      if (warnings.indexOf('TOOL_PERMISSION_DENIED') >= 0) {
        return '当前账号缺少该类业务数据的查询权限，系统未返回对象是否存在或相关明细。'
      }
      if (warnings.indexOf('TOOL_BUDGET_EXCEEDED') >= 0) {
        return '本轮查询达到安全调用上限，已返回可用部分结果。'
      }
      if (warnings.some(item => String(item).indexOf('TOOL_') >= 0 || String(item).indexOf('AGENT_QUERY_') >= 0)) {
        return '部分业务查询暂不可用，当前结果不构成完整结论。'
      }
      if (warnings.indexOf('MENU_RESULT_IMPLAUSIBLE') >= 0) {
        return '查询结果仅包含米饭类型菜品，不能确认这是完整公共菜单；请核对排期配置或改查客户实际排餐。'
      }
      if (message.partial) {
        return '结果已按安全上限截断，请缩小查询范围后重试。'
      }
      return warnings.join('；')
    },
    /** 保存助手响应中的卡片和展示描述，供本条消息按调用 ID 关联渲染。 */
    addAssistantResponse(response) {
      this.activeSessionId = response.sessionId || this.activeSessionId
      this.sessionId = this.activeSessionId
      this.slots = response.slots || this.slots || {}
      this.slotConfidence = response.slotConfidence || (response.slots && response.slots.slotConfidence) || {}
      this.missingSlots = response.missingSlots || []
      this.conversationStage = response.conversationStage || this.conversationStage
      this.currentDiagnosis = response.diagnosisResult || this.currentDiagnosis
      this.messages.push({
        role: 'assistant',
        content: response.assistantMessage || '已收到，请继续补充排查信息。',
        status: response.status,
        stage: response.conversationStage,
        missingSlots: response.missingSlots || [],
        slotConfidence: response.slotConfidence || (response.slots && response.slots.slotConfidence) || {},
        slots: response.slots,
        result: response.diagnosisResult,
        facts: response.facts || [],
        cards: response.cards || [],
        presentations: response.presentations || [],
        toolFacts: response.toolFacts || [],
        toolTraceSummary: response.toolTraceSummary || [],
        warnings: response.warnings || [],
        cached: response.cached === true,
        partial: response.partial === true,
        queriedAt: response.queriedAt
      })
    },
    async clearSession() {
      await this.createSession()
    },
    resetSessionState(sessionId) {
      this.activeSessionId = sessionId || null
      this.sessionId = sessionId || null
      this.inputMessage = ''
      this.slots = {}
      this.slotConfidence = {}
      this.missingSlots = []
      this.conversationStage = 'READY'
      this.currentDiagnosis = null
      this.toolSummaryExpanded = false
      this.traceExpanded = false
      this.messages = [welcomeMessage()]
      this.scrollToBottom()
    },
    applySessionDetail(detail) {
      this.activeSessionId = detail && detail.sessionId ? detail.sessionId : this.activeSessionId
      this.sessionId = this.activeSessionId
      this.slots = (detail && detail.currentSlots) || {}
      this.slotConfidence = (detail && detail.currentSlots && detail.currentSlots.slotConfidence) || {}
      this.missingSlots = []
      this.conversationStage = (detail && detail.stage) || 'READY'
      this.currentDiagnosis = (detail && detail.latestDiagnosisResult) || null
      this.messages = this.restoreSessionMessages(detail)
      this.scrollToBottom()
    },
    restoreSessionMessages(detail) {
      const mappedMessages = this.mapSessionMessages(detail && detail.messages)
      const latestDiagnosisResult = detail && detail.latestDiagnosisResult
      if (!latestDiagnosisResult) {
        return mappedMessages
      }
      if (mappedMessages.some(message => !!message.result)) {
        return mappedMessages
      }
      const matchedMessage = this.findDiagnosisMessage(mappedMessages, latestDiagnosisResult)
      if (matchedMessage) {
        matchedMessage.result = matchedMessage.result || latestDiagnosisResult
        return mappedMessages
      }
      return mappedMessages.concat([{
        role: 'assistant',
        content: latestDiagnosisResult.summary || '已恢复最近一次诊断结果。',
        status: 'ANSWERED',
        stage: (detail && detail.stage) || 'ANSWERED',
        missingSlots: [],
        slotConfidence: {},
        slots: (detail && detail.currentSlots) || {},
        result: latestDiagnosisResult
      }])
    },
    findDiagnosisMessage(messages, latestDiagnosisResult) {
      if (!messages || !messages.length || !latestDiagnosisResult) {
        return null
      }
      const diagnosisRequestId = latestDiagnosisResult.requestId
      if (diagnosisRequestId) {
        for (let i = messages.length - 1; i >= 0; i--) {
          const message = messages[i]
          if (message.role === 'assistant' && message.requestId === diagnosisRequestId) {
            return message
          }
        }
      }
      for (let i = messages.length - 1; i >= 0; i--) {
        const message = messages[i]
        if (message.role === 'assistant') {
          return message
        }
      }
      return null
    },
    /** 将持久化消息映射为前端只读状态；只有缺少 presentations 的旧消息才使用本地兼容层。 */
    mapSessionMessages(messages) {
      if (!messages || !messages.length) {
        return [welcomeMessage()]
      }
      return messages.map(message => {
        const business = message.businessResult || {}
        const cards = Array.isArray(business.cards) ? business.cards : []
        const businessPresentations = Array.isArray(business.presentations) ? business.presentations : []
        const messagePresentations = Array.isArray(message.presentations) ? message.presentations : []
        const storedPresentations = businessPresentations.length ? businessPresentations : messagePresentations
        const legacyState = storedPresentations.length ? null : mapLegacyCards(cards)
        return {
          requestId: message.requestId,
          role: (message.role || '').toLowerCase() === 'user' ? 'user' : 'assistant',
          content: message.content,
          status: message.status,
          stage: message.conversationStage,
          missingSlots: [],
          slotConfidence: (message.slots && message.slots.slotConfidence) || {},
          slots: message.slots,
          result: message.diagnosisResult,
          facts: business.facts || [],
          cards: legacyState ? legacyState.cards : cards,
          presentations: storedPresentations.length ? storedPresentations : legacyState.presentations,
          toolFacts: business.toolFacts || [],
          toolTraceSummary: business.toolTraceSummary || message.toolSummary || [],
          warnings: business.warnings || [],
          cached: business.cached === true,
          partial: business.partial === true,
          queriedAt: business.queriedAt
        }
      })
    },
    sessionOptionLabel(session) {
      if (!session) {
        return ''
      }
      if (session.title) {
        return session.title
      }
      const parts = [session.customerCode, session.recordDate, this.mealTypeText(session.mealType)].filter(Boolean)
      return parts.length ? parts.join(' / ') : session.sessionId
    },
    generateClientMessageId() {
      return `msg-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
    },
    togglePanel(key) {
      this[key] = !this[key]
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.messageList
        if (el) {
          el.scrollTop = el.scrollHeight
        }
      })
    },
    mealTypeText(value) {
      const map = {
        BREAKFAST: '早餐',
        LUNCH: '午餐',
        DINNER: '晚餐'
      }
      return map[value] || value || '-'
    },
    /** 通过 sourceToolCallId 关联同一响应中的事实卡片和展示描述。 */
    presentationForCard(card, presentations) {
      if (!card || !card.sourceToolCallId || !Array.isArray(presentations)) {
        return null
      }
      return presentations.find(item => item && item.sourceToolCallId === card.sourceToolCallId) || null
    },
    missingSlotText(value) {
      const map = {
        CUSTOMER: '客户',
        RECORD_DATE: '日期',
        MEAL_TYPE: '餐次'
      }
      return map[value] || value || '-'
    },
    stageText(value) {
      const map = {
        READY: '可查询',
        ANSWERED: '已回答',
        NEED_MORE_INFO: '需要补充',
        RESET: '已重置',
        ERROR: '异常'
      }
      return map[value] || value || '-'
    },
    slotLabel(key) {
      const map = {
        customer: '客户',
        recordDate: '日期',
        mealType: '餐次'
      }
      return map[key] || key
    },
    levelTag(level) {
      if (level === 'HIGH') return 'danger'
      if (level === 'MEDIUM') return 'warning'
      return 'info'
    },
    confidenceTag(level) {
      if (level === 'HIGH') return 'success'
      if (level === 'MEDIUM') return 'warning'
      return 'info'
    },
    shortDigest(value) {
      return value ? value.slice(0, 12) : '-'
    },
    /** 将助手返回的 Markdown 文本转换为安全 HTML，保留换行和列表展示。 */
    renderAssistantMessage(content) {
      return renderAssistantMessageHtml(content)
    },
    openFeedbackDialog(result, accepted) {
      this.feedbackDiagnosisResult = result
      const reasonCodes = this.extractReasonCodes(result)
      this.feedbackForm = {
        accepted,
        actualReasonCode: reasonCodes[0] || '',
        comment: ''
      }
      this.feedbackResult = null
      this.feedbackDialogVisible = true
    },
    async submitFeedback() {
      if (!this.feedbackDiagnosisResult) {
        return
      }
      this.feedbackLoading = true
      try {
        const result = this.feedbackDiagnosisResult
        const response = await submitDiagnosisFeedback({
          requestId: result.requestId,
          sessionId: this.sessionId,
          customerId: result.customerId,
          customerName: result.customerName,
          recordDate: result.recordDate,
          mealType: result.mealType,
          predictedReasonCodes: this.extractReasonCodes(result),
          accepted: this.feedbackForm.accepted,
          actualReasonCode: this.feedbackForm.actualReasonCode,
          comment: this.feedbackForm.comment
        })
        this.feedbackResult = response
        this.$message.success((response && response.message) || '诊断反馈已记录')
        this.feedbackDialogVisible = false
      } catch (e) {
        this.$message.error('诊断反馈提交失败')
      } finally {
        this.feedbackLoading = false
      }
    },
    extractReasonCodes(result) {
      if (!result || !result.reasons) {
        return []
      }
      return result.reasons
        .map(reason => reason && reason.code)
        .filter(Boolean)
    },
    extractPageContent(response) {
      if (!response) {
        return []
      }
      if (Array.isArray(response.content)) {
        return response.content
      }
      if (Array.isArray(response.records)) {
        return response.records
      }
      if (Array.isArray(response)) {
        return response
      }
      return []
    },
    hasCustomerTarget(result) {
      return !!(result && (result.customerId || result.customerCode || result.customerName))
    },
    openCustomerProfile(result) {
      const query = {}
      if (result.customerCode) query.customerCode = result.customerCode
      if (result.customerName) query.customerName = result.customerName
      if (result.customerId) query.customerId = result.customerId
      this.$router.push({ path: '/customer/profile', query })
    },
    openCustomerOrders(result) {
      const query = {}
      if (result.customerCode) query.customerCode = result.customerCode
      if (result.customerName) query.customerName = result.customerName
      if (result.customerId) query.customerId = result.customerId
      if (result.recordDate) query.scheduleDate = result.recordDate
      this.$router.push({ path: '/customer/order', query })
    },
    openMealPlan(result) {
      this.$router.push({
        path: '/meal/production-sheet',
        query: {
          date: result.recordDate,
          mealType: result.mealType
        }
      })
    }
  }
}
</script>

<style scoped>
.agent-diagnosis {
  min-height: calc(100vh - 84px);
}

.workspace-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 116px);
  min-height: 640px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
}

.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.workspace-body {
  flex: 1;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
}

.session-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
  border-right: 1px solid #ebeef5;
  background: #fafbfd;
}

.session-toolbar {
  padding: 14px;
  border-bottom: 1px solid #ebeef5;
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 12px;
}

.session-item {
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
}

.session-item + .session-item {
  margin-top: 4px;
}

.session-item-active {
  background: #ecf5ff;
}

.session-item-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.5;
  color: #303133;
}

.chat-panel {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  background: #f7f8fa;
}

.chat-footer {
  background: #fff;
  border-top: 1px solid #ebeef5;
  box-shadow: 0 -8px 24px rgba(17, 24, 39, 0.06);
}

.message-row {
  display: flex;
  margin-bottom: 14px;
}

.message-row-user {
  justify-content: flex-end;
}

.message-row-assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 88%;
  padding: 12px 14px;
  border-radius: 6px;
  line-height: 1.7;
  font-size: 14px;
  word-break: break-word;
}

.message-content {
  white-space: normal;
}

.markdown-body p {
  margin: 0;
}

.markdown-body p + p {
  margin-top: 10px;
}

.markdown-body ul,
.markdown-body ol {
  margin: 6px 0 0;
  padding-left: 22px;
}

.markdown-body li + li {
  margin-top: 4px;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin: 0 0 8px;
  line-height: 1.5;
}

.markdown-body blockquote {
  margin: 8px 0;
  padding-left: 10px;
  border-left: 3px solid #dcdfe6;
  color: #606266;
}

.markdown-body code {
  padding: 1px 4px;
  border-radius: 3px;
  background: #f2f6fc;
  color: #606266;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.markdown-body strong {
  font-weight: 600;
}

.user-bubble {
  color: #fff;
  background: #409eff;
}

.assistant-bubble {
  color: #303133;
  background: #fff;
  border: 1px solid #ebeef5;
}

.message-meta,
.slot-line,
.confidence-line,
.meta,
.result-links,
.feedback-actions,
.inspector-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.message-meta,
.slot-line,
.confidence-line {
  margin-top: 10px;
}

.confidence-item {
  color: #606266;
}

.result-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.insight-section {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  font-weight: 600;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.fallback-alert {
  margin-top: 12px;
}

.summary {
  margin: 16px 0;
  padding: 14px 16px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #303133;
  line-height: 1.7;
}

.meta {
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
}

.result-links {
  margin-bottom: 14px;
}

.feedback-actions {
  margin-bottom: 14px;
}

.reason-title {
  margin-left: 8px;
  margin-right: 8px;
  font-weight: 600;
}

.reason-desc,
.reason-suggestion,
.reason-ruleids {
  margin-bottom: 10px;
  color: #606266;
  line-height: 1.7;
}

.block-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.action-block,
.draft-block,
.trace-block {
  margin-top: 14px;
}

.draft-alert {
  margin-bottom: 10px;
}

.draft-preview {
  max-width: 360px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.draft-count {
  margin-top: 12px;
  color: #606266;
  font-size: 13px;
}

.confirm-dialog-body {
  color: #303133;
}

.confirm-title,
.confirm-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.confirm-title {
  font-weight: 600;
}

.confirm-meta {
  margin-top: 10px;
  color: #606266;
  font-size: 13px;
}

.confirm-comment {
  margin-top: 14px;
}

.preview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 14px 0;
}

.preview-grid pre {
  min-height: 120px;
  max-height: 240px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.confirm-result {
  margin-top: 12px;
}

.feedback-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feedback-control {
  width: 100%;
}

.feedback-predicted {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.action-list {
  margin: 0;
  padding-left: 18px;
  color: #606266;
  line-height: 1.8;
}

.compact-list {
  margin-top: 8px;
}

.composer {
  display: grid;
  grid-template-columns: 1fr 96px;
  gap: 12px;
  padding: 12px 20px 20px;
  align-items: stretch;
}

.composer .el-button {
  height: 54px;
}

.business-query-card .el-table {
  margin-top: 10px;
}

.query-time {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.unified-tool-cards {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.unified-tool-card {
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

.unified-tool-card-data {
  max-height: 260px;
  margin: 8px 0 0;
  padding: 8px;
  overflow: auto;
  background: #fff;
  color: #606266;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 1080px) {
  .workspace-body {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .chat-panel {
    border-right: 0;
  }
}

@media (max-width: 768px) {
  .workspace-shell {
    height: auto;
    min-height: 520px;
  }

  .workspace-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .workspace-body {
    grid-template-columns: 1fr;
    grid-template-rows: auto minmax(0, 1fr);
  }

  .session-panel {
    height: auto;
    max-height: 180px;
    border-right: 0;
    border-bottom: 1px solid #ebeef5;
  }

  .message-bubble {
    max-width: 100%;
  }

  .chat-footer {
    box-shadow: 0 -6px 16px rgba(17, 24, 39, 0.05);
  }

  .composer {
    grid-template-columns: 1fr;
  }

  .composer .el-button {
    height: 40px;
  }

  .preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
