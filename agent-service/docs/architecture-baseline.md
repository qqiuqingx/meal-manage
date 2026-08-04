# agent-service 架构基线

> 记录日期：2026-07-27。用于后续绞杀式迁移的对比，不代表业务口径变更。

## 当前规模

| 项目 | 基线值 | 说明 |
|---|---:|---|
| `MealPlanChatServiceImpl` | 2210 行 | 仍是历史兼容实现，后续按能力迁出。 |
| Agent 生产 Java 文件 | 209 | `src/main/java/me/zhengjie/agent`。 |
| Agent 测试类 | 72 | `src/test/java` 下的 `*Test.java`。 |
| 历史业务工具 | 19 | `ToolCatalog` 登记数量。 |
| 语义能力 | 5 | `semantics/capability-catalog.yaml`。 |
| 规则 YAML | 9 | `rules/` 下全部 YAML。 |

## 已建立的边界

- HTTP v2 信封携带可信快照和 `sessionVersion`；Agent 返回 `expectedSessionVersion` 与 `ConversationPatch`。
- 主系统 `agent_chat_session.version` 使用 MyBatis-Plus `@Version` 条件更新；冲突会回滚本轮会话写入。
- 业务工具历史路径由 `ToolCatalog` 兼容，新工具由 `TypedAgentToolCatalog` 收集。
- 模型任务经 `AgentModelGateway` 选择 profile；规则、模型和主系统深度连通性不作为普通健康探针。

## 验证基线

```bash
cd agent-service
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q test

cd ../eladmin/eladmin-system
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q -Dtest='*Agent*Test' test
```

测试 profile 使用 `logback-test.xml` 将 `me.zhengjie.agent` 降为 WARN；真实模型评测仍必须显式启用 `real-model-eval` profile。

## 2026-07-29 收口快照

| 项目 | 当前值 | 相对基线 |
|---|---:|---|
| `MealPlanChatServiceImpl` | 42 行、1 个依赖字段、0 个业务分支 | 已成为兼容 Facade |
| `ConversationCoordinator` | 31 行 | 只选择唯一 `ConversationHandler`，无具体工具名 |
| `DefaultConversationHandler` | 1913 行 | 历史行为仍在渐进迁移；状态、兼容意图、旧客户汇总适配和测试依赖组装已拆出 |
| Agent 生产 Java 文件 | 212 | 包含新应用边界、模型网关、配置校验及兼容适配组件 |
| Agent 测试类 | 78 | 全量 329 个测试，0 failure、0 error、1 skipped |
| 历史业务工具 | 19 | 描述符与执行器名称集合启动期强制一致 |
| 诊断工具 | 11 | 与业务工具共享 `ToolDescriptor` 元数据协议 |
| 语义能力 | 5 | 由强类型能力目录加载并映射唯一 `plannerProfile` |
| QueryPlan 指标 | 15 | 使用 `AgentQueryMetric` 受控枚举 |
| 默认 Handler 业务查询响应/控制码 | 21 | 仍是后续响应类型目录化的迁移基线 |

本机 Java 17 + Maven 3.9.9 的 `mvn -q clean package` 用时约 6 秒且无普通 INFO 测试日志；该耗时只用于本地回归对比，不替代 CI 或灰度环境基线。架构测试已固定以下边界：领域包不依赖 Controller/Spring Web/HTTP Client，能力包不依赖 Controller 契约，Coordinator 不依赖具体 `Http*Client`，Facade/Coordinator 不包含业务意图和工具字符串。
