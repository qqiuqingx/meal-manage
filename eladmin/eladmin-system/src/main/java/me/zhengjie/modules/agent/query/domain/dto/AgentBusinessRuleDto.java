package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** Agent 可解释的版本化业务规则条目。 */
@Data
public class AgentBusinessRuleDto {
    /** 规则是否存在。 */ private boolean present;
    /** 稳定规则 ID。 */ private String ruleId;
    /** 规则版本。 */ private String version;
    /** 规则主题。 */ private String topic;
    /** 结构化规则说明。 */ private String content;
    /** 责任模块。 */ private String ownerModule;
    /** 业务依据文档相对路径。 */ private String evidenceDocument;
    /** 更新时间。 */ private String updatedAt;
}
