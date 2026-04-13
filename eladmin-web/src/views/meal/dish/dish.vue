<template>
  <el-dialog :visible.sync="dialogVisible" width="1000px" custom-class="editorial-dialog" :show-close="false" @close="dialogClose">
    <!-- Modal Header -->
    <div slot="title" class="dialog-header flex-between">
      <div>
        <h2 class="editorial-title text-primary">{{ title }}</h2>
        <p class="editorial-subtitle text-on-surface-variant">定义配料组成和编辑分类。</p>
      </div>
      <button class="btn-close" @click="cancel">
        <i class="el-icon-close" />
      </button>
    </div>

    <!-- Modal Content -->
    <div class="dialog-body custom-scrollbar">
      <el-form ref="form" :model="form" :rules="rules" label-position="top" class="editorial-form">
        <el-row :gutter="40">
          <!-- Left Column: Basic Info & Instructions -->
          <el-col :span="9" class="form-col-left">
            <el-form-item label="菜品名称" prop="name">
              <el-input v-model="form.name" placeholder="例如：清蒸鲈鱼" class="editorial-input-line" />
            </el-form-item>

            <el-form-item label="视觉图片" prop="imageUrl">
              <div class="visual-image-placeholder group relative cursor-pointer" @click="showImageInput = true">
                <img v-if="form.imageUrl && !showImageInput" :src="form.imageUrl" class="w-full h-full object-cover">
                <div v-if="!form.imageUrl && !showImageInput" class="placeholder-content">
                  <i class="el-icon-camera text-4xl mb-2" />
                  <span>添加/更换图片</span>
                </div>
                <!-- Input shown when clicked -->
                <el-input v-if="showImageInput" v-model="form.imageUrl" placeholder="输入图片路径 URL..." class="editorial-input-line abs-input" @blur="showImageInput = false" />
              </div>
            </el-form-item>

            <el-form-item label="所属套餐" prop="mealPackages">
              <el-checkbox-group v-model="form.mealPackages" class="vertical-checkbox-group">
                <el-checkbox v-for="pkg in packageOptions" :key="pkg.id" :label="pkg.id" class="package-checkbox group">
                  <span class="group-hover-text">{{ pkg.packageName }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="制作流程" prop="cookingMethod">
              <el-input v-model="form.cookingMethod" type="textarea" :rows="4" placeholder="输入菜品的具体制作流程..." class="editorial-textarea" />
            </el-form-item>

            <div class="flex gap-6 mt-4">
              <el-form-item label="排序" prop="sort" class="flex-1">
                <el-input-number v-model="form.sort" :min="0" class="editorial-input-line w-full text-left" :controls="false" placeholder="数字越小越靠前" />
              </el-form-item>
              <el-form-item label="是否启用" prop="enabled" class="flex-1">
                <el-switch v-model="form.enabled" :active-value="true" :inactive-value="false" active-color="#006b5c" inactive-color="#bbcac4" />
              </el-form-item>
            </div>
          </el-col>

          <!-- Right Column: Ingredients -->
          <el-col :span="15">
            <div class="bg-surface-container-low rounded-xl p-6 border-none shadow-sm ingredient-panel h-full">
              <div class="flex-between mb-8">
                <h3 class="font-headline text-lg font-bold text-on-surface tracking-tight">配料管理</h3>
                <div class="flex gap-3">
                  <el-select
                    v-model="selectIngredientId"
                    filterable
                    remote
                    placeholder="搜索配料库..."
                    :remote-method="searchIngredients"
                    :loading="ingredientLoading"
                    value-key="id"
                    class="ingredient-search-input"
                  >
                    <i slot="prefix" class="el-input__icon el-icon-search" />
                    <el-option
                      v-for="item in ingredientOptions"
                      :key="item.id"
                      :label="item.name"
                      :value="item.id"
                    >
                      <span class="font-bold">{{ item.name }}</span>
                      <span class="text-secondary text-xs float-right uppercase px-2 bg-secondary-container text-on-secondary-container rounded align-middle mt-1 ml-2">{{ item.unit }}</span>
                    </el-option>
                  </el-select>
                  <button class="btn-primary shadow-sm" @click.prevent="addIngredientRow">
                    <i class="el-icon-plus font-bold" /> 添加
                  </button>
                </div>
              </div>

              <!-- Minimalist Ingredient Table -->
              <div class="overflow-hidden bg-surface-container-lowest rounded-xl shadow-sm border-none">
                <el-table :data="form.ingredientList" class="ingredient-table" :header-cell-style="{ background: 'rgba(230, 232, 234, 0.5)' }">
                  <el-table-column label="配料名称" prop="ingredientName" min-width="120">
                    <template slot-scope="scope">
                      <span class="font-bold text-primary">{{ scope.row.ingredientName }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="用量" align="left" width="100">
                    <template slot-scope="scope">
                      <el-input-number
                        v-model="scope.row.quantity"
                        :min="0"
                        size="small"
                        :controls="false"
                        class="quantity-input"
                        placeholder="数量"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="单位" prop="unit" align="center" width="80">
                    <template slot-scope="scope">
                      <span class="custom-pill pill-neutral text-xs px-2 py-1 uppercase rounded">{{ scope.row.unit }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="备注" align="left">
                    <template slot-scope="scope">
                      <el-input v-model="scope.row.remark" size="small" placeholder="添加备注..." class="remark-input italic focus-visible-border" />
                    </template>
                  </el-table-column>
                  <el-table-column label="" align="right" width="60">
                    <template slot-scope="scope">
                      <button class="btn-delete text-outline-variant hover:text-tertiary transition-colors" title="删除配料" @click.prevent="removeIngredientRow(scope.$index)">
                        <i class="el-icon-delete" />
                      </button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <!-- Modal Footer -->
    <div slot="footer" class="dialog-footer editorial-footer flex items-center justify-end gap-4 px-8 py-6">
      <button class="btn-cancel text-secondary font-bold hover-bg-highest transition-colors rounded-lg px-6 py-3" @click="cancel">取消</button>
      <button class="btn-primary shadow-lg font-bold rounded-lg px-8 py-3" @click="submitForm">保存菜品修改</button>
    </div>
  </el-dialog>
</template>

<script>
import { addDish, editDish, queryPackages, getDish } from '@/api/dish'
import { queryIngredients } from '@/api/dishIngredient'

export default {
  name: 'DishForm',
  data() {
    return {
      dialogVisible: false,
      title: '',
      showImageInput: false,
      packageOptions: [],
      form: {
        id: null,
        name: '',
        cookingMethod: '',
        imageUrl: '',
        dishType: 'MAIN',
        mealTypes: ['LUNCH'],
        mealPackages: [],
        sort: 0,
        enabled: true,
        ingredientList: []
      },
      rules: {
        name: [{ required: true, message: '菜品名称不能为空', trigger: 'blur' }]
      },

      // 配料搜索相关
      selectIngredientId: null,
      ingredientOptions: [],
      ingredientLoading: false,
      // 缓存全部配料选项map，用于追加到列表时获取详情
      ingredientMap: {}
    }
  },

  methods: {
    loadPackages() {
      queryPackages().then(response => {
        this.packageOptions = response || []
      })
    },
    handleAdd() {
      this.title = '编辑/新增菜品'
      this.showImageInput = false
      this.resetForm()
      this.loadPackages()
      this.dialogVisible = true
    },
    handleUpdate(row) {
      this.title = '编辑/新增菜品'
      this.showImageInput = false
      this.loadPackages()

      getDish(row.id).then(fullDish => {
        this.form = JSON.parse(JSON.stringify(fullDish))

        // 补充被后端 @JsonIgnore 或历史剥离导致丢失的字段，避免 Vue 双向绑定失效 (使用 $set)
        if (!this.form.ingredientList) {
          this.$set(this.form, 'ingredientList', [])
        }
        this.$set(this.form, 'dishType', this.form.dishType || 'MAIN')
        this.$set(this.form, 'mealTypes', this.form.mealTypes || ['LUNCH'])

        // 将 mealPackageDetails 中的 id 反填到 mealPackages（DB 存的是 ID）
        if (this.form.mealPackageDetails && this.form.mealPackageDetails.length > 0) {
          this.$set(this.form, 'mealPackages', this.form.mealPackageDetails.map(pkg => pkg.id))
        } else {
          this.$set(this.form, 'mealPackages', [])
        }
        this.dialogVisible = true
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (valid) {
          const action = this.form.id ? editDish : addDish
          action(this.form).then(() => {
            this.$message.success('保存成功')
            this.dialogVisible = false
            this.$emit('saved', { ...this.form, isEdit: !!this.form.id })
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
        cookingMethod: '',
        imageUrl: '',
        dishType: 'MAIN',
        mealTypes: ['LUNCH'],
        mealPackages: [],
        sort: 0,
        enabled: true,
        ingredientList: []
      }
      this.selectIngredientId = null
      this.ingredientOptions = []
      if (this.$refs.form) {
        this.$refs.form.resetFields()
      }
    },

    searchIngredients(query) {
      if (query !== '') {
        this.ingredientLoading = true
        queryIngredients({ name: query, size: 20, page: 0, enabled: true }).then(response => {
          this.ingredientOptions = response.content || []
          this.ingredientOptions.forEach(item => {
            this.ingredientMap[item.id] = item
          })
          this.ingredientLoading = false
        })
      } else {
        this.ingredientOptions = []
      }
    },
    addIngredientRow() {
      if (!this.selectIngredientId) {
        this.$message.warning('请先选择配料')
        return
      }
      const ingredient = this.ingredientMap[this.selectIngredientId]
      if (!ingredient) {
        this.$message.warning('配料信息未找到')
        return
      }
      // 检查是否已经添加
      const exists = this.form.ingredientList.some(item => item.ingredientId === ingredient.id)
      if (exists) {
        this.$message.warning('该配料已存在列表中')
        return
      }
      this.form.ingredientList.push({
        ingredientId: ingredient.id,
        ingredientName: ingredient.name,
        quantity: 100,
        unit: ingredient.unit || '克',
        remark: ''
      })
      this.selectIngredientId = null
    },
    removeIngredientRow(index) {
      this.form.ingredientList.splice(index, 1)
    }
  }
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;700;800&family=Inter:wght@400;500;600&display=swap');

/* Color Variables injected if needed locally, usually globally accessible */
.editorial-dialog {
  background-color: var(--surface-container-lowest, #ffffff);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 32px 64px -12px rgba(0, 107, 92, 0.15) !important;
}
::v-deep .el-dialog__header {
  display: none; /* Hide default header */
}
::v-deep .el-dialog__body {
  padding: 0;
}

.flex { display: flex; }
.flex-1 { flex: 1; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }
.gap-3 { gap: 12px; }
.gap-4 { gap: 16px; }
.gap-6 { gap: 24px; }
.mt-4 { margin-top: 16px; }
.mb-2 { margin-bottom: 8px; }
.mb-3 { margin-bottom: 12px; }
.mb-6 { margin-bottom: 24px; }
.mb-8 { margin-bottom: 32px; }
.w-full { width: 100%; }
.h-full { height: 100%; }
.font-bold { font-weight: 700; }
.text-xs { font-size: 0.75rem; }
.text-sm { font-size: 0.875rem; }
.text-lg { font-size: 1.125rem; }
.text-primary { color: var(--primary, #006b5c); }
.text-secondary { color: var(--secondary, #546067); }
.text-on-surface { color: var(--on-surface, #191c1e); }
.text-on-surface-variant { color: var(--on-surface-variant, #3c4a46); }
.uppercase { text-transform: uppercase; }
.italic { font-style: italic; }
.tracking-tight { letter-spacing: -0.025em; }
.tracking-widest { letter-spacing: 0.1em; }
.shadow-sm { box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05); }
.shadow-lg { box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -2px rgba(0,0,0,0.05); }
.rounded-xl { border-radius: 0.75rem; }
.rounded-lg { border-radius: 0.5rem; }
.rounded { border-radius: 0.25rem; }
.px-2 { padding-left: 0.5rem; padding-right: 0.5rem; }
.px-6 { padding-left: 1.5rem; padding-right: 1.5rem; }
.px-8 { padding-left: 2rem; padding-right: 2rem; }
.py-1 { padding-top: 0.25rem; padding-bottom: 0.25rem; }
.py-3 { padding-top: 0.75rem; padding-bottom: 0.75rem; }
.align-middle { vertical-align: middle; }
.float-right { float: right; }
.font-headline { font-family: 'Manrope', sans-serif; }
.bg-surface-container-low { background-color: var(--surface-container-low, #f2f4f6); }
.bg-surface-container-lowest { background-color: var(--surface-container-lowest, #ffffff); }
.bg-secondary-container { background-color: var(--secondary-container, #d7e4ec); }
.text-on-secondary-container { color: var(--on-secondary-container, #5a666d); }

/* Header/Footer Elements */
.dialog-header {
  padding: 24px 32px;
  background-color: var(--surface-container-low, #f2f4f6);
}
.editorial-title {
  margin: 0;
  font-family: 'Manrope', sans-serif;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.025em;
}
.editorial-subtitle {
  margin: 4px 0 0;
  font-size: 0.875rem;
  font-weight: 500;
}
.btn-close {
  background: transparent;
  border: none;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--on-surface-variant, #3c4a46);
  transition: background 0.2s;
}
.btn-close:hover { background-color: var(--surface-container-highest, #e0e3e5); }

.editorial-footer {
  background-color: var(--surface-container-low, #f2f4f6);
}
.btn-cancel {
  background: transparent;
  border: none;
  cursor: pointer;
}
.hover-bg-highest:hover {
  background-color: var(--surface-container-highest, #e0e3e5);
}

.btn-primary {
  background: linear-gradient(135deg, var(--primary, #006b5c) 0%, var(--primary-container, #00bfa5) 100%);
  color: white;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.btn-primary:active { transform: translateY(2px); }

/* Form Elements */
.dialog-body {
  padding: 32px;
  max-height: 70vh;
  overflow-y: auto;
}
::v-deep .editorial-form .el-form-item__label {
  font-family: 'Manrope', sans-serif;
  font-size: 0.875rem;
  font-weight: 700;
  color: var(--on-surface-variant, #3c4a46);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding-bottom: 4px;
}

/* Input Overrides */
::v-deep .editorial-input-line .el-input__inner,
::v-deep .editorial-input-line.el-input-number .el-input__inner {
  background-color: var(--surface-container-high, #e6e8ea);
  border: none;
  border-bottom: 2px solid transparent;
  border-radius: 8px;
  padding: 16px;
  height: auto;
  font-weight: 500;
  transition: all 0.2s;
}
::v-deep .editorial-input-line .el-input__inner:focus {
  border-bottom-color: var(--primary, #006b5c);
  background-color: var(--surface-container-lowest, #ffffff);
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
}
::v-deep .text-left .el-input__inner { text-align: left; }

::v-deep .editorial-textarea .el-textarea__inner {
  background-color: var(--surface-container-high, #e6e8ea);
  border: none;
  border-bottom: 2px solid transparent;
  border-radius: 8px;
  padding: 16px;
  font-weight: 500;
  font-family: 'Inter', sans-serif;
  transition: all 0.2s;
  resize: none;
}
::v-deep .editorial-textarea .el-textarea__inner:focus {
  border-bottom-color: var(--primary, #006b5c);
  background-color: var(--surface-container-lowest, #ffffff);
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
}

/* Visual Image Placeholder */
.visual-image-placeholder {
  aspect-ratio: 16 / 9;
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
  background-color: var(--surface-container-highest, #e0e3e5);
  border: 2px dashed var(--outline-variant, #bbcac4);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.visual-image-placeholder:hover {
  border-color: var(--primary, #006b5c);
}
.visual-image-placeholder img {
  opacity: 0.8;
  transition: opacity 0.2s;
}
.visual-image-placeholder:hover img {
  opacity: 1;
}
.placeholder-content {
  color: var(--on-surface-variant, #3c4a46);
  display: flex;
  flex-direction: column;
  align-items: center;
  font-weight: 700;
  text-transform: uppercase;
  font-size: 0.75rem;
}
.abs-input {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 90%;
  z-index: 10;
}

/* Custom Checkboxes / Radios */
.vertical-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.package-checkbox {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: transparent;
  transition: background 0.2s;
  display: flex;
  align-items: center;
}
.package-checkbox:hover {
  background-color: var(--surface-container-low, #f2f4f6);
}
::v-deep .package-checkbox .el-checkbox__label {
  font-weight: 500;
  color: var(--secondary, #546067);
  transition: color 0.2s;
}
.package-checkbox:hover ::v-deep .el-checkbox__label {
  color: var(--primary, #006b5c);
}
::v-deep .package-checkbox .el-checkbox__inner {
  border-color: var(--outline-variant, #bbcac4);
  border-radius: 4px;
}
::v-deep .package-checkbox.is-checked .el-checkbox__inner {
  background-color: var(--primary, #006b5c);
  border-color: var(--primary, #006b5c);
}

::v-deep .pill-radio-group .el-checkbox-button__inner,
::v-deep .pill-radio-group .el-radio-button__inner {
  border: none !important;
  background: var(--surface-container-high, #e6e8ea);
  color: var(--on-surface-variant, #3c4a46);
  border-radius: 8px !important;
  margin-right: 8px;
  margin-bottom: 8px;
  padding: 10px 16px;
  font-weight: 600;
  box-shadow: none !important;
  transition: all 0.2s;
}
::v-deep .pill-radio-group.w-full {
  display: flex;
  flex-wrap: wrap;
}
::v-deep .pill-radio-group.w-full .el-radio-button,
::v-deep .pill-radio-group.w-full .el-checkbox-button {
  flex: 1 1 calc(33.3% - 8px);
  min-width: 80px;
}
::v-deep .pill-radio-group .el-checkbox-button__inner:hover,
::v-deep .pill-radio-group .el-radio-button__inner:hover {
  background: var(--surface-container-highest, #e0e3e5);
}
::v-deep .pill-radio-group .el-radio-button.is-active .el-radio-button__inner,
::v-deep .pill-radio-group .el-checkbox-button.is-checked .el-checkbox-button__inner {
  background: rgba(0, 191, 165, 0.1) !important;
  color: var(--primary, #006b5c) !important;
  border: 1px solid rgba(0, 191, 165, 0.2) !important;
  box-shadow: 0 4px 6px -1px rgba(0, 191, 165, 0.05) !important;
}

/* Schedule List Styles */
.schedule-group { display: flex; flex-wrap: wrap; gap: 8px; }
::v-deep .schedule-group .el-checkbox { margin-right: 8px; }

/* Ingredient Select Input */
::v-deep .ingredient-search-input .el-input__inner {
  background-color: var(--surface-container-lowest, #ffffff);
  border: none;
  border-radius: 8px;
  padding-left: 36px;
  font-weight: 500;
  width: 250px;
}
::v-deep .ingredient-search-input .el-input__inner:focus {
  box-shadow: 0 0 0 2px var(--primary-container, #00bfa5);
}

/* Ingredient Table */
::v-deep .ingredient-table {
  background: transparent;
  font-family: 'Inter', sans-serif;
}
::v-deep .ingredient-table::before {
  display: none;
}
::v-deep .ingredient-table th {
  border-bottom: none !important;
  color: var(--on-surface-variant, #3c4a46);
  font-family: 'Manrope', sans-serif;
  font-size: 0.6875rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  padding: 16px;
}
::v-deep .ingredient-table td {
  border-bottom: 1px solid var(--surface-container, #eceef0);
  padding: 16px 0;
}
::v-deep .ingredient-table .el-table__row:hover > td {
  background-color: rgba(242, 244, 246, 0.5) !important;
}
::v-deep .quantity-input .el-input__inner {
  background: transparent;
  border: none;
  font-weight: 600;
  color: var(--secondary, #546067);
  padding: 0;
  text-align: left;
}
::v-deep .quantity-input .el-input__inner:focus {
  background: var(--surface-container-lowest, #ffffff);
  border-bottom: 2px solid var(--primary, #006b5c);
}
::v-deep .remark-input .el-input__inner {
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  font-style: italic;
  font-size: 0.75rem;
  color: var(--on-surface-variant, #3c4a46);
  transition: all 0.2s;
  padding: 0 8px;
}
::v-deep .remark-input .el-input__inner:focus {
  background: var(--surface-container-lowest, #ffffff);
  border-bottom-color: var(--primary, #006b5c);
  font-style: normal;
}

.text-outline-variant { color: var(--outline-variant, #bbcac4); }
.hover\:text-tertiary:hover { color: var(--tertiary, #ac3509); }
.btn-delete {
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 1.2rem;
}
.pill-neutral { background: var(--surface-container-high, #e6e8ea); color: var(--on-surface-variant, #3c4a46); }

/* Scrollbar Customization */
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: var(--outline-variant, #bbcac4);
  border-radius: 10px;
}
</style>
