<template>
  <div class="business-rule-card">
    <el-alert v-if="!result.present" title="当前问题尚未配置为可解释的业务规则。" type="info" :closable="false" show-icon />
    <template v-else>
      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="规则主题">{{ result.topic || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规则版本">{{ result.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="责任模块">{{ result.ownerModule || '-' }}</el-descriptions-item>
        <el-descriptions-item label="依据文档">{{ result.evidenceDocument || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ result.updatedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="rule-content">{{ result.content || '-' }}</div>
    </template>
  </div>
</template>
<script>
export default {
  name: 'BusinessRuleCard',
  props: { result: { type: Object, required: true }}
}
</script>
<style scoped>
.rule-content { margin-top: 12px; line-height: 1.7; white-space: pre-wrap; }
</style>
