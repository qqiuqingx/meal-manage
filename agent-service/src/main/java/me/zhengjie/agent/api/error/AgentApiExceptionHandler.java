package me.zhengjie.agent.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.UUID;

/** 将 v2 API 的参数和服务错误统一映射为无敏感信息的稳定协议。 */
@RestControllerAdvice(basePackages = "me.zhengjie.agent.api")
public class AgentApiExceptionHandler {

    /** 返回字段级参数错误。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AgentApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数不符合契约要求。", false, request, details);
    }

    /** 返回明确的契约版本不匹配错误，调用方不应重试相同请求。 */
    @ExceptionHandler(AgentContractException.class)
    public ResponseEntity<AgentApiError> contract(AgentContractException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "CONTRACT_VERSION_MISMATCH", "当前服务不支持该契约版本。", false, request, new LinkedHashMap<>());
    }

    /** 返回不暴露实现细节的通用服务错误。 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AgentApiError> runtime(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "AGENT_PROCESSING_FAILED", "Agent 服务暂时无法处理请求。", true, request, new LinkedHashMap<>());
    }

    private ResponseEntity<AgentApiError> response(HttpStatus status, String code, String message, boolean retryable,
                                                    HttpServletRequest request, LinkedHashMap<String, String> details) {
        AgentApiError body = new AgentApiError();
        body.setCode(code);
        body.setMessage(message);
        body.setRetryable(retryable);
        body.setDetails(details);
        String requestId = request.getHeader("X-Request-Id");
        body.setRequestId(requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim());
        return ResponseEntity.status(status).body(body);
    }
}
