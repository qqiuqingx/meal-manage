# 客户档案管理模块设计方案

## 1. 背景与目标

当前 `meal/customerDietaryRestrictions` 已承载排餐侧客户资料，但本次需求明确要求：

- 不扩展现有 `meal` 客户管理功能
- 新增独立“客户档案/签约管理”模块
- 金额信息拆分为独立模块，本次不做

本期目标是新建一个独立模块，用于沉淀客户基础档案、地址、两级套餐、餐数、过敏食物、医嘱要求、孕周等信息，为后续金额模块和可能的排餐联动提供清晰基础。

## 2. 范围

### 本期包含

- 独立客户档案模块
- 客户编号管理
- 多地址管理（默认 / 工作日 / 周末）
- 两级套餐分类管理（后台可配置）
- 客户当前套餐签约信息
- 过敏食物多选标签
- 医嘱要求自由文本
- 孕周字段
- 早餐数、午餐+晚餐数、总份数管理

### 本期不包含

- 定金金额、总金额等金额逻辑
- 收款、尾款、订单、账单
- 对现有 `meal/customerDietaryRestrictions` 的改造
- 与排餐模块的自动同步
- 旧数据迁移

## 3. 业务规则

### 3.1 模块边界

- 新模块负责客户建档、地址、套餐配置、客户当前签约信息
- 现有 `meal/customerDietaryRestrictions` 保持不变，继续服务排餐场景
- 金额信息后续拆到独立模块处理
- 本期客户档案与当前签约同步创建、同步维护，不支持“仅建档不签约”
- 本期客户状态与当前签约状态关系如下：
  - `customer_profile.status = ENABLED` 表示客户档案可用
  - `customer_profile_package.active_flag = 1` 表示该客户存在唯一当前生效签约
  - 启用客户必须存在 1 条生效签约记录
  - 停用客户后，当前生效签约同步置为失效（`active_flag = 0`）

### 3.2 编号规则

- 编号格式：父套餐字母前缀 + 三位流水号
- 示例：`A001`、`A002`
- 父套餐维护自己的字母前缀，如：
  - 月子餐 -> `A`
  - 营养餐 -> `B`
- 选择父套餐后，系统自动生成建议编号
- 用户允许手工调整编号，但仍必须满足“所选父套餐前缀 + 三位数字”的格式规则
- 不允许录入与所选父套餐前缀不一致的编号
- 保存时后端做唯一性校验与格式校验
- 编辑客户时：
  - 若未修改父套餐，则保留当前编号
  - 若修改了父套餐，前端重新请求建议编号
  - 若用户此前手工修改过编号，则切换父套餐后仍以新父套餐前缀重新生成建议编号，需用户确认后保存

### 3.3 地址规则

一个客户支持多个地址，但本期采用固定槽位模型，只支持以下 3 类地址位：

- `DEFAULT`：默认地址
- `WORKDAY`：工作日地址
- `WEEKEND`：周末地址

约束：

- 一个客户最多 1 个默认地址
- 一个客户最多 1 个工作日地址
- 一个客户最多 1 个周末地址
- `DEFAULT`、`WORKDAY`、`WEEKEND` 三个地址槽位中至少填写一个
- 地址返回顺序固定为：`DEFAULT -> WORKDAY -> WEEKEND`

### 3.4 套餐规则

套餐采用两级结构：

- 父级：月子餐 / 营养餐等
- 子级：两荤一素 / 一荤一素 / 两荤两素等

要求：

- 父级和子级均由后台配置维护
- 子级必须挂在某个父级下
- 客户签约时必须选择父套餐和子套餐

### 3.5 餐数规则

客户套餐信息保存以下字段：

- `breakfast_count`：早餐数
- `lunch_dinner_count`：午餐+晚餐数
- `total_count`：总份数
- `start_date`：签约开始日期，必填
- `end_date`：签约结束日期，必填

规则：

- `total_count = coalesce(breakfast_count, 0) + coalesce(lunch_dinner_count, 0)`
- 早餐数或午餐+晚餐数允许其中一个为空
- 不允许两者都为空
- `total_count` 由后端自动计算，不允许前端手工覆盖
- `end_date >= start_date`
- `active_flag` 不由日期自动推导，本期仅由业务状态切换控制

### 3.6 过敏食物与医嘱要求

- 过敏食物：多选标签
- 医嘱要求：自由文本

过敏食物前端支持多选并允许录入新标签；后端按 JSON 数组存储。

## 4. 总体方案

采用“独立新模块 + 领域拆分”方式建设，不复用现有 `meal/customerDietaryRestrictions` 表结构。

### 后端建议目录

`eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/`

建议分层：

- `domain/`
- `domain/dto/`
- `mapper/`
- `rest/`
- `service/`
- `service/impl/`

### 前端建议目录

- `eladmin-web/src/views/customer/profile/`
- `eladmin-web/src/api/customerProfile.js`
- `eladmin-web/src/views/customer/packageCategory/`
- `eladmin-web/src/api/customerPackageCategory.js`

## 5. 数据模型设计

本期建议 4 张表。

### 5.1 客户主档表 `customer_profile`

用途：存客户基础资料。

核心字段：

- `id`
- `customer_code`：客户编号，唯一
- `customer_name`
- `phone`
- `gestational_week`：孕周，正整数，可为空
- `allergy_tags`：JSON 数组
- `medical_requirements`
- `status`
- `remark`
- `create_by`
- `update_by`
- `create_time`
- `update_time`

约束：

- `customer_code` 唯一索引
- `gestational_week` 若填写则必须为正整数
- `allergy_tags` 中单个标签长度建议不超过 20 个字符，提交前去重并做首尾空白裁剪

### 5.2 客户地址表 `customer_profile_address`

用途：存一个客户的地址槽位信息。本期不是通用无限多地址模型，而是固定 3 个槽位：默认地址、工作日地址、周末地址。

核心字段：

- `id`
- `customer_id`
- `address_type`：`DEFAULT` / `WORKDAY` / `WEEKEND`
- `address_detail`
- `contact_name`（可选）
- `contact_phone`（可选）
- `create_time`
- `update_time`

约束：

- `customer_id + address_type` 唯一索引
- 本期不设计 `sort`、`enabled` 等通用多地址字段，避免与固定槽位模型冲突

### 5.3 套餐字典表 `customer_package_category`

用途：维护父子两级套餐。

核心字段：

- `id`
- `category_name`
- `category_code`
- `parent_id`
- `level`
- `sort`
- `enabled`
- `code_prefix`
- `create_time`
- `update_time`

说明：

- 父级节点保存 `code_prefix`
- 子级节点不需要编号前缀
- 使用树结构维护父子关系

### 5.4 客户签约表 `customer_profile_package`

用途：保存客户当前生效套餐信息。

核心字段：

- `id`
- `customer_id`
- `parent_package_id`
- `child_package_id`
- `breakfast_count`
- `lunch_dinner_count`
- `total_count`
- `start_date`
- `end_date`
- `active_flag`
- `remark`
- `create_time`
- `update_time`

说明：

- 本期每个客户必须有且仅有 1 条当前生效签约记录，不支持“先建档不签约”
- 当前阶段每个客户只允许 1 条生效记录
- 编辑客户套餐时，直接覆盖当前生效记录，不新增历史记录
- `start_date`、`end_date` 为本期正式使用字段，需在前端录入并参与校验
- `active_flag` 本期仅由业务启停控制，不根据日期自动变更，不引入定时任务
- 新建客户时：
  - 若客户状态为启用，则签约记录必须为生效
  - 若客户状态为停用，则签约记录必须为失效
- 重新启用停用客户时，要求同时提交并更新一条有效签约记录，不支持直接恢复历史签约
- 为后续“续签 / 换套餐 / 历史签约记录”保留扩展空间
- `total_count` 为持久化字段，但前端传值会被忽略，后端每次保存时按规则重算并覆盖

约束建议：

- 业务层保证同一客户仅 1 条 `active_flag = 1` 的记录
- 若数据库支持部分唯一索引，可增加“每个客户仅一个生效记录”的唯一约束

## 6. 接口设计

### 6.1 客户档案接口

接口前缀：`/api/customerProfile`

规则约束：

- 本期客户档案删除策略为：默认禁止物理删除，仅允许停用
- 如需彻底删除，只允许在未被任何其他业务引用的情况下由后端内部管理工具执行，不对普通业务接口开放
- 客户停用时，同步将当前生效签约置为失效

#### `GET /api/customerProfile`
分页查询客户档案，支持条件：

- 编号
- 姓名
- 手机号
- 父套餐
- 子套餐
- 状态

#### `GET /api/customerProfile/{id}`
查询客户详情，返回：

- 主档信息
- 地址列表
- 当前套餐信息

建议返回结构：

```json
{
  "id": 1,
  "customerCode": "A001",
  "customerName": "张三",
  "phone": "138xxxx",
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
    "parentPackageName": "月子餐",
    "childPackageId": 11,
    "childPackageName": "两荤一素",
    "breakfastCount": 10,
    "lunchDinnerCount": 20,
    "totalCount": 30,
    "startDate": "2026-03-25",
    "endDate": "2026-04-25",
    "activeFlag": true
  }
}
```

#### `POST /api/customerProfile`
新增客户档案，提交聚合数据。

#### `PUT /api/customerProfile`
编辑客户档案。

#### `PUT /api/customerProfile/{id}/status`
启用或停用客户档案。

规则：

- 启用时，必须同时存在 1 条有效签约记录
- 停用时，当前生效签约同步失效

> 本期不开放普通业务 `DELETE /api/customerProfile/{id}` 接口。

### 6.2 套餐字典接口

接口前缀：`/api/customerPackageCategory`

#### `GET /api/customerPackageCategory/tree`
获取父子树。

#### `GET /api/customerPackageCategory/parents`
获取父级列表，用于客户档案页联动选择。

#### `POST /api/customerPackageCategory`
新增父级或子级套餐。

#### `PUT /api/customerPackageCategory`
修改套餐。

#### `PUT /api/customerPackageCategory/{id}/status`
启用或停用套餐。

#### `DELETE /api/customerPackageCategory/{id}`
删除套餐。仅在无客户引用且无子节点时允许删除；其他场景仅允许停用。

### 6.3 编号生成接口

#### `GET /api/customerProfile/generateCode?parentPackageId=xxx`

逻辑：

- 根据父套餐 `code_prefix` 生成建议编号
- 返回格式必须满足“父套餐前缀 + 三位数字”
- 例如：`A001`
- 仅返回建议值，最终保存仍以后端唯一校验与格式校验为准
- 若父套餐不存在、未启用或未配置 `code_prefix`，接口返回明确错误码与提示信息

## 7. 保存模型设计

建议客户新增/编辑接口采用聚合提交方式：

```json
{
  "customerName": "张三",
  "phone": "138xxxx",
  "customerCode": "A001",
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
    "childPackageId": 11,
    "breakfastCount": 10,
    "lunchDinnerCount": 20,
    "startDate": "2026-03-25",
    "endDate": "2026-04-25"
  }
}
```

后端保存时：

- 自动计算 `total_count`
- 忽略前端传入的 `totalCount`，以后端重算结果为准
- 采用全量覆盖更新语义：前端提交的 `addresses` 与 `packageInfo` 视为客户当前完整状态
- 编辑时如果某个地址槽位未提交，则视为清空该槽位；但必须保证 `DEFAULT`、`WORKDAY`、`WEEKEND` 三个槽位中至少保留一个有效地址
- `packageInfo` 每次整体覆盖当前签约记录，不支持局部 patch
- 事务性写入主档、地址、套餐三部分数据

## 8. 后端校验规则

保存客户档案时需校验：

- 编号不能为空、格式必须匹配所选父套餐前缀 + 三位数字，且全局唯一
- 编号建议值按“当前父套餐前缀下最大流水号 + 1”生成；停用客户编号仍占用号段，不回收空洞
- 人工录入较大编号后，后续建议编号按最新最大流水号继续递增
- 姓名不能为空，长度建议不超过 50 个字符
- 手机号必填，校验中国大陆手机号格式
- 父套餐不能为空，且必须为启用状态的父级节点
- 子套餐不能为空，且必须为启用状态的子级节点
- 子套餐必须属于所选父套餐
- `DEFAULT`、`WORKDAY`、`WEEKEND` 三个地址槽位中至少有一个地址明细非空，且地址类型不能重复
- 已填写的地址明细不能为空字符串，长度建议不超过 200 个字符
- 早餐数与午餐+晚餐数不能同时为空
- `start_date`、`end_date` 必填，且 `end_date >= start_date`
- `total_count` 由后端自动计算
- 过敏食物标签提交前去重、裁剪空白，并做大小写与全半角规范化
- 医嘱要求长度建议不超过 500 个字符

## 9. 前端页面设计

### 9.1 菜单规划

新增菜单：

- 客户档案管理
- 套餐分类管理

### 9.2 客户档案列表页

建议页面：`eladmin-web/src/views/customer/profile/index.vue`

列表字段：

- 编号
- 姓名
- 手机号
- 默认地址
- 父套餐
- 子套餐
- 早餐数
- 午餐+晚餐数
- 总份数
- 签约开始日期
- 签约结束日期
- 孕周
- 状态
- 创建时间

筛选条件：

- 编号
- 姓名
- 手机号
- 父套餐
- 子套餐
- 状态

### 9.3 客户档案编辑弹窗

建议拆成 3 个区块：

#### 基础信息

- 编号
- 姓名
- 手机号
- 孕周
- 过敏食物
- 医嘱要求

#### 地址信息

- 默认地址（可选）
- 工作日地址（可选）
- 周末地址（可选）

规则提示：

- 三个地址中至少填写一个

#### 套餐信息

- 父套餐
- 子套餐
- 早餐数
- 午餐+晚餐数
- 总份数（自动计算，只读）
- 签约开始日期
- 签约结束日期

### 9.4 套餐分类管理页

支持：

- 维护父级套餐
- 维护子级套餐
- 配置父级 `code_prefix`
- 启停用
- 排序

规则补充：

- `category_code` 全局唯一
- 父级 `code_prefix` 全局唯一，限定为单个大写英文字母
- 已被客户引用的父级或子级套餐禁止删除
- 停用父级套餐前，必须先停用其所有子级套餐

### 9.5 前端交互细节

- 选择父套餐后：
  - 自动加载子套餐
  - 自动调用编号生成接口，回填建议编号
- 编号输入框允许人工修改，但必须符合当前父套餐前缀格式
- 若用户已手工修改编号，再切换父套餐，则前端重新获取新父套餐对应的建议编号并提示用户确认
- 过敏食物使用 `el-select` 的 `multiple + filterable + allow-create`
- 总份数前端实时展示，提交后以后端计算结果为准

## 10. 实施顺序

### Phase 1：数据库与后端基础

完成：

- 4 张表建表脚本
- 实体 / DTO / Mapper / XML / Service / Controller
- 基础 CRUD
- 编号生成接口

### Phase 2：套餐分类管理

完成：

- 套餐树管理页
- 父子套餐维护
- 编号前缀配置

### Phase 3：客户档案管理页

完成：

- 列表
- 新增 / 编辑
- 详情
- 地址录入
- 套餐联动
- 编号自动建议

### Phase 4：联调与验证

重点验证：

- 编号唯一性
- 父子套餐关联正确
- 地址类型约束正确
- 总份数计算正确
- 早餐数或午餐+晚餐数单独填写场景可用

## 11. 迁移策略

本期不迁移旧表数据。

原因：

- 新模块明确独立建设
- 不改现有 `meal/customerDietaryRestrictions`
- 避免新旧模型混淆

后续如有需要，再补“旧客户导入新档案”的一次性迁移工具。

## 12. 风险与应对

### 12.1 编号并发冲突

风险：多人同时新建客户时，建议编号可能重复。

应对：

- `customer_code` 建唯一索引
- 后端保存时再次校验唯一性
- 冲突时提示前端重新获取建议编号

### 12.2 套餐删除引用问题

风险：套餐已被客户引用后被删除。

应对：

- 普通业务接口仅允许停用，不做硬删除
- 已被客户引用的套餐禁止删除
- 删除前必须检查引用关系与子节点关系

### 12.3 过敏食物标签口径不统一

风险：自由输入会产生近义标签。

应对：

- 本期先接受自由录入
- 后续如需要，再抽离过敏标签字典

### 12.4 新旧客户体系并行

风险：短期内会存在两套客户数据。

应对：

- 明确新模块用于建档与签约
- 旧模块继续服务排餐
- 后续再根据业务决定是否做同步或替换

## 14. 变更记录

- 根据用户最新确认，地址规则调整为：默认地址、工作日地址、周末地址三者中至少填写一个，不再要求默认地址必填。

## 13. 最终建议

本次按以下边界落地：

- 新模块：`customer/profile`
- 新增菜单：客户档案管理、套餐分类管理
- 本期只做档案与套餐，不做金额
- 不改现有 `customerDietaryRestrictions`
- 不做排餐联动

这套方案能在不影响现有 `meal` 业务的前提下，独立沉淀更规范的客户档案能力，并为后续金额模块与更完整的客户生命周期管理预留清晰扩展点。
