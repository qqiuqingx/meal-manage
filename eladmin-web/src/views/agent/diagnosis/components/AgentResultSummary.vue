<template>
  <div class="agent-result-summary">
    <div v-if="fields.length" class="summary-grid">
      <div v-for="field in fields" :key="field.key" class="summary-item">
        <div class="summary-label">{{ field.label }}</div>
        <div class="summary-value">{{ field.value }}</div>
      </div>
    </div>
    <div v-else class="summary-empty">暂无摘要</div>
  </div>
</template>

<script>
import {
  formatFieldValue,
  isSafeLabel,
  isSafePath,
  isSupportedFormat,
  readPath
} from '../utils/agentPresentationFormatters'

export default {
  name: 'AgentResultSummary',
  props: {
    card: { type: Object, default: null },
    data: { type: [Object, Array], default: null },
    descriptor: { type: Object, default: null },
    summary: { type: Object, default: null }
  },
  computed: {
    sourceData() {
      if (this.data !== null && this.data !== undefined) {
        return this.data
      }
      return this.card && this.card.data ? this.card.data : {}
    },
    summaryDescriptor() {
      return this.summary || (this.descriptor && this.descriptor.summary) || null
    },
    summaryData() {
      const descriptor = this.summaryDescriptor
      if (!descriptor || !isSafePath(descriptor.dataPath)) {
        return null
      }
      return readPath(this.sourceData, descriptor.dataPath)
    },
    summaryRow() {
      if (Array.isArray(this.summaryData)) {
        return this.summaryData[0] || {}
      }
      return this.summaryData && Object.prototype.toString.call(this.summaryData) === '[object Object]'
        ? this.summaryData : {}
    },
    fields() {
      const descriptor = this.summaryDescriptor
      const configuredFields = descriptor && Array.isArray(descriptor.fields) ? descriptor.fields : []
      return configuredFields
        .filter(field => field && isSafePath(field.field) && isSafeLabel(field.label, 30) && isSupportedFormat(field.format))
        .slice(0, 20)
        .map((field, index) => ({
          key: `${field.field}-${index}`,
          label: field.label,
          value: formatFieldValue(this.summaryRow, field.field, field.format)
        }))
    }
  }
}
</script>

<style scoped>
.agent-result-summary {
  color: #303133;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.summary-item {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
}

.summary-label {
  margin-bottom: 5px;
  color: #909399;
  font-size: 12px;
}

.summary-value {
  max-height: 120px;
  overflow: auto;
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.summary-empty {
  padding: 16px;
  color: #909399;
  text-align: center;
}

@media (max-width: 768px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
