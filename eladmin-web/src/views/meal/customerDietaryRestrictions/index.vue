<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <!-- 搜索 -->
        <label class="el-form-item-label">客户名称</label>
        <el-input v-model="query.customerName" clearable placeholder="客户名称" style="width: 185px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <label class="el-form-item-label">手机号</label>
        <el-input v-model="query.phone" clearable placeholder="手机号" style="width: 185px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <label class="el-form-item-label">开始时间</label>
        <el-date-picker v-model="query.startDate" type="date" value-format="yyyy-MM-dd" clearable placeholder="开始时间" style="width: 185px;" class="filter-item" @change="crud.toQuery" />
        <label class="el-form-item-label">结束时间</label>
        <el-date-picker v-model="query.endDate" type="date" value-format="yyyy-MM-dd" clearable placeholder="结束时间" style="width: 185px;" class="filter-item" @change="crud.toQuery" />
        <label class="el-form-item-label">来源</label>
        <el-select v-model="query.source" clearable placeholder="来源" style="width: 185px;" class="filter-item" @change="crud.toQuery">
          <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <rrOperation :crud="crud" />
      </div>
      <!--如果想在工具栏加入更多按钮，可以使用插槽方式， slot = 'left' or 'right'-->
      <crudOperation :permission="permission" />
      <!--表单组件-->
      <el-dialog :close-on-click-modal="false" :before-close="crud.cancelCU" :visible.sync="crud.status.cu > 0" :title="crud.status.title" width="500px">
        <el-form ref="form" :model="form" :rules="rules" size="small" label-width="80px">
          <el-form-item label="客户名称" prop="customerName">
            <el-input v-model="form.customerName" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="特殊要求">
            <el-input v-model="form.specialNeeds" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="忌口">
            <el-tag
              v-for="(tag, index) in form.restrictions"
              :key="index"
              closable
              :disable-transitions="false"
              style="margin-right: 10px;"
              @close="removeRestriction(index)"
            >
              {{ tag }}
            </el-tag>
            <el-input
              v-if="restrictionInputVisible"
              ref="restrictionInput"
              v-model="restrictionInputValue"
              class="input-new-tag"
              size="small"
              style="width: 100px;"
              @keyup.enter.native="handleRestrictionInputConfirm"
              @blur="handleRestrictionInputConfirm"
            />
            <el-button v-else class="button-new-tag" size="small" @click="showRestrictionInput">+ 新建</el-button>
          </el-form-item>
          <el-form-item label="餐数" prop="num">
            <el-input v-model="form.num" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="开始时间" prop="startDate">
            <el-date-picker v-model="form.startDate" type="date" value-format="yyyy-MM-dd" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="结束时间" prop="endDate">
            <el-date-picker v-model="form.endDate" type="date" value-format="yyyy-MM-dd" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="客户地址">
            <el-input v-model="form.customerAddress" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="来源">
            <el-select v-model="form.source" placeholder="请选择来源" style="width: 370px;" clearable>
              <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="客户套餐" prop="mealPackage">
            <el-select v-model="form.mealPackage" placeholder="请选择客户套餐" style="width: 370px;">
              <el-option label="月子餐" value="yuezi" />
              <el-option label="孕期餐" value="yunqi" />
              <el-option label="小月子" value="xiaoyuezi" />
              <el-option label="营养餐" value="yingyang" />
              <el-option label="分娩餐" value="fenmian" />
            </el-select>
          </el-form-item>
        </el-form>
        <div slot="footer" class="dialog-footer">
          <el-button type="text" @click="crud.cancelCU">取消</el-button>
          <el-button :loading="crud.status.cu === 2" type="primary" @click="crud.submitCU">确认</el-button>
        </div>
      </el-dialog>
      <!--表格渲染-->
      <el-table ref="table" v-loading="crud.loading" :data="crud.data" size="small" style="width: 100%;" @selection-change="crud.selectionChangeHandler">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="customerName" label="客户名称" />
        <el-table-column prop="specialNeeds" label="特殊要求" />
        <el-table-column prop="restrictions" label="忌口">
          <template slot-scope="scope">
            {{ scope.row.restrictions ? scope.row.restrictions.join('、') : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="num" label="餐数" />
        <el-table-column prop="startDate" label="开始时间" />
        <el-table-column prop="endDate" label="结束时间" />
        <el-table-column prop="customerAddress" label="客户地址" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="remainingMeals" label="剩余餐数" />
        <el-table-column prop="mealPackage" label="客户套餐">
          <template slot-scope="scope">
            {{ getMealPackageName(scope.row.mealPackage) }}
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源">
          <template slot-scope="scope">
            {{ getSourceName(scope.row.source) }}
          </template>
        </el-table-column>
        <el-table-column v-if="checkPer(['admin','customerDietaryRestrictions:edit','customerDietaryRestrictions:del'])" label="操作" width="150px" align="center">
          <template slot-scope="scope">
            <udOperation
              :data="scope.row"
              :permission="permission"
            />
          </template>
        </el-table-column>
      </el-table>
      <!--分页组件-->
      <pagination />
    </div>
  </div>
</template>

<script>
import crudCustomerDietaryRestrictions from '@/api/customerDietaryRestrictions'
import { getDictMap } from '@/api/system/dictDetail'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'
import udOperation from '@crud/UD.operation'
import pagination from '@crud/Pagination'

const defaultForm = { id: null, customerName: null, specialNeeds: null, restrictions: [], updateDate: null, createdAt: null, updatedAt: null, num: null, startDate: null, endDate: null, customerAddress: null, phone: null, remainingMeals: null, mealPackage: null, source: null }
export default {
  name: 'CustomerDietaryRestrictions',
  components: { pagination, crudOperation, rrOperation, udOperation },
  mixins: [presenter(), header(), form(defaultForm), crud()],
  cruds() {
    return CRUD({ title: 'meal', url: 'api/customerDietaryRestrictions', idField: 'id', sort: 'id,desc', crudMethod: { ...crudCustomerDietaryRestrictions }})
  },
  data() {
    return {
      permission: {
        add: ['admin', 'customerDietaryRestrictions:add'],
        edit: ['admin', 'customerDietaryRestrictions:edit'],
        del: ['admin', 'customerDietaryRestrictions:del']
      },
      rules: {
        customerName: [
          { required: true, message: '客户名称不能为空', trigger: 'blur' }
        ],
        num: [
          { required: true, message: '餐数不能为空', trigger: 'blur' }
        ],
        startDate: [
          { required: true, message: '开始时间不能为空', trigger: 'blur' }
        ],
        endDate: [
          { required: true, message: '结束时间不能为空', trigger: 'blur' }
        ],
        mealPackage: [
          { required: true, message: '客户套餐不能为空', trigger: 'change' }
        ]
      },
      queryTypeOptions: [
        { key: 'customerName', display_name: '客户名称' },
        { key: 'phone', display_name: '手机号' },
        { key: 'startDate', display_name: '开始时间' },
        { key: 'endDate', display_name: '结束时间' }
      ],
      restrictionInputVisible: false,
      restrictionInputValue: '',
      sourceOptions: []
    }
  },
  mounted() {
    this.loadSourceOptions()
  },
  methods: {
    // 钩子：在获取表格数据之前执行，false 则代表不获取数据
    [CRUD.HOOK.beforeRefresh]() {
      return true
    },
    [CRUD.HOOK.beforeToAdd]() {
      if (!this.form.restrictions) {
        this.$set(this.form, 'restrictions', [])
      }
    },
    [CRUD.HOOK.beforeToEdit]() {
      if (!this.form.restrictions) {
        this.$set(this.form, 'restrictions', [])
      }
    },
    getMealPackageName(code) {
      const map = { yuezi: '月子餐', yunqi: '孕期餐', xiaoyuezi: '小月子', yingyang: '营养餐', fenmian: '分娩餐' }
      return map[code] || code
    },
    getSourceName(value) {
      if (!value) return ''
      const item = this.sourceOptions.find(opt => opt.value === value)
      return item ? item.label : value
    },
    loadSourceOptions() {
      getDictMap('customer_source').then(res => {
        this.sourceOptions = (res.customer_source || []).map(item => ({
          label: item.label,
          value: item.value
        }))
      })
    },
    removeRestriction(index) {
      this.form.restrictions.splice(index, 1)
    },
    showRestrictionInput() {
      this.restrictionInputVisible = true
      this.$nextTick(_ => {
        this.$refs.restrictionInput.$refs.input.focus()
      })
    },
    handleRestrictionInputConfirm() {
      if (this.restrictionInputValue) {
        if (!this.form.restrictions) {
          this.$set(this.form, 'restrictions', [])
        }
        this.form.restrictions.push(this.restrictionInputValue)
      }
      this.restrictionInputVisible = false
      this.restrictionInputValue = ''
    }
  }
}
</script>

<style scoped>
.input-new-tag {
  width: 100px;
}
.button-new-tag {
  height: 32px;
  line-height: 30px;
  padding-top: 0;
  padding-bottom: 0;
}
</style>
