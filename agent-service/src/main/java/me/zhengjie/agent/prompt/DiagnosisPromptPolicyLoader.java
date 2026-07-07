package me.zhengjie.agent.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 负责加载并校验诊断提示词策略，避免提示词约束散落在代码中。
 */
public class DiagnosisPromptPolicyLoader {

    static final String DEFAULT_RESOURCE_PATH = "rules/meal-plan/prompt-policy.yaml";

    private final Resource resource;
    private final ObjectMapper objectMapper;

    public DiagnosisPromptPolicyLoader() {
        this(new ClassPathResource(DEFAULT_RESOURCE_PATH), new ObjectMapper());
    }

    DiagnosisPromptPolicyLoader(Resource resource) {
        this(resource, new ObjectMapper());
    }

    DiagnosisPromptPolicyLoader(Resource resource, ObjectMapper objectMapper) {
        this.resource = resource;
        this.objectMapper = objectMapper;
    }

    /**
     * 加载并校验提示词策略。
     *
     * @return 可直接用于构建提示词的策略对象
     */
    public DiagnosisPromptPolicy load() {
        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, Object> raw = new Yaml().load(inputStream);
            DiagnosisPromptPolicy policy = objectMapper.convertValue(raw, DiagnosisPromptPolicy.class);
            validate(policy);
            return policy;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load diagnosis prompt policy", ex);
        }
    }

    /**
     * 校验策略必填项和取值范围，避免不完整策略进入运行时。
     *
     * @param policy 待校验策略
     */
    void validate(DiagnosisPromptPolicy policy) {
        if (policy == null) {
            throw new IllegalStateException("Diagnosis prompt policy must not be null");
        }
        if (!"MEAL_PLAN_NOT_GENERATED".equals(policy.getScene())) {
            throw new IllegalStateException("Diagnosis prompt policy scene must be MEAL_PLAN_NOT_GENERATED");
        }
        if (policy.getVersion() <= 0) {
            throw new IllegalStateException("Diagnosis prompt policy version must be greater than 0");
        }
        if (isBlank(policy.getRole())) {
            throw new IllegalStateException("Diagnosis prompt policy role must not be blank");
        }
        if (policy.getOutputContract() == null || policy.getOutputContract().getRequiredFields() == null) {
            throw new IllegalStateException("Diagnosis prompt policy outputContract.requiredFields must not be null");
        }
        List<String> requiredFields = policy.getOutputContract().getRequiredFields();
        if (!requiredFields.contains("summary") || !requiredFields.contains("reasons")) {
            throw new IllegalStateException("Diagnosis prompt policy requiredFields must include summary and reasons");
        }
        if (policy.getForbiddenClaims() == null || policy.getForbiddenClaims().isEmpty()) {
            throw new IllegalStateException("Diagnosis prompt policy forbiddenClaims must not be empty");
        }
        if (policy.getToolPolicy() == null) {
            throw new IllegalStateException("Diagnosis prompt policy toolPolicy must not be null");
        }
        if (policy.getToolPolicy().getMaxToolCalls() < 1 || policy.getToolPolicy().getMaxToolCalls() > 20) {
            throw new IllegalStateException("Diagnosis prompt policy maxToolCalls must be between 1 and 20");
        }
        if (policy.getToolPolicy().getRequiredBeforeConclusion() == null
            || policy.getToolPolicy().getRequiredBeforeConclusion().isEmpty()) {
            throw new IllegalStateException("Diagnosis prompt policy requiredBeforeConclusion must not be empty");
        }
        if (policy.getEvidencePolicy() == null) {
            throw new IllegalStateException("Diagnosis prompt policy evidencePolicy must not be null");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
