package me.zhengjie.agent.architecture;

import me.zhengjie.agent.application.BusinessAgentRunner;
import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一工具调用架构边界测试，防止旧 Planner、Capability 和关键词路由回流。
 */
class ArchitectureBoundaryTest {

    /**
     * 生产入口必须收敛为一个业务 Agent Runner，工具名称必须来自唯一登记表。
     */
    @Test
    void shouldExposeSingleToolCallingEntryAndTwelveTools() {
        assertTrue(BusinessAgentRunner.class.isAnnotationPresent(
            org.springframework.stereotype.Component.class));
        assertEquals(12, new ToolRegistry().all().size());
    }

    /**
     * 旧业务查询编排和重复工具目录不得重新出现在 Agent 生产源码中。
     */
    @Test
    void productionSourcesMustNotContainRemovedFixedRoutingTypes() throws Exception {
        assertSourcesDoNotContain(Path.of("src/main/java"), List.of(
            "BusinessQuery" + "PlanningService",
            "BusinessQuery" + "Planner",
            "AgentQuery" + "Plan",
            "Capability" + "Handler",
            "RuleBasedBusinessQuestion" + "Analyzer",
            "LegacyBusinessQuestionAnalysis" + "Factory",
            "AgentBusinessTool" + "Executor",
            "AgentBusinessTool" + "Registry",
            "Tool" + "Catalog",
            "Chat" + "Intent",
            "CUSTOMER_" + "ORDER_QUERY",
            "BUSINESS_QUERY_OPERATION" + "_",
            "capability-" + "catalog.yaml"));
    }

    /**
     * Agent 服务只依赖主系统只读 HTTP 端口，不得引入数据库、Mapper 或 JDBC 依赖。
     */
    @Test
    void agentServiceMustNotDeclareDatabaseDependencies() throws Exception {
        String pom = Files.readString(Path.of("pom.xml")).toLowerCase();
        for (String forbidden : List.of("mybatis", "jdbc", "mysql", "postgresql", "druid")) {
            assertFalse(pom.contains(forbidden), forbidden);
        }
        assertTrue(Files.readString(Path.of(
            "src/main/java/me/zhengjie/agent/client/HttpMainSystemQueryClient.java"))
            .contains("/api/internal/agent/query/"));
    }

    /**
     * 规则资源只能引用统一 ToolRegistry 中的名称。
     */
    @Test
    void ruleResourcesMustNotContainLegacyToolNames() throws Exception {
        assertSourcesDoNotContain(Path.of("rules"), List.of(
            "getCustomerProfile",
            "getCustomerExcludeDates",
            "getOrderMealBalance",
            "getMealPlanGenerationSnapshot",
            "getDishCandidateDetail",
            "getPackageSpec",
            "listVerificationLogs",
            "listMealRefunds"));
    }

    /** 调试日志记录受配置控制的脱敏工具和主系统请求正文，便于复盘具体调用。 */
    @Test
    void debugLogsMustContainConfigurableToolPayloadSummaries() throws Exception {
        String toolSource = Files.readString(Path.of(
            "src/main/java/me/zhengjie/agent/tool/BusinessAgentTools.java"));
        String querySource = Files.readString(Path.of(
            "src/main/java/me/zhengjie/agent/client/HttpMainSystemQueryClient.java"));

        assertTrue(toolSource.contains("rawInput={}"));
        assertTrue(toolSource.contains("output={}"));
        assertTrue(toolSource.contains("rawInputLength={}"));
        assertTrue(toolSource.contains("resultCount={}"));
        assertTrue(querySource.contains("body={}"));
        assertTrue(querySource.contains("requestBody={}"));
        assertTrue(querySource.contains("responseBody={}"));
        assertTrue(querySource.contains("requestType={}"));
        assertTrue(querySource.contains("truncated={}"));
    }

    /** 检查指定目录中的文本文件是否包含禁止回流的标识。 */
    private void assertSourcesDoNotContain(Path root, List<String> forbiddenTokens) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> sources = Files.walk(root)) {
            for (Path source : sources.filter(Files::isRegularFile)
                .filter(path -> path.toString().matches(".*\\\\.(java|yaml|yml|json|xml|properties|md)$"))
                .toList()) {
                String content = Files.readString(source);
                for (String forbidden : forbiddenTokens) {
                    assertFalse(content.contains(forbidden), source + " -> " + forbidden);
                }
            }
        }
    }
}
