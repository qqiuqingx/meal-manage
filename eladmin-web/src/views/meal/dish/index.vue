<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form ref="queryForm" :model="queryParams" :inline="true">
        <el-form-item label="菜品名称" prop="name">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入菜品名称"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="菜品类型" prop="dishType">
          <el-select v-model="queryParams.dishType" placeholder="请选择菜品类型" clearable>
            <el-option label="主菜" value="MAIN" />
            <el-option label="副菜" value="SIDE" />
            <el-option label="汤" value="SOUP" />
            <el-option label="素菜" value="VEGETABLE" />
            <el-option label="米饭" value="RICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="餐次" prop="mealType">
          <el-select v-model="queryParams.mealType" placeholder="请选择餐次" clearable>
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐" prop="mealPackage">
          <el-select v-model="queryParams.mealPackage" placeholder="请选择套餐" clearable>
            <el-option label="月子餐" value="yuezi" />
            <el-option label="孕期餐" value="yunqi" />
            <el-option label="小月子" value="xiaoyuezi" />
            <el-option label="营养餐" value="yingyang" />
            <el-option label="分娩餐" value="fenmian" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-select v-model="queryParams.enabled" placeholder="请选择状态" clearable>
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="table-card" shadow="never">
      <div slot="header" class="clearfix">
        <el-button type="primary" icon="el-icon-plus" @click="handleAdd">新增</el-button>
        <el-button type="danger" icon="el-icon-delete" :disabled="multiple" @click="handleDelete">删除</el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="dishList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="菜品名称" prop="name" align="center" />
        <el-table-column label="菜品类型" prop="dishType" align="center">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.dishType === 'MAIN'" type="danger">主菜</el-tag>
            <el-tag v-else-if="scope.row.dishType === 'SIDE'" type="warning">副菜</el-tag>
            <el-tag v-else-if="scope.row.dishType === 'SOUP'" type="info">汤</el-tag>
            <el-tag v-else-if="scope.row.dishType === 'VEGETABLE'" type="success">素菜</el-tag>
            <el-tag v-else-if="scope.row.dishType === 'RICE'">米饭</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="餐次" prop="mealTypes" align="center">
          <template slot-scope="scope">
            <el-tag v-for="item in scope.row.mealTypes" :key="item" style="margin-right: 5px;">
              {{ item === 'LUNCH' ? '午餐' : '晚餐' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="套餐" prop="mealPackages" align="center">
          <template slot-scope="scope">
            <el-tag v-for="pkg in scope.row.mealPackageDetails" :key="pkg.packageCode" style="margin-right: 5px;">
              {{ pkg.packageName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排期" prop="schedule" align="center">
          <template slot-scope="scope">
            <span>{{ formatSchedule(scope.row.schedule) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="enabled" align="center">
          <template slot-scope="scope">
            <el-switch
              v-model="scope.row.enabled"
              :active-value="true"
              :inactive-value="false"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150">
          <template slot-scope="scope">
            <el-button type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button type="text" icon="el-icon-delete" style="color: #f56c6c;" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        :page="queryParams.page + 1"
        :limit.sync="queryParams.size"
        @pagination="handlePagination"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <dish-form ref="dishForm" @refresh="getList" />
  </div>
</template>

<script>
import { queryDishes, editDish, delDish } from '@/api/dish'
import DishForm from './dish'
import Pagination from '@/components/Pagination'

export default {
  name: 'Dish',
  components: {
    DishForm,
    Pagination
  },
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      total: 0,
      dishList: [],
      queryParams: {
        page: 0,
        size: 10,
        name: null,
        dishType: null,
        mealType: null,
        mealPackage: null,
        enabled: null
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      queryDishes(this.queryParams).then(response => {
        this.dishList = response.content || []
        this.total = response.totalElements || response.total || 0
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handlePagination({ page, limit }) {
      // el-pagination 从 1 开始，后端从 0 开始
      this.queryParams.page = page - 1
      this.queryParams.size = limit
      this.getList()
    },
    handleQuery() {
      this.queryParams.page = 0
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.$refs.dishForm.handleAdd()
    },
    handleUpdate(row) {
      this.$refs.dishForm.handleUpdate(row)
    },
    handleStatusChange(row) {
      editDish(row).then(() => {
        this.$message.success('更新成功')
      }).catch(() => {
        row.enabled = !row.enabled
      })
    },
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.ids
      this.$confirm('是否确认删除菜品编号为"' + ids + '"的数据项?', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return delDish(ids)
      }).then(() => {
        this.getList()
        this.$message.success('删除成功')
      }).catch(() => {})
    },
    formatSchedule(schedule) {
      if (!schedule || !schedule.length) return '-'
      return schedule.map(s => {
        const [week, day] = s.split('-')
        return `第${week}周周${['一', '二', '三', '四', '五', '六', '日'][day - 1]}`
      }).join(', ')
    },
    resetForm(formName) {
      this.$refs[formName].resetFields()
    }
  }
}
</script>

<style scoped>
.search-card {
  margin-bottom: 15px;
}
.table-card {
  margin-bottom: 15px;
}
</style>
