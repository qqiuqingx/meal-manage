package me.zhengjie.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPropertiesTest {

    @Test
    void shouldExposeSafeDefaultsForRuleLoadingAndInternalCommunication() {
        AgentProperties properties = new AgentProperties();

        assertEquals("http://localhost:8000", properties.getContextBaseUrl());
        assertEquals(8000, properties.getBusinessQueryTimeoutMs());
        assertEquals("meal-plan", properties.getRules().getSceneDirectories().get("MEAL_PLAN_NOT_GENERATED"));
        assertTrue(properties.getInternalToken().isEmpty());
    }
}
