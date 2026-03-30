package me.zhengjie.modules.customer.pkg.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 子套餐实体
 */
@Data
@TableName("sub_package")
public class SubPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 子套餐编码
     */
    private String subPackageCode;

    /**
     * 子套餐名称
     */
    private String subPackageName;

    /**
     * 荤菜数量
     */
    private Integer meatCount;

    /**
     * 素菜数量
     */
    private Integer vegCount;

    /**
     * 是否含汤（1=是，0=否）
     */
    private Boolean includeSoup;

    /**
     * 是否含米饭（1=是，0=否）
     */
    private Boolean includeRice;

    /**
     * 状态（1=启用，0=停用）
     */
    private Boolean status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updateTime;
}
