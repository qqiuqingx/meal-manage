package me.zhengjie.modules.agent.rest;

import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.rest.dto.AgentBusinessQueryErrorDto;
import me.zhengjie.modules.agent.rest.exception.AgentBusinessQueryNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 仅约束 Agent 内部业务查询接口的稳定错误契约，避免通用异常响应泄露实现细节。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = InternalAgentBusinessQueryController.class)
public class AgentBusinessQueryExceptionHandler {

    /** 处理请求体 Bean Validation 失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AgentBusinessQueryErrorDto> validation(MethodArgumentNotValidException exception) {
        return response(HttpStatus.BAD_REQUEST, "AGENT_QUERY_REQUEST_VALIDATION_FAILED", "查询参数不符合要求");
    }

    /** 处理受控查询条件错误，例如缺少客户、日期范围超限。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AgentBusinessQueryErrorDto> invalidRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "AGENT_QUERY_INVALID_REQUEST", "查询参数不符合要求");
    }

    /** 处理客户上下文内找不到订单等对象不存在场景。 */
    @ExceptionHandler(AgentBusinessQueryNotFoundException.class)
    public ResponseEntity<AgentBusinessQueryErrorDto> notFound(AgentBusinessQueryNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "AGENT_QUERY_NOT_FOUND", "业务对象不存在或无权查看");
    }

    /** 处理内部服务身份、签名上下文及业务权限拒绝。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AgentBusinessQueryErrorDto> status(ResponseStatusException exception) {
        HttpStatus status = exception.getStatus();
        String code = status == HttpStatus.UNAUTHORIZED ? "AGENT_QUERY_UNAUTHORIZED" : "AGENT_QUERY_ACCESS_DENIED";
        return response(status, code, status == HttpStatus.UNAUTHORIZED ? "内部查询身份验证失败" : "当前客服无权执行该查询");
    }

    /** 兜底屏蔽实现层异常、数据库细节和调用栈。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AgentBusinessQueryErrorDto> unexpected(Exception exception) {
        log.warn("Agent internal business query failed: {}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "AGENT_QUERY_INTERNAL_ERROR", "内部查询处理失败");
    }

    private ResponseEntity<AgentBusinessQueryErrorDto> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new AgentBusinessQueryErrorDto(code, message));
    }
}
