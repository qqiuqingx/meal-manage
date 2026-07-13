package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import java.util.List;

/** Agent 公共排期菜单受控查询请求。 */
@Data
public class AgentScheduledMenuQueryRequest {
    /** 要查询的日期（yyyy-MM-dd）。 */
    @NotBlank(message = "查询日期不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "查询日期格式必须为 yyyy-MM-dd")
    private String recordDate;

    /** 受控菜单餐次集合，仅允许午餐和晚餐。 */
    @NotEmpty(message = "菜单餐次不能为空")
    private List<@Pattern(regexp = "LUNCH|DINNER", message = "公共菜单仅支持午餐或晚餐") String> mealTypes;
}
