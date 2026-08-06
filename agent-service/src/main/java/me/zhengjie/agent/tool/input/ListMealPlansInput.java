package me.zhengjie.agent.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.ai.tool.annotation.ToolParam;

/** 查询排餐与菜品明细的强类型输入。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ListMealPlansInput {
    @ToolParam(required = false, description = "客户稳定 ID；与客户编号、订单 ID 或订单编号至少提供一个，禁止传 0。")
    private Long customerId;
    @ToolParam(required = false, description = "客户编号；与客户 ID、订单 ID 或订单编号至少提供一个，字符串必须加引号。")
    private String customerCode;
    @ToolParam(required = false, description = "订单稳定 ID；与客户 ID、客户编号或订单编号至少提供一个，禁止传 0。")
    private Long orderId;
    @ToolParam(required = false, description = "订单编号；与客户 ID、客户编号或订单 ID 至少提供一个，字符串必须加引号。")
    private String orderCode;
    @ToolParam(required = false, description = "单日排餐日期；格式 yyyy-MM-dd，不能与 startDate/endDate 同时传。")
    private String recordDate;
    @ToolParam(required = false, description = "排餐日期范围起始；格式 yyyy-MM-dd，必须不晚于 endDate。")
    private String startDate;
    @ToolParam(required = false, description = "排餐日期范围结束；格式 yyyy-MM-dd，必须不早于 startDate。")
    private String endDate;
    @ToolParam(required = false, description = "餐次，只能是 BREAKFAST、LUNCH 或 DINNER。")
    private MealType mealType;
    @ToolParam(required = false, description = "页码，从 1 开始，默认 1。")
    private Integer page = 1;
    @ToolParam(required = false, description = "每页数量，范围 1-50，默认 50。")
    private Integer size = 50;

    public Long getCustomerId() { return customerId; }
    /** 设置客户 ID，并将模型常见的 0 占位符归一化为未提供。 */
    public void setCustomerId(Long value) { customerId = normalizeOptionalId(value); }
    public String getCustomerCode() { return customerCode; }
    /** 设置客户编号，并将空白占位符归一化为未提供。 */
    public void setCustomerCode(String value) { customerCode = normalizeOptionalText(value); }
    public Long getOrderId() { return orderId; }
    /** 设置订单 ID，并将模型常见的 0 占位符归一化为未提供。 */
    public void setOrderId(Long value) { orderId = normalizeOptionalId(value); }
    public String getOrderCode() { return orderCode; }
    /** 设置订单编号，并将空白占位符归一化为未提供。 */
    public void setOrderCode(String value) { orderCode = normalizeOptionalText(value); }
    public String getRecordDate() { return recordDate; }
    /** 设置单日排餐日期，并将空白占位符归一化为未提供。 */
    public void setRecordDate(String value) { recordDate = normalizeOptionalText(value); }
    public String getStartDate() { return startDate; }
    /** 设置排餐范围起始日期，并将空白占位符归一化为未提供。 */
    public void setStartDate(String value) { startDate = normalizeOptionalText(value); }
    public String getEndDate() { return endDate; }
    /** 设置排餐范围结束日期，并将空白占位符归一化为未提供。 */
    public void setEndDate(String value) { endDate = normalizeOptionalText(value); }
    public MealType getMealType() { return mealType; }
    /** 设置餐次枚举；空值表示查询全部餐次。 */
    public void setMealType(MealType value) { mealType = value; }
    public Integer getPage() { return page; }
    /** 设置页码；具体范围由工具输入护栏统一校验。 */
    public void setPage(Integer value) { page = value; }
    public Integer getSize() { return size; }
    /** 设置分页大小；具体范围由工具输入护栏统一校验。 */
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
