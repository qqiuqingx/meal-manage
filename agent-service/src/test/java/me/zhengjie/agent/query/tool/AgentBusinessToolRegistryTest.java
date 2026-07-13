package me.zhengjie.agent.query.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBusinessToolRegistryTest {
    @Test
    void shouldRegisterOnlyBoundedInternalReadOnlyTools() {
        assertFalse(AgentBusinessToolRegistry.descriptors().isEmpty());
        AgentBusinessToolRegistry.descriptors().forEach(descriptor -> {
            assertTrue(descriptor.maxResults() > 0);
            assertTrue(descriptor.timeoutMillis() > 0);
            assertFalse(descriptor.inputSchema().isBlank());
            assertFalse(descriptor.outputSchema().isBlank());
            assertTrue("INTERNAL_READ_ONLY".equals(descriptor.sensitivity()));
        });
    }

    /** 套餐规格必须通过固定内部只读工具访问，不允许自由 URL 或字段查询。 */
    @Test
    void shouldRegisterBoundedPackageDetailTool() {
        assertTrue(AgentBusinessToolRegistry.descriptors().stream()
            .anyMatch(item -> "packageDetail".equals(item.name())
                && "parentPackageId".equals(item.inputSchema())
                && item.maxResults() == 5));
    }
}
