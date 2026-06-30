package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 排餐坑位批量操作结果
 * @author qqx
 * @date 2026-04-13
 **/
@Data
public class MealSchedulePlanBatchResult {

    @ApiModelProperty(value = "成功数量")
    private Integer successCount = 0;

    @ApiModelProperty(value = "失败数量")
    private Integer failCount = 0;

    @ApiModelProperty(value = "失败项")
    private List<String> failedItems = new ArrayList<>();
}
