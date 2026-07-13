package me.zhengjie.agent.analysis.domain;

import java.util.ArrayList;
import java.util.List;

/** 会改变业务查询结果、因此必须向用户澄清的歧义。 */
public class BusinessAmbiguity {
    private String field;
    private List<String> options = new ArrayList<>();
    private boolean material;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public boolean isMaterial() { return material; }
    public void setMaterial(boolean material) { this.material = material; }
}
