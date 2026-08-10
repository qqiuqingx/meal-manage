# Spring AI 2.0 升级实施计划

## 背景

当前仓库包含两个技术基线不同的后端：

- `eladmin/`：Spring Boot 2.7.18，仍包含 `javax.*`、Spring 5 适配和 Boot 2 专用依赖。
- `agent-service/`：Java 17、Spring Boot 3.5.14、Spring AI 1.1.6，是本次 Spring AI 升级的实际目标。

Spring AI 2.0 需要 Spring Boot 4.x。若把主系统一并升级，会同时引入 Jakarta、Spring Security、MyBatis-Plus、Knife4j、Druid、Redisson 和序列化体系迁移，显著扩大风险。因此本计划只升级独立部署的 `agent-service`，保持它与主系统之间的 HTTP 契约不变。

前期兼容性验证表明：Spring Boot 4.0.7 + Spring AI 2.0.0 可以完成编译、测试和本地启动，但必须同步处理 Spring AI Builder API、OpenAI-compatible Provider 构造方式、DeepSeek 配置键和 Jackson 2 兼容问题。

## 目标

- 将 `agent-service` 固定升级到 Spring AI 2.0.0、Spring Boot 4.0.7。
- 保持 Java 17、端口、环境变量、对主系统 HTTP API 和 Agent 对外响应契约不变。
- 完成 DeepSeek 主 Provider 与 OpenAI-compatible 备用 Provider 的 API 迁移。
- 保持工具白名单、工具调用预算、Guardrail、模型降级和展示结果链路行为不变。
- 通过 Jackson 2 兼容模块完成低风险过渡，不在本次升级中扩大为全量 Jackson 3 改造。
- 形成可验证、可回滚、无数据库变更的独立发布单元。

## 非目标

- 不升级 `eladmin/` 的 Spring Boot、Spring Framework、MyBatis-Plus 或其他依赖。
- 不修改 `eladmin-web/`。
- 不迁移数据库，不新增 SQL。
- 不重构 Agent 业务流程、Prompt、规则文件或展示协议。
- 不在本次任务中完成 Jackson 3 全量迁移；该工作应单独立项。
- 默认不调用收费的真实模型；真实模型验证只作为受控的发布前可选项。

## 目标版本与运行基线

| 项目 | 当前版本 | 目标版本/要求 |
|---|---:|---:|
| Java | 17 | 17 |
| Maven | 本机多版本 | 3.9.9 |
| Spring Boot | 3.5.14 | 4.0.7 |
| Spring AI | 1.1.6 | 2.0.0 |
| Web Starter | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| JSON 过渡方案 | Jackson 2 | `spring-boot-jackson2` + Jackson 2 HTTP Converter |

选择 Boot 4.0.7 是因为该组合已完成临时副本的编译、测试和启动验证。Boot 4.1.x 可在本次稳定上线后作为独立的小版本升级处理，避免一次引入两个未经仓库验证的变量。

## 影响范围

### 必改文件

- `agent-service/pom.xml`
- `agent-service/src/main/resources/application.yml`
- `agent-service/src/main/java/me/zhengjie/agent/infrastructure/llm/SpringAiAgentModelGateway.java`
- `agent-service/src/main/java/me/zhengjie/agent/infrastructure/llm/OpenAiCompatibleProviderModelGateway.java`
- `agent-service/src/test/java/me/zhengjie/agent/infrastructure/llm/SpringAiAgentModelGatewayTest.java`
- `agent-service/README.md`
- `agent-service/docs/architecture-baseline.md`

### 预计新增或扩展的验证文件

- `agent-service/src/test/java/me/zhengjie/agent/infrastructure/llm/OpenAiCompatibleProviderModelGatewayTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/AgentServiceApplicationTest.java`
- 与 Tool Calling、Fallback、Controller、Contract 相关的既有测试；仅在兼容性验证暴露缺口时修改。

### 明确禁止改动

- `eladmin/pom.xml` 及 `eladmin/**`
- `eladmin-web/**`
- `sql/**`

## Phase 列表

| Phase | 名称 | 核心产出 | 状态 |
|---|---|---|---|
| 01 | 版本基线与依赖对齐 | Boot 4/AI 2 依赖树可控，旧代依赖清除 | pending |
| 02 | 模型网关与 Provider 迁移 | 新 Builder API 可编译，双 Provider 行为受测试保护 | pending |
| 03 | Boot 4 配置与启动兼容 | DeepSeek 配置生效，Jackson 2 Bean 与 HTTP 转换正常 | pending |
| 04 | 全链路回归与发布收口 | 测试、启动、契约、文档、回滚材料全部完成 | pending |

Phase 必须按顺序执行。每完成一个 Phase，先更新 `status.yaml`，再加载下一阶段文件，禁止一次性修改全部阶段。

## 核心技术决策

### 1. 使用 Jackson 2 过渡模块

Boot 4 默认转向 Jackson 3，而当前代码大量注入 `com.fasterxml.jackson.databind.ObjectMapper` 并使用 Jackson 2 YAML API。直接升级后应用会因缺少 Jackson 2 `ObjectMapper` Bean 启动失败。

本次采用：

- 增加 `org.springframework.boot:spring-boot-jackson2`。
- 设置 `spring.http.converters.preferred-json-mapper: jackson2`。
- 将原 `spring.jackson.*` 配置迁移到 `spring.jackson2.*`。

这是一项明确的过渡措施。后续 Jackson 3 迁移完成后，应删除兼容模块和 `spring.jackson2` 配置。

### 2. 工具调用由 Advisor 统一驱动

`BusinessAgentRunner` 已通过 `ToolCallAdvisor` 和动态 Tool Callback 驱动工具执行。OpenAI-compatible 模型构造阶段不再依赖旧的 `OpenAiApi`、`RetryTemplate`，并优先移除即将废弃的模型级 `toolCallingManager` 注入，避免同一工具被两套机制重复执行。

若移除后回归测试发现兼容 Provider 必须依赖该组件，允许暂时保留 AI 2 Builder 上的兼容调用，但必须记录弃用原因和后续清理项。

### 3. 保持服务契约不变

升级仅改变框架和模型适配层，不改变：

- Agent Controller 路径、请求字段和响应字段。
- 主系统 Context API 路径与鉴权头。
- 环境变量名称。
- 工具名、工具参数、工具结果和 Presentation Schema。

## 风险与控制

| 风险 | 控制措施 |
|---|---|
| Boot 4 默认 Jackson 3 导致 Bean 缺失或 JSON 行为变化 | 引入 Jackson 2 过渡模块，增加上下文启动和严格反序列化测试 |
| Spring AI Builder API 变化导致编译失败 | 先迁移模型网关并增加参数捕获测试 |
| OpenAI-compatible Provider 构造方式改变 | 对配置完整、配置缺失、模型构造和单次工具执行增加测试 |
| ToolCallAdvisor 与模型级工具管理重复执行 | 以 Advisor 为唯一主路径，增加“同一工具只执行一次”断言 |
| BOM 混入 Spring AI 1.x、Boot 3.x 或 Framework 6.x | 使用 `dependency:tree` 检查并把结果纳入完成标准 |
| 当前工作区存在未提交 Agent 改动 | 每阶段先记录 `git status`，只编辑计划列明文件，不覆盖、不暂存无关修改 |
| 真实模型请求产生费用或泄露密钥 | 默认使用 Mock/Stub；密钥只从环境变量读取，日志和提交中不得出现明文 |

## 总体验收标准

- `agent-service` 在 JDK 17、Maven 3.9.9 下执行 `mvn clean test` 全部通过。
- `mvn clean package` 成功生成可执行 Jar。
- 应用可在无真实模型调用的本地配置下启动，`/api/agent/health` 返回成功。
- Controller、Contract、Fallback、Tool Calling、Guardrail 和 Presentation 相关测试通过。
- 依赖树中不存在 Spring AI 1.x、Spring Boot 3.x、Spring Framework 6.x 的运行时依赖。
- 主系统、前端、数据库和外部 API 契约无变更。
- README 和架构基线准确记录版本、Jackson 过渡方案、启动命令和回滚方式。
- `git diff --check` 无错误，且提交范围不包含任务外的既有修改。

## 回滚策略

本次无数据库变更，回滚以 `agent-service` 制品为单位：

1. 停止 Spring AI 2.0 / Boot 4.0.7 版本实例。
2. 恢复上一版 Spring AI 1.1.6 / Boot 3.5.14 Jar 和原配置。
3. 保持原环境变量、端口和主系统地址不变，重新启动旧实例。
4. 验证健康接口、一次无工具对话和一次工具对话。
5. 若代码级回滚，必须整体回滚 POM、`application.yml` 和两个模型网关，禁止只回退其中一项。

## 执行约束

- 执行前阅读 `status.yaml`，只读取 `current_phase` 对应文件。
- 当前工作区存在其他 Agent 功能修改；禁止清理、覆盖、暂存或提交这些改动。
- 修改或新增 Java 方法时，按仓库要求补充用途、关键参数和返回值注释。
- 若升级导致外部 API 契约变化，应立即停止并重新评估，不得把契约变化作为普通兼容修改继续推进。
