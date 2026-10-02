# Phase 01 人工移除记忆闭环

## 执行前规则检查

读取 status.yaml、00-overview.md 和本阶段文件；完整检查用户级及本阶段文件目录链 AGENTS.md。规则变化先更新本阶段和 overview。确认前置 Phase 已完成，读取其阶段交付记录和本阶段依赖产物。

## 目标

两个人工编辑入口能记住移除、主动选择能恢复、双字段同事务保存；既有权限、审计、D列行为和客户其他字段保留。

## 前置依赖

无

## 输入与输出

输入：已审阅设计、overview 公共契约，以及上述前置阶段交付物。
输出：本阶段范围内可独立验证的生产能力、相关测试及下一阶段依赖的稳定契约。

## 本阶段实施约束

Java 8 / fastjson2、现有分层与权限审计；保留 D 列只录原文和无关未提交工作。方法补充业务注释，不加测试专用生产逻辑，不新增依赖。禁止清表/无条件删除，不自动运行迁移、部署或提交。涉及客户写入沿用行锁与 overview 的锁顺序；字段和 API 变化仅限已审阅设计。

## 涉及文件与责任

- 新增限定结构迁移 eladmin/sql/20261002_customer_diet_restriction_exclusions.sql，只新增可空 JSON 列，禁止执行迁移或修改无关已有脚本。
- 修改 CustomerProfile、CustomerProfileMapper.java/XML；增加内部排除集合与 JSON 映射，普通公开响应不暴露该字段。
- 新增专用 CustomerDietExclusionsTypeHandler 和 CustomerDietRestrictionService（及 impl），承担稳定键校验、人工删除/恢复和原子合并的真实生产职责。
- 修改 CustomerProfileServiceImpl.update 与 CustomerOrderServiceImpl 的禁忌行内更新，两种人工入口都在客户锁下原子写对象和排除集合；订单维持旧值并发比较与原有审计。
- 只存有效五类对象稳定键；人工选回解除该键，不提供客户端直接清空排除元数据的字段。

## 实施步骤

1. 加入迁移、实体映射及内部字段的序列化限制，NULL 按空集合处理；更新客户 SQL 初始化定义时隔离原有未提交 hunk。
2. 在共享服务中实现移除集合与重新选择集合计算；替换现有定向禁忌更新方法契约，不增加弃用方法兼容重载。
3. 两个编辑入口读取/锁定最新客户，保存对象和排除结果；锁顺序满足 overview。
4. 扩展已有订单行内审计记录，不依赖测试环境用户名；普通档案编辑沿用现有日志权限。
5. 覆盖删除、清空、重新添加、历史引用保留、未传字段保持以及并发冲突。

## 验证方式

新迁移文件与 CustomerProfileMapperMapping/类型处理器测试；CustomerProfileServiceImplTest、CustomerOrderServiceImplTest 及共享禁忌服务测试。验证响应不暴露或接受内部字段。不要连接业务数据库。

## 完成标准

两个人工编辑入口能记住移除、主动选择能恢复、双字段同事务保存；既有权限、审计、D列行为和客户其他字段保留。

## 状态

complete

## 阶段交付记录

已完成迁移脚本、内部排除字段/处理器、原子 Mapper、两种人工编辑入口与审计。无状态集合规则使用 CustomerDietRestrictionUtil，避免引入无依赖 Spring 服务及重复构造器注入；后续自动更新复用同一规则。Java 8/Maven 3.5.2 的 CustomerDietRestrictionTest、CustomerProfileServiceImplTest、CustomerOrderServiceImplTest、CustomerDietTypeHandlerTest、CustomerOrderInlineMapperStringBindingTest 共 81 项通过。补齐客户服务测试缺失的登录上下文仅在测试代码内进行。迁移未执行，SQL 初始化定义只追加本字段，原有修改保留。
