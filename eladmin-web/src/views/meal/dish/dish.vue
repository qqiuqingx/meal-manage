<template>
  <el-dialog :title="title" :visible.sync="dialogVisible" width="800px" @close="dialogClose">
    <el-form ref="form" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="菜品名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入菜品名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="菜品类型" prop="dishType">
            <el-radio-group v-model="form.dishType">
              <el-radio label="MAIN">主菜</el-radio>
              <el-radio label="SIDE">副菜</el-radio>
              <el-radio label="SOUP">汤</el-radio>
              <el-radio label="VEGETABLE">素菜</el-radio>
              <el-radio label="RICE">米饭</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="做法/流程" prop="cookingMethod">
        <el-input v-model="form.cookingMethod" type="textarea" :rows="3" placeholder="请输入做法/流程" />
      </el-form-item>
      <el-form-item label="图片" prop="imageUrl">
        <el-input v-model="form.imageUrl" placeholder="请输入图片路径" />
      </el-form-item>
      <el-form-item label="餐次" prop="mealTypes">
        <el-checkbox-group v-model="form.mealTypes">
          <el-checkbox label="LUNCH">午餐</el-checkbox>
          <el-checkbox label="DINNER">晚餐</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="套餐" prop="mealPackages">
        <el-select
          v-model="form.mealPackages"
          multiple
          placeholder="请选择套餐"
          style="width: 100%;"
        >
          <el-option
            v-for="pkg in packageOptions"
            :key="pkg.id"
            :label="pkg.packageName"
            :value="pkg.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="排期" prop="schedule">
        <div class="schedule-container">
          <div class="schedule-weeks">
            <span class="label">选择周数：</span>
            <el-checkbox-group v-model="selectedWeeks">
              <el-checkbox label="1">第1周</el-checkbox>
              <el-checkbox label="2">第2周</el-checkbox>
              <el-checkbox label="3">第3周</el-checkbox>
              <el-checkbox label="4">第4周</el-checkbox>
            </el-checkbox-group>
          </div>
          <div class="schedule-days">
            <span class="label">选择星期：</span>
            <el-checkbox-group v-model="selectedDays">
              <el-checkbox label="1">周一</el-checkbox>
              <el-checkbox label="2">周二</el-checkbox>
              <el-checkbox label="3">周三</el-checkbox>
              <el-checkbox label="4">周四</el-checkbox>
              <el-checkbox label="5">周五</el-checkbox>
              <el-checkbox label="6">周六</el-checkbox>
              <el-checkbox label="7">周日</el-checkbox>
            </el-checkbox-group>
          </div>
        </div>
      </el-form-item>

      <!-- 配料列表 -->
      <el-form-item label="配料列表">
        <div class="ingredient-section">
          <div class="ingredient-add-row">
            <el-select
              v-model="selectIngredientId"
              filterable
              remote
              placeholder="搜索配料名称"
              :remote-method="searchIngredients"
              :loading="ingredientLoading"
              style="width: 200px; margin-right: 10px;"
              value-key="id"
            >
              <el-option
                v-for="item in ingredientOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
                <span>{{ item.name }}</span>
                <span style="float: right; color: #8492a6; font-size: 12px;">{{ item.unit }}</span>
              </el-option>
            </el-select>
            <el-button type="primary" icon="el-icon-plus" @click="addIngredientRow">添加</el-button>
          </div>

          <el-table :data="form.ingredientList" size="small" style="margin-top: 10px;" border>
            <el-table-column label="配料名称" prop="ingredientName" align="center" />
            <el-table-column label="用量" align="center" width="160">
              <template slot-scope="scope">
                <el-input-number
                  v-model="scope.row.quantity"
                  :min="0"
                  size="small"
                  style="width: 120px;"
                />
              </template>
            </el-table-column>
            <el-table-column label="单位" prop="unit" align="center" width="80" />
            <el-table-column label="备注" align="center">
              <template slot-scope="scope">
                <el-input v-model="scope.row.remark" size="small" placeholder="备注" />
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="80">
              <template slot-scope="scope">
                <el-button
                  type="text"
                  icon="el-icon-delete"
                  style="color: #f56c6c;"
                  @click="removeIngredientRow(scope.$index)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="排序" prop="sort">
            <el-input-number v-model="form.sort" :min="0" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="是否启用" prop="enabled">
            <el-switch v-model="form.enabled" :active-value="true" :inactive-value="false" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="cancel">取 消</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { addDish, editDish, queryPackages } from '@/api/dish'
import { queryIngredients } from '@/api/dishIngredient'

export default {
  name: 'DishForm',
  data() {
    return {
      dialogVisible: false,
      title: '',
      packageOptions: [],
      form: {
        id: null,
        name: '',
        cookingMethod: '',
        imageUrl: '',
        dishType: 'MAIN',
        mealTypes: ['LUNCH'],
        mealPackages: [],
        schedule: [],
        sort: 0,
        enabled: true,
        ingredientList: []
      },
      rules: {
        name: [{ required: true, message: '菜品名称不能为空', trigger: 'blur' }],
        dishType: [{ required: true, message: '菜品类型不能为空', trigger: 'change' }],
        mealTypes: [{ required: true, message: '餐次不能为空', trigger: 'change' }]
      },
      selectedWeeks: [],
      selectedDays: [],
      // 配料搜索相关
      selectIngredientId: null,
      ingredientOptions: [],
      ingredientLoading: false,
      // 缓存全部配料选项map，用于追加到列表时获取详情
      ingredientMap: {}
    }
  },
  watch: {
    selectedWeeks() {
      this.updateSchedule()
    },
    selectedDays() {
      this.updateSchedule()
    }
  },
  methods: {
    loadPackages() {
      queryPackages().then(response => {
        this.packageOptions = response || []
      })
    },
    handleAdd() {
      this.title = '新增菜品'
      this.resetForm()
      this.loadPackages()
      this.dialogVisible = true
    },
    handleUpdate(row) {
      this.title = '编辑菜品'
      // 列表接口已返回完整 ingredientList 和 mealPackageDetails，直接使用
      this.form = JSON.parse(JSON.stringify(row))
      if (!this.form.ingredientList) {
        this.form.ingredientList = []
      }
      // 将 mealPackageDetails 中的 id 反填到 mealPackages（DB 存的是 ID）
      if (this.form.mealPackageDetails && this.form.mealPackageDetails.length > 0) {
        this.form.mealPackages = this.form.mealPackageDetails.map(pkg => pkg.id)
      } else {
        this.form.mealPackages = []
      }
      this.parseSchedule()
      this.loadPackages()
      this.dialogVisible = true
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (valid) {
          const action = this.form.id ? editDish : addDish
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
        cookingMethod: '',
        imageUrl: '',
        dishType: 'MAIN',
        mealTypes: ['LUNCH'],
        mealPackages: [],
        schedule: [],
        sort: 0,
        enabled: true,
        ingredientList: []
      }
      this.selectedWeeks = []
      this.selectedDays = []
      this.selectIngredientId = null
      this.ingredientOptions = []
      if (this.$refs.form) {
        this.$refs.form.resetFields()
      }
    },
    updateSchedule() {
      const schedule = []
      this.selectedWeeks.forEach(week => {
        this.selectedDays.forEach(day => {
          schedule.push(`${week}-${day}`)
        })
      })
      this.form.schedule = schedule
    },
    parseSchedule() {
      const weeks = new Set()
      const days = new Set()
      if (this.form.schedule && this.form.schedule.length) {
        this.form.schedule.forEach(s => {
          const [week, day] = s.split('-')
          weeks.add(week)
          days.add(day)
        })
      }
      this.selectedWeeks = Array.from(weeks)
      this.selectedDays = Array.from(days)
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
.schedule-container {
  border: 1px solid #eee;
  padding: 15px;
  border-radius: 4px;
}
.schedule-weeks,
.schedule-days {
  margin-bottom: 10px;
}
.schedule-weeks .label,
.schedule-days .label {
  display: inline-block;
  width: 80px;
  font-weight: 500;
}
.el-checkbox-group {
  display: inline-block;
}
.ingredient-section {
  border: 1px solid #eee;
  padding: 15px;
  border-radius: 4px;
}
.ingredient-add-row {
  display: flex;
  align-items: center;
  margin-bottom: 5px;
}
</style>
