<template>
  <div class="customer-meal-summary">
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

      <!-- 汇总统计 -->
      <el-row :gutter="20" class="summary-stats">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ result.totalRemaining || 0 }}</div>
            <div class="stat-label">合计剩余餐数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ result.remainingBreakfast || 0 }}</div>
            <div class="stat-label">剩余早餐</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ result.remainingLunchDinner || 0 }}</div>
            <div class="stat-label">剩余午晚餐</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ result.activeOrderCount || 0 }}</div>
            <div class="stat-label">有效订单数</div>
          </div>
        </el-col>
      </el-row>

      <!-- 已核销统计 -->
      <el-row :gutter="20" class="summary-stats verified-stats">
        <el-col :span="8">
          <div class="stat-item">
            <div class="stat-value">{{ result.verifiedBreakfast || 0 }}</div>
            <div class="stat-label">已核销早餐</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-item">
            <div class="stat-value">{{ result.verifiedLunch || 0 }}</div>
            <div class="stat-label">已核销午餐</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-item">
            <div class="stat-value">{{ result.verifiedDinner || 0 }}</div>
            <div class="stat-label">已核销晚餐</div>
          </div>
        </el-col>
      </el-row>

      <!-- 订单明细表 -->
      <div v-if="result.orderItems && result.orderItems.length > 0" class="order-detail-section">
        <h4>订单明细</h4>
        <el-table :data="result.orderItems" border stripe size="small">
          <el-table-column prop="orderNo" label="订单编号" min-width="140" />
          <el-table-column prop="breakfastCount" label="早餐总数" width="80" align="center" />
          <el-table-column prop="lunchDinnerCount" label="午晚餐总数" width="80" align="center" />
          <el-table-column prop="remainingBreakfast" label="剩余早餐" width="80" align="center" />
          <el-table-column prop="remainingLunchDinner" label="剩余午晚餐" width="80" align="center" />
          <el-table-column prop="verifiedBreakfast" label="已核销早餐" width="90" align="center" />
          <el-table-column prop="verifiedLunch" label="已核销午餐" width="90" align="center" />
          <el-table-column prop="verifiedDinner" label="已核销晚餐" width="90" align="center" />
          <el-table-column label="状态" width="70" align="center">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="mini">
                {{ scope.row.status === 1 ? '进行中' : '已完成' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'CustomerMealSummaryCard',
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
  }
}
</script>

<style scoped>
.summary-message {
  margin-bottom: 16px;
  line-height: 1.6;
  color: #606266;
}
.summary-stats {
  margin-bottom: 16px;
}
.verified-stats {
  margin-bottom: 20px;
}
.stat-item {
  text-align: center;
  padding: 12px 8px;
  background: #f5f7fa;
  border-radius: 4px;
}
.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.verified-stats .stat-value {
  color: #e6a23c;
}
.order-detail-section h4 {
  margin-bottom: 10px;
  color: #303133;
}
</style>
