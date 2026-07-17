<template>
  <div class="customer-overview-card">
    <el-alert v-if="!result.present" title="未找到客户，或当前无权查看客户档案。" type="warning" :closable="false" show-icon />
    <template v-else>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="客户编号">{{ result.customerCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户姓名">{{ result.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="档案创建时间">{{ formatTime(result.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="首次购买时间">{{ formatTime(result.firstPurchaseTime) }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ result.maskedPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="进行中订单">{{ result.activeOrderCount || 0 }} 笔</el-descriptions-item>
        <el-descriptions-item label="剩余早餐">{{ balance.remainingBreakfast || 0 }} 餐</el-descriptions-item>
        <el-descriptions-item label="剩余午晚餐">{{ balance.remainingLunchDinner || 0 }} 餐</el-descriptions-item>
      </el-descriptions>
      <div v-if="result.addresses && result.addresses.length" class="section"><b>配送地址：</b><span v-for="(item, index) in result.addresses" :key="index" class="tag">{{ item.addressTypeName || '地址' }}：{{ item.maskedAddress || '-' }}</span></div>
      <div v-if="result.allergyTags && result.allergyTags.length" class="section"><b>过敏标签：</b><el-tag v-for="item in result.allergyTags" :key="item" type="warning" size="mini" class="tag">{{ item }}</el-tag></div>
      <div v-if="result.excludedDishIds && result.excludedDishIds.length" class="section"><b>排除菜品：</b><el-tag v-for="item in result.excludedDishIds" :key="item" type="info" size="mini" class="tag">菜品 #{{ item }}</el-tag></div>
      <div v-if="result.specialRequirements" class="section"><b>特殊要求：</b>{{ result.specialRequirements }}</div>
      <div v-if="result.packages && result.packages.length" class="section"><b>签约套餐：</b><el-tag v-for="item in result.packages" :key="item.orderId" :type="item.active ? 'success' : 'info'" size="mini" class="tag">{{ item.parentPackageName || item.childPackageName || '未命名套餐' }}</el-tag></div>
      <div v-if="result.latestVerification || result.latestRefund" class="section">
        <b>最近业务记录：</b>
        <span v-if="result.latestVerification" class="tag">核销 {{ mealTypeText(result.latestVerification.mealTypeCode) }} {{ result.latestVerification.verificationCount || 0 }} 餐</span>
        <span v-if="result.latestRefund" class="tag">退餐 早餐 {{ result.latestRefund.refundBreakfastCount || 0 }} 餐、午晚餐 {{ result.latestRefund.refundLunchDinnerCount || 0 }} 餐</span>
      </div>
    </template>
  </div>
</template>
<script>
export default {
  name: 'CustomerOverviewCard',
  props: {
    result: { type: Object, required: true }
  },
  computed: {
    balance() {
      return this.result.mealBalance || {}
    }
  },
  methods: {
    formatTime(value) { return value ? String(value).replace('T', ' ') : '-' },
    mealTypeText(value) { return ({ BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' })[value] || value || '-' }
  }
}
</script>
<style scoped>.section { margin-top: 12px; line-height: 28px; }.tag { margin-right: 6px; }</style>
