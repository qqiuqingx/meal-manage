<template>
  <div class="customer-order-summary">
    <!-- 客户不存在提示 -->
    <el-alert
      v-if="!result.present"
      :title="`未找到客户编号 ${result.customerCode || ''}`"
      type="warning"
      show-icon
      :closable="false"
    />

    <template v-if="result.present">
      <!-- 消息 -->
      <p class="summary-message">{{ chatMessage }}</p>

      <!-- 订单列表 -->
      <div v-if="result.orders && result.orders.length > 0">
        <el-table :data="result.orders" border stripe size="small">
          <el-table-column prop="orderNo" label="订单编号" min-width="140" />
          <el-table-column label="状态" width="70" align="center">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="mini">
                {{ scope.row.status === 1 ? '进行中' : '已完成' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="breakfastCount" label="早餐数" width="70" align="center" />
          <el-table-column prop="lunchDinnerCount" label="午晚餐数" width="80" align="center" />
          <el-table-column prop="remainingBreakfast" label="剩余早餐" width="80" align="center" />
          <el-table-column prop="remainingLunchDinner" label="剩余午晚餐" width="80" align="center" />
          <el-table-column label="开始日期" width="100" align="center">
            <template slot-scope="scope">
              {{ formatDate(scope.row.startDate) }}
            </template>
          </el-table-column>
          <el-table-column label="结束日期" width="100" align="center">
            <template slot-scope="scope">
              {{ formatDate(scope.row.endDate) }}
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="150" align="center">
            <template slot-scope="scope">
              {{ formatDateTime(scope.row.createTime) }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 无订单 -->
      <el-empty v-else description="暂无订单" />
    </template>
  </div>
</template>

<script>
export default {
  name: 'CustomerOrderSummaryCard',
  props: {
    result: {
      type: Object,
      required: true
    },
    messageText: {
      type: String,
      default: ''
    }
  },
  computed: {
    chatMessage() {
      return this.messageText || ''
    }
  },
  methods: {
    formatDate(date) {
      if (!date) return '-'
      const d = new Date(date)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    },
    formatDateTime(date) {
      if (!date) return '-'
      const d = new Date(date)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    }
  }
}
</script>

<style scoped>
.summary-message {
  margin-bottom: 16px;
  line-height: 1.6;
  color: #606266;
}
</style>
