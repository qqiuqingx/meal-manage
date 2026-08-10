<template>
  <div v-if="hasDetails" class="agent-technical-details">
    <el-button type="text" size="mini" @click="expanded = !expanded">
      {{ expanded ? '收起技术详情' : '技术详情' }}
    </el-button>
    <div v-if="expanded" class="technical-content">
      <div v-if="modelName || ruleDigest" class="technical-meta">
        <span v-if="modelName">模型：{{ modelName }}</span>
        <span v-if="ruleDigest">规则版本：{{ ruleDigest }}</span>
      </div>
      <div v-if="confidenceEntries.length" class="technical-section">
        <div class="technical-title">槽位置信度</div>
        <span v-for="entry in confidenceEntries" :key="entry.key" class="technical-item">
          {{ entry.label }}：{{ entry.value }}
        </span>
      </div>
      <div v-if="warningCodes.length" class="technical-section">
        <div class="technical-title">告警码</div>
        <span v-for="code in warningCodes" :key="code" class="technical-code">{{ code }}</span>
      </div>
      <div v-if="toolSummary.length" class="technical-section">
        <div class="technical-title">工具调用摘要</div>
        <el-table :data="toolSummary" size="mini" border>
          <el-table-column prop="toolName" label="工具" width="180" />
          <el-table-column prop="eventType" label="事件" width="150" />
          <el-table-column prop="resultCount" label="结果数" width="90" />
          <el-table-column prop="costMs" label="耗时(ms)" width="110" />
          <el-table-column prop="errorType" label="错误类型" />
        </el-table>
      </div>
      <div v-if="diagnosisTrace.length" class="technical-section">
        <div class="technical-title">诊断链路</div>
        <el-table :data="diagnosisTrace" size="mini" border>
          <el-table-column prop="eventType" label="事件" width="180" />
          <el-table-column prop="round" label="轮次" width="80" />
          <el-table-column prop="toolName" label="工具" width="180" />
          <el-table-column prop="toolNames" label="工具摘要" />
          <el-table-column prop="costMs" label="耗时(ms)" width="110" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script>
import { parseAgentWarning } from '../utils/agentWarningMessages'

const SLOT_LABELS = {
  customer: '客户',
  customerCode: '客户编号',
  recordDate: '日期',
  mealType: '餐次',
  orderCode: '订单编号'
}

export default {
  name: 'AgentTechnicalDetails',
  props: {
    message: { type: Object, default: () => ({}) }
  },
  data() {
    return { expanded: false }
  },
  computed: {
    /** 读取诊断结果中的技术字段，默认不在业务摘要中显示。 */
    diagnosisResult() {
      return this.message.result || {}
    },
    modelName() {
      return this.diagnosisResult.modelName || ''
    },
    ruleDigest() {
      return this.diagnosisResult.ruleVersionDigest || ''
    },
    confidenceEntries() {
      const confidence = this.message.slotConfidence || {}
      return Object.keys(confidence).map(key => ({
        key,
        label: SLOT_LABELS[key] || key,
        value: confidence[key]
      }))
    },
    warningCodes() {
      const warnings = Array.isArray(this.message.warnings) ? this.message.warnings : []
      const seen = {}
      return warnings.map(parseAgentWarning).map(item => item.code).filter(code => {
        if (!code || seen[code]) return false
        seen[code] = true
        return true
      })
    },
    toolSummary() {
      const resultSummary = this.diagnosisResult.toolCallSummary
      return Array.isArray(resultSummary) && resultSummary.length
        ? resultSummary
        : (this.message.toolTraceSummary || [])
    },
    diagnosisTrace() {
      return Array.isArray(this.diagnosisResult.diagnosisTrace)
        ? this.diagnosisResult.diagnosisTrace
        : []
    },
    /** 仅有技术字段时也保留入口，便于查看稳定告警码或调用轨迹。 */
    hasDetails() {
      return !!(this.modelName || this.ruleDigest || this.confidenceEntries.length ||
        this.warningCodes.length || this.toolSummary.length || this.diagnosisTrace.length)
    }
  }
}
</script>

<style scoped>
.agent-technical-details {
  margin-top: 8px;
}

.technical-content {
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

.technical-meta,
.technical-section {
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.technical-meta,
.technical-section {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.technical-section {
  display: block;
}

.technical-title {
  margin-bottom: 5px;
  color: #606266;
  font-weight: 600;
}

.technical-item,
.technical-code {
  display: inline-block;
  margin: 0 8px 5px 0;
}

.technical-code {
  padding: 2px 6px;
  border-radius: 3px;
  background: #fef0f0;
  color: #f56c6c;
  font-family: monospace;
}
</style>
