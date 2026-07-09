<template>
  <div class="customer-verification-summary">
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

      <!-- 累计核销统计 -->
      <el-row :gutter="20" class="summary-stats">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ result.totalVerified || 0 }}</div>
            <div class="stat-label">累计核销</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ result.totalVerifiedBreakfast || 0 }}</div>
            <div class="stat-label">早餐</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ result.totalVerifiedLunch || 0 }}</div>
            <div class="stat-label">午餐</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ result.totalVerifiedDinner || 0 }}</div>
            <div class="stat-label">晚餐</div>
          </div>
        </el-col>
      </el-row>

      <!-- 最近核销记录 -->
      <div v-if="result.recentVerifications && result.recentVerifications.length > 0" class="recent-section">
        <h4>最近核销记录</h4>
        <el-table :data="result.recentVerifications" border stripe size="small">
          <el-table-column prop="orderNo" label="订单编号" min-width="140" />
          <el-table-column label="餐次" width="80" align="center">
            <template slot-scope="scope">
              <el-tag :type="mealTypeTag(scope.row.mealType)" size="mini">
                {{ mealTypeLabel(scope.row.mealType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="verificationCount" label="核销数量" width="80" align="center" />
          <el-table-column label="核销日期" width="100" align="center">
            <template slot-scope="scope">
              {{ formatDate(scope.row.recordDate) }}
            </template>
          </el-table-column>
          <el-table-column label="核销时间" width="150" align="center">
            <template slot-scope="scope">
              {{ formatDateTime(scope.row.createTime) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'CustomerVerificationSummaryCard',
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
    mealTypeLabel(type) {
      const map = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }
      return map[type] || type
    },
    mealTypeTag(type) {
      const map = { BREAKFAST: 'warning', LUNCH: 'primary', DINNER: 'success' }
      return map[type] || ''
    },
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
.summary-stats {
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
.recent-section h4 {
  margin-bottom: 10px;
  color: #303133;
}
</style>
