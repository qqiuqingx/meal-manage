<template>
  <div class="business-dish-card">
    <el-table :data="items" size="mini" border>
      <el-table-column prop="dishName" label="菜品" min-width="130" />
      <el-table-column prop="dishTypeName" label="类型" width="100" />
      <el-table-column label="适用餐次" min-width="120">
        <template slot-scope="{ row }">{{ (row.mealTypes || []).join('、') || '-' }}</template>
      </el-table-column>
      <el-table-column label="配料摘要" min-width="220">
        <template slot-scope="{ row }">{{ ingredients(row) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>
<script>
export default {
  name: 'BusinessDishCard',
  props: { result: { type: Object, required: true }},
  computed: { items() { return this.result.items || [] } },
  methods: {
    ingredients(row) {
      const text = (row.ingredientNames || []).join('、') || '-'
      return row.ingredientsTruncated ? `${text}（已截断）` : text
    }
  }
}
</script>
