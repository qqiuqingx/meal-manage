<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <el-input v-model="query.customerCode" clearable size="small" placeholder="客户编号" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.customerName" clearable size="small" placeholder="客户姓名" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.phone" clearable size="small" placeholder="手机号" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <rrOperation />
      </div>
      <crudOperation :permission="permission">
        <template slot="right">
          <el-button
            v-if="checkPer(['admin','mealPlan:generate'])"
            class="filter-item"
            size="mini"
            type="warning"
            icon="el-icon-finished"
            @click="mealPlanDialogVisible = true; mealPlanResult = null"
          >
            生成排餐计划
          </el-button>
        </template>
      </crudOperation>
    </div>

    <!--表格渲染-->
    <el-table
      ref="table"
      v-loading="crud.loading"
      :data="crud.data"
      @selection-change="crud.selectionChangeHandler"
    >
      <el-table-column :selectable="checkboxT" type="selection" width="55" />
      <el-table-column label="客户编号" prop="customerCode" width="100" />
      <el-table-column label="姓名" prop="customerName" width="100" />
      <el-table-column label="手机号" prop="phone" width="120" />
      <el-table-column label="地址" prop="defaultAddress" min-width="150" />
      <el-table-column label="孕周" prop="gestationalWeek" width="60" align="center" />
      <el-table-column label="早餐数" prop="breakfastCount" width="80" align="center" />
      <el-table-column label="午晚数" prop="lunchDinnerCount" width="80" align="center" />
      <el-table-column label="创建时间" prop="createTime" width="150" />
      <el-table-column v-if="checkPer(['admin','customerProfile:edit'])" label="操作" width="180px" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" icon="edit" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button
            size="mini"
            type="success"
            icon="view"
            @click="handleDetail(scope.row)"
          >
            详情
          </el-button>
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
      append-to-body
      :close-on-click-modal="false"
      :visible.sync="dialogVisible"
      :title="dialogTitle"
      width="800px"
      top="5vh"
    >
      <el-form ref="form" :model="form" :rules="rules" size="small" label-width="100px">
        <!-- 客户基本信息 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户姓名" prop="customerName">
              <el-input v-model="form.customerName" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="孕周">
              <el-input-number v-model="form.gestationalWeek" :min="1" :max="50" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="过敏食物">
              <el-select
                v-model="form.allergyTags"
                multiple
                filterable
                remote
                allow-create
                default-first-option
                :remote-method="searchAllergy"
                :loading="allergyLoading"
                placeholder="输入配料名称搜索"
                style="width: 100%;"
              >
                <el-option v-for="item in allergyOptions" :key="item.id" :label="item.name" :value="item.name" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="医嘱要求">
              <el-input v-model="form.medicalRequirements" type="textarea" :rows="2" placeholder="请输入医嘱要求" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">地址信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="默认地址">
              <el-input v-model="form.addresses[0].addressDetail" placeholder="默认地址" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="工作日地址">
              <el-input v-model="form.addresses[1].addressDetail" placeholder="工作日地址(可选)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="周末地址">
              <el-input v-model="form.addresses[2].addressDetail" placeholder="周末地址(可选)" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 首单信息（仅新增时显示） -->
        <template v-if="isCreateMode()">
          <el-divider content-position="left">首单信息</el-divider>
          <OrderForm
            ref="orderFormRef"
            v-model="form.orderInfo"
            mode="firstOrder"
            :readonly="false"
          />
        </template>

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

    <!-- 客户详情弹窗 -->
    <CustomerDetailDialog
      :visible.sync="detailDialogVisible"
      :customer="currentCustomer"
      @edit-customer="handleEdit"
      @view-all-orders="handleViewAllOrders"
    />

    <!-- 排餐生成弹窗 -->
    <el-dialog
      append-to-body
      :close-on-click-modal="false"
      :visible.sync="mealPlanDialogVisible"
      title="生成排餐计划"
      width="500px"
    >
      <el-form ref="mealPlanForm" :model="mealPlanForm" :rules="mealPlanRules" size="small" label-width="100px">
        <el-form-item label="排餐日期" prop="recordDate">
          <el-date-picker
            v-model="mealPlanForm.recordDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="餐次" prop="mealType">
          <el-select v-model="mealPlanForm.mealType" placeholder="请选择" style="width: 100%">
            <el-option label="测试/所有" value="ALL" />
            <el-option label="早餐" value="BREAKFAST" />
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
            <el-option label="午晚" value="LUNCH_DINNER" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="mealPlanResult" style="margin-top: 15px; padding: 10px; background-color: #f4f4f5; border-radius: 4px;">
        <h4 style="margin-top: 0; margin-bottom: 10px;">生成结果：</h4>
        <p style="margin: 5px 0;">总数：{{ mealPlanResult.totalCount || 0 }}</p>
        <p style="margin: 5px 0;">成功：<span style="color: #67C23A">{{ mealPlanResult.successCount || 0 }}</span></p>
        <p style="margin: 5px 0;">失败：<span style="color: #F56C6C">{{ mealPlanResult.failCount || 0 }}</span></p>
        <div v-if="mealPlanResult.failDetails && mealPlanResult.failDetails.length > 0">
          <p style="margin: 5px 0;">失败明细：</p>
          <ul style="margin: 5px 0; padding-left: 20px;">
            <li v-for="(detail, index) in mealPlanResult.failDetails" :key="index" style="color: #F56C6C">
              {{ detail.failReason || detail }}
            </li>
          </ul>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button type="text" @click="mealPlanDialogVisible = false">关闭</el-button>
        <el-button :loading="mealPlanLoading" type="primary" @click="doGenerateMealPlan">生成</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as profileApi from '@/api/customer/profile'
import { generateMealPlan } from '@/api/mealPlan'
import { queryIngredients } from '@/api/dishIngredient'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'
import OrderForm, { createFirstOrderDefaultForm } from '@/components/Order/OrderForm.vue'
import CustomerDetailDialog from './CustomerDetailDialog.vue'

function createDefaultAddresses() {
  return [
    { addressType: 'DEFAULT', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
  ]
}

const defaultForm = {
  id: null,
  customerName: null,
  phone: null,
  gestationalWeek: null,
  allergyTags: [],
  medicalRequirements: null,
  remark: null,
  addresses: createDefaultAddresses(),
  orderInfo: createFirstOrderDefaultForm()
}

export default {
  name: 'CustomerProfile',
  components: { crudOperation, rrOperation, OrderForm, CustomerDetailDialog },
  mixins: [presenter(), header(), form(defaultForm), crud()],
  cruds() {
    return CRUD({
      title: '客户档案',
      url: '/api/customerProfile',
      idField: 'id',
      sort: 'id,desc',
      crudMethod: { ...profileApi }
    })
  },
  data() {
    return {
      permission: {
        add: ['admin', 'customerProfile:add'],
        edit: ['admin', 'customerProfile:edit'],
        del: ['admin', 'customerProfile:del']
      },
      query: {
        customerCode: '',
        customerName: '',
        phone: ''
      },
      rules: {
        customerName: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }],
        phone: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
        ],
        'orderInfo.parentPackageId': [{ required: true, message: '请选择父套餐', trigger: 'change' }],
        'orderInfo.childPackageId': [{ required: true, message: '请选择子套餐', trigger: 'change' }],
        'orderInfo.startDate': [{ required: true, message: '请选择开始日期', trigger: 'change' }],
        'orderInfo.endDate': [{ required: true, message: '请选择结束日期', trigger: 'change' }]
      },
      submitLoading: false,
      allergyOptions: [],
      allergyLoading: false,
      editRequestId: 0,
      detailDialogVisible: false,
      currentCustomer: null,
      mealPlanDialogVisible: false,
      mealPlanLoading: false,
      mealPlanForm: {
        recordDate: null,
        mealType: null
      },
      mealPlanRules: {
        recordDate: [{ required: true, message: '请选择排餐日期', trigger: 'change' }],
        mealType: [{ required: true, message: '请选择餐次', trigger: 'change' }]
      },
      mealPlanResult: null
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
      return this.isCreateMode() ? '新增客户' : '编辑客户'
    }
  },
  methods: {
    serializeDeliveryDates(value) {
      if (Array.isArray(value)) {
        const dates = value.map(item => String(item || '').trim()).filter(Boolean)
        return dates.length ? JSON.stringify(dates) : null
      }
      if (typeof value === 'string') {
        const trimmed = value.trim()
        return trimmed || null
      }
      return null
    },
    isCreateMode() {
      return this.crud.status.add === CRUD.STATUS.PREPARED
    },
    createAddressesFromForm(formData = {}) {
      const defaults = createDefaultAddresses()
      if (Array.isArray(formData.addresses)) {
        const addressMap = formData.addresses.reduce((result, address) => {
          if (address && address.addressType) {
            result[address.addressType] = {
              addressType: address.addressType,
              addressDetail: address.addressDetail || '',
              contactName: address.contactName || '',
              contactPhone: address.contactPhone || ''
            }
          }
          return result
        }, {})
        return defaults.map(address => addressMap[address.addressType] || address)
      }
      return defaults
    },
    buildSubmitPayload() {
      const formData = this.form
      const payload = {
        customerName: formData.customerName,
        phone: formData.phone,
        gestationalWeek: formData.gestationalWeek,
        allergyTags: Array.isArray(formData.allergyTags) ? formData.allergyTags : [],
        medicalRequirements: formData.medicalRequirements,
        remark: formData.remark,
        addresses: this.createAddressesFromForm(formData)
      }

      if (formData.id) {
        payload.id = formData.id
      }

      if (this.isCreateMode()) {
        const orderInfo = formData.orderInfo || createFirstOrderDefaultForm()
        const breakfastCount = orderInfo.breakfastCount || 0
        const lunchDinnerCount = orderInfo.lunchDinnerCount || 0
        payload.orderInfo = {
          parentPackageId: orderInfo.parentPackageId,
          childPackageId: orderInfo.childPackageId,
          breakfastCount,
          lunchDinnerCount,
          totalCount: breakfastCount + lunchDinnerCount,
          breakfastPrice: orderInfo.breakfastPrice || 0,
          lunchDinnerPrice: orderInfo.lunchDinnerPrice || 0,
          totalAmount: orderInfo.totalAmount || 0,
          depositAmount: orderInfo.depositAmount || 0,
          finalAmount: orderInfo.finalAmount || 0,
          scheduleMode: orderInfo.scheduleMode || 'SCHEDULE',
          startDate: orderInfo.startDate,
          endDate: orderInfo.endDate,
          mealType: orderInfo.mealType || 'ALL',
          customerSource: orderInfo.customerSource || null,
          deliveryDates: this.serializeDeliveryDates(orderInfo.deliveryDates)
        }
      }

      return payload
    },
    cancelDialog() {
      this.crud.cancelCU()
    },
    async submitForm() {
      // 基础字段校验
      const basicValid = await new Promise(resolve => {
        this.$refs.form.validate((valid) => resolve(valid))
      })
      if (!basicValid) return

      // 首单信息校验（仅新增时）
      if (this.isCreateMode() && this.$refs.orderFormRef) {
        const orderValid = await this.$refs.orderFormRef.validate().catch(() => false)
        if (!orderValid) return
      }

      try {
        this.submitLoading = true
        const payload = this.buildSubmitPayload()
        if (payload.id) {
          await profileApi.edit(payload)
          this.$message.success('编辑成功')
        } else {
          await profileApi.add(payload)
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
    [CRUD.HOOK.beforeToAdd]() {
      Object.assign(this.form, JSON.parse(JSON.stringify(defaultForm)))
      return true
    },
    [CRUD.HOOK.beforeToCU]() {
      // 确保过敏食物是数组
      if (!this.form.allergyTags || !Array.isArray(this.form.allergyTags)) {
        this.$set(this.form, 'allergyTags', [])
      }
      // 确保地址完整
      const existingTypes = (this.form.addresses || []).map(a => a.addressType)
      const requiredTypes = ['DEFAULT', 'WORKDAY', 'WEEKEND']
      const missingTypes = requiredTypes.filter(t => !existingTypes.includes(t))
      if (missingTypes.length > 0 || !this.form.addresses || this.form.addresses.length === 0) {
        const addressMap = new Map()
        ;(this.form.addresses || []).forEach(a => {
          addressMap.set(a.addressType, a)
        })
        const newAddresses = requiredTypes.map(type => {
          return addressMap.get(type) || { addressType: type, addressDetail: '', contactName: '', contactPhone: '' }
        })
        this.$set(this.form, 'addresses', newAddresses)
      }
      // 确保首单信息存在
      if (!this.form.orderInfo) {
        this.$set(this.form, 'orderInfo', createFirstOrderDefaultForm())
      }
      return true
    },
    async handleEdit(row) {
      const requestId = this.editRequestId + 1
      this.editRequestId = requestId
      try {
        const res = await profileApi.getProfile(row.id)
        if (requestId !== this.editRequestId) {
          return
        }
        const detail = res.data || res
        this.crud.toEdit(detail)
      } catch (e) {
        if (requestId !== this.editRequestId) {
          return
        }
        this.$message.error('获取客户详情失败: ' + (e.message || '未知错误'))
      }
    },
    checkboxT() {
      return true
    },
    handleDetail(row) {
      this.currentCustomer = row
      this.detailDialogVisible = true
    },
    handleViewAllOrders(customer) {
      // 跳转到订单页面并筛选该客户的订单
      this.$router.push({
        path: '/customer/order',
        query: {
          customerId: customer.id,
          customerName: customer.customerName
        }
      })
    },
    async searchAllergy(query) {
      if (!query) {
        this.allergyOptions = []
        return
      }
      this.allergyLoading = true
      try {
        const res = await queryIngredients({ name: query, page: 0, size: 20 })
        this.allergyOptions = res.content || []
      } catch (e) {
        console.error('searchAllergy error', e)
      } finally {
        this.allergyLoading = false
      }
    },
    async doGenerateMealPlan() {
      const valid = await new Promise(resolve => {
        this.$refs.mealPlanForm.validate((v) => resolve(v))
      })
      if (!valid) return
      this.mealPlanLoading = true
      this.mealPlanResult = null
      try {
        const res = await generateMealPlan(this.mealPlanForm)
        this.mealPlanResult = res.data || res
        this.$message.success('生成排餐计划执行完成')
      } catch (e) {
        console.error('generateMealPlan error', e)
      } finally {
        this.mealPlanLoading = false
      }
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
