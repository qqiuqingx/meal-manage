<template>
  <div class="app-container">
    <div class="head-container">
      <el-input
        v-model="query.customerCode"
        clearable
        size="small"
        placeholder="客户编号"
        style="width: 140px;"
        class="filter-item"
        @keyup.enter.native="handleQuery"
      />
      <el-input
        v-model="query.customerName"
        clearable
        size="small"
        placeholder="客户姓名"
        style="width: 140px;"
        class="filter-item"
        @keyup.enter.native="handleQuery"
      />
      <el-input
        v-model="query.phone"
        clearable
        size="small"
        placeholder="手机号"
        style="width: 140px;"
        class="filter-item"
        @keyup.enter.native="handleQuery"
      />
      <el-button type="primary" size="small" icon="el-icon-search" @click="handleQuery">搜索</el-button>
      <el-button size="small" icon="el-icon-refresh-right" @click="resetQuery">重置</el-button>
    </div>

    <el-table
      ref="mealStatsTable"
      v-loading="loading"
      :data="rows"
      :height="tableHeight"
      border
      stripe
      :span-method="tableSpanMethod"
      row-key="rowKey"
      class="meal-stats-table"
    >
      <el-table-column label="编号" prop="customerCode" width="110" fixed="left" />
      <el-table-column label="电话" prop="phone" width="130" fixed="left" />
      <el-table-column label="地址" prop="addressText" min-width="260">
        <template slot-scope="{ row }">
          <div class="multiline-cell">{{ row.addressText || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="备注信息" prop="remarkInfo" min-width="120">
        <template slot-scope="{ row }">
          <div class="multiline-cell">{{ row.remarkInfo || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="特殊要求" prop="specialRequirementText" min-width="180">
        <template slot-scope="{ row }">
          <div class="multiline-cell">{{ row.specialRequirementText || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="汤品" prop="soupLabel" width="90" align="center">
        <template slot-scope="{ row }">
          {{ row.soupLabel || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="送餐情况" prop="deliveryInfo" min-width="150" />
      <el-table-column label="购买时间" prop="purchaseDateText" width="100" align="center">
        <template slot-scope="{ row }">
          {{ row.purchaseDateText || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="开始时间" prop="startDateText" width="100" align="center">
        <template slot-scope="{ row }">
          {{ row.startDateText || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="餐数" prop="mealCount" width="80" align="center" />
      <el-table-column label="剩余餐数" prop="remainingMealCount" width="100" align="center">
        <template slot-scope="{ row }">
          <span :class="{ 'danger-count': row.remainingMealCount < 3 }">
            {{ row.remainingMealCount }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      :current-page="page.current"
      :page-sizes="[10, 20, 50, 100]"
      :page-size="page.size"
      :total="page.total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script>
import { getMealStats } from '@/api/customer/profile'

const defaultQuery = () => ({
  customerCode: '',
  customerName: '',
  phone: ''
})

export default {
  name: 'CustomerMealStats',
  data() {
    return {
      loading: false,
      rows: [],
      tableHeight: 520,
      query: defaultQuery(),
      page: {
        current: 1,
        size: 20,
        total: 0
      }
    }
  },
  created() {
    this.loadData()
  },
  mounted() {
    this.$nextTick(() => {
      this.updateTableHeight()
    })
    window.addEventListener('resize', this.updateTableHeight)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateTableHeight)
  },
  methods: {
    loadData() {
      this.loading = true
      getMealStats({
        ...this.query,
        page: this.page.current,
        size: this.page.size
      }).then(res => {
        this.rows = res.content || []
        this.page.total = res.totalElements || 0
        this.$nextTick(() => {
          this.updateTableHeight()
        })
      }).finally(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.page.current = 1
      this.loadData()
    },
    resetQuery() {
      this.query = defaultQuery()
      this.page.current = 1
      this.loadData()
    },
    handleSizeChange(size) {
      this.page.size = size
      this.page.current = 1
      this.loadData()
    },
    handleCurrentChange(page) {
      this.page.current = page
      this.loadData()
    },
    tableSpanMethod({ row, columnIndex }) {
      if (columnIndex > 4) {
        return [1, 1]
      }
      if (row.firstRowInGroup) {
        return [row.groupRowSpan || 1, 1]
      }
      return [0, 0]
    },
    updateTableHeight() {
      const tableRef = this.$refs.mealStatsTable
      if (!tableRef || !tableRef.$el) {
        return
      }
      const rect = tableRef.$el.getBoundingClientRect()
      const reservedSpace = 140
      const minHeight = 360
      this.tableHeight = Math.max(window.innerHeight - rect.top - reservedSpace, minHeight)
    }
  }
}
</script>

<style scoped>
.meal-stats-table {
  margin-bottom: 16px;
}

.multiline-cell {
  white-space: pre-line;
  line-height: 1.5;
}

.danger-count {
  color: #f56c6c;
  font-weight: 600;
}
</style>
