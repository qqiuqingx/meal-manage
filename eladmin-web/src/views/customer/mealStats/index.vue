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
      <el-date-picker
        v-model="query.statsMonth"
        type="month"
        size="small"
        value-format="yyyy-MM"
        placeholder="统计月份"
        style="width: 140px;"
        class="filter-item"
        @change="handleQuery"
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
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template slot-scope="{ row }">
          <el-button
            v-if="row.firstRowInGroup"
            type="text"
            size="small"
            icon="el-icon-date"
            @click="openScheduleCalendar(row)"
          >
            排餐日历
          </el-button>
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

    <el-dialog
      :visible.sync="calendarDialogVisible"
      :title="calendarDialogTitle"
      width="760px"
      append-to-body
      class="schedule-calendar-dialog"
    >
      <div class="schedule-calendar-meta">
        <span>统计月份：{{ query.statsMonth || '-' }}</span>
        <span>应排餐：{{ selectedScheduleDays.length }} 天</span>
      </div>
      <div class="readonly-calendar">
        <div
          v-for="weekday in weekdays"
          :key="weekday"
          class="readonly-calendar__weekday"
        >
          {{ weekday }}
        </div>
        <div
          v-for="day in calendarDays"
          :key="day.date"
          class="readonly-calendar__day"
          :class="{
            'readonly-calendar__day--outside': !day.currentMonth,
            'readonly-calendar__day--scheduled': day.mealTypes.length > 0
          }"
        >
          <div class="readonly-calendar__date">{{ day.day }}</div>
          <div v-if="day.mealTypes.length > 0" class="readonly-calendar__tags">
            <el-tag
              v-for="mealType in day.mealTypes"
              :key="mealType"
              size="mini"
              :type="mealTagType(mealType)"
              effect="plain"
            >
              {{ mealTypeName(mealType) }}
            </el-tag>
          </div>
        </div>
      </div>
      <div v-if="selectedScheduleDays.length === 0" class="schedule-calendar-empty">
        当前月份没有需要排餐的日期
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getMealStats } from '@/api/customer/profile'

const defaultQuery = () => ({
  customerCode: '',
  customerName: '',
  phone: '',
  statsMonth: formatCurrentMonth()
})

function formatCurrentMonth() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  return `${now.getFullYear()}-${month}`
}

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
      },
      calendarDialogVisible: false,
      selectedRow: null,
      selectedScheduleDays: [],
      weekdays: ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    }
  },
  computed: {
    calendarDialogTitle() {
      if (!this.selectedRow) {
        return '排餐日历'
      }
      return `${this.selectedRow.customerCode || '-'} 排餐日历`
    },
    calendarDays() {
      const month = this.parseStatsMonth(this.query.statsMonth)
      if (!month) {
        return []
      }
      const scheduleMap = this.selectedScheduleDays.reduce((map, item) => {
        map[item.date] = Array.isArray(item.mealTypes) ? item.mealTypes : []
        return map
      }, {})
      const firstDay = new Date(month.year, month.month - 1, 1)
      const daysInMonth = new Date(month.year, month.month, 0).getDate()
      const prevMonthDays = firstDay.getDay()
      const cells = []

      if (prevMonthDays > 0) {
        const prevDaysInMonth = new Date(month.year, month.month - 1, 0).getDate()
        for (let i = prevMonthDays - 1; i >= 0; i--) {
          const day = prevDaysInMonth - i
          const date = this.formatDate(new Date(month.year, month.month - 2, day))
          cells.push({ date, day, currentMonth: false, mealTypes: [] })
        }
      }

      for (let day = 1; day <= daysInMonth; day++) {
        const date = this.formatDate(new Date(month.year, month.month - 1, day))
        cells.push({ date, day, currentMonth: true, mealTypes: scheduleMap[date] || [] })
      }

      const nextCells = 42 - cells.length
      for (let day = 1; day <= nextCells; day++) {
        const date = this.formatDate(new Date(month.year, month.month, day))
        cells.push({ date, day, currentMonth: false, mealTypes: [] })
      }
      return cells
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
      if (columnIndex > 4 && columnIndex !== 11) {
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
    },
    openScheduleCalendar(row) {
      this.selectedRow = row
      this.selectedScheduleDays = Array.isArray(row.customerScheduleDays) ? row.customerScheduleDays : []
      this.calendarDialogVisible = true
    },
    parseStatsMonth(value) {
      if (!value || !/^\d{4}-\d{2}$/.test(value)) {
        return null
      }
      const parts = value.split('-').map(Number)
      return { year: parts[0], month: parts[1] }
    },
    formatDate(date) {
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      return `${year}-${month}-${day}`
    },
    mealTypeName(mealType) {
      const map = {
        BREAKFAST: '早',
        LUNCH: '午',
        DINNER: '晚'
      }
      return map[mealType] || mealType
    },
    mealTagType(mealType) {
      const map = {
        BREAKFAST: 'success',
        LUNCH: 'warning',
        DINNER: ''
      }
      return map[mealType] || 'info'
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

.schedule-calendar-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}

.readonly-calendar {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  border-top: 1px solid #ebeef5;
  border-left: 1px solid #ebeef5;
}

.readonly-calendar__weekday,
.readonly-calendar__day {
  border-right: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
}

.readonly-calendar__weekday {
  height: 36px;
  line-height: 36px;
  text-align: center;
  background: #f5f7fa;
  color: #606266;
  font-weight: 600;
}

.readonly-calendar__day {
  min-height: 78px;
  padding: 8px;
  background: #fff;
}

.readonly-calendar__day--outside {
  background: #fafafa;
  color: #c0c4cc;
}

.readonly-calendar__day--scheduled {
  background: #f0f7ff;
}

.readonly-calendar__date {
  margin-bottom: 8px;
  font-weight: 600;
}

.readonly-calendar__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.schedule-calendar-empty {
  margin-top: 12px;
  color: #909399;
  text-align: center;
}
</style>
