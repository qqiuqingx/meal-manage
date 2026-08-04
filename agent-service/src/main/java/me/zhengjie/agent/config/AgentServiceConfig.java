package me.zhengjie.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolInputGuardrail;
import me.zhengjie.agent.guardrail.ToolOutputGuardrail;
import me.zhengjie.agent.guardrail.FinalAnswerGuardrail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentServiceConfig {

    /** 创建统一敏感字段策略，工具结果和最终回答共享同一套红线。 */
    @Bean
    public SensitiveDataPolicy sensitiveDataPolicy() { return new SensitiveDataPolicy(); }

    /** 创建工具输入护栏，未知字段由 Jackson 严格模式拒绝。 */
    @Bean
    public ToolInputGuardrail toolInputGuardrail(ObjectMapper objectMapper, SensitiveDataPolicy policy) {
        return new ToolInputGuardrail(objectMapper, policy);
    }

    /** 创建工具输出护栏，主系统响应进入模型前必须经过字段级检查。 */
    @Bean
    public ToolOutputGuardrail toolOutputGuardrail(ObjectMapper objectMapper, SensitiveDataPolicy policy) {
        return new ToolOutputGuardrail(objectMapper, policy);
    }

    /** 创建最终回答护栏，确保实时事实可追溯且不声称执行写操作。 */
    @Bean
    public FinalAnswerGuardrail finalAnswerGuardrail(SensitiveDataPolicy policy) {
        return new FinalAnswerGuardrail(policy);
    }

}
