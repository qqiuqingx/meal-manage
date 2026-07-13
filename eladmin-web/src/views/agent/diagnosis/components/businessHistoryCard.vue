<template>
  <div class="business-history-card">
    <el-timeline>
      <el-timeline-item v-for="item in items" :key="item.verificationId || item.refundId" :timestamp="item.operateTime" placement="top">
        <div v-if="type === 'verification'">核销 {{ item.verificationCount || 0 }} 餐 · {{ mealText(item.mealTypeCode) }} · 订单 {{ item.orderId || '-' }}<span v-if="item.refunded">（已退餐）</span></div>
        <div v-else>退早餐 {{ item.refundBreakfastCount || 0 }} 餐，退午晚餐 {{ item.refundLunchDinnerCount || 0 }} 餐 · 订单 {{ item.orderId || '-' }}<span v-if="item.refundReason"> · {{ item.refundReason }}</span></div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>
<script>
export default {
  name: 'BusinessHistoryCard',
  props: { result: { type: Object, required: true }, type: { type: String, required: true }},
  computed: { items() { return this.result.items || [] } },
  methods: { mealText(value) { return { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }[value] || value || '-' } }
}
</script>
