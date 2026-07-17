package me.zhengjie.agent.analysis;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从内置 YAML 加载能力目录，并拒绝重复 ID 或缺少固定 planner profile 的条目。 */
public class SemanticCapabilityCatalogLoader {
    /** 加载 classpath 中唯一可信的语义能力目录。 */
    @SuppressWarnings("unchecked")
    public SemanticCapabilityCatalog load() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("semantics/capability-catalog.yaml")) {
            if (input == null) throw new IllegalStateException("Semantic capability catalog is missing");
            Map<String, Object> root = new Yaml().load(input);
            SemanticCapabilityCatalog catalog = new SemanticCapabilityCatalog();
            catalog.setCatalogVersion(String.valueOf(root.get("catalogVersion")));
            Object raw = root.get("capabilities");
            if (!(raw instanceof List)) throw new IllegalStateException("Semantic capability catalog has no capabilities");
            catalog.setCapabilities((List<Map<String, Object>>) raw); validate(catalog); return catalog;
        } catch (Exception exception) { throw new IllegalStateException("Failed to load semantic capability catalog", exception); }
    }
    private void validate(SemanticCapabilityCatalog catalog) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> capability : catalog.getCapabilities()) {
            String id = capability == null ? null : String.valueOf(capability.get("capabilityId"));
            if (id == null || "null".equals(id) || !ids.add(id) || !capability.containsKey("frame") || !capability.containsKey("plannerProfile"))
                throw new IllegalStateException("Invalid semantic capability catalog entry");
        }
    }
}
