package me.zhengjie.agent.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentMdcScopeTest {

    @Test
    void shouldRestoreNestedMdcValue() {
        MDC.put("toolName", "outer");
        try {
            try (AgentMdcScope ignored = AgentMdcScope.put("toolName", "inner")) {
                assertEquals("inner", MDC.get("toolName"));
            }
            assertEquals("outer", MDC.get("toolName"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void shouldRemoveNewFieldWhenScopeCloses() {
        try (AgentMdcScope ignored = AgentMdcScope.put("modelProfile", "default")) {
            assertEquals("default", MDC.get("modelProfile"));
        }
        assertNull(MDC.get("modelProfile"));
    }
}
