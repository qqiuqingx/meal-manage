<template>
  <div class="app-container agent-diagnosis">
    <div class="chat-shell">
      <div class="chat-header">
        <div>
          <div class="title">智能排查助手</div>
          <div class="subtitle">AI 基于当前业务数据和规则生成诊断建议，请结合证据人工确认。</div>
        </div>
        <el-button size="small" plain icon="el-icon-delete" @click="clearSession">清空会话</el-button>
      </div>

      <div ref="messageList" class="message-list">
        <div
          v-for="(message, index) in messages"
          :key="index"
          class="message-row"
          :class="message.role === 'user' ? 'message-row-user' : 'message-row-assistant'"
        >
          <div class="message-bubble" :class="message.role === 'user' ? 'user-bubble' : 'assistant-bubble'">
            <div class="message-content">{{ message.content }}</div>
            <div v-if="message.slots" class="slot-line">
              <el-tag v-if="message.slots.customerId || message.slots.customerCode" size="mini">客户：{{ message.slots.customerCode || message.slots.customerId }}</el-tag>
              <el-tag v-if="message.slots.recordDate" size="mini" type="info">日期：{{ message.slots.recordDate }}</el-tag>
              <el-tag v-if="message.slots.mealType" size="mini" type="info">餐次：{{ mealTypeText(message.slots.mealType) }}</el-tag>
            </div>
            <div v-if="message.result" class="result-panel">
              <div class="result-header">
                <span>AI 诊断结果</span>
                <el-tag v-if="message.result.fallback" type="warning" size="small">兜底结果</el-tag>
                <el-tag v-else type="success" size="small">AI 建议</el-tag>
              </div>
              <el-alert
                title="以下为 AI 基于当前业务数据和规则生成的诊断建议，请结合证据人工确认。"
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
              <el-empty v-if="!message.result.reasons || message.result.reasons.length === 0" description="暂无原因明细" />
              <el-collapse v-else>
                <el-collapse-item v-for="reason in message.result.reasons" :key="reason.code" :name="reason.code">
                  <template slot="title">
                    <el-tag :type="levelTag(reason.level)" size="small">{{ reason.level || 'LOW' }}</el-tag>
                    <span class="reason-title">{{ reason.title || reason.code }}</span>
                  </template>
                  <div class="reason-desc">{{ reason.description }}</div>
                  <div class="reason-suggestion">建议：{{ reason.suggestion || '请人工继续核对。' }}</div>
                  <el-table v-if="reason.evidence && reason.evidence.length" :data="reason.evidence" size="mini" border>
                    <el-table-column prop="label" label="证据" width="180" />
                    <el-table-column prop="value" label="值" />
                  </el-table>
                </el-collapse-item>
              </el-collapse>
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

      <div class="quick-replies">
        <el-button
          v-for="reply in quickReplies"
          :key="reply"
          size="mini"
          plain
          @click="sendQuickReply(reply)"
        >
          {{ reply }}
        </el-button>
      </div>

      <div class="composer">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          resize="none"
          placeholder="例如：帮我看下客户 C10001 明天午餐为什么没排出来"
          @keyup.enter.native.exact.prevent="sendMessage"
        />
        <el-button type="primary" :loading="loading" icon="el-icon-s-promotion" @click="sendMessage">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { chatMealPlan } from '@/api/agentDiagnosis'

function welcomeMessage() {
  return {
    role: 'assistant',
    content: '你好，我是智能排查助手。请描述要排查的客户、日期和餐次，例如：帮我看下客户 C10001 明天午餐为什么没排出来。',
    status: 'ANSWERED'
  }
}

export default {
  name: 'AgentDiagnosis',
  data() {
    return {
      loading: false,
      sessionId: null,
      inputMessage: '',
      slots: {},
      quickReplies: ['今天', '明天', '早餐', '午餐', '晚餐', '重新排查'],
      messages: [welcomeMessage()]
    }
  },
  methods: {
    async sendMessage() {
      const message = (this.inputMessage || '').trim()
      if (!message) {
        this.$message.warning('请输入排查诉求')
        return
      }
      this.messages.push({ role: 'user', content: message })
      this.inputMessage = ''
      this.loading = true
      this.scrollToBottom()
      try {
        const response = await chatMealPlan({ sessionId: this.sessionId, message })
        this.addAssistantResponse(response)
      } catch (e) {
        this.messages.push({
          role: 'assistant',
          content: '智能排查服务暂不可用，请稍后重试或先人工核对。',
          status: 'ERROR'
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
    addAssistantResponse(response) {
      this.sessionId = response.sessionId || this.sessionId
      this.slots = response.slots || this.slots || {}
      this.quickReplies = response.quickReplies && response.quickReplies.length
        ? response.quickReplies
        : ['今天', '明天', '早餐', '午餐', '晚餐', '重新排查']
      this.messages.push({
        role: 'assistant',
        content: response.assistantMessage || '已收到，请继续补充排查信息。',
        status: response.status,
        slots: response.slots,
        result: response.diagnosisResult
      })
    },
    clearSession() {
      this.sessionId = null
      this.inputMessage = ''
      this.slots = {}
      this.quickReplies = ['今天', '明天', '早餐', '午餐', '晚餐', '重新排查']
      this.messages = [welcomeMessage()]
      this.scrollToBottom()
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
    levelTag(level) {
      if (level === 'HIGH') return 'danger'
      if (level === 'MEDIUM') return 'warning'
      return 'info'
    },
    shortDigest(value) {
      return value ? value.slice(0, 12) : '-'
    }
  }
}
</script>

<style scoped>
.agent-diagnosis {
  min-height: calc(100vh - 84px);
}

.chat-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 116px);
  min-height: 620px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
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
  overflow-y: auto;
  padding: 20px;
  background: #f7f8fa;
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
  max-width: 78%;
  padding: 12px 14px;
  border-radius: 6px;
  line-height: 1.7;
  font-size: 14px;
  word-break: break-word;
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

.slot-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.result-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 600;
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
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
}

.reason-title {
  margin-left: 8px;
  font-weight: 600;
}

.reason-desc,
.reason-suggestion {
  margin-bottom: 10px;
  color: #606266;
  line-height: 1.7;
}

.quick-replies {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 20px 0;
  border-top: 1px solid #ebeef5;
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

@media (max-width: 768px) {
  .chat-shell {
    height: calc(100vh - 96px);
    min-height: 520px;
  }

  .message-bubble {
    max-width: 92%;
  }

  .composer {
    grid-template-columns: 1fr;
  }

  .composer .el-button {
    height: 40px;
  }
}
</style>
