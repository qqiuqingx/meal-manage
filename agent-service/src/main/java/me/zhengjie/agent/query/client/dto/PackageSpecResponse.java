package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统父子套餐规格的强类型 Agent 传输契约。 */
public class PackageSpecResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private boolean present; private Long parentPackageId; private String parentPackageCode; private String parentPackageName; private Boolean enabled; private List<SubPackageSpec> subPackages = new ArrayList<>();
    public boolean isPresent() { return present; } public void setPresent(boolean value) { present = value; }
    public Long getParentPackageId() { return parentPackageId; } public void setParentPackageId(Long value) { parentPackageId = value; }
    public String getParentPackageCode() { return parentPackageCode; } public void setParentPackageCode(String value) { parentPackageCode = value; }
    public String getParentPackageName() { return parentPackageName; } public void setParentPackageName(String value) { parentPackageName = value; }
    public Boolean getEnabled() { return enabled; } public void setEnabled(Boolean value) { enabled = value; }
    public List<SubPackageSpec> getSubPackages() { return subPackages; } public void setSubPackages(List<SubPackageSpec> value) { subPackages = value == null ? new ArrayList<>() : value; }
    /** 过渡期适配未迁移 Map。 */
    public static PackageSpecResponse fromLegacyMap(Map<String, Object> value) { return value == null || value.isEmpty() ? new PackageSpecResponse() : LEGACY_MAPPER.convertValue(value, PackageSpecResponse.class); }
    /** 显式生成套餐卡片数据。 */
    public Map<String, Object> toPresentationMap() { Map<String, Object> result = new LinkedHashMap<>(); result.put("present", present); result.put("parentPackageId", parentPackageId); result.put("parentPackageCode", parentPackageCode); result.put("parentPackageName", parentPackageName); result.put("enabled", enabled); result.put("subPackages", subPackages.stream().map(SubPackageSpec::toPresentationMap).toList()); return result; }
    /** 子套餐餐品规格。 */
    public static class SubPackageSpec {
        private Long subPackageId; private String subPackageCode; private String subPackageName; private Integer meatCount; private Integer vegCount; private Boolean includeSoup; private Boolean includeRice; private Boolean enabled;
        public Long getSubPackageId() { return subPackageId; } public void setSubPackageId(Long value) { subPackageId = value; }
        public String getSubPackageCode() { return subPackageCode; } public void setSubPackageCode(String value) { subPackageCode = value; }
        public String getSubPackageName() { return subPackageName; } public void setSubPackageName(String value) { subPackageName = value; }
        public Integer getMeatCount() { return meatCount; } public void setMeatCount(Integer value) { meatCount = value; }
        public Integer getVegCount() { return vegCount; } public void setVegCount(Integer value) { vegCount = value; }
        public Boolean getIncludeSoup() { return includeSoup; } public void setIncludeSoup(Boolean value) { includeSoup = value; }
        public Boolean getIncludeRice() { return includeRice; } public void setIncludeRice(Boolean value) { includeRice = value; }
        public Boolean getEnabled() { return enabled; } public void setEnabled(Boolean value) { enabled = value; }
        private Map<String, Object> toPresentationMap() { Map<String, Object> item = new LinkedHashMap<>(); item.put("subPackageId", subPackageId); item.put("subPackageCode", subPackageCode); item.put("subPackageName", subPackageName); item.put("meatCount", meatCount); item.put("vegCount", vegCount); item.put("includeSoup", includeSoup); item.put("includeRice", includeRice); item.put("enabled", enabled); return item; }
    }
}
