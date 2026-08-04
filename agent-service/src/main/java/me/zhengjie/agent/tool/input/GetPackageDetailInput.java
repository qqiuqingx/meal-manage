package me.zhengjie.agent.tool.input;

/** 查询父子套餐规格的强类型输入。 */
public class GetPackageDetailInput {
    private Long packageId;
    private String packageCode;

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long value) { packageId = value; }
    public String getPackageCode() { return packageCode; }
    public void setPackageCode(String value) { packageCode = value; }
}
