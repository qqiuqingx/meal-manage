package me.zhengjie.agent.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPropertiesTest {

    @Test
    void shouldExposeSafeDefaultsForRuleLoadingAndInternalCommunication() {
        AgentProperties properties = new AgentProperties();

        assertEquals("http://localhost:8000", properties.getContextBaseUrl());
        assertEquals("meal-plan", properties.getRules().getSceneDirectories().get("MEAL_PLAN_NOT_GENERATED"));
        assertEquals(6, properties.getChat().getToolLoop().getMaxToolCalls());
        assertEquals(4, properties.getChat().getToolLoop().getMaxModelRounds());
        assertEquals(100, properties.getChat().getToolLoop().getMaxRecords());
        assertEquals(1, properties.getChat().getToolLoop().getMaxAnswerRepairs());
        assertTrue(properties.getInternalToken().isEmpty());
    }

    @Test
    void rejectsBlankRuleSceneDuringStartupValidation() {
        AgentProperties properties = new AgentProperties();
        properties.getRules().setSceneDirectories(Map.of("", "meal-plan"));

        assertThrows(IllegalStateException.class,
            () -> new AgentPropertiesValidator(properties).afterSingletonsInstantiated());
    }

    @Test
    void presentationProfileMustBeStructuredAndToolFree() {
        AgentProperties properties = new AgentProperties();
        AgentProperties.ModelProfile profile = new AgentProperties.ModelProfile();
        profile.setStructuredOutput(false);
        properties.getModels().setProfiles(new LinkedHashMap<>(Map.of("presentation", profile)));

        assertThrows(IllegalStateException.class,
            () -> new AgentPropertiesValidator(properties).afterSingletonsInstantiated());
    }

    @Test
    void missingPresentationProfileLeavesUnknownCardsOnDirectFallback() {
        AgentProperties properties = new AgentProperties();

        assertTrue(!properties.getModels().getProfiles().containsKey("presentation"));
    }
}
