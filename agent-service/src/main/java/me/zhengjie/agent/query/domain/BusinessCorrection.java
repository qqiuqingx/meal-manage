package me.zhengjie.agent.query.domain;

import me.zhengjie.agent.analysis.domain.CorrectionReason;

import java.util.ArrayList;
import java.util.List;

/** 上一轮结果纠错的受控描述，不保存用户原文或工具原始响应。 */
public class BusinessCorrection {
    private CorrectionReason reason = CorrectionReason.UNKNOWN;
    private List<String> observations = new ArrayList<>();
    private boolean requiresReplan;

    public CorrectionReason getReason() { return reason; }
    public void setReason(CorrectionReason reason) { this.reason = reason; }
    public List<String> getObservations() { return observations; }
    public void setObservations(List<String> observations) { this.observations = observations; }
    public boolean isRequiresReplan() { return requiresReplan; }
    public void setRequiresReplan(boolean requiresReplan) { this.requiresReplan = requiresReplan; }
}
