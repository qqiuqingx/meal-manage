# 智能客服 Agent 自然语言理解与查询纠错优化实施方案

> 编制日期：2026-07-13
> 适用范围：`agent-service` 智能客服对话编排、`eladmin-system` Agent 内部只读查询、`eladmin-web` 查询结果展示
> 关联文档：`eladmin/doc/智能客服Agent全业务问答剩余任务实施计划.md`、`eladmin/doc/apidoc/智能客服Agent内部业务查询接口文档.md`、`eladmin/doc/business/配菜管理业务说明.md`、`eladmin/doc/business/排餐管理业务说明.md`

## 1. 实施背景

### 1.1 已复现问题

2026-07-13 会话中出现以下问答：

```text
用户：今天的菜单是什么
Agent：指定日期的公共排期菜单：山姆东北长粒香、山姆有机杂粮米……

用户：今天的菜单查的不对吧，怎么全是米饭
Agent：再次返回相同的公共排期米饭列表
```

审计与会话记录显示：

- 两轮实际调用的都是 `listScheduledDishes`，并未调用订单列表工具。
- 查询只提取了 `recordDate=2026-07-13`，`mealType=null`。
- 排期 SQL 必须按 `meal_time` 匹配，空餐次导致普通排期菜为空。
- 查询服务在排期菜之外无条件合并全部启用米饭，最终只剩 9 个米饭项。
- 事实生成器将未单独适配的 `total` 默认标记为“订单数量 / ORDER_LIST”，造成“Agent 查了订单表”的误解。
- “查得不对、怎么全是”没有被识别为上下文纠错；因为消息再次包含“菜单”，规则仍以 0.95 高置信度路由到相同查询，LLM 未被触发。

### 1.2 根本问题

当前混合架构的职责边界不合理：

1. 规则不仅提取确定性参数，还仅凭“菜单”等宽泛名词直接决定业务意图。
2. 高置信度规则会阻断 LLM，自然语言越复杂，越容易被某个关键词误导。
3. LLM 意图协议只有粗粒度意图，不能表达“否定上轮结果、要求重新规划、纠错原因”。
4. 会话状态保存了轮次和最近诊断结果，但没有保存可供重新规划的上轮业务查询摘要。
5. 结果安全校验重点是金额、数字和对象一致性，缺少业务合理性校验。

## 2. 优化目标与边界

### 2.1 目标

- 将规则职责收缩为“明确控制指令 + 确定性槽位提取 + 安全边界”。
- 将业务语义、口语化表达、省略、代词、否定和纠错交给 LLM 分析。
- LLM 只输出受控语义 JSON，不输出 SQL、URL、表名、任意字段或工具名。
- 服务端将受控语义映射为 QueryPlan，继续负责权限、参数、工具、数据范围和业务口径。
- 对“上轮结果不对”建立可观测、可测试、不重复错误计划的重新规划机制。
- 修复公共菜单查询的餐次、米饭候选和事实标签问题。

### 2.2 不在本次范围

- 不允许 LLM 自由选择未登记工具或构造任意查询。
- 不允许 LLM 自行统计列表、推断未返回数据或修改业务数据。
- 不将所有业务规则堆入 system prompt；数据来源、默认口径和执行约束仍由可测试代码保证。
- 不在本次开放金额、写操作或自由 SQL 能力。

## 3. 设计原则

### 3.1 LLM 负责“理解”

LLM 负责：

- 用户想查的业务对象和问题类型。
- 本轮是新查询、追问、局部改查、结果纠错还是简单重试。
- “他、这个客户、这笔订单、刚才的菜单”等上下文指代。
- “查得不对、搞错了、不应该吧、怎么全是”等多样化否定表达。
- 哪些歧义会实质影响查询结果，是否需要追问。

### 3.2 服务端负责“保证”

服务端负责：

- 将业务对象、指标、维度和过滤条件映射到白名单 QueryPlan。
- 决定“当天全部菜单”应查哪些餐次、使用什么数据来源。
- 确保公共排期菜单与客户候选菜、客户已生成排餐三种概念不混淆。
- 权限、数据范围、日期范围、结果上限、超时、缓存和审计。
- 校验查询计划与结果的对象、餐次、来源和基本业务合理性。

## 4. 目标架构

```text
用户消息 + 最近业务查询摘要
        -> Deterministic Guard
           明确控制指令、安全拦截、确定性槽位提取
        -> BusinessQuestionAnalyzer (LLM first for semantics)
           领域、目标、交互模式、对象、过滤、歧义、置信度
        -> BusinessQueryPlanningService
           受控语义 -> 白名单 QueryPlan
        -> AgentQueryPlanValidator
           工具、权限、范围、预算、纠错计划校验
        -> BusinessQueryOrchestrator
           执行一个或多个受控只读工具
        -> BusinessResultValidator
           结构一致性 + 领域合理性 + 异常结果检测
        -> BusinessAnswerComposer
           按 facts 组装回答，必要时使用 LLM 润色
        -> BusinessAnswerValidator
           数字、引用、敏感字段、越权声称校验
```

关键调整：不再使用“宽泛业务关键词命中 = 0.95 置信度 = 跳过 LLM”的决策方式。

## 5. 受控语义协议设计

### 5.1 扩展 BusinessQuestionAnalysis

在现有 `BusinessQuestionAnalysis` 上增加以下受控字段：

```json
{
  "questionType": "BUSINESS_QUERY",
  "queryTarget": "SCHEDULED_MENU",
  "interactionMode": "CORRECTION",
  "referenceTurn": "PREVIOUS_BUSINESS_QUERY",
  "domains": ["DISH"],
  "entities": {},
  "filters": {
    "recordDate": "2026-07-13",
    "mealScope": "ALL_AVAILABLE"
  },
  "correction": {
    "reason": "PREVIOUS_RESULT_IMPLAUSIBLE",
    "observations": ["ONLY_RICE_RETURNED"],
    "requiresReplan": true
  },
  "ambiguities": [],
  "confidence": 0.96,
  "requiresClarification": false
}
```

新增枚举建议：

| 枚举 | 值 | 说明 |
| --- | --- | --- |
| `BusinessInteractionMode` | `NEW_QUERY` | 新业务查询 |
|  | `FOLLOW_UP` | 围绕上轮结果补充询问 |
|  | `REFINE` | 替换日期、餐次、客户等局部条件 |
|  | `CORRECTION` | 否定上轮理解或结果，必须重新规划 |
|  | `RETRY` | 用户明确要求使用相同条件重试 |
| `BusinessQueryTarget` | `SCHEDULED_MENU` | 公共排期菜单 |
|  | `CUSTOMER_MEAL_PLAN` | 客户已生成排餐 |
|  | `DISH_CANDIDATES` | 客户候选菜及过滤结果 |
|  | 其他现有目标 | 与已登记业务领域对齐 |
| `MealScope` | `BREAKFAST` / `LUNCH` / `DINNER` | 单餐次 |
|  | `ALL_AVAILABLE` | 当日所有已登记菜单餐次 |
| `CorrectionReason` | `WRONG_TARGET` | 上轮查错业务对象 |
|  | `WRONG_FILTER` | 日期、餐次、客户等条件错误 |
|  | `PREVIOUS_RESULT_IMPLAUSIBLE` | 结果与用户预期明显矛盾 |
|  | `MISSING_DATA` | 结果缺失用户期望部分 |
|  | `UNKNOWN` | 确认是纠错但原因不明 |

`observations` 必须使用白名单枚举，禁止将用户原文无限长度写入查询计划或审计字段。

### 5.2 最近业务查询上下文

在会话状态中新增 `lastBusinessQueryContext`，只保存重新规划所需的受控摘要：

```json
{
  "responseType": "BUSINESS_QUERY_SCHEDULED_MENU",
  "queryTarget": "SCHEDULED_MENU",
  "queryPlanFingerprint": "sha256:...",
  "domain": "DISH",
  "filters": {
    "recordDate": "2026-07-13",
    "mealScope": "ALL_AVAILABLE"
  },
  "resultShape": {
    "total": 9,
    "mealTypes": [],
    "dishTypeDistribution": {"RICE": 9},
    "warnings": []
  },
  "assistantSummary": "已返回指定日期公共菜单",
  "queriedAt": "2026-07-13T09:48:51+08:00"
}
```

不将工具原始响应、完整客户数据、手机号、完整地址或金额放入 LLM 上下文。

## 6. 意图与语义分析改造

### 6.1 收缩高置信度规则

`RuleBasedIntentClassifier` 的高置信度快速路径仅保留：

- `RESET`：“清空会话、重新开始”。
- `RETRY`：“使用相同条件再试一次”等明确重试指令。
- `OUT_OF_SCOPE`：明确的写操作或尚未开放的金额查询。
- 不需要业务语义判断的纯槽位回复，例如系统正在追问餐次，用户只回复“午餐”。

以下宽泛词只能产生候选领域，不得直接给出 0.95 最终意图：

```text
菜单、菜品、订单、排餐、客户、套餐、核销、退餐、剩余、情况
```

建议规则候选置信度上限为 `0.65`，默认进入 LLM 语义分析。

### 6.2 通用上下文依赖门控

代码不穷举每种否定说法，只负责识别“可能依赖上下文”的粗粒度信号并强制触发 LLM：

- 句子中存在否定、反问或对结果质疑的语气。
- 使用“他、这个、刚才、上一个、这些”等指代。
- 存在“换成、改查、那午餐呢”等局部覆盖表达。
- 本轮语义只有结合上轮结果才能完整解释。

实现上可保留少量正则作为低成本触发器，但命中后的动作是“交给 LLM”，而不是“直接决定业务意图”。

### 6.3 LLM system prompt

将稳定指令从当前的 `.user(prompt)` 字符串中拆出，使用 Spring AI `ChatClient` 的 system 消息承载。

system prompt 的核心约束：

```text
你是内部客服业务问题分析器，只输出符合 JSON Schema 的语义分析，不查库、不回答用户。

1. 不得输出 SQL、URL、表名、任意字段名或工具名。
2. 不得仅因出现“菜单、订单、排餐”等单一名词就忽略整句和会话上下文。
3. 当用户否定、质疑或指出上轮结果异常时，interactionMode 必须优先考虑 CORRECTION。
4. CORRECTION 不得简单复制上轮语义；必须标记 requiresReplan=true，并分析可能错在目标、过滤条件还是结果合理性。
5. “当天菜单”未指定餐次时，语义层输出 mealScope=ALL_AVAILABLE；如上下文明确限定餐次，则继承该餐次。
6. 关键歧义会改变业务对象或结果口径时，requiresClarification=true。
7. 不得根据常识补造业务数据。
```

user 消息仅包含：

- 当前用户原始问题。
- 已脱敏、限长的最近会话轮次。
- 当前受控槽位。
- `lastBusinessQueryContext` 受控摘要。

### 6.4 分析结果校验

`LlmBusinessQuestionAnalyzer` 继续执行严格 schema 校验，并新增：

- `CORRECTION` 必须存在 `referenceTurn` 和 `correction.requiresReplan=true`。
- `queryTarget=SCHEDULED_MENU` 的 domain 必须是 `DISH`。
- `mealScope=ALL_AVAILABLE` 不得同时携带单一 `mealType`。
- 模型置信度低于 `0.80` 或存在实质歧义时不执行工具。
- 模型输出非法时，可回退到“追问”或有充分证据的高精度规则，不允许回退到宽泛关键词直接查询。

## 7. 公共菜单业务口径修正

### 7.1 明确三种“菜单”

| 用户语义 | 查询目标 | 数据来源 | 是否需要客户 |
| --- | --- | --- | --- |
| 今天公共菜单 | `SCHEDULED_MENU` | `meal_schedule_plan JOIN dish` | 否 |
| B3303 今天实际吃什么 | `CUSTOMER_MEAL_PLAN` | `meal_plan_customer_item` 及关联排餐 | 是 |
| B3303 今天哪些菜可用 | `DISH_CANDIDATES` | 排期菜 + 套餐 + 过敏/排除过滤 | 是 |

LLM 负责区分用户语义，服务端负责保证每个目标只使用对应数据源。

### 7.2 未指定餐次的默认策略

产品默认口径：

- “今天菜单、明天菜单、2026-07-14 菜单”未指定餐次时，查询当日所有支持排期的菜单餐次。
- 按当前业务模型，公共排期菜单包含 `LUNCH` 和 `DINNER`；`BREAKFAST` 不进入公共排期菜品明细。
- 回答按餐次分组展示，不将午餐和晚餐菜品合并为一个无标识列表。
- 当用户明确说“今天午餐菜单”时，只查 `LUNCH`。
- 上下文已明确餐次时，“那菜单呢”可继承该餐次，并在回答中显示所用口径。

QueryPlan 不应将 `mealType=null` 传入只接受单餐次的 SQL。可选实现：

1. 扩展 `listScheduledDishes` 接口支持受控 `mealScopes=[LUNCH,DINNER]`，主系统一次查询并分组返回。
2. Agent 编排器在同一 QueryPlan 下分别调用 `LUNCH` 和 `DINNER`，然后合并成分组结果。

推荐方案 1：主系统能使用一个业务服务统一保证餐次范围、排序和分组口径，且只消耗一次 Agent 工具预算。

### 7.3 公共菜单不合并全局米饭候选

`SCHEDULED_MENU` 只返回 `meal_schedule_plan` 中该日期、餐次明确配置的菜品。

- 不得调用当前 `mergeCandidates` 将所有启用米饭追加到公共菜单。
- 全局米饭是排餐候选池概念，仅在 `DISH_CANDIDATES` 或正式排餐选菜中使用。
- 如产品后续需要在公共菜单展示“默认米饭”，必须建立单独的可追溯默认米饭配置，不能展示全部米饭主档。
- 同步核对主档米饭类型 `RICE_TYPE` 与当前查询实现使用 `RICE` 的差异，以现行业务文档和正式排餐实现为准统一口径。

### 7.4 分组响应结构

建议内部接口返回强类型结构，不再使用原始 `Map`：

```json
{
  "recordDate": "2026-07-13",
  "groups": [
    {
      "mealTypeCode": "LUNCH",
      "mealTypeName": "午餐",
      "total": 4,
      "items": []
    },
    {
      "mealTypeCode": "DINNER",
      "mealTypeName": "晚餐",
      "total": 4,
      "items": []
    }
  ],
  "total": 8,
  "truncated": false
}
```

## 8. 查询纠错与重新规划

### 8.1 CORRECTION 处理流程

```text
识别为 CORRECTION
    -> 读取 lastBusinessQueryContext
    -> LLM 分析否定的对象、条件或结果异常
    -> 生成新的受控语义
    -> 生成新 QueryPlan
    -> 比较上轮与本轮 QueryPlan fingerprint
    -> 执行或追问
```

规则：

- `CORRECTION` 不能直接使用上轮缓存结果。
- 如新旧 QueryPlan fingerprint 完全相同，必须有可证明的重新查询理由，例如上轮超时、部分失败或用户明确要求 `RETRY`。
- 对“结果不对”的 `CORRECTION`，如新计划与上轮相同，不得再次无解释返回相同结果；应先执行结果合理性检查，仍无法修正时追问用户期望的业务对象或口径。
- 回答应简要说明已修正的查询口径，例如“上一次没有区分餐次，现按今天午餐和晚餐公共排期分别查询”。

### 8.2 上轮结果合理性摘要

对不同业务目标生成受控 `resultShape`，用于纠错分析：

- 菜单：餐次分布、菜品类型分布、是否只有米饭、是否为空。
- 订单：记录数、状态分布、是否匹配当前客户。
- 排餐：日期、餐次、记录数、生成状态、菜品明细数。
- 核销/退餐：日期范围、餐次分布、记录数。

LLM 仅接收分布摘要，不接收原始业务对象列表。

## 9. 结果合理性和事实标签校验

### 9.1 新增 BusinessResultValidator

结果校验在工具执行后、回答组装前进行，至少覆盖：

| 查询目标 | 校验 | 失败处理 |
| --- | --- | --- |
| `SCHEDULED_MENU` | 日期必须存在 | 追问日期 |
| `SCHEDULED_MENU` | 必须有单餐次或 `ALL_AVAILABLE` | 不执行空餐次 SQL |
| `SCHEDULED_MENU` | 返回餐次必须属于计划范围 | `PLAN_RESULT_MISMATCH` |
| `SCHEDULED_MENU` | 结果只有全局米饭且没有排期来源 | `MENU_RESULT_IMPLAUSIBLE`，禁止声称完整菜单 |
| `CUSTOMER_MEAL_PLAN` | 客户、日期、餐次与 QueryPlan 一致 | 隐藏结果并记录告警 |
| 全部列表查询 | `total` 与当前响应类型有明确事实定义 | 不允许回退到“订单数量” |

### 9.2 修正 facts 生成

`BusinessQueryResponseFactory` 不得对所有未匹配 `BUSINESS_QUERY_*` 统一使用 `ORDER_LIST`。建立显式映射：

| responseType | label | unit | sourceType |
| --- | --- | --- | --- |
| `BUSINESS_QUERY_ORDER` | 订单数量 | 笔 | `ORDER_LIST` |
| `BUSINESS_QUERY_MEAL_PLAN` | 排餐记录数 | 条 | `MEAL_PLAN_LIST` |
| `BUSINESS_QUERY_VERIFICATION` | 核销记录数 | 条 | `VERIFICATION_LIST` |
| `BUSINESS_QUERY_REFUND` | 退餐记录数 | 条 | `REFUND_LIST` |
| `BUSINESS_QUERY_SCHEDULED_MENU` | 排期菜品数 | 道 | `SCHEDULED_DISH_LIST` |
| `BUSINESS_QUERY_DISH` | 菜品数量 | 道 | `DISH_LIST` |

未登记的 responseType 不生成数字 fact，并记录 `FACT_MAPPING_MISSING`，防止再次用错误标签误导用户。

## 10. 具体代码改造清单

### 10.1 agent-service

| 文件 | 改造内容 |
| --- | --- |
| `chat/RuleBasedIntentClassifier.java` | 收缩高置信度规则；宽泛业务词仅产生低置信度候选 |
| `chat/RuleBasedSlotExtractor.java` | 保留客户、订单、日期、餐次等确定性槽位；不再仅凭“菜单”直接决定最终意图 |
| `chat/HybridMealPlanChatExtractor.java` | 增加上下文依赖强制 LLM 路径；调整规则快速路径条件 |
| `chat/LlmIntentClassifier.java` | 逐步退化为顶层安全分类，业务语义统一交给 `BusinessQuestionAnalyzer` |
| `analysis/domain/BusinessQuestionAnalysis.java` | 增加 `queryTarget`、`interactionMode`、`referenceTurn`、`correction`、`mealScope` |
| `analysis/LlmBusinessQuestionAnalyzer.java` | 使用 system prompt；注入最近业务查询摘要；扩展严格 JSON schema 校验 |
| `analysis/HybridBusinessQuestionAnalyzer.java` | 业务问题默认 LLM 语义分析；仅明确高精度规则跳过模型 |
| `chat/DiagnosisConversationState.java` | 增加受控 `lastBusinessQueryContext` 及限长清理逻辑 |
| `query/BusinessQueryPlanningService.java` | 将 `queryTarget + mealScope` 映射为受控 QueryPlan；支持 `CORRECTION` |
| `query/AgentQueryPlanValidator.java` | 校验公共菜单餐次范围、纠错重新规划和 plan fingerprint |
| `query/BusinessQueryResponseFactory.java` | 修正菜单/菜品 facts；未登记类型禁止默认为订单 |
| `query/BusinessAnswerComposer.java` | 公共菜单按午餐/晚餐分组；纠错回答说明修正后口径 |
| 新增 `query/BusinessResultValidator.java` | 实现结果结构与业务合理性校验 |
| 新增 `query/domain/LastBusinessQueryContext.java` | 定义可持久化、可传给 LLM 的脱敏上轮查询摘要 |
| 新增 `query/domain/BusinessCorrection.java` | 定义纠错原因、异常观测和是否必须重新规划 |

`MealPlanChatServiceImpl` 应逐步删除对 `SCHEDULED_MENU_QUERY` 等细粒度意图的专用分支，改为统一执行 `BusinessQuestionAnalysis -> QueryPlan -> Orchestrator`。为降低一次性改造风险，第一阶段可保留旧分支作为兼容回退，但新路径必须优先。

### 10.2 eladmin-system

| 文件 | 改造内容 |
| --- | --- |
| `modules/agent/query/service/AgentDishQueryService.java` | 新增按日期和受控餐次集查询公共菜单的强类型方法 |
| `modules/agent/query/service/impl/AgentDishQueryServiceImpl.java` | 公共菜单改为只查 `meal_schedule_plan`；不再复用 `mergeCandidates` |
| `modules/agent/rest/InternalAgentBusinessQueryController.java` | 扩展 `/dishes/scheduled` 的受控餐次集请求和分组响应 |
| `modules/agent/query/domain/dto/AgentMealPlanQueryRequest.java` | 拆分或扩展为专用菜单请求 DTO，禁止餐次集出现未登记值 |
| 新增菜单分组 DTO | 返回日期、餐次分组、菜品列表、数量和截断标识 |
| `MealSchedulePlanMapper.java/xml` | 如采用单次查询，新增餐次白名单 `IN` 查询，不允许任意字符串 |

### 10.3 eladmin-web

| 文件 | 改造内容 |
| --- | --- |
| `views/agent/diagnosis/index.vue` | 对 `CORRECTION` 回答显示“已重新规划”口径摘要；展示结果异常告警 |
| 菜单结果卡片 | 按午餐/晚餐分组展示，区分“公共排期菜单”与“客户实际排餐” |
| facts 展示 | 菜单使用“排期菜品数 / SCHEDULED_DISH_LIST”，禁止显示“订单数量” |

### 10.4 配置与开关

新增配置建议：

```yaml
agent:
  chat:
    semantic-analysis:
      enabled: true
      confidence-threshold: 0.80
      business-keyword-fast-path-enabled: false
      correction-replan-enabled: true
      unchanged-plan-on-correction: CLARIFY
    scheduled-menu:
      default-meal-scopes: LUNCH,DINNER
      include-global-rice-candidates: false
```

生产环境开关必须支持独立灰度和快速回退，不得只提供一个全局 LLM 开关。

## 11. 测试方案

### 11.1 单元测试

#### 意图和语义分析

- “今天的菜单是什么”不再由单一“菜单”关键词以 0.95 置信度结束分析。
- “今天菜单”输出 `queryTarget=SCHEDULED_MENU`、`mealScope=ALL_AVAILABLE`。
- “今天午餐菜单”输出 `mealScope=LUNCH`。
- “B3303 今天吃什么”输出 `queryTarget=CUSTOMER_MEAL_PLAN`，不是公共菜单。
- “B3303 今天有哪些候选菜”输出 `queryTarget=DISH_CANDIDATES`。
- “菜单查得不对”输出 `interactionMode=CORRECTION`。
- “怎么全是米饭”在上轮为菜单查询时输出 `PREVIOUS_RESULT_IMPLAUSIBLE + ONLY_RICE_RETURNED`。
- “再查一次”输出 `RETRY`，允许同计划执行。
- “不对”但无上轮业务查询上下文时追问，不自行选择领域。

#### QueryPlan

- `SCHEDULED_MENU + ALL_AVAILABLE` 生成受控餐次范围 `[LUNCH,DINNER]`。
- `SCHEDULED_MENU` 不能带空餐次执行单餐次 SQL。
- `CORRECTION` 与上轮计划完全相同时，除非为 `RETRY`，否则返回追问或合理性检查结果。
- LLM 伪造工具名、SQL、URL、任意枚举时必须被拒绝。

#### 菜单查询

- 公共菜单只返回 `meal_schedule_plan` 排期菜。
- 排期为空时返回空分组，不返回全部米饭。
- 全局米饭候选仍可在客户候选菜预览中使用，防止修正公共菜单时破坏正式排餐逻辑。
- `LUNCH` 和 `DINNER` 按稳定顺序分组，各组菜品按 `sort + id` 排序。

#### facts

- 菜单总数事实为“排期菜品数 / 道 / SCHEDULED_DISH_LIST”。
- 菜品列表事实为“菜品数量 / 道 / DISH_LIST”。
- 未知 `BUSINESS_QUERY_*` 不得生成 `ORDER_LIST`。

### 11.2 集成测试

覆盖以下端到端路径：

1. “今天菜单” -> 查询午餐和晚餐 -> 分组返回 -> facts 正确。
2. “今天午餐菜单” -> 只查午餐。
3. “菜单不对，怎么全是米饭” -> 识别纠错 -> 不复用上轮缓存 -> 修正餐次和数据源 -> 返回重新规划说明。
4. 菜单确实未配置 -> 明确回答该日期/餐次无公共排期，不用米饭填充。
5. LLM 服务不可用 -> 明确问法可使用高精度回退；否定/纠错问法进入追问，不重复错误结果。
6. 当前账号缺少 `dish:list` 或 `mealPlan:list` -> 不泄露菜单是否存在。

### 11.3 自然语言评测集

不使用“同一问法 + 场景编号”扩充评测，每个语义至少覆盖：

- 正式问法、口语化、省略、错别字、反问、否定、代词、多轮改查。
- 同一关键词对应不同目标，例如公共菜单、客户实际菜单、候选菜、菜品配料。
- 不含“不对”原词的纠错表达，例如“不应该只有这些吧”、“你刚才是不是查错了”、“这明显是主食列表”。
- 提示词注入和伪造工具指令。

上线门槛建议：

- 顶层业务领域准确率 `>= 95%`。
- 菜单三种语义区分准确率 `>= 97%`。
- 纠错识别召回率 `>= 95%`，精确率 `>= 90%`。
- 纠错后无解释重复相同错误 QueryPlan 的比例为 `0`。
- 未登记工具、SQL、URL 输出通过率为 `0`。

## 12. 可观测性与运营指标

审计记录新增或汇总以下字段：

- `interaction_mode`：新查询、追问、改查、纠错、重试。
- `query_target`：公共菜单、客户排餐、候选菜等受控目标。
- `semantic_source`：`RULE` / `LLM` / `HYBRID` / `FALLBACK`。
- `semantic_confidence`。
- `previous_plan_fingerprint`、`current_plan_fingerprint`。
- `replanned`、`plan_changed`。
- `result_validation_code`。
- `clarification_required`。

运营指标：

- LLM 语义分析触发率、成功率、P50/P95 延迟。
- 规则快速路径占比及事后纠错率。
- 纠错轮数、重新规划成功率、同计划重复率。
- `MENU_RESULT_IMPLAUSIBLE`、`FACT_MAPPING_MISSING`、`PLAN_RESULT_MISMATCH` 分布。
- 菜单问题直接回答率、追问率、用户否定率。
- 未支持问题 Top N，用于扩充受控领域、指标和工具，不用于无限增加关键词正则。

## 13. 分阶段实施计划

### 阶段 P0：修复确定性数据错误（1～2 人日）

- 修正公共菜单空餐次执行问题。
- 未指定餐次时查询午餐和晚餐并分组返回。
- 公共菜单不再合并全局米饭候选。
- 修正菜单/菜品 facts 被标记为 `ORDER_LIST` 的问题。
- 增加回归测试和同步 API/业务文档。

P0 不依赖 LLM 改造，可先独立上线，立即阻断“菜单全是米饭”的错误数据链路。

### 阶段 P1：语义理解与纠错协议（3～5 人日）

- 收缩关键词高置信度路由。
- 扩展 `BusinessQuestionAnalysis`、交互模式和纠错枚举。
- 将稳定指令迁移到 system prompt。
- 在会话中保存 `lastBusinessQueryContext`。
- 实现 `CORRECTION -> REPLAN`、计划指纹对比和同计划重复保护。
- 补充多样化自然语言评测集。

### 阶段 P2：统一业务查询编排（4～7 人日）

- 将菜单、菜品、排餐等旧细粒度意图分支迁移到 `BusinessQuestionAnalysis -> QueryPlan`。
- 新增 `BusinessResultValidator`并覆盖其他高频业务领域。
- 将菜单内部响应和 Agent 客户端改为强类型 DTO。
- 减少 `MealPlanChatServiceImpl` 中的业务专用 `if/switch`。
- 完成灰度、双实例会话恢复、性能和故障回退验收。

## 14. 上线与回退策略

### 14.1 灰度步骤

1. 先上线 P0 数据链路修正，不改变全局意图分类。
2. P1 开启影子分析：LLM 生成语义结果但不执行，与现有路由对比并记录差异。
3. 对管理员或指定客服账号开启新语义规划，保留旧路径回退开关。
4. 观察至少 3 个工作日的纠错率、追问率、延迟和工具失败率。
5. 评测和线上指标达标后扩大灰度，最后关闭宽泛关键词快速路径。

### 14.2 回退

- 菜单 P0 修复可独立保留，不随 LLM 语义开关回退。
- LLM 语义异常时，回退到高精度规则或追问，不回退到宽泛关键词直接查询。
- 新 QueryPlan 编排异常时，可通过开关恢复旧业务分支，但仍使用 P0 修正后的菜单数据服务。

## 15. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| LLM 调用增多导致延迟上升 | 对话响应变慢 | 保留明确控制指令和纯槽位回复快速路径；限长上下文；监控 P95 |
| LLM 将普通追问误判为纠错 | 不必要重新查询 | 交互模式评测集；纠错置信度门槛；无上轮上下文时禁止 CORRECTION |
| system prompt 被当作唯一业务保证 | 模型遗漏规则导致错查 | 业务口径继续由 Planner、Validator 和主系统 Service 强制 |
| 公共菜单修正误伤正式排餐米饭候选 | 排餐缺少米饭 | 公共菜单新建专用查询，不修改正式排餐 `mergeCandidateDishes` 逻辑 |
| 会话上下文扩张 | 隐私和 token 风险 | 仅保存受控摘要和分布；限制轮次和字段；不传完整业务对象 |
| 纠错时无限重新规划 | 循环与额外调用 | 单轮最多一次语义重新规划；计划仍不可执行时追问或人工核对 |

## 16. 文档同步要求

实施代码时必须同步更新：

- `eladmin/doc/apidoc/智能客服Agent内部业务查询接口文档.md`：补充公共菜单请求、分组响应、权限和错误码。
- `eladmin/doc/business/配菜管理业务说明.md`：明确公共排期菜单和全局米饭候选池的边界。
- `eladmin/doc/business/排餐管理业务说明.md`：如米饭类型口径或候选菜来源有调整，保持与正式排餐实现一致。
- `eladmin/doc/智能客服Agent全业务问答剩余任务实施计划.md`：更新大模型分析、纠错重新规划和结果合理性校验的完成状态。

## 16.1 实施进度（2026-07-13）

- 已完成 P0：公共菜单按受控午餐/晚餐集合查询并分组；只使用 `meal_schedule_plan`，不再混入全局米饭候选；菜单 facts 使用 `SCHEDULED_DISH_LIST`。
- 已完成 P1 首批主链路：`BusinessQuestionAnalysis` 已支持查询目标、交互模式、餐次范围和纠错描述；LLM 使用 system/user 消息分离并接收脱敏的上轮查询摘要；公共菜单纠错会比较 QueryPlan 指纹，未发生计划变化时改为澄清，避免重复相同查询。
- 已完成 P2 首批保护：菜单结果进入 `BusinessResultValidator`，餐次范围不一致或仅返回米饭类型时会产生受控告警，前端展示对应提示。
- 其余业务目标仍通过兼容分支运行，后续迁移须复用相同的受控语义、计划和结果校验框架。

## 17. 最终验收标准

- 输入“今天菜单”时，Agent 默认返回当日午餐、晚餐公共排期，并按餐次分组。
- 公共菜单没有排期时，返回“未配置”，不使用全局米饭候选填充。
- 菜单数量的 fact 不再显示为“订单数量 / ORDER_LIST”。
- “菜单查得不对，怎么全是米饭”被识别为 `CORRECTION`，不再无解释重复上轮相同查询和结果。
- 宽泛业务关键词不再直接产生 0.95 最终意图；否定、代词、省略、改查和复合问法进入 LLM 语义分析。
- LLM 输出只包含受控语义，无法绕过工具白名单、权限、数据范围、查询预算和结果校验。
- 单元测试、主系统内部接口测试、Agent 编排集成测试、前端卡片测试和自然语言评测集全部通过。
