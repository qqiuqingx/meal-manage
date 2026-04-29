<template>
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
      <el-table-column label="操作" width="120" align="center">
        <template slot-scope="scope">
          <el-button
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
</template>

<script>
import { delCategory } from '@/api/dishIngredientCategory'

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
      deleteLoadingId: null
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
    handleDelete(row) {
      this.$confirm(`是否确认删除分类“${row.name}”？`, '提示', {
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
</style>
