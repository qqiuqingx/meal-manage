package me.zhengjie.agent.domain.dto;

/**
 * 诊断原因的字段级证据。
 */
public class DiagnosisEvidenceDto {

    private String label;
    private String value;

    public DiagnosisEvidenceDto() {
    }

    public DiagnosisEvidenceDto(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
