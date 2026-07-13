package me.zhengjie.modules.agent.session.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.exception.EntityNotFoundException;
import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.modules.agent.mapper.AgentActionAuditMapper;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisFeedbackMapper;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import me.zhengjie.modules.agent.service.AgentBusinessQueryAuditService;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.modules.agent.session.domain.AgentChatMessage;
import me.zhengjie.modules.agent.session.domain.AgentChatSession;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatMessageDto;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionCreateRequest;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionDetailDto;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionQueryCriteria;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionSummaryDto;
import me.zhengjie.modules.agent.session.mapper.AgentChatMessageMapper;
import me.zhengjie.modules.agent.session.mapper.AgentChatSessionMapper;
import me.zhengjie.modules.agent.session.service.AgentChatSessionService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.PageUtil;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 智能排查会话服务实现。
 */
@Service
@RequiredArgsConstructor
public class AgentChatSessionServiceImpl implements AgentChatSessionService {

    private static final String DEFAULT_STAGE = "COLLECTING_SLOTS";
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    private final AgentChatSessionMapper sessionMapper;
    private final AgentChatMessageMapper messageMapper;
    private final AgentActionAuditMapper actionAuditMapper;
    private final AgentDiagnosisFeedbackMapper feedbackMapper;
    private final AgentDiagnosisFacadeService diagnosisFacadeService;
    private final AgentAccessContextService accessContextService;
    private final AgentBusinessQueryAuditService businessQueryAuditService;

    /**
     * 创建一个新的智能排查会话，允许前端显式预建空会话后再发送消息。
     *
     * @param request 会话初始化请求
     * @return 会话摘要
     */
    @Override
    public AgentChatSessionSummaryDto createSession(AgentChatSessionCreateRequest request) {
        AgentChatSession session = buildSession(request == null ? new AgentChatSessionCreateRequest() : request);
        sessionMapper.insert(session);
        return toSummary(session);
    }

    /**
     * 分页查询当前客服的会话列表，支持按客户、标题、摘要和时间筛选。
     *
     * @param criteria 查询条件
     * @return 会话分页结果
     */
    @Override
    public PageResult<AgentChatSessionSummaryDto> querySessions(AgentChatSessionQueryCriteria criteria) {
        AgentChatSessionQueryCriteria safeCriteria = criteria == null ? new AgentChatSessionQueryCriteria() : criteria;
        Page<AgentChatSession> page = new Page<>(normalizePage(safeCriteria.getPage()) + 1L, normalizeSize(safeCriteria.getSize()));
        Page<AgentChatSession> result = sessionMapper.selectPage(page, buildSessionWrapper(safeCriteria));
        List<AgentChatSessionSummaryDto> summaries = result.getRecords().stream().map(this::toSummary).collect(Collectors.toList());
        return PageUtil.toPage(summaries, result.getTotal());
    }

    /**
     * 查询会话详情，返回完整消息列表和最近动作、反馈追踪。
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    @Override
    public AgentChatSessionDetailDto getSession(String sessionId) {
        AgentChatSession session = requireOwnedSession(sessionId);
        List<AgentChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AgentChatMessage>()
            .eq(AgentChatMessage::getSessionId, sessionId)
            .orderByAsc(AgentChatMessage::getCreateTime)
            .orderByAsc(AgentChatMessage::getId));
        AgentChatSessionDetailDto detail = toDetail(session, messages);
        detail.setRecentAudits(actionAuditMapper.selectList(new LambdaQueryWrapper<AgentActionAudit>()
            .eq(AgentActionAudit::getSessionId, sessionId)
            .orderByDesc(AgentActionAudit::getCreateTime)
            .last("limit 10")));
        detail.setRecentFeedbacks(feedbackMapper.selectList(new LambdaQueryWrapper<AgentDiagnosisFeedback>()
            .eq(AgentDiagnosisFeedback::getSessionId, sessionId)
            .orderByDesc(AgentDiagnosisFeedback::getCreateTime)
            .last("limit 10")));
        return detail;
    }

    /**
     * 更新会话标题，便于客服按问题语义管理多会话列表。
     *
     * @param sessionId 会话ID
     * @param title 新标题
     */
    @Override
    public void updateTitle(String sessionId, String title) {
        AgentChatSession session = requireOwnedSession(sessionId);
        session.setTitle(limitTitle(title));
        bumpSessionVersion(session);
        sessionMapper.updateById(session);
    }

    /**
     * 更新会话归档状态，归档后的会话保留追踪信息但不允许继续写入。
     *
     * @param sessionId 会话ID
     * @param archived 是否归档
     */
    @Override
    public void updateArchiveStatus(String sessionId, boolean archived) {
        AgentChatSession session = requireOwnedSession(sessionId);
        session.setArchived(archived);
        bumpSessionVersion(session);
        sessionMapper.updateById(session);
    }

    /**
     * 处理聊天请求，负责会话解析、消息幂等、助手响应落库和会话摘要更新。
     *
     * @param request 聊天请求
     * @param requestId 请求链路ID
     * @return 助手响应
     */
    @Override
    public AgentChatResponse chat(AgentChatRequest request, String requestId) {
        AgentChatRequest safeRequest = request == null ? new AgentChatRequest() : request;
        AgentChatSession session = resolveWritableSession(safeRequest.getSessionId());
        String resolvedRequestId = resolveRequestId(requestId);
        String clientMessageId = StringUtils.isBlank(safeRequest.getClientMessageId()) ? UUID.randomUUID().toString() : safeRequest.getClientMessageId().trim();

        AgentChatMessage existingUserMessage = findClientMessage(session.getSessionId(), clientMessageId);
        if (existingUserMessage != null) {
            AgentChatResponse replayResponse = restoreAssistantResponse(existingUserMessage, clientMessageId);
            if (replayResponse != null) {
                return replayResponse;
            }
        }

        persistUserMessage(session, safeRequest, resolvedRequestId, clientMessageId);
        AgentChatRequest downstreamRequest = new AgentChatRequest();
        downstreamRequest.setSessionId(session.getSessionId());
        downstreamRequest.setClientMessageId(clientMessageId);
        downstreamRequest.setMessage(safeRequest.getMessage());
        downstreamRequest.setContextSlots(toPersistedSlots(session));
        String accessContext = accessContextService.issue(session.getSessionId(), resolvedRequestId);
        long queryStart = System.currentTimeMillis();
        AgentChatResponse response = diagnosisFacadeService.chatMealPlan(downstreamRequest, resolvedRequestId, accessContext);
        AgentChatResponse normalizedResponse = normalizeResponse(response, session.getSessionId(), resolvedRequestId, clientMessageId);
        businessQueryAuditService.record(normalizedResponse, currentUsername(), System.currentTimeMillis() - queryStart);
        persistAssistantMessage(session, normalizedResponse);
        refreshSessionSummary(session, safeRequest, normalizedResponse, resolvedRequestId);
        return normalizedResponse;
    }

    /**
     * 构造当前登录客服可见的会话筛选条件。
     */
    private LambdaQueryWrapper<AgentChatSession> buildSessionWrapper(AgentChatSessionQueryCriteria criteria) {
        LambdaQueryWrapper<AgentChatSession> wrapper = new LambdaQueryWrapper<AgentChatSession>()
            .eq(criteria.getCustomerId() != null, AgentChatSession::getCustomerId, criteria.getCustomerId())
            .eq(StringUtils.isNotBlank(criteria.getCustomerCode()), AgentChatSession::getCustomerCode, criteria.getCustomerCode())
            .ge(StringUtils.isNotBlank(criteria.getRecordDateStart()), AgentChatSession::getRecordDate, criteria.getRecordDateStart())
            .le(StringUtils.isNotBlank(criteria.getRecordDateEnd()), AgentChatSession::getRecordDate, criteria.getRecordDateEnd())
            .eq(StringUtils.isNotBlank(criteria.getMealType()), AgentChatSession::getMealType, normalize(criteria.getMealType()))
            .eq(criteria.getArchived() != null, AgentChatSession::getArchived, criteria.getArchived());
        String operator = currentUsername();
        if (StringUtils.isNotBlank(operator)) {
            wrapper.eq(AgentChatSession::getOperator, operator);
        }
        if (StringUtils.isNotBlank(criteria.getKeyword())) {
            wrapper.and(item -> item.like(AgentChatSession::getTitle, criteria.getKeyword())
                .or()
                .like(AgentChatSession::getCustomerCode, criteria.getKeyword())
                .or()
                .like(AgentChatSession::getLastSummary, criteria.getKeyword()));
        }
        return wrapper.orderByDesc(AgentChatSession::getUpdateTime)
            .orderByDesc(AgentChatSession::getLastMessageTime)
            .orderByDesc(AgentChatSession::getId);
    }

    /**
     * 将数据库会话记录转换为列表摘要对象。
     */
    private AgentChatSessionSummaryDto toSummary(AgentChatSession session) {
        AgentChatSessionSummaryDto dto = new AgentChatSessionSummaryDto();
        dto.setSessionId(session.getSessionId());
        dto.setTitle(session.getTitle());
        dto.setOperator(session.getOperator());
        dto.setCustomerId(session.getCustomerId());
        dto.setCustomerCode(session.getCustomerCode());
        dto.setOrderId(session.getOrderId());
        dto.setOrderCode(session.getOrderCode());
        dto.setMealPlanRecordId(session.getMealPlanRecordId());
        dto.setRecordDate(session.getRecordDate());
        dto.setQueryStartDate(session.getQueryStartDate());
        dto.setQueryEndDate(session.getQueryEndDate());
        dto.setMealType(session.getMealType());
        dto.setStage(session.getStage());
        dto.setLastRequestId(session.getLastRequestId());
        dto.setLastSummary(session.getLastSummary());
        dto.setLastMessageTime(session.getLastMessageTime());
        dto.setArchived(session.getArchived());
        dto.setCreateTime(session.getCreateTime());
        dto.setUpdateTime(session.getUpdateTime());
        return dto;
    }

    /**
     * 组装会话详情，并从最近的助手消息中回填当前槽位和最近诊断结果。
     */
    private AgentChatSessionDetailDto toDetail(AgentChatSession session, List<AgentChatMessage> messages) {
        AgentChatSessionDetailDto dto = new AgentChatSessionDetailDto();
        dto.setSessionId(session.getSessionId());
        dto.setTitle(session.getTitle());
        dto.setOperator(session.getOperator());
        dto.setCustomerId(session.getCustomerId());
        dto.setCustomerCode(session.getCustomerCode());
        dto.setOrderId(session.getOrderId());
        dto.setOrderCode(session.getOrderCode());
        dto.setMealPlanRecordId(session.getMealPlanRecordId());
        dto.setRecordDate(session.getRecordDate());
        dto.setQueryStartDate(session.getQueryStartDate());
        dto.setQueryEndDate(session.getQueryEndDate());
        dto.setMealType(session.getMealType());
        dto.setStage(session.getStage());
        dto.setLastRequestId(session.getLastRequestId());
        dto.setLastSummary(session.getLastSummary());
        dto.setLastMessageTime(session.getLastMessageTime());
        dto.setArchived(session.getArchived());
        dto.setCreateTime(session.getCreateTime());
        dto.setUpdateTime(session.getUpdateTime());
        List<AgentChatMessageDto> messageDtos = messages.stream().map(this::toMessageDto).collect(Collectors.toList());
        dto.setMessages(messageDtos);
        for (int i = messageDtos.size() - 1; i >= 0; i--) {
            AgentChatMessageDto message = messageDtos.get(i);
            if (message.getSlots() != null && dto.getCurrentSlots() == null) {
                dto.setCurrentSlots(message.getSlots());
            }
            if (message.getDiagnosisResult() != null && dto.getLatestDiagnosisResult() == null) {
                dto.setLatestDiagnosisResult(message.getDiagnosisResult());
            }
            if (dto.getCurrentSlots() != null && dto.getLatestDiagnosisResult() != null) {
                break;
            }
        }
        return dto;
    }

    /**
     * 将消息实体转换为接口对象，并反序列化槽位和诊断结果。
     */
    private AgentChatMessageDto toMessageDto(AgentChatMessage message) {
        AgentChatMessageDto dto = new AgentChatMessageDto();
        dto.setId(message.getId());
        dto.setSessionId(message.getSessionId());
        dto.setRequestId(message.getRequestId());
        dto.setClientMessageId(message.getClientMessageId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setStatus(message.getStatus());
        dto.setConversationStage(message.getConversationStage());
        dto.setSlots(parseObject(message.getSlotsJson(), DiagnosisSlots.class));
        dto.setDiagnosisResult(parseObject(message.getDiagnosisResultJson(), AgentDiagnosisResponse.class));
        dto.setToolSummary(parseObjectList(message.getToolSummaryJson()));
        dto.setBusinessResult(parseMap(message.getBusinessResultJson()));
        dto.setCreateBy(message.getCreateBy());
        dto.setCreateTime(message.getCreateTime());
        return dto;
    }

    /**
     * 生成新的会话记录，默认按当前客服归属。
     */
    private AgentChatSession buildSession(AgentChatSessionCreateRequest request) {
        Timestamp now = now();
        AgentChatSession session = new AgentChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setTitle(limitTitle(request.getTitle()));
        session.setOperator(currentUsername());
        session.setCustomerId(request.getCustomerId());
        session.setCustomerCode(trimToNull(request.getCustomerCode()));
        session.setRecordDate(trimToNull(request.getRecordDate()));
        session.setMealType(normalize(request.getMealType()));
        session.setStage(DEFAULT_STAGE);
        session.setArchived(false);
        session.setVersion(0);
        session.setCreateBy(currentUsername());
        session.setUpdateBy(currentUsername());
        session.setCreateTime(now);
        session.setUpdateTime(now);
        return session;
    }

    /**
     * 将已持久化会话焦点转换为下游可恢复的槽位；不传递原始业务查询结果。
     *
     * @param session 当前主系统会话
     * @return 可传给 agent-service 的最小业务上下文
     */
    private DiagnosisSlots toPersistedSlots(AgentChatSession session) {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerId(session.getCustomerId());
        slots.setCustomerCode(session.getCustomerCode());
        slots.setRecordDate(session.getRecordDate());
        slots.setStartDate(session.getQueryStartDate());
        slots.setEndDate(session.getQueryEndDate());
        slots.setMealType(session.getMealType());
        slots.setOrderId(session.getOrderId());
        slots.setOrderCode(session.getOrderCode());
        slots.setMealPlanRecordId(session.getMealPlanRecordId());
        return slots;
    }

    /**
     * 解析可写会话；未传会话ID时自动创建，会话已归档时要求先新建会话。
     */
    private AgentChatSession resolveWritableSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            AgentChatSession session = buildSession(new AgentChatSessionCreateRequest());
            sessionMapper.insert(session);
            return session;
        }
        AgentChatSession session = requireOwnedSession(sessionId);
        if (Boolean.TRUE.equals(session.getArchived())) {
            throw new BadRequestException("当前会话已归档，请新建会话后继续排查");
        }
        return session;
    }

    /**
     * 校验会话存在且属于当前客服，避免跨账号查看或写入他人会话。
     */
    private AgentChatSession requireOwnedSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new BadRequestException("sessionId不能为空");
        }
        AgentChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AgentChatSession>()
            .eq(AgentChatSession::getSessionId, sessionId)
            .last("limit 1"));
        if (session == null) {
            throw new EntityNotFoundException(AgentChatSession.class, "sessionId", sessionId);
        }
        String operator = currentUsername();
        if (StringUtils.isNotBlank(operator) && StringUtils.isNotBlank(session.getOperator()) && !StringUtils.equals(operator, session.getOperator())) {
            throw new BadRequestException("无权访问该会话");
        }
        return session;
    }

    /**
     * 查询已保存的用户消息，用于命中前端 clientMessageId 幂等。
     */
    private AgentChatMessage findClientMessage(String sessionId, String clientMessageId) {
        if (StringUtils.isBlank(clientMessageId)) {
            return null;
        }
        return messageMapper.selectOne(new LambdaQueryWrapper<AgentChatMessage>()
            .eq(AgentChatMessage::getSessionId, sessionId)
            .eq(AgentChatMessage::getClientMessageId, clientMessageId)
            .eq(AgentChatMessage::getRole, ROLE_USER)
            .last("limit 1"));
    }

    /**
     * 恢复重复提交消息对应的助手响应，避免重复调用 agent-service。
     */
    private AgentChatResponse restoreAssistantResponse(AgentChatMessage existingUserMessage, String clientMessageId) {
        if (existingUserMessage == null || StringUtils.isBlank(existingUserMessage.getRequestId())) {
            return null;
        }
        AgentChatMessage assistantMessage = messageMapper.selectOne(new LambdaQueryWrapper<AgentChatMessage>()
            .eq(AgentChatMessage::getSessionId, existingUserMessage.getSessionId())
            .eq(AgentChatMessage::getRequestId, existingUserMessage.getRequestId())
            .eq(AgentChatMessage::getRole, ROLE_ASSISTANT)
            .orderByDesc(AgentChatMessage::getId)
            .last("limit 1"));
        if (assistantMessage == null) {
            return null;
        }
        AgentChatResponse response = new AgentChatResponse();
        response.setRequestId(assistantMessage.getRequestId());
        response.setSessionId(assistantMessage.getSessionId());
        response.setClientMessageId(clientMessageId);
        response.setStatus(assistantMessage.getStatus());
        response.setAssistantMessage(assistantMessage.getContent());
        response.setSlots(parseObject(assistantMessage.getSlotsJson(), DiagnosisSlots.class));
        response.setDiagnosisResult(parseObject(assistantMessage.getDiagnosisResultJson(), AgentDiagnosisResponse.class));
        response.setConversationStage(assistantMessage.getConversationStage());
        restoreBusinessResponse(response, parseMap(assistantMessage.getBusinessResultJson()));
        return response;
    }

    /**
     * 持久化用户消息，保证一条前端消息在主系统内只落一条记录。
     */
    private void persistUserMessage(AgentChatSession session, AgentChatRequest request, String requestId, String clientMessageId) {
        Timestamp now = now();
        AgentChatMessage message = new AgentChatMessage();
        message.setSessionId(session.getSessionId());
        message.setRequestId(requestId);
        message.setClientMessageId(clientMessageId);
        message.setRole(ROLE_USER);
        message.setContent(request.getMessage());
        message.setConversationStage(session.getStage());
        message.setCreateBy(currentUsername());
        message.setUpdateBy(currentUsername());
        message.setCreateTime(now);
        message.setUpdateTime(now);
        messageMapper.insert(message);
    }

    /**
     * 持久化助手消息，保留会话恢复所需的槽位和诊断结果快照。
     */
    private void persistAssistantMessage(AgentChatSession session, AgentChatResponse response) {
        Timestamp now = now();
        AgentChatMessage message = new AgentChatMessage();
        message.setSessionId(session.getSessionId());
        message.setRequestId(response.getRequestId());
        message.setRole(ROLE_ASSISTANT);
        message.setContent(response.getAssistantMessage());
        message.setStatus(response.getStatus());
        message.setConversationStage(response.getConversationStage());
        message.setSlotsJson(toJson(response.getSlots()));
        message.setDiagnosisResultJson(toJson(response.getDiagnosisResult()));
        message.setToolSummaryJson(toJson(response.getDiagnosisResult() == null ? Collections.emptyList() : response.getDiagnosisResult().getToolCallSummary()));
        message.setBusinessResultJson(toJson(buildBusinessSnapshot(response)));
        message.setCreateBy(currentUsername());
        message.setUpdateBy(currentUsername());
        message.setCreateTime(now);
        message.setUpdateTime(now);
        messageMapper.insert(message);
    }

    /**
     * 更新会话摘要与当前槽位信息，供多会话列表和刷新恢复直接读取。
     */
    private void refreshSessionSummary(AgentChatSession session,
                                       AgentChatRequest request,
                                       AgentChatResponse response,
                                       String requestId) {
        DiagnosisSlots slots = response.getSlots();
        AgentDiagnosisResponse diagnosisResult = response.getDiagnosisResult();
        session.setLastRequestId(requestId);
        session.setStage(trimToNull(response.getConversationStage()) == null ? DEFAULT_STAGE : response.getConversationStage());
        if ("RESET".equalsIgnoreCase(response.getStatus())) {
            clearBusinessFocus(session);
        }
        if (slots != null) {
            boolean customerFocusChanged = customerFocusChanged(session, slots);
            session.setCustomerId(slots.getCustomerId());
            session.setCustomerCode(firstNonBlank(slots.getCustomerCode(), session.getCustomerCode()));
            if (StringUtils.isNotBlank(slots.getStartDate()) && StringUtils.isNotBlank(slots.getEndDate())) {
                session.setRecordDate(null);
                session.setQueryStartDate(slots.getStartDate());
                session.setQueryEndDate(slots.getEndDate());
            } else if (StringUtils.isNotBlank(slots.getRecordDate())) {
                session.setRecordDate(slots.getRecordDate());
                session.setQueryStartDate(null);
                session.setQueryEndDate(null);
            } else {
                session.setRecordDate(firstNonBlank(slots.getRecordDate(), session.getRecordDate()));
            }
            session.setMealType(firstNonBlank(normalize(slots.getMealType()), session.getMealType()));
            // 客户切换后不得沿用旧客户的订单焦点；同一客户的追问则继续保留未重复提及的订单。
            session.setOrderId(customerFocusChanged ? slots.getOrderId()
                : (slots.getOrderId() == null ? session.getOrderId() : slots.getOrderId()));
            session.setOrderCode(customerFocusChanged ? trimToNull(slots.getOrderCode())
                : firstNonBlank(slots.getOrderCode(), session.getOrderCode()));
            session.setMealPlanRecordId(customerFocusChanged ? null
                : (slots.getMealPlanRecordId() == null ? session.getMealPlanRecordId() : slots.getMealPlanRecordId()));
        }
        if (diagnosisResult != null) {
            session.setCustomerId(diagnosisResult.getCustomerId() == null ? session.getCustomerId() : diagnosisResult.getCustomerId());
            session.setRecordDate(firstNonBlank(diagnosisResult.getRecordDate(), session.getRecordDate()));
            session.setMealType(firstNonBlank(normalize(diagnosisResult.getMealType()), session.getMealType()));
        }
        session.setLastSummary(resolveSummary(response));
        session.setLastMessageTime(now());
        if (StringUtils.isBlank(session.getTitle())) {
            session.setTitle(resolveSessionTitle(session, request, response));
        }
        bumpSessionVersion(session);
        sessionMapper.updateById(session);
    }

    /**
     * 标准化下游助手响应，确保会话ID、请求ID和幂等消息ID始终回传给前端。
     */
    private AgentChatResponse normalizeResponse(AgentChatResponse response,
                                               String sessionId,
                                               String requestId,
                                               String clientMessageId) {
        AgentChatResponse normalized = response == null ? new AgentChatResponse() : response;
        normalized.setSessionId(StringUtils.defaultIfBlank(normalized.getSessionId(), sessionId));
        normalized.setRequestId(StringUtils.defaultIfBlank(normalized.getRequestId(), requestId));
        normalized.setClientMessageId(clientMessageId);
        normalized.setStatus(StringUtils.defaultIfBlank(normalized.getStatus(), "ERROR"));
        normalized.setConversationStage(StringUtils.defaultIfBlank(normalized.getConversationStage(), "ERROR"));
        if (normalized.getSlots() == null) {
            normalized.setSlots(new DiagnosisSlots());
        }
        return normalized;
    }

    /**
     * 为列表摘要生成稳定标题，优先使用显式标题，其次使用客户/日期/餐次，再退回第一条消息。
     */
    private String resolveSessionTitle(AgentChatSession session, AgentChatRequest request, AgentChatResponse response) {
        if (StringUtils.isNotBlank(session.getCustomerCode()) && StringUtils.isNotBlank(session.getRecordDate()) && StringUtils.isNotBlank(session.getMealType())) {
            return limitTitle(session.getCustomerCode() + " " + session.getRecordDate() + " " + session.getMealType());
        }
        if (response.getSlots() != null && StringUtils.isNotBlank(response.getSlots().getCustomerCode())
            && StringUtils.isNotBlank(response.getSlots().getRecordDate()) && StringUtils.isNotBlank(response.getSlots().getMealType())) {
            return limitTitle(response.getSlots().getCustomerCode() + " " + response.getSlots().getRecordDate() + " " + response.getSlots().getMealType());
        }
        return limitTitle(request.getMessage());
    }

    /**
     * 生成会话摘要文案，优先使用诊断摘要，其次回退到助手最新回复。
     */
    private String resolveSummary(AgentChatResponse response) {
        if (response.getDiagnosisResult() != null && StringUtils.isNotBlank(response.getDiagnosisResult().getSummary())) {
            return limitSummary(response.getDiagnosisResult().getSummary());
        }
        return limitSummary(response.getAssistantMessage());
    }

    /**
     * 统一递增会话版本并刷新更新时间。
     */
    private void bumpSessionVersion(AgentChatSession session) {
        session.setVersion(session.getVersion() == null ? 1 : session.getVersion() + 1);
        session.setUpdateBy(currentUsername());
        session.setUpdateTime(now());
    }

    /**
     * 解析JSON对象，空串或异常时返回空值，避免历史脏数据影响查询。
     */
    private <T> T parseObject(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 解析工具摘要列表，空串或异常时返回空集合。
     */
    private List<Map<String, Object>> parseObjectList(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseObject(json, List.class);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    /**
     * 解析已持久化的受控业务查询卡片快照；异常或历史空数据按空值处理。
     *
     * @param json 卡片快照 JSON
     * @return 卡片快照映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, Map.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 构造可持久化的业务查询展示快照，只保留前端恢复卡片所需字段。
     *
     * @param response 当前助手响应
     * @return 无业务查询时返回空值，否则返回受控展示字段
     */
    private Map<String, Object> buildBusinessSnapshot(AgentChatResponse response) {
        if (response == null || StringUtils.isBlank(response.getResponseType())
            || !response.getResponseType().startsWith("BUSINESS_QUERY")) {
            return null;
        }
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("responseType", response.getResponseType());
        snapshot.put("insightResult", response.getInsightResult());
        snapshot.put("facts", response.getFacts());
        snapshot.put("warnings", response.getWarnings());
        snapshot.put("cached", response.isCached());
        snapshot.put("partial", response.isPartial());
        snapshot.put("queriedAt", response.getQueriedAt());
        snapshot.put("queryPlan", response.getQueryPlan());
        return snapshot;
    }

    /**
     * 将历史业务查询卡片快照回填到聊天响应，用于幂等重放和会话刷新。
     *
     * @param response 待回填响应
     * @param snapshot 持久化卡片快照
     */
    @SuppressWarnings("unchecked")
    private void restoreBusinessResponse(AgentChatResponse response, Map<String, Object> snapshot) {
        if (response == null || snapshot == null) {
            return;
        }
        response.setResponseType((String) snapshot.get("responseType"));
        response.setInsightResult(snapshot.get("insightResult") instanceof Map ? (Map<String, Object>) snapshot.get("insightResult") : Collections.emptyMap());
        response.setFacts(snapshot.get("facts") instanceof List ? (List<Map<String, Object>>) snapshot.get("facts") : Collections.emptyList());
        response.setWarnings(snapshot.get("warnings") instanceof List ? (List<String>) snapshot.get("warnings") : Collections.emptyList());
        response.setCached(Boolean.TRUE.equals(snapshot.get("cached")));
        response.setPartial(Boolean.TRUE.equals(snapshot.get("partial")));
        response.setQueriedAt((String) snapshot.get("queriedAt"));
        response.setQueryPlan(snapshot.get("queryPlan") instanceof Map ? (Map<String, Object>) snapshot.get("queryPlan") : Collections.emptyMap());
    }

    /**
     * 将对象序列化为JSON字符串，空对象时返回空值便于压缩存储。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        String json = JSON.toJSONString(value);
        return "null".equals(json) ? null : json;
    }

    private String resolveRequestId(String requestId) {
        return StringUtils.isBlank(requestId) ? UUID.randomUUID().toString() : requestId.trim();
    }

    private String currentUsername() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception ex) {
            return "system";
        }
    }

    private Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private String normalize(String value) {
        return trimToNull(value) == null ? null : value.trim().toUpperCase();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }

    /**
     * 判断本轮槽位是否显式切换了客户，避免把旧客户订单带入新客户查询。
     *
     * @param session 当前已持久化会话
     * @param slots 本轮 agent 返回槽位
     * @return 客户标识变化时返回 true
     */
    private boolean customerFocusChanged(AgentChatSession session, DiagnosisSlots slots) {
        if (slots == null) {
            return false;
        }
        if (slots.getCustomerId() != null && !slots.getCustomerId().equals(session.getCustomerId())) {
            return true;
        }
        return StringUtils.isNotBlank(slots.getCustomerCode())
            && !slots.getCustomerCode().equalsIgnoreCase(session.getCustomerCode());
    }

    /**
     * 清空会话的全部业务焦点，确保后续请求不会从持久化上下文恢复旧客户、订单或排餐。
     *
     * @param session 当前会话
     */
    private void clearBusinessFocus(AgentChatSession session) {
        session.setCustomerId(null);
        session.setCustomerCode(null);
        session.setOrderId(null);
        session.setOrderCode(null);
        session.setMealPlanRecordId(null);
        session.setRecordDate(null);
        session.setQueryStartDate(null);
        session.setQueryEndDate(null);
        session.setMealType(null);
    }

    private String limitTitle(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private String limitSummary(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }
}
