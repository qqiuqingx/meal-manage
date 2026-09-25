# Phase 03 客户详情编辑与验收

## 执行前规则检查

重新读取当前生效的 `AGENTS.md`，以及客户业务与档案接口文档。

## 目标

让配送电话信息在客户详情可见、编辑后保留，并完成文档及针对性验证。

## 前置依赖

依赖 Phase 01 字段契约与 Phase 02 导入值；需读取两阶段交付记录。

## 本阶段实施约束

接口只做可选字段扩展；旧调用方省略字段时保持现有值。前端沿用现有客户表单，不增加新的权限或依赖。

## 涉及文件与步骤

- 更新客户详情/保存 DTO、`CustomerProfileServiceImpl` 与客户编辑表单。
- 更新客户业务说明及档案 API 文档，标明两个电话来源、字段与迁移要求。
- 运行相关 Maven 测试、前端 ESLint、`git diff --check`。

## 验证方式与完成标准

详情返回新字段，编辑不丢失，明确报告测试结果和 SQL 尚未执行的部署前置条件。

## 状态

complete

## 阶段交付记录

- 客户详情/保存 DTO 与 `CustomerProfileServiceImpl` 已接入 `deliveryPhoneInfo`；旧请求省略字段保留原值，空字符串可清空。
- 客户编辑表单显示可编辑的多行配送电话信息；客户业务说明和档案 API 文档已同步。
- 相关 Maven 定向测试 28 项通过；前端 `index.vue` ESLint 与 `git diff --check` 通过。
- 整个 `CustomerProfileServiceImplTest` 类另有 8 个现有排餐/试餐测试失败，本需求新测的 2 项通过；本次未运行数据库迁移。
