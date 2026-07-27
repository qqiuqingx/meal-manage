package me.zhengjie.agent.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/**
 * 将 HTTP 请求追踪字段一次性写入 MDC，并在请求结束后清理，避免线程复用泄露会话信息。
 * 不读取或记录请求正文、访问令牌、Prompt 与工具原始结果。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentRequestMdcFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        requestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
        try {
            MDC.put("requestId", requestId);
            MDC.put("contractVersion", request.getRequestURI().contains("/v2/") ? "v2" : "v1");
            response.setHeader("X-Request-Id", requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
