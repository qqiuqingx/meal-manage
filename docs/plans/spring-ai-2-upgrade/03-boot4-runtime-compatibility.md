# Phase 03：Boot 4 配置与启动兼容

## 目标

完成 Spring Boot 4 下的配置键迁移和 Jackson 2 兼容配置，确保应用上下文、HTTP JSON 转换、YAML 规则加载及 DeepSeek 自动配置正常。

## 输入

- Phase 02 已通过编译和模型网关测试的代码。
- 当前 `application.yml` 与现有环境变量约定。
- 现有上下文启动、配置绑定、Controller 和规则加载测试。

## 涉及文件

修改：

- `agent-service/src/main/resources/application.yml`
- `agent-service/src/test/java/me/zhengjie/agent/AgentServiceApplicationTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/config/AgentPropertiesTest.java`（仅在配置断言需要时）
- `agent-service/src/test/java/me/zhengjie/agent/rule/RuleRegistryLoaderTest.java`（仅在 YAML 兼容性缺口存在时）
- `agent-service/src/test/java/me/zhengjie/agent/api/controller/AgentV2ChatControllerTest.java`（仅在 JSON Converter 行为需要补充时）

不得批量修改全部 Jackson import；本阶段只建立 Boot 4 上的兼容运行基线。

## 实施步骤

### Step 1：迁移 DeepSeek 模型配置键

将：

```yaml
spring:
  ai:
    deepseek:
      chat:
        options:
          model: ...
```

迁移为：

```yaml
spring:
  ai:
    deepseek:
      chat:
        model: ...
```

并在 `spring.ai.model` 下显式设置：

```yaml
chat: deepseek
```

继续保留 audio、embedding、image 的 `none` 配置，防止创建无关模型 Bean。

所有现有环境变量名保持不变：

- `AGENT_DEEPSEEK_API_KEY`
- `AGENT_OPENAI_API_KEY`（兼容回退）
- `AGENT_DEEPSEEK_BASE_URL`
- `AGENT_OPENAI_BASE_URL`
- `AGENT_DEEPSEEK_MODEL`
- `AGENT_OPENAI_MODEL`

### Step 2：迁移 Jackson 配置命名空间

将：

```yaml
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: true
```

迁移为：

```yaml
spring:
  http:
    converters:
      preferred-json-mapper: jackson2
  jackson2:
    deserialization:
      fail-on-unknown-properties: true
```

保持严格反序列化语义，避免升级后未知字段被静默忽略。

### Step 3：增强应用上下文启动测试

扩展 `AgentServiceApplicationTest`，至少验证：

- Spring Boot 应用上下文成功启动。
- 存在 `com.fasterxml.jackson.databind.ObjectMapper` Bean。
- HTTP Converter 实际使用 Jackson 2。
- DeepSeek ChatModel/ChatClient 相关 Bean 能在占位 API Key 下创建，但测试不发送请求。
- OpenAI-compatible Provider 默认禁用，不因空配置阻塞启动。

若完整上下文依赖主系统地址，使用 Stub/Mock Bean 隔离，不访问 `localhost:8000` 或外网。

### Step 4：验证 JSON 严格反序列化

在最合适的 Controller 或 ObjectMapper 测试中验证：

- 合法 Agent 请求可反序列化。
- 包含未知字段的请求仍按当前契约失败。
- Controller 正常响应结构与升级前一致。
- 日期、枚举、空值和泛型集合序列化没有发生意外变化。

不改变 API 字段以迁就框架默认值；若发现差异，优先显式配置兼容行为。

### Step 5：验证 YAML 规则加载

运行 `RuleRegistryLoaderTest` 及相关 Presentation 规则测试，确认：

- `jackson-dataformat-yaml` 可正常创建 YAMLMapper。
- classpath 与外部目录覆盖规则不变。
- 未知字段检查和枚举解析行为不变。
- 中文规则内容无编码问题。

### Step 6：本地启动冒烟验证

先构建：

```bash
mvn clean package
```

再使用非生产占位配置启动 Jar；不得提供真实密钥，不触发真实对话：

```bash
AGENT_DEEPSEEK_API_KEY=unused \
java -jar target/agent-service-1.0.0-SNAPSHOT.jar
```

检查：

- 端口 18081 正常监听。
- 启动日志无 `ObjectMapper` Bean 缺失、配置绑定失败和模型 Bean 冲突。
- `/api/agent/health` 健康接口成功。
- 停止进程后端口释放。

### Step 7：检查配置元数据和废弃项

审查启动日志中的 deprecated/unknown property 警告。对于本次涉及配置：

- 未识别的 Spring AI 配置必须修复。
- Jackson 2 过渡模块的弃用说明记录到文档，不以关闭日志方式掩盖。
- 业务自定义 `agent.*` 配置键保持不变。

## 验证方式

重点测试：

```bash
mvn -Dtest='AgentServiceApplicationTest,AgentPropertiesTest,RuleRegistryLoaderTest,AgentV2ChatControllerTest,AgentServiceContractTest' test
mvn clean package
```

人工检查：

- 健康接口响应。
- 启动日志。
- 端口启动和停止状态。
- `application.yml` 中不再存在 `spring.ai.deepseek.chat.options.model` 和 `spring.jackson` 旧配置。

## 完成标准

- Boot 4 应用上下文成功启动。
- Jackson 2 ObjectMapper、HTTP Converter 和 YAMLMapper 均可用。
- 严格反序列化行为与升级前一致。
- DeepSeek 配置通过新键生效，备用 Provider 默认禁用。
- 无真实模型调用、无密钥泄露、无外部系统依赖。
- 健康接口成功且启动进程可干净停止。

## 回滚点

回滚 `application.yml` 时必须同时回滚 POM 中的 `spring-boot-jackson2` 和 Boot/Spring AI 版本。旧 `spring.jackson` 配置不能与 Boot 4 迁移结果混搭。

## 状态

completed
