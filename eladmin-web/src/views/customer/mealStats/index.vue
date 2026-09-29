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
      <span class="meal-stats-value-note">餐数为当前订单累计值</span>
    </div>

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
          <div v-for="item in depletionWarnings" :key="item.orderId" class="depletion-warning-row">
            <span class="depletion-warning-row__title">{{ item.customerName }}（{{ item.customerCode }}） · {{ item.orderCode }}</span>
            <span v-if="item.tomorrowScheduledCount > 0">
              {{ item.mealTypeName || '餐数' }}剩余 {{ item.remainingCount }} 餐，明日排餐 {{ item.tomorrowScheduledCount }} 餐后将耗尽
            </span>
            <span v-else>{{ item.mealTypeName || '餐数' }}剩余 {{ item.remainingCount }} 餐（明日未排餐）</span>
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
      row-key="orderId"
      class="meal-stats-table"
    >
      <el-table-column label="手机号" prop="phone" width="125" fixed="left">
        <template slot-scope="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="地址" prop="addressText" width="250" fixed="left">
        <template slot-scope="{ row }"><div class="multiline-cell">{{ row.addressText || '-' }}</div></template>
      </el-table-column>
      <el-table-column label="客户编号" prop="customerCode" width="105" fixed="left">
        <template slot-scope="{ row }">{{ row.customerCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="客户姓名" prop="customerName" width="110">
        <template slot-scope="{ row }">{{ row.customerName || '-' }}</template>
      </el-table-column>
      <el-table-column label="特殊要求" prop="specialRequirements" min-width="180">
        <template slot-scope="{ row }"><div class="multiline-cell">{{ row.specialRequirements || '-' }}</div></template>
      </el-table-column>
      <el-table-column label="排餐模式" prop="scheduleModeText" width="100" align="center">
        <template slot-scope="{ row }">{{ row.scheduleModeText || '-' }}</template>
      </el-table-column>
      <el-table-column label="餐次" prop="mealTypeText" width="115" align="center">
        <template slot-scope="{ row }">{{ row.mealTypeText || '待确认' }}</template>
      </el-table-column>
      <el-table-column label="规格" prop="specification" width="150" align="center">
        <template slot-scope="{ row }">{{ row.specification || '-' }}</template>
      </el-table-column>
      <el-table-column label="含汤" prop="soupCount" width="70" align="center">
        <template slot-scope="{ row }">{{ Number(row.soupCount) > 0 ? '含汤' : '不含汤' }}</template>
      </el-table-column>
      <el-table-column label="早餐" prop="breakfastCount" width="72" align="center">
        <template slot-scope="{ row }">{{ countText(row.breakfastCount) }}</template>
      </el-table-column>
      <el-table-column label="午晚" prop="lunchDinnerCount" width="72" align="center">
        <template slot-scope="{ row }">{{ countText(row.lunchDinnerCount) }}</template>
      </el-table-column>
      <el-table-column label="合计" prop="totalCount" width="72" align="center">
        <template slot-scope="{ row }">{{ countText(row.totalCount) }}</template>
      </el-table-column>
      <el-table-column prop="verifiedCount" width="80" align="center">
        <template slot="header">
          <el-tooltip content="订单当前累计核销数，已包含导入历史核销基数。" placement="top">
            <span>核销 <i class="el-icon-question metric-help" /></span>
          </el-tooltip>
        </template>
        <template slot-scope="{ row }">{{ countText(row.verifiedCount) }}</template>
      </el-table-column>
      <el-table-column prop="scheduledCount" width="85" align="center">
        <template slot="header">
          <el-tooltip content="当前订单全部有效排餐结果行数，包含成功和失败，不区分是否核销。" placement="top">
            <span>已排餐 <i class="el-icon-question metric-help" /></span>
          </el-tooltip>
        </template>
        <template slot-scope="{ row }">{{ countText(row.scheduledCount) }}</template>
      </el-table-column>
      <el-table-column label="剩余" prop="remainingCount" width="72" align="center">
        <template slot-scope="{ row }">{{ countText(row.remainingCount) }}</template>
      </el-table-column>
      <el-table-column prop="estimatedRemainingCount" width="100" align="center">
        <template slot="header">
          <el-tooltip content="当前剩余餐数减去今日成功且未核销的已排份数，小于 0 时按 0 展示。" placement="top">
            <span>预计剩余 <i class="el-icon-question metric-help" /></span>
          </el-tooltip>
        </template>
        <template slot-scope="{ row }"><span :class="{ 'danger-count': row.estimatedRemainingCount < 3 }">{{ countText(row.estimatedRemainingCount) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" prop="statusLabel" width="85" align="center">
        <template slot-scope="{ row }">
          <el-tag size="mini" :type="statusTagType(row.status)">{{ row.statusLabel || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="基本情况" prop="medicalRequirements" min-width="150">
        <template slot-scope="{ row }"><div class="multiline-cell">{{ row.medicalRequirements || '-' }}</div></template>
      </el-table-column>
      <el-table-column label="成单时间" prop="dealTime" width="155" align="center">
        <template slot-scope="{ row }">{{ row.dealTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="术后天数" prop="postoperativeInfo" width="100" align="center">
        <template slot-scope="{ row }">{{ row.postoperativeInfo || '-' }}</template>
      </el-table-column>
      <el-table-column label="菜品特殊要求" min-width="230">
        <template slot-scope="{ row }">
          <CustomerDietCell :raw="row.dishRequirementsRaw" :items="row.dishRequirements" />
        </template>
      </el-table-column>
      <el-table-column label="过敏食物" min-width="140">
        <template slot-scope="{ row }">
          <div v-if="row.allergyTags && row.allergyTags.length" class="allergy-tags">
            <el-tag v-for="(tag, index) in row.allergyTags" :key="`${tag}-${index}`" size="mini" type="warning">{{ tag }}</el-tag>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="禁忌食物" min-width="230">
        <template slot-scope="{ row }">
          <CustomerDietCell :raw="row.dietaryRestrictionsRaw" :items="row.dietaryRestrictions" />
        </template>
      </el-table-column>
      <el-table-column label="自定义菜单" width="105" align="center">
        <template slot-scope="{ row }">
          <el-image
            v-if="row.customMenuImage"
            :src="getCustomMenuImageUrl(row.customMenuImage)"
            :preview-src-list="[getCustomMenuImageUrl(row.customMenuImage)]"
            fit="contain"
            class="custom-menu-thumbnail"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="105" align="center" fixed="right">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" icon="el-icon-date" @click="openOrderCalendar(row)">排餐日历</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="loadError && rows.length === 0" class="meal-stats-first-load-error">
      <span>统计列表加载失败</span>
      <el-button type="text" size="small" @click="loadData(true)">点击重试</el-button>
    </div>

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
      :close-on-click-modal="false"
      class="schedule-calendar-dialog"
      @close="handleCalendarClose"
    >
      <div v-if="calendarLoading" class="calendar-loading">正在读取该订单的排餐日历…</div>
      <el-alert
        v-else-if="calendarLoadError"
        type="error"
        :closable="false"
        show-icon
        title="排餐日历加载失败"
      >
        <el-button type="text" size="small" @click="loadOrderCalendar">重试</el-button>
      </el-alert>
      <template v-else-if="calendarData">
        <div class="schedule-calendar-meta">
          <span>统计月份：{{ calendarMonth }}</span>
          <span>早餐购买 / 本月可用：{{ countText(calendarData.breakfastCount) }} / {{ countText(calendarData.availableBreakfastCount) }}</span>
          <span>午晚购买 / 本月可用：{{ countText(calendarData.lunchDinnerCount) }} / {{ countText(calendarData.availableLunchDinnerCount) }}</span>
          <span>默认汤品：{{ calendarData.defaultIncludesSoup ? '含汤' : '不含汤' }}</span>
        </div>
        <el-alert
          v-if="!calendarData.editable || !canEditCalendar"
          type="warning"
          :closable="false"
          show-icon
          :title="calendarReadOnlyReason"
          style="margin-bottom: 12px;"
        />
        <customer-meal-quantity-grid
          :order="calendarData"
          :cells="calendarCells"
          :overrides="calendarDraftOverrides"
          :stats-month="calendarMonth"
          :editable="calendarCanEdit"
          @override-upsert="upsertCalendarOverride"
          @override-remove="removeCalendarOverride"
        />
        <div v-if="calendarHasBreakfast" class="breakfast-calendar-section">
          <div class="breakfast-calendar-section__heading">
            <strong>早餐安排</strong>
            <span>勾选操作只调整当前订单；0 份会停用当前订单当天早餐。</span>
          </div>
          <div class="readonly-calendar readonly-calendar--breakfast">
            <div v-for="weekday in weekdays" :key="weekday" class="readonly-calendar__weekday">{{ weekday }}</div>
            <div
              v-for="day in calendarDays"
              :key="day.date"
              class="readonly-calendar__day"
              :class="{
                'readonly-calendar__day--outside': !day.currentMonth,
                'readonly-calendar__day--scheduled': day.breakfastCell && Number(day.breakfastCell.quantity) > 0,
                'readonly-calendar__day--global-stop': day.breakfastCell && day.breakfastCell.customerExcluded
              }"
            >
              <div class="readonly-calendar__date">{{ day.day }}</div>
              <div class="readonly-calendar__tags">
                <button
                  type="button"
                  class="readonly-calendar__meal-button"
                  :class="breakfastButtonClass(day)"
                  :disabled="!calendarCanEdit || !day.currentMonth || !day.breakfastCell || day.breakfastCell.customerExcluded"
                  :title="breakfastButtonTitle(day)"
                  @click="toggleBreakfast(day)"
                >早</button>
              </div>
              <small v-if="day.breakfastCell && day.breakfastCell.customerExcluded" class="readonly-calendar__reason">客户统一停餐</small>
              <small v-else-if="day.breakfastCell && day.breakfastCell.orderExcluded" class="readonly-calendar__reason">订单停餐</small>
            </div>
          </div>
        </div>
      </template>
      <span slot="footer" class="dialog-footer">
        <el-button size="small" @click="closeCalendar">关闭</el-button>
        <el-button
          v-if="canEditCalendar"
          type="primary"
          size="small"
          :loading="calendarSaving"
          :disabled="!calendarCanSave"
          @click="saveCalendar"
        >保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { getMealStats, getOrderMealCalendar, saveOrderMealCalendar } from '@/api/customer/profile'
import { getDepletionWarnings } from '@/api/mealPlan'
import CustomerMealQuantityGrid from './CustomerMealQuantityGrid'
import CustomerDietCell from '@/components/CustomerDietCell.vue'

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const defaultQuery = () => ({
  customerCode: '',
  customerName: '',
  phone: '',
  statsMonth: formatCurrentMonth()
})

function formatCurrentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
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
      page: { current: 0, size: 20, total: 0 },
      listRequestSequence: 0,
      calendarRequestSequence: 0,
      calendarDialogVisible: false,
      calendarLoading: false,
      calendarLoadError: false,
      calendarSaving: false,
      calendarLoaded: false,
      selectedRow: null,
      calendarData: null,
      calendarMonth: '',
      calendarCells: [],
      calendarDraftOverrides: [],
      calendarRevision: '',
      weekdays: WEEKDAYS,
      depletionWarnings: []
    }
  },
  computed: {
    ...mapGetters(['baseApi', 'roles']),
    canEditCalendar() {
      return this.roles.includes('admin') || this.roles.includes('customerProfile:edit')
    },
    calendarCanEdit() {
      return Boolean(this.calendarData && this.calendarData.editable && this.canEditCalendar && this.calendarLoaded)
    },
    calendarCanSave() {
      return this.calendarCanEdit && !this.calendarLoading && !this.calendarLoadError && !this.calendarSaving
    },
    calendarReadOnlyReason() {
      if (!this.canEditCalendar) return '当前账号没有编辑权限，只能查看。'
      return (this.calendarData && this.calendarData.readOnlyReason) || '当前订单只读。'
    },
    calendarDialogTitle() {
      if (!this.selectedRow) return '排餐日历'
      return `${this.selectedRow.customerCode || '-'} · ${this.selectedRow.customerName || '-'} · ${this.selectedRow.orderCode || '-'} · ${this.calendarMonth}`
    },
    calendarHasBreakfast() {
      return Boolean(this.calendarData && Number(this.calendarData.breakfastCount) > 0)
    },
    calendarDays() {
      const parsed = this.parseStatsMonth(this.calendarMonth)
      if (!parsed) return []
      const firstDay = new Date(parsed.year, parsed.month - 1, 1)
      const dayCount = new Date(parsed.year, parsed.month, 0).getDate()
      const cells = []
      const leading = firstDay.getDay()
      const previousMonthDays = new Date(parsed.year, parsed.month - 1, 0).getDate()
      for (let day = previousMonthDays - leading + 1; day <= previousMonthDays; day += 1) {
        const date = this.formatDate(new Date(parsed.year, parsed.month - 2, day))
        cells.push({ date, day, currentMonth: false, breakfastCell: null })
      }
      for (let day = 1; day <= dayCount; day += 1) {
        const date = `${this.calendarMonth}-${String(day).padStart(2, '0')}`
        cells.push({
          date,
          day,
          currentMonth: true,
          breakfastCell: this.getCalendarCell(date, 'BREAKFAST')
        })
      }
      while (cells.length < 42) {
        const next = new Date(parsed.year, parsed.month, cells.length - leading - dayCount + 1)
        const date = this.formatDate(next)
        cells.push({ date, day: next.getDate(), currentMonth: false, breakfastCell: null })
      }
      return cells
    }
  },
  created() {
    this.loadData(true)
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
    this.listRequestSequence += 1
    this.calendarRequestSequence += 1
  },
  methods: {
    /**
     * 加载订单分页并忽略筛选变化前发出的旧响应。
     * @param {boolean} reset 是否清空列表后从第一页加载
     */
    loadData(reset = true) {
      if (!reset && (this.loading || this.loadingMore || this.rows.length >= this.page.total)) return
      const requestId = ++this.listRequestSequence
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
      return getMealStats({ ...this.query, page: requestPage, size: this.page.size })
        .then(res => {
          if (requestId !== this.listRequestSequence) return
          const content = Array.isArray(res.content) ? res.content : []
          this.rows = reset ? content : this.rows.concat(content)
          this.page.current = requestPage
          this.page.total = Number(res.totalElements) || 0
        })
        .catch(() => {
          if (requestId === this.listRequestSequence) this.loadError = true
        })
        .finally(() => {
          if (requestId !== this.listRequestSequence) return
          this.loading = false
          this.loadingMore = false
          this.$nextTick(() => {
            this.updateTableHeight()
            if (!this.loadError) this.loadMoreWhenTableFits()
          })
        })
    },
    /**
     * 请求当前筛选条件下的下一页。
     * @param {boolean} retry 是否为失败请求的手动重试
     */
    loadNextPage(retry = false) {
      if (this.loading || this.loadingMore || (this.loadError && !retry) || this.rows.length >= this.page.total) return
      this.loadData(false)
    },
    /** 取得 Element UI 表格实际负责滚动的容器。 */
    getTableScrollContainer() {
      const tableRef = this.$refs.mealStatsTable
      return tableRef && tableRef.$el ? tableRef.$el.querySelector('.el-table__body-wrapper') : null
    },
    /** 绑定表格滚动事件，触底时加载下一页。 */
    bindTableScroll() {
      const body = this.getTableScrollContainer()
      if (!body || this._mealStatsScrollBody === body) return
      this.unbindTableScroll()
      this._mealStatsScrollBody = body
      body.addEventListener('scroll', this.handleTableScroll)
    },
    /** 移除表格滚动事件监听。 */
    unbindTableScroll() {
      if (!this._mealStatsScrollBody) return
      this._mealStatsScrollBody.removeEventListener('scroll', this.handleTableScroll)
      this._mealStatsScrollBody = null
    },
    /** 在滚动位置接近表格底部时加载下一页。 */
    handleTableScroll() {
      const body = this.getTableScrollContainer()
      if (!body || this.loading || this.loadingMore || this.loadError || this.rows.length >= this.page.total) return
      if (body.scrollHeight - body.scrollTop - body.clientHeight <= 100) this.loadNextPage()
    },
    /** 首批内容不足以产生滚动条时继续加载，避免用户无处触发滚动。 */
    loadMoreWhenTableFits() {
      const body = this.getTableScrollContainer()
      if (body && body.scrollHeight <= body.clientHeight && this.rows.length < this.page.total) this.loadNextPage()
    },
    loadDepletionWarnings() {
      getDepletionWarnings().then(res => {
        this.depletionWarnings = res || []
      }).catch(() => {})
    },
    /** 使用当前筛选条件重新加载第一页。 */
    handleQuery() {
      this.loadData(true)
    },
    /** 恢复默认筛选条件并重新查询。 */
    resetQuery() {
      this.query = defaultQuery()
      this.loadData(true)
    },
    updateTableHeight() {
      const tableRef = this.$refs.mealStatsTable
      if (!tableRef || !tableRef.$el) return
      const rect = tableRef.$el.getBoundingClientRect()
      this.tableHeight = Math.max(window.innerHeight - rect.top - 140, 360)
    },
    /**
     * 打开指定订单日历，并用请求序号隔离快速切换的迟到响应。
     * @param {Object} row 当前订单列表行
     */
    openOrderCalendar(row) {
      this.selectedRow = row
      this.calendarMonth = this.query.statsMonth
      this.calendarData = null
      this.calendarCells = []
      this.calendarDraftOverrides = []
      this.calendarRevision = ''
      this.calendarLoadError = false
      this.calendarLoaded = false
      this.calendarDialogVisible = true
      this.loadOrderCalendar()
    },
    /**
     * 请求当前选中订单和打开时月份的日历。
     */
    loadOrderCalendar() {
      if (!this.selectedRow || !this.calendarMonth) return
      const requestId = ++this.calendarRequestSequence
      const orderId = this.selectedRow.orderId
      const statsMonth = this.calendarMonth
      this.calendarLoading = true
      this.calendarLoadError = false
      this.calendarLoaded = false
      return getOrderMealCalendar(orderId, statsMonth)
        .then(data => {
          if (requestId !== this.calendarRequestSequence || !this.calendarDialogVisible) return
          this.calendarData = data
          this.calendarCells = Array.isArray(data.cells) ? data.cells.map(cell => ({ ...cell })) : []
          this.calendarDraftOverrides = Array.isArray(data.overrides) ? data.overrides.map(item => ({ ...item })) : []
          this.calendarRevision = data.revision || ''
          this.calendarLoaded = true
        })
        .catch(() => {
          if (requestId === this.calendarRequestSequence && this.calendarDialogVisible) this.calendarLoadError = true
        })
        .finally(() => {
          if (requestId === this.calendarRequestSequence) this.calendarLoading = false
        })
    },
    /** 关闭时使正在执行的旧日历请求失效。 */
    handleCalendarClose() {
      this.calendarRequestSequence += 1
      this.calendarLoading = false
    },
    /** 关闭订单日历并使迟到响应失效。 */
    closeCalendar() {
      this.calendarRequestSequence += 1
      this.calendarDialogVisible = false
      this.calendarLoading = false
    },
    /** 按日期和餐次取得当前订单的日历格。 */
    getCalendarCell(date, mealType) {
      return this.calendarCells.find(cell => cell.date === date && cell.mealType === mealType) || null
    },
    /** 将用户明确保存的单格数量更新到完整订单覆盖快照。 */
    upsertCalendarOverride(override) {
      const next = { ...override }
      const index = this.calendarDraftOverrides.findIndex(item => item.date === next.date && item.mealType === next.mealType)
      if (index < 0) this.calendarDraftOverrides.push(next)
      else this.$set(this.calendarDraftOverrides, index, next)
      this.updateCalendarCell(next.date, next.mealType, {
        quantity: next.quantity,
        soupQuantity: next.soupQuantity,
        manualOverride: true,
        customerExcluded: false,
        orderExcluded: Number(next.quantity) === 0,
        excluded: Number(next.quantity) === 0
      })
    },
    /** 移除单格覆盖，让服务端恢复当前基础规则。 */
    removeCalendarOverride(cell) {
      if (!cell) return
      this.calendarDraftOverrides = this.calendarDraftOverrides.filter(item =>
        !(item.date === cell.date && item.mealType === cell.mealType))
      this.updateCalendarCell(cell.date, cell.mealType, {
        quantity: Number(cell.baseQuantity) || 0,
        soupQuantity: null,
        manualOverride: false,
        orderExcluded: false,
        excluded: Boolean(cell.customerExcluded)
      })
    },
    /** 更新当前订单指定日历格的本地草稿显示。 */
    updateCalendarCell(date, mealType, changes) {
      const index = this.calendarCells.findIndex(cell => cell.date === date && cell.mealType === mealType)
      if (index >= 0) this.$set(this.calendarCells, index, { ...this.calendarCells[index], ...changes })
    },
    /** 早餐单选式操作只添加或移除当前订单的该格覆盖。 */
    toggleBreakfast(day) {
      const cell = day && day.breakfastCell
      if (!cell || !this.calendarCanEdit || cell.customerExcluded) return
      if (cell.orderExcluded) {
        this.removeCalendarOverride(cell)
      } else {
        this.upsertCalendarOverride({
          date: cell.date,
          mealType: 'BREAKFAST',
          quantity: 0,
          soupQuantity: null,
          remark: ''
        })
      }
    },
    breakfastButtonClass(day) {
      const cell = day && day.breakfastCell
      return {
        'readonly-calendar__meal-button--active': cell && Number(cell.quantity) > 0,
        'readonly-calendar__meal-button--order-stop': cell && cell.orderExcluded,
        'readonly-calendar__meal-button--global-stop': cell && cell.customerExcluded
      }
    },
    breakfastButtonTitle(day) {
      const cell = day && day.breakfastCell
      if (!cell) return '无当前订单早餐单元格'
      if (cell.customerExcluded) return '客户统一停餐，请到客户档案恢复'
      if (cell.orderExcluded) return '当前订单停餐，点击恢复默认'
      return Number(cell.quantity) > 0 ? '当前订单有早餐计划，点击停餐' : '当前订单无早餐计划，点击设置为停餐'
    },
    /**
     * 保存当前订单和打开时月份的完整覆盖快照；409 时保留草稿供用户核对。
     */
    saveCalendar() {
      if (!this.calendarCanSave || !this.selectedRow) return
      this.calendarSaving = true
      const orderId = this.selectedRow.orderId
      const saveCalendarSequence = this.calendarRequestSequence
      const statsMonth = this.calendarMonth
      const payload = {
        statsMonth: this.calendarMonth,
        expectedRevision: this.calendarRevision,
        overrides: this.calendarDraftOverrides.map(item => ({
          date: item.date,
          mealType: item.mealType,
          quantity: item.quantity,
          soupQuantity: Number(item.quantity) === 0 ? null : item.soupQuantity,
          remark: item.remark || ''
        }))
      }
      return saveOrderMealCalendar(orderId, payload)
        .then(() => {
          this.$message.success('订单排餐日历已保存')
          this.loadData(true)
          this.loadDepletionWarnings()
          if (this.calendarRequestSequence === saveCalendarSequence) {
            this.closeCalendar()
          } else if (this.calendarDialogVisible && this.selectedRow &&
            this.selectedRow.orderId === orderId && this.calendarMonth === statsMonth) {
            this.loadOrderCalendar()
          }
        })
        .catch(error => {
          const data = error && error.response && error.response.data
          const message = data && data.message
          if (error && error.response && error.response.status === 409) {
            this.$message.warning(message || '排餐日历已变化，请重新加载后核对草稿')
          } else {
            this.$message.error(message || '订单排餐日历保存失败，请重试')
          }
        })
        .finally(() => {
          this.calendarSaving = false
        })
    },
    countText(value) {
      return value == null ? 0 : value
    },
    statusTagType(status) {
      return Number(status) === 4 ? 'warning' : 'success'
    },
    getCustomMenuImageUrl(path) {
      if (!path) return ''
      if (path.startsWith('http://') || path.startsWith('https://')) return path
      return `${this.baseApi}${path}`
    },
    parseStatsMonth(value) {
      if (!value || !/^\d{4}-\d{2}$/.test(value)) return null
      const [year, month] = value.split('-').map(Number)
      if (month < 1 || month > 12) return null
      return { year, month }
    },
    formatDate(date) {
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    }
  }
}
</script>

<style scoped>
.meal-stats-table { margin-bottom: 16px; }
.meal-stats-value-note { margin-left: 8px; color: #8794a5; font-size: 12px; }
.multiline-cell { white-space: pre-line; line-height: 1.5; }
.allergy-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.metric-help { margin-left: 3px; color: #909399; cursor: help; }
.depletion-warning-row { padding: 2px 0; font-size: 13px; }
.depletion-warning-row__title { margin-right: 8px; font-weight: 600; }
.danger-count { color: #f56c6c; font-weight: 600; }
.custom-menu-thumbnail { width: 58px; height: 42px; }
.meal-stats-load-status { min-height: 32px; display: flex; align-items: center; justify-content: center; color: #909399; font-size: 12px; }
.meal-stats-load-status__message { margin-left: 8px; }
.schedule-calendar-dialog /deep/ .el-dialog { max-width: 1500px; margin-top: 3vh !important; }
.schedule-calendar-dialog /deep/ .el-dialog__body { max-height: 78vh; overflow: auto; }
.schedule-calendar-meta { display: flex; flex-wrap: wrap; gap: 18px; margin-bottom: 12px; color: #606266; font-size: 13px; }
.calendar-loading { padding: 48px 0; color: #8492a4; text-align: center; }
.breakfast-calendar-section { margin-top: 18px; padding-top: 12px; border-top: 1px solid #ebeef5; }
.breakfast-calendar-section__heading { display: flex; align-items: baseline; gap: 12px; margin-bottom: 9px; color: #405776; font-size: 13px; }
.breakfast-calendar-section__heading span { color: #98a3b1; font-size: 12px; }
.readonly-calendar { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); border-top: 1px solid #ebeef5; border-left: 1px solid #ebeef5; }
.readonly-calendar__weekday, .readonly-calendar__day { border-right: 1px solid #ebeef5; border-bottom: 1px solid #ebeef5; }
.readonly-calendar__weekday { height: 36px; line-height: 36px; text-align: center; background: #f5f7fa; color: #606266; font-weight: 600; }
.readonly-calendar__day { min-height: 58px; padding: 6px; background: #fff; }
.readonly-calendar__day--outside { background: #fafafa; color: #c0c4cc; }
.readonly-calendar__day--scheduled { background: #f0f7ff; }
.readonly-calendar__day--global-stop { background: #fff5f5; }
.readonly-calendar__date { margin-bottom: 6px; font-weight: 600; }
.readonly-calendar__meal-button { width: 30px; height: 25px; border: 1px solid #dcdfe6; background: #fff; color: #606266; cursor: pointer; font-size: 12px; padding: 0; }
.readonly-calendar__meal-button:disabled { cursor: not-allowed; }
.readonly-calendar__meal-button--active { border-color: #409eff; color: #217ac0; background: #ecf5ff; }
.readonly-calendar__meal-button--order-stop { border-color: #99a5b4; background: #eef1f5; color: #68788d; }
.readonly-calendar__meal-button--global-stop { border-color: #e6a0a0; background: #fef0f0; color: #c45656; }
.readonly-calendar__reason { display: block; margin-top: 3px; color: #c45656; font-size: 10px; }
@media (max-width: 768px) {
  .meal-stats-value-note { display: block; margin: 6px 0; }
  .schedule-calendar-meta { gap: 8px 14px; }
}
</style>
