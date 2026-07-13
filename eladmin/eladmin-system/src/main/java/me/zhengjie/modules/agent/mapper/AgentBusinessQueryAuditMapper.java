package me.zhengjie.modules.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.agent.domain.AgentBusinessQueryAudit;
import org.apache.ibatis.annotations.Mapper;

/** Agent 只读业务查询审计 Mapper。 */
@Mapper
public interface AgentBusinessQueryAuditMapper extends BaseMapper<AgentBusinessQueryAudit> {
}
