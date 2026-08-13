# Phase 07 安全、幂等与端到端验收

## 目标

对三端闭环进行安全和可靠性收口，证明敏感资料、工具副作用、权限变化、历史恢复和一次提交满足设计边界。

## 依赖与输入

- Phase 01 至 Phase 06 全部完成。
- 不在本阶段新增产品范围；发现协议缺陷回到对应 Phase 修复。

## 输出

- 敏感数据、工具副作用、权限、并发事务和 8 个端到端场景的验收证据。
- 三端完整回归结果和测试数据清理记录。

## 涉及文件

新增/修改测试建议：

- `agent-service/src/test/java/me/zhengjie/agent/tool/SaveFormDraftToolTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/guardrail/FormDraftSensitiveDataPolicyTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/infrastructure/observability/AgentDebugLogFormatterTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/BusinessAgentRunnerFormDraftTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/formdraft/AgentFormDraftServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/formdraft/AgentFormDraftControllerTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfileServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/order/service/impl/CustomerOrderServiceImplTest.java`
- `eladmin-web/tests/unit/views/agent/diagnosis/**/*.spec.js`
- `eladmin-web/tests/unit/views/customer/{profile,order}/**/*.spec.js`

生产代码只允许为修复本阶段发现的明确缺陷而修改。

## 实施步骤

### Step 1：敏感数据矩阵

- 验证完整手机号/地址可进入模型请求和 `saveFormDraft` 强类型入参。
- 验证普通日志、工具 trace、草稿卡片、摘要接口和错误响应均不含原值。
- 验证查询工具仍拒绝完整手机号/地址输出。
- 验证 Token、权限、SQL、URL、HTML、提示注入和超长文本被阻断。

### Step 2：工具副作用与幂等

- 同一 clientMessageId 的 Provider 重放、网络超时重试和用户重试不会创建重复草稿。
- 不同消息正常创建新草稿；显式修订只更新目标 draftId。
- `saveFormDraft` 不使用只读 cache，主系统是最终状态真相源。
- 版本冲突返回最新版本提示，不覆盖另一轮修改。

### Step 3：权限和所有权

- 创建、修订、领取和提交分别模拟权限被撤销。
- 跨客服、跨会话、跨目标页面和猜测 draftId 均失败。
- 金额编辑权限不能被草稿绕过。
- 客户数据范围变化后订单草稿不可继续提交。

### Step 4：一次提交和事务

- 并发双击或两标签提交同一草稿，仅一个事务成功。
- 客户/订单插入失败时草稿不变为 SUBMITTED。
- 标记 SUBMITTED 失败时业务新增整体回滚。
- 测试只删除本场景创建的草稿、客户和订单。

### Step 5：端到端场景

1. 完整文本 -> 客户 + 首单 READY -> 预填 -> 人工提交。
2. 普通字段缺失 -> READY -> 页面黄色补充 -> 提交。
3. 关键套餐歧义 -> EDITABLE -> 对话纠正 -> READY。
4. 唯一已有客户 -> CREATE_ORDER -> 提交。
5. 多客户候选 -> 人工选择 -> 订单草稿。
6. 未找到客户 -> 转客户 + 首单并复用字段。
7. 历史刷新、幂等重放和返回会话保持正确状态。
8. 过期/失效套餐、菜品、试餐订单安全失败。

### Step 6：全量回归

- `agent-service`: `mvn399 -q clean test`。
- 主系统 Agent/客户/订单测试：`mvn399 -q -DskipTests=false -Dtest='*Agent*Test,*CustomerProfile*Test,*CustomerOrder*Test' test`。
- 前端：运行 Agent、客户、订单相关 Jest 测试和 lint。
- 检查 `git diff --check`，并搜索日志语句是否可能输出 payload。

## 验证方式

- 保存每组 Maven/Jest 命令的通过结果和测试数量。
- 使用受控测试手机号、地址验证日志脱敏，测试结束后只删除本场景数据。
- 通过并发测试证明同一草稿只能成功提交一次。
- 手工执行 8 个端到端场景，并记录每个场景的草稿状态变化和最终业务结果。
- 在关闭 `saveFormDraft` 白名单后回归普通只读 Agent。

## 完成标准

- 安全矩阵、并发幂等和 8 个 E2E 场景全部通过。
- 现有只读查询、客户手工建档、订单手工新增/编辑无回归。
- 没有测试数据遗留、敏感日志或独立审计模块。
- 失败场景不会显示成功话术或“去新建”按钮。

## 回滚

- 任何高风险安全失败都阻止进入 Phase 08 和灰度。
- 通过关闭工具白名单及前端入口恢复到原只读 Agent。

## 状态

completed
