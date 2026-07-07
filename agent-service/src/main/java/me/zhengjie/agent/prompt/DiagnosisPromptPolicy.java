package me.zhengjie.agent.prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * 排餐诊断提示词策略，集中维护场景、输出契约和工具使用约束。
 */
public class DiagnosisPromptPolicy {

    private String scene;
    private int version;
    private String role;
    private OutputContract outputContract = new OutputContract();
    private List<String> forbiddenClaims = new ArrayList<>();
    private ToolPolicy toolPolicy = new ToolPolicy();
    private EvidencePolicy evidencePolicy = new EvidencePolicy();

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public OutputContract getOutputContract() {
        return outputContract;
    }

    public void setOutputContract(OutputContract outputContract) {
        this.outputContract = outputContract;
    }

    public List<String> getForbiddenClaims() {
        return forbiddenClaims;
    }

    public void setForbiddenClaims(List<String> forbiddenClaims) {
        this.forbiddenClaims = forbiddenClaims;
    }

    public ToolPolicy getToolPolicy() {
        return toolPolicy;
    }

    public void setToolPolicy(ToolPolicy toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

    public EvidencePolicy getEvidencePolicy() {
        return evidencePolicy;
    }

    public void setEvidencePolicy(EvidencePolicy evidencePolicy) {
        this.evidencePolicy = evidencePolicy;
    }

    public static class OutputContract {
        private List<String> requiredFields = new ArrayList<>();

        public List<String> getRequiredFields() {
            return requiredFields;
        }

        public void setRequiredFields(List<String> requiredFields) {
            this.requiredFields = requiredFields;
        }
    }

    public static class ToolPolicy {
        private int maxToolCalls;
        private List<String> requiredBeforeConclusion = new ArrayList<>();

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }

        public List<String> getRequiredBeforeConclusion() {
            return requiredBeforeConclusion;
        }

        public void setRequiredBeforeConclusion(List<String> requiredBeforeConclusion) {
            this.requiredBeforeConclusion = requiredBeforeConclusion;
        }
    }

    public static class EvidencePolicy {
        private int minEvidencePerReason;
        private boolean requireRuleIds;
        private boolean requireFieldReference;

        public int getMinEvidencePerReason() {
            return minEvidencePerReason;
        }

        public void setMinEvidencePerReason(int minEvidencePerReason) {
            this.minEvidencePerReason = minEvidencePerReason;
        }

        public boolean isRequireRuleIds() {
            return requireRuleIds;
        }

        public void setRequireRuleIds(boolean requireRuleIds) {
            this.requireRuleIds = requireRuleIds;
        }

        public boolean isRequireFieldReference() {
            return requireFieldReference;
        }

        public void setRequireFieldReference(boolean requireFieldReference) {
            this.requireFieldReference = requireFieldReference;
        }
    }
}
