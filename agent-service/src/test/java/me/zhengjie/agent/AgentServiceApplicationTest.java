package me.zhengjie.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.infrastructure.llm.OpenAiCompatibleProviderModelGateway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "spring.ai.deepseek.api-key=unused",
    "agent.models.providers.openai-compatible.enabled=false",
    "agent.internal-token=test-internal-token"
})
class AgentServiceApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private OpenAiCompatibleProviderModelGateway openAiCompatibleProvider;

    @Test
    void shouldCreateApplicationInstance() {
        assertDoesNotThrow(AgentServiceApplication::new);
    }

    @Test
    void shouldStartContextWithDeepSeekChatAndDisabledFallbackProvider() {
        assertNotNull(applicationContext);
        assertInstanceOf(DeepSeekChatModel.class, chatModel);
        assertNotNull(chatClientBuilder);
        assertFalse(openAiCompatibleProvider.isConfigured());
    }

    @Test
    void shouldUseJackson2HttpConverterAndRejectUnknownRequestFields() {
        assertTrue(handlerAdapter.getMessageConverters().stream()
            .anyMatch(this::isJackson2Converter));

        assertThrows(JsonProcessingException.class, () -> objectMapper.readValue(
            "{\"message\":\"查询订单\",\"unknownField\":true}", AgentChatRequest.class));
    }

    private boolean isJackson2Converter(HttpMessageConverter<?> converter) {
        return converter instanceof MappingJackson2HttpMessageConverter;
    }
}
