<template>
  <div class="customer-meal-quantity-grid">
    <div class="quantity-grid-toolbar">
      <div class="quantity-grid-toolbar__month">{{ monthLabel }} · 午餐 / 晚餐</div>
      <div class="quantity-grid-legend">
        <span><i class="legend-dot legend-dot--base" />基础计划</span>
        <span><i class="legend-dot legend-dot--manual" />人工覆盖</span>
        <span><i class="legend-dot legend-dot--generated" />已生成</span>
        <span><i class="legend-dot legend-dot--verified" />已核销</span>
        <span><i class="legend-dot legend-dot--excluded" />已排除</span>
        <span><i class="legend-dot legend-dot--paused" />待通知</span>
      </div>
    </div>

    <el-alert
      v-if="pausedOrderCount > 0"
      class="paused-order-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="pausedOrderCount + ' 个暂停订单按来源计划显示为待通知，不能编辑或排餐。'"
    />

    <div v-if="orders.length === 0" class="quantity-grid-empty">
      当前月份没有午晚餐订单
    </div>

    <div v-else class="quantity-grid-table-wrap">
      <el-table
        :data="gridOrders"
        border
        stripe
        row-key="orderId"
        max-height="430"
        class="quantity-grid-table"
      >
        <el-table-column label="订单 / 余额" fixed="left" width="160" align="left">
          <template slot-scope="{ row }">
            <div class="quantity-grid-order">
              <div class="quantity-grid-order__id">订单 {{ row.orderId }}</div>
              <el-tag :type="orderStatusTagType(row.status)" size="mini">
                {{ orderStatusText(row.status, row.mealType) }}
              </el-tag>
              <div class="quantity-grid-order__balance">剩余 {{ row.remainingMealCount || 0 }} / {{ row.mealCount || 0 }} 份</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          v-for="day in monthDays"
          :key="day.date"
          :label="day.label"
          align="center"
          header-align="center"
          class-name="quantity-grid-date-column"
          label-class-name="quantity-grid-date-header"
        >
          <el-table-column :key="day.date + '-lunch'" label="午餐" width="100" align="center">
            <template slot-scope="{ row }">
              <button
                type="button"
                class="quantity-cell"
                :class="cellClass(row, day.date, 'LUNCH')"
                :disabled="!isEditable(row, day.date, 'LUNCH')"
                @click="openEditor(row, day.date, 'LUNCH')"
              >
                <template v-if="getCell(row, day.date, 'LUNCH')">
                  <strong>{{ quantityText(getCell(row, day.date, 'LUNCH')) }}</strong>
                  <small>{{ soupText(getCell(row, day.date, 'LUNCH')) }}</small>
                  <small v-if="getCell(row, day.date, 'LUNCH').generatedCount || getCell(row, day.date, 'LUNCH').failedCount">
                    排 {{ getCell(row, day.date, 'LUNCH').generatedCount || 0 }}<span v-if="getCell(row, day.date, 'LUNCH').failedCount"> / 失败 {{ getCell(row, day.date, 'LUNCH').failedCount }}</span>
                  </small>
                  <small v-if="getCell(row, day.date, 'LUNCH').verifiedCount">核 {{ getCell(row, day.date, 'LUNCH').verifiedCount }}</small>
                  <small v-if="getCell(row, day.date, 'LUNCH').excluded">已排除</small>
                  <small v-else-if="getCell(row, day.date, 'LUNCH').manualOverride">人工覆盖</small>
                </template>
                <span v-else>—</span>
              </button>
            </template>
          </el-table-column>
          <el-table-column :key="day.date + '-dinner'" label="晚餐" width="100" align="center">
            <template slot-scope="{ row }">
              <button
                type="button"
                class="quantity-cell"
                :class="cellClass(row, day.date, 'DINNER')"
                :disabled="!isEditable(row, day.date, 'DINNER')"
                @click="openEditor(row, day.date, 'DINNER')"
              >
                <template v-if="getCell(row, day.date, 'DINNER')">
                  <strong>{{ quantityText(getCell(row, day.date, 'DINNER')) }}</strong>
                  <small>{{ soupText(getCell(row, day.date, 'DINNER')) }}</small>
                  <small v-if="getCell(row, day.date, 'DINNER').generatedCount || getCell(row, day.date, 'DINNER').failedCount">
                    排 {{ getCell(row, day.date, 'DINNER').generatedCount || 0 }}<span v-if="getCell(row, day.date, 'DINNER').failedCount"> / 失败 {{ getCell(row, day.date, 'DINNER').failedCount }}</span>
                  </small>
                  <small v-if="getCell(row, day.date, 'DINNER').verifiedCount">核 {{ getCell(row, day.date, 'DINNER').verifiedCount }}</small>
                  <small v-if="getCell(row, day.date, 'DINNER').excluded">已排除</small>
                  <small v-else-if="getCell(row, day.date, 'DINNER').manualOverride">人工覆盖</small>
                </template>
                <span v-else>—</span>
              </button>
            </template>
          </el-table-column>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="editingCell" class="quantity-cell-editor">
      <div class="quantity-cell-editor__heading">
        编辑 {{ editingCell.date }} · {{ mealTypeText(editingCell.mealType) }} · 订单 {{ editingCell.orderId }}
      </div>
      <el-form :model="editingCell" label-position="top" class="quantity-cell-editor__fields">
        <el-form-item label="计划份数">
          <el-input-number v-model="editingCell.quantity" :min="0" :step="1" size="small" />
        </el-form-item>
        <el-form-item label="汤品设置">
          <el-checkbox v-model="inheritSoup">沿用订单汤品配置</el-checkbox>
          <el-input-number
            v-if="!inheritSoup"
            v-model="editingSoupQuantity"
            :min="0"
            :max="Math.max(Number(editingCell.quantity) || 0, 0)"
            :step="1"
            size="small"
          />
        </el-form-item>
        <div class="quantity-cell-editor__note">
          0 份会排除该客户当天该餐次的所有订单；目标份数不会增加订单购买餐数，已核销份不能调低。
        </div>
      </el-form>
      <div class="quantity-cell-editor__actions">
        <el-button size="mini" type="danger" plain :disabled="Number(editingCell.verifiedCount) > 0" @click="excludeMeal">排除该餐次</el-button>
        <el-button size="mini" @click="restoreDefault">恢复默认</el-button>
        <el-button size="mini" @click="editingCell = null">取消</el-button>
        <el-button size="mini" type="primary" @click="applyEdit">应用修改</el-button>
      </div>
    </div>
  </div>
</template>

<script>
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export default {
  name: 'CustomerMealQuantityGrid',
  props: {
    orders: {
      type: Array,
      default: () => []
    },
    cells: {
      type: Array,
      default: () => []
    },
    statsMonth: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      editingCell: null,
      editingSoupQuantity: 0,
      inheritSoup: true
    }
  },
  computed: {
    monthDays() {
      if (!/^\d{4}-\d{2}$/.test(this.statsMonth)) return []
      const [year, month] = this.statsMonth.split('-').map(Number)
      const daysInMonth = new Date(year, month, 0).getDate()
      return Array.from({ length: daysInMonth }, (_, index) => {
        const day = index + 1
        const date = `${this.statsMonth}-${String(day).padStart(2, '0')}`
        const weekday = WEEKDAYS[new Date(year, month - 1, day).getDay()]
        return { date, label: `${day}日 ${weekday}` }
      })
    },
    monthLabel() {
      const match = this.statsMonth.match(/^(\d{4})-(\d{2})$/)
      return match ? `${match[1]}年${Number(match[2])}月` : this.statsMonth
    },
    cellMap() {
      return this.cells.reduce((map, cell) => {
        map[this.cellKey(cell.orderId, cell.date, cell.mealType)] = cell
        return map
      }, {})
    },
    pausedOrderCount() {
      return this.gridOrders.filter(order => Number(order.status) === 4).length
    },
    gridOrders() {
      return this.orders.filter(order => Number(order.mealCount) > 0)
    }
  },
  methods: {
    cellKey(orderId, date, mealType) {
      return `${orderId}#${date}#${mealType}`
    },
    getCell(order, date, mealType) {
      return this.cellMap[this.cellKey(order.orderId, date, mealType)] || null
    },
    isEditable(order, date, mealType) {
      return Boolean(this.getCell(order, date, mealType)) && Number(order.status) === 1 && Boolean(order.mealType)
    },
    cellClass(order, date, mealType) {
      const cell = this.getCell(order, date, mealType)
      return {
        'quantity-cell--selected': this.editingCell && this.cellKey(this.editingCell.orderId, this.editingCell.date, this.editingCell.mealType) === this.cellKey(order.orderId, date, mealType),
        'quantity-cell--empty': !cell,
        'quantity-cell--base': cell && !cell.excluded && !cell.manualOverride && Number(cell.quantity) > 0,
        'quantity-cell--manual': cell && cell.manualOverride && !cell.excluded,
        'quantity-cell--generated': cell && Number(cell.generatedCount) > 0,
        'quantity-cell--verified': cell && Number(cell.verifiedCount) > 0,
        'quantity-cell--excluded': cell && cell.excluded,
        'quantity-cell--paused': Number(order.status) === 4
      }
    },
    quantityText(cell) {
      if (cell.excluded) return '0 份'
      if (Number(cell.quantity) === 0) return '未排'
      return `${cell.quantity} 份`
    },
    soupText(cell) {
      if (Number(cell.quantity) === 0) return cell.excluded ? '已排除' : '—'
      if (cell.soupQuantity != null) return `含汤 ${cell.soupQuantity}`
      return cell.defaultIncludesSoup ? '随订单含汤' : '默认无汤'
    },
    mealTypeText(mealType) {
      return mealType === 'LUNCH' ? '午餐' : '晚餐'
    },
    orderStatusText(status, mealType) {
      const statusNumber = Number(status)
      if (statusNumber === 4 || !mealType) return '待通知'
      if (statusNumber === 1) return '进行中'
      return '不可排餐'
    },
    orderStatusTagType(status) {
      return Number(status) === 4 ? 'warning' : (Number(status) === 1 ? 'success' : 'info')
    },
    openEditor(order, date, mealType) {
      const cell = this.getCell(order, date, mealType)
      if (!cell || !this.isEditable(order, date, mealType)) return
      this.editingCell = { ...cell }
      this.inheritSoup = cell.soupQuantity == null
      this.editingSoupQuantity = cell.soupQuantity == null ? 0 : Number(cell.soupQuantity)
    },
    applyEdit() {
      if (!this.editingCell) return
      const quantity = Number(this.editingCell.quantity)
      const soupQuantity = this.inheritSoup ? null : Number(this.editingSoupQuantity)
      if (!Number.isInteger(quantity) || quantity < 0) {
        this.$message.warning('计划份数必须是 0 或正整数')
        return
      }
      if (soupQuantity != null && (!Number.isInteger(soupQuantity) || soupQuantity < 0 || soupQuantity > quantity)) {
        this.$message.warning('含汤份数必须在 0 到计划份数之间')
        return
      }
      const nextCell = {
        ...this.editingCell,
        quantity,
        soupQuantity,
        excluded: quantity === 0,
        manualOverride: quantity > 0 && (
          quantity !== Number(this.editingCell.baseQuantity) || soupQuantity != null
        )
      }
      this.$emit('cell-change', nextCell)
      this.editingCell = null
    },
    /**
     * 将当前日期餐次的目标份数设为 0，由页面统一保存为客户排除日期。
     */
    excludeMeal() {
      if (!this.editingCell || Number(this.editingCell.verifiedCount) > 0) return
      this.editingCell.quantity = 0
      this.inheritSoup = true
      this.applyEdit()
    },
    restoreDefault() {
      if (!this.editingCell) return
      const nextCell = {
        ...this.editingCell,
        quantity: Number(this.editingCell.baseQuantity) || 0,
        soupQuantity: null,
        excluded: false,
        manualOverride: false
      }
      this.$emit('cell-change', nextCell)
      this.editingCell = null
    }
  }
}
</script>

<style scoped>
.quantity-grid-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.quantity-grid-toolbar__month {
  color: #344a64;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.quantity-grid-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 14px;
  color: #748399;
  font-size: 12px;
}

.quantity-grid-legend span {
  white-space: nowrap;
}

.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 5px;
  border-radius: 50%;
}

.legend-dot--base { background: #6a9ce0; }
.legend-dot--manual { background: #31a779; }
.legend-dot--generated { background: #5eae88; }
.legend-dot--verified { background: #8e6cc2; }
.legend-dot--excluded { background: #9ba7b6; }
.legend-dot--paused { background: #d79b35; }

.paused-order-alert {
  margin-bottom: 10px;
}

.quantity-grid-empty {
  padding: 36px 0;
  color: #98a4b3;
  text-align: center;
}

.quantity-grid-table-wrap {
  width: 100%;
  overflow-x: auto;
  border-radius: 6px;
}

.quantity-grid-table {
  min-width: 1200px;
}

.quantity-grid-order {
  padding: 5px 3px;
  line-height: 1.7;
}

.quantity-grid-order__id {
  color: #334a64;
  font-weight: 600;
}

.quantity-grid-order__balance {
  color: #7f8ca0;
  font-size: 12px;
}

.quantity-cell {
  display: flex;
  min-height: 90px;
  width: 100%;
  padding: 6px 4px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbe4ef;
  border-radius: 5px;
  background: #fff;
  color: #42566e;
  cursor: pointer;
  line-height: 1.45;
}

.quantity-cell strong {
  color: #2f72ba;
  font-size: 17px;
  font-weight: 700;
}

.quantity-cell small {
  color: #8391a2;
  font-size: 10px;
  white-space: nowrap;
}

.quantity-cell--base { background: #f1f7ff; border-color: #c9def7; }
.quantity-cell--manual { background: #eff9f4; border-color: #8dcab0; }
.quantity-cell--generated { box-shadow: inset 0 0 0 1px #6eb48f; }
.quantity-cell--verified { background: #f5f0fb; border-color: #c2addf; }
.quantity-cell--selected { border-color: #2382d4; box-shadow: 0 0 0 2px rgba(35, 130, 212, .22); }
.quantity-cell--excluded { background: #f0f2f5; color: #8c98a8; border-color: #d9dee6; }
.quantity-cell--excluded strong { color: #8290a0; }
.quantity-cell--paused { background: #fff7e8; border-color: #e5c989; color: #a87722; }
.quantity-cell--empty { min-height: 90px; border-style: dashed; color: #c0c7d1; cursor: not-allowed; }
.quantity-cell:disabled { cursor: not-allowed; }
.quantity-cell:not(:disabled):hover { border-color: #409eff; box-shadow: 0 0 0 1px rgba(64, 158, 255, .14); }

.quantity-cell-editor {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid #d9e6f4;
  border-radius: 8px;
  background: #f9fbfe;
}

.quantity-cell-editor__heading {
  margin-bottom: 10px;
  color: #304966;
  font-size: 14px;
  font-weight: 600;
}

.quantity-cell-editor__fields {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  flex-wrap: wrap;
}

.quantity-cell-editor__fields .el-form-item {
  margin-bottom: 8px;
}

.quantity-cell-editor__note {
  flex: 1 1 100%;
  color: #8492a4;
  font-size: 12px;
}

.quantity-cell-editor__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .quantity-grid-toolbar { flex-direction: column; }
  .quantity-grid-legend { justify-content: flex-start; }
  .quantity-cell-editor__fields { gap: 8px; }
}
</style>
