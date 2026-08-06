# Phase 05 新消息固定组件渲染

## 目标

使用固定 Vue 2、Element UI 和现有 ECharts 依赖渲染新消息的摘要、表格和图表。诊断页只负责编排，不再按卡片类型拼字段或使用 `JSON.stringify` 展示已知业务结果。

## 依赖

- Phase 04 已将 `cards + presentations` 返回前端。

## 输入

- 同一条消息中的 cards、presentations、warnings、partial 和 queriedAt。

## 输出

- 固定展示容器、表格、摘要、图表组件和格式化工具。
- 表格/图表页签、默认视图、空状态和分页。
- 服务客户结果按客户编号、姓名、订单编号、下单时间、状态、套餐展示。

## 涉及文件

新增：

- `eladmin-web/src/views/agent/diagnosis/components/AgentPresentationCard.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentResultTable.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentResultChart.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentResultSummary.vue`
- `eladmin-web/src/views/agent/diagnosis/utils/agentPresentationFormatters.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentPresentationCard.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentResultTable.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentResultChart.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentResultSummary.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/agentPresentationFormatters.spec.js`

修改：

- `eladmin-web/src/views/agent/diagnosis/index.vue`
- `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`

## 实施步骤

### Step 1：集中处理字段路径和格式

- 格式化器只解析点路径和固定数组路径，不支持表达式、函数、括号调用或原型链字段。
- 实现 `TEXT/DATE/DATE_TIME/STATUS/MEAL_TYPE/NUMBER/BOOLEAN` 和空值格式化。
- 日期按现有本地展示习惯格式化；服务客户优先读取 `orderTime`，兼容缺字段时回退 `dealTime/createTime`。
- 状态、餐次映射集中维护，未知枚举按普通文本安全显示。

### Step 2：实现摘要组件

- `AgentResultSummary` 按受控字段配置渲染单指标、键值摘要和规则文本。
- 标题、标签、值全部用 Vue 文本插值；禁止 `v-html`、Markdown HTML 和动态组件。
- 长文本折行并限制展示高度，不修改原始业务值。

### Step 3：实现表格组件

- `AgentResultTable` 按 columns 渲染 Element UI 表格，支持单表和受控 sections。
- 列数、数据路径和数组类型再次防御性校验；无数据显示统一空状态。
- 采用前端本地分页，仅分页展示卡片中已有完整数据，不做分组、求和或占比。
- `customerCode` 使用主标识样式，`customerName` 使用普通辅助样式。

### Step 4：实现图表组件

- `AgentResultChart` 仅接受 BAR/LINE/PIE 描述，转换为项目固定 ECharts option。
- 禁止合并服务端任意 option、颜色、Tooltip formatter 或脚本。
- 只读取 descriptor 指定的数据路径和字段；空数据不初始化图表。
- 监听容器 resize，组件销毁和视图切换时调用 `dispose()`，避免实例泄漏。

### Step 5：实现展示卡容器与页签

- 用 `sourceToolCallId` 关联 card 和 presentation，找不到关联时交由 Phase 06 兼容层处理。
- 同时有表格与图表时显示“表格 / 图表”页签并选中 `defaultView`。
- 只有一种有效视图时不显示页签；每张卡片维护独立选中状态。
- 摘要可显示在视图上方；展示警告不覆盖工具原始 warning。

### Step 6：精简诊断页编排

- `addAssistantResponse()` 保存 presentations。
- 用 `AgentPresentationCard` 替换已知卡片的标题映射和 `<pre>{{ JSON.stringify }}</pre>`。
- 保留工具调用摘要、诊断链路、业务 warning、partial 和 queriedAt 区域。
- `assistantMessage` 继续作为简短纯文本结论，不从中解析 Markdown 表格或业务值。

## 验证方式

- 单元测试覆盖路径解析、日期/状态/餐次/空值和危险路径拒绝。
- 表格测试覆盖固定列顺序、分页、空状态、长文本和 sections。
- 图表测试 mock ECharts，验证 init/resize/dispose、固定 option 和空数据。
- 容器测试覆盖单视图无页签、双视图默认选择和卡片间状态隔离。
- `index.spec.js` 验证原诊断结果、工具摘要和 warning 区域不回归。

## 完成标准

- 新消息已知卡片不再显示原始 JSON 或 Markdown 源文本。
- 服务客户表格列顺序和主/辅身份样式正确。
- 图表 option 完全由固定代码构造。
- 无 `v-html`、动态组件、eval、Function 或任意表达式执行。
- ECharts 实例在切换和销毁时正确释放。

## 回滚

- 可暂时让容器退化为 Phase 06 的安全摘要；不得回退为未筛选原始 JSON。
- 没有新增 npm 依赖，回滚只涉及组件和诊断页接线。

## 状态

completed
