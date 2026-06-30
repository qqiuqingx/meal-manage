package me.zhengjie.modules.customer.orderReplaceRule.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import me.zhengjie.base.BaseEntity;

/**
 * 订单换菜规则
 * @author qqx
 * @date 2026-05-06
 **/
@Getter
@Setter
@TableName("customer_order_replace_rule")
public class CustomerOrderReplaceRule extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "订单ID")
    private Long orderId;

    @ApiModelProperty(value = "原菜品ID")
    private Long sourceDishId;

    @ApiModelProperty(value = "原菜品名称(快照)")
    private String sourceDishName;

    @ApiModelProperty(value = "原菜品类型(快照)")
    private String sourceDishType;

    @ApiModelProperty(value = "目标菜品ID")
    private Long targetDishId;

    @ApiModelProperty(value = "目标菜品名称(快照)")
    private String targetDishName;

    @ApiModelProperty(value = "目标菜品类型(快照)")
    private String targetDishType;

    @ApiModelProperty(value = "是否启用")
    private Boolean enabled;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "是否删除")
    private Boolean deleted;
}
