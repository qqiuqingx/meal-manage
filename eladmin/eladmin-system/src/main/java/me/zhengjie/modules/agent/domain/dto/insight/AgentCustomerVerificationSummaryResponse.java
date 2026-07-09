package me.zhengjie.modules.agent.domain.dto.insight;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 客户核销统计响应 DTO
 * @author qqx
 * @date 2026-07-09
 */
@Getter
@Setter
public class AgentCustomerVerificationSummaryResponse {

    /** 客户是否存在 */
    private boolean present = true;

    /** 客户 ID */
    private Long customerId;

    /** 客户编号 */
    private String customerCode;

    /** 客户姓名 */
    private String customerName;

    /** 累计核销早餐 */
    private int totalVerifiedBreakfast;

    /** 累计核销午餐 */
    private int totalVerifiedLunch;

    /** 累计核销晚餐 */
    private int totalVerifiedDinner;

    /** 累计核销合计 */
    private int totalVerified;

    /** 最近核销记录 */
    private List<AgentCustomerVerificationLogItem> recentVerifications;
}
