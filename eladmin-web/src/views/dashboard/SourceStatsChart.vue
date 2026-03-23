<template>
  <div :class="className" :style="{height:height,width:width}" />
</template>

<script>
import echarts from 'echarts'
require('echarts/theme/macarons')
import { debounce } from '@/utils'

const animationDuration = 1200

export default {
  props: {
    className: {
      type: String,
      default: 'chart'
    },
    width: {
      type: String,
      default: '100%'
    },
    height: {
      type: String,
      default: '300px'
    },
    chartData: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      chart: null
    }
  },
  watch: {
    chartData: {
      deep: true,
      handler() {
        this.setOption()
      }
    }
  },
  mounted() {
    this.initChart()
    this.__resizeHandler = debounce(() => {
      if (this.chart) {
        this.chart.resize()
      }
    }, 100)
    window.addEventListener('resize', this.__resizeHandler)
  },
  beforeDestroy() {
    if (!this.chart) {
      return
    }
    window.removeEventListener('resize', this.__resizeHandler)
    this.chart.dispose()
    this.chart = null
  },
  methods: {
    initChart() {
      this.chart = echarts.init(this.$el, 'macarons')
      this.setOption()
    },
    setOption() {
      if (!this.chart) return

      const pieData = this.chartData.map(item => ({
        name: item.sourceDesc,
        value: item.customerCount
      }))

      this.chart.setOption({
        tooltip: {
          trigger: 'item',
          formatter: (params) => {
            return `${params.name}<br/>客户数：<strong>${params.value}</strong> 人<br/>占比：${params.percent}%`
          }
        },
        legend: {
          orient: 'vertical',
          right: 10,
          top: 'middle',
          itemWidth: 12,
          itemHeight: 12,
          textStyle: {
            fontSize: 12
          },
          data: this.chartData.map(item => item.sourceDesc)
        },
        series: [{
          name: '客户来源',
          type: 'pie',
          radius: ['35%', '60%'],
          center: ['38%', '50%'],
          avoidLabelOverlap: true,
          itemStyle: {
            borderRadius: 6,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: true,
            formatter: '{b}: {c} 人',
            fontSize: 12,
            color: '#666'
          },
          labelLine: {
            show: true
          },
          data: pieData,
          animationDuration,
          animationEasing: 'cubicInOut'
        }]
      })
    }
  }
}
</script>
