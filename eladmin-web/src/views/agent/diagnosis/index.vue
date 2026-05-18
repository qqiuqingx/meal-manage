<template>
  <div class="app-container agent-diagnosis">
    <el-card shadow="never">
      <div slot="header" class="diagnosis-header">
        <div>
          <div class="title">智能排查助手</div>
          <div class="subtitle">AI 基于当前业务数据和规则生成诊断建议，请结合证据人工确认。</div>
        </div>
      </div>

      <el-form ref="form" :model="form" :rules="rules" inline size="small" label-width="90px">
        <el-form-item label="客户ID" prop="customerId">
          <el-input v-model="form.customerId" clearable placeholder="客户ID" style="width: 140px" />
        </el-form-item>
        <el-form-item label="客户编号">
          <el-input v-model="form.customerCode" clearable placeholder="可选" style="width: 150px" />
        </el-form-item>
        <el-form-item label="日期" prop="recordDate">
          <el-date-picker v-model="form.recordDate" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" style="width: 150px" />
        </el-form-item>
        <el-form-item label="餐次" prop="mealType">
          <el-select v-model="form.mealType" placeholder="选择餐次" style="width: 120px">
            <el-option label="早餐" value="BREAKFAST" />
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">开始分析</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result" class="result-card" shadow="never">
      <div slot="header" class="result-header">
        <span>AI 诊断结果</span>
        <el-tag v-if="result.fallback" type="warning" size="small">兜底结果</el-tag>
        <el-tag v-else type="success" size="small">AI 建议</el-tag>
      </div>

      <el-alert
        title="以下为 AI 基于当前业务数据和规则生成的诊断建议，请结合证据人工确认。"
        type="warning"
        :closable="false"
        show-icon
      />

      <div class="summary">{{ result.summary || '暂无诊断摘要' }}</div>

      <div class="meta">
        <span>客户：{{ result.customerName || result.customerId || '-' }}</span>
        <span>日期：{{ result.recordDate || '-' }}</span>
        <span>餐次：{{ mealTypeText(result.mealType) }}</span>
        <span>规则版本：{{ shortDigest(result.ruleVersionDigest) }}</span>
        <span>模型：{{ result.modelName || '-' }}</span>
      </div>

      <el-empty v-if="!result.reasons || result.reasons.length === 0" description="暂无原因明细" />

      <el-collapse v-else>
        <el-collapse-item v-for="reason in result.reasons" :key="reason.code" :name="reason.code">
          <template slot="title">
            <el-tag :type="levelTag(reason.level)" size="small">{{ reason.level || 'LOW' }}</el-tag>
            <span class="reason-title">{{ reason.title || reason.code }}</span>
          </template>
          <div class="reason-desc">{{ reason.description }}</div>
          <div class="reason-suggestion">建议：{{ reason.suggestion || '请人工继续核对。' }}</div>
          <el-table v-if="reason.evidence && reason.evidence.length" :data="reason.evidence" size="mini" border>
            <el-table-column prop="label" label="证据" width="180" />
            <el-table-column prop="value" label="值" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script>
import { diagnoseMealPlan } from '@/api/agentDiagnosis'

export default {
  name: 'AgentDiagnosis',
  data() {
    return {
      loading: false,
      result: null,
      form: {
        customerId: '',
        customerCode: '',
        recordDate: '',
        mealType: 'LUNCH'
      },
      rules: {
        recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
        mealType: [{ required: true, message: '请选择餐次', trigger: 'change' }]
      }
    }
  },
  methods: {
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.loading = true
        diagnoseMealPlan(this.normalizeForm())
          .then(res => {
            this.result = res
          })
          .catch(() => {
            this.$message.error('智能排查服务暂不可用，请稍后重试')
          })
          .finally(() => {
            this.loading = false
          })
      })
    },
    normalizeForm() {
      return {
        customerId: this.form.customerId ? Number(this.form.customerId) : null,
        customerCode: this.form.customerCode || null,
        recordDate: this.form.recordDate,
        mealType: this.form.mealType
      }
    },
    mealTypeText(value) {
      const map = {
        BREAKFAST: '早餐',
        LUNCH: '午餐',
        DINNER: '晚餐'
      }
      return map[value] || value || '-'
    },
    levelTag(level) {
      if (level === 'HIGH') return 'danger'
      if (level === 'MEDIUM') return 'warning'
      return 'info'
    },
    shortDigest(value) {
      return value ? value.slice(0, 12) : '-'
    }
  }
}
</script>

<style scoped>
.agent-diagnosis .diagnosis-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.agent-diagnosis .title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.agent-diagnosis .subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.result-card {
  margin-top: 16px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.summary {
  margin: 16px 0;
  padding: 14px 16px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #303133;
  line-height: 1.7;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
}

.reason-title {
  margin-left: 8px;
  font-weight: 600;
}

.reason-desc,
.reason-suggestion {
  margin-bottom: 10px;
  color: #606266;
  line-height: 1.7;
}
</style>
