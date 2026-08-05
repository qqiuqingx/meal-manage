# Agent 调试日志实现计划

> **面向 AI 代理的工作者：** 必需子技能：当前环境未提供 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans`，因此将在当前会话中按本计划逐项实现，并在每项后运行验证。

**目标：** 在不改变 Agent 业务行为的前提下，打印每个 LLM 回合、工具回调和主系统查询的完整调试链路，便于用 `requestId` 定位工具参数错误和预算耗尽。

**架构：** 在 `BusinessAgentRunner` 记录模型请求/响应，在 `BusinessAgentTools.GuardedCallback` 记录工具请求/响应，在 `HttpMainSystemQueryClient` 记录主系统查询请求/响应。日志只增加观测，不参与结果判断；所有现有稳定错误码和异常返回保持不变。

**技术栈：** Java 17、Spring Boot 3.5、Spring AI 1.1.6、SLF4J/Logback。

---

## 文件清单与职责

- 修改：`agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java` —— 增加每个模型回合的 LLM 请求、响应、异常和耗时日志。
- 修改：`agent-service/src/main/java/me/zhengjie/agent/tool/BusinessAgentTools.java` —— 增加工具回调入参、缓存命中、结果、稳定错误码和耗时日志。
- 修改：`agent-service/src/main/java/me/zhengjie/agent/client/HttpMainSystemQueryClient.java` —— 增加调用主系统统一查询接口的请求、响应、HTTP 异常和耗时日志。
### 任务 1：实现 LLM 回合调试日志

**文件：**

- 修改：`agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java:132-146`

- [ ] **步骤 1：为 Runner 引入 logger 并拆出实际 user 内容**

使用 Lombok `@Slf4j`，在 `invokeModel` 中保留现有 `prompt.substring(prompt.lastIndexOf("用户问题：") + 6)` 语义，将结果保存为 `userPrompt`，不要改变传给 `ChatClient` 的 system/user 文本。

- [ ] **步骤 2：记录请求、响应和异常**

在 `modelExecutor.execute` 前记录：

```java
log.info("AGENT_DEBUG_LLM_REQUEST requestId={} modelRound={} systemPrompt={} userPrompt={}",
    MDC.get("requestId"), context.modelRounds(), prompt, userPrompt);
```

模型调用成功后记录：

```java
log.info("AGENT_DEBUG_LLM_RESPONSE requestId={} modelRound={} costMs={} content={}",
    MDC.get("requestId"), context.modelRounds(), elapsedMs, answer);
```

模型调用抛出 `RuntimeException` 时记录 `AGENT_DEBUG_LLM_RESPONSE`、`status=FAILED`、异常类型和耗时后原样重新抛出，确保既有 fallback 和 provider 切换逻辑不变。

- [ ] **步骤 3：编译 Runner 相关代码**

运行：

```bash
mvn -q -f agent-service/pom.xml -DskipTests compile
```

预期：编译通过。

### 任务 2：实现工具回调和主系统查询日志

**文件：**

- 修改：`agent-service/src/main/java/me/zhengjie/agent/tool/BusinessAgentTools.java:117-155`
- 修改：`agent-service/src/main/java/me/zhengjie/agent/client/HttpMainSystemQueryClient.java:119-141`

- [ ] **步骤 1：记录工具请求和所有返回路径**

在 `GuardedCallback.call` 开始记录 `AGENT_DEBUG_TOOL_REQUEST`，字段包含 `requestId`、工具名和 `rawInput`；缓存命中、正常结果、护栏异常、下游异常和预算异常分别记录 `AGENT_DEBUG_TOOL_RESPONSE`，字段包含状态、稳定错误码、结果 JSON 和 `costMs`。

记录日志后继续执行现有 `context.record`；不要改变 `errorJson` 内容、缓存行为、调用预算或返回值。

- [ ] **步骤 2：记录主系统查询请求和成功结果**

在 `convert` 内序列化当前 `body` 作为调试字段，记录固定路径、`requestId`、查询 DTO 和开始时间。成功映射为 `ToolResult` 后记录 `status=SUCCESS`、受控结果 JSON 和耗时。

- [ ] **步骤 3：记录主系统异常并保持原稳定码**

在 `RestClientResponseException` 分支记录 HTTP 状态、`resolveFailure(exception)` 的稳定码和受控响应正文；在 `ResourceAccessException`、`MainSystemQueryException` 和其他运行时异常分支记录对应稳定码/异常类型和耗时，然后保持原有异常继续抛出路径。

不得把 `internalToken`、`X-Agent-Access-Context` 或请求头原文加入日志；查询 body 只记录传给统一查询接口的业务 DTO。

### 任务 3：最小验证并提交代码

**文件：**

- 验证：`agent-service` 源码编译和本次差异。

- [ ] **步骤 1：编译 Agent 服务源码**

运行：

```bash
mvn -q -f agent-service/pom.xml -DskipTests compile
```

预期：编译通过。

- [ ] **步骤 2：检查差异和敏感字段**

运行：

```bash
git diff --check
git diff -- agent-service/src/main/java
```

确认只包含三个计划源码文件的变更，日志字符串中没有 `internalToken`、`accessContext`、Authorization 或完整异常堆栈。

- [ ] **步骤 3：提交本次代码改动**

只暂存本计划列出的三个源码文件，使用中文提交说明：

```bash
git add agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java \
  agent-service/src/main/java/me/zhengjie/agent/tool/BusinessAgentTools.java \
  agent-service/src/main/java/me/zhengjie/agent/client/HttpMainSystemQueryClient.java
git commit -m "feat: 增加Agent调试链路日志"
```
