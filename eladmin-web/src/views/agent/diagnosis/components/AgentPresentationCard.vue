<template>
  <div class="agent-presentation-card">
    <div class="presentation-header">
      <span class="presentation-title">{{ title }}</span>
      <el-tag v-if="decisionSource" size="mini" type="info">展示规则：{{ decisionSource }}</el-tag>
    </div>

    <el-alert
      v-if="hasNotice"
      class="presentation-notice"
      :title="noticeText"
      type="warning"
      :closable="false"
      show-icon
    />
    <div v-if="queriedAt" class="presentation-time">查询时间：{{ queriedAt }}</div>

    <AgentResultSummary
      v-if="showSummaryAbove"
      :data="cardData"
      :descriptor="safePresentation"
    />

    <el-tabs v-if="hasTabs" v-model="selectedView" class="presentation-tabs" @tab-click="handleTabChange">
      <el-tab-pane v-for="view in validViews" :key="view" :label="viewLabel(view)" :name="view">
        <div v-if="currentView === view" class="presentation-view">
          <AgentResultSummary
            v-if="view === 'TEXT'"
            :data="cardData"
            :descriptor="safePresentation"
          />
          <AgentResultTable
            v-else-if="view === 'TABLE'"
            :data="cardData"
            :descriptor="safePresentation"
          />
          <AgentResultChart
            v-else-if="isChartView(view)"
            :data="cardData"
            :descriptor="safePresentation"
            @render-error="handleChartError(view)"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <div v-else-if="currentView" class="presentation-view">
      <AgentResultSummary
        v-if="currentView === 'TEXT'"
        :data="cardData"
        :descriptor="safePresentation"
      />
      <AgentResultTable
        v-else-if="currentView === 'TABLE'"
        :data="cardData"
        :descriptor="safePresentation"
      />
      <AgentResultChart
        v-else-if="isChartView(currentView)"
        :data="cardData"
        :descriptor="safePresentation"
        @render-error="handleChartError(currentView)"
      />
    </div>
    <div v-else class="presentation-empty">展示格式暂不可用</div>
  </div>
</template>

<script>
import AgentResultChart from './AgentResultChart.vue'
import AgentResultSummary from './AgentResultSummary.vue'
import AgentResultTable from './AgentResultTable.vue'
import { isSafeLabel } from '../utils/agentPresentationFormatters'
import { sanitizePresentation } from '../utils/agentPresentationValidation'

const VIEW_LABELS = {
  TEXT: '摘要',
  TABLE: '表格',
  BAR: '图表',
  LINE: '图表',
  PIE: '图表'
}
const CHART_VIEWS = ['BAR', 'LINE', 'PIE']

export default {
  name: 'AgentPresentationCard',
  components: {
    AgentResultChart,
    AgentResultSummary,
    AgentResultTable
  },
  props: {
    card: { type: Object, required: true },
    partial: { type: Boolean, default: false },
    presentation: { type: Object, default: null },
    queriedAt: { type: String, default: '' },
    warnings: { type: Array, default: () => [] }
  },
  data() {
    return {
      failedViews: [],
      selectedView: null
    }
  },
  computed: {
    /** 在任何子组件渲染前校验关联关系并生成只读安全副本。 */
    safePresentation() {
      return sanitizePresentation(this.presentation, this.card, {
        warnings: this.warnings,
        partial: this.partial
      })
    },
    cardData() {
      return this.card && this.card.data ? this.card.data : {}
    },
    title() {
      const value = this.safePresentation && this.safePresentation.title
      return isSafeLabel(value, 100) ? value : '业务结果'
    },
    decisionSource() {
      const value = this.safePresentation && this.safePresentation.decisionSource
      return isSafeLabel(value, 20) ? value : ''
    },
    warningList() {
      const source = Array.isArray(this.warnings) ? this.warnings : []
      return source
        .filter(value => value !== undefined && value !== null && String(value).trim())
        .map(value => String(value))
        .filter((value, index, values) => values.indexOf(value) === index)
    },
    hasNotice() {
      return this.partial || this.warningList.length > 0
    },
    noticeText() {
      const warningText = this.warningList.join('；')
      if (this.partial && warningText) return `结果可能不完整：${warningText}`
      if (this.partial) return '结果可能不完整，请结合工具告警继续核对。'
      return `展示提示：${warningText}`
    },
    validViews() {
      const descriptor = this.safePresentation
      if (!descriptor || !Array.isArray(descriptor.availableViews)) return []
      return descriptor.availableViews.filter(view => this.failedViews.indexOf(view) < 0)
    },
    currentView() {
      return this.validViews.indexOf(this.selectedView) >= 0 ? this.selectedView : (this.validViews[0] || null)
    },
    hasTabs() {
      return this.validViews.length > 1
    },
    showSummaryAbove() {
      return !!(this.safePresentation && this.safePresentation.summary) &&
        this.currentView !== 'TEXT'
    }
  },
  watch: {
    presentation: {
      deep: true,
      immediate: true,
      handler(value) {
        this.failedViews = []
        this.selectedView = value && value.defaultView ? String(value.defaultView).toUpperCase() : null
      }
    }
  },
  methods: {
    /** 返回展示描述的默认视图，视图无效时由 computed currentView 安全回退。 */
    defaultView() {
      return this.presentation && this.presentation.defaultView
        ? String(this.presentation.defaultView).toUpperCase()
        : null
    },

    /** 将受控视图枚举转换为固定中文页签标题。 */
    viewLabel(view) {
      return VIEW_LABELS[view] || '结果'
    },

    /** 判断视图是否属于固定图表组件。 */
    isChartView(view) {
      return CHART_VIEWS.indexOf(view) >= 0
    },

    /** 响应 Element 页签事件，只允许切换 descriptor 白名单中的视图。 */
    handleTabChange(tab) {
      const view = tab && tab.name ? tab.name : tab
      if (this.validViews.indexOf(view) >= 0) {
        this.selectedView = view
      }
    },

    /** 图表初始化失败时移除对应视图并选择仍可用的安全展示方式。 */
    handleChartError(view) {
      const target = String(view || (this.safePresentation && this.safePresentation.chart && this.safePresentation.chart.type) || '').toUpperCase()
      if (target && this.failedViews.indexOf(target) < 0) {
        this.failedViews.push(target)
      }
      let fallback = null
      for (let index = 0; index < this.validViews.length; index++) {
        if (this.validViews[index] !== target) {
          fallback = this.validViews[index]
          break
        }
      }
      this.selectedView = fallback || null
    }
  }
}
</script>

<style scoped>
.agent-presentation-card {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
}

.presentation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.presentation-title {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.presentation-notice {
  margin-bottom: 10px;
}

.presentation-time {
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
}

.presentation-tabs {
  margin-top: 12px;
}

.presentation-view {
  min-width: 0;
}

.presentation-empty {
  padding: 18px 12px;
  color: #909399;
  text-align: center;
}
</style>
