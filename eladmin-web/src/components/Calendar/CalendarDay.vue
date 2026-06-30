<template>
  <div
    class="calendar-day"
    :class="{
      'calendar-day--disabled': disabled || isPrevMonth || isNextMonth,
      'calendar-day--selected': isSelected,
      'calendar-day--today': isToday,
      'calendar-day--current-month': isCurrentMonth
    }"
    @click="handleClick"
  >
    <div class="calendar-day__date">{{ day }}</div>

    <!-- 餐次指示器 -->
    <div v-if="!disabled && mealTypes && mealTypes.length > 0" class="calendar-day__meal-indicators">
      <span
        v-for="type in mealTypes"
        :key="type"
        class="calendar-day__meal-tag"
        :class="getMealTypeClass(type)"
        :title="getMealTypeName(type)"
      >
        {{ getMealTypeShortName(type) }}
      </span>
    </div>
  </div>
</template>

<script>
import { MealTypeColor, MealTypeName, formatDate, isToday as checkIsToday } from '@/utils/calendar'

export default {
  name: 'CalendarDay',
  props: {
    // 日期对象
    date: {
      type: Date,
      required: true
    },
    // 是否是当前月
    isCurrentMonth: {
      type: Boolean,
      default: true
    },
    // 是否是上个月
    isPrevMonth: {
      type: Boolean,
      default: false
    },
    // 是否是下个月
    isNextMonth: {
      type: Boolean,
      default: false
    },
    // 是否已选中
    isSelected: {
      type: Boolean,
      default: false
    },
    // 是否禁用
    disabled: {
      type: Boolean,
      default: false
    },
    // 餐次类型数组
    mealTypes: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    day() {
      return this.date.getDate()
    },
    isToday() {
      return checkIsToday(this.date)
    }
  },
  methods: {
    handleClick() {
      if (this.disabled || !this.isCurrentMonth) return
      this.$emit('click', {
        date: this.date,
        formattedDate: formatDate(this.date)
      })
    },
    getMealTypeColor(type) {
      return MealTypeColor[type] || '#909399'
    },
    getMealTypeName(type) {
      return MealTypeName[type] || type
    },
    getMealTypeShortName(type) {
      const shortNames = {
        'BREAKFAST': '早',
        'LUNCH': '午',
        'DINNER': '晚'
      }
      return shortNames[type] || type
    },
    getMealTypeClass(type) {
      const classes = {
        'BREAKFAST': 'meal-tag--breakfast',
        'LUNCH': 'meal-tag--lunch',
        'DINNER': 'meal-tag--dinner'
      }
      return classes[type] || ''
    }
  }
}
</script>

<style scoped>
.calendar-day {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 36px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
  user-select: none;
}

.calendar-day:hover:not(.calendar-day--disabled) {
  background-color: #f5f7fa;
}

.calendar-day--disabled {
  cursor: not-allowed;
  color: #c0c4cc;
}

.calendar-day--current-month {
  color: #606266;
}

.calendar-day--selected {
  background-color: #409eff;
  color: #ffffff;
}

.calendar-day--today:not(.calendar-day--selected) {
  border: 1px solid #409eff;
  color: #409eff;
}

.calendar-day__date {
  font-size: 12px;
  font-weight: 500;
}

.calendar-day__meal-indicators {
  display: flex;
  gap: 1px;
  margin-top: 2px;
}

.calendar-day__meal-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 14px;
  height: 12px;
  font-size: 9px;
  font-weight: 500;
  border-radius: 2px;
  padding: 0 1px;
}

.meal-tag--breakfast {
  background-color: #67c23a;
  color: #ffffff;
}

.meal-tag--lunch {
  background-color: #e6a23c;
  color: #ffffff;
}

.meal-tag--dinner {
  background-color: #409eff;
  color: #ffffff;
}

/* 选中状态下的餐次标签样式 */
.calendar-day--selected .meal-tag--breakfast,
.calendar-day--selected .meal-tag--lunch,
.calendar-day--selected .meal-tag--dinner {
  background-color: rgba(255, 255, 255, 0.9);
  color: #409eff;
}
</style>
