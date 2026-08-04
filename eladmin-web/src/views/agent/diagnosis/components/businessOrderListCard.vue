<template>
  <div class="business-order-list-card">
    <div class="order-summary">共 {{ result.total || 0 }} 笔，当前展示 {{ items.length }} 笔</div>
    <el-table :data="items" size="mini" border>
      <el-table-column prop="orderCode" label="订单编号" min-width="140" />
      <el-table-column prop="customerCode" label="客户编号" min-width="110" />
      <el-table-column prop="statusName" label="状态" width="90" />
      <el-table-column label="下单时间" min-width="165"><template slot-scope="{ row }">{{ orderTime(row) }}</template></el-table-column>
      <el-table-column prop="startDate" label="开始日期" width="110" />
      <el-table-column prop="endDate" label="结束日期" width="110" />
      <el-table-column prop="mealTypeCode" label="餐次类型" width="100" />
      <el-table-column prop="parentPackageName" label="父套餐" min-width="120" />
      <el-table-column label="剩余早餐" width="90"><template slot-scope="{ row }">{{ balance(row).remainingBreakfast || 0 }}</template></el-table-column>
      <el-table-column label="剩余午晚餐" width="100"><template slot-scope="{ row }">{{ balance(row).remainingLunchDinner || 0 }}</template></el-table-column>
      <el-table-column prop="mealPlanRecordCount" label="排餐记录" width="90" />
      <el-table-column prop="verificationRecordCount" label="核销记录" width="90" />
      <el-table-column prop="refundRecordCount" label="退餐记录" width="90" />
    </el-table>
  </div>
</template>
<script>
export default {
  name: 'BusinessOrderListCard',
  props: { result: { type: Object, required: true }},
  computed: { items() { return this.result.items || [] } },
  methods: {
    balance(row) { return row.mealBalance || {} },
    orderTime(row) { return (row && (row.dealTime || row.createTime)) || '-' }
  }
}
</script>
<style scoped>
.order-summary {
  margin-bottom: 8px;
  color: #606266;
  font-size: 12px;
}
</style>
