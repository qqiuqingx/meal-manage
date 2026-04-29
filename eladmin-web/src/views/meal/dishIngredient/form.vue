<template>
  <el-dialog :title="title" :visible.sync="dialogVisible" width="500px" @close="dialogClose">
    <el-form ref="form" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="配料名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入配料名称" />
      </el-form-item>
      <el-form-item label="一级分类" prop="parentCategoryName">
        <el-select
          v-model="form.parentCategoryName"
          filterable
          allow-create
          default-first-option
          placeholder="请选择或输入一级分类"
          style="width: 100%"
          @change="handleParentCategoryChange"
        >
          <el-option
            v-for="item in level1Categories"
            :key="item.id"
            :label="item.name"
            :value="item.name"
          />
        </el-select>
        <div class="form-tip">输入新的名称将自动创建一级分类</div>
      </el-form-item>
      <el-form-item label="二级分类" prop="categoryName">
        <el-select
          v-model="form.categoryName"
          filterable
          allow-create
          default-first-option
          placeholder="请选择或输入二级分类"
          :disabled="!form.parentCategoryName"
          style="width: 100%"
        >
          <el-option
            v-for="item in currentLevel2Categories"
            :key="item.id"
            :label="item.name"
            :value="item.name"
          />
        </el-select>
        <div v-if="form.parentCategoryName" class="form-tip">
          输入新的名称将自动创建二级分类（归属：{{ form.parentCategoryName }}）
        </div>
      </el-form-item>
      <el-form-item label="单位" prop="unit">
        <el-select v-model="form.unit" placeholder="请选择单位" clearable style="width: 100%">
          <el-option label="克" value="克" />
          <el-option label="毫升" value="毫升" />
          <el-option label="个" value="个" />
        </el-select>
      </el-form-item>
      <el-form-item label="热量(卡)" prop="calories">
        <el-input-number v-model="form.calories" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
      </el-form-item>
      <el-form-item label="是否启用" prop="enabled">
        <el-switch v-model="form.enabled" :active-value="true" :inactive-value="false" />
      </el-form-item>
    </el-form>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="cancel">取 消</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { addIngredient, editIngredient, getIngredient } from '@/api/dishIngredient'

export default {
  name: 'IngredientForm',
  props: {
    categoryTree: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      dialogVisible: false,
      title: '',
      level1Categories: [],
      currentLevel2Categories: [],
      form: {
        id: null,
        name: '',
        parentCategoryId: null,
        parentCategoryName: null,
        categoryName: null,
        categoryId: null,
        unit: '克',
        calories: 0,
        remark: '',
        enabled: true
      },
      rules: {
        name: [{ required: true, message: '配料名称不能为空', trigger: 'blur' }],
        parentCategoryName: [{ required: true, message: '请选择或输入一级分类', trigger: 'change' }],
        categoryName: [{ required: true, message: '请选择或输入二级分类', trigger: 'change' }]
      }
    }
  },
  watch: {
    categoryTree: {
      immediate: true,
      handler(tree) {
        this.level1Categories = Array.isArray(tree) ? tree : []
        this.syncCategoryOptions()
      }
    }
  },
  methods: {
    handleParentCategoryChange(value) {
      const existing = this.level1Categories.find(c => c.name === value)
      this.form.parentCategoryId = existing ? existing.id : null
      this.form.categoryName = null
      this.form.categoryId = null
      this.currentLevel2Categories = []

      if (!value) return

      if (existing && existing.children) {
        this.currentLevel2Categories = existing.children
      }
    },
    handleAdd() {
      this.title = '新增配料'
      this.resetForm()
      this.dialogVisible = true
    },
    handleUpdate(id) {
      this.title = '编辑配料'
      getIngredient(id).then(response => {
        this.form = {
          id: response.id,
          name: response.name,
          parentCategoryId: response.parentCategoryId || null,
          parentCategoryName: response.parentCategoryName || null,
          categoryName: response.categoryName || null,
          categoryId: response.categoryId || null,
          unit: response.unit || '克',
          calories: response.calories || 0,
          remark: response.remark || '',
          enabled: response.enabled !== false
        }
        this.syncCategoryOptions()
        this.dialogVisible = true
      })
    },
    syncCategoryOptions() {
      if (this.form.parentCategoryId) {
        const parentById = this.level1Categories.find(c => c.id === this.form.parentCategoryId)
        if (!parentById) {
          this.form.parentCategoryId = null
          this.form.parentCategoryName = null
          this.form.categoryId = null
          this.form.categoryName = null
          this.currentLevel2Categories = []
          return
        }
        this.form.parentCategoryName = parentById.name
        this.currentLevel2Categories = parentById.children || []
        if (this.form.categoryId) {
          const categoryById = this.currentLevel2Categories.find(c => c.id === this.form.categoryId)
          if (!categoryById) {
            this.form.categoryId = null
            this.form.categoryName = null
          } else {
            this.form.categoryName = categoryById.name
          }
        }
        return
      }

      if (!this.form.parentCategoryName) {
        this.currentLevel2Categories = []
        this.form.categoryId = null
        this.form.categoryName = null
        return
      }

      const parentByName = this.level1Categories.find(c => c.name === this.form.parentCategoryName)
      if (!parentByName) {
        this.currentLevel2Categories = []
        return
      }

      this.form.parentCategoryId = parentByName.id
      this.currentLevel2Categories = parentByName.children || []
      if (this.form.categoryId) {
        const categoryById = this.currentLevel2Categories.find(c => c.id === this.form.categoryId)
        if (!categoryById) {
          this.form.categoryId = null
          this.form.categoryName = null
        } else {
          this.form.categoryName = categoryById.name
        }
        return
      }

      if (this.form.categoryName) {
        const categoryByName = this.currentLevel2Categories.find(c => c.name === this.form.categoryName)
        if (categoryByName) {
          this.form.categoryId = categoryByName.id
        }
      }
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (valid) {
          const action = this.form.id ? editIngredient : addIngredient
          action(this.form).then(() => {
            this.$message.success('保存成功')
            this.dialogVisible = false
            this.$emit('refresh')
          })
        }
      })
    },
    cancel() {
      this.dialogVisible = false
    },
    dialogClose() {
      this.resetForm()
    },
    resetForm() {
      this.form = {
        id: null,
        name: '',
        parentCategoryId: null,
        parentCategoryName: null,
        categoryName: null,
        categoryId: null,
        unit: '克',
        calories: 0,
        remark: '',
        enabled: true
      }
      this.currentLevel2Categories = []
      if (this.$refs.form) {
        this.$refs.form.resetFields()
      }
    }
  }
}
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  line-height: 1.4;
}
</style>
