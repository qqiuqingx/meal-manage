package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentPackageSpecDto;

/** Agent 套餐只读查询服务。 */
public interface AgentPackageQueryService {
    /** 根据父套餐 ID 查询父子关系及子套餐规格。 */
    AgentPackageSpecDto getDetail(Long parentPackageId);
}
