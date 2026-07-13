<template>
  <div class="customer-candidate-card">
    <el-table :data="items" size="mini" border>
      <el-table-column prop="customerCode" label="客户编号" min-width="120" />
      <el-table-column prop="customerName" label="客户姓名" min-width="100" />
      <el-table-column prop="maskedPhone" label="联系电话" min-width="130" />
      <el-table-column label="操作" width="96" fixed="right">
        <template slot-scope="{ row }">
          <el-button type="primary" size="mini" plain @click="$emit('select', row)">选择</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="candidate-meta">
      共 {{ result.total || items.length }} 个候选
      <span v-if="result.truncated">，已按安全上限截断</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'CustomerCandidateCard',
  props: {
    result: { type: Object, required: true }
  },
  computed: {
    items() {
      return this.result.items || []
    }
  }
}
</script>

<style scoped>
.candidate-meta {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
