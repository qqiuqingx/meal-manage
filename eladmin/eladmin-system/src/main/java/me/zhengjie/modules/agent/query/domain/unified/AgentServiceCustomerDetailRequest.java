package me.zhengjie.modules.agent.query.domain.unified;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 单客户或单订单服务客户详情请求。 */
@Data
public class AgentServiceCustomerDetailRequest {
    /** 客户稳定 ID。 */ @Min(1) private Long customerId;
    /** 客户编号。 */ @Size(max = 64) private String customerCode;
    /** 订单稳定 ID。 */ @Min(1) private Long orderId;
    /** 订单编号。 */ @Size(max = 64) private String orderCode;
    /** 详情级别，仅允许 STANDARD 或 DIAGNOSTIC。 */ @Pattern(regexp = "STANDARD|DIAGNOSTIC") private String detailLevel = "STANDARD";
}
