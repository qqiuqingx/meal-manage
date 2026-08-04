package me.zhengjie.modules.agent.query.domain.unified;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 以订单为根的服务客户分页查询请求。 */
@Data
public class AgentServiceCustomerSearchRequest {
    /** 客户稳定 ID。 */ @Min(1) private Long customerId;
    /** 客户编号。 */ @Size(max = 64) private String customerCode;
    /** 订单稳定 ID。 */ @Min(1) private Long orderId;
    /** 订单编号。 */ @Size(max = 64) private String orderCode;
    /** 服务客户状态枚举。 */ @Pattern(regexp = "ALL|ACTIVE|CANCELLED|COMPLETED|REFUNDED") private String status = "ALL";
    /** 成交日期下界。 */ @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String dealTimeFrom;
    /** 成交日期上界。 */ @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String dealTimeTo;
    /** 父或子套餐编码。 */ @Size(max = 64) private String packageCode;
    /** 页码。 */ @Min(1) private Integer page = 1;
    /** 单页数量，服务端上限 20。 */ @Min(1) @Max(20) private Integer size = 20;
}
