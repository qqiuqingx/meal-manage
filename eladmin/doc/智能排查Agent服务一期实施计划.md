# 智能排查Agent服务一期 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个独立启动的 `agent-service`，为后台客服提供“某客户为什么没有生成排餐计划”的诊断能力，并在现有后台中完成入口与结果展示。

**Architecture:** 采用“主系统提供诊断上下文 + agent-service 加载 rule registry + agent-service 调用 AI 直接分析 + 后台页面展示结果”的三段式方案。第一期以 AI 直接分析为核心，规则只做真相源、输入约束和结果兜底。

**Tech Stack:** `eladmin-system`（JDK 17、Spring Boot 2.7.18、MyBatis-Plus）、`agent-service`（JDK 17、Spring Boot 3.5.x、Spring AI 1.1.6、JUnit 5）、`eladmin-web`（Vue 2.7、element-ui、Jest）

---

## File Structure

### Create

- `/Users/qqx/job/code/eladmin-mp/agent-service/pom.xml`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/AgentServiceApplication.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/controller/MealPlanDiagnosisController.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/service/MealPlanDiagnosisService.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/orchestrator/MealPlanDiagnosisOrchestrator.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/client/DiagnosisContextClient.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/client/DiagnosisAiClient.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/prompt/DiagnosisPromptBuilder.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/rule/RuleRegistryLoader.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/tool/*.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/advisor/*.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/memory/*.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/validator/DiagnosisResultValidator.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisRequest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisResponse.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisReasonDto.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisEvidenceDto.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisContextDto.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/resources/application.yml`
- `/Users/qqx/job/code/eladmin-mp/agent-service/rules/meal-plan/*.yaml`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/client/DiagnosisAiClientTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/prompt/DiagnosisPromptBuilderTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/rule/RuleRegistryLoaderTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/validator/DiagnosisResultValidatorTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/controller/MealPlanDiagnosisControllerTest.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/AgentDiagnosisController.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/InternalAgentDiagnosisContextController.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentDiagnosisFacadeService.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentDiagnosisContextService.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisFacadeServiceImpl.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisContextServiceImpl.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentDiagnosisRequest.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentDiagnosisResponse.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/MealPlanDiagnosisContextRequest.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/MealPlanDiagnosisContextDto.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisContextServiceImplTest.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisFacadeServiceImplTest.java`
- `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/api/agentDiagnosis.js`
- `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/views/agent/diagnosis/index.vue`
- `/Users/qqx/job/code/eladmin-mp/eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- `/Users/qqx/job/code/eladmin-mp/eladmin/doc/apidoc/智能排查助手接口文档.md`
- `/Users/qqx/job/code/eladmin-mp/eladmin/doc/business/智能排查助手业务说明.md`

### Modify

- `/Users/qqx/job/code/eladmin-mp/eladmin/pom.xml`
- `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/resources/config/application-dev.yml`
- `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/router/index.js` 或现有动态菜单关联配置文件
- `/Users/qqx/job/code/eladmin-mp/eladmin/doc/智能排查Agent服务设计方案.md`
- `/Users/qqx/job/code/eladmin-mp/scripts/deploy-from-github.sh`

---

## 方案调整说明（2026-05-18）

- Task 1 和 Task 2 已完成，且保留有效。
- 原 Task 3 至 Task 6 基于“规则分析器优先、AI 总结可插拔”的旧方案编写。
- 从 2026-05-18 起，后续实现以“rule registry + 业务上下文 + AI 直接分析”为准。
- 下文保留旧任务作为历史记录，不再作为后续开发依据。
- 历史任务中的代码片段仅记录当时实现方式；`agent-service` 后续依赖版本以 Task 3A 中的 Spring Boot 3.5.14 和 Spring AI 1.1.6 为准。

## 当前实施重点

### Task 3A: 将 agent-service 调整为 AI 直接分析链路

**目标：** 废弃“本地分析器判断原因 + 模板总结”的主路径，改为“业务上下文 + rule registry + Spring AI 结构化诊断”的主路径。本地规则只负责基础校验、兜底和审计，不负责给出主要业务结论。

**Files:**
- Modify: `/Users/qqx/job/code/eladmin-mp/agent-service/pom.xml`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/client/DiagnosisAiClient.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/client/SpringAiDiagnosisAiClient.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/prompt/DiagnosisPromptBuilder.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/rule/DiagnosisRule.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/rule/RuleRegistry.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/rule/RuleRegistryLoader.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/config/SpringAiConfig.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/tool/AgentToolRegistry.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/advisor/AgentAdvisorChain.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/memory/AgentMemoryConfig.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/validator/DiagnosisResultValidator.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/rules/meal-plan/customer.yaml`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/rules/meal-plan/order.yaml`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/rules/meal-plan/schedule.yaml`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/rules/meal-plan/dish-candidate.yaml`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/rules/meal-plan/generated-result.yaml`
- Modify: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/service/impl/MealPlanDiagnosisServiceImpl.java`
- Modify: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/orchestrator/MealPlanDiagnosisOrchestrator.java`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/rule/RuleRegistryLoaderTest.java`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/prompt/DiagnosisPromptBuilderTest.java`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/validator/DiagnosisResultValidatorTest.java`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/orchestrator/MealPlanDiagnosisOrchestratorTest.java`

- [x] **Step 1: 给 `pom.xml` 增加 Spring AI BOM 和模型 starter**

实施时以 Spring AI 官方文档的最新稳定版本为准。当前建议结构：

```xml
<properties>
    <java.version>17</java.version>
    <spring.boot.version>3.5.14</spring.boot.version>
    <spring.ai.version>1.1.6</spring.ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring.boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring.ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

模型 starter 第一阶段先选一个供应商落地，例如 OpenAI 兼容接口：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

- [x] **Step 2: 定义 `DiagnosisAiClient` 抽象，隔离 Spring AI 细节**

```java
package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.rule.RuleRegistry;

public interface DiagnosisAiClient {
    DiagnosisResponse diagnose(DiagnosisContextDto context, RuleRegistry ruleRegistry);
}
```

- [x] **Step 3: 定义 rule registry 模型**

```java
package me.zhengjie.agent.rule;

import java.util.List;

public class DiagnosisRule {
    private String ruleId;
    private Integer version;
    private String scene;
    private String title;
    private String description;
    private List<String> requiredData;
    private List<String> decisionHints;
    private List<String> evidenceFields;
    private String severity;
    private String owner;
}
```

```java
package me.zhengjie.agent.rule;

import java.util.List;

public class RuleRegistry {
    private String scene;
    private String versionDigest;
    private List<DiagnosisRule> rules;
}
```

- [x] **Step 4: 写 `RuleRegistryLoaderTest`，约束规则文件质量**

必须覆盖：

- 能加载 `agent-service/rules/meal-plan/*.yaml`
- `ruleId` 全局唯一
- `version` 大于 0
- `requiredData` 非空
- 至少包含客户、订单、排餐、候选菜、生成失败五类规则

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
mvn -q -Dtest=RuleRegistryLoaderTest test
```

Expected: PASS

- [x] **Step 5: 创建首批 rule registry YAML**

首批规则至少包含：

- `CUSTOMER_NOT_FOUND`
- `ORDER_MISSING`
- `ORDER_NOT_EFFECTIVE`
- `ORDER_REMAINING_COUNT_EMPTY`
- `SCHEDULE_MODE_NOT_MATCH`
- `CUSTOMER_EXCLUDE_DATE_HIT`
- `CANDIDATE_DISH_EMPTY`
- `DISH_FILTERED_EMPTY`
- `MEAL_PLAN_GENERATED_FAILED`

- [x] **Step 6: 实现 `DiagnosisPromptBuilder`**

提示词必须包含：

- 角色：只做排餐未生成原因诊断
- 输入：用户问题、诊断上下文、rule registry
- 约束：不能声称已修改数据，不能编造字段，不能输出未提供的敏感信息
- 输出：固定 JSON，对应 `DiagnosisResponse`
- 证据：每个原因必须引用字段、证据或 `ruleId`

测试重点：

- prompt 包含用户输入日期、餐次、客户标识
- prompt 包含所有规则 `ruleId`
- prompt 明确要求 JSON 结构化输出
- prompt 明确要求“AI 建议需要人工确认”

- [x] **Step 7: 实现 `DiagnosisResultValidator`**

校验规则：

- `summary` 非空
- `reasons` 不为 null
- `level` 只能是 `HIGH`、`MEDIUM`、`LOW`
- 每个 reason 至少有一条 evidence 或一个 ruleId
- 不允许出现“已修复”“已修改数据库”“已创建客户”等写操作措辞

校验失败时生成兜底 `DiagnosisResponse`。

- [x] **Step 8: 实现 `SpringAiDiagnosisAiClient`**

职责：

- 调用 `DiagnosisPromptBuilder` 生成 prompt
- 使用 Spring AI `ChatClient` 发起模型调用
- 使用结构化输出或 JSON 解析映射到 `DiagnosisResponse`
- 调用 `DiagnosisResultValidator` 校验
- 捕获模型超时、解析失败、校验失败并返回兜底结果

- [x] **Step 9: 改造 `MealPlanDiagnosisOrchestrator`**

新编排顺序：

1. 接收 `DiagnosisContextDto`
2. 加载 `RuleRegistry`
3. 调用 `DiagnosisAiClient`
4. 校验并补齐请求基础字段
5. 返回 `DiagnosisResponse`

历史 `DiagnosisAnalyzer` 可暂时保留在代码中，但不能再作为主诊断路径。

- [x] **Step 10: 预留 Spring AI Tool、Advisor、Memory 空壳**

第一阶段不实现写操作，只建立边界：

- `AgentToolRegistry` 只注册只读工具或暂时为空
- `AgentAdvisorChain` 预留审计、权限、提示词增强入口
- `AgentMemoryConfig` 预留多轮会话配置，默认不开启长期记忆

- [x] **Step 11: 运行 agent-service 核心测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/agent-service
mvn -q -Dtest=RuleRegistryLoaderTest,DiagnosisPromptBuilderTest,DiagnosisResultValidatorTest,MealPlanDiagnosisOrchestratorTest test
```

Expected: PASS

### Task 4A: 保留主系统代理链路并接上新的 agent-service 诊断接口

**目标：** 前端仍只访问 `eladmin-system`，由 `eladmin-system` 负责权限、审计和代理调用；`agent-service` 只作为内部 AI 诊断能力服务。

**Files:**
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/AgentDiagnosisController.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentDiagnosisFacadeService.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisFacadeServiceImpl.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/client/AgentServiceClient.java`
- Modify: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/resources/config/application-dev.yml`
- Test: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisFacadeServiceImplTest.java`

- [x] **Step 1: 新增后台对外诊断接口**

接口路径：

```text
POST /api/agent/meal-plan/diagnose
```

职责：

- 校验后台登录与菜单权限
- 接收前端诊断请求
- 调用 `AgentDiagnosisFacadeService`
- 返回统一 `summary / reasons / evidence` 结构

- [x] **Step 2: 新增 `AgentServiceClient`**

职责：

- HTTP 调用 `agent-service`
- 设置超时时间
- 失败时返回可识别异常，不吞掉错误
- 不在主系统里拼 AI prompt

- [x] **Step 3: 新增代理服务测试**

测试重点：

- 前端请求能被转换为 agent-service 请求
- agent-service 返回结果能原样透传关键字段
- agent-service 超时时返回友好兜底提示

- [x] **Step 4: 运行主系统代理测试**

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin
mvn -q -Dtest=AgentDiagnosisFacadeServiceImplTest test
```

Expected: PASS

### Task 5A: 增加规则同步约束

**目标：** 在没有 CI 的情况下，先通过部署脚本建立“改诊断代码必须同步考虑规则”的约束。

**Files:**
- Modify: `/Users/qqx/job/code/eladmin-mp/scripts/deploy-from-github.sh`
- Create: `/Users/qqx/job/code/eladmin-mp/scripts/check-agent-rule-sync.sh`

- [x] **Step 1: 新增规则同步检查脚本**

输入：

```bash
scripts/check-agent-rule-sync.sh <previous_commit> <current_commit>
```

判断：

- 如果变更包含 `agent-service/src/main/java/me/zhengjie/agent/orchestrator/`
- 或变更包含 `agent-service/src/main/java/me/zhengjie/agent/context/`
- 或变更包含 `agent-service/src/main/java/me/zhengjie/agent/client/`
- 或变更包含 `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/`
- 但不包含 `agent-service/rules/`
- 且提交信息不包含 `rules-no-change`
- 则退出码为 1

- [x] **Step 2: 在部署脚本中调用校验**

调用位置：

- `detect_changes` 之后
- `determine_build_targets` 之前

失败提示：

```text
agent diagnosis code changed but agent-service/rules was not updated.
If rules are unchanged by design, include "rules-no-change" in commit message.
```

- [x] **Step 3: 本地验证脚本行为**

用临时提交区或历史 commit 验证三种场景：

- 只改普通文件：通过
- 改诊断代码但不改规则且无 `rules-no-change`：失败
- 改诊断代码但提交信息有 `rules-no-change`：通过

### Task 6A: 更新前端与文档口径

**目标：** 前端先保持实用型页面，不做完整聊天机器人；但页面文案、接口文档和业务说明必须清楚表达“AI 诊断建议，需要人工确认”。

**Files:**
- Modify: `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/views/agent/diagnosis/index.vue`
- Modify: `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/api/agentDiagnosis.js`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/doc/apidoc/智能排查助手接口文档.md`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/doc/business/智能排查助手业务说明.md`

- [x] **Step 1: 前端页面文案调整**

页面显示：

- 标题：`智能排查助手`
- 结果标题：`AI 诊断结果`
- 提示文案：`以下为 AI 基于当前业务数据和规则生成的诊断建议，请结合证据人工确认。`

- [x] **Step 2: 接口文档补充 AI 字段**

接口文档必须说明：

- `requestId`
- `ruleVersionDigest`
- `modelName`
- `fallback`
- `summary`
- `reasons[].ruleIds`
- `reasons[].evidence`

- [x] **Step 3: 业务说明补充边界**

业务说明必须写清楚：

- 第一阶段只做排餐未生成诊断
- 不自动修复数据
- 不直接新增客户
- AI 新增客户后续采用“草稿 -> 人工确认 -> 正式提交”

---

### Task 1: 搭建 agent-service 骨架与统一返回模型

**Status:** 已完成

**Files:**
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/pom.xml`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/AgentServiceApplication.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisRequest.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisResponse.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisReasonDto.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisEvidenceDto.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/enums/DiagnosisLevel.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/resources/application.yml`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/controller/MealPlanDiagnosisControllerTest.java`

- [x] **Step 1: 写控制器层失败用例，锁定诊断接口契约**

```java
package me.zhengjie.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MealPlanDiagnosisController.class)
class MealPlanDiagnosisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MealPlanDiagnosisService diagnosisService;

    @Test
    void shouldReturnDiagnosisResponse() throws Exception {
        DiagnosisResponse response = new DiagnosisResponse();
        response.setCustomerId(1001L);
        response.setCustomerName("张三");
        response.setSummary("命中客户排除日期");
        response.setReasons(Collections.emptyList());
        given(diagnosisService.diagnose(any())).willReturn(response);

        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        mockMvc.perform(post("/api/agent/meal-plan/diagnose")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerId").value(1001L))
            .andExpect(jsonPath("$.summary").value("命中客户排除日期"));
    }
}
```

- [x] **Step 2: 运行单测，确认当前缺少应用骨架与控制器实现**

Run: `cd /Users/qqx/job/code/eladmin-mp/agent-service && mvn test -Dtest=MealPlanDiagnosisControllerTest`

Expected: FAIL，提示 `MealPlanDiagnosisController` 或 Spring Boot 应用类不存在。

- [x] **Step 3: 创建最小可启动工程和统一 DTO**

```xml
<!-- /Users/qqx/job/code/eladmin-mp/agent-service/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>me.zhengjie</groupId>
    <artifactId>agent-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
        <spring.boot.version>3.3.2</spring.boot.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/AgentServiceApplication.java
package me.zhengjie.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}
```

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisRequest.java
package me.zhengjie.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DiagnosisRequest {
    private Long customerId;
    private String customerCode;
    @NotBlank
    private String recordDate;
    @NotBlank
    private String mealType;
}
```

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisResponse.java
package me.zhengjie.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DiagnosisResponse {
    private String requestId;
    private Long customerId;
    private String customerName;
    private String recordDate;
    private String mealType;
    private String summary;
    private List<DiagnosisReasonDto> reasons = new ArrayList<>();
}
```

- [x] **Step 4: 实现最小控制器与服务接口，让契约测试通过**

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/controller/MealPlanDiagnosisController.java
package me.zhengjie.agent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/meal-plan")
public class MealPlanDiagnosisController {

    private final MealPlanDiagnosisService diagnosisService;

    @PostMapping("/diagnose")
    public DiagnosisResponse diagnose(@Valid @RequestBody DiagnosisRequest request) {
        return diagnosisService.diagnose(request);
    }
}
```

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/service/MealPlanDiagnosisService.java
package me.zhengjie.agent.service;

import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;

public interface MealPlanDiagnosisService {
    DiagnosisResponse diagnose(DiagnosisRequest request);
}
```

```yaml
# /Users/qqx/job/code/eladmin-mp/agent-service/src/main/resources/application.yml
server:
  port: 18081

spring:
  application:
    name: agent-service
```

- [x] **Step 5: 运行测试并提交骨架代码**

Run: `cd /Users/qqx/job/code/eladmin-mp/agent-service && mvn test -Dtest=MealPlanDiagnosisControllerTest`

Expected: PASS

```bash
git -C /Users/qqx/job/code/eladmin-mp add agent-service
git -C /Users/qqx/job/code/eladmin-mp commit -m "feat: 初始化智能排查agent服务骨架"
```

### Task 2: 在 eladmin-system 中提供诊断上下文聚合接口

**Files:**
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/InternalAgentDiagnosisContextController.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentDiagnosisContextService.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisContextServiceImpl.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/MealPlanDiagnosisContextRequest.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/MealPlanDiagnosisContextDto.java`
- Modify: `/Users/qqx/job/code/eladmin-mp/eladmin/pom.xml`
- Test: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisContextServiceImplTest.java`

- [x] **Step 1: 先写上下文服务失败用例，覆盖第一批关键规则字段**

```java
package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import org.junit.Test;

import static org.junit.Assert.*;

public class AgentDiagnosisContextServiceImplTest {

    @Test
    public void shouldAssembleDiagnosisContext() {
        AgentDiagnosisContextServiceImpl service = new AgentDiagnosisContextServiceImpl();
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        MealPlanDiagnosisContextDto context = service.buildContext(request);

        assertNotNull(context);
        assertEquals("2026-05-17", context.getRecordDate());
        assertEquals("LUNCH", context.getMealType());
    }
}
```

- [x] **Step 2: 运行后端单测，确认模块和实现类尚未创建**

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -q -Dtest=AgentDiagnosisContextServiceImplTest test`

Expected: FAIL，提示 `AgentDiagnosisContextServiceImpl` 或相关 DTO 不存在。

- [x] **Step 3: 增加 agent 模块 DTO、服务接口和内部控制器**

```java
// /Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/MealPlanDiagnosisContextRequest.java
package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

@Data
public class MealPlanDiagnosisContextRequest {
    private Long customerId;
    private String customerCode;
    private String recordDate;
    private String mealType;
}
```

```java
// /Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/MealPlanDiagnosisContextDto.java
package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class MealPlanDiagnosisContextDto {
    private Long customerId;
    private String customerName;
    private String recordDate;
    private String mealType;
    private Map<String, Object> customerProfile;
    private List<Map<String, Object>> orders = new ArrayList<>();
    private Map<String, Object> mealPlan;
    private List<Map<String, Object>> customerPlans = new ArrayList<>();
    private Map<String, Object> candidateDishStats;
}
```

```java
// /Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/InternalAgentDiagnosisContextController.java
package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/agent")
public class InternalAgentDiagnosisContextController {

    private final AgentDiagnosisContextService contextService;

    @PostMapping("/meal-plan/context")
    public ResponseEntity<MealPlanDiagnosisContextDto> buildContext(@RequestBody MealPlanDiagnosisContextRequest request) {
        return ResponseEntity.ok(contextService.buildContext(request));
    }
}
```

- [x] **Step 4: 用最小聚合实现串起客户、订单、排餐三类数据**

```java
// /Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisContextServiceImpl.java
package me.zhengjie.modules.agent.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.meal.service.MealPlanService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentDiagnosisContextServiceImpl implements AgentDiagnosisContextService {

    private final CustomerProfileService customerProfileService;
    private final CustomerOrderService customerOrderService;
    private final MealPlanService mealPlanService;

    @Override
    public MealPlanDiagnosisContextDto buildContext(MealPlanDiagnosisContextRequest request) {
        MealPlanDiagnosisContextDto context = new MealPlanDiagnosisContextDto();
        context.setCustomerId(request.getCustomerId());
        context.setRecordDate(request.getRecordDate());
        context.setMealType(request.getMealType());
        context.setCustomerProfile(customerProfileService.queryDiagnosisProfile(request.getCustomerId(), request.getCustomerCode()));
        context.setOrders(customerOrderService.queryDiagnosisOrders(request.getCustomerId(), request.getRecordDate(), request.getMealType()));
        context.setMealPlan(mealPlanService.queryDiagnosisMealPlan(request.getCustomerId(), request.getRecordDate(), request.getMealType()));
        return context;
    }
}
```

```java
// /Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentDiagnosisContextService.java
package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;

public interface AgentDiagnosisContextService {
    MealPlanDiagnosisContextDto buildContext(MealPlanDiagnosisContextRequest request);
}
```

- [x] **Step 5: 运行单测并提交主系统上下文接口**

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -q -Dtest=AgentDiagnosisContextServiceImplTest test`

Expected: PASS

```bash
git -C /Users/qqx/job/code/eladmin-mp add eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent
git -C /Users/qqx/job/code/eladmin-mp commit -m "feat: 新增智能排查上下文聚合接口"
```

### Task 3（历史方案，保留记录，不再执行）: 在 agent-service 中落地诊断编排与首批分析器

**Status:** 已完成

**Files:**
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/orchestrator/MealPlanDiagnosisOrchestrator.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/DiagnosisAnalyzer.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/ExcludeDateAnalyzer.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/OrderEffectiveAnalyzer.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/PlanFailedAnalyzer.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/client/DiagnosisContextClient.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/summary/DiagnosisSummaryService.java`
- Create: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/summary/TemplateDiagnosisSummaryService.java`
- Modify: `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/service/MealPlanDiagnosisService.java`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/orchestrator/MealPlanDiagnosisOrchestratorTest.java`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/analyzer/impl/ExcludeDateAnalyzerTest.java`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/analyzer/impl/OrderEffectiveAnalyzerTest.java`
- Test: `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/analyzer/impl/PlanFailedAnalyzerTest.java`

- [x] **Step 1: 先写编排器和核心分析器失败用例**

```java
package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExcludeDateAnalyzerTest {

    @Test
    void shouldHitExcludeDateReason() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");
        context.setCustomerProfile(Map.of(
            "excludeDates", List.of(Map.of("date", "2026-05-17", "mealTypes", List.of("LUNCH")))
        ));

        ExcludeDateAnalyzer analyzer = new ExcludeDateAnalyzer();
        List<DiagnosisReasonDto> reasons = analyzer.analyze(context);

        assertFalse(reasons.isEmpty());
        assertEquals("EXCLUDE_DATE_HIT", reasons.get(0).getCode());
    }
}
```

```java
package me.zhengjie.agent.orchestrator;

import me.zhengjie.agent.analyzer.DiagnosisAnalyzer;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.summary.DiagnosisSummaryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MealPlanDiagnosisOrchestratorTest {

    @Test
    void shouldCollectReasonsAndSummary() {
        DiagnosisAnalyzer analyzer = mock(DiagnosisAnalyzer.class);
        DiagnosisSummaryService summaryService = mock(DiagnosisSummaryService.class);
        when(analyzer.analyze(new DiagnosisContextDto())).thenReturn(List.of());
        when(summaryService.buildSummary(org.mockito.ArgumentMatchers.any())).thenReturn("未命中异常");

        MealPlanDiagnosisOrchestrator orchestrator =
            new MealPlanDiagnosisOrchestrator(List.of(analyzer), summaryService);

        DiagnosisResponse response = orchestrator.orchestrate(new DiagnosisContextDto());

        assertEquals("未命中异常", response.getSummary());
    }
}
```

- [x] **Step 2: 运行 agent-service 单测，确认编排器和分析器尚未实现**

Run: `cd /Users/qqx/job/code/eladmin-mp/agent-service && mvn test -Dtest=ExcludeDateAnalyzerTest,MealPlanDiagnosisOrchestratorTest`

Expected: FAIL，提示相关类不存在或方法签名不匹配。

- [x] **Step 3: 定义分析器接口、编排器和模板总结服务**

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/DiagnosisAnalyzer.java
package me.zhengjie.agent.analyzer;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;

import java.util.List;

public interface DiagnosisAnalyzer {
    List<DiagnosisReasonDto> analyze(DiagnosisContextDto context);
}
```

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/orchestrator/MealPlanDiagnosisOrchestrator.java
package me.zhengjie.agent.orchestrator;

import lombok.RequiredArgsConstructor;
import me.zhengjie.agent.analyzer.DiagnosisAnalyzer;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.summary.DiagnosisSummaryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MealPlanDiagnosisOrchestrator {

    private final List<DiagnosisAnalyzer> analyzers;
    private final DiagnosisSummaryService summaryService;

    public DiagnosisResponse orchestrate(DiagnosisContextDto context) {
        List<DiagnosisReasonDto> reasons = new ArrayList<>();
        for (DiagnosisAnalyzer analyzer : analyzers) {
            reasons.addAll(analyzer.analyze(context));
        }
        DiagnosisResponse response = new DiagnosisResponse();
        response.setCustomerId(context.getCustomerId());
        response.setCustomerName(context.getCustomerName());
        response.setRecordDate(context.getRecordDate());
        response.setMealType(context.getMealType());
        response.setReasons(reasons);
        response.setSummary(summaryService.buildSummary(response));
        return response;
    }
}
```

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/summary/TemplateDiagnosisSummaryService.java
package me.zhengjie.agent.summary;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.springframework.stereotype.Service;

@Service
public class TemplateDiagnosisSummaryService implements DiagnosisSummaryService {

    @Override
    public String buildSummary(DiagnosisResponse response) {
        if (response.getReasons().isEmpty()) {
            return "当前未命中明确异常原因，建议人工继续核对订单与菜单配置。";
        }
        return response.getReasons().get(0).getTitle();
    }
}
```

- [x] **Step 4: 实现首批 3 个高价值分析器，并在服务层串起上下文客户端**

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/ExcludeDateAnalyzer.java
package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.analyzer.DiagnosisAnalyzer;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.enums.DiagnosisLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExcludeDateAnalyzer implements DiagnosisAnalyzer {

    @Override
    @SuppressWarnings("unchecked")
    public List<DiagnosisReasonDto> analyze(DiagnosisContextDto context) {
        List<Map<String, Object>> excludeDates =
            (List<Map<String, Object>>) context.getCustomerProfile().getOrDefault("excludeDates", List.of());
        List<DiagnosisReasonDto> reasons = new ArrayList<>();
        for (Map<String, Object> item : excludeDates) {
            if (context.getRecordDate().equals(item.get("date")) && ((List<String>) item.get("mealTypes")).contains(context.getMealType())) {
                DiagnosisReasonDto reason = new DiagnosisReasonDto();
                reason.setCode("EXCLUDE_DATE_HIT");
                reason.setTitle("命中客户排除日期");
                reason.setLevel(DiagnosisLevel.HIGH.name());
                reason.setDescription("客户档案配置了目标日期和餐次不配送。");
                reason.setEvidence(List.of(
                    new DiagnosisEvidenceDto("排除日期", String.valueOf(item.get("date"))),
                    new DiagnosisEvidenceDto("排除餐次", context.getMealType())
                ));
                reason.setSuggestion("先核对客户是否已登记停送。");
                reasons.add(reason);
            }
        }
        return reasons;
    }
}
```

```java
// /Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/service/impl/MealPlanDiagnosisServiceImpl.java
package me.zhengjie.agent.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.agent.client.DiagnosisContextClient;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.orchestrator.MealPlanDiagnosisOrchestrator;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MealPlanDiagnosisServiceImpl implements MealPlanDiagnosisService {

    private final DiagnosisContextClient contextClient;
    private final MealPlanDiagnosisOrchestrator orchestrator;

    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest request) {
        DiagnosisContextDto context = contextClient.fetchContext(request);
        return orchestrator.orchestrate(context);
    }
}
```

- [x] **Step 5: 跑通 agent-service 单测并提交首批诊断能力**

Run: `cd /Users/qqx/job/code/eladmin-mp/agent-service && mvn test`

Expected: PASS

```bash
git -C /Users/qqx/job/code/eladmin-mp add agent-service
git -C /Users/qqx/job/code/eladmin-mp commit -m "feat: 新增排餐诊断编排与首批分析器"
```

### Task 4（历史方案，保留记录，不再执行）: 在 eladmin-system 中增加面向前端的诊断代理接口

**Files:**
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/AgentDiagnosisController.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentDiagnosisFacadeService.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisFacadeServiceImpl.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentDiagnosisRequest.java`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/domain/dto/AgentDiagnosisResponse.java`
- Modify: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/resources/config/application-dev.yml`
- Test: `/Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisFacadeServiceImplTest.java`

- [ ] **Step 1: 先写代理服务失败用例，固定主系统对 agent-service 的调用方式**

```java
package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AgentDiagnosisFacadeServiceImplTest {

    @Test
    public void shouldForwardRequestToAgentService() {
        AgentDiagnosisFacadeServiceImpl service = new AgentDiagnosisFacadeServiceImpl();
        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = service.diagnose(request);

        assertEquals("2026-05-17", response.getRecordDate());
    }
}
```

- [ ] **Step 2: 运行后端单测，确认代理服务尚未实现**

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -q -Dtest=AgentDiagnosisFacadeServiceImplTest test`

Expected: FAIL，提示 Facade 类或 DTO 不存在。

- [ ] **Step 3: 增加前端可调用的代理控制器与 DTO**

```java
// /Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/AgentDiagnosisController.java
package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.Log;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/diagnosis")
public class AgentDiagnosisController {

    private final AgentDiagnosisFacadeService facadeService;

    @Log("智能排查诊断")
    @PostMapping("/meal-plan")
    @PreAuthorize("@el.check('agentDiagnosis:run')")
    public ResponseEntity<AgentDiagnosisResponse> diagnose(@RequestBody AgentDiagnosisRequest request) {
        return ResponseEntity.ok(facadeService.diagnose(request));
    }
}
```

- [ ] **Step 4: 用 `RestTemplate` 最小代理到 agent-service，并增加本地配置**

```java
// /Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/service/impl/AgentDiagnosisFacadeServiceImpl.java
package me.zhengjie.modules.agent.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AgentDiagnosisFacadeServiceImpl implements AgentDiagnosisFacadeService {

    private final RestTemplate restTemplate;

    @Value("${agent.service.base-url:http://127.0.0.1:18081}")
    private String agentServiceBaseUrl;

    @Override
    public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
        return restTemplate.postForObject(
            agentServiceBaseUrl + "/api/agent/meal-plan/diagnose",
            request,
            AgentDiagnosisResponse.class
        );
    }
}
```

```yaml
# /Users/qqx/job/code/eladmin-mp/eladmin/eladmin-system/src/main/resources/config/application-dev.yml
agent:
  service:
    base-url: http://127.0.0.1:18081
```

- [ ] **Step 5: 运行单测并提交代理链路**

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -q -Dtest=AgentDiagnosisFacadeServiceImplTest test`

Expected: PASS

```bash
git -C /Users/qqx/job/code/eladmin-mp add eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent eladmin/eladmin-system/src/main/resources/config/application-dev.yml eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent
git -C /Users/qqx/job/code/eladmin-mp commit -m "feat: 打通智能排查代理调用链路"
```

### Task 5（历史方案，保留记录，不再执行）: 在 eladmin-web 中新增智能排查助手页面

**Files:**
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/api/agentDiagnosis.js`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/views/agent/diagnosis/index.vue`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- Modify: `/Users/qqx/job/code/eladmin-mp/eladmin-web/src/router/index.js` 或现有菜单配置接入文件

- [ ] **Step 1: 先写页面单测，固定最小交互**

```javascript
import { shallowMount } from '@vue/test-utils'
import AgentDiagnosis from '@/views/agent/diagnosis/index.vue'

describe('AgentDiagnosis', () => {
  it('renders query form and result area', () => {
    const wrapper = shallowMount(AgentDiagnosis, {
      stubs: ['el-form', 'el-form-item', 'el-input', 'el-date-picker', 'el-select', 'el-option', 'el-button', 'el-card', 'el-tag']
    })
    expect(wrapper.text()).toContain('智能排查助手')
    expect(wrapper.text()).toContain('开始分析')
  })
})
```

- [ ] **Step 2: 运行前端单测，确认页面与 API 文件尚未创建**

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin-web && npm run test:unit -- tests/unit/views/agent/diagnosis/index.spec.js`

Expected: FAIL，提示找不到 `@/views/agent/diagnosis/index.vue`。

- [ ] **Step 3: 新增 API 封装和页面骨架**

```javascript
// /Users/qqx/job/code/eladmin-mp/eladmin-web/src/api/agentDiagnosis.js
import request from '@/utils/request'

export function diagnoseMealPlan(data) {
  return request({
    url: '/api/agent/diagnosis/meal-plan',
    method: 'post',
    data
  })
}
```

```vue
<!-- /Users/qqx/job/code/eladmin-mp/eladmin-web/src/views/agent/diagnosis/index.vue -->
<template>
  <div class="app-container">
    <div class="head-container">
      <span class="filter-item" style="font-size: 18px; font-weight: 600;">智能排查助手</span>
      <el-input v-model="form.customerCode" placeholder="客户编号" size="small" style="width: 140px;" class="filter-item" />
      <el-date-picker v-model="form.recordDate" type="date" value-format="yyyy-MM-dd" placeholder="排餐日期" size="small" class="filter-item" />
      <el-select v-model="form.mealType" placeholder="餐次" size="small" style="width: 120px;" class="filter-item">
        <el-option label="午餐" value="LUNCH" />
        <el-option label="晚餐" value="DINNER" />
      </el-select>
      <el-button type="primary" size="small" @click="runDiagnosis">开始分析</el-button>
    </div>
    <el-card v-if="result" class="box-card">
      <div slot="header">诊断结果</div>
      <div>{{ result.summary }}</div>
      <div v-for="reason in result.reasons" :key="reason.code" class="reason-card">
        <el-tag :type="reason.level === 'HIGH' ? 'danger' : 'warning'">{{ reason.title }}</el-tag>
        <p>{{ reason.description }}</p>
      </div>
    </el-card>
  </div>
</template>
```

- [ ] **Step 4: 接上接口调用与错误提示**

```javascript
<script>
import { diagnoseMealPlan } from '@/api/agentDiagnosis'

export default {
  name: 'AgentDiagnosis',
  data() {
    return {
      loading: false,
      result: null,
      form: {
        customerCode: '',
        recordDate: '',
        mealType: 'LUNCH'
      }
    }
  },
  methods: {
    async runDiagnosis() {
      this.loading = true
      try {
        this.result = await diagnoseMealPlan(this.form)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
```

- [ ] **Step 5: 运行前端单测并提交后台页面**

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin-web && npm run test:unit -- tests/unit/views/agent/diagnosis/index.spec.js`

Expected: PASS

```bash
git -C /Users/qqx/job/code/eladmin-mp add eladmin-web/src/api/agentDiagnosis.js eladmin-web/src/views/agent/diagnosis/index.vue eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js
git -C /Users/qqx/job/code/eladmin-mp commit -m "feat: 新增智能排查助手后台页面"
```

### Task 6（历史方案，保留记录，不再执行）: 补齐文档、接口说明与联调验证

**Files:**
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/doc/apidoc/智能排查助手接口文档.md`
- Create: `/Users/qqx/job/code/eladmin-mp/eladmin/doc/business/智能排查助手业务说明.md`
- Modify: `/Users/qqx/job/code/eladmin-mp/eladmin/doc/智能排查Agent服务设计方案.md`
- Modify: `/Users/qqx/job/code/eladmin-mp/eladmin/doc/智能排查Agent服务一期实施计划.md`

- [ ] **Step 1: 先写联调检查清单，覆盖独立服务启动和主系统代理链路**

```markdown
# 联调检查清单

- agent-service 已启动，端口 `18081`
- eladmin-system 已配置 `agent.service.base-url`
- 前端页面能正常提交客户编号、日期、餐次
- 返回结果至少包含 `summary`、`reasons`、`evidence`
```

- [ ] **Step 2: 补齐接口文档，明确两个系统之间的契约**

```markdown
# 智能排查助手接口文档

## 1. 后台对外接口

- `POST /api/agent/diagnosis/meal-plan`

请求体：

```json
{
  "customerId": 1001,
  "customerCode": "A11001",
  "recordDate": "2026-05-17",
  "mealType": "LUNCH"
}
```

返回体：

```json
{
  "customerId": 1001,
  "customerName": "张三",
  "recordDate": "2026-05-17",
  "mealType": "LUNCH",
  "summary": "命中客户排除日期",
  "reasons": []
}
```
```

- [ ] **Step 3: 补齐业务说明，写清楚“规则诊断优先”的产品边界**

```markdown
# 智能排查助手业务说明

## 产品定位

智能排查助手用于帮助后台客服定位“客户未生成排餐计划”的可能原因。

## 第一阶段边界

- 只做排餐未生成诊断
- 不自动修复数据
- 结果必须附证据
```

- [ ] **Step 4: 执行完整联调验证**

Run: `cd /Users/qqx/job/code/eladmin-mp/agent-service && mvn test`

Expected: PASS

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -q -Dtest=AgentDiagnosisContextServiceImplTest,AgentDiagnosisFacadeServiceImplTest test`

Expected: PASS

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin-web && npm run test:unit -- tests/unit/views/agent/diagnosis/index.spec.js`

Expected: PASS

- [ ] **Step 5: 提交文档与验证记录**

```bash
git -C /Users/qqx/job/code/eladmin-mp add eladmin/doc/智能排查Agent服务设计方案.md eladmin/doc/智能排查Agent服务一期实施计划.md eladmin/doc/apidoc/智能排查助手接口文档.md eladmin/doc/business/智能排查助手业务说明.md
git -C /Users/qqx/job/code/eladmin-mp commit -m "docs: 补充智能排查助手实施计划与接口说明"
```

---

## Self-Review

- 规格覆盖检查：已补充 AI 直接分析、rule registry、部署脚本约束和主系统上下文接口。
- 占位符检查：旧方案任务已明确标记为历史记录，不再作为现行实施依据。
- 类型一致性检查：`agent-service` 继续使用 `DiagnosisRequest / DiagnosisResponse / DiagnosisContextDto`；规则信息通过 `rules/` 目录和提示词链路注入。
