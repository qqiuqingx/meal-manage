package me.zhengjie.modules.customer.profile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户签约实体
 */
@Data
@TableName("customer_profile_package")
public class CustomerProfilePackage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 父套餐ID
     */
    private Long parentPackageId;

    /**
     * 子套餐ID
     */
    private Long childPackageId;

    /**
     * 早餐数
     */
    private Integer breakfastCount;

    /**
     * 午餐+晚餐数
     */
    private Integer lunchDinnerCount;

    /**
     * 总份数(后端计算)
     */
    private Integer totalCount;

    /**
     * 签约开始日期
     */
    private LocalDate startDate;

    /**
     * 签约结束日期
     */
    private LocalDate endDate;

    /**
     * 生效标志(0=失效,1=生效)
     */
    private Boolean activeFlag;

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

    // ========== 非数据库字段 ==========

    /**
     * 父套餐名称(查询时填充)
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String parentPackageName;

    /**
     * 子套餐名称(查询时填充)
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String childPackageName;
}