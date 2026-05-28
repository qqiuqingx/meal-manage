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

    <!-- 餐数耗尽预警 -->
    <el-alert
      v-if="depletionWarnings.length > 0"
      :title="'以下 ' + depletionWarnings.length + ' 个订单剩余餐数即将耗尽'"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 12px;"
    >
      <template slot>
        <div style="max-height: 120px; overflow-y: auto;">
          <div v-for="item in depletionWarnings" :key="item.orderId" style="padding: 2px 0; font-size: 13px;">
            <span style="font-weight: 600; margin-right: 8px;">{{ item.customerName }}（{{ item.customerCode }}）</span>
            <span v-if="item.tomorrowScheduledCount > 0" style="color: #909399;">
              剩余 {{ item.remainingCount }} 餐，明日排餐 {{ item.tomorrowScheduledCount }} 餐后将耗尽
            </span>
            <span v-else style="color: #909399;">
              剩余 {{ item.remainingCount }} 餐（明日未排餐）
            </span>
          </div>
        </div>
      </template>
    </el-alert>

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
      <el-table-column label="编号" prop="customerCode" width="50" fixed="left" />
      <el-table-column label="电话" prop="phone" width="100" fixed="left" />
      <el-table-column label="地址" prop="addressText" min-width="260" fixed="left">
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
      <el-table-column prop="remainingMealCount" width="110" align="center">
        <template slot="header">
          <span>剩余餐数</span>
          <el-tooltip
            effect="dark"
            placement="top"
            popper-class="meal-stats-remaining-tooltip"
          >
            <div slot="content">
              <div>早餐行：早餐总数 - BREAKFAST 已核销数</div>
              <div>午晚餐行：午晚餐总数 - LUNCH 已核销数 - DINNER 已核销数</div>
              <div>结果小于 0 时按 0 展示</div>
            </div>
            <i class="el-icon-question remaining-count-help" />
          </el-tooltip>
        </template>
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
          <div class="readonly-calendar__tags">
            <button
              v-for="mealType in mealTypes"
              :key="mealType"
              type="button"
              class="readonly-calendar__meal-button"
              :class="mealButtonClass(day, mealType)"
              :disabled="!day.currentMonth"
              @click="toggleMeal(day, mealType)"
            >
              <span class="readonly-calendar__tag-label">{{ mealTypeName(mealType) }}</span>
            </button>
          </div>
        </div>
      </div>
      <div v-if="selectedScheduleDays.length === 0" class="schedule-calendar-empty">
        当前月份没有需要排餐的日期
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button size="small" @click="calendarDialogVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="calendarSaving" @click="saveCalendarAdjustments">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { getMealStats, saveMealScheduleAdjustments } from '@/api/customer/profile'
import { getDepletionWarnings } from '@/api/mealPlan'

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
      calendarSaving: false,
      selectedRow: null,
      selectedScheduleDays: [],
      calendarExcludedDates: [],
      calendarAdditions: [],
      mealTypes: ['BREAKFAST', 'LUNCH', 'DINNER'],
      weekdays: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'],
      depletionWarnings: []
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
        map[item.date] = {
          mealTypes: Array.isArray(item.mealTypes) ? item.mealTypes : [],
          baseMealTypes: Array.isArray(item.baseMealTypes) ? item.baseMealTypes : [],
          excludedMealTypes: Array.isArray(item.excludedMealTypes) ? item.excludedMealTypes : [],
          addedMealTypes: Array.isArray(item.addedMealTypes) ? item.addedMealTypes : [],
          scheduledMealTypes: Array.isArray(item.scheduledMealTypes) ? item.scheduledMealTypes : []
        }
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
          cells.push({ date, day, currentMonth: false, mealTypes: [], baseMealTypes: [], excludedMealTypes: [], addedMealTypes: [], scheduledMealTypes: [] })
        }
      }

      for (let day = 1; day <= daysInMonth; day++) {
        const date = this.formatDate(new Date(month.year, month.month - 1, day))
        const scheduleInfo = scheduleMap[date] || {
          mealTypes: [],
          baseMealTypes: [],
          excludedMealTypes: [],
          addedMealTypes: [],
          scheduledMealTypes: []
        }
        cells.push({
          date,
          day,
          currentMonth: true,
          mealTypes: scheduleInfo.mealTypes,
          baseMealTypes: scheduleInfo.baseMealTypes,
          excludedMealTypes: scheduleInfo.excludedMealTypes,
          addedMealTypes: scheduleInfo.addedMealTypes,
          scheduledMealTypes: scheduleInfo.scheduledMealTypes
        })
      }

      const nextCells = 42 - cells.length
      for (let day = 1; day <= nextCells; day++) {
        const date = this.formatDate(new Date(month.year, month.month, day))
        cells.push({ date, day, currentMonth: false, mealTypes: [], baseMealTypes: [], excludedMealTypes: [], addedMealTypes: [], scheduledMealTypes: [] })
      }
      return cells
    }
  },
  created() {
    this.loadData()
    this.loadDepletionWarnings()
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
    loadDepletionWarnings() {
      getDepletionWarnings().then(res => {
        this.depletionWarnings = res || []
      }).catch(() => {})
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
      this.calendarExcludedDates = this.extractExcludedDates(this.selectedScheduleDays)
      this.calendarAdditions = this.extractAdditions(this.selectedScheduleDays)
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
    isMealScheduled(day, mealType) {
      return Array.isArray(day.scheduledMealTypes) && day.scheduledMealTypes.includes(mealType)
    },
    mealButtonClass(day, mealType) {
      const excluded = this.isMealExcluded(day, mealType)
      const scheduled = this.isMealScheduled(day, mealType)
      return {
        'readonly-calendar__meal-button--base': this.isBaseMeal(day, mealType),
        'readonly-calendar__meal-button--excluded': excluded,
        'readonly-calendar__meal-button--added': this.isMealAdded(day, mealType),
        'readonly-calendar__meal-button--scheduled': scheduled,
        'readonly-calendar__meal-button--scheduled-cancelled': scheduled && excluded
      }
    },
    isBaseMeal(day, mealType) {
      return Array.isArray(day.baseMealTypes) && day.baseMealTypes.includes(mealType)
    },
    isMealExcluded(day, mealType) {
      return this.hasExcludedMeal(day.date, mealType)
    },
    isMealAdded(day, mealType) {
      return this.calendarAdditions.some(item => item.date === day.date && item.mealType === mealType)
    },
    hasExcludedMeal(date, mealType) {
      return this.calendarExcludedDates.some(item => item.date === date && Array.isArray(item.mealTypes) && item.mealTypes.includes(mealType))
    },
    toggleMeal(day, mealType) {
      if (!day.currentMonth) {
        return
      }
      if (this.hasExcludedMeal(day.date, mealType)) {
        this.removeExcludedMeal(day.date, mealType)
        return
      }
      if (this.isMealAdded(day, mealType)) {
        this.calendarAdditions = this.calendarAdditions.filter(item => !(item.date === day.date && item.mealType === mealType))
        return
      }
      if (this.isBaseMeal(day, mealType)) {
        this.addExcludedMeal(day.date, mealType)
        return
      }
      const orderId = this.resolveAdditionOrderId(mealType)
      if (!orderId) {
        this.$message.warning('没有可用于该餐次的进行中订单')
        return
      }
      this.calendarAdditions.push({ orderId, date: day.date, mealType, remark: '' })
    },
    addExcludedMeal(date, mealType) {
      let item = this.calendarExcludedDates.find(value => value.date === date)
      if (!item) {
        item = { date, mealTypes: [] }
        this.calendarExcludedDates.push(item)
      }
      if (!item.mealTypes.includes(mealType)) {
        item.mealTypes.push(mealType)
      }
    },
    removeExcludedMeal(date, mealType) {
      const item = this.calendarExcludedDates.find(value => value.date === date)
      if (!item) {
        return
      }
      item.mealTypes = item.mealTypes.filter(value => value !== mealType)
      if (item.mealTypes.length === 0) {
        this.calendarExcludedDates = this.calendarExcludedDates.filter(value => value.date !== date)
      }
    },
    resolveAdditionOrderId(mealType) {
      if (!this.selectedRow) {
        return null
      }
      const row = this.rows.find(item => item.customerId === this.selectedRow.customerId && (
        (mealType === 'BREAKFAST' && item.mealBucket === 'BREAKFAST') ||
        (mealType !== 'BREAKFAST' && item.mealBucket === 'LUNCH_DINNER')
      ))
      return row && row.orderId
    },
    extractExcludedDates(days) {
      return days
        .filter(day => Array.isArray(day.excludedMealTypes) && day.excludedMealTypes.length > 0)
        .map(day => ({ date: day.date, mealTypes: [...day.excludedMealTypes] }))
    },
    extractAdditions(days) {
      const result = []
      days.forEach(day => {
        if (!Array.isArray(day.addedMealTypes)) {
          return
        }
        day.addedMealTypes.forEach(mealType => {
          result.push({ orderId: this.resolveAdditionOrderId(mealType), date: day.date, mealType, remark: '' })
        })
      })
      return result.filter(item => item.orderId)
    },
    saveCalendarAdjustments() {
      if (!this.selectedRow) {
        return
      }
      this.calendarSaving = true
      saveMealScheduleAdjustments({
        customerId: this.selectedRow.customerId,
        statsMonth: this.query.statsMonth,
        excludedDates: this.calendarExcludedDates,
        additions: this.calendarAdditions
      }).then(() => {
        this.$message.success('排餐日历已保存')
        this.calendarDialogVisible = false
        this.loadData()
      }).finally(() => {
        this.calendarSaving = false
      })
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

.remaining-count-help {
  margin-left: 4px;
  color: #909399;
  cursor: help;
  font-size: 14px;
  vertical-align: middle;
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

.readonly-calendar__meal-button {
  width: 28px;
  height: 24px;
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  cursor: pointer;
  font-size: 12px;
  line-height: 20px;
  padding: 0;
  text-align: center;
}

.readonly-calendar__meal-button:disabled {
  cursor: not-allowed;
}

.readonly-calendar__meal-button--base {
  border-color: #409eff;
  color: #409eff;
}

.readonly-calendar__meal-button--excluded {
  border-color: #c0c4cc;
  background: #f5f7fa;
  color: #909399;
  text-decoration: line-through;
}

.readonly-calendar__meal-button--added {
  border-color: #67c23a;
  background: #f0f9eb;
  color: #529b2e;
}

.readonly-calendar__meal-button--scheduled {
  position: relative;
  border-color: #67c23a;
  background: #ecf5ff;
  color: #1f9d55;
  font-weight: 700;
  overflow: hidden;
}

.readonly-calendar__meal-button--scheduled::before {
  content: "✓";
  position: absolute;
  top: 50%;
  left: 50%;
  color: #1f9d55;
  font-size: 34px;
  font-weight: 900;
  line-height: 1;
  opacity: 0.82;
  transform: translate(-50%, -52%);
  z-index: 2;
}

.readonly-calendar__meal-button--scheduled-cancelled {
  border-color: #c0c4cc;
  background: #f5f7fa;
  color: #909399;
}

.readonly-calendar__meal-button--scheduled-cancelled::before {
  color: #909399;
  opacity: 0.68;
}

.readonly-calendar__tag-label {
  position: relative;
  z-index: 1;
}

.schedule-calendar-empty {
  margin-top: 12px;
  color: #909399;
  text-align: center;
}
</style>
