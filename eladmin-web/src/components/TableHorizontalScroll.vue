<template>
  <div class="table-horizontal-scroll">
    <input
      ref="range"
      class="table-horizontal-scroll__range"
      type="range"
      min="0"
      :max="maxScroll"
      step="1"
      :value="scrollPosition"
      :disabled="maxScroll === 0"
      :style="{ '--scroll-thumb-width': thumbWidth + 'px' }"
      aria-label="左右滚动表格"
      @input="handleInput"
    >
  </div>
</template>

<script>
export default {
  name: 'TableHorizontalScroll',
  props: {
    table: { type: Object, default: null }
  },
  data() {
    return { maxScroll: 0, scrollPosition: 0, thumbWidth: 24 }
  },
  watch: {
    table() {
      this.$nextTick(() => this.bindTable())
    },
    'table.layout.bodyWidth'() {
      this.$nextTick(() => this.updateMetrics())
    }
  },
  created() {
    this.scrollBody = null
    this.resizeObserver = null
    this.scrollActive = false
  },
  mounted() {
    this.activate()
  },
  activated() {
    this.activate()
  },
  deactivated() {
    this.deactivate()
  },
  beforeDestroy() {
    this.deactivate()
  },
  methods: {
    /** 挂载或恢复页面时绑定表格，并监听可见区域尺寸变化。 */
    activate() {
      this.scrollActive = true
      window.addEventListener('resize', this.updateMetrics)
      this.$nextTick(() => this.bindTable())
    },
    /** 离开页面时释放滚动和尺寸监听。 */
    deactivate() {
      this.scrollActive = false
      window.removeEventListener('resize', this.updateMetrics)
      this.unbindTable()
    },
    /** 绑定 Element UI 表格主体，列宽变化时同步滚动范围和滑块宽度。 */
    bindTable() {
      if (!this.scrollActive) return
      const body = this.table && this.table.bodyWrapper
      if (body !== this.scrollBody) {
        this.unbindTable()
        this.scrollBody = body
        if (body) {
          body.addEventListener('scroll', this.syncFromTable)
          if (typeof ResizeObserver !== 'undefined') {
            this.resizeObserver = new ResizeObserver(() => this.updateMetrics())
            this.resizeObserver.observe(body)
            const content = body.querySelector('table')
            if (content) this.resizeObserver.observe(content)
          }
        }
      }
      this.updateMetrics()
    },
    /** 解绑旧表格，防止缓存切换或销毁后继续触发更新。 */
    unbindTable() {
      if (this.scrollBody) this.scrollBody.removeEventListener('scroll', this.syncFromTable)
      if (this.resizeObserver) this.resizeObserver.disconnect()
      this.scrollBody = null
      this.resizeObserver = null
    },
    /** 按表格内容与可见宽度计算可滚动距离；无溢出时保留禁用轨道。 */
    updateMetrics() {
      if (!this.scrollActive) return
      const body = this.scrollBody
      const range = this.$refs.range
      if (!body || !range) {
        this.maxScroll = 0
        this.scrollPosition = 0
        return
      }
      this.maxScroll = Math.max(body.scrollWidth - body.clientWidth, 0)
      const ratio = body.scrollWidth > 0 ? Math.min(body.clientWidth / body.scrollWidth, 1) : 1
      this.thumbWidth = Math.min(range.clientWidth, Math.max(24, range.clientWidth * ratio))
      this.scrollTo(body.scrollLeft)
    },
    /** 原生触控板或表格滚动后更新常显滑块位置。 */
    syncFromTable() {
      if (this.scrollBody) this.scrollPosition = Math.min(this.maxScroll, Math.max(0, this.scrollBody.scrollLeft))
    },
    /** 将拖动、点击或键盘操作产生的位置写入表格。
     * @param {Event} event 常显滑块的输入事件
     */
    handleInput(event) {
      this.scrollTo(Number(event.target.value))
    },
    /** 同步主体、表头和汇总行的位置，保持所有列对齐。
     * @param {number} position 请求的横向像素位置，自动限定在可滚动范围内
     */
    scrollTo(position) {
      this.scrollPosition = Math.min(this.maxScroll, Math.max(0, position))
      if (!this.table || !this.scrollBody) return
      this.scrollBody.scrollLeft = this.scrollPosition
      if (this.table.headerWrapper) this.table.headerWrapper.scrollLeft = this.scrollPosition
      if (this.table.footerWrapper) this.table.footerWrapper.scrollLeft = this.scrollPosition
    }
  }
}
</script>

<style>
/* 主体仍支持触控板滚动，由常显控件承担横向拖动入口。 */
.table-horizontal-scroll-table .el-table__body-wrapper::-webkit-scrollbar {
  height: 0;
}
.table-horizontal-scroll {
  position: sticky;
  bottom: 0;
  z-index: 4;
  padding: 4px 0;
  background: #fff;
}
.table-horizontal-scroll__range {
  display: block;
  width: 100%;
  height: 12px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: #f0f2f5;
  appearance: none;
  -webkit-appearance: none;
  cursor: pointer;
}
.table-horizontal-scroll__range:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: 2px;
}
.table-horizontal-scroll__range::-webkit-slider-thumb {
  width: var(--scroll-thumb-width);
  height: 12px;
  border: 2px solid #f0f2f5;
  border-radius: 6px;
  background: #909399;
  appearance: none;
  -webkit-appearance: none;
}
.table-horizontal-scroll__range::-moz-range-thumb {
  width: var(--scroll-thumb-width);
  height: 8px;
  border: 2px solid #f0f2f5;
  border-radius: 6px;
  background: #909399;
}
.table-horizontal-scroll__range::-moz-range-track {
  height: 12px;
  border-radius: 6px;
  background: #f0f2f5;
}
.table-horizontal-scroll__range:disabled {
  cursor: default;
}
.table-horizontal-scroll__range:disabled::-webkit-slider-thumb {
  background: #c0c4cc;
}
.table-horizontal-scroll__range:disabled::-moz-range-thumb {
  background: #c0c4cc;
}
</style>
