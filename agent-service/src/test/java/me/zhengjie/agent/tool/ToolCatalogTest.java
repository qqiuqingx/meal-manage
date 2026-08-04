package me.zhengjie.agent.tool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工具注册的兼容门面和统一目录必须指向同一份元数据。 */
class ToolCatalogTest {
    @Test
    void exposesLegacyToolFromSingleCatalog() {
        assertTrue(ToolCatalog.isRegistered("listOrders"));
        assertTrue(ToolCatalog.isRegistered("getActiveOrderSummary"));
        assertEquals("customerOrder:list", ToolCatalog.descriptor("listOrders").requiredPermission());
        assertEquals("customerOrder:list",
            ToolCatalog.descriptor("getActiveOrderSummary").requiredPermission());
        assertEquals(ToolCatalog.descriptor("listOrders"),
            me.zhengjie.agent.query.tool.AgentBusinessToolRegistry.descriptor("listOrders"));
    }

    @Test
    void everyRegisteredBusinessToolHasExactlyOneCatalogInvoker() {
        assertTrue(ToolCatalog.descriptors().stream()
            .allMatch(descriptor -> ToolCatalog.isExecutable(descriptor.name())));
    }
}
