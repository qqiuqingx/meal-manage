# Phase 05 客户与首单预填提交

## 目标

将 `CREATE_CUSTOMER_WITH_ORDER` 草稿安全映射到现有“新增客户 + 首单”表单，并在客服手动提交时与草稿 `SUBMITTED` 状态保持同一事务。

## 依赖与输入

- Phase 01 的领取和提交锁定服务。
- Phase 04 的固定客户页面跳转。
- 实施前重读 `客户管理业务说明.md`、`订单管理业务说明.md` 和客户建档 API 文档。

## 输出

- 客户 + 首单草稿到现有表单的完整映射和来源提示。
- 正式客户创建与草稿 SUBMITTED 同事务的一次提交闭环。

## 涉及文件

新增建议：

- `eladmin-web/src/views/customer/profile/utils/agentCustomerDraftMapper.js`
- `eladmin-web/tests/unit/views/customer/profile/agentCustomerDraftMapper.spec.js`
- 主系统草稿到 `CustomerProfileSaveDto` 的映射/提交测试。

修改：

- `eladmin-web/src/api/agentDiagnosis.js` 或新增小型 `agentFormDraft.js` API 文件。
- `eladmin-web/src/views/customer/profile/index.vue`
- `eladmin-web/tests/unit/views/customer/profile/index.spec.js`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileSaveDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerProfileController.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/CustomerProfileService.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfileServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfileServiceImplTest.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/service/impl/AgentFormDraftServiceImpl.java`

## 实施步骤

### Step 1：领取并确认草稿类型

- 页面从受控 query 读取 `draftId`，调用 claim 接口。
- 只接受 `CREATE_CUSTOMER_WITH_ORDER` 和受支持的 schemaVersion。
- 无权、过期、已提交或版本不兼容时不打开空白表单。
- 显示来源提示、草稿版本、缺失项和“返回原会话”。

### Step 2：显式映射客户字段

- 映射姓名、手机号、孕周/生产日期、过敏、排除菜品/日期、医疗和特殊要求、备注。
- 映射默认/工作日/周末地址及联系人电话。
- 加载真实字典和菜品选项；失效 ID 不作为合法选中项。
- 不映射任何图片字段。

### Step 3：显式映射首单复杂字段

- 映射父子套餐、餐数、金额、日期、开始餐次、餐次类型、排餐模式、销售渠道和菜品份数。
- 把指定配送日期及餐次转换为现有日历结构。
- 加载并校验试餐关联订单和换菜规则两端菜品；失效项标红要求重选。
- 复用现有试餐简化建档默认值和编号池规则，不在前端复制后端真相源。

### Step 4：缺失与复核提示

- 缺失/风险字段使用黄色提示，不改变现有红色必填校验。
- 草稿值允许客服编辑；正式提交使用页面当前值，不要求与原草稿逐字段相同。
- 金额字段按 `customerOrder:amount:edit` 控制，无权限时不发送或由后端归零。

### Step 5：同事务一次提交

- 在保存 DTO 增加非业务字段 `agentDraftId/agentDraftRevision`，不落客户/订单表。
- `CustomerProfileServiceImpl.create()` 开始时锁定并验证草稿所有权、类型、状态和版本。
- 现有客户、地址、首单和换菜规则全部成功后，在同一事务标记 `SUBMITTED` 并记录新客户业务 ID。
- 任一步失败时业务数据和草稿状态一起回滚，草稿保持可重试。

### Step 6：成功反馈

- 成功后刷新列表，并提供查看新客户、返回原会话入口。
- 返回会话后刷新草稿摘要，使按钮转为只读。
- 不让 Agent 声称自己执行了客户新增。

## 验证方式

- 前端 mapper 单测覆盖全部登记字段、复杂日期、试餐和换菜规则。
- 页面测试覆盖自动开窗、黄色提示、失效关联、返回会话和异常不空开表单。
- Service 测试覆盖无草稿旧流程、合法草稿、版本冲突、重复提交和事务回滚。
- 测试数据按唯一 draftId/customerCode 清理，不能删除其他客户。

## 完成标准

- 客户与首单可完整预填，客服仍可修改并手动提交。
- 旧手工新增客户接口行为保持兼容。
- 正式创建与草稿 SUBMITTED 同事务，重复提交不产生第二个客户。
- 图片字段始终为空且不阻塞流程。

## 回滚

- 前端忽略 draftId 后恢复普通新增表单。
- 后端 DTO 的可选草稿字段不影响旧请求；关闭草稿校验分支即可回滚。

## 状态

completed
