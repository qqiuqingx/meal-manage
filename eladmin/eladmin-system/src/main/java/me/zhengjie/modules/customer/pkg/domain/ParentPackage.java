package me.zhengjie.modules.customer.pkg.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 父套餐实体
 */
@Data
@TableName("parent_package")
public class ParentPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 套餐编码
     */
    private String packageCode;

    /**
     * 编号前缀（单个大写字母）
     */
    private String prefix;

    /**
     * 套餐名称
     */
    private String packageName;

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
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
