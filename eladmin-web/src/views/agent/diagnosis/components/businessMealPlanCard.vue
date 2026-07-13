<template>
  <div class="business-meal-plan-card">
    <el-table :data="items" size="mini" border>
      <el-table-column prop="recordDate" label="日期" width="110" />
      <el-table-column prop="mealTypeCode" label="餐次" width="90" />
      <el-table-column prop="generationStatus" label="生成状态" min-width="100" />
      <el-table-column prop="orderId" label="订单 ID" width="100" />
      <el-table-column prop="maskedDeliveryAddress" label="配送地址摘要" min-width="130" />
      <el-table-column label="核销" width="70"><template slot-scope="{ row }">{{ row.verified ? '已核销' : '未核销' }}</template></el-table-column>
      <el-table-column label="手工换菜" width="90"><template slot-scope="{ row }">{{ row.manualReplaceCount || 0 }} 项</template></el-table-column>
      <el-table-column label="人工新增" width="90"><template slot-scope="{ row }">{{ row.manualAddition ? '是' : '否' }}</template></el-table-column>
      <el-table-column label="菜品" min-width="200"><template slot-scope="{ row }">{{ dishes(row) }}</template></el-table-column>
      <el-table-column prop="failureReason" label="失败原因" min-width="160" />
    </el-table>
  </div>
</template>
<script>
export default {
  name: 'BusinessMealPlanCard',
  props: { result: { type: Object, required: true }},
  computed: { items() { return this.result.items || [] } },
  methods: { dishes(row) { return (row.dishes || []).map(item => item.dishName).filter(Boolean).join('、') || '-' } }
}
</script>
