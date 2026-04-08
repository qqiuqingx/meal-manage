# 客户统计接口

## customer-stats

获取当天客户总数，按套餐和餐次分组统计。

### 请求

```
GET /api/dishes/schedule/customer-stats?date=2026-04-05
```

### 响应

```json
{
  "date": "2026-04-05",
  "totalCustomerCount": 10,
  "groups": [
    { "mealType": "LUNCH", "mealPackage": "yuezi", "mealPackageDesc": "月子餐", "customerCount": 5 },
    { "mealType": "LUNCH", "mealPackage": "yunqi", "mealPackageDesc": "孕期餐", "customerCount": 3 },
    { "mealType": "DINNER", "mealPackage": "yuezi", "mealPackageDesc": "月子餐", "customerCount": 2 }
  ],
  "sourceGroups": [
    { "source": "1380013", "sourceDesc": "1380013", "customerCount": 3 },
    { "source": "1390009", "sourceDesc": "1390009", "customerCount": 7 }
  ]
}
```

### 数据来源

| 字段 | 来源表 |
|------|-------|
| 排餐计划 | meal_plan |
| 客户排餐 | meal_plan_customer |
| 套餐 | customer_order + parent_package |
| 来源标识 | customer_profile.phone 前7位 |

---

## customer-source-stats

获取当天有效客户，按来源分组统计。

### 请求

```
GET /api/dishes/schedule/customer-source-stats?date=2026-04-05
```

### 响应

```json
[
  { "source": "1380013", "sourceDesc": "1380013", "customerCount": 3 },
  { "source": "1390009", "sourceDesc": "1390009", "customerCount": 7 }
]
```

### 数据来源

| 字段 | 来源表 |
|------|-------|
| 排餐计划 | meal_plan |
| 客户排餐 | meal_plan_customer |
| 来源标识 | customer_profile.phone 前7位 |