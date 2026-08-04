# 智能客服 Agent 指标口径字典

## 1. 适用范围

本字典约束 `queryBusinessMetrics` 工具可查询的只读聚合。指标由
`eladmin-system` 在客服签名数据范围内计算，模型只能选择登记的指标、日期和维度，不能自行汇总列表或构造 SQL。

当前版本：`2026.08`
时区：`Asia/Shanghai`

## 2. 指标枚举

| 指标代码 | 定义 | 输入口径 | 主要结果 |
|---|---|---|---|
| `CUSTOMER_PROFILE_COUNT` | 当前授权范围内的客户档案数量，包含尚未下单档案 | 不需要日期 | `total` |
| `ACTIVE_SERVICE_CUSTOMER_COUNT` | 存在进行中订单且早餐或午晚餐餐数池仍有余额的客户去重数 | 不需要日期；午餐和晚餐共享餐数池 | `total` |
| `ACTIVE_ORDER_COUNT` | 当前授权范围内状态为进行中的订单数 | 不需要日期；按订单计数 | `total` |
| `DAILY_SCHEDULED_CUSTOMER_COUNT` | 指定日期已成功生成有效排餐的客户去重数 | 必须提供 `recordDate`，可选餐次和最多两个维度 | `total`、`dimensions` |
| `DAILY_VERIFIED_CUSTOMER_COUNT` | 指定日期已核销排餐客户去重数 | 必须提供 `recordDate`，可选餐次和最多两个维度 | `total`、`dimensions` |
| `DAILY_UNVERIFIED_CUSTOMER_COUNT` | 指定日期已排餐但尚未核销客户去重数 | 必须提供 `recordDate`，可选餐次和最多两个维度 | `total`、`dimensions` |
| `DAILY_UNSCHEDULED_CUSTOMER_COUNT` | 指定日期按有效订单、餐次和排餐规则应服务但未成功生成排餐的客户+餐次数量 | 必须提供 `recordDate`，可选餐次和最多两个维度 | `total`、`dimensions` |
| `MEAL_PLAN_FAILURE_COUNT` | 指定日期排餐客户记录中的失败数量 | 必须提供 `recordDate`，可选餐次和最多两个维度 | `total`、`dimensions` |
| `EXPIRING_ORDER_COUNT` | 指定日期范围内到期的进行中订单数 | 必须提供 `startDate` 与 `endDate` | `total` |

`dimensions` 只能使用 `MEAL_TYPE`、`PACKAGE`、`CUSTOMER_SOURCE`，最多两个。每日指标未指定维度时返回按餐次的受控分组；客户档案、活跃服务客户和活跃订单总数当前不支持分组。

## 3. 业务边界

- 每个总数都在主系统完成权限和数据范围过滤后计算，不能先算全量再隐藏明细。
- 日期使用 `yyyy-MM-dd`。每日指标只接受 `recordDate`；到期订单只接受明确的 `startDate/endDate`。
- 早餐、午餐、晚餐的已核销统计遵循主系统餐次枚举；剩余餐数规则由 `explainBusinessRule(MEAL_BALANCE)` 解释，不由 Agent 自行推算。
- 运营指标响应只包含指标枚举、计数、受控分组、查询时间和稳定告警，不包含客户明细、手机号、地址、特殊要求、金额或底层异常。
- 没有明确“系统档案总数”“进行中订单数”“活跃客户数”口径时，模型应先澄清，不能把不同指标混为“客户数”。
- 空结果和数据范围未绑定不能回答为“系统中不存在该对象”；应保留主系统返回的告警或说明范围有限。

## 4. 工具调用要求

`queryBusinessMetrics` 的输入 Schema 只包含 `metric`、`recordDate`、`startDate`、`endDate`、`mealType` 和 `dimensions`。权限、部门范围、内部 Token、接口地址和排序字段不属于模型输入。

指标数字必须引用本轮成功工具事实。结果被截断、工具失败或模型无法确定日期时，回答必须明确部分性或请求补充条件；不得使用历史会话中的旧数字代替当前查询。
