package me.zhengjie.agent.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.RuleRegistry;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 组装给大模型的诊断提示词。
 */
public class DiagnosisPromptBuilder {

    private final ObjectMapper objectMapper;

    /**
     * 复用 Spring 容器里的 ObjectMapper，保证提示词里的上下文 JSON 和业务序列化配置一致。
     */
    public DiagnosisPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DiagnosisPromptBuilder() {
        this(new ObjectMapper());
    }

    public String build(DiagnosisContextDto context, RuleRegistry registry) {
        return buildToolPrompt(context, registry);
    }

    /**
     * 工具模式下只注入最小请求信息和规则，把上下文查询交给模型按需触发工具。
     */
    public String buildToolPrompt(DiagnosisContextDto context, RuleRegistry registry) {
        List<DiagnosisRule> diagnosisRules = registry == null || registry.getRules() == null ? List.of() : registry.getRules();
        String rules = diagnosisRules.stream()
            .map(this::formatRule)
            .collect(Collectors.joining("\n"));

        return """
            你是排餐未生成原因诊断助手，只能基于用户请求、规则和工具返回的业务数据进行分析。
            请输出 JSON，结构必须严格映射到 DiagnosisResponse，字段只能使用 summary、reasons、modelName、fallback、ruleVersionDigest、customerId、customerName、recordDate、mealType、requestId。
            AI 诊断结果仅作为建议，需要客服结合证据人工确认。
            不允许声称已经修复、已经修改数据库或已经创建客户。
            如果证据不足，必须调用可用工具查询；如果工具无数据或失败，请返回需要人工核对，不允许猜测。
            reasons 至少包含 1 条；每条 reason 必须包含 code、title、level、description、suggestion、evidence。
            evidence 必须是对象数组，每个对象包含 label 和 value。

            用户问题：
            客户ID：%s
            客户编号：%s
            诊断日期：%s
            餐次：%s

            可用工具：
            - getCustomerProfile：查询客户档案，用于判断客户状态、排除日期、配送要求等。
            - listCustomerOrders：查询客户订单，用于判断订单有效性、剩余餐数、套餐等。
            - getMealPlan：查询指定日期餐次的排餐详情，用于判断排餐是否生成和失败原因。
            - getCandidateDishStats：查询指定日期候选菜统计，用于判断候选菜过滤后数量。

            规则版本：%s
            规则列表：
            %s
            """.formatted(
            context == null ? null : context.getCustomerId(),
            context == null ? null : context.getCustomerCode(),
            context == null ? null : context.getRecordDate(),
            context == null ? null : context.getMealType(),
            registry == null ? null : registry.getVersionDigest(),
            rules
        );
    }

    /**
     * 兼容旧模式时直接把完整上下文 JSON 放进提示词，保持历史行为不变。
     */
    public String buildLegacyPrompt(DiagnosisContextDto context, RuleRegistry registry) {
        List<DiagnosisRule> diagnosisRules = registry == null || registry.getRules() == null ? List.of() : registry.getRules();
        String rules = diagnosisRules.stream()
            .map(this::formatRule)
            .collect(Collectors.joining("\n"));

        return """
            你是排餐未生成原因诊断助手，请基于用户请求、业务上下文 JSON 和规则进行分析。
            请输出 JSON，结构必须严格映射到 DiagnosisResponse，字段只能使用 summary、reasons、modelName、fallback、ruleVersionDigest、customerId、customerName、recordDate、mealType、requestId。
            AI 诊断结果仅作为建议，需要客服结合证据人工确认。
            不允许声称已经修复、已经修改数据库或已经创建客户。
            如果业务上下文证据不足，请返回需要人工核对，不允许猜测。
            reasons 至少包含 1 条；每条 reason 必须包含 code、title、level、description、suggestion、evidence。
            evidence 必须是对象数组，每个对象包含 label 和 value。

            用户问题：
            客户ID：%s
            客户编号：%s
            客户名称：%s
            诊断日期：%s
            餐次：%s

            业务上下文 JSON：
            %s

            规则版本：%s
            规则列表：
            %s
            """.formatted(
            context == null ? null : context.getCustomerId(),
            context == null ? null : context.getCustomerCode(),
            context == null ? null : context.getCustomerName(),
            context == null ? null : context.getRecordDate(),
            context == null ? null : context.getMealType(),
            serializeContext(context),
            registry == null ? null : registry.getVersionDigest(),
            rules
        );
    }

    /**
     * 规则文件里允许出现空项，提示词层统一兜底，避免拼接时直接抛空指针。
     */
    private String formatRule(DiagnosisRule rule) {
        if (rule == null) {
            return "- ruleId: null, version: null, title: null, description: null";
        }
        return "- ruleId: %s, version: %s, title: %s, description: %s"
            .formatted(rule.getRuleId(), rule.getVersion(), rule.getTitle(), rule.getDescription());
    }

    /**
     * 旧模式依赖上下文 JSON 直接入模，序列化失败时显式抛错，避免 silent fallback 掩盖问题。
     */
    private String serializeContext(DiagnosisContextDto context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize diagnosis context", ex);
        }
    }
}
