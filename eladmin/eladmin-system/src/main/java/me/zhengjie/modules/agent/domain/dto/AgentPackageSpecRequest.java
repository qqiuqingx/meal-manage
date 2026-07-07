package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查套餐规格查询请求。
 */
@Data
public class AgentPackageSpecRequest {
    private Long customerId;
    private String customerCode;
    private Long parentPackageId;
    private Long childPackageId;
}
