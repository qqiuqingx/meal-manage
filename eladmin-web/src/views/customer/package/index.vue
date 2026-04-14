<template>
  <div class="app-container">
    <div class="head-container">
      <el-row type="flex" justify="space-between" align="middle" :gutter="12">
        <el-col :span="16">
          <div class="page-title">套餐管理</div>
          <div class="page-desc">按父套餐分组维护子套餐及配餐规则，展开行后可直接新增、编辑子套餐。</div>
        </el-col>
        <el-col :span="8" style="text-align: right;">
          <el-button class="filter-item" type="primary" icon="el-icon-plus" @click="handleAddParent">新增套餐</el-button>
        </el-col>
      </el-row>
    </div>

    <el-table
      v-loading="loading"
      :data="treeData"
      border
      row-key="id"
      style="width: 100%"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="sub-table-wrap">
            <div class="sub-table-header">
              <span class="sub-table-title">子套餐列表</span>
              <el-button type="primary" size="mini" icon="el-icon-plus" plain @click="handleAddSub(row)">新增子套餐</el-button>
            </div>
            <el-table :data="row.subPackages || []" border size="small" empty-text="暂无子套餐">
              <el-table-column prop="subPackageName" label="子套餐名称" min-width="150" />
              <el-table-column prop="meatCount" label="荤菜数" width="90" align="center" />
              <el-table-column prop="vegCount" label="素菜数" width="90" align="center" />
              <el-table-column prop="includeSoup" label="含汤" width="90" align="center">
                <template #default="{ row: subRow }">
                  <el-tag :type="subRow.includeSoup === 1 ? 'success' : 'info'" size="small">
                    {{ subRow.includeSoup === 1 ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="includeRice" label="含米饭" width="90" align="center">
                <template #default="{ row: subRow }">
                  <el-tag :type="subRow.includeRice === 1 ? 'success' : 'info'" size="small">
                    {{ subRow.includeRice === 1 ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="90" align="center">
                <template #default="{ row: subRow }">
                  <el-tag :type="subRow.status === 1 ? 'success' : 'danger'" size="small">
                    {{ subRow.status === 1 ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
              <el-table-column label="操作" width="200" align="center">
                <template #default="{ row: subRow }">
                  <el-button type="text" size="small" @click="handleEditSub(row, subRow)">编辑</el-button>
                  <el-button type="text" size="small" @click="handleDeleteSub(row, subRow)">删除</el-button>
                  <el-button type="text" size="small" @click="handleSubStatusChange(row, subRow)">
                    {{ subRow.status === 1 ? '停用' : '启用' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="packageName" label="套餐名称" min-width="150" />
      <el-table-column prop="poolPrefix" label="编号池前缀" width="110" align="center" />
      <el-table-column prop="poolStart" label="编号池起始号" width="120" align="center" />
      <el-table-column prop="poolEnd" label="编号池结束号" width="120" align="center" />
      <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            :active-value="1"
            :inactive-value="0"
            :value="row.status"
            @change="handleStatusChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="poolUsage" label="编号池使用" width="130" align="center">
        <template #default="{ row }">
          <span v-if="row.poolStart != null && row.poolEnd != null">
            {{ getPoolUsedCount(row) }}/{{ row.poolEnd - row.poolStart + 1 }}
          </span>
          <span v-else style="color: #909399;">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <el-button type="text" size="small" @click="handleEditParent(row)">编辑</el-button>
          <el-button type="text" size="small" @click="handleDeleteParent(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑父套餐弹窗 -->
    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="580px" append-to-body :close-on-click-modal="false" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="编号池前缀" prop="poolPrefix">
          <el-input v-model="form.poolPrefix" placeholder="如 A1" style="width: 200px;" />
        </el-form-item>
        <el-form-item label="起始号" prop="poolStart">
          <el-input-number v-model="form.poolStart" :min="1" placeholder="如 1001" style="width: 200px;" />
        </el-form-item>
        <el-form-item label="结束号" prop="poolEnd">
          <el-input-number v-model="form.poolEnd" :min="1" placeholder="如 1199" style="width: 200px;" />
        </el-form-item>
        <el-form-item label="套餐名称" prop="packageName">
          <el-input v-model="form.packageName" placeholder="请输入套餐名称" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </div>
    </el-dialog>

    <!-- 新增/编辑子套餐弹窗 -->
    <el-dialog :title="subDialogTitle" :visible.sync="subDialogVisible" width="620px" append-to-body :close-on-click-modal="false" @closed="resetSubForm">
      <el-form ref="subFormRef" :model="subForm" :rules="subRules" label-width="100px">
        <el-form-item label="所属套餐">
          <el-input :value="subForm.parentPackageName || '-'" disabled />
        </el-form-item>
        <el-form-item label="子套餐名称" prop="subPackageName">
          <el-input v-model="subForm.subPackageName" placeholder="请输入子套餐名称" />
        </el-form-item>
        <el-form-item label="荤菜数量" prop="meatCount">
          <el-input-number v-model="subForm.meatCount" :min="0" :max="99" />
        </el-form-item>
        <el-form-item label="素菜数量" prop="vegCount">
          <el-input-number v-model="subForm.vegCount" :min="0" :max="99" />
        </el-form-item>
        <el-form-item label="含汤">
          <el-switch v-model="subForm.includeSoup" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="含米饭">
          <el-switch v-model="subForm.includeRice" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="subForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="subDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitSubForm">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as api from '@/api/customer/package'

const defaultParentForm = {
  id: null,
  packageName: '',
  remark: '',
  status: 1,
  children: [],
  // 编号池配置
  poolPrefix: '',
  poolStart: null,
  poolEnd: null
}

const defaultSubForm = {
  id: null,
  subPackageName: '',
  meatCount: 0,
  vegCount: 0,
  includeSoup: 0,
  includeRice: 0,
  remark: '',
  parentPackageId: null,
  parentPackageName: ''
}

export default {
  name: 'CustomerPackage',
  data() {
    return {
      loading: false,
      submitLoading: false,
      treeData: [],
      dialogVisible: false,
      subDialogVisible: false,
      dialogTitle: '新增套餐',
      subDialogTitle: '新增子套餐',
      form: { ...defaultParentForm },
      subForm: { ...defaultSubForm },
      rules: {
        packageName: [
          { required: true, message: '请输入套餐名称', trigger: 'blur' }
        ],
        poolPrefix: [
          { required: true, message: '编号池前缀不能为空', trigger: 'blur' }
        ],
        poolStart: [
          { required: true, message: '起始号不能为空', trigger: 'blur' }
        ],
        poolEnd: [
          { required: true, message: '结束号不能为空', trigger: 'blur' },
          {
            validator: (rule, value, callback) => {
              if (value !== null && this.form.poolStart !== null && value <= this.form.poolStart) {
                callback(new Error('结束号必须大于起始号'))
              } else {
                callback()
              }
            },
            trigger: 'blur'
          }
        ]
      },
      subRules: {
        subPackageName: [
          { required: true, message: '请输入子套餐名称', trigger: 'blur' }
        ],
        meatCount: [
          { required: true, message: '请输入荤菜数量', trigger: 'blur' }
        ],
        vegCount: [
          { required: true, message: '请输入素菜数量', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    this.getTree()
  },
  methods: {
    getTree() {
      this.loading = true
      api.getTree().then(res => {
        const rows = Array.isArray(res) ? res : []
        this.treeData = rows.map(item => {
          const { children, ...parentRow } = item
          return {
            ...parentRow,
            subPackages: Array.isArray(children) ? children : []
          }
        })
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },

    // ===== 父套餐操作 =====
    handleAddParent() {
      this.form = { ...defaultParentForm }
      this.dialogTitle = '新增套餐'
      this.dialogVisible = true
    },

    handleEditParent(row) {
      this.form = {
        id: row.id,
        packageName: row.packageName,
        remark: row.remark || '',
        status: row.status,
        children: (row.subPackages || []).map(child => ({ id: child.id })),
        // 编号池配置（从列表行回填）
        poolPrefix: row.poolPrefix || '',
        poolStart: row.poolStart || null,
        poolEnd: row.poolEnd || null
      }
      this.dialogTitle = '编辑套餐'
      this.dialogVisible = true
    },

    submitForm() {
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        this.submitLoading = true
        const data = {
          id: this.form.id,
          packageName: this.form.packageName,
          remark: this.form.remark,
          status: this.form.status,
          children: this.form.children,
          // 编号池配置
          poolPrefix: this.form.poolPrefix,
          poolStart: this.form.poolStart,
          poolEnd: this.form.poolEnd
        }
        const action = this.form.id ? api.edit(data) : api.add(data)
        action.then(() => {
          this.$message.success(this.form.id ? '编辑成功' : '新增成功')
          this.dialogVisible = false
          this.getTree()
        }).catch(() => {
          // rollback handled by UI
        }).finally(() => {
          this.submitLoading = false
        })
      })
    },

    handleDeleteParent(row) {
      this.$confirm(`确定删除套餐「${row.packageName}」吗？删除后将同时删除所有子套餐。`, '提示', { type: 'warning' })
        .then(() => api.del(row.id))
        .then(() => {
          this.$message.success('删除成功')
          this.getTree()
        }).catch(() => {})
    },

    handleStatusChange(row) {
      const newStatus = row.status === 1 ? 0 : 1
      api.editStatus(row.id, newStatus).then(() => {
        row.status = newStatus
        this.$message.success('状态更新成功')
      }).catch(() => {
        // rollback
        this.getTree()
      })
    },

    getPoolUsedCount(row) {
      // 后端尚未提供 per-package 已用数量统计接口（GET api/package/usage/{id}）
      return '—'
    },

    resetForm() {
      this.$refs.formRef && this.$refs.formRef.resetFields()
      this.form = { ...defaultParentForm }
    },

    // ===== 子套餐操作 =====
    handleAddSub(parentRow) {
      this.subForm = {
        id: null,
        subPackageName: '',
        meatCount: 0,
        vegCount: 0,
        includeSoup: 0,
        includeRice: 0,
        remark: '',
        parentPackageId: parentRow.id,
        parentPackageName: parentRow.packageName
      }
      this.subDialogTitle = '新增子套餐'
      this.subDialogVisible = true
    },

    handleEditSub(parentRow, row) {
      // 通过 API 获取最新数据（因为展开列表中的字段可能不完整）
      api.getSubById(row.id).then(dto => {
        this.subForm = {
          id: dto.id,
          subPackageName: dto.subPackageName,
          meatCount: dto.meatCount,
          vegCount: dto.vegCount,
          includeSoup: dto.includeSoup,
          includeRice: dto.includeRice,
          remark: dto.remark || '',
          parentPackageId: parentRow.id,
          parentPackageName: parentRow.packageName
        }
        this.subDialogTitle = '编辑子套餐'
        this.subDialogVisible = true
      }).catch(() => {})
    },

    submitSubForm() {
      this.$refs.subFormRef.validate(valid => {
        if (!valid) return
        this.submitLoading = true
        if (this.subForm.id) {
          // 编辑：发送完整 SubPackage 实体
          const payload = {
            id: this.subForm.id,
            subPackageName: this.subForm.subPackageName,
            meatCount: this.subForm.meatCount,
            vegCount: this.subForm.vegCount,
            includeSoup: this.subForm.includeSoup,
            includeRice: this.subForm.includeRice,
            remark: this.subForm.remark
          }
          api.editSub(payload).then(() => {
            this.$message.success('编辑成功')
            this.subDialogVisible = false
            this.getTree()
          }).catch(() => {}).finally(() => { this.submitLoading = false })
        } else {
          // 新增：发送 SubPackageCreateDto
          api.addSub({
            subPackageName: this.subForm.subPackageName,
            meatCount: this.subForm.meatCount,
            vegCount: this.subForm.vegCount,
            includeSoup: this.subForm.includeSoup,
            includeRice: this.subForm.includeRice,
            remark: this.subForm.remark,
            parentPackageId: this.subForm.parentPackageId
          }).then(() => {
            this.$message.success('新增成功')
            this.subDialogVisible = false
            this.getTree()
          }).catch(() => {}).finally(() => { this.submitLoading = false })
        }
      })
    },

    handleDeleteSub(parentRow, row) {
      this.$confirm(`确定删除子套餐「${row.subPackageName}」吗？`, '提示', { type: 'warning' })
        .then(() => api.delSub(row.id))
        .then(() => {
          this.$message.success('删除成功')
          this.getTree()
        }).catch(() => {})
    },

    handleSubStatusChange(parentRow, row) {
      const newStatus = row.status === 1 ? 0 : 1
      api.editSubStatus(row.id, newStatus).then(() => {
        row.status = newStatus
        this.$message.success('状态更新成功')
      }).catch(() => {
        this.getTree()
      })
    },

    resetSubForm() {
      this.$refs.subFormRef && this.$refs.subFormRef.resetFields()
      this.subForm = { ...defaultSubForm }
    }
  }
}
</script>

<style scoped>
.head-container {
  padding: 10px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 4px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.page-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.sub-table-wrap {
  padding: 0 0 10px 50px;
}

.sub-table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.sub-table-title {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
}
</style>
