# Phase 01 订单自身字段即时更新与可靠审计

## 执行前规则检查

- 重新读取当时生效的用户级、仓库及目标路径 `AGENTS.md`；若规则变化，先修订本阶段。
- 检查订单业务说明、订单服务/Mapper、现有测试及数据库迁移命名；确认无同名审计表、无外部调用方依赖拟变更的契约。

## 目标

完成订单自有字段的单字段更新、业务校验及事务内前后状态审计，形成最小可验证的后端闭环。

## 前置依赖

- 无。输入是 `00-overview.md` 的公共契约，以及当前订单、客户和套餐表结构。

## 输入与输出

- 输入：订单列表/详情字段、现有 `CustomerOrderServiceImpl` 更新规则、订单表与 `sys_log` 机制。
- 输出：审计表迁移脚本、字段级 PATCH 接口、订单字段处理和针对性后端测试；供 Phase 02 复用同一审计写入与冲突处理契约。

## 本阶段实施约束

- 新表是数据库结构新增；先核对发布脚本惯例和表名冲突。禁止在测试中清空订单、档案或审计表。
- 只更新白名单中的订单字段：主/副/素菜数、汤数、排餐模式、自定义菜单图片、早餐数、午晚数、状态。完整 `PUT` 保留。
- `soupCount` 行内接口只接受 `0/1`；计数均为非负整数，排餐模式使用现有四种值；图片路径按现有上传结果格式校验，不接受任意外部地址。
- 使用 `customerOrder:edit` 授权与操作日志；终态订单拒绝更新。复用现有状态、餐数与暂停日期规则，不放宽验证。
- 每次成功写入一条追加审计；记录派生字段和状态副作用。业务更新或审计任一失败时整体回滚。无变化的请求不写审计。

## 涉及文件

预计新增：`eladmin/sql/customer_order_inline_audit.sql`、`eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/domain/CustomerOrderInlineAudit.java`、`eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/domain/dto/CustomerOrderInlineUpdateDto.java`、`eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/mapper/CustomerOrderInlineAuditMapper.java`，以及订单 Controller/Service 的针对性测试。

预计修改：订单模块的 `CustomerOrderController.java`、`CustomerOrderService.java`、`CustomerOrderServiceImpl.java`、`CustomerOrderMapper.java` 及必要的 `CustomerOrderMapper.xml`，以及 `eladmin/doc/apidoc/客户订单管理接口文档.md` 和 `eladmin/doc/business/订单管理业务说明.md`。类名沿现有模块风格确定，避免新增通用字段更新框架。

## 实施步骤

1. 核对 `customer_order`、`customer_profile` 的主键与现有 DDL 风格；新增审计表，包含主键、订单/客户 ID、字段键、操作人、创建时间、前后状态 JSON，并建立按订单 ID/时间查询的索引。写明所有列的业务注释。
2. 定义单字段请求及白名单解析，严格区分缺少 `expectedValue` 与预期值为 `null`；按字段校验 JSON 类型、枚举和长度。新增 PATCH Controller，保持 JWT、`customerOrder:edit`、`@Log` 约定。
3. 事务中按 ID 读取并锁定订单，对比目标旧值；不匹配返回冲突。用定向更新避免覆盖其他订单字段。早餐/午晚数改变时按现有规则校验已核销数、计算剩余数；状态改变时维护 `pauseEffectiveDate`。
4. 将实际受影响的字段按实体 ID 组成前后 JSON，连同操作人写入审计表。无变化请求返回成功但不写日志；错误请求不产生审计记录。
5. 测试字段白名单、权限、类型、终态、冲突、计数边界、状态暂停/恢复、审计内容与写审计失败时事务回滚。直接验证生产接口及服务路径，不加测试专用入口。

## 验证方式

- 按 `docs/development/本地开发指南.md` 和 Maven 技能选对 Java 8/Maven，从 `eladmin/` 编译 `eladmin-system` 及必要依赖，运行订单 Controller/Service 定向测试。
- 审查迁移 DDL、索引、注释与 `git diff --check`；在隔离测试库按唯一测试标识验证审计查询，并仅按本次主键清理测试数据。

## 完成标准

- 订单自有字段可逐字段保存；未开放字段、旧值冲突、非法状态和不合法餐数被拒绝。
- 修改前后实际字段（含派生值）可按订单 ID 在审计表追溯，写入失败不会留下无审计的业务更新。
- 原完整编辑接口和既有调用方继续可用，相关测试通过。

## 状态

completed

## 阶段交付记录

- 新增 `eladmin/sql/customer_order_inline_audit.sql`、`CustomerOrderInlineAudit`、`CustomerOrderInlineAuditMapper` 和 `CustomerOrderInlineUpdateDto`；`PATCH /api/customer/order/{id}/inline` 只接受显式提供的 `field`、`value`、`expectedValue` 三键，成功返回 204，旧值冲突返回 409。
- 订单通过 `SELECT ... FOR UPDATE` 锁行，再由 Mapper 白名单 SQL 定向修改一个订单字段；餐数同时更新剩余数，状态同时更新暂停日期。事务内追加包含实体 ID、变更字段、操作人及实际更新时间的前后状态 JSON。无变化不写审计；写审计异常向外抛出并触发事务回滚。
- Phase 02 沿用同一请求、锁行、审计实体和事务边界；新增档案字段时不能扩展为整单更新，也不能变更 `PUT /api/customer/order`。
- 验证：Java 8 / Maven 3.5.2 定向执行 `CustomerOrderServiceImplTest` 和 `CustomerOrderControllerInlineUpdateTest`；两类相关测试合计 22 个用例通过。隔离数据库与真实 SQL 事务回滚未验证，生产 DDL 未执行。
