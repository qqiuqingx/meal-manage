# 智能客服 Agent 统一内部业务查询接口

## 1. 适用范围与安全边界

本组接口只供 `agent-service` 回调 `eladmin-system` 使用，前端不得直连。普通业务查询和排餐诊断共用同一组 12 个只读工具，由模型通过 Tool Calling 自主选择；主系统负责身份、业务权限、客户数据范围、对象关系校验和 SQL 分页。

- 必须同时携带 `X-Agent-Internal-Token`、`X-Agent-Access-Context`、`X-Agent-Session-Id` 和 `X-Request-Id`。
- `X-Agent-Access-Context` 由主系统按当前客服签发并绑定会话/请求，Agent 不得自行构造。
- 除入口权限 `agentDiagnosis:list` 外，工具还必须满足表中的业务权限；权限矩阵不由模型决定。
- 主系统在 SQL 查询前应用部门数据范围和对象关系过滤，不能先查全量再在响应层过滤。
- DTO 和响应不提供订单金额、价格、优惠、退款金额、原始/完整手机号或原始/完整地址；允许出现的手机号/地址摘要必须已经由主系统脱敏。
- 内部关联 ID 只用于工具间关联和主系统二次校验；返回 Agent 前端的卡片会隐藏这些 ID。

## 2. 公共请求头

| Header | 必填 | 说明 |
| --- | --- | --- |
| `X-Agent-Internal-Token` | 是 | Agent 服务身份凭证 |
| `X-Agent-Access-Context` | 是 | 主系统签发的短期客服访问上下文 |
| `X-Agent-Session-Id` | 是 | 当前会话 ID |
| `X-Request-Id` | 是 | 请求链路 ID |

## 3. 工具与接口映射

| 工具名 | 主系统路径 | 业务权限 | 最大结果 |
| --- | --- | --- | ---: |
| `searchCustomerProfiles` | `POST /api/internal/agent/query/customer-profiles/search` | `customerProfile:list` | 20 |
| `searchServiceCustomers` | `POST /api/internal/agent/query/service-customers/search` | `customerOrder:list` | 20 |
| `getServiceCustomerDetail` | `POST /api/internal/agent/query/service-customers/detail` | `customerProfile:list` + `customerOrder:list` | 1 |
| `listMealPlans` | `POST /api/internal/agent/query/meal-plans/list` | `mealPlan:list` | 50 |
| `listVerifications` | `POST /api/internal/agent/query/verifications/list` | `mealVerification:list` | 50 |
| `listRefunds` | `POST /api/internal/agent/query/refunds/list` | `mealRefund:list` | 50 |
| `previewDishCandidates` | `POST /api/internal/agent/query/dishes/candidates` | 客户档案 + 订单 + 套餐 + 菜品 | 20 |
| `listScheduledDishes` | `POST /api/internal/agent/query/dishes/scheduled` | `mealPlan:list` + `dish:list` | 20 |
| `searchDishes` | `POST /api/internal/agent/query/dishes/search` | `dish:list` | 20 |
| `getPackageDetail` | `POST /api/internal/agent/query/packages/detail` | `package:list` | 5 |
| `queryBusinessMetrics` | `POST /api/internal/agent/query/metrics/query` | 按指标选择最小业务权限 | 100 |
| `explainBusinessRule` | `POST /api/internal/agent/query/rules/explain` | `agentDiagnosis:list` | 1 |

工具名称、输入类型、输出类型、权限、超时、结果上限和前端卡片类型由 `agent-service/src/main/java/me/zhengjie/agent/tool/ToolRegistry.java` 唯一登记。主系统只向 Agent 下发当前请求实际允许的工具名称。

## 4. 统一响应

列表和单对象接口共用以下响应信封：

```json
{
  "schemaVersion": "v1",
  "items": [],
  "total": 0,
  "page": 1,
  "size": 20,
  "truncated": false,
  "queriedAt": "2026-08-04T10:00:00+08:00",
  "data": null,
  "warnings": []
}
```

`items` 用于分页列表，`data` 用于详情或聚合结果。`truncated=true` 表示仍有结果未返回，不代表查询失败。错误使用稳定码：`AGENT_QUERY_REQUEST_VALIDATION_FAILED`、`AGENT_QUERY_INVALID_REQUEST`、`AGENT_QUERY_NOT_FOUND`、`AGENT_QUERY_UNAUTHORIZED`、`AGENT_QUERY_ACCESS_DENIED`、`AGENT_QUERY_INTERNAL_ERROR`。

## 5. 请求字段与业务口径

### 5.1 客户档案：`searchCustomerProfiles`

路径：`POST /api/internal/agent/query/customer-profiles/search`。

请求字段：`customerId`、`customerCode`、`customerName`、`hasOrder`、`page`、`size`。可查询尚未下单客户；在已认证并完成权限/数据范围校验的内部 Agent 链路中，响应使用 `customerCode + customerName`，手机号仅以 `maskedPhone` 等脱敏摘要出现。本接口只返回客户档案摘要，不包含订单成交/创建时间，不用于回答客户下单时间；新响应不使用 `maskedName`。可选字段未使用时省略或传 `null`，ID 不得传 `0`，`page` 从 1 开始，`size` 范围为 1-20。

### 5.2 服务客户：`searchServiceCustomers`

路径：`POST /api/internal/agent/query/service-customers/search`。

请求字段：`customerId`、`customerCode`、`orderId`、`orderCode`、`status`、`dealTimeFrom`、`dealTimeTo`、`packageCode`、`page`、`size`。一笔订单一行，不合并同一客户的多笔订单；状态为 `ALL`、`ACTIVE`、`CANCELLED`、`COMPLETED` 或 `REFUNDED`。用户询问“现在/当前/服务中的客户”或“分别什么时候下单”时使用 `status=ACTIVE`。在授权和数据范围过滤完成后，每行可受控返回 `customerCode`、完整 `customerName`、`orderCode`、`dealTime`、`createTime`、`orderTime`、状态和套餐摘要。`orderTime` 优先使用成交时间，缺失时回退创建时间，不由模型或前端选择。可选字段未使用时省略或传 `null`，ID 不得传 `0`，日期只能使用 `yyyy-MM-dd`，`page` 从 1 开始，`size` 范围为 1-20；`truncated=true` 时继续查询下一页。

服务客户结果的安全示例（字段顺序也是结构化展示的固定列顺序）：

```json
{
  "items": [
    {
      "customerCode": "C10001",
      "customerName": "示例客户",
      "orderCode": "ORD20260805001",
      "dealTime": "2026-08-05T09:00:00+08:00",
      "createTime": "2026-08-05T08:55:00+08:00",
      "orderTime": "2026-08-05T09:00:00+08:00",
      "status": "进行中",
      "parentPackageName": "示例套餐"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 20,
  "truncated": false,
  "warnings": []
}
```

### 5.3 服务客户详情：`getServiceCustomerDetail`

路径：`POST /api/internal/agent/query/service-customers/detail`。

请求至少提供 `customerId`、`customerCode`、`orderId`、`orderCode` 之一，可选 `detailLevel=STANDARD|DIAGNOSTIC`。返回客户档案、订单、餐数池、套餐及限长的排餐/核销/退餐摘要；跨数据范围对象按不存在处理。

### 5.4 排餐：`listMealPlans`

路径：`POST /api/internal/agent/query/meal-plans/list`。

请求字段：客户/订单编号或 ID、`recordDate`、`startDate`、`endDate`、`mealType`、`page`、`size`。支持 `BREAKFAST`、`LUNCH`、`DINNER`；日期和分页在主系统再次校验。排餐不消耗餐数，餐数只由有效核销实时扣减。

### 5.5 核销和退餐：`listVerifications`、`listRefunds`

路径分别为：

- `POST /api/internal/agent/query/verifications/list`
- `POST /api/internal/agent/query/refunds/list`

请求字段为客户/订单编号或 ID、日期范围、餐次（核销）、`page`、`size`。具体客户或订单历史可以不带日期；广域查询必须提供 `startDate` 和 `endDate`，跨度最多 31 天。只统计未删除核销日志；退餐响应只返回餐数、状态、时间和限长原因摘要，不返回退款金额。早餐扣早餐池，午餐和晚餐共同扣午晚餐池。

### 5.6 候选菜和公共菜单

`previewDishCandidates` 路径为 `POST /api/internal/agent/query/dishes/candidates`，必须提供客户/订单身份、`recordDate` 和 `mealType=LUNCH|DINNER`。提供订单身份时，主系统先校验客户—订单关系，并仅使用该服务客户订单的有效套餐；关系不成立按 `SERVICE_CUSTOMER_NOT_FOUND` 处理。它只读返回套餐、过敏、忌口等过滤原因，不创建排餐。

`listScheduledDishes` 路径为 `POST /api/internal/agent/query/dishes/scheduled`，必须提供 `recordDate` 和非空 `mealTypes`，每项只能是 `LUNCH` 或 `DINNER`。它只查询公共排期，不代表某个客户实际排餐。

### 5.7 菜品：`searchDishes`

路径：`POST /api/internal/agent/query/dishes/search`。

请求字段：`name`、`dishType`、`enabled`、`page`、`size`。`dishType` 只能使用 `MAIN`、`SIDE`、`SOUP`、`VEGETABLE`、`RICE`、`RICE_TYPE`，不接受任意排序、字段选择或 SQL。

### 5.8 套餐：`getPackageDetail`

路径：`POST /api/internal/agent/query/packages/detail`。

请求至少提供 `packageId` 或 `packageCode`。返回父套餐、子套餐和餐品规格，不返回价格或其他金额字段。`packageId` 只能来自已授权业务事实或受控 ID，不构成主系统授权依据。

### 5.9 运营指标：`queryBusinessMetrics`

路径：`POST /api/internal/agent/query/metrics/query`。

`metric` 只能使用：`CUSTOMER_PROFILE_COUNT`、`ACTIVE_SERVICE_CUSTOMER_COUNT`、`ACTIVE_ORDER_COUNT`、`DAILY_SCHEDULED_CUSTOMER_COUNT`、`DAILY_VERIFIED_CUSTOMER_COUNT`、`DAILY_UNVERIFIED_CUSTOMER_COUNT`、`DAILY_UNSCHEDULED_CUSTOMER_COUNT`、`MEAL_PLAN_FAILURE_COUNT`、`EXPIRING_ORDER_COUNT`。`dimensions` 最多两个，只能使用 `MEAL_TYPE`、`PACKAGE`、`CUSTOMER_SOURCE`。日期使用 `recordDate` 或 `startDate/endDate`，不能提交任意字段或排序。

主系统按指标选择最小业务权限；只返回计数和受控维度聚合，不返回客户明细、订单明细或金额。`data.breakdown` 是主系统从受控 `dimensions` 按原顺序确定性生成的 `[{"label":"分组","value":数量}]`，仅用于展示，不改变指标总数和维度口径。具体口径见 `eladmin/doc/business/智能客服Agent指标口径字典.md`。

当 `truncated=true`、结果告警非空或聚合结构不完整时，Agent 不从当前页重新聚合；前端不显示图表，只显示已有摘要/表格和告警。完整且有非空 `breakdown` 的当前指标卡使用摘要、表格和柱状图视图。

### 5.10 规则：`explainBusinessRule`

路径：`POST /api/internal/agent/query/rules/explain`。

请求 `topic` 只能使用规则登记主题：`MEAL_BALANCE`、`ORDER_EFFECTIVE`、`MEAL_PLAN_MATCH`、`DIETARY_FILTER`、`VERIFICATION_REFUND_EFFECT`。主系统只返回版本化规则摘要，不接受任意文档路径、规则文本或 SQL。

## 6. Agent 聊天与会话恢复

服务间聊天唯一入口为 `POST /api/agent/v2/chat`，契约文件为 `agent-service/src/main/resources/openapi/agent-service-v2.yaml`。主系统向 Agent 下发会话范围摘要、最近工具摘要、可用工具和 `sessionVersion`；Agent 回传文本、`cards`、`presentations`、`facts`、`warnings`、`partial`、`toolTraceSummary` 和 `conversationPatch`。主系统以版本条件提交会话 Patch。

聊天响应不再使用固定业务 `responseType`、Java 查询计划或关键词路由。业务卡片类型由成功工具的 `ToolRegistry.cardType` 确定；工具结果是事实来源，模型无法自行构造业务数字。Agent 服务再按 `cardType` 生成 `presentations`：已知卡片优先使用 `SYSTEM` 规则，未知卡片才使用不含业务值的独立 LLM 规划，并经路径、字段、视图、数量和敏感字段校验；失败时返回通用安全降级和 `PRESENTATION_FALLBACK_APPLIED`，不影响 cards 或业务告警。

`presentations` 只包含 `schemaVersion=v1`、`sourceToolCallId`、`cardType`、`decisionSource`、`title`、`layout=TABS`、固定视图、字段路径和格式。视图白名单为 `TEXT/TABLE/BAR/LINE/PIE`，表格每处最多 20 列、详情 sections 最多 8 个、图表最多 4 个指标；它不包含业务值、HTML、Markdown、表达式、组件名或 ECharts option。

新会话将 `cards + presentations` 一起写入 `business_result_json` 并直接恢复；旧 cards-only 快照由前端只读兼容层处理，旧 `maskedName` 保持原值，不重新查询、不补全姓名、不调用 LLM。

每轮最多 6 次工具调用、4 个模型回合、100 条业务记录，单个主系统请求默认超时 3 秒，最多自动修复最终回答 1 次。相同工具和规范化参数命中同轮缓存。

## 7. 变更记录

- 2026-08-04：删除旧固定业务查询/诊断内部 Controller，统一为 12 工具和 `/api/internal/agent/query/**`。
- 2026-08-04：核销、退餐改用独立 `mealVerification:list`、`mealRefund:list` 权限；需执行权限菜单补充脚本并为业务角色重新授权。
- 2026-08-06：内部 Agent 客户结果使用受控 `customerName` 和统一 `orderTime`；聊天响应增加 `cards + presentations` 展示快照，并补充完整性告警、历史兼容和安全边界说明。
