package me.zhengjie.agent.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.ai.tool.annotation.ToolParam;

/** 以订单为根查询服务客户的强类型输入。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchServiceCustomersInput {
    @ToolParam(required = false, description = "可选客户稳定 ID；只查询指定客户时传正整数，不指定时省略，禁止传 0。")
    private Long customerId;
    @ToolParam(required = false, description = "可选客户编号；不指定客户时省略或传 null。")
    private String customerCode;
    @ToolParam(required = false, description = "可选订单稳定 ID；只查询指定订单时传正整数，不指定时省略，禁止传 0。")
    private Long orderId;
    @ToolParam(required = false, description = "可选订单编号；不指定订单时省略或传 null。")
    private String orderCode;
    @ToolParam(required = false, description = "订单状态。查询‘现在/当前/服务中的客户’或‘分别什么时候下单’时必须传 ACTIVE；查询全部状态才传 ALL。")
    private ServiceCustomerStatus status = ServiceCustomerStatus.ALL;
    @ToolParam(required = false, description = "成交日期下界，可选；只能使用 yyyy-MM-dd，不筛选时省略或传 null，禁止传空字符串。")
    private String dealTimeFrom;
    @ToolParam(required = false, description = "成交日期上界，可选；只能使用 yyyy-MM-dd，不筛选时省略或传 null，禁止传空字符串。")
    private String dealTimeTo;
    @ToolParam(required = false, description = "可选父套餐或子套餐编码；不筛选时省略或传 null。")
    private String packageCode;
    @ToolParam(required = false, description = "页码，从 1 开始，默认 1。")
    private Integer page = 1;
    @ToolParam(required = false, description = "每页数量，范围 1-20，默认 20，不能传 50；truncated=true 时继续查询下一页。")
    private Integer size = 20;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = normalizeOptionalId(value); }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = normalizeOptionalText(value); }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long value) { orderId = normalizeOptionalId(value); }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String value) { orderCode = normalizeOptionalText(value); }
    public ServiceCustomerStatus getStatus() { return status; }
    public void setStatus(ServiceCustomerStatus value) { status = value == null ? ServiceCustomerStatus.ALL : value; }
    public String getDealTimeFrom() { return dealTimeFrom; }
    public void setDealTimeFrom(String value) { dealTimeFrom = normalizeOptionalText(value); }
    public String getDealTimeTo() { return dealTimeTo; }
    public void setDealTimeTo(String value) { dealTimeTo = normalizeOptionalText(value); }
    public String getPackageCode() { return packageCode; }
    public void setPackageCode(String value) { packageCode = normalizeOptionalText(value); }
    public Integer getPage() { return page; }
    public void setPage(Integer value) { page = value; }
    public Integer getSize() { return size; }
    public void setSize(Integer value) { size = value; }

    /** 将模型常见的零值占位符转换为未提供可选 ID。 */
    private static Long normalizeOptionalId(Long value) {
        return value != null && value == 0L ? null : value;
    }

    /** 将可选文本的空白占位符转换为未提供。 */
    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
