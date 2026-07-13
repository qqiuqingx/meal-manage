package me.zhengjie.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.IntentClassificationRequest;
import me.zhengjie.agent.domain.dto.IntentClassificationResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 使用模型完成复杂多轮意图分类。模型只能返回受控意图，不参与诊断和业务查询。
 */
@Component
@ConditionalOnProperty(prefix = "agent.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LlmIntentClassifier implements ChatIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmIntentClassifier.class);
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /** 供单元测试替换分类行为，不创建真实模型客户端。 */
    protected LlmIntentClassifier() {
        this.chatClient = null;
        this.objectMapper = null;
    }

    public LlmIntentClassifier(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 调用模型识别依赖上下文的省略、代词和追问，并校验返回意图枚举。
     *
     * @param request 当前消息及会话上下文
     * @return 受控分类结果；调用或解析失败时返回 fallbackSuggested=true
     */
    @Override
    public IntentClassificationResult classify(IntentClassificationRequest request) {
        try {
            String prompt = buildPrompt(request);
            String content = chatClient.prompt().user(prompt).call().content();
            IntentClassificationResult result = objectMapper.readValue(normalizeJson(content), IntentClassificationResult.class);
            if (result == null || result.getIntent() == null || result.getConfidence() < 0.0 || result.getConfidence() > 1.0) {
                return failed("模型返回了未知意图或非法置信度");
            }
            result.setReason(limit(result.getReason(), 200));
            result.setFallbackSuggested(result.getConfidence() < 0.8);
            return result;
        } catch (Exception ex) {
            log.warn("聊天意图模型分类失败 errorType={} errorMessage={}", ex.getClass().getSimpleName(), ex.getMessage());
            return failed("模型分类失败: " + ex.getClass().getSimpleName());
        }
    }

    private String buildPrompt(IntentClassificationRequest request) throws Exception {
        String context = objectMapper.writeValueAsString(request);
        return "你是排餐客服的意图分类器，只做意图分类，不查库、不诊断、不回答用户。"
            + "必须只输出 JSON，字段为 intent、confidence、reason、fallbackSuggested。"
            + "intent 只能是 DIAGNOSE、FOLLOW_UP、RETRY、RESET、OUT_OF_SCOPE、BUSINESS_QUERY。"
            + "DIAGNOSE 表示查询某客户某日期某餐次的排餐；FOLLOW_UP 表示依赖最近诊断结果的原因、局部追问或改查；"
            + "BUSINESS_QUERY 表示所有只读客户、订单、排餐、核销、退餐、套餐、菜品、规则或运营统计问题；具体领域由后续受控分析决定。"
            + "理解他、这个客户、这个订单、那午餐等指代，并优先使用已有槽位。"
            + "输入上下文：" + context;
    }

    private IntentClassificationResult failed(String reason) {
        return new IntentClassificationResult(null, 0.0, reason, true);
    }

    private String normalizeJson(String content) {
        if (content == null) return "";
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            int lastFence = normalized.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                normalized = normalized.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return normalized;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
