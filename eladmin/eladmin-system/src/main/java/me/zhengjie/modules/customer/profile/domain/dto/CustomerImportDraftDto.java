package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户批量导入的逐位客户草稿。
 *
 * <p>预览与提交结果共用该结构：预览时它是「将写入什么」的说明，提交时它是
 * 「实际写入结果」的说明。不含手机号、地址等个人敏感字段的完整值。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class CustomerImportDraftDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 来源工作表行号（1 基），按升序排列；续行会包含多个行号
     */
    private List<Integer> sourceRows = new ArrayList<>();

    /**
     * 原始「编号」列取值
     */
    private String originalCodeA;

    /**
     * 原始「新编号」列取值
     */
    private String originalCodeB;

    /**
     * 有效编号，同时作为客户姓名与地址联系人名
     */
    private String customerCode;

    /**
     * 脱敏手机号，仅用于操作人核对
     */
    private String phoneMasked;

    /**
     * 父套餐名称；未匹配成功时为空
     */
    private String parentPackageName;

    /**
     * 父套餐ID；未匹配成功时为空
     */
    private Long parentPackageId;

    /**
     * 是否写入暂停状态（备注、特殊要求或送餐描述命中「等通知」）
     */
    private Boolean paused;

    /**
     * 送餐模式：DAILY / WEEKDAY / WEEKEND / SCHEDULE
     */
    private String scheduleMode;

    /**
     * 餐次类型：ALL / LUNCH / DINNER / LUNCH_DINNER；「等通知送餐」时为空
     */
    private String mealType;

    /**
     * 导入的早餐餐数，本批次恒为 0
     */
    private Integer breakfastCount;

    /**
     * 导入的午晚餐购买数，取工作簿午晚餐明细行「餐数」之和
     */
    private Integer lunchDinnerCount;

    /**
     * 导入日期及之前的历史已核销餐数 = 购买数 - (来源剩余餐数 + 未来日格份数)
     */
    private Integer importedVerifiedCount;

    /**
     * 订单合计餐数
     */
    private Integer totalCount;

    /**
     * 来源工作簿的「剩余餐数」列取值
     */
    private Integer sheetRemainingCount;

    /**
     * 未来日格份数之和
     */
    private Integer futureMealCount;

    /**
     * 每餐主菜数量
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
     * 米饭类型
     */
    private String riceType;

    /**
     * 每餐汤数量
     */
    private Integer soupCount;

    /**
     * 地址槽位
     */
    private List<CustomerImportAddressDto> addresses = new ArrayList<>();

    /**
     * 客户备注（来源「备注信息」列）
     */
    private String remark;

    /**
     * 客户特殊要求（来源「特殊要求」列）
     */
    private String specialRequirements;

    /**
     * 未来逐餐计划
     */
    private List<CustomerImportMealCellDto> mealCells = new ArrayList<>();

    /**
     * 非阻塞提示
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 阻塞性问题描述；非空表示本次不导入
     */
    private List<String> errors = new ArrayList<>();

    /**
     * 是否可以导入
     */
    private Boolean importable;
}
