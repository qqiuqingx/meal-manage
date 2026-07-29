package me.zhengjie.agent.architecture;

import me.zhengjie.agent.application.conversation.ConversationCoordinator;
import me.zhengjie.agent.chat.DefaultConversationHandler;
import me.zhengjie.agent.chat.MealPlanChatServiceImpl;
import me.zhengjie.agent.query.BusinessAnswerComposer;
import me.zhengjie.agent.query.BusinessQueryResponseFactory;
import me.zhengjie.agent.query.BusinessResultValidator;
import me.zhengjie.agent.query.presentation.BusinessPresentationResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 固定中心入口的包边界，防止业务分支和工具字符串重新回流到 Facade/Coordinator。 */
class ArchitectureBoundaryTest {

    @Test
    void chatFacadeMustOnlyDependOnCoordinator() {
        Field[] fields = MealPlanChatServiceImpl.class.getDeclaredFields();
        assertEquals(2, fields.length);
        assertTrue(java.util.Arrays.stream(fields)
            .anyMatch(field -> field.getType() == ConversationCoordinator.class));
        assertEquals(1, MealPlanChatServiceImpl.class.getDeclaredConstructors().length);
    }

    @Test
    void centralRoutingClassesMustNotContainBusinessBranchesOrToolNames() throws Exception {
        String facade = Files.readString(Path.of(
            "src/main/java/me/zhengjie/agent/chat/MealPlanChatServiceImpl.java"));
        String coordinator = Files.readString(Path.of(
            "src/main/java/me/zhengjie/agent/application/conversation/ConversationCoordinator.java"));

        for (String forbidden : java.util.List.of("ChatIntent", "listOrders", "listMealPlans",
            "customerOverview", "BUSINESS_QUERY")) {
            assertFalse(facade.contains(forbidden), forbidden);
            assertFalse(coordinator.contains(forbidden), forbidden);
        }
    }

    @Test
    void defaultHandlerMustDelegateStateIntentAndLegacyMapCompatibility() throws Exception {
        String handler = Files.readString(Path.of(
            "src/main/java/me/zhengjie/agent/chat/DefaultConversationHandler.java"));

        assertEquals(1, DefaultConversationHandler.class.getDeclaredConstructors().length);
        assertEquals(1, java.util.Arrays.stream(DefaultConversationHandler.class.getDeclaredConstructors())
            .filter(constructor -> java.lang.reflect.Modifier.isPublic(constructor.getModifiers()))
            .count());
        assertTrue(handler.contains("ConversationStateSupport conversationStateSupport"));
        assertTrue(handler.contains("BusinessQueryIntentPolicy businessQueryIntentPolicy"));
        assertTrue(handler.contains("LegacyCustomerInsightAdapter legacyCustomerInsightAdapter"));
        assertTrue(handler.contains("BusinessQueryChatService businessQueryChatService"));
        assertTrue(handler.contains(
            "BusinessConversationUnderstandingPipeline understandingPipeline"));
        assertTrue(handler.contains(
            "BusinessConversationResultPipeline resultPipeline"));
        assertFalse(handler.contains(
            "this.businessQueryChatService = new BusinessQueryChatService"));
        assertFalse(handler.contains("new ContextReferenceResolver()"));
        for (String forbidden : java.util.List.of(
            "dataClient.getCustomerMealSummary",
            "dataClient.getCustomerVerificationSummary",
            "dataClient.getCustomerOrderSummary",
            "private DiagnosisSlots copy(",
            "private ChatIntent compatibilityIntent(",
            "private String buildMealBalanceMessage(")) {
            assertFalse(handler.contains(forbidden), forbidden);
        }
    }

    @Test
    void presenterAndResultValidationMustConsumeControlledDto() {
        assertTrue(java.util.Arrays.stream(BusinessAnswerComposer.class.getDeclaredMethods())
            .flatMap(method -> java.util.Arrays.stream(method.getParameterTypes()))
            .noneMatch(java.util.Map.class::equals));
        assertTrue(java.util.Arrays.stream(BusinessQueryResponseFactory.class.getDeclaredMethods())
            .filter(method -> "create".equals(method.getName()))
            .allMatch(method -> java.util.Arrays.asList(method.getParameterTypes())
                .contains(BusinessPresentationResult.class)));
        assertTrue(java.util.Arrays.stream(BusinessResultValidator.class.getDeclaredMethods())
            .filter(method -> "validate".equals(method.getName()))
            .allMatch(method -> method.getParameterTypes()[2]
                == BusinessPresentationResult.class));
    }

    @Test
    void businessToolExecutionMustUseCatalogInvokersInsteadOfBranchChain() throws Exception {
        String catalog = Files.readString(Path.of(
            "src/main/java/me/zhengjie/agent/tool/ToolCatalog.java"));

        assertTrue(catalog.contains("BUSINESS_TOOL_INVOKERS.get(toolName)"));
        assertTrue(catalog.contains("BUSINESS_TOOLS.keySet().equals(BUSINESS_TOOL_INVOKERS.keySet())"));
        assertFalse(catalog.contains("if (\"resolveCustomer\".equals(toolName))"));
        assertFalse(catalog.contains("if (\"listOrders\".equals(toolName))"));
    }

    @Test
    void packageDependenciesMustPointTowardDomainPorts() throws Exception {
        assertSourcesDoNotContain(Path.of("src/main/java/me/zhengjie/agent/domain"), List.of(
            "import me.zhengjie.agent.controller.",
            "import me.zhengjie.agent.api.controller.",
            "import org.springframework.web.",
            "import org.springframework.http.",
            "RestTemplate",
            "WebClient"));
        assertSourcesDoNotContain(Path.of("src/main/java/me/zhengjie/agent/capability"), List.of(
            "import me.zhengjie.agent.controller.",
            "import me.zhengjie.agent.api.controller.",
            "import me.zhengjie.agent.api.contract."));
        assertSourcesDoNotContain(
            Path.of("src/main/java/me/zhengjie/agent/application/conversation"),
            List.of("import me.zhengjie.agent.client.Http", "import me.zhengjie.agent.query.client.Http"));
    }

    /** 检查指定生产包中的 Java 源码不包含反向依赖标识。 */
    private void assertSourcesDoNotContain(Path root, List<String> forbiddenTokens) throws Exception {
        try (Stream<Path> sources = Files.walk(root)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source);
                for (String forbidden : forbiddenTokens) {
                    assertFalse(content.contains(forbidden), source + " -> " + forbidden);
                }
            }
        }
    }
}
