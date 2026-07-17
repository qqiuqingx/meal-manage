# 智能排查 Agent 客户信息查询能力实施计划

## 1. 背景与目标

当前智能排查 Agent 的能力按“排查排餐计划为什么没生成”设计，主流程强依赖三个槽位：客户、排餐日期、餐次。因此当客服输入 `B3303 这个客户还剩多少餐数`、`B3303 核销了多少餐` 这类问题时，系统仍会按排餐诊断处理，并继续追问日期和餐次。

本次改造目标是新增一条独立的“客户信息查询 / 客户餐数问答”能力，让 Agent 能直接回答客户维度的确定性业务数据问题。

目标示例：

```text
B3303 这个客户还剩多少餐数
B3303 核销了多少餐
B3303 有哪些进行中订单
B3303 最近核销记录
B3303 午晚餐还剩多少
```

## 2. 设计原则

1. 不把客户信息查询硬塞进现有排餐诊断链路。
2. 排餐诊断仍要求客户、日期、餐次。
3. 客户信息查询只强制要求客户编号或客户 ID，日期和餐次作为可选过滤条件。
4. 餐数、核销、订单状态属于确定性统计，应由服务端聚合计算，不依赖模型自由推理。
5. Agent 仍保持只读边界，不直接修改客户、订单、核销或排餐数据。
6. 所有返回口径必须能追溯到订单表和核销日志表。

## 3. 当前现状

### 3.1 现有 Agent 聊天链路

主要代码位置：

- `agent-service/src/main/java/me/zhengjie/agent/chat/RuleBasedMealPlanChatExtractor.java`
- `agent-service/src/main/java/me/zhengjie/agent/chat/MealPlanChatServiceImpl.java`
- `agent-service/src/main/java/me/zhengjie/agent/domain/chat/ChatIntent.java`

当前 `ChatIntent` 只有：

```java
DIAGNOSE,
FOLLOW_UP,
RETRY,
RESET,
OUT_OF_SCOPE
```

`RuleBasedMealPlanChatExtractor` 能识别 `B3303` 这类客户编号，但后续 `missingSlots()` 会继续要求 `recordDate` 和 `mealType`，导致客户餐数查询无法直接回答。

### 3.2 现有可复用内部工具

主系统已有内部只读工具接口：

- `POST /api/internal/agent/customer-profile`
- `POST /api/internal/agent/customer-orders`
- `POST /api/internal/agent/order-meal-balance`
- `POST /api/internal/agent/verification-logs`

相关代码：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/InternalAgentDiagnosisContextController.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisContextServiceImpl.java`
- `agent-service/src/main/java/me/zhengjie/agent/client/HttpDiagnosisToolDataClient.java`
- `agent-service/src/main/java/me/zhengjie/agent/tool/AgentToolRegistry.java`

这些接口目前定位为“排餐诊断证据工具”，可以复用部分查询逻辑，但不建议直接作为最终客户问答 API，因为返回结构偏工具证据，不适合前端稳定展示。

## 4. 目标能力范围

### 4.1 一期必须支持

| 用户问题 | 目标意图 | 必填槽位 | 可选槽位 |
| --- | --- | --- | --- |
| `B3303 还剩多少餐` | 客户餐数余额查询 | 客户 | 餐次 |
| `B3303 早餐还剩多少` | 客户餐数余额查询 | 客户 | 餐次=BREAKFAST |
| `B3303 午晚餐还剩多少` | 客户餐数余额查询 | 客户 | 餐次=LUNCH_DINNER |
| `B3303 核销了多少餐` | 客户核销统计查询 | 客户 | 日期范围、餐次 |
| `B3303 最近核销记录` | 客户核销记录查询 | 客户 | 日期范围、餐次、条数 |
| `B3303 有哪些订单` | 客户订单查询 | 客户 | 订单状态 |

### 4.2 一期暂不支持

1. 不支持跨客户统计，例如“今天一共核销多少餐”。
2. 不支持写操作，例如“帮 B3303 重算餐数”。
3. 不支持模型自行生成 SQL。
4. 不支持绕过内部 token 直接访问主系统数据。

## 5. 后端数据口径

### 5.1 剩余餐数口径

客户维度汇总所有有效订单。

有效订单建议沿用客户档案业务口径：

```text
status = 1 且 remaining_count > 0
```

如需要展示历史总购餐数和累计核销数，可以同时返回全部订单统计，但默认回答“还剩多少”时只使用有效订单。

计算公式：

```text
剩余早餐数 = 早餐总数 - BREAKFAST 已核销数
剩余午晚餐数 = 午晚餐总数 - LUNCH 已核销数 - DINNER 已核销数
合计剩余餐数 = 剩余早餐数 + 剩余午晚餐数
```

其中：

- 早餐总数来自 `customer_order.breakfast_count`
- 午晚餐总数来自 `customer_order.lunch_dinner_count`
- 已核销数来自 `meal_verification_log.meal_type`
- 已软删除核销日志不计入有效核销
- 已退餐日志是否计入需显式返回说明，建议默认不计入有效核销，并在返回中单独展示退餐影响

### 5.2 核销统计口径

累计核销按未删除核销日志统计：

```text
已核销早餐 = SUM(meal_type = BREAKFAST 的 verification_count)
已核销午餐 = SUM(meal_type = LUNCH 的 verification_count)
已核销晚餐 = SUM(meal_type = DINNER 的 verification_count)
已核销午晚餐 = 已核销午餐 + 已核销晚餐
累计已核销 = 已核销早餐 + 已核销午餐 + 已核销晚餐
```

如果用户指定日期范围，只统计范围内的核销日志。

如果用户指定餐次，只返回该餐次统计。

## 6. 后端改造计划

### 6.1 新增 DTO

建议新增包：

```text
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/insight/
```

新增 DTO：

- `AgentCustomerInsightRequest`
- `AgentCustomerMealSummaryResponse`
- `AgentCustomerVerificationSummaryResponse`
- `AgentCustomerOrderSummaryResponse`
- `AgentCustomerOrderMealBalanceItem`
- `AgentCustomerVerificationLogItem`

`AgentCustomerInsightRequest` 字段建议：

```java
private Long customerId;
private String customerCode;
private String recordDateStart;
private String recordDateEnd;
private String mealType;
private Integer orderStatus;
private Integer recentLimit;
```

### 6.2 新增聚合服务

新增接口：

```text
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentCustomerInsightService.java
```

新增实现：

```text
eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentCustomerInsightServiceImpl.java
```

服务方法：

```java
AgentCustomerMealSummaryResponse getMealSummary(AgentCustomerInsightRequest request);

AgentCustomerVerificationSummaryResponse getVerificationSummary(AgentCustomerInsightRequest request);

AgentCustomerOrderSummaryResponse getOrderSummary(AgentCustomerInsightRequest request);
```

实现要求：

1. 先通过 `customerId` 或 `customerCode` 解析客户档案。
2. 客户不存在时返回 `present=false`，不要抛业务异常中断 Agent。
3. 查询客户订单列表，区分有效订单和全部订单。
4. 查询订单关联核销日志，按早餐、午餐、晚餐分别聚合。
5. 返回每笔订单明细，方便前端展示和客服核对。
6. 日志只记录客户编号、客户 ID、结果条数、耗时，不记录手机号等敏感信息。

### 6.3 新增内部接口

在 `InternalAgentDiagnosisContextController` 新增：

```text
POST /api/internal/agent/customer-insight/meal-summary
POST /api/internal/agent/customer-insight/verification-summary
POST /api/internal/agent/customer-insight/order-summary
```

接口要求：

1. 继续使用 `X-Agent-Internal-Token` 鉴权。
2. 继续透传 `X-Request-Id`。
3. 只允许内部 Agent 调用，不对普通前端开放。
4. 返回结构稳定，避免直接返回数据库实体。

## 7. agent-service 改造计划

### 7.1 扩展意图枚举

修改 `ChatIntent`：

```java
CUSTOMER_INFO_QUERY,
CUSTOMER_MEAL_BALANCE_QUERY,
CUSTOMER_VERIFICATION_QUERY,
CUSTOMER_ORDER_QUERY
```

### 7.2 扩展规则抽取器

修改 `RuleBasedMealPlanChatExtractor`，新增关键词识别：

餐数余额类关键词：

```text
还剩多少、剩多少、剩余多少、剩余餐数、餐数余额、还能吃几餐
```

核销类关键词：

```text
核销了多少、已核销、核销记录、最近核销、用了多少餐、消耗多少餐
```

订单类关键词：

```text
有哪些订单、订单情况、进行中订单、订单状态、买了什么套餐
```

识别优先级建议：

1. reset / retry 优先。
2. 明确排餐诊断表达优先，例如“为什么没排出来”“没生成排餐”。
3. 客户餐数、核销、订单查询其次。
4. 普通追问最后。

### 7.3 按意图计算缺失槽位

当前 `missingSlots()` 固定要求日期和餐次，需要调整为：

```text
DIAGNOSE: CUSTOMER + RECORD_DATE + MEAL_TYPE
CUSTOMER_MEAL_BALANCE_QUERY: CUSTOMER
CUSTOMER_VERIFICATION_QUERY: CUSTOMER
CUSTOMER_ORDER_QUERY: CUSTOMER
CUSTOMER_INFO_QUERY: CUSTOMER
```

如果会话已有客户编号，用户输入“还剩多少餐”时应复用上下文客户，不再追问客户。

### 7.4 新增客户信息查询编排服务

新增：

```text
agent-service/src/main/java/me/zhengjie/agent/chat/CustomerInsightChatService.java
agent-service/src/main/java/me/zhengjie/agent/chat/CustomerInsightChatServiceImpl.java
```

职责：

1. 接收用户意图和槽位。
2. 调用主系统内部聚合接口。
3. 生成稳定的自然语言回答。
4. 返回结构化查询结果给前端。

不建议一期使用模型组织答案，避免餐数统计被模型改写或误算。

### 7.5 扩展工具数据客户端

修改：

- `DiagnosisToolDataClient`
- `HttpDiagnosisToolDataClient`

新增方法：

```java
Map<String, Object> getCustomerMealSummary(DiagnosisToolCustomerInsightRequest request);

Map<String, Object> getCustomerVerificationSummary(DiagnosisToolCustomerInsightRequest request);

Map<String, Object> getCustomerOrderSummary(DiagnosisToolCustomerInsightRequest request);
```

虽然一期不依赖模型 tool calling，也建议通过统一客户端调用，便于链路日志、内部 token 和 requestId 复用。

### 7.6 扩展聊天响应结构

当前 `AgentChatResponse` 主要面向 `diagnosisResult`。建议新增：

```java
private String responseType;
private Map<String, Object> insightResult;
```

`responseType` 可选：

```text
MEAL_PLAN_DIAGNOSIS
CUSTOMER_MEAL_SUMMARY
CUSTOMER_VERIFICATION_SUMMARY
CUSTOMER_ORDER_SUMMARY
CUSTOMER_INFO
```

## 8. 前端改造计划

### 8.1 工作台展示逻辑

修改：

```text
eladmin-web/src/views/agent/diagnosis/index.vue
```

新增根据 `responseType` 渲染不同卡片：

1. `CUSTOMER_MEAL_SUMMARY`：展示客户餐数余额卡。
2. `CUSTOMER_VERIFICATION_SUMMARY`：展示核销统计卡和最近核销记录。
3. `CUSTOMER_ORDER_SUMMARY`：展示订单列表卡。
4. `MEAL_PLAN_DIAGNOSIS`：保持原诊断结果展示。

### 8.2 餐数余额卡字段

建议展示：

- 客户编号
- 客户姓名
- 有效订单数
- 剩余早餐
- 剩余午晚餐
- 合计剩余餐数
- 已核销早餐
- 已核销午餐
- 已核销晚餐
- 订单明细表

### 8.3 核销统计卡字段

建议展示：

- 累计核销早餐
- 累计核销午餐
- 累计核销晚餐
- 累计核销合计
- 日期范围
- 最近核销记录

## 9. 回复模板

### 9.1 剩余餐数

```text
B3303 当前有效订单共 2 笔。剩余早餐 3 餐，剩余午晚餐 18 餐，合计剩余 21 餐。已核销早餐 7 餐，午餐 12 餐，晚餐 10 餐。数据按未删除核销日志实时汇总。
```

### 9.2 核销统计

```text
B3303 累计已核销 29 餐，其中早餐 7 餐、午餐 12 餐、晚餐 10 餐。最近 10 条核销记录已列在下方。
```

### 9.3 客户不存在

```text
未找到客户编号 B3303，请确认编号是否正确。
```

### 9.4 无有效订单

```text
B3303 当前没有有效进行中订单，因此没有可继续核销的剩余餐数。历史订单和核销记录已列在下方供核对。
```

## 10. 测试计划

### 10.1 agent-service 单元测试

修改或新增：

- `RuleBasedMealPlanChatExtractorTest`
- `MealPlanChatServiceImplTest`
- `CustomerInsightChatServiceImplTest`

测试用例：

1. `B3303 这个客户还剩多少餐数` 识别为 `CUSTOMER_MEAL_BALANCE_QUERY`。
2. `B3303 核销了多少餐` 识别为 `CUSTOMER_VERIFICATION_QUERY`。
3. `B3303 有哪些订单` 识别为 `CUSTOMER_ORDER_QUERY`。
4. `B3303 今天午餐为什么没排出来` 仍识别为 `DIAGNOSE`。
5. `还剩多少餐` 在会话已有客户时复用客户编号。
6. `还剩多少餐` 在会话无客户时只追问客户，不追问日期和餐次。
7. 客户不存在时返回稳定提示，不 fallback 到排餐诊断。

### 10.2 eladmin-system 单元测试

新增：

```text
eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/service/impl/AgentCustomerInsightServiceImplTest.java
```

测试用例：

1. 多笔有效订单餐数汇总正确。
2. 早餐和午晚餐餐数池分开计算。
3. `LUNCH` 和 `DINNER` 同时扣减午晚餐池。
4. 已删除核销日志不计入统计。
5. 客户不存在返回 `present=false`。
6. 无有效订单时返回 0 剩余并保留历史订单摘要。

### 10.3 接口测试

验证内部接口：

1. 无 `X-Agent-Internal-Token` 返回 403。
2. token 错误返回 403。
3. token 正确返回客户汇总数据。
4. 日志不输出手机号和完整响应体。

## 11. 文档同步

需要更新：

- `eladmin/doc/business/智能排查助手业务说明.md`
- `eladmin/doc/apidoc/智能排查助手接口文档.md`

更新内容：

1. 新增“客户信息查询能力”章节。
2. 明确排餐诊断与客户查询的槽位差异。
3. 明确餐数余额和核销统计口径。
4. 明确 Agent 只读边界。
5. 补充前端响应结构和示例。

## 12. 分期实施建议

### 一期：确定性查询闭环

目标：让 Agent 能直接回答客户剩余餐数、核销数量、订单列表。

交付内容：

1. 意图识别扩展。
2. 客户餐数聚合服务。
3. 客户核销聚合服务。
4. 客户订单聚合服务。
5. 聊天编排接入。
6. 前端基础卡片展示。
7. 单测和接口文档。

验收标准：

```text
输入：B3303 这个客户还剩多少餐数
结果：不追问日期和餐次，直接返回客户维度剩余餐数。
```

```text
输入：B3303 核销了多少餐
结果：不追问日期和餐次，直接返回累计核销统计和最近核销记录。
```

```text
输入：B3303 今天午餐为什么没排出来
结果：仍进入排餐诊断链路。
```

### 二期：自然语言增强

目标：支持更多查询表达和过滤条件。

可扩展能力：

1. 最近一周核销多少餐。
2. 今天核销了多少午餐。
3. 哪笔订单快吃完了。
4. 当前有效订单从什么时候开始、什么时候结束。
5. 客户套餐和特殊要求摘要。

### 三期：运营分析与审计

目标：沉淀客服查询行为，识别高频问题。

可扩展能力：

1. 统计客户餐数查询次数。
2. 统计客户核销查询次数。
3. 查询失败原因分布。
4. 客服常用问题模板。

## 13. 风险与注意事项

1. 餐数余额不要同时混用订单表 `remaining_count` 和核销日志推导值作为最终口径，必须选定一个主口径并在返回中说明。
2. 如果现有订单表 `remaining_count` 曾经出现历史脏数据，应优先以核销日志推导值作为 Agent 展示口径，并提供“订单表当前值”用于核对。
3. 多笔进行中订单需要明确汇总和明细，避免客服误以为只有一笔订单。
4. 前端不要把查询结果伪装成诊断结果，避免显示无意义的原因码和动作草稿。
5. 客户手机号、地址等敏感字段不要默认传给模型，也不要写入 Agent 链路日志。
