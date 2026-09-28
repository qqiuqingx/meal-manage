package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

/**
 * 菜品标签新增和修改请求。
 */
@Getter
@Setter
public class DishTagSaveDto {

    /** 修改时必填的标签ID；新增时忽略。 */
    @ApiModelProperty(value = "标签ID，修改时必填")
    private Integer id;

    /** 标签名称；服务端会去除首尾空白并校验长度和唯一性。 */
    @NotBlank(message = "标签名称不能为空")
    @ApiModelProperty(value = "标签名称")
    private String name;
}
