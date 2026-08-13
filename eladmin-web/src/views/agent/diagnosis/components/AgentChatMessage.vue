<template>
  <div class="message-row" :class="message.role === 'user' ? 'message-row-user' : 'message-row-assistant'">
    <div class="message-bubble" :class="message.role === 'user' ? 'user-bubble' : 'assistant-bubble'">
      <div
        v-if="message.role === 'assistant'"
        class="message-content markdown-body"
        v-html="renderAssistantMessage(message.content)"
      />
      <div v-else class="message-content">{{ message.content }}</div>
      <div v-if="(message.stage && message.stage !== 'ANSWERED') || (message.missingSlots && message.missingSlots.length)" class="message-meta">
        <el-tag v-if="message.stage && message.stage !== 'ANSWERED'" size="mini" type="info">阶段：{{ stageText(message.stage) }}</el-tag>
        <el-tag v-for="slot in message.missingSlots || []" :key="slot" size="mini" type="warning">
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
      <AgentDiagnosisResult
        v-if="message.result"
        :message="message"
        :meal-type-text="mealTypeText"
        :confidence-tag="confidenceTag"
        :level-tag="levelTag"
        @feedback="$emit('feedback', $event)"
      />
      <AgentBusinessResult
        v-if="message.role === 'assistant'"
        :message="message"
        :archived="archived"
        :has-business-query-result="hasBusinessQueryResult"
        :query-warning-text="queryWarningText"
        :presentation-for-card="presentationForCard"
        :is-candidate-card="isCandidateCard"
        @select-customer="$emit('select-customer', $event)"
      />
      <AgentTechnicalDetails v-if="message.role === 'assistant'" :message="message" />
      <AgentFormDraftCard
        v-if="message.role === 'assistant' && message.formDraftSummary"
        :summary="message.formDraftSummary"
        :actions="message.uiActions"
        :archived="archived"
        :loading="loading"
        @action="$emit('form-draft-action', $event)"
      />
      <AgentMessageActions
        v-if="message.role === 'assistant'"
        :message="message"
        :latest="latest"
        :loading="loading || archived"
        :navigation-targets="navigationTargets(message)"
        @quick-reply="$emit('quick-reply', $event)"
        @retry="$emit('retry')"
        @copy="$emit('copy')"
        @navigate="$emit('navigate', $event)"
      />
    </div>
  </div>
</template>

<script>
import AgentBusinessResult from './AgentBusinessResult.vue'
import AgentDiagnosisResult from './AgentDiagnosisResult.vue'
import AgentMessageActions from './AgentMessageActions.vue'
import AgentTechnicalDetails from './AgentTechnicalDetails.vue'
import AgentFormDraftCard from './AgentFormDraftCard.vue'

export default {
  name: 'AgentChatMessage',
  components: {
    AgentBusinessResult,
    AgentDiagnosisResult,
    AgentMessageActions,
    AgentTechnicalDetails,
    AgentFormDraftCard
  },
  props: {
    message: { type: Object, required: true },
    latest: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    archived: { type: Boolean, default: false },
    renderAssistantMessage: { type: Function, default: value => value || '' },
    stageText: { type: Function, default: value => value || '-' },
    missingSlotText: { type: Function, default: value => value || '-' },
    mealTypeText: { type: Function, default: value => value || '-' },
    slotLabel: { type: Function, default: value => value || '-' },
    confidenceTag: { type: Function, default: () => 'info' },
    levelTag: { type: Function, default: () => 'info' },
    hasBusinessQueryResult: { type: Function, required: true },
    queryWarningText: { type: Function, required: true },
    presentationForCard: { type: Function, required: true },
    isCandidateCard: { type: Function, required: true },
    navigationTargets: { type: Function, required: true }
  }
}
</script>

<style scoped>
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
.confidence-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.confidence-item {
  color: #606266;
}

@media (max-width: 768px) {
  .message-bubble {
    max-width: 100%;
  }
}
</style>
