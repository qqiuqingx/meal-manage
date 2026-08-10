# Phase 02 澄清与回复契约

## 目标

建立最小、可兼容的助手回合结果协议，使模型需要补充条件时返回 `NEED_MORE_INFO`、明确缺失项和受控快捷回复，而不是把所有正常文本都标记为 `ANSWERED`。

## 依赖

- Phase 01 已能生成稳定的会话 Patch。
- 保留现有结构化诊断解析和最终答案事实校验能力。

## 输入

- 当前用户问题。
- 本轮成功/失败工具事实。
- Phase 01 生成的最新安全 slots。
- 模型最终输出。

## 输出

- `ANSWERED/NEED_MORE_INFO/ERROR` 三种状态。
- 受控 `missingSlots` 枚举。
- 最多 6 个短文本 `quickReplies`。
- 新响应与历史消息的保存、恢复和前端基础展示。

## 涉及文件

新增建议：

- `agent-service/src/main/java/me/zhengjie/agent/application/conversation/AssistantTurnResult.java`
- `agent-service/src/main/java/me/zhengjie/agent/application/conversation/AssistantTurnParser.java`
- `agent-service/src/main/java/me/zhengjie/agent/domain/chat/MissingSlot.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/conversation/AssistantTurnParserTest.java`

修改：

- `agent-service/src/main/java/me/zhengjie/agent/domain/chat/ChatStatus.java`
- `agent-service/src/main/java/me/zhengjie/agent/domain/dto/AgentChatResponse.java`
- `agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java`
- `agent-service/src/main/java/me/zhengjie/agent/guardrail/FinalAnswerGuardrail.java`
- `agent-service/src/main/java/me/zhengjie/agent/api/controller/AgentV2ChatController.java`
- `agent-service/src/main/resources/openapi/agent-service-v2.yaml`
- `agent-service/src/test/java/me/zhengjie/agent/domain/dto/AgentChatDtoTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/contract/AgentServiceContractTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/api/controller/AgentV2ChatControllerTest.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentChatResponse.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/client/HttpAgentServiceClient.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/domain/dto/AgentChatMessageDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/client/HttpAgentServiceClientTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java`
- `eladmin-web/src/views/agent/diagnosis/index.vue`
- `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`

## 最小协议

模型最终回复优先使用一个受控 JSON 对象：

```json
{
  "outcome": "NEED_MORE_INFO",
  "assistantMessage": "请补充需要查询的客户编号。",
  "missingSlots": ["CUSTOMER"]
}
```

允许字段只有：

- `outcome`：`ANSWERED` 或 `NEED_MORE_INFO`。
- `assistantMessage`：最终面向客服的文本。
- `missingSlots`：受控枚举数组。
- `diagnosisResult`：仅排餐诊断场景可选，仍交由现有 `DiagnosisResultValidator` 校验。

`quickReplies` 不直接信任模型生成，由 Java 根据缺失项和当前 slots 确定性生成。Provider 返回旧纯文本时按兼容路径处理为 `ANSWERED`；不能通过“请提供”“缺少”等关键词猜测状态。

## 缺失项枚举

首版只支持已有工具真正需要的字段：

- `CUSTOMER_OR_ORDER`
- `RECORD_DATE`
- `DATE_RANGE`
- `MEAL_TYPE`
- `PACKAGE`
- `RULE_TOPIC`

不要增加任意字符串 slot。一个问题最多返回 4 个缺失项，按用户最先能补充的顺序展示。

## 快捷回复规则

- `RECORD_DATE`：`今天`、`昨天`、`明天`。
- `MEAL_TYPE`：`早餐`、`午餐`、`晚餐`。
- `DATE_RANGE`：不自动猜范围，只提示用户输入起止日期。
- `CUSTOMER_OR_ORDER`：不生成虚假编号；如果同一条响应有候选客户卡片，选择操作由 Phase 04 提供。
- `PACKAGE/RULE_TOPIC`：仅在已有固定业务选项时生成中文选项。
- 总数最多 6 个，文本最多 20 个中文字符，不生成命令、URL 或内部枚举。

## 实施步骤

### Step 1：增加状态与 DTO 字段

- `ChatStatus` 增加 `NEED_MORE_INFO`。
- Agent 与主系统响应 DTO 增加 `missingSlots`，null 统一为空数组。
- 保留 `conversationStage` 兼容字段，使其与 status 采用同一业务值；不再扩展第二套阶段枚举。
- OpenAPI `status` 和 `missingSlots` 使用严格枚举、数量和长度限制。

### Step 2：实现单一回合解析器

- 尝试解析受控 JSON；校验 outcome、消息非空、缺失项枚举和数量。
- `ANSWERED` 不允许携带非空 missingSlots。
- `NEED_MORE_INFO` 至少携带一个 missingSlot，并且不得声称已取得实时业务结果。
- 纯文本兼容路径只返回 `ANSWERED + assistantMessage`，不猜测 missingSlots。
- 非法 JSON 使用一次现有回答修复预算；仍失败时沿用现有稳定 fallback，不再套额外解析器。

### Step 3：调整 Prompt 和答案校验

- system prompt 明确最终输出最小 JSON Schema，并给出普通回答与澄清各一个短例子。
- 有成功工具事实的 `ANSWERED` 继续执行事实、身份和明细重复校验。
- `NEED_MORE_INFO` 允许零成功工具，但回答中不得包含实时数字、状态或“已查询”结论。
- 结构化诊断继续使用现有规则和证据校验，不因回合协议降低要求。

### Step 4：生成快捷回复

- 根据 missingSlots 和当前 slots 调用固定映射生成中文回复。
- 不让模型生成任意按钮文本，不新增数据库配置表。
- 错误响应只提供“重试本条”；该按钮由 Phase 04 根据前端原消息生成，不作为 Agent quickReply。

### Step 5：主系统透传与持久化

- HTTP 客户端解析新字段并保留旧响应兼容。
- `buildBusinessSnapshot()` 增加 `missingSlots`、`quickReplies`，继续写入现有 JSON 字段。
- `restoreBusinessResponse()` 和消息 DTO 恢复新字段；旧快照缺失时返回空数组。
- 会话 stage 保存 `NEED_MORE_INFO`，下一轮成功后更新为 `ANSWERED`。
- 不新增数据库列和迁移 SQL。

### Step 6：前端基础接线

- `addAssistantResponse()` 保存 `requestId/clientMessageId/missingSlots/quickReplies`。
- 历史消息恢复同样读取这些字段。
- 消息顶部不再为每条 `ANSWERED` 显示“阶段：已回答”；只在 `NEED_MORE_INFO` 或 `ERROR` 时展示状态。
- Phase 02 只完成基本展示，按钮样式与候选交互在 Phase 04 完成。

## 验证方式

- Parser 测试覆盖合法回答、合法澄清、非法枚举、空消息、纯文本兼容和诊断 payload。
- Runner 测试覆盖无工具澄清、有工具回答、回答修复和 Provider 旧文本。
- OpenAPI 契约测试验证新增 enum 和数组限制。
- 主系统测试覆盖新快照恢复、旧快照兼容和 stage 更新。
- 前端测试覆盖缺失项中文展示和历史恢复。

## 完成标准

- 缺少必要条件的问题返回 `NEED_MORE_INFO`，不再伪装为已回答。
- `missingSlots` 和 `quickReplies` 能跨主系统保存并在刷新后恢复。
- 普通旧纯文本响应继续可用。
- 不使用关键词判断澄清，不新增额外模型调用或复杂状态机。
- 结构化诊断的规则、证据和安全校验不回退。

## 回滚

- 前端和主系统遇到未知状态时按普通助手消息展示。
- 回滚 Agent 后，新字段为空，旧 `ANSWERED/ERROR` 行为继续工作。
- JSON 快照新增键可被旧代码忽略，无需清理历史记录。

## 状态

completed
