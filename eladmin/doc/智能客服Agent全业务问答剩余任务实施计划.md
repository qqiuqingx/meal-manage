# 智能客服 Agent 全业务问答剩余任务实施计划

> 编制日期：2026-07-12
> 基线文档：`eladmin/doc/智能客服Agent全业务只读查询能力实施任务清单.md`
> 实施目标：补齐原清单未完成任务，并将跨客户统计、受控自然语言报表和模糊问题的大模型分析纳入正式范围。

## 1. 目标与边界

### 1.1 最终目标

智能客服 Agent 应能够回答当前登录用户权限范围内的业务只读问题，包括：

- 单客户档案、地址、饮食限制、套餐、订单、餐数、排餐、核销和退餐问题。
- 跨模块原因分析，例如有餐未排、已排未核销、餐数变化和菜品过滤原因。
- 跨客户运营统计，例如今日客户数、待排餐客户数、待核销客户数和按餐次/套餐/来源分组统计。
- 有明确指标和维度定义的自然语言汇总问题。
- 业务规则、统计口径和当前实时数据的混合问题。
- 模糊、省略、口语化、带错别字或依赖上下文的问题。

Agent 不要求用户先掌握固定问法。对于模糊问题，系统应先使用大模型分析用户想查询的业务对象、指标、维度、过滤条件和歧义点；只有关键歧义会改变查询结果时才追问。

### 1.2 “所有业务问题”的工程定义

“所有业务问题”不能实现为模型自由生成 SQL，而应定义为：

```text
业务问题覆盖范围
= 已登记业务领域
+ 已登记指标
+ 已登记维度
+ 已登记过滤条件
+ 已登记业务工具
+ 当前用户有权访问的数据范围
```

当问题未命中已登记能力时，Agent 必须说明当前缺少的业务能力，并记录到“未支持问题 Top N”，不能猜测答案。

### 1.3 保留的安全边界

- Agent 不直连数据库，不生成或执行自由 SQL。
- Agent 不新增、修改、删除、重排餐、核销或退餐。
- 所有数字由 `eladmin-system` 业务服务计算，大模型不得自行汇总业务列表。
- 内部 Token 与当前客服访问上下文必须同时有效。
- 金额能力继续默认关闭；如产品确认开放，必须单独设计权限、DTO、工具、审计和脱敏策略。
- 任何跨客户统计必须校验业务权限、数据范围、日期范围和结果上限。

## 2. 当前基线核查

### 2.1 已有代码基础

以下能力已有主体实现，可以在原代码上补齐，不应重复建设：

- QueryPlan、QueryPlan 校验器和固定业务领域枚举。
- 客户、订单、排餐、核销、退餐、套餐、菜品和业务规则专用查询服务。
- 通用内部业务查询 Controller、HMAC 访问上下文和工具权限映射。
- 通用业务工具注册表、单轮调用预算、数据预算和同参缓存。
- 事实引用、回答安全校验、查询时间、部分失败和结构化结果。
- 客户、订单、排餐、核销、退餐、套餐和菜品前端卡片。
- 主系统会话持久化、业务实体上下文和查询审计。
- 120 条评测集结构门禁。

### 2.2 已确认缺口

- `ChatIntent` 仍持续扩展，业务查询没有真正迁移为大模型生成受控 QueryPlan。
- `LlmIntentClassifier` 只允许少量固定意图，不能覆盖任意业务领域。
- `MealPlanChatServiceImpl` 仍承担大量业务路由、组合查询和回答拼装。
- QueryPlan 没有运营统计领域、统计维度、排序白名单和聚合查询契约。
- 工具注册表没有每日客户工作量、待排餐、待核销等跨客户统计工具。
- 现有 `DailyCustomerStats.totalCustomerCount` 实现按分组求和，需先确认并修正跨餐次/套餐重复计数风险。
- 业务规则仍主要硬编码在 Java 中，没有统一版本化规则加载机制。
- 强类型 DTO 已建立，但部分内部适配和展示层仍使用 `Map<String, Object>`。
- `agent-service` 仍保留内存会话，多实例和重启恢复没有完成全链路验收。
- 当前 120 条评测用例大量为相同问法增加场景编号，不是真实语言覆盖。
- 主系统定向测试存在管理员工具白名单期望未同步失败。
- 可直接回答率、追问率、识别失败率和未支持问题 Top N 尚未完整落地。

## 3. 目标架构

```text
eladmin-web
  -> eladmin-system
      -> 登录用户、权限、数据范围、sessionId、requestId
      -> agent-service
          -> RuleBasedEntityExtractor
          -> BusinessQuestionAnalyzer（大模型）
              -> 领域、对象、指标、维度、过滤条件
              -> 歧义、置信度、澄清问题
          -> BusinessQueryPlanner
              -> 受控 QueryPlan
          -> AgentQueryPlanValidator
              -> 白名单、权限、范围、预算校验
          -> BusinessQueryOrchestrator
              -> 一个或多个受控只读工具
          -> eladmin-system 内部业务查询接口
              -> 主系统业务服务计算确定性结果
          -> BusinessAnswerComposer
              -> 固定问题使用模板
              -> 复杂问题允许模型润色
          -> BusinessAnswerValidator
              -> facts、对象、数字、敏感字段、部分失败校验
      -> 保存消息、上下文、QueryPlan 摘要和审计
  -> 展示文本、卡片、事实依据、口径和查询时间
```

## 4. 核心协议设计

### 4.1 BusinessQuestionAnalysis

在 `agent-service` 新增模型分析结果，不直接复用 `ChatIntent`：

```text
agent-service/src/main/java/me/zhengjie/agent/analysis/domain/BusinessQuestionAnalysis.java
agent-service/src/main/java/me/zhengjie/agent/analysis/domain/BusinessAmbiguity.java
agent-service/src/main/java/me/zhengjie/agent/analysis/BusinessQuestionAnalyzer.java
agent-service/src/main/java/me/zhengjie/agent/analysis/LlmBusinessQuestionAnalyzer.java
agent-service/src/main/java/me/zhengjie/agent/analysis/HybridBusinessQuestionAnalyzer.java
```

建议字段：

```json
{
  "questionType": "BUSINESS_QUERY",
  "domains": ["OPERATION_STATISTICS"],
  "entities": {},
  "metrics": ["DAILY_SCHEDULED_CUSTOMER_COUNT"],
  "dimensions": ["MEAL_TYPE"],
  "filters": {"recordDate": "2026-07-12"},
  "ambiguities": [
    {
      "field": "remainingMeaning",
      "options": ["UNSCHEDULED", "UNVERIFIED", "UNDELIVERED"],
      "material": true
    }
  ],
  "confidence": 0.82,
  "requiresClarification": true,
  "clarificationQuestion": "你想查今天待排餐、待配送还是待核销的客户数？"
}
```

约束：

- 模型只能返回 JSON Schema 中声明的枚举和字段。
- 模型不得返回 URL、SQL、表名、任意字段名或工具名。
- 模型分析失败时回退到规则提取结果，不触发无条件查询。
- `confidence` 低于阈值或存在 `material=true` 的歧义时必须澄清。
- 非关键歧义可以使用产品默认值，但回答必须展示所采用的口径。

### 4.2 QueryPlan 2.0

在兼容 `1.0` 的前提下增加 `2.0`：

- 新增领域：`OPERATION_STATISTICS`、`NATURAL_LANGUAGE_REPORT`。
- 新增动作：保留 `SUMMARY`，增加 `BREAKDOWN`。
- 新增维度枚举：日期、餐次、套餐、客户来源、订单状态、排餐状态、核销状态。
- 扩充指标枚举，禁止模型传入任意指标字符串。
- 新增 `limit`、受控排序枚举和是否需要明细字段。
- 增加默认口径 ID、口径版本和时区。
- QueryPlan 必须记录由规则还是模型产生，以及模型置信度。

建议新增：

```text
AgentQueryDimension
AgentQuerySort
AgentMetricDefinition
AgentMetricCatalog
AgentQueryPlanV2
```

### 4.3 指标目录

第一批必须覆盖：

| 指标 | 口径要求 |
|---|---|
| `DAILY_SCHEDULED_CUSTOMER_COUNT` | 指定日期有效排餐客户去重数 |
| `DAILY_VERIFIED_CUSTOMER_COUNT` | 指定日期已核销客户去重数 |
| `DAILY_UNVERIFIED_CUSTOMER_COUNT` | 指定日期已排餐未核销客户去重数 |
| `DAILY_EXPECTED_CUSTOMER_COUNT` | 按有效订单、排餐模式、日期、餐次和排除规则计算的应服务客户去重数 |
| `DAILY_UNSCHEDULED_CUSTOMER_COUNT` | 应服务客户减去已生成有效排餐客户，按客户+餐次去重 |
| `ACTIVE_CUSTOMER_COUNT` | 存在进行中且仍有剩余餐数订单的客户去重数 |
| `ACTIVE_ORDER_COUNT` | 进行中订单数 |
| `EXPIRING_ORDER_COUNT` | 指定日期范围内到期的进行中订单数 |
| `MEAL_PLAN_FAILURE_COUNT` | 指定日期和餐次生成失败的客户排餐数 |
| `VERIFICATION_COUNT` | 未删除核销记录的受控统计 |
| `REFUND_COUNT` | 退餐记录的受控统计 |

每个指标必须登记：

- `metricCode`
- 中文名称和常见问法
- 业务定义
- 责任模块
- 数据权限
- 支持的维度和过滤条件
- 默认日期范围和最大日期范围
- 结果单位
- 去重键
- 口径版本和更新时间
- 对应业务 Service 方法

## 5. 分阶段实施任务

> 2026-07-13 进展：公共菜单 P0 数据链路已完成。内部菜单接口使用受控午餐/晚餐集合和分组响应，公共菜单不再合并全局米饭候选，菜单 facts 使用 `SCHEDULED_DISH_LIST`。菜单语义分析、脱敏的上轮查询摘要、`CORRECTION -> REPLAN` 指纹保护及结果合理性告警已完成首批接入；其他业务目标仍保留兼容路由，按相同框架逐步迁移。

## 阶段 P0：修复基线并冻结当前成果

### P0-1 修复当前失败测试

修改：

```text
eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/security/DefaultAgentQueryPermissionServiceTest.java
```

任务：

- 将 `listScheduledDishes` 纳入管理员可用工具期望。
- 增加“拥有 `mealPlan:list` 但没有 `dish:list` 时不可使用公共菜单工具”测试。
- 运行主系统 Agent 权限和查询服务定向测试。

验收：主系统 Agent 定向测试零失败。

### P0-2 回填原清单状态

修改：

```text
eladmin/doc/智能客服Agent全业务只读查询能力实施任务清单.md
```

任务：

- 按“已完成 / 部分完成 / 未完成 / 已调整范围”更新复选框或状态标记。
- 在原清单顶部链接本实施计划。
- 将跨客户统计和自然语言报表从暂缓项移出，并指向本计划。

验收：文档状态与代码和测试证据一致。

### P0-3 建立回归基线

- 保存 `agent-service`、主系统和前端当前测试结果。
- 明确当前支持的工具、权限、接口和卡片清单。
- 禁止在后续阶段删除原排餐诊断能力。

## 阶段 P1：大模型业务问题分析与受控规划

### P1-1 实现业务问题分析器

任务：

- 新增 `BusinessQuestionAnalyzer`，输出强类型 `BusinessQuestionAnalysis`。
- 规则层优先识别客户编号、订单编号、日期、日期范围、餐次和明确指标。
- 大模型分析省略、指代、模糊词、组合问题和口语表达。
- 将当前会话客户、订单、排餐、日期、餐次和最近 QueryPlan 摘要提供给模型。
- 模型输入只包含必要上下文，不包含工具原始结果、手机号和完整地址。
- 使用 JSON Schema 和枚举反序列化拒绝未知字段。

测试：

- “今天还有多少客户”识别到运营统计并产生关键歧义。
- “还有几个人没弄完”结合上一轮“今天午餐排餐”解析为待处理客户统计。
- “张三和李四谁还有餐”识别多个客户并要求澄清比较范围。
- “这笔订单呢”正确使用当前订单。
- 模型返回未知领域、任意 SQL 或未知指标时拒绝执行。

### P1-2 将业务查询从 ChatIntent 迁移到 QueryPlan

任务：

- `ChatIntent` 只保留 `RESET`、`RETRY`、`FOLLOW_UP`、`DIAGNOSE`、`BUSINESS_QUERY` 和 `OUT_OF_SCOPE` 等顶层意图。
- 移除新增业务能力时继续增加 `CUSTOMER_*` 枚举的模式。
- `BusinessQuestionAnalysis -> BusinessQueryPlanner -> QueryPlan` 成为业务查询唯一入口。
- 保留旧意图到 QueryPlan 的兼容适配，完成回归后再删除旧分支。

验收：新增一个指标不需要修改 `ChatIntent` 和 `MealPlanChatServiceImpl` 的业务 `switch`。

### P1-3 扩展 QueryPlan 与校验器

任务：

- 增加统计领域、维度、指标、受控排序和口径版本。
- 校验指标与领域、维度、过滤条件的组合合法性。
- 校验跨客户查询权限和数据范围。
- 默认单日统计；自然语言报表最大范围按指标配置，默认不超过 31 天。
- 明细结果限制条数，聚合结果限制分组数。
- 金额、自由字段、自由排序、未知维度和未知指标全部拒绝。
- 返回结构化澄清字段，不把缺参数当成查询失败。

## 阶段 P2：跨客户运营统计能力

### P2-1 统一每日客户统计口径

阅读并同步：

```text
eladmin/doc/business/客户管理业务说明.md
eladmin/doc/business/订单管理业务说明.md
eladmin/doc/business/排餐管理业务说明.md
eladmin/doc/business/核销管理业务说明.md
```

任务：

- 明确“今日客户”“今日还有客户”“待排餐”“待核销”“待配送”的业务定义。
- 确认每个统计按客户、客户+餐次还是排餐记录去重。
- 修复或替换 `DishServiceImpl.getDailyCustomerStats()` 中分组求和造成的潜在重复计数。
- 统计逻辑抽取到可复用服务，页面和 Agent 共用同一口径。

建议新增：

```text
me.zhengjie.modules.agent.query.service.AgentOperationQueryService
me.zhengjie.modules.agent.query.service.impl.AgentOperationQueryServiceImpl
me.zhengjie.modules.agent.query.domain.dto.AgentDailyCustomerStatsDto
me.zhengjie.modules.agent.query.domain.dto.AgentOperationBreakdownDto
```

### P2-2 新增运营统计内部接口

建议接口：

```http
POST /api/internal/agent/operations/daily-customers
POST /api/internal/agent/operations/active-customers
POST /api/internal/agent/operations/expiring-orders
POST /api/internal/agent/operations/meal-plan-failures
```

要求：

- 强类型请求和响应 DTO。
- 同时校验 Agent 入口权限和对应业务权限。
- 返回总数、分组、口径 ID、口径版本、查询时间、时区和截断状态。
- 统计明细仅在明确请求且有权限时返回，并限制最大条数。
- 不返回手机号、完整地址、金额或工具内部异常。

### P2-3 注册统计工具并实现回答

新增工具建议：

```text
getDailyCustomerWorkload
getActiveCustomerSummary
getExpiringOrderSummary
getMealPlanFailureSummary
```

任务：

- 在 `AgentBusinessToolRegistry` 登记领域、动作、权限、超时、最大分组数和敏感级别。
- 在客户端增加强类型传输 DTO。
- Composer 使用主系统返回的指标和分组生成回答。
- 每个关键数字生成 factId，来源指向指标口径和查询日期。
- 前端增加运营统计卡片，支持总数和餐次/套餐/来源分组。

“今天还有多少客户”的验收行为：

```text
无上下文：追问“待排餐、待配送还是待核销？”
上文为“今天午餐排餐”：解释为今日午餐待排餐或待处理统计。
用户回答“待核销”：调用 getDailyCustomerWorkload，返回待核销客户去重数和口径。
```

### P2-4 受控自然语言报表

任务：

- 只允许指标目录中的指标和维度组合，不开放任意 SQL。
- 第一版支持单指标或少量指标、最多两个分组维度、最大 31 天。
- 返回数据表和摘要，不允许模型改变数值。
- 明细导出不在本阶段实现；如后续需要，单独设计权限和异步任务。

## 阶段 P3：补齐原清单业务查询缺口

### P3-1 客户与订单

- 客户概览增加最近核销和最近退餐摘要，避免只有记录总数。
- 明确客户状态、客户类型和套餐分类字段来源。
- 验证客户页面和 Agent 的有效订单及剩余餐数完全一致。
- 验证订单不存在与订单不属于当前客户使用不可枚举的安全错误表达。
- 将 `BusinessQueryDataClient` 的旧 Map 方法迁移为纯强类型契约。

### P3-2 排餐查询

- 补齐手工新增、手工删除、手工换菜和首次成功排餐摘要。
- 补齐生成失败原因、生成批次和使用订单/套餐展示字段。
- 增加按排餐客户记录 ID 查询的关系越权测试。
- 对菜品和配料结果统一截断标识。

### P3-3 核销、退餐、套餐和菜品

- 增加累计核销统计，不仅返回最近记录列表。
- 区分核销、核销回退、退餐和订单取消。
- 套餐规格返回缺失项和责任模块。
- 菜品过滤结果返回结构化过滤原因，而不只返回数量。
- 验证午餐和晚餐共享午晚餐池的所有回答模板。

### P3-4 版本化业务规则

任务：

- 将 Java 硬编码规则迁移为版本化规则目录或结构化配置。
- 规则条目包含规则 ID、版本、更新时间、责任模块、依据文档和展示内容。
- 启动时校验规则 schema，错误规则不注册。
- 实时数据与规则混合问题分别生成“当前数据 facts”和“业务规则 facts”。
- 业务规则变更必须同步对应业务文档和规则版本。

## 阶段 P4：编排、回答和会话重构

### P4-1 拆分聊天服务

目标：`MealPlanChatServiceImpl` 只保留会话控制和顶层路由。

建议拆分：

```text
BusinessQuestionAnalysisService
BusinessQueryPlanningService
BusinessQueryExecutionService
BusinessClarificationService
BusinessAnswerService
DiagnosisConversationService
```

任务：

- 排餐诊断继续交给 `MealPlanDiagnosisService`。
- 通用业务查询全部交给 `BusinessQueryChatService`。
- 组合问题由 QueryPlan 声明工具，不在聊天服务中新增专用分支。
- 单轮工具失败时保留成功事实并设置 `partial=true`。

### P4-2 模型回答策略

- 单工具、固定统计和高频查询使用确定性模板。
- 多工具组合问题允许模型根据 facts 组织语言。
- 提供给回答模型的只有 QueryPlan、结构化 facts、口径和受控警告。
- 模型回答必须经过数字、对象、权限、敏感字段和执行声明校验。
- 校验失败时回退到结构化模板，不返回未经验证的模型文本。

### P4-3 澄清策略

只有以下情况追问：

- 多个客户或订单候选无法唯一确定。
- 日期、餐次或指标缺失会导致不同结果。
- 模糊词存在多个业务口径且上下文不能消解。
- 用户同时提出互相冲突的过滤条件。

不应追问：

- “今天”“昨天”“本周”等可按系统时区确定的日期。
- 已在会话中唯一确定的客户、订单和餐次。
- 产品已定义默认口径且回答会明确展示该口径的情况。

### P4-4 会话一致性

- 主系统持久化当前客户、订单、排餐、日期、餐次和最近 QueryPlan 摘要。
- `agent-service` 内存只作为单请求优化，不作为业务真相源。
- 每轮请求由主系统下发完整受控上下文。
- 更换客户时清理订单和排餐引用；更换订单时清理不匹配排餐引用。
- 增加服务重启和双实例切换测试。

## 阶段 P5：权限、安全与审计

### P5-1 数据范围

- 复核客户、订单、排餐表是否支持部门、创建人或门店数据范围。
- 定义跨客户统计在不同角色下的数据范围。
- 统计总数必须基于授权后的数据集，不能先全量统计再隐藏明细。
- 管理员、普通客服、只读运营角色分别建立权限用例。

### P5-2 提示词与工具安全

- 测试“忽略规则”“返回金额”“调用未登记工具”“扩大日期范围”等注入。
- 模型不能获得完整权限集合，只接收本轮可用领域和能力摘要。
- 工具执行前再次校验 QueryPlan 和主系统访问上下文。
- 日志不记录原始 prompt、工具原始响应、手机号或完整地址。

### P5-3 查询审计和未支持问题

扩展审计字段或新增受控记录：

- `analysisSource`
- `analysisConfidence`
- `clarificationRequired`
- `metricCodes`
- `dimensionCodes`
- `unsupportedReason`
- `answerValidationResult`

如需新增或修改表结构：

- 增加 SQL 建表/迁移脚本。
- 使用数据安全审查清单流程登记新字段。
- 明确审计保留周期和清理策略。

## 阶段 P6：前端工作台

### P6-1 修正文案和入口定位

- 页面、欢迎语、异常提示统一使用“智能客服助手”。
- 欢迎语不再要求必须提供客户、日期和餐次。
- 输入示例同时覆盖单客户查询和运营统计。

### P6-2 澄清交互

- 关键歧义使用选项按钮或候选卡片，不要求用户重新输入整句话。
- 展示当前采用的客户、订单、日期、餐次和统计口径。
- 允许用户纠正“不是待核销，是待排餐”。

### P6-3 统计和报表卡片

- 每日客户工作量卡片。
- 指标总数与分组明细表。
- 口径、查询时间、时区、部分失败和截断提示。
- 明细跳转继续依赖目标页面自身权限。

### P6-4 前端安全

- 不渲染模型返回的动态 HTML。
- 不将敏感结构化结果写入 localStorage/sessionStorage。
- 会话切换后清理上一会话临时卡片和筛选状态。
- 订单及统计卡片默认不包含金额字段。

## 阶段 P7：测试、评测与全链路验收

### P7-1 重建真实评测集

现有 120 条结构保留，但用真实不同问法替换模板重复用例。

最低要求：

- 真实或人工改写问题不少于 200 条。
- 单客户查询不少于 60 条。
- 跨模块组合问题不少于 30 条。
- 跨客户运营统计不少于 40 条。
- 模糊、省略、指代、错别字和口语问题不少于 30 条。
- 权限、金额、提示词注入和越权问题不少于 20 条。
- 未支持能力和安全拒绝问题不少于 20 条。

每条用例声明：

- 期望分析领域、指标、维度和歧义。
- 是否应该追问及期望追问字段。
- 期望 QueryPlan。
- 必调和禁调工具。
- 关键 facts。
- 是否允许部分回答。
- 期望权限结果。

### P7-2 单元测试

`agent-service`：

- 大模型分析 JSON 解析和枚举校验。
- 模糊问题、上下文消歧和澄清。
- QueryPlan 2.0 校验。
- 统计工具预算、缓存、超时和部分失败。
- 模型回答事实一致性。

`eladmin-system`：

- 每个统计指标的去重和边界口径。
- 页面业务服务与 Agent 统计结果一致。
- 数据范围、权限和关系越权。
- DTO 金额隔离和日志脱敏。
- 会话上下文持久化。

`eladmin-web`：

- 澄清选项交互。
- 统计卡片和事实依据。
- 会话切换不串数据。
- 权限不足和部分失败显示。

### P7-3 集成测试

覆盖完整链路：

```text
eladmin-web -> eladmin-system -> agent-service -> eladmin-system
```

必须验证：

- requestId、sessionId 和 operator 全链路一致。
- Agent 服务重启后继续会话。
- 两个 Agent 实例交替处理同一会话。
- 单个工具超时或无权限时返回受控部分结果。
- 模型不可用时规则和模板能力仍可使用。
- 原排餐诊断、反馈、历史会话和运营统计无回归。

### P7-4 性能测试

- 单工具查询 P95 不超过 3 秒。
- 多工具组合查询 P95 不超过 8 秒。
- 大模型分析设置独立超时和失败降级。
- 跨客户统计必须使用聚合查询，禁止先加载无界明细到 Java 或模型。

## 阶段 P8：可观测性、文档与上线

### P8-1 核心指标

补齐：

- 可直接回答率。
- 需要追问率。
- 澄清后成功回答率。
- 客户、订单和指标识别失败率。
- 各领域和指标问题分布。
- 工具失败率、平均耗时和 P95。
- 部分回答率。
- 权限拒绝次数。
- 未支持问题 Top N。
- 回答校验失败率。
- 客服有用反馈率。

### P8-2 文档同步

至少更新：

```text
eladmin/doc/business/智能排查助手业务说明.md
eladmin/doc/business/智能客服助手使用说明.md
eladmin/doc/business/智能客服助手运维配置说明.md
eladmin/doc/business/客户管理业务说明.md
eladmin/doc/business/订单管理业务说明.md
eladmin/doc/business/排餐管理业务说明.md
eladmin/doc/business/核销管理业务说明.md
eladmin/doc/apidoc/智能排查助手接口文档.md
eladmin/doc/apidoc/智能客服Agent内部业务查询接口文档.md
```

新增：

```text
eladmin/doc/business/智能客服Agent指标口径字典.md
eladmin/doc/apidoc/智能客服Agent运营统计内部接口文档.md
```

### P8-3 灰度上线

- 第一阶段仅对内部测试客服开放。
- 收集未支持问题、错误澄清和无用回答。
- 每周复核 Top N，补指标、工具或真实评测案例。
- 达到验收阈值后再扩大客服范围。

## 6. 原 Task 1～27 收尾映射

| 原任务 | 本计划收尾阶段 |
|---|---|
| Task 1～3 QueryPlan、校验和意图 | P1 |
| Task 4～9 DTO 与业务查询服务 | P2、P3 |
| Task 10～13 内部接口、访问上下文、权限和工具 | P2、P5 |
| Task 14～17 编排、回答、校验和会话 | P4 |
| Task 18～20 前端与安全 | P6 |
| Task 21～25 测试、评测和集成 | P7 |
| Task 26～27 审计和核心指标 | P5、P8 |
| 文档同步与生产验收 | P8 |

## 7. 推荐执行顺序与依赖

```text
P0 基线修复
 -> P1 问题分析与 QueryPlan 2.0
 -> P2 跨客户统计
 -> P3 现有领域补齐
 -> P4 编排和会话重构
 -> P5 权限、安全和审计
 -> P6 前端工作台
 -> P7 测试与评测
 -> P8 灰度上线
```

并行约束：

- P2 和 P3 可在 QueryPlan 2.0 协议稳定后并行。
- P5 的数据范围设计必须在跨客户统计接口开放前完成。
- P6 可在接口 DTO 稳定后并行，但不得自行解释模型自然语言。
- P7 的评测集建设应从 P1 开始持续进行，不应等到开发结束。

## 8. 每阶段交付物

每个阶段必须同时交付：

- 代码和方法注释。
- 单元测试与必要集成测试。
- 业务口径或接口文档。
- 权限与敏感字段检查结果。
- 对应评测案例。
- 原清单状态更新。

禁止仅提交接口或仅提交提示词而没有业务口径、权限和测试。

## 9. 构建与验证命令

项目当前实际使用 Java 17，按本地 Maven 配置执行：

```bash
cd agent-service
source ~/.zshrc && jenv shell 17 && mvn399 && mvn -q test
```

```bash
cd eladmin
source ~/.zshrc && jenv shell 17 && mvn399 && mvn -q -DskipTests=false test
```

```bash
cd eladmin-web
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service test:unit --runInBand
NODE_OPTIONS=--openssl-legacy-provider npm run build:prod
```

## 10. 最终上线验收标准

- [ ] 已登记业务问题可回答率不低于 95%。
- [ ] 模糊问题能够正确澄清或结合上下文消歧，错误默认口径率低于 2%。
- [ ] 客户编号、订单编号、日期和餐次识别准确率不低于 98%。
- [ ] 剩余餐数、客户数、订单数、核销数和退餐数准确率为 100%。
- [ ] 跨客户统计与对应业务页面或核验 SQL 的授权范围口径一致。
- [ ] 所有关键数字都具有 factId、指标口径、业务对象和查询时间。
- [ ] 金额与未授权敏感数据泄露次数为 0。
- [ ] 数据越权次数为 0。
- [ ] 提示词注入不能绕过工具、指标、权限和日期范围白名单。
- [ ] 工具部分失败时不输出伪完整结论。
- [ ] Agent 重启和多实例切换不丢失关键业务上下文。
- [ ] 真实评测集不少于 200 条，且不是相同问法的编号复制。
- [ ] 单工具查询 P95 不超过 3 秒，多工具查询 P95 不超过 8 秒。
- [ ] 原排餐诊断、反馈、历史会话和前端工作台回归通过。
- [ ] 主系统、Agent 服务和前端构建及测试全部通过。
- [ ] 业务文档、API 文档、指标字典和运维说明同步完成。

## 11. 完成定义

单个能力只有同时满足以下条件才可标记完成：

1. 业务口径已由责任模块确认。
2. 主系统存在强类型只读服务并复用真实业务逻辑。
3. 内部接口、工具、权限和数据范围已配置。
4. QueryPlan 能选择该能力，模糊问法能正确澄清。
5. 回答数字来自结构化 facts，不由模型自行计算。
6. 前端能够安全展示结果、口径、时间和部分失败。
7. 单元测试、权限测试、评测案例和全链路测试通过。
8. 业务文档和接口文档已同步。

任何一项缺失，都只能标记为“部分完成”，不能视为已交付。
