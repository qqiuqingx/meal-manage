<template>
  <div class="app-container agent-diagnosis">
    <div class="workspace-shell">
      <div class="workspace-header">
        <div>
          <div class="title">智能排查助手</div>
          <div class="subtitle">AI 基于当前业务数据和规则生成诊断建议，请结合证据人工确认。</div>
        </div>
        <div class="header-actions">
          <el-button size="small" plain icon="el-icon-plus" :loading="sessionCreating" @click="clearSession">新建会话</el-button>
          <el-button size="small" plain :disabled="!activeSessionId" :loading="sessionRenameLoading" @click="renameCurrentSession">改名</el-button>
          <el-button size="small" plain :disabled="!activeSessionId" @click="archiveCurrentSession">归档会话</el-button>
          <el-button size="small" plain @click="sendQuickReply('重新排查')">重新排查</el-button>
          <el-button size="small" plain icon="el-icon-refresh" :loading="sessionsLoading" @click="loadSessions(false)">刷新会话</el-button>
        </div>
      </div>

      <div class="workspace-body">
        <aside class="session-panel">
          <div class="session-toolbar">
            <el-input
              v-model="sessionKeyword"
              size="small"
              clearable
              placeholder="搜索客户编号或标题"
            >
              <i slot="prefix" class="el-input__icon el-icon-search" />
            </el-input>
          </div>
          <div class="session-list">
            <div
              v-for="session in filteredSessions"
              :key="session.sessionId"
              class="session-item"
              :class="{ 'session-item-active': session.sessionId === activeSessionId }"
              @click="handleSessionChange(session.sessionId)"
            >
              <div class="session-item-head">
                <span class="session-item-title">{{ session.title || sessionOptionLabel(session) }}</span>
                <el-tag size="mini" type="info">{{ stageText(session.stage) }}</el-tag>
              </div>
              <div class="session-item-meta">
                <span>{{ session.customerCode || '-' }}</span>
                <span>{{ mealTypeText(session.mealType) }}</span>
              </div>
              <div class="session-item-meta">
                <span>{{ session.recordDate || '-' }}</span>
                <span>{{ formatSessionTime(session.lastMessageTime) }}</span>
              </div>
              <div v-if="session.lastSummary" class="session-item-summary">{{ session.lastSummary }}</div>
            </div>
            <el-empty v-if="!sessionsLoading && !filteredSessions.length" description="暂无会话" :image-size="64" />
          </div>
        </aside>

        <div class="chat-panel">
          <div ref="messageList" class="message-list">
            <div
              v-for="(message, index) in messages"
              :key="index"
              class="message-row"
              :class="message.role === 'user' ? 'message-row-user' : 'message-row-assistant'"
            >
              <div class="message-bubble" :class="message.role === 'user' ? 'user-bubble' : 'assistant-bubble'">
                <div class="message-content">{{ message.content }}</div>
                <div v-if="message.stage || (message.missingSlots && message.missingSlots.length)" class="message-meta">
                  <el-tag v-if="message.stage" size="mini" type="info">阶段：{{ stageText(message.stage) }}</el-tag>
                  <el-tag
                    v-for="slot in message.missingSlots || []"
                    :key="slot"
                    size="mini"
                    type="warning"
                  >
                    待补充：{{ missingSlotText(slot) }}
                  </el-tag>
                </div>
                <div v-if="message.slots" class="slot-line">
                  <el-tag v-if="message.slots.customerId || message.slots.customerCode" size="mini">客户：{{ message.slots.customerCode || message.slots.customerId }}</el-tag>
                  <el-tag v-if="message.slots.recordDate" size="mini" type="info">日期：{{ message.slots.recordDate }}</el-tag>
                  <el-tag v-if="message.slots.mealType" size="mini" type="info">餐次：{{ mealTypeText(message.slots.mealType) }}</el-tag>
                </div>
                <div v-if="message.slotConfidence && Object.keys(message.slotConfidence).length" class="confidence-line">
                  <span v-for="(value, key) in message.slotConfidence" :key="key" class="confidence-item">
                    {{ slotLabel(key) }}：<el-tag size="mini" :type="confidenceTag(value)">{{ value }}</el-tag>
                  </span>
                </div>
                <div v-if="message.result" class="result-panel">
                  <div class="result-header">
                    <span>AI 诊断结果</span>
                    <div class="result-tags">
                      <el-tag size="small" :type="message.result.fallback ? 'warning' : 'success'">
                        {{ message.result.fallback ? '兜底结果' : 'AI 建议' }}
                      </el-tag>
                      <el-tag v-if="message.result.confidence" size="small" :type="confidenceTag(message.result.confidence)">
                        {{ message.result.confidence }}
                      </el-tag>
                    </div>
                  </div>
                  <el-alert
                    title="以下为 AI 基于当前业务数据和规则生成的诊断建议，请结合证据人工确认。"
                    type="warning"
                    :closable="false"
                    show-icon
                  />
                  <el-alert
                    v-if="message.result.fallbackReason"
                    class="fallback-alert"
                    :title="message.result.fallbackReason"
                    type="warning"
                    :closable="false"
                    show-icon
                  />
                  <div class="summary">{{ message.result.summary || '暂无诊断摘要' }}</div>
                  <div class="meta">
                    <span>客户：{{ message.result.customerName || message.result.customerId || '-' }}</span>
                    <span>日期：{{ message.result.recordDate || '-' }}</span>
                    <span>餐次：{{ mealTypeText(message.result.mealType) }}</span>
                    <span>规则版本：{{ shortDigest(message.result.ruleVersionDigest) }}</span>
                    <span>模型：{{ message.result.modelName || '-' }}</span>
                  </div>
                  <div class="result-links">
                    <el-button
                      size="mini"
                      plain
                      icon="el-icon-user"
                      :disabled="!hasCustomerTarget(message.result)"
                      @click="openCustomerProfile(message.result)"
                    >
                      客户档案
                    </el-button>
                    <el-button
                      size="mini"
                      plain
                      icon="el-icon-document"
                      :disabled="!hasCustomerTarget(message.result)"
                      @click="openCustomerOrders(message.result)"
                    >
                      客户订单
                    </el-button>
                    <el-button
                      size="mini"
                      plain
                      icon="el-icon-date"
                      :disabled="!message.result.recordDate || !message.result.mealType"
                      @click="openMealPlan(message.result)"
                    >
                      排餐详情
                    </el-button>
                  </div>
                  <div class="feedback-actions">
                    <el-button size="mini" plain icon="el-icon-check" @click="openFeedbackDialog(message.result, 'ACCEPTED')">采纳</el-button>
                    <el-button size="mini" plain icon="el-icon-warning-outline" @click="openFeedbackDialog(message.result, 'PARTIAL')">部分正确</el-button>
                    <el-button size="mini" plain icon="el-icon-close" @click="openFeedbackDialog(message.result, 'REJECTED')">不采纳</el-button>
                  </div>

                  <div v-if="message.result.nextActions && message.result.nextActions.length" class="action-block">
                    <div class="block-title">建议动作</div>
                    <ul class="action-list">
                      <li v-for="(action, actionIndex) in message.result.nextActions" :key="actionIndex">{{ action }}</li>
                    </ul>
                  </div>

                  <div v-if="message.result.actionDrafts && message.result.actionDrafts.length" class="draft-block">
                    <div class="block-title">动作草稿</div>
                    <el-alert
                      class="draft-alert"
                      title="动作草稿仅供人工确认，不会自动写入业务数据。"
                      type="info"
                      :closable="false"
                      show-icon
                    />
                    <el-table :data="message.result.actionDrafts" size="mini" border>
                      <el-table-column prop="title" label="动作" min-width="150" />
                      <el-table-column prop="riskLevel" label="风险" width="90">
                        <template slot-scope="{ row }">
                          <el-tag size="mini" :type="riskTag(row.riskLevel)">{{ row.riskLevel || 'LOW' }}</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column label="目标" min-width="160">
                        <template slot-scope="{ row }">
                          <span>{{ row.targetType || '-' }}：{{ row.targetId || '-' }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column prop="requiredPermission" label="权限" min-width="140" />
                      <el-table-column prop="confirmApi" label="确认接口" min-width="180" />
                      <el-table-column label="变更预览" min-width="220">
                        <template slot-scope="{ row }">
                          <div class="draft-preview">{{ compactJson(row.afterPreview) }}</div>
                        </template>
                      </el-table-column>
                      <el-table-column label="确认" width="100" fixed="right">
                        <template slot-scope="{ row }">
                          <el-button size="mini" type="primary" plain @click="openActionConfirm(row, message.result)">确认</el-button>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>

                  <el-empty v-if="!message.result.reasons || message.result.reasons.length === 0" description="暂无原因明细" />
                  <el-collapse v-else>
                    <el-collapse-item v-for="reason in message.result.reasons" :key="reason.code" :name="reason.code">
                      <template slot="title">
                        <el-tag :type="levelTag(reason.level)" size="small">{{ reason.level || 'LOW' }}</el-tag>
                        <span class="reason-title">{{ reason.title || reason.code }}</span>
                        <el-tag v-if="reason.confidence" size="mini" :type="confidenceTag(reason.confidence)">{{ reason.confidence }}</el-tag>
                      </template>
                      <div class="reason-desc">{{ reason.description }}</div>
                      <div class="reason-suggestion">建议：{{ reason.suggestion || '请人工继续核对。' }}</div>
                      <div v-if="reason.ruleIds && reason.ruleIds.length" class="reason-ruleids">
                        规则：{{ reason.ruleIds.join(' / ') }}
                      </div>
                      <ul v-if="reason.nextActions && reason.nextActions.length" class="action-list compact-list">
                        <li v-for="(action, reasonActionIndex) in reason.nextActions" :key="reasonActionIndex">{{ action }}</li>
                      </ul>
                      <el-table v-if="reason.evidence && reason.evidence.length" :data="reason.evidence" size="mini" border>
                        <el-table-column prop="label" label="证据" width="180" />
                        <el-table-column prop="value" label="值" />
                      </el-table>
                    </el-collapse-item>
                  </el-collapse>

                  <div class="inspector-actions">
                    <el-button size="mini" plain @click="togglePanel('toolSummaryExpanded')">
                      {{ toolSummaryExpanded ? '收起工具摘要' : '展开工具摘要' }}
                    </el-button>
                    <el-button size="mini" plain @click="togglePanel('traceExpanded')">
                      {{ traceExpanded ? '收起诊断链路' : '展开诊断链路' }}
                    </el-button>
                  </div>

                  <div v-if="toolSummaryExpanded && message.result.toolCallSummary && message.result.toolCallSummary.length" class="trace-block">
                    <div class="block-title">工具调用摘要</div>
                    <el-table :data="message.result.toolCallSummary" size="mini" border>
                      <el-table-column prop="toolName" label="工具" width="180" />
                      <el-table-column prop="eventType" label="事件" width="150" />
                      <el-table-column prop="resultCount" label="结果数" width="90" />
                      <el-table-column prop="costMs" label="耗时(ms)" width="110" />
                      <el-table-column prop="errorType" label="错误类型" />
                    </el-table>
                  </div>

                  <div v-if="traceExpanded && message.result.diagnosisTrace && message.result.diagnosisTrace.length" class="trace-block">
                    <div class="block-title">诊断链路</div>
                    <el-table :data="message.result.diagnosisTrace" size="mini" border>
                      <el-table-column prop="eventType" label="事件" width="180" />
                      <el-table-column prop="round" label="轮次" width="80" />
                      <el-table-column prop="toolName" label="工具" width="180" />
                      <el-table-column prop="toolNames" label="工具摘要" />
                      <el-table-column prop="costMs" label="耗时(ms)" width="110" />
                    </el-table>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="loading" class="message-row message-row-assistant">
              <div class="message-bubble assistant-bubble">
                <i class="el-icon-loading" />
                正在排查...
              </div>
            </div>
          </div>

          <div class="chat-footer">
            <div class="quick-replies">
              <el-button
                v-for="reply in quickReplies"
                :key="reply"
                size="mini"
                plain
                @click="sendQuickReply(reply)"
              >
                {{ reply }}
              </el-button>
            </div>

            <div class="composer">
              <el-input
                v-model="inputMessage"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                resize="none"
                placeholder="例如：帮我看下客户 C10001 明天午餐为什么没排出来"
                @keyup.enter.native.exact.prevent="sendMessage"
              />
              <el-button type="primary" :loading="loading" icon="el-icon-s-promotion" @click="sendMessage">发送</el-button>
            </div>
          </div>
        </div>

        <aside class="side-panel">
          <section class="panel-section">
            <div class="panel-title">当前槽位</div>
            <div class="slot-card">
              <div class="slot-row">
                <span class="slot-label">客户</span>
                <span class="slot-value">{{ currentCustomer }}</span>
              </div>
              <div class="slot-row">
                <span class="slot-label">日期</span>
                <span class="slot-value">{{ slots.recordDate || '-' }}</span>
              </div>
              <div class="slot-row">
                <span class="slot-label">餐次</span>
                <span class="slot-value">{{ mealTypeText(slots.mealType) }}</span>
              </div>
            </div>
            <div v-if="slotConfidenceList.length" class="confidence-list">
              <div v-for="item in slotConfidenceList" :key="item.key" class="confidence-row">
                <span>{{ item.label }}</span>
                <el-tag size="mini" :type="confidenceTag(item.value)">{{ item.value }}</el-tag>
              </div>
            </div>
          </section>

          <section class="panel-section">
            <div class="panel-title">会话状态</div>
            <div class="status-card">
              <div class="status-row">
                <span class="slot-label">阶段</span>
                <el-tag size="mini" type="info">{{ stageText(conversationStage) }}</el-tag>
              </div>
              <div class="status-row">
                <span class="slot-label">待补充</span>
                <span class="slot-value">{{ formatMissingSlots(missingSlots) }}</span>
              </div>
              <div class="status-actions">
                <el-button size="mini" plain @click="sendQuickReply('换成午餐')">换成午餐</el-button>
                <el-button size="mini" plain @click="sendQuickReply('换成晚餐')">换成晚餐</el-button>
              </div>
            </div>
          </section>

          <section class="panel-section">
            <div class="panel-title">最近结果</div>
            <div v-if="currentDiagnosis" class="summary-card">
              <div class="summary-line">{{ currentDiagnosis.summary || '暂无诊断摘要' }}</div>
              <div class="summary-tags">
                <el-tag size="mini" :type="currentDiagnosis.fallback ? 'warning' : 'success'">
                  {{ currentDiagnosis.fallback ? '兜底结果' : 'AI 建议' }}
                </el-tag>
                <el-tag v-if="currentDiagnosis.confidence" size="mini" :type="confidenceTag(currentDiagnosis.confidence)">
                  {{ currentDiagnosis.confidence }}
                </el-tag>
              </div>
              <div v-if="currentDiagnosis.fallbackReason" class="fallback-text">{{ currentDiagnosis.fallbackReason }}</div>
              <ul v-if="currentDiagnosis.nextActions && currentDiagnosis.nextActions.length" class="action-list compact-list">
                <li v-for="(action, actionIndex) in currentDiagnosis.nextActions" :key="actionIndex">{{ action }}</li>
              </ul>
              <div v-if="currentDiagnosis.actionDrafts && currentDiagnosis.actionDrafts.length" class="draft-count">
                动作草稿：{{ currentDiagnosis.actionDrafts.length }}
              </div>
            </div>
            <el-empty v-else description="暂无诊断结果" :image-size="60" />
          </section>

          <section class="panel-section">
            <div class="panel-title panel-title-row">
              <span>动作确认记录</span>
              <el-button size="mini" plain :loading="actionAuditLoading" @click="loadActionAudits">刷新</el-button>
            </div>
            <div class="audit-card">
              <el-empty v-if="!actionAudits.length" description="暂无确认记录" :image-size="52" />
              <template v-else>
                <div v-for="audit in actionAudits" :key="audit.id || audit.idempotencyKey" class="audit-item">
                  <div class="audit-head">
                    <span>{{ audit.actionTitle || audit.actionCode }}</span>
                    <el-tag size="mini" :type="auditStatusTag(audit.status)">{{ audit.status || '-' }}</el-tag>
                  </div>
                  <div class="audit-meta">
                    <span>{{ audit.targetType || '-' }}：{{ audit.targetId || '-' }}</span>
                    <span>{{ audit.operator || '-' }}</span>
                  </div>
                  <div v-if="audit.failureReason" class="audit-failure">{{ audit.failureReason }}</div>
                </div>
              </template>
            </div>
          </section>

          <section class="panel-section">
            <div class="panel-title panel-title-row">
              <span>运营看板</span>
              <el-button size="mini" plain :loading="operationStatsLoading" @click="loadOperationStats">刷新</el-button>
            </div>
            <div class="ops-card">
              <div class="ops-grid">
                <div class="ops-item">
                  <span>诊断次数</span>
                  <strong>{{ operationStats.diagnosisCount || 0 }}</strong>
                </div>
                <div class="ops-item">
                  <span>fallback 率</span>
                  <strong>{{ percent(operationStats.fallbackRate) }}</strong>
                </div>
                <div class="ops-item">
                  <span>采纳率</span>
                  <strong>{{ percent(operationStats.feedbackAcceptedRate) }}</strong>
                </div>
                <div class="ops-item">
                  <span>动作确认率</span>
                  <strong>{{ percent(operationStats.actionDraftConfirmationRate) }}</strong>
                </div>
              </div>
              <div class="ops-line">平均耗时：{{ numberText(operationStats.averageDiagnosisCostMs) }} ms</div>
              <div class="ops-line">工具失败率：{{ percent(operationStats.toolFailureRate) }}</div>
              <div v-if="topReasonCodes.length" class="ops-list">
                <div class="block-title">原因码分布</div>
                <div v-for="item in topReasonCodes" :key="item.code" class="ops-reason-row">
                  <span>{{ item.code }}</span>
                  <strong>{{ item.count }}</strong>
                </div>
              </div>
              <div v-if="topFailureTypes.length" class="ops-list">
                <div class="block-title">失败类型</div>
                <div v-for="item in topFailureTypes" :key="item.code" class="ops-reason-row">
                  <span>{{ item.code }}</span>
                  <strong>{{ item.count }}</strong>
                </div>
              </div>
              <div v-if="topFallbackSources.length" class="ops-list">
                <div class="block-title">兜底来源</div>
                <div v-for="item in topFallbackSources" :key="item.code" class="ops-reason-row">
                  <span>{{ item.code }}</span>
                  <strong>{{ item.count }}</strong>
                </div>
              </div>
            </div>
          </section>

          <section class="panel-section">
            <div class="panel-title panel-title-row">
              <span>规则缺口</span>
              <el-button size="mini" plain :loading="ruleGapLoading" @click="loadRuleGaps">刷新</el-button>
            </div>
            <div class="rule-gap-card">
              <el-empty v-if="!ruleGaps.length" description="暂无待处理缺口" :image-size="52" />
              <template v-else>
                <div v-for="gap in ruleGaps" :key="gap.id" class="rule-gap-item">
                  <div class="rule-gap-head">
                    <el-tag size="mini" :type="ruleGapTypeTag(gap.gapType)">{{ gap.gapType || '-' }}</el-tag>
                    <el-tag size="mini" :type="ruleGapStatusTag(gap.status)">{{ ruleGapStatusText(gap.status) }}</el-tag>
                  </div>
                  <div class="rule-gap-reason">{{ gap.actualReasonCode || 'UNKNOWN' }}</div>
                  <div class="rule-gap-desc">{{ gap.gapDescription || '-' }}</div>
                  <div class="rule-gap-meta">
                    <span>次数：{{ gap.occurrenceCount || 0 }}</span>
                    <span>{{ gap.recordDate || '-' }} {{ mealTypeText(gap.mealType) }}</span>
                  </div>
                  <div class="rule-gap-actions">
                    <el-button size="mini" plain :disabled="gap.status === 'IN_PROGRESS'" @click="updateRuleGapStatus(gap, 'IN_PROGRESS')">处理中</el-button>
                    <el-button size="mini" plain type="success" :disabled="gap.status === 'RESOLVED'" @click="updateRuleGapStatus(gap, 'RESOLVED')">已解决</el-button>
                    <el-button size="mini" plain type="info" :disabled="gap.status === 'IGNORED'" @click="updateRuleGapStatus(gap, 'IGNORED')">忽略</el-button>
                  </div>
                </div>
              </template>
            </div>
          </section>
        </aside>
      </div>
    </div>

    <el-dialog
      title="确认动作草稿"
      :visible.sync="actionConfirmDialogVisible"
      width="640px"
      append-to-body
    >
      <div v-if="selectedActionDraft" class="confirm-dialog-body">
        <div class="confirm-title">
          <span>{{ selectedActionDraft.title || selectedActionDraft.actionCode }}</span>
          <el-tag size="mini" :type="riskTag(selectedActionDraft.riskLevel)">{{ selectedActionDraft.riskLevel || 'LOW' }}</el-tag>
        </div>
        <div class="confirm-meta">
          <span>目标：{{ selectedActionDraft.targetType || '-' }} / {{ selectedActionDraft.targetId || '-' }}</span>
          <span>权限：{{ selectedActionDraft.requiredPermission || '-' }}</span>
        </div>
        <el-input
          v-model="actionConfirmComment"
          class="confirm-comment"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="备注"
        />
        <div class="preview-grid">
          <div>
            <div class="block-title">变更前</div>
            <pre>{{ prettyJson(selectedActionDraft.beforeSnapshot) }}</pre>
          </div>
          <div>
            <div class="block-title">变更后</div>
            <pre>{{ prettyJson(selectedActionDraft.afterPreview) }}</pre>
          </div>
        </div>
        <el-checkbox v-if="isHighRisk(selectedActionDraft)" v-model="secondConfirmed">
          已完成高风险动作二次确认
        </el-checkbox>
        <el-alert
          v-if="actionConfirmResult"
          class="confirm-result"
          :title="actionConfirmResult.message || actionConfirmResult.failureReason"
          :type="actionConfirmResult.success ? 'success' : 'warning'"
          :closable="false"
          show-icon
        />
        <div v-if="actionConfirmResult && actionConfirmResult.executionResult" class="execution-result">
          <div class="block-title">执行结果</div>
          <pre>{{ prettyJson(actionConfirmResult.executionResult) }}</pre>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="actionConfirmDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="actionConfirmLoading"
          :disabled="isHighRisk(selectedActionDraft) && !secondConfirmed"
          @click="submitActionConfirm"
        >
          提交确认
        </el-button>
      </span>
    </el-dialog>

    <el-dialog
      title="诊断反馈"
      :visible.sync="feedbackDialogVisible"
      width="560px"
      append-to-body
    >
      <div class="feedback-form">
        <el-radio-group v-model="feedbackForm.accepted" size="small">
          <el-radio-button label="ACCEPTED">采纳</el-radio-button>
          <el-radio-button label="PARTIAL">部分正确</el-radio-button>
          <el-radio-button label="REJECTED">不采纳</el-radio-button>
        </el-radio-group>
        <el-select
          v-model="feedbackForm.actualReasonCode"
          class="feedback-control"
          filterable
          allow-create
          clearable
          placeholder="真实原因"
        >
          <el-option
            v-for="code in feedbackReasonOptions"
            :key="code"
            :label="code"
            :value="code"
          />
        </el-select>
        <el-input
          v-model="feedbackForm.comment"
          class="feedback-control"
          type="textarea"
          :rows="3"
          maxlength="300"
          show-word-limit
          placeholder="备注"
        />
        <div class="feedback-predicted">预测原因：{{ feedbackReasonOptions.join(' / ') || '-' }}</div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="feedbackDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackLoading" @click="submitFeedback">提交反馈</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import {
  archiveChatSession,
  chatMealPlan,
  confirmActionDraft,
  createChatSession,
  getChatSession,
  queryAgentOperationStats,
  queryActionAudits,
  queryChatSessions,
  queryAgentRuleGaps,
  submitDiagnosisFeedback,
  updateChatSessionTitle,
  updateAgentRuleGapStatus
} from '@/api/agentDiagnosis'

const DEFAULT_QUICK_REPLIES = ['今天', '明天', '早餐', '午餐', '晚餐', '重新排查']

function welcomeMessage() {
  return {
    role: 'assistant',
    content: '你好，我是智能排查助手。请描述要排查的客户、日期和餐次，例如：帮我看下客户 C10001 明天午餐为什么没排出来。',
    status: 'ANSWERED',
    stage: 'COLLECTING_SLOTS',
    missingSlots: []
  }
}

export default {
  name: 'AgentDiagnosis',
  data() {
    return {
      loading: false,
      sessionId: null,
      activeSessionId: null,
      sessions: [],
      sessionsLoading: false,
      sessionCreating: false,
      sessionRenameLoading: false,
      sessionKeyword: '',
      inputMessage: '',
      slots: {},
      slotConfidence: {},
      missingSlots: [],
      conversationStage: 'COLLECTING_SLOTS',
      currentDiagnosis: null,
      toolSummaryExpanded: false,
      traceExpanded: false,
      quickReplies: DEFAULT_QUICK_REPLIES,
      actionConfirmDialogVisible: false,
      selectedActionDraft: null,
      selectedDiagnosisResult: null,
      secondConfirmed: false,
      actionConfirmComment: '',
      actionConfirmLoading: false,
      actionConfirmResult: null,
      feedbackDialogVisible: false,
      feedbackLoading: false,
      feedbackResult: null,
      feedbackDiagnosisResult: null,
      feedbackForm: {
        accepted: 'ACCEPTED',
        actualReasonCode: '',
        comment: ''
      },
      operationStatsLoading: false,
      operationStats: {},
      actionAuditLoading: false,
      actionAudits: [],
      ruleGapLoading: false,
      ruleGaps: [],
      messages: [welcomeMessage()]
    }
  },
  computed: {
    currentCustomer() {
      return this.slots.customerCode || this.slots.customerId || '-'
    },
    slotConfidenceList() {
      return Object.keys(this.slotConfidence || {}).map(key => ({
        key,
        label: this.slotLabel(key),
        value: this.slotConfidence[key]
      }))
    },
    feedbackReasonOptions() {
      return this.extractReasonCodes(this.feedbackDiagnosisResult)
    },
    topReasonCodes() {
      const distribution = this.operationStats.reasonCodeDistribution || {}
      return Object.keys(distribution)
        .map(code => ({ code, count: distribution[code] }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 5)
    },
    topFailureTypes() {
      const distribution = this.operationStats.failureTypeDistribution || {}
      return Object.keys(distribution)
        .map(code => ({ code, count: distribution[code] }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 5)
    },
    topFallbackSources() {
      const distribution = this.operationStats.fallbackSourceDistribution || {}
      return Object.keys(distribution)
        .map(code => ({ code, count: distribution[code] }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 5)
    },
    filteredSessions() {
      const keyword = (this.sessionKeyword || '').trim().toLowerCase()
      if (!keyword) {
        return this.sessions || []
      }
      return (this.sessions || []).filter(session => {
        const title = (session.title || '').toLowerCase()
        const customerCode = (session.customerCode || '').toLowerCase()
        const summary = (session.lastSummary || '').toLowerCase()
        return title.includes(keyword) || customerCode.includes(keyword) || summary.includes(keyword)
      })
    }
  },
  mounted() {
    this.loadSessions(true)
    this.loadOperationStats()
    this.loadRuleGaps()
  },
  methods: {
    async loadSessions(selectCurrent) {
      this.sessionsLoading = true
      try {
        const response = await queryChatSessions({ archived: false, page: 0, size: 20 })
        this.sessions = this.extractPageContent(response)
        if (selectCurrent) {
          if (this.activeSessionId && this.sessions.some(item => item.sessionId === this.activeSessionId)) {
            await this.handleSessionChange(this.activeSessionId)
          } else if (this.sessions.length) {
            await this.handleSessionChange(this.sessions[0].sessionId)
          } else {
            this.resetSessionState()
          }
        }
      } catch (e) {
        this.sessions = this.sessions || []
      } finally {
        this.sessionsLoading = false
      }
    },
    async handleSessionChange(sessionId) {
      if (!sessionId) {
        this.resetSessionState()
        return
      }
      this.sessionsLoading = true
      try {
        const detail = await getChatSession(sessionId)
        this.applySessionDetail(detail)
        this.loadActionAudits()
      } catch (e) {
        this.$message.error('会话加载失败')
      } finally {
        this.sessionsLoading = false
      }
    },
    async createSession() {
      this.sessionCreating = true
      try {
        const session = await createChatSession({})
        if (session && session.sessionId) {
          this.sessions = [session].concat((this.sessions || []).filter(item => item.sessionId !== session.sessionId))
          this.activeSessionId = session.sessionId
          this.sessionId = session.sessionId
          this.resetSessionState(session.sessionId)
        } else {
          this.resetSessionState()
        }
      } catch (e) {
        this.$message.error('新建会话失败')
      } finally {
        this.sessionCreating = false
      }
    },
    async archiveCurrentSession() {
      if (!this.activeSessionId) {
        return
      }
      try {
        await archiveChatSession(this.activeSessionId, true)
        this.$message.success('会话已归档')
        this.activeSessionId = null
        this.sessionId = null
        await this.loadSessions(true)
      } catch (e) {
        this.$message.error('会话归档失败')
      }
    },
    async renameCurrentSession() {
      if (!this.activeSessionId) {
        return
      }
      const current = (this.sessions || []).find(item => item.sessionId === this.activeSessionId) || {}
      try {
        const result = await this.$prompt('请输入会话标题', '会话改名', {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          inputValue: current.title || this.sessionOptionLabel(current),
          inputPattern: /\S+/,
          inputErrorMessage: '会话标题不能为空'
        })
        this.sessionRenameLoading = true
        await updateChatSessionTitle(this.activeSessionId, { title: result.value })
        this.$message.success('会话标题已更新')
        await this.loadSessions(false)
      } catch (e) {
        if (e && e.value !== undefined) {
          this.$message.error('会话改名失败')
        }
      } finally {
        this.sessionRenameLoading = false
      }
    },
    async sendMessage() {
      const message = (this.inputMessage || '').trim()
      if (!message) {
        this.$message.warning('请输入排查诉求')
        return
      }
      if (!this.activeSessionId) {
        await this.createSession()
      }
      const clientMessageId = this.generateClientMessageId()
      this.messages.push({ role: 'user', content: message })
      this.inputMessage = ''
      this.loading = true
      this.scrollToBottom()
      try {
        const response = await chatMealPlan({ sessionId: this.activeSessionId, clientMessageId, message })
        this.addAssistantResponse(response)
        this.loadSessions(false)
        if (response && response.diagnosisResult) {
          this.loadOperationStats()
        }
      } catch (e) {
        this.messages.push({
          role: 'assistant',
          content: '智能排查服务暂不可用，请稍后重试或先人工核对。',
          status: 'ERROR',
          stage: 'ERROR',
          missingSlots: []
        })
        this.$message.error('智能排查服务暂不可用，请稍后重试')
      } finally {
        this.loading = false
        this.scrollToBottom()
      }
    },
    sendQuickReply(reply) {
      if (reply === '清空会话') {
        this.clearSession()
        return Promise.resolve()
      }
      this.inputMessage = reply
      return this.sendMessage()
    },
    addAssistantResponse(response) {
      this.activeSessionId = response.sessionId || this.activeSessionId
      this.sessionId = this.activeSessionId
      this.slots = response.slots || this.slots || {}
      this.slotConfidence = response.slotConfidence || (response.slots && response.slots.slotConfidence) || {}
      this.missingSlots = response.missingSlots || []
      this.conversationStage = response.conversationStage || this.conversationStage
      this.currentDiagnosis = response.diagnosisResult || this.currentDiagnosis
      this.quickReplies = response.quickReplies && response.quickReplies.length
        ? response.quickReplies
        : DEFAULT_QUICK_REPLIES
      this.messages.push({
        role: 'assistant',
        content: response.assistantMessage || '已收到，请继续补充排查信息。',
        status: response.status,
        stage: response.conversationStage,
        missingSlots: response.missingSlots || [],
        slotConfidence: response.slotConfidence || (response.slots && response.slots.slotConfidence) || {},
        slots: response.slots,
        result: response.diagnosisResult
      })
      this.loadActionAudits()
    },
    async clearSession() {
      await this.createSession()
    },
    resetSessionState(sessionId) {
      this.activeSessionId = sessionId || null
      this.sessionId = sessionId || null
      this.inputMessage = ''
      this.slots = {}
      this.slotConfidence = {}
      this.missingSlots = []
      this.conversationStage = 'COLLECTING_SLOTS'
      this.currentDiagnosis = null
      this.toolSummaryExpanded = false
      this.traceExpanded = false
      this.quickReplies = DEFAULT_QUICK_REPLIES
      this.actionAudits = []
      this.messages = [welcomeMessage()]
      this.scrollToBottom()
    },
    applySessionDetail(detail) {
      this.activeSessionId = detail && detail.sessionId ? detail.sessionId : this.activeSessionId
      this.sessionId = this.activeSessionId
      this.slots = (detail && detail.currentSlots) || {}
      this.slotConfidence = (detail && detail.currentSlots && detail.currentSlots.slotConfidence) || {}
      this.missingSlots = []
      this.conversationStage = (detail && detail.stage) || 'COLLECTING_SLOTS'
      this.currentDiagnosis = (detail && detail.latestDiagnosisResult) || null
      this.quickReplies = this.currentDiagnosis ? ['重新排查', '清空会话'] : DEFAULT_QUICK_REPLIES
      this.messages = this.restoreSessionMessages(detail)
      this.scrollToBottom()
    },
    restoreSessionMessages(detail) {
      const mappedMessages = this.mapSessionMessages(detail && detail.messages)
      const latestDiagnosisResult = detail && detail.latestDiagnosisResult
      if (!latestDiagnosisResult) {
        return mappedMessages
      }
      if (mappedMessages.some(message => !!message.result)) {
        return mappedMessages
      }
      const matchedMessage = this.findDiagnosisMessage(mappedMessages, latestDiagnosisResult)
      if (matchedMessage) {
        matchedMessage.result = matchedMessage.result || latestDiagnosisResult
        return mappedMessages
      }
      return mappedMessages.concat([{
        role: 'assistant',
        content: latestDiagnosisResult.summary || '已恢复最近一次诊断结果。',
        status: 'ANSWERED',
        stage: (detail && detail.stage) || 'DIAGNOSED',
        missingSlots: [],
        slotConfidence: {},
        slots: (detail && detail.currentSlots) || {},
        result: latestDiagnosisResult
      }])
    },
    findDiagnosisMessage(messages, latestDiagnosisResult) {
      if (!messages || !messages.length || !latestDiagnosisResult) {
        return null
      }
      const diagnosisRequestId = latestDiagnosisResult.requestId
      if (diagnosisRequestId) {
        for (let i = messages.length - 1; i >= 0; i--) {
          const message = messages[i]
          if (message.role === 'assistant' && message.requestId === diagnosisRequestId) {
            return message
          }
        }
      }
      for (let i = messages.length - 1; i >= 0; i--) {
        const message = messages[i]
        if (message.role === 'assistant') {
          return message
        }
      }
      return null
    },
    mapSessionMessages(messages) {
      if (!messages || !messages.length) {
        return [welcomeMessage()]
      }
      return messages.map(message => ({
        requestId: message.requestId,
        role: (message.role || '').toLowerCase() === 'user' ? 'user' : 'assistant',
        content: message.content,
        status: message.status,
        stage: message.conversationStage,
        missingSlots: [],
        slotConfidence: (message.slots && message.slots.slotConfidence) || {},
        slots: message.slots,
        result: message.diagnosisResult
      }))
    },
    sessionOptionLabel(session) {
      if (!session) {
        return ''
      }
      if (session.title) {
        return session.title
      }
      const parts = [session.customerCode, session.recordDate, this.mealTypeText(session.mealType)].filter(Boolean)
      return parts.length ? parts.join(' / ') : session.sessionId
    },
    generateClientMessageId() {
      return `msg-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
    },
    formatSessionTime(value) {
      if (!value) {
        return '-'
      }
      return String(value).slice(5, 16).replace('T', ' ')
    },
    togglePanel(key) {
      this[key] = !this[key]
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.messageList
        if (el) {
          el.scrollTop = el.scrollHeight
        }
      })
    },
    mealTypeText(value) {
      const map = {
        BREAKFAST: '早餐',
        LUNCH: '午餐',
        DINNER: '晚餐'
      }
      return map[value] || value || '-'
    },
    missingSlotText(value) {
      const map = {
        CUSTOMER: '客户',
        RECORD_DATE: '日期',
        MEAL_TYPE: '餐次'
      }
      return map[value] || value || '-'
    },
    stageText(value) {
      const map = {
        COLLECTING_SLOTS: '收集槽位',
        READY_TO_DIAGNOSE: '待诊断',
        DIAGNOSING: '诊断中',
        DIAGNOSED: '已诊断',
        FOLLOWING_UP: '继续追问',
        RESET: '已重置',
        ERROR: '异常'
      }
      return map[value] || value || '-'
    },
    slotLabel(key) {
      const map = {
        customer: '客户',
        recordDate: '日期',
        mealType: '餐次'
      }
      return map[key] || key
    },
    formatMissingSlots(slots) {
      if (!slots || !slots.length) {
        return '无'
      }
      return slots.map(this.missingSlotText).join('、')
    },
    levelTag(level) {
      if (level === 'HIGH') return 'danger'
      if (level === 'MEDIUM') return 'warning'
      return 'info'
    },
    confidenceTag(level) {
      if (level === 'HIGH') return 'success'
      if (level === 'MEDIUM') return 'warning'
      return 'info'
    },
    riskTag(level) {
      if (level === 'HIGH') return 'danger'
      if (level === 'MEDIUM') return 'warning'
      return 'info'
    },
    shortDigest(value) {
      return value ? value.slice(0, 12) : '-'
    },
    compactJson(value) {
      if (!value) {
        return '-'
      }
      try {
        return JSON.stringify(value)
      } catch (e) {
        return '-'
      }
    },
    prettyJson(value) {
      if (!value) {
        return '-'
      }
      try {
        return JSON.stringify(value, null, 2)
      } catch (e) {
        return '-'
      }
    },
    isHighRisk(draft) {
      return !!(draft && draft.riskLevel === 'HIGH')
    },
    openActionConfirm(draft, result) {
      this.selectedActionDraft = draft
      this.selectedDiagnosisResult = result
      this.secondConfirmed = false
      this.actionConfirmComment = ''
      this.actionConfirmResult = null
      this.actionConfirmDialogVisible = true
    },
    async submitActionConfirm() {
      if (!this.selectedActionDraft) {
        return
      }
      this.actionConfirmLoading = true
      try {
        const response = await confirmActionDraft({
          requestId: this.selectedDiagnosisResult && this.selectedDiagnosisResult.requestId,
          sessionId: this.sessionId,
          idempotencyKey: this.buildIdempotencyKey(this.selectedActionDraft),
          actionDraft: this.selectedActionDraft,
          secondConfirmed: this.secondConfirmed,
          comment: this.actionConfirmComment
        })
        this.actionConfirmResult = response
        if (response && response.success) {
          this.$message.success(response.message || '动作确认已记录')
        } else if (response && response.status === 'STALE_DRAFT') {
          this.handleStaleDraftResponse(response)
        } else {
          this.$message.warning((response && (response.failureReason || response.message)) || '动作确认未执行')
        }
        this.loadActionAudits()
        this.loadOperationStats()
      } catch (e) {
        this.$message.error('动作确认失败')
      } finally {
        this.actionConfirmLoading = false
      }
    },
    handleStaleDraftResponse(response) {
      this.$message.warning(response.message || '业务数据已变化，请重新排查后再确认动作。')
      this.quickReplies = ['重新排查', '清空会话']
      this.messages.push({
        role: 'assistant',
        content: response.message || '业务数据已变化，请重新排查后再确认动作。',
        status: 'STALE_DRAFT',
        stage: this.conversationStage,
        missingSlots: [],
        slotConfidence: this.slotConfidence,
        slots: this.slots
      })
      this.actionConfirmDialogVisible = false
      this.scrollToBottom()
    },
    buildIdempotencyKey(draft) {
      const result = this.selectedDiagnosisResult || {}
      return [
        result.requestId || this.sessionId || 'local',
        draft.actionCode || 'ACTION',
        draft.targetType || 'TARGET',
        draft.targetId || result.customerId || result.recordDate || 'NONE'
      ].join(':')
    },
    openFeedbackDialog(result, accepted) {
      this.feedbackDiagnosisResult = result
      const reasonCodes = this.extractReasonCodes(result)
      this.feedbackForm = {
        accepted,
        actualReasonCode: reasonCodes[0] || '',
        comment: ''
      }
      this.feedbackResult = null
      this.feedbackDialogVisible = true
    },
    async submitFeedback() {
      if (!this.feedbackDiagnosisResult) {
        return
      }
      this.feedbackLoading = true
      try {
        const result = this.feedbackDiagnosisResult
        const response = await submitDiagnosisFeedback({
          requestId: result.requestId,
          sessionId: this.sessionId,
          customerId: result.customerId,
          customerName: result.customerName,
          recordDate: result.recordDate,
          mealType: result.mealType,
          predictedReasonCodes: this.extractReasonCodes(result),
          accepted: this.feedbackForm.accepted,
          actualReasonCode: this.feedbackForm.actualReasonCode,
          comment: this.feedbackForm.comment
        })
        this.feedbackResult = response
        this.$message.success((response && response.message) || '诊断反馈已记录')
        this.feedbackDialogVisible = false
        this.loadOperationStats()
        this.loadRuleGaps()
      } catch (e) {
        this.$message.error('诊断反馈提交失败')
      } finally {
        this.feedbackLoading = false
      }
    },
    extractReasonCodes(result) {
      if (!result || !result.reasons) {
        return []
      }
      return result.reasons
        .map(reason => reason && reason.code)
        .filter(Boolean)
    },
    async loadOperationStats() {
      this.operationStatsLoading = true
      try {
        this.operationStats = await queryAgentOperationStats({})
      } catch (e) {
        this.operationStats = this.operationStats || {}
      } finally {
        this.operationStatsLoading = false
      }
    },
    async loadActionAudits() {
      if (!this.sessionId) {
        this.actionAudits = []
        return
      }
      this.actionAuditLoading = true
      try {
        const response = await queryActionAudits({ sessionId: this.sessionId, page: 0, size: 5 })
        this.actionAudits = this.extractPageContent(response)
      } catch (e) {
        this.actionAudits = this.actionAudits || []
      } finally {
        this.actionAuditLoading = false
      }
    },
    async loadRuleGaps() {
      this.ruleGapLoading = true
      try {
        const response = await queryAgentRuleGaps({ status: 'OPEN', page: 0, size: 5 })
        this.ruleGaps = this.extractPageContent(response)
      } catch (e) {
        this.ruleGaps = this.ruleGaps || []
      } finally {
        this.ruleGapLoading = false
      }
    },
    extractPageContent(response) {
      if (!response) {
        return []
      }
      if (Array.isArray(response.content)) {
        return response.content
      }
      if (Array.isArray(response.records)) {
        return response.records
      }
      if (Array.isArray(response)) {
        return response
      }
      return []
    },
    async updateRuleGapStatus(gap, status) {
      if (!gap || !gap.id) {
        return
      }
      const payload = await this.buildRuleGapStatusPayload(status)
      if (!payload) {
        return
      }
      this.ruleGapLoading = true
      try {
        await updateAgentRuleGapStatus(gap.id, payload)
        this.$message.success('规则缺口状态已更新')
        this.loadRuleGaps()
      } catch (e) {
        this.$message.error('规则缺口状态更新失败')
      } finally {
        this.ruleGapLoading = false
      }
    },
    async buildRuleGapStatusPayload(status) {
      if (status === 'IN_PROGRESS') {
        try {
          const result = await this.$prompt('请输入处理人账号', '规则缺口处理中', {
            confirmButtonText: '确认',
            cancelButtonText: '取消',
            inputPattern: /\S+/,
            inputErrorMessage: '处理人不能为空'
          })
          return {
            status,
            owner: result.value,
            comment: `已分配给 ${result.value} 处理`
          }
        } catch (e) {
          return null
        }
      }
      if (status === 'RESOLVED') {
        try {
          const result = await this.$prompt('请输入规则、评测用例或发布验证证据', '规则缺口已解决', {
            confirmButtonText: '确认',
            cancelButtonText: '取消',
            inputType: 'textarea',
            inputPattern: /\S+/,
            inputErrorMessage: '维护证据不能为空'
          })
          return {
            status,
            owner: '',
            comment: result.value
          }
        } catch (e) {
          return null
        }
      }
      return {
        status,
        owner: '',
        comment: `前端工作台更新为 ${this.ruleGapStatusText(status)}`
      }
    },
    ruleGapStatusText(status) {
      const map = {
        OPEN: '待处理',
        IN_PROGRESS: '处理中',
        RESOLVED: '已解决',
        IGNORED: '已忽略'
      }
      return map[status] || status || '-'
    },
    ruleGapStatusTag(status) {
      if (status === 'RESOLVED') return 'success'
      if (status === 'IN_PROGRESS') return 'warning'
      if (status === 'IGNORED') return 'info'
      return 'danger'
    },
    auditStatusTag(status) {
      if (status === 'EXECUTED' || status === 'CONFIRMED') return 'success'
      if (status === 'STALE_DRAFT') return 'warning'
      if (status === 'NEED_SECOND_CONFIRM') return 'warning'
      if (status === 'PERMISSION_DENIED' || status === 'VALIDATION_FAILED' || status === 'EXECUTION_FAILED') return 'danger'
      return 'info'
    },
    ruleGapTypeTag(type) {
      if (type === 'UNKNOWN_REASON') return 'danger'
      if (type === 'WRONG_REASON') return 'warning'
      return 'info'
    },
    percent(value) {
      const numberValue = Number(value || 0)
      return `${Math.round(numberValue * 1000) / 10}%`
    },
    numberText(value) {
      if (value === null || value === undefined) {
        return '0'
      }
      return String(Math.round(Number(value) || 0))
    },
    hasCustomerTarget(result) {
      return !!(result && (result.customerId || result.customerCode || result.customerName))
    },
    openCustomerProfile(result) {
      const query = {}
      if (result.customerCode) query.customerCode = result.customerCode
      if (result.customerName) query.customerName = result.customerName
      if (result.customerId) query.customerId = result.customerId
      this.$router.push({ path: '/customer/profile', query })
    },
    openCustomerOrders(result) {
      const query = {}
      if (result.customerCode) query.customerCode = result.customerCode
      if (result.customerName) query.customerName = result.customerName
      if (result.customerId) query.customerId = result.customerId
      if (result.recordDate) query.scheduleDate = result.recordDate
      this.$router.push({ path: '/customer/order', query })
    },
    openMealPlan(result) {
      this.$router.push({
        path: '/meal/production-sheet',
        query: {
          date: result.recordDate,
          mealType: result.mealType
        }
      })
    }
  }
}
</script>

<style scoped>
.agent-diagnosis {
  min-height: calc(100vh - 84px);
}

.workspace-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 116px);
  min-height: 640px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
}

.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.workspace-body {
  flex: 1;
  display: grid;
  grid-template-columns: 280px minmax(0, 1.5fr) minmax(280px, 0.9fr);
  min-height: 0;
  overflow: hidden;
}

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

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 12px;
}

.session-item {
  padding: 12px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
}

.session-item + .session-item {
  margin-top: 10px;
}

.session-item-active {
  border-color: #409eff;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.08);
}

.session-item-head,
.session-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-item-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.session-item-meta {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.session-item-summary {
  margin-top: 8px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.chat-panel {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  border-right: 1px solid #ebeef5;
}

.side-panel {
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 20px 18px;
  background: #fafbfd;
}

.panel-section + .panel-section {
  margin-top: 16px;
}

.panel-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.slot-card,
.status-card,
.summary-card,
.audit-card,
.ops-card,
.rule-gap-card {
  padding: 14px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.audit-item + .audit-item {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.audit-head,
.audit-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: space-between;
}

.audit-head {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.audit-head span {
  min-width: 0;
  word-break: break-all;
}

.audit-meta {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.audit-failure {
  margin-top: 6px;
  color: #f56c6c;
  font-size: 12px;
  line-height: 1.6;
}

.ops-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.ops-item {
  min-width: 0;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}

.ops-item span,
.ops-line {
  color: #909399;
  font-size: 12px;
}

.ops-item strong {
  display: block;
  margin-top: 4px;
  color: #303133;
  font-size: 18px;
  line-height: 1.2;
}

.ops-line {
  margin-top: 10px;
}

.ops-list {
  margin-top: 12px;
}

.ops-reason-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #606266;
  font-size: 12px;
  line-height: 1.8;
}

.ops-reason-row span {
  min-width: 0;
  word-break: break-all;
}

.rule-gap-item + .rule-gap-item {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.rule-gap-head,
.rule-gap-meta,
.rule-gap-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rule-gap-head {
  justify-content: space-between;
}

.rule-gap-reason {
  margin-top: 8px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}

.rule-gap-desc {
  margin-top: 6px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}

.rule-gap-meta {
  justify-content: space-between;
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.rule-gap-actions {
  margin-top: 10px;
}

.slot-row,
.status-row,
.confidence-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.slot-row + .slot-row,
.status-row + .status-row,
.confidence-row + .confidence-row {
  margin-top: 10px;
}

.slot-label {
  color: #909399;
}

.slot-value {
  color: #303133;
  text-align: right;
}

.confidence-list {
  margin-top: 10px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.status-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.summary-line {
  color: #303133;
  line-height: 1.7;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.fallback-text {
  margin-top: 12px;
  color: #e6a23c;
  line-height: 1.6;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  background: #f7f8fa;
}

.chat-footer {
  background: #fff;
  border-top: 1px solid #ebeef5;
  box-shadow: 0 -8px 24px rgba(17, 24, 39, 0.06);
}

.message-row {
  display: flex;
  margin-bottom: 14px;
}

.message-row-user {
  justify-content: flex-end;
}

.message-row-assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 88%;
  padding: 12px 14px;
  border-radius: 6px;
  line-height: 1.7;
  font-size: 14px;
  word-break: break-word;
}

.user-bubble {
  color: #fff;
  background: #409eff;
}

.assistant-bubble {
  color: #303133;
  background: #fff;
  border: 1px solid #ebeef5;
}

.message-meta,
.slot-line,
.confidence-line,
.meta,
.result-links,
.feedback-actions,
.inspector-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.message-meta,
.slot-line,
.confidence-line {
  margin-top: 10px;
}

.confidence-item {
  color: #606266;
}

.result-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  font-weight: 600;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.fallback-alert {
  margin-top: 12px;
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
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
}

.result-links {
  margin-bottom: 14px;
}

.feedback-actions {
  margin-bottom: 14px;
}

.reason-title {
  margin-left: 8px;
  margin-right: 8px;
  font-weight: 600;
}

.reason-desc,
.reason-suggestion,
.reason-ruleids {
  margin-bottom: 10px;
  color: #606266;
  line-height: 1.7;
}

.block-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.action-block,
.draft-block,
.trace-block {
  margin-top: 14px;
}

.draft-alert {
  margin-bottom: 10px;
}

.draft-preview {
  max-width: 360px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.draft-count {
  margin-top: 12px;
  color: #606266;
  font-size: 13px;
}

.confirm-dialog-body {
  color: #303133;
}

.confirm-title,
.confirm-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.confirm-title {
  font-weight: 600;
}

.confirm-meta {
  margin-top: 10px;
  color: #606266;
  font-size: 13px;
}

.confirm-comment {
  margin-top: 14px;
}

.preview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 14px 0;
}

.preview-grid pre {
  min-height: 120px;
  max-height: 240px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.confirm-result {
  margin-top: 12px;
}

.feedback-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feedback-control {
  width: 100%;
}

.feedback-predicted {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.action-list {
  margin: 0;
  padding-left: 18px;
  color: #606266;
  line-height: 1.8;
}

.compact-list {
  margin-top: 8px;
}

.quick-replies {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 20px 0;
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

@media (max-width: 1080px) {
  .workspace-body {
    grid-template-columns: 1fr;
  }

  .chat-panel {
    border-right: 0;
    border-bottom: 1px solid #ebeef5;
  }
}

@media (max-width: 768px) {
  .workspace-shell {
    height: auto;
    min-height: 520px;
  }

  .workspace-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .message-bubble {
    max-width: 100%;
  }

  .chat-footer {
    box-shadow: 0 -6px 16px rgba(17, 24, 39, 0.05);
  }

  .composer {
    grid-template-columns: 1fr;
  }

  .composer .el-button {
    height: 40px;
  }

  .preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
