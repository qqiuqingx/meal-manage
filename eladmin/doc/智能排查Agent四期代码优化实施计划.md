# 智能排查 Agent 四期代码优化实施计划

## 1. 阶段目标

四期不继续扩展新的诊断业务范围，优先补齐代码层面的生产化能力。

核心目标:

- 会话记录可持久化，服务重启、前端刷新后仍可恢复排查上下文。
- 前端支持多个排查会话并行处理，客服可以在多个客户问题之间切换。
- 主系统调用 `agent-service` 具备明确的超时、失败分类、重试和降级策略。
- 聊天消息和动作确认具备幂等保护，避免重复诊断、重复执行。
- 动作草稿确认前校验业务数据是否已变化，避免基于过期诊断结果执行写操作。
- 会话、诊断、反馈、动作审计、运营统计之间能通过 `sessionId` 和 `requestId` 串联。

## 2. 当前代码现状

### 2.1 会话存储

当前 `agent-service` 使用内存会话存储:

```text
agent-service/src/main/java/me/zhengjie/agent/chat/InMemoryMealPlanChatSessionStore.java
```

现有限制:

- 服务重启后会话丢失。
- 多实例部署时不同实例之间无法共享会话。
- 前端刷新后只能依赖页面状态，无法从服务端恢复完整会话。
- 只保留最近 10 轮消息和最近 3 次诊断摘要，无法做完整客服复盘。

### 2.2 前端会话

当前页面:

```text
eladmin-web/src/views/agent/diagnosis/index.vue
```

现有限制:

- 页面只维护一个 `sessionId`。
- 页面只维护一个 `messages` 数组。
- “清空会话”会直接丢弃当前页面上下文。
- 不支持会话列表、会话切换、会话归档、历史消息恢复。

### 2.3 服务韧性

当前主系统通过:

```text
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/client/HttpAgentServiceClient.java
```

调用 `agent-service`。

现有限制:

- 调用失败后已有 fallback，但失败类型不够细。
- 超时、不可用、返回结构异常、模型失败、工具失败没有完整拆分。
- 缺少统一的健康检查和熔断策略。
- 运营统计无法准确区分 fallback 来源。

### 2.4 动作草稿确认

当前动作确认服务:

```text
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentActionConfirmServiceImpl.java
```

已支持:

- 权限校验。
- 高风险二次确认。
- 幂等键查询。
- 部分动作正式调用主系统 Service 执行。

待增强:

- 确认动作前重新读取业务数据。
- 判断诊断草稿生成时的 `beforeSnapshot` 是否已经过期。
- 返回明确的 `STALE_DRAFT` 状态，要求客服重新排查。

## 3. 总体设计

### 3.1 会话归属

会话持久化建议放在主系统 `eladmin-system`，而不是只放在 `agent-service`。

原因:

- 主系统拥有登录用户、权限、菜单和审计上下文。
- 前端已经统一访问主系统接口。
- 反馈、动作审计、运营指标目前也在主系统表中。
- 会话列表需要按客服账号、权限、客户、时间过滤，主系统更适合承载。

`agent-service` 仍保留短期内存状态，用于单次编排过程；完整会话记录由 `eladmin-system` 落库。

### 3.2 调用链调整

当前调用链:

```text
eladmin-web -> eladmin-system -> agent-service
```

四期保持该调用链不变，但在 `eladmin-system` 增加会话应用层:

```text
eladmin-web
  -> eladmin-system
      -> AgentChatSessionService
          -> 保存用户消息
          -> 调用 AgentServiceClient.chatMealPlan
          -> 保存助手消息
          -> 更新会话摘要
      -> agent-service
```

### 3.3 ID 设计

- `sessionId`: 一个客服排查会话的稳定 ID。
- `requestId`: 一次诊断或一次聊天请求的稳定 ID。
- `clientMessageId`: 前端生成的消息幂等 ID。
- `idempotencyKey`: 动作确认幂等键。

建议生成规则:

- `sessionId`: 后端生成 UUID。
- `requestId`: 网关或主系统生成 UUID，并通过 `X-Request-Id` 透传到 `agent-service`。
- `clientMessageId`: 前端生成 UUID，重复提交同一条消息时保持不变。
- `idempotencyKey`: 继续使用当前动作确认规则，但补充动作草稿摘要。

## 4. 数据库设计

### 4.1 会话表

新增表 `agent_chat_session`。

```sql
CREATE TABLE IF NOT EXISTS `agent_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `title` varchar(120) DEFAULT NULL COMMENT '会话标题',
  `operator` varchar(64) DEFAULT NULL COMMENT '客服账号',
  `customer_id` bigint DEFAULT NULL COMMENT '当前会话客户ID',
  `customer_code` varchar(64) DEFAULT NULL COMMENT '当前会话客户编号',
  `record_date` varchar(20) DEFAULT NULL COMMENT '当前会话排查日期',
  `meal_type` varchar(20) DEFAULT NULL COMMENT '当前会话餐次',
  `stage` varchar(32) NOT NULL DEFAULT 'COLLECTING_SLOTS' COMMENT '会话阶段',
  `last_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次请求ID',
  `last_summary` varchar(500) DEFAULT NULL COMMENT '最近诊断摘要',
  `last_message_time` datetime DEFAULT NULL COMMENT '最近消息时间',
  `archived` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否归档',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_chat_session_id` (`session_id`),
  KEY `idx_agent_chat_operator_time` (`operator`, `update_time`),
  KEY `idx_agent_chat_customer` (`customer_id`, `customer_code`),
  KEY `idx_agent_chat_archived` (`archived`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能排查聊天会话表';
```

### 4.2 消息表

新增表 `agent_chat_message`。

```sql
CREATE TABLE IF NOT EXISTS `agent_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `request_id` varchar(64) DEFAULT NULL COMMENT '请求ID',
  `client_message_id` varchar(64) DEFAULT NULL COMMENT '前端消息幂等ID',
  `role` varchar(16) NOT NULL COMMENT '消息角色(USER/ASSISTANT/SYSTEM)',
  `content` text COMMENT '消息内容',
  `status` varchar(32) DEFAULT NULL COMMENT '聊天响应状态',
  `conversation_stage` varchar(32) DEFAULT NULL COMMENT '消息产生后的会话阶段',
  `slots_json` text COMMENT '槽位快照JSON',
  `diagnosis_result_json` mediumtext COMMENT '诊断结果JSON',
  `tool_summary_json` text COMMENT '工具调用摘要JSON',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_chat_msg_session_time` (`session_id`, `create_time`),
  KEY `idx_agent_chat_msg_request` (`request_id`),
  UNIQUE KEY `uk_agent_chat_msg_client` (`session_id`, `client_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能排查聊天消息表';
```

说明:

- `diagnosis_result_json` 使用 `mediumtext`，避免复杂诊断结果超出普通 `text`。
- 工具原始入参和原始出参不落库，只保存 `toolCallSummary`。
- `uk_agent_chat_msg_client` 用于避免用户双击发送导致重复写入。

### 4.3 动作审计增强

现有 `agent_action_audit` 建议增加字段:

```sql
ALTER TABLE `agent_action_audit`
  ADD COLUMN `draft_digest` varchar(64) DEFAULT NULL COMMENT '动作草稿摘要' AFTER `idempotency_key`,
  ADD COLUMN `stale_check_result` varchar(32) DEFAULT NULL COMMENT '草稿过期校验结果' AFTER `failure_reason`,
  ADD COLUMN `stale_check_detail` varchar(500) DEFAULT NULL COMMENT '草稿过期校验详情' AFTER `stale_check_result`;
```

### 4.4 诊断指标增强

现有 `agent_diagnosis_metric` 建议增加字段:

```sql
ALTER TABLE `agent_diagnosis_metric`
  ADD COLUMN `fallback_source` varchar(32) DEFAULT NULL COMMENT '兜底来源' AFTER `fallback_reason`,
  ADD COLUMN `failure_type` varchar(64) DEFAULT NULL COMMENT '失败类型' AFTER `fallback_source`;
```

建议枚举:

- `ELADMIN_CLIENT`: 主系统调用 agent-service 失败。
- `AGENT_VALIDATOR`: agent-service 结果校验失败。
- `AGENT_TOOL`: agent-service 工具调用失败。
- `AGENT_MODEL`: 模型调用失败。
- `AGENT_BUDGET`: 工具预算或耗时限制触发。

## 5. 后端实施计划

### 5.1 主系统新增会话模块

新增包:

```text
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/
```

建议结构:

```text
session/
├── domain/
│   ├── AgentChatSession.java
│   └── AgentChatMessage.java
├── domain/dto/
│   ├── AgentChatSessionCreateRequest.java
│   ├── AgentChatSessionQueryCriteria.java
│   ├── AgentChatSessionSummaryDto.java
│   ├── AgentChatSessionDetailDto.java
│   └── AgentChatMessageDto.java
├── mapper/
│   ├── AgentChatSessionMapper.java
│   └── AgentChatMessageMapper.java
├── rest/
│   └── AgentChatSessionController.java
├── service/
│   ├── AgentChatSessionService.java
│   └── impl/AgentChatSessionServiceImpl.java
```

### 5.2 聊天入口改造

当前入口:

```text
AgentDiagnosisController.chatMealPlan()
  -> AgentDiagnosisFacadeService.chatMealPlan()
  -> HttpAgentServiceClient.chatMealPlan()
```

改造后:

```text
AgentDiagnosisController.chatMealPlan()
  -> AgentChatSessionService.chat()
      -> resolveSession()
      -> saveUserMessage()
      -> AgentDiagnosisFacadeService.chatMealPlan()
      -> saveAssistantMessage()
      -> updateSessionSummary()
```

改造重点:

- `AgentChatRequest` 增加 `clientMessageId`。
- 如果 `sessionId` 为空，主系统先创建会话，再调用 `agent-service`。
- 如果 `clientMessageId` 已存在，直接返回该消息对应的助手响应，避免重复诊断。
- 主系统将 `requestId` 通过 `X-Request-Id` 透传到 `agent-service`。
- `agent-service` 返回的 `sessionId` 必须与主系统会话保持一致。

### 5.3 会话查询接口

新增接口:

```text
GET /api/agent/chat-sessions
```

查询参数:

- `keyword`: 客户编号、会话标题、最近摘要模糊查询。
- `customerId`
- `customerCode`
- `recordDateStart`
- `recordDateEnd`
- `mealType`
- `archived`
- `page`
- `size`

新增接口:

```text
POST /api/agent/chat-sessions
```

用于显式创建新会话。

新增接口:

```text
GET /api/agent/chat-sessions/{sessionId}
```

返回:

- 会话摘要。
- 当前槽位。
- 会话阶段。
- 最近诊断结果。
- 完整消息列表。
- 最近动作审计。
- 最近反馈状态。

新增接口:

```text
PUT /api/agent/chat-sessions/{sessionId}/archive
PUT /api/agent/chat-sessions/{sessionId}/title
```

用于归档和改名。

### 5.4 服务韧性改造

改造文件:

```text
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/client/HttpAgentServiceClient.java
```

建议新增配置:

```yaml
agent:
  service:
    base-url: http://localhost:9001
    connect-timeout-ms: 2000
    read-timeout-ms: 8000
    retry-times: 1
    retry-backoff-ms: 300
```

失败分类:

| 失败类型 | 触发场景 | 是否重试 | fallbackReason |
| --- | --- | --- | --- |
| `AGENT_SERVICE_TIMEOUT` | 连接或读取超时 | 是 | 智能排查服务响应超时，已生成兜底人工复核建议。 |
| `AGENT_SERVICE_UNAVAILABLE` | 连接失败、服务不可达 | 是 | 智能排查服务不可用，已生成兜底人工复核建议。 |
| `AGENT_SERVICE_BAD_RESPONSE` | JSON 解析失败、返回结构为空 | 否 | 智能排查服务返回异常，已生成兜底人工复核建议。 |
| `AGENT_SERVICE_4XX` | 请求参数或权限错误 | 否 | 智能排查请求未通过服务校验，请检查输入信息。 |
| `AGENT_SERVICE_5XX` | agent-service 内部异常 | 是 | 智能排查服务内部异常，已生成兜底人工复核建议。 |

实现要求:

- 只对超时、连接失败、5xx 做短重试。
- 4xx 不重试。
- 每次失败写入日志时带 `requestId`、`sessionId`、`failureType`、耗时。
- fallback 响应中填充 `fallbackSource=ELADMIN_CLIENT`。

### 5.5 agent-service 健康检查

新增接口:

```text
GET /api/agent/health
```

返回:

```json
{
  "status": "UP",
  "ruleRegistryLoaded": true,
  "ruleVersionDigest": "a4f8c1e9d320",
  "modelConfigured": true,
  "toolClientConfigured": true
}
```

健康检查不主动调用大模型，避免造成成本和延迟。

如需要深度检查，保留现有 LLM 测试接口:

```text
POST /api/agent/meal-plan/llm/test
```

### 5.6 agent-service 会话存储调整

`agent-service` 暂不直接落库，但要做两点调整:

1. `InMemoryMealPlanChatSessionStore` 改名为 `LocalMealPlanChatSessionStore` 或保留原名但明确只用于短期编排。
2. `AgentChatRequest` 增加 `requestId` 或继续依赖请求头，确保日志、主系统消息和 agent-service 响应可关联。

后续如果 `agent-service` 独立部署多实例并需要直接承载会话，可再切换为 Redis 存储。

## 6. 前端实施计划

### 6.1 API 扩展

改造:

```text
eladmin-web/src/api/agentDiagnosis.js
```

新增方法:

```javascript
export function queryChatSessions(params)
export function createChatSession(data)
export function getChatSession(sessionId)
export function archiveChatSession(sessionId)
export function updateChatSessionTitle(sessionId, data)
```

`chatMealPlan(data)` 请求体增加:

```json
{
  "sessionId": "session-001",
  "clientMessageId": "uuid",
  "message": "看下 C10001 明天午餐为什么没排"
}
```

### 6.2 页面布局调整

改造:

```text
eladmin-web/src/views/agent/diagnosis/index.vue
```

目标布局:

```text
┌───────────────────────────────────────────────┐
│ 顶部工具条：新建会话 / 搜索 / 统计刷新          │
├──────────────┬────────────────────────────────┤
│ 会话列表      │ 当前会话消息区                   │
│ - 标题        │ - 消息列表                       │
│ - 客户/餐次    │ - 快捷回复                       │
│ - 状态/时间    │ - 输入区                         │
├──────────────┼────────────────────────────────┤
│ 可折叠统计区  │ 右侧：槽位、诊断结果、动作、反馈    │
└──────────────┴────────────────────────────────┘
```

### 6.3 前端状态结构

建议替换单会话状态:

```javascript
data() {
  return {
    sessions: [],
    activeSessionId: null,
    activeSession: null,
    sessionMessages: [],
    sendingMessageIds: {},
    ...
  }
}
```

发送消息流程:

1. 如果没有 `activeSessionId`，先调用 `createChatSession`。
2. 前端生成 `clientMessageId`。
3. 将用户消息临时插入页面，状态为 `SENDING`。
4. 调用 `chatMealPlan`。
5. 收到响应后刷新当前会话详情。
6. 刷新会话列表中的标题、时间、阶段和摘要。

### 6.4 多会话交互

必须支持:

- 新建会话。
- 切换会话。
- 归档会话。
- 按客户编号或标题搜索。
- 显示会话阶段。
- 显示最近更新时间。
- 当前会话发送中时禁止重复发送同一条消息。

暂不做:

- 多人实时协同编辑同一会话。
- WebSocket 实时推送。
- 会话批量导出。

## 7. 动作草稿过期校验

### 7.1 草稿摘要

`DiagnosisActionDraftDto` / `AgentDiagnosisActionDraftDto` 建议增加:

```java
private String draftDigest;
private String snapshotDigest;
private Long snapshotTime;
```

生成规则:

- `draftDigest`: `actionCode + targetType + targetId + afterPreview` 的稳定摘要。
- `snapshotDigest`: `beforeSnapshot` 关键字段摘要。

### 7.2 确认前校验

在:

```text
AgentActionConfirmServiceImpl.confirm()
```

中增加:

```text
validateDraft()
  -> checkStaleDraft()
  -> executeDraft()
```

不同动作的关键校验:

| 动作码 | 重新读取数据 | 校验字段 |
| --- | --- | --- |
| `RESUME_CUSTOMER_DELIVERY` | 客户档案排除日期 | 指定日期餐次是否仍被排除 |
| `ADJUST_ORDER_EFFECTIVE_DATE` | 订单详情 | 原开始日期、结束日期、订单状态 |
| `RECALCULATE_ORDER_BALANCE` | 订单核销统计 | 核销日志数量、已核销餐数、余额 |
| `REGENERATE_MEAL_PLAN` | 排餐计划 | 日期、餐次是否已生成、是否存在冲突 |

如果过期:

```json
{
  "status": "STALE_DRAFT",
  "success": false,
  "message": "业务数据已变化，请重新排查后再确认动作。",
  "failureReason": "诊断草稿生成后订单有效期已变化"
}
```

## 8. 测试计划

### 8.1 后端单元测试

新增测试:

```text
AgentChatSessionServiceImplTest
AgentChatSessionControllerTest
HttpAgentServiceClientResilienceTest
AgentActionConfirmStaleDraftTest
```

覆盖场景:

- 无 `sessionId` 时自动创建会话。
- 有 `sessionId` 时追加消息。
- 重复 `clientMessageId` 不重复调用 `agent-service`。
- 会话归档后不允许继续写入，或自动新建会话。
- agent-service 超时后生成 fallback。
- agent-service 返回非法 JSON 后生成 fallback。
- 高风险动作旧草稿返回 `STALE_DRAFT`。

### 8.2 agent-service 测试

新增测试:

```text
AgentHealthControllerTest
MealPlanChatRequestIdPropagationTest
```

覆盖场景:

- 健康检查返回规则摘要。
- 聊天响应保留主系统传入的会话 ID。
- fallback 时仍返回可落库的 `requestId`、`sessionId`、`conversationStage`。

### 8.3 前端测试

建议新增:

```text
eladmin-web/tests/unit/views/agent/diagnosis/
```

覆盖场景:

- 新建会话后 activeSessionId 更新。
- 切换会话后消息区刷新。
- 发送消息时生成 clientMessageId。
- 重复点击发送不会产生两条用户消息。
- 归档会话后从默认列表移除。

### 8.4 集成验证

本地验证路径:

1. 启动 `eladmin-system`。
2. 启动 `agent-service`。
3. 启动 `eladmin-web`。
4. 新建会话，发送“看下 C10001 明天午餐为什么没排”。
5. 刷新浏览器，确认会话和消息可恢复。
6. 新建第二个会话，确认两个会话可切换。
7. 停止 `agent-service`，发送消息，确认主系统 fallback 正常落库。
8. 恢复 `agent-service`，重新排查，确认同一会话继续追加消息。

## 9. 发布计划

### 9.1 第一批: 会话持久化和多会话

范围:

- 新增会话表、消息表。
- 主系统新增会话接口。
- 聊天入口接入落库。
- 前端支持会话列表、新建、切换、归档。
- 保持原诊断和动作功能不变。

验收标准:

- 刷新页面后会话和消息可恢复。
- 同一客服可打开多个会话。
- 会话消息能按 `sessionId` 串联诊断结果、反馈、动作审计。
- 不影响原有 `POST /api/agent/meal-plan/chat` 使用。

### 9.2 第二批: 服务韧性和幂等

范围:

- `HttpAgentServiceClient` 增加超时、重试和失败分类。
- `AgentDiagnosisMetric` 记录 `fallbackSource` 和 `failureType`。
- `AgentChatRequest` 增加 `clientMessageId`。
- 消息表唯一索引防止重复发送。
- 新增健康检查接口。

验收标准:

- agent-service 停止时，前端收到明确 fallback，不出现页面异常。
- 同一消息重复提交只产生一轮助手响应。
- 运营统计能区分超时、不可用、返回异常。

### 9.3 第三批: 动作草稿过期校验

范围:

- 动作草稿增加摘要字段。
- 动作确认前重新读取关键业务数据。
- 过期草稿返回 `STALE_DRAFT`。
- 前端展示“重新排查后再确认”的提示。

验收标准:

- 订单日期变化后，旧的调整订单动作不能继续执行。
- 客户排除日期已被人工删除后，旧的恢复配送动作不能重复执行。
- 所有 `STALE_DRAFT` 都写入动作审计。

### 9.4 第四批: 运营优化

范围:

- 会话列表增加搜索和筛选。
- 统计按 `sessionId`、客服、失败类型聚合。
- 高频 fallback 和工具失败增加告警条件。
- 从真实反馈沉淀评测案例。

验收标准:

- 客服能按客户编号快速找到历史排查。
- 管理员能看到 fallback 来源分布。
- 每次规则缺口关闭都能关联评测或发布证据。

## 10. 风险和注意事项

### 10.1 数据量风险

`diagnosis_result_json` 可能较大，需要控制:

- 不保存工具原始大对象。
- 只保存前端展示需要的诊断结果。
- 定期归档或清理超过保留期的消息。

### 10.2 隐私风险

会话消息可能包含客户信息。

要求:

- 不记录手机号、地址等敏感原文，或在保存前脱敏。
- 工具调用摘要不保存原始入参和出参。
- 会话查询按客服权限控制。

### 10.3 兼容风险

前端改为多会话后，需要保证旧入口仍能直接聊天。

策略:

- `sessionId` 为空时自动创建会话。
- 前端路由不变。
- 原有聊天接口路径不变。

### 10.4 部署风险

第一批包含 DDL，必须先执行数据库脚本，再发布后端和前端。

建议发布顺序:

1. 执行 DDL。
2. 发布后端，保持前端旧页面可用。
3. 发布前端多会话页面。
4. 灰度验证 agent-service 不可用时 fallback 表现。

## 11. 建议任务拆分

### 11.1 后端任务

- `BE-01` 新增 `agent_chat_session`、`agent_chat_message` DDL。
- `BE-02` 新增会话和消息实体、Mapper、Service。
- `BE-03` 新增会话列表、详情、创建、归档、改名接口。
- `BE-04` 改造聊天入口，接入会话落库。
- `BE-05` 增加 `clientMessageId` 幂等。
- `BE-06` 改造 `HttpAgentServiceClient` 超时、重试、失败分类。
- `BE-07` 增强诊断指标字段和记录逻辑。
- `BE-08` 增加动作草稿过期校验。

### 11.2 agent-service 任务

- `AS-01` 增加健康检查接口。
- `AS-02` 聊天响应补齐 requestId/sessionId 透传测试。
- `AS-03` 细化工具失败类型和 fallbackReason。
- `AS-04` 保留短期内存会话，但明确主系统为会话持久化真相源。

### 11.3 前端任务

- `FE-01` 扩展 agent API。
- `FE-02` 改造诊断页布局，增加会话列表。
- `FE-03` 新建、切换、归档会话。
- `FE-04` 发送消息增加 `clientMessageId`。
- `FE-05` 会话详情恢复消息、槽位、诊断结果。
- `FE-06` 动作草稿过期提示。

### 11.4 测试任务

- `QA-01` 后端会话 Service 单测。
- `QA-02` 聊天幂等单测。
- `QA-03` agent-service 不可用降级测试。
- `QA-04` 多会话前端单测。
- `QA-05` 动作草稿过期校验测试。
- `QA-06` 本地端到端验证。

## 12. 预计排期

按一个开发人估算:

| 阶段 | 工作内容 | 预计时间 |
| --- | --- | --- |
| 第 1 天 | DDL、实体、Mapper、会话 Service | 1 天 |
| 第 2 天 | 会话接口、聊天入口落库、幂等 | 1 天 |
| 第 3 天 | 前端多会话列表和切换 | 1 天 |
| 第 4 天 | 服务韧性、失败分类、健康检查 | 1 天 |
| 第 5 天 | 动作草稿过期校验、单测补齐 | 1 天 |
| 第 6 天 | 联调、回归、文档更新 | 1 天 |

如果拆成多批发布，建议:

- 第 1 批 3 天: 会话持久化 + 多会话。
- 第 2 批 2 天: 服务韧性 + 幂等。
- 第 3 批 1 天: 动作草稿过期校验。

## 13. 最终验收清单

- [ ] 新建会话后刷新页面，会话仍存在。
- [ ] 同一客服可同时维护多个排查会话。
- [ ] 切换会话后消息、槽位、诊断结果完整恢复。
- [ ] 重复点击发送不会重复调用诊断服务。
- [ ] agent-service 停止时，主系统返回可展示 fallback。
- [ ] fallback 记录包含明确 `fallbackSource` 和 `failureType`。
- [ ] 动作草稿确认前会检测业务数据是否变化。
- [ ] 过期草稿不会执行写操作，并写入动作审计。
- [ ] 会话详情能串联诊断、反馈、动作审计。
- [ ] 相关接口文档和业务说明同步更新。

### 13.1 2026-07-08 当前核对结果

已证明:

- 新建会话后刷新页面，会话仍存在。
  - 证据: 2026-07-08 本地联调，浏览器刷新 `http://localhost:8013/meal/agent/diagnosis` 后，左侧会话区恢复出已有会话项
- 同一客服可同时维护多个排查会话。
  - 证据: 本地接口联调通过 `POST /api/agent/chat-sessions` 连续创建多个会话，`GET /api/agent/chat-sessions` 返回多条会话
- 切换会话后消息、槽位、诊断结果完整恢复。
  - 证据: `AgentChatSessionServiceImplTest`
  - 证据: `AgentChatSessionControllerTest`
  - 证据: `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- 重复点击发送不会重复调用诊断服务。
  - 证据: `AgentChatSessionServiceImplTest.shouldReplayAssistantResponseWhenClientMessageIdAlreadyExists`
- `agent-service` 不可用时，主系统返回可展示 fallback。
  - 证据: `HttpAgentServiceClientTest.shouldReturnChatFallbackWhenAgentServiceUnavailable`
  - 证据: `HttpAgentServiceClientTest.shouldReturnDiagnosisFallbackWhenAgentServiceUnavailable`
  - 证据: 2026-07-08 本地接口联调，停止 `agent-service` 后通过 `POST /api/agent/meal-plan/chat` 返回 `AGENT_SERVICE_UNAVAILABLE`
- fallback 记录包含明确 `fallbackSource` 和 `failureType`。
  - 证据: `HttpAgentServiceClientTest`
  - 证据: `AgentOperationStatsServiceImplTest`
- 动作草稿确认前会检测业务数据是否变化。
  - 证据: `AgentActionConfirmServiceImplTest`
- 过期草稿不会执行写操作，并写入动作审计。
  - 证据: `AgentActionConfirmServiceImplTest`
- 会话详情能串联诊断、反馈、动作审计。
  - 证据: 2026-07-08 本地接口联调，同一 `sessionId` 下 `recentAudits=1`、`recentFeedbacks=1`、`messages=2`
- 相关接口文档和业务说明已同步更新。
  - 证据: `eladmin/doc/apidoc/智能排查助手接口文档.md`
  - 证据: `eladmin/doc/business/智能排查助手业务说明.md`

部分完成:
- 切换会话后的真实浏览器恢复体验。
  - 证据: 已有后端详情接口、前端恢复逻辑和单测
  - 说明: 当前仍缺一次浏览器层“点选不同会话项后完整恢复消息区”的直接证据

待本地联调:
- 停止 `agent-service` 后，前端页面是否按预期展示 fallback 并正常落库。

当前测试/验证证据:

- 后端定向测试已通过（JDK 17 + Maven 3.9.9）：
  - `AgentDiagnosisControllerTest`
  - `AgentOperationStatsServiceImplTest`
  - `AgentActionConfirmServiceImplTest`
  - `HttpAgentServiceClientTest`
  - `AgentChatSessionServiceImplTest`
  - `AgentChatSessionControllerTest`
- `agent-service` 定向测试已通过：
  - `AgentHealthControllerTest`
  - `MealPlanDiagnosisControllerTest`
- 前端当前已完成 `eslint` 校验，agent 页面单测可运行；API 单测运行链仍需进一步处理
