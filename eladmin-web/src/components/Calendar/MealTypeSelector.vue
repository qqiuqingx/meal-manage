<template>
  <div class="meal-type-selector">
    <div class="meal-type-selector__header">
      <span class="meal-type-selector__date">{{ formattedDate }}</span>
      <span v-if="dayName" class="meal-type-selector__day-name">{{ dayName }}</span>
    </div>

    <el-checkbox-group v-model="localMealTypes" class="meal-type-selector__options">
      <el-checkbox :label="MealType.BREAKFAST">
        <span class="meal-type-label">
          <span class="meal-type-dot" :style="{ backgroundColor: MealTypeColor[MealType.BREAKFAST] }" />
          {{ MealTypeName[MealType.BREAKFAST] }}
        </span>
      </el-checkbox>

      <el-checkbox :label="MealType.LUNCH">
        <span class="meal-type-label">
          <span class="meal-type-dot" :style="{ backgroundColor: MealTypeColor[MealType.LUNCH] }" />
          {{ MealTypeName[MealType.LUNCH] }}
        </span>
      </el-checkbox>

      <el-checkbox :label="MealType.DINNER">
        <span class="meal-type-label">
          <span class="meal-type-dot" :style="{ backgroundColor: MealTypeColor[MealType.DINNER] }" />
          {{ MealTypeName[MealType.DINNER] }}
        </span>
      </el-checkbox>
    </el-checkbox-group>

    <div class="meal-type-selector__footer">
      <div class="meal-type-selector__footer-left">
        <span class="meal-type-selector__summary">{{ summary }}</span>
        <span v-if="localMealTypes.length === 0" class="meal-type-selector__warning">
          请至少选择一个餐次
        </span>
      </div>
      <el-button-group>
        <el-button size="small" @click="handleCancel">取消</el-button>
        <el-button size="small" type="primary" :disabled="localMealTypes.length === 0" @click="handleSave">保存</el-button>
      </el-button-group>
    </div>
  </div>
</template>

<script>
import { MealType, MealTypeColor, MealTypeName, formatDate, getDayNameFull } from '@/utils/calendar'

export default {
  name: 'MealTypeSelector',
  props: {
    // 日期对象
    date: {
      type: Date,
      required: true
    },
    // 当前已选中的餐次
    value: {
      type: Array,
      default: () => []
    },
    // 是否禁用
    disabled: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      localMealTypes: [],
      MealType,
      MealTypeColor,
      MealTypeName
    }
  },
  computed: {
    formattedDate() {
      return formatDate(this.date)
    },
    dayName() {
      return getDayNameFull(this.date.getDay())
    },
    summary() {
      const count = this.localMealTypes.length
      if (count === 0) return '未选择餐次'
      return `已选择 ${count} 个餐次`
    }
  },
  watch: {
    value: {
      handler(val) {
        this.localMealTypes = [...val]
      },
      immediate: true
    }
  },
  methods: {
    handleSave() {
      this.$emit('input', [...this.localMealTypes])
      this.$emit('save', this.localMealTypes)
    },
    handleCancel() {
      this.localMealTypes = [...this.value]
      this.$emit('cancel')
    }
  }
}
</script>

<style scoped>
.meal-type-selector {
  background-color: #ffffff;
  border-radius: 4px;
}

.meal-type-selector__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #f5f7fa;
  border-radius: 4px 4px 0 0;
}

.meal-type-selector__date {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.meal-type-selector__day-name {
  font-size: 14px;
  color: #909399;
}

.meal-type-selector__options {
  padding: 16px;
}

.meal-type-selector__options .el-checkbox {
  display: flex;
  margin-bottom: 12px;
  margin-right: 0;
}

.meal-type-selector__options .el-checkbox:last-child {
  margin-bottom: 0;
}

.meal-type-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meal-type-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.meal-type-selector__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  border-radius: 0 0 4px 4px;
}

.meal-type-selector__footer-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meal-type-selector__summary {
  font-size: 13px;
  color: #909399;
}

.meal-type-selector__warning {
  font-size: 12px;
  color: #f56c6c;
}
</style>
