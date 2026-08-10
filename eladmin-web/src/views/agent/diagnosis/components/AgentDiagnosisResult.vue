<template>
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
    </div>
    <div class="feedback-actions">
      <el-button size="mini" plain icon="el-icon-check" @click="$emit('feedback', { result: message.result, accepted: 'ACCEPTED' })">采纳</el-button>
      <el-button size="mini" plain icon="el-icon-warning-outline" @click="$emit('feedback', { result: message.result, accepted: 'PARTIAL' })">部分正确</el-button>
      <el-button size="mini" plain icon="el-icon-close" @click="$emit('feedback', { result: message.result, accepted: 'REJECTED' })">不采纳</el-button>
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
  </div>
</template>

<script>
export default {
  name: 'AgentDiagnosisResult',
  props: {
    message: { type: Object, required: true },
    mealTypeText: { type: Function, default: value => value || '-' },
    confidenceTag: { type: Function, default: () => 'info' },
    levelTag: { type: Function, default: () => 'info' }
  }
}
</script>

<style scoped>
.result-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
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

.meta,
.feedback-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta {
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
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

.action-block {
  margin-top: 14px;
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
</style>
