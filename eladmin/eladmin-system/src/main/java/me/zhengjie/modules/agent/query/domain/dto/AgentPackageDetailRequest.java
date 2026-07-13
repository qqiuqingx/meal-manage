package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/** Agent 套餐详情受控请求。 */
@Data
public class AgentPackageDetailRequest {
    /** 父套餐 ID。 */
    @NotNull
    @Positive
    private Long parentPackageId;
}
