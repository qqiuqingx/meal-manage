package me.zhengjie.modules.agent.query;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.modules.agent.query.domain.AgentBusinessRuleDefinition;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 加载并校验客服可解释业务规则目录，禁止接口直接使用 Java 硬编码文案。 */
@Component
public class AgentBusinessRuleRegistry {
    private static final String RESOURCE = "config/agent-business-rules.json";
    private volatile Map<String, AgentBusinessRuleDefinition> rulesByTopic = Collections.emptyMap();

    /** 在服务启动时加载目录；不合法规则必须阻止服务以不完整规则集启动。 */
    @PostConstruct
    public void initialize() {
        this.rulesByTopic = Collections.unmodifiableMap(load());
    }

    /**
     * 按登记主题查询规则，主题不在目录中时返回 null。
     *
     * @param topic 客服问题映射出的受控主题
     * @return 已校验的规则定义，或 null
     */
    public AgentBusinessRuleDefinition find(String topic) {
        return rulesByTopic.get(normalize(topic));
    }

    /** 读取资源并校验字段、主题唯一性和安全展示边界。 */
    private Map<String, AgentBusinessRuleDefinition> load() {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Agent business rule resource is missing: " + RESOURCE);
            List<AgentBusinessRuleDefinition> definitions = JSON.parseArray(new String(input.readAllBytes(), StandardCharsets.UTF_8), AgentBusinessRuleDefinition.class);
            if (definitions == null || definitions.isEmpty()) throw new IllegalStateException("Agent business rule registry cannot be empty");
            Map<String, AgentBusinessRuleDefinition> result = new LinkedHashMap<>();
            for (AgentBusinessRuleDefinition definition : definitions) register(result, definition);
            return result;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load Agent business rule registry", exception);
        }
    }

    /** 校验一条规则并注册全部主题别名。 */
    private void register(Map<String, AgentBusinessRuleDefinition> target, AgentBusinessRuleDefinition definition) {
        if (definition == null || blank(definition.getRuleId()) || blank(definition.getVersion()) || blank(definition.getTitle())
            || blank(definition.getContent()) || blank(definition.getOwnerModule()) || blank(definition.getEvidenceDocument())
            || blank(definition.getUpdatedAt()) || definition.getTopics() == null || definition.getTopics().isEmpty()) {
            throw new IllegalStateException("Agent business rule contains required empty field");
        }
        if (!definition.getEvidenceDocument().startsWith("doc/")) {
            throw new IllegalStateException("Agent business rule evidence document must be a doc/ path");
        }
        if (containsForbiddenAmountTerm(definition.getContent())) {
            throw new IllegalStateException("Agent business rule cannot expose amount semantics");
        }
        for (String topic : definition.getTopics()) {
            String key = normalize(topic);
            if (blank(key) || target.putIfAbsent(key, definition) != null) {
                throw new IllegalStateException("Agent business rule topic must be unique");
            }
        }
    }

    /** 将英文主题统一为大写，中文主题保持原文以支持受控同义问法。 */
    private String normalize(String topic) { return topic == null ? "" : topic.trim().toUpperCase(Locale.ROOT); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private boolean containsForbiddenAmountTerm(String content) {
        return content.contains("订单金额") || content.contains("退款金额") || content.contains("单价") || content.contains("优惠金额") || content.contains("已收金额");
    }
}
