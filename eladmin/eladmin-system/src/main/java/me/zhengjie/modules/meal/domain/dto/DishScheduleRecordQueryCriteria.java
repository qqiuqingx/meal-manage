package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;

/**
 * 排餐记录查询条件
 * @author qqx
 * @date 2026-03-17
 **/
@Data
public class DishScheduleRecordQueryCriteria {

    @ApiModelProperty(value = "开始日期（yyyy-MM-dd）")
    private String startDate;

    @ApiModelProperty(value = "结束日期（yyyy-MM-dd）")
    private String endDate;

    @ApiModelProperty(value = "客户ID")
    private Integer customerId;

    @ApiModelProperty(value = "客户名称（模糊查询）")
    private String customerName;

    @ApiModelProperty(value = "餐次列表（LUNCH/DINNER）")
    private List<String> mealTypes;

    @ApiModelProperty(value = "套餐类型/菜品类型")
    private String dishType;

    @ApiModelProperty(value = "页码")
    private Integer page = 0;

    @ApiModelProperty(value = "每页数量")
    private Integer size = 10;
}
