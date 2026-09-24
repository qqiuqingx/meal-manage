package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户排餐日历调整请求。
 */
@Data
public class CustomerMealScheduleAdjustmentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 客户ID
     */
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    /**
     * 当前编辑的统计月份，格式 yyyy-MM。用于限定人工新增同步范围，避免保存单月日历时影响其他月份。
     */
    private String statsMonth;

    /**
     * 是否由数量日历提交；旧调用方省略时继续按单份人工新增请求处理。
     */
    private Boolean quantityMode;

    /**
     * 查询日历时返回的修订标记，用于拒绝覆盖他人刚保存的变更。
     */
    private String expectedRevision;

    /**
     * 页面保存后的排除日期完整列表
     */
    private List<ExcludedDateDto> excludedDates = new ArrayList<>();

    /**
     * 页面保存后的人工新增完整列表
     */
    private List<CustomerMealScheduleAdditionDto> additions = new ArrayList<>();
}
