# agent-service

内部智能客服与排餐诊断服务。正常前端流量必须经 `eladmin-system` 的鉴权、数据范围和二次工具授权后再访问本服务；不要把本服务的内部接口直接暴露给前端或外网。

## 环境与启动

- Java 17，Maven 3.9.9；执行前使用 `jenv shell 17` 和 `mvn399`。
- 默认端口：`18081`。主系统地址：`AGENT_CONTEXT_BASE_URL`（默认 `http://localhost:8000`）。
- 内部调用令牌：`AGENT_INTERNAL_TOKEN`。生产/预发环境必须配置非空令牌。
- 默认 Spring AI provider 使用 `AGENT_DEEPSEEK_API_KEY`、`AGENT_DEEPSEEK_BASE_URL`、`AGENT_DEEPSEEK_MODEL`。业务层通过 `AgentModelGateway` 选择 `default` profile；可用 `AGENT_MODEL_DEFAULT`、`AGENT_MODEL_DEFAULT_TIMEOUT_MS`、`AGENT_MODEL_DEFAULT_STRUCTURED_OUTPUT`、`AGENT_MODEL_DEFAULT_TOOL_CALLING`、`AGENT_MODEL_DEFAULT_MAX_RETRIES` 覆盖 profile。无模型时受控链路会降级，不得绕过规则和工具白名单。
- 会话默认无状态：主系统信封携带快照和 `sessionVersion`，Agent 回传 `expectedSessionVersion`。仅排障兼容时设 `AGENT_CHAT_STATEFUL_SESSION_CACHE_ENABLED=true`。

```bash
cd agent-service
source ~/.zshrc && jenv shell 17 && mvn399
mvn -q test
mvn -q spring-boot:run
```

真实模型评测只可在显式配置 API Key 后运行：

```bash
mvn -q -Preal-model-eval -Dtest=RealModelIntentEvaluationTest test
```

## 规则资源

规则位于 `rules/{scene}/`。`agent.rules.base-path` 指向外部规则根目录；若外部 scene 目录存在，会**完整覆盖** classpath 同名 scene，避免两个来源混用。加载器递归扫描所有 YAML，按相对路径稳定排序；仅根节点为含 `ruleId` 的数组的 YAML 会作为诊断规则，其余 YAML（提示词策略、建议模板）由对应组件负责。

新增规则 scene：创建 `rules/{scene}/`，在 `agent.rules.scene-directories` 登记业务场景到目录的映射，补充规则测试和评测集；不需要改 Loader Java 代码。

## 扩展约定

- 新能力：先定义受控语义与权限，再实现 `CapabilityHandler`、登记 `semantics/capability-catalog.yaml` 并添加行为测试；启动期会拒绝重复 ID、未知 profile 和空权限。
- 新工具：在 `ToolCatalog` 定义唯一元数据、输入/输出 DTO、权限、条数与超时，并经主系统二次鉴权；历史 `AgentBusinessToolRegistry` 仅为兼容门面。
- 新规则：提供 `schemaVersion`（当前为 1）、`ruleId`、`reasonCode`、版本、工具、证据、后续动作和 owner；未知字段或无效工具会加载失败。
- 配置：新公共配置优先加入 `AgentProperties`，不要继续新增散落的 `@Value`。

## 跨服务契约

服务间 v2 契约位于 `src/main/resources/openapi/agent-service-v2.yaml`。`POST /api/agent/v2/chat` 只接受 `AgentExecutionEnvelope`：客户端消息与主系统生成的会话快照、可用工具、`sessionVersion` 分离；响应回传 `contractVersion`、`requestId`、`clientMessageId` 与 `expectedSessionVersion`。旧的 `/api/agent/meal-plan/chat` 已标记为兼容入口，直到主系统完成一个完整发布周期的 v2 切换。

## 排障与回滚

`GET /api/agent/health` 是 liveness/readiness 摘要，只检查本地规则、模型 profile 和客户端配置，不发起真实模型或主系统调用；深度连通性检测须显式调用既有测试接口。规则加载失败时先核对 scene 映射、外部目录完整性及 YAML 字段；移除 `AGENT_RULES_BASE_PATH` 可回退到打包 rules。业务口径、安全边界和接口细节见 `eladmin/doc/business/智能排查助手业务说明.md` 与 `eladmin/doc/apidoc/智能排查助手接口文档.md`。
