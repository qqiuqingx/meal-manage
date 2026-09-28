<template>
  <section class="dish-tag-editor">
    <div class="dish-tag-editor__selected">
      <el-tag
        v-for="tag in selectedTagOptions"
        :key="tag.id"
        closable
        size="small"
        effect="plain"
        @close="removeTag(tag.id)"
      >
        {{ tag.name }}
      </el-tag>
      <span v-if="selectedTagOptions.length === 0" class="dish-tag-editor__empty">尚未选择标签</span>
    </div>

    <el-button size="mini" type="text" @click="toggleSelector">
      {{ selectorVisible ? '收起标签选择' : '选择标签' }}
    </el-button>

    <el-collapse-transition>
      <div v-if="selectorVisible" class="dish-tag-editor__selector">
        <el-input
          v-model="searchText"
          clearable
          size="small"
          placeholder="搜索菜品标签"
          prefix-icon="el-icon-search"
          @input="searchTags"
          @clear="searchTags('')"
        />
        <div v-loading="loading" class="dish-tag-editor__options">
          <div v-for="tag in tagOptions" :key="tag.id" class="dish-tag-editor__option">
            <div class="dish-tag-editor__option-main">
              <el-checkbox :value="isSelected(tag.id)" @change="toggleTag(tag.id, $event)">
                {{ tag.name }}
              </el-checkbox>
              <div v-if="renamingTagId !== tag.id" class="dish-tag-editor__actions">
                <el-button v-if="canEditTag" size="mini" type="text" :disabled="isMutating" @click="startRename(tag)">重命名</el-button>
                <el-button
                  v-if="canDeleteTag"
                  size="mini"
                  type="text"
                  class="dish-tag-editor__delete"
                  :loading="deletingTagId === tag.id"
                  :disabled="isMutating"
                  @click="deleteTag(tag)"
                >删除</el-button>
              </div>
            </div>
            <div v-if="renamingTagId === tag.id" class="dish-tag-editor__rename-row">
              <el-input
                v-model="renameValue"
                class="dish-tag-editor__rename-input"
                size="mini"
                maxlength="128"
                :disabled="savingRename"
                @keyup.enter.native="submitRename(tag)"
              />
              <span class="dish-tag-editor__count">{{ renameLength }}/64</span>
              <el-button size="mini" type="text" :loading="savingRename" :disabled="savingRename || !canSubmitRename" @click="submitRename(tag)">保存</el-button>
              <el-button size="mini" type="text" :disabled="savingRename" @click="cancelRename">取消</el-button>
            </div>
          </div>
          <el-empty v-if="!loading && tagOptions.length === 0" description="暂无匹配标签" :image-size="48" />
        </div>
        <el-button
          v-if="canCreateFromSearch"
          class="dish-tag-editor__create"
          type="primary"
          size="mini"
          :loading="creatingTag"
          :disabled="isMutating"
          @click="createTag"
        >
          新增标签“{{ normalizedSearchText }}”
        </el-button>
        <el-button
          v-if="hasMore"
          class="dish-tag-editor__more"
          size="mini"
          :loading="loading"
          :disabled="loading"
          @click="loadMoreTags"
        >
          加载更多
        </el-button>
      </div>
    </el-collapse-transition>
    <p class="dish-tag-editor__help">标签字典操作立即生效；标签选择在保存菜品后生效。</p>
  </section>
</template>

<script>
import { addDishTag, deleteDishTag, editDishTag, queryDishTags } from '@/api/dishTag'

export default {
  name: 'DishTagEditor',
  props: {
    value: {
      type: Array,
      default: () => []
    },
    selectedTags: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      selectorVisible: false,
      searchText: '',
      tagOptions: [],
      tagById: {},
      page: 0,
      pageSize: 20,
      totalElements: 0,
      loading: false,
      searchCompleted: false,
      searchSequence: 0,
      creatingTag: false,
      renamingTagId: null,
      renameValue: '',
      savingRename: false,
      deletingTagId: null
    }
  },
  computed: {
    selectedTagOptions() {
      return (this.value || []).map(id => this.tagById[id] || { id, name: `标签${id}` })
    },
    hasMore() {
      return this.tagOptions.length < this.totalElements
    },
    normalizedSearchText() {
      return (this.searchText || '').trim()
    },
    normalizedRenameValue() {
      return (this.renameValue || '').trim()
    },
    renameLength() {
      return Array.from(this.normalizedRenameValue).length
    },
    canSubmitRename() {
      return this.normalizedRenameValue.length > 0 && Array.from(this.normalizedRenameValue).length <= 64
    },
    canCreateTag() {
      return this.checkPer(['admin', 'dishTag:add'])
    },
    canEditTag() {
      return this.checkPer(['admin', 'dishTag:edit'])
    },
    canDeleteTag() {
      return this.checkPer(['admin', 'dishTag:del'])
    },
    canCreateFromSearch() {
      return this.canCreateTag && this.normalizedSearchText.length > 0 &&
        Array.from(this.normalizedSearchText).length <= 64 && this.searchCompleted &&
        !this.loading && this.tagOptions.length === 0
    },
    isMutating() {
      return this.creatingTag || this.savingRename || this.deletingTagId !== null
    }
  },
  watch: {
    selectedTags: {
      immediate: true,
      handler(tags) {
        const selected = tags || []
        selected.forEach(tag => this.$set(this.tagById, tag.id, tag))
      }
    }
  },
  methods: {
    /** 打开标签选择面板时加载第一页，关闭时保留已选关系。 */
    toggleSelector() {
      this.selectorVisible = !this.selectorVisible
      if (this.selectorVisible && this.tagOptions.length === 0) {
        this.loadTags(true)
      }
    },
    /** 按名称重新查询标签，并忽略较早搜索返回的结果。 */
    searchTags(query) {
      this.searchText = query || ''
      this.loadTags(true)
    },
    /** 加载标签分页，可用于浏览未出现在当前页的字典项。 */
    loadMoreTags() {
      if (this.loading || !this.hasMore) return
      this.page += 1
      this.loadTags(false)
    },
    /** 请求标签分页并把标签名称保存在本地，供跨页已选项显示。 */
    loadTags(reset) {
      if (reset) {
        this.page = 0
        this.tagOptions = []
        this.totalElements = 0
        this.searchCompleted = false
      }
      const requestedPage = this.page
      const sequence = ++this.searchSequence
      this.loading = true
      queryDishTags({ name: this.searchText, page: this.page, size: this.pageSize })
        .then(response => {
          if (sequence !== this.searchSequence) return
          const records = response.content || []
          records.forEach(tag => this.$set(this.tagById, tag.id, tag))
          const merged = reset ? [] : this.tagOptions.slice()
          const existingIds = new Set(merged.map(tag => tag.id))
          records.forEach(tag => {
            if (!existingIds.has(tag.id)) merged.push(tag)
          })
          this.tagOptions = merged
          this.totalElements = response.totalElements || 0
          this.searchCompleted = true
        })
        .catch(() => {
          if (sequence === this.searchSequence) {
            if (reset) this.totalElements = 0
            else this.page = Math.max(0, requestedPage - 1)
            this.searchCompleted = false
            this.$message.error('菜品标签加载失败')
          }
        })
        .finally(() => {
          if (sequence === this.searchSequence) this.loading = false
        })
    },
    /** 通知菜品表单标签ID及其显示名称的最新选择。 */
    updateSelected(ids) {
      const uniqueIds = Array.from(new Set(ids || []))
      this.$emit('input', uniqueIds)
      this.$emit('tags-change', uniqueIds.map(id => this.tagById[id]).filter(Boolean))
    },
    /** 返回一个标签是否属于当前表单的待保存选择。 */
    isSelected(id) {
      return (this.value || []).includes(id)
    },
    /** 单独切换当前标签，保留其它搜索页已经选中的ID。 */
    toggleTag(id, checked) {
      const selected = (this.value || []).filter(tagId => tagId !== id)
      if (checked) selected.push(id)
      this.updateSelected(selected)
    },
    /** 仅移除菜品与标签的绑定，不删除标签字典。 */
    removeTag(id) {
      this.updateSelected((this.value || []).filter(tagId => tagId !== id))
    },
    /** 无搜索结果时即时创建标签并加入当前菜品的待保存选择。 */
    createTag() {
      const name = this.normalizedSearchText
      if (!this.canCreateFromSearch || !this.canCreateTag || !name || this.isMutating) return
      this.creatingTag = true
      addDishTag({ name }).then(tag => {
        this.$set(this.tagById, tag.id, tag)
        this.tagOptions = [tag].concat(this.tagOptions)
        this.totalElements += 1
        this.updateSelected((this.value || []).concat(tag.id))
        this.$message.success('标签已创建并加入当前菜品，保存菜品后生效')
      }).catch(error => {
        this.$message.error(this.errorMessage(error, '新增标签失败'))
      }).finally(() => {
        this.creatingTag = false
      })
    },
    /** 在标签列表中进入内联重命名。 */
    startRename(tag) {
      if (!this.canEditTag || this.isMutating) return
      this.renamingTagId = tag.id
      this.renameValue = tag.name
    },
    /** 取消内联重命名并清空编辑内容。 */
    cancelRename() {
      if (this.savingRename) return
      this.renamingTagId = null
      this.renameValue = ''
    },
    /** 确认并即时保存标签名称，更新当前弹窗的所有本地显示。 */
    submitRename(tag) {
      const name = this.normalizedRenameValue
      if (!this.canEditTag || !this.canSubmitRename || this.isMutating) return
      this.savingRename = true
      this.$confirm('重命名会影响所有使用该标签的菜品，确定保存吗？', '确认重命名', {
        type: 'warning',
        confirmButtonText: '确认',
        cancelButtonText: '取消'
      }).then(() => editDishTag({ id: tag.id, name })).then(() => {
        const renamed = Object.assign({}, tag, { name })
        this.$set(this.tagById, tag.id, renamed)
        this.tagOptions = this.tagOptions.map(item => item.id === tag.id ? renamed : item)
        this.updateSelected(this.value || [])
        this.renamingTagId = null
        this.renameValue = ''
        this.$message.success('标签名称已更新')
      }).catch(error => {
        if (error !== 'cancel' && error !== 'close') {
          this.$message.error(this.errorMessage(error, '修改标签失败'))
        }
      }).finally(() => {
        this.savingRename = false
      })
    },
    /** 确认删除未被保存菜品引用的标签，并清理当前表单的本地选择。 */
    deleteTag(tag) {
      if (!this.canDeleteTag || this.isMutating) return
      this.deletingTagId = tag.id
      this.$confirm('仅未被任何已保存菜品引用的标签可以删除，确定删除吗？', '确认删除', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }).then(() => deleteDishTag(tag.id)).then(() => {
        this.tagOptions = this.tagOptions.filter(item => item.id !== tag.id)
        this.$delete(this.tagById, tag.id)
        this.updateSelected((this.value || []).filter(id => id !== tag.id))
        if (this.renamingTagId === tag.id) this.cancelRename()
        this.loadTags(true)
        this.$message.success('标签已删除')
      }).catch(error => {
        if (error !== 'cancel' && error !== 'close') {
          this.$message.error(this.errorMessage(error, '删除标签失败'))
        }
      }).finally(() => {
        this.deletingTagId = null
      })
    },
    /** 提取后端业务错误，保留可操作的重复名称或引用提示。 */
    errorMessage(error, fallback) {
      return error && error.response && error.response.data && error.response.data.message
        ? error.response.data.message
        : (error && error.message ? error.message : fallback)
    },
    /** 清空弹窗关闭后的搜索与分页状态。 */
    resetState() {
      this.selectorVisible = false
      this.searchText = ''
      this.tagOptions = []
      this.tagById = {}
      this.page = 0
      this.totalElements = 0
      this.searchCompleted = false
      this.creatingTag = false
      this.renamingTagId = null
      this.renameValue = ''
      this.savingRename = false
      this.deletingTagId = null
      this.searchSequence += 1
      this.loading = false
    }
  }
}
</script>

<style scoped>
.dish-tag-editor {
  padding: 12px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.dish-tag-editor__selected {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
}

.dish-tag-editor__empty,
.dish-tag-editor__help {
  color: #909399;
  font-size: 12px;
}

.dish-tag-editor__selector {
  margin-top: 8px;
}

.dish-tag-editor__options {
  max-height: 180px;
  overflow-y: auto;
  padding: 8px 4px;
}

.dish-tag-editor__option {
  padding: 6px 0;
  border-bottom: 1px solid #f2f3f5;
}

.dish-tag-editor__option-main,
.dish-tag-editor__rename-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.dish-tag-editor__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dish-tag-editor__rename-input {
  flex: 1;
  min-width: 0;
}

.dish-tag-editor__count {
  color: #909399;
  font-size: 11px;
  white-space: nowrap;
}

.dish-tag-editor__delete {
  color: #f56c6c;
}

.dish-tag-editor__create {
  width: 100%;
}

.dish-tag-editor__more {
  width: 100%;
}

.dish-tag-editor__help {
  margin: 6px 0 0;
}
</style>
