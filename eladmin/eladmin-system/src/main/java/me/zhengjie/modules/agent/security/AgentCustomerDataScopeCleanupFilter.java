package me.zhengjie.modules.agent.security;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 在内部 Agent 请求结束后释放客户数据范围，防止线程复用导致越权。 */
@Component
public class AgentCustomerDataScopeCleanupFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request == null || request.getRequestURI() == null || !request.getRequestURI().startsWith("/api/internal/agent/");
    }

    /** 清理在控制器权限校验阶段绑定的当前客服客户范围。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            AgentCustomerDataScopeContext.clear();
        }
    }
}
