package me.zhengjie.agent.summary;

import java.util.ArrayList;
import java.util.List;

/**
 * 诊断建议模板，用于按原因码补齐稳定建议和客服动作。
 */
public class DiagnosisSuggestionTemplate {

    private String code;
    private String title;
    private String defaultSuggestion;
    private List<String> nextActions = new ArrayList<>();
    private boolean customerVisible;
    private boolean requiresManualConfirm;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDefaultSuggestion() {
        return defaultSuggestion;
    }

    public void setDefaultSuggestion(String defaultSuggestion) {
        this.defaultSuggestion = defaultSuggestion;
    }

    public List<String> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<String> nextActions) {
        this.nextActions = nextActions;
    }

    public boolean isCustomerVisible() {
        return customerVisible;
    }

    public void setCustomerVisible(boolean customerVisible) {
        this.customerVisible = customerVisible;
    }

    public boolean isRequiresManualConfirm() {
        return requiresManualConfirm;
    }

    public void setRequiresManualConfirm(boolean requiresManualConfirm) {
        this.requiresManualConfirm = requiresManualConfirm;
    }
}
