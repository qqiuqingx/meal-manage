# 智能客服 Agent 内部业务查询接口

## 1. 适用范围与安全边界

本组接口仅供 `agent-service` 回调主系统使用，全部为只读接口。不得向前端、客户本人或第三方直接开放。

- 所有接口同时校验 `X-Agent-Internal-Token`、`X-Agent-Access-Context`、`X-Agent-Session-Id` 和 `X-Request-Id`。
- 访问上下文由主系统根据当前客服签发，包含短期有效期、会话和请求绑定；Agent 只能原样透传，不能自行构造。
- 入口权限 `agentDiagnosis:list` 与各业务权限必须同时满足。
- 非管理员范围按签名上下文中的部门集合映射为可访问客户：部门内用户的 `customer_profile.create_by` 对应客户才能被读取。订单、排餐、核销和退餐均按关联客户先过滤；不允许先读取跨范围结果再在响应层隐藏。
- 主系统会向 Agent 下发本轮可用工具名，用于在编排前跳过无权限工具；该白名单不包含完整权限集合，也不替代内部接口的二次权限校验。
- 所有订单响应均不含订单金额、单价、优惠、已收、退款金额或餐费余额字段。
- Agent 单轮最多实际调用 6 个工具，并按 QueryPlan 中受控的分页、最近条数或菜品 ID 数量预留累计 100 条业务数据预算，且不会超过工具登记上限；预算耗尽时不再调用下游接口，返回 `TOOL_DATA_BUDGET_EXCEEDED` 部分失败告警。
- Agent 调用内部查询接口的连接和读取超时默认均为 3 秒，可通过 `AGENT_BUSINESS_QUERY_TIMEOUT_MS` 配置为 100 至 10000 毫秒；超时返回 `TOOL_TIMEOUT`，网络不可用返回 `TOOL_UNAVAILABLE`，均不透传底层连接信息。
- 所有异常均返回 `{code, message}` 稳定错误体：参数校验失败为 `AGENT_QUERY_REQUEST_VALIDATION_FAILED` 或 `AGENT_QUERY_INVALID_REQUEST`，对象不存在为 `AGENT_QUERY_NOT_FOUND`，身份或权限拒绝为 `AGENT_QUERY_UNAUTHORIZED` / `AGENT_QUERY_ACCESS_DENIED`，未知内部异常为 `AGENT_QUERY_INTERNAL_ERROR`。错误体不包含 SQL、堆栈、手机号、完整地址或原始下游响应。

## 2. 公共请求头

| Header | 必填 | 说明 |
| --- | --- | --- |
| `X-Agent-Internal-Token` | 是 | Agent 服务间身份凭证 |
| `X-Agent-Access-Context` | 是 | 主系统签发的 HMAC 短期客服访问上下文 |
| `X-Agent-Session-Id` | 是 | 当前客服会话 ID |
| `X-Request-Id` | 是 | 当前链路请求 ID |

## 3. 接口清单

### 3.1 解析客户

`POST /api/internal/agent/query/customer/resolve`

权限：`agentDiagnosis:list` + `customerProfile:list`

请求支持 `customerId`、`customerCode` 或 `customerName`。姓名模糊查询最多返回 10 个候选；多候选必须由客服选择，Agent 不得自行选择。

### 3.2 客户综合概览

`POST /api/internal/agent/query/customer/overview`

权限：`agentDiagnosis:list` + `customerProfile:list` + `customerOrder:list`

返回客户档案摘要、档案创建时间 `createTime`、首次购买时间 `firstPurchaseTime`、脱敏联系方式与地址、饮食限制、订单数、早餐/午晚餐余额、已签父/子套餐摘要及最近核销/退餐摘要。`firstPurchaseTime` 优先取首笔订单成交时间，缺失时回退订单创建时间；无订单时为空。套餐和历史摘要均不包含任何价格或金额。客户不存在时返回 `present=false`。

### 3.3 客户订单列表

订单列表和详情均返回非金额关联摘要：`verificationRecordCount`（有效核销记录数）、`refundRecordCount`（退餐记录数）、`mealPlanRecordCount`（有效排餐记录数）。三个字段均通过订单 ID 批量聚合，核销统计排除已软删除日志；它们只表示关联记录条数，明细仍需调用核销、退餐或排餐查询接口。

`POST /api/internal/agent/query/orders/list`

权限：`agentDiagnosis:list` + `customerOrder:list`

请求：`customerId`（必填）、`status`（可选）、`page`、`size`。单页最大 20 条，响应包含 `total`、`items`、`truncated`。

### 3.4 订单详情

`POST /api/internal/agent/query/orders/detail`

权限：`agentDiagnosis:list` + `customerOrder:list`

请求支持 `orderId` 或 `orderCode`，可携带 `customerId` 进行关系约束。订单不属于当前客户时返回 404，不泄露跨客户对象信息。

### 3.5 核销与退餐记录

`POST /api/internal/agent/query/verifications/list`

权限：`agentDiagnosis:list` + `mealPlan:list`。可按客户、订单、餐次及受控日期范围查询最近记录，默认 10 条、最大 50 条；日期范围最大 31 天；只统计未删除核销日志。

`POST /api/internal/agent/query/refunds/list`

权限：`agentDiagnosis:list` + `customerOrder:list` + `mealPlan:list`。支持最大 31 天日期范围；响应包含退早餐、退午晚餐和已核销不退餐数，但不包含退款金额。

### 3.6 排餐与菜品明细

`POST /api/internal/agent/query/meal-plans/list`

权限：`agentDiagnosis:list` + `mealPlan:list`。支持 `customerId + recordDate`、`customerId + startDate + endDate`（最多 31 天）或受控的 `customerMealPlanId`。跨客户范围查询使用 `recordDate + page + size`，`mealType` 可选：传入时仅允许 `BREAKFAST`、`LUNCH`、`DINNER` 并只查该餐次，省略时查询当天全部排餐餐次。范围查询只支持单日，`size` 最大 50，并在 SQL 条件中应用当前客服的数据范围。未绑定数据范围直接拒绝，不会按全量数据执行。

响应按“排餐主单 → 客户排餐记录 → 菜品明细”返回，包含 `customerCode`、使用订单/父子套餐 ID、生成时间、脱敏配送地址、人工新增命中标记、手工换菜数量和失败原因摘要；包含 `total`、`page`、`size`、`truncated` 和 `queriedAt`。菜品明细最多 30 条并通过 `dishesTruncated` 表示截断，且返回 `allergyFiltered`、`allergyReasons`、`replaceReason`、`originalDishId` 和 `originalDishName`。

只有 `allergyFiltered=true` 且 `replaceReason=ALLERGY` 才能解释为本次排餐实际命中过敏过滤；客户主动排除菜品、订单换菜和缺菜回退不得表述为客户过敏。当前表结构没有可审计的人工删除原因，因此不会将软删除记录推断为人工删除。

### 3.7 套餐规格

`POST /api/internal/agent/query/packages/detail`

权限：`agentDiagnosis:list` + `package:list`。请求 `parentPackageId`，返回真实 `parent_package_sub` 关联下的子套餐、荤素数量、米饭和汤规格；不返回价格或金额字段。

客服对话不会将用户输入直接作为 `parentPackageId` 调用本接口，而是仅使用客户综合概览中已签约套餐摘要的稳定 ID，单轮最多查询 5 个套餐规格。

### 3.8 业务规则解释

`POST /api/internal/agent/query/rules/explain`

权限：`agentDiagnosis:list`。`topic` 只能是白名单规则主题：`MEAL_BALANCE`、`ORDER_EFFECTIVE`、`MEAL_PLAN_MATCH`、`DIETARY_FILTER`、`VERIFICATION_REFUND_EFFECT`；返回规则 ID、版本、责任模块、业务依据文档相对路径和结构化说明，不读取任意文档路径，也不依赖模型常识。规则目录固定为 `config/agent-business-rules.json`，服务启动时校验规则 ID、主题、版本、责任模块、依据文档与展示内容；目录错误时规则不会注册。

### 3.9 菜品与配料摘要

`POST /api/internal/agent/query/dishes/list`

权限：`agentDiagnosis:list` + `dish:list`。请求必须提供最多 20 个已受控的 `dishIds`；响应返回菜品名称、类型、适用餐次、启用状态及最多 20 个配料名称。该接口不支持按任意字段搜索、自由排序或返回全量菜品。

### 3.10 客户候选菜预览

`POST /api/internal/agent/query/dishes/candidates`

权限：`agentDiagnosis:list` + `customerProfile:list` + `customerOrder:list` + `package:list` + `dish:list`。请求必须提供客户 ID、单日日期和午餐或晚餐。服务读取当日排期菜、固定米饭、客户当前进行中订单关联的父套餐、客户排除菜和三级过敏标签，最多返回 20 条候选菜，并标注 `PACKAGE_NOT_MATCHED`、`CUSTOMER_EXCLUDED_DISH`、`ALLERGY:<标签>` 等过滤摘要。该结果仅表示只读候选预览，不创建排餐记录，也不表示该餐次已生成或可直接配送。

### 3.11 公共排期菜单

`POST /api/internal/agent/query/dishes/scheduled`

权限：`agentDiagnosis:list` + `mealPlan:list` + `dish:list`。该接口只查询公共排期，不关联客户、订单、配送地址或客户实际排餐。

请求体：

```json
{
  "recordDate": "2026-07-13",
  "mealTypes": ["LUNCH", "DINNER"]
}
```

- `recordDate` 必填，格式为 `yyyy-MM-dd`；服务按 `ScheduleKeyUtil` 映射到排期周次和星期。
- `mealTypes` 必填，仅允许受控值 `LUNCH`、`DINNER`；未指定餐次的产品默认值由 Agent 固定为两者，禁止向单餐次 SQL 传入空餐次。
- 数据仅来自启用的 `meal_schedule_plan JOIN dish`；不会追加 `dish` 主档中的全局 `RICE_TYPE` 米饭候选。
- 响应为 `{recordDate, groups, total, truncated}`。`groups` 按午餐、晚餐稳定排序，每组包含 `mealTypeCode`、`mealTypeName`、`total` 和限量 `items`；无排期时仍返回对应的空分组。
- `total` 的事实含义为排期菜品数，单位为“道”，来源类型为 `SCHEDULED_DISH_LIST`，不得解释为订单数量。

## 4. 会话历史卡片恢复

业务查询完成后，主系统仅将前端恢复所需的受控卡片快照写入 `agent_chat_message.business_result_json`：响应类型、已脱敏的结构化结果、事实、告警、同轮缓存标记、部分结果标记、查询时间和 QueryPlan。不会保存内部工具原始响应、模型提示词、手机号明文、完整地址或任何金额字段。

已有数据库需先执行 `eladmin/sql/agent_chat_message_business_query_migration.sql`。回滚时确认不再需要恢复历史业务查询卡片后，按脚本注释删除该列即可。

## 5. 统一语义与业务时间约束

- LLM 只能选择 `AgentMetricCatalog` 登记的指标、领域、维度和相对时间枚举，不能指定本文件中的 URL、工具名、Java 结果字段或 SQL。
- 服务端按指标目录将语义编译为固定工具。`DAILY_UNSCHEDULED_CUSTOMER_COUNT` 只能调用 `getDailyCustomerWorkload`，回答只能读取 `unscheduledCustomerCount`，缺失该字段时返回受控错误，不能改读其他计数。
- 每日指标必须解析为单个 `recordDate`；`BusinessTemporalResolver` 使用 `Asia/Shanghai` 和可注入 `Clock` 处理 `CURRENT_DAY`、`PREVIOUS_DAY`、`NEXT_DAY`、`CURRENT_WEEK`。
- 主系统聊天会话保存 `pending_business_query_json` 与 `last_business_query_context_json`，每轮通过内部聊天请求/响应透传。纯日期、餐次或编号回复优先续接 Pending，避免实例切换后的业务目标漂移。
- 语义追踪只记录来源、稳定 fallback 原因、目录版本、时间枚举、解析日期和 Pending 复用标记。审计升级脚本为 `eladmin/sql/alter_agent_business_query_audit_semantic_trace.sql`。
