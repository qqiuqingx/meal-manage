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
              {{ item.mealTypeName || '餐数' }}剩余 {{ item.remainingCount }} 餐，明日排餐 {{ item.tomorrowScheduledCount }} 餐后将耗尽
            </span>
            <span v-else style="color: #909399;">
              {{ item.mealTypeName || '餐数' }}剩余 {{ item.remainingCount }} 餐（明日未排餐）
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
      <el-table-column column-key="medicalRequirements" label="医嘱" min-width="180">
        <template slot-scope="{ row }">
          <div class="multiline-cell">{{ row.medicalRequirements || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column column-key="dishRequirements" label="菜品特殊要求" min-width="240">
        <template slot-scope="{ row }">
          <CustomerDietCell :raw="row.dishRequirementsRaw" :items="row.dishRequirements" />
        </template>
      </el-table-column>
      <el-table-column column-key="dietaryRestrictions" label="客户禁忌" min-width="240">
        <template slot-scope="{ row }">
          <CustomerDietCell :raw="row.dietaryRestrictionsRaw" :items="row.dietaryRestrictions" />
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
      <el-table-column column-key="actions" label="操作" width="110" align="center" fixed="right">
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

    <div v-if="page.total > 0" class="meal-stats-load-status" aria-live="polite">
      <span>已加载 {{ rows.length }} / {{ page.total }} 条</span>
      <span v-if="loadingMore" class="meal-stats-load-status__message">正在加载…</span>
      <el-button v-else-if="loadError" type="text" size="small" @click="loadNextPage(true)">加载失败，点击重试</el-button>
      <span v-else-if="rows.length >= page.total" class="meal-stats-load-status__message">已加载全部</span>
      <span v-else class="meal-stats-load-status__message">向下滚动加载更多</span>
    </div>

    <el-dialog
      :visible.sync="calendarDialogVisible"
      :title="calendarDialogTitle"
      width="92%"
      append-to-body
      class="schedule-calendar-dialog"
    >
      <div class="schedule-calendar-meta">
        <span>统计月份：{{ query.statsMonth || '-' }}</span>
        <span>午晚餐订单：{{ lunchDinnerOrderCount }} 笔</span>
        <span>购买 / 剩余：{{ lunchDinnerPurchaseCount }} / {{ lunchDinnerRemainingCount }} 份</span>
        <span v-if="selectedRow && selectedRow.specialRequirementText">特殊要求：{{ selectedRow.specialRequirementText }}</span>
      </div>
      <customer-meal-quantity-grid
        :orders="calendarScheduleOrders"
        :cells="calendarScheduleCells"
        :stats-month="query.statsMonth"
        @cell-change="handleScheduleCellChange"
      />

      <div v-if="hasEditableBreakfastOrder || pausedBreakfastOrderCount > 0" class="breakfast-calendar-section">
        <div class="breakfast-calendar-section__heading">
          <strong>早餐调整</strong>
          <span>沿用原有日历操作；早餐不参与份数编辑。</span>
        </div>
        <el-alert
          v-if="pausedBreakfastOrderCount > 0 && !hasEditableBreakfastOrder"
          type="warning"
          :closable="false"
          show-icon
          title="暂停订单的早餐计划仅供查看，恢复前不能调整或排餐。"
          style="margin-bottom: 8px;"
        />
        <div class="readonly-calendar readonly-calendar--breakfast">
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
              'readonly-calendar__day--scheduled': day.mealTypes.includes('BREAKFAST')
            }"
          >
            <div class="readonly-calendar__date">{{ day.day }}</div>
            <div class="readonly-calendar__tags">
              <button
                type="button"
                class="readonly-calendar__meal-button"
                :class="mealButtonClass(day, 'BREAKFAST')"
                :disabled="!day.currentMonth || !hasEditableBreakfastOrder"
                @click="toggleMeal(day, 'BREAKFAST')"
              >
                <span class="readonly-calendar__tag-label">早</span>
              </button>
            </div>
          </div>
        </div>
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
import CustomerMealQuantityGrid from './CustomerMealQuantityGrid'
import CustomerDietCell from '@/components/CustomerDietCell.vue'

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
  components: { CustomerMealQuantityGrid, CustomerDietCell },
  data() {
    return {
      loading: false,
      loadingMore: false,
      loadError: false,
      rows: [],
      tableHeight: 520,
      query: defaultQuery(),
      page: {
        current: 0,
        size: 20,
        total: 0
      },
      calendarDialogVisible: false,
      calendarSaving: false,
      selectedRow: null,
      selectedScheduleDays: [],
      calendarExcludedDates: [],
      calendarAdditions: [],
      weekdays: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'],
      depletionWarnings: [],
      calendarScheduleOrders: [],
      calendarScheduleCells: [],
      calendarRevision: '',
      requestSequence: 0
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
    },
    breakfastStatsRow() {
      if (!this.selectedRow) return null
      return this.rows.find(row => row.customerId === this.selectedRow.customerId && row.mealBucket === 'BREAKFAST') || null
    },
    lunchDinnerPurchaseCount() {
      return this.calendarScheduleOrders.reduce((total, order) => total + (Number(order.mealCount) || 0), 0)
    },
    lunchDinnerRemainingCount() {
      return this.calendarScheduleOrders.reduce((total, order) => total + (Number(order.remainingMealCount) || 0), 0)
    },
    lunchDinnerOrderCount() {
      return this.calendarScheduleOrders.filter(order => Number(order.mealCount) > 0).length
    },
    hasEditableBreakfastOrder() {
      return this.calendarScheduleOrders.some(order => Number(order.breakfastCount) > 0 && Number(order.status) === 1)
    },
    pausedBreakfastOrderCount() {
      return this.calendarScheduleOrders.filter(order => Number(order.breakfastCount) > 0 && Number(order.status) === 4).length
    }
  },
  created() {
    this.loadData()
    this.loadDepletionWarnings()
  },
  mounted() {
    this.$nextTick(() => {
      this.updateTableHeight()
      this.bindTableScroll()
    })
    window.addEventListener('resize', this.updateTableHeight)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateTableHeight)
    this.unbindTableScroll()
    this.requestSequence += 1
  },
  methods: {
    /**
     * 加载首批或追加一页客户用餐统计，并忽略已过期的查询响应。
     * @param {boolean} reset 是否清空当前列表并从第一页加载
     */
    loadData(reset = true) {
      if (!reset && (this.loading || this.loadingMore || this.rows.length >= this.page.total)) {
        return
      }
      const requestId = ++this.requestSequence
      const requestPage = reset ? 1 : this.page.current + 1
      if (reset) {
        this.rows = []
        this.page.current = 0
        this.page.total = 0
        this.loading = true
        this.loadingMore = false
        this.loadError = false
        this.$nextTick(() => {
          const body = this.getTableScrollContainer()
          if (body) body.scrollTop = 0
        })
      } else {
        this.loadingMore = true
        this.loadError = false
      }
      getMealStats({
        ...this.query,
        page: requestPage,
        size: this.page.size
      }).then(res => {
        if (requestId !== this.requestSequence) {
          return
        }
        const content = Array.isArray(res.content) ? res.content : []
        this.rows = reset ? content : this.rows.concat(content)
        this.page.current = requestPage
        this.page.total = res.totalElements || 0
        this.recalculateRowGroups()
      }).catch(() => {
        if (requestId === this.requestSequence && !reset) {
          this.loadError = true
        }
      }).finally(() => {
        if (requestId !== this.requestSequence) {
          return
        }
        this.loading = false
        this.loadingMore = false
        this.$nextTick(() => {
          this.updateTableHeight()
          if (!this.loadError) this.loadMoreWhenTableFits()
        })
      })
    },
    /**
     * 请求当前筛选条件下的下一页数据。
     * @param {boolean} retry 是否为失败请求的手动重试
     */
    loadNextPage(retry = false) {
      if (this.loading || this.loadingMore || (this.loadError && !retry) || this.rows.length >= this.page.total) {
        return
      }
      this.loadData(false)
    },
    /** 取得 Element UI 表格实际负责滚动的容器。 */
    getTableScrollContainer() {
      const tableRef = this.$refs.mealStatsTable
      return tableRef && tableRef.$el
        ? tableRef.$el.querySelector('.el-table__body-wrapper')
        : null
    },
    /** 绑定表格滚动事件，触底时自动加载下一页。 */
    bindTableScroll() {
      const body = this.getTableScrollContainer()
      if (!body || this._mealStatsScrollBody === body) {
        return
      }
      this.unbindTableScroll()
      this._mealStatsScrollBody = body
      body.addEventListener('scroll', this.handleTableScroll)
    },
    /** 移除表格滚动事件监听。 */
    unbindTableScroll() {
      if (this._mealStatsScrollBody) {
        this._mealStatsScrollBody.removeEventListener('scroll', this.handleTableScroll)
        this._mealStatsScrollBody = null
      }
    },
    /** 在滚动位置接近表格底部时加载下一页。 */
    handleTableScroll() {
      const body = this.getTableScrollContainer()
      if (!body || this.loading || this.loadingMore || this.loadError || this.rows.length >= this.page.total) {
        return
      }
      if (body.scrollHeight - body.scrollTop - body.clientHeight <= 100) {
        this.loadNextPage()
      }
    },
    /** 首批内容不足以产生滚动条时继续加载，避免用户无处触发滚动。 */
    loadMoreWhenTableFits() {
      const body = this.getTableScrollContainer()
      if (body && body.scrollHeight <= body.clientHeight && this.rows.length < this.page.total) {
        this.loadNextPage()
      }
    },
    /** 追加分页后重算客户合并行，兼容同一客户的明细跨页返回。 */
    recalculateRowGroups() {
      let start = 0
      while (start < this.rows.length) {
        let end = start + 1
        while (end < this.rows.length && this.rows[end].customerId === this.rows[start].customerId) {
          end += 1
        }
        const span = end - start
        for (let index = start; index < end; index += 1) {
          this.rows[index].firstRowInGroup = index === start
          this.rows[index].groupRowSpan = index === start ? span : 0
        }
        start = end
      }
    },
    loadDepletionWarnings() {
      getDepletionWarnings().then(res => {
        this.depletionWarnings = res || []
      }).catch(() => {})
    },
    /** 按当前筛选条件清空列表并从第一页重新加载。 */
    handleQuery() {
      this.loadData(true)
    },
    /** 恢复默认筛选条件并重新加载首批数据。 */
    resetQuery() {
      this.query = defaultQuery()
      this.loadData(true)
    },
    /** 合并同一客户的共享资料与操作列，保留餐池专属列逐行展示。
     * @param {Object} context 当前表格行、列及列位置
     * @returns {number[]} 单元格跨行与跨列数量
     */
    tableSpanMethod({ row, column, columnIndex }) {
      const sharedColumn = columnIndex <= 4 || ['medicalRequirements', 'dishRequirements', 'dietaryRestrictions', 'actions'].includes(column.columnKey)
      if (!sharedColumn) {
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
      const quantityCells = Array.isArray(row.mealScheduleCells) ? row.mealScheduleCells : []
      const overriddenCellKeys = new Set(quantityCells
        .filter(cell => cell.manualOverride)
        .map(cell => this.scheduleCellKey(cell.orderId, cell.date, cell.mealType)))
      const savedAdditions = Array.isArray(row.manualScheduleAdditions)
        ? row.manualScheduleAdditions.map(item => ({ ...item }))
        : this.extractAdditions(this.selectedScheduleDays)
      this.calendarAdditions = savedAdditions
        .filter(item => item.mealType === 'BREAKFAST' || !overriddenCellKeys.has(this.scheduleCellKey(item.orderId, item.date, item.mealType)))
      this.calendarScheduleOrders = Array.isArray(row.mealScheduleOrders) ? row.mealScheduleOrders.map(order => ({ ...order })) : []
      this.calendarScheduleCells = quantityCells.map(cell => ({ ...cell }))
      this.calendarRevision = row.calendarRevision || ''
      this.calendarDialogVisible = true
    },
    scheduleCellKey(orderId, date, mealType) {
      return `${orderId}#${date}#${mealType}`
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
      const orderId = this.resolveAdditionOrderId(mealType, day.date)
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
    /**
     * 查找指定餐次人工新增计划应关联的订单。
     * @param {string} mealType 餐次类型
     * @param {string} date 计划日期
     * @return {number|null} 可用订单ID；没有匹配订单时返回 null
     */
    resolveAdditionOrderId(mealType, date) {
      if (!this.selectedRow) {
        return null
      }
      if (mealType === 'BREAKFAST') {
        const breakfastOrders = this.calendarScheduleOrders.filter(order =>
          Number(order.status) === 1 && Number(order.breakfastCount) > 0)
        const order = breakfastOrders.find(candidate =>
          (!candidate.startDate || date >= candidate.startDate) &&
          (!candidate.endDate || date <= candidate.endDate)) || breakfastOrders[0]
        return order && order.orderId
      }
      const row = this.rows.find(item => item.customerId === this.selectedRow.customerId && item.mealBucket === 'LUNCH_DINNER')
      if (row && row.orderId) {
        return row.orderId
      }
      const lunchDinnerOrders = this.calendarScheduleOrders
        .filter(order => Number(order.status) === 1 && Number(order.mealCount) > 0)
        .sort((left, right) => Number(left.orderId) - Number(right.orderId))
      return lunchDinnerOrders.length > 0 ? lunchDinnerOrders[0].orderId : null
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
          if (mealType === 'BREAKFAST') {
            result.push({ orderId: this.resolveAdditionOrderId(mealType, day.date), date: day.date, mealType, remark: '' })
          }
        })
      })
      return result.filter(item => item.orderId)
    },
    handleScheduleCellChange(cell) {
      if (!cell) return
      const wasExcluded = this.hasExcludedMeal(cell.date, cell.mealType)
      if (cell.excluded) {
        this.addExcludedMeal(cell.date, cell.mealType)
        this.calendarScheduleCells = this.calendarScheduleCells.map(current => {
          if (current.date !== cell.date || current.mealType !== cell.mealType) return current
          return { ...current, quantity: 0, soupQuantity: null, excluded: true, manualOverride: false }
        })
      } else {
        if (wasExcluded) {
          this.calendarScheduleCells = this.calendarScheduleCells.map(current => {
            if (current.date !== cell.date || current.mealType !== cell.mealType) return current
            return {
              ...current,
              quantity: Number(current.baseQuantity) || 0,
              soupQuantity: null,
              excluded: false,
              manualOverride: false
            }
          })
        }
        this.removeExcludedMeal(cell.date, cell.mealType)
        const index = this.calendarScheduleCells.findIndex(current =>
          this.scheduleCellKey(current.orderId, current.date, current.mealType) ===
          this.scheduleCellKey(cell.orderId, cell.date, cell.mealType))
        if (index >= 0) {
          this.$set(this.calendarScheduleCells, index, { ...cell, excluded: false })
        }
      }
    },
    buildQuantityOverrides() {
      return this.calendarScheduleCells
        .filter(cell => !cell.excluded && !this.hasExcludedMeal(cell.date, cell.mealType) && Number(cell.quantity) > 0 && (
          cell.manualOverride ||
          Number(cell.quantity) !== Number(cell.baseQuantity) ||
          cell.soupQuantity != null
        ))
        .map(cell => ({
          orderId: cell.orderId,
          date: cell.date,
          mealType: cell.mealType,
          quantity: cell.quantity,
          soupQuantity: cell.soupQuantity,
          remark: ''
        }))
    },
    /** 保存日历调整并刷新客户统计列表。 */
    saveCalendarAdjustments() {
      if (!this.selectedRow) {
        return
      }
      this.calendarSaving = true
      saveMealScheduleAdjustments({
        customerId: this.selectedRow.customerId,
        statsMonth: this.query.statsMonth,
        quantityMode: true,
        expectedRevision: this.calendarRevision,
        excludedDates: this.calendarExcludedDates,
        additions: this.calendarAdditions.concat(this.buildQuantityOverrides())
      }).then(() => {
        this.$message.success('排餐日历已保存')
        this.calendarDialogVisible = false
        this.loadData(true)
      }).catch(err => {
        const message = err && err.response && err.response.data && err.response.data.message
        this.$message.error(message || '排餐日历保存失败，请刷新后重试')
      }).finally(() => {
        this.calendarSaving = false
      })
    }
  }
}
</script>

<style scoped>
.schedule-calendar-dialog /deep/ .el-dialog {
  max-width: 1500px;
  margin-top: 3vh !important;
}

.schedule-calendar-dialog /deep/ .el-dialog__body {
  max-height: 78vh;
  overflow: auto;
}

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
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}

.breakfast-calendar-section {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.breakfast-calendar-section__heading {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 9px;
  color: #405776;
  font-size: 13px;
}

.breakfast-calendar-section__heading span {
  color: #98a3b1;
  font-size: 12px;
}

.readonly-calendar--breakfast .readonly-calendar__day {
  min-height: 58px;
  padding: 6px;
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

.meal-stats-load-status {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 12px;
}

.meal-stats-load-status__message {
  margin-left: 8px;
}
</style>
