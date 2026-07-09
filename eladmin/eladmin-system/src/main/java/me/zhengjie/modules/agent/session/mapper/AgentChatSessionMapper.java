package me.zhengjie.modules.agent.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.agent.session.domain.AgentChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能排查会话 Mapper。
 */
@Mapper
public interface AgentChatSessionMapper extends BaseMapper<AgentChatSession> {
}
