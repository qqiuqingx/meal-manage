<template>
  <div class="app-container ingredient-page">
    <header class="ingredient-header">
      <div class="page-title">配菜管理 <small>配料字典 · 分类分级</small></div>
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

    <div class="market-layout">
      <aside class="category-sidebar">
        <div class="side-head">商品分类</div>
        <button class="category-item" :class="{ active: !queryParams.parentCategoryId }" @click="selectParentCategory(null)">全部配料</button>
        <button v-for="item in level1Categories" :key="item.id" class="category-item" :class="{ active: queryParams.parentCategoryId === item.id }" @click="selectParentCategory(item.id)">{{ item.name }}</button>
        <div class="side-foot"><el-button type="success" plain icon="el-icon-setting" @click="handleCategoryManage">分类管理</el-button></div>
      </aside>
      <!-- 操作按钮 -->
      <el-card class="table-card market-content" shadow="never">
        <div slot="header" class="clearfix action-toolbar">
          <div class="current-category">{{ currentCategoryName }} <small>共 {{ total }} 种配料（当前筛选）</small></div>
          <div class="toolbar-buttons">
            <el-button type="success" icon="el-icon-plus" @click="handleAdd">新增配料</el-button>
            <el-button
              v-permission="['admin', 'dishIngredientTag:add', 'dishIngredientTag:edit', 'dishIngredientTag:del']"
              icon="el-icon-collection-tag"
              @click="handleTagManage"
            >标签管理</el-button>
            <el-button type="warning" icon="el-icon-download" @click="handleDownload">导出</el-button>
          </div>
        </div>

        <div>
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
              <div class="card-tags">
                <el-tag v-for="tag in item.tags" :key="tag.id" size="small" effect="plain" class="ingredient-tag">
                  {{ tag.name }}
                  <button
                    v-if="canEditIngredient"
                    type="button"
                    class="ingredient-tag-remove"
                    :disabled="!!tagRemovalPending[item.id]"
                    :aria-label="`从${item.name}移除标签${tag.name}`"
                    @click.stop="removeCardTag(item, tag)"
                  >×</button>
                </el-tag>
                <el-popover
                  v-model="tagPopoverVisible[item.id]"
                  placement="bottom-start"
                  trigger="click"
                  width="240"
                  @show="openCardTags(item)"
                >
                  <el-input v-model="cardTagSearchText" size="mini" prefix-icon="el-icon-search" :placeholder="canCreateTag ? '搜索或输入新标签' : '搜索标签'" :disabled="savingTags" clearable @keyup.enter.native="saveCardTags" />
                  <div class="tag-pop-list">
                    <el-checkbox-group v-model="draftTagIds">
                      <el-checkbox v-for="opt in filteredTagOptions" :key="opt.id" :label="opt.id" class="tag-pop-checkbox">{{ opt.name }}</el-checkbox>
                    </el-checkbox-group>
                    <div v-if="!filteredTagOptions.length && !pendingCardTagName" class="tag-pop-empty">{{ tagOptionsLoading ? '加载中…' : '暂无匹配标签' }}</div>
                  </div>
                  <div v-if="pendingCardTagName" class="tag-pop-empty">保存时创建「{{ pendingCardTagName }}」并绑定</div>
                  <div class="tag-pop-footer">
                    <el-button size="mini" @click="tagPopoverVisible[item.id] = false">取消</el-button>
                    <el-button size="mini" type="primary" :loading="savingTags" :disabled="tagOptionsLoading || !tagOptionsLoaded || !!tagRemovalPending[item.id]" @click="saveCardTags">保存</el-button>
                  </div>
                  <el-button slot="reference" :disabled="savingTags" class="tag-add-btn" icon="el-icon-plus" circle size="mini" />
                </el-popover>
              </div>
              <div v-if="item.remark" class="card-remark">{{ item.remark }}</div>
              <div class="card-actions">
                <el-button type="text" icon="el-icon-edit" @click="handleUpdate(item)">编辑</el-button>
                <el-button type="text" icon="el-icon-delete" class="delete-button" @click="handleDelete(item)">删除</el-button>
              </div>
            </article>
            <div v-if="!loading && !ingredientList.length" class="empty-state">暂无符合条件的配料</div>
          </div>
        </div>

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

    <!-- 新增/编辑弹窗 -->
    <ingredient-form ref="ingredientForm" :category-tree="categoryTree" @refresh="getList" />

    <category-manager
      :visible.sync="categoryManagerVisible"
      :category-tree="categoryTree"
      @refresh-categories="handleCategoriesChanged"
    />

    <tag-manager
      :visible.sync="tagManagerVisible"
      @refresh-tags="handleTagsChanged"
    />
  </div>
</template>

<script>
import { queryIngredients, editIngredient, delIngredients, downloadIngredients } from '@/api/dishIngredient'
import { queryCategoryTree } from '@/api/dishIngredientCategory'
import { queryIngredientTags, addIngredientTag } from '@/api/dishIngredientTag'
import Pagination from '@/components/Pagination'
import IngredientForm from './form'
import CategoryManager from './categoryManager'
import TagManager from './tagManager'

export default {
  name: 'DishIngredient',
  components: {
    Pagination,
    IngredientForm,
    CategoryManager,
    TagManager
  },
  data() {
    return {
      listRequestId: 0,
      statusPending: {},
      tagRemovalPending: {},
      loading: true,
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
      tagManagerVisible: false,
      tagPopoverVisible: {},
      tagOptionsAll: [],
      tagOptionsLoaded: false,
      tagOptionsLoading: false,
      activeCard: null,
      draftTagIds: [],
      cardTagSearchText: '',
      savingTags: false,
      categoryMap: {
        MEAT: { label: '肉类' },
        VEGETABLE: { label: '蔬菜' },
        SEAFOOD: { label: '海鲜' },
        TOFU: { label: '豆制品' },
        SPICE: { label: '调料' },
        OTHER: { label: '其他' }
      }
    }
  },
  computed: {
    /** 返回当前一级分类标题，未筛选时展示全部配料。 */
    currentCategoryName() {
      const category = this.level1Categories.find(item => item.id === this.queryParams.parentCategoryId)
      return category ? category.name : '全部配料'
    },
    /** 卡片标签气泡内按关键字过滤后的可选标签。 */
    filteredTagOptions() {
      const keyword = (this.cardTagSearchText || '').trim().toLowerCase()
      if (!keyword) return this.tagOptionsAll
      return this.tagOptionsAll.filter(option => (option.name || '').toLowerCase().indexOf(keyword) >= 0)
    },
    /** 搜索无匹配且已加载全部选项时，返回保存时应创建的名称。 */
    pendingCardTagName() {
      return this.canCreateTag && this.tagOptionsLoaded && !this.tagOptionsLoading && !this.filteredTagOptions.length
        ? (this.cardTagSearchText || '').trim() : ''
    },
    /** 是否具备通过搜索创建标签的权限。 */
    canCreateTag() {
      return this.checkPer(['admin', 'dishIngredientTag:add'])
    },
    /** 是否可以修改配料与标签的关联。 */
    canEditIngredient() {
      return this.checkPer(['admin', 'dishIngredient:edit'])
    }
  },
  created() {
    this.refreshCategories()
    this.getList()
  },
  methods: {
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
    /** 刷新分类树并同步当前筛选，返回筛选条件是否已失效。 */
    async refreshCategories() {
      try {
        const tree = await queryCategoryTree()
        this.categoryTree = tree || []
        this.level1Categories = this.categoryTree
        return this.syncQueryCategoryState(this.categoryTree)
      } catch (error) {
        console.error('加载分类失败', error)
        return false
      }
    },
    /** 分类新增、改名或删除后刷新筛选树和配料当前页，更新分类路径名称。 */
    async handleCategoriesChanged() {
      const filtersChanged = await this.refreshCategories()
      if (filtersChanged) this.queryParams.page = 0
      this.getList()
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
      this.loading = true
      queryIngredients({ ...this.queryParams }).then(response => {
        if (requestId !== this.listRequestId) return
        this.ingredientList = response.content || []
        this.total = response.totalElements || 0
        this.initTagPopoverState()
      }).catch(() => {
        if (requestId !== this.listRequestId) return
        this.ingredientList = []
        this.total = 0
      }).finally(() => {
        if (requestId === this.listRequestId) this.loading = false
      })
    },
    /** 为当前页配料初始化标签气泡可见性状态。 */
    initTagPopoverState() {
      this.tagPopoverVisible = {}
      this.ingredientList.forEach(item => {
        this.$set(this.tagPopoverVisible, item.id, false)
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
    /** 打开新增弹窗，并用当前分类筛选值预填一级和二级分类。 */
    handleAdd() {
      this.$refs.ingredientForm.handleAdd(this.queryParams.parentCategoryId, this.queryParams.categoryId)
    },
    handleCategoryManage() {
      this.categoryManagerVisible = true
    },
    /** 打开页内标签管理弹窗。 */
    handleTagManage() {
      this.tagManagerVisible = true
    },
    /** 标签增/改/删后刷新配料列表与筛选选项，并使卡片气泡下次重新拉取。 */
    handleTagsChanged() {
      this.getList()
      this.tagSearchLoaded = false
      this.tagOptionsLoaded = false
      this.tagOptionsAll = []
    },
    /** 打开某张卡片的标签气泡，初始化待编辑集合与选项。 */
    openCardTags(item) {
      Object.keys(this.tagPopoverVisible).forEach(id => {
        if (Number(id) !== item.id) this.$set(this.tagPopoverVisible, id, false)
      })
      this.activeCard = item
      this.draftTagIds = (item.tagIds || []).slice()
      this.cardTagSearchText = ''
      this.ensureTagOptions()
    },
    /** 首次展开时拉取全部标签选项，并并入当前配料已有标签。 */
    async ensureTagOptions() {
      const existingTags = (this.activeCard && this.activeCard.tags) || []
      this.tagOptionsAll = this.mergeTagOptions(this.tagOptionsAll, existingTags)
      if (this.tagOptionsLoaded || this.tagOptionsLoading) return
      this.tagOptionsLoading = true
      try {
        let page = 0
        let loaded = 0
        let total = 0
        do {
          const response = await queryIngredientTags({ page, size: 200 })
          const content = response.content || []
          this.tagOptionsAll = this.mergeTagOptions(this.tagOptionsAll, content)
          loaded += content.length
          total = response.totalElements || 0
          page += 1
          if (!content.length) break
        } while (loaded < total)
        this.tagOptionsLoaded = true
      } catch (error) {
        this.tagOptionsLoaded = false
      } finally {
        this.tagOptionsLoading = false
      }
    },
    /** 按名称创建标签并加入指定草稿集合，返回新标签ID的 Promise。 */
    createTagAndAdd(name, draftTagIds = this.draftTagIds) {
      return addIngredientTag({ name }).then(tag => {
        const created = { id: tag.id, name: tag.name || name }
        this.tagOptionsAll = this.mergeTagOptions(this.tagOptionsAll, [created])
        if (!draftTagIds.includes(created.id)) {
          draftTagIds.push(created.id)
        }
        return created.id
      })
    },
    /** 保存卡片标签；若有待创建的新标签名则先创建再绑定，成功后局部更新卡片并同步筛选选项。 */
    saveCardTags() {
      if (!this.activeCard || this.savingTags || this.tagRemovalPending[this.activeCard.id] || this.tagOptionsLoading || !this.tagOptionsLoaded) return
      const card = this.activeCard
      const tagIds = this.draftTagIds.slice()
      this.savingTags = true
      const pendingName = this.pendingCardTagName
      const createPromise = pendingName ? this.createTagAndAdd(pendingName, tagIds) : Promise.resolve()
      createPromise.then(() => {
        if (this.activeCard === card) {
          this.cardTagSearchText = ''
          this.draftTagIds = tagIds.slice()
        }
        return editIngredient({ id: card.id, name: card.name, tagIds })
      }).then(() => {
        const tagMap = new Map(this.tagOptionsAll.map(option => [option.id, option]))
        const nextTags = tagIds.map(id => tagMap.get(id)).filter(Boolean)
        this.$set(card, 'tags', nextTags)
        this.$set(card, 'tagIds', tagIds)
        this.$set(this.tagPopoverVisible, card.id, false)
        this.tagSearchLoaded = false
        this.$message.success('标签已更新')
      }).catch(() => {
      }).finally(() => {
        this.savingTags = false
      })
    },
    /** 只移除当前配料与指定标签的关联，保留供其他配料使用的标签字典。 */
    removeCardTag(card, tag) {
      if (!this.canEditIngredient || this.tagRemovalPending[card.id] || (this.savingTags && this.activeCard === card)) return
      const tagIds = (card.tagIds || []).filter(id => id !== tag.id)
      this.$set(this.tagRemovalPending, card.id, true)
      editIngredient({ id: card.id, name: card.name, tagIds }).then(() => {
        this.$set(card, 'tagIds', tagIds)
        this.$set(card, 'tags', (card.tags || []).filter(item => item.id !== tag.id))
        if (this.activeCard === card) {
          this.draftTagIds = this.draftTagIds.filter(id => id !== tag.id)
        }
        this.getList()
        this.$message.success('标签已移除')
      }).catch(() => {
      }).finally(() => {
        this.$delete(this.tagRemovalPending, card.id)
      })
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
      const ids = [row.id]
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
.card-tags { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
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
.ingredient-tag-remove {
  margin-left: 4px;
  padding: 0 2px;
  border: 0;
  border-radius: 50%;
  color: inherit;
  background: transparent;
  cursor: pointer;
  opacity: 0;
}
.ingredient-tag:hover .ingredient-tag-remove,
.ingredient-tag:focus-within .ingredient-tag-remove {
  opacity: 1;
}
.ingredient-tag-remove:disabled { cursor: wait; }
.tag-add-btn.el-button {
  width: 22px;
  height: 22px;
  padding: 0;
  margin: 0 0 0 2px;
  border: 1px dashed #67c23a;
  color: #67c23a;
  background: #fff;
  border-radius: 50%;
}
.tag-add-btn.el-button:hover {
  background: #f0f9eb;
}
.tag-add-btn.el-button i {
  font-size: 12px;
}
.tag-pop-list {
  max-height: 200px;
  overflow-y: auto;
  margin-top: 8px;
}
.tag-pop-checkbox {
  display: block;
  margin: 4px 0;
}
.tag-pop-checkbox >>> .el-checkbox__label {
  color: #606266;
}
.tag-pop-empty {
  padding: 12px 0;
  text-align: center;
  color: #909399;
  font-size: 12px;
}
.tag-pop-footer {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 10px;
}
</style>
