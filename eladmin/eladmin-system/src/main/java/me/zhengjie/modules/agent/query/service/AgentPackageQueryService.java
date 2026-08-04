package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentPackageSpecDto;

/** Agent 套餐只读查询服务。 */
public interface AgentPackageQueryService {
    /** 根据父套餐 ID 查询父子关系及子套餐规格。 */
    AgentPackageSpecDto getDetail(Long parentPackageId);

    /**
     * 按父套餐编码查询父子套餐规格。
     *
     * @param packageCode 父套餐业务编码
     * @return 套餐不存在时返回 present=false
     */
    AgentPackageSpecDto getDetailByCode(String packageCode);
}
