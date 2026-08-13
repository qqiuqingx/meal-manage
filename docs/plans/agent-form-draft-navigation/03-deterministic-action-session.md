# Phase 03 确定性草稿动作与会话恢复

## 目标

把 `saveFormDraft` 成功事实转换为跨服务稳定协议：模型文本负责说明，系统事实决定状态；`READY` 草稿生成固定表单导航动作，缺少唯一客户的可编辑新增订单仅生成固定转换对话动作，并能在幂等重放和历史会话中恢复。

## 依赖与输入

- Phase 02 的强类型工具输出和 ToolFact。
- 当前 `BusinessAgentRunner.assemble()`、主系统 `business_result_json` 快照和前端响应兼容机制。

## 输出

- 跨服务 `formDraftSummary/uiActions` 契约和确定性动作生成器。
- 支持幂等重放、历史会话恢复和草稿状态刷新的脱敏快照。

## 涉及文件

新增建议：

- `agent-service/src/main/java/me/zhengjie/agent/domain/dto/FormDraftSummary.java`
- `agent-service/src/main/java/me/zhengjie/agent/domain/dto/AgentUiAction.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentFormDraftSummaryDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentUiActionDto.java`
- 对应 Runner、契约和会话恢复测试。

修改：

- `agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java`
- `agent-service/src/main/java/me/zhengjie/agent/domain/dto/AgentChatResponse.java`
- `agent-service/src/main/resources/openapi/agent-service-v2.yaml`
- `agent-service/src/test/java/me/zhengjie/agent/contract/AgentServiceContractTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/BusinessAgentRunnerConversationTest.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentChatResponse.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/client/HttpAgentServiceClient.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java`

## 实施步骤

### Step 1：定义响应字段

- 增加脱敏 `formDraftSummary`：`draftId/type/status/revision/recognizedFields/missingFields/warnings/expiresAt`。
- 增加固定 `uiActions`：只允许 `OPEN_CREATE_CUSTOMER_WITH_ORDER_FORM`、`OPEN_CREATE_ORDER_FORM`、`CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER`。
- 动作 payload 只包含 `draftId` 和受控来源会话标识，不包含 URL、route、手机号、地址或完整 payload。
- 新字段保持可选，旧主系统/前端可以忽略。

### Step 2：从工具事实确定性组装

- Runner 只读取成功的 `saveFormDraft` 输出，不从模型回答反向解析草稿。
- `success=true + EDITABLE` 生成摘要；新增订单缺少唯一客户且无多匹配歧义时只生成固定转换对话动作，不生成打开表单动作。
- `success=true + READY` 按 draftType 生成固定动作。
- 工具失败或输出护栏失败时不生成摘要/动作；模型声称成功时由回答护栏修复或降级。

### Step 3：隔离展示和查询事实

- `saveFormDraft` 不进入通用业务表格/图表 PresentationRegistry。
- 不把完整工具输入或 payload 放入 `facts/cards/toolFacts` 的前端可见内容。
- `toolTraceSummary` 只记录工具名、状态和 callId。
- 查询上下文更新器忽略草稿写工具，避免污染客户/订单实时查询焦点。

### Step 4：贯通主系统契约

- Java 8 主系统 DTO 兼容解析新增字段。
- `HttpAgentServiceClient` 对未知/缺失字段保持当前兼容行为。
- `AgentChatSessionServiceImpl` 在 `business_result_json` 中保存脱敏摘要与固定动作。
- 幂等消息重放和历史会话详情恢复相同摘要/动作，不重新调用模型或草稿接口。

### Step 5：处理状态变化

- 会话恢复时可用摘要接口刷新 `SUBMITTED/EXPIRED/CANCELLED`，但不回填完整 payload。
- 已失效草稿的打开动作由主系统或前端变为不可用状态。
- 不新增独立草稿审计或业务查询审计字段；现有审计服务忽略草稿 payload。

## 验证方式

- Runner 测试覆盖 EDITABLE、READY、失败、模型文本与工具事实冲突。
- OpenAPI 与 DTO 契约测试覆盖动作枚举、额外字段拒绝和敏感字段缺失。
- 会话测试覆盖首次响应、消息幂等重放、刷新历史和旧快照兼容。
- `saveFormDraft` 不生成通用卡片或图表。
- 两端 Maven 精准测试并执行 `git diff --check`。

## 完成标准

- UI 动作完全由工具事实确定，模型不能控制路由。
- 历史消息恢复后仍可看到正确草稿状态和动作。
- 会话快照不包含完整草稿 payload、手机号或地址副本。
- 旧对话响应与旧历史快照正常展示。

## 回滚

- 主系统和前端忽略新增可选字段后，Agent 仍可返回文本。
- 移除 `saveFormDraft` 白名单即可停止产生新摘要和动作。

## 状态

completed
