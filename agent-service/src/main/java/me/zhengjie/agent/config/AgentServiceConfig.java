package me.zhengjie.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.infrastructure.llm.AgentModelGateway;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolInputGuardrail;
import me.zhengjie.agent.guardrail.ToolOutputGuardrail;
import me.zhengjie.agent.guardrail.FinalAnswerGuardrail;
import me.zhengjie.agent.presentation.PresentationFieldCatalog;
import me.zhengjie.agent.presentation.CardSchemaInspector;
import me.zhengjie.agent.presentation.GenericPresentationFactory;
import me.zhengjie.agent.presentation.LlmPresentationPlanner;
import me.zhengjie.agent.presentation.PresentationPlanner;
import me.zhengjie.agent.presentation.PresentationRegistry;
import me.zhengjie.agent.presentation.PresentationService;
import me.zhengjie.agent.presentation.PresentationSuggestionValidator;
import me.zhengjie.agent.tool.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentServiceConfig {

    /** 创建统一敏感字段策略，工具结果和最终回答共享同一套红线。 */
    @Bean
    public SensitiveDataPolicy sensitiveDataPolicy() { return new SensitiveDataPolicy(); }

    /** 创建只允许草稿登记路径携带手机号和地址的专用策略。 */
    @Bean
    public me.zhengjie.agent.guardrail.FormDraftSensitiveDataPolicy formDraftSensitiveDataPolicy() {
        return new me.zhengjie.agent.guardrail.FormDraftSensitiveDataPolicy();
    }

    /** 创建工具输入护栏，未知字段由 Jackson 严格模式拒绝。 */
    @Bean
    public ToolInputGuardrail toolInputGuardrail(ObjectMapper objectMapper, SensitiveDataPolicy policy,
        me.zhengjie.agent.guardrail.FormDraftSensitiveDataPolicy draftPolicy) {
        return new ToolInputGuardrail(objectMapper, policy, draftPolicy);
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

    /** 创建安全字段目录；展示规则只能引用目录中的路径和字段语义。 */
    @Bean
    public PresentationFieldCatalog presentationFieldCatalog() { return new PresentationFieldCatalog(); }

    /** 创建并在 Spring 启动阶段校验系统展示规则。 */
    @Bean
    public PresentationRegistry presentationRegistry(ToolRegistry toolRegistry, PresentationFieldCatalog catalog) {
        return new PresentationRegistry(toolRegistry, catalog, null);
    }

    /** 创建未知卡片的无工具规划器；缺少 presentation profile 时由服务直接通用降级。 */
    @Bean
    public PresentationPlanner presentationPlanner(AgentModelGateway modelGateway, ObjectMapper objectMapper) {
        return new LlmPresentationPlanner(modelGateway, objectMapper);
    }

    /** 创建展示服务；已知卡片不会经过模型网关，未知卡片才进入独立 planner。 */
    @Bean
    public PresentationService presentationService(PresentationRegistry registry, PresentationPlanner planner) {
        PresentationSuggestionValidator validator = new PresentationSuggestionValidator();
        return new PresentationService(registry, new CardSchemaInspector(), planner, validator,
            new GenericPresentationFactory(validator));
    }

}
