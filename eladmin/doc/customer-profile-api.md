# 客户档案管理 API 文档

> 文档版本: 1.0
> 创建日期: 2026-03-25

## 1. 概述

客户档案管理模块提供客户基础档案、地址、两级套餐、餐数、过敏食物、医嘱要求、孕周等信息的管理能力。

### 1.1 模块结构

- **后端路径**: `/api/customerProfile` (客户档案), `/api/customerPackageCategory` (套餐分类)
- **前端路径**: `customer/profile/index` (客户档案页面), `customer/packageCategory/index` (套餐分类页面)

### 1.2 权限定义

| 权限标识 | 说明 |
|---------|------|
| `customerProfile:list` | 客户档案列表查询 |
| `customerProfile:add` | 新增客户档案 |
| `customerProfile:edit` | 编辑客户档案 |
| `customerProfile:status` | 启用/停用客户档案 |
| `customerProfile:del` | 删除客户档案(仅内部管理工具) |
| `customerPackageCategory:list` | 套餐分类列表 |
| `customerPackageCategory:add` | 新增套餐分类 |
| `customerPackageCategory:edit` | 编辑套餐分类 |
| `customerPackageCategory:del` | 删除套餐分类 |
| `customerPackageCategory:status` | 启用/停用套餐分类 |

---

## 2. 套餐分类管理 API

### 2.1 获取套餐分类树

**请求**

```
GET /api/customerPackageCategory/tree
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "categoryName": "月子餐",
      "categoryCode": "PACKAGE_A",
      "parentId": null,
      "level": 1,
      "sort": 1,
      "enabled": true,
      "codePrefix": "A",
      "children": [
        {
          "id": 3,
          "categoryName": "两荤一素",
          "categoryCode": "PACKAGE_A_1",
          "parentId": 1,
          "level": 2,
          "sort": 1,
          "enabled": true,
          "codePrefix": null,
          "children": []
        }
      ]
    }
  ]
}
```

### 2.2 获取父级套餐列表

用于客户档案页联动选择。

**请求**

```
GET /api/customerPackageCategory/parents
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "categoryName": "月子餐", "codePrefix": "A" },
    { "id": 2, "categoryName": "营养餐", "codePrefix": "B" }
  ]
}
```

### 2.3 新增套餐分类

**请求**

```
POST /api/customerPackageCategory
Content-Type: application/json

{
  "categoryName": "两荤一素",
  "categoryCode": "PACKAGE_A_1",
  "parentId": 1,
  "level": 2,
  "sort": 1,
  "enabled": true
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| categoryName | string | 是 | 分类名称 |
| categoryCode | string | 是 | 分类编码(全局唯一) |
| parentId | long | 否 | 父级ID(顶级为NULL) |
| level | int | 是 | 层级(1=父级,2=子级) |
| sort | int | 是 | 排序 |
| enabled | boolean | 是 | 是否启用 |
| codePrefix | string | 否 | 编号前缀(仅父级使用,单个大写字母) |

### 2.4 编辑套餐分类

**请求**

```
PUT /api/customerPackageCategory
Content-Type: application/json

{
  "id": 3,
  "categoryName": "两荤一素(升级版)",
  "categoryCode": "PACKAGE_A_1",
  "parentId": 1,
  "level": 2,
  "sort": 1,
  "enabled": true
}
```

### 2.5 启用/停用套餐分类

**请求**

```
PUT /api/customerPackageCategory/{id}/status
Content-Type: application/json

{
  "enabled": false
}
```

### 2.6 删除套餐分类

**请求**

```
DELETE /api/customerPackageCategory/{id}
```

**说明**

- 仅在无客户引用且无子节点时允许删除
- 其他场景仅允许停用

---

## 3. 客户档案管理 API

### 3.1 分页查询客户档案

**请求**

```
GET /api/customerProfile
```

**查询参数**

| 参数 | 类型 | 说明 |
|-----|------|------|
| customerCode | string | 客户编号 |
| customerName | string | 客户姓名 |
| phone | string | 手机号 |
| parentPackageId | long | 父套餐ID |
| childPackageId | long | 子套餐ID |
| status | boolean | 状态 |
| current | int | 当前页码(默认1) |
| size | int | 每页条数(默认10) |

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "customerCode": "A001",
        "customerName": "张三",
        "phone": "13800000000",
        "gestationalWeek": 32,
        "allergyTags": ["牛奶", "海鲜"],
        "medicalRequirements": "少盐少油",
        "status": true,
        "defaultAddress": "北京市朝阳区xxx",
        "parentPackageName": "月子餐",
        "childPackageName": "两荤一素",
        "breakfastCount": 10,
        "lunchDinnerCount": 20,
        "totalCount": 30,
        "startDate": "2026-03-25",
        "endDate": "2026-04-25",
        "createTime": "2026-03-25 10:00:00"
      }
    ],
    "total": 100,
    "size": 10,
    "current": 1
  }
}
```

### 3.2 获取客户详情

**请求**

```
GET /api/customerProfile/{id}
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "customerCode": "A001",
    "customerName": "张三",
    "phone": "13800000000",
    "gestationalWeek": 32,
    "allergyTags": ["牛奶", "海鲜"],
    "medicalRequirements": "少盐少油",
    "status": true,
    "remark": "备注信息",
    "addresses": [
      { "addressType": "DEFAULT", "addressDetail": "地址1", "contactName": "张三", "contactPhone": "13800000000" },
      { "addressType": "WORKDAY", "addressDetail": "地址2" },
      { "addressType": "WEEKEND", "addressDetail": "地址3" }
    ],
    "packageInfo": {
      "parentPackageId": 1,
      "parentPackageName": "月子餐",
      "childPackageId": 3,
      "childPackageName": "两荤一素",
      "breakfastCount": 10,
      "lunchDinnerCount": 20,
      "totalCount": 30,
      "startDate": "2026-03-25",
      "endDate": "2026-04-25",
      "activeFlag": true
    },
    "createTime": "2026-03-25 10:00:00",
    "updateTime": "2026-03-25 10:00:00"
  }
}
```

### 3.3 新增客户档案

**请求**

```
POST /api/customerProfile
Content-Type: application/json

{
  "customerCode": "A001",
  "customerName": "张三",
  "phone": "13800000000",
  "gestationalWeek": 32,
  "allergyTags": ["牛奶", "海鲜"],
  "medicalRequirements": "少盐少油",
  "status": true,
  "addresses": [
    { "addressType": "DEFAULT", "addressDetail": "地址1" },
    { "addressType": "WORKDAY", "addressDetail": "地址2" },
    { "addressType": "WEEKEND", "addressDetail": "地址3" }
  ],
  "packageInfo": {
    "parentPackageId": 1,
    "childPackageId": 3,
    "breakfastCount": 10,
    "lunchDinnerCount": 20,
    "startDate": "2026-03-25",
    "endDate": "2026-04-25"
  }
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| customerCode | string | 是 | 客户编号(格式: 父套餐前缀+3位数字,如A001) |
| customerName | string | 是 | 客户姓名 |
| phone | string | 是 | 手机号(中国大陆手机号格式) |
| gestationalWeek | int | 否 | 孕周(正整数) |
| allergyTags | array | 否 | 过敏食物标签(JSON数组) |
| medicalRequirements | string | 否 | 医嘱要求(建议不超过500字符) |
| status | boolean | 是 | 状态 |
| addresses | array | 是 | 地址列表(至少一个地址非空) |
| addresses[].addressType | string | 是 | 地址类型(DEFAULT/WORKDAY/WEEKEND) |
| addresses[].addressDetail | string | 是 | 详细地址(建议不超过200字符) |
| addresses[].contactName | string | 否 | 联系人姓名 |
| addresses[].contactPhone | string | 否 | 联系人电话 |
| packageInfo | object | 是 | 套餐信息 |
| packageInfo.parentPackageId | long | 是 | 父套餐ID |
| packageInfo.childPackageId | long | 是 | 子套餐ID |
| packageInfo.breakfastCount | int | 否 | 早餐数(与午餐+晚餐数至少填一个) |
| packageInfo.lunchDinnerCount | int | 否 | 午餐+晚餐数 |
| packageInfo.startDate | string | 是 | 签约开始日期(YYYY-MM-DD) |
| packageInfo.endDate | string | 是 | 签约结束日期(YYYY-MM-DD) |

**校验规则**

- `DEFAULT`、`WORKDAY`、`WEEKEND` 三个地址槽位中至少有一个地址明细非空
- 早餐数与午餐+晚餐数不能同时为空
- `total_count` 由后端自动计算
- `end_date >= start_date`

### 3.4 编辑客户档案

**请求**

```
PUT /api/customerProfile
Content-Type: application/json

{
  "id": 1,
  "customerCode": "A001",
  "customerName": "张三",
  "phone": "13800000000",
  "gestationalWeek": 32,
  "allergyTags": ["牛奶", "海鲜"],
  "medicalRequirements": "少盐少油",
  "status": true,
  "addresses": [...],
  "packageInfo": {...}
}
```

**说明**

- 采用全量覆盖更新语义: 前端提交的 `addresses` 与 `packageInfo` 视为客户当前完整状态
- 编辑时如果某个地址槽位未提交，则视为清空该槽位
- `packageInfo` 每次整体覆盖当前签约记录，不支持局部 patch

### 3.5 启用/停用客户档案

**请求**

```
PUT /api/customerProfile/{id}/status
Content-Type: application/json

{
  "status": false
}
```

**停用时**

- 当前生效签约同步失效 (`active_flag = 0`)

**启用时**

- 必须同时存在 1 条有效签约记录
- 重新启用停用客户时，要求同时提交并更新一条有效签约记录

```json
{
  "status": true,
  "packageInfo": {
    "parentPackageId": 1,
    "childPackageId": 3,
    "breakfastCount": 10,
    "lunchDinnerCount": 20,
    "startDate": "2026-03-25",
    "endDate": "2026-04-25"
  }
}
```

### 3.6 生成客户编号

**请求**

```
GET /api/customerProfile/generateCode?parentPackageId=1
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": "A001"
}
```

**说明**

- 根据父套餐 `code_prefix` 生成建议编号
- 格式为"父套餐前缀 + 三位数字"
- 若父套餐不存在、未启用或未配置 `code_prefix`，返回错误

---

## 4. 错误码说明

| 错误码 | 说明 |
|-------|------|
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 5. 附录

### 5.1 地址类型枚举

| 值 | 说明 |
|----|------|
| DEFAULT | 默认地址 |
| WORKDAY | 工作日地址 |
| WEEKEND | 周末地址 |

### 5.2 套餐类型说明

套餐采用两级结构:
- **父级**: 月子餐、营养餐等(配置编号前缀)
- **子级**: 两荤一素、一荤一素、两荤两素等