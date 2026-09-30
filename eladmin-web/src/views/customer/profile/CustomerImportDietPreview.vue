<template>
  <div class="import-diet-preview">
    <div class="import-diet-preview__brief" :title="draft.medicalRequirements || '—'">医嘱：{{ draft.medicalRequirements || '—' }}</div>
    <div v-if="matches.length" class="import-diet-preview__counts">
      <span class="import-diet-preview__success">唯一匹配 {{ counts.unique }}</span>
      <span v-if="counts.multi" class="import-diet-preview__warning">同名多对象 {{ counts.multi }}</span>
      <span v-if="counts.unmatched" class="import-diet-preview__warning">未匹配 {{ counts.unmatched }}</span>
    </div>
    <div v-else class="import-diet-preview__empty">想吃和禁忌原文为空，无需匹配</div>
    <div v-if="wantedNames" class="import-diet-preview__brief" :title="wantedNames">想吃：{{ wantedNames }}</div>
    <div v-if="restrictedNames" class="import-diet-preview__brief" :title="restrictedNames">禁忌：{{ restrictedNames }}</div>
    <el-popover placement="left" width="600" trigger="click">
      <div class="import-diet-preview__details">
        <div class="import-diet-preview__heading">{{ draft.customerCode }} · 医嘱与饮食明细</div>
        <div class="import-diet-preview__field"><strong>医嘱：</strong>{{ draft.medicalRequirements || '—' }}</div>
        <div class="import-diet-preview__field"><strong>成交时间：</strong>{{ draft.dealTimeSource || '空' }} → {{ dietOnly ? '不修改历史订单' : (draft.dealTime || (draft.dealTimeAtConfirmation ? '确认时填入' : '—')) }}</div>
        <div class="import-diet-preview__field"><strong>术后：</strong>{{ draft.postoperativeInfo || '—' }}</div>
        <div class="import-diet-preview__field"><strong>想吃原文：</strong>{{ (draft.dishRequirementsRaw || []).join(' ｜ ') || '—' }}</div>
        <div class="import-diet-preview__field"><strong>禁忌原文：</strong>{{ (draft.dietaryRestrictionsRaw || []).join(' ｜ ') || '—' }}</div>
        <div v-for="match in matches" :key="match.sourceKey" class="import-diet-preview__match">
          <div>
            <strong>{{ match.side === 'DISH_REQUIREMENTS' ? '想吃' : '禁忌' }} 第{{ match.sourceRow }}行：</strong>
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
    /** 返回当前客户的逐词匹配结果，无匹配数据时返回空数组。 */
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
    /** 返回想吃对象的名称摘要；详细类型和分类路径在明细中保留。 */
    wantedNames() {
      return this.selectedNames('DISH_REQUIREMENTS')
    },
    /** 返回禁忌对象的名称摘要；详细类型和分类路径在明细中保留。 */
    restrictedNames() {
      return this.selectedNames('DIETARY_RESTRICTIONS')
    }
  },
  methods: {
    /**
     * 按指定方向汇总匹配对象的名称，同名对象只在摘要中展示一次。
     * @param {string} side 想吃或禁忌方向
     * @returns {string} 用顿号连接的名称摘要
     */
    selectedNames(side) {
      const names = new Set()
      this.matches.filter(match => match.side === side).forEach(match => {
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
