# Phase 06 历史兼容与前端异常回退

## 目标

为旧会话和展示异常建立只读兼容层：旧卡片不调用 LLM、不查询实时数据；非法描述或图表故障只移除无效视图，业务事实仍通过表格、摘要或文本读取。

## 依赖

- Phase 05 的固定展示组件。

## 输入

- 新消息：cards + presentations。
- 旧消息：只有 cards，可能仍含 `maskedName`。
- 字段缺失、路径错误、截断、空数据或 ECharts 初始化失败。

## 输出

- 面向 11 个已知 cardType 的前端历史只读映射。
- 未知旧卡片的安全键值摘要。
- 描述校验、图表故障、空数据和全部展示失败的稳定降级。

## 涉及文件

新增：

- `eladmin-web/src/views/agent/diagnosis/utils/agentPresentationCompatibility.js`
- `eladmin-web/src/views/agent/diagnosis/utils/agentPresentationValidation.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/agentPresentationCompatibility.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/agentPresentationValidation.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/historyPresentation.spec.js`

修改：

- `eladmin-web/src/views/agent/diagnosis/index.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentPresentationCard.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentResultChart.vue`
- `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentPresentationCard.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentResultChart.spec.js`

## 实施步骤

### Step 1：实现历史卡片只读映射

- 仅当恢复消息没有 presentations 时，根据已知 cardType 生成本地描述。
- 映射覆盖当前 11 类卡片，字段和中文标签与 Agent 系统规则一致。
- 同时兼容旧 `maskedName` 和新 `customerName`；不得用实时查询补全姓名。
- 兼容映射只在 `mapSessionMessages()` 恢复路径使用，不参与新消息展示决策。

### Step 2：实现未知旧卡片安全摘要

- 递归过滤 `customerId/orderId/dishId/packageId`、手机号、地址、金额、Token、权限、SQL 等字段。
- 对安全对象显示有限键值摘要；安全数组显示有限通用表格；无法识别时显示“展示格式暂不可用”。
- 不把完整对象重新 `JSON.stringify` 到页面，不解析自由文本为 HTML。

### Step 3：前端防御性校验描述

- 校验 schemaVersion、callId 关联、view 白名单、默认视图、标题/标签长度、列数、指标数和字段路径。
- 非法单视图只移除该视图，保留同卡片其他有效视图。
- 所有视图无效时保留 `assistantMessage` 和统一展示失败提示，不显示“查询无结果”。

### Step 4：处理数据完整性和空状态

- `truncated=true` 或影响完整性的 warning 时隐藏图表，继续显示表格和原 warning。
- 空图表数据显示空状态，不创建 ECharts 实例。
- 表格空数据与业务无结果分开表达，不能把格式错误解释为零条记录。

### Step 5：处理 ECharts 生命周期故障

- 捕获初始化和 setOption 异常，立即 dispose 已创建实例并向容器报告图表不可用。
- 容器移除图表页签，自动切换到表格或摘要。
- resize 异常不影响其他卡片；组件销毁后不再触发监听器。

### Step 6：固定新旧会话回归样例

- 新快照：恢复后保持 `decisionSource/defaultView`，不重新选择。
- 旧已知卡片：转换为中文表格/摘要，`maskedName` 原样显示。
- 旧未知卡片：安全摘要，不调用 API/LLM。
- 混合消息：一张卡片展示失败不影响其他卡片、工具摘要和 warning。

## 验证方式

- 用 API/模型 mock 断言历史恢复没有额外网络请求。
- 用固定旧 JSON 验证 11 类映射、姓名兼容和内部 ID 过滤。
- 注入错误 path、非法 view、截断数据、ECharts throw，验证逐级回退。
- 回归会话列表切换、消息恢复、诊断结果、工具追踪和部分失败警告。

## 完成标准

- 新旧会话均不展示已知卡片原始 JSON。
- 历史恢复不调用 LLM、不查实时业务数据、不补全姓名。
- 图表异常后表格/摘要仍可读取，且业务告警完整保留。
- 全部展示失败时仍显示简短结论和稳定提示。

## 回滚

- 兼容层可独立回滚到“安全摘要”单一路径，不影响新消息系统描述。
- 不修改历史数据库内容，因此不需要数据恢复。

## 状态

completed
