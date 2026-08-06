# Phase 07 跨模块验收、文档与发布准备

## 目标

完成主系统、Agent 服务和前端的契约联调，执行核心业务与安全验收，更新业务/API 文档并准备可独立回滚的发布顺序。

## 依赖

- Phase 01 至 Phase 06 全部完成。

## 输入

- 三模块实现和单元测试。
- 设计文档第 14 节验收案例。
- 当前业务/API 文档与 Agent v2 OpenAPI。

## 输出

- 跨服务契约和前端渲染验收通过。
- 相关业务说明、接口示例、安全边界和兼容说明同步完成。
- 明确的发布、观测和回滚检查单。

## 涉及文件

新增：

- `agent-service/src/test/java/me/zhengjie/agent/application/ServiceCustomerPresentationAcceptanceTest.java`
- `eladmin-web/tests/unit/views/agent/diagnosis/structuredPresentationAcceptance.spec.js`

修改：

- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/client/HttpAgentServiceClientTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/contract/AgentServiceContractTest.java`
- `agent-service/src/main/resources/openapi/agent-service-v2.yaml`
- `eladmin/doc/business/智能排查助手业务说明.md`
- `eladmin/doc/business/客户管理业务说明.md`
- `eladmin/doc/business/订单管理业务说明.md`
- `eladmin/doc/apidoc/智能排查助手接口文档.md`
- `eladmin/doc/apidoc/智能客服Agent内部业务查询接口文档.md`

## 实施步骤

### Step 1：执行核心服务客户验收

对问题“现在系统中的客户分别是什么时候下单的”固定验证：

1. 主模型调用 `searchServiceCustomers(status=ACTIVE)`。
2. `assistantMessage` 只返回简短结论，不重复 Markdown 明细。
3. `cards` 中每笔订单一行，包含 customerCode/customerName/orderCode/orderTime/status/package。
4. `presentations` 使用 `SYSTEM`、TABLE 和规定列顺序。
5. 前端客户编号第一列、姓名第二列，成交时间优先、创建时间回退。
6. 页面和日志不出现原始 JSON、内部 ID、手机号、地址或金额。

### Step 2：执行图表与降级验收

- 单值指标只显示摘要。
- 完整分类 breakdown 显示柱状图；完整且不超过 8 类的占比数据才显示饼图。
- 明确时间序列的受控测试卡默认折线图，并在存在表格时可切换。
- 截断数据不生成图表。
- 未知卡片合法 LLM 建议可渲染；非法建议、模型不可用和 ECharts 失败均保持业务事实可读。

### Step 3：执行身份和敏感数据验收

- 客户相关卡片和结论以 customerCode 为第一识别信息，姓名只辅助确认。
- 内部 Agent 查询可返回完整姓名；普通客户页面和非 Agent API 行为不变。
- 11 位手机号、完整地址、金额、价格、Token、权限集合、SQL 和内部 ID 均被 DTO/护栏/前端三层测试拦截。
- 日志仅含 requestId、工具名、数量、状态、告警和耗时，不含姓名或模型原文。

### Step 4：执行会话兼容验收

- 新消息保存并恢复原 presentations。
- 旧 cards-only 消息正常显示，maskedName 保持原值。
- 历史恢复没有业务查询和 LLM 调用。
- 会话幂等、版本冲突和并发测试继续通过。

### Step 5：更新业务文档

- `智能排查助手业务说明`：新增 presentations 数据流、系统规则优先、LLM 兜底、图表完整性和会话恢复规则。
- `客户管理业务说明`：内部 Agent 完整姓名使用范围、customerCode 主标识和其他隐私保护不变。
- `订单管理业务说明`：orderTime 确定性回退和服务客户表格口径。
- 文档不得描述尚未实现的卡片类型或指标口径。

### Step 6：更新接口与 OpenAPI 文档

- `智能排查助手接口文档`：响应示例、字段字典、错误/降级、历史兼容。
- `智能客服Agent内部业务查询接口文档`：maskedName -> customerName、orderTime、metric breakdown、安全边界。
- OpenAPI：presentations 严格 Schema、枚举、长度/数量限制和示例。
- 运行契约测试确保 Java 字段与 OpenAPI 属性一致。

### Step 7：全量验证

- Agent 服务：使用 JDK 17 和项目 Maven 执行全部测试。
- 主系统：使用 JDK 17，显式 `-DskipTests=false` 执行 Agent 模块测试；测试产生的数据必须由测试自身清理。
- 前端：使用 `NODE_OPTIONS=--openssl-legacy-provider` 执行诊断页单测、lint 和生产构建。
- 执行 `git diff --check`，确认没有修改用户现有无关文件。

### Step 8：发布与观测

建议顺序：

1. 主系统事实契约（兼容字段）与 presentations 透传/存储。
2. Agent 服务系统规则和 LLM 兜底。
3. 前端固定组件与历史兼容。

上线后观察：展示规则覆盖告警、`PRESENTATION_FALLBACK_APPLIED` 数量、图表初始化失败、Agent 响应耗时和未知 cardType；不采集业务值或姓名。

## 验证方式

依次执行 Agent 服务、主系统 Agent 模块和前端诊断页的全量验证，并人工核对核心服务客户、图表降级和新旧会话固定样例。

验证命令：

```bash
/Users/qqx/job/maven/apache-maven-3.9.9/bin/mvn -f agent-service/pom.xml test
```

```bash
/Users/qqx/job/maven/apache-maven-3.9.9/bin/mvn -f eladmin/eladmin-system/pom.xml -DskipTests=false test
```

```bash
cd eladmin-web
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service test:unit tests/unit/views/agent/diagnosis --runInBand
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service lint
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service build --mode production
```

## 完成标准

- 设计文档第 14 节核心验收案例全部通过。
- 三模块测试、前端 lint/build 和 OpenAPI 契约测试通过。
- 五份业务/API 文档与 OpenAPI 同步完成。
- 发布顺序、观测指标和逐模块回滚均可执行。
- 不存在未清理测试数据、DDL 或业务指标口径变更。

## 回滚

- 按前端、Agent 服务、主系统逆序回滚。
- 前端回滚后忽略 presentations；Agent 回滚后主系统仍能接收 cards；主系统新增 JSON 快照键可被旧代码忽略。
- 如 LLM 兜底异常，优先禁用 presentation profile，保留系统规则和通用安全降级。

## 状态

completed
