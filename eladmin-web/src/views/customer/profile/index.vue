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
      <el-table-column label="父套餐" prop="parentPackageName" width="100" />
      <el-table-column label="子套餐" prop="childPackageName" width="100" />
      <el-table-column label="早餐" prop="breakfastCount" width="60" align="center" />
      <el-table-column label="午晚" prop="lunchDinnerCount" width="60" align="center" />
      <el-table-column label="总份数" prop="totalCount" width="70" align="center" />
      <el-table-column label="签约日期" width="180">
        <template slot-scope="scope">
          {{ scope.row.startDate }} ~ {{ scope.row.endDate }}
        </template>
      </el-table-column>
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
          <el-button size="mini" type="primary" icon="edit" @click="crud.toEdit(scope.row)">编辑</el-button>
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
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户编号" prop="customerCode">
              <el-input v-model="form.customerCode" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户姓名" prop="customerName">
              <el-input v-model="form.customerName" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" style="width: 100%;" />
            </el-form-item>
          </el-col>
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

        <el-divider content-position="left">套餐信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="父套餐" prop="packageInfo.parentPackageId">
              <el-select v-model="form.packageInfo.parentPackageId" placeholder="选择父套餐" style="width: 100%;" @change="parentPackageChange">
                <el-option v-for="item in parentPackages" :key="item.id" :label="item.categoryName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="子套餐" prop="packageInfo.childPackageId">
              <el-select v-model="form.packageInfo.childPackageId" placeholder="选择子套餐" style="width: 100%;">
                <el-option v-for="item in childPackages" :key="item.id" :label="item.categoryName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-switch v-model="form.status" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="早餐数">
              <el-input-number v-model="form.packageInfo.breakfastCount" :min="0" controls-position="right" style="width: 100%;" @change="calcTotalCount" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="午餐+晚餐数">
              <el-input-number v-model="form.packageInfo.lunchDinnerCount" :min="0" controls-position="right" style="width: 100%;" @change="calcTotalCount" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="总份数">
              <el-input-number v-model="form.packageInfo.totalCount" :min="0" disabled controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="form.packageInfo.startDate" type="date" placeholder="选择开始日期" style="width: 100%;" value-format="yyyy-MM-dd" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker v-model="form.packageInfo.endDate" type="date" placeholder="选择结束日期" style="width: 100%;" value-format="yyyy-MM-dd" />
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
import * as profileApi from '@/api/customer/profile'
import * as categoryApi from '@/api/customer/packageCategory'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'

const defaultForm = {
  id: null,
  customerCode: null,
  customerName: null,
  phone: null,
  gestationalWeek: null,
  allergyTags: [],
  medicalRequirements: null,
  status: true,
  remark: null,
  addresses: [
    { addressType: 'DEFAULT', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
  ],
  packageInfo: {
    parentPackageId: null,
    childPackageId: null,
    breakfastCount: 0,
    lunchDinnerCount: 0,
    totalCount: 0,
    startDate: null,
    endDate: null
  }
}

export default {
  name: 'CustomerProfile',
  components: { crudOperation, rrOperation },
  mixins: [presenter(), header(), form(defaultForm), crud()],
  cruds() {
    return CRUD({ title: '客户档案', url: '/api/customerProfile', idField: 'id', sort: 'id,desc', crudMethod: { ...profileApi }})
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
        customerCode: [{ required: true, message: '请输入客户编号', trigger: 'blur' }],
        customerName: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }],
        phone: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
        ],
        'packageInfo.parentPackageId': [{ required: true, message: '请选择父套餐', trigger: 'change' }],
        'packageInfo.childPackageId': [{ required: true, message: '请选择子套餐', trigger: 'change' }],
        'packageInfo.startDate': [{ required: true, message: '请选择开始日期', trigger: 'change' }],
        'packageInfo.endDate': [{ required: true, message: '请选择结束日期', trigger: 'change' }]
      },
      CRUD_INIT: {
        title: '客户档案',
        url: '/api/customerProfile',
        crudMethod: profileApi
      }
    }
  },
  created() {
    this.loadParentPackages()
  },
  methods: {
    [CRUD.HOOK.beforeToCU]() {
      // For edit: map flat defaultAddress to addresses array.
      // For add: addresses already set by beforeToAdd.
      if (this.form && this.form.defaultAddress) {
        this.$set(this.form, 'addresses', [
          { addressType: 'DEFAULT', addressDetail: this.form.defaultAddress, contactName: '', contactPhone: '' },
          { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
          { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
        ])
      }
    },
    [CRUD.HOOK.beforeSubmit]() {
      // Sync form data to crud.form before submit, including address mapping
      const formData = JSON.parse(JSON.stringify(this.form))
      // Map flat defaultAddress to addresses array if addresses are empty/default
      if (!formData.addresses || !formData.addresses[0] || !formData.addresses[0].addressDetail) {
        formData.addresses = [
          { addressType: 'DEFAULT', addressDetail: formData.defaultAddress || '', contactName: '', contactPhone: '' },
          { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
          { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
        ]
      }
      Object.keys(formData).forEach(key => {
        this.crud.form[key] = formData[key]
      })
      return true
    },
    [CRUD.HOOK.beforeRefresh]() {
      return true
    },
    [CRUD.HOOK.beforeToAdd]() {
      this.loadParentPackages()
      return true
    },
    [CRUD.HOOK.afterToAdd]() {
      // Runs AFTER resetForm (which copies empty addresses from defaultForm).
      // Set addresses here so they persist in the dialog.
      this.$set(this.form, 'addresses', [
        { addressType: 'DEFAULT', addressDetail: '', contactName: '', contactPhone: '' },
        { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
        { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
      ])
    },
    [CRUD.HOOK.beforeToEdit](crud, form) {
      return true
    },
    [CRUD.HOOK.afterToEdit]() {
      this.loadParentPackages().then(() => {
        // 'this.form' IS 'crud.form' (same reactive object from form mixin)
        const crudData = JSON.parse(JSON.stringify(this.form))
        Object.keys(crudData).forEach(key => {
          this.form[key] = crudData[key]
        })
        if (this.form.packageInfo) {
          if (crudData.parentPackageName) {
            const parentPkg = this.parentPackages.find(p => p.categoryName === crudData.parentPackageName)
            if (parentPkg) {
              this.form.packageInfo.parentPackageId = parentPkg.id
            }
          }
          this.form.packageInfo.startDate = crudData.startDate || this.form.packageInfo.startDate
          this.form.packageInfo.endDate = crudData.endDate || this.form.packageInfo.endDate
        }
        requestAnimationFrame(() => {
          this.$forceUpdate()
          this.$nextTick(() => {
            this.loadChildPackages(this.form.packageInfo ? this.form.packageInfo.parentPackageId : null).then(() => {
              if (crudData.childPackageName && this.childPackages.length > 0) {
                const childPkg = this.childPackages.find(c => c.categoryName === crudData.childPackageName)
                if (childPkg) {
                  this.form.packageInfo.childPackageId = childPkg.id
                  this.$forceUpdate()
                }
              }
            })
          })
        })
      })
      return true
    },
    initForm() {
      // Use Object.assign to preserve binding to crud.form (not this.form = newObject)
      // Save and restore addresses since Object.assign overwrites them
      const savedAddresses = this.form && this.form.addresses
      Object.assign(this.form, JSON.parse(JSON.stringify(defaultForm)))
      if (savedAddresses) {
        this.form.addresses = savedAddresses
      }
    },
    async loadParentPackages() {
      try {
        const res = await categoryApi.getParents()
        const data = res.data || res // Handle both {data: [...]} and [...]
        this.$nextTick(() => {
          this.parentPackages = data || []
        })
      } catch (e) {
        console.error('loadParentPackages error', e)
      }
    },
    async parentPackageChange(parentId) {
      // 加载子套餐
      await this.loadChildPackages(parentId)
      // 清空子套餐选择
      this.form.packageInfo.childPackageId = null

      // 自动生成编号
      await this.generateCode(parentId)
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
    async generateCode(parentId) {
      if (!parentId) return
      try {
        const res = await profileApi.generateCode(parentId)
        const code = res.data || res
        this.form.customerCode = code
      } catch (e) {
        console.error('generateCode error', e)
      }
    },
    calcTotalCount() {
      const breakfast = this.form.packageInfo.breakfastCount || 0
      const lunchDinner = this.form.packageInfo.lunchDinnerCount || 0
      this.form.packageInfo.totalCount = breakfast + lunchDinner
    },
    async toggleStatus(row) {
      const newStatus = !row.status
      const payload = { status: newStatus }
      if (newStatus) {
        // 启用时需要带套餐信息
        this.$prompt('请输入套餐信息(直接确认使用当前套餐)', '启用客户', {
          inputValue: JSON.stringify({
            parentPackageId: row.parentPackageId,
            childPackageId: row.childPackageId,
            breakfastCount: row.breakfastCount,
            lunchDinnerCount: row.lunchDinnerCount,
            startDate: row.startDate,
            endDate: row.endDate
          })
        }).then(({ value }) => {
          try {
            const pkg = JSON.parse(value)
            payload.packageInfo = pkg
          } catch (e) {
            // 使用默认值
          }
          this.doUpdateStatus(row.id, payload)
        }).catch(() => {
          this.$message.info('已取消')
        })
      } else {
        this.doUpdateStatus(row.id, payload)
      }
    },
    async doUpdateStatus(id, payload) {
      try {
        await profileApi.updateStatus(id, payload)
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
