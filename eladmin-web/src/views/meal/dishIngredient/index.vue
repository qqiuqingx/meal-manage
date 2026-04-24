<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form ref="queryForm" :model="queryParams" :inline="true">
        <el-form-item label="配料名称" prop="name">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入配料名称"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="一级分类" prop="parentCategoryId">
          <el-select
            v-model="queryParams.parentCategoryId"
            placeholder="请选择一级分类"
            clearable
            @change="handleParentCategoryChange"
          >
            <el-option
              v-for="item in level1Categories"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="二级分类" prop="categoryId">
          <el-select
            v-model="queryParams.categoryId"
            placeholder="请选择二级分类"
            clearable
            :disabled="!queryParams.parentCategoryId"
          >
            <el-option
              v-for="item in level2Categories"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
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
        <el-button type="warning" icon="el-icon-download" @click="handleDownload">导出</el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="ingredientList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="配料名称" prop="name" align="center" />
        <el-table-column label="分类" align="center" width="200">
          <template slot-scope="scope">
            <span v-if="scope.row.categoryPathName">{{ scope.row.categoryPathName }}</span>
            <el-tag
              v-else
              :type="getCategoryTagType(scope.row.category)"
              size="small"
            >
              {{ getCategoryLabel(scope.row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" align="center" />
        <el-table-column label="热量(卡/单位)" prop="calories" align="center" />
        <el-table-column label="备注" prop="remark" align="center" />
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
    <ingredient-form ref="ingredientForm" @refresh="getList" />
  </div>
</template>

<script>
import { queryIngredients, editIngredient, delIngredients, downloadIngredients } from '@/api/dishIngredient'
import { queryCategoryTree } from '@/api/dishIngredientCategory'
import Pagination from '@/components/Pagination'
import IngredientForm from './form'

export default {
  name: 'DishIngredient',
  components: {
    Pagination,
    IngredientForm
  },
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      total: 0,
      ingredientList: [],
      queryParams: {
        page: 0,
        size: 10,
        name: null,
        parentCategoryId: null,
        categoryId: null,
        enabled: null
      },
      level1Categories: [],
      level2Categories: [],
      categoryMap: {
        MEAT: { label: '肉类', type: 'danger' },
        VEGETABLE: { label: '蔬菜', type: 'success' },
        SEAFOOD: { label: '海鲜', type: 'primary' },
        TOFU: { label: '豆制品', type: 'warning' },
        SPICE: { label: '调料', type: 'info' },
        OTHER: { label: '其他', type: '' }
      }
    }
  },
  created() {
    this.initCategories()
    this.getList()
  },
  methods: {
    async initCategories() {
      try {
        const tree = await queryCategoryTree()
        this.level1Categories = tree || []
      } catch (error) {
        console.error('加载分类失败', error)
      }
    },
    handleParentCategoryChange(parentId) {
      this.level2Categories = []
      this.queryParams.categoryId = null
      if (parentId) {
        const parent = this.level1Categories.find(c => c.id === parentId)
        if (parent && parent.children) {
          this.level2Categories = parent.children
        }
      }
    },
    getList() {
      this.loading = true
      queryIngredients(this.queryParams).then(response => {
        this.ingredientList = response.content || []
        this.total = response.totalElements || 0
      }).catch(() => {
        this.ingredientList = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.page = 0
      this.getList()
    },
    resetQuery() {
      this.level2Categories = []
      this.resetForm('queryForm')
      this.handleQuery()
    },
    handlePagination({ page, limit }) {
      this.queryParams.page = page - 1
      this.queryParams.size = limit
      this.getList()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.$refs.ingredientForm.handleAdd()
    },
    handleUpdate(row) {
      this.$refs.ingredientForm.handleUpdate(row.id)
    },
    handleStatusChange(row) {
      editIngredient(row).then(() => {
        this.$message.success('更新成功')
      }).catch(() => {
        row.enabled = !row.enabled
      })
    },
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.ids
      this.$confirm('是否确认删除配料编号为"' + ids + '"的数据项?', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return delIngredients(ids)
      }).then(() => {
        this.getList()
        this.$message.success('删除成功')
      }).catch(() => {})
    },
    handleDownload() {
      const params = {
        name: this.queryParams.name,
        parentCategoryId: this.queryParams.parentCategoryId,
        categoryId: this.queryParams.categoryId,
        enabled: this.queryParams.enabled
      }
      downloadIngredients(params).then(response => {
        const blob = new Blob([response], { type: 'application/vnd.ms-excel' })
        const link = document.createElement('a')
        link.href = URL.createObjectURL(blob)
        link.download = '配料列表.xlsx'
        link.click()
        URL.revokeObjectURL(link.href)
      })
    },
    getCategoryLabel(category) {
      return this.categoryMap[category] ? this.categoryMap[category].label : category
    },
    getCategoryTagType(category) {
      return this.categoryMap[category] ? this.categoryMap[category].type : ''
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
