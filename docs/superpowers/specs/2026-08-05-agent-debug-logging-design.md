# Agent 调试日志设计

## 1. 目标与范围

为当前 `agent-service` 增加可串联一次聊天请求的调试日志，定位 LLM 入参、LLM 返回、工具参数校验、工具执行、主系统内部查询和预算耗尽等问题。

本次只修改 `agent-service` 日志代码，不改变聊天协议、工具契约、查询结果和预算规则；不修改工作区已有的其他业务改动。

## 2. 日志边界

在三个边界输出日志：

1. `BusinessAgentRunner`：每个模型回合输出可见工具、输入/输出长度、状态和耗时。
2. `BusinessAgentTools.GuardedCallback`：每次工具调用输出工具名、输入/输出长度、结果数量、缓存命中、成功/失败、稳定错误码和耗时。
3. `HttpMainSystemQueryClient`：每次调用主系统统一查询接口输出路径、请求 DTO 类型、成功/失败、结果数量、截断状态、HTTP 状态/稳定错误码和耗时。

使用固定标识便于检索：

- `AGENT_DEBUG_LLM_REQUEST`
- `AGENT_DEBUG_LLM_RESPONSE`
- `AGENT_DEBUG_ANSWER_VALIDATION`
- `AGENT_DEBUG_TOOL_REQUEST`
- `AGENT_DEBUG_TOOL_RESPONSE`
- `AGENT_DEBUG_QUERY_REQUEST`
- `AGENT_DEBUG_QUERY_RESPONSE`

日志字段至少包含 `requestId`、回合/调用序号、工具名、耗时和结果状态。LLM 提示词和回答正文、工具 JSON 入参/返回以及主系统查询业务正文均不完整打印，只保留排障所需摘要。

## 3. 安全边界

即使当前允许调试明文，以下内容仍不写入日志：

- `X-Agent-Internal-Token`；
- `X-Agent-Access-Context`；
- 完整手机号、地址及其他不属于 Agent 输出契约的敏感字段；
- 异常堆栈中可能包含的 Token、URL 查询参数或数据库细节。

主系统返回的错误响应只记录 HTTP 状态、稳定错误码和受控错误摘要，不改变对模型返回的安全错误信封。

## 4. 数据流与错误处理

模型回合开始时记录 LLM 请求摘要；模型返回或抛出异常时记录对应响应摘要/错误并带耗时；最终回答校验只记录尝试次数、状态和稳定错误码。工具回调在输入护栏之前记录请求长度，在缓存命中、执行成功、护栏失败、下游异常和预算异常等路径统一记录结果摘要。主系统查询客户端在 HTTP 成功和各类异常分支分别记录不含业务正文的结果摘要。

日志失败不能影响业务响应；记录日志本身的异常必须被隔离。原有 `ToolGuardrailException`、`MainSystemQueryException` 和最终回答流程保持不变。

## 5. 验证

- 不新增专门的日志断言测试，不改变现有工具契约和业务测试；
- 执行 `agent-service` 编译检查和 `git diff --check`；
- 使用同一 `requestId` 检查 LLM、工具和下游查询日志可以按时间串联。
