<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-form ref="queryForm" :model="queryParams" :inline="true">
        <el-form-item label="标签名称" prop="name">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入标签名称"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div slot="header" class="clearfix">
        <el-button
          v-permission="['dishIngredientTag:add']"
          type="primary"
          icon="el-icon-plus"
          @click="handleAdd"
        >新增标签</el-button>
      </div>

      <el-table v-loading="loading" :data="tagList">
        <el-table-column label="标签名称" prop="name" align="center" />
        <el-table-column label="创建时间" prop="createTime" align="center" />
        <el-table-column label="更新时间" prop="updateTime" align="center" />
        <el-table-column label="操作" align="center" width="180">
          <template slot-scope="scope">
            <el-button
              v-permission="['dishIngredientTag:edit']"
              type="text"
              icon="el-icon-edit"
              @click="handleEdit(scope.row)"
            >编辑</el-button>
            <el-button
              v-permission="['dishIngredientTag:del']"
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
    </el-card>

    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="460px" @close="resetDialog">
      <el-form ref="tagForm" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="请输入标签名称" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="dialogVisible = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { addIngredientTag, deleteIngredientTag, editIngredientTag, queryIngredientTags } from '@/api/dishIngredientTag'
import Pagination from '@/components/Pagination'

export default {
  name: 'DishIngredientTag',
  components: { Pagination },
  data() {
    return {
      loading: false,
      total: 0,
      tagList: [],
      queryParams: { page: 0, size: 20, name: null },
      dialogVisible: false,
      dialogTitle: '',
      form: { id: null, name: '' },
      rules: {
        name: [{ required: true, message: '标签名称不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
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
    /** 重置筛选条件并回到第一页。 */
    resetQuery() {
      this.queryParams.name = null
      this.handleQuery()
    },
    /** 使用当前筛选条件重新查询。 */
    handleQuery() {
      this.queryParams.page = 0
      this.getList()
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
      this.dialogVisible = true
    },
    /** 打开标签改名表单。 */
    handleEdit(row) {
      this.dialogTitle = '编辑标签'
      this.form = { id: row.id, name: row.name }
      this.dialogVisible = true
    },
    /** 校验并保存标签名称。 */
    submitForm() {
      this.$refs.tagForm.validate(valid => {
        if (!valid) return
        const action = this.form.id ? editIngredientTag : addIngredientTag
        const payload = this.form.id ? { id: this.form.id, name: this.form.name } : { name: this.form.name }
        action(payload).then(() => {
          this.$message.success('保存成功')
          this.dialogVisible = false
          this.getList()
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
      }).catch(() => {})
    },
    /** 清空对话框字段。 */
    resetDialog() {
      this.form = { id: null, name: '' }
      if (this.$refs.tagForm) this.$refs.tagForm.resetFields()
    }
  }
}
</script>

<style scoped>
.search-card {
  margin-bottom: 15px;
}
</style>
