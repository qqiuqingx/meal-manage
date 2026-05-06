<template>
  <div class="production-sheet-wrapper">
    <!-- 顶部操作栏（打印时隐藏） -->
    <div class="action-bar no-print">
      <div class="action-bar__left">
        <span class="action-bar__title">排餐生产单</span>
      </div>
      <div class="action-bar__right">
        <el-date-picker
          v-model="queryDate"
          type="date"
          placeholder="选择日期"
          value-format="yyyy-MM-dd"
          size="small"
          style="width: 160px; margin-right: 10px;"
          @change="onDateChange"
        />
        <el-select
          v-model="queryMealType"
          placeholder="选择餐次"
          size="small"
          style="width: 120px; margin-right: 10px;"
          @change="onMealTypeChange"
        >
          <el-option label="早餐" value="BREAKFAST" />
          <el-option label="午餐" value="LUNCH" />
          <el-option label="晚餐" value="DINNER" />
        </el-select>
        <el-button size="small" icon="el-icon-search" type="primary" @click="loadByDateAndMeal">查询</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" icon="el-icon-printer" @click="handlePrint">打印预览</el-button>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" v-loading="loading" class="loading-mask" />

    <!-- 无数据 -->
    <div v-if="!loading && !planData" class="empty-state no-print">
      <i class="el-icon-document" />
      <p>暂无数据，请选择日期和餐次查询</p>
    </div>

    <!-- 生产单主体 -->
    <div v-if="planData" class="sheet-root">
      <!-- ░░ 页头 ░░ -->
      <div class="sheet-header">
        <div class="sheet-header__cell">
          <span class="cell-label">日期</span>
          <span class="cell-value">{{ formatDate(planData.mealPlan.recordDate) }}</span>
        </div>
        <div class="sheet-header__cell">
          <span class="cell-label">餐次</span>
          <span class="cell-value">{{ mealTypeText }}</span>
        </div>
        <div class="sheet-header__cell sheet-header__cell--right">
          <span class="cell-label">总人数</span>
          <span class="cell-value cell-value--hero">{{ planData.totalCustomers }}</span>
        </div>
      </div>

      <!-- ░░ 主体：左右两栏 ░░ -->
      <div class="sheet-body">
        <!-- ════ 左栏：编号区 ════ -->
        <div class="sheet-left">
          <div class="section-title">
            <span>编号区</span>
            <span class="section-badge">ITEM CODES</span>
          </div>
          <div class="code-grid">
            <div
              v-for="customer in allCustomers"
              :key="customer.id"
              class="code-cell"
              :class="{
                'code-cell--replaced': customer.hasReplaced,
                'code-cell--first': customer.firstMealOfOrder
              }"
            >
              <div class="code-main">
                <span class="code-text">{{ customer.customerCode || customer.customerName }}</span>
                <span v-if="customer.firstMealOfOrder" class="code-first-badge">首</span>
              </div>
            </div>
          </div>
        </div>

        <!-- ════ 右栏：菜单汇总 + 换菜明细 ════ -->
        <div class="sheet-right">
          <!-- 右上：今日菜单汇总 -->
          <div class="sheet-right__top">
            <table class="dish-table">
              <thead>
                <tr>
                  <th class="col-category">类目</th>
                  <th class="col-name">菜名</th>
                  <th class="col-count">人数</th>
                  <th class="col-codes">编号明细</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(dish, idx) in regularDishes" :key="`reg-${idx}`">
                  <td class="col-category">
                    <span class="dish-type-tag" :class="`dish-type-tag--${dish.dishType.toLowerCase()}`">
                      {{ dishTypeMap[dish.dishType] || dish.dishType }}
                    </span>
                  </td>
                  <td class="col-name">{{ dish.dishName }}</td>
                  <td class="col-count">{{ dish.count }}</td>
                  <td class="col-codes">{{ dish.codeSnippet }}</td>
                </tr>
                <tr v-if="regularDishes.length === 0">
                  <td colspan="4" class="empty-row">暂无常规排餐</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 分割线 -->
          <div class="sheet-divider" />

          <!-- 右下：换菜明细 -->
          <div class="sheet-right__bottom">
            <table class="dish-table">
              <thead>
                <tr>
                  <th class="col-category">换菜</th>
                  <th class="col-name">替换项目</th>
                  <th class="col-count">人数</th>
                  <th class="col-codes">目标编号</th>
                </tr>
              </thead>
              <tbody>
                <template v-if="replacedDishes.length > 0">
                  <tr v-for="(dish, idx) in replacedDishes" :key="`rep-${idx}`">
                    <td v-if="idx === 0" class="col-category" :rowspan="replacedDishes.length">明细</td>
                    <td class="col-name">{{ dish.dishName }}</td>
                    <td class="col-count">{{ dish.count }}</td>
                    <td class="col-codes">{{ dish.codeSnippet }}</td>
                  </tr>
                </template>
                <tr v-else>
                  <td colspan="4" class="empty-row">暂无换菜记录</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- ░░ 页脚统计信息 ░░ -->
      <div class="sheet-footer no-print">
        <div class="footer-stat">
          <span class="footer-stat__label">成功排餐</span>
          <span class="footer-stat__value footer-stat__value--success">{{ planData.successCount }}</span>
        </div>
        <div class="footer-stat">
          <span class="footer-stat__label">失败</span>
          <span class="footer-stat__value footer-stat__value--danger">{{ planData.failCount }}</span>
        </div>
        <div class="footer-stat">
          <span class="footer-stat__label">生成时间</span>
          <span class="footer-stat__value">{{ formatDateTime(planData.mealPlan.generateTime) }}</span>
        </div>
        <div class="footer-stat footer-stat--status">
          <el-tag :type="statusTag[planData.mealPlan.status] || 'info'" size="small">
            {{ statusMap[planData.mealPlan.status] || planData.mealPlan.status }}
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getMealPlanList, getMealPlanFullDetail } from '@/api/mealPlan'
import { MealTypeName } from '@/utils/calendar'

export default {
  name: 'ProductionSheet',
  data() {
    return {
      loading: false,
      planData: null,
      latestLoadRequestId: 0,
      queryDate: null,
      queryMealType: 'LUNCH',
      dishTypeMap: {
        MAIN: '主菜',
        SIDE: '副菜',
        SOUP: '汤品',
        VEGETABLE: '蔬菜',
        RICE: '米饭'
      },
      dishTypeOrder: { SOUP: 1, MAIN: 2, SIDE: 3, VEGETABLE: 4, RICE: 5 },
      statusMap: {
        SUCCESS: '成功',
        FAILED: '部分失败',
        GENERATING: '生成中'
      },
      statusTag: {
        SUCCESS: 'success',
        FAILED: 'danger',
        GENERATING: 'info'
      }
    }
  },
  computed: {
    mealTypeText() {
      const type = (this.planData && this.planData.mealPlan && this.planData.mealPlan.mealType) || this.queryMealType
      return MealTypeName[type] || type
    },
    // 所有客户列表（含是否有换菜标记）
    allCustomers() {
      if (!this.planData) return []
      const decorated = (this.planData.customers || []).map(c => ({
        ...c,
        hasReplaced: (c.items || []).some(i => i.isReplaced)
      }))
      const firstCustomers = decorated.filter(item => item.firstMealOfOrder)
      const normalCustomers = decorated.filter(item => !item.firstMealOfOrder)
      return [...firstCustomers, ...normalCustomers]
    },
    // 常规排餐（非换菜）按菜聚合
    // 编号明细：显示「原本应该吃这道菜、但因换菜被替换掉」的客户编号
    // 使用 originalDishName 做精准匹配，避免替换菜 dishType 与原菜不同导致漏匹配
    regularDishes() {
      if (!this.planData) return []

      // 第一步：按 originalDishName 收集有换菜的客户编号
      // replacedCodesByOriginalName[originalDishName] = Set<customerCode>
      const replacedCodesByOriginalName = {}
      ;(this.planData.customers || []).forEach(customer => {
        const code = customer.customerCode || customer.customerName || ''
        ;(customer.items || []).filter(item => item.isReplaced && item.originalDishName).forEach(item => {
          const origName = item.originalDishName
          if (!replacedCodesByOriginalName[origName]) {
            replacedCodesByOriginalName[origName] = new Set()
          }
          replacedCodesByOriginalName[origName].add(code)
        })
      })

      // 第二步：按标准菜（isReplaced=false）聚合人数
      const groups = {}
      ;(this.planData.customers || []).forEach(customer => {
        const code = customer.customerCode || customer.customerName || ''
        ;(customer.items || []).filter(item => !item.isReplaced).forEach(item => {
          const key = `${item.dishType}__${item.dishName}`
          if (!groups[key]) {
            groups[key] = {
              dishType: item.dishType,
              dishName: item.dishName,
              eatCodes: []
            }
          }
          if (!groups[key].eatCodes.includes(code)) {
            groups[key].eatCodes.push(code)
          }
        })
      })

      return Object.values(groups)
        .sort((a, b) => (this.dishTypeOrder[a.dishType] || 99) - (this.dishTypeOrder[b.dishType] || 99))
        .map(g => {
          // 编号明细 = 原本应吃这道菜但被换掉的客户编号
          const excludedSet = replacedCodesByOriginalName[g.dishName]
          const excludedCodes = excludedSet ? Array.from(excludedSet) : []
          return {
            ...g,
            count: g.eatCodes.length,
            codeSnippet: excludedCodes.length > 0 ? this.buildCodeSnippet(excludedCodes) : '-'
          }
        })
    },
    // 换菜明细（isReplaced = true）按替换菜名聚合
    replacedDishes() {
      if (!this.planData) return []
      const groups = {}
      ;(this.planData.customers || []).forEach(customer => {
        const code = customer.customerCode || customer.customerName || ''
        ;(customer.items || []).filter(item => item.isReplaced).forEach(item => {
          // 用替换后的菜名作为 key（即实际吃的菜）
          const key = item.dishName
          if (!groups[key]) {
            groups[key] = {
              dishName: item.dishName,
              originalDishName: item.originalDishName,
              replaceReason: item.replaceReason,
              codes: []
            }
          }
          if (!groups[key].codes.includes(code)) {
            groups[key].codes.push(code)
          }
        })
      })
      return Object.values(groups).map(g => ({
        ...g,
        count: g.codes.length,
        codeSnippet: this.buildCodeSnippet(g.codes)
      }))
    }
  },
  created() {
    // 从 URL query 参数初始化
    const { mealPlanId, date, mealType } = this.$route.query
    if (date) this.queryDate = date
    if (mealType) this.queryMealType = mealType

    if (mealPlanId) {
      this.loadById(mealPlanId)
    } else if (date && mealType) {
      this.loadByDateAndMeal()
    }
  },
  methods: {
    loadById(id) {
      const requestId = ++this.latestLoadRequestId
      this.loading = true
      getMealPlanFullDetail(id).then(res => {
        if (requestId !== this.latestLoadRequestId) return
        this.planData = res
        // 同步日期/餐次到筛选器
        if (res.mealPlan) {
          this.queryDate = res.mealPlan.recordDate
          this.queryMealType = res.mealPlan.mealType
        }
      }).catch(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.$message.error('加载排餐数据失败')
      }).finally(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.loading = false
      })
    },
    loadByDateAndMeal() {
      if (!this.queryDate || !this.queryMealType) {
        this.$message.warning('请选择日期和餐次')
        return
      }
      const requestId = ++this.latestLoadRequestId
      this.loading = true
      this.planData = null
      getMealPlanList({ recordDate: this.queryDate, mealType: this.queryMealType, page: 0, size: 1 }).then(res => {
        if (requestId !== this.latestLoadRequestId) return null
        const list = res.content || []
        if (list.length === 0) {
          this.$message.warning('未找到该日期和餐次的排餐计划')
          this.planData = null
          return null
        }
        return getMealPlanFullDetail(list[0].id)
      }).then(res => {
        if (requestId !== this.latestLoadRequestId || !res) return
        this.planData = res
      }).catch(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.$message.error('加载排餐数据失败')
      }).finally(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.loading = false
      })
    },
    onDateChange() {},
    onMealTypeChange() {},
    buildCodeSnippet(codes, maxShow = 6) {
      if (!codes || codes.length === 0) return '-'
      if (codes.length <= maxShow) return codes.join(', ')
      return codes.slice(0, maxShow).join(', ') + ` ...+${codes.length - maxShow}`
    },
    formatDate(dateStr) {
      if (!dateStr) return '-'
      // 例：2026-04-01 → 4.1
      const parts = dateStr.split('-')
      if (parts.length >= 3) return `${parseInt(parts[1])}.${parseInt(parts[2])}`
      return dateStr
    },
    formatDateTime(dt) {
      if (!dt) return '-'
      return dt.replace('T', ' ').substring(0, 16)
    },
    handlePrint() {
      window.print()
    }
  }
}
</script>

<style scoped>
/* ──────────────────────────────────────────
   全局字体和基础
────────────────────────────────────────── */
.production-sheet-wrapper {
  min-height: 100vh;
  background: #f8f9fb;
  font-family: 'Manrope', 'Inter', 'PingFang SC', sans-serif;
  padding-bottom: 40px;
}

/* ──────────────────────────────────────────
   顶部操作栏
────────────────────────────────────────── */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 32px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid #e2e8f0;
  position: sticky;
  top: 0;
  z-index: 100;
}
.action-bar__title {
  font-size: 18px;
  font-weight: 800;
  color: #006b5c;
  letter-spacing: -0.5px;
}
.action-bar__right {
  display: flex;
  align-items: center;
}

/* ──────────────────────────────────────────
   空/加载状态
────────────────────────────────────────── */
.loading-mask {
  height: 200px;
}
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #909399;
}
.empty-state i {
  font-size: 52px;
  display: block;
  margin-bottom: 12px;
  color: #c0c4cc;
}

/* ──────────────────────────────────────────
   生产单主体卡片
────────────────────────────────────────── */
.sheet-root {
  max-width: 1400px;
  margin: 28px auto;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 24px -2px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

/* ──────────────────────────────────────────
   页头
────────────────────────────────────────── */
.sheet-header {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  border-bottom: 1px solid #e2e8f0;
}
.sheet-header__cell {
  padding: 20px 28px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-right: 1px solid #e2e8f0;
}
.sheet-header__cell:last-child {
  border-right: none;
  justify-content: flex-end;
}
.sheet-header__cell--right {
  gap: 16px;
}
.cell-label {
  font-size: 11px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #94a3b8;
}
.cell-value {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}
.cell-value--hero {
  font-size: 36px;
  font-weight: 900;
  color: #006b5c;
  letter-spacing: -1px;
  line-height: 1;
}

/* ──────────────────────────────────────────
   主体：左右两栏
────────────────────────────────────────── */
.sheet-body {
  display: flex;
  min-height: 600px;
}

/* ════ 左栏 ════ */
.sheet-left {
  width: 46%;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 18px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.section-title > span:first-child {
  font-size: 10px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #64748b;
}
.section-badge {
  font-size: 9px;
  font-weight: 700;
  background: #fef3c7;
  color: #92400e;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

/* 编号网格 */
.code-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  flex: 1;
}
.code-cell {
  padding: 20px 16px 16px;
  min-height: 80px;
  border-bottom: 1px solid #f1f5f9;
  border-right: 1px solid #f1f5f9;
  position: relative;
  transition: background 0.15s;
}
.code-cell:nth-child(4n) {
  border-right: none;
}
.code-cell:hover {
  background: #f8fffe;
}
.code-text {
  font-size: 13px;
  font-weight: 700;
  color: #475569;
}
.code-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.code-cell--first {
  border-left: 8px solid #16a34a;
  border-color: #86efac;
  background: #f0fdf4;
}
.code-first-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid #86efac;
  background: #dcfce7;
  color: #166534;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}
/* 红框：有换菜的客户 */
.code-cell--replaced .code-text {
  display: inline-block;
  border: 2px solid rgba(186, 26, 26, 0.35);
  background: rgba(186, 26, 26, 0.05);
  color: #ba1a1a;
  padding: 2px 6px;
  border-radius: 4px;
}

/* ════ 右栏 ════ */
.sheet-right {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.sheet-right__top {
  flex: 1;
  border-bottom: 1px solid #e2e8f0;
}
.sheet-divider {
  height: 0;
}
.sheet-right__bottom {
  flex: 1;
}

/* ──────────────────────────────────────────
   菜单表格
────────────────────────────────────────── */
.dish-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  height: 100%;
}
.dish-table thead tr {
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.dish-table th {
  padding: 10px 14px;
  font-size: 10px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #64748b;
  text-align: left;
}
.dish-table th.col-count,
.dish-table td.col-count {
  text-align: center;
  width: 68px;
}
.dish-table th.col-category,
.dish-table td.col-category {
  width: 74px;
  text-align: center;
  border-right: 1px solid #f1f5f9;
}
.dish-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #f8fafc;
  color: #334155;
  vertical-align: middle;
}
.dish-table tr:nth-child(even) td {
  background: rgba(248, 250, 252, 0.4);
}
.dish-table tr:hover td {
  background: #f0fdf9;
}
.col-name {
  font-weight: 600;
  color: #1e293b;
}
.col-count {
  font-weight: 700;
  color: #006b5c;
  font-size: 15px;
}
.col-codes {
  font-size: 11px;
  color: #94a3b8;
  font-style: italic;
  word-break: break-all;
}
.empty-row {
  text-align: center;
  color: #c0c4cc;
  font-style: italic;
  padding: 20px 0;
}

/* 菜品类型标签 */
.dish-type-tag {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
}
.dish-type-tag--soup { background: #e0f2fe; color: #0369a1; }
.dish-type-tag--main { background: #fee2e2; color: #991b1b; }
.dish-type-tag--side { background: #fef3c7; color: #92400e; }
.dish-type-tag--vegetable { background: #dcfce7; color: #166534; }
.dish-type-tag--rice { background: #f3f4f6; color: #374151; }

/* ──────────────────────────────────────────
   页脚
────────────────────────────────────────── */
.sheet-footer {
  display: flex;
  align-items: center;
  gap: 36px;
  padding: 16px 28px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}
.footer-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.footer-stat--status {
  margin-left: auto;
}
.footer-stat__label {
  font-size: 9px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #94a3b8;
}
.footer-stat__value {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}
.footer-stat__value--success { color: #16a34a; }
.footer-stat__value--danger { color: #dc2626; }

/* ──────────────────────────────────────────
   打印样式
────────────────────────────────────────── */
@media print {
  .no-print {
    display: none !important;
  }
  .production-sheet-wrapper {
    background: #fff;
    padding: 0;
  }
  .sheet-root {
    margin: 0;
    border: none;
    box-shadow: none;
    border-radius: 0;
    max-width: 100%;
  }
  .code-cell {
    min-height: 60px;
    padding: 12px 10px;
  }
  .dish-table tr:hover td {
    background: initial;
  }
  .dish-table tr:nth-child(even) td {
    background: rgba(0,0,0,0.02);
  }
  body {
    font-size: 12px;
  }
  .code-cell--first {
    border-left-color: #166534 !important;
    border-color: #166534 !important;
    background: #ffffff !important;
  }
  .code-first-badge {
    border-color: #166534 !important;
    color: #166534 !important;
    background: #ffffff !important;
  }
}
</style>

<!-- 全局打印样式：隐藏 eladmin Layout 框架（导航栏、侧边栏、标签栏等） -->
<style>
@media print {
  /* 顶部导航栏 */
  .navbar,
  .app-header-wrapper,
  .el-header {
    display: none !important;
  }
  /* 标签/面包屑导航栏 */
  .tags-view-container,
  .tagsView-container,
  .tags-view-wrapper {
    display: none !important;
  }
  /* 左侧侧边栏 */
  .sidebar-container,
  .side-bar,
  .el-aside {
    display: none !important;
  }
  /* 底部版权 footer */
  .footer,
  .el-footer,
  .app-footer {
    display: none !important;
  }
  /* 让主内容区占满全宽，移除 margin-left */
  .main-container,
  .app-main,
  .el-main {
    margin-left: 0 !important;
    padding: 0 !important;
    width: 100% !important;
  }
  /* 移除 app-wrapper 可能带的边距 */
  .app-wrapper,
  #app {
    padding: 0 !important;
    margin: 0 !important;
    width: 100% !important;
  }
}
</style>
