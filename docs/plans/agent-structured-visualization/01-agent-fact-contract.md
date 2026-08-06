# Phase 01 内部 Agent 事实与身份契约

## 目标

先把展示所依赖的事实契约稳定下来：内部 Agent 客户结果使用 `customerCode + customerName`，服务客户行提供确定性的 `orderTime`，指标结果提供可直接引用的完整 `breakdown`。本阶段不生成 `presentations`，不改前端。

## 输入

- 已认证客服的主系统数据范围和工具权限。
- 现有统一查询 DTO、客户/订单查询服务和 Agent 工具输出 DTO。
- 既有口径：`orderTime = dealTime != null ? dealTime : createTime`。

## 输出

- 主系统统一内部接口不再声明 `maskedName`，改为 `customerName`。
- 客户编号始终与完整姓名同时返回；手机号仍为 `maskedPhone`。
- 服务客户行新增 `orderTime`，同时保留 `dealTime/createTime` 两个事实字段。
- 指标 `dimensions` 保持兼容，并新增确定性 `breakdown[{label,value}]`。
- 主系统与 Agent 工具 DTO 的敏感字段契约测试通过。

## 涉及文件

修改：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/query/domain/dto/AgentCustomerProfileDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/query/domain/unified/AgentUnifiedQueryDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/query/service/impl/AgentCustomerQueryServiceImpl.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/query/service/impl/AgentUnifiedQueryServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/query/AgentQueryDtoAmountIsolationTest.java`
- `agent-service/src/main/java/me/zhengjie/agent/tool/output/ToolOutputs.java`
- `agent-service/src/main/java/me/zhengjie/agent/guardrail/FinalAnswerGuardrail.java`
- `agent-service/src/main/java/me/zhengjie/agent/application/BusinessAgentRunner.java`
- `agent-service/src/test/java/me/zhengjie/agent/tool/UnifiedToolContractTest.java`

新增：

- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/query/service/impl/AgentUnifiedQueryIdentityContractTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/tool/ToolOutputIdentityContractTest.java`
- `agent-service/src/test/java/me/zhengjie/agent/guardrail/FinalAnswerCustomerIdentityTest.java`

## 实施步骤

### Step 1：调整客户档案内部 DTO

- 将 `AgentCustomerProfileDto.maskedName` 改为 `customerName`，同步字段注释和访问方法调用。
- `AgentCustomerQueryServiceImpl.profileSummary()` 在已完成权限和数据范围过滤后复制完整姓名。
- 保留 `maskedPhone`；普通客户管理 DTO、候选解析 DTO和非 Agent API 不做改动。
- 更新方法注释，明确完整姓名只允许离开主系统进入已授权的内部 Agent 链路。

### Step 2：调整统一查询客户契约

- 将 `AgentUnifiedQueryDto.ProfileItem` 和 `ServiceCustomerItem` 的 `maskedName` 改为 `customerName`。
- `AgentUnifiedQueryServiceImpl` 将 `maskedNames()` 改为批量加载授权客户完整姓名的 `customerNames()`，继续避免 N+1 查询。
- `profileItem(AgentCustomerOverviewDto)` 不再二次调用 `maskName()`；详情 profile 与订单子表使用同一姓名口径。
- 空姓名允许为空，但客户相关结果必须保留非空 `customerCode`；发现缺编号时增加测试失败而不是用姓名替代。

### Step 3：增加统一下单时间

- 在 `ServiceCustomerItem` 和 `ToolOutputs.ServiceCustomer` 增加 `orderTime`。
- 在 `serviceCustomerItem()` 中集中实现成交时间优先、创建时间回退；不得交给 LLM 选择。
- 保留原 `dealTime/createTime`，以免破坏现有事实查询和兼容客户端。
- 测试成交时间存在、成交时间为空、两者均为空三种情况。

### Step 4：增加指标展示分组

- 在 `MetricItem` 增加受控 `breakdown`，元素固定为 `label` 和 `value`；从现有有序 `dimensions` Map 确定性转换。
- `total`、`dimensions`、指标枚举、维度口径和完整性语义保持不变。
- `ToolOutputs.Metric` 同步声明 `breakdown`；不得新增动态表达式、金额或任意字段名。
- 单值指标的 `breakdown` 为空，后续展示层据此选择摘要而不是空图表。

### Step 5：加强安全与兼容测试

- 反射断言内部 Agent 客户 DTO 不再含 `maskedName`，且含 `customerCode/customerName`。
- 验证 `phone/address/amount/price/customerId/orderId` 的既有隔离策略未被放宽。
- 验证完整姓名可通过工具输出护栏，而 11 位手机号、完整地址、金额字段仍被拒绝。
- 验证客户档案和服务客户的完整姓名只在数据范围过滤后的结果中出现。

### Step 6：约束自然语言中的客户身份

- `BusinessAgentRunner` 把本轮成功工具事实中的 `customerCode/customerName` 配对传给 `FinalAnswerGuardrail`，不从回答文本反推客户身份。
- 回答若出现某个完整姓名，必须同时出现该事实对应的客户编号；只有编号、没有姓名的简短结论仍允许。
- 工具事实没有可验证配对时，不允许模型只凭历史姓名生成身份结论。
- 校验失败进入既有回答修复流程；日志只记录稳定错误码，不记录姓名或回答原文。

## 验证方式

- 主系统定向测试：
  - `AgentUnifiedQueryIdentityContractTest`
  - `AgentQueryDtoAmountIsolationTest`
- Agent 服务定向测试：
  - `UnifiedToolContractTest`
  - `ToolOutputIdentityContractTest`
  - `FinalAnswerCustomerIdentityTest`
- 序列化样例检查：客户档案、服务客户、详情 profile 和 metric 均符合新字段名。

## 完成标准

- 统一内部 Agent DTO 和工具 DTO 中不存在 `maskedName`。
- 服务客户每行包含 `customerCode`，可用时包含 `customerName`，且 `orderTime` 回退规则正确。
- `assistantMessage` 出现完整姓名时，同时出现对应客户编号。
- 指标 `breakdown` 与原 `dimensions` 数值逐项一致。
- 完整手机号、地址、金额和内部 ID 保护测试全部通过。
- 未修改普通客户管理 API、数据库或前端。

## 回滚

- 恢复 DTO 字段和转换方法即可；没有数据库变更。
- `orderTime/breakdown` 均为新增兼容字段，回滚后旧消费者仍可使用 `dealTime/createTime/dimensions`。

## 状态

completed
