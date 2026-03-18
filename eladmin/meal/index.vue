<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <!-- 搜索 -->
        <label class="el-form-item-label">客户名称</label>
        <el-input v-model="query.customerName" clearable placeholder="客户名称" style="width: 185px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <label class="el-form-item-label">特殊要求</label>
        <el-input v-model="query.specialNeeds" clearable placeholder="特殊要求" style="width: 185px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <label class="el-form-item-label">忌口</label>
        <el-input v-model="query.restrictions" clearable placeholder="忌口" style="width: 185px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <label class="el-form-item-label">餐数</label>
        <el-input v-model="query.num" clearable placeholder="餐数" style="width: 185px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <rrOperation :crud="crud" />
      </div>
      <!--如果想在工具栏加入更多按钮，可以使用插槽方式， slot = 'left' or 'right'-->
      <crudOperation :permission="permission" />
      <!--表单组件-->
      <el-dialog :close-on-click-modal="false" :before-close="crud.cancelCU" :visible.sync="crud.status.cu > 0" :title="crud.status.title" width="500px">
        <el-form ref="form" :model="form" :rules="rules" size="small" label-width="80px">
          <el-form-item label="id">
            <el-input v-model="form.id" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="客户名称" prop="customerName">
            <el-input v-model="form.customerName" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="特殊要求">
            <el-input v-model="form.specialNeeds" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="忌口">
            <el-input v-model="form.restrictions" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="orderDate">
            <el-input v-model="form.orderDate" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="updateDate">
            <el-input v-model="form.updateDate" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="createdAt">
            <el-input v-model="form.createdAt" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="updatedAt">
            <el-input v-model="form.updatedAt" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="餐数">
            <el-input v-model="form.num" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="开始时间">
            <el-date-picker v-model="form.startDate" type="datetime" style="width: 370px;" />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker v-model="form.endDate" type="datetime" style="width: 370px;" />
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
        <el-table-column prop="id" label="id" />
        <el-table-column prop="customerName" label="客户名称" />
        <el-table-column prop="specialNeeds" label="特殊要求" />
        <el-table-column prop="restrictions" label="忌口" />
        <el-table-column prop="orderDate" label="orderDate" />
        <el-table-column prop="updateDate" label="updateDate" />
        <el-table-column prop="createdAt" label="createdAt" />
        <el-table-column prop="updatedAt" label="updatedAt" />
        <el-table-column prop="num" label="餐数" />
        <el-table-column prop="startDate" label="开始时间" />
        <el-table-column prop="endDate" label="结束时间" />
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
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'
import udOperation from '@crud/UD.operation'
import pagination from '@crud/Pagination'

const defaultForm = { id: null, customerName: null, specialNeeds: null, restrictions: null, orderDate: null, updateDate: null, createdAt: null, updatedAt: null, num: null, startDate: null, endDate: null }
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
        ]
      },
      queryTypeOptions: [
        { key: 'customerName', display_name: '客户名称' },
        { key: 'specialNeeds', display_name: '特殊要求' },
        { key: 'restrictions', display_name: '忌口' },
        { key: 'num', display_name: '餐数' }
      ]
    }
  },
  methods: {
    // 钩子：在获取表格数据之前执行，false 则代表不获取数据
    [CRUD.HOOK.beforeRefresh]() {
      return true
    }
  }
}
</script>

<style scoped>

</style>
