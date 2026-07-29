package me.zhengjie.agent.capability;

import me.zhengjie.agent.analysis.SemanticCapabilityCatalog;
import me.zhengjie.agent.analysis.SemanticCapabilityCatalogLoader;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 能力目录的 profile 必须在启动期映射到唯一处理器。 */
class CapabilityHandlerRegistryTest {
    @Test
    void resolvesRegisteredProfileWithoutCoordinatorChanges() {
        SemanticCapabilityCatalog catalog = new SemanticCapabilityCatalogLoader().load();
        CapabilityHandler handler = new LegacyBusinessQueryCapabilityHandler();
        CapabilityHandlerRegistry registry = new CapabilityHandlerRegistry(List.of(handler), catalog);
        assertEquals(handler, registry.require("CUSTOMER_ORDER_LIST_V1"));
    }

    @Test
    void rejectsDuplicateProfiles() {
        SemanticCapabilityCatalog catalog = new SemanticCapabilityCatalogLoader().load();
        CapabilityHandler duplicate = new CapabilityHandler() {
            public String handlerId() { return "duplicate"; }
            public Set<String> plannerProfiles() { return Set.of("CUSTOMER_ORDER_LIST_V1"); }
        };
        assertThrows(IllegalStateException.class, () -> new CapabilityHandlerRegistry(
            List.of(new LegacyBusinessQueryCapabilityHandler(), duplicate), catalog));
    }

    @Test
    void rejectsUnknownCapabilityPermissionAtStartup() {
        SemanticCapabilityCatalog source = new SemanticCapabilityCatalogLoader().load();
        SemanticCapabilityCatalog.CapabilityDefinition original = source.getCapabilities().get(0);
        SemanticCapabilityCatalog invalid = new SemanticCapabilityCatalog();
        invalid.setCatalogVersion("test");
        invalid.setCapabilities(List.of(new SemanticCapabilityCatalog.CapabilityDefinition(
            original.capabilityId(), original.displayName(), original.frame(), original.allowedSetDefinitions(),
            Set.of("unknown:permission"), original.plannerProfile(), original.riskLevel())));
        assertThrows(IllegalStateException.class, () -> new CapabilityHandlerRegistry(
            List.of(new LegacyBusinessQueryCapabilityHandler()), invalid));
    }

    @Test
    void rejectsUnknownPlannerProfileAtStartup() {
        SemanticCapabilityCatalog source = new SemanticCapabilityCatalogLoader().load();
        SemanticCapabilityCatalog.CapabilityDefinition original = source.getCapabilities().get(0);
        SemanticCapabilityCatalog invalid = new SemanticCapabilityCatalog();
        invalid.setCatalogVersion("test");
        invalid.setCapabilities(List.of(new SemanticCapabilityCatalog.CapabilityDefinition(
            original.capabilityId(), original.displayName(), original.frame(),
            original.allowedSetDefinitions(), original.requiredPermissions(),
            "UNKNOWN_PROFILE", original.riskLevel())));

        assertThrows(IllegalStateException.class, () -> new CapabilityHandlerRegistry(
            List.of(new LegacyBusinessQueryCapabilityHandler()), invalid));
    }

    @Test
    void rejectsDuplicateCapabilityIdsAtStartup() {
        SemanticCapabilityCatalog source = new SemanticCapabilityCatalogLoader().load();
        SemanticCapabilityCatalog.CapabilityDefinition original = source.getCapabilities().get(0);
        SemanticCapabilityCatalog invalid = new SemanticCapabilityCatalog();
        invalid.setCatalogVersion("test");
        invalid.setCapabilities(List.of(original, original));

        assertThrows(IllegalStateException.class, () -> new CapabilityHandlerRegistry(
            List.of(new LegacyBusinessQueryCapabilityHandler()), invalid));
    }
}
