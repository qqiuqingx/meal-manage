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
            style="width: 150px;"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="菜品类型" prop="dishType">
          <el-select v-model="queryParams.dishType" placeholder="请选择" clearable style="width: 110px;">
            <el-option v-for="item in dishTypeOptions" :key="item.key" :label="item.label" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属套餐" prop="mealPackage">
          <el-select v-model="queryParams.mealPackage" placeholder="请选择" clearable style="width: 130px;">
            <el-option v-for="pkg in packageOptions" :key="pkg.id" :label="pkg.packageName" :value="pkg.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排期日期" prop="scheduleDate">
          <el-date-picker
            v-model="queryParams.scheduleDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="请选择日期"
            clearable
            style="width: 150px;"
          />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-select v-model="queryParams.enabled" placeholder="全部" clearable style="width: 90px;">
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

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never">
      <div slot="header" class="flex items-center justify-between">
        <span class="font-bold text-base">菜品主档列表</span>
        <div>
          <el-button type="primary" icon="el-icon-plus" @click="handleAdd">新增</el-button>
          <el-button
            type="danger"
            icon="el-icon-delete"
            :disabled="ids.length === 0"
            @click="handleBatchDelete"
          >批量删除</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="dishList"
        border
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="40" align="center" />
        <el-table-column label="菜品名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="制作流程" prop="cookingMethod" min-width="200" show-overflow-tooltip />
        <el-table-column label="菜品类型" prop="dishType" width="100" align="center">
          <template slot-scope="scope">
            <span class="type-badge" :class="'type-' + scope.row.dishType">
              {{ translateDishType(scope.row.dishType) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="所属套餐" prop="mealPackages" min-width="180">
          <template slot-scope="scope">
            <template v-if="scope.row.mealPackageDetails && scope.row.mealPackageDetails.length > 0">
              <span
                v-for="pkg in scope.row.mealPackageDetails"
                :key="pkg.id"
                class="package-tag"
              >
                {{ pkg.packageName || ('套餐#' + pkg.id) }}
              </span>
            </template>
            <template v-else-if="scope.row.mealPackages && scope.row.mealPackages.length > 0">
              <span v-for="mp in scope.row.mealPackages" :key="mp" class="package-tag package-tag-plain">
                {{ translatePackage(mp) }}
              </span>
            </template>
            <template v-else>
              <span class="text-gray-400 text-xs">—</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="scope">
            <el-switch
              :value="scope.row.enabled"
              :active-value="true"
              :inactive-value="false"
              active-color="#006b5c"
              inactive-color="#bbcac4"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template slot-scope="scope">
            <el-button type="text" size="small" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button type="text" size="small" icon="el-icon-delete" class="text-danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap mt-mb-4">
        <el-pagination
          :current-page="pagination.page"
          :page-sizes="[10, 20, 50]"
          :page-size="pagination.size"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <dish-form ref="dishForm" @refresh="getList" />
  </div>
</template>

<script>
import { queryDishes, editDish, delDish, queryPackages } from '@/api/dish'
import DishForm from './dish'

export default {
  name: 'DishList',
  components: { DishForm },
  data() {
    return {
      loading: false,
      dishList: [],
      ids: [],
      packageOptions: [],
      queryParams: {
        name: null,
        dishType: null,
        mealPackage: null,
        scheduleDate: null,
        enabled: null
      },
      pagination: {
        page: 1,
        size: 20,
        total: 0
      },
      dishTypeOptions: [
        { key: 'SOUP', label: '汤' },
        { key: 'MAIN', label: '主菜' },
        { key: 'SIDE', label: '副菜' },
        { key: 'VEGETABLE', label: '素菜' },
        { key: 'RICE', label: '米饭' }
      ],
      packageMap: {}
    }
  },
  created() {
    this.loadPackages()
    this.getList()
  },
  methods: {
    loadPackages() {
      queryPackages().then(response => {
        this.packageOptions = response || []
        this.packageOptions.forEach(pkg => {
          this.packageMap[pkg.id] = pkg.packageName
        })
      })
    },
    getList() {
      this.loading = true
      const params = {
        page: this.pagination.page - 1,
        size: this.pagination.size,
        name: this.queryParams.name || null,
        dishType: this.queryParams.dishType || null,
        mealPackage: this.queryParams.mealPackage || null,
        scheduleDate: this.queryParams.scheduleDate || null,
        enabled: this.queryParams.enabled
      }
      // 移除 enabled=null 的情况（后端可能不支持）
      if (params.enabled === null) {
        delete params.enabled
      }
      queryDishes(params).then(response => {
        this.dishList = response.content || []
        this.pagination.total = response.totalElements || response.total || 0
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.pagination.page = 1
      this.getList()
    },
    resetQuery() {
      this.queryParams = {
        name: null,
        dishType: null,
        mealPackage: null,
        scheduleDate: null,
        enabled: null
      }
      this.pagination.page = 1
      this.getList()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
    },
    handlePageChange(page) {
      this.pagination.page = page
      this.getList()
    },
    handleSizeChange(size) {
      this.pagination.size = size
      this.pagination.page = 1
      this.getList()
    },
    handleAdd() {
      this.$refs.dishForm.handleAdd()
    },
    handleUpdate(row) {
      this.$refs.dishForm.handleUpdate(row)
    },
    handleDelete(row) {
      const message = row.name
        ? `确认删除菜品【${row.name}】吗？`
        : '确认删除选中的菜品吗？'
      this.$confirm(message, '删除确认', { type: 'warning' })
        .then(() => delDish([row.id]))
        .then(() => {
          this.$message.success('删除成功')
          this.getList()
        })
        .catch(() => {})
    },
    handleBatchDelete() {
      if (this.ids.length === 0) return
      this.$confirm(`确认删除选中的 ${this.ids.length} 个菜品吗？`, '批量删除', { type: 'warning' })
        .then(() => delDish(this.ids))
        .then(() => {
          this.$message.success('批量删除成功')
          this.ids = []
          this.getList()
        })
        .catch(() => {})
    },
    handleStatusChange(row) {
      editDish(row).then(() => {
        this.$message.success('状态更新成功')
      }).catch(() => {
        row.enabled = !row.enabled // 回滚
      })
    },
    translateDishType(type) {
      const map = { SOUP: '汤', MAIN: '主菜', SIDE: '副菜', VEGETABLE: '素菜', RICE: '米饭' }
      return map[type] || type || '—'
    },
    translateMealTypes(types) {
      if (!types || types.length === 0) return '—'
      const map = { LUNCH: '午餐', DINNER: '晚餐', BREAKFAST: '早餐' }
      return types.map(t => map[t] || t).join(', ')
    },
    translatePackage(id) {
      return this.packageMap[id] || id || '—'
    }
  }
}
</script>

<style scoped>
.font-bold { font-weight: 700; }
.text-base { font-size: 1rem; }
.flex { display: flex; }
.items-center { align-items: center; }
.justify-between { justify-content: space-between; }
.mt-mb-4 { margin-top: 16px; }
.text-danger { color: #f56c6c; }
.text-gray-400 { color: #c0c4cc; }
.text-xs { font-size: 0.75rem; }

.search-card {
  border: none;
  background: #f8f9fb;
}
.table-card {
  border: none;
}

.type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
}
.type-SOUP { background: #e0f2fe; color: #0369a1; }
.type-MAIN { background: #fee2e2; color: #991b1b; }
.type-SIDE { background: #fef3c7; color: #92400e; }
.type-VEGETABLE { background: #dcfce7; color: #166534; }
.type-RICE { background: #f3f4f6; color: #374151; }

.package-tag {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.75rem;
  background: rgba(0, 107, 92, 0.08);
  color: #006b5c;
  margin: 2px;
  font-weight: 500;
}
.package-tag-plain {
  background: #f1f5f9;
  color: #64748b;
}
</style>
