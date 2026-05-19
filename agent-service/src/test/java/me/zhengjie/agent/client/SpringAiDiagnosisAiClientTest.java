package me.zhengjie.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.prompt.DiagnosisPromptBuilder;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.tool.AgentToolRegistry;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiDiagnosisAiClientTest {

    @Test
    void shouldRegisterToolsAndUseToolPromptWhenToolModeEnabled() {
        RecordingChatClient recording = new RecordingChatClient();
        AgentToolRegistry agentToolRegistry = toolRegistry();
        SpringAiDiagnosisAiClient client = new SpringAiDiagnosisAiClient(
            recording.builder(),
            new DiagnosisPromptBuilder(new ObjectMapper()),
            new DiagnosisResultValidator(),
            agentToolRegistry,
            true
        );

        client.diagnose(context(), registry());

        assertTrue(recording.toolsRegisteredWith(agentToolRegistry));
        assertTrue(recording.userPrompt().contains("getCustomerProfile"));
        assertTrue(recording.userPrompt().contains("listCustomerOrders"));
        assertFalse(recording.userPrompt().contains("业务上下文 JSON"));
        assertFalse(recording.userPrompt().contains("excludeDates"));
        assertFalse(recording.userPrompt().contains("张三"));
    }

    @Test
    void shouldSkipToolsAndUseLegacyPromptWhenToolModeDisabled() {
        RecordingChatClient recording = new RecordingChatClient();
        SpringAiDiagnosisAiClient client = new SpringAiDiagnosisAiClient(
            recording.builder(),
            new DiagnosisPromptBuilder(new ObjectMapper()),
            new DiagnosisResultValidator(),
            toolRegistry(),
            false
        );

        client.diagnose(context(), registry());

        assertFalse(recording.toolsRegistered());
        assertTrue(recording.userPrompt().contains("业务上下文 JSON"));
        assertTrue(recording.userPrompt().contains("excludeDates"));
        assertTrue(recording.userPrompt().contains("remainingCount"));
        assertTrue(recording.userPrompt().contains("张三"));
        assertFalse(recording.userPrompt().contains("如果证据不足，必须调用可用工具查询"));
    }

    private DiagnosisContextDto context() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(1001L);
        context.setCustomerCode("C1001");
        context.setCustomerName("张三");
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");
        context.setCustomerProfile(Map.of("excludeDates", List.of(Map.of("date", "2026-05-17"))));
        context.setOrders(List.of(Map.<String, Object>of("orderId", 88L, "remainingCount", 0)));
        return context;
    }

    private RuleRegistry registry() {
        DiagnosisRule rule = new DiagnosisRule();
        rule.setRuleId("CUSTOMER_EXCLUDE_DATE_HIT");
        rule.setVersion(1);
        rule.setTitle("命中客户排除日期");
        rule.setDescription("客户档案配置了目标日期和餐次不配送。");

        RuleRegistry registry = new RuleRegistry();
        registry.setVersionDigest("digest-1");
        registry.setRules(List.of(rule));
        return registry;
    }

    private AgentToolRegistry toolRegistry() {
        return new AgentToolRegistry(new DiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getCustomerProfile(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest request) {
                return Map.of();
            }

            @Override
            public List<Map<String, Object>> listCustomerOrders(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest request) {
                return List.of();
            }

            @Override
            public Map<String, Object> getMealPlan(me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest request) {
                return Map.of();
            }

            @Override
            public List<Map<String, Object>> getCandidateDishStats(me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest request) {
                return List.of();
            }
        });
    }

    private static DiagnosisResponse response() {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("CUSTOMER_EXCLUDE_DATE_HIT");
        reason.setTitle("命中客户排除日期");
        reason.setLevel("HIGH");
        reason.setDescription("目标日期不配送。");
        reason.setSuggestion("请人工核对排除日期。");
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("excludeDates", "2026-05-17")));

        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("命中客户排除日期。");
        response.setReasons(List.of(reason));
        return response;
    }

    private static class RecordingChatClient {

        private String userPrompt;
        private Object[] tools;

        private ChatClient.Builder builder() {
            return proxy(ChatClient.Builder.class, (proxy, method, args) -> {
                if ("build".equals(method.getName())) {
                    return chatClient();
                }
                if ("clone".equals(method.getName())) {
                    return proxy;
                }
                return proxy;
            });
        }

        private ChatClient chatClient() {
            return proxy(ChatClient.class, (proxy, method, args) -> {
                if ("prompt".equals(method.getName()) && (args == null || args.length == 0)) {
                    return requestSpec();
                }
                if ("mutate".equals(method.getName())) {
                    return builder();
                }
                return defaultValue(method.getReturnType());
            });
        }

        private ChatClient.ChatClientRequestSpec requestSpec() {
            return proxy(ChatClient.ChatClientRequestSpec.class, (proxy, method, args) -> {
                if ("tools".equals(method.getName())) {
                    tools = args == null ? null : (Object[]) args[0];
                    return proxy;
                }
                if ("user".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof String) {
                    userPrompt = (String) args[0];
                    return proxy;
                }
                if ("call".equals(method.getName())) {
                    return callResponseSpec();
                }
                if ("mutate".equals(method.getName())) {
                    return builder();
                }
                return proxy;
            });
        }

        private ChatClient.CallResponseSpec callResponseSpec() {
            return proxy(ChatClient.CallResponseSpec.class, (proxy, method, args) -> {
                if ("entity".equals(method.getName()) && args != null && args.length == 1 && args[0] == DiagnosisResponse.class) {
                    return response();
                }
                return defaultValue(method.getReturnType());
            });
        }

        private String userPrompt() {
            return userPrompt == null ? "" : userPrompt;
        }

        private boolean toolsRegistered() {
            return tools != null && tools.length > 0;
        }

        private boolean toolsRegisteredWith(Object toolRegistry) {
            return toolsRegistered() && tools[0] == toolRegistry;
        }

        @SuppressWarnings("unchecked")
        private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class || returnType == short.class || returnType == int.class || returnType == long.class) {
                return 0;
            }
            if (returnType == float.class || returnType == double.class) {
                return 0.0;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
