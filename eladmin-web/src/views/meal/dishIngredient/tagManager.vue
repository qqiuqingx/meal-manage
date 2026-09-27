<template>
  <el-dialog
    title="标签管理"
    :visible.sync="dialogVisible"
    width="640px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="manager-tip">
      删除前会校验：标签正在被配料使用时，后端会拒绝删除。重命名会同步更新所有配料卡片上的标签显示。
    </div>

    <div class="tag-toolbar">
      <el-button
        v-permission="['admin', 'dishIngredientTag:add']"
        type="primary"
        icon="el-icon-plus"
        @click="handleAdd"
      >新增标签</el-button>
    </div>

    <el-table v-loading="loading" :data="tagList" border>
      <el-table-column label="标签名称" prop="name" min-width="200" />
      <el-table-column label="创建时间" prop="createTime" align="center" width="170" />
      <el-table-column label="更新时间" prop="updateTime" align="center" width="170" />
      <el-table-column label="操作" align="center" width="160">
        <template slot-scope="scope">
          <el-button
            v-permission="['admin', 'dishIngredientTag:edit']"
            type="text"
            icon="el-icon-edit"
            @click="handleEdit(scope.row)"
          >编辑</el-button>
          <el-button
            v-permission="['admin', 'dishIngredientTag:del']"
            type="text"
            icon="el-icon-delete"
            style="color: #f56c6c;"
            @click="handleDelete(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page="queryParams.page + 1"
      :limit.sync="queryParams.size"
      @pagination="handlePagination"
    />

    <el-dialog
      :title="dialogTitle"
      :visible.sync="formVisible"
      width="460px"
      append-to-body
      @close="resetForm"
    >
      <el-form ref="tagForm" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="请输入标签名称" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="formVisible = false">取 消</el-button>
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script>
import { addIngredientTag, deleteIngredientTag, editIngredientTag, queryIngredientTags } from '@/api/dishIngredientTag'
import Pagination from '@/components/Pagination'

export default {
  name: 'TagManager',
  components: { Pagination },
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      loading: false,
      total: 0,
      tagList: [],
      queryParams: { page: 0, size: 20, name: null },
      formVisible: false,
      dialogTitle: '',
      form: { id: null, name: '' },
      rules: {
        name: [{ required: true, message: '标签名称不能为空', trigger: 'blur' }]
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
  watch: {
    visible(val) {
      if (val) {
        this.queryParams.page = 0
        this.getList()
      }
    }
  },
  methods: {
    /** 查询当前标签页。 */
    getList() {
      this.loading = true
      queryIngredientTags(this.queryParams).then(response => {
        this.tagList = response.content || []
        this.total = response.totalElements || 0
      }).catch(() => {
        this.tagList = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    /** 更新分页参数。 */
    handlePagination({ page, limit }) {
      this.queryParams.page = page - 1
      this.queryParams.size = limit
      this.getList()
    },
    /** 打开新增标签表单。 */
    handleAdd() {
      this.dialogTitle = '新增标签'
      this.form = { id: null, name: '' }
      this.formVisible = true
    },
    /** 打开标签改名表单。 */
    handleEdit(row) {
      this.dialogTitle = '编辑标签'
      this.form = { id: row.id, name: row.name }
      this.formVisible = true
    },
    /** 校验并保存标签名称。 */
    submitForm() {
      this.$refs.tagForm.validate(valid => {
        if (!valid) return
        const action = this.form.id ? editIngredientTag : addIngredientTag
        const payload = this.form.id ? { id: this.form.id, name: this.form.name } : { name: this.form.name }
        action(payload).then(() => {
          this.$message.success('保存成功')
          this.formVisible = false
          this.getList()
          this.$emit('refresh-tags')
        })
      })
    },
    /** 请求删除标签；服务端拒绝仍在使用的标签。 */
    handleDelete(row) {
      this.$confirm(`是否删除标签“${row.name}”？正在被配料使用的标签无法删除。`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => deleteIngredientTag(row.id)).then(() => {
        this.$message.success('删除成功')
        this.getList()
        this.$emit('refresh-tags')
      }).catch(() => {})
    },
    /** 清空对话框字段。 */
    resetForm() {
      this.form = { id: null, name: '' }
      if (this.$refs.tagForm) this.$refs.tagForm.resetFields()
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
.tag-toolbar {
  margin-bottom: 12px;
  text-align: right;
}
</style>
