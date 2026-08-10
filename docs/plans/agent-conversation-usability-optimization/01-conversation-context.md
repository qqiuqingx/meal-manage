# Phase 01 多轮业务焦点闭环

## 目标

修正 LLM message 角色组装，并从本轮成功工具调用的强类型输入中确定性生成会话 Patch，使普通查询可以稳定继承客户、订单、日期、日期范围和餐次。

## 依赖

- 无前置 Phase。
- 实施前读取 `eladmin/doc/business/智能排查助手业务说明.md` 的会话、权限和工具章节。

## 输入

- 主系统下发的 `contextSnapshot`、`lastBusinessQueryContext` 和 `sessionVersion`。
- `ToolExecutionContext` 中按调用顺序保存的成功工具事实及 `inputJson`。
- 当前用户消息，仅用于 LLM 理解，不作为 Java 槽位猜测来源。

## 输出

- system prompt 与 user message 分离的模型调用。
- 一个确定性的会话上下文合并器。
- 包含最新业务焦点的 `slots` 和 `lastBusinessQueryContext`。
- 主系统按现有会话版本规则持久化 Patch。
- 跨轮焦点、焦点切换和旧响应兼容测试。

## 涉及文件

新增建议：

- `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ConversationContextUpdater.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/conversation/ConversationContextUpdaterTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/BusinessAgentRunnerConversationTest.java`

修改：

- `agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java`
- `agent-service/src/main/java/me/zhengjie/agent/guardrail/ToolExecutionContext.java`
- `agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisSlots.java`
- `agent-service/src/main/java/me/zhengjie/agent/application/conversation/ConversationPatch.java`
- `agent-service/src/main/java/me/zhengjie/agent/api/controller/AgentV2ChatController.java`
- `agent-service/src/test/java/me/zhengjie/agent/api/controller/AgentV2ChatControllerTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/application/BusinessAgentRunnerRecoveryTest.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionConcurrencyIntegrationTest.java`

如果现有 DTO 已覆盖所需字段，不新建平行 DTO；仅补方法注释和必要的复制方法。

## 上下文更新规则

只处理本轮 `success=true` 的工具事实，并按实际调用顺序合并。失败、缓存失败、模型文本和工具输出中的自由文本都不能更新焦点。

### 可持久化字段

| 槽位 | 允许来源 | 合并规则 |
| --- | --- | --- |
| customerId | 成功工具 input 的正整数 `customerId` | 明确出现时覆盖 |
| customerCode | 成功工具 input 的非空 `customerCode` | 明确出现时覆盖，作为前端主标识 |
| orderId | 成功工具 input 的正整数 `orderId` | 明确出现时覆盖 |
| orderCode | 成功工具 input 的非空 `orderCode` | 明确出现时覆盖 |
| mealPlanRecordId | 已登记且成功工具 input 的正整数记录 ID | 仅明确出现时覆盖 |
| recordDate | 成功工具 input 的 `yyyy-MM-dd` | 设置后清空 startDate/endDate |
| startDate/endDate | 成功工具 input 的完整日期范围 | 两者同时合法时设置，并清空 recordDate |
| mealType | 成功工具 input 的单个餐次枚举 | 单值时覆盖；全部餐次或多值不写入 |

以下内容不持久化：customerName、自由关键词、原始工具输出、列表行、手机号、地址、金额、权限、SQL、URL、Prompt 和模型回答。

### 焦点切换

- 新 `customerId/customerCode` 与旧客户明确不同时，清空旧 `orderId/orderCode/mealPlanRecordId`。
- 只有订单编号而没有客户编号时可以保存订单焦点；后续工具取得明确客户编号后再补充客户焦点。
- 名称搜索、模糊关键词和多候选结果不能直接设置客户焦点。
- 查询公共菜单或全局指标时不清空已有客户焦点，也不凭空创建客户焦点。
- 用户显式新建会话时由主系统清空全部焦点；普通问题切换主题不自动清空客户。

### 最近查询摘要

`lastBusinessQueryContext` 只保存：

- `lastToolName`：最后一个成功工具名。
- `successfulToolNames`：本轮去重工具名，限制为当前工具调用上限。
- `filters`：与 slots 相同的安全编号、日期和餐次字段。
- `queriedAt`：本轮查询时间。
- `partial`：是否存在业务失败或截断。

不保存工具结果数量以外的业务值；不保存完整消息历史。本字段用于指代理解和调试，不作为实时业务事实。

## 实施步骤

### Step 1：分离模型角色

- `systemPrompt()` 只生成系统规则、工具说明、受控会话摘要和诊断规则摘要。
- `invokeModel()` 分别接收 `systemPrompt` 与当前 `userMessage`，不再从拼接字符串中用 `substring` 提取用户问题。
- 回答修复要求作为明确的附加系统约束或当前回合修复指令传入，不把原问题再次拼进 system 内容。
- 调试日志分别记录 system/user 长度；内容日志继续服从现有开关和脱敏规则。

### Step 2：暴露成功工具输入快照

- 复用 `ToolExecutionContext.ToolFact.inputJson`，不增加第二份调用记录。
- 提供只读的成功事实视图或由更新器直接读取 `facts()`。
- 缓存命中的成功事实允许参与上下文更新，因为输入仍是本轮明确调用条件。
- 输入 JSON 解析失败时跳过该条上下文更新，但不影响业务结果；记录稳定日志码即可，不新增多层 fallback。

### Step 3：实现确定性合并器

- 从 `request.contextSlots` 创建副本，禁止直接修改请求对象。
- 按工具登记的输入 Schema 读取公共字段，不使用反射扫描任意字段；可用一个固定字段白名单读取 JSON。
- 执行焦点切换、日期互斥和单餐次规则。
- 返回更新后的 `DiagnosisSlots` 和受控摘要。
- 为新增/修改方法补充用途、参数和返回值注释。

### Step 4：接入 Runner 响应

- `assemble()` 使用合并器结果设置 `response.slots` 和 `response.lastBusinessQueryContext`。
- `fallback()` 有成功工具事实时也生成 Patch，使部分成功结果可延续；完全无成功事实时保留请求原上下文。
- 不改变 cards、presentations、toolFacts 和 toolTraceSummary 组装逻辑。

### Step 5：主系统提交 Patch

- 主系统优先读取 `conversationPatch.slots` 和 `conversationPatch.lastBusinessQueryContext`；旧响应缺 Patch 时继续读取顶层兼容字段。
- 继续使用现有事务、行锁和 `@Version` 更新，不引入新的分布式锁。
- 保留客户切换后清空订单焦点的主系统最终校验，避免 Agent 与主系统规则漂移导致串客户。
- 不新增表字段或历史回填任务。

### Step 6：覆盖核心跨轮测试

- 首轮成功 `listMealPlans(customerCode=B3303, recordDate=2026-08-09, mealType=LUNCH)` 后 Patch 保存三项焦点。
- 第二轮工具仅提交 `mealType=DINNER` 时，合并结果保留客户和日期并替换餐次。
- 新客户编号出现时清空旧订单和排餐记录焦点。
- 单日切换日期范围、日期范围切换单日时互相清空。
- 模糊姓名、多候选、失败工具和公共菜单查询不错误覆盖客户。
- Agent 返回旧式无 Patch 响应时主系统会话恢复行为不变。

## 验证方式

- 运行 `ConversationContextUpdaterTest` 和 `BusinessAgentRunnerConversationTest`。
- 运行 `AgentV2ChatControllerTest`，验证返回的 `conversationPatch` 与顶层兼容字段一致。
- 运行主系统 `AgentChatSessionServiceImplTest` 和并发集成测试。
- 检查日志中不出现工具原始 input/output、客户姓名、手机号、地址和金额。

## 完成标准

- system message 不再包含当前用户问题正文。
- 成功工具明确使用的客户、订单、日期和餐次可以跨轮恢复。
- 客户切换不会沿用旧订单或排餐记录。
- 失败工具、模糊姓名和公共查询不会污染已有业务焦点。
- 现有会话版本、消息幂等、工具卡片和安全测试继续通过。
- 没有新增数据库结构、额外模型调用或自由文本槽位解析。

## 回滚

- 回滚 Runner 的上下文合并接线后，主系统继续使用旧顶层 slots 和摘要。
- 新 Patch 只包含现有字段，旧代码可忽略；无需清理数据库内容。
- Prompt 角色分离可独立回滚，但应保留对应测试以定位 Provider 兼容问题。

## 状态

pending
