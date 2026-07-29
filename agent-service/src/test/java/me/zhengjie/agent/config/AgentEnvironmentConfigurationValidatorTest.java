package me.zhengjie.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEnvironmentConfigurationValidatorTest {

    @Test
    void rejectsMissingInternalTokenInProduction() {
        AgentProperties properties = new AgentProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new AgentEnvironmentConfigurationValidator(properties, environment).validate());

        assertTrue(error.getMessage().contains("agent.internal-token"));
    }

    @Test
    void allowsEmptyInternalTokenInDevelopment() {
        AgentProperties properties = new AgentProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertDoesNotThrow(
            () -> new AgentEnvironmentConfigurationValidator(properties, environment).validate());
    }

    @Test
    void acceptsConfiguredInternalTokenInStaging() {
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("configured");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertDoesNotThrow(
            () -> new AgentEnvironmentConfigurationValidator(properties, environment).validate());
    }
}
