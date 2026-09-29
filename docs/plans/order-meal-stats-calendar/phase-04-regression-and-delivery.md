# Phase 04：隔离验收、文档和完整交付

## 执行前规则检查

加载 overview、contracts、status 和 Phase 03 交付记录；按验证失败原因才补读其他 Phase。读取最新 AGENTS.md；执行 Maven 时加载 maven 技能并使用 Java 8 和适配 Maven；前端使用仓库现有依赖。保护已有 API/业务文档的未提交编辑，不用整文件替换。

## 目标

证明新列表、订单日历和实际排餐链路一致，删除无调用旧路径，补全发布所需文档及验证证据。

## 前置依赖

Phase 01–03 完成。必须取得三阶段的实际契约、测试记录、锁/事务说明及旧引用清单；不能用计划描述替代实现结果。

## 输入与输出

输入：可运行的完整功能、既有订单/排餐/核销相关测试及已获准的隔离测试数据库配置。

输出：订单隔离与数据库事务验证结果、真实页面验收结果、业务和 API 文档、删除旧路径后的引用核查、发布/回滚说明、更新的阶段状态。

## 本阶段实施约束

- 集成测试只创建带唯一标识的数据并按记录 ID 清理；无条件删表/清表、Docker 查库和写入凭据均禁止。
- 未取得测试数据库条件时明确报告集成验证未完成，不连接业务数据库替代，也不宣称已验证事务隔离。
- 仅删除已确认无调用方且被本需求替代的客户日历路径；不能删除客户档案统一停餐或订单建档日期选择功能。
- 测试覆盖真实接口/组件/SQL；不为测试新建生产接口或启动专用业务开关。
- 前后端同版本交付；不单独发布 Phase 产物。提交和部署均不包含在本次计划请求内。

## 涉及文件

已有测试补充：

- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/impl/CustomerMealStatsServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/util/CustomerMealStatsScheduleUtilTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/meal/service/impl/MealPlanServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/meal/service/impl/MealVerificationServiceImplTest.java`（仅有影响或缺少关键回归时补充）
- Phase 03 指定的前端测试文件。

新增：`eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/impl/CustomerMealStatsMySqlIntegrationTest.java`，采用仓库既有受控 MySQL 集成测试约定；环境配置仅从环境变量/私有配置读取。

文档修改：

- `eladmin/doc/business/客户管理业务说明.md`
- `eladmin/doc/business/订单管理业务说明.md`
- `eladmin/doc/business/排餐管理业务说明.md`
- `eladmin/doc/apidoc/客户档案管理接口文档.md`
- `eladmin/doc/apidoc/排餐计划生成接口.md`

按实际契约变动补充 `eladmin/doc/apidoc/排餐计划接口文档.md`；订单建档用的 `订单排餐日历接口文档.md` 只补充功能区分/关联入口，不改写其现有组件协议。核销业务规则未改变时不机械改核销文档。

本计划目录全部 Phase 的交付记录、`00-overview.md` 和 `status.yaml`。

## 实施步骤

### 1. 执行核心隔离验收矩阵

| 编号 | 场景 | 必须成立 |
|---|---|---|
| A01 | 同客户 A/B 两笔单，A 含早餐＋午晚 | 列表两行，A 仍只一行，24 列各取对应来源 |
| A02 | 打开 A 日历 | 响应只含 A，标题订单身份明确，B 不出现 |
| A03 | A 某日午餐设为 0 | A 不生成/对应未核销份清理；B 同日午餐与覆盖完全不变 |
| A04 | A 3 份减到 1，已有 1 份核销 | 保留核销份，清理其余未核销；改 0 拒绝且无部分写入 |
| A05 | B 同日已核销、A 未核销 | A 的合法取消不因 B 的核销而失败 |
| A06 | A 恢复默认或删除人工新增 | 只移除当前格覆盖，按保留其他格历史的规则重算 |
| A07 | 客户统一停餐＋A 正数覆盖 | A/B 都停餐；A 页不能解除，底层原覆盖未丢失 |
| A08 | 客户档案解除统一停餐 | A/B 各自覆盖重新生效；A 的独立 0 仍为停餐 |
| A09 | 保存 A 10 月/空 overrides | 只同步 A 10 月，A 9/11 月和 B 10 月记录主键及内容不变 |
| A10 | A 请求越月/不支持餐次/身份伪造/重复格 | 请求完整失败，不自动改绑或忽略条目 |
| A11 | 暂停、待确认餐次、打开后订单完成 | 只读或保存拒绝，客户其他可用订单不能被替代使用 |
| A12 | 同订单两个窗口编辑 | 后保存陈旧 revision 返回 409，无覆盖前一人的变更 |
| A13 | 同客户不同订单分别编辑 | 不覆盖对方数据；仅对方覆盖变更不造成 revision 冲突 |
| A14 | 保存与核销/生成并发 | 不删除已核销份、不遗留被取消订单的未核销份；失败事务全部回滚 |
| A15 | 取消/恢复循环、连续无变化保存 | 活动覆盖最多一条，无唯一键冲突或重复覆盖 |
| A16 | 跨月、导入核销基数、多份、含汤 | 购买池累计、当月可用量、生成份数、最后 k 份含汤规则一致 |
| A17 | 统计对账 | 核销基数不重复加、今日已核销不重复扣、失败份统计口径明确 |
| A18 | 无编辑权限、GET 失败、A/B 响应乱序 | 不允许错误保存或把 A 响应写进 B |
| A19 | 新 API 完整范围 | 无金额字段泄漏，旧客户保存协议不再可调用 |

数据库集成测试必须以 A/B 和相邻月份的真实对照记录证明 SQL where 范围、唯一键恢复和事务回滚；若并发测试未跑通，交付明确列为阻塞验收项，不把单元 mock 的结果当作数据库证明。

### 2. 核对并清理旧路径

前端 API 已切换后搜索生产与测试引用，移除旧 `PUT /mealStats/scheduleAdjustments`、`CustomerMealScheduleAdjustmentRequest/Result` 及旧专用 DTO/旧接口分支（仅当确无其他使用）。删除 customer 维度自动改绑、整客户当月覆盖同步和旧列表聚合字段。

核对 `deleteUnverifiedCustomerMealForCalendarAdjustment`、`softDeleteMissingByCustomerIdAndDateRange`、`findMealStatsCalendarOrdersByCustomerIds`、`CustomerMealScheduleOrderDto`、旧 getMealStats 行组字段等；无调用时连同接口/Mapper/XML/测试定向移除，有真实业务调用则保留并在记录中说明用途。客户档案 `excluded_dates` 和订单表单 `deliveryDates` 均仍有真实调用，保留。

### 3. 同步业务/API 文档

客户文档删除“按餐池拆行”“客户合并日历保存”的旧描述，写清一单一行、状态 1/4、订单分页和客户统一停餐边界；修正相关段落旧 `exclude_dates` 拼写。订单文档补充本页统计来源和原文术后展示。

排餐业务及生成 API 补充 0 份覆盖、正数覆盖、恢复默认、客户统一停餐优先、跨月累计、已核销下限与单订单清理。客户档案 API 新增三项统计/日历契约的请求响应、权限、409、完整月覆盖数组含义，移除旧保存接口说明。不要覆盖文档中本次无关的客户饮食导入更新。

### 4. 运行必要检查并保留证据

后端先按 maven 技能设置适配 JDK/Maven，从 `eladmin/` 执行定向 reactor 测试，例如：

```bash
mvn -pl eladmin-system -am test -Dtest=CustomerMealStatsServiceImplTest,CustomerMealStatsControllerTest,CustomerMealStatsScheduleUtilTest,MealPlanServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
```

实际测试类名以已实现文件为准；相关阶段已完成且代码未再变更的检查不重复执行。若只需补编译：`mvn -pl eladmin-system -am compile -DskipTests`，该结果只能报告编译通过。MySQL 集成类按现有测试约定单独运行，检查未因环境条件而被跳过。

从 `eladmin-web/` 执行：

```bash
./node_modules/.bin/eslint src/views/customer/mealStats/index.vue src/views/customer/mealStats/CustomerMealQuantityGrid.vue src/api/customer/profile.js
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service test:unit --runInBand --runTestsByPath tests/unit/views/customer/mealStats.spec.js tests/unit/views/customer/mealStats/lazyLoading.spec.js tests/unit/views/customer/mealStats/orderCalendar.spec.js tests/unit/views/customer/mealQuantityGrid.spec.js
```

需要目视联调时按本地开发指南复用已有服务，先核对端口，不默认启动全套服务；使用测试账号和隔离数据验收宽表、图片、日历和错误提示。无浏览器/环境时说明目视验收未完成。

最后 `git diff --check`，核对变更清单只包含本需求及保留的原有工作区改动。不要为赶工关闭测试或扩大忽略规则。

### 5. 发布与回滚核对

确认旧 API 生产调用清单已切换；前后端同版本上线、旧页面刷新。核对现有 quantity/soup_quantity 和索引已部署，不因“不新增字段”而省略旧环境结构检查。

发布前记录目标环境调整表快照范围和版本；新 0 份数据写入后，旧版本无法完整理解，按 overview 的前滚优先与受控回滚流程处理。不能以删除 0 记录或转客户统一停餐的方式回退。

## 验证方式

- 按 A01–A19 矩阵逐项记录单元、接口、数据库集成或页面证据，明确验证层次。
- 执行 Step 4 中与实际修改相关的 Maven/Jest/ESLint 命令，保存真实通过和跳过数量。
- 通过引用搜索、文档链接核对和 `git diff --check` 检查旧路径清理及交付范围。
- 没有运行条件的验证单独列出原因和补测条件，不能用其他层次的检查代替。

## 完成标准

- A01–A19 每项有通过证据，或明确列出未执行的验证及原因；隔离/事务关键项未验证时不得标记“完整验收通过”。
- 相关编译、单元测试、SQL/事务集成、组件 lint/测试和可用的页面验收完成。
- 无失去调用方的旧客户日历接口/同步删除/自动改绑路径；客户统一停餐继续正常。
- 业务/API 文档与实现一致，回滚限制已注明，status.yaml 与实际阶段一致。
- 未提交、未发布，除非用户在实施过程中另有明确授权。

## 状态

in_progress

## 阶段交付记录

### 实际交付

- 列表已迁移为订单分页和一单一行；日历读写以 URL `orderId` 为唯一身份，保存范围限定订单和月份。订单统计、订单日历服务、份数清理和核销/生成锁顺序已按 Phase 01–03 交付记录实施。
- 移除了旧 `PUT /mealStats/scheduleAdjustments`、客户级保存请求/结果 DTO、旧自动改绑和客户维度同步清理方法，以及用餐统计旧聚合行字段和 Mapper 查询。生产代码、测试、当前 API 文档搜索不到旧保存路径；历史计划记录保留为实施历史。
- 保留客户档案 `excluded_dates` 统一停餐和订单建档表单 `deliveryDates`，二者仍有业务用途。业务文档把排餐日历记录和清理口径改为订单范围，并统一修正数据库字段名 `excluded_dates`。
- 本阶段未新增 `CustomerMealStatsMySqlIntegrationTest`：工作区未提供可确认隔离性的 MySQL 测试库配置。没有读取私有 `.env.local`、连接业务数据库或使用 Docker 代替；实际数据库元数据、行锁、事务回滚和并发行为均保留为上线验收前置项。

### A01–A19 验收矩阵

以下“通过”只代表括号注明的单元、组件、静态检查层级。Mockito/模拟 HTTP 结果没有被当作真实 MySQL 事务证明。

| 编号 | 当前证据 | 状态 |
|---|---|---|
| A01 | Service 分页映射测试、真实 Vue 挂载测试覆盖同客户订单分行及 24 列顺序 | 单元/组件通过；数据库分页待隔离库 |
| A02 | Service/Controller 按订单读取测试、页面订单切换与乱序响应测试 | 单元/组件通过；真实页面目视待环境 |
| A03 | 零份保存 Service 测试与真实 `generateMealPlan` 入口的同客户另一单生成测试 | 单元/模拟依赖通过；A/B 数据库隔离待隔离库 |
| A04 | 减份优先清理失败份、已核销下限和未核销条件行数变化回滚的 Service 测试 | 单元/模拟 Mapper 通过；真实事务待隔离库 |
| A05 | SQL 条件审查及取消按当前 `order_id` 查询/清理 | 静态通过；B 已核销真实对照数据待隔离库 |
| A06 | 空快照恢复默认的 Service 测试，清理谓词限定订单和月份 | 单元/静态通过；跨月主键对照待隔离库 |
| A07 | 日历来源计算及组件测试覆盖客户统一停餐优先、订单覆盖不可改 | 单元/组件通过；持久化恢复行为待隔离库 |
| A08 | 客户统一停餐和订单 0 份标记独立的计算测试 | 单元通过；解除统一停餐后的真实记录恢复待隔离库 |
| A09 | 空覆盖快照测试和 `order_id + 日期范围` Mapper SQL 审查 | 单元/静态通过；相邻月份/B 记录不变待隔离库 |
| A10 | 重复日期餐次、负数、餐池超额、路由 `orderId` 等 Service/Controller 测试 | 已覆盖验证路径通过；越月及全部非法组合 SQL 事务回滚待隔离库 |
| A11 | 暂停、未确认餐次只读和保存拒绝测试 | 部分单元通过；打开后订单状态变化的真实重读待隔离库 |
| A12 | 陈旧 `expectedRevision` 返回冲突且写入前拒绝的 Service 测试 | 单元通过；并发窗口真实事务待隔离库 |
| A13 | revision 由当前订单数据计算的实现审查、前端按订单捕获请求 | 静态/组件通过；不同订单并发编辑隔离待隔离库 |
| A14 | 核销服务与清理模拟竞态测试、订单锁顺序实现审查 | 单元/静态通过；真实生成/核销/保存并发及回滚待隔离库 |
| A15 | 相同覆盖重复保存幂等性 Service 测试 | 单元通过；真实唯一键恢复循环待隔离库 |
| A16 | 工具测试覆盖跨月 0 份预算释放、导入核销基数、早餐/午晚池及含汤覆盖；生成入口覆盖零份订单 | 单元通过；生产库数据对账待隔离库 |
| A17 | 订单统计映射测试覆盖核销、已排餐及预计剩余字段口径 | 单元通过；线上数据对账未执行 |
| A18 | Vue 测试覆盖订单响应乱序与 409 草稿保留；Controller 测试核对路由权限注解 | 组件/注解通过；登录权限和真实 GET 失败页面验收待环境 |
| A19 | DTO/Controller 文档核对无金额字段；生产和当前测试/API 文档搜索无旧客户级保存路径 | 静态/单元通过；发布调用方清单需目标环境确认 |

### 验证命令与结果

- 后端（Java 8、Maven 3.5.2），在 `eladmin/` 执行：`mvn -pl eladmin-system -am '-Dtest=CustomerMealStatsServiceImplTest,CustomerMealStatsControllerTest,CustomerMealStatsScheduleUtilTest,MealVerificationServiceImplTest,MealPlanServiceImplTest#shouldSkipZeroQuantityOrderAndGenerateAnotherOrderForTheSameCustomer+shouldDeleteFailedServingBeforeSuccessfulUnverifiedServingWhenReducingPlan+shouldRollbackCalendarReductionWhenUnverifiedGuardLosesRaceWithVerification' -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.useFile=true test`：27 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS。
- 前端 Jest 定向覆盖 `mealStats.spec.js`、`lazyLoading.spec.js`、`orderCalendar.spec.js`、`mealQuantityGrid.spec.js` 和饮食导入回归 `dietImportReview.spec.js`：5 个 suite、13 项通过；修改的 Vue/JS/API/测试文件 ESLint 通过。Jest API 为 mock，不能代替完整部署联调。
- `git diff --check` 与 Mapper XML 解析在最终文档修改后执行；结果记录在本计划交付状态。本阶段最后一轮业务改动为文档修改，未因文档再次运行测试。
- 历史的整类 `CustomerProfileServiceImplTest` 运行曾在旧客户日历用例及 `SpringBeanHolder` 上失败，旧客户日历路径随后已移除；它不是当前新 Service 的验证结果，亦不计入本阶段通过数。详情见 Phase 03 记录。

### 文档、发布和未完成事项

- 业务及 API 文档：客户管理、订单管理、排餐管理；客户档案 API、客户统计/用餐统计页面 API、排餐计划生成 API；另明确区分订单建档 `deliveryDates` 日历与统计页数量覆盖日历。订单统计字段、`specialRequirements`/`medicalRequirements` 来源、术后原文和订单范围日历与实现一致。
- 未取得部署测试环境，因此未做 1366/1920 页面目视验收、旧调用方的目标发布环境确认或线上 DDL 元数据核对。虽然仓库 DDL 已包含 `quantity`、`soup_quantity`，目标环境结构仍须上线前确认。
- 前后端必须同版本发布。写入 0 份覆盖后，旧版本无法完整解释该语义；优先前滚修复。如必须回退，先暂停日历写入及相关排餐生成，按受影响的订单/日期/餐次导出快照并制定保留逐订单停餐含义的恢复方案，不直接删除零份覆盖或转成客户统一停餐。
- 阶段状态保持 `in_progress`；取得隔离 MySQL 和已部署页面验收环境后，补跑真实 A/B、多月 SQL 隔离、唯一键恢复、事务回滚/并发验证及 1366/1920 目视确认，再决定是否完成 Phase 04。
