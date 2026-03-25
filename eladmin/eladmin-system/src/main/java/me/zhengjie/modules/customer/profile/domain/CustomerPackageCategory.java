package me.zhengjie.modules.customer.profile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户套餐分类实体
 */
@Data
@TableName("customer_package_category")
public class CustomerPackageCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 分类编码(全局唯一)
     */
    private String categoryCode;

    /**
     * 父级ID(顶级为NULL)
     */
    private Long parentId;

    /**
     * 层级(1=父级,2=子级)
     */
    private Integer level;

    /**
     * 排序(越小越靠前)
     */
    private Integer sort;

    /**
     * 是否启用(0=否,1=是)
     */
    private Boolean enabled;

    /**
     * 编号前缀(仅父级使用,单个大写字母)
     */
    private String codePrefix;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 子级分类列表(非数据库字段,用于树形结构)
     */
    @TableField(exist = false)
    private List<CustomerPackageCategory> children = new ArrayList<>();
}