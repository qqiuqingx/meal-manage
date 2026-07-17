package me.zhengjie.agent.query.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 多语义帧的独立结果区块，单个失败不得掩盖其他区块成功结果。 */
public class BusinessQueryResultBlock {
    private String frameId;
    private String responseType;
    private String status;
    private Map<String, Object> result = new LinkedHashMap<>();
    private List<String> warnings = List.of();
    public String getFrameId() { return frameId; }
    public void setFrameId(String frameId) { this.frameId = frameId; }
    public String getResponseType() { return responseType; }
    public void setResponseType(String responseType) { this.responseType = responseType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result == null ? new LinkedHashMap<>() : result; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings == null ? List.of() : warnings; }
}
