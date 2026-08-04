# agent-service

内部智能客服 Agent。前端请求必须先经过 `eladmin-system` 的登录权限、部门数据范围和短期访问上下文校验；本服务不连接数据库，也不对外暴露业务查询接口。

## 当前架构

- `BusinessAgentRunner` 是普通业务查询和排餐诊断的唯一应用入口。
- Spring AI `ToolCallAdvisor` 驱动模型自主选择、组合和排序工具；Java 只提供当前授权白名单、输入输出 Schema、预算和护栏。
- `ToolRegistry` 是唯一的 12 个只读工具登记表；工具结果先经过输出护栏，再生成事实、卡片和 trace 摘要。
- 主系统只通过 `POST /api/agent/v2/chat` 下发可信执行信封。Agent 回传 `conversationPatch`，主系统以 `sessionVersion` 条件提交。
- 业务数据只能通过主系统 `/api/internal/agent/query/**` 统一接口获取。Agent 不依赖 MyBatis、JDBC、数据库驱动或业务 Mapper。
- `rules/{scene}/` 是排餐诊断规则真相源；规则的 `requiredTools` 必须来自 `ToolRegistry`，证据只能来自成功工具事实。

工具预算：单轮最多 6 次工具调用、4 个模型回合、100 条业务记录、每次主系统请求默认 3 秒超时、最多 1 次回答修复。相同工具和规范化参数在同轮命中缓存。

## 环境与启动

- Java 17，Maven 3.9.9；执行前使用 `jenv shell 17` 和 `mvn399`。
- 默认端口：`18081`。主系统地址：`AGENT_CONTEXT_BASE_URL`（默认 `http://localhost:8000`）。
- 内部令牌：`AGENT_INTERNAL_TOKEN`；生产、预发必须配置非空值。
- 模型配置使用 `AGENT_DEEPSEEK_API_KEY`、`AGENT_DEEPSEEK_BASE_URL`、`AGENT_DEEPSEEK_MODEL`，并由 `AgentModelGateway` 选择 profile。

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

生产 profile 下内部令牌为空会在启动期失败。模型不可用时返回稳定 fallback；不会绕过工具白名单、主系统权限或数据护栏。

## 规则资源

规则位于 `rules/{scene}/`。外部 scene 目录存在时完整覆盖 classpath 同名目录，加载器递归读取 YAML 并校验 `schemaVersion`、规则 ID、版本、必需工具、证据字段、后续动作和 owner。未知工具或无效规则会使规则加载失败。

新增规则时只需在相应 scene 目录增加 YAML，并在 `requiredTools` 使用 12 个登记工具之一；同时补充规则加载测试和业务文档证据。

## 跨服务契约

契约唯一文件：`src/main/resources/openapi/agent-service-v2.yaml`。

`POST /api/agent/v2/chat` 只接受主系统生成的 `AgentExecutionEnvelope`，必须携带 `X-Agent-Access-Context`。客户端消息、会话摘要、可用工具和版本字段严格分离。响应包含 `cards`、`facts`、`warnings`、`partial`、`toolFacts`、`toolTraceSummary` 和 `conversationPatch`；不再使用关键词路由、固定业务查询计划或旧响应类型分支。

常见错误码：`INVALID_REQUEST`、`CONTRACT_VERSION_MISMATCH`、`PERMISSION_DENIED`、`SESSION_VERSION_CONFLICT`、`TOOL_NOT_AVAILABLE`、`DEPENDENCY_UNAVAILABLE`、`MODEL_UNAVAILABLE`、`MODEL_CAPABILITY_UNSUPPORTED`。

## 安全边界

- 工具输入拒绝权限、Token、数据范围、URL、SQL、表名、字段选择和任意排序字段。
- 主系统在 SQL 前执行权限、客户数据范围和对象关系校验；模型提交的关联 ID 不构成授权依据。
- 工具结果和最终回答禁止金额、价格、完整手机号、完整地址、内部 Token、权限集合和写操作声称。
- 工具自由文本按不可信数据处理，命中提示注入或敏感数据时拒绝该工具事实。
- 日志只保留请求 ID、工具名、状态、计数、耗时和稳定错误码，不记录原始问题、Prompt、完整模型输出或工具原始响应。

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
