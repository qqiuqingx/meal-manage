<template>
  <div class="business-dish-candidate-card">
    <div class="candidate-summary">
      <el-tag size="mini" type="success">可用 {{ result.availableCandidateCount || 0 }}</el-tag>
      <el-tag size="mini" type="warning">已过滤 {{ result.filteredCandidateCount || 0 }}</el-tag>
      <span v-if="result.truncated" class="truncated-note">仅展示前 20 条</span>
    </div>
    <el-table :data="items" size="mini" border>
      <el-table-column prop="dishName" label="候选菜品" min-width="130" />
      <el-table-column prop="dishTypeCode" label="类型" width="90" />
      <el-table-column label="配料摘要" min-width="180">
        <template slot-scope="{ row }">{{ ingredients(row) }}</template>
      </el-table-column>
      <el-table-column label="当前状态" width="90">
        <template slot-scope="{ row }">
          <el-tag size="mini" :type="row.available ? 'success' : 'warning'">{{ row.available ? '可用' : '已过滤' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过滤原因" min-width="180">
        <template slot-scope="{ row }">{{ (row.filterReasons || []).join('、') || '-' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
export default {
  name: 'BusinessDishCandidateCard',
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

<style scoped>
.candidate-summary { margin-bottom: 8px; display: flex; gap: 8px; align-items: center; }
.truncated-note { color: #909399; font-size: 12px; }
</style>
