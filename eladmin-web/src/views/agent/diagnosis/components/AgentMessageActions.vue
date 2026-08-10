<template>
  <div v-if="hasActions" class="agent-message-actions">
    <el-button
      v-for="reply in visibleQuickReplies"
      :key="`reply-${reply}`"
      size="mini"
      plain
      :disabled="loading"
      @click="$emit('quick-reply', reply)"
    >
      {{ reply }}
    </el-button>
    <el-button
      v-if="canRetry"
      size="mini"
      type="warning"
      plain
      :loading="message.retrying === true"
      :disabled="loading && message.retrying !== true"
      @click="$emit('retry')"
    >
      重试本条
    </el-button>
    <el-button
      v-if="canCopy"
      size="mini"
      plain
      @click="$emit('copy')"
    >
      复制结论
    </el-button>
    <el-button
      v-for="target in navigationTargets"
      :key="`target-${target.kind}`"
      size="mini"
      plain
      @click="$emit('navigate', target)"
    >
      {{ target.label }}
    </el-button>
  </div>
</template>

<script>
export default {
  name: 'AgentMessageActions',
  props: {
    message: { type: Object, default: () => ({}) },
    latest: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    navigationTargets: { type: Array, default: () => [] }
  },
  computed: {
    /** 只为最后一条助手消息提供快捷追问，避免历史消息堆满按钮。 */
    visibleQuickReplies() {
      if (!this.latest || this.loading || this.message.status === 'ERROR') return []
      return Array.isArray(this.message.quickReplies)
        ? this.message.quickReplies.filter(Boolean).slice(0, 6)
        : []
    },
    /** 错误消息只有保存了原始问题时才允许重试。 */
    canRetry() {
      return this.message.status === 'ERROR' && typeof this.message.retryText === 'string' &&
        this.message.retryText.trim() !== ''
    },
    /** 已回答消息只复制面向客服的正文，不复制技术字段。 */
    canCopy() {
      return this.message.status === 'ANSWERED' && typeof this.message.content === 'string' &&
        this.message.content.trim() !== ''
    },
    /** 计算动作区域是否有任何可见内容，避免空白占位。 */
    hasActions() {
      return this.visibleQuickReplies.length > 0 || this.canRetry || this.canCopy ||
        this.navigationTargets.length > 0
    }
  }
}
</script>

<style scoped>
.agent-message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
</style>
