# Phase 07 联调、文档与发布验收

## 目标

完成 agent-service、主系统和前端的契约联调，验证多轮对话、澄清、完整性降级、任务操作和会话生命周期，更新业务/API/OpenAPI 文档并形成可回滚的发布清单。

## 依赖

- Phase 01 至 Phase 06 全部完成。
- `status.yaml` 中前六个阶段均为 `completed`。

## 输入

- 三模块实现和测试。
- 本计划 overview 中的 10 个核心验收场景。
- 当前智能排查助手业务说明、使用说明、运维说明和接口文档。

## 输出

- 跨模块契约和核心用户路径验收通过。
- 业务、使用、运维、API 和 OpenAPI 文档同步。
- 构建、发布、观测和逐模块回滚清单。
- `status.yaml` 更新为完成状态。

## 涉及文件

新增建议：

- `agent-service/src/test/java/me/zhengjie/agent/application/AgentConversationAcceptanceTest.java`
- `eladmin-web/tests/unit/views/agent/diagnosis/conversationUsabilityAcceptance.spec.js`

修改：

- `agent-service/src/main/resources/openapi/agent-service-v2.yaml`
- `agent-service/src/test/java/me/zhengjie/agent/contract/AgentServiceContractTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/client/HttpAgentServiceClientTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionConcurrencyIntegrationTest.java`
- `eladmin-web/tests/unit/views/agent/diagnosis/structuredPresentationAcceptance.spec.js`
- `eladmin/doc/business/智能排查助手业务说明.md`
- `eladmin/doc/business/智能客服助手使用说明.md`
- `eladmin/doc/business/智能客服助手运维配置说明.md`
- `eladmin/doc/business/智能客服助手灰度验收清单.md`
- `eladmin/doc/apidoc/智能排查助手接口文档.md`
- 本目录 `status.yaml`

如果接口文档已把会话接口拆到其他文件，实施时同步实际生效文档；不得只更新计划而遗漏对外契约。

## 实施步骤

### Step 1：跨服务契约验收

验证 v2 请求和响应：

- system prompt 与 user message 分离，日志长度符合预期。
- status 支持 `ANSWERED/NEED_MORE_INFO/ERROR`。
- missingSlots、quickReplies、conversationPatch 和 lastBusinessQueryContext 字段类型与 OpenAPI 一致。
- 主系统旧响应兼容、sessionVersion、clientMessageId 和 requestId 原样关联。
- 新旧消息快照刷新后内容一致，不触发实时查询。

### Step 2：多轮焦点验收

固定场景一：

```text
B3303 今天午餐排了吗？
那晚餐呢？
最近核销呢？
```

验收：

- 第二轮继承 B3303 和当天，只将餐次改为晚餐。
- 第三轮继承 B3303，不把晚餐强行用于未指定餐次的核销历史。
- 每轮实时事实都重新调用合适工具，不把会话摘要当作业务事实。

固定场景二：

```text
订单 O20260711001 什么情况？
那这个客户还有其他订单吗？
```

验收：

- 第二轮可使用订单关联客户焦点。
- 切换到另一客户编号后，旧订单焦点被清空。

### Step 3：澄清与候选验收

- “查一下排餐”返回 NEED_MORE_INFO 和缺失项，不调用无界查询。
- “查张三今天午餐”命中多个客户时展示候选表，不自动选第一个。
- 点击候选只发送客户编号并继续当前会话。
- 日期和餐次快捷回复可继续查询；没有业务选项时不生成虚假按钮。
- 页面刷新后澄清状态和快捷回复仍可恢复。

### Step 4：完整性与告警验收

- 单工具权限不足显示权限文案，不显示对象不存在或内部工具名。
- 一个工具失败、一个工具成功时保留成功卡片并标记部分结果。
- message.partial、卡片 truncated 和完整性 warning 都能隐藏图表。
- presentation 生成失败只降级当前卡片，不把业务查询标记为失败。
- 告警和查询时间在同一业务结果中只显示一次。

### Step 5：任务交互验收

- 网络失败后可重试本条，使用新 clientMessageId，不重复用户气泡。
- Enter 和按钮无法并发重复发送。
- 复制内容只包含面向用户的结论，不包含工具事实 JSON 和技术详情。
- 客户、订单、排餐跳转只使用固定路由和安全业务参数。
- 默认界面不显示展示规则来源、模型名、规则摘要和工具链路；技术详情可手动展开。

### Step 6：会话生命周期验收

- 超过 20 条会话时可以加载更多。
- 服务端关键字能找到首屏之外的标题、客户编号或最近摘要。
- 新建按钮只重置本地状态，首次发送后才创建服务端会话。
- 归档会话只读可查看，恢复后可继续发送。
- 不同客服无法查询、查看、恢复或修改对方会话。

### Step 7：安全与非回归验收

- 回答、卡片、Patch、quickReplies、日志和会话列表不出现完整手机号、地址、金额、Token、权限集合、SQL、内部 URL 或隐藏推理。
- 客户编号仍是主标识，姓名只作为辅助确认。
- 所有工具保持只读；前端固定动作不调用写业务接口。
- 订单、餐数、核销、退餐和排餐业务口径不变。
- 历史 cards-only 快照继续展示，不重新查询或调用 LLM。

### Step 8：更新业务和使用文档

`智能排查助手业务说明` 更新：

- 成功工具输入生成会话 Patch 的字段和切换规则。
- NEED_MORE_INFO、missingSlots 和 quickReplies。
- 部分结果图表隐藏与告警优先级。
- 会话搜索、归档恢复和历史兼容。

`智能客服助手使用说明` 更新：

- 多轮追问示例。
- 候选客户点击选择。
- 快捷回复、失败重试和固定业务跳转。
- 技术详情和业务结论的展示层级。

`运维配置说明/灰度验收清单` 更新：

- NEED_MORE_INFO 比率、重试率、部分结果率和上下文切换错误观测。
- 不采集用户消息正文、客户姓名或业务值。
- 灰度时固定执行本 Phase 的多轮、歧义和部分结果场景。

### Step 9：更新接口与 OpenAPI

- 聊天响应补充 NEED_MORE_INFO、missingSlots、quickReplies 和 ConversationPatch 示例。
- 会话接口补充 keyword 分页、archived 查询和恢复示例。
- 明确旧响应与旧快照兼容行为。
- OpenAPI 使用严格 enum 和数组限制，但不增加无业务意义的嵌套包装。
- 运行契约测试确保 Java 字段与文档一致。

### Step 10：执行测试和构建

执行时使用项目 `maven` 技能选择正确 Maven/JDK。建议命令：

```bash
/Users/qqx/job/maven/apache-maven-3.9.9/bin/mvn -f agent-service/pom.xml test
```

```bash
/Users/qqx/job/maven/apache-maven-3.9.9/bin/mvn -f eladmin/pom.xml \
  -pl eladmin-system -am -DskipTests=false -DfailIfNoTests=false test
```

```bash
cd eladmin-web
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service test:unit \
  tests/unit/views/agent/diagnosis tests/unit/api/agentDiagnosis.spec.js --runInBand
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/eslint \
  src/views/agent/diagnosis src/api/agentDiagnosis.js
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service build --mode production
```

最后执行：

```bash
git diff --check
git status --short
```

只报告和本计划相关的失败；不得修改或清理用户现有无关改动。

### Step 11：发布与观测

发布顺序：

1. 主系统：先支持新响应字段、Patch 提交、快照恢复和会话查询兼容。
2. Agent 服务：再启用新回合协议、上下文更新和 Prompt 分离。
3. 前端：最后启用澄清、动作、会话列表和代码收敛版本。

观察指标：

- `NEED_MORE_INFO` 比率及按缺失项分布。
- 用户紧接着重复客户编号/订单编号的比率是否下降。
- 工具失败率、partial 比率、图表降级次数。
- 单轮模型/工具耗时和失败重试成功率。
- 会话搜索、归档和恢复接口错误率。

日志只记录稳定码、计数和耗时，不记录用户原文或业务值。

## 验证方式

- Agent 服务：执行全部单元测试、契约测试和新增多轮会话验收测试。
- 主系统：执行 Agent client、session、权限、快照兼容和并发测试；测试产生的数据由测试自身清理。
- 前端：执行 diagnosis/API 单元测试、ESLint 和 production build。
- 契约：对照 OpenAPI 核验 status、missingSlots、quickReplies、ConversationPatch 和历史兼容字段。
- 人工场景：逐项执行 overview 的 10 个核心验收场景，记录请求状态、工具调用、页面结果和会话恢复结果。
- 安全检查：搜索构建产物、响应样例和日志样例，确认不含手机号、地址、金额、Token、权限、SQL 和内部 URL。
- 变更检查：执行 `git diff --check` 和 `git status --short`，只处理本计划相关文件。

## 完成标准

- overview 的 10 个核心验收场景全部通过。
- Agent 服务、主系统 Agent 测试、前端单测、lint 和生产构建通过。
- OpenAPI、业务说明、使用说明、运维说明和接口文档与实现一致。
- 不存在新增 DDL、写工具、动态动作协议或额外 LLM 调用。
- 没有修改用户无关文件，没有未清理测试数据。
- `status.yaml` 的所有 Phase 标记为 `completed`，`current_phase` 更新为 `complete`。

## 回滚

- 按前端、Agent 服务、主系统逆序回滚。
- 前端回滚后忽略新字段；Agent 回滚后主系统继续接受旧纯文本响应；主系统最后回滚。
- 如果结构化回合协议导致 Provider 兼容问题，先切回纯文本兼容路径，保留 Phase 01 的确定性上下文更新。
- 如果会话交互出现问题，只回滚 Phase 04-06 前端提交，不回滚业务事实和安全修复。
- JSON 快照新增键无需 DDL 回滚，旧代码会忽略。

## 验收记录

- agent-service：JDK 17 + Maven 3.9.9，全量 120 tests 通过。
- 主系统：Agent 相关定向 97 tests 通过，数据库并发集成测试按现有环境条件跳过；全量 reactor 另受 `eladmin-common` 既有 `FileUtilTest` 磁盘容量/格式断言和 `StringUtilsTest` SpringContextHolder 初始化失败阻断，未进入 Agent 模块。
- 前端：27 suites、156 tests 通过；`npm run lint` 通过；生产构建通过，仅保留既有 asset/entrypoint size warnings。
- 文档与契约：OpenAPI、聊天/会话 API、业务说明、使用说明、运维配置、灰度清单已同步；核销业务说明变更后的 SHA-256 已同步到业务规则目录。
- 变更检查：计划相关文件 `git diff --check` 通过；全工作区检查仍报告用户已有 `AGENTS.md` 第 264、266、270 行尾随空格，未修改。

## 状态

completed
