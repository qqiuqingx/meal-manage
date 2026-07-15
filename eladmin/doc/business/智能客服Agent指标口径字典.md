# 智能客服 Agent 指标口径字典

## 1. 适用范围

本字典约束智能客服助手的跨客户只读统计。所有指标由 `eladmin-system` 业务服务计算，模型只可选择已登记的指标和维度，不能自行汇总业务列表。

当前版本：`2026.07`
时区：`Asia/Shanghai`

## 2. 指标

| 指标代码 | 名称 | 定义 | 默认时间 | 结果字段 | 去重键 | 支持维度 |
|---|---|---|---|---|---|---|
| `DAILY_SCHEDULED_CUSTOMER_COUNT` | 当日已排餐客户数 | 指定日期有效排餐客户去重数 | `CURRENT_DAY` | `scheduledCustomerCount` | `customerId` | 日期、餐次、套餐、客户来源 |
| `DAILY_VERIFIED_CUSTOMER_COUNT` | 当日已核销客户数 | 指定日期已核销排餐客户去重数 | `CURRENT_DAY` | `verifiedCustomerCount` | `customerId` | 日期、餐次、套餐、客户来源 |
| `DAILY_UNVERIFIED_CUSTOMER_COUNT` | 当日待核销客户数 | 指定日期已排餐且未核销客户去重数 | `CURRENT_DAY` | `unverifiedCustomerCount` | `customerId` | 日期、餐次、套餐、客户来源 |
| `DAILY_EXPECTED_CUSTOMER_COUNT` | 当日应服务客户数 | 复用排餐生成前的有效订单、开始餐次、配送模式、人工新增、排除日期和餐数池上限过滤后，按客户+餐次去重 | `CURRENT_DAY` | `expectedCustomerCount` | `customerId + mealType` | 日期、餐次、套餐、客户来源 |
| `DAILY_UNSCHEDULED_CUSTOMER_COUNT` | 当日待排餐客户数 | 应服务客户+餐次减去成功生成有效排餐客户+餐次 | `CURRENT_DAY` | `unscheduledCustomerCount` | `customerId + mealType` | 日期、餐次、套餐、客户来源 |
| `MEAL_PLAN_FAILURE_COUNT` | 排餐失败数 | 指定日期和餐次生成失败的客户排餐记录数 | `CURRENT_DAY` | `mealPlanFailureCount` | `meal_plan_customer.id` | 日期、餐次、套餐 |
| `CUSTOMER_PROFILE_COUNT` | 客户档案总数 | 当前客服授权数据范围内已录入系统的客户档案数量 | `NONE` | `total` | `customer_profile.id` | 无 |
| `ACTIVE_CUSTOMER_COUNT` | 活跃客户数 | 存在进行中且按订单餐数与未删除核销实时计算后仍有任一餐数池余额的客户去重数 | `NONE` | `total` | `customerId` | 无 |
| `EXPIRING_ORDER_COUNT` | 即将到期订单数 | 指定日期范围内到期的进行中订单数 | `REQUIRE_EXPLICIT` | `total` | `customer_order.id` | 套餐、客户来源 |
| `VERIFICATION_COUNT` | 核销次数 | 未删除核销记录数 | `REQUIRE_EXPLICIT` | `total` | `meal_verification_log.id` | 日期、餐次、核销状态 |
| `REFUND_COUNT` | 退餐次数 | 退餐记录数 | `REQUIRE_EXPLICIT` | `total` | `meal_refund_log.id` | 日期、餐次 |

## 3. 安全和边界

- 默认单日查询；受控自然语言报表最长 31 天，最多 3 个指标、2 个分组维度和 100 个分组结果。
- 当前已上线的自然语言报表仅组合每日客户工作量同源指标；每项指标各自生成事实引用，数值直接来自主系统聚合结果。支持 `MEAL_TYPE`、`PACKAGE`、`CUSTOMER_SOURCE` 中最多两个维度的组合分组，响应按每个指标分别返回计数，不能复用其他指标分组。套餐和来源取自通过排餐资格过滤的订单及已生成排餐关联订单；应服务和待排餐仍按客户+餐次去重，因此一个客户有多个有效订单归属时，分组之和不承诺等于跨维总数。跨数据源组合仍不自动开放。
- 总数必须在授权数据集上计算，不能先计算全量再隐藏明细。非全量范围使用签名上下文的部门集合，经部门内用户和 `customer_profile.create_by` 映射为客户集合后参与统计。
- 聚合响应不包含手机号、完整地址、金额、客户明细或底层异常。
- “系统中有多少客户”“客户档案总数”等明确表达使用 `CUSTOMER_PROFILE_COUNT`；“还有多少客户”“剩下的客户有多少”“没完成的客户还有几个”等未说明系统档案或待办口径的问法，必须澄清客户档案总数、待排餐、待配送或待核销。
- 未明确提出“按套餐”“按来源”等分组要求时不得自动增加维度；活跃客户与客户档案总数当前仅支持总数查询。
