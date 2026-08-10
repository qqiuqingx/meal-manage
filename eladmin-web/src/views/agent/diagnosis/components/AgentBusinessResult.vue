<template>
  <div v-if="hasBusinessQueryResult(message)" class="insight-section business-query-card">
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
        :partial="message.partial"
        :warnings="message.warnings || []"
        :selectable="isCandidateCard(message, card) && !archived"
        @select="$emit('select-customer', $event)"
      />
    </div>
  </div>
</template>

<script>
import AgentPresentationCard from './AgentPresentationCard.vue'

export default {
  name: 'AgentBusinessResult',
  components: { AgentPresentationCard },
  props: {
    message: { type: Object, required: true },
    archived: { type: Boolean, default: false },
    hasBusinessQueryResult: { type: Function, required: true },
    queryWarningText: { type: Function, required: true },
    presentationForCard: { type: Function, required: true },
    isCandidateCard: { type: Function, required: true }
  }
}
</script>

<style scoped>
.insight-section {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

.block-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
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
</style>
