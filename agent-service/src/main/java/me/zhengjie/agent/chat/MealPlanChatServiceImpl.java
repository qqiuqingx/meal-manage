package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天编排服务。
 */
@Service
public class MealPlanChatServiceImpl implements MealPlanChatService {

    private static final Logger log = LoggerFactory.getLogger(MealPlanChatServiceImpl.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final MealPlanChatSessionStore sessionStore;
    private final MealPlanChatExtractor extractor;
    private final MealPlanDiagnosisService diagnosisService;

    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService) {
        this.sessionStore = sessionStore;
        this.extractor = extractor;
        this.diagnosisService = diagnosisService;
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        MealPlanChatSession session = sessionStore.getOrCreate(request.getSessionId());
        remember(session, request.getMessage());
        ChatExtractionResult extraction = extractor.extract(request.getMessage(), session.getSlots());
        mergeSlots(session.getSlots(), extraction.getSlots());

        long start = System.currentTimeMillis();
        if (extraction.getIntent() == ChatIntent.RESET) {
            MealPlanChatSession reset = sessionStore.reset(session.getSessionId());
            logChat(reset, extraction, false, start);
            return response(reset, ChatStatus.RESET, "会话已清空，请重新提供客户、日期和餐次。", null, List.of("今天", "明天", "午餐"));
        }

        if (extraction.getIntent() == ChatIntent.OUT_OF_SCOPE) {
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response(session, ChatStatus.ANSWERED, "我目前只能处理排餐诊断，请提供客户、日期和餐次。", null, List.of("重新排查", "清空会话"));
        }

        List<MissingSlot> missingSlots = missingSlots(session.getSlots());
        if (!missingSlots.isEmpty()) {
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response(session, ChatStatus.NEED_MORE_INFO, questionFor(missingSlots), null, quickRepliesFor(missingSlots));
        }

        if (extraction.getIntent() == ChatIntent.FOLLOW_UP && session.getLastDiagnosisResult() != null) {
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            String message = "上次诊断摘要：" + safe(session.getLastDiagnosisResult().getSummary()) + "请结合结果卡片中的原因、建议和证据继续人工确认。";
            return response(session, ChatStatus.ANSWERED, message, null, List.of("继续追问", "重新排查", "清空会话"));
        }

        DiagnosisResponse diagnosisResult = diagnosisService.diagnose(toDiagnosisRequest(session.getSlots()));
        session.setLastDiagnosisResult(diagnosisResult);
        sessionStore.save(session);
        logChat(session, extraction, true, start);
        return response(session, ChatStatus.ANSWERED, diagnosisMessage(diagnosisResult), diagnosisResult, List.of("继续追问", "重新排查", "清空会话"));
    }

    private DiagnosisRequest toDiagnosisRequest(DiagnosisSlots slots) {
        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(slots.getCustomerId());
        request.setCustomerCode(slots.getCustomerCode());
        request.setRecordDate(slots.getRecordDate());
        request.setMealType(slots.getMealType());
        return request;
    }

    private AgentChatResponse response(MealPlanChatSession session, ChatStatus status, String message, DiagnosisResponse diagnosisResult, List<String> quickReplies) {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(session.getSessionId());
        response.setStatus(status);
        response.setAssistantMessage(message);
        response.setSlots(copy(session.getSlots()));
        response.setDiagnosisResult(diagnosisResult);
        response.setQuickReplies(quickReplies);
        return response;
    }

    private void mergeSlots(DiagnosisSlots target, DiagnosisSlots source) {
        if (source == null) {
            return;
        }
        if (source.getCustomerId() != null) {
            target.setCustomerId(source.getCustomerId());
        }
        if (isNotBlank(source.getCustomerCode())) {
            target.setCustomerCode(source.getCustomerCode());
        }
        if (isNotBlank(source.getRecordDate())) {
            target.setRecordDate(source.getRecordDate());
        }
        if (isNotBlank(source.getMealType())) {
            target.setMealType(source.getMealType());
        }
    }

    private List<MissingSlot> missingSlots(DiagnosisSlots slots) {
        List<MissingSlot> missing = new ArrayList<>();
        if (slots.getCustomerId() == null && isNotBlank(slots.getCustomerCode()) == false) {
            missing.add(MissingSlot.CUSTOMER);
        }
        if (isNotBlank(slots.getRecordDate()) == false) {
            missing.add(MissingSlot.RECORD_DATE);
        }
        if (isNotBlank(slots.getMealType()) == false) {
            missing.add(MissingSlot.MEAL_TYPE);
        }
        return missing;
    }

    private String questionFor(List<MissingSlot> missingSlots) {
        if (missingSlots.contains(MissingSlot.CUSTOMER)) {
            return "请提供客户 ID 或客户编号。";
        }
        if (missingSlots.contains(MissingSlot.RECORD_DATE)) {
            return "请补充要排查的日期，例如今天、明天或 2026-05-22。";
        }
        return "请补充餐次：早餐、午餐还是晚餐？";
    }

    private List<String> quickRepliesFor(List<MissingSlot> missingSlots) {
        if (missingSlots.contains(MissingSlot.RECORD_DATE)) {
            return List.of("今天", "明天", "重新排查");
        }
        if (missingSlots.contains(MissingSlot.MEAL_TYPE)) {
            return List.of("早餐", "午餐", "晚餐", "重新排查");
        }
        return List.of("重新排查");
    }

    private void remember(MealPlanChatSession session, String message) {
        session.setLastUserMessage(message);
        session.getRecentMessages().add(message);
        if (session.getRecentMessages().size() > 10) {
            session.setRecentMessages(new ArrayList<>(session.getRecentMessages().subList(session.getRecentMessages().size() - 10, session.getRecentMessages().size())));
        }
    }

    private DiagnosisSlots copy(DiagnosisSlots source) {
        DiagnosisSlots target = new DiagnosisSlots();
        if (source == null) {
            return target;
        }
        target.setCustomerId(source.getCustomerId());
        target.setCustomerCode(source.getCustomerCode());
        target.setRecordDate(source.getRecordDate());
        target.setMealType(source.getMealType());
        return target;
    }

    private String safe(String value) {
        return isNotBlank(value) ? value : "暂无诊断摘要。";
    }

    private String diagnosisMessage(DiagnosisResponse diagnosisResult) {
        int reasonCount = diagnosisResult == null || diagnosisResult.getReasons() == null ? 0 : diagnosisResult.getReasons().size();
        return "已完成诊断，发现 " + reasonCount + " 个可能原因，请结合证据人工确认。";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void logChat(MealPlanChatSession session, ChatExtractionResult extraction, boolean diagnosisTriggered, long start) {
        List<MissingSlot> missing = missingSlots(session.getSlots());
        log.info("聊天诊断阶段 requestId={} sessionId={} intent={} slots.customerId={} slots.customerCode={} slots.recordDate={} slots.mealType={} missingSlots={} diagnosisTriggered={} costMs={}",
            MDC.get(REQUEST_ID_KEY), session.getSessionId(), extraction.getIntent(), session.getSlots().getCustomerId(),
            session.getSlots().getCustomerCode(), session.getSlots().getRecordDate(), session.getSlots().getMealType(),
            missing, diagnosisTriggered, System.currentTimeMillis() - start);
    }
}
