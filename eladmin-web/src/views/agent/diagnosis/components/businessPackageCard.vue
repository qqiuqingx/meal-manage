<template>
  <div class="business-package-card">
    <el-collapse>
      <el-collapse-item v-for="item in items" :key="item.parentPackageId" :title="item.parentPackageName || '未命名套餐'">
        <el-table :data="item.subPackages || []" size="mini" border>
          <el-table-column prop="subPackageName" label="子套餐" min-width="130" />
          <el-table-column prop="meatCount" label="荤菜" width="70" />
          <el-table-column prop="vegCount" label="素菜" width="70" />
          <el-table-column label="配餐" min-width="110">
            <template slot-scope="{ row }">{{ row.includeSoup ? '含汤' : '无汤' }} / {{ row.includeRice ? '含饭' : '无饭' }}</template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>
<script>
export default {
  name: 'BusinessPackageCard',
  props: { result: { type: Object, required: true }},
  computed: { items() { return this.result.items || [] } }
}
</script>
