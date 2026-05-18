package me.zhengjie.agent.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * 某个诊断场景下生效的规则集合。
 */
public class RuleRegistry {

    private String scene;
    private String versionDigest;
    private List<DiagnosisRule> rules = new ArrayList<>();

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getVersionDigest() {
        return versionDigest;
    }

    public void setVersionDigest(String versionDigest) {
        this.versionDigest = versionDigest;
    }

    public List<DiagnosisRule> getRules() {
        return rules;
    }

    public void setRules(List<DiagnosisRule> rules) {
        this.rules = rules;
    }
}
