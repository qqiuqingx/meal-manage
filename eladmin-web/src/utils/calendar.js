/**
 * 日历工具函数
 * 用于处理日期验证、格式化、日历生成等
 */

/**
 * 获取某年某月的第一天是星期几（0-6，0为周日）
 */
export function getFirstDayOfMonth(year, month) {
  return new Date(year, month, 1).getDay()
}

/**
 * 获取某年某月有多少天
 */
export function getDaysInMonth(year, month) {
  return new Date(year, month + 1, 0).getDate()
}

/**
 * 生成日历数据（包含上个月的填充日期和下个月的填充日期）
 * @param {number} year 年份
 * @param {number} month 月份（0-11）
 * @param {number} firstDayOfWeek 一周的第一天（0=周日，1=周一）
 * @returns {Array} 日历日期数组
 */
export function generateCalendarData(year, month, firstDayOfWeek = 0) {
  const daysInMonth = getDaysInMonth(year, month)
  const firstDayOfMonth = getFirstDayOfMonth(year, month)

  const calendar = []

  // 上个月的填充日期
  const prevMonthDays = (firstDayOfMonth - firstDayOfWeek + 7) % 7
  if (prevMonthDays > 0) {
    const prevMonth = month === 0 ? 11 : month - 1
    const prevYear = month === 0 ? year - 1 : year
    const prevMonthDaysCount = getDaysInMonth(prevYear, prevMonth)
    for (let i = prevMonthDaysCount - prevMonthDays + 1; i <= prevMonthDaysCount; i++) {
      calendar.push({
        date: new Date(prevYear, prevMonth, i),
        day: i,
        isCurrentMonth: false,
        isPrevMonth: true,
        isNextMonth: false
      })
    }
  }

  // 当前月的日期
  for (let i = 1; i <= daysInMonth; i++) {
    calendar.push({
      date: new Date(year, month, i),
      day: i,
      isCurrentMonth: true,
      isPrevMonth: false,
      isNextMonth: false
    })
  }

  // 下个月的填充日期（补齐到42个单元格，6周）
  const totalCells = 42
  const remainingCells = totalCells - calendar.length
  if (remainingCells > 0) {
    const nextMonth = month === 11 ? 0 : month + 1
    const nextYear = month === 11 ? year + 1 : year
    for (let i = 1; i <= remainingCells; i++) {
      calendar.push({
        date: new Date(nextYear, nextMonth, i),
        day: i,
        isCurrentMonth: false,
        isPrevMonth: false,
        isNextMonth: true
      })
    }
  }

  return calendar
}

/**
 * 格式化日期为 yyyy-MM-dd
 */
export function formatDate(date) {
  if (!date) return ''
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 解析 yyyy-MM-dd 格式的日期字符串
 */
export function parseDate(dateStr) {
  if (!dateStr) return null
  // 支持 "2026-04-12" 或 "2026-04-12 00:00:00" 格式
  const datePart = String(dateStr).split(' ')[0] // 只取日期部分
  const parts = datePart.split('-')
  if (parts.length !== 3) return null
  const [year, month, day] = parts.map(Number)
  if (isNaN(year) || isNaN(month) || isNaN(day)) return null
  return new Date(year, month - 1, day)
}

/**
 * 检查日期是否在范围内
 */
export function isDateInRange(date, startDate, endDate) {
  if (!date) return false
  const target = formatDate(date)
  const start = startDate ? formatDate(parseDate(startDate)) : null
  const end = endDate ? formatDate(parseDate(endDate)) : null

  if (start && target < start) return false
  if (end && target > end) return false
  return true
}

/**
 * 比较两个日期是否相同（忽略时间）
 */
export function isSameDay(date1, date2) {
  if (!date1 || !date2) return false
  return formatDate(date1) === formatDate(date2)
}

/**
 * 获取月份的中文显示
 */
export function getMonthName(month) {
  const monthNames = ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月']
  return monthNames[month]
}

/**
 * 获取星期的中文显示（简写）
 */
export function getDayNameShort(dayOfWeek) {
  const dayNames = ['日', '一', '二', '三', '四', '五', '六']
  return dayNames[dayOfWeek]
}

/**
 * 获取星期的中文显示（完整）
 */
export function getDayNameFull(dayOfWeek) {
  const dayNames = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return dayNames[dayOfWeek]
}

/**
 * 添加天数到日期
 */
export function addDays(date, days) {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

/**
 * 添加月到日期
 */
export function addMonths(date, months) {
  const result = new Date(date)
  result.setMonth(result.getMonth() + months)
  return result
}

/**
 * 获取两个日期之间的天数差
 */
export function diffDays(date1, date2) {
  const d1 = new Date(date1.getFullYear(), date1.getMonth(), date1.getDate())
  const d2 = new Date(date2.getFullYear(), date2.getMonth(), date2.getDate())
  return Math.round((d2 - d1) / (1000 * 60 * 60 * 24))
}

/**
 * 判断是否为今天
 */
export function isToday(date) {
  const today = new Date()
  return isSameDay(date, today)
}

/**
 * 判断是否为周末
 */
export function isWeekend(date) {
  const day = date.getDay()
  return day === 0 || day === 6
}

/**
 * 判断是否为工作日
 */
export function isWeekday(date) {
  return !isWeekend(date)
}

/**
 * 获取日期范围的所有日期
 */
export function getDateRange(startDate, endDate) {
  const dates = []
  let current = parseDate(startDate)
  const end = parseDate(endDate)

  while (current <= end) {
    dates.push(formatDate(current))
    current = addDays(current, 1)
  }

  return dates
}

/**
 * 餐次枚举
 */
export const MealType = {
  BREAKFAST: 'BREAKFAST',
  LUNCH: 'LUNCH',
  DINNER: 'DINNER'
}

/**
 * 餐次显示名称
 */
export const MealTypeName = {
  [MealType.BREAKFAST]: '早餐',
  [MealType.LUNCH]: '午餐',
  [MealType.DINNER]: '晚餐'
}

/**
 * 餐次颜色映射
 */
export const MealTypeColor = {
  [MealType.BREAKFAST]: '#67C23A', // 绿色
  [MealType.LUNCH]: '#E6A23C', // 橙色
  [MealType.DINNER]: '#409EFF' // 蓝色
}

/**
 * 根据日期和餐次配置计算餐数
 */
export function calculateMealCounts(deliveryDates) {
  let breakfastCount = 0
  let lunchCount = 0
  let dinnerCount = 0

  if (Array.isArray(deliveryDates)) {
    deliveryDates.forEach(item => {
      const mealTypes = item.mealTypes || []
      if (mealTypes.includes(MealType.BREAKFAST)) breakfastCount++
      if (mealTypes.includes(MealType.LUNCH)) lunchCount++
      if (mealTypes.includes(MealType.DINNER)) dinnerCount++
    })
  }

  return {
    breakfastCount,
    lunchDinnerCount: lunchCount + dinnerCount,
    lunchCount,
    dinnerCount
  }
}

/**
 * 从旧格式转换为新格式（兼容性）
 * 旧格式：["2026-04-01", "2026-04-02"]
 * 新格式：[{date: "2026-04-01", mealTypes: ["BREAKFAST", "LUNCH"]}, ...]
 */
export function normalizeDeliveryDates(value, defaultMealTypes = [MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER]) {
  // 如果是字符串，尝试解析为 JSON
  if (typeof value === 'string') {
    if (!value || value.trim() === '') {
      return []
    }
    try {
      value = JSON.parse(value)
    } catch (e) {
      return []
    }
  }

  // 如果解析后不是数组，返回空
  if (!Array.isArray(value)) {
    return []
  }

  // 已经是新格式（对象数组）
  if (value.length > 0 && typeof value[0] === 'object' && value[0] !== null && 'date' in value[0]) {
    return value
  }

  // 旧格式：字符串数组
  if (value.length > 0 && typeof value[0] === 'string') {
    return value.map(date => ({
      date,
      mealTypes: [...defaultMealTypes]
    }))
  }

  // 空值
  return []
}

/**
 * 从新格式转换为旧格式（兼容性）
 */
export function denormalizeDeliveryDates(deliveryDates) {
  if (!Array.isArray(deliveryDates)) return []

  return deliveryDates.map(item => {
    if (typeof item === 'string') return item
    return item.date
  })
}
