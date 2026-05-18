package me.zhengjie.agent.rule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * 从本地 rules 目录加载 AI 诊断规则。
 */
@Component
public class FileSystemRuleRegistryLoader implements RuleRegistryLoader {

    private final Path ruleBasePath;

    @Autowired
    public FileSystemRuleRegistryLoader(@Value("${agent.rules.base-path:rules}") String ruleBasePath) {
        this(Path.of(ruleBasePath));
    }

    public FileSystemRuleRegistryLoader(Path ruleBasePath) {
        this.ruleBasePath = ruleBasePath;
    }

    @Override
    public RuleRegistry load(String scene) {
        Path scenePath = ruleBasePath.resolve("meal-plan");
        if (!Files.isDirectory(scenePath)) {
            return loadFromClasspath(scene, scenePath);
        }

        return loadFromFileSystem(scene, scenePath);
    }

    private RuleRegistry loadFromFileSystem(String scene, Path scenePath) {
        List<DiagnosisRule> rules = new ArrayList<>();
        StringBuilder digestSource = new StringBuilder(scene);

        try (Stream<Path> paths = Files.list(scenePath)) {
            List<Path> yamlFiles = paths
                .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();

            for (Path yamlFile : yamlFiles) {
                String content = Files.readString(yamlFile, StandardCharsets.UTF_8);
                digestSource.append(content);
                rules.addAll(parseRules(content, scene));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load rule registry from " + scenePath, ex);
        }

        RuleRegistry registry = new RuleRegistry();
        registry.setScene(scene);
        registry.setVersionDigest(sha256(digestSource.toString()));
        registry.setRules(rules);
        return registry;
    }

    private RuleRegistry loadFromClasspath(String scene, Path scenePath) {
        List<DiagnosisRule> rules = new ArrayList<>();
        StringBuilder digestSource = new StringBuilder(scene);
        String[] resourceNames = {
            "customer.yaml",
            "dish-candidate.yaml",
            "generated-result.yaml",
            "order.yaml",
            "schedule.yaml"
        };
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        for (String resourceName : resourceNames) {
            String resourcePath = "rules/meal-plan/" + resourceName;
            try (var inputStream = classLoader.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    continue;
                }
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                digestSource.append(content);
                rules.addAll(parseRules(content, scene));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load rule registry resource " + resourcePath, ex);
            }
        }

        if (rules.isEmpty()) {
            throw new IllegalStateException("Failed to load rule registry from " + scenePath
                + " or classpath rules/meal-plan");
        }

        RuleRegistry registry = new RuleRegistry();
        registry.setScene(scene);
        registry.setVersionDigest(sha256(digestSource.toString()));
        registry.setRules(rules);
        return registry;
    }

    private List<DiagnosisRule> parseRules(String content, String defaultScene) {
        List<DiagnosisRule> rules = new ArrayList<>();
        DiagnosisRule current = null;
        String currentList = null;

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("- ruleId:")) {
                if (current != null) {
                    rules.add(current);
                }
                current = new DiagnosisRule();
                current.setScene(defaultScene);
                current.setRuleId(valueAfterColon(trimmed));
                currentList = null;
                continue;
            }
            if (current == null) {
                continue;
            }
            if (trimmed.endsWith(":")) {
                currentList = trimmed.substring(0, trimmed.length() - 1);
                continue;
            }
            if (trimmed.startsWith("- ")) {
                addListValue(current, currentList, trimmed.substring(2).trim());
                continue;
            }
            currentList = null;
            setScalarValue(current, trimmed);
        }

        if (current != null) {
            rules.add(current);
        }
        return rules;
    }

    private void setScalarValue(DiagnosisRule rule, String line) {
        String key = line.substring(0, line.indexOf(':')).trim();
        String value = valueAfterColon(line);
        switch (key) {
            case "version" -> rule.setVersion(Integer.valueOf(value));
            case "scene" -> rule.setScene(value);
            case "title" -> rule.setTitle(value);
            case "description" -> rule.setDescription(value);
            case "severity" -> rule.setSeverity(value);
            case "owner" -> rule.setOwner(value);
            default -> {
            }
        }
    }

    private void addListValue(DiagnosisRule rule, String listName, String value) {
        if ("requiredData".equals(listName)) {
            rule.getRequiredData().add(value);
        } else if ("decisionHints".equals(listName)) {
            rule.getDecisionHints().add(value);
        } else if ("evidenceFields".equals(listName)) {
            rule.getEvidenceFields().add(value);
        }
    }

    private String valueAfterColon(String line) {
        return line.substring(line.indexOf(':') + 1).trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
