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
    },
    mealType: {
      type: String,
      default: 'LUNCH'
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

      const xData = this.chartData.map(item => item.mealPackageDesc)
      const yData = this.chartData.map(item => item.customerCount)

      this.chart.setOption({
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          },
          formatter: (params) => {
            const item = params[0]
            return `${item.name}<br/>客户数：<strong>${item.value}</strong> 人`
          }
        },
        grid: {
          top: 20,
          left: '2%',
          right: '2%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: xData,
          axisTick: {
            alignWithLabel: true
          },
          axisLabel: {
            interval: 0,
            rotate: 0,
            fontSize: 12
          }
        },
        yAxis: {
          type: 'value',
          name: '客户数',
          axisTick: {
            show: false
          },
          min: 0,
          minInterval: 1
        },
        series: [{
          name: '客户数',
          type: 'bar',
          barWidth: '50%',
          data: yData,
          itemStyle: {
            color: function(params) {
              const colorList = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de']
              return colorList[params.dataIndex % colorList.length]
            },
            borderRadius: [4, 4, 0, 0]
          },
          label: {
            show: true,
            position: 'top',
            fontSize: 12,
            color: '#666'
          },
          animationDuration
        }]
      })
    }
  }
}
</script>
