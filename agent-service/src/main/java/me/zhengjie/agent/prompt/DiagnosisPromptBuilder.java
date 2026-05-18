package me.zhengjie.agent.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.RuleRegistry;

import java.util.stream.Collectors;

/**
 * 组装给大模型的诊断提示词。
 */
public class DiagnosisPromptBuilder {

    private final ObjectMapper objectMapper;

    public DiagnosisPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(DiagnosisContextDto context, RuleRegistry registry) {
        String rules = registry.getRules().stream()
            .map(this::formatRule)
            .collect(Collectors.joining("\n"));
        String contextJson = toJson(context);

        return """
            你是排餐未生成原因诊断助手，只能基于输入的业务数据和规则进行分析。
            请输出 JSON，结构必须映射到 DiagnosisResponse。
            AI 诊断结果仅作为建议，需要客服结合证据人工确认。
            不允许声称已经修复、已经修改数据库或已经创建客户。

            用户问题：
            客户ID：%s
            客户名称：%s
            诊断日期：%s
            餐次：%s

            规则版本：%s
            规则列表：
            %s

            业务上下文 JSON：
            %s
            """.formatted(
            context.getCustomerId(),
            context.getCustomerName(),
            context.getRecordDate(),
            context.getMealType(),
            registry.getVersionDigest(),
            rules,
            contextJson
        );
    }

    private String formatRule(DiagnosisRule rule) {
        return "- ruleId: %s, version: %s, title: %s, requiredData: %s, description: %s"
            .formatted(rule.getRuleId(), rule.getVersion(), rule.getTitle(), rule.getRequiredData(), rule.getDescription());
    }

    private String toJson(DiagnosisContextDto context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize diagnosis context", ex);
        }
    }
}
