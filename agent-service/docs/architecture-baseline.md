# agent-service 架构基线

> 更新日期：2026-08-04。本文记录 LLM 主导统一工具调用重构后的当前边界。

## 当前边界

| 项目 | 当前约束 | 真相源 |
|---|---|---|
| 应用入口 | 1 个 `BusinessAgentRunner` | `src/main/java/.../application/BusinessAgentRunner.java` |
| 只读工具 | 12 个，唯一登记 | `tool/ToolRegistry.java` |
| 工具循环 | 最多 6 次工具调用、4 个模型回合、100 条记录 | `AgentProperties.chat.tool-loop` |
| 主系统查询 | 统一 `/api/internal/agent/query/**` | `HttpMainSystemQueryClient` |
| 数据库访问 | 0 个数据库/JDBC/MyBatis 依赖 | `ArchitectureBoundaryTest` |
| 诊断规则 | YAML 规则目录，requiredTools 引用统一工具 | `rules/{scene}/` |
| 会话状态 | 主系统快照 + `sessionVersion`，Agent 无本地会话缓存 | v2 信封与 `ConversationPatch` |

## 工具集合

`searchCustomerProfiles`、`searchServiceCustomers`、`getServiceCustomerDetail`、`listMealPlans`、`listVerifications`、`listRefunds`、`previewDishCandidates`、`listScheduledDishes`、`searchDishes`、`getPackageDetail`、`queryBusinessMetrics`、`explainBusinessRule`。

工具名称、输入/输出类型、权限、结果上限、超时和卡片类型只能在 `ToolRegistry` 登记一次。主系统根据签名访问上下文裁剪可用工具；Agent 不接收完整权限集合，也不能自行扩大白名单。

## 请求与结果边界

模型负责理解问题、选择工具、组合工具和生成回答。Java 负责可信信封转换、工具注册、输入/输出/最终回答护栏、预算、同参缓存、事实审计和确定性卡片映射。主系统负责身份、权限、数据范围、关系校验和 SQL 分页。

工具结果进入模型前必须经过敏感数据和提示注入检查；返回前端的卡片隐藏内部关联 ID，只展示脱敏业务摘要。回答不能输出金额、完整手机号、完整地址、Token、权限集合或写操作结果。

成功工具结果由系统确定性生成卡片、表格或图表时，模型文本只总结直接结论、关键范围和异常提示，不逐行复述结构化明细；最终回答护栏会拒绝重复的 Markdown 表格。已有成功工具事实时，回答校验失败会立即改用确定性摘要并保留首次查询卡片，不再重复调用模型和工具，也不把校验失败误报为模型不可用。调试日志只记录模型、回答校验、工具和主系统查询的状态、数量、长度、耗时及稳定错误码，不记录完整工具参数或业务响应 JSON。

## 验证命令

```bash
cd agent-service
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q clean test

cd ../eladmin/eladmin-system
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q -DskipTests=false -Dtest='*Agent*Test' test
```

架构测试还会检查 Agent 服务没有数据库依赖、规则没有旧工具名、工具表数量为 12，以及生产代码没有固定关键词路由/旧计划编排类型。
