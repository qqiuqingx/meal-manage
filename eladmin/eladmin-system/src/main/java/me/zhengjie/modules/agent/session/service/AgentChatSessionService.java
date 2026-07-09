package me.zhengjie.modules.agent.session.service;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionCreateRequest;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionDetailDto;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionQueryCriteria;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionSummaryDto;
import me.zhengjie.utils.PageResult;

/**
 * 智能排查会话服务。
 */
public interface AgentChatSessionService {

    /**
     * 创建一个新的智能排查会话。
     *
     * @param request 会话初始化信息
     * @return 会话摘要
     */
    AgentChatSessionSummaryDto createSession(AgentChatSessionCreateRequest request);

    /**
     * 分页查询当前客服可见的会话列表。
     *
     * @param criteria 查询条件
     * @return 会话分页结果
     */
    PageResult<AgentChatSessionSummaryDto> querySessions(AgentChatSessionQueryCriteria criteria);

    /**
     * 查询单个会话详情，返回消息、最近诊断和审计追踪信息。
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    AgentChatSessionDetailDto getSession(String sessionId);

    /**
     * 更新会话标题。
     *
     * @param sessionId 会话ID
     * @param title 新标题
     */
    void updateTitle(String sessionId, String title);

    /**
     * 更新会话归档状态。
     *
     * @param sessionId 会话ID
     * @param archived 是否归档
     */
    void updateArchiveStatus(String sessionId, boolean archived);

    /**
     * 处理聊天消息并持久化用户与助手消息。
     *
     * @param request 聊天请求
     * @param requestId 请求链路ID
     * @return 助手响应
     */
    AgentChatResponse chat(AgentChatRequest request, String requestId);
}
