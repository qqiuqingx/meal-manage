# Phase 03：24 列订单列表与订单日历页面闭环

## 执行前规则检查

加载 status、overview、contracts、Phase 02 交付记录及最新规则链。核对前端现有懒加载测试和订单页枚举/图片/饮食组件实现；本阶段只复用展示能力，不把订单页行内编辑功能扩展到本页面。

## 目标

用户在同一页面看到按顺序排列的 24 个订单字段，并从任一订单打开、编辑和保存该订单的日历，完成前后端切换。

## 前置依赖

Phase 02 完成；依赖新订单日历 GET/PUT、只读标记、格来源字段、覆盖快照及 revision 契约；Phase 01 的 0 份语义已在实际生成路径验证。

## 输入与输出

输入：24 列字段契约、现有订单数据和批量统计 SQL、客户资料组件、新订单日历 API。

输出：订单数据库分页查询、完整统计 DTO、新列表、新订单日历交互、定向组件与查询测试。至此可在测试环境以同一版本运行完整功能。

## 本阶段实施约束

- 一笔订单一行，row-key 为 orderId；分页总数是订单数，禁止继续按客户/餐池拆行或合并客户公共列。
- 已排餐/预计剩余复用现有批量统计 SQL；只处理当前页，不做每行 HTTP/数据库查询，不重复计算全部客户整月日历。
- 特殊要求、基本情况、术后原文严格遵循用户确认，不增加新存储字段或日期计算。
- 客户统一停餐在订单日历清晰标识、禁止单独恢复；早餐和午晚均只能操作 selectedOrder。
- Vue 2/Element UI 现有依赖可完成，不新增依赖；方法注释说明参数与行为。
- 保留与本任务无关的客户档案/订单页逻辑，不附带全表格式化或行内编辑扩展。

## 涉及文件

后端修改：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerMealStatsRowDto.java`
- `.../customer/profile/service/CustomerMealStatsService.java`、`.../service/impl/CustomerMealStatsServiceImpl.java`
- `.../customer/profile/service/CustomerProfileService.java`、`.../service/impl/CustomerProfileServiceImpl.java`
- `.../customer/profile/rest/CustomerProfileController.java`
- `.../customer/order/mapper/CustomerOrderMapper.java` 及 `eladmin/eladmin-system/src/main/resources/mapper/CustomerOrderMapper.xml`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/impl/CustomerMealStatsServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfileServiceImplTest.java`（搬迁并更新统计相关用例，保护其他测试）

前端修改：

- `eladmin-web/src/views/customer/mealStats/index.vue`
- `eladmin-web/src/views/customer/mealStats/CustomerMealQuantityGrid.vue`
- `eladmin-web/src/api/customer/profile.js`
- `eladmin-web/tests/unit/views/customer/mealStats.spec.js`
- `eladmin-web/tests/unit/views/customer/mealStats/lazyLoading.spec.js`
- `eladmin-web/tests/unit/views/customer/mealQuantityGrid.spec.js`

新增：`eladmin-web/tests/unit/views/customer/mealStats/orderCalendar.spec.js`。其中 `...` 以 `eladmin/eladmin-system/src/main/java/me/zhengjie/modules` 为根。

## 实施步骤

### 1. 改为订单数据库分页

在 `CustomerOrderMapper` 增加面向本页条件的订单分页查询，使用当前已配置的 MyBatis-Plus Page。订单与客户基础表一对一关联筛选姓名/手机号；客户编号按展示来源过滤。不得直接联结多地址表造成订单行重复、count 失真。

保留 status=1/4、remaining>0、startDate<次月首日，稳定排序并以订单 ID 收尾。分页后收集 orderIds/customerIds，批量取客户结构化饮食、地址，以及 `countAllScheduledByOrderIds/countTodayUnverifiedScheduledByOrderIds`。空页不发空 IN 查询。

`CustomerMealStatsService` 负责映射明确的行 DTO；只有当前页订单需要派生总数和预计剩余。复用已有 SQL，简单加法不为抽象而新增通用框架；若抽取跨订单页真实复用的填充职责，必须同时接入两个生产调用方并增加相关回归。

### 2. 精确映射 24 字段

逐项落实 contracts 表格；客户资料取共享当前值，规格/模式/菜单/状态/成单时间取本订单。DTO 不把客户共享列合并为字符串，保留饮食组件所需结构。

移除旧列表的餐池字段和整客户日历 payload。列表查询从 CustomerProfileService 定向移到新 Service，清理已经无用的行聚合辅助方法；保留仍被其他业务调用的日期工具。

### 3. 改造列表展示

严格设置 24 业务列顺序，右侧“排餐日历”每行可见；删除 `span-method`、recalculateRowGroups 及 `firstRowInGroup` 依赖。保留懒加载请求序列防乱序、加载失败重试和 resize/滚动监听释放。

处理宽表：手机号/地址/客户编号采用现有固定列机制并控制宽度，右侧操作固定；长文本多行或可展开，饮食继续用 CustomerDietCell，自定义菜单支持预览。检查 1366/1920 宽度下横向滚动和固定列无重叠，不要求 24 列全部挤入一屏。

统计月份提示说明“餐数为当前订单累计值”；已排餐/预计剩余的 tooltip 与 contracts 一致，避免把失败记录、计划格数或所选月份误当今日统计。

### 4. 打开当前订单日历

每次点击捕获 orderId 和 statsMonth，再调用专用 GET。标题使用“客户编号＋姓名＋订单编号＋月份”，元信息只展示当前订单餐数和状态。删除客户订单笔数汇总、按客户 rows 寻找早餐/午晚行、默认订单选择等逻辑。

日历请求单独维护 loading/错误/请求序号；快速点击 A 后 B 或关闭弹窗时，A 的晚到响应不能覆盖 B，加载失败不能启用保存。月份绑定以当前已加载日历月份为准，避免列表月份改变后提交错月。

### 5. 绑定单订单编辑状态

午晚份数组件收敛为单个 order 输入，保留当前份数/含汤编辑能力，不再把多个订单作为可编辑列表；只改本 orderId/date/mealType 的 cell。早餐沿用勾选交互，但从当前订单 cells 读取、写当前订单覆盖，不从同客户其他订单找可用餐次。

订单停餐写 0 覆盖；“恢复默认”明确删除覆盖；手动设置为默认数量也不自动等同于移除覆盖。统一停餐单元格标明原因并禁用修改，隐藏的历史 overrides 原样保存。暂停/未指定餐次/无编辑权限时只读。

不要把计算出的全部默认 cells 都持久化为覆盖：维护完整原始 overrides，仅按实际编辑/恢复更新对应项，保证后续默认规则变化仍能生效。

### 6. 保存与页面刷新

PUT URL 仅使用打开时的 orderId，body 使用该日历月份、revision、完整 overrides，彻底移除 customerId/excludedDates/quantityMode 和跨订单自动选择。

保存期间禁用重复提交；失败保留用户草稿；409 提示重新加载后核对，不自动重试覆盖。成功后刷新统计列表和耗尽预警；新开日历重新取服务端状态，不使用旧行缓存。

## 验证方式

- 后端列表：同客户两单两行、混合早餐午晚一行、分页总数/稳定排序、只批量查询当前页、金额不输出、24 字段不同订单不串值。
- 对账：早餐5＋午晚10=15；verifiedCount 含导入基数不重复加；remaining=8、今日成功未核销2→预计6；今日已核销和失败份不再扣减，已排餐仍遵循全部有效结果行规则。
- 前端挂载真实 index.vue/数量格组件，mock API；现有 mealStats.spec.js 中复制业务函数的测试改为真实组件交互，不能继续以镜像函数作为新功能验证。
- 断言 24 列顺序、每单入口、医嘱与特殊要求分离、术后原文、餐次、图片 URL/预览和空值。
- 断言 A→B 快速切换、只发送当前订单、0 覆盖不被过滤、恢复默认删除覆盖、统一停餐/暂停只读、409 草稿保留、懒加载与重试。
- 对实际修改的 Vue/JS 文件执行 ESLint；只运行相关 Jest 测试。本次不涉及打包配置，不默认全量构建。

## 完成标准

- 用户要求的 24 列按顺序展示，统计和状态都属于当前订单。
- 从任意订单打开、编辑、保存、重开日历，订单身份和月份一致。
- 页面不再接收或提交客户级日历；其他订单不因编辑发生变化。
- 真实组件测试和当前页查询测试通过，宽表与图片预览完成目视检查。

## 状态

complete

## 阶段交付记录

### 实际修改文件

- 后端：`CustomerOrderMapper.java`/XML 新增客户用餐统计的订单分页查询；`CustomerMealStatsService/Impl` 增加列表批量映射；`CustomerMealStatsRowDto` 改为当前订单行；`CustomerProfileServiceImpl` 移除客户维度列表查询；Controller 将列表路由切到新 Service。
- 前端：`src/views/customer/mealStats/index.vue` 重写为订单宽表与单订单日历；`CustomerMealQuantityGrid.vue` 改为单订单格输入；`src/api/customer/profile.js` 使用新 GET/PUT；原行合并和旧保存 API 前端调用已清除。
- 测试：删除 `CustomerProfileServiceImplTest` 中旧客户/餐池拆行查询用例；在 `CustomerMealStatsServiceImplTest` 覆盖订单分页和批量映射；新增/改写真实页面、懒加载、订单日历与数量格 Jest 用例。
- 文档：补齐客户统计字段口径和订单列表 API。

### 列表及日历契约

- 24 列顺序为：手机号、地址、客户编号、客户姓名、特殊要求、排餐模式、餐次、规格、含汤、早餐、午晚、合计、核销、已排餐、剩余、预计剩余、状态、基本情况、成单时间、术后天数、菜品特殊要求、过敏食物、禁忌食物、自定义菜单；“排餐日历”操作列位于其后。
- `row-key=orderId`；MyBatis-Plus 先对 `customer_order` 分页，再批量加载当页客户档案、地址、`countAllScheduledByOrderIds` 和 `countTodayUnverifiedScheduledByOrderIds`。筛选为订单状态 1/4、剩余数大于 0、开始日期早于次月首日，不按结束日期过滤；稳定排序为客户编号、开始日期、成交时间、订单 ID。
- `verifiedCount` 使用订单累计字段且不重复加导入基数；`scheduledCount` 含成功/失败；预计剩余按今日成功未核销数计算；金额不在 DTO。
- 每次打开捕获 `orderId + statsMonth` 独立 GET；当前页面的数量格和早餐勾选只编辑该订单。编辑器保留服务端返回的完整 `overrides` 快照；恢复默认删除该格覆盖；客户统一停餐标明来源并禁用。
- 保存仅向当前订单 URL 发送月份、revision 和覆盖快照；409 保留草稿，不自动覆盖或重试；成功后刷新列表和耗尽预警。

### 验证

- 后端命令：`mvn -pl eladmin-system -am -Dtest=CustomerMealStatsScheduleUtilTest,CustomerMealStatsServiceImplTest,CustomerMealStatsControllerTest,MealVerificationServiceImplTest,MealPlanServiceImplTest#shouldSkipZeroQuantityOrderAndGenerateAnotherOrderForTheSameCustomer+shouldDeleteFailedServingBeforeSuccessfulUnverifiedServingWhenReducingPlan+shouldRollbackCalendarReductionWhenUnverifiedGuardLosesRaceWithVerification -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.useFile=true test`：28 个测试通过。
- 前端命令：`NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service test:unit tests/unit/views/customer/mealStats.spec.js tests/unit/views/customer/mealStats/lazyLoading.spec.js tests/unit/views/customer/mealStats/orderCalendar.spec.js tests/unit/views/customer/mealQuantityGrid.spec.js`：4 个 suite、11 个测试通过。
- 前端 ESLint 命令：`./node_modules/.bin/eslint src/views/customer/mealStats/index.vue src/views/customer/mealStats/CustomerMealQuantityGrid.vue src/api/customer/profile.js tests/unit/views/customer/mealStats.spec.js tests/unit/views/customer/mealStats/lazyLoading.spec.js tests/unit/views/customer/mealStats/orderCalendar.spec.js tests/unit/views/customer/mealQuantityGrid.spec.js`：通过。
- Jest 挂载实际页面并断言 24 列顺序、每客户两订单分行、快速切换后只显示最新订单、零份快照完整保存及 409 草稿保留。未取得已部署测试环境页面截图；表格使用 Element UI 固定列与自身横向滚动，需在 Phase 04 的测试环境目视确认 1366/1920 宽度。
- 额外运行 `CustomerProfileServiceImplTest` 全类时 43 个测试中 3 个旧客户日历用例失败、4 个试餐/订单用例因 `SpringBeanHolder` 未初始化报错；其中旧客户日历路径将在 Phase 04 清理，容器错误与新统计 Service 无关。旧 `MealPlanServiceImplTest` 整类在 Phase 01 的既有失败也已在 Phase 01 交付记录中说明。本阶段新列表和新日历相关测试全部通过。

### Phase 04 遗留项

- 在同版本 UI 已切换的前提下删除旧 `/mealStats/scheduleAdjustments` 客户维度保存接口、DTO/Service 路径、自动改绑与按客户整月同步删除。
- 清理剩余旧列表/日历 DTO、Mapper/Service 测试引用并做全仓调用检索。
- 在可用隔离测试环境验证 MySQL 订单/排餐行锁、完整事务回滚、跨订单/跨月不变和已核销竞态；当前未连接数据库。
- 若测试环境可用，按 1366/1920 目视检查宽表固定列与日历弹窗。
