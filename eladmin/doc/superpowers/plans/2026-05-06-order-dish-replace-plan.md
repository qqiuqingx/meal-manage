# 订单级自动换菜功能实施计划

## 目标

在订单中配置“指定菜品替换规则”：

1. 运营在订单新增/编辑时配置 `原菜品 -> 替换菜品`。
2. 规则在该订单整个有效期内持续生效。
3. 排餐生成时，若原始选中的菜品命中规则，则直接替换为目标菜品。
4. 替换规则优先级最高，不再参与过敏和排除条件过滤。
5. 允许跨 `dish_type` 替换。

## 范围

- 改后端：订单模块、排餐模块、SQL、单元测试、接口文档、业务文档。
- 改前端：订单表单新增换菜规则维护区。
- 不做：
  - 单独的换菜规则管理页
  - 排餐生成后的手工改单
  - 按餐次生效
  - 按日期范围生效
  - 规则优先级排序

## 业务结论

### 1. 生效范围

- 规则按 `order_id` 绑定。
- 订单有效期内全程生效，不区分早餐/午餐/晚餐。

### 2. 命中时机

- 先按现有排餐逻辑选出原始菜品。
- 再根据订单规则判断是否需要替换。
- 最终以替换后的菜品写入 `meal_plan_customer_item`。

### 3. 跨 dish_type 处理

- `meal_plan_customer_item.dish_type` 存最终菜品的 `dish_type`。
- 为保留追溯信息，新增 `original_dish_type` 字段记录被替换前的类型。

### 4. 过敏/排除条件

- 订单规则命中后直接替换。
- 替换目标不再参与过敏过滤、排除日期过滤、客户忌口过滤。

### 5. 替换原因

- 新增 `replace_reason = ORDER_RULE`。

## 数据结构

### 1. 新增订单换菜规则表

- 表名：`customer_order_replace_rule`

建议字段：

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `order_id BIGINT NOT NULL`
- `source_dish_id BIGINT NOT NULL`
- `source_dish_name VARCHAR(100) NOT NULL`
- `source_dish_type VARCHAR(32)`
- `target_dish_id BIGINT NOT NULL`
- `target_dish_name VARCHAR(100) NOT NULL`
- `target_dish_type VARCHAR(32)`
- `enabled TINYINT NOT NULL DEFAULT 1`
- `remark VARCHAR(255)`
- `deleted TINYINT NOT NULL DEFAULT 0`
- `create_by VARCHAR(100)`
- `update_by VARCHAR(100)`
- `create_time DATETIME DEFAULT CURRENT_TIMESTAMP`
- `update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`

索引与约束建议：

- 普通索引：`idx_order_id(order_id)`
- 普通索引：`idx_order_source_active(order_id, source_dish_id, deleted)`

唯一性策略：

- 不在数据库层使用 `uk_order_source(order_id, source_dish_id, deleted)` 唯一索引。
- 原因：当前软删除字段 `deleted` 只有 `0/1` 两个值，若同一规则多次“删除 -> 重建”，会累计多条 `deleted=1` 历史记录，与唯一索引冲突。
- 改为在应用层做唯一性校验：保存订单时，只校验 `deleted=0` 的有效规则中，同一 `order_id` 下 `source_dish_id` 不允许重复。
- 当前订单编辑采用“旧规则软删除 + 新规则重建”的全量覆盖方式，保留历史软删记录即可，不额外引入 `deleted_id` 模式。

名称字段语义：

- `source_dish_name/source_dish_type/target_dish_name/target_dish_type` 作为规则创建或最后一次编辑时的快照保留，用于审计、排查和兜底展示。
- 订单详情接口、订单编辑回显时，优先实时关联 `dish` 表返回最新名称和类型。
- 若菜品已被删除，无法实时关联，再回退显示规则表中的快照值。

### 2. 扩展排餐明细表

- 表：`meal_plan_customer_item`
- 新增字段：`original_dish_type VARCHAR(32) NULL COMMENT '原菜品类型'`

原因：

- 最终生产单、汇总、接口返回需要体现“最终做什么菜”。
- 但排查换菜规则时，还需要知道原始槽位是什么类型。

## 后端设计

### 1. 订单模块新增规则明细

新增目录建议：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/orderReplaceRule/domain/`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/orderReplaceRule/mapper/`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/orderReplaceRule/service/`

核心对象：

- `CustomerOrderReplaceRule`
- `CustomerOrderReplaceRuleDto`
- `CustomerOrderReplaceRuleMapper`

### 2. 订单保存 DTO 扩展

修改：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/domain/dto/CustomerOrderSaveDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/domain/dto/CustomerOrderDetailDto.java`

新增字段：

- `List<CustomerOrderReplaceRuleDto> replaceRules`

保存规则：

- 新增订单：先存订单主表，再批量插入规则表。
- 编辑订单：删除该订单旧规则，再按前端提交的完整列表重建。

原因：

- 当前项目订单编辑就是整单提交，规则列表也按“全量覆盖”实现最稳妥。
- 逻辑简单，避免前端维护增量状态。

### 3. 订单校验规则

在 `CustomerOrderServiceImpl.validateAndNormalize(...)` 或专用私有方法中增加校验：

- 原菜不能为空
- 目标菜不能为空
- 原菜和目标菜不能相同
- 同一订单下原菜不能重复
- 原菜和目标菜必须是存在且启用的菜品
- 允许 `source_dish_type != target_dish_type`

异常提示建议：

- `换菜规则中的原菜不能为空`
- `换菜规则中的目标菜不能为空`
- `同一订单不能重复配置同一个原菜`
- `原菜和目标菜不能相同`
- `换菜规则中的菜品不存在或已停用`

订单详情回显规则：

- 查询规则列表时，联查 `dish` 表获取最新 `name` 和 `dish_type`。
- 若联查成功：
  - 前端回显使用最新菜品名称和类型
- 若联查失败：
  - 回退到规则表中的 `source_dish_name/source_dish_type/target_dish_name/target_dish_type`
  - 同时可增加一个前端提示态，标识该规则引用的菜品已失效

### 4. 排餐模块接入规则

重点修改：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/MealPlanServiceImpl.java`

建议实现方式：

1. 查询参与排餐订单时，批量加载这些订单的换菜规则。
2. 按 `orderId + sourceDishId` 建立内存 Map。
3. 正常选出原始菜品后，调用 `applyOrderReplaceRule(...)`。
4. 若命中规则：
   - 将最终菜品替换为目标菜
   - `isReplaced = true`
   - `originalDishId/originalDishName/originalDishType` 记录原菜
   - `replaceReason = ORDER_RULE`
   - 清空过敏相关标记：`isAllergyFiltered = false`、`allergyReasons = null`
5. 若命中规则但目标菜已停用或已删除：
   - 强制跳过该条规则
   - 保留原始选菜结果，不中断整单排餐
   - 记录 `WARN` 日志，包含 `orderId/sourceDishId/targetDishId/ruleId`
6. 未命中则保持现有逻辑。

建议新增私有方法：

- `Map<Long, Map<Long, CustomerOrderReplaceRule>> buildOrderReplaceRuleMap(Set<Long> orderIds)`
- `SelectedDish applyOrderReplaceRule(Long orderId, SelectedDish selectedDish, Map<Long, Map<Long, CustomerOrderReplaceRule>> ruleMap)`

### 5. 排餐接口返回扩展

修改：

- `MealPlanCustomerItem`
- `MealPlanCustomerItemVO`

新增返回字段：

- `originalDishType`

并在文档中补充：

- `replaceReason` 新增 `ORDER_RULE`

## 前端设计

### 1. 订单表单新增换菜规则区

修改：

- `eladmin-web/src/components/Order/OrderForm.vue`

建议放在“每餐菜品配置”下方、“金额信息”上方，新增区块：

- 标题：`换菜规则`
- 说明文案：`当排餐命中原菜时，将自动替换为目标菜，规则全订单生效`

每行字段：

- 原菜品选择器
- 目标菜品选择器
- 备注
- 删除按钮

底部按钮：

- `新增规则`

### 2. 交互细节

- 原菜、目标菜使用远程搜索菜品下拉。
- 不按 `dish_type` 限制目标菜。
- 编辑订单时回显已有规则。
- 提交前做前端去重校验，避免重复原菜。
- 编辑订单回显时，优先使用接口返回的实时菜品名称；若规则引用的菜品已删除，则展示快照名称并提示“菜品已失效”。

### 3. API 数据

修改：

- `eladmin-web/src/api/customer/order.js`
- 订单新增/编辑请求体中透传 `replaceRules`

默认表单对象也要扩展：

- `createOrderDefaultForm()` 增加 `replaceRules: []`

## 文档更新

必须同步修改：

- `eladmin/doc/business/订单管理业务说明.md`
- `eladmin/doc/business/排餐管理业务说明.md`
- `eladmin/doc/apidoc/客户订单管理接口文档.md`
- `eladmin/doc/apidoc/排餐计划接口文档.md`

需要补充的文档点：

- 订单新增/编辑接口请求体新增 `replaceRules`
- 订单详情接口响应新增 `replaceRules`
- 排餐详情接口中 `replaceReason=ORDER_RULE`
- 说明“订单规则优先级最高，命中后跳过过敏和排除条件”

## 测试计划

### 后端单元测试

重点补充：

- `CustomerOrderServiceImplTest`
- `MealPlanServiceImplTest`

至少覆盖以下场景：

1. 新增订单时保存换菜规则成功
2. 编辑订单时全量覆盖旧规则成功
3. 同一原菜重复配置时报错
4. 原菜和目标菜相同时报错
5. 目标菜停用时报错
6. 排餐命中规则后替换成功
7. 跨 `dish_type` 替换后，明细中的 `dish_type` 为目标类型
8. 命中规则后 `replaceReason = ORDER_RULE`
9. 命中规则后不过敏过滤也不记录 `allergyReasons`
10. 历史规则的目标菜已停用时，排餐跳过规则、保留原始选菜并记录日志
11. 历史规则的目标菜已删除时，排餐跳过规则、保留原始选菜并记录日志
12. 未命中规则时保留现有逻辑

### 前端自测

1. 新增订单可添加多条规则并成功提交
2. 编辑订单可回显、删除、替换规则
3. 重复原菜前端能拦截
4. 提交后重新打开订单仍能看到规则
5. 生成排餐后明细正确显示替换结果

## 实施顺序

### 阶段 1：数据库与后端模型

1. 新增 `customer_order_replace_rule` 表 SQL
2. 为 `meal_plan_customer_item` 增加 `original_dish_type`
3. 新增规则实体、Mapper、DTO

### 阶段 2：订单保存链路

1. 订单新增接口接入规则保存
2. 订单编辑接口接入规则全量覆盖
3. 订单详情接口返回规则列表
4. 补订单模块测试

### 阶段 3：排餐生成链路

1. 批量加载订单规则
2. 在选菜后应用规则替换
3. 落库原菜类型、替换原因
4. 补排餐模块测试

### 阶段 4：前端表单

1. 订单表单新增规则维护区
2. 接入新增/编辑/详情回显
3. 前端校验与交互优化

### 阶段 5：文档与联调

1. 更新业务文档
2. 更新接口文档
3. 联调新增订单 -> 排餐生成 -> 排餐详情展示

## 风险点

### 1. 跨类型替换改变生产单归类

- 这是预期行为，不是缺陷。
- 生产单应按最终菜品类型汇总。

### 2. 规则依赖“原菜命中”

- 若当天原菜本来没有被选中，则该规则不会生效。
- 这是“指定菜品替换”的天然语义，应在产品说明里明确。

### 3. 规则失效数据

- 新增或编辑订单时：
  - 目标菜不存在或已停用，严格拦截保存
- 历史规则在保存后失效时：
  - 目标菜已停用：排餐时跳过该条规则，保留原始选菜，记录 `WARN` 日志
  - 目标菜已删除：排餐时跳过该条规则，保留原始选菜，记录 `WARN` 日志
- 以上两种情况都不允许中断整单排餐

## 交付物

1. 订单换菜规则表与升级 SQL
2. 订单新增/编辑/详情接口支持规则列表
3. 排餐自动换菜能力
4. 订单页换菜规则维护区
5. 业务文档与接口文档更新
