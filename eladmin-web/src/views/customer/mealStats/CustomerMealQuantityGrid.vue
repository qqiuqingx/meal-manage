<template>
  <div class="customer-meal-quantity-grid">
    <div class="quantity-grid-toolbar">
      <div class="quantity-grid-toolbar__month">{{ monthLabel }} · 午餐 / 晚餐</div>
      <div class="quantity-grid-legend">
        <span><i class="legend-dot legend-dot--base" />基础计划</span>
        <span><i class="legend-dot legend-dot--manual" />订单覆盖</span>
        <span><i class="legend-dot legend-dot--generated" />已生成</span>
        <span><i class="legend-dot legend-dot--verified" />已核销</span>
        <span><i class="legend-dot legend-dot--global-stop" />客户统一停餐</span>
        <span><i class="legend-dot legend-dot--order-stop" />订单停餐</span>
      </div>
    </div>

    <el-alert
      v-if="order && !editable"
      class="read-only-order-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="order.readOnlyReason || '当前订单只能查看。'"
    />
    <div v-if="!order || Number(order.lunchDinnerCount) <= 0" class="quantity-grid-empty">
      当前订单没有午晚餐购买数
    </div>
    <div v-else class="quantity-grid-table-wrap">
      <el-table
        :data="[order]"
        border
        stripe
        row-key="orderId"
        max-height="430"
        class="quantity-grid-table"
      >
        <el-table-column label="当前订单 / 餐池" fixed="left" width="190" align="left">
          <template slot-scope="{ row }">
            <div class="quantity-grid-order">
              <div class="quantity-grid-order__id">{{ row.orderCode || row.orderId }}</div>
              <el-tag :type="orderStatusTagType(row.status)" size="mini">{{ row.statusLabel || orderStatusText(row.status) }}</el-tag>
              <div class="quantity-grid-order__balance">
                本月计划上限 {{ row.availableLunchDinnerCount || 0 }} / 购买 {{ row.lunchDinnerCount || 0 }} 份
              </div>
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
          <el-table-column :key="day.date + '-lunch'" label="午餐" width="104" align="center">
            <template slot-scope="{}">
              <button
                type="button"
                class="quantity-cell"
                :class="cellClass(day.date, 'LUNCH')"
                :disabled="!isEditable(day.date, 'LUNCH')"
                @click="openEditor(day.date, 'LUNCH')"
              >
                <template v-if="getCell(day.date, 'LUNCH')">
                  <strong>{{ quantityText(getCell(day.date, 'LUNCH')) }}</strong>
                  <small>{{ soupText(getCell(day.date, 'LUNCH')) }}</small>
                  <small v-if="getCell(day.date, 'LUNCH').generatedCount || getCell(day.date, 'LUNCH').failedCount">
                    排 {{ getCell(day.date, 'LUNCH').generatedCount || 0 }}<span v-if="getCell(day.date, 'LUNCH').failedCount"> / 失败 {{ getCell(day.date, 'LUNCH').failedCount }}</span>
                  </small>
                  <small v-if="getCell(day.date, 'LUNCH').verifiedCount">核 {{ getCell(day.date, 'LUNCH').verifiedCount }}</small>
                  <small v-if="stopReason(getCell(day.date, 'LUNCH'))">{{ stopReason(getCell(day.date, 'LUNCH')) }}</small>
                  <small v-else-if="getCell(day.date, 'LUNCH').manualOverride">订单覆盖</small>
                </template>
                <span v-else>—</span>
              </button>
            </template>
          </el-table-column>
          <el-table-column :key="day.date + '-dinner'" label="晚餐" width="104" align="center">
            <template slot-scope="{}">
              <button
                type="button"
                class="quantity-cell"
                :class="cellClass(day.date, 'DINNER')"
                :disabled="!isEditable(day.date, 'DINNER')"
                @click="openEditor(day.date, 'DINNER')"
              >
                <template v-if="getCell(day.date, 'DINNER')">
                  <strong>{{ quantityText(getCell(day.date, 'DINNER')) }}</strong>
                  <small>{{ soupText(getCell(day.date, 'DINNER')) }}</small>
                  <small v-if="getCell(day.date, 'DINNER').generatedCount || getCell(day.date, 'DINNER').failedCount">
                    排 {{ getCell(day.date, 'DINNER').generatedCount || 0 }}<span v-if="getCell(day.date, 'DINNER').failedCount"> / 失败 {{ getCell(day.date, 'DINNER').failedCount }}</span>
                  </small>
                  <small v-if="getCell(day.date, 'DINNER').verifiedCount">核 {{ getCell(day.date, 'DINNER').verifiedCount }}</small>
                  <small v-if="stopReason(getCell(day.date, 'DINNER'))">{{ stopReason(getCell(day.date, 'DINNER')) }}</small>
                  <small v-else-if="getCell(day.date, 'DINNER').manualOverride">订单覆盖</small>
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
        编辑 {{ editingCell.date }} · {{ mealTypeText(editingCell.mealType) }} · {{ order.orderCode || order.orderId }}
      </div>
      <el-form :model="editingCell" label-position="top" class="quantity-cell-editor__fields">
        <el-form-item label="计划份数">
          <el-input-number v-model="editingCell.quantity" :min="Number(editingCell.verifiedCount) || 0" :step="1" size="small" />
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
          0 份只停用当前订单该日期餐次，释放出的餐数按排餐顺序分给后续日期。已核销份不能删除。
        </div>
      </el-form>
      <div class="quantity-cell-editor__actions">
        <el-button size="mini" type="danger" plain :disabled="Number(editingCell.verifiedCount) > 0" @click="setOrderStop">订单停餐</el-button>
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
    order: { type: Object, default: null },
    cells: { type: Array, default: () => [] },
    overrides: { type: Array, default: () => [] },
    statsMonth: { type: String, default: '' },
    editable: { type: Boolean, default: false }
  },
  data() {
    return { editingCell: null, editingSoupQuantity: 0, inheritSoup: true }
  },
  computed: {
    monthDays() {
      if (!/^\d{4}-\d{2}$/.test(this.statsMonth)) return []
      const [year, month] = this.statsMonth.split('-').map(Number)
      const dayCount = new Date(year, month, 0).getDate()
      return Array.from({ length: dayCount }, (_, index) => {
        const day = index + 1
        const date = `${this.statsMonth}-${String(day).padStart(2, '0')}`
        return { date, label: `${day}日 ${WEEKDAYS[new Date(year, month - 1, day).getDay()]}` }
      })
    },
    monthLabel() {
      const match = this.statsMonth.match(/^(\d{4})-(\d{2})$/)
      return match ? `${match[1]}年${Number(match[2])}月` : this.statsMonth
    },
    cellMap() {
      return this.cells.reduce((map, cell) => {
        map[this.cellKey(cell.date, cell.mealType)] = cell
        return map
      }, {})
    }
  },
  methods: {
    /** 根据日期和餐次取得当前订单日历格。 */
    cellKey(date, mealType) {
      return `${date}#${mealType}`
    },
    /** 从已加载的当前订单日历查找单元格。 */
    getCell(date, mealType) {
      return this.cellMap[this.cellKey(date, mealType)] || null
    },
    /** 叠加订单状态、权限和客户统一停餐判断当前格是否可编辑。 */
    isEditable(date, mealType) {
      const cell = this.getCell(date, mealType)
      return Boolean(cell && this.editable && !cell.customerExcluded)
    },
    /** 为基础计划、订单覆盖及两种停餐来源设置格子样式。 */
    cellClass(date, mealType) {
      const cell = this.getCell(date, mealType)
      return {
        'quantity-cell--selected': Boolean(this.editingCell && this.cellKey(this.editingCell.date, this.editingCell.mealType) === this.cellKey(date, mealType)),
        'quantity-cell--empty': !cell,
        'quantity-cell--base': Boolean(cell && !cell.manualOverride && Number(cell.quantity) > 0),
        'quantity-cell--manual': Boolean(cell && cell.manualOverride && !cell.customerExcluded),
        'quantity-cell--generated': Boolean(cell && Number(cell.generatedCount) > 0),
        'quantity-cell--verified': Boolean(cell && Number(cell.verifiedCount) > 0),
        'quantity-cell--global-stop': Boolean(cell && cell.customerExcluded),
        'quantity-cell--order-stop': Boolean(cell && cell.orderExcluded)
      }
    },
    /** 显示计划份数或客户/订单停餐来源。 */
    quantityText(cell) {
      if (cell.customerExcluded) return '统一停餐'
      if (cell.orderExcluded) return '订单停餐'
      if (Number(cell.quantity) === 0) return '未排'
      return `${cell.quantity} 份`
    },
    /** 显示显式或继承的含汤数量。 */
    soupText(cell) {
      if (Number(cell.quantity) === 0) return '—'
      if (cell.soupQuantity != null) return `含汤 ${cell.soupQuantity}`
      return cell.defaultIncludesSoup ? '随订单含汤' : '默认无汤'
    },
    /** 返回单元格的停餐来源说明。 */
    stopReason(cell) {
      if (!cell) return ''
      if (cell.customerExcluded) return '客户统一停餐'
      if (cell.orderExcluded) return '当前订单停餐'
      return ''
    },
    /** 将餐次代码转换为编辑标题。 */
    mealTypeText(mealType) {
      return mealType === 'LUNCH' ? '午餐' : '晚餐'
    },
    /** 根据订单状态选择状态标签颜色。 */
    orderStatusTagType(status) {
      return Number(status) === 4 ? 'warning' : (Number(status) === 1 ? 'success' : 'info')
    },
    /** 将订单状态映射为日历摘要文案。 */
    orderStatusText(status) {
      return Number(status) === 4 ? '暂停' : (Number(status) === 1 ? '进行中' : '不可排餐')
    },
    /** 将可编辑的单元格复制到右侧编辑器。 */
    openEditor(date, mealType) {
      const cell = this.getCell(date, mealType)
      if (!cell || !this.isEditable(date, mealType)) return
      this.editingCell = { ...cell }
      this.inheritSoup = cell.soupQuantity == null
      this.editingSoupQuantity = cell.soupQuantity == null ? 0 : Number(cell.soupQuantity)
    },
    /** 校验目标份数和汤数后发出当前订单的覆盖更新。 */
    applyEdit() {
      if (!this.editingCell) return
      const quantity = Number(this.editingCell.quantity)
      const soupQuantity = quantity === 0 || this.inheritSoup ? null : Number(this.editingSoupQuantity)
      if (!Number.isInteger(quantity) || quantity < (Number(this.editingCell.verifiedCount) || 0)) {
        this.$message.warning('计划份数不能小于已核销份数')
        return
      }
      if (soupQuantity != null && (!Number.isInteger(soupQuantity) || soupQuantity < 0 || soupQuantity > quantity)) {
        this.$message.warning('含汤份数必须在 0 到计划份数之间')
        return
      }
      this.$emit('override-upsert', {
        date: this.editingCell.date,
        mealType: this.editingCell.mealType,
        quantity,
        soupQuantity,
        remark: this.findRemark(this.editingCell.date, this.editingCell.mealType)
      })
      this.editingCell = null
    },
    /** 查找并返回当前单元格已保存覆盖的备注。 */
    findRemark(date, mealType) {
      const override = this.overrides.find(item => item.date === date && item.mealType === mealType)
      return override ? override.remark || '' : ''
    },
    /** 将当前订单该格设置为零份停餐。 */
    setOrderStop() {
      if (!this.editingCell || Number(this.editingCell.verifiedCount) > 0) return
      this.editingCell.quantity = 0
      this.inheritSoup = true
      this.applyEdit()
    },
    /** 移除当前格覆盖，让后端按默认规则重新计算。 */
    restoreDefault() {
      if (!this.editingCell || !this.isEditable(this.editingCell.date, this.editingCell.mealType)) return
      const cell = { ...this.editingCell }
      this.$emit('override-remove', cell)
      this.editingCell = null
    }
  }
}
</script>

<style scoped>
.quantity-grid-toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.quantity-grid-toolbar__month { color: #344a64; font-size: 15px; font-weight: 600; white-space: nowrap; }
.quantity-grid-legend { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px 14px; color: #748399; font-size: 12px; }
.quantity-grid-legend span { white-space: nowrap; }
.legend-dot { display: inline-block; width: 8px; height: 8px; margin-right: 5px; border-radius: 50%; }
.legend-dot--base { background: #6a9ce0; }
.legend-dot--manual { background: #31a779; }
.legend-dot--generated { background: #5eae88; }
.legend-dot--verified { background: #8e6cc2; }
.legend-dot--global-stop { background: #d56b6b; }
.legend-dot--order-stop { background: #9ba7b6; }
.read-only-order-alert { margin-bottom: 10px; }
.quantity-grid-empty { padding: 36px 0; color: #98a4b3; text-align: center; }
.quantity-grid-table-wrap { width: 100%; overflow-x: auto; border-radius: 6px; }
.quantity-grid-table { min-width: 1200px; }
.quantity-grid-order { padding: 5px 3px; line-height: 1.7; }
.quantity-grid-order__id { color: #334a64; font-weight: 600; }
.quantity-grid-order__balance { color: #7f8ca0; font-size: 12px; }
.quantity-cell { display: flex; min-height: 90px; width: 100%; padding: 6px 4px; flex-direction: column; align-items: center; justify-content: center; border: 1px solid #dbe4ef; border-radius: 5px; background: #fff; color: #42566e; cursor: pointer; line-height: 1.45; }
.quantity-cell strong { color: #2f72ba; font-size: 15px; font-weight: 700; }
.quantity-cell small { color: #8391a2; font-size: 10px; white-space: nowrap; }
.quantity-cell--base { background: #f1f7ff; border-color: #c9def7; }
.quantity-cell--manual { background: #eff9f4; border-color: #8dcab0; }
.quantity-cell--generated { box-shadow: inset 0 0 0 1px #6eb48f; }
.quantity-cell--verified { background: #f5f0fb; border-color: #c2addf; }
.quantity-cell--selected { border-color: #2382d4; box-shadow: 0 0 0 2px rgba(35, 130, 212, .22); }
.quantity-cell--global-stop { background: #fff5f5; border-color: #e6a0a0; }
.quantity-cell--order-stop { background: #f0f2f5; border-color: #d9dee6; }
.quantity-cell--empty { min-height: 90px; border-style: dashed; color: #c0c7d1; cursor: not-allowed; }
.quantity-cell:disabled { cursor: not-allowed; }
.quantity-cell:not(:disabled):hover { border-color: #409eff; box-shadow: 0 0 0 1px rgba(64, 158, 255, .14); }
.quantity-cell-editor { margin-top: 14px; padding: 12px 14px; border: 1px solid #d9e6f4; border-radius: 8px; background: #f9fbfe; }
.quantity-cell-editor__heading { margin-bottom: 10px; color: #304966; font-size: 14px; font-weight: 600; }
.quantity-cell-editor__fields { display: flex; align-items: flex-start; gap: 24px; flex-wrap: wrap; }
.quantity-cell-editor__fields .el-form-item { margin-bottom: 8px; }
.quantity-cell-editor__note { flex: 1 1 100%; color: #8492a4; font-size: 12px; }
.quantity-cell-editor__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
@media (max-width: 768px) {
  .quantity-grid-toolbar { flex-direction: column; }
  .quantity-grid-legend { justify-content: flex-start; }
  .quantity-cell-editor__fields { gap: 8px; }
}
</style>
