# Phase 02：模型网关与 Provider 迁移

## 目标

适配 Spring AI 2.0 的 ChatClient、ChatOptions 和 OpenAI ChatModel Builder API，使 DeepSeek 主 Provider 与 OpenAI-compatible 备用 Provider 能在新版本下编译，并通过隔离测试保护原有模型选择与工具调用语义。

## 输入

- Phase 01 已完成的 Boot 4 / AI 2 依赖基线。
- Phase 01 记录的已知编译错误。
- 当前 `BusinessAgentRunner` 中基于 `ToolCallAdvisor` 的工具调用机制。

## 涉及文件

修改：

- `agent-service/src/main/java/me/zhengjie/agent/infrastructure/llm/SpringAiAgentModelGateway.java`
- `agent-service/src/main/java/me/zhengjie/agent/infrastructure/llm/OpenAiCompatibleProviderModelGateway.java`
- `agent-service/src/test/java/me/zhengjie/agent/infrastructure/llm/SpringAiAgentModelGatewayTest.java`

新增：

- `agent-service/src/test/java/me/zhengjie/agent/infrastructure/llm/OpenAiCompatibleProviderModelGatewayTest.java`

按测试结果可能扩展，但不得无依据重构：

- `agent-service/src/test/java/me/zhengjie/agent/application/BusinessAgentRunnerConversationTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/BusinessAgentRunnerRecoveryTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/infrastructure/llm/FallbackModelExecutorTest.java`

## 实施步骤

### Step 1：迁移 ChatOptions Builder 传参

在 `SpringAiAgentModelGateway` 中，将两处：

```java
.defaultOptions(ChatOptions.builder().model(model).build())
```

迁移为 Spring AI 2.0 接受的 Builder：

```java
.defaultOptions(ChatOptions.builder().model(model))
```

覆盖场景：

- 根据模型 Profile 首次创建 ChatClient。
- 基于现有 Builder 克隆并切换模型。

保持模型选择、Provider 选择和 fallback 顺序不变。

### Step 2：更新 ChatOptions 测试捕获方式

`SpringAiAgentModelGatewayTest` 当前捕获的是构建完成的 `ChatOptions`。更新为捕获 `ChatOptions.Builder<?>`，再调用 `build()` 断言模型名。

至少验证：

- 默认 Profile 使用默认模型。
- Presentation Profile 使用展示模型。
- 克隆 Builder 后不会污染原 Builder 的模型配置。
- 未知或禁用 Provider 的异常语义不变。

### Step 3：重写 OpenAI-compatible 模型构造

从 `OpenAiCompatibleProviderModelGateway` 删除旧 API：

- `org.springframework.ai.openai.api.OpenAiApi`
- `org.springframework.retry.support.RetryTemplate`
- `.openAiApi(...)`
- `.defaultOptions(...)`
- `.retryTemplate(...)`

使用 Spring AI 2.0 的 `OpenAiChatOptions` 直接传入：

- `baseUrl`
- `apiKey`
- `model`

模型 Builder 继续显式设置 `ObservationRegistry.NOOP`，除非仓库后续已有统一 ObservationRegistry Bean。重试次数继续由现有业务层 Profile/Fallback 机制控制，不在模型 SDK 内增加隐式重试，避免一次业务请求产生不可见的重复模型调用。

### Step 4：统一工具执行责任

优先从 OpenAI-compatible 模型 Builder 移除模型级 `toolCallingManager` 注入，以 `BusinessAgentRunner` 的 `ToolCallAdvisor` 为唯一工具循环入口。

验证重点：

- Tool Callback 仍由当前请求动态传入。
- 工具白名单仍生效。
- `max-tool-calls`、`max-model-rounds`、超时和记录数限制仍生效。
- 单个模型工具请求只触发一次业务工具执行。

若兼容 Provider 的实际测试证明必须保留 `toolCallingManager`，可临时保留 AI 2 Builder 上的兼容调用，但必须：

- 在代码注释中说明弃用风险。
- 增加防重复执行测试。
- 在 README 中记录后续移除项。

### Step 5：补充备用 Provider 单元测试

新增 `OpenAiCompatibleProviderModelGatewayTest`，通过 Mock/Stub 覆盖：

- Provider 未启用时不创建模型客户端。
- `base-url`、`api-key`、`model` 缺失时按当前配置校验规则失败。
- 配置完整时可创建 ChatClient/ChatModel，且选项被正确传入。
- 不发生真实网络请求。
- 不在异常、日志或 `toString()` 中泄露 API Key。

如 Spring AI 2 的 Builder 不便直接捕获内部选项，使用包边界内可观察行为或小型 Factory seam 测试；不得为了测试引入真实模型调用。

### Step 6：回归 Fallback 与工具调用

执行相关测试：

```bash
mvn -Dtest='SpringAiAgentModelGatewayTest,OpenAiCompatibleProviderModelGatewayTest,FallbackModelExecutorTest,BusinessAgentRunnerConversationTest,BusinessAgentRunnerRecoveryTest' test
```

若现有测试没有覆盖“模型要求调用一个工具，业务工具恰好执行一次”，在最接近现有测试结构的 Runner 测试中补充该断言。

### Step 7：完成主代码编译

执行：

```bash
mvn clean test
```

此时不应再出现 Spring AI 1.x API 编译错误。若出现新的弃用警告，区分：

- 可直接移除且有测试保护：本阶段处理。
- 需要改变业务机制：记录为后续项，不扩大本次升级范围。

## 验证方式

- 主代码与测试代码均可编译。
- 模型 Profile 和模型名称选择断言通过。
- DeepSeek 与 OpenAI-compatible 两条构造路径均有自动化覆盖。
- Fallback 顺序、失败传播和重试次数不变。
- Tool Calling 单次执行、白名单和预算测试通过。
- 代码中不再引用 `OpenAiApi` 和 `RetryTemplate`。

可用检查：

```bash
rg -n 'OpenAiApi|RetryTemplate|openAiApi\(|retryTemplate\(' src/main src/test
git diff --check
```

## 完成标准

- 所有已知 Spring AI 2 Builder API 编译问题解决。
- 双 Provider 行为由自动化测试保护，测试不访问外网。
- 工具执行责任明确，不存在双重工具循环。
- API Key 不进入代码、测试数据快照或日志。
- 新增或修改的方法已补充清晰注释。

## 回滚点

将本阶段 Java 和测试改动与 Phase 01 的 POM 一起回滚，恢复 Spring AI 1.1.6 API。禁止只恢复旧 `OpenAiApi` 代码而保留 Spring AI 2.0 依赖。

## 状态

completed
