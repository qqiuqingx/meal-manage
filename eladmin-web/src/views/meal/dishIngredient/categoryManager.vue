<template>
  <div>
    <el-dialog
      title="分类管理"
      :visible.sync="dialogVisible"
      width="720px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="manager-tip">
        删除前会校验：一级分类下仍有二级分类、或二级分类下仍有关联配料时，后端会拒绝删除。
      </div>

      <div class="category-toolbar">
        <el-button
          v-permission="['admin', 'dishIngredient:add']"
          type="primary"
          size="small"
          icon="el-icon-plus"
          @click="handleAddLevelOne"
        >新增一级分类</el-button>
      </div>

      <el-table
        :data="categoryTree"
        border
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        empty-text="暂无分类数据"
      >
        <el-table-column prop="name" label="分类名称" min-width="240" />
        <el-table-column label="层级" width="120" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.level === 1 ? 'primary' : 'success'" size="small">
              {{ scope.row.level === 1 ? '一级分类' : '二级分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="100" align="center" />
        <el-table-column label="操作" width="240" align="center">
          <template slot-scope="scope">
            <el-button
              v-if="scope.row.level === 1"
              v-permission="['admin', 'dishIngredient:add']"
              type="text"
              icon="el-icon-plus"
              @click="handleAddLevelTwo(scope.row)"
            >新增二级</el-button>
            <el-button
              v-permission="['admin', 'dishIngredient:edit']"
              type="text"
              icon="el-icon-edit"
              @click="handleEdit(scope.row)"
            >编辑</el-button>
            <el-button
              v-permission="['admin', 'dishIngredient:del']"
              type="text"
              icon="el-icon-delete"
              style="color: #f56c6c;"
              :loading="deleteLoadingId === scope.row.id"
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog
      :title="formMode === 'create' ? (form.level === 1 ? '新增一级分类' : '新增二级分类') : '编辑配料分类'"
      :visible.sync="formVisible"
      width="460px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="categoryForm" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="formMode === 'create' && form.level === 2" label="上级分类">
          <el-input :value="parentCategoryName" disabled />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number
            v-model="form.sort"
            :controls="false"
            style="width: 100%"
            placeholder="留空时追加到末尾"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        <el-button :disabled="submitLoading" @click="formVisible = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { addCategory, delCategory, editCategory } from '@/api/dishIngredientCategory'

export default {
  name: 'CategoryManager',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    categoryTree: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      deleteLoadingId: null,
      formVisible: false,
      formMode: 'create',
      editingCategoryId: null,
      parentCategoryName: '',
      originalName: '',
      submitLoading: false,
      form: { name: '', level: 1, parentId: null, sort: null },
      rules: {
        name: [{
          validator: (rule, value, callback) => {
            const name = (value || '').trim()
            if (!name) return callback(new Error('分类名称不能为空'))
            if (name.length > 64) return callback(new Error('分类名称不能超过64个字符'))
            callback()
          },
          trigger: 'blur'
        }]
      }
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible
      },
      set(value) {
        this.$emit('update:visible', value)
      }
    }
  },
  methods: {
    /** 打开新增一级分类表单。 */
    handleAddLevelOne() {
      this.formMode = 'create'
      this.editingCategoryId = null
      this.parentCategoryName = ''
      this.originalName = ''
      this.form = { name: '', level: 1, parentId: null, sort: null }
      this.formVisible = true
    },
    /** 打开当前一级分类下的新增二级分类表单。 */
    handleAddLevelTwo(parent) {
      this.formMode = 'create'
      this.editingCategoryId = null
      this.parentCategoryName = parent.name
      this.originalName = ''
      this.form = { name: '', level: 2, parentId: parent.id, sort: null }
      this.formVisible = true
    },
    /** 打开指定分类的名称和排序编辑表单。 */
    handleEdit(row) {
      this.formMode = 'edit'
      this.editingCategoryId = row.id
      this.parentCategoryName = ''
      this.originalName = row.name
      this.form = { name: row.name, level: row.level, parentId: row.parentId, sort: row.sort }
      this.formVisible = true
    },
    /** 校验表单、确认改名影响并提交分类变更。 */
    submitForm() {
      return new Promise(resolve => this.$refs.categoryForm.validate(resolve)).then(valid => {
        if (!valid || this.submitLoading) return

        const name = this.form.name.trim()
        const confirmation = this.formMode === 'edit' && name !== this.originalName
          ? this.$confirm(
            '客户档案中已保存的过敏标签不会随分类改名自动更新，可能影响之后的排餐过滤。请先确认业务已知晓，是否继续？',
            '分类改名提示',
            { confirmButtonText: '继续修改', cancelButtonText: '取消', type: 'warning' }
          ).then(() => true).catch(() => false)
          : Promise.resolve(true)

        return confirmation.then(confirmed => {
          if (!confirmed) return
          this.submitLoading = true
          const request = this.formMode === 'create'
            ? addCategory({
              name,
              level: this.form.level,
              parentId: this.form.parentId,
              sort: this.form.sort
            })
            : editCategory(this.editingCategoryId, { name, sort: this.form.sort })
          return request.then(() => {
            this.$message.success(this.formMode === 'create' ? '新增成功' : '修改成功')
            this.formVisible = false
            this.$emit('refresh-categories')
          }).catch(() => {
            // 请求拦截器负责展示接口错误提示。
          }).finally(() => {
            this.submitLoading = false
          })
        })
      })
    },
    /** 清空表单字段和校验状态。 */
    resetForm() {
      this.form = { name: '', level: 1, parentId: null, sort: null }
      if (this.$refs.categoryForm) this.$refs.categoryForm.resetFields()
    },
    handleDelete(row) {
      return this.$confirm(`是否确认删除分类“${row.name}”？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async() => {
        this.deleteLoadingId = row.id
        await delCategory(row.id)
        this.$message.success('删除成功')
        this.$emit('refresh-categories')
      }).catch(() => {
      }).finally(() => {
        this.deleteLoadingId = null
      })
    }
  }
}
</script>

<style scoped>
.manager-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  font-size: 13px;
  color: #606266;
  background: #f4f8ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
}
.category-toolbar {
  margin-bottom: 12px;
  text-align: right;
}
</style>
