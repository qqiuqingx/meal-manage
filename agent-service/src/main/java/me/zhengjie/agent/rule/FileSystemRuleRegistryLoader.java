package me.zhengjie.agent.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.tool.ToolRegistry;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 加载诊断规则。外部 scene 目录存在时完整覆盖 classpath，避免两种来源混杂。
 */
@Component
public class FileSystemRuleRegistryLoader implements RuleRegistryLoader {

    private static final Map<String, String> DEFAULT_SCENE_DIRECTORIES =
        Map.of("MEAL_PLAN_NOT_GENERATED", "meal-plan");

    private final Path ruleBasePath;
    private final Map<String, String> sceneDirectories;
    private final YAMLMapper yamlMapper;

    @Autowired
    public FileSystemRuleRegistryLoader(AgentProperties properties) {
        this(Path.of(properties.getRules().getBasePath()), properties.getRules().getSceneDirectories());
    }

    /** 使用默认场景目录映射创建规则加载器。 */
    public FileSystemRuleRegistryLoader(Path ruleBasePath) {
        this(ruleBasePath, DEFAULT_SCENE_DIRECTORIES);
    }

    /**
     * 兼容历史测试和独立工具以字符串传入规则根目录的构造方式。
     *
     * @param ruleBasePath 外部规则根目录
     */
    public FileSystemRuleRegistryLoader(String ruleBasePath) {
        this(Path.of(ruleBasePath));
    }

    /** 使用指定外部根目录和场景目录映射创建规则加载器。 */
    public FileSystemRuleRegistryLoader(Path ruleBasePath, Map<String, String> sceneDirectories) {
        this.ruleBasePath = ruleBasePath;
        this.sceneDirectories = sceneDirectories == null ? DEFAULT_SCENE_DIRECTORIES : Map.copyOf(sceneDirectories);
        this.yamlMapper = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    /**
     * 加载指定场景的规则；场景目录由配置映射或规范化场景名确定。
     *
     * @param scene 诊断场景标识
     * @return 已校验且带版本摘要的规则集合
     */
    @Override
    public RuleRegistry load(String scene) {
        String sceneDirectory = resolveSceneDirectory(scene);
        Path externalScenePath = ruleBasePath.resolve(sceneDirectory);
        List<RuleSource> sources = Files.isDirectory(externalScenePath)
            ? findFileSystemSources(externalScenePath)
            : findClasspathSources(sceneDirectory);
        if (sources.isEmpty()) {
            throw new IllegalStateException("Failed to load rule registry from " + externalScenePath
                + " or classpath rules/" + sceneDirectory);
        }
        return buildRegistry(scene, sources);
    }

    /** 将场景标识解析为安全的规则目录名。 */
    private String resolveSceneDirectory(String scene) {
        if (scene == null || scene.isBlank()) {
            throw new IllegalArgumentException("scene must not be blank");
        }
        return sceneDirectories.getOrDefault(scene, scene.trim().toLowerCase().replace('_', '-'));
    }

    /** 扫描外部场景目录下按路径排序的 YAML 规则文件。 */
    private List<RuleSource> findFileSystemSources(Path scenePath) {
        try (Stream<Path> paths = Files.walk(scenePath)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                .sorted(Comparator.comparing(path -> scenePath.relativize(path).toString()))
                .map(path -> readFileSystemSource(scenePath, path))
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load rule registry from " + scenePath, ex);
        }
    }

    /** 读取一个外部规则文件并保留相对名称用于摘要计算。 */
    private RuleSource readFileSystemSource(Path scenePath, Path path) {
        try {
            return new RuleSource(scenePath.relativize(path).toString(), Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read rule resource " + path, ex);
        }
    }

    /** 扫描 classpath 规则资源并按稳定名称排序。 */
    private List<RuleSource> findClasspathSources(String sceneDirectory) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:rules/" + sceneDirectory + "/**/*.yaml");
            List<RuleSource> sources = new ArrayList<>();
            for (Resource resource : resources) {
                try (var input = resource.getInputStream()) {
                    sources.add(new RuleSource(classpathRelativeName(resource, sceneDirectory), new String(input.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
            sources.sort(Comparator.comparing(RuleSource::name));
            return sources;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan classpath rule resources for " + sceneDirectory, ex);
        }
    }

    /** 从 classpath 资源 URL 中提取相对规则文件名。 */
    private String classpathRelativeName(Resource resource, String sceneDirectory) throws IOException {
        String location = resource.getURL().toExternalForm().replace('\\', '/');
        String marker = "rules/" + sceneDirectory + "/";
        int markerIndex = location.indexOf(marker);
        return markerIndex < 0 ? location : location.substring(markerIndex + marker.length());
    }

    /** 解析规则来源、计算版本摘要并执行目录级契约校验。 */
    private RuleRegistry buildRegistry(String scene, List<RuleSource> sources) {
        List<DiagnosisRule> rules = new ArrayList<>();
        StringBuilder digestSource = new StringBuilder(scene);
        for (RuleSource source : sources) {
            List<DiagnosisRule> parsedRules = parseRuleDocument(source);
            if (parsedRules.isEmpty()) {
                continue;
            }
            digestSource.append('\n').append(source.name()).append('\n').append(source.content());
            for (DiagnosisRule rule : parsedRules) {
                if (rule.getScene() == null || rule.getScene().isBlank()) {
                    rule.setScene(scene);
                }
            }
            rules.addAll(parsedRules);
        }
        if (rules.isEmpty()) {
            throw new IllegalStateException("No diagnosis rule documents found for scene " + scene);
        }
        RuleRegistry registry = new RuleRegistry();
        registry.setScene(scene);
        registry.setVersionDigest(sha256(digestSource.toString()));
        registry.setRules(rules);
        validateRegistry(registry);
        return registry;
    }

    /** 将单个 YAML 文档转换为诊断规则列表，忽略非规则根节点。 */
    private List<DiagnosisRule> parseRuleDocument(RuleSource source) {
        try {
            JsonNode root = yamlMapper.readTree(source.content());
            if (root == null || !root.isArray() || root.isEmpty() || !root.get(0).has("ruleId")) {
                return List.of();
            }
            return yamlMapper.convertValue(root, new TypeReference<List<DiagnosisRule>>() { });
        } catch (IllegalArgumentException | IOException ex) {
            throw new IllegalStateException("Invalid diagnosis rule YAML: " + source.name(), ex);
        }
    }

    /** 校验规则 ID、工具名、证据字段和下一步动作的完整性。 */
    private void validateRegistry(RuleRegistry registry) {
        Set<String> ruleIds = new HashSet<>();
        for (DiagnosisRule rule : registry.getRules()) {
            if (rule.getSchemaVersion() == null || rule.getSchemaVersion() < 1) throw new IllegalStateException("schemaVersion must be positive for ruleId: " + rule.getRuleId());
            if (isBlank(rule.getRuleId())) throw new IllegalStateException("ruleId must not be blank");
            if (!ruleIds.add(rule.getRuleId())) throw new IllegalStateException("duplicate ruleId: " + rule.getRuleId());
            if (isBlank(rule.getReasonCode())) throw new IllegalStateException("reasonCode must not be blank for ruleId: " + rule.getRuleId());
            if (rule.getVersion() == null || rule.getVersion() < 1) throw new IllegalStateException("version must be positive for ruleId: " + rule.getRuleId());
            if (rule.getRequiredTools() == null || rule.getRequiredTools().isEmpty()) throw new IllegalStateException("requiredTools must not be empty for ruleId: " + rule.getRuleId());
            for (String toolName : rule.getRequiredTools()) {
                if (!new ToolRegistry().contains(toolName)) {
                    throw new IllegalStateException("requiredTools contains unregistered tool " + toolName
                        + " for ruleId: " + rule.getRuleId());
                }
            }
            if (rule.getEvidenceFields() == null || rule.getEvidenceFields().isEmpty()) throw new IllegalStateException("evidenceFields must not be empty for ruleId: " + rule.getRuleId());
            if (rule.getNextActions() == null || rule.getNextActions().isEmpty()) throw new IllegalStateException("nextActions must not be empty for ruleId: " + rule.getRuleId());
            if (isBlank(rule.getOwner())) throw new IllegalStateException("owner must not be blank for ruleId: " + rule.getRuleId());
        }
    }

    /** 判断规则文本字段是否为空。 */
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }

    /** 计算规则文件内容的 SHA-256 版本摘要。 */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private record RuleSource(String name, String content) { }
}
