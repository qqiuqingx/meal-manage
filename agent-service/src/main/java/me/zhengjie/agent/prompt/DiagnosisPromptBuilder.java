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
    private final DiagnosisPromptPolicyLoader policyLoader;

    /**
     * 复用 Spring 容器里的 ObjectMapper，保证提示词里的上下文 JSON 和业务序列化配置一致。
     */
    public DiagnosisPromptBuilder(ObjectMapper objectMapper) {
        this(objectMapper, new DiagnosisPromptPolicyLoader());
    }

    public DiagnosisPromptBuilder(ObjectMapper objectMapper, DiagnosisPromptPolicyLoader policyLoader) {
        this.objectMapper = objectMapper;
        this.policyLoader = policyLoader;
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
        DiagnosisPromptPolicy policy = policyLoader.load();
        List<DiagnosisRule> diagnosisRules = registry == null || registry.getRules() == null ? List.of() : registry.getRules();
        String rules = diagnosisRules.stream()
            .map(this::formatRule)
            .collect(Collectors.joining("\n"));

        return """
            你是%s，只能基于用户请求、规则和工具返回的业务数据进行分析。
            Prompt Policy Version：%s
            请输出 JSON，结构必须严格映射到 DiagnosisResponse。
            输出至少必须包含字段：%s。
            AI 诊断结果仅作为建议，需要客服结合证据人工确认。
            禁止表述：%s。
            如果证据不足，必须先调用可用工具查询，再下结论；工具调用预算最多 %s 次。
            在得出最终结论前，至少完成这些关键工具查询：%s。
            如果工具无数据、超预算或失败，请返回 fallback=true，并明确写出 fallbackReason，不允许猜测。
            请明确输出 confidence 和 nextActions。
            reasons 至少包含 1 条；每条 reason 必须包含 code、title、level、description、suggestion、evidence。
            每条 reason 至少包含 %s 条 evidence。
            每条 reason.ruleIds 必须是 JSON 字符串数组，并包含当前 reason 引用的规则 ruleId；是否强制要求：%s。
            fieldReference 不是输出字段，不得输出；字段可追溯性通过 evidence.label 实现，是否强制要求：%s。
            evidence 必须是对象数组，每个对象包含 label 和 value。
            evidence.label 必须逐字使用当前 reason 引用规则的 evidenceFields，禁止翻译、改写或新增标签。
            一旦已有工具证据足以确认至少一个登记原因，应立即停止调用工具并输出结论，禁止为了穷举所有可能原因而调用全部工具。
            actionDrafts 由系统模板生成，模型不得声明已执行任何草稿动作。

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
            - getCustomerExcludeDates：查询客户停送、排除日期和排除餐次。
            - getOrderMealBalance：查询订单有效期、餐次类型和早餐/午晚餐剩余餐数。
            - getPackageSpec：查询父套餐、子套餐和餐品规格。
            - getDishCandidateDetail：查询候选菜池、套餐过滤和过敏忌口过滤明细。
            - listVerificationLogs：查询客户或订单核销记录。
            - listMealRefunds：查询客户或订单退餐、停餐、退款记录。
            - getMealPlanGenerationSnapshot：查询排餐生成快照、失败原因和失败客户摘要。

            规则版本：%s
            规则列表：
            %s
            """.formatted(
            policy.getRole(),
            policy.getVersion(),
            String.join("、", policy.getOutputContract().getRequiredFields()),
            String.join("、", policy.getForbiddenClaims()),
            policy.getToolPolicy().getMaxToolCalls(),
            String.join("、", policy.getToolPolicy().getRequiredBeforeConclusion()),
            policy.getEvidencePolicy().getMinEvidencePerReason(),
            policy.getEvidencePolicy().isRequireRuleIds(),
            policy.getEvidencePolicy().isRequireFieldReference(),
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
            请输出 JSON，结构必须严格映射到 DiagnosisResponse，字段只能使用 summary、reasons、modelName、fallback、ruleVersionDigest、customerId、customerName、recordDate、mealType、requestId、nextActions、confidence。
            AI 诊断结果仅作为建议，需要客服结合证据人工确认。
            不允许声称已经修复、已经修改数据库或已经创建客户。
            如果业务上下文证据不足，请返回需要人工核对，不允许猜测。
            reasons 至少包含 1 条；每条 reason 必须包含 code、title、level、description、suggestion、evidence。
            evidence 必须是对象数组，每个对象包含 label 和 value。
            evidence.label 必须逐字使用当前 reason 引用规则的 evidenceFields，禁止翻译、改写或新增标签。

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
            return "- ruleId: null, reasonCode: null, version: null, title: null, description: null";
        }
        return "- ruleId: %s, reasonCode: %s, version: %s, title: %s, description: %s, requiredTools: %s, evidenceFields: %s, nextActions: %s"
            .formatted(rule.getRuleId(), rule.getReasonCode(), rule.getVersion(), rule.getTitle(), rule.getDescription(),
                rule.getRequiredTools(), rule.getEvidenceFields(), rule.getNextActions());
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
