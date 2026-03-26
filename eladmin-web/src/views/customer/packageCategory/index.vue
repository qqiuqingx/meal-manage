<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <el-input v-model="crud.query.categoryName" clearable size="small" placeholder="输入分类名称搜索" style="width: 200px;" class="filter-item" @keyup.enter.native="handleQuery" />
        <el-select v-model="crud.query.enabled" clearable size="small" placeholder="状态" class="filter-item" style="width: 90px" @change="handleQuery">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <rrOperation />
      </div>
      <crudOperation :permission="permission" />
    </div>

    <!--表单组件-->
    <el-dialog append-to-body :close-on-click-modal="false" :before-close="crud.cancelCU" :visible.sync="crud.status.cu > 0" :title="crud.status.title" width="500px">
      <el-form ref="form" inline :model="form" :rules="rules" size="small" label-width="100px">
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" style="width: 300px;" />
        </el-form-item>
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="form.categoryCode" style="width: 300px;" />
        </el-form-item>
        <el-form-item label="层级">
          <el-radio-group v-model="form.level" @change="levelChange">
            <el-radio :label="1">父级</el-radio>
            <el-radio :label="2">子级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.level === 2" label="上级分类" prop="parentId">
          <treeselect
            v-model="form.parentId"
            :options="parentOptions"
            :load-options="loadParents"
            style="width: 300px;"
            placeholder="选择上级分类"
            :normalizer="normalizer"
          />
        </el-form-item>
        <el-form-item v-if="form.level === 1" label="编号前缀" prop="codePrefix">
          <el-input v-model="form.codePrefix" maxlength="1" style="width: 300px;" placeholder="单个大写字母,如A" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model.number="form.sort" :min="0" :max="999" controls-position="right" style="width: 300px;" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.enabled">
            <el-radio :label="true">启用</el-radio>
            <el-radio :label="false">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="text" @click="crud.cancelCU">取消</el-button>
        <el-button :loading="crud.status.cu === 2" type="primary" @click="crud.submitCU">确认</el-button>
      </div>
    </el-dialog>

    <!--表格渲染-->
    <el-table
      ref="table"
      v-loading="crud.loading"
      :data="crud.data"
      row-key="id"
      :tree-props="{ children: 'children' }"
      :default-expand-all="true"
      @select="crud.selectChange"
      @select-all="crud.selectAllChange"
      @selection-change="crud.selectionChangeHandler"
    >
      <el-table-column :selectable="checkboxT" type="selection" width="55" />
      <el-table-column label="分类名称" prop="categoryName" />
      <el-table-column label="分类编码" prop="categoryCode" />
      <el-table-column label="层级">
        <template slot-scope="scope">
          <el-tag :type="scope.row.level === 1 ? 'primary' : 'info'" size="mini">
            {{ scope.row.level === 1 ? '父级' : '子级' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="编号前缀">
        <template slot-scope="scope">
          <span v-if="scope.row.level === 1">{{ scope.row.codePrefix }}</span>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sort" />
      <el-table-column label="状态" align="center">
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.enabled"
            active-color="#409EFF"
            inactive-color="#F56C6C"
            @change="changeEnabled(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column v-if="checkPer(['admin','customerPackageCategory:edit','customerPackageCategory:del'])" label="操作" width="130px" align="center" fixed="right">
        <template slot-scope="scope">
          <udOperation
            :data="scope.row"
            :permission="permission"
            :disabled-dle="scope.row.children && scope.row.children.length > 0"
            msg="确定删除吗?"
          />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'
import * as api from '@/api/customer/packageCategory'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import { checkPer } from '@/utils/permission'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'
import udOperation from '@crud/UD.operation'

const defaultForm = {
  id: null,
  categoryName: null,
  categoryCode: null,
  parentId: null,
  level: 1,
  sort: 0,
  enabled: true,
  codePrefix: null
}

export default {
  name: 'CustomerPackageCategory',
  components: { Treeselect, udOperation, rrOperation, crudOperation },
  mixins: [presenter(), header(), form(defaultForm), crud()],
  cruds() {
    return CRUD({ title: '套餐分类', url: '/api/customerPackageCategory', idField: 'id', crudMethod: { ...api },
      query: { categoryName: '', enabled: null }
    })
  },
  data() {
    return {
      permission: {
        add: ['admin', 'customerPackageCategory:add'],
        edit: ['admin', 'customerPackageCategory:edit'],
        del: ['admin', 'customerPackageCategory:del']
      },
      fullTreeData: [],
      parentOptions: [],
      rules: {
        categoryName: [
          { required: true, message: '请输入分类名称', trigger: 'blur' }
        ],
        categoryCode: [
          { required: true, message: '请输入分类编码', trigger: 'blur' }
        ],
        codePrefix: [
          { pattern: /^[A-Z]$/, message: '请输入单个大写字母', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    this.crud.refresh()
    this.loadParents()
  },
  methods: {
    checkPer,
    [CRUD.HOOK.beforeRefresh]() {
      this.crud.loading = true
      api.getTree().then(res => {
        this.fullTreeData = Array.isArray(res) ? res : (res.data || [])
        this.applyFilter()
        this.loadParents()
      }).catch(() => {
        this.crud.loading = false
      })
      return false
    },
    handleQuery() {
      if (this.fullTreeData.length > 0) {
        this.applyFilter()
      } else {
        this.crud.refresh()
      }
    },
    applyFilter() {
      const name = (this.crud.query.categoryName || '').trim()
      const enabled = this.crud.query.enabled
      const hasFilter = name || (enabled !== null && enabled !== undefined && enabled !== '')

      let result
      if (!hasFilter) {
        result = this.fullTreeData
      } else {
        const filtered = []
        for (const parent of this.fullTreeData) {
          const parentMatch = this.matchNode(parent, name, enabled)
          const matchedChildren = (parent.children || []).filter(child => this.matchNode(child, name, enabled))

          if (parentMatch) {
            // 父级匹配：保留父级及全部子级
            filtered.push(parent)
          } else if (matchedChildren.length > 0) {
            // 父级不匹配但有匹配子级：保留父级，只展示匹配的子级
            filtered.push({ ...parent, children: matchedChildren })
          }
        }
        result = filtered
      }
      // 深拷贝隔断引用，防止 Element UI 树形表格交互时修改 fullTreeData
      this.crud.data = JSON.parse(JSON.stringify(result))
      this.crud.resetDataStatus()
      this.crud.loading = false
    },
    matchNode(node, name, enabled) {
      const nameMatch = !name || node.categoryName.includes(name)
      const enabledMatch = enabled === null || enabled === undefined || enabled === '' || node.enabled === enabled
      return nameMatch && enabledMatch
    },
    normalizer(node) {
      return {
        id: node.id,
        label: node.categoryName,
        children: node.children
      }
    },
    async loadParents() {
      try {
        const res = await api.getParents()
        this.parentOptions = Array.isArray(res) ? res : (res.data || [])
      } catch (e) {
        console.error('loadParents error', e)
      }
    },
    levelChange(val) {
      if (val === 1) {
        this.form.parentId = null
        this.form.codePrefix = null
      }
    },
    changeEnabled(row) {
      try {
        api.editStatus(row.id, row.enabled)
        this.$message.success('状态更新成功')
      } catch (e) {
        row.enabled = !row.enabled
        this.$message.error('状态更新失败')
      }
    },
    checkboxT(row) {
      return row.id !== 1
    }
  }
}
</script>

<style scoped>
.head-container {
  padding: 10px;
  margin-bottom: 10px;
}
.head-container .filter-item {
  margin-right: 10px;
}
</style>
