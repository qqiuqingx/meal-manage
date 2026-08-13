<template>
  <div ref="messageList" class="message-list">
    <AgentChatMessage
      v-for="(message, index) in messages"
      :key="`${message.role}-${message.clientMessageId || message.requestId || index}`"
      :message="message"
      :latest="isLatestAssistant(index)"
      :loading="loading"
      :archived="archived"
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
      @quick-reply="$emit('quick-reply', $event)"
      @retry="$emit('retry', message)"
      @copy="$emit('copy', message)"
      @navigate="$emit('navigate', $event)"
      @select-customer="$emit('select-customer', $event)"
      @feedback="$emit('feedback', $event)"
      @form-draft-action="$emit('form-draft-action', $event)"
    />
    <div v-if="loading" class="message-row message-row-assistant">
      <div class="message-bubble assistant-bubble">
        <i class="el-icon-loading" />
        正在查询业务数据…
      </div>
    </div>
  </div>
</template>

<script>
import AgentChatMessage from './AgentChatMessage.vue'

export default {
  name: 'AgentMessageList',
  components: { AgentChatMessage },
  props: {
    messages: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    archived: { type: Boolean, default: false },
    isLatestAssistant: { type: Function, required: true },
    renderAssistantMessage: { type: Function, required: true },
    stageText: { type: Function, required: true },
    missingSlotText: { type: Function, required: true },
    mealTypeText: { type: Function, required: true },
    slotLabel: { type: Function, required: true },
    confidenceTag: { type: Function, required: true },
    levelTag: { type: Function, required: true },
    hasBusinessQueryResult: { type: Function, required: true },
    queryWarningText: { type: Function, required: true },
    presentationForCard: { type: Function, required: true },
    isCandidateCard: { type: Function, required: true },
    navigationTargets: { type: Function, required: true }
  }
}
</script>

<style scoped>
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  background: #f7f8fa;
}

.message-row {
  display: flex;
  margin-bottom: 14px;
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

.assistant-bubble {
  color: #303133;
  background: #fff;
  border: 1px solid #ebeef5;
}

@media (max-width: 768px) {
  .message-bubble {
    max-width: 100%;
  }
}
</style>
