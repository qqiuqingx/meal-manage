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

    /** CREATE/PROFILE_ONLY/NEW_ORDER/UPDATE_MONTH/UPDATE_SAME_MONTH/BACKFILL_MONTH。 */
    private String importAction;
    /** 目标订单ID，首次创建时为空。 */
    private Long targetOrderId;
    /** 面向操作人的目标订单编号。 */
    private String targetOrderCode;
    /** 本表所属月份与当前已采用月份，格式 yyyy-MM。 */
    private String sourceMonth;
    private String currentImportMonth;
    /** 订单更新前与确认后购买数、可用餐数，旧月份不改余额。 */
    private Integer currentMealCount;
    private Integer currentRemainingCount;
    private Integer afterMealCount;
    private Integer afterRemainingCount;
    /** 真实消费发生在本表覆盖期之后，保留其扣减而不因重传返还。 */
    private Integer postSnapshotVerifiedCount;
    /** 当前与导入后的订单状态，暂停/取消/退餐不由续导自动改变。 */
    private Integer currentOrderStatus;
    private Integer afterOrderStatus;
    /** 本月非零日格的新增、变更与移除数量。 */
    private Integer addedMealCellCount;
    private Integer changedMealCellCount;
    private Integer removedMealCellCount;

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
     * 本次采用的历史核销基数：首单=购买数-(J+未来)，续导还扣除已有真实核销；旧月沿用原基数
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

    /** E 列医嘱原文及目标客户字段值。 */
    private String medicalRequirements;

    /** G 列来源成交时间原文。 */
    private String dealTimeSource;

    /** G 列解析成交时间，格式 yyyy-MM-dd HH:mm:ss。 */
    private String dealTime;

    /** 成交时间为空时由确认请求开始时间补齐。 */
    private Boolean dealTimeAtConfirmation;

    /** H 列术后原文。 */
    private String postoperativeInfo;

    /** D 列完整原文块。 */
    private List<String> dishRequirementsRaw = new ArrayList<>();

    /** F 列完整原文块。 */
    private List<String> dietaryRestrictionsRaw = new ArrayList<>();

    /** D/F 列匹配词项、候选与状态。 */
    private List<CustomerDietMatchDto> dietMatches = new ArrayList<>();

    /** 本候选是否只补录已存在客户资料。 */
    private Boolean supplemental;

    /**
     * 未来逐餐计划
     */
    private List<CustomerImportMealCellDto> mealCells = new ArrayList<>();

    /** 导入日期及之前的逐餐历史数量，不参与未来餐数计算。 */
    private List<CustomerImportMealCellDto> historicalMealCells = new ArrayList<>();

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
