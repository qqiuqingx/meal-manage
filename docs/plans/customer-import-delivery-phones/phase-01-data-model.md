# Phase 01 数据模型与增量 SQL

## 执行前规则检查

重新读取当前生效的用户级与仓库级 `AGENTS.md`，确认目标文件无更具体规则。

## 目标

为客户主档增加可空的配送电话信息字段，并提供现有库增量 SQL。

## 前置依赖

无。输入为现有 `customer_profile` 表定义与实体/Mapper 映射；输出为数据库和 Java 字段契约。

## 本阶段实施约束

只新增可空字段，不覆盖 `phone`；不执行 SQL、不清理数据、不新增依赖。

## 涉及文件与步骤

- 更新 `eladmin/sql/customer-profile.sql` 基础建表定义。
- 新增 `eladmin/sql/20260925_customer_profile_delivery_phone_info.sql` 增量迁移。
- 更新 `CustomerProfile` 实体及 `CustomerProfileMapper.xml` 结果映射。

## 验证方式与完成标准

检查 SQL 字段名、类型与实体映射一致；`git diff --check` 通过。记录实际文件及迁移前置要求。

## 状态

complete

## 阶段交付记录

- 已在 `CustomerProfile`、`CustomerProfileMapper.xml` 与基础 DDL 中增加 `delivery_phone_info`，类型为可空 `TEXT`。
- 新增 `eladmin/sql/20260925_customer_profile_delivery_phone_info.sql`，需在部署新应用前执行；本次未连接或修改数据库。
- `git diff --check` 通过；字段名与映射已核对。
