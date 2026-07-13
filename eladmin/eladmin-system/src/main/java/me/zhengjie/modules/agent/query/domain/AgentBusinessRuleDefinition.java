package me.zhengjie.modules.agent.query.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 版本化 Agent 业务规则目录条目，仅允许由主系统资源文件加载。 */
@Data
public class AgentBusinessRuleDefinition {
    /** 稳定规则 ID。 */ private String ruleId;
    /** 白名单查询主题及同义主题。 */ private List<String> topics = new ArrayList<>();
    /** 规则版本。 */ private String version;
    /** 面向客服的规则主题。 */ private String title;
    /** 经过审核的只读说明。 */ private String content;
    /** 规则责任模块。 */ private String ownerModule;
    /** 业务依据文档相对路径。 */ private String evidenceDocument;
    /** 规则最后更新时间。 */ private String updatedAt;
}
