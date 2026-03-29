# 套餐管理数据结构重构设计

**日期**: 2026-03-29
**状态**: 已确认

## 背景

现有 `customer_package_category` 表通过 `level` + `parent_id` 模拟二级树形结构，耦合度高、扩展性差。
将其重构为 3 张独立表，职责清晰，并为后续订单配餐自动校验打下基础。

## 数据模型

### 表 1: `parent_package` — 父套餐

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| package_code | VARCHAR(50) | NOT NULL, UNIQUE | 套餐编码 |
| prefix | CHAR(1) | NOT NULL, UNIQUE | 单个大写字母，用于拼接客户编号，如 A/B/C |
| package_name | VARCHAR(100) | NOT NULL | 套餐名称 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1=启用, 0=停用 |
| remark | VARCHAR(500) | | 备注 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

### 表 2: `sub_package` — 子套餐（含规则字段）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| sub_package_code | VARCHAR(50) | NOT NULL, UNIQUE | 子套餐编码 |
| sub_package_name | VARCHAR(100) | NOT NULL | 子套餐名称 |
| meat_count | INT | NOT NULL, DEFAULT 0 | 荤菜数量 |
| veg_count | INT | NOT NULL, DEFAULT 0 | 素菜数量 |
| include_soup | TINYINT | NOT NULL, DEFAULT 0 | 是否含汤：1=是, 0=否 |
| include_rice | TINYINT | NOT NULL, DEFAULT 1 | 是否含米饭：1=是, 0=否 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1=启用, 0=停用 |
| remark | VARCHAR(500) | | 备注 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

### 表 3: `parent_package_sub` — 父子关联关系

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| parent_package_id | BIGINT | NOT NULL | 父套餐ID |
| sub_package_id | BIGINT | NOT NULL | 子套餐ID |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1=启用, 0=停用 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| | | UNIQUE(parent_package_id, sub_package_id) | 防止重复关联 |
| | | INDEX(parent_package_id) | |
| | | INDEX(sub_package_id) | |

## 关键约束

- `parent_package.prefix` — 单个大写字母，全局唯一（继承旧有逻辑，用于客户编号前缀）
- `parent_package.package_code` — 全局唯一
- `sub_package.sub_package_code` — 全局唯一
- `parent_package_sub` 上 `(parent_package_id, sub_package_id)` 联合唯一

## 业务规则

1. **Prefix 唯一性**：新建/编辑父套餐时，prefix 不能与其他父套餐重复
2. **停用校验**：停用父套餐前，需先停用其所有子套餐关联
3. **删除父套餐**：服务层自动清理 `parent_package_sub` 关联记录，再删父套餐
4. **删除子套餐**：删除前检查 `customer_order` 表的 `parent_package_id` 和 `child_package_id` 是否有引用，有则报错提示
5. **子套餐新增**：在父套餐展开列表中直接新增，无需再选父套餐

## 前端交互

- 套餐管理页面：父套餐列表，点击行展开显示子套餐列表（树形折叠）
- 新增子套餐时，在子套餐表单中填写荤/素数量、是否含汤/米饭（与子套餐一并保存）
- 编辑子套餐时，同步编辑规则字段

## 后续计划

- 订单配餐环节：下单时根据 `customer_order` 表的 `parent_package_id` / `child_package_id` 关联查询 `sub_package` 规则，自动校验配餐是否满足荤素数量、是否含汤/米饭等要求

## 涉及文件

**后端新建**:
- `modules/customer/package/domain/ParentPackage.java`
- `modules/customer/package/domain/SubPackage.java`
- `modules/customer/package/domain/ParentPackageSub.java`
- `modules/customer/package/domain/dto/SubPackageDto.java`
- `modules/customer/package/domain/dto/ParentPackageDto.java`
- `modules/customer/package/domain/dto/ParentPackageQueryCriteria.java`
- `modules/customer/package/mapper/ParentPackageMapper.java`
- `modules/customer/package/mapper/SubPackageMapper.java`
- `modules/customer/package/mapper/ParentPackageSubMapper.java`
- `modules/customer/package/service/ParentPackageService.java`
- `modules/customer/package/service/impl/ParentPackageServiceImpl.java`
- `modules/customer/package/service/SubPackageService.java`
- `modules/customer/package/service/impl/SubPackageServiceImpl.java`
- `modules/customer/package/rest/ParentPackageController.java`
- `resources/mapper/package/ParentPackageMapper.xml`
- `resources/mapper/package/SubPackageMapper.xml`
- `resources/mapper/package/ParentPackageSubMapper.xml`

**后端修改**:
- `modules/customer/profile/domain/CustomerProfilePackage.java` — 更新关联（字段名不变，引用的 ID 来源切换）
- `modules/customer/profile/service/impl/CustomerProfilePackageServiceImpl.java` — 订单中套餐选择器数据源切换
- `modules/order/service/impl/OrderServiceImpl.java` — 套餐选择器数据源切换

**前端新建**:
- `views/customer/package/index.vue` — 套餐管理页面
- `api/customer/package.js` — API 调用

**前端修改**:
- `api/customer/packageCategory.js` — 路由路径调整
- `views/customer/packageCategory/index.vue` — 暂时保留或移除

**数据库**:
- `sql/package-management.sql` — 3 张新表 DDL + 种子数据（健康餐/营养餐）
- 删除旧表 `customer_package_category`（需先确认无遗留引用）

**菜单**:
- `sql/package-management.sql` — 更新 sys_menu 路由/权限标识
