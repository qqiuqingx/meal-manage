package me.zhengjie.modules.agent.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.agent.session.domain.AgentChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 智能排查会话 Mapper。
 */
@Mapper
public interface AgentChatSessionMapper extends BaseMapper<AgentChatSession> {

    /**
     * 在聊天事务内锁定指定会话，使同一 session 的不同消息按数据库顺序提交。
     *
     * @param sessionId 会话业务 ID
     * @return 已加行锁的会话；不存在时返回 null
     */
    @Select("SELECT * FROM agent_chat_session WHERE session_id = #{sessionId} LIMIT 1 FOR UPDATE")
    AgentChatSession selectBySessionIdForUpdate(@Param("sessionId") String sessionId);
}
