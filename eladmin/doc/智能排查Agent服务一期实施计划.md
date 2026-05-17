# 智能排查Agent服务一期 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个独立启动的 `agent-service`，为后台客服提供“某客户为什么没有生成排餐计划”的诊断能力，并在现有后台中完成入口与结果展示。

**Architecture:** 采用“主系统提供诊断上下文 + agent-service 执行规则分析 + 后台页面展示结果”的三段式方案。第一期坚持规则诊断优先、AI 总结可插拔，避免让大模型直接承担业务事实判断。

**Tech Stack:** `eladmin-system`（JDK 17、Spring Boot 2.7.18、MyBatis-Plus）、`agent-service`（JDK 17、Spring Boot 3.x、JUnit 5）、`eladmin-web`（Vue 2.7、element-ui、Jest）

---

## File Structure

### Create

- `/Users/qqx/job/code/eladmin-mp/agent-service/pom.xml`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/AgentServiceApplication.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/controller/MealPlanDiagnosisController.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/service/MealPlanDiagnosisService.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/orchestrator/MealPlanDiagnosisOrchestrator.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/DiagnosisAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/CustomerMissingAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/OrderMissingAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/OrderEffectiveAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/RemainingCountAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/ScheduleModeAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/ExcludeDateAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/CandidateDishPoolAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/FilteredDishEmptyAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/analyzer/impl/PlanFailedAnalyzer.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/client/DiagnosisContextClient.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/summary/DiagnosisSummaryService.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/summary/TemplateDiagnosisSummaryService.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisRequest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisResponse.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisReasonDto.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisEvidenceDto.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/dto/DiagnosisContextDto.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/java/me/zhengjie/agent/domain/enums/DiagnosisLevel.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/main/resources/application.yml`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/orchestrator/MealPlanDiagnosisOrchestratorTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/analyzer/impl/ExcludeDateAnalyzerTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/analyzer/impl/OrderEffectiveAnalyzerTest.java`
- `/Users/qqx/job/code/eladmin-mp/agent-service/src/test/java/me/zhengjie/agent/analyzer/impl/PlanFailedAnalyzerTest.java`
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

---

### Task 1: 搭建 agent-service 骨架与统一返回模型

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

- [ ] **Step 1: 写控制器层失败用例，锁定诊断接口契约**

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

- [ ] **Step 2: 运行单测，确认当前缺少应用骨架与控制器实现**

Run: `cd /Users/qqx/job/code/eladmin-mp/agent-service && mvn test -Dtest=MealPlanDiagnosisControllerTest`

Expected: FAIL，提示 `MealPlanDiagnosisController` 或 Spring Boot 应用类不存在。

- [ ] **Step 3: 创建最小可启动工程和统一 DTO**

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

- [ ] **Step 4: 实现最小控制器与服务接口，让契约测试通过**

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

- [ ] **Step 5: 运行测试并提交骨架代码**

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

- [ ] **Step 1: 先写上下文服务失败用例，覆盖第一批关键规则字段**

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

- [ ] **Step 2: 运行后端单测，确认模块和实现类尚未创建**

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -q -Dtest=AgentDiagnosisContextServiceImplTest test`

Expected: FAIL，提示 `AgentDiagnosisContextServiceImpl` 或相关 DTO 不存在。

- [ ] **Step 3: 增加 agent 模块 DTO、服务接口和内部控制器**

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

- [ ] **Step 4: 用最小聚合实现串起客户、订单、排餐三类数据**

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

- [ ] **Step 5: 运行单测并提交主系统上下文接口**

Run: `cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -q -Dtest=AgentDiagnosisContextServiceImplTest test`

Expected: PASS

```bash
git -C /Users/qqx/job/code/eladmin-mp add eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent
git -C /Users/qqx/job/code/eladmin-mp commit -m "feat: 新增智能排查上下文聚合接口"
```

### Task 3: 在 agent-service 中落地诊断编排与首批分析器

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

- [ ] **Step 1: 先写编排器和核心分析器失败用例**

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

- [ ] **Step 2: 运行 agent-service 单测，确认编排器和分析器尚未实现**

Run: `cd /Users/qqx/job/code/eladmin-mp/agent-service && mvn test -Dtest=ExcludeDateAnalyzerTest,MealPlanDiagnosisOrchestratorTest`

Expected: FAIL，提示相关类不存在或方法签名不匹配。

- [ ] **Step 3: 定义分析器接口、编排器和模板总结服务**

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

- [ ] **Step 4: 实现首批 3 个高价值分析器，并在服务层串起上下文客户端**

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

- [ ] **Step 5: 跑通 agent-service 单测并提交首批诊断能力**

Run: `cd /Users/qqx/job/code/eladmin-mp/agent-service && mvn test`

Expected: PASS

```bash
git -C /Users/qqx/job/code/eladmin-mp add agent-service
git -C /Users/qqx/job/code/eladmin-mp commit -m "feat: 新增排餐诊断编排与首批分析器"
```

### Task 4: 在 eladmin-system 中增加面向前端的诊断代理接口

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

### Task 5: 在 eladmin-web 中新增智能排查助手页面

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

### Task 6: 补齐文档、接口说明与联调验证

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

- 规格覆盖检查：已覆盖独立 `agent-service`、主系统内部上下文接口、前端入口页面、接口文档与联调验证。
- 占位符检查：已去除 `TODO`、`TBD` 类表述；每个任务都给出了文件路径、测试命令和最小代码样例。
- 类型一致性检查：统一使用 `DiagnosisRequest / DiagnosisResponse / DiagnosisContextDto` 作为 agent-service 诊断主模型，主系统侧统一使用 `AgentDiagnosisRequest / AgentDiagnosisResponse / MealPlanDiagnosisContextDto` 作为代理与聚合模型。
