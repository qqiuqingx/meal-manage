package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentBusinessRuleDto;

/** Agent 版本化业务规则只读查询服务。 */
public interface AgentBusinessRuleQueryService {
    /** 根据白名单主题返回规则，不支持任意文档路径。 */
    AgentBusinessRuleDto explain(String topic);
}
