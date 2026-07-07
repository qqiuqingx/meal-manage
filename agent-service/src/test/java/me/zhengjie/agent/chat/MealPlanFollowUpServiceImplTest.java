package me.zhengjie.agent.chat;

import me.zhengjie.agent.chat.impl.MealPlanFollowUpServiceImpl;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlanFollowUpServiceImplTest {

    private final MealPlanFollowUpService service = new MealPlanFollowUpServiceImpl();

    @Test
    void shouldBuildReasonSpecificReply() {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("CANDIDATE_DISH_EMPTY");
        reason.setTitle("候选菜为空");
        reason.setDescription("候选菜统计结果为空。");
        reason.setSuggestion("请核对菜单配置。");
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("candidateCount", "0")));
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("候选菜为空");
        response.setReasons(List.of(reason));

        String reply = service.buildFollowUpReply("为什么候选菜为空？", response);

        assertTrue(reply.contains("候选菜为空"));
        assertTrue(reply.contains("candidateCount=0"));
        assertTrue(reply.contains("请核对菜单配置"));
    }

    @Test
    void shouldFallbackToSummaryWhenNoReasonMatches() {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("ORDER_MISSING");
        reason.setTitle("未找到有效订单");
        reason.setDescription("没有查询到有效订单。");
        reason.setSuggestion("请核对订单。");
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("orderCount", "0")));
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("未找到有效订单");
        response.setReasons(List.of(reason));

        String reply = service.buildFollowUpReply("再解释一下", response);

        assertTrue(reply.contains("上次诊断摘要"));
        assertTrue(reply.contains("未找到有效订单"));
    }
}
