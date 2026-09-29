# Phase 02：单订单排餐日历读取与安全保存

## 执行前规则检查

加载 overview、contracts、status、Phase 01 交付记录及当前规则链。复查现有客户维度保存 API 的调用方和授权规则；本阶段新增订单接口，旧客户端切换及无调用路径删除在后续阶段完成，不发布中间版本。

## 目标

提供能独立验证的“读取当前订单日历→调整→保存→重新读取/生成”的后台闭环，所有写入限定订单和月份，并保护已核销事实。

## 前置依赖

Phase 01 完成；依赖其 0 份生效规则、单订单 Mapper 查询/同步能力、cell 来源标记及生成回归结果。若这些契约变更，先同步 contracts，不能在本阶段重新定义数量语义。

## 输入与输出

输入：订单、客户统一停餐、订单历史覆盖、当前月排餐进度、核销/已排统计。

输出：订单日历 GET/PUT、DTO、`CustomerMealStatsService/Impl` 的订单日历职责、修订冲突处理、单订单减份清理及真实保存路径测试。

## 本阶段实施约束

- orderId 是唯一写入身份；customerId 从订单读取，禁止选择其他可用订单、静默忽略越界项或自动改绑。
- 保存当前月完整覆盖时不得触碰同客户其他订单、该订单其他月份或客户 `excluded_dates`。
- 既有数量预算、导入核销基数、暂停/未指定餐次和已核销保护必须保留；早餐也受校验。
- Service 定向迁移现有日历职责，不复制整段实现，不做客户建档/导入模块的无关重构。
- 必须有事务和数据库层未核销保护；仅有 revision 或前端禁用不算完成并发验证。
- 保留 JWT、`customerProfile:list/edit` 与 `@Log`；DTO 不返回金额字段。

## 涉及文件

新增（均位于 `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/`）：

- `service/CustomerMealStatsService.java`
- `service/impl/CustomerMealStatsServiceImpl.java`
- `domain/dto/CustomerOrderMealCalendarDto.java`
- `domain/dto/CustomerOrderMealCalendarSaveDto.java`
- `domain/dto/CustomerOrderMealCalendarOverrideDto.java`
- `domain/dto/CustomerOrderMealCalendarSaveResult.java`

修改：

- `.../customer/profile/rest/CustomerProfileController.java`
- `.../customer/profile/service/impl/CustomerProfileServiceImpl.java`（迁移日历公共职责；列表保留至 Phase 03）
- `.../customer/profile/mapper/CustomerMealScheduleAdditionMapper.java` 及对应 XML
- `.../customer/order/mapper/CustomerOrderMapper.java`（复用/明确订单行锁职责；不新增测试重载）
- `.../meal/mapper/MealPlanCustomerMapper.java` 及 `src/main/resources/mapper/MealPlanCustomerMapper.xml`
- `.../meal/service/MealPlanService.java`、`.../meal/service/impl/MealPlanServiceImpl.java`

新增测试：`src/test/java/me/zhengjie/modules/customer/profile/service/impl/CustomerMealStatsServiceImplTest.java`、`src/test/java/me/zhengjie/modules/customer/profile/rest/CustomerMealStatsControllerTest.java`。上述 `...` 均指 `eladmin/eladmin-system/src/main/java/me/zhengjie/modules`。

## 实施步骤

### 1. 建立严格请求/响应

按 contracts 实现 GET/PUT，保存请求必填月份、revision 和覆盖数组；嵌套校验日期/餐次/非负整数。数组缺失/null 拒绝，空数组只能清本单本月；不保留 quantityMode、客户排除数组或多订单列表参数。

服务端仅从 URL 订单取客户归属。共享资料从带 JSON TypeHandler 的客户查询取得，防止过敏/禁忌结构退化为字符串。每次打开日历重新读取，响应仅含该订单和选定月份。

### 2. 组装单订单日历

加载该订单从 startDate 到月末的有效覆盖，供跨月累计；进度查询新增 `order_id + dateRange` 限定。展示范围为当前月份：有效基础格、当前覆盖、已生成/核销事实的并集，不能因开始/结束日期后改而隐藏已有事实。

区分客户统一停餐和订单停餐；保留被统一停餐遮蔽的原始 overrides，避免完整快照保存时误删。暂停/未指定餐次展示来源计划并返回只读原因。列表加载后订单关闭时，详情可只读呈现已有事实，保存拒绝。

### 3. 建立修订和事务边界

revision 按 contracts 包含本单的计划依赖与数量校验事实，以稳定排序生成；不能依赖客户所有订单的 revision。保存事务内重新读取相关事实，expectedRevision 不一致返回 409，所有写入尚未发生。

复用现有行锁和计划锁，核实核销/订单行内编辑/生成的锁顺序，按稳定日期餐次顺序锁定涉及的排餐数据；在同单并发保存、保存并发核销/生成时保证最终判定有效。不要为单次页面改造引入新的分布式锁基础设施。

### 4. 校验下一版计划

先构造“历史月份原覆盖＋本月请求覆盖”的当前订单计划，校验：订单归属、状态、有效期、开始餐次、支持餐次、月份、重复键、数量、含汤范围、统一停餐格不可改。

再比较保存前后所有当前月格，目标量不得少于该格已核销量；预算分别计算早餐池、午晚池，考虑其他月份核销/成功已排未核销占用。移植现有允许减少历史超额计划的规则，并为“恢复默认后导致超额”运行同样校验。不得借用同客户另一单的余额。

### 5. 保存覆盖并清理减少的已生成份

按 `(orderId,date,mealType)` upsert/恢复软删除行；请求未保留的记录仅在该 orderId＋month 范围软删。事务中不写客户统一停餐。

更新覆盖时必须能把 `soup_quantity` 显式清为 NULL（恢复随订单含汤或设为 0 份）；核对 MyBatis-Plus 的空值更新策略，使用明确的定向更新，避免 `updateById` 忽略 null 后遗留旧含汤数。无变化记录不必重复更新审计字段。

对所有减量格（含明确 0 和累计顺延造成的减量）调用 `deleteExcessUnverifiedCustomerServingsForCalendarAdjustment(customerId, orderId, date, mealType, targetQuantity)`。该方法已支持 0；不能再调用整客户取消方法，也不能因格 excluded 跳过清理。

清理新增或复用具有真实业务职责的“按指定 ID 且未核销软删”SQL，条件至少含 deleted=0、is_verified=0/null；核对影响行数，若并发核销导致数量不符，整次保存回滚。关联菜品/换菜记录与汇总更新处在同一事务，不误删其他订单的核销份。增加/恢复只写计划，不自动排菜。

### 6. 完成接口与保存回归

测试从新 Service 公共方法及 Controller 进入，不访问私有方法复制计算。重点构造同客户 A/B 订单、A 前后月份、统一停餐遮蔽覆盖和已生成数据，断言记录身份及副作用范围，而不是只断言 mapper 被调用次数。

## 验证方式

- Controller：GET/list 权限、PUT/edit 权限、非法/空请求、orderId 路由、409 返回、金额字段不泄漏。
- Service：A 取消/恢复不影响 B；空数组只清 A 当月；越月和重复项整单拒绝；不能自动改绑；暂停/关闭后保存拒绝；统一停餐格原样保留、不可修改。
- 容量：早午晚分池、导入基数、跨月占用、仅减少超额允许、增加超额失败、恢复默认导致的超额失败。
- 清理：目标 0、3→1、失败份优先、已核销下限、B 已核销不妨碍 A 合法减量、异常完整回滚。
- 并发：同单陈旧 revision 冲突；不同订单合法保存不互相覆盖；已有排餐生成/核销在读取后变化时重新校验。实际事务/SQL行为在 Phase 04 隔离数据库中验证，单元 mock 不代替集成证明。

## 完成标准

- GET→PUT→GET 及后续生成使用同一订单与计划语义。
- 所有写入均被订单＋月份限定，客户统一停餐字段不写入。
- 已核销保护具备数据库更新条件与回滚验证，不依赖前端。
- 无跨订单兜底、隐式月份推断或旧请求自动兼容。
- 单订单日历契约和测试结果已记录，供 Phase 03 前端直接对接。

## 状态

complete

## 阶段交付记录

### 实际修改文件

- 新增 `CustomerMealStatsService`、`CustomerMealStatsServiceImpl`，承接本页面订单日历 GET/PUT；列表职责留待 Phase 03 迁移。
- 新增 `CustomerOrderMealCalendarDto`、`CustomerOrderMealCalendarSaveDto`、`CustomerOrderMealCalendarOverrideDto`、`CustomerOrderMealCalendarSaveResult`。
- `CustomerProfileController` 新增订单日历 GET/PUT 路由，分别使用 `customerProfile:list`、`customerProfile:edit`，PUT 保留 `@Log`。
- `CustomerMealScheduleAdditionMapper`/XML 增加按订单范围查询、软删除同步、含汤数显式置空更新和软删除恢复。
- `MealPlanCustomerMapper`/XML 增加订单日期范围进度、订单日期餐次生成结果、按餐次成功排餐统计和未核销条件软删除。
- `MealPlanServiceImpl` 的减份清理改用订单范围查询，并检查未核销 SQL 的实际影响行数；`MealVerificationServiceImpl` 核销及回退改为先锁订单再改排餐行。排餐生成按订单 ID 稳定顺序先锁候选/覆盖订单，再锁日期排餐计划。
- `CustomerMealStatsScheduleUtil` 的 `baseQuantity` 改为保留客户统一停餐、此前月份和其他格覆盖，仅移除当前格覆盖。
- 同步客户管理、排餐管理业务说明和客户档案 API 文档；新增 Service/Controller 定向测试。

### 最终 API 与只读契约

- `GET /api/customerProfile/mealStats/orders/{orderId}/calendar?statsMonth=yyyy-MM`：响应只包含 URL 指定订单；`overrides` 是本订单本月完整持久化快照；`cells` 支持 BREAKFAST/LUNCH/DINNER 并分别返回 `customerExcluded/orderExcluded`；订单摘要包含两餐池 `availableBreakfastCount/availableLunchDinnerCount`、`editable/readOnlyReason`、`revision`。
- `PUT /api/customerProfile/mealStats/orders/{orderId}/calendar`：请求必填 `statsMonth/expectedRevision/overrides`。空数组只恢复该订单当月覆盖；0 份归一化含汤为空。成功返回 `orderId/statsMonth/revision/deletedUnverifiedPlanCount`。
- 订单状态不可编辑、月份/日期/餐次/购买数池无效、重复格、全局停餐格变更等返回 400；订单/档案不存在返回 404；revision 冲突或清理影响数与候选数不符返回 409。
- revision 包含订单身份、状态、餐次、日期、排餐模式、餐数和菜品计划字段；客户统一停餐；订单全部历史覆盖至所选月末；所选月生成/失败/核销事实；全订单各餐次成功排餐与核销汇总。其他订单变化不进入该修订标记。

### 事务、锁顺序与 SQL 限定

- 保存事务顺序：`customer_order FOR UPDATE → customer_profile FOR UPDATE → 覆盖/计划校验 → 当前月覆盖写入 → 逐格按日期餐次锁 meal_plan 并清理超额结果 → 汇总更新`。不更新客户 `excluded_dates`。
- 生成入口先按排序后的订单 ID 锁住基础候选与当前日期数量覆盖订单，再清理/锁日期排餐主记录和排餐行。核销及核销回退同样先锁订单，再以 `markVerifiedIfPending/revertVerified` 更新排餐行，避免订单日历反向锁序。
- `softDeleteMissingByOrderIdAndDateRange` SQL 条件为 `deleted=0 AND order_id=? AND record_date>=? AND record_date<=?`，空 keepIds 只作用于本订单本月。`softDeleteUnverifiedByIds` SQL 另含 `deleted=0 AND (is_verified=0 OR is_verified IS NULL)`；影响行数与待删 ID 数不一致时抛 409，使事务回滚。份数查询和清理 SQL 均包含当前 `order_id`。
- MySQL 行锁、事务回滚及实际 DDL 元数据未在本地/隔离测试库集成验证；本阶段通过 Mapper 条件审查和真实 Service/生成方法的单元路径验证，不把 mock 结果视作数据库并发证明。

### 验证结果和 Phase 03 输入

- 命令：`mvn -pl eladmin-system -am -Dtest=CustomerMealStatsScheduleUtilTest,CustomerMealStatsServiceImplTest,CustomerMealStatsControllerTest,MealVerificationServiceImplTest,MealPlanServiceImplTest#shouldSkipZeroQuantityOrderAndGenerateAnotherOrderForTheSameCustomer+shouldDeleteFailedServingBeforeSuccessfulUnverifiedServingWhenReducingPlan+shouldRollbackCalendarReductionWhenUnverifiedGuardLosesRaceWithVerification -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.useFile=true test`。
- 结果：26 个测试通过，0 失败、0 错误；此前 `mvn -pl eladmin-system -am -DskipTests compile` 通过。
- Phase 03 直接使用字段：列表保留 `orderId/customerId/orderCode` 内部身份；日历标题使用 `customerName/customerCode/orderCode/statsMonth`；摘要使用 `status/mealType/startMealType/startDate/endDate/breakfastCount/lunchDinnerCount/availableBreakfastCount/availableLunchDinnerCount/defaultIncludesSoup/editable/readOnlyReason/revision`；单元格使用 `orderId/date/mealType/baseQuantity/quantity/soupQuantity/defaultIncludesSoup/generatedCount/failedCount/verifiedCount/manualOverride/customerExcluded/orderExcluded`；编辑器全量回传 `overrides`。
- 暂停、未指定餐次、已关闭和剩余餐数为零均为服务端只读；前端仍须叠加编辑权限。旧客户维度保存端点与当前列表仅在同版本 UI 切换后于 Phase 04 清理。
