package me.zhengjie.agent.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** 从内置 YAML 加载强类型能力目录，并在启动期拒绝不完整或重复配置。 */
public class SemanticCapabilityCatalogLoader {
    /** 加载 classpath 中唯一可信的语义能力目录。 */
    public SemanticCapabilityCatalog load() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("semantics/capability-catalog.yaml")) {
            if (input == null) throw new IllegalStateException("Semantic capability catalog is missing");
            SemanticCapabilityCatalog catalog = new ObjectMapper(new YAMLFactory()).readValue(input, SemanticCapabilityCatalog.class);
            validate(catalog);
            return catalog;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load semantic capability catalog", exception);
        }
    }

    private void validate(SemanticCapabilityCatalog catalog) {
        if (catalog == null || catalog.getCatalogVersion() == null || catalog.getCatalogVersion().isBlank()) {
            throw new IllegalStateException("Semantic capability catalog version is required");
        }
        Set<String> ids = new HashSet<>();
        for (SemanticCapabilityCatalog.CapabilityDefinition item : catalog.getCapabilities()) {
            if (item == null || item.capabilityId() == null || item.capabilityId().isBlank() || !ids.add(item.capabilityId())
                || item.frame() == null || item.plannerProfile() == null || item.plannerProfile().isBlank()
                || item.riskLevel() == null || item.requiredPermissions() == null || item.requiredPermissions().isEmpty()) {
                throw new IllegalStateException("Invalid semantic capability catalog entry");
            }
        }
    }
}
