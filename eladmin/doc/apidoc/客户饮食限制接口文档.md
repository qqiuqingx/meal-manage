# 客户饮食限制接口文档

## 基础信息

- **基础路径**: `/api/customerDietaryRestrictions`
- **认证方式**: JWT Token (Bearer Token)
- **权限前缀**: `customerDietaryRestrictions:`

## 接口列表

### 1. 查询列表

| 项目 | 说明 |
|------|------|
| **接口地址** | `GET /api/customerDietaryRestrictions` |
| **权限** | `customerDietaryRestrictions:list` |
| **Content-Type** | `application/json` |

**请求参数 (Query)**:

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 | 1 |
| size | Integer | 否 | 每页数量，默认 10 | 10 |
| customerName | String | 否 | 客户名称（模糊查询） | 张三 |
| specialNeeds | String | 否 | 特殊要求（模糊查询） | |
| restrictions | String | 否 | 忌口（模糊查询） | |
| num | Integer | 否 | 餐数 | |
| customerAddress | String | 否 | 客户地址（模糊查询） | |
| phone | String | 否 | 客户手机号（模糊查询） | |
| remainingMeals | Integer | 否 | 剩余餐数 | |
| mealPackage | String | 否 | 客户套餐 | |
| startDate | String | 否 | 开始时间（查询 start_date >= 此值） | 2026-03-13 |
| endDate | String | 否 | 结束时间（查询 end_date <= 此值） | 2026-03-30 |

**响应示例**:
```json
{
  "content": [
    {
      "id": 1,
      "customerName": "张三",
      "specialNeeds": "少盐",
      "restrictions": ["海鲜", "辛辣", "牛肉"],
      "updateDate": null,
      "createAt": "admin",
      "updateAt": null,
      "createTime": "2026-03-14 16:53:03",
      "num": 1,
      "startDate": "2026-03-12",
      "endDate": "2026-03-27",
      "customerAddress": "北京市朝阳区xxx",
      "phone": "13800138000",
      "remainingMeals": 5,
      "mealPackage": "yuezi"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### 2. 新增客户

| 项目 | 说明 |
|------|------|
| **接口地址** | `POST /api/customerDietaryRestrictions` |
| **权限** | `customerDietaryRestrictions:add` |
| **Content-Type** | `application/json` |

**请求体 (JSON)**:

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| customerName | String | 是 | 客户名称 | 张三 |
| specialNeeds | String | 否 | 特殊要求 | 少盐少油 |
| restrictions | String[] | 否 | 忌口（JSON数组） | ["海鲜", "辛辣"] |
| num | Integer | 否 | 餐数，默认 0 | 1 |
| startDate | String | 是 | 开始时间 | 2026-03-12 |
| endDate | String | 是 | 结束时间 | 2026-03-27 |
| customerAddress | String | 否 | 客户地址 | 北京市朝阳区xxx |
| phone | String | 否 | 客户手机号 | 13800138000 |
| mealPackage | String | 是 | 客户套餐 | yuezi |

> **注意**: 剩余餐数(remainingMeals)新增时自动设置为等于餐数(num)，无需传入

**请求示例**:
```json
{
  "customerName": "张三",
  "specialNeeds": "少盐少油",
  "restrictions": ["海鲜", "辛辣"],
  "num": 1,
  "startDate": "2026-03-12",
  "endDate": "2026-03-27",
  "customerAddress": "北京市朝阳区xxx",
  "phone": "13800138000",
  "mealPackage": "yuezi"
}
```

**响应**: `HTTP 201 Created`

---

### 3. 修改客户

| 项目 | 说明 |
|------|------|
| **接口地址** | `PUT /api/customerDietaryRestrictions` |
| **权限** | `customerDietaryRestrictions:edit` |
| **Content-Type** | `application/json` |

**请求体 (JSON)**:

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| id | Integer | 是 | 客户ID | 1 |
| customerName | String | 是 | 客户名称 | 张三 |
| specialNeeds | String | 否 | 特殊要求 | 少盐 |
| restrictions | String[] | 否 | 忌口（JSON数组） | ["海鲜", "牛肉"] |
| num | Integer | 否 | 餐数 | 2 |
| startDate | String | 是 | 开始时间 | 2026-03-12 |
| endDate | String | 是 | 结束时间 | 2026-03-27 |
| customerAddress | String | 否 | 客户地址 | 北京市朝阳区xxx |
| phone | String | 否 | 客户手机号 | 13800138000 |
| remainingMeals | Integer | 否 | 剩余餐数（不可编辑，仅返回） | 1 |
| mealPackage | String | 是 | 客户套餐 | yuezi |

**请求示例**:
```json
{
  "id": 1,
  "customerName": "张三",
  "specialNeeds": "少盐",
  "restrictions": ["海鲜", "牛肉"],
  "num": 2,
  "startDate": "2026-03-12",
  "endDate": "2026-03-27",
  "customerAddress": "北京市朝阳区xxx",
  "phone": "13800138000",
  "mealPackage": "yuezi"
}
```

**响应**: `HTTP 204 No Content`

---

### 4. 删除客户

| 项目 | 说明 |
|------|------|
| **接口地址** | `DELETE /api/customerDietaryRestrictions` |
| **权限** | `customerDietaryRestrictions:del` |
| **Content-Type** | `application/json` |

**请求体 (JSON)**: 整数数组

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| - | Integer[] | 是 | 要删除的客户ID数组 | [1, 2, 3] |

**请求示例**:
```json
[1, 2, 3]
```

**响应**: `HTTP 200 OK`

---

### 5. 导出数据

| 项目 | 说明 |
|------|------|
| **接口地址** | `GET /api/customerDietaryRestrictions/download` |
| **权限** | `customerDietaryRestrictions:list` |
| **Content-Type** | `application/octet-stream` |

**请求参数 (Query)**: 同查询列表参数

**响应**: Excel 文件下载 (`application/vnd.ms-excel`)

---

## 实体字段说明

| 字段 | 数据库列 | 类型 | 说明 |
|------|----------|------|------|
| id | id | Integer | 主键，自增 |
| customerName | customer_name | String | 客户名称 |
| specialNeeds | special_needs | String | 特殊要求 |
| restrictions | restrictions | JSON Array | 忌口（JSON数组，如 ["海鲜", "辛辣"]） |
| updateDate | update_date | String | 更新日期 |
| createAt | created_at | String | 创建人用户名 |
| updateAt | updated_at | String | 更新人用户名 |
| createTime | create_time | DateTime | 创建时间 |
| num | num | Integer | 餐数 |
| startDate | start_date | String | 开始时间 |
| endDate | end_date | String | 结束时间 |
| customerAddress | customer_address | String | 客户地址 |
| phone | phone | String | 客户手机号 |
| remainingMeals | remaining_meals | Integer | 剩余餐数（新增时自动等于num） |
| mealPackage | meal_package | String | 客户套餐 |

---

## 套餐枚举值

| code | desc |
|------|------|
| yuezi | 月子餐 |
| yunqi | 孕期餐 |
| xiaoyuezi | 小月子 |
| yingyang | 营养餐 |
| fenmian | 分娩餐 |

---

## 更新日志

- 2026-03-14: 初始版本
- 2026-03-14: 新增字段 customerAddress、phone、remainingMeals、mealPackage
- 2026-03-14: 移除 orderDate 字段
- 2026-03-14: 新增时 remainingMeals 自动设置为等于 num
- 2026-03-14: restrictions 字段从 String 改为 JSON Array（支持多个忌口标签）
