# 智能客服 Agent 通用业务分析实施计划

## 1. 背景与问题

当前 Agent 已具备客户概览、订单、排餐、菜品、核销、退餐和业务规则等只读查询能力，但整体编排仍以“单领域 + 单工具 + 固定模板”为主。当用户提出跨领域问题，例如：

> 今天午餐排餐的客户对哪些菜过敏？

该问题需要关联“排餐记录 → 客户 → 排餐菜品 → 过敏过滤原因”，现有 QueryPlan 无法完整表达，容易落入单客户排餐诊断或固定兜底。

本计划不为每个新问题新增专用聚合工具，而是建设“基础数据工具 + 受控多领域 QueryPlan + LLM 事实分析”能力。

## 2. 建设目标

1. LLM 负责理解用户问题、选择业务对象和关系、生成受控语义计划，不直接生成 SQL、URL 或任意工具名。
2. 服务端负责将语义计划编译为白名单工具调用图，并校验权限、数据范围、分页、字段和调用预算。
3. LLM 只能基于已脱敏的结构化事实和版本化业务规则生成回答。
4. 所有客户相关证据必须携带客户编号，前端不使用内部客户 ID 作为客户识别信息。
5. 客户手机号和地址联系电话在离开 `eladmin-system` 前完成脱敏，原始手机号不得进入 Agent、LLM 上下文、证据、日志或前端。
6. 查询不完整、工具失败或结果被截断时，系统必须明确标识部分结果，不得生成“全部”“所有”等全量结论。

## 3. 总体架构

```text
用户自然语言
  → LLM 业务问题分析
  → 受控 BusinessQuestionAnalysis
  → 服务端生成 QueryPlan V3
  → 权限/数据范围/字段/预算校验
  → 基础数据工具执行图
  → 脱敏结构化事实 + 业务规则
  → LLM 归纳回答
  → 事实引用/客户编号/敏感数据/完整性校验
  → 前端展示
```

## 4. 阶段一：隐私与证据硬约束

### 4.1 手机号脱敏

统一采用“前 3 位 + 四个星号 + 后 4 位”规则：

```text
13812345678 → 138****5678
```

实施内容：

1. 保留 `maskedPhone`，Agent 专用 DTO 中禁止出现 `phone`、`mobile`、`contactPhone` 等原始字段。
2. 客户手机号、默认地址联系电话、工作日地址联系电话、周末地址联系电话都必须在主系统响应组装时脱敏。
3. 在 Agent HTTP 客户端或传输 DTO 转换层增加递归敏感字段校验：
   - 命中原始手机号字段名时拒绝结果。
   - 命中连续 11 位大陆手机号时拒绝结果。
   - 只允许 `maskedPhone` 形式进入 Agent 上下文。
4. 工具调用日志、审计记录和异常日志不记录完整响应正文。
5. 即使用户明确询问手机号，回答也只能使用脱敏手机号。

主要修改位置：

- `eladmin-system/.../AgentCustomerOverviewDto.java`
- `eladmin-system/.../AgentCustomerAddressDto.java`
- `eladmin-system/.../AgentCustomerQueryServiceImpl.java`
- `agent-service/.../CustomerOverviewResponse.java`
- `agent-service/.../HttpBusinessQueryDataClient.java`
- `agent-service/.../BusinessAnswerValidator.java`

### 4.2 证据客户编号

扩展 `AgentQueryFact`：

```java
private String customerCode;
private String recordDate;
private String mealType;
private String sourceRecordId;
```

校验规则：

1. 客户、订单、排餐、核销、退餐、客户候选菜等客户相关事实，`customerCode` 必填。
2. `sourceRecordId` 用于记录订单 ID、排餐客户记录 ID、核销 ID 等内部溯源值，不替代客户编号。
3. 缺少客户编号的客户相关事实不允许展示，返回 `EVIDENCE_CUSTOMER_CODE_MISSING`。
4. 公共菜单、全局业务规则等确实不关联客户的事实标记为 `GLOBAL`，不伪造客户编号。
5. 跨客户回答按客户编号分组构建事实，每条客户结论至少引用一条同客户编号的事实。

证据示例：

```json
{
  "factId": "F1",
  "customerCode": "B3303",
  "label": "因过敏过滤菜品",
  "value": "香菇滑鸡",
  "sourceType": "MEAL_PLAN_DISH_ITEM",
  "sourceRecordId": "89231",
  "recordDate": "2026-07-13",
  "mealType": "LUNCH"
}
```

主要修改位置：

- `agent-service/.../AgentQueryFact.java`
- `agent-service/.../BusinessQueryResponseFactory.java`
- `agent-service/.../BusinessAnswerValidator.java`
- `eladmin-web/src/views/agent/diagnosis/`

## 5. 阶段二：通用基础数据能力

### 5.1 扩展排餐基础查询

现有 `listMealPlans` 扩展为支持两种查询范围：

```text
单客户：customerId/customerCode + recordDate/mealType
范围查询：recordDate + mealType + page/size
```

范围查询必须满足：

1. 强制指定日期，第一版只允许查询单日。
2. 强制指定餐次。
3. 最大每页 50 条。
4. SQL 查询必须应用当前客服的客户数据范围。
5. 每条排餐记录必须返回 `customerCode`。
6. 不返回原始手机号或完整地址。
7. 返回 `total`、`page`、`size`、`truncated` 和查询时间。

排餐菜品明细补充：

```text
customerCode
isAllergyFiltered
allergyReasons
replaceReason
originalDishId
originalDishName
```

业务口径必须区分：

- `replaceReason=ALLERGY`：本次排餐确实因过敏命中而过滤。
- 客户主动排除菜品：不得表述为“客户过敏”。
- 客户档案 `allergyTags`：表示客户配置的过敏标签。
- `isAllergyFiltered=true`：表示本次排餐实际命中过敏过滤。

### 5.2 批量基础查询

为避免 N+1 调用，对现有基础工具增加批量入参：

- 按 `customerIds` 批量查询客户受控摘要。
- 按 `orderIds` 批量查询订单摘要。
- 按 `dishIds` 批量查询菜品及配料。
- 按 `customerMealPlanIds` 批量查询排餐菜品明细。

每批最多 50 个 ID。所有客户相关结果必须携带稳定关联键和 `customerCode`。

### 5.3 权限与数据范围失败关闭

将客户数据范围从“`null` 表示全量”改为显式状态：

```text
UNBOUND      → 拒绝查询
RESTRICTED   → 仅允许指定客户集合
ALL_ALLOWED  → 仅管理员或明确授权角色
```

跨客户范围查询必须在 SQL 层应用数据范围，禁止先查全量再在 Agent 侧过滤。

## 6. 阶段三：QueryPlan V3

### 6.1 语义计划结构

将当前单领域 QueryPlan 升级为可表达多业务对象和对象关系的语义计划：

```json
{
  "version": "3.0",
  "intent": "ANALYZE",
  "subjects": ["MEAL_PLAN", "CUSTOMER", "DISH"],
  "filters": {
    "recordDate": "2026-07-13",
    "mealType": "LUNCH"
  },
  "relations": [
    "MEAL_PLAN_CUSTOMER",
    "MEAL_PLAN_DISH"
  ],
  "requestedFacts": [
    "CUSTOMER_CODE",
    "DISH_NAME",
    "ALLERGY_FILTERED",
    "ALLERGY_REASONS"
  ],
  "operation": "FILTER_AND_GROUP",
  "groupBy": ["CUSTOMER_CODE"]
}
```

LLM 只允许从服务端已定义的枚举中选择：

- `subjects`
- `relations`
- `requestedFacts`
- `operation`
- `groupBy`
- `filters`

LLM 禁止输出：

- 工具名
- SQL
- 表名
- Java 字段名
- URL
- 任意条件表达式

### 6.2 服务端计划编译

服务端维护业务对象关系图，将语义计划编译为白名单工具调用顺序。

例如：

```text
MEAL_PLAN + CUSTOMER + DISH
  → listMealPlans(recordDate, mealType, page, size)
  → 若菜品明细不足，批量调用 listDishes(dishIds)
  → 构建按 customerCode 分组的事实
```

主要修改位置：

- `agent-service/.../BusinessQuestionAnalysis.java`
- `agent-service/.../LlmBusinessQuestionAnalyzer.java`
- `agent-service/.../AgentQueryPlan.java`
- `agent-service/.../BusinessQueryPlanningService.java`
- `agent-service/.../AgentQueryPlanValidator.java`

## 7. 阶段四：受控执行图

在 `BusinessQueryOrchestrator` 中增加 QueryPlan V3 多步骤执行能力：

```java
ExecutionResult execute(AgentQueryPlanV3 plan);
```

执行器负责：

1. 由服务端决定工具调用顺序。
2. 自动将上一步返回的 ID 集合传入下一步。
3. 使用批量查询，禁止 LLM 逐客户自由循环调用。
4. 限制单轮工具调用数、客户数、订单数、菜品数和上下文总量。
5. 提供单轮结果缓存，同参数工具不重复调用。
6. 记录查询时间、调用链、截断信息和部分失败摘要。

第一版预算建议：

- 单轮最多 6 次内部工具调用。
- 最多处理 100 位客户。
- 最多返回 200 条排餐菜品事实。
- 单工具默认超时 3 秒。
- 查询超出范围时返回 `partial=true`、`scannedCount`、`totalCount`、`truncated=true`。

## 8. 阶段五：LLM 事实归纳

一次完整问答使用两阶段 LLM：

```text
第一次 LLM：理解问题，输出受控语义分析
服务端：编译并执行 QueryPlan
第二次 LLM：仅基于事实与规则归纳回答
```

第二次 LLM 只能获取：

- 已脱敏的结构化事实
- 客户编号
- 版本化业务规则摘要
- 查询完整性信息
- 事实编号

禁止将以下数据传入第二次 LLM：

- 原始手机号
- 完整地址
- SQL 或数据库表结构
- 内部鉴权信息
- 不在字段白名单中的业务文本

LLM 输出结构：

```json
{
  "answer": "今天午餐有以下客户的排餐菜品命中过敏过滤……",
  "factIds": ["F1", "F2"],
  "partial": false
}
```

回答校验规则：

1. 每个客户结论必须引用至少一条同客户编号的事实。
2. 回答中的菜名、过敏标签、数量和日期必须存在于事实集合。
3. 不允许引用不存在的事实编号。
4. 不允许将客户主动排除菜品表述为过敏菜品。
5. 不允许返回原始手机号。
6. `partial=true` 时禁止使用“全部”“所有”“仅有”等全量结论。
7. LLM 回答校验失败时，使用确定性事实模板展示，不进入“排餐未生成原因诊断”兜底。

## 9. 目标问题的预期执行

用户输入：

```text
今天午餐排餐的客户对哪些菜过敏？
```

预期语义：

```text
日期：当天
餐次：LUNCH
业务对象：排餐客户 + 排餐菜品
过滤条件：isAllergyFiltered=true 且 replaceReason=ALLERGY
分组条件：customerCode
```

预期回答：

```text
今天午餐排餐中有 2 位客户存在实际过敏过滤：

- B3303：香菇滑鸡，命中过敏标签“鸡肉”。[F1]
- A1208：牛奶南瓜汤，命中过敏标签“牛奶”。[F2]

本次查询覆盖 35 位已排餐客户，结果未截断。
```

预期证据：

```text
F1 客户编号 B3303 / 2026-07-13 / 午餐 /
   菜品 香菇滑鸡 / 过敏标签 鸡肉

F2 客户编号 A1208 / 2026-07-13 / 午餐 /
   菜品 牛奶南瓜汤 / 过敏标签 牛奶
```

## 10. 测试计划

### 10.1 主系统单元测试

1. 客户手机号返回 `138****5678`，不包含原始手机号。
2. 地址联系电话使用相同脱敏规则。
3. 按日期和餐次查询排餐时，只返回当前客服数据范围内的客户。
4. 未绑定数据范围时范围查询被拒绝。
5. 每条客户排餐记录都包含 `customerCode`。
6. 排餐菜品明细正确返回 `isAllergyFiltered`、`allergyReasons` 和 `replaceReason`。
7. `ALLERGY` 和客户主动排除菜品口径正确分离。

### 10.2 Agent 单元测试

1. 示例问题识别为业务分析，不进入排餐诊断。
2. LLM 输出 SQL、URL、工具名或未知枚举时被拒绝。
3. QueryPlan V3 正确编译为排餐和菜品基础查询。
4. 客户相关事实缺少 `customerCode` 时拒绝展示。
5. 事实中出现原始手机号时拒绝展示。
6. LLM 引用不存在的事实编号时回退到确定性模板。
7. 回答中的菜名、过敏标签和数量不在事实集中时校验失败。
8. 分页、数据预算或工具失败导致 `partial=true` 时，禁止生成全量结论。
9. 业务数据中的客户备注、菜名等文本不能改变 LLM 系统指令。

### 10.3 集成测试

1. 从聊天入口发送“今天午餐排餐的客户对哪些菜过敏”，返回客户编号分组的过敏菜品结果。
2. 权限受限用户不得看到权限外客户。
3. 返回正文、证据、卡片数据和应用日志中均不存在原始手机号。
4. 切换会话后不复用上一个会话的客户数据和事实。
5. 重复发送相同请求时，幂等与单轮缓存行为正确。

### 10.4 前端测试

1. 客户相关证据显示客户编号。
2. 前端不使用客户 ID 替代客户编号。
3. 手机号只能以脱敏形式显示。
4. `partial=true` 时显示“结果未完整”和已扫描/总记录数。
5. 工具部分失败时显示受控告警，不展示伪造的证据。

## 11. 验收标准

1. 客户手机号仅能以 `138****5678` 类似形式出现在 Agent 回答中。
2. Agent 传输 DTO、LLM 输入、证据和日志不存在原始手机号。
3. 每条客户相关证据都包含非空客户编号。
4. “客户过敏标签”“客户主动排除菜品”“本次排餐实际过敏过滤”三种口径能够正确区分。
5. 目标示例问题不再进入排餐诊断兜底。
6. 新的跨领域问题优先通过组合已登记基础工具解决，不按每个问题增加专用聚合接口。
7. 数据范围、分页或工具预算导致结果不完整时，前后端都有明确标识。
8. LLM 输出无法通过事实校验时，使用确定性模板回退，不伪造业务结论。

## 12. 实施顺序

1. 手机号脱敏与敏感字段失败关闭。
2. `AgentQueryFact` 客户编号与证据校验。
3. 扩展排餐范围查询与过敏字段传输。
4. 客户数据范围失败关闭改造。
5. 基础数据批量查询。
6. QueryPlan V3 语义协议与服务端编译。
7. 多步骤受控执行图。
8. LLM 事实归纳与回答校验。
9. 前端证据卡片与部分结果展示。
10. 全链路测试、评测用例、业务文档和 API 文档更新。

## 13. 文档同步要求

实施业务逻辑时需同步更新：

- `eladmin/doc/business/客户管理业务说明.md`
- `eladmin/doc/business/排餐管理业务说明.md`
- `eladmin/doc/apidoc/智能排查助手接口文档.md`

任何新增或修改的方法必须补充清晰的方法注释，说明用途、关键参数、权限范围和返回含义。
