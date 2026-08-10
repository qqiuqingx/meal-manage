<template>
  <div class="chat-footer">
    <div class="composer">
      <el-input
        :value="draft"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 4 }"
        resize="none"
        :disabled="archived"
        placeholder="例如：B3303 还有多少餐、今天午餐排了吗、这笔订单什么时候到期"
        @input="$emit('input', $event)"
        @keyup.enter.native.exact.prevent="$emit('send')"
      />
      <el-button
        type="primary"
        :loading="loading"
        :disabled="archived"
        icon="el-icon-s-promotion"
        @click="$emit('send')"
      >
        发送
      </el-button>
    </div>
    <div v-if="archived" class="archived-hint">当前会话已归档，恢复后可继续。</div>
  </div>
</template>

<script>
export default {
  name: 'AgentComposer',
  props: {
    draft: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    archived: { type: Boolean, default: false }
  }
}
</script>

<style scoped>
.chat-footer {
  background: #fff;
  border-top: 1px solid #ebeef5;
  box-shadow: 0 -8px 24px rgba(17, 24, 39, 0.06);
}

.composer {
  display: grid;
  grid-template-columns: 1fr 96px;
  gap: 12px;
  padding: 12px 20px 20px;
  align-items: stretch;
}

.composer .el-button {
  height: 54px;
}

.archived-hint {
  padding: 0 20px 12px;
  color: #909399;
  font-size: 12px;
  text-align: center;
}

@media (max-width: 768px) {
  .chat-footer {
    box-shadow: 0 -6px 16px rgba(17, 24, 39, 0.05);
  }

  .composer {
    grid-template-columns: 1fr;
  }

  .composer .el-button {
    height: 40px;
  }
}
</style>
