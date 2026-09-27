<template>
  <div :class="{ 'app-container ingredient-page': !isTagManagementRoute }">
    <router-view v-if="isTagManagementRoute" />
    <template v-else>
      <header class="ingredient-header">
        <div class="page-title">配菜管理 <small>配料字典 · 分类分级</small></div>
        <div class="view-tabs" role="group" aria-label="展示方式">
          <button :class="{ active: viewMode === 'table' }" @click="setViewMode('table')">表格视图</button>
          <button :class="{ active: viewMode === 'market' }" @click="setViewMode('market')">商超视图</button>
        </div>
      </header>
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
          <el-form-item v-show="viewMode === 'table'" label="一级分类" prop="parentCategoryId">
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
          <el-form-item v-show="viewMode === 'table'" label="二级分类" prop="categoryId">
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

      <div :class="{ 'market-layout': viewMode === 'market' }">
        <aside v-if="viewMode === 'market'" class="category-sidebar">
          <div class="side-head">商品分类</div>
          <button class="category-item" :class="{ active: !queryParams.parentCategoryId }" @click="selectParentCategory(null)">全部配料</button>
          <button v-for="item in level1Categories" :key="item.id" class="category-item" :class="{ active: queryParams.parentCategoryId === item.id }" @click="selectParentCategory(item.id)">{{ item.name }}</button>
          <div class="side-foot"><el-button type="success" plain icon="el-icon-setting" @click="handleCategoryManage">分类管理</el-button></div>
        </aside>
        <!-- 操作按钮 -->
        <el-card class="table-card" :class="{ 'market-content': viewMode === 'market' }" shadow="never">
          <div slot="header" class="clearfix action-toolbar">
            <div v-if="viewMode === 'market'" class="current-category">{{ currentCategoryName }} <small>共 {{ total }} 种配料（当前筛选）</small></div>
            <div class="toolbar-buttons">
              <el-button type="success" icon="el-icon-plus" @click="handleAdd">新增配料</el-button>
              <el-button v-if="viewMode === 'table'" icon="el-icon-collection-tag" @click="handleCategoryManage">分类管理</el-button>
              <el-button
                v-permission="['dishIngredientTag:list']"
                icon="el-icon-collection-tag"
                @click="handleTagManage"
              >标签管理</el-button>
              <el-button v-if="viewMode === 'table'" type="danger" icon="el-icon-delete" :disabled="multiple" @click="handleDelete">删除</el-button>
              <el-button type="warning" icon="el-icon-download" @click="handleDownload">导出</el-button>
            </div>
          </div>

          <div v-if="viewMode === 'market'">
            <div v-if="queryParams.parentCategoryId" class="category-chips">
              <button :class="{ active: !queryParams.categoryId }" @click="selectCategory(null)">全部</button>
              <button v-for="item in level2Categories" :key="item.id" :class="{ active: queryParams.categoryId === item.id }" @click="selectCategory(item.id)">{{ item.name }}</button>
            </div>
            <div v-loading="loading" class="ingredient-grid">
              <article v-for="item in ingredientList" :key="item.id" class="ingredient-card">
                <div class="card-top">
                  <span class="card-name">{{ item.name }}</span>
                  <el-switch v-model="item.enabled" active-color="#67c23a" :disabled="!!statusPending[item.id]" :aria-label="item.name + '启用状态'" @change="handleStatusChange(item)" />
                </div>
                <div class="card-path">{{ item.categoryPathName || getCategoryLabel(item.category) || '未分类' }}</div>
                <div class="card-meta"><span>单位：{{ item.unit || '—' }}</span><span>热量：{{ item.calories == null ? '—' : item.calories }} 卡/单位</span></div>
                <div v-if="item.tags && item.tags.length" class="card-tags"><el-tag v-for="tag in item.tags" :key="tag.id" size="small" effect="plain" class="ingredient-tag">{{ tag.name }}</el-tag></div>
                <div v-if="item.remark" class="card-remark">{{ item.remark }}</div>
                <div class="card-actions">
                  <el-button type="text" icon="el-icon-edit" @click="handleUpdate(item)">编辑</el-button>
                  <el-button type="text" icon="el-icon-delete" class="delete-button" @click="handleDelete(item)">删除</el-button>
                </div>
              </article>
              <div v-if="!loading && !ingredientList.length" class="empty-state">暂无符合条件的配料</div>
            </div>
          </div>
          <!-- 表格 -->
          <el-table v-if="viewMode === 'table'" v-loading="loading" :data="ingredientList" @selection-change="handleSelectionChange">
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
                  :disabled="!!statusPending[scope.row.id]"
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

      </div>
    </template>

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
      viewMode: 'market',
      listRequestId: 0,
      statusPending: {},
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
    /** 返回当前一级分类标题，未筛选时展示全部配料。 */
    currentCategoryName() {
      const category = this.level1Categories.find(item => item.id === this.queryParams.parentCategoryId)
      return category ? category.name : '全部配料'
    },
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
    /** 切换展示方式并清空表格勾选，保留查询和分页条件。 */
    setViewMode(mode) {
      this.viewMode = mode
      this.handleSelectionChange([])
    },
    /** 根据一级分类ID切换货架，清空二级筛选并查询首页。 */
    selectParentCategory(id) {
      this.queryParams.parentCategoryId = id
      this.handleParentCategoryChange(id)
      this.handleQuery()
    },
    /** 根据二级分类ID筛选配料，null 表示当前一级下全部配料。 */
    selectCategory(id) {
      this.queryParams.categoryId = id
      this.handleQuery()
    },
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
    /** 查询当前分页，仅接收最新请求，避免快速切换分类时旧结果覆盖。 */
    getList() {
      const requestId = ++this.listRequestId
      this.handleSelectionChange([])
      this.loading = true
      queryIngredients({ ...this.queryParams }).then(response => {
        if (requestId !== this.listRequestId) return
        this.ingredientList = response.content || []
        this.total = response.totalElements || 0
      }).catch(() => {
        if (requestId !== this.listRequestId) return
        this.ingredientList = []
        this.total = 0
      }).finally(() => {
        if (requestId === this.listRequestId) this.loading = false
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
    /** 保存配料启用状态，提交期间禁用开关，失败时恢复原状态。 */
    handleStatusChange(row) {
      this.$set(this.statusPending, row.id, true)
      editIngredient(row).then(() => {
        this.$message.success('更新成功')
      }).catch(() => {
        row.enabled = !row.enabled
      }).finally(() => {
        this.$delete(this.statusPending, row.id)
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
.ingredient-page { background: #f5f7fa; min-height: calc(100vh - 84px); }
.ingredient-header, .action-toolbar, .card-top { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.ingredient-header { background: #fff; padding: 16px 24px; margin-bottom: 16px; border-radius: 10px; }
.page-title, .current-category { font-size: 18px; font-weight: 600; }
.page-title small, .current-category small { font-size: 12px; color: #909399; font-weight: 400; margin-left: 10px; }
.view-tabs { display: flex; background: #f0f2f5; padding: 4px; border-radius: 8px; flex-shrink: 0; }
.view-tabs button { border: 0; background: transparent; padding: 7px 22px; border-radius: 6px; color: #606266; cursor: pointer; }
.view-tabs button.active { background: #fff; color: #67c23a; font-weight: 600; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.market-layout { display: flex; gap: 16px; align-items: flex-start; }
.category-sidebar { width: 208px; flex: none; background: #fff; border-radius: 10px; overflow: hidden; }
.side-head { padding: 14px 18px; color: #909399; font-size: 13px; font-weight: 600; border-bottom: 1px solid #f0f2f5; }
.category-item { display: block; width: 100%; border: 0; border-left: 4px solid transparent; padding: 13px 14px; text-align: left; color: #606266; background: #fff; cursor: pointer; }
.category-item:hover { background: #fafafa; }
.category-item.active { background: #f0f9eb; color: #67c23a; border-left-color: #67c23a; font-weight: 600; }
.side-foot { padding: 12px 14px; border-top: 1px solid #f0f2f5; }
.side-foot .el-button { width: 100%; border-style: dashed; }
.market-content { flex: 1; min-width: 0; border: 0; background: transparent; }
.market-content >>> .el-card__header { padding: 0 0 14px; border: 0; }
.market-content >>> .el-card__body { padding: 0; }
.action-toolbar { flex-wrap: wrap; }
.toolbar-buttons { display: flex; flex-wrap: wrap; gap: 8px; }
.toolbar-buttons .el-button { margin-left: 0; }
.category-chips { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 12px; margin-bottom: 6px; }
.category-chips button { flex: none; padding: 7px 18px; font-size: 13px; color: #606266; background: #fff; border: 1px solid #dcdfe6; border-radius: 18px; cursor: pointer; }
.category-chips button.active { background: #67c23a; color: #fff; border-color: #67c23a; }
.ingredient-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; min-height: 100px; }
.ingredient-card { background: #fff; border-radius: 10px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.05); transition: box-shadow .15s, transform .15s; }
.ingredient-card:hover { box-shadow: 0 6px 16px rgba(103,194,58,.12); transform: translateY(-2px); }
.card-name { font-size: 16px; font-weight: 600; overflow-wrap: anywhere; }
.card-top .el-switch { flex-shrink: 0; }
.card-path { display: inline-block; margin-top: 10px; font-size: 12px; color: #529b2e; background: #f0f9eb; padding: 3px 10px; border-radius: 4px; }
.card-meta { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 10px; font-size: 12px; color: #909399; }
.card-tags, .card-remark { margin-top: 10px; }
.card-remark { font-size: 12px; color: #909399; overflow-wrap: anywhere; }
.card-actions { margin-top: 14px; padding-top: 4px; border-top: 1px solid #f0f2f5; }
.delete-button { color: #f56c6c; }
.empty-state { grid-column: 1 / -1; padding: 48px 16px; text-align: center; background: #fff; border-radius: 10px; color: #909399; }
@media (max-width: 1200px) {
  .ingredient-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .category-sidebar { width: 170px; }
}
@media (max-width: 768px) {
  .ingredient-header { flex-wrap: wrap; padding: 16px; }
  .market-layout { flex-direction: column; }
  .category-sidebar, .market-content { width: 100%; }
  .ingredient-grid { grid-template-columns: minmax(0, 1fr); }
}

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
