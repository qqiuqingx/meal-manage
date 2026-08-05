package me.zhengjie.agent.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.ai.tool.annotation.ToolParam;

/** 查询客户档案的强类型输入；不包含权限、数据范围或内部鉴权字段。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchCustomerProfilesInput {
    @ToolParam(required = false, description = "可选客户稳定 ID；只查询指定客户时传正整数，不指定时省略，禁止传 0。")
    private Long customerId;
    @ToolParam(required = false, description = "可选客户编号；不指定客户时省略或传 null。")
    private String customerCode;
    @ToolParam(required = false, description = "可选客户姓名关键字；不指定时省略或传 null。")
    private String customerName;
    @ToolParam(required = false, description = "是否已下单；只能用于客户档案筛选，返回结果不包含订单成交/创建时间。")
    private Boolean hasOrder;
    @ToolParam(required = false, description = "页码，从 1 开始，默认 1。")
    private Integer page = 1;
    @ToolParam(required = false, description = "每页数量，范围 1-20，默认 20；不能传 50。")
    private Integer size = 20;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = normalizeOptionalId(value); }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = normalizeOptionalText(value); }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = normalizeOptionalText(value); }
    public Boolean getHasOrder() { return hasOrder; }
    public void setHasOrder(Boolean value) { hasOrder = value; }
    public Integer getPage() { return page; }
    public void setPage(Integer value) { page = value; }
    public Integer getSize() { return size; }
    public void setSize(Integer value) { size = value; }

    /** 将模型常见的零值占位符转换为未提供可选客户 ID。 */
    private static Long normalizeOptionalId(Long value) {
        return value != null && value == 0L ? null : value;
    }

    /** 将可选文本的空白占位符转换为未提供。 */
    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
