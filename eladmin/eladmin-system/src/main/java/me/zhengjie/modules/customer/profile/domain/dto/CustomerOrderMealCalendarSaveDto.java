package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.List;

/**
 * 保存单笔订单某月完整的数量覆盖快照。
 */
@Data
public class CustomerOrderMealCalendarSaveDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前编辑月份，格式 yyyy-MM。 */
    @NotBlank
    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])")
    private String statsMonth;

    /** 查询时取得的修订标记，过期时服务端返回 409。 */
    @NotBlank
    private String expectedRevision;

    /** 当前订单当前月份全部需要保留的覆盖；空数组表示恢复该月默认计划。 */
    @NotNull
    @Valid
    private List<CustomerOrderMealCalendarOverrideDto> overrides;
}
