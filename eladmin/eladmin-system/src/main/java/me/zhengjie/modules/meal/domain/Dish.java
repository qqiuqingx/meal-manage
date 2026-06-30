package me.zhengjie.modules.meal.domain;

import lombok.Data;
import cn.hutool.core.bean.BeanUtil;
import io.swagger.annotations.ApiModelProperty;
import cn.hutool.core.bean.copier.CopyOptions;
import javax.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import java.util.List;
import java.io.Serializable;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

/**
 * 菜品实体
 * @author qqx
 * @date 2026-03-14
 **/
@Data
@TableName(value = "dish", autoResultMap = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dish implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;

    @NotBlank(message = "菜品名称不能为空")
    @ApiModelProperty(value = "菜品名称")
    @TableField("name")
    private String name;

    @ApiModelProperty(value = "做法/流程")
    @TableField("cooking_method")
    private String cookingMethod;

    @ApiModelProperty(value = "配料")
    @TableField("ingredients")
    private String ingredients;

    @ApiModelProperty(value = "切配信息")
    @TableField("cutting_info")
    private String cuttingInfo;

    @ApiModelProperty(value = "配料列表（新增/编辑时使用）")
    @TableField(exist = false)
    private List<DishIngredientDto> ingredientList;

    @ApiModelProperty(value = "图片路径")
    @TableField("image_url")
    private String imageUrl;

    @NotBlank(message = "菜品类型不能为空")
    @ApiModelProperty(value = "菜品类型：MAIN主菜、SIDE副菜、SOUP汤、VEGETABLE素菜、RICE米饭")
    @TableField("dish_type")
    private String dishType;

    @ApiModelProperty(value = "餐次：LUNCH午餐、DINNER晚餐")
    @TableField(value = "meal_types", typeHandler = JacksonTypeHandler.class)
    private List<String> mealTypes;

    @ApiModelProperty(value = "所属套餐（父套餐ID列表）")
    @TableField(value = "meal_packages", typeHandler = JacksonTypeHandler.class)

    private List<String> mealPackages;

    @ApiModelProperty(value = "所属套餐详情列表（查询时显示用，非数据库字段）")
    @TableField(exist = false)
    private List<PackageInfo> mealPackageDetails;

    @ApiModelProperty(value = "餐次信息（查询时显示用，非数据库字段）")
    @TableField(exist = false)
    private java.util.Set<String> mealTimeInfo;

    @ApiModelProperty(value = "排期：格式如1-1表示第1周周一")
    @TableField(value = "schedule", typeHandler = JacksonTypeHandler.class)
    private List<String> schedule;

    @ApiModelProperty(value = "排序")
    @TableField("sort")
    private Integer sort;

    @ApiModelProperty(value = "是否启用")
    @TableField("enabled")
    private Boolean enabled;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private Timestamp createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private Timestamp updateTime;

    public void copy(Dish source){
        BeanUtil.copyProperties(source,this, CopyOptions.create().setIgnoreNullValue(true));
    }

    /**
     * 套餐简要信息（用于 API 返回）
     */
    @Data
    public static class PackageInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String packageCode;
        private String packageName;

        public PackageInfo() {}

        public PackageInfo(Long id, String packageCode, String packageName) {
            this.id = id;
            this.packageCode = packageCode;
            this.packageName = packageName;
        }
    }
}
