<template>
  <div class="import-diet-preview">
    <div class="import-diet-preview__brief" :title="draft.medicalRequirements || '—'">医嘱：{{ draft.medicalRequirements || '—' }}</div>
    <div v-if="requirementsRaw" class="import-diet-preview__brief" :title="requirementsRaw">菜品特殊需求原文：{{ requirementsRaw }}</div>
    <div v-if="matches.length" class="import-diet-preview__counts">
      <span>禁忌匹配：</span>
      <span class="import-diet-preview__success">唯一匹配 {{ counts.unique }}</span>
      <span v-if="counts.multi" class="import-diet-preview__warning">同名多对象 {{ counts.multi }}</span>
      <span v-if="counts.unmatched" class="import-diet-preview__warning">未匹配 {{ counts.unmatched }}</span>
    </div>
    <div v-else class="import-diet-preview__empty">禁忌无匹配项；菜品特殊需求仅保存原文</div>
    <div v-if="restrictedNames" class="import-diet-preview__brief" :title="restrictedNames">禁忌：{{ restrictedNames }}</div>
    <el-popover placement="left" width="600" trigger="click">
      <div class="import-diet-preview__details">
        <div class="import-diet-preview__heading">{{ draft.customerCode }} · 医嘱与饮食明细</div>
        <div class="import-diet-preview__field"><strong>医嘱：</strong>{{ draft.medicalRequirements || '—' }}</div>
        <div class="import-diet-preview__field"><strong>成交时间：</strong>{{ draft.dealTimeSource || '空' }} → {{ dietOnly ? '不修改历史订单' : (draft.dealTime || (draft.dealTimeAtConfirmation ? '确认时填入' : '—')) }}</div>
        <div class="import-diet-preview__field"><strong>术后：</strong>{{ draft.postoperativeInfo || '—' }}</div>
        <div class="import-diet-preview__field"><strong>菜品特殊需求原文（仅保存原文）：</strong>{{ requirementsRaw || '—' }}</div>
        <div class="import-diet-preview__field"><strong>禁忌原文：</strong>{{ (draft.dietaryRestrictionsRaw || []).join(' ｜ ') || '—' }}</div>
        <div v-for="match in matches" :key="match.sourceKey" class="import-diet-preview__match">
          <div>
            <strong>禁忌 第{{ match.sourceRow }}行：</strong>
            <span>{{ match.rawText }}</span>
            <span v-if="match.lookupText !== match.rawText"> → {{ match.lookupText }}</span>
          </div>
          <div v-if="match.status === 'UNIQUE'" class="import-diet-preview__success">唯一匹配：{{ optionLabel(match.selectedItems[0]) }}</div>
          <div v-else-if="match.status === 'MULTI'" class="import-diet-preview__success">
            确认后将同时录入 {{ (match.selectedItems || []).length }} 个对象：
            <span v-for="(item, index) in (match.selectedItems || [])" :key="item.type + ':' + item.id">{{ index ? '、' : '' }}{{ optionLabel(item) }}</span>
          </div>
          <div v-else class="import-diet-preview__warning">未匹配到字典对象，完整原文仍会保存</div>
        </div>
      </div>
      <el-button slot="reference" type="text" size="mini">查看明细</el-button>
    </el-popover>
  </div>
</template>

<script>
export default {
  name: 'CustomerImportDietPreview',
  props: {
    draft: { type: Object, required: true },
    dietOnly: { type: Boolean, default: false },
    optionLabel: { type: Function, required: true }
  },
  computed: {
    /** 返回当前客户 F 列禁忌的逐词匹配结果，无匹配数据时返回空数组。 */
    matches() {
      return this.draft.dietMatches || []
    },
    /** 汇总唯一、同名多对象和未匹配词项数量，便于在表格中直接核对。 */
    counts() {
      return this.matches.reduce((counts, match) => {
        if (match.status === 'UNIQUE') counts.unique++
        else if (match.status === 'MULTI') counts.multi++
        else counts.unmatched++
        return counts
      }, { unique: 0, multi: 0, unmatched: 0 })
    },
    /** 返回 D 列完整原文摘要；导入不生成菜品特殊需求匹配对象。 */
    requirementsRaw() {
      return (this.draft.dishRequirementsRaw || []).join(' ｜ ')
    },
    /** 返回禁忌对象的名称摘要；详细类型和分类路径在明细中保留。 */
    restrictedNames() {
      return this.selectedNames()
    }
  },
  methods: {
    /**
     * 汇总禁忌匹配对象的名称，同名对象只在摘要中展示一次。
     * @returns {string} 用顿号连接的名称摘要
     */
    selectedNames() {
      const names = new Set()
      this.matches.forEach(match => {
        const items = match.selectedItems || []
        items.forEach(item => names.add(item.name || ('#' + item.id)))
      })
      return Array.from(names).join('、')
    }
  }
}
</script>

<style scoped>
.import-diet-preview {
  line-height: 1.5;
}
.import-diet-preview__brief {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.import-diet-preview__counts {
  display: flex;
  flex-wrap: wrap;
  gap: 0 10px;
}
.import-diet-preview__empty {
  color: #909399;
}
.import-diet-preview__details {
  max-height: 400px;
  overflow-y: auto;
  overflow-wrap: anywhere;
  line-height: 1.5;
}
.import-diet-preview__heading {
  margin-bottom: 10px;
  font-weight: 600;
}
.import-diet-preview__field {
  margin-bottom: 6px;
  white-space: pre-wrap;
}
.import-diet-preview__match {
  padding: 6px 0;
  border-top: 1px solid #ebeef5;
}
.import-diet-preview__success {
  color: #67c23a;
}
.import-diet-preview__warning {
  color: #e6a23c;
}
</style>
