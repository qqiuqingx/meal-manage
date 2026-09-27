package me.zhengjie.modules.meal.domain;

import lombok.Data;
import cn.hutool.core.bean.BeanUtil;
import io.swagger.annotations.ApiModelProperty;
import cn.hutool.core.bean.copier.CopyOptions;
import javax.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagDto;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;

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

    @ApiModelProperty(value = "二级分类ID")
    private Integer categoryId;

    @ApiModelProperty(value = "历史分类枚举（保留读写；不再根据分类名称自动生成，当前归属使用 categoryId）")
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

    @TableField(exist = false)
    @ApiModelProperty(value = "二级分类名称（非数据库字段）")
    private String categoryName;

    @TableField(exist = false)
    @ApiModelProperty(value = "一级分类ID（非数据库字段）")
    private Integer parentCategoryId;

    @TableField(exist = false)
    @ApiModelProperty(value = "一级分类名称（非数据库字段）")
    private String parentCategoryName;

    @TableField(exist = false)
    @ApiModelProperty(value = "分类路径（非数据库字段，如：蔬菜/瓜类）")
    private String categoryPathName;

    /** 配料写入时可选的标签ID集合。 */
    @TableField(exist = false)
    @ApiModelProperty(value = "关联标签ID集合；新增未传时为空，编辑未传时保持原标签")
    private List<Integer> tagIds;

    /** 查询结果中的标签ID和名称列表。 */
    @TableField(exist = false)
    @ApiModelProperty(value = "配料关联的标签详情")
    private List<DishIngredientTagDto> tags;

    public void copy(DishIngredient source){
        BeanUtil.copyProperties(source,this, CopyOptions.create().setIgnoreNullValue(true));
    }
}
