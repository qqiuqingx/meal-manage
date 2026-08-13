package me.zhengjie.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.security.AgentAccessContextHolder;
import me.zhengjie.agent.tool.input.formdraft.CustomerWithOrderDraftInput;
import me.zhengjie.agent.tool.input.formdraft.SaveFormDraftInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 主系统草稿客户端的可信会话与消息上下文组装测试。 */
class HttpMainSystemFormDraftClientTest {

    @AfterEach
    void clearContext() {
        AgentAccessContextHolder.clear();
    }

    /** clientMessageId 和 sourceSessionId 必须来自可信请求线程，不能来自模型 DTO。 */
    @Test
    void shouldInjectTrustedSourceContext() {
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("internal-token");
        HttpMainSystemFormDraftClient client = new HttpMainSystemFormDraftClient(
            RestClient.builder(), properties, new ObjectMapper().findAndRegisterModules());
        SaveFormDraftInput input = new SaveFormDraftInput();
        input.setDraftType("CREATE_CUSTOMER_WITH_ORDER");
        input.setCustomerWithOrderPayload(new CustomerWithOrderDraftInput());
        AgentAccessContextHolder.bind("signed-context", "session-trusted", "message-trusted");

        ObjectNode request = client.buildRequest(input);

        assertEquals("session-trusted", request.path("sourceSessionId").asText());
        assertEquals("message-trusted", request.path("clientMessageId").asText());
        assertFalse(request.has("customerWithOrderPayload"));
        assertEquals(true, request.has("payload"));
    }
}
