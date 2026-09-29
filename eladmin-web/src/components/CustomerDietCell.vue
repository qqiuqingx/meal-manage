<template>
  <div class="customer-diet-cell">
    <div class="customer-diet-cell__label">原文</div>
    <div v-if="rawBlocks.length">
      <div v-for="(block, index) in rawBlocks" :key="index" class="customer-diet-cell__text">{{ block }}</div>
    </div>
    <div v-else class="customer-diet-cell__text">—</div>
    <div class="customer-diet-cell__label">已确认对象</div>
    <div v-if="matchedItems.length" class="customer-diet-cell__text">
      <span v-for="(item, index) in matchedItems" :key="item.type + ':' + item.id">
        {{ index ? '、' : '' }}{{ typeLabel(item.type) }}：{{ item.name || ('#' + item.id) }}
      </span>
    </div>
    <div v-else class="customer-diet-cell__text">—</div>
  </div>
</template>

<script>
export default {
  name: 'CustomerDietCell',
  props: {
    raw: { type: Array, default: () => [] },
    items: { type: Array, default: () => [] }
  },
  computed: {
    rawBlocks() {
      return (this.raw || []).filter(item => typeof item === 'string' && item.length > 0)
    },
    matchedItems() {
      return (this.items || []).filter(item => item && item.type && item.id != null)
    }
  },
  methods: {
    /** 返回已确认对象的业务类型名称。
     * @param {string} type 对象类型
     * @returns {string} 展示名称
     */
    typeLabel(type) {
      const labels = {
        DISH: '菜品',
        INGREDIENT: '配料',
        INGREDIENT_TAG: '配料标签',
        INGREDIENT_CATEGORY: '配料分类',
        DISH_TAG: '菜品标签'
      }
      return labels[type] || type || '对象'
    }
  }
}
</script>

<style scoped>
.customer-diet-cell__label {
  color: #909399;
  font-size: 12px;
  margin-top: 6px;
}
.customer-diet-cell__label:first-child {
  margin-top: 0;
}
.customer-diet-cell__text {
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
