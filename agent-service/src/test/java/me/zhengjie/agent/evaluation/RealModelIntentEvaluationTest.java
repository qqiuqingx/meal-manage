package me.zhengjie.agent.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实模型评测入口；仅在 real-model-eval profile 配置的环境中运行，普通构建不会依赖模型密钥。 */
@EnabledIfSystemProperty(named = "agent.real-model-eval.enabled", matches = "true")
class RealModelIntentEvaluationTest {
    @Test
    void preparesIsolatedEvaluationOutputDirectory() throws Exception {
        Path output = Path.of("target", "intent-evaluation");
        Files.createDirectories(output);
        assertTrue(Files.isDirectory(output));
    }
}
