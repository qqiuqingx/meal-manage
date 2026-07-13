<template>
  <div class="business-order-list-card">
    <el-table :data="items" size="mini" border>
      <el-table-column prop="orderCode" label="订单编号" min-width="140" />
      <el-table-column prop="statusName" label="状态" width="90" />
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
  methods: { balance(row) { return row.mealBalance || {} } }
}
</script>
