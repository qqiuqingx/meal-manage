# Phase 03 未知卡片 LLM 规划与安全降级

## 目标

只为未登记 cardType 提供独立、无工具、无业务值的 LLM 展示规划；候选必须经后端校验，任何失败都降级为安全系统描述并增加稳定告警，不影响已知卡片和业务答案。

## 依赖

- Phase 02 的展示 DTO、字段目录、注册表和 `PresentationService`。

## 输入

- 未登记 cardType。
- 已通过敏感数据策略的卡片结构。
- 字段名、字段类型、可用路径、`truncated` 和完整性告警。

## 输出

- 合法候选转换为 `decisionSource=LLM` 的 v1 描述。
- 非法候选、超时、模型不可用或结构无法识别时生成 `SYSTEM` 通用降级描述。
- 告警统一增加 `PRESENTATION_FALLBACK_APPLIED`，不把模型/供应商异常原文返回前端。

## 涉及文件

新增：

- `agent-service/src/main/java/me/zhengjie/agent/presentation/CardSchemaInspector.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationSuggestion.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationPlanner.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/LlmPresentationPlanner.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationSuggestionValidator.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/GenericPresentationFactory.java`
- `agent-service/src/test/java/me/zhengjie/agent/presentation/CardSchemaInspectorTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/presentation/LlmPresentationPlannerTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/presentation/PresentationSuggestionValidatorTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/presentation/GenericPresentationFactoryTest.java`

修改：

- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationService.java`
- `agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java`
- `agent-service/src/main/java/me/zhengjie/agent/config/AgentProperties.java`
- `agent-service/src/main/resources/application.yml`
- `agent-service/src/test/java/me/zhengjie/agent/config/AgentPropertiesTest.java`

## 实施步骤

### Step 1：生成不含值的卡片结构摘要

- `CardSchemaInspector` 递归识别对象、数组和标量，只保留路径、字段名、类型及有限深度。
- 在结构提取前移除内部 ID 和禁用字段；不把姓名、订单号、日期、数量、状态等业务值发送给规划模型。
- 记录 `truncated`、空数组和影响完整性的稳定告警；限制最大路径数和嵌套深度，防止异常卡片放大 prompt。

### Step 2：增加独立模型 profile

- 在 `application.yml` 增加 `agent.models.profiles.presentation`，要求 `structured-output=true`、`tool-calling=false`，使用更短超时和独立重试上限。
- 复用 `AgentModelGateway.execute()` 的 provider fallback，但不注册 `BusinessAgentTools`。
- `AgentProperties` 校验该 profile 的能力和边界；未配置时直接走通用系统降级。

### Step 3：约束 LLM 输出

- `PresentationSuggestion` 只允许 `view/title/dataPath/dimensionField/metricFields` 以及普通文本标签。
- Prompt 明确禁止值、表达式、转换函数、HTML、代码、组件名和 ECharts option。
- 使用结构化输出解析；未知字段由 Jackson 严格模式拒绝。

### Step 4：后端二次校验

- 校验 view 白名单、标题/标签长度和纯文本。
- 校验路径、维度、指标字段存在且类型兼容，不允许引用敏感字段或内部 ID。
- 校验 `defaultView`、类别数、指标数、数据完整性和截断状态。
- BAR/LINE 最多 50 类，PIE 最多 8 类；饼图超限先改 BAR，BAR 仍超限只保留表格。
- 截断或完整性告警时拒绝图表，不允许根据当前页求和、分组或占比。

### Step 5：实现稳定通用降级

- 安全数组：生成有限列通用表格，列只来自可展示字段。
- 安全对象：生成键值摘要。
- 无法安全识别：生成 TEXT 提示“展示格式暂不可用”。
- 降级描述来源为 `SYSTEM`，同时追加一次 `PRESENTATION_FALLBACK_APPLIED`；不把展示失败错误地转换成 `partial=true` 的业务查询失败，保留原业务 partial 语义。
- `BusinessAgentRunner` 分开计算业务完整性告警和展示告警：`partial` 只由工具失败、截断和业务数据不完整决定，展示告警可见但不改变业务查询状态。

### Step 6：隔离错误与日志

- 规划器异常只能影响当前未知卡片，其他卡片继续生成展示描述。
- 日志只记录 requestId、cardType、候选 view、校验结果、稳定原因码和耗时；不记录结构 prompt、业务值、姓名或模型原文。
- Agent 主回答和工具事实不因展示规划失败重试或丢弃。

## 验证方式

- 模型桩验证：合法 BAR 建议通过，模型没有任何工具回调。
- 拒绝测试：未知 view、不存在路径、字符串指标、敏感字段、HTML 标题、超限系列。
- 完整性测试：`truncated=true`、结果告警、50/8 类边界和空数据。
- 故障测试：超时、无 profile、结构化解析失败、provider 不可用均返回通用展示和稳定告警。
- 回归测试：所有已知 cardType 的模型调用次数为 0。

## 完成标准

- LLM 输入和输出均不携带业务值副本。
- LLM 无工具、无代码、无任意转换能力。
- 任一规划失败不会影响 `assistantMessage`、`cards`、原工具告警或其他展示。
- 已知生产卡片继续只使用系统规则。

## 回滚

- 禁用 `presentation` profile 即可让未知卡片全部走通用降级；系统规则无需回滚。
- 移除 LLM planner 接线不会改变 Agent 主模型 Tool Calling 流程。

## 状态

completed
