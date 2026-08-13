# Phase 06 已有客户新增订单预填提交

## 目标

将 `CREATE_ORDER` 草稿映射到现有新增订单页面，确保客户唯一匹配、复杂订单字段回填和草稿一次提交事务闭环。

## 依赖与输入

- Phase 05 已验证共享领取、提示和提交锁定协议。
- 实施前重读 `客户管理业务说明.md`、`订单管理业务说明.md` 和客户订单 API 文档。

## 输出

- 已有客户新增订单草稿到完整订单表单的映射。
- 正式订单创建与草稿 SUBMITTED 同事务的一次提交闭环。

## 涉及文件

新增建议：

- `eladmin-web/src/views/customer/order/utils/agentOrderDraftMapper.js`
- `eladmin-web/tests/unit/views/customer/order/agentOrderDraftMapper.spec.js`
- 主系统新增订单草稿提交集成测试。

修改：

- `eladmin-web/src/views/customer/order/index.vue`
- `eladmin-web/src/components/Order/OrderForm.vue`
- `eladmin-web/tests/unit/views/customer/order/index.spec.js`
- `eladmin-web/tests/unit/components/Order/OrderForm.spec.js`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/domain/dto/CustomerOrderSaveDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/rest/CustomerOrderController.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/service/CustomerOrderService.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/order/service/impl/CustomerOrderServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/order/service/impl/CustomerOrderServiceImplTest.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/service/impl/AgentFormDraftServiceImpl.java`

## 实施步骤

### Step 1：固定客户匹配结果

- 只有主系统已解析并授权的唯一 `customerId/customerCode` 才能进入 READY 订单草稿。
- 多匹配继续由 Agent 候选选择；找不到客户只提供转换为客户 + 首单，不打开订单表单。
- 页面领取后重新加载客户选项，客户不可见或失效时要求返回对话处理。

### Step 2：映射订单基础字段

- 映射套餐、餐数、金额、成交/配送/开始/结束时间、状态、餐次、排餐模式和销售渠道。
- 映射菜品数量、米饭、汤、备注、过敏与特殊要求。
- 排除 `id/orderCode/verifiedCount/verifiedAmount/mealBalance/remainingCount/customMenuImage` 等服务端或非首期字段。

### Step 3：映射复杂控件

- 将配送日期及餐次回填现有 `MealScheduleCalendar`。
- 加载父子套餐真实选项并验证关系。
- 加载试餐关联订单，失效时清空选择并提示。
- 加载换菜规则原菜/目标菜，复用现有重复原菜和同菜校验。

### Step 4：打开与校验流程

- claim 成功后调用现有 CRUD `toAdd`，在 `beforeToAdd` 默认值完成后应用草稿，避免被重置覆盖。
- 保留现有 `validateOrder` 预校验和表单验证。
- 缺失字段黄色提示，非法/冲突字段使用现有红色或警告逻辑。
- 编辑订单流程不得读取或沿用新增草稿状态。

### Step 5：同事务一次提交

- `CustomerOrderSaveDto` 增加可选 `agentDraftId/agentDraftRevision`。
- `CustomerOrderServiceImpl.create()` 在业务校验前锁定并验证草稿类型、owner、状态和版本。
- 订单与换菜规则成功后同事务标记 SUBMITTED 并记录订单 ID。
- 重复提交返回稳定错误，不生成第二笔订单；旧无草稿请求保持原逻辑。

### Step 6：提交后导航

- 成功刷新订单列表，可定位新订单并返回原会话。
- 原会话重新加载后草稿按钮转为已提交。
- 创建失败停留表单，保留客服已修改数据和 CLAIMED 草稿。

## 验证方式

- mapper 单测覆盖字段白名单和所有复杂控件。
- 页面测试覆盖 beforeToAdd 顺序、客户选项、权限金额、冲突校验和失败保留。
- Service 测试覆盖唯一客户、跨数据范围、重复提交、事务回滚和旧接口兼容。
- E2E 冒烟：已有客户订单草稿 -> 页面预填 -> 人工提交 -> 会话状态刷新。

## 完成标准

- 唯一匹配客户的订单可以可靠预填并人工提交。
- 多匹配/未找到客户不会错误打开订单表单。
- 正式订单创建与草稿 SUBMITTED 同事务。
- 编辑订单和普通手工新增不回归。

## 回滚

- 移除订单页 draftId 接收和后端可选草稿关联，普通订单新增保持可用。
- 未提交草稿按过期策略清理。

## 状态

completed
