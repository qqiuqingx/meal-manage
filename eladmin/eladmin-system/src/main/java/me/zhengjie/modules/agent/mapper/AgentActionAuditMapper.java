package me.zhengjie.modules.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.agent.domain.AgentActionAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能排查动作确认审计 Mapper。
 */
@Mapper
public interface AgentActionAuditMapper extends BaseMapper<AgentActionAudit> {
}
