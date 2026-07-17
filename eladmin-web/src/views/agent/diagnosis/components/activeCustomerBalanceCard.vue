<template>
  <div class="active-customer-balance-card">
    <div class="summary">共 {{ result.total || 0 }} 位客户，当前展示 {{ items.length }} 位</div>
    <el-table :data="items" size="mini" border>
      <el-table-column prop="customerCode" label="客户编号" min-width="110" />
      <el-table-column prop="customerNameMasked" label="客户" min-width="80" />
      <el-table-column prop="remainingBreakfast" label="早餐剩余" width="100" align="right" />
      <el-table-column prop="remainingLunchDinner" label="午晚餐剩余" width="110" align="right" />
      <el-table-column prop="remainingTotal" label="合计" width="80" align="right" />
    </el-table>
  </div>
</template>

<script>
export default {
  name: 'ActiveCustomerBalanceCard',
  props: { result: { type: Object, default: () => ({}) }},
  computed: { items() { return Array.isArray(this.result.items) ? this.result.items : [] } }
}
</script>

<style scoped>
.summary { margin-bottom: 8px; color: #606266; font-size: 13px; }
</style>
