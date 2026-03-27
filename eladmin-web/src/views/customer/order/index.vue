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
    <el-dialog append-to-body :close-on-click-modal="false" :before-close="crud.cancelCU" :visible.sync="crud.status.cu > 0" :title="crud.status.title" width="900px" top="5vh">
      <el-form ref="form" :model="form" :rules="rules" size="small" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select
                v-model="form.customerId"
                filterable
                remote
                placeholder="输入客户姓名或手机号搜索"
                :remote-method="searchCustomer"
                :loading="customerLoading"
                style="width: 100%;"
                @change="onCustomerChange"
              >
                <el-option v-for="item in customers" :key="item.id" :label="item.customerName + ' - ' + item.phone" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%;">
                <el-option label="进行中" :value="1" />
                <el-option label="已完成" :value="2" />
                <el-option label="已取消" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">金额信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="总金额(元)" prop="totalAmount">
              <el-input-number v-model="form.totalAmount" :min="0" :precision="2" controls-position="right" style="width: 100%;" @change="calcBalance" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="成交金额(元)" prop="finalAmount">
              <el-input-number v-model="form.finalAmount" :min="0" :precision="2" controls-position="right" style="width: 100%;" @change="calcBalance" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="定金(元)">
              <el-input-number v-model="form.depositAmount" :min="0" :precision="2" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">餐数信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="早餐合计(份)">
              <el-input-number v-model="form.breakfastCount" :min="0" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="午餐+晚餐(份)">
              <el-input-number v-model="form.lunchDinnerCount" :min="0" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="合计(份)">
              <el-input-number :value="totalCount" :min="0" disabled controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="早餐单价(元)">
              <el-input-number v-model="form.breakfastPrice" :min="0" :precision="2" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="午餐晚餐单价(元)">
              <el-input-number v-model="form.lunchDinnerPrice" :min="0" :precision="2" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">核销信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="核销餐数(合计)">
              <el-input-number v-model="form.verifiedCount" :min="0" controls-position="right" style="width: 100%;" @change="calcRemaining" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="核销金额(元)">
              <el-input-number v-model="form.verifiedAmount" :min="0" :precision="2" controls-position="right" style="width: 100%;" @change="calcBalance" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="剩余餐数">
              <el-input-number v-model="form.remainingCount" :min="0" disabled controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="餐费余额">
              <el-input-number v-model="form.mealBalance" :min="0" :precision="2" disabled controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">日期信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="成交时间">
              <el-date-picker v-model="form.dealTime" type="datetime" placeholder="选择成交时间" style="width: 100%;" value-format="yyyy-MM-dd HH:mm:ss" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="第一次送餐时间">
              <el-date-picker v-model="form.firstDeliveryTime" type="datetime" placeholder="选择送餐时间" style="width: 100%;" value-format="yyyy-MM-dd HH:mm:ss" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="订单开始日期">
              <el-date-picker v-model="form.startDate" type="date" placeholder="选择开始日期" style="width: 100%;" value-format="yyyy-MM-dd" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单结束日期">
              <el-date-picker v-model="form.endDate" type="date" placeholder="选择结束日期" style="width: 100%;" value-format="yyyy-MM-dd" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="text" @click="crud.cancelCU">取消</el-button>
        <el-button :loading="crud.status.cu === 2" type="primary" @click="crud.submitCU">确认</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as orderApi from '@/api/customer/order'
import * as profileApi from '@/api/customer/profile'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'
import { parseTime } from '@/utils/index'

const defaultForm = {
  id: null,
  customerId: null,
  orderCode: null,
  depositAmount: 0,
  totalAmount: null,
  finalAmount: null,
  breakfastCount: 0,
  lunchDinnerCount: 0,
  breakfastPrice: 0,
  lunchDinnerPrice: 0,
  verifiedCount: 0,
  verifiedAmount: 0,
  mealBalance: 0,
  remainingCount: 0,
  dealTime: null,
  firstDeliveryTime: null,
  startDate: null,
  endDate: null,
  status: 1,
  remark: null
}

export default {
  name: 'CustomerOrder',
  components: { crudOperation, rrOperation },
  mixins: [presenter(), header(), form(defaultForm), crud()],
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
        status: null
      },
      customers: [],
      customerLoading: false,
      rules: {
        customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
        totalAmount: [{ required: true, message: '请输入总金额', trigger: 'blur' }],
        finalAmount: [{ required: true, message: '请输入成交金额', trigger: 'blur' }],
        status: [{ required: true, message: '请选择订单状态', trigger: 'change' }]
      }
    }
  },
  computed: {
    totalCount() {
      const breakfast = this.form.breakfastCount || 0
      const lunchDinner = this.form.lunchDinnerCount || 0
      return breakfast + lunchDinner
    }
  },
  methods: {
    [CRUD.HOOK.beforeToCU]() {
      // 编辑时：把当前客户加入下拉列表，避免列表为空
      if (this.form.customerId && this.form.customerName) {
        const exists = this.customers.find(c => c.id === this.form.customerId)
        if (!exists) {
          this.customers = [{ id: this.form.customerId, customerName: this.form.customerName, phone: this.form.phone }]
        }
      }
    },
    [CRUD.HOOK.beforeToAdd]() {
      this.customers = []
      return true
    },
    [CRUD.HOOK.beforeSubmit]() {
      // 计算余额和剩余餐数
      this.calcBalance()
      this.calcRemaining()
      return true
    },
    calcBalance() {
      const finalAmt = this.form.finalAmount || 0
      const verifiedAmt = this.form.verifiedAmount || 0
      this.form.mealBalance = Math.max(0, finalAmt - verifiedAmt)
    },
    calcRemaining() {
      const total = this.totalCount
      const verified = this.form.verifiedCount || 0
      this.form.remainingCount = Math.max(0, total - verified)
    },
    async searchCustomer(query) {
      if (!query) return
      this.customerLoading = true
      try {
        const res = await profileApi.getProfiles({ customerName: query, size: 20 })
        this.customers = res.content || []
      } catch (e) {
        console.error('searchCustomer error', e)
      } finally {
        this.customerLoading = false
      }
    },
    onCustomerChange(customerId) {
      const customer = this.customers.find(c => c.id === customerId)
      if (customer) {
        this.form.customerName = customer.customerName
        this.form.phone = customer.phone
      }
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
