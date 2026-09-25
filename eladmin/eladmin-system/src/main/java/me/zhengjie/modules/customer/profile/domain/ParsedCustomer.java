package me.zhengjie.modules.customer.profile.domain;

import lombok.Data;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportAddressDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 从工作簿聚合出来的一位客户草稿（未做数据库校验，不含套餐归属）。
 *
 * <p>解析器只负责把同一位客户的连续续行合并成一条草稿并给出源行范围，
 * 父套餐映射、编号池校验、重复建档判定由导入服务在只读校验阶段补齐。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class ParsedCustomer implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 归属本客户的源工作表行号（1 基），按升序排列
     */
    private List<Integer> sourceRows = new ArrayList<>();

    /**
     * 原始「编号」列取值（A 列）
     */
    private String originalCodeA;

    /**
     * 原始「新编号」列取值（B 列）
     */
    private String originalCodeB;

    /**
     * 有效编号：B 列非空取 B，否则取 A
     */
    private String effectiveCode;

    /**
     * 规范化后的手机号（仅保留数字），仅用于校验与去重，不写日志
     */
    private String phoneNormalized;

    /**
     * 地址列电话标签中的配送电话原文，保留全部号码和原有分隔方式。
     */
    private String deliveryPhoneInfo;

    /**
     * 客户姓名，与有效编号一致
     */
    private String customerName;

    /**
     * 地址槽位
     */
    private List<CustomerImportAddressDto> addresses = new ArrayList<>();

    /**
     * 「备注信息」列（E）内容，写入客户备注
     */
    private String remark;

    /**
     * 「特殊要求」列（F）内容，写入客户特殊要求
     */
    private String specialRequirements;

    /**
     * 是否暂停：备注、特殊要求或送餐描述命中「等通知」
     */
    private boolean paused;

    /**
     * 送餐模式：DAILY / WEEKDAY / WEEKEND / SCHEDULE
     */
    private String scheduleMode;

    /**
     * 餐次类型：ALL / LUNCH / DINNER / LUNCH_DINNER；未指定时为空
     */
    private String mealType;

    /**
     * 午晚餐明细行「餐数」列（I）之和，不包含本批次跳过的早餐行
     */
    private Integer sheetMealCount;

    /**
     * 「剩余餐数」列（J）公式值，作为待导入午晚餐数的基础
     */
    private Integer sheetRemainingCount;

    /**
     * 日格合计（CW）公式值，用于交叉核对公式缓存
     */
    private Integer sheetGridTotal;

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
     * 订单默认汤数：含汤 1 / 不含汤 0
     */
    private Integer soupCount;

    /**
     * 导入日期之后的非零午晚餐格
     */
    private List<CustomerImportMealCellDto> futureMealCells = new ArrayList<>();

    /**
     * 是否有非零的未来午晚餐格
     */
    private boolean hasFutureMealCell;

    /**
     * 非阻塞性提示
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 阻塞性问题：非空表示该客户本次不导入
     */
    private List<CustomerImportIssueDto> issues = new ArrayList<>();

    /**
     * 追加一条阻塞性问题。
     *
     * @param category 问题分类
     * @param message 原因描述
     */
    public void addIssue(CustomerImportIssueCategory category, String message) {
        Integer row = sourceRows.isEmpty() ? null : sourceRows.get(0);
        issues.add(CustomerImportIssueDto.of(category, row, effectiveCode, message));
    }

    /**
     * 追加一条非阻塞提示。
     *
     * @param message 提示内容
     */
    public void addWarning(String message) {
        warnings.add(message);
    }

    /**
     * 判断该客户是否可以进入导入。
     *
     * @return true 表示无阻塞性问题
     */
    public boolean isImportable() {
        return issues.isEmpty();
    }
}
