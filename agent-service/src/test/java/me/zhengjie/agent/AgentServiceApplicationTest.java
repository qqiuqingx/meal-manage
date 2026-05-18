package me.zhengjie.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AgentServiceApplicationTest {

    @Test
    void shouldCreateApplicationInstance() {
        assertDoesNotThrow(AgentServiceApplication::new);
    }
}
