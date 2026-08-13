# agent-service

内部智能客服 Agent。前端请求必须先经过 `eladmin-system` 的登录权限、部门数据范围和短期访问上下文校验；本服务不连接数据库，也不对外暴露业务查询接口。

## 当前架构

- `BusinessAgentRunner` 是普通业务查询和排餐诊断的唯一应用入口。
- Spring AI `ToolCallAdvisor` 驱动模型自主选择、组合和排序工具；Java 只提供当前授权白名单、输入输出 Schema、预算和护栏。
- `ToolRegistry` 是唯一工具登记表，当前包含 12 个只读工具和 1 个受控的 `saveFormDraft` 辅助草稿写工具；工具结果先经过输出护栏，再生成事实、卡片和 trace 摘要。
- 主系统只通过 `POST /api/agent/v2/chat` 下发可信执行信封。Agent 回传 `conversationPatch`，主系统以 `sessionVersion` 条件提交。
- 业务数据只能通过主系统 `/api/internal/agent/query/**` 获取；辅助草稿只能通过 `/api/internal/agent/form-drafts:save` 保存。Agent 不依赖 MyBatis、JDBC、数据库驱动或业务 Mapper，也不能直接写正式业务表。
- `rules/{scene}/` 是排餐诊断规则真相源；规则的 `requiredTools` 必须来自 `ToolRegistry`，证据只能来自成功工具事实。

工具预算：单轮最多 6 次工具调用、4 个模型回合、100 条业务记录、每次主系统请求默认 3 秒超时、最多 1 次回答修复。12 个只读工具的相同工具与规范化参数在同轮命中缓存；`saveFormDraft` 有副作用，禁止使用同参缓存，创建重试由主系统按客服、会话和消息幂等。

## 环境与启动

- 运行基线：Spring Boot 4.0.7、Spring AI 2.0.0、Spring Framework 7.x、Java 17，Maven 3.9.9；执行前使用 `jenv shell 17` 和 `mvn399`。
- 默认端口：`18081`。主系统地址：`AGENT_CONTEXT_BASE_URL`（默认 `http://localhost:8000`）。
- 内部令牌：`AGENT_INTERNAL_TOKEN`；生产、预发必须配置非空值。
- 模型配置使用 `AGENT_DEEPSEEK_API_KEY`、`AGENT_DEEPSEEK_BASE_URL`、`AGENT_DEEPSEEK_MODEL`，并由 `AgentModelGateway` 选择 profile。
- OpenAI-compatible fallback 默认关闭；只有同时设置 `AGENT_FALLBACK_OPENAI_ENABLED=true`、`AGENT_FALLBACK_OPENAI_BASE_URL`、`AGENT_FALLBACK_OPENAI_API_KEY` 和 `AGENT_FALLBACK_OPENAI_MODEL` 时才会参与主 Provider 失败后的业务层 fallback。
- 当前运行基线为 Spring Boot 4.0.7 + Spring AI 2.0.0；Jackson 2 通过 `spring-boot-jackson2` 和 `spring.http.converters.preferred-json-mapper: jackson2` 兼容现有 `com.fasterxml.jackson.databind`/YAMLMapper 代码。
- Boot 4 已将 Jackson 2 HTTP Converter 标记为待删除；在完成 Jackson 3 迁移前，不要恢复旧的 `spring.jackson` 或 `spring.ai.deepseek.chat.options.model` 配置。迁移时必须同步验证 HTTP JSON 严格反序列化和 YAML 规则加载。

```bash
cd agent-service
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q test
mvn -q spring-boot:run
```

常用变量：

| 变量 | 默认值 | 用途 |
|---|---|---|
| `AGENT_INTERNAL_TOKEN` | 空 | 主系统到 Agent 的内部身份令牌 |
| `AGENT_CONTEXT_BASE_URL` | `http://localhost:8000` | 主系统地址 |
| `AGENT_RULES_BASE_PATH` | `rules` | 外部规则根目录；不存在时读取 JAR 内资源 |
| `AGENT_MODEL_DEFAULT` | `deepseek-chat` | 通用模型 profile 的模型名 |
| `AGENT_DEEPSEEK_API_KEY` | 空 | DeepSeek API Key |
| `AGENT_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API 地址 |
| `AGENT_DEEPSEEK_MODEL` | `deepseek-chat` | DeepSeek 模型名 |
| `AGENT_CHAT_LOG_CONTENT` | `false` | 是否记录经过脱敏的正文；草稿可能包含自由文本地址，生产环境必须保持 `false`，仅保留摘要日志 |

生产 profile 下内部令牌为空会在启动期失败。模型不可用时返回稳定 fallback；不会绕过工具白名单、主系统权限或数据护栏。

健康检查：`GET /api/agent/health`；响应中的 `readOnlyToolCount=12` 和 `formDraftWriteToolRegistered=true` 是静态登记校验，不代表草稿写工具对当前客服可用；实际可用性仍由主系统开关、入口权限和目标新增权限签发。关闭开关时主系统仍可下发 12 个只读工具。本地启动冒烟时使用 `AGENT_DEEPSEEK_API_KEY=unused` 和测试内部令牌即可，不会触发模型请求。若启动失败，优先检查 JDK/Maven 版本、`AGENT_INTERNAL_TOKEN`、规则目录和 `AGENT_CONTEXT_BASE_URL`；OpenAI-compatible 配置不完整时应保持禁用，而不是填入占位密钥。

Agent 应用回滚不删除主系统草稿表：先在主系统设置 `AGENT_FORM_DRAFT_ENABLED=false` 使第 13 个工具从白名单消失，再恢复上一版 Agent JAR，并重新检查 `/api/agent/health`、普通对话和只读单工具对话。已有未提交草稿由主系统自然过期并清空 payload。

## 规则资源

规则位于 `rules/{scene}/`。外部 scene 目录存在时完整覆盖 classpath 同名目录，加载器递归读取 YAML 并校验 `schemaVersion`、规则 ID、版本、必需工具、证据字段、后续动作和 owner。未知工具或无效规则会使规则加载失败。

新增规则时只需在相应 scene 目录增加 YAML，并在 `requiredTools` 使用 12 个只读登记工具之一；诊断规则不得依赖 `saveFormDraft`，同时补充规则加载测试和业务文档证据。

## 跨服务契约

契约唯一文件：`src/main/resources/openapi/agent-service-v2.yaml`。

`POST /api/agent/v2/chat` 只接受主系统生成的 `AgentExecutionEnvelope`，必须携带 `X-Agent-Access-Context`。客户端消息、会话摘要、可用工具和版本字段严格分离。响应除原有 `cards`、`facts`、`warnings`、`partial`、`toolFacts`、`toolTraceSummary` 和 `conversationPatch` 外，可选返回脱敏的 `formDraftSummary` 与固定枚举 `uiActions`；旧主系统和前端可安全忽略新增字段。

常见错误码：`INVALID_REQUEST`、`CONTRACT_VERSION_MISMATCH`、`PERMISSION_DENIED`、`SESSION_VERSION_CONFLICT`、`TOOL_NOT_AVAILABLE`、`DEPENDENCY_UNAVAILABLE`、`MODEL_UNAVAILABLE`、`MODEL_CAPABILITY_UNSUPPORTED`。

## 安全边界

- 工具输入拒绝权限、Token、数据范围、URL、SQL、表名、字段选择和任意排序字段。
- 主系统在 SQL 前执行权限、客户数据范围和对象关系校验；模型提交的关联 ID 不构成授权依据。
- 普通只读工具结果、卡片和最终回答禁止金额、价格、完整手机号、完整地址、内部 Token、权限集合和正式写操作声称。完整手机号和地址仅可出现在 `saveFormDraft` 已登记输入路径，经主系统严格 DTO 校验后进入会话原文和草稿 payload；不得进入工具输出、卡片或普通日志。
- 工具自由文本按不可信数据处理，命中提示注入或敏感数据时拒绝该工具事实。
- 日志按 `requestId` 串联 LLM 回合、工具调用和主系统查询，默认只保留请求 ID、工具名、状态、计数、耗时和稳定错误码。生产环境不得开启 `AGENT_CHAT_LOG_CONTENT`；结构化脱敏不能可靠识别用户自由文本中的所有地址。

## 验证

```bash
cd agent-service
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q clean test

cd ../eladmin/eladmin-system
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q -DskipTests=false -Dtest='*Agent*Test' test
```

主系统业务口径、统一内部查询接口和权限矩阵见 `eladmin/doc/apidoc/智能客服Agent内部业务查询接口文档.md`、`eladmin/doc/business/智能排查助手业务说明.md` 及同目录下的客户、订单、排餐、核销业务说明。
