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
     * 开始餐次(BREAKFAST/LUNCH/DINNER)
     */
    private String startMealType;

    /**
     * 订单结束日期
     */
    private LocalDate endDate;

    /**
     * 订单状态(0=已取消,1=进行中,2=已完成,3=已退餐)
     */
    private Integer status;

    /**
     * 餐次类型(ALL=早+午餐+晚餐,LUNCH=午餐,DINNER=晚餐,LUNCH_DINNER=午餐+晚餐)
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
     * 是否试餐成单
     */
    private Boolean trialConverted;

    /**
     * 关联试餐订单ID
     */
    private Long trialOrderId;

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
     * 预计剩余餐数(当前剩余餐数 - 今日已排未核销餐数)
     */
    @TableField(exist = false)
    private Integer estimatedRemainingCount;

    /**
     * 已排餐餐数（有效排餐记录总数）
     */
    @TableField(exist = false)
    private Integer scheduledCount;

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
     * 客户特殊要求(查询时填充，来自 customer_profile.special_requirements)
     */
    @TableField(exist = false)
    private String specialRequirements;

    /**
     * 客户地址列表(查询时填充，来自 customer_profile_address)
     */
    @TableField(exist = false)
    private List<AddressInfo> addresses;

    /**
     * 关联试餐订单编号(查询时填充)
     */
    @TableField(exist = false)
    private String trialOrderCode;

    @Data
    public static class AddressInfo implements Serializable {
        private String type;
        private String detail;
    }

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
     * 每餐米饭数量（默认1，页面不展示）
     */
    private Integer riceCount;

    /**
     * 米饭类型（普通杂粮米饭、杂粮1:1米饭、三色糙米、白米饭）
     */
    private String riceType;

    /**
     * 每餐汤数量
     */
    private Integer soupCount;

    /**
     * 自定义菜单图片地址，用于客户换菜参考
     */
    private String customMenuImage;
}
