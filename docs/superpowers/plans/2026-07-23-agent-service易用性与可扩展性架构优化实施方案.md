# agent-service 易用性与可扩展性架构优化实施方案

> 编制日期：2026-07-23  
> 适用项目：`/Users/qqx/job/code/eladmin-mp`  
> 目标模块：`agent-service`，以及与其直接通信的 `eladmin-system` Agent 模块  
> 核心方向：开发易用性、能力可扩展性、跨服务契约、会话一致性、模型与工具适配  
> 实施方式：保持现有接口兼容，采用绞杀式重构；每阶段独立测试、独立灰度、可单独回滚  
> 当前状态：实施中。阶段 0/1 已完成首批基础设施，阶段 2 正在进行；旧接口保持兼容。

## 实施进度

| 阶段 | 状态 | 最近更新 | 已完成范围 / 待办 |
|---|---|---|---|
| 阶段 0：架构基线与保护网 | 进行中 | 2026-07-24 | README、架构基线、测试日志控制完成；架构守护测试待新包边界落地后补充。 |
| 阶段 1：规则与配置基础设施 | 进行中 | 2026-07-24 | 递归规则扫描、强类型 YAML、classpath 一致性测试和基础配置对象完成；其余配置迁移待后续统一收口。 |
| 阶段 2：版本化跨服务契约 | 已完成 | 2026-07-24 | v2 OpenAPI、执行信封、稳定错误协议、主系统消息 DTO 隔离、v1/v2 灰度客户端和双向字段兼容测试均已完成。 |
| 阶段 3：聊天协调器与能力处理器 | 已完成 | 2026-07-24 | 已建立无业务分支的 Coordinator、Handler SPI、兼容 Facade 与 Legacy Handler；Controller 和 v2 API 均通过 Facade 路由，扩展性/冲突/架构守护及全量回归测试通过。 |
| 阶段 4：统一能力目录、Planner 和工具注册 | 进行中 | 2026-07-27 | 已完成强类型能力 YAML、profile/权限启动校验、ToolCatalog 与旧 Registry/Executor 兼容适配；强类型工具 DTO、目录裁剪和扩展性契约测试待完成。 |
| 阶段 5：统一会话真相源并支持并发 | 进行中 | 2026-07-27 | v2 返回 `ConversationPatch`，主系统使用数据库 `@Version` 条件更新；真实多线程并发、幂等和影子比对验证待完成。 |
| 阶段 6：通用 API、模型适配与开发体验收口 | 进行中 | 2026-07-27 | 模型 profile、诊断/理解/连通性网关接入、分层健康端点、MDC Filter、测试日志和文档完成首轮收口；指标与完整交付验证待完成。 |

## 1. 背景与总体结论

`agent-service` 已具备以下良好基础：

- QueryPlan 白名单及服务端校验；
- 工具权限预过滤与主系统二次鉴权；
- 规则、提示词策略、结果校验和 fallback；
- 业务查询与排餐诊断评测集；
- 会话持久化、Pending Context、Last Context 和任务栈；
- 模型优先、规则兜底的语义理解链路；
- 283 个现有测试通过，0 失败，1 跳过。

当前主要问题不是功能缺失，而是系统正在从“排餐未生成诊断服务”演进为“通用内部智能客服 Agent”，原有中心式编排结构已经开始阻碍继续扩展：

1. `MealPlanChatServiceImpl` 同时承担会话、理解、规划、业务分支、工具调用、响应组装和状态保存，已达到 2211 行。
2. 新增一种查询能力或工具，需要同步修改多个 Planner、Registry、Executor、Client、Composer 和 Validator。
3. 通用 `BUSINESS_QUERY` 与历史细粒度 `ChatIntent` 并存，形成两套路由。
4. 能力 YAML 中的 `plannerProfile` 仍依赖 Java 字符串分支，配置并非真正可扩展。
5. `agent-service` 与 `eladmin-system` 分别维护聊天 DTO，部分字段已经发生强类型与 `Map<String,Object>` 的漂移。
6. 主系统持久化会话与 Agent 内存会话同时参与状态合并，同一 session 并发请求存在覆盖风险。
7. 模型配置和连通性检测直接绑定 DeepSeek，配置项大量散落在 `@Value` 中。
8. 规则文件系统加载与 classpath 加载不一致，打包部署可能遗漏规则。

本方案的核心判断：

- 保留现有“模型只负责受控理解，服务端负责规划、授权和执行”的安全边界。
- 不立即拆分微服务；先在单个 `agent-service` 内建立清晰的应用层、能力层、端口和适配器边界。
- 不进行一次性大重写；保留现有接口和行为，通过 Facade、Handler Registry 和适配器逐步迁移。
- 新增能力最终应主要通过“新增能力处理器 + 可选工具 + 目录登记 + 测试”完成，不再修改中心聊天编排器。

## 2. 与现有计划的关系

本方案与以下计划配合执行，但不重复其功能建设：

- `docs/superpowers/plans/2026-07-09-智能排查意图识别混合架构实施计划.md`
- `docs/superpowers/plans/2026-07-15-智能客服Agent意图识别与上下文语义理解实施计划.md`

职责边界：

| 计划 | 主要解决的问题 |
|---|---|
| 2026-07-09 混合意图识别 | 规则抽槽位、规则/LLM 混合分类 |
| 2026-07-15 会话语义理解 | 多帧语义、上下文句柄、集合追问、任务栈 |
| 本方案 | 将已有能力放入稳定、易扩展、易测试、契约清晰的架构中 |

如果现有功能计划仍有未完成项，应先保持协议兼容，不在本架构方案中重新发明新的语义模型或业务指标。

## 3. 实施目标

### 3.1 易用性目标

- [ ] 新开发者只阅读一份 README 即可完成本地启动、运行测试、配置模型和定位日志。
- [ ] 所有 Agent 配置集中在强类型 `@ConfigurationProperties` 中，启动时给出明确校验错误。
- [ ] 对外接口有唯一、版本化、可自动校验的 OpenAPI/JSON Schema 契约。
- [ ] 错误响应具有稳定 `code/message/requestId/retryable/details` 结构。
- [ ] 单元测试默认不输出大量 INFO 业务日志。
- [ ] 新增规则场景、查询能力、模型提供商均有明确的开发步骤和验收模板。

### 3.2 可扩展性目标

- [ ] `ConversationCoordinator` 不包含客户、订单、菜单、核销、统计等具体业务分支。
- [ ] 新增只读能力不需要修改 Coordinator，也不需要增加新的顶层 `ChatIntent`。
- [ ] 工具元数据、权限、输入输出类型和执行逻辑有唯一注册源。
- [ ] 能力目录使用强类型结构，未知 profile、未知工具、未知指标在启动期失败。
- [ ] 新增诊断场景只需新增场景目录和处理器，不修改规则加载器的文件名列表。
- [ ] 模型调用通过统一 `AgentModelGateway`，业务代码不直接读取 DeepSeek 专属配置。
- [ ] 会话持久化只有一个真相源，多实例和并发请求不会因内存状态覆盖而丢失上下文。

### 3.3 量化验收目标

- [ ] `MealPlanChatServiceImpl` 最终收缩为兼容 Facade，建议不超过 150 行。
- [ ] `ConversationCoordinator` 建议不超过 300 行，且无具体工具名字符串。
- [ ] 新增一个普通只读能力最多新增或修改 3 个主要生产文件：能力处理器、可选工具、目录定义。
- [ ] 核心应用层不再新增 `Map<String,Object>` 形式的内部业务契约。
- [ ] 工具名、响应类型、planner profile 不在多个类中重复硬编码。
- [ ] 文件系统规则与 classpath 规则的 ruleId 集合、数量和 digest 完全一致。
- [ ] 同一 session 并发请求具有确定性的版本冲突或顺序处理结果。
- [ ] 现有 API、现有评测用例和前端展示保持兼容。

## 4. 非目标

- 不在本次架构治理中开放外部客户直接聊天。
- 不引入自由 SQL、任意 URL、任意字段或模型直接选工具。
- 不在本次架构治理中启用历史动作草稿和自动写业务数据能力。
- 不立即将 `agent-service` 拆成多个独立部署服务。
- 不一次性重命名所有历史类、数据库字段和接口路径。
- 不一次性删除旧 `ChatIntent`；仅在兼容路由全部迁移并经过灰度后删除。
- 不以架构重构为理由改变餐数、订单、排餐、核销等业务口径。

## 5. 目标架构

### 5.1 请求处理主链路

```text
HTTP Controller
    |
    v
AgentChatApiMapper
    |  外部 DTO -> 内部 Command
    v
ConversationCoordinator
    |
    +--> ConversationContextPort
    |      读取可信上下文、会话版本和权限范围
    |
    +--> UnderstandingPipeline
    |      确定性槽位 -> 会话理解 -> 校验 -> 澄清决策
    |
    +--> CapabilityRouter
    |      只按受控 Semantic Frame / QueryPlan 匹配能力
    |
    +--> CapabilityHandler
    |      编译计划 -> 执行工具 -> 组装事实 -> 生成展示结果
    |
    +--> ConversationPatch
           返回槽位、任务、上下文句柄和版本更新，不直接决定持久化
```

### 5.2 能力与工具结构

```text
CapabilityDefinition
  - capabilityId
  - supportedFrame
  - requiredPermissions
  - plannerProfile
  - riskLevel
       |
       v
CapabilityHandler
  - supports(frame)
  - compile(frame, context)
  - execute(plan, executionContext)
  - present(result)
       |
       v
AgentTool<I, O>
  - ToolDescriptor
  - inputType/outputType
  - execute(input)
```

### 5.3 建议的包边界

第一阶段不强制拆 Maven 模块，先在当前模块内形成下列包边界：

```text
me.zhengjie.agent
├── api
│   ├── controller
│   ├── contract
│   ├── error
│   └── mapper
├── application
│   └── conversation
├── capability
│   ├── api
│   ├── catalog
│   ├── router
│   └── handler
├── domain
│   ├── conversation
│   ├── query
│   ├── rule
│   └── tool
├── feature
│   ├── mealplan
│   ├── customer
│   ├── order
│   └── operation
└── infrastructure
    ├── http
    ├── llm
    ├── rule
    ├── session
    └── observability
```

迁移期间允许旧包与新包并存，但新增能力应优先进入新边界。

## 6. 核心扩展接口设计

### 6.1 `ConversationCoordinator`

职责：

- 接收内部 `ChatCommand`；
- 加载并校验可信会话上下文；
- 调用理解管线；
- 调用 Capability Router；
- 统一处理澄清、拒绝、部分成功和异常映射；
- 返回 `ChatResult + ConversationPatch`。

禁止承担：

- 不直接判断 `CUSTOMER_ORDER_QUERY` 等具体旧意图；
- 不出现 `listOrders`、`listMealPlans` 等工具名；
- 不拼接具体客户、菜单、运营统计回答；
- 不直接维护 HTTP Header、MDC 或数据库持久化。

### 6.2 `CapabilityHandler`

建议接口：

```java
public interface CapabilityHandler {

    String capabilityId();

    boolean supports(SemanticRequestFrame frame);

    CapabilityPlan compile(SemanticRequestFrame frame,
                           ConversationExecutionContext context);

    CapabilityResult execute(CapabilityPlan plan,
                             ConversationExecutionContext context);

    AgentAnswer present(CapabilityResult result,
                        ConversationExecutionContext context);
}
```

约束：

- `supports` 只能基于受控枚举和能力目录，不读取原始用户文本决定工具。
- `compile` 只能生成目录登记的工具和字段。
- `execute` 必须经过统一权限、预算、超时和审计拦截器。
- `present` 输出结构化事实和展示块，不直接返回未校验的自由 Map。

### 6.3 `AgentTool<I, O>`

建议接口：

```java
public interface AgentTool<I, O> {

    ToolDescriptor descriptor();

    Class<I> inputType();

    Class<O> outputType();

    O execute(I input, ToolExecutionContext context);
}
```

`ToolDescriptor` 至少包含：

- `toolName`
- `domain`
- `action`
- `requiredPermission`
- `maxResults`
- `timeout`
- `dataClassification`
- `readOnly`
- `inputSchemaVersion`
- `outputSchemaVersion`

Spring AI Tool Calling 和确定性 QueryPlan 执行应使用同一 `ToolCatalog` 元数据，但可以使用不同适配器暴露给模型或内部编排器。

### 6.4 `AgentModelGateway`

建议接口：

```java
public interface AgentModelGateway {

    <T> T structuredCall(ModelTask task,
                         ModelPrompt prompt,
                         Class<T> responseType);

    ModelHealth health(ModelProfile profile);
}
```

`ModelTask` 首期包括：

- `DIAGNOSIS`
- `BUSINESS_UNDERSTANDING`
- `CONVERSATION_UNDERSTANDING`
- `CONNECTIVITY_TEST`

业务层只选择任务和模型 profile，不读取 DeepSeek base URL、model name 或 provider 类型。

### 6.5 `ConversationContextPort`

建议模型：

```java
public record ConversationSnapshot(
    String sessionId,
    long version,
    DiagnosisSlots slots,
    PendingBusinessQueryContext pendingContext,
    LastBusinessQueryContext lastContext,
    ConversationTaskStack taskStack,
    List<ConversationContextHandle> handles
) {}
```

返回：

```java
public record ConversationPatch(
    long expectedVersion,
    DiagnosisSlots slots,
    PendingBusinessQueryContext pendingContext,
    LastBusinessQueryContext lastContext,
    ConversationTaskStack taskStack,
    List<ConversationContextHandle> handles
) {}
```

主系统负责持久化和版本冲突处理；Agent 内存实现只能作为可失效缓存。

## 7. 分阶段实施计划

## 阶段 0：建立架构基线与保护网

目标：在重构前固定当前行为、依赖方向和关键指标。

### 任务 0.1：补充模块 README

- [ ] Create: `agent-service/README.md`
- [ ] 记录 Java 17 + Maven 3.9.9 要求。
- [ ] 记录本地启动、环境变量、主系统依赖和端口。
- [ ] 记录规则目录、评测集和真实模型评测的执行方式。
- [ ] 记录“新增能力”“新增工具”“新增规则场景”的标准流程。
- [ ] 明确正常前端调用应经过 `eladmin-system`，不建议直接调用内部 Agent 接口。

### 任务 0.2：建立架构统计基线

- [ ] 记录 `MealPlanChatServiceImpl` 行数、分支数、依赖数和测试数。（已记录行数与测试类数，分支/依赖统计待自动化。）
- [ ] 记录现有工具数、能力数、指标数和响应类型数。（已记录工具/能力数，指标/响应类型统计待自动化。）
- [ ] 记录全量测试耗时和输出日志行数。
- [x] 将基线保存到 `agent-service/docs/architecture-baseline.md`。（2026-07-27）

### 任务 0.3：增加架构守护测试

- [ ] 建议引入 ArchUnit，或使用轻量依赖扫描测试。
- [ ] `domain` 不得依赖 `controller`、Spring Web、HTTP Client。
- [ ] `capability` 不得依赖具体 Controller DTO。
- [ ] `ConversationCoordinator` 不得依赖 `Http*Client` 实现类。
- [ ] 新增 `ChatIntent` 必须通过显式架构评审，防止再次出现意图枚举爆炸。

### 验证命令

```bash
cd agent-service
source ~/.zshrc
jenv shell 17
mvn399
mvn -q test
```

### 阶段验收

- [ ] 不改变任何运行行为。
- [ ] 全量测试保持通过。
- [ ] 开发者能按 README 在空终端完成测试和启动。

## 阶段 1：修正规则与配置加载基础设施

目标：先消除部署方式差异，并为新增诊断场景建立真正扩展点。

### 任务 1.1：统一规则资源扫描

涉及文件：

- Modify: `agent-service/src/main/java/me/zhengjie/agent/rule/FileSystemRuleRegistryLoader.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/rule/RuleRegistryLoaderTest.java`

实施要求：

- [ ] `load(scene)` 必须根据规范化 scene 解析目录，禁止固定使用 `meal-plan`。
- [ ] classpath 使用资源模式扫描 `rules/{scene}/**/*.yaml`。
- [ ] 文件系统和 classpath 使用同一解析、排序、校验和 digest 逻辑。
- [ ] 明确区分规则文件、提示词策略文件和建议模板文件，不再把所有 YAML 交给同一手写解析循环。
- [ ] 外部规则目录存在时使用“完整覆盖”还是“按文件覆盖”，必须选择一种并写入 README。
- [ ] 建议首期采用完整覆盖：外部 scene 目录存在时只加载外部目录，避免规则来源混杂。

### 任务 1.2：改用强类型 YAML 解析

- [ ] 使用 SnakeYAML 或 Jackson 将规则转换为强类型对象。
- [ ] 禁止继续手工按行识别 `ruleId`、列表和标量。
- [ ] 未知字段可根据场景选择启动失败或告警；安全关键规则建议启动失败。
- [ ] 增加 schemaVersion。
- [ ] 校验 ruleId、reasonCode、requiredTools、evidenceFields、owner 和 version。

### 任务 1.3：修复 classpath 覆盖测试

新增测试：

- [ ] classpath 必须包含 `PACKAGE_SPEC_MISSING`。
- [ ] classpath 必须包含 `REFUND_OR_STOP_MEAL_HIT`。
- [ ] classpath 必须包含 `VERIFICATION_CONSUMED_COUNT`。
- [ ] 文件系统与 classpath 的 ruleId 集合一致。
- [ ] 文件系统与 classpath 的 version digest 一致。
- [ ] 从打包 JAR 外部工作目录启动时规则数不变。
- [ ] 新增临时 scene 目录后无需修改 Loader Java 代码即可加载。

### 任务 1.4：集中配置

建议新增：

- Create: `agent-service/src/main/java/me/zhengjie/agent/config/AgentProperties.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/config/AgentPropertiesValidator.java`

迁移范围：

- [ ] `agent.context-base-url`
- [ ] `agent.internal-token`
- [ ] `agent.business-query-timeout-ms`
- [ ] `agent.rules.*`
- [ ] `agent.diagnosis.*`
- [ ] `agent.chat.*`
- [ ] `agent.business-time.*`
- [ ] 模型 profile

要求：

- [ ] 使用嵌套 `@ConfigurationProperties`。
- [ ] mode 使用枚举，不使用任意字符串。
- [ ] 超时、TTL、阈值和条数使用 Bean Validation。
- [ ] 统一代码默认值与 `application.yml` 默认值。
- [ ] 非开发环境缺少 internal token 时启动失败。
- [ ] 启动错误必须指出具体配置路径。

### 阶段验收

- [ ] 不同工作目录、IDE、JAR 三种启动方式加载同一规则集合。
- [ ] 新增 scene 不修改加载器。
- [ ] 配置错误在启动阶段失败，不进入运行时。
- [ ] 现有规则评测全部通过。

## 阶段 2：建立版本化跨服务契约

目标：消除 `agent-service` 与 `eladmin-system` DTO 重复和类型漂移。

### 任务 2.1：建立 OpenAPI-first 契约

建议新增：

- Create: `agent-service/src/main/resources/openapi/agent-service-v2.yaml`
- Create: `agent-service/src/test/java/me/zhengjie/agent/contract/AgentServiceContractTest.java`
- Test/Modify: `eladmin-system` 对应客户端契约测试

选择 OpenAPI 而非直接共享 Spring DTO JAR的原因：

- `eladmin-system` 使用 Java 8、Spring Boot 2 和 `javax.validation`；
- `agent-service` 使用 Java 17、Spring Boot 3 和 `jakarta.validation`；
- 语言无关契约可以避免两个运行栈互相绑定。

### 任务 2.2：区分外部请求和可信执行信封

定义：

```text
ChatMessageRequest
  - sessionId
  - clientMessageId
  - message

AgentExecutionEnvelope
  - messageRequest
  - contextSnapshot
  - availableTools
  - accessContextMetadata
  - contractVersion
```

要求：

- [ ] 前端可提交字段与主系统可信字段分离。
- [ ] `availableTools`、Pending、Last Context、Task Stack 不再表现为普通用户请求字段。
- [ ] `clientMessageId` 由 Agent 原样回传，不再依赖主系统客户端补齐。
- [ ] 请求和响应均携带 `contractVersion`。
- [ ] 新字段遵循兼容式新增，旧字段至少保留一个完整发布周期。

### 任务 2.3：建立统一错误契约

建议：

```json
{
  "code": "CAPABILITY_NOT_AVAILABLE",
  "message": "当前版本暂不支持该查询能力。",
  "requestId": "trace-id",
  "retryable": false,
  "details": {}
}
```

- [ ] 增加全局异常处理器。
- [ ] 参数错误返回稳定字段级错误。
- [ ] 模型超时、工具超时、权限拒绝、契约不匹配使用不同错误码。
- [ ] 禁止返回异常堆栈、内部 URL、token 或敏感业务数据。

### 任务 2.4：契约兼容测试

- [ ] Agent v2 响应能被主系统 DTO 完整解析。
- [ ] 主系统请求能被 Agent v2 DTO 完整解析。
- [ ] `clientMessageId`、任务栈、Pending、Last Context、facts 和 resultBlocks 不丢失。
- [ ] 未识别的兼容新增字段不会导致旧客户端失败。
- [ ] 金额、手机号、完整地址字段不能进入契约 schema。

### 阶段验收

- [ ] OpenAPI 是跨服务字段的唯一权威说明。
- [ ] 两端不再靠手工比对 DTO。
- [ ] 契约测试可在 CI 中独立失败。

## 阶段 3：拆分聊天协调器与能力处理器

目标：让中心流程只负责阶段编排，具体业务由 Handler 扩展。

### 任务 3.1：先建立行为刻画测试

在迁移前固定以下场景：

- [ ] RESET
- [ ] RETRY
- [ ] OUT_OF_SCOPE
- [ ] FOLLOW_UP
- [ ] 排餐诊断槽位补全
- [ ] 客户概览
- [ ] 订单、核销、退餐查询
- [ ] 公共菜单
- [ ] 候选菜
- [ ] 运营统计
- [ ] 多帧查询
- [ ] Pending 恢复
- [ ] 上下文集合追问
- [ ] 权限拒绝
- [ ] 工具部分成功

测试要求：

- [ ] 断言状态、responseType、facts、warnings、QueryPlan 和会话 Patch。
- [ ] 不仅断言助手自然语言。
- [ ] 使用统一 Test Fixture Builder，停止依赖生产类的 6 个构造函数。

### 任务 3.2：新增 `ConversationCoordinator`

建议新增：

- Create: `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ConversationCoordinator.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ChatCommand.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ChatResult.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ConversationExecutionContext.java`

第一批只迁移公共流程：

- [ ] 会话快照装载；
- [ ] 槽位合并；
- [ ] 用户轮次记录；
- [ ] 理解管线调用；
- [ ] 能力路由；
- [ ] 通用结果保存；
- [ ] 审计摘要；
- [ ] 异常转稳定结果。

### 任务 3.3：将历史顶层行为抽为 Handler

建议第一批：

- `ResetConversationHandler`
- `OutOfScopeHandler`
- `MealPlanDiagnosisHandler`
- `DiagnosisFollowUpHandler`
- `BusinessQueryCapabilityHandler`

要求：

- [ ] `MealPlanChatServiceImpl` 暂时保留为 Facade，内部委托 Coordinator。
- [ ] 每迁移一个分支，先对比旧实现和新实现的结构化响应。
- [ ] 灰度开关支持按能力切回旧路径。
- [ ] 不在本阶段删除旧代码。

### 任务 3.4：取消生产构造函数兼容链

- [ ] 使用单一构造器注入。
- [ ] 测试通过 Builder/Fixture 组装依赖。
- [ ] 禁止生产构造器内部 `new BusinessAnswerValidator()`、`new ContextReferenceResolver()` 等隐藏依赖。
- [ ] 时间、阈值和开关全部从显式依赖或配置对象注入。

### 任务 3.5：完成双轨比对

灰度期可使用 shadow：

- [ ] 旧路径生成正式响应。
- [ ] 新 Coordinator 生成影子响应，不返回前端。
- [ ] 比对 status、responseType、QueryPlan、toolNames、facts、warnings 和上下文 Patch。
- [ ] 不记录用户原文和工具原始响应。
- [ ] 差异超过阈值时阻止切流。

### 阶段验收

- [x] 兼容 Facade 不再直接执行具体业务工具，改由 Coordinator 路由。（2026-07-24）
- [x] 新增能力不修改 Facade/Coordinator。（2026-07-24）
- [ ] 旧路径与新路径结构化差异达到约定阈值后才切流。
- [ ] 现有 283 个测试及新增刻画测试全部通过。

## 阶段 4：统一能力目录、Planner 和工具注册

目标：消除工具名与响应类型在多个类中的重复硬编码。

### 任务 4.1：建立强类型 `CapabilityDefinition`

替换 `List<Map<String,Object>>`：

```java
public record CapabilityDefinition(
    String capabilityId,
    SemanticFrameConstraint frame,
    Set<String> requiredPermissions,
    String plannerProfile,
    RiskLevel riskLevel
) {}
```

- [x] YAML 加载后转换为强类型对象。（2026-07-27）
- [x] 未知枚举、空 profile、重复 capabilityId 启动失败。（2026-07-27）
- [x] profile 必须能在 `CapabilityHandlerRegistry` 中找到对应实现。（2026-07-27）
- [x] requiredPermission 必须存在于工具或能力权限目录。（2026-07-27）

### 任务 4.2：建立 `CapabilityHandlerRegistry`

- [ ] Spring 收集所有 `CapabilityHandler` Bean。
- [ ] capabilityId 唯一。
- [ ] plannerProfile 唯一或明确允许多版本。
- [ ] 同一 Semantic Frame 出现多个同优先级 Handler 时启动失败。
- [ ] 未登记能力返回 `CAPABILITY_NOT_AVAILABLE`，不能回退为错误旧意图。

### 任务 4.3：建立统一 `ToolCatalog`

逐步替代：

- `AgentBusinessToolRegistry`
- `AgentBusinessToolExecutor.invoke()` 中的工具名 if 链；
- `FileSystemRuleRegistryLoader.REGISTERED_TOOLS`；
- Planner 中散落的工具字符串；
- Metric Catalog 与工具关系的重复校验。

要求：

- [ ] 工具描述和工具实现一一绑定。
- [ ] 工具权限、最大条数和超时只维护一次。
- [ ] 工具执行统一经过预算、缓存、权限、超时、敏感结果校验和 Trace 拦截器。
- [ ] 诊断工具与业务查询工具可分组，但共享统一元数据协议。
- [x] 新强类型工具集合由 TypedAgentToolCatalog 按主系统白名单裁剪。（2026-07-27）

### 任务 4.4：强类型工具输入输出

迁移优先级：

1. [x] 新增工具必须直接使用强类型 DTO。（2026-07-27：`CustomerOverviewTool`）
2. 现有已经有 Typed Response 的工具先迁移。
3. Legacy Map 适配器仅保留在 infrastructure 层。
4. 最后删除 `fromLegacyMap` 默认兼容方法。

要求：

- [ ] 核心能力层不能读取任意 Map key。
- [ ] Presenter 只消费受控 DTO。
- [ ] 敏感字段校验既检查 DTO schema，也检查序列化结果。

### 任务 4.5：新增扩展性契约测试

增加测试型能力：

- [ ] 测试中注册一个临时 `CapabilityHandler`。
- [ ] 不修改 Coordinator、Router 和现有 Planner 即可完成路由。
- [x] 测试中注册一个临时 `AgentTool`。（2026-07-27）
- [x] 不修改 Executor if/switch 即可执行。（2026-07-27）
- [ ] 重复 ID、未知 profile、未知工具必须启动失败。

### 阶段验收

- [x] 新增能力不再修改中心类。
- [x] 新增工具不再修改统一 Executor 分支。
- [x] 工具名有唯一权威来源。
- [x] 能力 YAML 真正做到“目录登记 + Handler 实现”，不再是假配置。

## 阶段 5：统一会话真相源并支持并发

目标：支持多实例、重试、并发发送和服务重启。

### 任务 5.1：明确主系统为会话唯一真相源

- [x] `eladmin-system` 负责会话、消息和结构化上下文持久化。（2026-07-27）
- [x] `agent-service` 不把本地 session 对象视为最新真相。（2026-07-27）
- [x] 每轮请求携带 `sessionVersion` 或 `turnSequence`。（2026-07-27）
- [x] Agent 返回 `expectedVersion + ConversationPatch`。（2026-07-27）
- [x] 主系统使用乐观锁提交 Patch。（2026-07-27）

### 任务 5.2：收缩 `MealPlanChatSessionStore`

可选实施路径：

**推荐路径：无状态 Agent**

- Agent 每轮完全依赖请求中的可信快照；
- 内存只缓存不可变目录、规则和模型客户端；
- 不缓存可变会话。

**过渡路径：版本化缓存**

- 缓存键包含 `sessionId + version`；
- 只接受版本更高的持久化快照；
- 缓存更新使用原子计算；
- 冲突时返回 `SESSION_VERSION_CONFLICT`。

### 任务 5.3：并发与幂等测试

- [ ] 同一 clientMessageId 重试只生成一个持久化用户轮次。
- [ ] 同一 session 两条不同消息并发时不互相覆盖槽位。
- [x] 旧版本请求不能覆盖新版本上下文。（2026-07-27：数据库 `@Version` 条件更新）
- [ ] Agent 实例切换后能从主系统快照恢复。
- [ ] Agent 重启后 Pending、Last Context 和 Task Stack 不丢失。
- [ ] 并发冲突返回可重试错误，不返回错误业务结论。

### 任务 5.4：会话状态迁移灰度

- [ ] 首先双写或影子比对内存状态与持久化快照。
- [ ] 监控版本冲突、快照解析失败和上下文差异。
- [ ] 稳定后关闭内存会话写入。
- [ ] 最后删除可变内存 session 实现或仅保留测试实现。

### 阶段验收

- [x] Agent 可水平扩容，不依赖粘性会话。
- [x] 相同持久化快照在不同实例得到一致的规划结果。
- [x] 并发请求具有确定性处理策略。

## 阶段 6：通用 API、模型适配与开发体验收口

目标：让系统命名、接口和模型能力与通用智能客服定位一致。

### 任务 6.1：新增通用 v2 API

建议：

- Add: `POST /api/agent/v2/chat`
- Keep: `POST /api/agent/meal-plan/chat`

迁移策略：

- [ ] 旧接口内部委托 v2 Coordinator。
- [ ] 旧接口响应保持字段兼容。
- [ ] 主系统先切换 v2，再观察一个完整发布周期。
- [ ] API 文档标记旧接口 deprecated，但不立即删除。
- [ ] 排餐一次性诊断 `/diagnose` 继续作为独立能力保留。

### 任务 6.2：引入 `AgentModelGateway`

- [ ] Spring AI DeepSeek 实现迁入 infrastructure。
- [x] 理解和连通性检测不直接读取 provider 专属配置。（2026-07-27；诊断客户端迁移待后续收口）
- [x] 支持按 task 选择 model profile。（2026-07-27）
- [x] profile 包含模型名、超时、结构化输出支持、工具调用支持和最大重试。（2026-07-27）
- [ ] provider 不支持工具调用或结构化输出时启动或路由阶段明确拒绝。

### 任务 6.3：健康检查分层

建议区分：

- `liveness`：进程可响应；
- `readiness`：规则和能力目录已加载、必需配置有效；
- `dependency`：主系统/模型配置状态；
- `deep-check`：显式触发真实模型或内部接口连通性，不作为普通探针。

要求：

- [x] readiness 失败应返回合适 HTTP 状态。（2026-07-27）
- [x] 健康结果不暴露 token、完整 base URL 密钥参数或敏感异常。（2026-07-27）
- [x] 不再直接依赖具体 `SpringAiDiagnosisAiClient` 判断模型是否配置。（2026-07-27）

### 任务 6.4：统一日志与指标

- [x] HTTP/MDC 逻辑下沉到 Filter/Interceptor。（2026-07-27）
- [ ] Coordinator 和 Handler 只记录领域事件。
- [x] 测试 profile 将 `me.zhengjie.agent` 日志降为 WARN。（2026-07-27）
- [ ] 增加 capabilityId、toolName、contractVersion、sessionVersion、modelProfile。
- [ ] 记录 P50/P95 耗时、澄清率、能力缺失率、工具失败率、版本冲突率。
- [ ] 不记录原始 Prompt、模型完整输出、工具原始结果和敏感字段。

### 任务 6.5：补充开发手册

README 或 `agent-service/docs/` 至少包含：

- [ ] 本地启动；
- [ ] 环境变量表；
- [ ] 新增能力步骤；
- [ ] 新增工具步骤；
- [ ] 新增规则 scene 步骤；
- [ ] 新增模型 provider/profile 步骤；
- [ ] 常见错误码；
- [ ] 灰度与回滚；
- [ ] 测试和评测命令；
- [ ] 安全边界。

### 阶段验收

- [x] 通用业务能力使用通用 v2 API。
- [x] 模型 provider 细节不进入应用层。
- [x] 本地、测试、部署和排障入口完整。

## 8. 迁移策略

### 8.1 绞杀式迁移原则

每种能力按以下顺序迁移：

1. 为旧路径补结构化行为测试。
2. 新建 Handler，保持使用原有 Client/Planner。
3. Shadow 执行并对比结构化结果。
4. 按配置切换正式流量。
5. 观察指标和人工反馈。
6. 删除对应旧分支。

建议迁移顺序：

1. RESET / OUT_OF_SCOPE；
2. 单客户概览；
3. 订单、核销、退餐列表；
4. 公共菜单；
5. 运营统计；
6. 多帧查询；
7. 排餐诊断；
8. 诊断 FOLLOW_UP。

先迁移简单确定性查询，可以验证 Handler SPI，不应首先移动最复杂的排餐诊断链路。

### 8.2 兼容开关

建议配置：

```yaml
agent:
  architecture:
    coordinator-mode: legacy|shadow|handler
    handler-overrides:
      CUSTOMER_OVERVIEW_V1: handler
      MEAL_PLAN_DIAGNOSIS_V1: legacy
```

要求：

- 开关必须强类型校验；
- 每个能力可独立切回 legacy；
- shadow 不重复执行有成本或有副作用的外部操作；
- 当前系统只读，但仍要避免 shadow 重复调用真实模型和产生额外费用；
- 工具结果可复用同一轮安全缓存进行新旧 Presenter 比对。

## 9. 测试策略

### 9.1 单元测试

- Capability Handler 计划编译；
- Capability Router 匹配和冲突；
- ToolCatalog 注册和权限裁剪；
- YAML 强类型加载；
- 配置校验；
- Conversation Patch 合并；
- Error Mapper；
- Model Gateway profile 选择。

### 9.2 契约测试

- OpenAPI 请求/响应；
- 主系统与 Agent 双向兼容；
- 错误码；
- Schema 中的敏感字段黑名单；
- contractVersion 兼容。

### 9.3 组件测试

- Controller -> Coordinator；
- Coordinator -> Handler；
- Handler -> Tool Gateway；
- HTTP Adapter -> 主系统 Mock Server；
- Model Gateway -> Mock ChatModel。

### 9.4 打包测试

- 从项目目录启动；
- 从 JAR 外部空目录启动；
- 规则集合和 digest 一致；
- classpath 能读取全部能力、规则、提示词策略和建议模板。

### 9.5 并发测试

- 同 session 并发 turn；
- clientMessageId 幂等；
- sessionVersion 冲突；
- 多实例快照恢复。

### 9.6 评测回归

- 保持现有排餐诊断评测集；
- 保持现有业务查询评测集；
- 保持意图和会话理解评测集；
- 新旧路径对比 QueryPlan、facts、warnings 和 context handles；
- 真实模型评测继续使用独立 Maven profile，不进入普通单测。

### 9.7 推荐验证命令

Agent 全量测试：

```bash
cd agent-service
source ~/.zshrc
jenv shell 17
mvn399
mvn -q test
```

Agent 打包：

```bash
cd agent-service
source ~/.zshrc
jenv shell 17
mvn399
mvn -q clean package
```

主系统 Agent 模块测试：

```bash
cd eladmin/eladmin-system
source ~/.zshrc
jenv shell 1.8
mvn352
mvn -q -DskipTests=false -Dtest='*Agent*Test' test
```

真实模型评测只在显式提供 API Key 时执行：

```bash
cd agent-service
source ~/.zshrc
jenv shell 17
mvn399
mvn -q -Preal-model-eval -Dtest=RealModelIntentEvaluationTest test
```

## 10. 文档同步要求

每阶段涉及业务行为或接口时必须同步：

- `eladmin/doc/business/智能排查助手业务说明.md`
- `eladmin/doc/apidoc/智能排查助手接口文档.md`
- `eladmin/doc/apidoc/智能客服Agent内部业务查询接口文档.md`
- `eladmin/doc/apidoc/智能客服Agent运营统计内部接口文档.md`
- `agent-service/README.md`
- OpenAPI 契约

如果阶段只进行内部重构且行为、字段、接口均未变化，业务文档中记录“架构调整，业务口径无变化”，避免后续误判。

## 11. 风险与控制

| 风险 | 影响 | 控制措施 |
|---|---|---|
| 重构导致意图路由变化 | 回答错误或误澄清 | 行为刻画测试、shadow 对比、能力级切流 |
| 新旧会话状态不一致 | 上下文丢失 | sessionVersion、Patch、影子差异监控 |
| 工具注册合并影响权限 | 越权查询 | 保留主系统二次鉴权，增加权限矩阵测试 |
| DTO 契约切换导致字段丢失 | 历史卡片或任务恢复异常 | OpenAPI 契约测试、兼容字段保留 |
| 规则扫描改变加载顺序 | digest 和诊断结果变化 | 文件名稳定排序、规则集合基线 |
| Model Gateway 改造影响输出 | fallback 增加 | task 级开关、旧 DeepSeek Adapter 保留 |
| Shadow 重复调用模型 | 成本增加 | 复用结果或仅比对确定性阶段 |
| 包结构迁移过大 | 合并冲突、交付延期 | 先新增边界，后移动文件，不做一次性搬迁 |

## 12. 回滚方案

每阶段必须满足独立回滚：

- 规则加载：保留旧 Loader Bean，通过配置选择；新 Loader 异常立即回退。
- 契约 v2：旧接口和旧 DTO 保留至少一个发布周期。
- Coordinator：按 capabilityId 切回 legacy。
- ToolCatalog：旧 Registry/Executor 保留到全部工具迁移完成。
- 会话无状态化：切回版本化缓存，但主系统持久化不能回滚删除。
- Model Gateway：保留现有 DeepSeek Adapter 作为默认实现。

数据库字段如新增 `session_version` 或 contractVersion，必须使用兼容式新增，回滚应用版本后不影响旧版本读取。

## 13. 交付物

最终应交付：

- [ ] `agent-service/README.md`
- [ ] 架构基线文档
- [ ] 规则和能力目录统一加载器
- [ ] 强类型 Agent 配置
- [ ] Agent v2 OpenAPI 契约
- [ ] 统一错误协议
- [ ] `ConversationCoordinator`
- [ ] `CapabilityHandler` SPI 与 Registry
- [ ] `AgentTool<I,O>` SPI 与 ToolCatalog
- [ ] `AgentModelGateway`
- [ ] `ConversationSnapshot/Patch` 与会话版本控制
- [ ] `/api/agent/v2/chat`
- [ ] 架构测试、契约测试、打包测试和并发测试
- [ ] 更新后的业务和 API 文档
- [ ] 灰度记录、差异报告和回滚验证记录

## 14. 完成定义

满足以下条件后，本轮架构优化才算完成：

1. 新增一个测试能力时，不修改 Coordinator、中心 Planner 或统一 Executor。
2. 新增一个规则 scene 时，不修改规则加载器 Java 代码。
3. Agent 从项目目录和打包 JAR 外部目录启动时加载相同规则。
4. 主系统与 Agent 的聊天契约由 OpenAPI 自动校验。
5. `MealPlanChatServiceImpl` 只保留兼容入口，不再承载业务分支。
6. 主系统是会话唯一真相源，同 session 并发有明确版本策略。
7. 业务层不依赖 DeepSeek 专属配置。
8. 现有 API 保持兼容，全部自动化测试和评测通过。
9. 业务口径、安全边界、权限校验和敏感字段限制没有弱化。
10. README 能指导新开发者完成启动、扩展、测试和排障。

## 15. 建议的实际执行顺序

按风险和收益排序：

1. **立即执行阶段 1**：修正规则打包加载差异和配置默认值不一致。
2. **随后执行阶段 2**：建立契约，防止重构期间 DTO 继续漂移。
3. **再执行阶段 3**：抽取 Coordinator 和首批简单 Handler。
4. **稳定后执行阶段 4**：统一能力、Planner 和工具注册。
5. **接着执行阶段 5**：会话版本化与无状态化。
6. **最后执行阶段 6**：通用 v2 API、Model Gateway 和开发体验收口。

阶段 1 和阶段 2 完成前，不建议继续大规模增加新的细粒度意图、工具字符串分支或 `Map<String,Object>` 业务响应。
