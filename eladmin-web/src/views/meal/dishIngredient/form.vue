<template>
  <el-dialog :title="title" :visible.sync="dialogVisible" width="500px" @close="dialogClose">
    <el-form ref="form" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="配料名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入配料名称" />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select v-model="form.category" placeholder="请选择分类" clearable style="width: 100%">
          <el-option label="肉类" value="MEAT" />
          <el-option label="蔬菜" value="VEGETABLE" />
          <el-option label="海鲜" value="SEAFOOD" />
          <el-option label="豆制品" value="TOFU" />
          <el-option label="调料" value="SPICE" />
          <el-option label="其他" value="OTHER" />
        </el-select>
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
  data() {
    return {
      dialogVisible: false,
      title: '',
      form: {
        id: null,
        name: '',
        category: null,
        unit: '克',
        calories: 0,
        remark: '',
        enabled: true
      },
      rules: {
        name: [{ required: true, message: '配料名称不能为空', trigger: 'blur' }]
      }
    }
  },
  methods: {
    handleAdd() {
      this.title = '新增配料'
      this.resetForm()
      this.dialogVisible = true
    },
    handleUpdate(id) {
      this.title = '编辑配料'
      getIngredient(id).then(response => {
        this.form = response
        this.dialogVisible = true
      })
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
        category: null,
        unit: '克',
        calories: 0,
        remark: '',
        enabled: true
      }
      if (this.$refs.form) {
        this.$refs.form.resetFields()
      }
    }
  }
}
</script>
