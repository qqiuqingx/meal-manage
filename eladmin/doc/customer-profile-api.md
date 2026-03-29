# 客户档案管理 API 文档

> 文档版本: 2.0
> 创建日期: 2026-03-25
> 更新日期: 2026-03-27
>
> **变更说明 (v2.0)**：
> - `packageInfo` → `orderInfo`：首单信息语义更清晰
> - 创建时 `customerCode` 由后端根据首单父套餐自动生成，前端不再传入
> - 编辑时只更新基本资料 + 地址，不再操作套餐/订单
> - 状态切换只传 `{ status }`，不再传套餐信息
> - 列表响应删除冗余套餐列（父套餐名/子套餐名/早餐数等）
> - 详情响应不再返回 `packageInfo`

## 1. 概述

客户档案管理模块提供客户基础档案、地址、过敏食物、医嘱要求、孕周等信息的管理能力。
首单（第一笔订单）随客户创建时自动生成。

### 1.1 模块结构

- **后端路径**: `/api/customerProfile` (客户档案), `/api/customerPackageCategory` (套餐分类)
- **前端路径**: `customer/profile/index` (客户档案页面), `customer/packageCategory/index` (套餐分类页面)

### 1.2 权限定义

| 权限标识 | 说明 |
|---------|------|
| `customerProfile:list` | 客户档案列表查询 |
| `customerProfile:add` | 新增客户档案 |
| `customerProfile:edit` | 编辑客户档案 |
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
| current | int | 当前页码(默认1) |
| size | int | 每页条数(默认10) |

> 注：`parentPackageId`/`childPackageId` 查询参数已废弃，列表不再返回套餐相关字段。

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
        "defaultAddress": "北京市朝阳区xxx",
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

> 编辑前必须先调用此接口获取完整详情，再进入编辑表单。

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
    "remark": "备注信息",
    "addresses": [
      { "addressType": "DEFAULT", "addressDetail": "地址1", "contactName": "张三", "contactPhone": "13800000000" },
      { "addressType": "WORKDAY", "addressDetail": "地址2" },
      { "addressType": "WEEKEND", "addressDetail": "地址3" }
    ],
    "createTime": "2026-03-25",
    "updateTime": "2026-03-25"
  }
}
```

### 3.3 新增客户档案

**请求**

```
POST /api/customerProfile
Content-Type: application/json

{
  "customerName": "张三",
  "phone": "13800000000",
  "gestationalWeek": 32,
  "allergyTags": ["牛奶", "海鲜"],
  "medicalRequirements": "少盐少油",
  "addresses": [
    { "addressType": "DEFAULT", "addressDetail": "地址1", "contactName": "张三", "contactPhone": "13800000000" },
    { "addressType": "WORKDAY", "addressDetail": "地址2" },
    { "addressType": "WEEKEND", "addressDetail": "地址3" }
  ],
  "orderInfo": {
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
| customerName | string | 是 | 客户姓名 |
| phone | string | 是 | 手机号(中国大陆手机号格式) |
| gestationalWeek | int | 否 | 孕周(正整数) |
| allergyTags | array | 否 | 过敏食物标签(JSON数组) |
| medicalRequirements | string | 否 | 医嘱要求 |
| addresses | array | 是 | 地址列表(至少一个地址非空) |
| addresses[].addressType | string | 是 | 地址类型(DEFAULT/WORKDAY/WEEKEND) |
| addresses[].addressDetail | string | 是 | 详细地址(至少一个非空) |
| addresses[].contactName | string | 否 | 联系人姓名 |
| addresses[].contactPhone | string | 否 | 联系人电话 |
| orderInfo | object | 是 | 首单信息 |
| orderInfo.parentPackageId | long | 是 | 父套餐ID |
| orderInfo.childPackageId | long | 是 | 子套餐ID |
| orderInfo.breakfastCount | int | 否 | 早餐数(与午餐+晚餐数至少填一个) |
| orderInfo.lunchDinnerCount | int | 否 | 午餐+晚餐数 |
| orderInfo.startDate | string | 是 | 签约开始日期(YYYY-MM-DD) |
| orderInfo.endDate | string | 是 | 签约结束日期(YYYY-MM-DD) |

**校验规则**

- `customerCode` 由后端自动生成，前端无需传入
- `DEFAULT`、`WORKDAY`、`WEEKEND` 三个地址槽位中至少有一个地址明细非空
- 早餐数与午餐+晚餐数不能同时为空
- `totalCount` 由后端根据 `breakfastCount + lunchDinnerCount` 自动计算
- `endDate >= startDate`
- 首单份数总和必须 > 0

**创建后行为**

- 自动生成 `customerCode`（格式：`{父套餐codePrefix}{NNN}`，如 A001）
- 自动在 `customer_order` 表创建首笔订单，状态为"进行中"

### 3.4 编辑客户档案

> 编辑时只更新基本资料 + 地址，套餐/订单信息不受影响。

**请求**

```
PUT /api/customerProfile
Content-Type: application/json

{
  "id": 1,
  "customerName": "张三",
  "phone": "13800000000",
  "gestationalWeek": 32,
  "allergyTags": ["牛奶", "海鲜"],
  "medicalRequirements": "少盐少油",
  "remark": "备注",
  "addresses": [
    { "addressType": "DEFAULT", "addressDetail": "新地址1", "contactName": "张三", "contactPhone": "13800000000" },
    { "addressType": "WORKDAY", "addressDetail": "新地址2" },
    { "addressType": "WEEKEND", "addressDetail": "" }
  ]
}
```

**说明**

- 必须传入 `id`
- 不传 `orderInfo`/`packageInfo`，后端不修改套餐/订单
- 不传 `status`，后端不修改状态
- `addresses` 整体覆盖：某槽位不传则清空

### 3.5 生成客户编号（预览）

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

- 根据父套餐 `code_prefix` 生成建议编号预览
- 格式为"父套餐前缀 + 三位数字"
- 若父套餐不存在、未启用或未配置 `code_prefix`，返回错误
- 此接口仅用于编号预览，实际编号在创建时由后端自动分配

---

## 4. 错误码说明

| 错误码 | 说明 |
|-------|------|
| 400 | 请求参数错误（后端会返回具体字段校验失败信息）|
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

### 5.3 create/edit 两路径分离

| 路径 | 传入 orderInfo | 修改客户基本资料 | 修改地址 |
|------|---------------|---------------|---------|
| POST（创建） | ✅ 必填 | ✅ | ✅ |
| PUT（编辑） | ❌ | ✅ | ✅ |
