# 智能排查Agent服务二期 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在一期“排餐未生成诊断”能力上，增强多轮追问、工具调用稳定性、结构化输出质量、诊断建议模板和可观测性，让客服能够围绕一次诊断继续补充问题、查看证据链、复用上下文，并降低 AI 输出不稳定带来的人工核对成本。

**Architecture:** 继续采用“`eladmin-system` 作为业务数据真相源 + `agent-service` 负责编排、提示词、工具调用和 AI 输出校验 + `eladmin-web` 展示会话和结果”的结构。二期仍然只做只读诊断，不引入自动修复、自动生成排餐、自动新增客户等写操作。

**Scope:** 二期只增强“排餐未生成诊断”场景，不扩展订单异常、核销异常、客服知识问答和 AI 新增客户。这些能力放入三期或后续专题。

**Tech Stack:** `eladmin-system`（Spring Boot 2.7.18、JDK 17、MyBatis-Plus）、`agent-service`（Spring Boot 3.5.x、Spring AI 1.1.6、JDK 17）、`eladmin-web`（Vue 2.7、element-ui、Jest）。

---

## 1. 二期边界

### 1.1 本期做

- 多轮补充字段和追问：用户可以先说“看下张三明天午餐”，后续补“换成晚餐”“这个客户是 C10001”“为什么候选菜为空”等。
- 会话记忆增强：会话保存最近输入、已识别槽位、上次诊断结果、上次工具调用摘要和诊断阶段。
- 工具调用稳定性增强：AI 按需调用客户、订单、排餐、候选菜工具时，必须有调用顺序、调用预算、错误兜底和日志记录。
- 结构化输出增强：诊断结果必须稳定包含 `summary`、`reasons`、`ruleIds`、`evidence`、`confidence`、`nextActions`、`diagnosisTrace`。
- 诊断建议模板：按规则类型输出可复用建议，避免 AI 自由发挥导致客服动作不一致。
- 前端会话体验增强：展示槽位、追问、诊断结果、证据表格、建议动作、工具调用摘要和兜底原因。
- 可观测性增强：记录 requestId、sessionId、ruleVersionDigest、modelName、toolCalls、fallbackReason、validatorErrors、costMs。

### 1.2 本期不做

- 不自动修改订单、客户、排餐计划、候选菜、核销记录。
- 不直接新增客户，不生成客户草稿。
- 不新增订单异常、核销异常、退餐异常等独立诊断场景。
- 不把 Agent 接口开放给客户自助使用。
- 不让 AI 直接访问数据库，所有数据仍通过 `eladmin-system` 内部只读接口获取。

---

## 2. File Structure

### Create

- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/chat/DiagnosisConversationState.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/chat/DiagnosisConversationTurn.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/chat/MealPlanFollowUpService.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/chat/impl/MealPlanFollowUpServiceImpl.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/prompt/DiagnosisPromptPolicy.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/prompt/DiagnosisPromptPolicyLoader.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/summary/DiagnosisSuggestionTemplate.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/summary/DiagnosisSuggestionTemplateService.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/summary/FileSystemDiagnosisSuggestionTemplateService.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/observability/DiagnosisTraceCollector.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/observability/DiagnosisTraceEvent.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/validator/DiagnosisValidationError.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/rules/meal-plan/suggestion-template.yaml`
- `/Users/qqx/job/code/eladmin-mp/agent-service/rules/meal-plan/prompt-policy.yaml`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/chat/MealPlanFollowUpServiceImplTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/prompt/DiagnosisPromptPolicyLoaderTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/summary/DiagnosisSuggestionTemplateServiceTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/observability/DiagnosisTraceCollectorTest.java`

### Modify

- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisResponse.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisReasonDto.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/AgentChatResponse.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/chat/MealPlanChatSession.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/chat/InMemoryMealPlanChatSessionStore.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/chat/RuleBasedMealPlanChatExtractor.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/chat/MealPlanChatServiceImpl.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/prompt/DiagnosisPromptBuilder.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/client/SpringAiDiagnosisAiClient.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/tool/AgentToolRegistry.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/validator/DiagnosisResultValidator.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentDiagnosisResponse.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentDiagnosisReasonDto.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentChatResponse.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/client/HttpAgentServiceClient.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/views/agent/diagnosis/index.vue`
- `/Users/qqx/job/code/eladmin-mp/eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- `/Users/qqx/job/code/eladmin-mp/eladmin/doc/apidoc/智能排查助手接口文档.md`
- `/Users/qqx/job/code/eladmin-mp/eladmin/doc/business/智能排查助手业务说明.md`
- `/Users/qqx/job/code/eladmin-mp/eladmin/doc/智能排查Agent服务设计方案.md`

---

## 3. Data Contract

### 3.1 DiagnosisResponse 新增字段

```json
{
  "requestId": "string",
  "sessionId": "string",
  "customerId": 1001,
  "customerName": "张三",
  "recordDate": "2026-05-22",
  "mealType": "LUNCH",
  "summary": "客户命中停送日期，未参与本餐次排餐。",
  "fallback": false,
  "fallbackReason": null,
  "ruleVersionDigest": "string",
  "modelName": "string",
  "confidence": "HIGH",
  "reasons": [],
  "nextActions": [],
  "diagnosisTrace": [],
  "toolCallSummary": []
}
```

### 3.2 DiagnosisReasonDto 新增字段

```json
{
  "code": "CUSTOMER_EXCLUDE_DATE_HIT",
  "title": "命中客户停送日期",
  "level": "HIGH",
  "confidence": "HIGH",
  "ruleIds": ["CUSTOMER_EXCLUDE_DATE_HIT"],
  "description": "客户档案中配置了该日期午餐停送。",
  "suggestion": "请先核对客户停送登记，如需恢复配送，按客户管理流程调整后重新生成排餐。",
  "nextActions": ["核对客户档案停送配置", "确认是否需要恢复配送"],
  "evidence": []
}
```

### 3.3 AgentChatResponse 新增字段

```json
{
  "sessionId": "string",
  "requestId": "string",
  "status": "NEED_MORE_INFO",
  "assistantMessage": "请补充要排查的日期。",
  "slots": {},
  "slotConfidence": {},
  "missingSlots": ["RECORD_DATE"],
  "diagnosisResult": null,
  "quickReplies": [],
  "conversationStage": "COLLECTING_SLOTS"
}
```

兼容要求：

- 旧字段保持不删，前端和主系统代理可以逐步升级。
- 新字段允许为空，但服务端 DTO、接口文档和前端展示要同步。
- `fallback=true` 时必须有 `fallbackReason` 或兜底 reason。

---

## 4. Task 1: 增强会话状态和多轮追问

**目标：** 把一期的“规则提取槽位 + 简单会话缓存”升级为可描述会话阶段、槽位来源、上次诊断结果和追问意图的状态模型。

**Files:**

- Modify: `agent-service/src/main/java/me/zhengjie/agent/chat/MealPlanChatSession.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/chat/InMemoryMealPlanChatSessionStore.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/chat/MealPlanChatServiceImpl.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/chat/DiagnosisConversationState.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/chat/DiagnosisConversationTurn.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/chat/MealPlanChatServiceImplTest.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/chat/InMemoryMealPlanChatSessionStoreTest.java`

- [x] **Step 1: 先补会话状态测试**

覆盖场景：

- 新会话默认阶段为 `COLLECTING_SLOTS`
- 已收集客户、日期、餐次后进入 `READY_TO_DIAGNOSE`
- 完成诊断后进入 `DIAGNOSED`
- 用户说“换成晚餐”时只覆盖 `mealType`，保留客户和日期
- 用户说“重新排查”时清空上次诊断结果，但保留或重置槽位的策略要明确
- 会话最多保留最近 10 轮输入和最近 3 次诊断摘要

- [x] **Step 2: 定义会话阶段和轮次模型**

建议阶段：

- `COLLECTING_SLOTS`
- `READY_TO_DIAGNOSE`
- `DIAGNOSING`
- `DIAGNOSED`
- `FOLLOWING_UP`
- `RESET`
- `ERROR`

`DiagnosisConversationTurn` 至少包含：

- `role`
- `message`
- `slotsSnapshot`
- `intent`
- `createdAt`
- `diagnosisRequestId`

- [x] **Step 3: 改造 `MealPlanChatServiceImpl`**

实现要求：

- 统一由会话阶段决定是否触发诊断
- FOLLOW_UP 时不再只返回“请结合结果卡片”，而是根据问题类型返回上一轮证据摘要或触发重新诊断
- 支持“改日期”“改餐次”“换客户”这类局部覆盖
- 每次响应都返回 `missingSlots`、`conversationStage`、`quickReplies`

- [x] **Step 4: 运行会话相关单测**

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
mvn -q -Dtest=MealPlanChatServiceImplTest,InMemoryMealPlanChatSessionStoreTest test
```

Expected: PASS

---

## 5. Task 2: 增强槽位提取和追问策略

**目标：** 当前 `RuleBasedMealPlanChatExtractor` 偏简单，二期要支持相对日期、局部修改、中文餐次别名、客户编号歧义提示，并输出槽位置信度。

**Files:**

- Modify: `agent-service/src/main/java/me/zhengjie/agent/chat/RuleBasedMealPlanChatExtractor.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/domain/dto/ChatExtractionResult.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisSlots.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/chat/RuleBasedMealPlanChatExtractorTest.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/domain/dto/AgentChatDtoTest.java`

- [x] **Step 1: 扩展提取测试用例**

覆盖输入：

- `看下 C10001 明天午餐为什么没排出来`
- `换成后天晚餐`
- `不是午餐，是晚餐`
- `客户编号 C10001`
- `客户ID 123`
- `今天早饭`
- `下周一午餐`
- `重新排查`
- `清空会话`

- [x] **Step 2: 增加槽位来源和置信度**

建议字段：

- `slotConfidence.customer`
- `slotConfidence.recordDate`
- `slotConfidence.mealType`
- `slotSource.customer`
- `slotSource.recordDate`
- `slotSource.mealType`

实现原则：

- 规则明确命中时为 `HIGH`
- 根据上下文继承时为 `MEDIUM`
- 模糊表达或冲突时为 `LOW`，需要追问确认

- [x] **Step 3: 追问文案标准化**

追问顺序：

1. 客户
2. 日期
3. 餐次
4. 模糊槽位确认

输出要求：

- 问一个最关键问题，不一次性抛多个问题
- quick replies 只给当前缺失项相关选项
- 不出现“系统无法识别”这类泛化文案

- [x] **Step 4: 运行提取器测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
mvn -q -Dtest=RuleBasedMealPlanChatExtractorTest,AgentChatDtoTest test
```

Expected: PASS

---

## 6. Task 3: 诊断提示词策略化

**目标：** 把硬编码在 `DiagnosisPromptBuilder` 中的提示词约束拆成可测试、可版本化的 prompt policy，减少后续规则和提示词修改时的代码改动。

**Files:**

- Modify: `agent-service/src/main/java/me/zhengjie/agent/prompt/DiagnosisPromptBuilder.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/prompt/DiagnosisPromptPolicy.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/prompt/DiagnosisPromptPolicyLoader.java`
- Create: `agent-service/rules/meal-plan/prompt-policy.yaml`
- Test: `agent-service/src/test/java/me/zhengjie/agent/prompt/DiagnosisPromptBuilderTest.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/prompt/DiagnosisPromptPolicyLoaderTest.java`

- [x] **Step 1: 定义 prompt policy YAML**

建议字段：

```yaml
scene: MEAL_PLAN_NOT_GENERATED
version: 1
role: 排餐未生成原因诊断助手
outputContract:
  requiredFields:
    - summary
    - reasons
    - evidence
    - nextActions
forbiddenClaims:
  - 已修复
  - 已修改数据库
  - 已创建客户
toolPolicy:
  maxToolCalls: 8
  requiredBeforeConclusion:
    - getCustomerProfile
    - listCustomerOrders
    - getMealPlan
evidencePolicy:
  minEvidencePerReason: 1
  requireRuleIds: true
  requireFieldReference: true
```

- [x] **Step 2: Loader 测试**

必须覆盖：

- 能加载 YAML
- `scene` 必须为 `MEAL_PLAN_NOT_GENERATED`
- `version` 大于 0
- `forbiddenClaims` 非空
- `outputContract.requiredFields` 包含 `summary` 和 `reasons`
- `toolPolicy.maxToolCalls` 在 1 到 20 之间

- [x] **Step 3: PromptBuilder 接入 policy**

要求：

- prompt 包含 policy version
- prompt 包含 tool 调用预算
- prompt 明确要求先查证据再下结论
- prompt 明确输出 `nextActions` 和 `confidence`
- prompt 明确 `fallback=true` 的输出条件

- [x] **Step 4: 运行提示词测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
mvn -q -Dtest=DiagnosisPromptBuilderTest,DiagnosisPromptPolicyLoaderTest test
```

Expected: PASS

---

## 7. Task 4: 工具调用稳定性和调用预算

**目标：** 防止模型无限调用工具、漏调用关键工具或工具失败后输出猜测结论。

**Files:**

- Modify: `agent-service/src/main/java/me/zhengjie/agent/tool/AgentToolRegistry.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/client/SpringAiDiagnosisAiClient.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/observability/DiagnosisToolCallLoggingAdvisor.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/observability/DiagnosisTraceCollector.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/observability/DiagnosisTraceEvent.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/tool/AgentToolRegistryTest.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/observability/DiagnosisTraceCollectorTest.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/client/SpringAiDiagnosisAiClientTest.java`

- [x] **Step 1: 增加工具调用摘要模型**

每次工具调用记录：

- `toolName`
- `inputDigest`
- `success`
- `resultCount`
- `costMs`
- `errorType`
- `errorMessage`

注意：日志和返回摘要都不应输出手机号等敏感字段原文。

- [x] **Step 2: 增加调用预算**

策略：

- 单次诊断默认最多 8 次工具调用
- 同一工具相同入参重复调用直接复用上次结果摘要
- 超预算时停止继续调用并要求模型返回 `fallback=true`

- [x] **Step 3: 关键工具调用顺序约束**

建议顺序：

1. `getCustomerProfile`
2. `listCustomerOrders`
3. `getMealPlan`
4. `getCandidateDishStats`

允许模型按问题跳过非必要工具，但如果最终原因涉及客户、订单、排餐或候选菜，必须有对应工具证据。

- [x] **Step 4: 工具失败兜底**

要求：

- 单个工具失败时记录 trace
- 关键工具失败时诊断结果降级为 `fallback=true`
- assistantMessage 提示“诊断数据不完整，需人工核对”

- [x] **Step 5: 运行工具相关测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
mvn -q -Dtest=AgentToolRegistryTest,DiagnosisTraceCollectorTest,SpringAiDiagnosisAiClientTest test
```

Expected: PASS

---

## 8. Task 5: 结构化输出校验增强

**目标：** 扩展 `DiagnosisResultValidator`，把一期“字段存在即可展示”升级为“证据、规则、建议和置信度都可控”。

**Files:**

- Modify: `agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisResponse.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisReasonDto.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/validator/DiagnosisResultValidator.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/validator/DiagnosisValidationError.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/validator/DiagnosisResultValidatorTest.java`

- [x] **Step 1: DTO 增加结构化字段**

`DiagnosisResponse` 新增：

- `sessionId`
- `confidence`
- `fallbackReason`
- `nextActions`
- `diagnosisTrace`
- `toolCallSummary`

`DiagnosisReasonDto` 新增：

- `ruleIds`
- `confidence`
- `nextActions`

- [x] **Step 2: Validator 增强**

校验规则：

- `summary` 非空且不包含禁用写操作表述
- `confidence` 只能是 `HIGH`、`MEDIUM`、`LOW`
- `reason.level` 只能是 `HIGH`、`MEDIUM`、`LOW`
- 每个 reason 至少一个 `ruleId`
- 每个 reason 至少一条 evidence
- 每个 reason 至少一个 nextAction
- `fallback=false` 时不能只有兜底 reason
- `fallback=true` 时必须有 `fallbackReason`

- [x] **Step 3: 校验错误可观测**

`DiagnosisValidationError` 至少包含：

- `field`
- `code`
- `message`
- `rawValueDigest`

校验失败时日志记录错误列表，但不输出完整原始模型响应。

- [x] **Step 4: 运行校验器测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
mvn -q -Dtest=DiagnosisResultValidatorTest test
```

Expected: PASS

---

## 9. Task 6: 诊断建议模板

**目标：** 把常见原因的客服处理建议固化为模板，AI 只负责选择和补充证据，减少建议文案不稳定。

**Files:**

- Create: `agent-service/rules/meal-plan/suggestion-template.yaml`
- Create: `agent-service/src/main/java/me/zhengjie/agent/summary/DiagnosisSuggestionTemplate.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/summary/DiagnosisSuggestionTemplateService.java`
- Create: `agent-service/src/main/java/me/zhengjie/agent/summary/FileSystemDiagnosisSuggestionTemplateService.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/client/SpringAiDiagnosisAiClient.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/validator/DiagnosisResultValidator.java`
- Test: `agent-service/src/test/java/me/zhengjie/agent/summary/DiagnosisSuggestionTemplateServiceTest.java`

- [x] **Step 1: 定义建议模板 YAML**

建议覆盖：

- `CUSTOMER_NOT_FOUND`
- `ORDER_MISSING`
- `ORDER_NOT_EFFECTIVE`
- `ORDER_REMAINING_COUNT_EMPTY`
- `SCHEDULE_MODE_NOT_MATCH`
- `CUSTOMER_EXCLUDE_DATE_HIT`
- `CANDIDATE_DISH_EMPTY`
- `DISH_FILTERED_EMPTY`
- `MEAL_PLAN_GENERATED_FAILED`
- `AI_RESULT_INVALID`

模板字段：

- `code`
- `title`
- `defaultSuggestion`
- `nextActions`
- `customerVisible`
- `requiresManualConfirm`

- [x] **Step 2: 模板加载测试**

必须覆盖：

- code 唯一
- defaultSuggestion 非空
- nextActions 非空
- 所有首批 ruleId 都有模板或显式标记无需模板

- [x] **Step 3: 接入 AI 输出后处理**

处理顺序：

1. AI 返回 reason code 和 evidence
2. 系统按 reason code 查模板
3. 如果模板存在，用模板补齐 `suggestion` 和 `nextActions`
4. 如果模板不存在，保留 AI suggestion，但 validator 降低 confidence 或记录 warning

- [x] **Step 4: 运行模板测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
mvn -q -Dtest=DiagnosisSuggestionTemplateServiceTest,DiagnosisResultValidatorTest test
```

Expected: PASS

---

## 10. Task 7: 主系统代理 DTO 和接口文档同步

**目标：** `eladmin-system` 继续只做代理、权限和审计，但需要透传二期新增字段，并保持旧前端兼容。

**Files:**

- Modify: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentDiagnosisResponse.java`
- Modify: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentDiagnosisReasonDto.java`
- Modify: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentChatResponse.java`
- Modify: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/client/HttpAgentServiceClient.java`
- Test: `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/client/HttpAgentServiceClientTest.java`
- Test: `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/rest/AgentDiagnosisControllerTest.java`

- [x] **Step 1: 主系统 DTO 增加二期字段**

字段与 `agent-service` 对齐：

- `confidence`
- `fallbackReason`
- `nextActions`
- `diagnosisTrace`
- `toolCallSummary`
- `missingSlots`
- `conversationStage`
- `slotConfidence`

- [x] **Step 2: 代理透传测试**

测试重点：

- agent-service 返回新字段后主系统不丢字段
- agent-service 不返回新字段时主系统仍正常返回旧结构
- 超时 fallback 也填充 `fallback=true` 和 `fallbackReason`

- [x] **Step 3: 文档同步**

更新：

- `eladmin/doc/apidoc/智能排查助手接口文档.md`
- `eladmin/doc/business/智能排查助手业务说明.md`

必须写清楚：

- 二期新增字段含义
- `confidence` 不是业务结论，只是模型建议可信度
- `diagnosisTrace` 面向客服和技术排查，不代表数据库操作

- [x] **Step 4: 运行主系统测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin
mvn -q -Dtest=HttpAgentServiceClientTest,AgentDiagnosisControllerTest,AgentDiagnosisFacadeServiceImplTest test
```

Expected: PASS

---

## 11. Task 8: 前端会话和结果展示增强

**目标：** 页面从“普通聊天 + 结果卡片”升级为客服可操作的诊断工作台，但保持当前后台管理系统的朴素风格。

**Files:**

- Modify: `eladmin-web/src/views/agent/diagnosis/index.vue`
- Modify: `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- Modify: `eladmin-web/tests/unit/api/agentDiagnosis.spec.js`

- [x] **Step 1: 页面结构调整**

建议区域：

- 左侧：会话消息和输入框
- 右侧：当前诊断槽位、诊断状态、最近一次结果摘要
- 结果区：原因列表、证据表格、建议动作、工具调用摘要、兜底提示

保持要求：

- 不做营销式页面
- 不新增写操作按钮
- 所有“建议动作”只展示，不直接执行业务修改

- [x] **Step 2: 展示二期字段**

新增展示：

- `conversationStage`
- `missingSlots`
- `slotConfidence`
- `confidence`
- `fallbackReason`
- `nextActions`
- `toolCallSummary`
- `diagnosisTrace`

- [x] **Step 3: 交互增强**

支持：

- 快捷补充客户、日期、餐次
- 点击“换成午餐/晚餐”发起局部覆盖
- 点击“重新排查”重跑当前槽位
- 点击“清空会话”重置
- 展开/收起工具调用摘要

- [x] **Step 4: 前端单测**

覆盖：

- NEED_MORE_INFO 显示缺失字段
- ANSWERED 显示诊断结果
- fallback 显示兜底原因
- nextActions 正常渲染
- toolCallSummary 可展开
- 清空会话重置状态

- [x] **Step 5: 运行前端测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin-web
npm run test:unit -- tests/unit/views/agent/diagnosis/index.spec.js tests/unit/api/agentDiagnosis.spec.js
```

Expected: PASS

---

## 12. Task 9: 可观测性和联调验收

**目标：** 二期上线前能定位一次诊断从前端到主系统、agent-service、工具调用、模型输出、校验兜底的完整链路。

**Files:**

- Modify: `agent-service/src/main/resources/logback-spring.xml`
- Modify: `eladmin/eladmin-system/src/main/resources/logback-spring.xml`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/controller/MealPlanDiagnosisController.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/chat/MealPlanChatServiceImpl.java`
- Modify: `agent-service/src/main/java/me/zhengjie/agent/client/SpringAiDiagnosisAiClient.java`
- Modify: `eladmin/doc/智能排查Agent服务二期实施计划.md`

- [x] **Step 1: 统一日志字段**

所有关键日志包含：

- `requestId`
- `sessionId`
- `customerId`
- `customerCode`
- `recordDate`
- `mealType`
- `stage`
- `costMs`
- `fallback`
- `fallbackReason`

- [x] **Step 2: 联调检查清单**

必须验证：

- [x] 首次输入缺客户时能追问客户
- [x] 首次输入完整客户、日期、餐次时能直接诊断
- [x] 诊断后输入“换成晚餐”能复用客户和日期重新诊断
- [x] 工具失败时返回兜底，不影响主系统
- [x] AI 输出非法 JSON 时返回兜底
- [x] 前端能展示证据、建议动作和工具调用摘要

- [x] **Step 3: 测试命令**

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
source ~/.zshrc && jenv shell 17 && mvn399 && mvn -q -Dmaven.repo.local=/private/tmp/agent-service-m2 test
```

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin
source ~/.zshrc && jenv shell 17 && mvn399 && mvn -q -Dmaven.repo.local=/private/tmp/eladmin-m2 -DskipTests=false -DfailIfNoTests=false -pl eladmin-system -am -Dtest=AgentDiagnosisContextServiceImplTest,AgentDiagnosisFacadeServiceImplTest,HttpAgentServiceClientTest,AgentDiagnosisControllerTest test
```

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin-web
npm run test:unit -- tests/unit/views/agent/diagnosis/index.spec.js tests/unit/api/agentDiagnosis.spec.js
```

- [x] **Step 4: 手工联调**

启动顺序：

1. 启动 `eladmin-system`
2. 启动 `agent-service`
3. 启动 `eladmin-web`
4. 登录后台进入“智能排查助手”

手工用例：

- [x] `帮我看下客户 C10001 明天午餐为什么没排出来`：agent-service 本地接口可返回 ANSWERED；使用远端 MySQL/Redis 时可通过 `eladmin-system` 拉取业务上下文；本地未配置真实模型时稳定 fallback，包含 `confidence`、`fallbackReason`、`nextActions`。
- [x] `换成晚餐`：同一 session 下保留客户和日期，槽位与诊断结果 `mealType` 均更新为 `DINNER`。
- [x] `这个客户今天早餐呢`：agent-service 会话级验证通过，继承上一轮 `C10001`，日期切到 `2026-07-07`，餐次切到 `BREAKFAST`。
- [x] `重新排查`：同一 session 下保留当前槽位并重新生成 requestId。
- [x] `清空会话`：返回 RESET，清空客户、日期、餐次并返回全部缺失槽位。

本地启动记录：

- `eladmin-system` 已用远端 MySQL/Redis 环境变量启动成功：MySQL、Redis 主机均为 `122.152.237.213`，端口分别为 `13306`、`16379`，密码不写入文档。
- `agent-service` 已用 `AGENT_INTERNAL_TOKEN=local-test-token`、`AGENT_AI_ENABLED=false`、`AGENT_DIAGNOSIS_TOOL_MODE_ENABLED=false`、`AGENT_CONTEXT_BASE_URL=http://localhost:8000` 启动并完成接口级手工验证。
- `eladmin-web` 已在 `http://localhost:8013` 启动，首页资源返回 `200`。
- `agent-service` 日志已验证远程上下文请求完成且 `bodyPresent=true`，远程上下文编排包含 `customerCode=C10001`、`candidateDishStats=1`，说明诊断链路已从 `agent-service` 调到 `eladmin-system` 并读取业务上下文。
- 后台页面登录级验证依赖可用后台账号和有效 RSA 登录私钥配置；本轮未在文档中固化敏感密钥，只完成服务启动、前端资源可访问和 agent-service 到 eladmin-system 的真实上下文联调。

- [x] **Step 5: 验收记录**

在本计划末尾补充：

- 测试执行日期
- 代码分支和 commit
- 三端测试命令结果
- 手工用例结果
- 已知限制

---

## 13. Rollout Plan

### 13.1 配置开关

建议增加配置：

```yaml
agent:
  diagnosis:
    phase2-enabled: true
    max-tool-calls: 8
    trace-enabled: true
    suggestion-template-enabled: true
  chat:
    max-turns: 10
    max-diagnosis-history: 3
```

要求：

- `phase2-enabled=false` 时保留一期行为
- 新字段可以返回空值，但不能导致前端报错
- 工具调用预算和 trace 开关可配置

### 13.2 灰度方式

- 先在测试环境开启二期能力
- 只给管理员或客服主管角色开放菜单权限
- 观察 fallback 比例、平均耗时、工具失败率
- 稳定后放开给普通客服角色

### 13.3 回滚方式

- 关闭 `agent.diagnosis.phase2-enabled`
- 前端仍展示一期字段
- 主系统代理 fallback 保持可用
- rule registry 不回滚也不能影响一期路径

---

## 14. Definition of Done

二期完成必须同时满足：

- 多轮会话支持槽位补充、局部覆盖、重新排查和清空会话
- 诊断结果新增 `confidence`、`nextActions`、`fallbackReason`、`toolCallSummary`、`diagnosisTrace`
- AI 输出非法、工具失败、超预算都能稳定兜底
- 常见 reason code 有建议模板
- 前端能展示缺失槽位、诊断结果、证据、建议动作、工具调用摘要
- 接口文档和业务说明已同步
- agent-service、eladmin-system、eladmin-web 相关测试通过
- 手工联调清单已记录结果

---

## 15. 后续三期候选

二期完成后再评估：

- 订单异常诊断
- 核销异常诊断
- 退餐异常诊断
- 客服知识问答
- AI 辅助新增客户草稿
- AI 辅助编辑客户草稿
- 异常客户批量扫描

其中涉及写操作的能力必须继续遵循：

1. AI 只生成草稿
2. 服务端校验草稿
3. 前端展示差异和风险
4. 人工确认
5. 主系统正式业务接口提交

AI 不允许直接写数据库。

---

## 16. Self-Review

- 范围检查：二期只增强排餐未生成诊断，不扩展新业务场景。
- 安全检查：所有能力保持只读，不新增 AI 写库入口。
- 兼容检查：新增字段必须兼容一期前端和主系统代理。
- 可测性检查：每个任务都有对应单测或联调检查。
- 文档检查：接口文档、业务说明和设计方案需要随二期实现同步更新。

---

## 17. 二期验收记录

- 测试执行日期：2026-07-07 22:22:04 CST
- 代码分支：`agent-dev`
- 当前基线 commit：`a300846e`
- agent-service 测试：`source ~/.zshrc && jenv shell 17 && mvn399 && mvn -q -Dmaven.repo.local=/private/tmp/agent-service-m2 test`，结果 PASS。
- eladmin-system 代理测试：`source ~/.zshrc && jenv shell 17 && mvn399 && mvn -q -Dmaven.repo.local=/private/tmp/eladmin-m2 -DskipTests=false -DfailIfNoTests=false -pl eladmin-system -am -Dtest=AgentDiagnosisContextServiceImplTest,AgentDiagnosisFacadeServiceImplTest,HttpAgentServiceClientTest,AgentDiagnosisControllerTest test`，结果 PASS。
- eladmin-web 测试：`npm run test:unit -- tests/unit/views/agent/diagnosis/index.spec.js tests/unit/api/agentDiagnosis.spec.js`，结果 PASS。
- agent-service 手工接口验证：缺客户追问、完整槽位诊断、换成晚餐、这个客户今天早餐、重新排查、清空会话均已通过；本地无真实模型 Key 时使用 fallback 结果，验证新增字段可返回并可观测。
- eladmin-system 启动验证：使用远端 MySQL/Redis 环境变量启动成功，MySQL、Redis 主机均为 `122.152.237.213`，端口分别为 `13306`、`16379`；`nc -vz 122.152.237.213 13306` 和 `nc -vz 122.152.237.213 16379` 均成功。启动时需提供有效 `JWT_BASE64_SECRET`，默认 `change-me` 不是合法 Base64。
- 三端联调验证：`eladmin-system` 运行在 `http://localhost:8000`，`agent-service` 运行在 `http://localhost:18081`，`eladmin-web` 运行在 `http://localhost:8013`；agent-service 请求 `帮我看下客户 C10001 明天午餐为什么没排出来` 返回 `ANSWERED`，日志显示远程上下文请求完成且 `bodyPresent=true`，上下文包含 `customerCode=C10001` 和候选菜统计。
- 本地依赖检查补充（2026-07-07 22:34:01 CST）：未设置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`REDIS_HOST`、`REDIS_PORT`、`AGENT_INTERNAL_TOKEN`、`AGENT_DEEPSEEK_API_KEY` 环境变量；`mysql` 和 `redis-server` 命令不存在；`brew services list` 仅显示 `cliproxyapi`，没有 MySQL/Redis 服务。
- 已知限制：本轮未配置真实模型 Key，AI 结果按预期进入 fallback；后台登录页面级验证还需要可用后台账号和有效 RSA 登录私钥配置。本次已完成 agent-service 接口级手工验证、主系统代理单测、前端单测、三端服务启动和 agent-service 到 eladmin-system 的真实上下文联调。
