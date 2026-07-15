# 智能客服 Agent 运营统计内部接口

## 1. 安全约束

接口仅供 `agent-service` 调用，前端不得直接访问。每个请求必须同时携带：

- `X-Agent-Internal-Token`：服务内部令牌。
- `X-Agent-Access-Context`：主系统签发的短期客服访问上下文。
- `X-Agent-Session-Id`、`X-Request-Id`：会话与链路标识。

主系统同时校验 Agent 入口权限和对应业务权限。非全量数据范围会先按“部门 -> 创建人 -> 客户”收缩授权客户集合，再计算聚合结果；接口只返回聚合数据，不返回客户明细、手机号、完整地址和金额。

## 2. 每日客户工作量

`POST /api/internal/agent/operations/daily-customers`

所需权限：`agentDiagnosis:list` + `mealPlan:list`。

请求：

```json
{"recordDate":"2026-07-13","mealType":"LUNCH","dimensions":["MEAL_TYPE","PACKAGE"]}
```

响应字段：`scheduledCustomerCount`、`verifiedCustomerCount`、`unverifiedCustomerCount`、`expectedCustomerCount`、`unscheduledCustomerCount`、`mealPlanFailureCount`、`mealTypeBreakdown`、`metricMealTypeBreakdown`、`breakdownDimensions`、`metricDimensionBreakdown`、`metricDefinitionId`、`metricVersion`、`timezone`、`queriedAt`、`truncated`。

`expectedCustomerCount` 复用排餐生成前的订单有效性、开始餐次、配送模式、人工新增、客户排除日期和餐数池上限过滤；`unscheduledCustomerCount` 以客户+餐次为键，从应服务集合中扣除成功排餐集合。

`dimensions` 只接受 QueryPlan 传入的 `MEAL_TYPE`、`PACKAGE`、`CUSTOMER_SOURCE`，最多两个。主系统会再次校验白名单；未知维度不会降级为自由字段查询。

前端使用 `breakdownDimensions` 与 `metricDimensionBreakdown` 展示实际分组。后者以指标枚举名为第一层键、维度组合展示值为第二层键；每个指标独立聚合，禁止将已排餐分组复用于待核销、应服务或待排餐指标。已排餐、已核销和待核销按客户去重；应服务和待排餐按客户+餐次去重。一个客户可能对应多个套餐或来源归属时，分组之和不承诺等于跨维总数。响应始终只包含计数，不包含客户、订单或排餐明细。

## 3. 客户档案总数

`POST /api/internal/agent/operations/customer-profiles/count`

所需权限：`agentDiagnosis:list` + `customerProfile:list`。返回当前客服授权数据范围内已录入的客户档案数量，不附加业务日期，也不返回客户明细。

## 4. 活跃客户数

`POST /api/internal/agent/operations/active-customers`

所需权限：`agentDiagnosis:list` + `customerOrder:list`。返回存在进行中且剩余餐数大于零的客户去重数。

活跃客户接口当前只返回总数，不支持套餐或客户来源分组。

## 5. 即将到期订单数

`POST /api/internal/agent/operations/expiring-orders`

所需权限：`agentDiagnosis:list` + `customerOrder:list`。

请求可传 `startDate` 与 `endDate`，两者格式均为 `yyyy-MM-dd`，范围不得超过 31 天；省略时默认当天起 7 天。

响应统一包含 `metricCode`、`total`、`metricDefinitionId`、`metricVersion`、`timezone`、`queriedAt` 和 `truncated`。
