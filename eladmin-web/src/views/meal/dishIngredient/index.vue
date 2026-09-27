<template>
  <div :class="{ 'app-container': !isTagManagementRoute }">
    <router-view v-if="isTagManagementRoute" />
    <el-card v-else class="search-card" shadow="never">
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
        <el-form-item label="标签" prop="tagId">
          <el-select
            v-model="queryParams.tagId"
            filterable
            remote
            clearable
            :remote-method="searchTagOptions"
            :loading="tagLoading"
            placeholder="搜索标签"
            @visible-change="handleTagFilterVisible"
            @change="handleQuery"
          >
            <el-option
              v-for="item in tagOptions"
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
    <el-card v-if="!isTagManagementRoute" class="table-card" shadow="never">
      <div slot="header" class="clearfix">
        <el-button type="primary" icon="el-icon-plus" @click="handleAdd">新增</el-button>
        <el-button icon="el-icon-collection-tag" @click="handleCategoryManage">分类管理</el-button>
        <el-button
          v-permission="['dishIngredientTag:list']"
          icon="el-icon-collection-tag"
          @click="handleTagManage"
        >标签管理</el-button>
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
        <el-table-column label="标签" align="center" min-width="160">
          <template slot-scope="scope">
            <el-tag
              v-for="tag in scope.row.tags || []"
              :key="tag.id"
              size="small"
              effect="plain"
              class="ingredient-tag"
            >{{ tag.name }}</el-tag>
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
    <ingredient-form v-if="!isTagManagementRoute" ref="ingredientForm" :category-tree="categoryTree" @refresh="getList" />

    <category-manager
      v-if="!isTagManagementRoute"
      :visible.sync="categoryManagerVisible"
      :category-tree="categoryTree"
      @refresh-categories="refreshCategories"
    />
  </div>
</template>

<script>
import { queryIngredients, editIngredient, delIngredients, downloadIngredients } from '@/api/dishIngredient'
import { queryCategoryTree } from '@/api/dishIngredientCategory'
import { queryIngredientTags } from '@/api/dishIngredientTag'
import Pagination from '@/components/Pagination'
import IngredientForm from './form'
import CategoryManager from './categoryManager'

export default {
  name: 'DishIngredient',
  components: {
    Pagination,
    IngredientForm,
    CategoryManager
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
        tagId: null,
        enabled: null
      },
      categoryTree: [],
      level1Categories: [],
      level2Categories: [],
      tagOptions: [],
      tagLoading: false,
      tagSearchText: '',
      tagSearchLoaded: false,
      tagRequestId: 0,
      categoryManagerVisible: false,
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
  computed: {
    /** 当前子路由是标签管理页时，由子路由组件占据主视图。 */
    isTagManagementRoute() {
      return this.$route.path.replace(/\/+$/, '').endsWith('/dishIngredientTag')
    }
  },
  created() {
    this.refreshCategories()
    this.getList()
  },
  methods: {
    async refreshCategories() {
      try {
        const tree = await queryCategoryTree()
        this.categoryTree = tree || []
        this.level1Categories = this.categoryTree
        const changed = this.syncQueryCategoryState(this.categoryTree)
        if (changed) {
          this.getList()
        }
      } catch (error) {
        console.error('加载分类失败', error)
      }
    },
    getLevel2CategoriesByParentId(parentId) {
      if (!parentId) {
        return []
      }
      const parent = this.level1Categories.find(c => c.id === parentId)
      return parent && parent.children ? parent.children : []
    },
    syncQueryCategoryState(tree) {
      const categoryTree = Array.isArray(tree) ? tree : []
      let changed = false
      if (!this.queryParams.parentCategoryId) {
        this.level2Categories = []
        if (this.queryParams.categoryId !== null) {
          this.queryParams.categoryId = null
          changed = true
        }
        return changed
      }

      const parent = categoryTree.find(c => c.id === this.queryParams.parentCategoryId)
      if (!parent) {
        this.queryParams.parentCategoryId = null
        if (this.queryParams.categoryId !== null) {
          this.queryParams.categoryId = null
        }
        this.level2Categories = []
        changed = true
        return changed
      }

      this.level2Categories = parent.children || []
      const exists = this.level2Categories.some(c => c.id === this.queryParams.categoryId)
      if (!exists) {
        if (this.queryParams.categoryId !== null) {
          this.queryParams.categoryId = null
          changed = true
        }
      }
      return changed
    },
    handleParentCategoryChange(parentId) {
      this.queryParams.categoryId = null
      this.level2Categories = this.getLevel2CategoriesByParentId(parentId)
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
    /** 首次展开筛选器时加载标签选项。 */
    handleTagFilterVisible(visible) {
      if (visible && !this.tagSearchLoaded) {
        this.searchTagOptions(this.tagSearchText)
      }
    },
    /** 按标签名称远程搜索筛选选项。 */
    searchTagOptions(query) {
      const name = query || ''
      if (name === this.tagSearchText && (this.tagSearchLoaded || this.tagLoading)) return
      this.tagRequestId += 1
      this.tagLoading = false
      this.tagSearchText = name
      this.tagSearchLoaded = false
      const selected = this.tagOptions.find(item => item.id === this.queryParams.tagId)
      this.tagOptions = selected ? [selected] : []
      const requestId = ++this.tagRequestId
      this.tagLoading = true
      queryIngredientTags({ name: name || undefined, page: 0, size: 20 }).then(response => {
        if (requestId !== this.tagRequestId) return
        this.tagOptions = this.mergeTagOptions(this.tagOptions, response.content || [])
        this.tagSearchLoaded = true
      }).catch(() => {
        if (requestId === this.tagRequestId) this.tagSearchLoaded = false
      }).finally(() => {
        if (requestId === this.tagRequestId) this.tagLoading = false
      })
    },
    /** 合并远程结果并让最新标签名称覆盖缓存。 */
    mergeTagOptions(current, incoming) {
      const options = new Map((current || []).map(item => [item.id, item]))
      ;(incoming || []).forEach(item => options.set(item.id, item))
      return Array.from(options.values())
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
    handleCategoryManage() {
      this.categoryManagerVisible = true
    },
    /** 打开标签维护页。 */
    handleTagManage() {
      const parentPath = this.$route.path.replace(/\/+$/, '')
      this.$router.push({ path: `${parentPath}/dishIngredientTag` })
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
        tagId: this.queryParams.tagId,
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
.ingredient-tag {
  margin: 2px;
}
</style>
