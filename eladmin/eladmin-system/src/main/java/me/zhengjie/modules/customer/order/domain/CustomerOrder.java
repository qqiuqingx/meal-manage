package me.zhengjie.modules.customer.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户订单实体
 */
@Data
@TableName("customer_order")
public class CustomerOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户编号
     */
    private String customerCode;

    /**
     * 父套餐ID
     */
    private Long parentPackageId;

    /**
     * 子套餐ID
     */
    private Long childPackageId;

    /**
     * 订单编号
     */
    private String orderCode;

    /**
     * 定金金额
     */
    private BigDecimal depositAmount;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 成交金额
     */
    private BigDecimal finalAmount;

    /**
     * 早餐合计餐数
     */
    private Integer breakfastCount;

    /**
     * 午餐+晚餐合计餐数
     */
    private Integer lunchDinnerCount;

    /**
     * 早餐单价
     */
    private BigDecimal breakfastPrice;

    /**
     * 午餐晚餐单价
     */
    private BigDecimal lunchDinnerPrice;

    /**
     * 核销餐数(合计)
     */
    private Integer verifiedCount;

    /**
     * 核销金额
     */
    private BigDecimal verifiedAmount;

    /**
     * 餐费余额
     */
    private BigDecimal mealBalance;

    /**
     * 剩余餐数
     */
    private Integer remainingCount;

    /**
     * 成交时间
     */
    private LocalDateTime dealTime;

    /**
     * 第一次送餐时间
     */
    private LocalDateTime firstDeliveryTime;

    /**
     * 订单开始日期
     */
    private LocalDate startDate;

    /**
     * 订单结束日期
     */
    private LocalDate endDate;

    /**
     * 订单状态(0=已取消,1=进行中,2=已完成)
     */
    private Integer status;

    /**
     * 餐次类型(LUNCH=午餐订单,DINNER=晚餐订单,ALL=全餐次订单)
     */
    private String mealType;

    /**
     * 排餐模式(SCHEDULE=指定日期送,DAILY=每天送,WEEKEND=周末送,WEEKDAY=工作日送)
     */
    private String scheduleMode;

    /**
     * 送餐日期(JSON数组格式,如["2026-04-01","2026-04-03"])
     */
    private String deliveryDates;

    /**
     * 备注
     */
    private String remark;

    /**
     * 销售渠道
     */
    private String customerSource;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

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
     * 客户姓名(查询时填充)
     */
    @TableField(exist = false)
    private String customerName;

    /**
     * 客户手机号(查询时填充)
     */
    @TableField(exist = false)
    private String phone;

    /**
     * 合计餐数(查询时计算)
     */
    @TableField(exist = false)
    private Integer totalCount;

    /**
     * 父套餐名称(查询时填充)
     */
    @TableField(exist = false)
    private String parentPackageName;

    /**
     * 子套餐名称(查询时填充)
     */
    @TableField(exist = false)
    private String childPackageName;

    /**
     * 客户过敏标签(查询时填充，来自 customer_profile.allergy_tags)
     */
    @TableField(exist = false)
    private List<String> allergyTags;

    /**
     * 每餐主菜/荤菜数量
     */
    private Integer mainDishCount;

    /**
     * 每餐副菜数量
     */
    private Integer sideDishCount;

    /**
     * 每餐素菜数量
     */
    private Integer vegCount;

    /**
     * 每餐米饭数量
     */
    private Integer riceCount;

    /**
     * 每餐汤数量
     */
    private Integer soupCount;
}
