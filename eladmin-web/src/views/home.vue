<template>
  <div class="dashboard-container">
    <div class="dashboard-editor-container">
      <github-corner class="github-corner" />

      <!-- 当日客户统计 + 客户来源统计 并行 -->
      <el-row :gutter="32" style="margin-top: 18px;">
        <!-- 左侧：当日客户统计 -->
        <el-col :xs="24" :sm="24" :lg="12">
          <div class="stats-card">
            <div class="stats-card-header">
              <span class="stats-title">当日客户统计</span>
              <el-date-picker
                v-model="statsDate"
                type="date"
                placeholder="选择日期"
                value-format="yyyy-MM-dd"
                size="small"
                style="margin-left: 16px;"
                @change="fetchCustomerStats"
              />
            </div>
            <el-tabs v-model="activeMealType" style="margin-top: 12px;" @tab-click="handleTabChange">
              <el-tab-pane label="午餐" name="LUNCH" />
              <el-tab-pane label="晚餐" name="DINNER" />
            </el-tabs>
            <div v-if="!statsLoading && filteredGroups.length === 0" style="text-align: center; color: #909399; padding: 20px 0;">
              暂无数据
            </div>
            <customer-stats-chart
              v-if="!statsLoading && filteredGroups.length > 0"
              :chart-data="filteredGroups"
              :meal-type="activeMealType"
              :height="'240px'"
            />
          </div>
        </el-col>

        <!-- 右侧：客户来源统计 -->
        <el-col :xs="24" :sm="24" :lg="12">
          <div class="stats-card">
            <div class="stats-card-header">
              <span class="stats-title">客户来源统计</span>
            </div>
            <div v-if="!sourceLoading && sourceGroups.length === 0" style="text-align: center; color: #909399; padding: 20px 0;">
              暂无数据
            </div>
            <source-stats-chart
              v-if="!sourceLoading && sourceGroups.length > 0"
              :chart-data="sourceGroups"
              :height="'280px'"
            />
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script>
import GithubCorner from '@/components/GithubCorner'
import CustomerStatsChart from './dashboard/CustomerStatsChart'
import SourceStatsChart from './dashboard/SourceStatsChart'
import { queryDailyCustomerStats, queryCustomerSourceStats } from '@/api/dish'

function formatDate(date) {
  const d = date || new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export default {
  name: 'Dashboard',
  components: {
    GithubCorner,
    CustomerStatsChart,
    SourceStatsChart
  },
  data() {
    return {
      statsDate: formatDate(),
      activeMealType: 'LUNCH',
      statsLoading: false,
      sourceLoading: false,
      customerStats: null,
      sourceGroups: []
    }
  },
  computed: {
    filteredGroups() {
      if (!this.customerStats || !this.customerStats.groups) {
        return []
      }
      return this.customerStats.groups.filter(
        g => g.mealType === this.activeMealType
      )
    }
  },
  mounted() {
    this.fetchCustomerStats()
    this.fetchSourceStats()
  },
  methods: {
    handleTabChange() {
      // 数据通过 computed 自动过滤，图表 watch chartData 自动更新
    },
    fetchCustomerStats() {
      this.statsLoading = true
      queryDailyCustomerStats({ date: this.statsDate })
        .then(data => {
          this.customerStats = data
        })
        .catch(() => {
          this.customerStats = null
        })
        .finally(() => {
          this.statsLoading = false
        })
    },
    fetchSourceStats() {
      this.sourceLoading = true
      queryCustomerSourceStats({ date: this.statsDate })
        .then(data => {
          this.sourceGroups = data || []
        })
        .catch(() => {
          this.sourceGroups = []
        })
        .finally(() => {
          this.sourceLoading = false
        })
    }
  }
}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
  .dashboard-editor-container {
    padding: 32px;
    background-color: rgb(240, 242, 245);
    position: relative;
    min-height: calc(100vh - 50px);

    .github-corner {
      position: absolute;
      top: 0;
      border: 0;
      right: 0;
    }
  }

  .stats-card {
    background: #fff;
    padding: 20px 24px;
    box-shadow: 4px 4px 40px rgba(0, 0, 0, .05);
    border-radius: 8px;
    height: 100%;
  }

  .stats-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .stats-title {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }
</style>
