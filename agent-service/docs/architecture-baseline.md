# agent-service 架构基线

> 更新日期：2026-08-12。本文记录 LLM 主导统一工具调用及辅助表单草稿能力的当前边界。

## 当前边界

| 项目 | 当前约束 | 真相源 |
|---|---|---|
| 应用入口 | 1 个 `BusinessAgentRunner` | `src/main/java/.../application/BusinessAgentRunner.java` |
| 工具 | 12 个只读 + 1 个 `FORM_DRAFT_WRITE`，唯一登记 | `tool/ToolRegistry.java` |
| 工具循环 | 最多 6 次工具调用、4 个模型回合、100 条记录 | `AgentProperties.chat.tool-loop` |
| 主系统访问 | 只读 `/api/internal/agent/query/**`；草稿 `/api/internal/agent/form-drafts:save` | 两个 MainSystem Client |
| 数据库访问 | 0 个数据库/JDBC/MyBatis 依赖 | `ArchitectureBoundaryTest` |
| 诊断规则 | YAML 规则目录，requiredTools 引用统一工具 | `rules/{scene}/` |
| 会话状态 | 主系统快照 + `sessionVersion`，Agent 无本地会话缓存 | v2 信封与 `ConversationPatch` |

## Spring AI 2 / Boot 4 运行基线

| 项目 | 当前约束 | 说明 |
|---|---|---|
| 框架版本 | Spring Boot 4.0.7、Spring AI 2.0.0、Spring Framework 7.x | 由 `agent-service/pom.xml` 中的两个 BOM 统一管理 |
| 主 Provider | `spring-ai-starter-model-deepseek` | `spring.ai.model.chat=deepseek`，模型名使用 `spring.ai.deepseek.chat.model` |
| 备用 Provider | `spring-ai-openai` + `OpenAiCompatibleProviderModelGateway` | 默认关闭；完整 base-url、api-key、model 且显式启用后才参与 fallback，不启用 OpenAI 自动配置 Starter |
| 工具循环责任 | `BusinessAgentRunner` 请求级 `ToolCallAdvisor` | Provider 模型 Builder 不注入额外 `ToolCallingManager`，工具白名单、动态回调和预算仍由业务层控制 |
| 重试与 fallback | `FallbackModelExecutor` | SDK 不配置隐式重试，主/备用 Provider 的尝试顺序和可恢复失败重放由业务层控制 |
| JSON/YAML | `spring-boot-jackson2` + `spring.http.converters.preferred-json-mapper: jackson2` | 保持 `com.fasterxml.jackson.databind`、严格未知字段和 YAMLMapper；这是迁移到 Jackson 3 前的过渡边界 |

### 升级回滚

草稿表属于主系统数据库迁移，不随 Agent JAR 回滚。发布失败时先在主系统关闭 `AGENT_FORM_DRAFT_ENABLED`，再恢复部署系统归档的上一版 Agent JAR；恢复后重新执行健康接口、普通对话和只读单工具对话验证。草稿 DDL 默认保留，未提交 payload 按过期任务清理。

## 工具集合

只读工具：`searchCustomerProfiles`、`searchServiceCustomers`、`getServiceCustomerDetail`、`listMealPlans`、`listVerifications`、`listRefunds`、`previewDishCandidates`、`listScheduledDishes`、`searchDishes`、`getPackageDetail`、`queryBusinessMetrics`、`explainBusinessRule`。

辅助写工具：`saveFormDraft`。它只创建或修订 `CREATE_CUSTOMER_WITH_ORDER`、`CREATE_ORDER` 草稿，不能新增客户或订单；主系统以 `AGENT_FORM_DRAFT_ENABLED` 和当前客服新增权限共同决定是否把它放入执行信封。只读同参缓存不适用于该工具，创建依赖 `owner + sessionId + clientMessageId` 幂等，修订依赖 `draftId + expectedRevision` 乐观锁。

工具名称、输入/输出类型、权限、结果上限、超时和卡片类型只能在 `ToolRegistry` 登记一次。主系统根据签名访问上下文裁剪可用工具；Agent 不接收完整权限集合，也不能自行扩大白名单。

## 请求与结果边界

模型负责理解问题、选择工具、组合工具和生成回答。Java 负责可信信封转换、工具注册、输入/输出/最终回答护栏、预算、只读同参缓存、事实审计和确定性卡片映射。主系统负责身份、权限、数据范围、关系校验、SQL 分页以及草稿生命周期。

普通工具结果进入模型前必须经过敏感数据和提示注入检查；返回前端的卡片隐藏内部关联 ID，只展示脱敏业务摘要。草稿工具仅在登记的手机号/地址字段路径放行敏感输入，输出只含草稿 ID、状态、版本、字段路径、稳定告警和过期时间。回答不能输出金额、完整手机号、完整地址、Token、权限集合，也不能声称正式业务数据已经创建。

成功只读工具结果由系统确定性生成卡片、表格或图表时，模型文本只总结直接结论、关键范围和异常提示，不逐行复述结构化明细；最终回答护栏会拒绝重复的 Markdown 表格。`saveFormDraft` 成功事实确定性生成 `formDraftSummary`；`READY` 状态生成固定页面 `uiActions`，新增订单缺少唯一客户且无歧义时的 `EDITABLE` 状态仅生成固定转换对话动作，模型不能提供 URL。健康响应额外报告静态 `readOnlyToolCount=12` 和草稿写工具登记状态，但不把它们当作当前客服授权结果。已有成功工具事实时，回答校验失败会立即改用确定性摘要并保留首次结果，不再重复调用模型和工具。正文日志默认关闭，只记录状态、数量、长度、耗时及稳定错误码。

## 验证命令

```bash
cd agent-service
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q clean test

cd ../eladmin/eladmin-system
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q -DskipTests=false -Dtest='*Agent*Test' test
```

架构测试还会检查 Agent 服务没有数据库依赖、规则没有旧工具名、工具表为 12 个只读加 1 个辅助草稿写工具、写工具不可缓存，以及生产代码没有固定关键词路由/旧计划编排类型。
