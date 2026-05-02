<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <el-input v-model="query.orderCode" clearable size="small" placeholder="订单编号" style="width: 150px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.customerCode" clearable size="small" placeholder="客户编号" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.customerName" clearable size="small" placeholder="客户姓名" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-select v-model="query.status" clearable size="small" placeholder="订单状态" class="filter-item" style="width: 100px" @change="crud.toQuery">
          <el-option label="进行中" :value="1" />
          <el-option label="已完成" :value="2" />
          <el-option label="已取消" :value="0" />
          <el-option label="已退餐" :value="3" />
        </el-select>
        <el-select v-model="query.customerSource" clearable size="small" placeholder="销售渠道" class="filter-item" style="width: 120px" @change="crud.toQuery">
          <el-option v-for="item in customerSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-date-picker
          v-model="query.scheduleDate"
          type="date"
          size="small"
          placeholder="排餐日期"
          class="filter-item"
          style="width: 150px"
          value-format="yyyy-MM-dd"
          clearable
          @change="crud.toQuery"
        />
        <rrOperation />
      </div>
      <crudOperation :permission="permission" />
    </div>

    <!--表格渲染-->
    <el-table
      ref="table"
      v-loading="crud.loading"
      :data="crud.data"
      @selection-change="crud.selectionChangeHandler"
    >
      <el-table-column :selectable="checkboxT" type="selection" width="55" />
      <el-table-column label="客户编号" prop="customerCode" width="120" fixed="left" />
      <el-table-column label="客户姓名" prop="customerName" width="100" fixed="left" />
      <el-table-column label="手机号" prop="phone" width="120" fixed="left" />
      <el-table-column label="地址" min-width="200">
        <template slot-scope="scope">
          <div v-if="!scope.row.addresses || scope.row.addresses.length === 0">-</div>
          <el-tag v-for="addr in scope.row.addresses" :key="addr.type" size="mini" style="margin-bottom: 2px; display: block; white-space: normal; height: auto;">
            {{ addr.type }}: {{ addr.detail }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="规格" width="110">
        <template slot-scope="scope">
          {{ packageSpecText(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column label="含汤" width="70" align="center">
        <template slot-scope="scope">
          {{ scope.row.soupCount >= 1 ? '含汤' : '不含汤' }}
        </template>
      </el-table-column>
      <el-table-column label="排餐模式" width="100">
        <template slot-scope="scope">
          {{ scheduleModeText(scope.row.scheduleMode) }}
        </template>
      </el-table-column>
      <el-table-column label="特殊要求" prop="specialRequirements" min-width="140" show-overflow-tooltip>
        <template slot-scope="scope">
          {{ scope.row.specialRequirements || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="过敏" width="150">
        <template slot-scope="scope">
          <span v-if="!scope.row.allergyTags || scope.row.allergyTags.length === 0">-</span>
          <el-tag v-for="tag in scope.row.allergyTags" :key="tag" size="mini" type="warning" style="margin-right: 4px;">
            {{ tag }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="早餐" prop="breakfastCount" width="60" align="center" />
      <el-table-column label="午晚" prop="lunchDinnerCount" width="60" align="center" />
      <el-table-column label="合计" prop="totalCount" width="60" align="center" />
      <el-table-column label="核销" prop="verifiedCount" width="60" align="center" />
      <el-table-column label="剩余" prop="remainingCount" width="60" align="center" />
      <el-table-column label="余额" prop="mealBalance" width="90" align="right">
        <template slot-scope="scope">
          {{ formatMoney(scope.row.mealBalance) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template slot-scope="scope">
          <el-tag :type="statusTagType(scope.row.status)">
            {{ statusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="餐次" width="70" align="center">
        <template slot-scope="scope">
          {{ mealTypeText(scope.row.mealType) }}
        </template>
      </el-table-column>
      <el-table-column label="销售渠道" width="100">
        <template slot-scope="scope">
          {{ getSourceLabel(scope.row.customerSource) }}
        </template>
      </el-table-column>
      <el-table-column label="订单期间" width="180">
        <template slot-scope="{ row }">
          {{ formatDate(row.startDate) }} ~ {{ formatDate(row.endDate) }}
        </template>
      </el-table-column>
      <el-table-column label="成交时间" prop="dealTime" width="150" />
      <el-table-column label="定金" prop="depositAmount" width="90" align="right">
        <template slot-scope="scope">
          {{ formatMoney(scope.row.depositAmount) }}
        </template>
      </el-table-column>
      <el-table-column label="总金额" prop="totalAmount" width="100" align="right">
        <template slot-scope="scope">
          {{ formatMoney(scope.row.totalAmount) }}
        </template>
      </el-table-column>
      <el-table-column label="成交金额" prop="finalAmount" width="100" align="right">
        <template slot-scope="scope">
          {{ formatMoney(scope.row.finalAmount) }}
        </template>
      </el-table-column>
      <el-table-column label="订单编号" prop="orderCode" width="140" />
      <el-table-column v-if="checkPer(['admin','customerOrder:edit','customerOrder:del'])" label="操作" width="180px" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" icon="edit" :disabled="scope.row.status !== 1" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button v-if="scope.row.status === 1" size="mini" type="danger" icon="refresh" @click="openRefundDialog(scope.row)">退餐</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!--分页-->
    <el-pagination
      :current-page="crud.page.current"
      :page-sizes="[10, 20, 50, 100]"
      :page-size="crud.page.size"
      :total="crud.page.total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="crud.sizeChangeHandler"
      @current-change="crud.pageChangeHandler"
    />

    <!--表单组件-->
    <el-dialog
      ref="dialogRef"
      append-to-body
      :close-on-click-modal="false"
      :before-close="handleDialogClose"
      :visible.sync="dialogVisible"
      :title="dialogTitle"
      width="900px"
      top="5vh"
    >
      <el-form ref="form" :model="form" :rules="rules" size="small" label-width="110px">
        <OrderForm
          ref="orderFormRef"
          v-model="form"
          mode="order"
          :readonly="false"
          :customer-disabled="!!form.id"
          :current-customer="{ id: form.customerId, customerName: form.customerName, phone: form.phone }"
          :rules="rules"
          @customer-change="onCustomerChange"
          @calc-change="onCalcChange"
        />

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="text" @click="cancelDialog">取消</el-button>
        <el-button :loading="submitLoading" type="primary" @click="submitForm">确认</el-button>
      </div>
    </el-dialog>

    <!--退餐对话框-->
    <el-dialog title="退餐" :visible.sync="refundDialogVisible" width="500px" append-to-body :close-on-click-modal="false">
      <el-form ref="refundFormRef" :model="refundForm" :rules="refundRules" size="small" label-width="90px">
        <el-form-item label="订单编号">
          <span>{{ refundForm.orderCode }}</span>
        </el-form-item>
        <el-form-item label="客户姓名">
          <span>{{ refundForm.customerName }}</span>
        </el-form-item>
        <el-form-item label="退餐金额">
          <span>¥{{ formatMoney(refundForm.refundAmount) }}</span>
        </el-form-item>
        <el-form-item label="退餐原因" prop="refundReason">
          <el-input v-model="refundForm.refundReason" type="textarea" :rows="3" placeholder="请输入退餐原因" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button :loading="refundLoading" type="primary" @click="confirmRefund">确认退餐</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as orderApi from '@/api/customer/order'
import * as dictDetailApi from '@/api/system/dictDetail'
import { refundMeal } from '@/api/mealRefund'
import { createOrderDefaultForm, normalizeRiceTypeForSubmit } from '@/components/Order/OrderForm.vue'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'
import OrderForm from '@/components/Order/OrderForm.vue'
import { parseTime } from '@/utils/index'

export default {
  name: 'CustomerOrder',
  components: { crudOperation, rrOperation, OrderForm },
  mixins: [presenter(), header(), form(createOrderDefaultForm()), crud()],
  cruds() {
    return CRUD({ title: '订单', url: '/api/customer/order', idField: 'id', sort: 'id,desc', crudMethod: { ...orderApi }, query: { orderCode: '', customerCode: '', customerName: '', status: null, customerSource: null, scheduleDate: null }})
  },
  data() {
    return {
      permission: {
        add: ['admin', 'customerOrder:add'],
        edit: ['admin', 'customerOrder:edit'],
        del: ['admin', 'customerOrder:del']
      },
      submitLoading: false,
      refundLoading: false,
      refundDialogVisible: false,
      refundForm: {
        orderId: null,
        orderCode: '',
        customerName: '',
        refundAmount: 0,
        refundReason: ''
      },
      refundRules: {
        refundReason: [{ required: true, message: '请输入退餐原因', trigger: 'blur' }]
      },
      customerSourceOptions: [],
      editRequestId: 0,
      rules: {
        customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
        totalAmount: [{ required: true, message: '请输入总金额', trigger: 'blur' }],
        finalAmount: [{ required: true, message: '请输入成交金额', trigger: 'blur' }],
        status: [{ required: true, message: '请选择订单状态', trigger: 'change' }]
      }
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.crud.status.cu > 0
      },
      set(val) {
        if (!val) {
          this.crud.cancelCU()
        }
      }
    },
    dialogTitle() {
      return this.crud.status.add === CRUD.STATUS.PREPARED ? '新增订单' : '编辑订单'
    }
  },
  created() {
    this.loadCustomerSourceDict()
  },
  methods: {
    loadCustomerSourceDict() {
      dictDetailApi.get('customer_source').then(res => {
        this.customerSourceOptions = (res.content || res.data || res || []).map(item => ({
          value: item.value,
          label: item.label
        }))
      }).catch(() => {})
    },
    getSourceLabel(value) {
      if (!value) return '-'
      const item = this.customerSourceOptions.find(o => o.value === value)
      return item ? item.label : value
    },
    [CRUD.HOOK.beforeToCU]() {
      const currentForm = { ...this.form }
      Object.assign(this.form, createOrderDefaultForm(), currentForm)
      return true
    },
    [CRUD.HOOK.beforeToAdd]() {
      // 重置表单
      Object.assign(this.form, createOrderDefaultForm())
      return true
    },
    [CRUD.HOOK.beforeSubmit]() {
      // 计算余额和剩余餐数
      this.onCalcChange()
      return true
    },
    handleDialogClose(done) {
      this.cancelDialog()
      done && done()
    },
    cancelDialog() {
      this.crud.cancelCU()
    },
    async handleEdit(row) {
      const requestId = this.editRequestId + 1
      this.editRequestId = requestId
      try {
        const res = await orderApi.getOrder(row.id)
        if (requestId !== this.editRequestId) {
          return
        }
        const detail = res.data || res
        this.crud.toEdit(detail)
      } catch (e) {
        if (requestId !== this.editRequestId) {
          return
        }
        this.$message.error('获取订单详情失败: ' + (e.message || '未知错误'))
      }
    },
    async submitForm() {
      const valid = await this.$refs.orderFormRef.validate().catch(() => false)
      if (!valid) return
      const payload = {
        ...this.form
        // deliveryDates 已在 OrderForm 中处理好，不需要再次序列化
      }
      payload.riceType = normalizeRiceTypeForSubmit(payload.riceType)

      // 先校验订单冲突（提交前校验）
      try {
        await orderApi.validateOrder(payload)
      } catch (e) {
        this.$message.warning(e.message || '订单校验失败')
        return // 校验失败则阻止提交
      }

      try {
        this.submitLoading = true
        if (this.form.id) {
          await orderApi.edit(payload)
          this.$message.success('编辑成功')
        } else {
          await orderApi.add(payload)
          this.$message.success('新增成功')
        }
        this.crud.cancelCU()
        this.crud.refresh()
      } catch (e) {
        console.error('submit error', e)
        this.$message.error((e.message || '') || '操作失败')
      } finally {
        this.submitLoading = false
      }
    },
    onCustomerChange(customerId, customer) {
      // 客户变更事件处理（如有需要可扩展）
    },
    onCalcChange() {
      // 余额计算
      const finalAmt = this.form.finalAmount || 0
      const verifiedAmt = this.form.verifiedAmount || 0
      this.form.mealBalance = Math.max(0, finalAmt - verifiedAmt)
      // 剩余餐数计算
      const breakfast = this.form.breakfastCount || 0
      const lunchDinner = this.form.lunchDinnerCount || 0
      const total = breakfast + lunchDinner
      const verified = this.form.verifiedCount || 0
      this.form.remainingCount = Math.max(0, total - verified)
    },
    statusText(status) {
      if (status === 0) return '已取消'
      if (status === 1) return '进行中'
      if (status === 2) return '已完成'
      if (status === 3) return '已退餐'
      return '未知'
    },
    statusTagType(status) {
      if (status === 0) return 'danger'
      if (status === 1) return 'success'
      if (status === 2) return 'info'
      if (status === 3) return 'warning'
      return 'info'
    },
    mealTypeText(mealType) {
      if (!mealType || mealType === 'ALL') return '-'
      const map = { LUNCH: '午餐', DINNER: '晚餐', LUNCH_DINNER: '午+晚' }
      return map[mealType] || mealType
    },
    scheduleModeText(scheduleMode) {
      const map = {
        SCHEDULE: '指定日期',
        DAILY: '每天送',
        WEEKEND: '周末送',
        WEEKDAY: '工作日'
      }
      return map[scheduleMode] || '-'
    },
    checkboxT() {
      return true
    },
    formatMoney(val) {
      const amount = Number(val)
      if (Number.isNaN(amount)) {
        return '0.00'
      }
      return amount.toFixed(2)
    },
    packageSpecText(row) {
      const meat = (row.mainDishCount || 0) + (row.sideDishCount || 0)
      const veg = row.vegCount || 0
      const soup = row.soupCount || 0
      return soup > 0 ? `${meat}荤${veg}素-${soup}汤` : `${meat}荤${veg}素`
    },
    formatDate(val) {
      if (!val) return '-'
      return parseTime(val, '{y}-{m}-{d}')
    },
    openRefundDialog(row) {
      this.refundForm = {
        orderId: row.id,
        orderCode: row.orderCode,
        customerName: row.customerName,
        refundAmount: row.mealBalance || 0,
        refundReason: ''
      }
      this.refundDialogVisible = true
      this.$nextTick(() => {
        this.$refs.refundFormRef && this.$refs.refundFormRef.clearValidate()
      })
    },
    confirmRefund() {
      this.$refs.refundFormRef.validate(async valid => {
        if (!valid) return
        this.refundLoading = true
        try {
          await refundMeal({
            orderId: this.refundForm.orderId,
            refundReason: this.refundForm.refundReason
          })
          this.$message.success('退餐成功')
          this.refundDialogVisible = false
          this.crud.refresh()
        } catch (e) {
          this.$message.error(e.message || '退餐失败')
        } finally {
          this.refundLoading = false
        }
      })
    }
  }
}
</script>

<style scoped>
.head-container {
  padding: 10px;
  margin-bottom: 10px;
}
.head-container .filter-item {
  margin-right: 10px;
}
</style>
