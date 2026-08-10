# Phase 03 结果完整性与告警展示

## 目标

修复消息级部分结果没有传入卡片、后端前缀错误码无法准确映射等问题，确保自然语言结论、告警、表格和图表对同一份完整性状态保持一致。

## 依赖

- Phase 02 已稳定消息状态和历史消息映射结构。
- 继续沿用当前结构化展示设计，不修改 presentation v1 Schema。

## 输入

- 消息级 `partial`、`warnings` 和 `queriedAt`。
- 卡片自身及其 data 中的 `truncated` 标记。
- 现有 `PresentationDescriptor` 和前端完整性校验器。

## 输出

- 统一错误码解析和中文告警映射。
- 页面级 partial/warnings 正确参与图表隐藏判断。
- 告警和查询时间只展示一次。
- 页面编排与卡片组件的集成测试。

## 涉及文件

新增建议：

- `eladmin-web/src/views/agent/diagnosis/utils/agentWarningMessages.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/agentWarningMessages.spec.js`

修改：

- `eladmin-web/src/views/agent/diagnosis/index.vue`
- `eladmin-web/src/views/agent/diagnosis/components/AgentPresentationCard.vue`
- `eladmin-web/src/views/agent/diagnosis/utils/agentPresentationValidation.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/AgentPresentationCard.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/agentPresentationValidation.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/structuredPresentationAcceptance.spec.js`
- `agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/BusinessAgentRunnerPresentationTest.java`

## 告警分类

前端先将原始 warning 解析为：

```text
source = 可选工具名
code   = 最后一个冒号后的稳定错误码
```

只映射已确认业务类别：

| 类别 | 典型错误码 | 用户提示重点 |
| --- | --- | --- |
| 权限 | `TOOL_PERMISSION_DENIED`、`PERMISSION_DENIED` | 当前账号缺少数据权限，不暴露对象是否存在 |
| 截断 | `RESULT_TRUNCATED`、包含 `TRUNCAT` | 当前只显示部分结果，建议缩小范围 |
| 预算 | `TOOL_BUDGET_EXCEEDED`、`TOOL_RECORD_BUDGET_EXCEEDED` | 已返回可用部分结果 |
| 工具失败 | `TOOL_EXECUTION_FAILED`、`TOOL_OUTPUT_INVALID`、`AGENT_QUERY_*FAILED` | 部分业务查询暂不可用 |
| 数据异常 | `MENU_RESULT_IMPLAUSIBLE`、包含 `INCOMPLETE` | 结果可能不完整并给出业务化建议 |
| 展示降级 | `PRESENTATION_*` | 默认不打断业务结论；仅无可用视图时提示展示暂不可用 |

未知稳定码不直接 `join` 输出给业务用户，统一显示简短通用提示；技术码保留在响应、审计和可折叠技术详情中。

## 完整性规则

- `message.partial=true`：该消息全部图表隐藏，表格和摘要保留。
- 卡片或其 data `truncated=true`：只隐藏该卡片图表。
- 消息 warnings 命中完整性类别：该消息全部图表隐藏。
- 纯展示降级 warning 不将业务结果标记为 partial，也不隐藏其他完整卡片图表。
- 外层只展示一次告警和查询时间；不把 `queriedAt` 继续传给每张卡片重复显示。
- Assistant 文本如果未说明部分结果，由现有回答护栏负责；前端不改写模型结论。

## 实施步骤

### Step 1：建立轻量错误码工具

- 实现 `parseAgentWarning(value)`，只做字符串规范化和最后一段 code 提取。
- 实现 `businessWarningMessage(warnings, partial)`，按权限、数据异常、预算、工具失败、截断的优先级返回一条中文摘要。
- 实现 `hasBusinessIntegrityWarning(warnings)` 供页面或展示校验复用。
- 不建立可配置规则引擎，不从服务器下载文案映射。

### Step 2：补齐卡片属性透传

在 `index.vue` 渲染 `AgentPresentationCard` 时传入：

- `:partial="message.partial"`
- `:warnings="message.warnings || []"`

继续由外层显示 `queriedAt`，不传给卡片，避免重复查询时间。

### Step 3：统一图表隐藏判断

- `AgentPresentationCard.safePresentation` 使用消息级 props 调用现有 `sanitizePresentation()`。
- `shouldHideChart()` 继续处理消息 partial、卡片 truncated 和完整性 warning。
- 视图移除后优先回退 TABLE，再回退 TEXT；如果 descriptor 只声明图表且被隐藏，显示“结果不完整，暂不展示图表”，不能显示“暂无业务数据”。
- 不改写卡片业务数据，不在前端重新聚合。

### Step 4：收敛告警展示

- `queryWarningText()` 改为调用集中工具，删除组件内重复字符串判断。
- 权限提示优先于通用工具失败；数据异常提示优先于普通截断。
- 多个同类 warning 只展示一条摘要。
- 技术错误码仅在 Phase 04 的技术详情中按权限展开。

### Step 5：复核 Agent partial 语义

- 保持业务 warning 决定 `partial`、presentation warning 不决定 `partial` 的现有原则。
- 去重 response warnings，保持首次出现顺序。
- 确认 `toolName:RESULT_TRUNCATED`、工具输出内 warnings 和工具失败均进入响应。
- 不为每种工具新增专用异常分支。

### Step 6：增加页面级集成测试

- `index.vue` 的 shallow mount 断言子卡片收到 partial/warnings。
- 消息 partial 时完整指标 presentation 不渲染图表，但表格或摘要存在。
- `listMealPlans:TOOL_PERMISSION_DENIED` 显示权限文案而不是通用文案。
- presentation fallback warning 不将完整业务结果误显示为查询失败。
- 多卡片只显示一次查询时间和一次消息级告警。

## 验证方式

- 执行 warning 工具、presentation validation、卡片和 index 单元测试。
- 执行 `structuredPresentationAcceptance.spec.js`。
- 执行 Agent presentation 测试，确认 partial 与 presentation warnings 分离。
- 人工构造权限、截断、工具失败和展示失败四类响应进行页面检查。

## 完成标准

- 页面级部分失败时不再显示可能误导的图表。
- 带工具名前缀的权限、预算和截断错误能显示正确中文提示。
- 业务告警不重复显示，内部错误码默认不暴露。
- 表格、摘要、自然语言回答和成功卡片不会因单个图表失败而丢失。
- 没有新增异常规则引擎、重复安全校验器或新的展示协议版本。

## 回滚

- warning 工具为纯前端函数，可单独回滚到旧文案映射。
- partial/warnings 属性接线可独立回滚，不改变服务端响应。
- Agent warning 去重不改变错误码内容，回滚无需数据处理。

## 状态

completed
