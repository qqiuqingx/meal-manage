<template>
  <aside class="session-panel">
    <div class="session-toolbar">
      <el-input
        :value="keyword"
        size="small"
        clearable
        placeholder="搜索标题、客户编号或最近内容"
        @input="$emit('keyword-input', $event)"
        @clear="$emit('keyword-input', '')"
        @keyup.enter.native="$emit('search')"
      >
        <i slot="prefix" class="el-input__icon el-icon-search" />
      </el-input>
    </div>
    <div class="session-view-tabs">
      <el-radio-group :value="archived" size="mini" @change="$emit('view-change', $event)">
        <el-radio-button :label="false">进行中</el-radio-button>
        <el-radio-button :label="true">已归档</el-radio-button>
      </el-radio-group>
    </div>
    <div class="session-list">
      <div
        v-for="session in sessions"
        :key="session.sessionId"
        class="session-item"
        :class="{ 'session-item-active': session.sessionId === activeSessionId }"
        @click="$emit('select', session.sessionId)"
      >
        <div class="session-item-title">{{ session.title || sessionOptionLabel(session) }}</div>
        <div class="session-item-meta">
          <span v-if="session.customerCode">客户 {{ session.customerCode }}</span>
          <span v-else-if="session.orderCode">订单 {{ session.orderCode }}</span>
          <span v-if="formatSessionTime(session.lastMessageTime)">{{ formatSessionTime(session.lastMessageTime) }}</span>
        </div>
        <div v-if="session.lastSummary" class="session-item-summary">{{ session.lastSummary }}</div>
      </div>
      <el-empty v-if="!loading && !sessions.length" description="暂无会话" :image-size="64" />
      <el-button
        v-if="hasMore"
        class="session-load-more"
        type="text"
        :loading="loading"
        @click="$emit('load-more')"
      >
        加载更多
      </el-button>
    </div>
  </aside>
</template>

<script>
export default {
  name: 'AgentSessionSidebar',
  props: {
    sessions: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    keyword: { type: String, default: '' },
    archived: { type: Boolean, default: false },
    activeSessionId: { type: String, default: null },
    hasMore: { type: Boolean, default: false },
    sessionOptionLabel: { type: Function, default: session => (session && session.title) || '新会话' },
    formatSessionTime: { type: Function, default: value => value || '' }
  }
}
</script>

<style scoped>
.session-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
  border-right: 1px solid #ebeef5;
  background: #fafbfd;
}

.session-toolbar {
  padding: 14px;
  border-bottom: 1px solid #ebeef5;
}

.session-view-tabs {
  padding: 10px 14px 0;
}

.session-view-tabs .el-radio-group {
  display: flex;
}

.session-view-tabs .el-radio-button {
  flex: 1;
}

.session-view-tabs .el-radio-button :deep(.el-radio-button__inner) {
  width: 100%;
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 12px;
}

.session-item {
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
}

.session-item + .session-item {
  margin-top: 4px;
}

.session-item:hover {
  background: #f0f7ff;
}

.session-item-active {
  background: #ecf5ff;
}

.session-item-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.5;
  color: #303133;
}

.session-item-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
  overflow: hidden;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

.session-item-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item-summary {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: #606266;
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.session-load-more {
  display: block;
  width: 100%;
  margin-top: 8px;
}
</style>
