<template>
  <el-collapse class="customer-diet-info" accordion>
    <el-collapse-item name="medical">
      <template slot="title">医嘱</template>
      <div class="customer-diet-info__text">{{ customer.medicalRequirements || '—' }}</div>
    </el-collapse-item>
    <el-collapse-item name="wants">
      <template slot="title">菜品特殊要求</template>
      <div class="customer-diet-info__group">
        <div class="customer-diet-info__label">原文</div>
        <div v-if="rawBlocks(customer.dishRequirementsRaw).length">
          <div v-for="(block, index) in rawBlocks(customer.dishRequirementsRaw)" :key="'w' + index" class="customer-diet-info__text">{{ block }}</div>
        </div>
        <div v-else class="customer-diet-info__text">—</div>
      </div>
    </el-collapse-item>
    <el-collapse-item name="restrictions">
      <template slot="title">禁忌食物</template>
      <div class="customer-diet-info__group">
        <div class="customer-diet-info__label">原文</div>
        <div v-if="rawBlocks(customer.dietaryRestrictionsRaw).length">
          <div v-for="(block, index) in rawBlocks(customer.dietaryRestrictionsRaw)" :key="'r' + index" class="customer-diet-info__text">{{ block }}</div>
        </div>
        <div v-else class="customer-diet-info__text">—</div>
      </div>
      <div class="customer-diet-info__group">
        <div class="customer-diet-info__label">已确认对象</div>
        <el-tag v-for="item in items(customer.dietaryRestrictions)" :key="item.type + ':' + item.id" size="mini" type="danger" class="customer-diet-info__tag">
          {{ typeLabel(item.type) }}：{{ item.name || ('#' + item.id) }}
        </el-tag>
        <span v-if="!items(customer.dietaryRestrictions).length" class="customer-diet-info__text">—</span>
      </div>
    </el-collapse-item>
    <el-collapse-item name="postoperative">
      <template slot="title">术后信息</template>
      <div class="customer-diet-info__text">{{ customer.postoperativeInfo || '—' }}</div>
    </el-collapse-item>
  </el-collapse>
</template>

<script>
export default {
  name: 'CustomerDietInfo',
  props: {
    customer: {
      type: Object,
      default: () => ({})
    }
  },
  methods: {
    rawBlocks(value) {
      return Array.isArray(value) ? value.filter(item => typeof item === 'string' && item.length > 0) : []
    },
    items(value) {
      return Array.isArray(value) ? value.filter(item => item && item.type && item.id != null) : []
    },
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
.customer-diet-info {
  border-top: 0;
}
.customer-diet-info__group + .customer-diet-info__group {
  margin-top: 10px;
}
.customer-diet-info__label {
  margin-bottom: 4px;
  color: #909399;
  font-size: 12px;
}
.customer-diet-info__text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.customer-diet-info__tag {
  margin: 0 4px 4px 0;
}
</style>
