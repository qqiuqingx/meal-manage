<template>
  <div class="operation-stats-card">
    <div class="stats-row">
      <span>统计日期</span><strong>{{ result.recordDate || '未指定' }}</strong>
      <span v-if="result.mealType">餐次</span><strong v-if="result.mealType">{{ mealTypeText(result.mealType) }}</strong>
    </div>
    <div class="metrics">
      <div v-for="item in metrics" :key="item.key" class="metric"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
    </div>
    <el-table v-if="breakdownRows.length" :data="breakdownRows" size="mini" border>
      <el-table-column prop="dimension" :label="breakdownLabel" />
      <el-table-column v-for="metric in breakdownMetrics" :key="metric" :prop="metric" :label="metricLabel(metric)" />
    </el-table>
    <div class="meta">口径：{{ result.metricDefinitionId || '-' }} / {{ result.metricVersion || '-' }}；{{ result.timezone || 'Asia/Shanghai' }}；{{ result.queriedAt || '-' }}</div>
  </div>
</template>

<script>
export default {
  name: 'BusinessOperationStatsCard',
  props: { result: { type: Object, required: true }},
  computed: {
    metrics() {
      const result = this.result || {}
      if (Object.prototype.hasOwnProperty.call(result, 'total')) return [{ key: 'total', label: '统计值', value: result.total || 0 }]
      const all = [
        { key: 'scheduled', label: '已排餐客户', value: result.scheduledCustomerCount || 0 },
        { key: 'verified', label: '已核销客户', value: result.verifiedCustomerCount || 0 },
        { key: 'unverified', label: '待核销客户', value: result.unverifiedCustomerCount || 0 },
        { key: 'expected', label: '应服务客户', value: result.expectedCustomerCount || 0 },
        { key: 'unscheduled', label: '待排餐客户', value: result.unscheduledCustomerCount || 0 },
        { key: 'failure', label: '排餐失败', value: result.mealPlanFailureCount || 0 }
      ]
      const reportMetrics = Array.isArray(result.reportMetrics) ? result.reportMetrics : []
      const keys = {
        DAILY_SCHEDULED_CUSTOMER_COUNT: 'scheduled', DAILY_VERIFIED_CUSTOMER_COUNT: 'verified',
        DAILY_UNVERIFIED_CUSTOMER_COUNT: 'unverified', DAILY_EXPECTED_CUSTOMER_COUNT: 'expected',
        DAILY_UNSCHEDULED_CUSTOMER_COUNT: 'unscheduled', MEAL_PLAN_FAILURE_COUNT: 'failure'
      }
      return reportMetrics.length ? all.filter(item => reportMetrics.indexOf(Object.keys(keys).find(key => keys[key] === item.key)) >= 0) : all
    },
    breakdownMetrics() {
      const source = this.breakdownSource
      const reportMetrics = Array.isArray(this.result && this.result.reportMetrics) ? this.result.reportMetrics : []
      const metrics = reportMetrics.length ? reportMetrics : Object.keys(source)
      return metrics.filter(metric => source[metric] && Object.keys(source[metric]).length)
    },
    breakdownRows() {
      const source = this.breakdownSource
      const dimensions = new Set()
      this.breakdownMetrics.forEach(metric => Object.keys(source[metric] || {}).forEach(dimension => dimensions.add(dimension)))
      const order = { BREAKFAST: 1, LUNCH: 2, DINNER: 3 }
      const ordered = Array.from(dimensions).sort((left, right) => this.isMealTypeBreakdown ? (order[left] || 99) - (order[right] || 99) : left.localeCompare(right, 'zh-CN'))
      return ordered.map(dimension => {
        const row = { dimension: this.dimensionText(dimension) }
        this.breakdownMetrics.forEach(metric => { row[metric] = (source[metric] || {})[dimension] || 0 })
        return row
      })
    },
    breakdownSource() {
      const result = this.result || {}
      const dimensionBreakdown = result.metricDimensionBreakdown || {}
      return Object.keys(dimensionBreakdown).length ? dimensionBreakdown : (result.metricMealTypeBreakdown || {})
    },
    isMealTypeBreakdown() {
      const dimensions = (this.result && this.result.breakdownDimensions) || []
      return !dimensions.length || (dimensions.length === 1 && dimensions[0] === 'MEAL_TYPE')
    },
    breakdownLabel() {
      const dimensions = (this.result && this.result.breakdownDimensions) || []
      if (!dimensions.length || (dimensions.length === 1 && dimensions[0] === 'MEAL_TYPE')) return '餐次'
      return dimensions.map(this.dimensionLabel).join(' / ')
    }
  },
  methods: {
    mealTypeText(value) { return ({ BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' })[value] || value },
    metricLabel(value) {
      return ({ DAILY_SCHEDULED_CUSTOMER_COUNT: '已排餐客户', DAILY_VERIFIED_CUSTOMER_COUNT: '已核销客户', DAILY_UNVERIFIED_CUSTOMER_COUNT: '待核销客户', DAILY_EXPECTED_CUSTOMER_COUNT: '应服务客户', DAILY_UNSCHEDULED_CUSTOMER_COUNT: '待排餐客户', MEAL_PLAN_FAILURE_COUNT: '排餐失败' })[value] || value
    },
    dimensionLabel(value) {
      return ({ MEAL_TYPE: '餐次', PACKAGE: '套餐', CUSTOMER_SOURCE: '客户来源' })[value] || value
    },
    dimensionText(value) {
      return this.isMealTypeBreakdown ? this.mealTypeText(value) : value
    }
  }
}
</script>

<style scoped>
.operation-stats-card { font-size: 13px; color: #4b5563; }
.stats-row, .metrics { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 8px; }
.metrics { align-items: stretch; }
.metric { min-width: 100px; border: 1px solid #dfe6ec; padding: 7px; }
.metric span { display: block; color: #7a8491; }
.metric strong { color: #24364b; font-size: 16px; }
.meta { margin-top: 8px; color: #7a8491; line-height: 1.5; word-break: break-all; }
</style>
