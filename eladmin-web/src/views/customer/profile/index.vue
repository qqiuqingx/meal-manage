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
      <el-table-column label="客户编号" prop="customerCode" width="100" />
      <el-table-column label="姓名" prop="customerName" width="100" />
      <el-table-column label="手机号" prop="phone" width="120" />
      <el-table-column label="地址" prop="defaultAddress" min-width="150" />
      <el-table-column label="孕周" prop="gestationalWeek" width="60" align="center" />
      <el-table-column label="早餐数" prop="breakfastCount" width="80" align="center" />
      <el-table-column label="午晚数" prop="lunchDinnerCount" width="80" align="center" />
      <el-table-column label="剩余早餐" prop="remainingBreakfastCount" width="90" align="center" />
      <el-table-column label="剩余午晚" prop="remainingLunchDinnerCount" width="90" align="center" />
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
            <el-form-item label="排除菜品">
              <el-select
                v-model="form.excludedDishIds"
                multiple
                filterable
                remote
                :remote-method="searchExcludedDish"
                :loading="excludedDishLoading"
                placeholder="输入菜品名称搜索并选择排除"
                style="width: 100%;"
              >
                <el-option
                  v-for="item in excludedDishOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
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

  </div>
</template>

<script>
import * as profileApi from '@/api/customer/profile'
import { queryIngredients } from '@/api/dishIngredient'
import { queryDishes } from '@/api/dish'
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
  excludedDishIds: [],
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
      crudMethod: { ...profileApi },
      query: {
        customerCode: '',
        customerName: '',
        phone: ''
      }
    })
  },
  data() {
    return {
      permission: {
        add: ['admin', 'customerProfile:add'],
        edit: ['admin', 'customerProfile:edit'],
        del: ['admin', 'customerProfile:del']
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
      excludedDishOptions: [],
      excludedDishLoading: false,
      editRequestId: 0,
      detailDialogVisible: false,
      currentCustomer: null
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
        excludedDishIds: Array.isArray(formData.excludedDishIds) ? formData.excludedDishIds : [],
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
      // 确保排除菜品是数组
      if (!this.form.excludedDishIds || !Array.isArray(this.form.excludedDishIds)) {
        this.$set(this.form, 'excludedDishIds', [])
      }
      // 回填排除菜品的选项（编辑时详情已有 ids，需要把对应菜品预加载到 options 中）
      if (this.form.excludedDishIds.length > 0) {
        this.preloadExcludedDishes(this.form.excludedDishIds)
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
    async searchExcludedDish(query) {
      if (!query) {
        this.excludedDishOptions = []
        return
      }
      this.excludedDishLoading = true
      try {
        const res = await queryDishes({ name: query, page: 0, size: 20 })
        this.excludedDishOptions = res.content || []
      } catch (e) {
        console.error('searchExcludedDish error', e)
      } finally {
        this.excludedDishLoading = false
      }
    },
    async preloadExcludedDishes(ids) {
      // 编辑时将已选的 excludedDishIds 对应菜品信息加载到选项中，保证 label 正常显示
      try {
        const res = await queryDishes({ ids: ids.join(','), page: 0, size: ids.length + 10 })
        const dishes = res.content || []
        // 合并已有选项，避免覆盖用户当前搜索结果
        const existingIds = new Set(this.excludedDishOptions.map(d => d.id))
        dishes.forEach(d => {
          if (!existingIds.has(d.id)) {
            this.excludedDishOptions.push(d)
          }
        })
      } catch (e) {
        console.error('preloadExcludedDishes error', e)
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
