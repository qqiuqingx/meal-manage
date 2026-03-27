<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <el-input v-model="query.customerCode" clearable size="small" placeholder="客户编号" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.customerName" clearable size="small" placeholder="客户姓名" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.phone" clearable size="small" placeholder="手机号" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-select v-model="query.status" clearable size="small" placeholder="状态" class="filter-item" style="width: 90px" @change="crud.toQuery">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
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
      <el-table-column label="客户编号" prop="customerCode" width="100" />
      <el-table-column label="姓名" prop="customerName" width="100" />
      <el-table-column label="手机号" prop="phone" width="120" />
      <el-table-column label="默认地址" prop="defaultAddress" min-width="150" />
      <el-table-column label="孕周" prop="gestationalWeek" width="60" align="center" />
      <el-table-column label="状态" width="70" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status ? 'success' : 'danger'">
            {{ scope.row.status ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="150" />
      <el-table-column v-if="checkPer(['admin','customerProfile:edit','customerProfile:status'])" label="操作" width="180px" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" icon="edit" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button
            size="mini"
            :type="scope.row.status ? 'warning' : 'success'"
            :icon="scope.row.status ? 'close' : 'check'"
            @click="toggleStatus(scope.row)"
          >
            {{ scope.row.status ? '停用' : '启用' }}
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
    <el-dialog append-to-body :close-on-click-modal="false" :before-close="crud.cancelCU" :visible.sync="crud.status.cu > 0" :title="crud.status.title" width="800px" top="5vh">
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
                allow-create
                default-first-option
                placeholder="输入或选择过敏食物"
                style="width: 100%;"
              />
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

        <!-- 创建时显示首单信息 -->
        <template v-if="isCreateMode()">
          <el-divider content-position="left">首单信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="父套餐" prop="orderInfo.parentPackageId">
                <el-select v-model="form.orderInfo.parentPackageId" placeholder="选择父套餐" style="width: 100%;" @change="parentPackageChange">
                  <el-option v-for="item in parentPackages" :key="item.id" :label="item.categoryName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="子套餐" prop="orderInfo.childPackageId">
                <el-select v-model="form.orderInfo.childPackageId" placeholder="选择子套餐" style="width: 100%;" @change="calcTotalCount">
                  <el-option v-for="item in childPackages" :key="item.id" :label="item.categoryName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="早餐数">
                <el-input-number v-model="form.orderInfo.breakfastCount" :min="0" controls-position="right" style="width: 100%;" @change="calcTotalCount" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="午餐+晚餐数">
                <el-input-number v-model="form.orderInfo.lunchDinnerCount" :min="0" controls-position="right" style="width: 100%;" @change="calcTotalCount" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="总份数">
                <el-input-number v-model="form.orderInfo.totalCount" :min="0" disabled controls-position="right" style="width: 100%;" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="早餐单价">
                <el-input-number v-model="form.orderInfo.breakfastPrice" :min="0" :precision="2" controls-position="right" style="width: 100%;" @change="calcTotalAmount" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="午晚单价">
                <el-input-number v-model="form.orderInfo.lunchDinnerPrice" :min="0" :precision="2" controls-position="right" style="width: 100%;" @change="calcTotalAmount" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="总价">
                <el-input-number v-model="form.orderInfo.totalAmount" :min="0" disabled :precision="2" controls-position="right" style="width: 100%;" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="定金">
                <el-input-number v-model="form.orderInfo.depositAmount" :min="0" :precision="2" controls-position="right" style="width: 100%;" @change="calcTotalAmount" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="成交金额">
                <el-input-number v-model="form.orderInfo.finalAmount" :min="0" :precision="2" controls-position="right" style="width: 100%;" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开始日期" prop="orderInfo.startDate">
                <el-date-picker v-model="form.orderInfo.startDate" type="date" placeholder="选择开始日期" style="width: 100%;" value-format="yyyy-MM-dd" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="结束日期" prop="orderInfo.endDate">
                <el-date-picker v-model="form.orderInfo.endDate" type="date" placeholder="选择结束日期" style="width: 100%;" value-format="yyyy-MM-dd" />
              </el-form-item>
            </el-col>
          </el-row>
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
        <el-button type="text" @click="crud.cancelCU">取消</el-button>
        <el-button :loading="crud.status.cu === 2" type="primary" @click="crud.submitCU">确认</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as profileApi from '@/api/customer/profile'
import * as categoryApi from '@/api/customer/packageCategory'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'

function createDefaultOrderInfo() {
  return {
    parentPackageId: null,
    childPackageId: null,
    breakfastCount: 0,
    lunchDinnerCount: 0,
    totalCount: 0,
    breakfastPrice: 0,
    lunchDinnerPrice: 0,
    depositAmount: 0,
    totalAmount: 0,
    finalAmount: 0,
    startDate: null,
    endDate: null
  }
}

const defaultForm = {
  id: null,
  customerName: null,
  phone: null,
  gestationalWeek: null,
  allergyTags: [],
  medicalRequirements: null,
  remark: null,
  addresses: [
    { addressType: 'DEFAULT', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
  ],
  orderInfo: createDefaultOrderInfo()
}

export default {
  name: 'CustomerProfile',
  components: { crudOperation, rrOperation },
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
        phone: '',
        status: null
      },
      parentPackages: [],
      childPackages: [],
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
      editRequestId: 0
    }
  },
  created() {
    this.loadParentPackages()
  },
  methods: {
    isCreateMode() {
      return this.crud.status.add === CRUD.STATUS.PREPARED
    },
    createAddressesFromForm(formData = {}) {
      const defaults = [
        { addressType: 'DEFAULT', addressDetail: '', contactName: '', contactPhone: '' },
        { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
        { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
      ]
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
      const formData = JSON.parse(JSON.stringify(this.form))
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
        const orderInfo = formData.orderInfo || createDefaultOrderInfo()
        const breakfastCount = orderInfo.breakfastCount || 0
        const lunchDinnerCount = orderInfo.lunchDinnerCount || 0
        payload.status = true
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
          startDate: orderInfo.startDate,
          endDate: orderInfo.endDate
        }
      }

      return payload
    },
    [CRUD.HOOK.beforeSubmit]() {
      this.submitFormSnapshot = JSON.parse(JSON.stringify(this.form))
      const payload = this.buildSubmitPayload()
      Object.keys(this.crud.form).forEach(key => {
        this.$delete(this.crud.form, key)
      })
      Object.keys(payload).forEach(key => {
        this.$set(this.crud.form, key, payload[key])
      })
      return true
    },
    [CRUD.HOOK.afterSubmit]() {
      this.submitFormSnapshot = null
    },
    restoreSubmitFormSnapshot() {
      if (!this.submitFormSnapshot) {
        return
      }
      const snapshot = JSON.parse(JSON.stringify(this.submitFormSnapshot))
      Object.keys(this.crud.form).forEach(key => {
        this.$delete(this.crud.form, key)
      })
      Object.keys(snapshot).forEach(key => {
        this.$set(this.crud.form, key, snapshot[key])
      })
      this.submitFormSnapshot = null
    },
    [CRUD.HOOK.afterAddError]() {
      this.restoreSubmitFormSnapshot()
    },
    [CRUD.HOOK.afterEditError]() {
      this.restoreSubmitFormSnapshot()
    },
    [CRUD.HOOK.beforeToAdd]() {
      this.loadParentPackages()
      return true
    },
    [CRUD.HOOK.afterToAdd]() {
      this.$set(this.form, 'addresses', [
        { addressType: 'DEFAULT', addressDetail: '', contactName: '', contactPhone: '' },
        { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
        { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
      ])
      this.$set(this.form, 'orderInfo', createDefaultOrderInfo())
    },
    [CRUD.HOOK.beforeToCU]() {
      // 确保过敏食物是数组
      if (!this.form.allergyTags || !Array.isArray(this.form.allergyTags)) {
        this.$set(this.form, 'allergyTags', [])
      }
      // 如果 addresses 不完整，补充缺失的地址类型
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
      if (!this.form.orderInfo) {
        this.$set(this.form, 'orderInfo', createDefaultOrderInfo())
      }
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
    initForm() {
      const savedAddresses = this.form && this.form.addresses
      Object.assign(this.form, JSON.parse(JSON.stringify(defaultForm)))
      if (savedAddresses) {
        this.form.addresses = savedAddresses
      }
    },
    async loadParentPackages() {
      try {
        const res = await categoryApi.getParents()
        const data = res.data || res
        this.$nextTick(() => {
          this.parentPackages = data || []
        })
      } catch (e) {
        console.error('loadParentPackages error', e)
      }
    },
    async parentPackageChange(parentId) {
      await this.loadChildPackages(parentId)
      this.form.orderInfo.childPackageId = null
      this.calcTotalCount()
    },
    async loadChildPackages(parentId) {
      if (!parentId) {
        this.childPackages = []
        return
      }
      try {
        const res = await categoryApi.getTree()
        const tree = res.data || res || []
        const parent = tree.find(p => p.id === parentId)
        this.childPackages = parent ? (parent.children || []) : []
      } catch (e) {
        console.error('loadChildPackages error', e)
      }
    },
    calcTotalCount() {
      const breakfast = this.form.orderInfo.breakfastCount || 0
      const lunchDinner = this.form.orderInfo.lunchDinnerCount || 0
      this.form.orderInfo.totalCount = breakfast + lunchDinner
      this.calcTotalAmount()
    },
    calcTotalAmount() {
      const breakfastCount = this.form.orderInfo.breakfastCount || 0
      const lunchDinnerCount = this.form.orderInfo.lunchDinnerCount || 0
      const breakfastPrice = this.form.orderInfo.breakfastPrice || 0
      const lunchDinnerPrice = this.form.orderInfo.lunchDinnerPrice || 0
      const totalAmount = breakfastCount * breakfastPrice + lunchDinnerCount * lunchDinnerPrice
      this.form.orderInfo.totalAmount = totalAmount
      // 如果成交金额未填写，默认等于总价
      if (!this.form.orderInfo.finalAmount) {
        this.form.orderInfo.finalAmount = totalAmount
      }
    },
    async toggleStatus(row) {
      const newStatus = !row.status
      try {
        await profileApi.updateStatus(row.id, { status: newStatus })
        this.$message.success('状态更新成功')
        this.crud.refresh()
      } catch (e) {
        this.$message.error('状态更新失败: ' + (e.message || '未知错误'))
      }
    },
    checkboxT(row) {
      return true
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
