<template>
  <div class="business-scheduled-menu-card">
    <div v-for="group in groups" :key="group.mealTypeCode" class="menu-group">
      <div class="menu-group__title">{{ group.mealTypeName || group.mealTypeCode }}（{{ group.total || 0 }} 道）</div>
      <business-dish-card :result="{ items: group.items || [] }" />
      <div v-if="!(group.items || []).length" class="menu-group__empty">该餐次暂无已配置的公共排期菜品</div>
    </div>
  </div>
</template>

<script>
import BusinessDishCard from './businessDishCard'

export default {
  name: 'BusinessScheduledMenuCard',
  components: { BusinessDishCard },
  props: { result: { type: Object, required: true }},
  computed: {
    groups() { return Array.isArray(this.result.groups) ? this.result.groups : [] }
  }
}
</script>

<style scoped>
.menu-group + .menu-group { margin-top: 14px; }
.menu-group__title { margin-bottom: 8px; font-weight: 600; }
.menu-group__empty { color: #909399; font-size: 13px; }
</style>
