# agent-service

内部智能客服与排餐诊断服务。正常前端流量必须经 `eladmin-system` 的鉴权、数据范围和二次工具授权后再访问本服务；不要把本服务的内部接口直接暴露给前端或外网。

## 环境与启动

- Java 17，Maven 3.9.9；执行前使用 `jenv shell 17` 和 `mvn399`。
- 默认端口：`18081`。主系统地址：`AGENT_CONTEXT_BASE_URL`（默认 `http://localhost:8000`）。
- 内部调用令牌：`AGENT_INTERNAL_TOKEN`。生产/预发环境必须配置非空令牌。
- 默认 Spring AI provider 使用 `AGENT_DEEPSEEK_API_KEY`、`AGENT_DEEPSEEK_BASE_URL`、`AGENT_DEEPSEEK_MODEL`。业务层通过 `AgentModelGateway` 选择 `default` profile；可用 `AGENT_MODEL_DEFAULT`、`AGENT_MODEL_DEFAULT_TIMEOUT_MS`、`AGENT_MODEL_DEFAULT_STRUCTURED_OUTPUT`、`AGENT_MODEL_DEFAULT_TOOL_CALLING`、`AGENT_MODEL_DEFAULT_MAX_RETRIES` 覆盖 profile。无模型时受控链路会降级，不得绕过规则和工具白名单。
- 会话始终无状态：主系统信封携带快照和 `sessionVersion`，Agent 回传 `expectedSessionVersion`；Agent 进程不提供可启用的 JVM 会话缓存。

```bash
cd agent-service
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q test
mvn -q spring-boot:run
```

最小本地配置：

```bash
export AGENT_INTERNAL_TOKEN=local-agent-token
```

常用环境变量：

| 变量 | 默认值 | 用途 |
|---|---|---|
| `AGENT_INTERNAL_TOKEN` | 空 | 主系统内部接口令牌；本地、预发和生产均应显式配置 |
| `AGENT_CONTEXT_BASE_URL` | `http://localhost:8000` | 主系统地址 |
| `AGENT_RULES_BASE_PATH` | `rules` | 外部规则根目录；目录不存在时读取 JAR 内资源 |
| `AGENT_MODEL_DEFAULT` | `deepseek-chat` | 通用理解模型 profile 的模型名 |
| `AGENT_MODEL_DIAGNOSIS` | 跟随 default | 排餐诊断 profile 的模型名 |
| `AGENT_MODEL_DIAGNOSIS_TOOL_CALLING` | `true` | 诊断 profile 是否支持工具调用；不满足时启动/路由明确失败 |
| `AGENT_CHAT_INTENT_CLASSIFIER_MODE` | `hybrid` | 仅允许 `rule_only`、`llm_only`、`hybrid` |
| `AGENT_CHAT_BUSINESS_SEMANTIC_MODE` | `llm_first` | 仅允许 `rule_only`、`shadow`、`llm_first` |
| `AGENT_CHAT_CONVERSATION_UNDERSTANDING_MODE` | `shadow` | 多帧理解灰度模式，仅允许 `shadow`、`new` |
| `AGENT_DEEPSEEK_API_KEY` | 空 | DeepSeek API Key（主 provider） |
| `AGENT_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API 地址 |
| `AGENT_DEEPSEEK_MODEL` | `deepseek-chat` | DeepSeek 模型名 |
| `AGENT_CLAUDE_ENABLED` | `false` | 是否启用 Claude 备用 provider（阶段 1 实施中） |
| `AGENT_CLAUDE_API_KEY` | 空 | Claude API Key（阶段 1 实施中） |
| `AGENT_CLAUDE_BASE_URL` | `https://api.anthropic.com` | Claude API 地址（阶段 1 实施中） |
| `AGENT_CLAUDE_MODEL` | `claude-sonnet-4-20250514` | Claude 模型名（阶段 1 实施中） |
| `AGENT_DEEPSEEK_EMBEDDING_MODEL` | `deepseek-embedding` | Embedding 模型名（阶段 2 实施中） |
| `AGENT_KNOWLEDGE_ENABLED` | `false` | 是否启用 RAG 知识检索（阶段 2 实施中） |
| `AGENT_KNOWLEDGE_DOCS_PATH` | `../eladmin/doc/business` | 知识库文档路径（阶段 2 实施中） |

聊天 mode 已使用枚举绑定，未知值会在启动期失败。Spring 激活 `prod`、`production`、`pre`、`preprod` 或 `staging` profile 时，`AGENT_INTERNAL_TOKEN` 为空同样会拒绝启动，并在错误中指出 `agent.internal-token` 配置路径。

真实模型评测只可在显式配置 API Key 后运行：

```bash
mvn -q -Preal-model-eval -Dtest=RealModelIntentEvaluationTest test
```

## 规则资源

规则位于 `rules/{scene}/`。`agent.rules.base-path` 指向外部规则根目录；若外部 scene 目录存在，会**完整覆盖** classpath 同名 scene，避免两个来源混用。加载器递归扫描所有 YAML，按相对路径稳定排序；仅根节点为含 `ruleId` 的数组的 YAML 会作为诊断规则，其余 YAML（提示词策略、建议模板）由对应组件负责。

新增规则 scene：创建 `rules/{scene}/`，在 `agent.rules.scene-directories` 登记业务场景到目录的映射，补充规则测试和评测集；不需要改 Loader Java 代码。

## 扩展约定

- 新能力：先在 `semantics/capability-catalog.yaml` 定义受控语义、权限和唯一 `plannerProfile`，再新增 `CapabilityHandler` 实现 `compile`，最后补充 Registry、QueryPlan、权限拒绝和结构化回答测试。中心 Coordinator 和 Planner 不应增加该能力的分支。
- 新工具：实现 `AgentTool<I,O>`，在 `ToolDescriptor` 声明 domain、action、权限、只读属性、数据分类、超时、条数及输入/输出 Schema 版本，并补充白名单裁剪和重复名称测试。尚未迁移的历史 Map 只能停留在基础设施转换边界，不得在新能力中扩散。
- 新规则：提供 `schemaVersion`（当前为 1）、`ruleId`、`reasonCode`、版本、工具、证据、后续动作和 owner；未知字段或无效工具会加载失败。
- 新模型 profile/provider：业务层只选择 profile；在 `AgentProperties.models.profiles` 声明模型名、超时、结构化输出、工具调用和重试能力。provider 适配代码放在 `infrastructure/llm`，任务入口调用 `requireCapabilities` 明确校验能力。
- 配置：新公共配置加入 `AgentProperties` 的嵌套对象；生产代码禁止新增散落的 `@Value`。

当前架构边界：

- `MealPlanChatServiceImpl` 只委托 `ConversationCoordinator`，中心入口不得出现业务意图或工具名。
- `DefaultConversationHandler` 继续承载尚未迁完的历史行为，但状态复制/恢复和兼容意图判定已分别交给 `ConversationStateSupport`、`BusinessQueryIntentPolicy`；生产类只保留一个注入构造器，测试依赖统一由 Fixture 组装。
- 客户餐数、核销和订单查询统一走强类型 `BusinessQueryDataClient`；旧 `DiagnosisToolDataClient` 三个客户汇总 Map 接口和 `LegacyCustomerInsightAdapter` 已删除。
- `BusinessQueryDataClient` 以 Typed Response 为主契约，生产 DTO 不提供 `fromLegacyMap` 反向适配；历史 Map 测试数据只允许在 `src/test` 夹具中转换。
- `ToolCatalog` 使用描述符表和执行器表统一登记历史只读工具，启动期校验两者名称集合一致；不要在调用方重新增加工具名 `if/switch`。

## 能力路线图

当前 agent-service 定位为**内部智能客服系统**（运营人员增强型），不涉及外部客户服务、人工转接、全渠道等场景。以下为 2026-07-29 差距分析后确认的待建设能力：

| 阶段 | 能力 | 状态 | 说明 |
|---|---|---|---|
| 阶段 1 | 多模型韧性 | 待实施 | 主备双 LLM provider（DeepSeek + Claude）+ 熔断自动切换 + 规则兜底 |
| 阶段 2 | RAG 知识检索 | 待实施 | 从 `eladmin/doc/business/` 构建知识库，支持"怎么做""规则是什么"类问答 |
| 阶段 3 | 幻觉检测 | 待实施 | 数据断言 API 反向校验 + 规则断言 YAML 匹配 + AI 建议标注 |
| 阶段 4 | 客户健康度评分 | 待实施 | 排餐失败/退款/核销异常/餐数紧张/过敏复杂度 五维度风险评分 |

详细方案见 `docs/superpowers/plans/2026-07-29-agent-service-多模型韧性与RAG知识检索实施方案.md`。

**新增扩展约定**（阶段实施后启用）：

- 新知识库文档：放入 `eladmin/doc/business/`，重启 agent-service 后自动索引；或调用 `POST /api/internal/agent/knowledge/refresh` 手动刷新。
- 新 provider：在 `AgentProperties.models.providers` 声明 type、apiKey、baseUrl；在 profile 的 `fallbackProviders` 中注册；provider 适配代码放在 `infrastructure/llm`。
- 新健康度维度：在 `health/domain/HealthDimension` 中定义维度枚举，在 `CustomerHealthScorer` 中实现计算逻辑，补充权重配置和测试。
- 幻觉检测仅对数据查询类意图启用；新增数据断言模式时同步更新 `DataAssertionValidator` 的正则提取规则。

## 跨服务契约

服务间聊天只支持 v2，契约位于 `src/main/resources/openapi/agent-service-v2.yaml`。`POST /api/agent/v2/chat` 只接受 `AgentExecutionEnvelope`：客户端消息与主系统生成的会话快照、可用工具、`sessionVersion` 分离；响应回传 `contractVersion`、`requestId`、`clientMessageId` 与 `expectedSessionVersion`。未上线的旧服务间路径 `/api/agent/meal-plan/chat` 已删除，不提供 v1 降级入口。

v2 必须携带 `X-Agent-Access-Context`。常见稳定错误码：

| 错误码 | HTTP | 是否重试 | 处理建议 |
|---|---:|---:|---|
| `INVALID_REQUEST` | 400 | 否 | 检查必需请求头、消息幂等键和字段长度 |
| `CONTRACT_VERSION_MISMATCH` | 400 | 否 | 调用受支持的 `v2` 契约 |
| `PERMISSION_DENIED` | 403 | 否 | 刷新主系统签发的访问上下文并检查业务权限 |
| `SESSION_VERSION_CONFLICT` | 409 | 是 | 重新读取会话快照后重试 |
| `CAPABILITY_NOT_AVAILABLE` / `TOOL_NOT_AVAILABLE` | 422 | 否 | 检查能力目录、Handler 和工具登记 |
| `DEPENDENCY_UNAVAILABLE` | 502 | 是 | 检查主系统内部接口连通性 |
| `MODEL_UNAVAILABLE` | 503 | 是 | 检查对应模型 profile/provider；多 provider 全部不可用时触发 |
| `MODEL_CAPABILITY_UNSUPPORTED` | 503 | 否 | 为任务选择支持结构化输出/工具调用的 profile |
| `KNOWLEDGE_UNAVAILABLE` | 503 | 是 | 知识库未加载或向量存储不可用；检查文档路径和向量数据库连接 |

## 排障与回滚

`GET /api/agent/health` 是 liveness/readiness 摘要，只检查本地规则、模型 profile 和客户端配置，不发起真实模型或主系统调用；深度连通性检测须显式调用既有测试接口。规则加载失败时先核对 scene 映射、外部目录完整性及 YAML 字段；移除 `AGENT_RULES_BASE_PATH` 可回退到打包 rules。业务口径、安全边界和接口细节见 `eladmin/doc/business/智能排查助手业务说明.md` 与 `eladmin/doc/apidoc/智能排查助手接口文档.md`。

灰度和回滚：

- 主系统固定调用 v2；契约问题通过回滚代码版本处理，不得切回已删除的 v1 路径。
- 多帧理解默认 `shadow`；异常时设置 `AGENT_CHAT_CONVERSATION_UNDERSTANDING_MODE=shadow` 或关闭对应能力开关。
- 模型 profile 异常时切回已验证 profile；不要通过关闭权限、Schema 校验或工具白名单绕过。
- 会话冲突只允许刷新快照重试，不允许用 Agent 本地缓存覆盖主系统版本。
- 多模型韧性：备用 provider 默认关闭（`AGENT_CLAUDE_ENABLED=false`），上线后逐步开启；主 provider 不可用时自动切换，无需手动干预。
- RAG 知识检索：默认关闭（`AGENT_KNOWLEDGE_ENABLED=false`），知识问答意图不命中时走现有规则兜底；异常时关闭开关即可回退。
- 幻觉检测：灰度期间仅标记不阻断，前端展示校验结果但不强制拦截；稳定后逐步收紧。

## 验证清单

```bash
# Agent 全量测试与可执行 JAR
mvn -q test
mvn -q clean package

# 主系统 Agent 测试（在 eladmin/eladmin-system）
mvn -q -DskipTests=false -Dtest='*Agent*Test' test

# 真实 MySQL 同 session 并发测试（在 eladmin/；需先配置 DB_*）
AGENT_DB_CONCURRENCY_TEST=true \
mvn -pl eladmin-system -am test -DskipTests=false \
  -Dtest='me.zhengjie.modules.agent.session.service.impl.AgentChatSessionConcurrencyIntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false

# 从 JAR 外部目录启动，验证规则只从 classpath 加载
cd "$(mktemp -d)"
java -jar /absolute/path/agent-service/target/agent-service-1.0.0-SNAPSHOT.jar \
  --agent.internal-token=local-agent-token --agent.ai.enabled=false

# 多模型 fallback（阶段 1）
# 模拟主 provider 不可达，验证自动切换到备用 provider
AGENT_DEEPSEEK_BASE_URL=http://localhost:9999 mvn -q test -Dtest='*Fallback*Test'

# 知识库刷新（阶段 2）
curl -X POST http://localhost:18081/api/internal/agent/knowledge/refresh \
  -H "X-Agent-Internal-Token: ${AGENT_INTERNAL_TOKEN}"

# 知识问答评测集（阶段 2）
mvn -q test -Dtest='*Knowledge*Test'

# 幻觉检测（阶段 3）
mvn -q test -Dtest='*Validation*Test'

# 客户健康度评分（阶段 4）
mvn -q test -Dtest='*HealthScore*Test'
```

日志只允许记录 requestId、sessionVersion、capabilityId、toolName、模型 profile、摘要计数、耗时和摘要 digest；禁止记录 sessionId、客户标识、用户原文、Prompt、模型完整输出、工具原始结果、异常消息、内部 token 或其他敏感字段。响应中的诊断 trace 同样只保留稳定异常类型，不携带下游异常原文。
