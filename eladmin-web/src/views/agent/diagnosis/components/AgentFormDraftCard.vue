<template>
  <div v-if="summary" class="form-draft-card">
    <div class="draft-header">
      <div>
        <span class="draft-title">{{ typeText }}</span>
        <el-tag size="mini" :type="statusTag">{{ statusText }}</el-tag>
      </div>
      <span class="draft-version">版本 {{ summary.revision || '-' }}</span>
    </div>
    <div class="draft-hint">来自智能客服草稿，正式提交前请人工核对。</div>
    <div class="draft-stats">
      <span>已识别 {{ recognizedCount }} 项</span>
      <span v-if="summary.missingFields && summary.missingFields.length">待补充 {{ summary.missingFields.length }} 项</span>
    </div>
    <div v-if="summary.missingFields && summary.missingFields.length" class="draft-fields">
      <span class="field-label">待补充：</span>{{ missingFieldText }}
    </div>
    <div v-if="summary.warnings && summary.warnings.length" class="draft-warnings">
      <div v-for="warning in summary.warnings" :key="warning.code || warning.message">
        {{ warning.message || warning.code || '部分字段需要人工复核' }}
      </div>
    </div>
    <div v-if="summary.status === 'EDITABLE'" class="draft-next">请继续在对话中补充或修改关键信息。</div>
    <div v-else-if="summary.status === 'READY'" class="draft-next">草稿尚未提交，可前往业务页面核对。</div>
    <div v-else class="draft-next">该草稿当前为只读状态，不能再次用于新建。</div>
    <div v-if="visibleActions.length" class="draft-actions">
      <el-button
        v-for="action in visibleActions"
        :key="action.type"
        size="mini"
        type="primary"
        :disabled="archived || loading"
        @click="$emit('action', action)"
      >
        {{ action.label || actionText(action.type) }}
      </el-button>
    </div>
  </div>
</template>

<script>
import { normalizeFormDraftActions } from '../utils/agentFormDraftActions'

const FIELD_LABELS = {
  'customer.customerName': '客户姓名',
  'customer.phone': '手机号',
  'customer.addresses': '配送地址',
  customerId: '客户',
  customerCode: '客户编号',
  parentPackageId: '父套餐',
  childPackageId: '子套餐',
  'order.parentPackageId': '父套餐',
  'order.childPackageId': '子套餐',
  'order.startDate': '开始日期',
  'order.mealType': '餐次类型'
}

export default {
  name: 'AgentFormDraftCard',
  props: {
    summary: { type: Object, default: null },
    actions: { type: Array, default: () => [] },
    archived: { type: Boolean, default: false },
    loading: { type: Boolean, default: false }
  },
  computed: {
    typeText() { return this.summary.type === 'CREATE_ORDER' ? '新增订单草稿' : '新增客户及首单草稿' },
    statusText() {
      const map = { EDITABLE: '待确认', READY: '可前往核对', CLAIMED: '已领取', SUBMITTED: '已提交', EXPIRED: '已过期', CANCELLED: '已取消' }
      return map[this.summary.status] || '未知状态'
    },
    statusTag() {
      if (this.summary.status === 'READY') return 'success'
      if (this.summary.status === 'EDITABLE') return 'warning'
      return 'info'
    },
    recognizedCount() { return Array.isArray(this.summary.recognizedFields) ? this.summary.recognizedFields.length : 0 },
    missingFieldText() { return (this.summary.missingFields || []).map(field => FIELD_LABELS[field] || field).join('、') },
    visibleActions() {
      const actions = normalizeFormDraftActions(this.actions)
      if (this.summary.status === 'READY') return actions.filter(action => action.type !== 'CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER')
      if (this.summary.status === 'EDITABLE' && this.summary.type === 'CREATE_ORDER') {
        return actions.filter(action => action.type === 'CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER')
      }
      return []
    }
  },
  methods: {
    actionText(type) {
      if (type === 'OPEN_CREATE_ORDER_FORM') return '去新增订单'
      if (type === 'CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER') return '转为新建客户'
      return '去新建客户'
    }
  }
}
</script>

<style scoped>
.form-draft-card { margin-top: 12px; padding: 12px; border: 1px solid #d9ecff; border-radius: 6px; background: #f4f9ff; }
.draft-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.draft-title { margin-right: 8px; font-weight: 600; color: #303133; }
.draft-version { color: #909399; font-size: 12px; }
.draft-hint, .draft-next { margin-top: 8px; color: #606266; font-size: 13px; }
.draft-stats { display: flex; gap: 14px; margin-top: 10px; color: #409eff; font-size: 13px; }
.draft-fields, .draft-warnings { margin-top: 8px; color: #e6a23c; font-size: 13px; }
.field-label { font-weight: 600; }
.draft-actions { margin-top: 10px; }
</style>
