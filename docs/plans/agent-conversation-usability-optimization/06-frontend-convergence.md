# Phase 06 前端代码收敛

## 目标

在行为已经稳定并有测试保护后，拆分过大的诊断页面，删除不再参与运行时的旧卡片组件和未使用前端 API，降低后续维护成本。

## 依赖

- Phase 01 至 Phase 05 全部完成。
- 所有新增交互已经有页面级测试，清理前先运行一次并保存基线结果。

## 输入

- 当前约 1373 行的 `diagnosis/index.vue`。
- 新的消息、卡片动作、技术详情和会话列表行为。
- 旧业务卡片组件、历史兼容工具和现有单元测试引用。

## 输出

- 职责清晰的页面子组件。
- 删除确认无运行时引用的旧组件与前端 API。
- 保持历史快照兼容和当前视觉行为。
- 更聚焦的组件测试。

## 涉及文件

新增建议：

- `eladmin-web/src/views/agent/diagnosis/components/AgentSessionSidebar.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentMessageList.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentChatMessage.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentComposer.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentDiagnosisResult.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentBusinessResult.vue`
- 对应的精简组件测试文件。

修改：

- `eladmin-web/src/views/agent/diagnosis/index.vue`
- `eladmin-web/src/api/agentDiagnosis.js`
- `eladmin-web/tests/unit/api/agentDiagnosis.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/historyPresentation.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/structuredPresentationAcceptance.spec.js`

候选删除对象，执行时必须重新用 `rg` 确认：

- `activeCustomerBalanceCard.vue`
- `businessDishCandidateCard.vue`
- `businessDishCard.vue`
- `businessHistoryCard.vue`
- `businessMealPlanCard.vue`
- `businessOperationStatsCard.vue`
- `businessOrderListCard.vue`
- `businessPackageCard.vue`
- `businessRuleCard.vue`
- `businessScheduledMenuCard.vue`
- `customerCandidateCard.vue`
- `customerOverviewCard.vue`
- 仅服务上述旧组件的单元测试。

候选删除前端 API：

- `confirmActionDraft`
- `queryActionAudits`

只删除前端无引用导出；本 Phase 不删除后端 Controller、Service、表或审计数据。

## 组件职责

### `index.vue`

- 只负责页面级状态、API 编排、当前会话选择和跨组件事件处理。
- 不直接包含诊断原因表格、工具链路表格或卡片具体模板。

### `AgentSessionSidebar`

- 搜索、进行中/归档切换、加载更多和会话选择。
- 通过事件通知父组件，不直接调用 API。

### `AgentMessageList` / `AgentChatMessage`

- 消息列表滚动和单条消息布局。
- 助手 Markdown、安全卡片、状态和动作组合。

### `AgentComposer`

- 输入、Enter 发送、loading/archived 禁用和快捷回复。
- 不保存会话业务状态。

### `AgentDiagnosisResult`

- 诊断摘要、原因、证据、反馈和技术详情入口。
- 不处理普通业务卡片。

### `AgentBusinessResult`

- 消息级告警、查询时间和 presentation cards。
- 负责向卡片传 partial/warnings，不解析业务值。

## 实施步骤

### Step 1：建立当前行为基线

- 运行 diagnosis 全部 60+ 单元测试及 Phase 01-05 新增测试。
- 记录 `index.vue` 对外事件、方法和现有路由行为。
- 使用 `rg` 列出每个旧组件和 API 的运行时、测试、文档引用。

### Step 2：先拆纯展示组件

- 依次提取 BusinessResult、DiagnosisResult、ChatMessage。
- props 使用当前规范化 message 对象，不重新定义平行字段名。
- 事件只包含业务动作，如 `retry/select-customer/open-route/feedback`。
- 不引入 Vuex、provide/inject 或事件总线。

### Step 3：拆会话侧栏和输入区

- Sidebar 接收列表状态并发出查询/选择事件。
- Composer 接收 draft、loading、archived、quickReplies 并发出发送事件。
- 保持父组件拥有 API 调用和 activeSessionId，避免跨组件隐式状态。

### Step 4：缩减 `index.vue`

- 删除已迁出的模板、样式和仅用于子组件的格式化方法。
- 保留 API 编排、消息规范化、路由跳转和会话生命周期。
- 目标不是追求固定行数；以职责清晰、测试可读为完成条件。

### Step 5：删除旧卡片组件

- 只有 `rg` 确认 src 运行时无引用后才删除。
- 将仍有价值的时间回退、指标中文和公共菜单分组测试迁移到 formatter、presentation 或 acceptance 测试。
- 历史 cards-only 继续由 `agentPresentationCompatibility.js` 处理，不因删除旧 Vue 组件失效。
- 不删除当前 `AgentPresentationCard/ResultTable/ResultSummary/ResultChart`。

### Step 6：清理未使用前端 API

- 删除无运行时调用的 action draft API 导出和对应 API 测试。
- 保留后端能力和历史文档，除非另有下线需求。
- 确认其他页面没有从默认导出间接访问这些方法。

### Step 7：调整测试边界

- `index.spec.js` 只测试页面编排和 API 状态。
- 子组件分别测试展示与事件。
- 保留一个跨组件 acceptance 测试验证完整响应渲染。
- 删除仅验证已删除组件内部实现的测试，不减少业务场景覆盖。

## 验证方式

- `rg` 确认删除文件无 src 引用。
- 执行 diagnosis 全量单元测试、API 测试、lint 和生产构建。
- 对比 Phase 05 前后的核心 DOM：会话、消息、告警、卡片、快捷回复和输入区。
- 执行 `git diff --check`，检查无意删除和格式问题。

## 完成标准

- `index.vue` 不再承载所有展示细节。
- 旧卡片组件和未使用前端 API 不再留在生产代码中。
- 历史卡片兼容、当前 presentation、诊断反馈和固定跳转全部通过测试。
- 不引入新状态库、事件总线、动态组件系统或通用动作框架。
- 不修改后端动作草稿和审计数据。

## 回滚

- 组件拆分和死代码删除使用独立提交；可只回滚 Phase 06，不影响前五个 Phase 的响应契约。
- 如果旧历史样例回归，恢复对应旧组件前先检查 compatibility 映射；不得恢复未筛选原始 JSON 展示。

## 状态

completed
