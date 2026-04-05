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

      const xData = this.chartData.map(item => item.packageName)
      const yData = this.chartData.map(item => item.mealCount)

      this.chart.setOption({
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          },
          formatter: (params) => {
            const item = params[0]
            return `${item.name}<br/>餐数：<strong>${item.value}</strong> 份`
          }
        },
        grid: {
          top: 20,
          left: 60,
          right: 20,
          bottom: 40,
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
            rotate: 15,
            fontSize: 12
          }
        },
        yAxis: {
          type: 'value',
          axisTick: {
            show: false
          },
          min: 0,
          minInterval: 1,
          axisLabel: {
            fontSize: 12,
            formatter: '{value} 份'
          },
          splitLine: {
            lineStyle: {
              type: 'dashed'
            }
          }
        },
        series: [{
          name: '餐数',
          type: 'bar',
          barWidth: '40%',
          data: yData,
          itemStyle: {
            color: function(params) {
              const colorList = ['#3ba272', '#fc8452', '#9a60b4', '#ea7ccc', '#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de']
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
