package me.zhengjie.agent.evaluation;

import me.zhengjie.agent.AgentServiceApplication;
import me.zhengjie.agent.analysis.ConversationUnderstandingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实模型评测入口。
 *
 * <p>必须显式设置 {@code AGENT_REAL_MODEL_EVAL_ENABLED=true}，并配置可用的
 * {@code AGENT_DEEPSEEK_API_KEY}（或兼容 OpenAI 配置）后才会运行，避免普通构建产生外部调用。</p>
 */
@SpringBootTest(classes = AgentServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_REAL_MODEL_EVAL_ENABLED", matches = "true")
class RealModelIntentEvaluationTest {
    @Autowired
    private ConversationUnderstandingService conversationUnderstandingService;

    /** 调用真实会话理解服务执行全部脱敏变体，并只输出无原文的汇总报告。 */
    @Test
    @SuppressWarnings("unchecked")
    void evaluatesRealModelAgainstSanitizedCases() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("evaluation/intent-understanding-cases.yaml")) {
            assertTrue(input != null, "缺少会话理解评测案例");
            Map<String, Object> root = new Yaml().load(input);
            List<Map<String, Object>> baseCases = (List<Map<String, Object>>) root.get("cases");
            List<Map<String, Object>> cases = new IntentUnderstandingCaseGenerator().expand(baseCases);
            List<IntentEvaluationReporter.EvaluationObservation> observations = new IntentUnderstandingEvaluationRunner()
                .evaluate(cases, conversationUnderstandingService);
            Path output = new IntentEvaluationReporter().write(new IntentEvaluationReporter().summarize(observations));
            assertTrue(observations.size() >= 300, "真实模型评测未覆盖全部生成案例");
            assertTrue(Files.isRegularFile(output));
        }
    }
}
