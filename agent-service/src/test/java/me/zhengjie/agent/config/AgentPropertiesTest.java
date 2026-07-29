package me.zhengjie.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPropertiesTest {

    @Test
    void shouldExposeSafeDefaultsForRuleLoadingAndInternalCommunication() {
        AgentProperties properties = new AgentProperties();

        assertEquals("http://localhost:8000", properties.getContextBaseUrl());
        assertEquals(8000, properties.getBusinessQueryTimeoutMs());
        assertEquals("meal-plan", properties.getRules().getSceneDirectories().get("MEAL_PLAN_NOT_GENERATED"));
        assertEquals(AgentProperties.IntentClassifierMode.HYBRID,
            properties.getChat().getIntentClassifier().getMode());
        assertEquals(AgentProperties.BusinessSemanticMode.LLM_FIRST,
            properties.getChat().getBusinessSemantic().getMode());
        assertEquals(AgentProperties.ConversationUnderstandingMode.SHADOW,
            properties.getChat().getConversationUnderstanding().getMode());
        assertEquals(30, properties.getChat().getBusinessSemantic().getPendingContextTtlMinutes());
        assertEquals(8, properties.getDiagnosis().getMaxToolCalls());
        assertTrue(properties.getInternalToken().isEmpty());
    }

    @Test
    void rejectsUnknownChatModeDuringConfigurationBinding() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
            "agent.chat.intent-classifier.mode", "unsupported")));

        assertThrows(BindException.class,
            () -> binder.bind("agent", Bindable.of(AgentProperties.class)).get());
    }
}
