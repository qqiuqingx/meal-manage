package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话状态复制、恢复与轮次记录支持。
 *
 * <p>该组件只处理状态对象，不识别意图、不执行工具，也不决定业务响应。</p>
 */
@Component
public class ConversationStateSupport {

    /** 主系统持久化上下文是跨实例真相源；每轮请求先恢复到本地会话状态。 */
    public void hydrateBusinessContexts(MealPlanChatSession session, AgentChatRequest request) {
        if (session == null || request == null) return;
        if (request.getPendingBusinessQueryContext() != null) {
            session.getConversationState().setPendingBusinessQueryContext(request.getPendingBusinessQueryContext());
        }
        if (request.getLastBusinessQueryContext() != null) {
            session.getConversationState().setLastBusinessQueryContext(request.getLastBusinessQueryContext());
        }
        if (request.getActiveTaskStack() != null) {
            session.getConversationState().setTaskStack(request.getActiveTaskStack());
        }
    }

    /**
     * 记录用户输入，保存不可共享的槽位快照。
     *
     * @param session 当前会话
     * @param message 当前用户消息
     * @param extraction 本轮受控提取结果
     */
    public void rememberUserTurn(MealPlanChatSession session, String message, ChatExtractionResult extraction) {
        session.getConversationState().addTurn(
            DiagnosisConversationTurn.userTurn(message, copy(session.getSlots()), extraction.getIntent().name())
        );
    }

    /**
     * 记录助手回复及关联诊断请求，保存不可共享的槽位快照。
     *
     * @param session 当前会话
     * @param response 本轮响应
     * @param extraction 本轮受控提取结果
     * @param diagnosisResult 可选诊断结果
     */
    public void rememberAssistantTurn(MealPlanChatSession session,
                                      AgentChatResponse response,
                                      ChatExtractionResult extraction,
                                      DiagnosisResponse diagnosisResult) {
        String diagnosisRequestId = diagnosisResult == null ? null : diagnosisResult.getRequestId();
        session.getConversationState().addTurn(
            DiagnosisConversationTurn.assistantTurn(
                response.getAssistantMessage(),
                copy(session.getSlots()),
                extraction.getIntent().name(),
                diagnosisRequestId
            )
        );
    }

    /**
     * 复制槽位对象，避免响应、历史轮次和会话共享可变引用。
     *
     * @param source 原始槽位
     * @return 独立槽位副本
     */
    public DiagnosisSlots copy(DiagnosisSlots source) {
        DiagnosisSlots target = new DiagnosisSlots();
        if (source == null) return target;
        target.setCustomerId(source.getCustomerId());
        target.setCustomerCode(source.getCustomerCode());
        target.setCustomerName(source.getCustomerName());
        target.setRecordDate(source.getRecordDate());
        target.setStartDate(source.getStartDate());
        target.setEndDate(source.getEndDate());
        target.setMealType(source.getMealType());
        target.setOrderStatus(source.getOrderStatus());
        target.setOrderId(source.getOrderId());
        target.setOrderCode(source.getOrderCode());
        target.setMealPlanRecordId(source.getMealPlanRecordId());
        target.setSlotConfidence(copyMap(source.getSlotConfidence()));
        target.setSlotSource(copyMap(source.getSlotSource()));
        return target;
    }

    /**
     * 合并增量槽位；有值的新槽位覆盖目标对象，其余字段保持不变。
     *
     * @param target 合并目标，可为空
     * @param source 本轮增量槽位，可为空
     * @return 合并后的目标对象
     */
    public DiagnosisSlots mergeSlots(DiagnosisSlots target, DiagnosisSlots source) {
        DiagnosisSlots merged = target == null ? new DiagnosisSlots() : target;
        if (source == null) return merged;
        if (source.getCustomerId() != null) merged.setCustomerId(source.getCustomerId());
        if (hasText(source.getCustomerCode())) merged.setCustomerCode(source.getCustomerCode());
        if (hasText(source.getCustomerName())) merged.setCustomerName(source.getCustomerName());
        if (hasText(source.getRecordDate())) merged.setRecordDate(source.getRecordDate());
        if (hasText(source.getStartDate())) merged.setStartDate(source.getStartDate());
        if (hasText(source.getEndDate())) merged.setEndDate(source.getEndDate());
        if (hasText(source.getMealType())) merged.setMealType(source.getMealType());
        if (source.getOrderStatus() != null) merged.setOrderStatus(source.getOrderStatus());
        if (source.getOrderId() != null) merged.setOrderId(source.getOrderId());
        if (hasText(source.getOrderCode())) merged.setOrderCode(source.getOrderCode());
        if (source.getMealPlanRecordId() != null) merged.setMealPlanRecordId(source.getMealPlanRecordId());
        if (source.getSlotConfidence() != null && !source.getSlotConfidence().isEmpty()) {
            merged.setSlotConfidence(copyMap(source.getSlotConfidence()));
        }
        if (source.getSlotSource() != null && !source.getSlotSource().isEmpty()) {
            merged.setSlotSource(copyMap(source.getSlotSource()));
        }
        return merged;
    }

    /**
     * 复制槽位置信度或来源映射。
     *
     * @param source 原始映射
     * @return 独立的可变副本
     */
    public Map<String, String> copyMap(Map<String, String> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    /** 判断槽位文本是否包含有效内容。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
