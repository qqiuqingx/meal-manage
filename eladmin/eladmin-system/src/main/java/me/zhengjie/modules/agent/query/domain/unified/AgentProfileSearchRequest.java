package me.zhengjie.modules.agent.query.domain.unified;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/** Agent 客户档案分页查询请求。 */
@Data
public class AgentProfileSearchRequest {
    /** 客户稳定 ID。 */ @Min(1) private Long customerId;
    /** 客户编号。 */ @Size(max = 64) private String customerCode;
    /** 客户姓名关键字。 */ @Size(max = 64) private String customerName;
    /** 是否已下单。 */ private Boolean hasOrder;
    /** 页码。 */ @Min(1) private Integer page = 1;
    /** 单页数量，服务端上限 20。 */ @Min(1) @Max(20) private Integer size = 20;
}
