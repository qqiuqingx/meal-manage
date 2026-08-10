# Phase 04：全链路回归与发布收口

## 目标

验证 Spring AI 2.0 升级未改变 Agent 的外部契约、工具调用安全边界和业务结果，并补齐版本文档、发布检查和可操作的回滚说明。

## 输入

- Phase 01 至 Phase 03 已完成的代码和配置。
- 升级前测试基线。
- 当前工作区未提交改动清单。

## 涉及文件

修改：

- `agent-service/README.md`
- `agent-service/docs/architecture-baseline.md`

按回归结果可能修改：

- 已在前序 Phase 列明的兼容性测试。
- 与 Tool Calling、Guardrail、Fallback、Controller、Contract、Presentation 直接相关的既有测试。

本阶段不新增业务功能，不修改业务文档和 API 文档，除非测试证明外部契约实际发生变化；若发生契约变化，应停止发布并重新评估，而不是直接更新文档接受变化。

## 实施步骤

### Step 1：执行完整自动化测试

在 `agent-service/` 下执行：

```bash
mvn clean test
```

重点确认以下测试域全部通过：

- 应用上下文与配置绑定。
- Agent V2 Controller 和服务契约。
- Conversation Context 与结果解析。
- DeepSeek 主 Provider、备用 Provider 和 Fallback。
- 工具契约、工具结果身份与工具白名单。
- Guardrail、最终回答客户身份和修复逻辑。
- Presentation Registry、通用展示和 LLM 展示降级。
- Architecture Boundary。

记录总测试数、失败数、跳过数和耗时，与升级前基线对比。

### Step 2：执行关键链路定向回归

至少覆盖：

1. 无工具普通对话：返回结构与状态正常。
2. 单工具对话：工具只执行一次，结果进入最终回答。
3. 工具参数非法：校验失败，不调用主系统。
4. 工具超时或异常：按现有恢复策略返回，不无限重试。
5. 主 Provider 失败：按配置进入 OpenAI-compatible fallback；fallback 禁用时错误清晰。
6. 超出工具次数或模型轮次：预算限制仍终止循环。
7. Presentation Profile：不启用工具调用，展示结果可降级。

自动化测试应使用 Mock/Stub，不访问真实主系统、数据库或模型 API。

### Step 3：检查外部契约不变

运行：

```bash
mvn -Dtest='AgentServiceContractTest,AgentV2ChatControllerTest,AgentHealthControllerTest,UnifiedToolContractTest,ToolOutputIdentityContractTest' test
```

对比确认：

- Controller 路径、HTTP 状态和请求/响应字段不变。
- Context API 地址和内部 Token Header 不变。
- Tool Definition 名称、参数和结果 Schema 不变。
- Presentation 数据结构不变。
- 环境变量名、默认端口和日志路径不变。

### Step 4：检查依赖与构建制品

执行：

```bash
mvn dependency:tree
mvn clean package
jar tf target/agent-service-1.0.0-SNAPSHOT.jar
```

检查：

- 运行时不含 Spring AI 1.x、Boot 3.x、Framework 6.x。
- Jar 可执行，规则 YAML 资源已打包。
- 不包含 `.env`、密钥、临时日志或测试报告以外的本地敏感文件。
- 制品名称和部署入口保持不变。

### Step 5：执行最终启动冒烟

使用与 Phase 03 相同的无真实调用配置启动最终 Jar：

- 验证 `/api/agent/health` 健康接口。
- 发起不会触发付费模型请求的本地 Controller 契约检查。
- 检查日志无 Bean 冲突、未知配置、重复 Tool Advisor 和序列化异常。
- 正常停止并确认 18081 端口释放。

可选真实模型验证必须满足：

- 用户明确授权并提供环境变量形式的测试密钥。
- 使用独立测试会话和最小 Token 请求。
- 覆盖一次普通回答和一次只读工具调用。
- 日志、命令历史和测试报告不记录密钥。

真实模型验证不是默认验收门槛。

### Step 6：更新文档

`agent-service/README.md` 至少更新：

- Java、Boot、Spring AI 版本。
- JDK 17 + Maven 3.9.9 构建命令。
- DeepSeek 新配置键对应的环境变量说明。
- OpenAI-compatible fallback 的启用条件。
- Jackson 2 兼容模块是过渡方案。
- 本地启动、健康检查和常见启动错误。

`agent-service/docs/architecture-baseline.md` 至少更新：

- Spring AI 2.0 模型适配边界。
- ToolCallAdvisor 是工具循环的唯一责任方。
- SDK 内不做隐式重试，重试和 fallback 由业务层控制。
- Jackson 2 兼容边界和后续 Jackson 3 迁移债务。

### Step 7：质量与工作区边界检查

执行：

```bash
git diff --check
git status --short
git diff -- agent-service docs/plans/spring-ai-2-upgrade
```

要求：

- 不覆盖用户已有的 Agent 功能修改。
- 不修改主系统、前端和 SQL。
- 不把日志、target、密钥或临时文件纳入提交。
- 新增/修改方法注释符合仓库规范。
- 提交时按文件精确暂存，中文描述，例如：`chore: 升级 Agent Service 至 Spring AI 2.0`。

除非用户明确要求，本计划执行不自动创建 Git 提交。

### Step 8：准备回滚验证

在发布说明中记录：

- 上一版 Jar 的制品位置或版本号。
- 当前版本启停命令和健康检查地址。
- 整体回滚顺序。
- 回滚后验证场景：健康接口、普通对话、单工具对话。

确认无数据库迁移、无配置中心不可逆变更，因此可直接进行应用制品回滚。

## 验证方式

- 完整测试套件通过。
- 定向契约与工具安全测试通过。
- 最终 Jar 构建和启动成功。
- 文档与实际版本、配置和行为一致。
- 依赖树、工作区和制品内容检查通过。

## 完成标准

- `mvn clean test`、`mvn clean package` 全部成功。
- 健康检查和关键链路冒烟成功。
- 对外 HTTP、工具和环境变量契约无变化。
- 工具只执行一次，预算、Guardrail、Fallback 行为无回归。
- 文档已记录升级基线、过渡债务和回滚方法。
- 无任务外文件、敏感信息或构建产物进入变更集。
- `status.yaml` 所有 Phase 更新为 `completed`，总体状态更新为 `complete`。

## 回滚点

若任一强制验收失败，不发布新 Jar。已部署时整体恢复 Spring AI 1.1.6 / Boot 3.5.14 的上一版制品；无需执行 SQL 回滚。回滚后必须重新执行健康、普通对话和单工具对话验证。

## 状态

completed
