# 智能客服 Agent 统一语义分析与时间口径实施计划

> 编制日期：2026-07-14
> 适用范围：`agent-service` 业务语义分析与查询编排、`eladmin-system` 会话持久化与 Agent 内部只读查询
> 关联文档：`智能客服Agent全业务问答剩余任务实施计划.md`、`智能客服Agent自然语言理解与查询纠错优化实施方案.md`、`business/订单管理业务说明.md`、`business/排餐管理业务说明.md`、`business/核销管理业务说明.md`

## 1. 背景与问题基线

### 1.1 已复现会话

会话 ID：`7c015e82-0f45-4c66-a647-108f45d49e05`

```text
用户：现在还有多少客户有餐数没有排餐
助手：请补充统计日期，例如今天、明天或 2026-07-13。
用户：今天
助手：公共菜单仅支持查询午餐或晚餐，请确认需要的餐次。
```

对应请求：

- 第一轮：`26369fa3-0d57-4ebf-a5b0-88629cdd3075`
- 第二轮：`db292374-30ea-4dc8-a1eb-be915516f6d1`

已确认事实：

1. 第一轮被识别为运营统计，并形成“当日待排餐客户数”方向，但“现在”没有形成日期槽位。
2. `DAILY_UNSCHEDULED_CUSTOMER_COUNT` 被代码判定为必须提供 `recordDate`，因此未执行工具，直接追问日期。
3. 第一轮澄清响应没有保存待执行指标和 QueryPlan 草稿。
4. 第二轮“今天”虽然解析出 `recordDate=2026-07-14`，但业务语义被重新分析并错误漂移到 `SCHEDULED_MENU`。
5. 两轮 `tool_names=[]`，没有实际执行运营统计或菜单查询。
6. 本问题不是服务 fallback；是业务语义、时间语义和澄清上下文未统一导致的误路由。

### 1.2 目标业务语义

原问题应解释为：

```text
统计日期：当前业务日期（Asia/Shanghai 当天）
统计对象：客户
指标：DAILY_UNSCHEDULED_CUSTOMER_COUNT
业务含义：截至今天，存在有效剩余餐数、今天按订单规则应服务，但尚未生成有效排餐的客户数
执行工具：由服务端根据指标编译为 getDailyCustomerWorkload
结果字段：unscheduledCustomerCount
```

需要与以下口径区分：

| 用户语义 | 指标 | 说明 |
| --- | --- | --- |
| 现在还有多少客户有餐数 | `ACTIVE_CUSTOMER_COUNT` | 存在进行中且仍有剩余餐数订单的客户去重数，不要求“今天未排餐” |
| 现在还有多少客户有餐数没有排餐 | `DAILY_UNSCHEDULED_CUSTOMER_COUNT` | 当前业务日应服务但尚未排餐的客户数 |
| 今天多少客户排了但还没核销 | `DAILY_UNVERIFIED_CUSTOMER_COUNT` | 当日已排餐但尚未核销客户数 |
| 今天多少客户已经核销 | `DAILY_VERIFIED_CUSTOMER_COUNT` | 当日已核销客户数 |

## 2. 建设目标与边界

### 2.1 建设目标

1. 业务领域、指标、统计对象、时间语义、上下文指代和是否需要澄清统一由 LLM 业务分析器输出。
2. 代码只保留确定性控制、安全边界、时间落地、QueryPlan 编译、权限校验、工具执行和结果校验。
3. 将指标定义、时间策略和业务口径以受控知识目录提供给 LLM，不继续在多个正则和 `if/else` 中重复维护。
4. 澄清响应必须保存待执行语义；用户只补日期、餐次或客户时，继续完成原计划，不重新猜测业务领域。
5. LLM 不可用、超时、非法 JSON、未知枚举或低置信度时才进入明确的规则兜底或受控追问。
6. 查询响应类型、展示标签和结果字段必须从 QueryPlan 指标生成，不再根据原始中文关键词二次判断。

### 2.2 非目标

- 不允许 LLM 生成 SQL、表名、URL、Java 字段名或自由工具名。
- 不允许 LLM 直接读取数据库或决定权限范围。
- 不让 LLM 自行计算“今天”的具体日期；具体日期由服务端使用固定时区和 `Clock` 解析。
- 本阶段不新增写操作能力，不改变 Agent 只读边界。
- 本阶段不修改订单、排餐、核销的业务数据口径，只统一理解和编排入口。

## 3. 目标职责划分

### 3.1 LLM 负责

- 判断业务领域：订单、排餐、核销、客户余额、运营统计、菜单、菜品等。
- 选择受控指标：如 `ACTIVE_CUSTOMER_COUNT`、`DAILY_UNSCHEDULED_CUSTOMER_COUNT`。
- 识别时间语义：当前日、昨天、明天、本周、明确日期、明确日期范围、继承上轮时间。
- 理解组合条件和口语表达：“有餐数没有排餐”“排了但是没核销”“截至目前”。
- 判断本轮是新查询、补充条件、局部改查、追问、纠错还是重试。
- 判断缺少的信息是否会实质改变结果，并输出结构化澄清字段。

### 3.2 服务端代码负责

- `RESET/RETRY/OUT_OF_SCOPE` 等确定性控制指令和安全拦截。
- 客户编号、订单编号、明确 ISO 日期等可确定槽位的提取与校验。
- 将 `CURRENT_DAY` 按 `Asia/Shanghai` 转成具体日期。
- 根据指标目录应用默认时间策略和最大日期范围。
- 将受控语义编译成白名单 QueryPlan 和工具调用。
- 校验指标、领域、维度、时间和权限组合。
- 执行查询、生成事实、校验数字、处理部分结果并审计。

### 3.3 规则兜底负责

- 仅在 LLM 不可用、超时、解析失败、Schema 非法或低置信度时启用。
- 只处理经过业务确认的高精度表达，不用“菜单、排餐、客户”等宽泛单词直接决定最终目标。
- 无法高精度确定时返回结构化澄清，不猜测成某个业务工具。
- 兜底结果必须记录来源和原因，不能与正常 LLM 路径混为一谈。

## 4. 目标处理流程

```text
用户消息 + 持久化会话上下文
    -> Deterministic Guard
       RESET / RETRY / OUT_OF_SCOPE / 安全边界
    -> Deterministic Slot Extractor
       客户、订单、明确日期、餐次等事实槽位
    -> Pending Query Resolver
       如上一轮正在补槽，只合并新增槽位并恢复原语义
    -> LLM BusinessQuestionAnalyzer
       领域、指标、时间语义、交互模式、歧义、置信度
    -> BusinessSemanticValidator
       JSON Schema、枚举、指标目录、时间组合校验
    -> BusinessTemporalResolver
       CURRENT_DAY -> 具体业务日期
    -> BusinessQueryPlanningService
       受控语义 -> QueryPlan
    -> AgentQueryPlanValidator
       权限、范围、维度、工具和预算校验
    -> BusinessQueryOrchestrator
       执行受控只读工具
    -> BusinessAnswerComposer / Validator
       基于指标和 facts 生成、校验回答
    -> 持久化 Last/Pending Business Context
```

目标问题预期分析结果：

```json
{
  "questionType": "BUSINESS_QUERY",
  "domains": ["OPERATION_STATISTICS"],
  "metrics": ["DAILY_UNSCHEDULED_CUSTOMER_COUNT"],
  "dimensions": [],
  "temporal": {
    "expression": "CURRENT_DAY"
  },
  "interactionMode": "NEW_QUERY",
  "requiresClarification": false,
  "confidence": 0.96
}
```

服务端解析后 QueryPlan：

```json
{
  "domain": "OPERATION_STATISTICS",
  "action": "SUMMARY",
  "metrics": ["DAILY_UNSCHEDULED_CUSTOMER_COUNT"],
  "filters": {
    "recordDate": "2026-07-14"
  },
  "toolNames": ["getDailyCustomerWorkload"]
}
```

## 5. 受控语义协议改造

### 5.1 新增时间语义对象

新增：

```text
agent-service/src/main/java/me/zhengjie/agent/analysis/domain/BusinessTemporalExpression.java
agent-service/src/main/java/me/zhengjie/agent/analysis/domain/BusinessTemporalIntent.java
```

首版时间枚举：

```text
UNSPECIFIED
CURRENT_DAY
PREVIOUS_DAY
NEXT_DAY
CURRENT_WEEK
EXPLICIT_DATE
EXPLICIT_RANGE
INHERIT_PREVIOUS
```

`BusinessTemporalIntent` 建议字段：

```json
{
  "expression": "CURRENT_DAY",
  "explicitDate": null,
  "explicitStartDate": null,
  "explicitEndDate": null
}
```

约束：

- `CURRENT_DAY/PREVIOUS_DAY/NEXT_DAY/CURRENT_WEEK` 不允许模型填具体日期。
- `EXPLICIT_DATE` 必须提供合法 ISO 日期，或引用确定性槽位中的日期。
- `EXPLICIT_RANGE` 必须提供合法起止日期，且起始日期不得晚于结束日期。
- `INHERIT_PREVIOUS` 仅在持久化上下文存在时间条件时有效。
- `UNSPECIFIED` 由指标默认时间策略决定是否默认或追问。

修改：

```text
agent-service/src/main/java/me/zhengjie/agent/analysis/domain/BusinessQuestionAnalysis.java
```

新增字段：

```java
private BusinessTemporalIntent temporal;
```

`AgentQueryFilters` 继续只保存已经落地的具体日期，不混入“今天/现在”等自然语言语义。

### 5.2 扩展指标知识目录

修改：

```text
agent-service/src/main/java/me/zhengjie/agent/query/domain/AgentMetricDefinition.java
agent-service/src/main/java/me/zhengjie/agent/query/domain/AgentMetricCatalog.java
```

每个指标至少增加：

```text
domain                 所属业务领域
semanticDescription    提供给 LLM 的受控业务含义
defaultTemporalPolicy  CURRENT_DAY / NONE / REQUIRE_EXPLICIT / INHERIT_OR_CURRENT
requiresSingleDate     是否必须落为单日
resultUnit             客户/订单/餐次/记录
resultFieldKey         服务端固定结果字段
commonBusinessTerms    经业务确认的少量同义表达，仅用于知识说明和离线评测
```

首批目录必须明确：

| 指标 | 语义说明 | 默认时间 |
| --- | --- | --- |
| `ACTIVE_CUSTOMER_COUNT` | 存在进行中且仍有剩余餐数订单的客户去重数 | `NONE` |
| `DAILY_EXPECTED_CUSTOMER_COUNT` | 指定业务日按订单有效期、餐次、排餐模式和排除规则应服务的客户数 | `CURRENT_DAY` |
| `DAILY_UNSCHEDULED_CUSTOMER_COUNT` | 指定业务日应服务但尚未生成有效排餐的客户数 | `CURRENT_DAY` |
| `DAILY_SCHEDULED_CUSTOMER_COUNT` | 指定业务日已生成有效排餐的客户数 | `CURRENT_DAY` |
| `DAILY_UNVERIFIED_CUSTOMER_COUNT` | 指定业务日已排餐但尚未核销的客户数 | `CURRENT_DAY` |
| `DAILY_VERIFIED_CUSTOMER_COUNT` | 指定业务日已核销的客户数 | `CURRENT_DAY` |
| `MEAL_PLAN_FAILURE_COUNT` | 指定业务日、可选餐次的排餐失败记录数 | `CURRENT_DAY` |
| `VERIFICATION_COUNT` | 指定日期或日期范围内的有效核销次数 | `REQUIRE_EXPLICIT` 或按产品确认后的默认策略 |

禁止在 Prompt 中暴露 `resultFieldKey`、数据库字段或工具名；LLM 只接收指标枚举、业务含义、支持维度和时间策略。

### 5.3 新增语义知识渲染器

新增：

```text
agent-service/src/main/java/me/zhengjie/agent/analysis/BusinessSemanticCatalog.java
agent-service/src/main/java/me/zhengjie/agent/analysis/BusinessSemanticPromptRenderer.java
```

职责：

- 从 `AgentMetricCatalog` 和已登记业务目标生成精简的模型知识上下文。
- 只输出当前账号可用工具对应的领域和指标，避免让模型选择无权限能力。
- 为每个指标提供业务定义、时间要求、支持维度和容易混淆的相邻指标。
- 目录内容版本化，日志记录 `semanticCatalogVersion`。
- Prompt 不直接复制整份业务文档，避免上下文过长和不可审计。

### 5.4 强化 LLM 输出校验

修改：

```text
agent-service/src/main/java/me/zhengjie/agent/analysis/LlmBusinessQuestionAnalyzer.java
```

新增校验：

- 指标必须存在于目录且属于声明领域。
- 时间表达和具体日期字段必须符合互斥规则。
- 每日指标缺少时间表达时，允许后续使用指标默认时间，不要求模型伪造日期。
- `ACTIVE_CUSTOMER_COUNT` 不得被强制转换成每日待排餐。
- “排了但没核销”必须选择 `DAILY_UNVERIFIED_CUSTOMER_COUNT`，不能只因出现“排餐”选择已排餐数。
- “有餐数没有排餐”必须区分 `ACTIVE_CUSTOMER_COUNT` 与 `DAILY_UNSCHEDULED_CUSTOMER_COUNT`。
- 模型输出 SQL、工具名、URL、未知枚举或未知字段时拒绝结果。

## 6. 时间落地服务

新增：

```text
agent-service/src/main/java/me/zhengjie/agent/analysis/BusinessTemporalResolver.java
agent-service/src/main/java/me/zhengjie/agent/config/BusinessTimeProperties.java
```

建议配置：

```yaml
agent:
  business-time:
    zone-id: Asia/Shanghai
    default-daily-expression: CURRENT_DAY
```

实现要求：

1. 构造器注入 `Clock`，测试禁止依赖机器当前日期。
2. 所有相对时间按配置时区解析，不使用模型所在时区。
3. 优先级固定为：用户明确日期 > 本轮时间语义 > 待执行上下文 > 会话继承日期 > 指标默认策略。
4. 用户明确说“现在、当前、目前、截至现在”且指标为每日统计时，LLM 输出 `CURRENT_DAY`，Resolver 落为当天。
5. 指标允许默认当天且用户完全未提时间时，可以直接默认，但回答和 facts 必须展示实际使用日期。
6. 只有缺失时间会实质改变结果且指标没有默认策略时才追问。

## 7. 统一业务路由

### 7.1 收缩顶层规则分类

修改：

```text
agent-service/src/main/java/me/zhengjie/agent/chat/RuleBasedIntentClassifier.java
agent-service/src/main/java/me/zhengjie/agent/chat/HybridMealPlanChatExtractor.java
```

规则直判只保留：

- `RESET`
- `RETRY`
- 明确安全拒绝和 `OUT_OF_SCOPE`
- 系统正在补某个槽位时的纯槽位回复

其他已支持业务问题统一输出顶层 `BUSINESS_QUERY`，具体领域和指标交给 `BusinessQuestionAnalyzer`。规则可以提供候选提示，但不得用高置信度阻断 LLM。

### 7.2 删除原始文本二次路由

修改：

```text
agent-service/src/main/java/me/zhengjie/agent/chat/MealPlanChatServiceImpl.java
```

逐步移除或停止使用：

```text
compatibilityBusinessIntent(...)
operationResponseType(String text)
operationMetricLabel(String responseType)
operationTool(String responseType)
```

替代方式：

- responseType 根据 QueryPlan 中的指标固定映射。
- 展示名称从 `AgentMetricDefinition.displayName` 获取。
- 结果字段从服务端登记映射读取。
- 工具由 `BusinessQueryPlanningService` 根据指标目录编译。
- 新增指标时不再修改聊天服务中的中文关键词判断。

目标验收：输入“未排餐、没排餐、没有排餐、还没给他们排、目前哪些人还没安排餐”时，只要 LLM 选择同一指标，后续执行路径完全一致。

## 8. 澄清上下文与跨实例持久化

### 8.1 新增待执行查询上下文

新增：

```text
agent-service/src/main/java/me/zhengjie/agent/query/domain/PendingBusinessQueryContext.java
```

建议字段：

```json
{
  "analysis": {},
  "missingFields": ["recordDate"],
  "originalQuestionSummary": "统计有剩余餐数但尚未排餐的客户数",
  "sourceRequestId": "...",
  "createdAt": "2026-07-14T22:51:39+08:00",
  "expiresAt": "2026-07-14T23:21:39+08:00"
}
```

约束：

- 只保存受控枚举、实体标识、日期、餐次和限长摘要。
- 不保存 Prompt、模型原始响应、工具原始结果、手机号、地址或金额。
- `RESET`、查询成功、用户明确切换业务目标或超时后清空。
- 用户回复纯日期、餐次、客户或订单时，优先补全 Pending Context。
- 用户回复包含新的完整业务问题时，废弃 Pending Context 并重新分析。

### 8.2 主系统持久化

新增 SQL：

```text
eladmin/sql/alter_agent_chat_session_semantic_context.sql
```

建议字段：

```sql
ALTER TABLE agent_chat_session
  ADD COLUMN pending_business_query_json longtext DEFAULT NULL COMMENT '待补充条件的受控业务查询上下文',
  ADD COLUMN last_business_query_context_json longtext DEFAULT NULL COMMENT '最近一次已执行业务查询的脱敏摘要';
```

修改：

```text
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/domain/AgentChatSession.java
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentChatRequest.java
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentChatResponse.java
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImpl.java
agent-service/src/main/java/me/zhengjie/agent/domain/dto/AgentChatRequest.java
agent-service/src/main/java/me/zhengjie/agent/domain/dto/AgentChatResponse.java
agent-service/src/main/java/me/zhengjie/agent/chat/DiagnosisConversationState.java
```

请求增加可选字段：

```text
pendingBusinessQueryContext
lastBusinessQueryContext
```

响应增加可选字段：

```text
pendingBusinessQueryContext
lastBusinessQueryContext
semanticTraceSummary
```

主系统每轮都下发完整受控上下文；`agent-service` 内存状态仅作为性能优化。必须覆盖 Agent 重启和两个实例交替处理同一会话。

### 8.3 澄清续接规则

以目标会话为例：

```text
第一轮分析：
metric=DAILY_UNSCHEDULED_CUSTOMER_COUNT
temporal=CURRENT_DAY
```

正确实现后第一轮无需澄清，直接查询当天。

对于确实需要澄清的场景：

```text
用户：查一下 B3303 的排餐
助手：请确认日期。
Pending：queryTarget=CUSTOMER_MEAL_PLAN, customerCode=B3303, missing=recordDate
用户：今天
系统：只补 recordDate，继续 CUSTOMER_MEAL_PLAN，不重新猜成公共菜单
```

## 9. Hybrid 与 fallback 策略

修改：

```text
agent-service/src/main/java/me/zhengjie/agent/analysis/HybridBusinessQuestionAnalyzer.java
agent-service/src/main/java/me/zhengjie/agent/analysis/RuleBasedBusinessQuestionAnalyzer.java
```

推荐决策顺序：

```text
1. 调用 LLM 业务语义分析
2. Schema、目录和置信度校验通过 -> 使用 LLM
3. 模型不可用/超时/非法/低置信度 -> 调用高精度规则分析器
4. 规则可唯一确定 -> 标记 RULE_FALLBACK 并继续
5. 规则也不能唯一确定 -> 返回结构化澄清
```

新增分析追踪字段：

```text
semanticSource=LLM|RULE_FALLBACK|PENDING_CONTEXT
fallbackReason=MODEL_TIMEOUT|MODEL_INVALID|MODEL_LOW_CONFIDENCE|MODEL_UNAVAILABLE
semanticConfidence
semanticCatalogVersion
temporalExpression
resolvedRecordDate/resolvedStartDate/resolvedEndDate
pendingContextReused
```

严禁把 `NEED_MORE_INFO` 自动视为 fallback；澄清和降级必须分开统计。

## 10. 分阶段实施任务

### Phase 0：冻结问题与回归基线

- [ ] 在 `MealPlanChatServiceImplTest` 增加目标会话的两轮失败复现。
- [ ] 在 `LlmBusinessQuestionAnalyzerSchemaTest` 固化目标分析 JSON。
- [ ] 在 `OperationStatisticsIntentTest` 增加“有餐数没有排餐”的指标期望。
- [ ] 固化当前审计断言：旧逻辑第一轮不调用工具、第二轮错误进入菜单。
- [ ] 记录现有 Agent 测试基线，避免后续把已有失败误认为本次引入。

### Phase 1：受控时间语义和指标知识目录

- [ ] 新增 `BusinessTemporalExpression` 和 `BusinessTemporalIntent`。
- [ ] 扩展 `BusinessQuestionAnalysis` JSON Schema。
- [ ] 扩展 `AgentMetricDefinition/AgentMetricCatalog` 的语义与默认时间策略。
- [ ] 新增 `BusinessSemanticCatalog` 和 Prompt 渲染器。
- [ ] 使用业务文档确认首批指标口径，尤其是剩余餐数、待排餐和待核销的差异。
- [ ] 单元测试目录版本、指标唯一性、领域归属和默认时间策略。

### Phase 2：LLM 统一业务分析

- [ ] 修改 `LlmBusinessQuestionAnalyzer` system/user prompt。
- [ ] 注入指标语义目录、当前业务日期、时区和受控上下文。
- [ ] LLM 输出相对时间枚举，不直接输出相对日期计算结果。
- [ ] 扩展 Schema 和业务一致性校验。
- [ ] 收缩顶层规则分类，业务领域不再由宽泛关键词最终决定。
- [ ] 低置信度和实质歧义返回结构化澄清。

### Phase 3：时间落地和 QueryPlan 编译

- [ ] 新增 `BusinessTemporalResolver`，注入固定 `Clock` 和 `ZoneId`。
- [ ] 将指标默认时间策略应用到分析结果。
- [ ] 修改 `BusinessQueryPlanningService`，只接收已解析的具体日期过滤条件。
- [ ] 扩展 `AgentQueryPlanValidator` 校验时间范围和指标组合。
- [ ] 将 responseType、label、result key 和工具选择统一改为指标驱动。
- [ ] 删除 `operationResponseType(text)` 等原始文本二次判断的调用。

### Phase 4：Pending Query 跨轮与跨实例续接

- [ ] 新增 `PendingBusinessQueryContext`。
- [ ] 新增会话表字段和迁移 SQL。
- [ ] 扩展主系统与 Agent 的聊天请求/响应 DTO。
- [ ] 修改 `AgentChatSessionServiceImpl` 持久化并下发 Pending/Last Context。
- [ ] 修改 `MealPlanChatServiceImpl` 优先续接待补条件计划。
- [ ] 增加清空、过期、业务目标切换和纠错规则。
- [ ] 验证 Agent 重启和双实例切换后仍能继续原查询。

### Phase 5：Fallback、日志与审计

- [ ] 规则分析器只保留高精度离线能力。
- [ ] 增加明确 fallback 原因和语义来源。
- [ ] 审计记录指标、时间表达、解析日期、是否复用 Pending Context。
- [ ] 区分澄清率、fallback 率、意图漂移率和澄清后成功率。
- [ ] 日志不记录完整 Prompt、模型原始响应或敏感业务数据。

### Phase 6：全链路测试、文档与灰度

- [ ] 完成单元、集成、权限、多实例和性能测试。
- [ ] 更新业务文档和 Agent API 文档。
- [ ] 在功能实现并验证通过后更新仓库根目录 `README.md`，确保入口架构说明与实际代码一致。
- [ ] 增加真实语言评测集并旁路对比新旧分析结果。
- [ ] 内部账号灰度，观察错误默认口径和意图漂移。
- [ ] 达到门槛后关闭旧细粒度业务意图兼容分支。

## 11. 详细测试清单

### 11.1 时间语义测试

固定测试时钟：`2026-07-14T12:00:00+08:00`。

| 输入 | 期望时间语义 | 解析结果 |
| --- | --- | --- |
| 现在还有多少客户有餐数没有排餐 | `CURRENT_DAY` | `2026-07-14` |
| 当前还有多少待排餐客户 | `CURRENT_DAY` | `2026-07-14` |
| 目前还有谁没核销 | `CURRENT_DAY` | `2026-07-14` |
| 昨天有多少客户完成核销 | `PREVIOUS_DAY` | `2026-07-13` |
| 明天需要排多少客户 | `NEXT_DAY` | `2026-07-15` |
| 本周核销了多少餐 | `CURRENT_WEEK` | 当周周一至 `2026-07-14` 或按产品确认的完整周口径 |
| 2026-07-10 未排餐客户 | `EXPLICIT_DATE` | `2026-07-10` |
| 7 月 1 日到 7 月 10 日核销数 | `EXPLICIT_RANGE` | `2026-07-01..2026-07-10` |

### 11.2 指标消歧测试

| 输入 | 期望指标 |
| --- | --- |
| 现在还有多少客户有餐数 | `ACTIVE_CUSTOMER_COUNT` |
| 现在还有多少客户有餐数没有排餐 | `DAILY_UNSCHEDULED_CUSTOMER_COUNT` |
| 今天应该送餐但还没排的是谁 | `DAILY_UNSCHEDULED_CUSTOMER_COUNT` |
| 今天已经排了多少客户 | `DAILY_SCHEDULED_CUSTOMER_COUNT` |
| 今天排了但是还没核销的客户 | `DAILY_UNVERIFIED_CUSTOMER_COUNT` |
| 今天已经核销了多少客户 | `DAILY_VERIFIED_CUSTOMER_COUNT` |
| 今天还有多少客户 | 结合上下文消歧；无上下文时结构化澄清 |

### 11.3 多轮续接测试

1. `现在还有多少客户有餐数没有排餐`：直接使用今天并执行统计，不追问。
2. `查 B3303 的排餐` -> `今天`：第二轮仍查询客户排餐，不进入公共菜单。
3. `查一下核销` -> `B3303` -> `本周`：三轮补槽后执行核销查询。
4. `今天待排餐客户` -> `那晚餐呢`：继承日期和指标，仅覆盖餐次。
5. `今天待排餐客户` -> `不对，我问的是没核销`：清除旧 Pending，重新规划为待核销。
6. 第一轮澄清后重启 `agent-service`，第二轮仍能续接原指标。
7. 两个 Agent 实例交替处理同一 session，不出现业务目标漂移。

### 11.4 Fallback 测试

1. LLM 超时，规则明确识别“今天待排餐客户数”，返回 `RULE_FALLBACK`。
2. LLM 返回非法 JSON，规则无法唯一识别时只追问，不调用工具。
3. LLM 输出未知指标、SQL、URL 或工具名时拒绝执行。
4. LLM 置信度低于阈值时不使用低置信度计划。
5. fallback 与正常 `NEED_MORE_INFO` 分别记入统计。

### 11.5 查询与回答测试

1. `DAILY_UNSCHEDULED_CUSTOMER_COUNT` 必须调用 `getDailyCustomerWorkload`。
2. 回答读取 `unscheduledCustomerCount`，不得读取 `unverifiedCustomerCount`。
3. 回答标签来自指标目录，必须显示“待排餐客户数”。
4. facts 包含查询日期、指标代码、口径版本、客户单位和查询时间。
5. 工具结果字段缺失时返回受控错误，不使用其他数字字段兜底。
6. 数据权限和现有运营统计接口口径保持不变。

## 12. 预期修改文件清单

### 12.1 agent-service

新增：

```text
analysis/domain/BusinessTemporalExpression.java
analysis/domain/BusinessTemporalIntent.java
analysis/BusinessSemanticCatalog.java
analysis/BusinessSemanticPromptRenderer.java
analysis/BusinessTemporalResolver.java
config/BusinessTimeProperties.java
query/domain/PendingBusinessQueryContext.java
```

修改：

```text
analysis/domain/BusinessQuestionAnalysis.java
analysis/LlmBusinessQuestionAnalyzer.java
analysis/HybridBusinessQuestionAnalyzer.java
analysis/RuleBasedBusinessQuestionAnalyzer.java
chat/RuleBasedIntentClassifier.java
chat/HybridMealPlanChatExtractor.java
chat/DiagnosisConversationState.java
chat/MealPlanChatServiceImpl.java
domain/dto/AgentChatRequest.java
domain/dto/AgentChatResponse.java
query/domain/AgentMetricDefinition.java
query/domain/AgentMetricCatalog.java
query/BusinessQueryPlanningService.java
query/AgentQueryPlanValidator.java
query/BusinessAnswerComposer.java
query/BusinessQueryResponseFactory.java
config/AgentServiceConfig.java
resources/application.yml
```

### 12.2 eladmin-system

新增：

```text
eladmin/sql/alter_agent_chat_session_semantic_context.sql
```

修改：

```text
modules/agent/session/domain/AgentChatSession.java
modules/agent/domain/dto/AgentChatRequest.java
modules/agent/domain/dto/AgentChatResponse.java
modules/agent/session/domain/dto/AgentChatSessionDetailDto.java
modules/agent/session/service/impl/AgentChatSessionServiceImpl.java
```

### 12.3 测试

新增或重点修改：

```text
agent-service/src/test/java/me/zhengjie/agent/analysis/BusinessTemporalResolverTest.java
agent-service/src/test/java/me/zhengjie/agent/analysis/BusinessSemanticCatalogTest.java
agent-service/src/test/java/me/zhengjie/agent/analysis/LlmBusinessQuestionAnalyzerSchemaTest.java
agent-service/src/test/java/me/zhengjie/agent/analysis/HybridBusinessQuestionAnalyzerTest.java
agent-service/src/test/java/me/zhengjie/agent/chat/HybridMealPlanChatExtractorTest.java
agent-service/src/test/java/me/zhengjie/agent/chat/MealPlanChatServiceImplTest.java
agent-service/src/test/java/me/zhengjie/agent/chat/OperationStatisticsIntentTest.java
agent-service/src/test/java/me/zhengjie/agent/query/BusinessQueryPlanningServiceTest.java
agent-service/src/test/java/me/zhengjie/agent/query/AgentQueryPlanValidatorTest.java
agent-service/src/test/java/me/zhengjie/agent/query/BusinessAnswerComposerTest.java
eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java
```

### 12.4 根目录 README

功能实现并通过测试后修改：

```text
README.md
```

必须同步以下内容：

1. **Agent 架构图**：在 `Deterministic Guard` 后增加 `Pending Query Resolver`、LLM 统一业务语义分析、指标知识目录和 `BusinessTemporalResolver`；主系统会话服务标明持久化 Pending/Last Business Context。
2. **组件职责**：明确 LLM 负责业务领域、指标、时间语义和上下文理解；服务端负责日期落地、QueryPlan 编译、安全校验和工具执行。
3. **请求处理流程**：说明纯补槽回复优先续接 Pending Query，避免重新分析导致业务目标漂移。
4. **时间口径**：说明“现在/当前/目前”等相对时间由 LLM 输出受控时间枚举，再由服务端按 `Asia/Shanghai` 和 `Clock` 转成具体日期。
5. **Fallback 说明**：规则只在模型不可用、超时、非法输出或低置信度时兜底；正常澄清不属于 fallback。
6. **配置说明**：补充 `business-semantic.mode`、置信度、Pending Context TTL 和业务时区配置。
7. **近期演进和目录结构**：增加统一语义知识目录、时间解析和持久化上下文能力。
8. **相关文档链接**：在 README 文档导航中增加本实施计划。

README 只描述已经落地并验证通过的能力。本计划创建时不提前修改 README 的“当前架构”，避免把尚未实现的设计写成现状。

## 13. 构建与验证命令

Agent 服务使用 JDK 17：

```bash
cd agent-service
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
/Users/qqx/job/maven/apache-maven-3.9.9/bin/mvn test
```

主系统使用项目约定的 JDK 8 和 Maven：

```bash
cd eladmin
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home/bin:/Users/qqx/job/maven/apache-maven-3.5.2-bin/apache-maven-3.5.2/bin:$PATH \
mvn -pl eladmin-system -am test -DskipTests=false
```

建议按阶段执行定向测试，再执行全量回归。所有会写测试数据的主系统测试必须只清理自身新增数据。

## 14. 灰度与可观测性

建议配置：

```yaml
agent:
  chat:
    business-semantic:
      mode: shadow|llm_first|rule_only
      confidence-threshold: 0.80
      pending-context-enabled: true
      pending-context-ttl-minutes: 30
    business-time:
      zone-id: Asia/Shanghai
```

灰度顺序：

1. `shadow`：旧结果生效，新分析旁路记录指标、时间和 QueryPlan 差异。
2. 内部测试账号启用 `llm_first`，重点观察运营统计和多轮补槽。
3. 达到门槛后扩大范围，并逐步移除旧细粒度业务意图兼容分支。
4. 如模型异常，可切回 `rule_only`；持久化上下文和时间 Resolver 不回退。

核心指标：

- 业务领域准确率。
- 指标选择准确率。
- 时间语义识别准确率。
- 澄清率和澄清后成功回答率。
- Pending Context 复用成功率。
- 意图漂移率。
- LLM fallback 率及原因分布。
- 工具选择错误率。
- 错误默认口径率。

## 15. 验收标准

- [ ] 目标问题单轮直接查询当天待排餐客户数，不再追问日期。
- [ ] “今天”作为补槽回复时不会重新漂移到公共菜单或其他领域。
- [ ] LLM 能稳定区分活跃有餐客户、待排餐客户、已排餐客户和待核销客户。
- [ ] “现在/当前/目前/截至现在”在每日指标中统一解析为当前业务日期。
- [ ] 具体日期由 `BusinessTemporalResolver` 按 `Asia/Shanghai` 生成，单元测试可固定时钟。
- [ ] 业务查询不再依赖 `operationResponseType(String text)` 选择指标、标签或结果字段。
- [ ] 新增指标只需登记目录、编译规则、工具和测试，不需要在聊天服务增加中文关键词分支。
- [ ] LLM 无法使用 SQL、URL、任意工具名或未知枚举绕过服务端编译。
- [ ] Agent 重启和双实例切换不丢失 Pending/Last Business Context。
- [ ] fallback、澄清和正常 LLM 路径在日志与运营统计中可区分。
- [ ] 目标自然语言评测集的指标选择准确率不低于 97%，时间语义准确率不低于 98%。
- [ ] 原排餐诊断、客户查询、订单查询、核销查询、菜单查询和会话历史回归通过。

## 16. 文档同步要求

实施代码时同步更新：

```text
README.md
eladmin/doc/business/订单管理业务说明.md
eladmin/doc/business/排餐管理业务说明.md
eladmin/doc/business/核销管理业务说明.md
eladmin/doc/business/智能排查助手业务说明.md
eladmin/doc/business/智能客服Agent指标口径字典.md
eladmin/doc/apidoc/智能排查助手接口文档.md
eladmin/doc/apidoc/智能客服Agent内部业务查询接口文档.md
```

如果聊天请求/响应增加 Pending/Last Context 字段，必须同步更新接口文档，并标明字段为内部受控上下文、不可由前端任意构造。

新增或修改的方法必须补充清晰的方法注释，说明用途、关键参数、时间口径、上下文来源和返回含义。
