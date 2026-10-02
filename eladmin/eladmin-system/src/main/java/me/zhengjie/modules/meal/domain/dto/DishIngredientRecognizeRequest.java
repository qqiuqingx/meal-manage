package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Size;

/** 制作流程配料名称识别请求；只读识别不保存菜品。 */
@Getter
@Setter
@ApiModel("制作流程配料识别请求")
public class DishIngredientRecognizeRequest {

    @Size(max = 10000, message = "制作流程不能超过10000字符")
    @ApiModelProperty(value = "制作流程；空文本返回空数组，最多10000字符")
    private String cookingMethod;
}
