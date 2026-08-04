package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/** Agent 套餐详情受控请求。 */
@Data
public class AgentPackageDetailRequest {
    /** 父套餐稳定 ID，可与套餐编码二选一。 */
    @Min(1)
    private Long packageId;
    /** 父套餐业务编码，可与 ID 二选一。 */
    @Size(max = 64)
    private String packageCode;
}
