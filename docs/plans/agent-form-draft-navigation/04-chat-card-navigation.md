# Phase 04 对话草稿卡片与固定导航

## 目标

在智能客服页面展示草稿识别结果、缺失项和状态，并将 `READY` 动作安全映射到现有客户/订单页面。

## 依赖与输入

- Phase 03 的 `formDraftSummary/uiActions` 和历史恢复协议。
- 现有 `AgentMessageActions.vue` 固定业务跳转模式。

## 输出

- 对话内草稿状态卡片、缺失/风险提示和固定导航动作。
- 历史/归档兼容、转换动作和状态刷新测试。

## 涉及文件

新增建议：

- `eladmin-web/src/views/agent/diagnosis/components/AgentFormDraftCard.vue`
- `eladmin-web/src/views/agent/diagnosis/utils/agentFormDraftActions.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentFormDraftCard.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/agentFormDraftActions.spec.js`

修改：

- `eladmin-web/src/views/agent/diagnosis/index.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentChatMessage.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentMessageList.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentMessageActions.vue`
- `eladmin-web/src/api/agentDiagnosis.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentMessageActions.spec.js`
- `eladmin-web/tests/unit/api/agentDiagnosis.spec.js`

## 实施步骤

### Step 1：规范化消息字段

- `normalizeAssistantMessage()` 保存草稿摘要和固定动作。
- 历史消息缺失新字段时按 null/空数组处理。
- 归档会话只读展示草稿，不允许继续纠正或跳转到可提交表单，恢复会话后再操作。

### Step 2：实现草稿卡片

- 显示草稿类型、版本、状态、已识别字段数、缺失字段中文名和脱敏告警。
- `EDITABLE` 显示“继续对话补充/修改”，不显示去新建。
- `READY` 显示“草稿未提交”和相应去新建按钮。
- `SUBMITTED/EXPIRED/CANCELLED` 显示只读状态，不能复用为新建。
- 卡片绝不渲染服务端 HTML 或完整 payload。

### Step 3：固定导航映射

- `OPEN_CREATE_CUSTOMER_WITH_ORDER_FORM` 固定到 `/customer/profile`。
- `OPEN_CREATE_ORDER_FORM` 固定到 `/customer/order`。
- query 只包含 `draftId` 和 `sourceSessionId`；忽略响应中的 route/path/url/query。
- 沿用当前 Router 权限体系，目标页面继续执行自身权限检查。

### Step 4：纠正与转换交互

- 客服通过输入框发送“手机号改成……”等自然语言，仍走正常聊天接口。
- 客户未找到时显示受控“转为新增客户 + 首单”快捷动作；点击后发送固定业务语句，不直接在前端构造 payload。
- 多客户候选继续复用现有脱敏候选选择，只发送 `customerCode`。

### Step 5：状态刷新

- 从业务页面返回原会话时重新加载会话或查询草稿摘要。
- 已提交按钮即时转只读；摘要接口失败时不恢复可提交按钮。
- 处理重复点击导航，避免同时打开两次新增流程。

## 验证方式

- 组件测试覆盖 EDITABLE/READY/失效状态和缺失字段。
- 导航测试确认固定路径、仅受控 query、任意 URL 被忽略。
- 历史恢复和归档只读测试。
- 候选客户、普通查询跳转和错误重试回归测试。
- `NODE_OPTIONS=--openssl-legacy-provider npm test -- --runInBand` 中运行相关单测。

## 完成标准

- 客服能看懂草稿已识别、待补充和是否可跳转。
- 只有 READY 草稿出现正式表单跳转按钮；缺少唯一客户的 EDITABLE 订单草稿最多出现固定转换对话按钮。
- 响应无法控制任意路由或把敏感字段写入 URL。
- 原有客户/订单/排餐查询跳转不回归。

## 回滚

- 移除草稿卡片和动作映射后，聊天文本与原有查询卡片仍正常。
- 服务端新增字段被旧前端忽略。

## 状态

completed
