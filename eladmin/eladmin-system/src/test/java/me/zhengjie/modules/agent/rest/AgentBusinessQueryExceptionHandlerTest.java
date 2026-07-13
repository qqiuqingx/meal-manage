package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.rest.dto.AgentBusinessQueryErrorDto;
import me.zhengjie.modules.agent.rest.exception.AgentBusinessQueryNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 验证内部业务查询异常统一返回稳定且不泄露实现细节的错误契约。 */
class AgentBusinessQueryExceptionHandlerTest {

    private final AgentBusinessQueryExceptionHandler handler = new AgentBusinessQueryExceptionHandler();

    @Test
    void shouldReturnStableCodesForRequestNotFoundPermissionAndInternalFailures() {
        assertError(handler.invalidRequest(new IllegalArgumentException("raw SQL must not leak")), HttpStatus.BAD_REQUEST, "AGENT_QUERY_INVALID_REQUEST");
        assertError(handler.notFound(new AgentBusinessQueryNotFoundException()), HttpStatus.NOT_FOUND, "AGENT_QUERY_NOT_FOUND");
        assertError(handler.status(new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid signature")), HttpStatus.FORBIDDEN, "AGENT_QUERY_ACCESS_DENIED");
        ResponseEntity<AgentBusinessQueryErrorDto> internal = handler.unexpected(new RuntimeException("database password=value"));
        assertError(internal, HttpStatus.INTERNAL_SERVER_ERROR, "AGENT_QUERY_INTERNAL_ERROR");
        assertFalse(internal.getBody().getMessage().contains("password"));
    }

    /** 统一断言状态和错误码，避免测试依赖内部异常文本。 */
    private void assertError(ResponseEntity<AgentBusinessQueryErrorDto> response, HttpStatus status, String code) {
        assertEquals(status, response.getStatusCode());
        assertEquals(code, response.getBody().getCode());
    }
}
