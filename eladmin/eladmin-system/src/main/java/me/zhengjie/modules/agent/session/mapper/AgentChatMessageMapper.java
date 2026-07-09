package me.zhengjie.modules.agent.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.agent.session.domain.AgentChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能排查消息 Mapper。
 */
@Mapper
public interface AgentChatMessageMapper extends BaseMapper<AgentChatMessage> {
}
