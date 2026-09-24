# Phase 01 订单暂停状态与生效日期

## 执行前规则检查

重新读取当前适用 AGENTS.md、订单业务说明和订单接口文档，核对订单保存与查询调用方。

## 目标

订单第一次从状态 1 改为 4 时自动记录暂停生效日期，并让该日期通过订单查询可读。

## 前置依赖

无。用户已确认暂停日期取状态转换当天。

## 输入与输出

输入：现有订单表、CustomerOrderSaveDto 和订单保存流程。输出：可空数据库列、迁移 SQL、订单实体/DTO/状态转换逻辑、可操作的暂停 UI。

## 本阶段实施约束

新增列和 DTO 字段保持兼容；请求中的暂停日期不作为可信写入来源。仅 1→4 时自动赋值；暂停期间其他编辑不覆盖；恢复进行中时清空。未建立历史迁移推断。不得覆写无关订单数据。

## 涉及文件

`eladmin/sql/` 增量脚本、`CustomerOrder.java`、`CustomerOrderSaveDto.java`、`CustomerOrderServiceImpl.java`、`OrderForm.vue`、订单列表、对应测试与订单接口/业务文档。

## 实施步骤

1. 新增 `pause_effective_date DATE NULL`；实体和查询 DTO 回传。
2. 校验状态变更并在服务端写入/保持/清空暂停日期；允许订单页面选择暂停和恢复。
3. 用实际保存路径测试状态转换和重复编辑。

## 验证方式与完成标准

Java 8/Maven 定向测试、前端相关测试或 lint、`git diff --check`；订单 1→4 日期落库，4→4 不变，4→1 清空，原有状态不受影响。完成后记录交付摘要和下游字段契约。

## 状态

completed

## 阶段交付记录

已新增并应用 `customer_order.pause_effective_date` 增量 SQL；订单实体与详情响应返回日期，订单状态 1→4 自动写当天、4→4 保持、4→1 清空，订单页面允许暂停及恢复。更新了订单业务/API 文档。Java 8 + Maven 3.5.2 定向状态转换测试和前端 lint 通过。Phase 02 使用 `CustomerOrder.getPauseEffectiveDate()`，只对状态 4 应用该截止日。
