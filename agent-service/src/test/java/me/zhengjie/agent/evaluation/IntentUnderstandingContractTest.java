package me.zhengjie.agent.evaluation;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 确保会话理解评测集可读取，且每个案例都声明至少一轮和期望结构。 */
class IntentUnderstandingContractTest {
    @Test
    void loadsStructuredUnderstandingCases() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("evaluation/intent-understanding-cases.yaml")) {
            assertNotNull(input);
            Map<String, Object> root = new Yaml().load(input);
            List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("cases");
            assertTrue(cases.size() >= 20);
            for (Map<String, Object> item : cases) {
                assertNotNull(item.get("id"));
                assertFalse(((List<?>) item.get("turns")).isEmpty());
            }
            assertTrue(new IntentUnderstandingCaseGenerator().expand(cases).size() >= 300);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
