# 销售看板接口设计与 SQL 清单

## 1. 文档说明

- 文档名称：销售看板接口设计与 SQL 清单
- 适用范围：销售看板首页、销售明细表、TOP3 统计、按渠道查询、按销售员查询
- 创建日期：2026-04-03

## 2. 业务口径

本销售看板基于 `customer_order` 订单表统计，相关业务字段映射如下：

| 看板字段 | 数据来源 | 说明 |
| --- | --- | --- |
| 销售日期 | `customer_order.deal_time` | 订单成交时间 |
| 销售产品 | `sub_package.sub_package_name` | 订单子套餐名称 |
| 销售餐数 | `COALESCE(o.breakfast_count, 0) + COALESCE(o.lunch_dinner_count, 0)` | 订单早餐合计 + 午餐晚餐合计（注意：`customer_order` 表无 `total_count` 物理列，需在 SQL 中计算） |
| 客户备注 | `customer_order.customer_code` | 本需求中客户备注直接展示客户编号 |
| 销售金额 | `customer_order.final_amount` | 订单成交金额 |
| 销售渠道 | `customer_order.customer_source` | 订单销售渠道 |
| 销售员 | `parent_package.package_name` | 本需求中销售员实际展示父套餐名称 |

## 3. 统计规则

- 销售看板明细表数据源为 `customer_order`
- 销售餐数统一按 `COALESCE(o.breakfast_count, 0) + COALESCE(o.lunch_dinner_count, 0)` 统计
- 销售金额统一按 `final_amount` 统计
- 销售日期统一按 `deal_time` 统计
- 销售员统一按父套餐统计
- 销售产品统一按子套餐统计
- 客户备注统一展示客户编号 `customer_code`
- 建议统计时只计算 `status != 0` 的订单
- 建议统计时过滤 `deal_time IS NOT NULL`

## 4. 接口清单

### 4.1 获取销售看板金额概览

**接口地址**

```http
GET /api/sales/dashboard/overview
```

**用途**

返回以下 4 个卡片金额：

- 今日销售金额
- 本周销售金额
- 本月销售金额
- 累计销售金额

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| startDate | String | 否 | 自定义开始日期，格式 `yyyy-MM-dd` |
| endDate | String | 否 | 自定义结束日期，格式 `yyyy-MM-dd` |

说明：

- 若不传开始和结束日期，则按系统当前日期计算今日、本周、本月、累计
- 若传了开始和结束日期，则四个金额均以该区间为统计范围（而非分别对应今日/本周/本月）
- 若只传了开始日期，则以该日期起累计至今；若只传了结束日期，则累计至该日期

**响应示例**

```json
{
  "todayAmount": 14840.00,
  "weekAmount": 56230.00,
  "monthAmount": 186540.00,
  "totalAmount": 892340.00
}
```

### 4.2 获取月度销售金额趋势

**接口地址**

```http
GET /api/sales/dashboard/monthly
```

**用途**

返回某一年的 1 至 12 月销售金额，用于月度销售金额折线图或柱状图。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| year | Integer | 是 | 年份，如 `2026` |

**响应示例**

```json
{
  "year": 2026,
  "months": [
    { "month": 1, "amount": 0.00 },
    { "month": 2, "amount": 0.00 },
    { "month": 3, "amount": 124530.00 },
    { "month": 4, "amount": 186540.00 },
    { "month": 5, "amount": 0.00 },
    { "month": 6, "amount": 0.00 },
    { "month": 7, "amount": 0.00 },
    { "month": 8, "amount": 0.00 },
    { "month": 9, "amount": 0.00 },
    { "month": 10, "amount": 0.00 },
    { "month": 11, "amount": 0.00 },
    { "month": 12, "amount": 0.00 }
  ]
}
```

### 4.3 获取销售看板 TOP3 数据

**接口地址**

```http
GET /api/sales/dashboard/top
```

**用途**

返回以下 4 组统计：

- 产品销售数量 TOP3
- 产品销售金额 TOP3
- 销售员业绩 TOP3
- 销售渠道 TOP3

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| startDate | String | 否 | 开始日期，格式 `yyyy-MM-dd` |
| endDate | String | 否 | 结束日期，格式 `yyyy-MM-dd` |

**响应示例**

```json
{
  "productQuantityTop3": [
    { "name": "小月子", "value": 120 },
    { "name": "营养餐", "value": 98 },
    { "name": "月子续餐", "value": 80 }
  ],
  "productAmountTop3": [
    { "name": "月子餐A1", "value": 44130.00 },
    { "name": "营养餐B1", "value": 41440.00 },
    { "name": "孕期餐C1", "value": 32680.00 }
  ],
  "salespersonTop3": [
    { "name": "营养餐", "value": 50937.00 },
    { "name": "月子餐", "value": 45970.00 },
    { "name": "孕期续餐", "value": 31190.00 }
  ],
  "channelTop3": [
    { "name": "小红书", "value": 86769.00 },
    { "name": "抖音", "value": 36444.00 },
    { "name": "小红书续费", "value": 36120.00 }
  ]
}
```

### 4.4 获取销售明细表

**接口地址**

```http
GET /api/sales/dashboard/detail
```

**用途**

返回销售看板中间表格的数据。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| startDate | String | 否 | 开始日期，格式 `yyyy-MM-dd` |
| endDate | String | 否 | 结束日期，格式 `yyyy-MM-dd` |
| customerSource | String | 否 | 销售渠道 |
| parentPackageId | Long | 否 | 销售员，实际为父套餐 ID |
| childPackageId | Long | 否 | 销售产品，实际为子套餐 ID |

**响应示例**

```json
{
  "content": [
    {
      "id": 1,
      "saleDate": "2026-03-01 10:23:00",
      "productName": "小月子",
      "mealCount": 14,
      "customerRemark": "C118",
      "saleAmount": 1484.00,
      "channelName": "小红书",
      "salespersonName": "月子餐"
    }
  ],
  "totalElements": 120,
  "page": 1,
  "size": 10
}
```

### 4.5 销售渠道查询卡片

**接口地址**

```http
GET /api/sales/dashboard/channel-summary
```

**用途**

根据销售渠道返回：

- 渠道名称
- 销售订单数
- 销售金额

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| customerSource | String | 是 | 销售渠道编码 |
| startDate | String | 否 | 开始日期，格式 `yyyy-MM-dd` |
| endDate | String | 否 | 结束日期，格式 `yyyy-MM-dd` |

**响应示例**

```json
{
  "channelName": "渠道1",
  "orderCount": 0,
  "saleAmount": 0.00
}
```

### 4.6 销售员查询卡片

**接口地址**

```http
GET /api/sales/dashboard/salesperson-summary
```

**用途**

根据销售员返回：

- 销售员名称
- 销售订单数
- 销售金额

说明：本需求中销售员实际查询父套餐。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| parentPackageId | Long | 是 | 父套餐 ID |
| startDate | String | 否 | 开始日期，格式 `yyyy-MM-dd` |
| endDate | String | 否 | 结束日期，格式 `yyyy-MM-dd` |

**响应示例**

```json
{
  "salespersonName": "月子餐",
  "orderCount": 12,
  "saleAmount": 45970.00
}
```

## 5. SQL 统计语句清单

以下 SQL 以 MySQL 为准。

### 5.1 销售金额四卡片

```sql
SELECT
  COALESCE(SUM(CASE WHEN DATE(deal_time) = CURDATE() THEN final_amount ELSE 0 END), 0) AS today_amount,
  COALESCE(SUM(CASE WHEN YEARWEEK(deal_time, 1) = YEARWEEK(CURDATE(), 1) THEN final_amount ELSE 0 END), 0) AS week_amount,
  COALESCE(SUM(CASE WHEN DATE_FORMAT(deal_time, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m') THEN final_amount ELSE 0 END), 0) AS month_amount,
  COALESCE(SUM(final_amount), 0) AS total_amount
FROM customer_order
WHERE status != 0
  AND deal_time IS NOT NULL
  /* 日期区间条件（可选，不传时按当前日期计算四个口径） */
  /* AND DATE(deal_time) >= #{startDate} */
  /* AND DATE(deal_time) <= #{endDate} */;
```

**说明：** 当传入 `startDate`/`endDate` 时，四项金额均以该区间为统计范围；若不传则以系统当前日期自动计算。

### 5.2 月度销售金额

```sql
SELECT
  MONTH(deal_time) AS month_num,
  COALESCE(SUM(final_amount), 0) AS amount
FROM customer_order
WHERE status != 0
  AND deal_time IS NOT NULL
  AND YEAR(deal_time) = #{year}
GROUP BY MONTH(deal_time)
ORDER BY month_num;
```

### 5.3 产品销售数量 TOP3

```sql
SELECT
  sp.sub_package_name AS name,
  COALESCE(SUM(COALESCE(o.breakfast_count, 0) + COALESCE(o.lunch_dinner_count, 0)), 0) AS value
FROM customer_order o
LEFT JOIN sub_package sp ON o.child_package_id = sp.id
WHERE o.status != 0
  AND o.deal_time IS NOT NULL
  /* 可选日期筛选 */
  /* AND DATE(o.deal_time) >= #{startDate} */
  /* AND DATE(o.deal_time) <= #{endDate} */
GROUP BY o.child_package_id, sp.sub_package_name
ORDER BY value DESC
LIMIT 3;
```

### 5.4 产品销售金额 TOP3

```sql
SELECT
  sp.sub_package_name AS name,
  COALESCE(SUM(o.final_amount), 0) AS value
FROM customer_order o
LEFT JOIN sub_package sp ON o.child_package_id = sp.id
WHERE o.status != 0
  AND o.deal_time IS NOT NULL
  /* 可选日期筛选 */
  /* AND DATE(o.deal_time) >= #{startDate} */
  /* AND DATE(o.deal_time) <= #{endDate} */
GROUP BY o.child_package_id, sp.sub_package_name
ORDER BY value DESC
LIMIT 3;
```

### 5.5 销售员业绩 TOP3

```sql
SELECT
  pp.package_name AS name,
  COALESCE(SUM(o.final_amount), 0) AS value
FROM customer_order o
LEFT JOIN parent_package pp ON o.parent_package_id = pp.id
WHERE o.status != 0
  AND o.deal_time IS NOT NULL
  /* 可选日期筛选 */
  /* AND DATE(o.deal_time) >= #{startDate} */
  /* AND DATE(o.deal_time) <= #{endDate} */
GROUP BY o.parent_package_id, pp.package_name
ORDER BY value DESC
LIMIT 3;
```

### 5.6 销售渠道 TOP3

```sql
SELECT
  o.customer_source AS name,
  COALESCE(SUM(o.final_amount), 0) AS value
FROM customer_order o
WHERE o.status != 0
  AND o.deal_time IS NOT NULL
  AND o.customer_source IS NOT NULL
  AND o.customer_source != ''
  /* 可选日期筛选 */
  /* AND DATE(o.deal_time) >= #{startDate} */
  /* AND DATE(o.deal_time) <= #{endDate} */
GROUP BY o.customer_source
ORDER BY value DESC
LIMIT 3;
```

### 5.7 销售明细表查询

```sql
SELECT
  o.id,
  o.deal_time AS sale_date,
  sp.sub_package_name AS product_name,
  (COALESCE(o.breakfast_count, 0) + COALESCE(o.lunch_dinner_count, 0)) AS meal_count,
  o.customer_code AS customer_remark,
  o.final_amount AS sale_amount,
  o.customer_source AS channel_name,
  pp.package_name AS salesperson_name
FROM customer_order o
LEFT JOIN parent_package pp ON o.parent_package_id = pp.id
LEFT JOIN sub_package sp ON o.child_package_id = sp.id
WHERE o.status != 0
  AND o.deal_time IS NOT NULL
  /* 可选筛选 */
  /* AND DATE(o.deal_time) >= #{startDate} */
  /* AND DATE(o.deal_time) <= #{endDate} */
  /* AND o.customer_source = #{customerSource} */
  /* AND o.parent_package_id = #{parentPackageId} */
  /* AND o.child_package_id = #{childPackageId} */
ORDER BY o.deal_time DESC, o.id DESC
LIMIT #{page * size}, #{size};
```

**说明：** `page` 为前端传入的 0 基页码（从 0 开始），后端计算 offset 为 `page * size`。例如第 0 页取 `LIMIT 0, 10`，第 1 页取 `LIMIT 10, 10`。

### 5.8 销售明细总数

```sql
SELECT COUNT(1)
FROM customer_order o
WHERE o.status != 0
  AND o.deal_time IS NOT NULL
  /* 可选筛选 */
  /* AND DATE(o.deal_time) >= #{startDate} */
  /* AND DATE(o.deal_time) <= #{endDate} */
  /* AND o.customer_source = #{customerSource} */
  /* AND o.parent_package_id = #{parentPackageId} */
  /* AND o.child_package_id = #{childPackageId} */;
```

### 5.9 销售渠道查询卡片

```sql
SELECT
  o.customer_source AS channel_name,
  COUNT(1) AS order_count,
  COALESCE(SUM(o.final_amount), 0) AS sale_amount
FROM customer_order o
WHERE o.status != 0
  AND o.customer_source = #{customerSource}
  AND o.deal_time IS NOT NULL
GROUP BY o.customer_source;
```

### 5.10 销售员查询卡片

```sql
SELECT
  pp.package_name AS salesperson_name,
  COUNT(1) AS order_count,
  COALESCE(SUM(o.final_amount), 0) AS sale_amount
FROM customer_order o
LEFT JOIN parent_package pp ON o.parent_package_id = pp.id
WHERE o.status != 0
  AND o.parent_package_id = #{parentPackageId}
  AND o.deal_time IS NOT NULL
GROUP BY o.parent_package_id, pp.package_name;
```

## 6. 建议新增的后端类

建议新增以下模块，避免与订单 CRUD 混杂：

- `me.zhengjie.modules.sales.rest.SalesDashboardController`
- `me.zhengjie.modules.sales.service.SalesDashboardService`
- `me.zhengjie.modules.sales.service.impl.SalesDashboardServiceImpl`
- `me.zhengjie.modules.sales.mapper.SalesDashboardMapper`
- `resources/mapper/SalesDashboardMapper.xml`

建议新增以下 DTO / VO：

- `SalesDashboardOverviewVO`
- `SalesMonthlyTrendVO`
- `SalesMonthlyItemVO`
- `SalesTopItemVO`
- `SalesDashboardTopVO`
- `SalesDetailVO`
- `SalesChannelSummaryVO`
- `SalesSalespersonSummaryVO`
- `SalesDashboardQueryCriteria`

## 7. 实现注意事项

### 7.1 关于销售餐数

`customer_order` 表中无 `total_count` 物理列，销售餐数统一使用表达式：

```sql
COALESCE(o.breakfast_count, 0) + COALESCE(o.lunch_dinner_count, 0)
```

各接口 SQL 中均已按此规则实现，无需额外 workaround。

### 7.2 关于销售渠道展示

数据库中的 `customer_source` 一般存的是字典值，前端展示时建议用字典翻译成标签名称。

### 7.3 关于销售员名称

本需求中“销售员”并非系统用户，不建议绑定用户表，应直接按父套餐处理。

### 7.4 关于筛选条件

建议所有统计接口统一支持：

- 开始日期
- 结束日期
- 销售渠道
- 销售员（父套餐）
- 销售产品（子套餐）

这样后续页面联动更方便。

## 8. 现有代码参考

- 订单实体：
  [CustomerOrder.java](/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/domain/CustomerOrder.java)
- 订单查询 SQL：
  [CustomerOrderMapper.xml](/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/resources/mapper/CustomerOrderMapper.xml)
- 订单控制器：
  [CustomerOrderController.java](/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/rest/CustomerOrderController.java)

