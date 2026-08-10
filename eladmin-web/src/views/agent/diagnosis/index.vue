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
          <el-button size="small" plain :disabled="!activeSessionId" @click="archiveCurrentSession">
            {{ activeSessionArchived ? '恢复会话' : '归档会话' }}
          </el-button>
          <el-button size="small" plain :disabled="activeSessionArchived || loading" @click="sendQuickReply('重新排查')">重新排查</el-button>
          <el-button size="small" plain icon="el-icon-refresh" :loading="sessionsLoading" @click="loadSessions({ reset: true, selectCurrent: false })">刷新会话</el-button>
        </div>
      </div>

      <div class="workspace-body">
        <AgentSessionSidebar
          :sessions="filteredSessions"
          :loading="sessionsLoading"
          :keyword="sessionKeyword"
          :archived="sessionArchivedView"
          :active-session-id="activeSessionId"
          :has-more="sessionHasMore"
          :session-option-label="sessionOptionLabel"
          :format-session-time="formatSessionTime"
          @keyword-input="sessionKeyword = $event; handleSessionKeywordInput()"
          @search="searchSessionsNow"
          @view-change="sessionArchivedView = $event; handleSessionViewChange()"
          @select="handleSessionChange"
          @load-more="loadMoreSessions"
        />

        <div class="chat-panel">
          <AgentMessageList
            ref="messageList"
            :messages="messages"
            :loading="loading"
            :archived="activeSessionArchived"
            :is-latest-assistant="isLatestAssistant"
            :render-assistant-message="renderAssistantMessage"
            :stage-text="stageText"
            :missing-slot-text="missingSlotText"
            :meal-type-text="mealTypeText"
            :slot-label="slotLabel"
            :confidence-tag="confidenceTag"
            :level-tag="levelTag"
            :has-business-query-result="hasBusinessQueryResult"
            :query-warning-text="queryWarningText"
            :presentation-for-card="presentationForCard"
            :is-candidate-card="isCandidateCard"
            :navigation-targets="navigationTargets"
            @quick-reply="sendQuickReply"
            @retry="retryMessage"
            @copy="copyAssistantMessage"
            @navigate="navigateTarget"
            @select-customer="selectCustomerCandidate"
            @feedback="openFeedbackDialog($event.result, $event.accepted)"
          />

          <AgentComposer
            :draft="inputMessage"
            :loading="loading"
            :archived="activeSessionArchived"
            @input="inputMessage = $event"
            @send="sendMessage"
          />
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
import AgentComposer from './components/AgentComposer.vue'
import AgentMessageList from './components/AgentMessageList.vue'
import AgentSessionSidebar from './components/AgentSessionSidebar.vue'
import { mapLegacyCards } from './utils/agentPresentationCompatibility'
import { businessWarningMessage } from './utils/agentWarningMessages'
import { renderAssistantMessage as renderAssistantMessageHtml } from './utils/assistantMessageRenderer'

function welcomeMessage() {
  return {
    role: 'assistant',
    content: '你好，我是智能客服助手。你可以查询客户、订单、排餐、核销、退餐或运营统计，例如“B3303 还有多少餐”或“今天待核销客户有多少？”。',
    status: 'ANSWERED',
    stage: 'READY',
    missingSlots: [],
    quickReplies: []
  }
}

export default {
  name: 'AgentDiagnosis',
  components: {
    AgentComposer,
    AgentMessageList,
    AgentSessionSidebar
  },
  data() {
    return {
      loading: false,
      sessionId: null,
      activeSessionId: null,
      activeSessionArchived: false,
      sessions: [],
      sessionsLoading: false,
      sessionCreating: false,
      sessionRenameLoading: false,
      sessionKeyword: '',
      sessionArchivedView: false,
      sessionPage: 0,
      sessionTotal: 0,
      sessionHasMore: false,
      sessionSearchTimer: null,
      sessionQuerySerial: 0,
      inputMessage: '',
      slots: {},
      slotConfidence: {},
      missingSlots: [],
      conversationStage: 'READY',
      currentDiagnosis: null,
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
      const seen = Object.create(null)
      return (this.sessions || []).filter((session, index) => {
        if (!session) return false
        const key = session.sessionId || `session-${index}`
        if (seen[key]) return false
        seen[key] = true
        return true
      })
    }
  },
  mounted() {
    this.loadSessions({ reset: true, selectCurrent: true })
  },
  beforeDestroy() {
    if (this.sessionSearchTimer) {
      clearTimeout(this.sessionSearchTimer)
    }
  },
  methods: {
    /** 按当前归档视图和关键字分页加载会话，并忽略已经过期的响应。 */
    async loadSessions(options) {
      const legacyCall = typeof options === 'boolean'
      const reset = legacyCall ? true : (!options || options.reset !== false)
      const selectCurrent = legacyCall ? options === true : !!(options && options.selectCurrent)
      if (reset) {
        this.sessionPage = 0
      } else {
        this.sessionPage += 1
      }
      const requestPage = this.sessionPage
      const requestSerial = ++this.sessionQuerySerial
      const keyword = (this.sessionKeyword || '').trim()
      this.sessionsLoading = true
      try {
        const params = {
          archived: this.sessionArchivedView,
          page: requestPage,
          size: 20
        }
        if (keyword) {
          params.keyword = keyword
        }
        const response = await queryChatSessions(params)
        if (requestSerial !== this.sessionQuerySerial) {
          return false
        }
        const pageContent = this.extractPageContent(response)
        this.sessions = reset
          ? this.mergeSessionSummaries([], pageContent)
          : this.mergeSessionSummaries(this.sessions, pageContent)
        this.sessionTotal = this.extractPageTotal(response)
        this.sessionHasMore = this.sessionTotal > 0
          ? this.sessions.length < this.sessionTotal
          : pageContent.length >= 20
        if (selectCurrent) {
          if (this.activeSessionId && this.sessions.some(item => item.sessionId === this.activeSessionId)) {
            await this.handleSessionChange(this.activeSessionId)
          } else if (this.sessions.length) {
            await this.handleSessionChange(this.sessions[0].sessionId)
          } else {
            this.resetSessionState()
          }
        }
        return true
      } catch (e) {
        if (requestSerial === this.sessionQuerySerial) {
          this.$message.error('会话列表加载失败')
        }
        return false
      } finally {
        if (requestSerial === this.sessionQuerySerial) {
          this.sessionsLoading = false
        }
      }
    },
    /** 合并分页结果并按 sessionId 去重，避免重复加载或旧响应造成重复项。 */
    mergeSessionSummaries(existing, incoming) {
      const result = []
      const seen = Object.create(null)
      ;(existing || []).concat(incoming || []).forEach((session, index) => {
        if (!session) return
        const key = session.sessionId || `session-${index}`
        if (seen[key]) return
        seen[key] = true
        result.push(session)
      })
      return result
    },
    /** 读取后端标准分页总数，兼容历史数组响应。 */
    extractPageTotal(response) {
      if (!response || Array.isArray(response)) return 0
      const total = response.totalElements !== undefined ? response.totalElements : response.total
      return Number(total) >= 0 ? Number(total) : 0
    },
    /** 关键字输入使用 300ms 防抖，避免每个按键都请求服务端。 */
    handleSessionKeywordInput() {
      if (this.sessionSearchTimer) {
        clearTimeout(this.sessionSearchTimer)
      }
      this.sessionSearchTimer = setTimeout(() => {
        this.sessionSearchTimer = null
        this.searchSessionsNow()
      }, 300)
    },
    /** 回车立即执行当前关键字查询，并取消尚未触发的防抖任务。 */
    searchSessionsNow() {
      if (this.sessionSearchTimer) {
        clearTimeout(this.sessionSearchTimer)
        this.sessionSearchTimer = null
      }
      return this.loadSessions({ reset: true, selectCurrent: false })
    },
    /** 切换进行中/已归档视图后从第一页重新加载并选中可查看的会话。 */
    handleSessionViewChange() {
      this.activeSessionArchived = !!this.sessionArchivedView && !!this.activeSessionId
      return this.loadSessions({ reset: true, selectCurrent: true })
    },
    /** 追加下一页会话，已有请求或没有更多数据时不重复发起请求。 */
    loadMoreSessions() {
      if (this.sessionsLoading || !this.sessionHasMore) {
        return Promise.resolve(false)
      }
      return this.loadSessions({ reset: false, selectCurrent: false })
    },
    /** 加载选中会话详情，并同步归档状态以控制当前工作区的可写权限。 */
    async handleSessionChange(sessionId) {
      if (!sessionId) {
        this.resetSessionState()
        return
      }
      const summary = (this.sessions || []).find(item => item.sessionId === sessionId)
      this.activeSessionArchived = !!(summary && summary.archived)
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
    /** 保留显式创建接口供外部预置上下文使用，本页面的新建按钮只做本地重置。 */
    async createSession() {
      this.sessionCreating = true
      try {
        const session = await createChatSession({})
        if (session && session.sessionId) {
          this.sessionArchivedView = false
          this.sessions = [session].concat((this.sessions || []).filter(item => item.sessionId !== session.sessionId))
          this.activeSessionId = session.sessionId
          this.sessionId = session.sessionId
          this.activeSessionArchived = false
          this.resetSessionState(session.sessionId)
          return true
        } else {
          this.resetSessionState()
          return false
        }
      } catch (e) {
        this.$message.error('新建会话失败')
        return false
      } finally {
        this.sessionCreating = false
      }
    },
    /** 归档或恢复当前会话，完成后重新加载对应视图并切换可用会话。 */
    async archiveCurrentSession() {
      if (!this.activeSessionId) {
        return
      }
      const shouldArchive = !this.activeSessionArchived
      try {
        await archiveChatSession(this.activeSessionId, shouldArchive)
        this.$message.success(shouldArchive ? '会话已归档' : '会话已恢复')
        if (shouldArchive) {
          this.resetSessionState()
        } else {
          this.sessionArchivedView = false
          this.activeSessionArchived = false
        }
        await this.loadSessions({ reset: true, selectCurrent: true })
      } catch (e) {
        this.$message.error(shouldArchive ? '会话归档失败' : '会话恢复失败')
      }
    },
    /** 更新当前会话标题，并刷新当前归档视图中的列表摘要。 */
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
        await this.loadSessions({ reset: true, selectCurrent: false })
      } catch (e) {
        if (e && e.value !== undefined) {
          this.$message.error('会话改名失败')
        }
      } finally {
        this.sessionRenameLoading = false
      }
    },
    /** 发送新问题或重试问题，统一维护用户消息状态和请求幂等 ID。 */
    async sendMessage(explicitMessage, options) {
      if (this.loading) {
        return false
      }
      const requestText = typeof explicitMessage === 'string' ? explicitMessage : this.inputMessage
      const message = (requestText || '').trim()
      if (!message) {
        this.$message.warning('请输入排查诉求')
        return false
      }
      if (this.activeSessionArchived) {
        this.$message.warning('当前会话已归档，恢复后可继续')
        return false
      }
      const retryOf = options && options.retryOf
      const clientMessageId = this.generateClientMessageId()
      let userMessage = null
      if (retryOf) {
        retryOf.retrying = true
      } else {
        userMessage = {
          role: 'user',
          content: message,
          requestText: message,
          clientMessageId,
          status: 'SENDING'
        }
        this.messages.push(userMessage)
      }
      if (!retryOf) {
        this.inputMessage = ''
      }
      this.loading = true
      this.scrollToBottom()
      try {
        const response = await chatMealPlan({ sessionId: this.activeSessionId || null, clientMessageId, message })
        if (userMessage) {
          userMessage.status = response && response.status === 'ERROR' ? 'ERROR' : 'SENT'
        }
        if (retryOf) {
          retryOf.retrying = false
        }
        this.addAssistantResponse(response, message, clientMessageId)
        this.loadSessions({ reset: true, selectCurrent: false })
        return true
      } catch (e) {
        if (userMessage) {
          userMessage.status = 'ERROR'
        }
        if (retryOf) {
          retryOf.retrying = false
        }
        const sessionUnavailable = retryOf && this.isSessionUnavailableError(e)
        const errorMessage = this.addAssistantResponse({
          sessionId: this.activeSessionId,
          clientMessageId,
          status: 'ERROR',
          conversationStage: 'ERROR',
          assistantMessage: sessionUnavailable
            ? '原会话已归档或不存在，请新建会话后重试。'
            : '智能排查服务暂不可用，请稍后重试或先人工核对。',
          missingSlots: [],
          quickReplies: [],
          warnings: [sessionUnavailable ? 'AGENT_SESSION_UNAVAILABLE' : 'AGENT_SERVICE_UNAVAILABLE'],
          partial: true
        }, message, clientMessageId)
        if (sessionUnavailable) {
          errorMessage.retryText = ''
          this.$message.warning('原会话已不可用，请新建会话后重试')
        } else {
          this.$message.error('智能排查服务暂不可用，请稍后重试')
        }
        this.loadSessions({ reset: true, selectCurrent: false })
        return false
      } finally {
        this.loading = false
        this.scrollToBottom()
      }
    },
    /** 将受控快捷回复直接作为新消息发送，并遵守归档与加载互斥规则。 */
    sendQuickReply(reply) {
      if (this.loading) {
        return Promise.resolve(false)
      }
      if (this.activeSessionArchived) {
        this.$message.warning('当前会话已归档，恢复后可继续')
        return Promise.resolve(false)
      }
      if (reply === '清空会话') {
        this.clearSession()
        return Promise.resolve()
      }
      return this.sendMessage(reply)
    },
    /** 重试错误消息时复用原问题文本，但生成新的幂等 ID且不新增用户气泡。 */
    retryMessage(message) {
      if (this.loading || !message || !message.retryText) {
        return Promise.resolve(false)
      }
      if (this.activeSessionArchived) {
        this.$message.warning('当前会话已归档，恢复后可继续')
        return Promise.resolve(false)
      }
      if (!this.activeSessionId || (message.sessionId && message.sessionId !== this.activeSessionId)) {
        this.$message.warning('请切换到原会话或新建会话后重试')
        return Promise.resolve(false)
      }
      return this.sendMessage(message.retryText, { retryOf: message })
    },
    /** 识别会话已归档或不存在的响应，给出明确的新建会话引导。 */
    isSessionUnavailableError(error) {
      const status = error && error.response && error.response.status
        ? error.response.status
        : (error && error.status)
      const code = error && error.code
      return [404, 410].indexOf(Number(status)) >= 0 ||
        ['SESSION_NOT_FOUND', 'SESSION_ARCHIVED'].indexOf(code) >= 0
    },
    /** 仅接受受控候选卡片中的客户编号，避免使用内部 ID或姓名拼接查询。 */
    selectCustomerCandidate(candidate) {
      if (!candidate || typeof candidate.customerCode !== 'string' || !candidate.customerCode.trim()) {
        return Promise.resolve()
      }
      return this.sendMessage(`客户编号 ${candidate.customerCode.trim()}`)
    },
    /** 判断当前助手消息是否为消息列表中最后一条助手消息。 */
    isLatestAssistant(index) {
      for (let cursor = this.messages.length - 1; cursor >= 0; cursor--) {
        if (this.messages[cursor] && this.messages[cursor].role === 'assistant') {
          return cursor === index
        }
      }
      return false
    },
    /** 只有需要补充客户的候选列表才显示选择按钮，其他业务表格保持只读。 */
    isCandidateCard(message, card) {
      return !!(message && message.status === 'NEED_MORE_INFO' && card &&
        card.type === 'CUSTOMER_PROFILE_LIST')
    },
    /** 从固定业务字段提取导航所需的最小参数，不读取服务端路由或查询配置。 */
    navigationTargets(message) {
      const result = message && message.result ? message.result : {}
      const slots = message && message.slots ? message.slots : {}
      const row = this.firstBusinessRow(message)
      const sources = [result, slots, row]
      const firstValue = keys => {
        for (let index = 0; index < sources.length; index++) {
          const source = sources[index]
          if (!source) continue
          for (let keyIndex = 0; keyIndex < keys.length; keyIndex++) {
            const value = source[keys[keyIndex]]
            if (typeof value === 'string' && value.trim() && value.trim().length <= 80) {
              return value.trim()
            }
          }
        }
        return ''
      }
      const customerCode = firstValue(['customerCode'])
      const orderCode = firstValue(['orderCode'])
      const recordDate = firstValue(['recordDate', 'date'])
      const mealType = firstValue(['mealType']).toUpperCase()
      const targets = []
      if (customerCode) {
        targets.push({ kind: 'CUSTOMER_PROFILE', label: '客户档案', payload: { customerCode }})
      }
      if (orderCode || customerCode) {
        targets.push({ kind: 'CUSTOMER_ORDER', label: '客户订单', payload: { orderCode, customerCode }})
      }
      if (recordDate && ['BREAKFAST', 'LUNCH', 'DINNER'].indexOf(mealType) >= 0) {
        targets.push({
          kind: 'MEAL_PLAN',
          label: '排餐详情',
          payload: { date: recordDate, mealType }
        })
      }
      return targets
    },
    /** 读取候选卡片中的首行作为导航补充来源，仅访问受控结果容器。 */
    firstBusinessRow(message) {
      const cards = message && Array.isArray(message.cards) ? message.cards : []
      for (let index = 0; index < cards.length; index++) {
        const data = cards[index] && cards[index].data
        if (!data || typeof data !== 'object') continue
        if (Array.isArray(data.items) && data.items.length) return data.items[0]
        if (data.data && Array.isArray(data.data.items) && data.data.items.length) return data.data.items[0]
        if (data.data && typeof data.data === 'object') return data.data
        return data
      }
      return null
    },
    /** 复制助手业务正文，不把模型、工具追踪和稳定告警码带入剪贴板。 */
    async copyAssistantMessage(message) {
      const content = message && typeof message.content === 'string' ? message.content.trim() : ''
      if (!content) return false
      try {
        if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(content)
        } else if (typeof document !== 'undefined') {
          const textarea = document.createElement('textarea')
          textarea.value = content
          textarea.setAttribute('readonly', 'readonly')
          textarea.style.position = 'fixed'
          textarea.style.opacity = '0'
          document.body.appendChild(textarea)
          textarea.select()
          document.execCommand('copy')
          document.body.removeChild(textarea)
        } else {
          return false
        }
        this.$message.success('结论已复制')
        return true
      } catch (e) {
        this.$message.error('复制失败，请手动选择文本')
        return false
      }
    },
    /** 按固定业务白名单跳转，忽略响应中的 route、path、query 等任意导航字段。 */
    navigateTarget(target) {
      if (!target || !target.payload || !this.$router) return false
      const payload = target.payload
      if (target.kind === 'CUSTOMER_PROFILE' && payload.customerCode) {
        this.$router.push({ path: '/customer/profile', query: { customerCode: payload.customerCode }})
        return true
      }
      if (target.kind === 'CUSTOMER_ORDER' && (payload.orderCode || payload.customerCode)) {
        const query = {}
        if (payload.orderCode) query.orderCode = payload.orderCode
        if (payload.customerCode) query.customerCode = payload.customerCode
        this.$router.push({ path: '/customer/order', query })
        return true
      }
      if (target.kind === 'MEAL_PLAN' && payload.date && payload.mealType) {
        this.$router.push({
          path: '/meal/production-sheet',
          query: { date: payload.date, mealType: payload.mealType }
        })
        return true
      }
      return false
    },
    queryWarningText(message) {
      return businessWarningMessage(message && message.warnings, message && message.partial)
    },
    /** 判断消息是否包含需要面向业务用户展示的卡片或告警；工具追踪摘要只保留在日志和会话数据中。 */
    hasBusinessQueryResult(message) {
      return !!(
        (message.cards && message.cards.length) ||
        message.partial ||
        (message.warnings && message.warnings.length)
      )
    },
    /** 将实时响应归一化为可重试、可恢复且不泄露技术字段的前端消息对象。 */
    normalizeAssistantMessage(response, requestText, clientMessageId) {
      const source = response || {}
      const status = source.status || (source.conversationStage === 'NEED_MORE_INFO' ? 'NEED_MORE_INFO' : 'ANSWERED')
      const slots = source.slots || {}
      const warnings = Array.isArray(source.warnings) ? source.warnings : []
      return {
        requestId: source.requestId,
        clientMessageId: source.clientMessageId || clientMessageId,
        sessionId: source.sessionId || this.activeSessionId,
        role: 'assistant',
        content: source.assistantMessage || '已收到，请继续补充排查信息。',
        status,
        stage: source.conversationStage || status,
        requestText: requestText || '',
        retryText: status === 'ERROR' ? (requestText || '') : '',
        retrying: false,
        missingSlots: Array.isArray(source.missingSlots) ? source.missingSlots : [],
        quickReplies: Array.isArray(source.quickReplies) ? source.quickReplies : [],
        slotConfidence: source.slotConfidence || slots.slotConfidence || {},
        slots,
        slotSummary: source.slotSummary || null,
        result: source.diagnosisResult,
        facts: Array.isArray(source.facts) ? source.facts : [],
        cards: Array.isArray(source.cards) ? source.cards : [],
        presentations: Array.isArray(source.presentations) ? source.presentations : [],
        toolFacts: Array.isArray(source.toolFacts) ? source.toolFacts : [],
        toolTraceSummary: Array.isArray(source.toolTraceSummary) ? source.toolTraceSummary : [],
        warnings,
        cached: source.cached === true,
        partial: source.partial === true,
        queriedAt: source.queriedAt
      }
    },
    /** 保存助手响应中的卡片和展示描述，供本条消息按调用 ID 关联渲染。 */
    addAssistantResponse(response, requestText, clientMessageId) {
      const message = this.normalizeAssistantMessage(response, requestText, clientMessageId)
      this.activeSessionId = message.sessionId || this.activeSessionId
      this.sessionId = this.activeSessionId
      this.slots = message.slots || this.slots || {}
      this.slotConfidence = message.slotConfidence || {}
      this.missingSlots = message.missingSlots || []
      this.conversationStage = message.stage || this.conversationStage
      this.currentDiagnosis = message.result || this.currentDiagnosis
      this.messages.push(message)
      return message
    },
    /** 只重置本地聊天工作区，首条消息发送时再由主系统懒创建会话。 */
    async clearSession() {
      this.resetSessionState()
    },
    /** 重置会话上下文和消息列表，同时清除归档只读标记。 */
    resetSessionState(sessionId) {
      this.activeSessionId = sessionId || null
      this.sessionId = sessionId || null
      this.activeSessionArchived = false
      this.inputMessage = ''
      this.slots = {}
      this.slotConfidence = {}
      this.missingSlots = []
      this.conversationStage = 'READY'
      this.currentDiagnosis = null
      this.messages = [welcomeMessage()]
      this.scrollToBottom()
    },
    /** 将服务端会话详情恢复到工作区，并根据归档状态控制后续交互。 */
    applySessionDetail(detail) {
      this.activeSessionId = detail && detail.sessionId ? detail.sessionId : this.activeSessionId
      this.sessionId = this.activeSessionId
      this.activeSessionArchived = detail && detail.archived === true
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
        requestId: latestDiagnosisResult.requestId,
        sessionId: (detail && detail.sessionId) || this.activeSessionId,
        content: latestDiagnosisResult.summary || '已恢复最近一次诊断结果。',
        status: 'ANSWERED',
        stage: (detail && detail.stage) || 'ANSWERED',
        retryText: '',
        retrying: false,
        missingSlots: [],
        quickReplies: [],
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
        const role = (message.role || '').toLowerCase() === 'user' ? 'user' : 'assistant'
        return {
          requestId: message.requestId,
          clientMessageId: message.clientMessageId,
          sessionId: message.sessionId || this.activeSessionId,
          role,
          content: message.content,
          requestText: role === 'user' ? message.content : '',
          status: message.status || (role === 'user' ? 'SENT' : 'ANSWERED'),
          stage: message.conversationStage || (role === 'user' ? 'SENT' : 'ANSWERED'),
          retryText: '',
          retrying: false,
          missingSlots: Array.isArray(business.missingSlots)
            ? business.missingSlots
            : (message.missingSlots || []),
          quickReplies: Array.isArray(business.quickReplies)
            ? business.quickReplies
            : (message.quickReplies || []),
          slotConfidence: (message.slots && message.slots.slotConfidence) || {},
          slots: message.slots,
          result: message.diagnosisResult,
          facts: business.facts || [],
          cards: legacyState ? legacyState.cards : cards,
          presentations: storedPresentations.length ? storedPresentations : legacyState.presentations,
          toolFacts: business.toolFacts || [],
          toolTraceSummary: business.toolTraceSummary || message.toolSummary || [],
          warnings: business.warnings || message.warnings || [],
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
      if (session.orderCode) parts.splice(1, 0, session.orderCode)
      return parts.length ? parts.join(' / ') : '新会话'
    },
    /** 将后端时间格式化为统一的列表时间文本，空值不渲染占位符。 */
    formatSessionTime(value) {
      if (!value) return ''
      const text = String(value).replace('T', ' ')
      const matched = text.match(/^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2})/)
      return matched ? `${matched[1]} ${matched[2]}` : text
    },
    generateClientMessageId() {
      return `msg-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.messageList
        if (el) {
          const target = el.$el || el
          target.scrollTop = target.scrollHeight
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
        CUSTOMER_OR_ORDER: '客户或订单',
        RECORD_DATE: '日期',
        DATE_RANGE: '日期范围',
        MEAL_TYPE: '餐次',
        PACKAGE: '套餐',
        RULE_TOPIC: '规则主题'
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

}
</style>
