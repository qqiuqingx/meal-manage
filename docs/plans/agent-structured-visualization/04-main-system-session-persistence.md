# Phase 04 主系统透传与会话持久化

## 目标

让主系统完整接收、返回、保存和恢复 `presentations`。新消息恢复时保持原展示决策；旧消息缺少该字段仍可正常反序列化，不重新调用 Agent 或业务查询。

## 依赖

- Phase 02 已确定 Agent v2 `presentations` 契约。

## 输入

- Agent 服务聊天响应中的 `cards + presentations`。
- 现有 `AgentChatMessage.businessResultJson` 快照。

## 输出

- `/api/agent/meal-plan/chat` 返回 `presentations`。
- 会话详情的消息业务结果包含 `presentations`。
- 新快照保存原展示来源、视图和字段引用；旧快照恢复为空列表。
- 不新增数据库字段或迁移脚本。

## 涉及文件

修改：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentChatResponse.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/domain/dto/AgentChatMessageDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/client/HttpAgentServiceClientTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/rest/AgentChatSessionControllerTest.java`

新增：

- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentPresentationSnapshotCompatibilityTest.java`

## 实施步骤

### Step 1：扩展主系统聊天 DTO

- `AgentChatResponse` 增加默认空列表的 `presentations`，保持 fastjson2/Jackson HTTP 客户端兼容。
- `AgentChatMessageDto` 增加展示描述列表，供会话详情直接使用。
- 字段使用受控 Map 列表以兼容 Agent v2 演进，主系统不解释展示业务逻辑。

### Step 2：验证 Agent HTTP 客户端透传

- 扩展 `HttpAgentServiceClientTest` 的 v2 响应样例，断言 descriptor 的 schemaVersion、callId、来源和列顺序未丢失。
- 旧 Agent 响应没有 `presentations` 时得到空列表，不抛反序列化异常。
- HTTP 客户端不重新生成或修改展示描述。

### Step 3：保存消息业务快照

- `buildBusinessSnapshot()` 把 `presentations` 与 cards、warnings、tool trace 一起写入 `businessResultJson`。
- 不把 presentations 写入会话摘要或主模型上下文，避免历史展示策略被误当实时事实。
- 保持现有幂等 `clientMessageId`、会话版本和条件更新流程不变。

### Step 4：恢复聊天响应和会话详情

- `restoreBusinessResponse()` 恢复 presentations；缺失、null 或类型异常时使用空列表。
- 消息 DTO 映射同时暴露 `businessResult.presentations` 和顶层便利字段，行为与 cards 一致。
- 恢复时不调用 Agent、不查实时业务数据、不重新选择默认视图。

### Step 5：覆盖兼容和并发场景

- 新快照保存/恢复后 descriptor 深度相等。
- 旧快照只有 cards 时正常恢复，姓名保持旧 `maskedName`。
- 展示字段不参与会话版本计算和工具摘要，不改变并发冲突语义。
- presentations 为空、非法类型或部分缺失时，其他业务结果继续恢复。

## 验证方式

- `HttpAgentServiceClientTest` 验证 v2 透传和旧响应兼容。
- `AgentChatSessionServiceImplTest` 验证保存、幂等命中和恢复。
- `AgentPresentationSnapshotCompatibilityTest` 使用新旧 JSON 固定样例验证兼容。
- `AgentChatSessionControllerTest` 验证聊天与会话详情响应字段。

## 完成标准

- 新消息的 `cards + presentations` 可无损保存和恢复。
- 旧消息没有 presentations 仍可读取。
- 恢复过程不触发任何 LLM 或业务查询。
- 无 DDL、无历史数据回填、无会话并发语义变化。

## 回滚

- 旧代码会忽略 `business_result_json` 中新增键；回滚无需清洗快照。
- 移除 DTO 字段不会损坏 cards、warnings 或历史消息。

## 状态

completed
