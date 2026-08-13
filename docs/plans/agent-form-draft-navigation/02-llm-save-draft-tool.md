# Phase 02 LLM `saveFormDraft` 工具

## 目标

将 `saveFormDraft` 作为第 13 个模型可见工具接入现有 Spring AI Tool Calling，并为敏感输入、副作用缓存、幂等重试和确定性输出建立专用边界。

## 依赖与输入

- Phase 01 的内部保存接口和 DTO 契约。
- 当前 `ToolRegistry -> BusinessAgentTools -> MainSystemQueryClient` 工具链。
- 当前工具预算为每轮最多 6 次，新增工具仍计入预算。

## 输出

- 模型白名单可见的 `saveFormDraft` 工具和固定主系统客户端。
- 副作用工具元数据、专用敏感数据护栏、幂等/非缓存语义及契约测试。

## 涉及文件

新增建议：

- `agent-service/src/main/java/me/zhengjie/agent/tool/input/formdraft/SaveFormDraftInput.java`
- `agent-service/src/main/java/me/zhengjie/agent/tool/input/formdraft/CustomerWithOrderDraftInput.java`
- `agent-service/src/main/java/me/zhengjie/agent/tool/input/formdraft/CustomerOrderDraftInput.java`
- `agent-service/src/main/java/me/zhengjie/agent/tool/output/FormDraftToolOutput.java`
- `agent-service/src/main/java/me/zhengjie/agent/client/MainSystemFormDraftClient.java`
- `agent-service/src/main/java/me/zhengjie/agent/client/HttpMainSystemFormDraftClient.java`
- `agent-service/src/main/java/me/zhengjie/agent/guardrail/FormDraftSensitiveDataPolicy.java`
- `agent-service/src/test/java/me/zhengjie/agent/tool/SaveFormDraftToolTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/guardrail/FormDraftSensitiveDataPolicyTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/client/HttpMainSystemFormDraftClientTest.java`

修改：

- `agent-service/src/main/java/me/zhengjie/agent/tool/ToolRegistry.java`
- `agent-service/src/main/java/me/zhengjie/agent/tool/BusinessAgentTools.java`
- `agent-service/src/main/java/me/zhengjie/agent/guardrail/ToolInputGuardrail.java`
- `agent-service/src/main/java/me/zhengjie/agent/guardrail/ToolOutputGuardrail.java`
- `agent-service/src/main/java/me/zhengjie/agent/guardrail/ToolExecutionContext.java`
- `agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java`
- `agent-service/src/main/java/me/zhengjie/agent/config/AgentServiceConfig.java`
- `agent-service/src/test/java/me/zhengjie/agent/architecture/ArchitectureBoundaryTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/tool/UnifiedToolContractTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/security/DefaultAgentQueryPermissionServiceTest.java`

## 实施步骤

### Step 1：扩展工具元数据

- 为 `ToolSpec` 增加 `effect` 或等价元数据，值至少包含 `READ_ONLY` 与 `FORM_DRAFT_WRITE`。
- 增加 `cacheable`/重试策略，原 12 个工具保持只读缓存；`saveFormDraft` 禁止 Agent 内存同参缓存。
- 架构测试从固定“12 个只读工具”改为“12 READ_ONLY + 1 FORM_DRAFT_WRITE”，并禁止新增其他写工具。

### Step 2：建立 Agent 强类型 DTO

- `SaveFormDraftInput` 与主系统 `schemaVersion=v1` 字段目录一致。
- 创建携带 `clientMessageId` 幂等键；修订携带 `draftId + expectedRevision`。
- 不允许模型传 owner、权限、内部令牌、URL、status、目标业务 ID。
- 用契约测试比较两端字段集合和枚举，防止独立 DTO 漂移。

### Step 3：实现专用敏感输入护栏

- 仅对 `saveFormDraft` 的登记路径允许完整手机号、联系人电话和地址。
- 继续拒绝 Token、权限、SQL、URL、超长文本、提示注入和未登记字段。
- 现有 12 个查询工具仍使用当前 `SensitiveDataPolicy`，不能因本功能整体放宽。
- 工具日志继续使用脱敏格式化器；即使 `AGENT_CHAT_LOG_CONTENT=true` 也不能输出草稿原文。

### Step 4：执行副作用工具

- 增加固定主系统草稿客户端，不允许动态 URL。
- `BusinessAgentTools` 在调用前消耗预算，但跳过只读 cache 查找/写入。
- 网络重试只复用同一幂等键；Provider 重放也由主系统返回同一草稿。
- 失败统一返回 `success=false/errorCode/retryable/message`，不把异常原文交给模型。

### Step 5：验证输出和提示

- 输出护栏要求 `success=true` 时存在合法 `draftId/status/revision/expiresAt`。
- `status` 只能为 `EDITABLE/READY`；模型不得自行修改。
- 更新系统提示：允许“保存辅助草稿”，仍禁止声称已新增客户或订单。
- 更新最终回答护栏，允许“草稿已保存/已准备好”，继续拒绝“客户已新建/订单已创建”。

## 验证方式

- 工具注册数量、effect 和 cacheable 契约测试。
- 手机号/地址只在草稿 DTO 路径放行，查询工具和回答仍拒绝。
- 同参调用两次确实到达主系统，但返回同一幂等草稿；Agent cacheHits 不增加。
- 修订版本冲突返回 `DRAFT_VERSION_CONFLICT`。
- `mvn399 -q -DskipTests=false -Dtest='*SaveFormDraft*,*UnifiedToolContractTest,*ArchitectureBoundaryTest,*FinalAnswer*' test`。

## 完成标准

- 模型白名单可见 `saveFormDraft`，无权限时不可见。
- 工具可创建/修订草稿且不会重复创建。
- 敏感资料不出现在普通日志或工具错误输出。
- 现有 12 个只读工具行为与测试不回归。

## 回滚

- 从主系统可用工具集合移除 `saveFormDraft` 即可停止模型调用。
- 保留 Phase 01 草稿接口供测试和已领取草稿完成，不影响只读 Agent。

## 状态

completed
