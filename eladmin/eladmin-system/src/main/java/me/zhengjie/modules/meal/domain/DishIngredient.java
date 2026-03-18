package me.zhengjie.modules.meal.domain;

import lombok.Data;
import cn.hutool.core.bean.BeanUtil;
import io.swagger.annotations.ApiModelProperty;
import cn.hutool.core.bean.copier.CopyOptions;
import javax.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 配料实体
 * @author qqx
 * @date 2026-03-15
 **/
@Data
@TableName("dish_ingredient")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DishIngredient implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;

    @NotBlank(message = "配料名称不能为空")
    @ApiModelProperty(value = "配料名称")
    private String name;

    @ApiModelProperty(value = "配料分类：MEAT肉类、VEGETABLE蔬菜、SEAFOOD海鲜、TOFU豆制品、SPICE调料、OTHER其他")
    private String category;

    @ApiModelProperty(value = "单位：克g、毫升ml、个")
    private String unit;

    @ApiModelProperty(value = "每单位热量（卡路里）")
    private Integer calories;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "是否启用")
    private Boolean enabled;

    @ApiModelProperty(value = "创建时间")
    private Timestamp createTime;

    @ApiModelProperty(value = "更新时间")
    private Timestamp updateTime;

    public void copy(DishIngredient source){
        BeanUtil.copyProperties(source,this, CopyOptions.create().setIgnoreNullValue(true));
    }
}
