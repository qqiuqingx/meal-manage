# 智能客服 Agent 运营指标查询口径

运营指标已合并为统一工具 `queryBusinessMetrics`，主系统唯一入口为：

`POST /api/internal/agent/query/metrics/query`

权限由指标选择：

- `CUSTOMER_PROFILE_COUNT`：`agentDiagnosis:list` + `customerProfile:list`
- `ACTIVE_SERVICE_CUSTOMER_COUNT`、`ACTIVE_ORDER_COUNT`、`EXPIRING_ORDER_COUNT`：`agentDiagnosis:list` + `customerOrder:list`
- 其他排餐/核销相关指标：`agentDiagnosis:list` + `mealPlan:list`

主系统仍会在内部 Controller 再次校验签名上下文和客户数据范围。接口不返回客户、订单或排餐明细，也不返回任何金额字段。

## 请求

```json
{
  "metric": "ACTIVE_ORDER_COUNT",
  "recordDate": null,
  "startDate": null,
  "endDate": null,
  "mealType": null,
  "dimensions": []
}
```

`metric` 允许：

`CUSTOMER_PROFILE_COUNT`、`ACTIVE_SERVICE_CUSTOMER_COUNT`、`ACTIVE_ORDER_COUNT`、`DAILY_SCHEDULED_CUSTOMER_COUNT`、`DAILY_VERIFIED_CUSTOMER_COUNT`、`DAILY_UNVERIFIED_CUSTOMER_COUNT`、`DAILY_UNSCHEDULED_CUSTOMER_COUNT`、`MEAL_PLAN_FAILURE_COUNT`、`EXPIRING_ORDER_COUNT`。

`dimensions` 最多两个，只允许 `MEAL_TYPE`、`PACKAGE`、`CUSTOMER_SOURCE`。日期使用单日 `recordDate` 或范围 `startDate/endDate`，由主系统按指标口径校验。

## 响应

响应使用统一 `AgentUnifiedQueryResponse<MetricItem>`：

```json
{
  "schemaVersion": "v1",
  "items": [],
  "total": 1,
  "page": 1,
  "size": 1,
  "truncated": false,
  "queriedAt": "2026-08-04T10:00:00+08:00",
  "data": {
    "metric": "ACTIVE_ORDER_COUNT",
    "total": 12,
    "dimensions": {},
    "warnings": []
  },
  "warnings": []
}
```

指标口径以 `eladmin/doc/business/智能客服Agent指标口径字典.md` 为准，尤其区分活跃客户去重数、进行中订单数、已排餐客户数和待核销客户数。模型不能提交工具名、URL、SQL、字段名或自由排序。

普通查询与排餐诊断共用该工具；不再保留 `/api/internal/agent/operations/**` 的重复 Agent 查询 Controller。
