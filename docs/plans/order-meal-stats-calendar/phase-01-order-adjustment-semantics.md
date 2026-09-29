# Phase 01：订单停餐与实际排餐语义闭环

## 执行前规则检查

读取 `status.yaml`、`00-overview.md`、`contracts.md` 和当前生效用户级/仓库/目标目录 AGENTS.md。记录当前未提交改动，重点保护已有客户资料导入测试和 SQL 的工作区修改。核对 `customer_meal_schedule_addition` 实际表结构是否已具备 quantity/soup_quantity（只读元数据，使用获准的测试环境）；计划阶段检查的是仓库 DDL，不代表线上已部署。

## 目标

建立“客户统一停餐优先＋当前订单数量覆盖”的计算与生成规则，使当前订单的 0 份确实不生成餐次，恢复默认可重新计算，其他订单不受影响。

## 前置依赖

无实施 Phase 依赖。输入是用户已确认范围和 contracts 中数量/优先级约定，不需要再次征求相同业务决定。

## 输入与输出

输入：现有订单数量分配工具、调整表、排餐生成流程与对应测试。

输出：支持 0 份的现有数据模型语义；按订单查询/同步覆盖的 Mapper；有效计划格计算；生成侧回归测试。旧页面在本阶段尚未切换，既有正数记录和客户统一停餐继续有效。

## 本阶段实施约束

- 不新增表或字段，不重命名现有表，不执行数据迁移；部署检查若发现缺少既有数量版本字段，记录为环境前置依赖，不能假定本次新建即可覆盖旧数据。
- 客户统一停餐保持最高优先级；订单覆盖数据原样保留，即使当时被统一停餐遮蔽。
- 复用 `CustomerMealStatsScheduleUtil`，避免另建一套预览算法与生成算法。
- 负数非法，0 与缺失记录严格区分；旧数据库缺省/已有正数语义保留，不新增旧 API 回退分支。
- 新增/修改方法补注释；实体新加字段必须有业务说明，本阶段原则上无需新增实体字段。

## 涉及文件

修改：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerMealScheduleAddition.java`（语义注释）
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerMealScheduleCellDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerMealScheduleAdditionMapper.java`
- `eladmin/eladmin-system/src/main/resources/mapper/CustomerMealScheduleAdditionMapper.xml`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/util/CustomerMealStatsScheduleUtil.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/MealPlanServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/util/CustomerMealStatsScheduleUtilTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/meal/service/impl/MealPlanServiceImplTest.java`

查阅但不机械修改：`eladmin/sql/customer_meal_schedule_addition.sql`、`eladmin/sql/20260924_customer_meal_calendar_quantity.sql`、客户档案导入 writer、`CustomerOrderServiceImpl.findValidOrdersForDate` 等日期候选查询的实际调用方。若另有生产路径直接生成餐次且绕过公共计算，再纳入最小必要修改。

## 实施步骤

### 1. 核实调整记录的全部读写方

搜索 Mapper 方法、`quantity` 判定、`buildOrderQuantities`、`resolveTargetQuantity`、客户排除日期过滤。区分导入的订单正数计划、统计日历编辑、实际生成；记录调用清单。订单日期餐次唯一键含 deleted，应沿用恢复软删除旧行的策略，避免反复取消/恢复产生唯一键冲突。

### 2. 建立订单范围的数据访问

在现有 Mapper 增加 `selectActiveByOrderIdAndDateRange` 和 `softDeleteMissingByOrderIdAndDateRange`；写入的 where 明确含 `order_id`、起止日期、`deleted=0`。orderId 和日期范围由 Service 校验必填，keepIds 为空只能清理该订单该月。保留尚被旧页面调用的方法，待 Phase 04 核实调用后删除。

### 3. 完善 0 份计算和来源标记

`buildOrderQuantities` 已有 quantity<=0 跳过逻辑，应明确负数拒绝、0 为合法取消；不要把 0 记录过滤掉后重新按基础 1 份计算。0 不扣餐池额度，后续日期按现有累计规则分配。

月格构建区分 `customerExcluded` 与 `orderExcluded`，`manualOverride` 应由覆盖记录是否存在决定，不能再由 quantity>0 决定。支持早餐的订单格，确保早餐独立、午晚共享预算。订单 A 的覆盖不能混入 B，即使日期和餐次相同。

恢复默认计算保留其他格和历史月份的覆盖，只移除目标格覆盖；统一停餐仍生效。校验导入历史核销基数只扣一次，暂停来源计划仍可查看且不可生成。

### 4. 同步生成链路

检查并更新 `loadManualAdditionsByOrder`、`mergeManualAdditionOrders`、`loadValidOrders`、`resolveTargetQuantity` 的组合行为。0 覆盖应阻止该订单该格生成，不能加入人工正数候选，也不能抛异常让同一批其他订单排餐失败。所有生成入口最终使用相同有效目标数量，不在多个方法重复定义优先级。

重复生成仍保留已核销份；客户统一停餐、暂停、订单日期/餐次和购买数限制继续生效。这里不更改菜单选择、换菜或自动核销规则。

### 5. 完成最小生成闭环测试

通过真实工具方法与 `MealPlanServiceImpl.generateMealPlan` 公共入口覆盖：保存模型中的 A 订单 0 份导致 A 不生成、同日 B 正常生成；移除覆盖后 A 恢复默认；正数多份及含汤规则保持；统一停餐压过正数覆盖。使用测试数据或 mock 依赖，不向生产代码增加测试接口。

## 验证方式

- 定向运行 `CustomerMealStatsScheduleUtilTest`、`MealPlanServiceImplTest` 中相关用例；调用真实生产计算路径。
- 必测跨月历史 0 份释放餐池、同客户多单同日、早餐/午晚分池、导入基数、暂停、0 份不污染人工新增候选。
- Mapper SQL 核对订单与月份谓词及空 keepIds；涉及数据库行为的验证留给受控 MySQL 集成测试，不用 mock 断言冒充 SQL 隔离验证。
- 按本地 Maven 技能和开发指南编译 eladmin-system 及依赖；具体命令见 Phase 04，不在本阶段启动整套应用。

## 完成标准

- 0 份保存模型、月格和实际生成三者语义一致，正数既有行为测试通过。
- A/B 订单隔离、统一停餐优先及跨月累计均有回归覆盖。
- 无新业务表/字段、无历史数据重写、无仅为测试存在的生产入口。
- 交付新增 Mapper 方法和 cell 字段的准确签名，供 Phase 02 使用。

## 状态

complete

## 阶段交付记录

### 实际修改文件

- `CustomerMealScheduleAddition.java`：quantity 注释明确零份是当前订单单元格停餐。
- `CustomerMealScheduleCellDto.java`：餐次包含早餐，并增加 `customerExcluded`、`orderExcluded` 来源标记；保留旧 `excluded` 供旧页面切换前兼容。
- `CustomerMealScheduleAdditionMapper.java` 与 XML：新增按订单及日期范围读取、软删除缺失覆盖的方法。
- `CustomerMealStatsScheduleUtil.java`：零份作为显式覆盖保留且不扣餐池；拒绝负数和非法含汤量；日历格包含早餐并按覆盖记录存在性标记人工调整。
- `MealPlanServiceImpl.java`：零份不会作为人工新增候选，也不会中止同日其他订单；正数越购买餐数仍拒绝。
- 对应工具及排餐服务测试新增零份跨日预算释放、跨月累计、早餐与午晚池隔离、来源标记及同客户多订单生成覆盖。

### Phase 02 使用的契约

- 数量覆盖表继续复用，`quantity=0` 是已保存的当前订单停餐；缺少覆盖仍按基础计划计算，`quantity<0` 非法。
- 月格的 `manualOverride` 表示是否存在覆盖记录；客户统一停餐与订单零份停餐分别使用 `customerExcluded`、`orderExcluded`。
- Mapper 签名：`selectActiveByOrderIdAndDateRange(Long orderId, LocalDate startDate, LocalDate endDate)`；`softDeleteMissingByOrderIdAndDateRange(Long orderId, LocalDate startDate, LocalDate endDate, List<Long> keepIds)`。空 keepIds 只会删除 where 中指定订单及日期范围内的记录。
- 排餐生成最终仍经 `CustomerMealStatsScheduleUtil.buildOrderQuantities`；生成入口 `MealPlanServiceImpl.generateMealPlan` → `doGenerateMealPlan` → `loadValidOrders`。零份不会被人工新增合并逻辑重新绑定到其他订单。
- 仓库建表及数量升级 DDL 已包含 `quantity`、`soup_quantity`。未连接数据库核验实际环境元数据；Phase 02 不依赖新表或新字段。

### 验证及遗留事项

- `mvn -pl eladmin-system -am -Dtest=CustomerMealStatsScheduleUtilTest,MealPlanServiceImplTest#shouldSkipZeroQuantityOrderAndGenerateAnotherOrderForTheSameCustomer -Dsurefire.failIfNoSpecifiedTests=false test`：11 个测试通过。
- 曾运行整类 `MealPlanServiceImplTest`，当前有 16 个失败和 5 个错误；失败集中在未修改的旧测试及既有测试反射签名，旧夹具从 3 月开始且购买餐数不足以到 4 月。新增用例已使用符合累计餐数的有效日期并单独通过。Phase 04 再按修改影响核对既有失败，不将该次整类结果记为通过。
- Mapper SQL 已静态检查包含 order_id、起止日期及 deleted 条件；无 MySQL 集成环境，本阶段未执行数据库 SQL 集成测试。
- 暂无阻塞 Phase 02 的代码问题。保存事务、revision、订单范围清理与生成结果级联由 Phase 02 实现。
