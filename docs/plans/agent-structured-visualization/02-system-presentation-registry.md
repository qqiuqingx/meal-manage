# Phase 02 系统展示契约与已知卡片规则

## 目标

在 Agent 服务建立 v1 展示描述、卡片字段目录和系统展示注册表，为所有当前卡片生成确定性的 `SYSTEM` 展示描述。已知卡片不得调用 LLM。

## 依赖

- Phase 01 已完成，客户姓名、`orderTime` 和指标 `breakdown` 字段稳定。

## 输入

- `ToolRegistry` 当前 12 个工具与 11 个 `cardType`。
- 成功工具调用生成的安全 `cards[].data`。

## 输出

- Agent v2 响应新增 `presentations`。
- 11 个已知卡片均有唯一系统规则。
- 重复规则、非法视图、未知路径或未知字段导致启动失败。
- 缺规则卡片只记录健康告警并成为 Phase 03 的兜底候选。

## 涉及文件

新增：

- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationDescriptor.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationFieldCatalog.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationRule.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationRegistry.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationRuleValidator.java`
- `agent-service/src/main/java/me/zhengjie/agent/presentation/PresentationService.java`
- `agent-service/src/test/java/me/zhengjie/agent/presentation/PresentationRegistryTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/presentation/PresentationRuleValidatorTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/BusinessAgentRunnerPresentationTest.java`

修改：

- `agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java`
- `agent-service/src/main/java/me/zhengjie/agent/domain/dto/AgentChatResponse.java`
- `agent-service/src/main/java/me/zhengjie/agent/domain/dto/AgentHealthResponse.java`
- `agent-service/src/main/java/me/zhengjie/agent/controller/AgentHealthController.java`
- `agent-service/src/main/java/me/zhengjie/agent/config/AgentServiceConfig.java`
- `agent-service/src/main/resources/openapi/agent-service-v2.yaml`
- `agent-service/src/test/java/me/zhengjie/agent/contract/AgentServiceContractTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/domain/dto/AgentChatDtoTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/controller/AgentHealthControllerTest.java`

## 实施步骤

### Step 1：定义严格的 v1 展示模型

- `PresentationDescriptor` 定义并限制：`schemaVersion`、`sourceToolCallId`、`cardType`、`decisionSource`、`title`、`layout`、`defaultView`、`availableViews`、`summary`、`table`、`chart`。
- 枚举固定为：来源 `SYSTEM/LLM`；布局 `TABS`；视图 `TEXT/TABLE/BAR/LINE/PIE`；格式 `TEXT/DATE/DATE_TIME/STATUS/MEAL_TYPE/NUMBER/BOOLEAN`。
- 表格列最多 20；图表指标最多 4；标题 100 字符；标签 30 字符。
- `table.sections` 仅用于详情卡多个受控子表，每个 section 仍使用固定 `dataPath + columns`，不允许递归 section。
- 描述对象只保存路径和语义，不复制任何业务值。

### Step 2：建立卡片安全字段目录

- `PresentationFieldCatalog` 为 11 个卡片类型登记可用数据路径、字段名、字段类型、数组/对象形态和是否允许图表。
- 字段目录与 Phase 01 的工具输出契约对齐，显式排除 `customerId/orderId/dishId/packageId`、手机号、地址、金额、Token、权限、SQL 等字段。
- 同一卡片类型被两个工具使用时复用同一目录；`DISH_LIST` 同时覆盖公共排期菜单和菜品搜索的安全共同字段或按已知 dataPath 分支登记。

### Step 3：登记 11 种系统规则

- `CUSTOMER_PROFILE_LIST`：表格，客户编号第一列、姓名第二列。
- `SERVICE_CUSTOMER_LIST`：表格固定为客户编号、姓名、订单编号、下单时间、订单状态、套餐。
- `SERVICE_CUSTOMER_DETAIL`：profile 摘要，订单、排餐、核销、退餐使用受控 sections。
- `MEAL_PLAN_LIST`、`VERIFICATION_LIST`、`REFUND_LIST`、`DISH_LIST`、`DISH_CANDIDATE_LIST`：固定表格和格式。
- `PACKAGE_DETAIL`：父套餐摘要与子套餐规格表。
- `METRIC_RESULT`：单值摘要；有完整 `breakdown` 时增加表格，并按类别规则选择 BAR/PIE，不对空分组生成图表。
- `BUSINESS_RULE`：受控文本摘要，不解析 HTML/Markdown。

### Step 4：实现注册表启动校验

- 检查规则唯一、cardType 已在 `ToolRegistry` 出现、视图合法、默认视图存在、路径和字段在安全目录中。
- 重复规则、未知字段、非法 view、超限列数直接抛出启动异常。
- 缺少规则不阻止启动，但 `AgentHealthResponse` 输出规则覆盖数量和稳定告警码；不输出字段详情或业务值。
- 契约测试强制当前 11 种卡片全覆盖，因此正常发布不会依赖 LLM 兜底。

### Step 5：接入 Agent 响应组装

- `BusinessAgentRunner` 在安全卡片生成后调用 `PresentationService`，按 `sourceToolCallId` 生成一对一描述。
- 把卡片/事实/追踪组装提取为独立方法，避免错误响应为了读取 trace 而重复生成展示。
- 已知卡片只读取注册表，不访问 `AgentModelGateway`。
- `AgentChatResponse` 增加空列表默认值的 `presentations`。

### Step 6：更新 OpenAPI 和契约测试

- 在 OpenAPI 中为展示描述、摘要字段、表格列/section、图表字段建立 `additionalProperties: false` 的明确 Schema。
- `AgentChatResult.properties` 增加 `presentations`，Java 字段集合与 OpenAPI 继续一一对应。
- 增加序列化往返测试和服务客户列顺序断言。

## 验证方式

- `PresentationRegistryTest`：11 类规则全覆盖、列顺序和默认视图正确、已知规则不调用模型。
- `PresentationRuleValidatorTest`：重复、未知字段、非法视图、超限配置全部失败。
- `BusinessAgentRunnerPresentationTest`：成功工具卡片和展示描述按 callId 一对一；工具失败不产生卡片或描述。
- `AgentServiceContractTest`、`AgentChatDtoTest`、`AgentHealthControllerTest`：OpenAPI、序列化、健康摘要兼容。

## 完成标准

- 当前 11 种 cardType 全部由 `SYSTEM` 规则覆盖。
- 服务客户表格列顺序完全符合设计。
- `presentations` 不包含业务值、HTML、组件名或 ECharts option。
- 已知卡片路径不触发模型调用。
- 配置错误可在启动/测试阶段发现。

## 回滚

- `presentations` 为新增可选响应字段，可移除注册表接入而不影响 `cards`。
- 保留 Phase 01 事实字段，回滚本阶段不会改变业务查询结果。

## 状态

completed
