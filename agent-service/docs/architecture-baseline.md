# agent-service 架构基线

> 记录日期：2026-07-27。用于后续绞杀式迁移的对比，不代表业务口径变更。

## 当前规模

| 项目 | 基线值 | 说明 |
|---|---:|---|
| `MealPlanChatServiceImpl` | 2210 行 | 仍是历史兼容实现，后续按能力迁出。 |
| Agent 生产 Java 文件 | 209 | `src/main/java/me/zhengjie/agent`。 |
| Agent 测试类 | 72 | `src/test/java` 下的 `*Test.java`。 |
| 历史业务工具 | 18 | `ToolCatalog` 登记数量。 |
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
