# agent-service 易用性与可扩展性架构优化实施方案

> 编制日期：2026-07-23  
> 适用项目：`/Users/qqx/job/code/eladmin-mp`  
> 目标模块：`agent-service`，以及与其直接通信的 `eladmin-system` Agent 模块  
> 核心方向：开发易用性、能力可扩展性、跨服务契约、会话一致性、模型与工具适配  
> 实施方式：保持已上线前端接口兼容；未上线的服务间旧接口和重复内部接口直接收口，每阶段独立测试并可通过代码版本回滚
> 当前状态：本地可完成的核心架构收口已完成并通过自动化回归。剩余工作为真实数据库并发压测、灰度指标基线和完整发布周期验证。

## 实施进度

| 阶段 | 状态 | 最近更新 | 已完成范围 / 待办 |
|---|---|---|---|
| 阶段 0：架构基线与保护网 | 已完成 | 2026-07-29 | README、架构基线、行为回归和 Facade/Coordinator 架构守护测试完成。 |
| 阶段 1：规则与配置基础设施 | 已完成 | 2026-07-29 | 递归规则扫描、强类型 YAML、classpath 一致性测试完成；生产代码散落 `@Value` 已迁移到 `AgentProperties`。 |
| 阶段 2：版本化跨服务契约 | 已完成 | 2026-07-29 | v2 OpenAPI、可信执行信封、必需访问上下文、稳定错误协议和 Java/OpenAPI 字段漂移测试完成。 |
| 阶段 3：聊天协调器与能力处理器 | 已完成 | 2026-07-29 | `MealPlanChatServiceImpl` 已收缩为 Facade；理解、Pending/时间解析、业务结果焦点保存和审计摘要均已下沉为独立 Pipeline，生产类仅保留一个注入构造器。代码尚未上线，不建设旧 Coordinator/新 Coordinator 双轨。 |
| 阶段 4：统一能力目录、Planner 和工具注册 | 已完成 | 2026-07-29 | 中心 Planner 改为 Registry 路由；工具名和业务 responseType 分别由 `ToolCatalog`、`BusinessResponseTypeCatalog` 统一登记；Presenter 与结果校验只消费 `BusinessPresentationResult`，旧客户汇总 Map 适配器、重复接口和 DTO `fromLegacyMap` 默认适配均已从生产源码删除。 |
| 阶段 5：统一会话真相源并支持并发 | 核心完成 | 2026-07-29 | 主系统持久化快照、`clientMessageId` 幂等、数据库行锁串行化和 `@Version` 冲突策略完成；生产包中的可变内存会话实现和启用开关已删除，真实数据库并发压测待发布前执行。 |
| 阶段 6：通用 API、模型适配与开发体验收口 | 核心完成 | 2026-07-29 | v2 默认灰度、严格 model profile/能力检查、动态 capabilityId/toolName/modelProfile MDC、原始模型日志清理、可执行 JAR 和开发手册完成；线上指标基线待灰度期采集。 |

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
- 不进行一次性大重写；保留已上线前端接口和业务行为，通过 Facade、Handler Registry 和适配器逐步迁移，未上线重复接口不保留兼容层。
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

- [x] 新开发者只阅读一份 README 即可完成本地启动、运行测试、配置模型和定位日志。（2026-07-29）
- [x] 所有 Agent 配置集中在强类型 `@ConfigurationProperties` 中，启动时给出明确校验错误。（2026-07-29；聊天 mode 已改为枚举，生产/预发缺少 internal token 启动失败。）
- [x] 对外接口有唯一、版本化、可自动校验的 OpenAPI/JSON Schema 契约。（2026-07-29；v2 OpenAPI 与契约漂移测试。）
- [x] 错误响应具有稳定 `code/message/requestId/retryable/details` 结构。（2026-07-29）
- [x] 单元测试默认不输出大量 INFO 业务日志。（2026-07-29；agent-service 与主系统 Agent 测试均降至 WARN。）
- [x] 新增规则场景、查询能力、模型提供商均有明确的开发步骤和验收模板。（2026-07-29；见 `agent-service/README.md` 扩展约定和验证清单。）

### 3.2 可扩展性目标

- [x] `ConversationCoordinator` 不包含客户、订单、菜单、核销、统计等具体业务分支。（2026-07-29；架构守护测试固定。）
- [x] 新增只读能力不需要修改 Coordinator，也不需要增加新的顶层 `ChatIntent`。（2026-07-29；临时 Handler 扩展测试固定。）
- [x] 工具元数据、权限、输入输出类型和执行逻辑有唯一注册源。（2026-07-29；新工具由 `AgentTool` Bean 收集，历史工具由 `ToolCatalog` 描述符/执行器表统一登记。）
- [x] 能力目录使用强类型结构，未知 profile、未知工具、未知指标在启动期失败。（2026-07-29）
- [x] 新增诊断场景只需新增场景目录和处理器，不修改规则加载器的文件名列表。（2026-07-29）
- [x] 模型调用通过统一 `AgentModelGateway`，业务代码不直接读取 DeepSeek 专属配置。（2026-07-29）
- [x] 会话持久化只有一个真相源，多实例和并发请求不会因内存状态覆盖而丢失上下文。（2026-07-29；主系统快照为真相源、Agent 默认无状态；真实库压测仍列为发布期事项。）

### 3.3 量化验收目标

- [x] `MealPlanChatServiceImpl` 最终收缩为兼容 Facade，建议不超过 150 行。（2026-07-29；当前 42 行。）
- [x] `ConversationCoordinator` 建议不超过 300 行，且无具体工具名字符串。（2026-07-29；当前 31 行。）
- [x] 新增一个普通只读能力最多新增或修改 3 个主要生产文件：能力处理器、可选工具、目录定义。（2026-07-29；临时能力扩展测试不修改中心类。）
- [x] 核心应用层不再新增 `Map<String,Object>` 形式的内部业务契约。（2026-07-29；Presenter、结果校验和活跃客户余额 DTO 已强类型化，Map 仅留在兼容序列化/基础设施适配边界。）
- [x] 工具名、响应类型、planner profile 不在多个类中重复硬编码。（2026-07-29；分别由 `ToolCatalog`、`BusinessResponseTypeCatalog` 和能力目录统一维护。）
- [x] 文件系统规则与 classpath 规则的 ruleId 集合、数量和 digest 完全一致。（2026-07-29）
- [x] 同一 session 并发请求具有确定性的版本冲突或顺序处理结果。（2026-07-29；行锁串行化与版本冲突协议完成，真实库压力数据待发布前采集。）
- [x] 现有前端 API、评测用例和展示保持兼容。（2026-07-29；代码尚未上线，未发布的 Agent 服务间 v1 路径按确认直接删除，前端仍调用主系统原接口。）

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

- [x] Create: `agent-service/README.md`（2026-07-29）
- [x] 记录 Java 17 + Maven 3.9.9 要求。（2026-07-29）
- [x] 记录本地启动、环境变量、主系统依赖和端口。（2026-07-29）
- [x] 记录规则目录、评测集和真实模型评测的执行方式。（2026-07-29）
- [x] 记录“新增能力”“新增工具”“新增规则场景”的标准流程。（2026-07-29）
- [x] 明确正常前端调用应经过 `eladmin-system`，不建议直接调用内部 Agent 接口。（2026-07-29）

### 任务 0.2：建立架构统计基线

- [x] 记录 `MealPlanChatServiceImpl` 行数、分支数、依赖数和测试数。（2026-07-29；见架构基线收口快照。）
- [x] 记录现有工具数、能力数、指标数和响应类型数。（2026-07-29；见架构基线收口快照。）
- [x] 记录全量测试耗时和输出日志行数。（2026-07-29；本机约 6 秒，quiet 模式无普通 INFO 输出。）
- [x] 将基线保存到 `agent-service/docs/architecture-baseline.md`。（2026-07-27）

### 任务 0.3：增加架构守护测试

- [x] 建议引入 ArchUnit，或使用轻量依赖扫描测试。（2026-07-29；采用零新增依赖的源码边界测试。）
- [x] `domain` 不得依赖 `controller`、Spring Web、HTTP Client。（2026-07-29）
- [x] `capability` 不得依赖具体 Controller DTO。（2026-07-29）
- [x] `ConversationCoordinator` 不得依赖 `Http*Client` 实现类。（2026-07-29）
- [x] 新增 `ChatIntent` 必须通过显式架构评审，防止再次出现意图枚举爆炸。（2026-07-29；README 明确新能力不得新增顶层意图，扩展测试固定 Handler 路径。）

### 验证命令

```bash
cd agent-service
source ~/.zshrc
jenv shell 17
mvn399
mvn -q test
```

### 阶段验收

- [x] 不改变任何运行行为。（2026-07-29）
- [x] 全量测试保持通过。（2026-07-29）
- [x] 开发者能按 README 在空终端完成测试和启动。（2026-07-29；可执行 JAR 已从项目外目录验证。）

## 阶段 1：修正规则与配置加载基础设施

目标：先消除部署方式差异，并为新增诊断场景建立真正扩展点。

### 任务 1.1：统一规则资源扫描

涉及文件：

- Modify: `agent-service/src/main/java/me/zhengjie/agent/rule/FileSystemRuleRegistryLoader.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/rule/RuleRegistryLoaderTest.java`

实施要求：

- [x] `load(scene)` 必须根据规范化 scene 解析目录，禁止固定使用 `meal-plan`。（2026-07-29）
- [x] classpath 使用资源模式扫描 `rules/{scene}/**/*.yaml`。（2026-07-29）
- [x] 文件系统和 classpath 使用同一解析、排序、校验和 digest 逻辑。（2026-07-29）
- [x] 明确区分规则文件、提示词策略文件和建议模板文件，不再把所有 YAML 交给同一手写解析循环。（2026-07-29）
- [x] 外部规则目录存在时使用“完整覆盖”还是“按文件覆盖”，必须选择一种并写入 README。（2026-07-29）
- [x] 建议首期采用完整覆盖：外部 scene 目录存在时只加载外部目录，避免规则来源混杂。（2026-07-29）

### 任务 1.2：改用强类型 YAML 解析

- [x] 使用 SnakeYAML 或 Jackson 将规则转换为强类型对象。（2026-07-29；使用 Jackson `YAMLMapper`。）
- [x] 禁止继续手工按行识别 `ruleId`、列表和标量。（2026-07-29）
- [x] 未知字段可根据场景选择启动失败或告警；安全关键规则建议启动失败。（2026-07-29；诊断规则未知字段失败。）
- [x] 增加 schemaVersion。（2026-07-29）
- [x] 校验 ruleId、reasonCode、requiredTools、evidenceFields、owner 和 version。（2026-07-29）

### 任务 1.3：修复 classpath 覆盖测试

新增测试：

- [x] classpath 必须包含 `PACKAGE_SPEC_MISSING`。（2026-07-29）
- [x] classpath 必须包含 `REFUND_OR_STOP_MEAL_HIT`。（2026-07-29）
- [x] classpath 必须包含 `VERIFICATION_CONSUMED_COUNT`。（2026-07-29）
- [x] 文件系统与 classpath 的 ruleId 集合一致。（2026-07-29）
- [x] 文件系统与 classpath 的 version digest 一致。（2026-07-29）
- [x] 从打包 JAR 外部工作目录启动时规则数不变。（2026-07-29）
- [x] 新增临时 scene 目录后无需修改 Loader Java 代码即可加载。（2026-07-29）

### 任务 1.4：集中配置

建议新增：

- Create: `agent-service/src/main/java/me/zhengjie/agent/config/AgentProperties.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/config/AgentPropertiesValidator.java`

迁移范围：

- [x] `agent.context-base-url`（2026-07-29）
- [x] `agent.internal-token`（2026-07-29）
- [x] `agent.business-query-timeout-ms`（2026-07-29）
- [x] `agent.rules.*`（2026-07-29）
- [x] `agent.diagnosis.*`（2026-07-29）
- [x] `agent.chat.*`（2026-07-29）
- [x] `agent.business-time.*`（2026-07-29；由独立强类型 `BusinessTimeProperties` 承载。）
- [x] 模型 profile（2026-07-29）

要求：

- [x] 使用嵌套 `@ConfigurationProperties`。（2026-07-29）
- [x] mode 使用枚举，不使用任意字符串。（2026-07-29）
- [x] 超时、TTL、阈值和条数使用 Bean Validation。（2026-07-29）
- [x] 统一代码默认值与 `application.yml` 默认值。（2026-07-29）
- [x] 非开发环境缺少 internal token 时启动失败。（2026-07-29）
- [x] 启动错误必须指出具体配置路径。（2026-07-29；错误包含 `agent.internal-token`。）

### 阶段验收

- [x] 不同工作目录、IDE、JAR 三种启动方式加载同一规则集合。（2026-07-29）
- [x] 新增 scene 不修改加载器。（2026-07-29）
- [x] 配置错误在启动阶段失败，不进入运行时。（2026-07-29）
- [x] 现有规则评测全部通过。（2026-07-29）

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

- [x] 前端可提交字段与主系统可信字段分离。（2026-07-29）
- [x] `availableTools`、Pending、Last Context、Task Stack 不再表现为普通用户请求字段。（2026-07-29）
- [x] `clientMessageId` 由 Agent 原样回传，不再依赖主系统客户端补齐。（2026-07-29）
- [x] 请求和响应均携带 `contractVersion`。（2026-07-29）
- [x] 新字段遵循兼容式新增；已上线旧字段按发布周期管理。（2026-07-29；当前改造尚未上线，不存在生产兼容对象，未发布 v1 已直接删除。）

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

- [x] 增加全局异常处理器。（2026-07-29）
- [x] 参数错误返回稳定字段级错误。（2026-07-29）
- [x] 模型超时、工具超时、权限拒绝、契约不匹配使用不同错误码。（2026-07-29）
- [x] 禁止返回异常堆栈、内部 URL、token 或敏感业务数据。（2026-07-29）

### 任务 2.4：契约兼容测试

- [x] Agent v2 响应能被主系统 DTO 完整解析。（2026-07-29）
- [x] 主系统请求能被 Agent v2 DTO 完整解析。（2026-07-29）
- [x] `clientMessageId`、任务栈、Pending、Last Context、facts 和 resultBlocks 不丢失。（2026-07-29）
- [x] 未识别的兼容新增字段不会导致旧客户端失败。（2026-07-29）
- [x] 金额、手机号、完整地址字段不能进入契约 schema。（2026-07-29）

### 阶段验收

- [x] OpenAPI 是跨服务字段的唯一权威说明。（2026-07-29）
- [x] 两端不再靠手工比对 DTO。（2026-07-29；契约测试自动比对 Java 字段与 OpenAPI。）
- [x] 契约测试可在 CI 中独立失败。（2026-07-29）

## 阶段 3：拆分聊天协调器与能力处理器

目标：让中心流程只负责阶段编排，具体业务由 Handler 扩展。

### 任务 3.1：先建立行为刻画测试

在迁移前固定以下场景：

- [x] RESET（2026-07-29）
- [x] RETRY（2026-07-29）
- [x] OUT_OF_SCOPE（2026-07-29）
- [x] FOLLOW_UP（2026-07-29）
- [x] 排餐诊断槽位补全（2026-07-29）
- [x] 客户概览（2026-07-29）
- [x] 订单、核销、退餐查询（2026-07-29）
- [x] 公共菜单（2026-07-29）
- [x] 候选菜（2026-07-29）
- [x] 运营统计（2026-07-29）
- [x] 多帧查询（2026-07-29）
- [x] Pending 恢复（2026-07-29）
- [x] 上下文集合追问（2026-07-29）
- [x] 权限拒绝（2026-07-29）
- [x] 工具部分成功（2026-07-29）

测试要求：

- [x] 断言状态、responseType、facts、warnings、QueryPlan 和会话 Patch。（2026-07-29）
- [x] 不仅断言助手自然语言。（2026-07-29）
- [x] 使用统一 Test Fixture Builder，停止依赖生产类的 6 个构造函数。（2026-07-29）

### 任务 3.2：新增 `ConversationCoordinator`

建议新增：

- Create: `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ConversationCoordinator.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ChatCommand.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ChatResult.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ConversationExecutionContext.java`

第一批只迁移公共流程：

- [x] 会话快照装载；（2026-07-29；由 `ConversationStateSupport` 承载。）
- [x] 槽位合并；（2026-07-29；由 `ConversationStateSupport` 承载。）
- [x] 用户轮次记录；（2026-07-29；由 `ConversationStateSupport` 承载。）
- [x] 理解管线调用；（2026-07-29；`BusinessConversationUnderstandingPipeline` 统一理解、Pending 合并、时间解析和语义追踪。）
- [x] 能力路由；（2026-07-29；Coordinator 与 `CapabilityHandlerRegistry` 分层路由。）
- [x] 通用结果保存；（2026-07-29；`BusinessConversationResultPipeline` 统一保存稳定对象焦点和最近查询上下文。）
- [x] 审计摘要；（2026-07-29；结果管线统一生成查询计划指纹、限长回答摘要和脱敏结果形状。）
- [x] 异常转稳定结果。（2026-07-29；统一异常协议和稳定错误码。）

### 任务 3.3：将历史顶层行为抽为 Handler

建议第一批：

- `ResetConversationHandler`
- `OutOfScopeHandler`
- `MealPlanDiagnosisHandler`
- `DiagnosisFollowUpHandler`
- `BusinessQueryCapabilityHandler`

要求：

- [x] `MealPlanChatServiceImpl` 暂时保留为 Facade，内部委托 Coordinator。（2026-07-29）
- [x] 每迁移一个分支，先对比旧实现和新实现的结构化响应。（2026-07-29；本轮拆分由既有 40 个 Handler 刻画测试和新增组件测试保护。）
- [x] 不建设按能力切回旧 Handler 的生产开关。（2026-07-29；代码尚未上线且旧、新实现未并存，回滚使用已验证代码版本；多帧理解仍保留独立 `shadow/new` 开关。）
- [x] 不在本阶段删除旧代码。（2026-07-29；旧业务行为整体保留在默认 Handler，仅移动职责和删除重复构造链。）

### 任务 3.4：取消生产构造函数兼容链

- [x] 使用单一构造器注入。（2026-07-29；生产类仅保留一个构造器，历史简化构造链已删除。）
- [x] 测试通过 Builder/Fixture 组装依赖。（2026-07-29；统一使用 `DefaultConversationHandlerFixture`。）
- [x] 禁止生产构造器内部 `new BusinessAnswerValidator()`、`new ContextReferenceResolver()` 等隐藏依赖。（2026-07-29；`BusinessQueryChatService` 改为 Spring 依赖注入。）
- [x] 时间、阈值和开关全部从显式依赖或配置对象注入。（2026-07-29；生产构造链已统一从强类型配置注入。）

### 任务 3.5：双轨方案决策

本代码尚未上线正式环境，旧 Coordinator 与新 Coordinator 没有生产流量可供双轨对比，因此不再引入一套仅用于迁移的生产影子执行路径：

- [x] 取消旧路径正式响应与新 Coordinator 影子响应的双执行设计。（2026-07-29）
- [x] 以结构化刻画测试覆盖 status、responseType、QueryPlan、toolNames、facts、warnings 和上下文 Patch。（2026-07-29）
- [x] 不记录用户原文和工具原始响应。（2026-07-29）
- [x] 发布门禁改为全量回归、灰度指标基线和代码版本回滚，不依赖不存在的旧路径差异阈值。（2026-07-29）

### 阶段验收

- [x] 兼容 Facade 不再直接执行具体业务工具，改由 Coordinator 路由。（2026-07-24）
- [x] 新增能力不修改 Facade/Coordinator。（2026-07-24）
- [x] 未上线代码直接使用新路径，发布前由结构化回归与打包启动验收把关。（2026-07-29）
- [x] 现有 283 个测试及新增刻画测试全部通过。（2026-07-29；当前 Agent 全量 329 个测试。）

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

- [x] Spring 收集所有 `CapabilityHandler` Bean。（2026-07-29）
- [x] capabilityId 唯一。（2026-07-29）
- [x] plannerProfile 唯一或明确允许多版本。（2026-07-29）
- [x] 同一 Semantic Frame 出现多个同优先级 Handler 时启动失败。（2026-07-29）
- [x] 未登记能力返回 `CAPABILITY_NOT_AVAILABLE`，不能回退为错误旧意图。（2026-07-29）

### 任务 4.3：建立统一 `ToolCatalog`

逐步替代：

- `AgentBusinessToolRegistry`
- `AgentBusinessToolExecutor.invoke()` 中的工具名 if 链；
- `FileSystemRuleRegistryLoader.REGISTERED_TOOLS`；
- Planner 中散落的工具字符串；
- Metric Catalog 与工具关系的重复校验。

要求：

- [x] 工具描述和工具实现一一绑定。（2026-07-29；Typed 工具由 Bean 绑定，历史工具描述符/执行器表集合强制一致。）
- [x] 工具权限、最大条数和超时只维护一次。（2026-07-29）
- [x] 工具执行统一经过预算、缓存、权限、超时、敏感结果校验和 Trace 拦截器。（2026-07-29）
- [x] 诊断工具与业务查询工具可分组，但共享统一元数据协议。（2026-07-29）
- [x] 新强类型工具集合由 TypedAgentToolCatalog 按主系统白名单裁剪。（2026-07-27）

### 任务 4.4：强类型工具输入输出

迁移优先级：

1. [x] 新增工具必须直接使用强类型 DTO。（2026-07-27：`CustomerOverviewTool`）
2. [x] 生产 HTTP 客户端对已有 Typed Response 的工具直接反序列化 DTO。（2026-07-29）
3. [x] 删除旧客户餐数、核销和订单汇总 Map 适配器及重复内部接口。（2026-07-29）
4. [x] 删除其余 DTO 的 `fromLegacyMap` 默认兼容方法。（2026-07-29；`BusinessQueryDataClient` 以 Typed Response 为主契约，历史 Map 测试数据只在 `src/test` 夹具中转换。）

要求：

- [x] 核心能力层不能读取任意 Map key。（2026-07-29；兼容 Map 读取隔离在旧适配/展示边界。）
- [x] Presenter 只消费受控 DTO。（2026-07-29；`BusinessAnswerComposer`、`BusinessQueryResponseFactory` 和 `BusinessResultValidator` 统一消费 `BusinessPresentationResult`，架构测试防止回退。）
- [x] 敏感字段校验既检查 DTO schema，也检查序列化结果。（2026-07-29）

### 任务 4.5：新增扩展性契约测试

增加测试型能力：

- [x] 测试中注册一个临时 `CapabilityHandler`。（2026-07-29）
- [x] 不修改 Coordinator、Router 和现有 Planner 即可完成路由。（2026-07-29）
- [x] 测试中注册一个临时 `AgentTool`。（2026-07-27）
- [x] 不修改 Executor if/switch 即可执行。（2026-07-27）
- [x] 重复 ID、未知 profile、未知工具必须启动失败。（2026-07-29）

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

- [x] 同一 clientMessageId 重试只生成一个持久化用户轮次。（2026-07-29）
- [ ] 同一 session 两条不同消息并发时不互相覆盖槽位。（行锁策略和调用验证已完成；真实 MySQL 集成测试入口 `AgentChatSessionConcurrencyIntegrationTest` 已补齐，待在可用数据库环境显式执行并保存结果。）
- [x] 旧版本请求不能覆盖新版本上下文。（2026-07-27：数据库 `@Version` 条件更新）
- [x] Agent 实例切换后能从主系统快照恢复。（2026-07-29）
- [x] Agent 重启后 Pending、Last Context 和 Task Stack 不丢失。（2026-07-29）
- [x] 并发冲突返回可重试错误，不返回错误业务结论。（2026-07-29）

### 任务 5.4：会话状态迁移灰度

- [x] 首先双写或影子比对内存状态与持久化快照。（2026-07-29；采用推荐的无状态路径，直接以请求快照装载并用回归测试对比历史行为，无需生产双写。）
- [ ] 监控版本冲突、快照解析失败和上下文差异。（待灰度指标采集。）
- [x] 关闭并删除生产内存会话写入及 `stateful-session-cache-enabled` 开关。（2026-07-29）
- [x] 可变内存 session 仅保留为 `src/test` 多轮会话测试夹具。（2026-07-29）

### 阶段验收

- [x] Agent 可水平扩容，不依赖粘性会话。
- [x] 相同持久化快照在不同实例得到一致的规划结果。
- [x] 并发请求具有确定性处理策略。

## 阶段 6：通用 API、模型适配与开发体验收口

目标：让系统命名、接口和模型能力与通用智能客服定位一致。

### 任务 6.1：新增通用 v2 API

建议：

- [x] Add: `POST /api/agent/v2/chat`（2026-07-29）
- [x] Delete: `POST /api/agent/meal-plan/chat` Agent 服务间旧路径（2026-07-29；尚未上线，无需保留 v1）

迁移策略：

- [x] 主系统固定使用 v2 可信执行信封。（2026-07-29；删除双路径和契约版本开关。）
- [x] 未发布旧接口直接删除。（2026-07-29；不再维护 v1 Controller、配置和测试。）
- [x] API 文档明确区分主系统前端接口与 Agent 服务间接口。（2026-07-29）
- [x] 排餐一次性诊断 `/diagnose` 继续作为独立能力保留。（2026-07-29）

### 任务 6.2：引入 `AgentModelGateway`

- [x] Spring AI DeepSeek 实现迁入 infrastructure。（2026-07-29）
- [x] 理解和连通性检测不直接读取 provider 专属配置。（2026-07-27；诊断客户端迁移待后续收口）
- [x] 支持按 task 选择 model profile。（2026-07-27）
- [x] profile 包含模型名、超时、结构化输出支持、工具调用支持和最大重试。（2026-07-27）
- [x] provider 不支持工具调用或结构化输出时启动或路由阶段明确拒绝。（2026-07-29）

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
- [x] Coordinator 和 Handler 只记录领域事件。（2026-07-29）
- [x] 测试 profile 将 `me.zhengjie.agent` 日志降为 WARN。（2026-07-27）
- [x] 增加 capabilityId、toolName、contractVersion、sessionVersion、modelProfile。（2026-07-29；Filter/Controller 提供契约与版本字段，Coordinator、工具目录和模型适配器在调用作用域动态补齐并恢复 MDC。）
- [ ] 记录 P50/P95 耗时、澄清率、能力缺失率、工具失败率、版本冲突率。（审计统计接口已直接返回 P50/P95 和澄清率，失败类型/工具分布可用于灰度聚合；待灰度环境建立实际基线。）
- [x] 不记录原始 Prompt、模型完整输出、工具原始结果和敏感字段。（2026-07-29；异常消息、sessionId、客户标识也从日志和诊断 trace 中移除。）

### 任务 6.5：补充开发手册

README 或 `agent-service/docs/` 至少包含：

- [x] 本地启动；（2026-07-29）
- [x] 环境变量表；（2026-07-29）
- [x] 新增能力步骤；（2026-07-29）
- [x] 新增工具步骤；（2026-07-29）
- [x] 新增规则 scene 步骤；（2026-07-29）
- [x] 新增模型 provider/profile 步骤；（2026-07-29）
- [x] 常见错误码；（2026-07-29）
- [x] 灰度与回滚；（2026-07-29）
- [x] 测试和评测命令；（2026-07-29）
- [x] 安全边界。（2026-07-29）

### 阶段验收

- [x] 通用业务能力使用通用 v2 API。
- [x] 模型 provider 细节不进入应用层。
- [x] 本地、测试、部署和排障入口完整。

## 8. 迁移策略

### 8.1 增量迁移原则

每种能力按以下顺序迁移：

1. 为既有行为补结构化刻画测试。
2. 新建 Handler，保持使用受控 Client/Planner。
3. 运行 QueryPlan、facts、warnings 和上下文 Patch 回归。
4. 删除未上线的对应旧分支和重复接口。
5. 灰度观察指标和人工反馈；异常时回滚代码版本。

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

### 8.2 灰度与回滚决策

原计划中的 `coordinator-mode=legacy|shadow|handler` 和按能力 legacy 覆盖未实施。代码尚未上线，旧 Coordinator 与新 Coordinator 没有并存和生产双轨的必要：

- 主系统固定调用 v2，新架构异常时回滚已验证代码版本；
- 多帧会话理解继续使用独立的强类型 `shadow/new` 开关，该开关不代表旧、新 Coordinator 双执行；
- 灰度阶段采集 P50/P95、澄清率、能力缺失率、工具失败率和版本冲突率；
- 不重复调用真实模型或业务工具来构造迁移期影子结果。

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

真实数据库同 session 并发测试默认跳过，避免普通单测误连开发数据库。配置
`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PWD` 后，在 `eladmin/`
目录显式执行：

```bash
AGENT_DB_CONCURRENCY_TEST=true \
mvn -pl eladmin-system -am test -DskipTests=false \
  -Dtest='me.zhengjie.modules.agent.session.service.impl.AgentChatSessionConcurrencyIntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

测试使用两个独立线程同时写入同一 session，校验数据库事务依次观察到
`sessionVersion=0/1`、最终客户与日期槽位均保留、版本递增为 2，并在结束后只清理
本测试创建的会话和消息。

### 9.6 评测回归

- 保持现有排餐诊断评测集；
- 保持现有业务查询评测集；
- 保持意图和会话理解评测集；
- 对 QueryPlan、facts、warnings 和 context handles 做结构化基线回归；
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
jenv shell 17
mvn399
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
| 重构导致意图路由变化 | 回答错误或误澄清 | 行为刻画测试、结构化响应断言、代码版本回滚 |
| 会话快照并发覆盖 | 上下文丢失 | sessionVersion、Patch、行锁、冲突率监控 |
| 工具注册合并影响权限 | 越权查询 | 保留主系统二次鉴权，增加权限矩阵测试 |
| DTO 契约切换导致字段丢失 | 历史卡片或任务恢复异常 | OpenAPI 契约测试、已上线前端字段兼容 |
| 规则扫描改变加载顺序 | digest 和诊断结果变化 | 文件名稳定排序、规则集合基线 |
| Model Gateway 改造影响输出 | fallback 增加 | task 级开关、旧 DeepSeek Adapter 保留 |
| 包结构迁移过大 | 合并冲突、交付延期 | 先新增边界，后移动文件，不做一次性搬迁 |

## 12. 回滚方案

每阶段必须满足独立回滚：

- 规则加载：通过回滚已验证代码版本恢复，外部规则目录可移除后回退到 classpath 规则。
- 契约 v2：尚未上线的旧接口和旧 DTO 已删除；异常时回滚整版代码，不能切回不存在的 v1 路径。
- Coordinator：通过回滚已验证代码版本恢复，不维护 capability 级 legacy 分支。
- ToolCatalog：旧 Registry/Executor 保留到全部工具迁移完成。
- 会话无状态化：主系统持久化是唯一真相源，不允许切回 Agent 进程内缓存。
- Model Gateway：保留现有 DeepSeek Adapter 作为默认实现。

数据库字段如新增 `session_version` 或 contractVersion，必须使用兼容式新增，回滚应用版本后不影响旧版本读取。

## 13. 交付物

最终应交付：

- [x] `agent-service/README.md`（2026-07-29）
- [x] 架构基线文档（2026-07-29）
- [x] 规则和能力目录统一加载器（2026-07-29）
- [x] 强类型 Agent 配置（2026-07-29）
- [x] Agent v2 OpenAPI 契约（2026-07-29）
- [x] 统一错误协议（2026-07-29）
- [x] `ConversationCoordinator`（2026-07-29）
- [x] `CapabilityHandler` SPI 与 Registry（2026-07-29）
- [x] `AgentTool<I,O>` SPI 与 ToolCatalog（2026-07-29）
- [x] `AgentModelGateway`（2026-07-29）
- [x] `ConversationSnapshot/Patch` 与会话版本控制（2026-07-29）
- [x] `/api/agent/v2/chat`（2026-07-29）
- [ ] 架构测试、契约测试、打包测试和并发测试（前三项和并发策略单测已完成；真实数据库并发压测待发布前执行。）
- [x] 更新后的业务和 API 文档（2026-07-29）
- [ ] 灰度指标记录和代码版本回滚验证记录

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

## 16. 2026-07-29 实施验收记录

本轮已完成方案的核心架构闭环，但仍按“核心完成”而非“全部完成”管理。自动化和运行验收结果如下：

- `agent-service` 全量单元测试：340 个测试，0 failure，0 error，1 skipped。
- 主系统 Agent 相关测试：109 个测试，0 failure，0 error，1 skipped。（真实 MySQL 并发集成测试默认跳过；已删除 10 个仅覆盖未上线旧客户汇总实现的测试。）
- `agent-service` 已生成 Spring Boot 可执行 JAR；从项目外临时目录启动成功，并能加载 classpath 中的规则、语义目录和 OpenAPI。
- `MealPlanChatServiceImpl` 已收缩为只依赖 `ConversationCoordinator` 的兼容 Facade；能力编译由 `CapabilityHandlerRegistry` 路由。
- `DefaultConversationHandler` 已抽出 `ConversationStateSupport`、`BusinessQueryIntentPolicy`、`BusinessConversationUnderstandingPipeline` 和 `BusinessConversationResultPipeline`；理解、Pending/时间解析、结果焦点保存和审计摘要不再留在默认 Handler。
- 客户餐数、核销和订单查询已统一走强类型 `BusinessQueryDataClient`；旧 `LegacyCustomerInsightAdapter`、三个 `DiagnosisToolDataClient` Map 方法、重复主系统服务和内部接口均已删除。
- Agent 生产包只保留请求快照会话适配器；可变内存会话实现已移入测试源码，对应生产配置和环境变量已删除。
- `DefaultConversationHandler` 生产代码只保留一个构造器，`BusinessQueryChatService` 与 `ContextReferenceResolver` 均由 Spring 注入；测试依赖统一由 Fixture 组装。
- `ToolCatalog` 的历史只读工具改为表驱动执行，描述符和执行器名称集合在类初始化时强制一致；架构测试禁止中心执行方法回退为工具名分支链。
- 主系统到 Agent 固定使用 v2 契约；未上线的 v1 Controller、双路径配置和环境变量切换已删除。
- 同 session 写入增加数据库行锁，保留 `clientMessageId` 幂等和 `sessionVersion` 冲突检测。
- 日志与诊断 trace 不记录 Prompt、模型完整输出、工具原始结果、异常消息、sessionId 或客户标识。
- 工具名与业务响应类型分别由 `ToolCatalog` 和 `BusinessResponseTypeCatalog` 统一登记；Presenter、ResponseFactory 和结果校验器只消费强类型 `BusinessPresentationResult`。
- capabilityId、toolName 和 modelProfile 已按 Handler、工具及模型调用作用域动态写入 MDC，并在调用结束后恢复外层值。
- 业务查询审计统计接口已同时返回平均、P50、P95 耗时和澄清率，灰度期只需按时间窗口保存基线结果。

尚未关闭的发布期事项：

1. 在真实数据库环境显式执行 `AgentChatSessionConcurrencyIntegrationTest`，并保存冲突率、幂等命中率和最终快照校验记录。
2. 在灰度环境观察 v2 的 P50/P95、澄清率、能力缺失率、工具失败率和版本冲突率基线。
3. 完成代码版本回滚演练并保存验证记录，不建设已取消的 capability 级 legacy/handler 双轨。
