# Phase 04 任务型对话交互

## 目标

把澄清、候选客户、查询失败和结构化结果转换为可以直接操作的客服工作流，减少重复输入，同时把技术信息移出默认业务视图。

## 依赖

- Phase 02 提供 `missingSlots/quickReplies`。
- Phase 03 提供稳定的告警摘要和完整性展示。

## 输入

- 当前与历史助手消息的 status、quickReplies、missingSlots、cards、diagnosisResult 和 requestId。
- 前端已知 cardType 及卡片中已有的安全业务编号、日期和餐次。
- 用户刚发送的原始文本，仅保存在当前页面消息对象中供失败重试。

## 输出

- 快捷追问按钮。
- 候选客户一键选择。
- 失败消息一键重试。
- 结论复制和固定业务页面跳转。
- 简化的默认消息层级和折叠技术详情。
- 防止重复发送和更清晰的加载反馈。

## 涉及文件

新增建议：

- `eladmin-web/src/views/agent/diagnosis/components/AgentMessageActions.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentTechnicalDetails.vue`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentMessageActions.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentTechnicalDetails.spec.js`

修改：

- `eladmin-web/src/views/agent/diagnosis/index.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentPresentationCard.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentResultTable.vue`
- `eladmin-web/src/views/agent/diagnosis/utils/agentPresentationCompatibility.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentPresentationCard.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentResultTable.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/structuredPresentationAcceptance.spec.js`

如果复制功能需要工具函数，可在 diagnosis utils 下增加一个小文件；优先使用浏览器现有 Clipboard API 和项目现有兼容写法，不新增 npm 依赖。

## 固定交互规则

### 快捷回复

- 只显示最后一条助手消息的 quickReplies，历史消息不铺满按钮。
- 点击后直接作为用户消息发送；发送前允许用户在输入框中编辑的需求不在首版实现。
- 正在请求时禁用快捷回复。

### 候选客户选择

- `CUSTOMER_PROFILE_LIST` 且当前消息为 `NEED_MORE_INFO` 时，在表格增加固定“选择”列。
- 选择动作只读取当前行安全的 `customerCode`；没有编号时不显示按钮。
- 点击后发送 `客户编号 {customerCode}`，不发送内部 ID、姓名或整行 JSON。
- 不修改通用 presentation Schema，不增加后端下发的动态 action。

### 重试

- 网络错误或 `status=ERROR` 的消息显示“重试本条”。
- 前端消息对象保存对应用户文本和 clientMessageId 关联；重试生成新的 clientMessageId，防止命中已失败请求的幂等重放。
- 重试不重复插入原用户气泡，可以在原错误消息下显示“正在重试”；成功后追加新助手响应。
- 若会话已经归档或不存在，明确提示新建会话，不自动跨会话重放。

### 业务跳转

只实现已确认的固定路由：

- 客户编号 -> `/customer/profile`
- 订单编号或客户编号 -> `/customer/order`
- 日期 + 餐次 -> `/meal/production-sheet`

动作由前端按 cardType 和安全字段决定。禁止响应中携带 URL、路由名、权限字符串或任意 query 参数。

## 实施步骤

### Step 1：完善消息对象

- 用户消息保存 `clientMessageId` 和发送状态。
- 助手消息保存 `requestId/clientMessageId/status/missingSlots/quickReplies/retryText`。
- 历史消息没有 retryText 时不提供重试，避免重放无法确认的旧问题。
- `addAssistantResponse()` 和恢复映射使用同一消息规范化函数，减少字段遗漏。

### Step 2：接入快捷回复和缺失项

- 输入框上方显示最后一条助手消息的快捷按钮。
- 缺失项使用业务中文标签，直接放在澄清消息附近。
- 删除所有正常 `ANSWERED` 消息上的“阶段：已回答”标签。
- `ERROR` 和 `NEED_MORE_INFO` 保留清晰但不过度醒目的状态提示。

### Step 3：恢复候选选择

- 为 `AgentResultTable` 增加可选、固定的 row action slot 或 `selectable` prop。
- 只有 AgentPresentationCard 明确判断为候选客户场景时启用。
- 复用现有 `selectCustomerCandidate()`，并将未接线的旧候选组件测试迁移到新路径。
- 不为其他表格开放任意行操作。

### Step 4：实现失败重试与发送互斥

- `sendMessage()` 开头增加 `loading` 判断，Enter 和按钮走同一互斥逻辑。
- 请求失败时保留原消息和 retryText，不清空用户可恢复内容。
- 创建/获取会话失败时停止发送，不继续用无效 sessionId 请求。
- loading 文案统一为“正在查询业务数据…”，超过既有前端超时时间不额外轮询。
- 首版不实施 SSE、流式 token、取消请求或多阶段进度协议。

### Step 5：实现复制和固定跳转

- 每条已回答消息提供“复制结论”，复制 assistantMessage，不拼接隐藏技术信息和完整工具 JSON。
- 已知卡片根据安全编号显示固定跳转按钮。
- 诊断结果现有三个跳转按钮改为复用同一动作组件。
- 路由跳转前只做字段存在性判断，不增加重复权限预检查；目标页面继续执行自身权限控制。

### Step 6：收拢技术信息

默认视图保留：

- assistantMessage
- 业务告警与查询时间
- cards/presentations
- 排餐诊断摘要、原因、证据和建议动作

折叠到“技术详情”：

- 模型名
- 规则版本摘要
- 槽位置信度
- 工具调用摘要
- 诊断链路
- 稳定 warning code

直接移除业务页面中的 `展示规则：SYSTEM/LLM` 标签。技术详情默认关闭，不新增新的权限接口；如果现有页面已有运维权限标识则复用，否则所有内部客服可手动展开。

### Step 7：调整视觉层级

- 黄色告警只在 fallback、partial、权限或数据异常时出现。
- 固定的“AI 建议请人工确认”说明移到页面说明或诊断区轻提示，不在每条正常查询中重复。
- 业务卡标题字号、间距和按钮位置统一。
- 移动端快捷按钮可横向换行，表格继续使用现有横向滚动。

## 验证方式

- 前端测试覆盖快捷回复只出现在最后一条消息、发送互斥和缺失项展示。
- 候选客户测试覆盖按钮出现、只发送 customerCode、无编号时不显示。
- 重试测试覆盖新 clientMessageId、不重复用户气泡和归档会话提示。
- 跳转测试覆盖客户、订单和排餐固定路由，不接受卡片内 URL。
- 技术详情测试覆盖默认折叠、SYSTEM 标签移除和业务内容始终可见。
- 手工检查桌面宽屏和 768px 以下布局。

## 完成标准

- 歧义客户可以一键选择，不需要复制编号再输入。
- 澄清问题可以通过快捷回复继续。
- 失败查询可以重试，Enter 不会产生并发重复发送。
- 普通查询默认不显示模型名、规则摘要和展示规则来源。
- 业务用户可复制结论并跳转到已有业务页面。
- 没有新增动态动作协议、任意 URL、流式基础设施或第三方前端依赖。

## 回滚

- 新动作组件可以整体移除，消息和卡片仍按 Phase 03 展示。
- 技术详情折叠回滚不会影响响应数据和历史快照。
- 候选选择回滚后仍可手工输入客户编号，不影响查询接口。

## 状态

completed
