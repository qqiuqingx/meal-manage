package me.zhengjie.modules.customer.profile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;

/**
 * 客户档案主档实体
 */
@Data
@TableName("customer_profile")
public class CustomerProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客户编号(唯一,如A001)
     */
    private String customerCode;

    /**
     * 客户姓名
     */
    private String customerName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 孕周(正整数)
     */
    private Integer gestationalWeek;

    /**
     * 过敏食物标签(JSON数组)
     */
    @TableField(value = "allergy_tags", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<String> allergyTags;

    /**
     * 排除菜品ID列表(JSON数组)
     */
    @TableField(value = "excluded_dish_ids", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Integer> excludedDishIds;

    /**
     * 排除日期列表(JSON数组)
     */
    @TableField(value = "excluded_dates", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<ExcludedDateDto> excludedDates;

    /**
     * 判断指定日期+餐次是否被排除
     * @param date 目标日期
     * @param mealType 餐次类型（LUNCH/DINNER）
     * @return true=被排除，应跳过该日期的排餐
     */
    public boolean isExcluded(LocalDate date, String mealType) {
        if (excludedDates == null || excludedDates.isEmpty()) {
            return false;
        }
        String targetDateStr = date.toString();  // ISO-8601: "yyyy-MM-dd"
        for (ExcludedDateDto excluded : excludedDates) {
            if (targetDateStr.equals(excluded.getDate())
                    && excluded.getMealTypes() != null
                    && excluded.getMealTypes().contains(mealType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 医嘱要求
     */
    private String medicalRequirements;

    //
    private String remark;

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
     * 默认地址(查询时填充)
     */
    @TableField(exist = false)
    private String defaultAddress;

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
     * 早餐数
     */
    @TableField(exist = false)
    private Integer breakfastCount;

    /**
     * 午餐+晚餐数
     */
    @TableField(exist = false)
    private Integer lunchDinnerCount;

    /**
     * 总份数
     */
    @TableField(exist = false)
    private Integer totalCount;

    /**
     * 签约开始日期
     */
    @TableField(exist = false)
    private String startDate;

    /**
     * 签约结束日期
     */
    @TableField(exist = false)
    private String endDate;

    /**
     * 剩余早餐数(查询时计算)
     */
    @TableField(exist = false)
    private Integer remainingBreakfastCount;

    /**
     * 剩余午晚数(午餐+晚餐)(查询时计算)
     */
    @TableField(exist = false)
    private Integer remainingLunchDinnerCount;
}