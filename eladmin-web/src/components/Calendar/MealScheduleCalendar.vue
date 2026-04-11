<template>
  <div class="meal-schedule-calendar">
    <!-- 月份导航 -->
    <div class="calendar-header">
      <div class="calendar-header__nav">
        <el-button-group>
          <el-button size="small" icon="el-icon-arrow-left" @click="prevMonth">上个月</el-button>
          <el-button size="small" @click="goToToday">今天</el-button>
          <el-button size="small" @click="nextMonth">下个月</el-button>
        </el-button-group>
      </div>
      <div class="calendar-header__title">
        {{ currentYear }}年{{ currentMonth + 1 }}月
      </div>
      <div class="calendar-header__actions">
        <el-button-group>
          <el-button size="small" @click="selectAllDates">全选</el-button>
          <el-button size="small" @click="clearAllDates">清空</el-button>
        </el-button-group>
      </div>
    </div>

    <!-- 星期表头 -->
    <div class="calendar-weekdays">
      <div
        v-for="(day, index) in weekdays"
        :key="index"
        class="calendar-weekday"
        :class="{ 'calendar-weekday--weekend': isWeekend(index) }"
      >
        {{ day }}
      </div>
    </div>

    <!-- 日期网格 -->
    <div class="calendar-grid">
      <CalendarDay
        v-for="(day, index) in calendarDays"
        :key="index"
        :date="day.date"
        :is-current-month="day.isCurrentMonth"
        :is-prev-month="day.isPrevMonth"
        :is-next-month="day.isNextMonth"
        :is-selected="isSelected(day.date)"
        :disabled="!day.isCurrentMonth || !isDateValid(day.date)"
        :meal-types="getMealTypesForDate(day.date)"
        @click="handleDateClick"
      />
    </div>

    <!-- 餐次配置对话框 -->
    <el-dialog
      :visible.sync="showMealSelector"
      title="配置餐次"
      width="400px"
      :close-on-click-modal="false"
      append-to-body
      :z-index="9999"
      modal-append-to-body
    >
      <MealTypeSelector
        v-if="selectedDate"
        :date="selectedDate"
        :value="selectedDateMealTypes"
        @input="handleMealTypesChange"
        @save="handleMealTypesSave"
        @cancel="closeMealSelector"
      />
    </el-dialog>

    <!-- 汇总信息 -->
    <div class="calendar-summary">
      <div class="calendar-summary__item">
        <span class="calendar-summary__label">已选日期：</span>
        <span class="calendar-summary__value">{{ internalSelectedDates.length }} 天</span>
      </div>
      <div class="calendar-summary__item">
        <span class="calendar-summary__label calendar-summary__label--breakfast">早餐：</span>
        <span class="calendar-summary__value">{{ mealCounts.breakfastCount }} 份</span>
      </div>
      <div class="calendar-summary__item">
        <span class="calendar-summary__label calendar-summary__label--lunch">午餐：</span>
        <span class="calendar-summary__value">{{ mealCounts.lunchCount }} 份</span>
      </div>
      <div class="calendar-summary__item">
        <span class="calendar-summary__label calendar-summary__label--dinner">晚餐：</span>
        <span class="calendar-summary__value">{{ mealCounts.dinnerCount }} 份</span>
      </div>
      <div class="calendar-summary__item">
        <span class="calendar-summary__label calendar-summary__label--total">午晚合计：</span>
        <span class="calendar-summary__value">{{ mealCounts.lunchDinnerCount }} 份</span>
      </div>
    </div>
  </div>
</template>

<script>
import CalendarDay from './CalendarDay.vue'
import MealTypeSelector from './MealTypeSelector.vue'
import {
  generateCalendarData,
  formatDate,
  parseDate,
  addMonths,
  calculateMealCounts,
  normalizeDeliveryDates,
  getDateRange
} from '@/utils/calendar'

export default {
  name: 'MealScheduleCalendar',
  components: {
    CalendarDay,
    MealTypeSelector
  },
  props: {
    // 已选日期（新格式：[{date: 'yyyy-MM-dd', mealTypes: [...]}, ...]）
    value: {
      type: Array,
      default: () => []
    },
    // 订单开始日期
    startDate: {
      type: String,
      default: null
    },
    // 订单结束日期
    endDate: {
      type: String,
      default: null
    },
    // 是否只读
    readonly: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      currentYear: new Date().getFullYear(),
      currentMonth: new Date().getMonth(),
      calendarDays: [],
      showMealSelector: false,
      selectedDate: null,
      selectedDateMealTypes: [],
      weekdays: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'],
      internalSelectedDates: []
    }
  },
  computed: {
    selectedDates() {
      console.log('[Calendar] computed selectedDates, value:', JSON.stringify(this.value))
      const result = normalizeDeliveryDates(this.value)
      console.log('[Calendar] computed selectedDates result:', JSON.stringify(result))
      return result
    },
    mealCounts() {
      return calculateMealCounts(this.internalSelectedDates)
    }
  },
  watch: {
    value: {
      handler(val) {
        console.log('[Calendar] watch.value:', JSON.stringify(val))
        const normalized = normalizeDeliveryDates(val)
        console.log('[Calendar] normalized:', JSON.stringify(normalized))
        this.internalSelectedDates = normalized
        this.$forceUpdate()
      },
      immediate: true
    },
    currentYear() {
      this.generateCalendar()
    },
    currentMonth() {
      this.generateCalendar()
    },
    startDate() {
      this.generateCalendar()
    },
    endDate() {
      this.generateCalendar()
    }
  },
  created() {
    console.log('[Calendar] created, startDate:', this.startDate, 'endDate:', this.endDate, 'value:', JSON.stringify(this.value))
    // 立即初始化 internalSelectedDates，防止首次渲染时数据为空
    this.internalSelectedDates = normalizeDeliveryDates(this.value)
    console.log('[Calendar] created internalSelectedDates:', JSON.stringify(this.internalSelectedDates))
    this.generateCalendar()
    // 如果有开始日期，跳到开始日期所在月份
    if (this.startDate) {
      const date = parseDate(this.startDate)
      console.log('[Calendar] parsed startDate:', date)
      if (date) {
        this.currentYear = date.getFullYear()
        this.currentMonth = date.getMonth()
        console.log('[Calendar] jumped to', this.currentYear, this.currentMonth)
      }
    }
  },
  methods: {
    generateCalendar() {
      this.calendarDays = generateCalendarData(this.currentYear, this.currentMonth)
    },
    isSelected(date) {
      const formattedDate = formatDate(date)
      const selected = this.internalSelectedDates.some(item => item.date === formattedDate)
      console.log('[Calendar] isSelected', formattedDate, '=>', selected, 'internalSelectedDates:', JSON.stringify(this.internalSelectedDates))
      return selected
    },
    getMealTypesForDate(date) {
      const formattedDate = formatDate(date)
      const selected = this.internalSelectedDates.find(item => item.date === formattedDate)
      return selected ? selected.mealTypes : []
    },
    handleDateClick({ date, formattedDate }) {
      if (this.readonly) return

      const existingIndex = this.internalSelectedDates.findIndex(item => item.date === formattedDate)

      if (existingIndex >= 0) {
        // 已选中，打开餐次配置
        this.openMealSelector(date, this.internalSelectedDates[existingIndex].mealTypes)
      } else {
        // 未选中，添加并配置餐次
        this.addDate(date)
      }
    },
    addDate(date) {
      const formattedDate = formatDate(date)
      const newSelected = [
        ...this.internalSelectedDates,
        { date: formattedDate, mealTypes: ['BREAKFAST', 'LUNCH', 'DINNER'] }
      ]
      this.emitChange(newSelected)
      // 自动打开餐次配置
      this.openMealSelector(date, ['BREAKFAST', 'LUNCH', 'DINNER'])
    },
    removeDate(date) {
      const formattedDate = formatDate(date)
      const newSelected = this.internalSelectedDates.filter(item => item.date !== formattedDate)
      this.emitChange(newSelected)
    },
    openMealSelector(date, mealTypes) {
      this.selectedDate = date
      this.selectedDateMealTypes = [...mealTypes]
      this.showMealSelector = true
    },
    handleMealTypesSave(mealTypes) {
      const formattedDate = formatDate(this.selectedDate)
      const newSelected = this.internalSelectedDates.map(item => {
        if (item.date === formattedDate) {
          return { ...item, mealTypes: [...mealTypes] }
        }
        return item
      })
      this.emitChange(newSelected)
      this.closeMealSelector()
    },
    closeMealSelector() {
      this.showMealSelector = false
      this.selectedDate = null
      this.selectedDateMealTypes = []
    },
    handleMealTypesChange(mealTypes) {
      this.selectedDateMealTypes = [...mealTypes]
    },
    emitChange(value) {
      this.$emit('input', value)
      this.$emit('selection-change', this.mealCounts)
    },
    prevMonth() {
      const newDate = addMonths(new Date(this.currentYear, this.currentMonth, 1), -1)
      this.currentYear = newDate.getFullYear()
      this.currentMonth = newDate.getMonth()
    },
    nextMonth() {
      const newDate = addMonths(new Date(this.currentYear, this.currentMonth, 1), 1)
      this.currentYear = newDate.getFullYear()
      this.currentMonth = newDate.getMonth()
    },
    goToToday() {
      const today = new Date()
      this.currentYear = today.getFullYear()
      this.currentMonth = today.getMonth()
    },
    selectAllDates() {
      if (this.readonly) return
      if (!this.startDate || !this.endDate) {
        this.$message.warning('请先设置订单的开始和结束日期')
        return
      }

      const dateRange = getDateRange(this.startDate, this.endDate)
      const newSelected = dateRange.map(date => ({
        date,
        mealTypes: ['BREAKFAST', 'LUNCH', 'DINNER']
      }))
      this.emitChange(newSelected)
    },
    clearAllDates() {
      if (this.readonly) return
      this.$confirm('确定要清空所有选中的日期吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.emitChange([])
      }).catch(() => {})
    },
    isDateValid(date) {
      if (!this.startDate) return true
      const start = parseDate(this.startDate)
      if (!start) return true
      const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
      const s = new Date(start.getFullYear(), start.getMonth(), start.getDate())
      return d >= s
    },
    isWeekend(index) {
      return index === 0 || index === 6
    }
  }
}
</script>

<style scoped>
.meal-schedule-calendar {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 16px;
  background-color: #ffffff;
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.calendar-header__title {
  font-size: 18px;
  font-weight: 500;
  color: #303133;
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  margin-bottom: 8px;
}

.calendar-weekday {
  text-align: center;
  padding: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.calendar-weekday--weekend {
  color: #f56c6c;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.calendar-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 16px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.calendar-summary__item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.calendar-summary__label {
  font-size: 14px;
  color: #606266;
}

.calendar-summary__value {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.calendar-summary__label--breakfast {
  color: #67c23a;
}

.calendar-summary__label--lunch {
  color: #e6a23c;
}

.calendar-summary__label--dinner {
  color: #409eff;
}

.calendar-summary__label--total {
  font-weight: 500;
}
</style>
