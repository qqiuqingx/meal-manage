<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <el-input v-model="query.orderCode" clearable size="small" placeholder="订单编号" style="width: 150px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.customerName" clearable size="small" placeholder="客户姓名" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-select v-model="query.status" clearable size="small" placeholder="订单状态" class="filter-item" style="width: 100px" @change="crud.toQuery">
          <el-option label="进行中" :value="1" />
          <el-option label="已完成" :value="2" />
          <el-option label="已取消" :value="0" />
        </el-select>
        <el-select v-model="query.customerSource" clearable size="small" placeholder="销售渠道" class="filter-item" style="width: 120px" @change="crud.toQuery">
          <el-option v-for="item in customerSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
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
      <el-table-column label="订单编号" prop="orderCode" width="140" />
      <el-table-column label="客户姓名" prop="customerName" width="100" />
      <el-table-column label="手机号" prop="phone" width="120" />
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
      <el-table-column v-if="checkPer(['admin','customerOrder:edit','customerOrder:del'])" label="操作" width="130px" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" icon="edit" @click="crud.toEdit(scope.row)">编辑</el-button>
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
  </div>
</template>

<script>
import * as orderApi from '@/api/customer/order'
import * as dictDetailApi from '@/api/system/dictDetail'
import { createOrderDefaultForm } from '@/components/Order/OrderForm.vue'
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
    return CRUD({ title: '订单', url: '/api/customer/order', idField: 'id', sort: 'id,desc', crudMethod: { ...orderApi }})
  },
  data() {
    return {
      permission: {
        add: ['admin', 'customerOrder:add'],
        edit: ['admin', 'customerOrder:edit'],
        del: ['admin', 'customerOrder:del']
      },
      query: {
        orderCode: '',
        customerName: '',
        status: null,
        customerSource: null
      },
      submitLoading: false,
      customerSourceOptions: [],
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
    async submitForm() {
      const valid = await this.$refs.orderFormRef.validate().catch(() => false)
      if (!valid) return
      try {
        this.submitLoading = true
        if (this.form.id) {
          await orderApi.edit({ ...this.form })
          this.$message.success('编辑成功')
        } else {
          await orderApi.add({ ...this.form })
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
      if (status === 1) return '进行中'
      if (status === 2) return '已完成'
      if (status === 0) return '已取消'
      return '未知'
    },
    statusTagType(status) {
      if (status === 1) return 'success'
      if (status === 2) return 'info'
      if (status === 0) return 'danger'
      return 'info'
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
    formatDate(val) {
      if (!val) return '-'
      return parseTime(val, '{y}-{m}-{d}')
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
