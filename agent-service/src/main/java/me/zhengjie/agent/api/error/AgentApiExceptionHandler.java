package me.zhengjie.agent.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.client.RestClientException;

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

    /** 缺少可信访问上下文等必需请求头时返回统一参数错误。 */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<AgentApiError> requestBinding(ServletRequestBindingException exception,
                                                        HttpServletRequest request) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("header", "缺少必需的内部调用请求头。");
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数不符合契约要求。",
            false, request, details);
    }

    /** 返回明确的契约版本不匹配错误，调用方不应重试相同请求。 */
    @ExceptionHandler(AgentContractException.class)
    public ResponseEntity<AgentApiError> contract(AgentContractException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "CONTRACT_VERSION_MISMATCH", "当前服务不支持该契约版本。", false, request, new LinkedHashMap<>());
    }

    /** 返回不暴露实现细节的通用服务错误。 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AgentApiError> runtime(RuntimeException exception, HttpServletRequest request) {
        ErrorMapping mapping = classify(exception);
        return response(mapping.status(), mapping.code(), mapping.message(), mapping.retryable(),
            request, new LinkedHashMap<>());
    }

    private ErrorMapping classify(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (exception instanceof SecurityException || message.startsWith("PERMISSION_DENIED")) {
            return new ErrorMapping(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "当前访问上下文无权执行该能力。", false);
        }
        if (message.startsWith("CAPABILITY_NOT_AVAILABLE")) {
            return new ErrorMapping(HttpStatus.UNPROCESSABLE_ENTITY, "CAPABILITY_NOT_AVAILABLE", "当前服务未登记该能力。", false);
        }
        if (message.startsWith("TOOL_NOT_AVAILABLE") || message.startsWith("unsupported tool")) {
            return new ErrorMapping(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_NOT_AVAILABLE", "当前能力所需工具不可用。", false);
        }
        if (message.startsWith("SESSION_VERSION_CONFLICT")) {
            return new ErrorMapping(HttpStatus.CONFLICT, "SESSION_VERSION_CONFLICT", "会话已被其他请求更新，请刷新后重试。", true);
        }
        if (message.startsWith("MODEL_CAPABILITY_UNSUPPORTED")) {
            return new ErrorMapping(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CAPABILITY_UNSUPPORTED", "当前模型不支持该任务所需能力。", false);
        }
        if (message.startsWith("MODEL_PROFILE_NOT_AVAILABLE") || message.startsWith("MODEL_UNAVAILABLE")) {
            return new ErrorMapping(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "当前模型服务不可用。", true);
        }
        if (exception instanceof RestClientException) {
            return new ErrorMapping(HttpStatus.BAD_GATEWAY, "DEPENDENCY_UNAVAILABLE", "内部依赖暂时不可用。", true);
        }
        return new ErrorMapping(HttpStatus.INTERNAL_SERVER_ERROR, "AGENT_PROCESSING_FAILED",
            "Agent 服务暂时无法处理请求。", true);
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

    private record ErrorMapping(HttpStatus status, String code, String message, boolean retryable) { }
}
