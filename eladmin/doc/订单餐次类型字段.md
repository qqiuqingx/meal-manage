# 订单餐次类型字段

## 概述

为支持客户同时拥有午餐和晚餐不同套餐的场景，`customer_order` 表新增 `meal_type` 字段。

## 字段说明

| 字段名 | 类型 | 说明 | 示例值 |
|---------|------|------|--------|
| meal_type | VARCHAR(20) | 餐次类型: LUNCH=午餐订单, DINNER=晚餐订单, ALL=全餐次订单 | "LUNCH", "DINNER", "ALL" |

## 使用场景

1. **全餐次订单 (meal_type=ALL)**: 默认值，兼容现有数据，订单包含早餐、午餐、晚餐
2. **午餐订单 (meal_type=LUNCH)**: 客户只订午餐套餐，`lunchDinnerCount` 仅统计午餐
3. **晚餐订单 (meal_type=DINNER)**: 客户只订晚餐套餐，`lunchDinnerCount` 仅统计晚餐

## API 变更

### 新增/编辑订单

**请求参数**新增字段：
```json
{
  "customerId": 1,
  "parentPackageId": 2,
  "childPackageId": 3,
  "breakfastCount": 0,
  "lunchDinnerCount": 20,
  "totalAmount": 2000.00,
  "finalAmount": 1800.00,
  "mealType": "LUNCH",  // 可选值: "LUNCH", "DINNER", "ALL"
  "scheduleMode": "SCHEDULE",
  "startDate": "2026-04-01",
  "endDate": "2026-04-30"
}
```

**字段验证规则**:
- `mealType` 为可选字段，默认值为 `"ALL"`
- 支持的值：`"LUNCH"`, `"DINNER"`, `"ALL"`

### 查询订单列表

**响应参数**新增字段：
```json
{
  "content": [
    {
      "id": 1,
      "orderCode": "ORD20260329001",
      "customerName": "张三",
      "parentPackageName": "月子餐",
      "childPackageName": "两荤一素",
      "status": 1,
      "statusDesc": "进行中",
      "mealType": "LUNCH",
      "mealTypeDesc": "午餐",
      "breakfastCount": 0,
      "lunchDinnerCount": 20,
      "totalCount": 20,
      "startDate": "2026-04-01",
      "endDate": "2026-04-30"
    }
  ],
  "totalElements": 1
}
```

## 数据迁移

现有订单数据 `meal_type` 默认为 `NULL`，系统逻辑中按 `ALL`（全餐次）处理。

**数据库升级脚本**:
```sql
-- 为现有数据库添加餐次类型字段
ALTER TABLE customer_order
ADD COLUMN meal_type VARCHAR(20) NULL COMMENT '餐次类型: LUNCH=午餐订单, DINNER=晚餐订单, ALL=全餐次订单'
AFTER status;

-- 创建索引提高查询效率
CREATE INDEX idx_meal_type ON customer_order(meal_type);
```

## 前端变更

1. **订单列表页** (`eladmin-web/src/views/customer/order/index.vue`):
   - 表格新增"餐次"列，显示 LUNCH/DINNER/ALL 对应的中文

2. **订单表单组件** (`eladmin-web/src/components/Order/OrderForm.vue`):
   - 表单新增"餐次类型"下拉选择器
   - 选项：全餐次（默认）、午餐订单、晚餐订单

3. **客户详情页** (`eladmin-web/src/views/customer/profile/CustomerDetailDialog.vue`):
   - 订单列表新增"餐次"列

## 业务场景示例

### 场景：客户同时有午餐和晚餐不同套餐

**操作步骤**:
1. 创建第一个订单：选择"月子餐/两荤一素"，餐次类型选择"午餐订单"，设置午晚餐数=20
2. 创建第二个订单：选择"营养餐/标准餐"，餐次类型选择"晚餐订单"，设置午晚餐数=20

**效果**:
- 两个订单在系统中清晰区分
- 订单列表显示餐次列，用户可直观看出哪个是午餐订单、哪个是晚餐订单
- 两个订单可以有独立的套餐配置和日期范围

---

## 订单重复校验接口

### 校验订单冲突

**接口地址**: `POST /api/customer/order/validate`

**请求参数**:
```json
{
  "customerId": 1,
  "startDate": "2026-04-01",
  "endDate": "2026-04-30",
  "mealType": "LUNCH"
}
```

**校验规则**:
1. **全餐次订单 (meal_type=ALL)**: 同一时间段只能有 1 个订单
2. **午餐/晚餐订单 (meal_type=LUNCH/DINNER)**: 同一时间段最多 2 个不同餐次的订单，且不能有相同餐次的订单
3. **剩余餐数检查**: 编辑订单时，新订单餐数不能小于已核销餐数

**响应**:
- 校验通过: 200 OK
- 校验失败: 400 BadRequest，返回错误消息

**错误示例**:
```json
{
  "message": "同一时间段已存在全餐次订单，不能重复创建"
}
```

```json
{
  "message": "同一时间段最多只能有两个不同餐次的订单"
}
```

```json
{
  "message": "同一时间段已存在相同餐次的订单"
}
```

```json
{
  "message": "订单餐数不能小于已核销餐数（当前已核销：15）"
}
```
