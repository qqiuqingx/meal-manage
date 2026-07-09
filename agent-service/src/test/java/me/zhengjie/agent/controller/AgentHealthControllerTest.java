package me.zhengjie.agent.controller;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.rule.RuleRegistryLoader;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentHealthControllerTest {

    @Test
    void shouldReturnHealthSummary() throws Exception {
        RuleRegistryLoader loader = scene -> {
            RuleRegistry registry = new RuleRegistry();
            registry.setScene(scene);
            registry.setVersionDigest("a4f8c1e9d320");
            registry.setRules(java.util.List.of(new me.zhengjie.agent.rule.DiagnosisRule()));
            return registry;
        };
        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AgentHealthController(loader, null, null, "http://localhost:8000", "token"))
            .build();

        mockMvc.perform(get("/api/agent/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.ruleRegistryLoaded").value(true))
            .andExpect(jsonPath("$.ruleVersionDigest").value("a4f8c1e9d320"))
            .andExpect(jsonPath("$.modelConfigured").value(false))
            .andExpect(jsonPath("$.toolClientConfigured").value(false));
    }

    @Test
    void shouldReturnDownWhenRuleRegistryLoadFails() throws Exception {
        RuleRegistryLoader loader = scene -> {
            throw new IllegalStateException("broken");
        };
        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AgentHealthController(loader, null, null, "http://localhost:8000", "token"))
            .build();

        mockMvc.perform(get("/api/agent/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DOWN"))
            .andExpect(jsonPath("$.ruleRegistryLoaded").value(false));
    }
}
