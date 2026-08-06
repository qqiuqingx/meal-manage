<template>
  <div class="agent-result-chart">
    <div v-if="chartOption" ref="chart" class="chart-canvas" />
    <div v-else class="chart-empty">暂无图表数据</div>
    <div v-if="renderError" class="chart-error">图表暂时无法展示</div>
  </div>
</template>

<script>
import echarts from 'echarts'
import {
  formatValue,
  isSafeLabel,
  isSafePath,
  readPath
} from '../utils/agentPresentationFormatters'

const CHART_TYPES = ['BAR', 'LINE', 'PIE']
const MAX_CATEGORIES = 50
const MAX_PIE_CATEGORIES = 8
const MAX_METRICS = 4
const CHART_COLORS = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C']

function finiteNumber(value) {
  if (Object.prototype.toString.call(value) === '[object Number]') {
    return isFinite(value) && !isNaN(value) ? value : 0
  }
  if (Object.prototype.toString.call(value) === '[object String]' && value.trim() !== '' && isFinite(Number(value))) {
    return Number(value)
  }
  return 0
}

export default {
  name: 'AgentResultChart',
  props: {
    card: { type: Object, default: null },
    chart: { type: Object, default: null },
    data: { type: [Object, Array], default: null },
    descriptor: { type: Object, default: null },
    visible: { type: Boolean, default: true }
  },
  data() {
    return {
      chartInstance: null,
      renderError: false
    }
  },
  computed: {
    sourceData() {
      if (this.data !== null && this.data !== undefined) {
        return this.data
      }
      return this.card && this.card.data ? this.card.data : {}
    },
    chartDescriptor() {
      return this.chart || (this.descriptor && this.descriptor.chart) || null
    },
    requestedChartType() {
      return String(this.chartDescriptor && this.chartDescriptor.type || '').toUpperCase()
    },
    chartRows() {
      const descriptor = this.chartDescriptor
      if (!descriptor || !isSafePath(descriptor.dataPath)) {
        return []
      }
      const rows = readPath(this.sourceData, descriptor.dataPath)
      return Array.isArray(rows)
        ? rows.filter(row => row && Object.prototype.toString.call(row) === '[object Object]')
        : []
    },
    metricDefinitions() {
      const descriptor = this.chartDescriptor || {}
      const fields = Array.isArray(descriptor.metricFields) ? descriptor.metricFields : []
      const labels = Array.isArray(descriptor.metricLabels) ? descriptor.metricLabels : []
      return fields
        .filter(field => isSafePath(field))
        .slice(0, MAX_METRICS)
        .map((field, index) => ({
          field,
          label: isSafeLabel(labels[index], 30) ? labels[index] : field
        }))
    },
    dimensionField() {
      return this.chartDescriptor && isSafePath(this.chartDescriptor.dimensionField)
        ? this.chartDescriptor.dimensionField
        : null
    },
    chartType() {
      const requested = this.requestedChartType
      if (CHART_TYPES.indexOf(requested) < 0 || !this.dimensionField || !this.metricDefinitions.length || !this.chartRows.length) {
        return null
      }
      if (this.chartRows.length > MAX_CATEGORIES) {
        return null
      }
      if (requested === 'PIE' && this.chartRows.length > MAX_PIE_CATEGORIES) {
        return 'BAR'
      }
      return requested
    },
    isOverLimit() {
      if (!this.chartRows.length) {
        return false
      }
      return this.chartRows.length > MAX_CATEGORIES ||
        (this.requestedChartType === 'PIE' && this.chartRows.length > MAX_CATEGORIES)
    },
    chartCategories() {
      return this.chartRows.map(row => formatValue(readPath(row, this.dimensionField), 'TEXT'))
    },
    chartOption() {
      if (!this.chartType) {
        return null
      }
      return this.buildChartOption(this.chartType)
    }
  },
  watch: {
    data: {
      deep: true,
      handler() { this.scheduleRender() }
    },
    chart: {
      deep: true,
      handler() { this.scheduleRender() }
    },
    visible(value) {
      if (value) {
        this.scheduleRender()
      } else {
        this.disposeChart()
      }
    }
  },
  mounted() {
    const browserWindow = this.$el && this.$el.ownerDocument && this.$el.ownerDocument.defaultView
    if (browserWindow) {
      browserWindow.addEventListener('resize', this.handleResize)
    }
    this.scheduleRender()
  },
  beforeDestroy() {
    const browserWindow = this.$el && this.$el.ownerDocument && this.$el.ownerDocument.defaultView
    if (browserWindow) {
      browserWindow.removeEventListener('resize', this.handleResize)
    }
    this.disposeChart()
  },
  methods: {
    /** 由固定字段描述构造 ECharts option，不接受服务端 option 或脚本。 */
    buildChartOption(type) {
      const descriptor = this.chartDescriptor || {}
      const metricDefinitions = this.metricDefinitions
      const categories = this.chartCategories
      const series = type === 'PIE'
        ? [{
          name: metricDefinitions[0].label,
          type: 'pie',
          radius: '55%',
          data: this.chartRows.map((row, index) => ({
            name: categories[index],
            value: finiteNumber(readPath(row, metricDefinitions[0].field))
          }))
        }]
        : metricDefinitions.map((metric, index) => ({
          name: metric.label,
          type: type === 'LINE' ? 'line' : 'bar',
          smooth: false,
          itemStyle: { color: CHART_COLORS[index] },
          data: this.chartRows.map(row => finiteNumber(readPath(row, metric.field)))
        }))

      if (type === 'PIE') {
        return {
          animation: false,
          color: CHART_COLORS,
          tooltip: { trigger: 'item' },
          legend: { type: 'scroll', data: categories },
          series
        }
      }
      return {
        animation: false,
        color: CHART_COLORS,
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
        xAxis: {
          type: 'category',
          name: isSafeLabel(descriptor.dimensionLabel, 30) ? descriptor.dimensionLabel : '',
          data: categories
        },
        yAxis: { type: 'value' },
        series
      }
    },

    /** 在页签切换或输入更新后等待 DOM 稳定，再尝试初始化图表。 */
    scheduleRender() {
      const render = () => this.renderChart()
      if (this.$nextTick) {
        this.$nextTick(render)
      } else {
        render()
      }
    },

    /** 初始化固定 option；空数据不创建 ECharts 实例，超限数据交由容器回退。 */
    renderChart() {
      this.disposeChart()
      this.renderError = false
      if (!this.visible || !this.chartOption) {
        if (this.isOverLimit) {
          this.renderError = true
          this.$emit('render-error', this.requestedChartType)
        }
        return false
      }
      const element = this.$refs && this.$refs.chart
      if (!element) {
        return false
      }
      try {
        this.chartInstance = echarts.init(element)
        this.chartInstance.setOption(this.chartOption, true)
        return true
      } catch (error) {
        this.renderError = true
        this.disposeChart()
        this.$emit('render-error', this.requestedChartType)
        return false
      }
    },

    /** 响应窗口变化，保持当前图表实例尺寸与容器一致。 */
    handleResize() {
      if (this.chartInstance && Object.prototype.toString.call(this.chartInstance.resize) === '[object Function]') {
        try {
          this.chartInstance.resize()
        } catch (error) {
          this.renderError = true
          this.disposeChart()
          this.$emit('render-error', this.requestedChartType)
        }
      }
    },

    /** 释放当前实例，避免页签切换和组件销毁造成 ECharts 泄漏。 */
    disposeChart() {
      if (this.chartInstance && Object.prototype.toString.call(this.chartInstance.dispose) === '[object Function]') {
        try {
          this.chartInstance.dispose()
        } catch (error) {
          // ECharts 已处于销毁状态时忽略二次释放异常。
        }
      }
      this.chartInstance = null
    }
  }
}
</script>

<style scoped>
.agent-result-chart {
  min-height: 280px;
}

.chart-canvas {
  width: 100%;
  height: 300px;
}

.chart-empty,
.chart-error {
  padding: 32px 16px;
  color: #909399;
  text-align: center;
}

.chart-error {
  padding-top: 0;
  color: #e6a23c;
}
</style>
