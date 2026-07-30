package me.zhengjie.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** 从唯一的 YAML 主题目录加载规则别名和正则，新增规则不需要修改会话编排器。 */
public class ResourceAgentBusinessRuleTopicResolver implements AgentBusinessRuleTopicResolver {
    private static final String RESOURCE = "config/agent-rule-topics.yaml";
    private final List<Entry> entries;

    /** 从 classpath 加载受控主题目录；目录错误应在应用启动前暴露。 */
    public ResourceAgentBusinessRuleTopicResolver() {
        this.entries = load();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<String> resolve(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) return Optional.empty();
        for (Entry entry : entries) {
            if (entry.matches(text)) return Optional.of(entry.topic);
        }
        return Optional.empty();
    }

    /** 读取并校验路由目录，规则 ID、主题、别名和正则均不得为空或重复。 */
    private List<Entry> load() {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Agent rule topic resource is missing: " + RESOURCE);
            DefinitionRoot root = new ObjectMapper(new YAMLFactory()).readValue(input, DefinitionRoot.class);
            if (root == null || root.rules == null || root.rules.isEmpty()) throw new IllegalStateException("Agent rule topic registry cannot be empty");
            List<Entry> result = new ArrayList<>();
            for (Definition definition : root.rules) result.add(Entry.from(definition));
            result.sort(Comparator.comparingInt((Entry item) -> item.priority).reversed());
            return List.copyOf(result);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load Agent rule topic registry", exception);
        }
    }

    /** YAML 根节点。 */
    public static class DefinitionRoot { public List<Definition> rules = new ArrayList<>(); }
    /** YAML 中的一条受控规则路由定义。 */
    public static class Definition {
        public String ruleId;
        public String topic;
        public Integer priority;
        public List<String> aliases = new ArrayList<>();
        public List<String> patterns = new ArrayList<>();
    }

    /** 完成正则编译后的不可变路由条目。 */
    private static final class Entry {
        private final String topic;
        private final int priority;
        private final List<String> aliases;
        private final List<Pattern> patterns;
        private Entry(String topic, int priority, List<String> aliases, List<Pattern> patterns) {
            this.topic = topic; this.priority = priority; this.aliases = aliases; this.patterns = patterns;
        }
        private static Entry from(Definition definition) {
            if (definition == null || blank(definition.ruleId) || blank(definition.topic)
                || definition.priority == null || (definition.aliases == null || definition.aliases.isEmpty())
                && (definition.patterns == null || definition.patterns.isEmpty())) {
                throw new IllegalStateException("Agent rule topic definition contains required empty field");
            }
            List<String> aliases = new ArrayList<>();
            for (String alias : definition.aliases == null ? List.<String>of() : definition.aliases) {
                if (blank(alias)) throw new IllegalStateException("Agent rule topic alias cannot be blank");
                aliases.add(alias.trim().toUpperCase(Locale.ROOT));
            }
            List<Pattern> patterns = new ArrayList<>();
            for (String expression : definition.patterns == null ? List.<String>of() : definition.patterns) {
                if (blank(expression)) throw new IllegalStateException("Agent rule topic pattern cannot be blank");
                patterns.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
            }
            return new Entry(definition.topic.trim().toUpperCase(Locale.ROOT), definition.priority, List.copyOf(aliases), List.copyOf(patterns));
        }
        private boolean matches(String text) {
            String normalized = text.toUpperCase(Locale.ROOT);
            return aliases.stream().anyMatch(normalized::contains) || patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
        }
        private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    }
}
