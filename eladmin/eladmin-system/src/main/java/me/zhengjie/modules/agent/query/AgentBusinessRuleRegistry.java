package me.zhengjie.modules.agent.query;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.modules.agent.query.domain.AgentBusinessRuleDefinition;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
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
            List<AgentBusinessRuleDefinition> definitions = JSON.parseArray(readText(input), AgentBusinessRuleDefinition.class);
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
            || blank(definition.getEvidenceAnchor()) || blank(definition.getEvidenceHash()) || blank(definition.getEffectiveFrom())
            || blank(definition.getUpdatedAt()) || definition.getTopics() == null || definition.getTopics().isEmpty()) {
            throw new IllegalStateException("Agent business rule contains required empty field");
        }
        if (!"EFFECTIVE".equals(definition.getStatus()) && !"DEPRECATED".equals(definition.getStatus())) {
            throw new IllegalStateException("Agent business rule status must be EFFECTIVE or DEPRECATED");
        }
        if (!definition.getEvidenceDocument().startsWith("doc/business/") || !definition.getEvidenceDocument().endsWith(".md")) {
            throw new IllegalStateException("Agent business rule evidence document must be a doc/business Markdown path");
        }
        if (containsForbiddenAmountTerm(definition.getContent())) {
            throw new IllegalStateException("Agent business rule cannot expose amount semantics");
        }
        validateEvidence(definition);
        if (!"EFFECTIVE".equals(definition.getStatus())) return;
        for (String topic : definition.getTopics()) {
            String key = normalize(topic);
            if (blank(key) || target.putIfAbsent(key, definition) != null) {
                throw new IllegalStateException("Agent business rule topic must be unique");
            }
        }
    }

    /** 校验随应用打包的业务依据，防止文档变更后仍以陈旧规则对外解释。 */
    private void validateEvidence(AgentBusinessRuleDefinition definition) {
        try (InputStream evidence = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(definition.getEvidenceDocument())) {
            if (evidence == null) throw new IllegalStateException("Agent business rule evidence document is missing: " + definition.getEvidenceDocument());
            String markdown = readText(evidence);
            if (countAnchor(markdown, definition.getEvidenceAnchor()) != 1) {
                throw new IllegalStateException("Agent business rule evidence anchor must occur exactly once: " + definition.getEvidenceAnchor());
            }
            if (!("sha256:" + sha256(markdown)).equalsIgnoreCase(definition.getEvidenceHash())) {
                throw new IllegalStateException("Agent business rule evidence hash does not match document: " + definition.getRuleId());
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot validate Agent business rule evidence", exception);
        }
    }

    /** Markdown 标题必须精确匹配登记锚点，避免同名章节导致规则依据不确定。 */
    private int countAnchor(String markdown, String anchor) {
        String expected = anchor == null ? "" : anchor.trim().replaceFirst("^#+\\s*", "");
        int count = 0;
        for (String line : markdown.split("\\R")) {
            if (line.matches("^#{1,6}\\s+.*") && line.replaceFirst("^#{1,6}\\s+", "").trim().equals(expected)) count++;
        }
        return count;
    }

    /** 返回小写十六进制 SHA-256，目录值统一以 sha256: 前缀登记。 */
    private String sha256(String content) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    /** Java 8 兼容地读取 classpath 资源，避免规则校验依赖运行时高版本 JDK API。 */
    private String readText(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) >= 0) output.write(buffer, 0, length);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 将英文主题统一为大写，中文主题保持原文以支持受控同义问法。 */
    private String normalize(String topic) { return topic == null ? "" : topic.trim().toUpperCase(Locale.ROOT); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private boolean containsForbiddenAmountTerm(String content) {
        return content.contains("订单金额") || content.contains("退款金额") || content.contains("单价") || content.contains("优惠金额") || content.contains("已收金额");
    }
}
