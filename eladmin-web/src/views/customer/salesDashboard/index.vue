<template>
  <div class="sales-dashboard">
    <!-- 页面标题 & 筛选条 -->
    <div class="db-header">
      <div class="db-title">
        <i class="el-icon-data-line db-title-icon" />
        <span>销售数据看板</span>
      </div>
      <div class="db-filters">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          size="small"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="yyyy-MM-dd"
          style="width: 260px; margin-right: 12px;"
          @change="onDateRangeChange"
        />
        <el-button type="primary" size="small" icon="el-icon-refresh" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- KPI 卡片：销售额 -->
    <el-row :gutter="20" class="kpi-row">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="kpi-card kpi-today">
          <div class="kpi-icon-wrap kpi-icon-green">
            <i class="el-icon-sunny" />
          </div>
          <div class="kpi-body">
            <p class="kpi-label">今日销售额</p>
            <p class="kpi-value">¥{{ formatMoney(overview.todayAmount) }}</p>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="kpi-card kpi-week">
          <div class="kpi-icon-wrap kpi-icon-teal">
            <i class="el-icon-date" />
          </div>
          <div class="kpi-body">
            <p class="kpi-label">本周销售额</p>
            <p class="kpi-value">¥{{ formatMoney(overview.weekAmount) }}</p>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="kpi-card kpi-month">
          <div class="kpi-icon-wrap kpi-icon-orange">
            <i class="el-icon-s-data" />
          </div>
          <div class="kpi-body">
            <p class="kpi-label">本月销售额</p>
            <p class="kpi-value">¥{{ formatMoney(overview.monthAmount) }}</p>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="kpi-card kpi-total">
          <div class="kpi-icon-wrap kpi-icon-primary">
            <i class="el-icon-money" />
          </div>
          <div class="kpi-body">
            <p class="kpi-label" style="color: rgba(255,255,255,0.85)">累计总销售额</p>
            <p class="kpi-value" style="color: #fff">¥{{ formatMoney(overview.totalAmount) }}</p>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- KPI 卡片：核销额 -->
    <el-row :gutter="20" class="kpi-row" style="margin-top: 0;">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="kpi-card kpi-today">
          <div class="kpi-icon-wrap kpi-icon-purple">
            <i class="el-icon-shopping-cart-2" />
          </div>
          <div class="kpi-body">
            <p class="kpi-label">今日核销额</p>
            <p class="kpi-value">¥{{ formatMoney(overview.todayVerificationAmount) }}</p>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="kpi-card kpi-week">
          <div class="kpi-icon-wrap kpi-icon-indigo">
            <i class="el-icon-calendar" />
          </div>
          <div class="kpi-body">
            <p class="kpi-label">本周核销额</p>
            <p class="kpi-value">¥{{ formatMoney(overview.weekVerificationAmount) }}</p>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="kpi-card kpi-month">
          <div class="kpi-icon-wrap kpi-icon-pink">
            <i class="el-icon-pie-chart" />
          </div>
          <div class="kpi-body">
            <p class="kpi-label">本月核销额</p>
            <p class="kpi-value">¥{{ formatMoney(overview.monthVerificationAmount) }}</p>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="kpi-card kpi-total-verification">
          <div class="kpi-icon-wrap kpi-icon-white">
            <i class="el-icon-coin" />
          </div>
          <div class="kpi-body">
            <p class="kpi-label" style="color: rgba(255,255,255,0.85)">累计核销额</p>
            <p class="kpi-value" style="color: #fff">¥{{ formatMoney(overview.totalVerificationAmount) }}</p>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- TOP3 + 月度趋势 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <!-- 左侧：TOP3 卡片 -->
      <el-col :xs="24" :lg="8">
        <el-row :gutter="16">
          <!-- 产品销量 TOP3 -->
          <el-col :span="24" style="margin-bottom: 16px;">
            <div class="panel">
              <div class="panel-header">
                <span class="panel-title">产品销量</span>
                <el-tag size="mini" type="success" effect="plain">数量</el-tag>
              </div>
              <div class="panel-body">
                <div v-if="!top.productQuantityList || !top.productQuantityList.length" class="no-data">暂无数据</div>
                <template v-else>
                  <div
                    v-for="item in top.productQuantityList"
                    :key="'pq'+item.name"
                    class="rank-item"
                  >
                    <div class="rank-meta">
                      <span class="rank-name">{{ item.name }}</span>
                      <span class="rank-val rank-val-green">{{ item.value }} 份</span>
                    </div>
                    <el-progress
                      :percentage="calcPercent(item.value, top.productQuantityList)"
                      :stroke-width="6"
                      :show-text="false"
                      color="#00bfa5"
                    />
                  </div>
                </template>
              </div>
            </div>
          </el-col>
          <!-- 产品金额 -->
          <el-col :span="24" style="margin-bottom: 16px;">
            <div class="panel">
              <div class="panel-header">
                <span class="panel-title">产品销售额</span>
                <el-tag size="mini" type="warning" effect="plain">金额</el-tag>
              </div>
              <div class="panel-body">
                <div v-if="!top.productAmountList || !top.productAmountList.length" class="no-data">暂无数据</div>
                <template v-else>
                  <div
                    v-for="item in top.productAmountList"
                    :key="'pa'+item.name"
                    class="rank-item"
                  >
                    <div class="rank-meta">
                      <span class="rank-name">{{ item.name }}</span>
                      <span class="rank-val rank-val-orange">¥{{ formatMoney(item.value) }}</span>
                    </div>
                    <el-progress
                      :percentage="calcPercent(item.value, top.productAmountList)"
                      :stroke-width="6"
                      :show-text="false"
                      color="#f5a623"
                    />
                  </div>
                </template>
              </div>
            </div>
          </el-col>
          <!-- 销售员 -->
          <el-col :span="24" style="margin-bottom: 16px;">
            <div class="panel">
              <div class="panel-header">
                <span class="panel-title">销售员业绩</span>
                <el-tag size="mini" type="danger" effect="plain">业绩</el-tag>
              </div>
              <div class="panel-body">
                <div v-if="!top.salespersonList || !top.salespersonList.length" class="no-data">暂无数据</div>
                <template v-else>
                  <div
                    v-for="item in top.salespersonList"
                    :key="'sp'+item.name"
                    class="rank-item"
                  >
                    <div class="rank-meta">
                      <span class="rank-name">{{ item.name }}</span>
                      <span class="rank-val rank-val-primary">¥{{ formatMoney(item.value) }}</span>
                    </div>
                    <el-progress
                      :percentage="calcPercent(item.value, top.salespersonList)"
                      :stroke-width="6"
                      :show-text="false"
                      color="#006b5c"
                    />
                  </div>
                </template>
              </div>
            </div>
          </el-col>
          <!-- 渠道 -->
          <el-col :span="24">
            <div class="panel">
              <div class="panel-header">
                <span class="panel-title">销售渠道</span>
                <el-tag size="mini" effect="plain">渠道</el-tag>
              </div>
              <div class="panel-body">
                <div v-if="!top.channelList || !top.channelList.length" class="no-data">暂无数据</div>
                <template v-else>
                  <div
                    v-for="item in top.channelList"
                    :key="'ch'+item.name"
                    class="rank-item"
                  >
                    <div class="rank-meta">
                      <span class="rank-name">{{ item.name }}</span>
                      <span class="rank-val rank-val-blue">¥{{ formatMoney(item.value) }}</span>
                    </div>
                    <el-progress
                      :percentage="calcPercent(item.value, top.channelList)"
                      :stroke-width="6"
                      :show-text="false"
                      color="#5470c6"
                    />
                  </div>
                </template>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-col>

      <!-- 右侧：月度趋势 + 筛选面板 -->
      <el-col :xs="24" :lg="16">
        <!-- 月度趋势图 -->
        <div class="panel" style="margin-bottom: 20px;">
          <div class="panel-header">
            <span class="panel-title">月度销售趋势</span>
            <div class="year-btns">
              <el-button
                v-for="y in yearOptions"
                :key="y"
                :type="selectedYear === y ? 'primary' : 'default'"
                size="mini"
                style="margin-left: 6px;"
                @click="switchYear(y)"
              >{{ y }} 年</el-button>
            </div>
          </div>
          <div class="panel-body chart-wrap">
            <div ref="monthlyChart" class="echarts-container" />
          </div>
        </div>

        <!-- 筛选查询面板 -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">查询分析</span>
            <el-tag size="mini" effect="dark" type="info">按渠道 / 销售员</el-tag>
          </div>
          <div class="panel-body">
            <el-row :gutter="16">
              <!-- 渠道查询 -->
              <el-col :xs="24" :sm="12">
                <div class="query-card">
                  <p class="query-card-label">渠道查询</p>
                  <el-select
                    v-model="channelQuery"
                    size="small"
                    placeholder="请选择销售渠道"
                    clearable
                    style="width: 100%; margin-bottom: 10px;"
                  >
                    <el-option
                      v-for="item in customerSourceOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                  <el-button type="primary" size="small" style="width:100%" @click="queryChannel">查询渠道数据</el-button>
                  <div v-if="channelResult" class="query-result">
                    <div class="query-result-row">
                      <span>渠道名称</span>
                      <strong>{{ channelResult.channelName }}</strong>
                    </div>
                    <div class="query-result-row">
                      <span>订单数量</span>
                      <strong class="color-teal">{{ channelResult.orderCount }}</strong>
                    </div>
                    <div class="query-result-row">
                      <span>销售金额</span>
                      <strong class="color-orange">¥{{ formatMoney(channelResult.saleAmount) }}</strong>
                    </div>
                  </div>
                  <div v-if="channelNoData" class="no-data" style="margin-top:12px">未查到该渠道数据</div>
                </div>
              </el-col>
              <!-- 销售员查询 -->
              <el-col :xs="24" :sm="12">
                <div class="query-card">
                  <p class="query-card-label">销售员查询</p>
                  <el-select
                    v-model="salespersonQuery"
                    size="small"
                    placeholder="请选择父套餐（销售员）"
                    clearable
                    filterable
                    style="width: 100%; margin-bottom: 10px;"
                  >
                    <el-option
                      v-for="item in parentPackages"
                      :key="item.id"
                      :label="item.packageName"
                      :value="item.id"
                    />
                  </el-select>
                  <el-button type="primary" size="small" style="width:100%" @click="querySalesperson">查询销售员数据</el-button>
                  <div v-if="salespersonResult" class="query-result">
                    <div class="query-result-row">
                      <span>销售员名称</span>
                      <strong>{{ salespersonResult.salespersonName }}</strong>
                    </div>
                    <div class="query-result-row">
                      <span>订单数量</span>
                      <strong class="color-teal">{{ salespersonResult.orderCount }}</strong>
                    </div>
                    <div class="query-result-row">
                      <span>销售金额</span>
                      <strong class="color-orange">¥{{ formatMoney(salespersonResult.saleAmount) }}</strong>
                    </div>
                  </div>
                  <div v-if="salespersonNoData" class="no-data" style="margin-top:12px">未查到该销售员数据</div>
                </div>
              </el-col>
            </el-row>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 销售明细表 -->
    <div class="panel" style="margin-bottom: 30px;">
      <div class="panel-header">
        <span class="panel-title">销售明细</span>
        <!-- 明细筛选 -->
        <div class="detail-filters">
          <el-select
            v-model="detailQuery.customerSource"
            size="mini"
            placeholder="销售渠道"
            clearable
            style="width: 130px; margin-right: 8px;"
          >
            <el-option
              v-for="item in customerSourceOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-date-picker
            v-model="detailDateRange"
            type="daterange"
            size="mini"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="yyyy-MM-dd"
            style="width: 220px; margin-right: 8px;"
          />
          <el-button size="mini" type="primary" @click="loadDetail(1)">搜索</el-button>
        </div>
      </div>
      <div class="panel-body" style="padding: 0;">
        <el-table
          v-loading="tableLoading"
          :data="tableData"
          size="small"
          stripe
          style="width: 100%"
        >
          <el-table-column label="序号" type="index" width="60" align="center" />
          <el-table-column label="销售日期" prop="saleDate" width="150" />
          <el-table-column label="产品" min-width="120" show-overflow-tooltip>
            <template slot-scope="scope">
              <span>
                {{ formatDishCount(scope.row) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="餐数" prop="mealCount" width="70" align="center" />
          <el-table-column label="客户备注" prop="customerRemark" width="100" />
          <el-table-column label="销售金额" prop="saleAmount" width="110" align="right">
            <template slot-scope="scope">
              <span class="color-primary">¥{{ formatMoney(scope.row.saleAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="销售渠道" prop="channelName" width="110">
            <template slot-scope="scope">
              <el-tag size="mini" effect="plain">{{ scope.row.channelName || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="销售员" prop="salespersonName" width="110" />
        </el-table>
      </div>
      <div class="panel-footer">
        <el-pagination
          :current-page="detailPage.current"
          :page-sizes="[10, 20, 50]"
          :page-size="detailPage.size"
          :total="detailPage.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<script>
import echarts from 'echarts'
import * as dashboardApi from '@/api/salesDashboard'
import * as dictDetailApi from '@/api/system/dictDetail'
import * as packageApi from '@/api/customer/package'

export default {
  name: 'SalesDashboard',
  data() {
    const currentYear = new Date().getFullYear()
    return {
      // 顶部日期筛选
      dateRange: null,
      // KPI 数据
      overview: {
        todayAmount: 0,
        weekAmount: 0,
        monthAmount: 0,
        totalAmount: 0,
        todayVerificationAmount: 0,
        weekVerificationAmount: 0,
        monthVerificationAmount: 0,
        totalVerificationAmount: 0
      },
      // 产品/渠道/人员
      top: {
        productQuantityList: [],
        productAmountList: [],
        salespersonList: [],
        channelList: []
      },
      // 月度趋势
      selectedYear: currentYear,
      yearOptions: [currentYear - 1, currentYear],
      monthlyChart: null,
      // 销售渠道字典
      customerSourceOptions: [],
      // 父套餐列表（销售员）
      parentPackages: [],
      // 渠道查询
      channelQuery: '',
      channelResult: null,
      channelNoData: false,
      // 销售员查询
      salespersonQuery: null,
      salespersonResult: null,
      salespersonNoData: false,
      // 明细表
      tableLoading: false,
      tableData: [],
      detailDateRange: null,
      detailQuery: {
        customerSource: ''
      },
      detailPage: {
        current: 1,
        size: 10,
        total: 0
      }
    }
  },
  created() {
    this.loadCustomerSourceDict()
    this.loadParentPackages()
  },
  mounted() {
    this.loadAll()
  },
  beforeDestroy() {
    if (this.monthlyChart) {
      this.monthlyChart.dispose()
      this.monthlyChart = null
    }
  },
  methods: {
    // ─── 字典与基础数据 ──────────────────────────────────
    loadCustomerSourceDict() {
      dictDetailApi.get('customer_source').then(res => {
        this.customerSourceOptions = (res.content || res.data || res || []).map(item => ({
          value: item.value,
          label: item.label
        }))
      }).catch(() => {})
    },

    loadParentPackages() {
      packageApi.getParents().then(res => {
        const list = res.content || res.data || res || []
        this.parentPackages = Array.isArray(list) ? list : []
      }).catch(() => {})
    },

    // ─── 日期筛选 ──────────────────────────────────────────
    onDateRangeChange() {
      this.loadOverview()
      this.loadTop()
    },

    // ─── 统一加载入口 ──────────────────────────────────────
    loadAll() {
      this.loadOverview()
      this.loadTop()
      this.loadMonthly()
      this.loadDetail(1)
    },

    getDateParams() {
      const params = {}
      if (this.dateRange && this.dateRange.length === 2) {
        params.startDate = this.dateRange[0]
        params.endDate = this.dateRange[1]
      }
      return params
    },

    // ─── KPI 概览 ──────────────────────────────────────────
    loadOverview() {
      dashboardApi.getOverview(this.getDateParams()).then(res => {
        this.overview = res || {}
      }).catch(() => {})
    },

    // ─── TOP3 ──────────────────────────────────────────────
    loadTop() {
      dashboardApi.getTop(this.getDateParams()).then(res => {
        this.top = res || {}
      }).catch(() => {})
    },

    // ─── 月度趋势 ──────────────────────────────────────────
    loadMonthly() {
      dashboardApi.getMonthly({ year: this.selectedYear }).then(res => {
        const months = (res && res.months) ? res.months : []
        this.renderMonthlyChart(months)
      }).catch(() => {
        this.renderMonthlyChart([])
      })
    },

    switchYear(year) {
      this.selectedYear = year
      this.loadMonthly()
    },

    renderMonthlyChart(months) {
      this.$nextTick(() => {
        const el = this.$refs.monthlyChart
        if (!el) return
        if (!this.monthlyChart) {
          this.monthlyChart = echarts.init(el)
        }
        const labels = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
        // 补齐 12 个月，缺失的填 0
        const dataMap = {}
        months.forEach(m => { dataMap[m.month] = m.amount })
        const values = Array.from({ length: 12 }, (_, i) => dataMap[i + 1] || 0)

        const option = {
          tooltip: {
            trigger: 'axis',
            formatter: params => {
              const p = params[0]
              return `${p.name}<br/>销售额：¥${this.formatMoney(p.value)}`
            }
          },
          grid: { top: 20, right: 20, bottom: 30, left: 60 },
          xAxis: {
            type: 'category',
            data: labels,
            axisLine: { lineStyle: { color: '#d0d0d0' }},
            axisTick: { show: false },
            axisLabel: { fontSize: 11, color: '#666' }
          },
          yAxis: {
            type: 'value',
            axisLabel: {
              fontSize: 11,
              color: '#666',
              formatter: val => val >= 10000 ? (val / 10000).toFixed(0) + 'w' : val
            },
            splitLine: { lineStyle: { color: '#f0f0f0' }}
          },
          series: [{
            type: 'bar',
            data: values,
            barMaxWidth: 36,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#00bfa5' },
                { offset: 1, color: '#006b5c' }
              ]),
              borderRadius: [4, 4, 0, 0]
            },
            emphasis: {
              itemStyle: { color: '#f5a623' }
            }
          }]
        }
        this.monthlyChart.setOption(option, true)
        // 响应窗口缩放
        window.addEventListener('resize', this._onChartResize)
      })
    },

    _onChartResize() {
      if (this.monthlyChart) this.monthlyChart.resize()
    },

    // ─── 渠道 / 销售员查询 ─────────────────────────────────
    queryChannel() {
      const cs = (this.channelQuery || '').trim()
      if (!cs) {
        this.$message.warning('请输入渠道名称')
        return
      }
      this.channelResult = null
      this.channelNoData = false
      const params = { customerSource: cs, ...this.getDateParams() }
      dashboardApi.getChannelSummary(params).then(res => {
        if (res && res.channelName) {
          this.channelResult = res
        } else {
          this.channelNoData = true
        }
      }).catch(() => { this.channelNoData = true })
    },

    querySalesperson() {
      const id = this.salespersonQuery
      if (!id) {
        this.$message.warning('请选择销售员（父套餐）')
        return
      }
      this.salespersonResult = null
      this.salespersonNoData = false
      const params = { parentPackageId: id, ...this.getDateParams() }
      dashboardApi.getSalespersonSummary(params).then(res => {
        if (res && res.salespersonName) {
          this.salespersonResult = res
        } else {
          this.salespersonNoData = true
        }
      }).catch(() => { this.salespersonNoData = true })
    },

    // ─── 明细表 ────────────────────────────────────────────
    loadDetail(page) {
      if (page) this.detailPage.current = page
      this.tableLoading = true
      const params = {
        page: this.detailPage.current,
        size: this.detailPage.size
      }
      if (this.detailQuery.customerSource) {
        params.customerSource = this.detailQuery.customerSource
      }
      if (this.detailDateRange && this.detailDateRange.length === 2) {
        params.startDate = this.detailDateRange[0]
        params.endDate = this.detailDateRange[1]
      }
      dashboardApi.getDetail(params).then(res => {
        this.tableData = (res && res.content) ? res.content : []
        this.detailPage.total = (res && res.totalElements) ? res.totalElements : 0
      }).catch(() => {
        this.tableData = []
        this.detailPage.total = 0
      }).finally(() => {
        this.tableLoading = false
      })
    },

    onSizeChange(size) {
      this.detailPage.size = size
      this.loadDetail(1)
    },

    onPageChange(page) {
      this.detailPage.current = page
      this.loadDetail()
    },

    // ─── 工具方法 ──────────────────────────────────────────
    formatMoney(val) {
      const n = Number(val)
      if (isNaN(n)) return '0.00'
      return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    },

    calcPercent(val, list) {
      if (!list || !list.length) return 0
      const max = Math.max(...list.map(i => Number(i.value) || 0))
      if (!max) return 0
      return Math.round((Number(val) / max) * 100)
    },

    formatDishCount(row) {
      const main = Number(row.mainDishCount) || 0
      const side = Number(row.sideDishCount) || 0
      const veg = Number(row.vegCount) || 0
      const soup = Number(row.soupCount) || 0

      const meatCount = main + side
      if (meatCount === 0) return '-'

      const parts = []
      if (meatCount > 0) parts.push(`${meatCount}荤`)
      if (veg > 0) parts.push(`${veg}素`)
      if (soup > 0) parts.push(`${soup}汤`)
      return parts.join('')
    }
  }
}
</script>

<style scoped>
/* ── 整体容器 ── */
.sales-dashboard {
  padding: 20px;
  background: #f4f6f8;
  min-height: 100%;
}

/* ── 页面标题栏 ── */
.db-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.db-title {
  font-size: 20px;
  font-weight: 700;
  color: #1a2b3c;
  display: flex;
  align-items: center;
  gap: 8px;
}
.db-title-icon {
  font-size: 22px;
  color: #006b5c;
}
.db-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

/* ── KPI 卡片 ── */
.kpi-row {
  margin-bottom: 20px;
}
.kpi-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  min-height: 90px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.kpi-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.10);
}
.kpi-total {
  background: linear-gradient(135deg, #006b5c 0%, #00bfa5 100%);
}
.kpi-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.kpi-icon-green { background: #e8f8f4; color: #00bfa5; }
.kpi-icon-teal  { background: #e0f7f4; color: #00897b; }
.kpi-icon-orange { background: #fff3e0; color: #f5a623; }
.kpi-icon-primary { background: rgba(255,255,255,0.25); color: #fff; }
.kpi-icon-purple { background: #f3e5f5; color: #8e24aa; }
.kpi-icon-indigo  { background: #e8eaf6; color: #3949ab; }
.kpi-icon-pink    { background: #fce4ec; color: #c62828; }
.kpi-icon-white   { background: rgba(255,255,255,0.25); color: #fff; }

.kpi-total-verification {
  background: linear-gradient(135deg, #8e24aa 0%, #ab47bc 100%);
}
.kpi-body { flex: 1; }
.kpi-label {
  font-size: 12px;
  color: #8a9baa;
  margin: 0 0 6px;
  font-weight: 500;
}
.kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: #1a2b3c;
  margin: 0;
  letter-spacing: -0.5px;
}

/* ── 面板通用 ── */
.panel {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f2f5;
  flex-wrap: wrap;
  gap: 8px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a2b3c;
}
.panel-body {
  padding: 16px 20px;
}
.panel-footer {
  border-top: 1px solid #f0f2f5;
  padding: 12px 20px;
  display: flex;
  justify-content: flex-end;
}

/* ── 排行条目 ── */
.rank-item {
  margin-bottom: 14px;
}
.rank-item:last-child { margin-bottom: 0; }
.rank-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.rank-badge {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.rank-gold   { background: #fff3cd; color: #d4a207; }
.rank-silver { background: #f0f0f0; color: #7a7a7a; }
.rank-bronze { background: #fde8da; color: #b25000; }
.rank-name {
  flex: 1;
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-val {
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
.rank-val-green   { color: #00897b; }
.rank-val-orange  { color: #f5a623; }
.rank-val-primary { color: #006b5c; }
.rank-val-blue    { color: #5470c6; }

/* ── 图表 ── */
.chart-wrap { padding: 10px 16px 0; }
.echarts-container {
  width: 100%;
  height: 260px;
}
.year-btns { display: flex; flex-wrap: wrap; }

/* ── 查询卡片 ── */
.query-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 16px;
  height: 100%;
  box-sizing: border-box;
}
.query-card-label {
  font-size: 12px;
  font-weight: 600;
  color: #6b7a8d;
  margin: 0 0 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.query-result {
  margin-top: 14px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
}
.query-result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 9px 14px;
  font-size: 13px;
  border-bottom: 1px solid #f0f2f5;
}
.query-result-row:last-child { border-bottom: none; }
.query-result-row span { color: #8a9baa; }

/* ── 明细筛选 ── */
.detail-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

/* ── 颜色工具 ── */
.color-primary { color: #006b5c; font-weight: 600; }
.color-teal    { color: #00897b; font-weight: 600; }
.color-orange  { color: #f5a623; font-weight: 600; }

/* ── 空状态 ── */
.no-data {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 20px 0;
}
</style>
