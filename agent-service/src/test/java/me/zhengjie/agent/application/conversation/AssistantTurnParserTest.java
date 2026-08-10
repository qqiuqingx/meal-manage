package me.zhengjie.agent.application.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.guardrail.ToolGuardrailException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证助手最小回答协议、旧纯文本兼容和确定性快捷回复。 */
class AssistantTurnParserTest {

    private final AssistantTurnParser parser = new AssistantTurnParser(new ObjectMapper());

    /** NEED_MORE_INFO 必须只携带受控枚举，并按缺失项生成固定快捷回复。 */
    @Test
    void shouldParseClarificationAndBuildDeterministicQuickReplies() {
        AssistantTurnResult result = parser.parse("{" +
            "\"outcome\":\"NEED_MORE_INFO\",\"assistantMessage\":\"请补充餐次。\"," +
            "\"missingSlots\":[\"MEAL_TYPE\",\"DATE_RANGE\"]}");

        assertEquals(ChatStatus.NEED_MORE_INFO, result.status());
        assertEquals(List.of(MissingSlot.MEAL_TYPE, MissingSlot.DATE_RANGE), result.missingSlots());
        assertEquals(List.of("早餐", "午餐", "晚餐", "输入起止日期"), parser.quickReplies(result, null));
    }

    /** ANSWERED 禁止同时声明缺失项，避免前端把已回答和待补充混成一个状态。 */
    @Test
    void shouldRejectAnsweredWithMissingSlots() {
        ToolGuardrailException error = assertThrows(ToolGuardrailException.class, () -> parser.parse(
            "{\"outcome\":\"ANSWERED\",\"assistantMessage\":\"已完成。\",\"missingSlots\":[\"CUSTOMER_OR_ORDER\"]}"));

        assertEquals("ANSWER_MISSING_SLOTS_ON_ANSWERED", error.getCode());
    }

    /** 澄清协议缺少 missingSlots 时必须失败，不能依赖自然语言猜测缺失条件。 */
    @Test
    void shouldRejectClarificationWithoutMissingSlots() {
        ToolGuardrailException error = assertThrows(ToolGuardrailException.class, () -> parser.parse(
            "{\"outcome\":\"NEED_MORE_INFO\",\"assistantMessage\":\"请补充信息。\"}"));

        assertEquals("ANSWER_MISSING_SLOTS_REQUIRED", error.getCode());
    }

    /** 协议字段以外的 JSON 字段必须拒绝，防止模型注入内部状态。 */
    @Test
    void shouldRejectUnknownProtocolFields() {
        ToolGuardrailException error = assertThrows(ToolGuardrailException.class, () -> parser.parse(
            "{\"outcome\":\"ANSWERED\",\"assistantMessage\":\"已完成。\",\"toolCalls\":[]}"));

        assertEquals("ANSWER_PROTOCOL_INVALID", error.getCode());
    }

    /** 空回答和非法缺失项都必须返回稳定协议错误，不能落入纯文本兼容路径。 */
    @Test
    void shouldRejectEmptyAndInvalidProtocolValues() {
        ToolGuardrailException empty = assertThrows(ToolGuardrailException.class, () -> parser.parse(" "));
        ToolGuardrailException invalidSlot = assertThrows(ToolGuardrailException.class, () -> parser.parse(
            "{\"outcome\":\"NEED_MORE_INFO\",\"assistantMessage\":\"请补充。\",\"missingSlots\":[\"UNKNOWN\"]}"));

        assertEquals("ANSWER_EMPTY", empty.getCode());
        assertEquals("ANSWER_MISSING_SLOTS_INVALID", invalidSlot.getCode());
    }

    /** 最小协议允许携带排餐诊断对象，并把它交给现有诊断结果校验链路。 */
    @Test
    void shouldParseOptionalDiagnosisResult() {
        AssistantTurnResult result = parser.parse(
            "{\"outcome\":\"ANSWERED\",\"assistantMessage\":\"已完成诊断。\",\"missingSlots\":[]," +
                "\"diagnosisResult\":{\"summary\":\"命中规则\",\"reasons\":[]}}");

        assertEquals(ChatStatus.ANSWERED, result.status());
        assertEquals("命中规则", result.diagnosisResult().getSummary());
        assertTrue(result.structured());
    }

    /** 旧版本纯文本仍按已回答处理，不通过关键词猜测 NEED_MORE_INFO。 */
    @Test
    void shouldKeepLegacyPlainTextAsAnswered() {
        AssistantTurnResult result = parser.parse("请补充客户编号后我再查询。");

        assertEquals(ChatStatus.ANSWERED, result.status());
        assertTrue(result.missingSlots().isEmpty());
        assertFalse(result.structured());
        assertTrue(parser.quickReplies(result, null).isEmpty());
    }

    /** 快捷回复始终限制在六项和二十字符以内。 */
    @Test
    void shouldRespectQuickReplyLimits() {
        AssistantTurnResult result = new AssistantTurnResult(ChatStatus.NEED_MORE_INFO,
            "请补充查询条件。", List.of(MissingSlot.RECORD_DATE, MissingSlot.MEAL_TYPE, MissingSlot.DATE_RANGE), null, true);

        List<String> replies = parser.quickReplies(result, null);

        assertTrue(replies.size() <= 6);
        assertTrue(replies.stream().allMatch(reply -> reply.length() <= 20));
        assertEquals(List.of("今天", "昨天", "明天", "早餐", "午餐", "晚餐"), replies);
    }
}
