package me.zhengjie.agent.guardrail;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 最终回答客户编号—完整姓名配对和无姓名泄露日志契约测试。 */
class FinalAnswerCustomerIdentityTest {
    private final FinalAnswerGuardrail guardrail = new FinalAnswerGuardrail(new SensitiveDataPolicy());
    private final Set<FinalAnswerGuardrail.CustomerIdentity> identities = Set.of(
        new FinalAnswerGuardrail.CustomerIdentity("C1001", "张三"));

    /** 完整姓名只有和同一客户编号同时出现时才允许进入回答。 */
    @Test
    void shouldRequireCustomerCodeWhenAnswerUsesFullName() {
        assertDoesNotThrow(() -> guardrail.validate("查询客户", "客户编号 C1001，客户张三目前有订单。", 1, identities));
        assertThrows(ToolGuardrailException.class,
            () -> guardrail.validate("查询客户", "客户张三目前有订单。", 1, identities));
    }

    /** 只有客户编号的回答仍可通过，不强制模型重复输出完整姓名。 */
    @Test
    void shouldAllowCodeOnlyConclusion() {
        assertDoesNotThrow(() -> guardrail.validate("查询客户", "客户编号 C1001 的查询结果已返回。", 1, identities));
    }

    /** 姓名配对校验只能返回稳定错误码，不应把回答原文或客户姓名写入异常内容。 */
    @Test
    void shouldUseStableIdentityErrorWithoutSensitiveText() {
        ToolGuardrailException exception = assertThrows(ToolGuardrailException.class,
            () -> guardrail.validate("查询客户", "客户张三目前有订单。", 1, identities));
        org.junit.jupiter.api.Assertions.assertEquals("ANSWER_CUSTOMER_IDENTITY_UNPAIRED", exception.getCode());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains("张三"));
    }

    /** 工具事实缺少客户编号时，不允许回答只引用该事实中的完整姓名。 */
    @Test
    void shouldRejectNameWhenFactHasNoCustomerCode() {
        Set<FinalAnswerGuardrail.CustomerIdentity> missingCode = Set.of(
            new FinalAnswerGuardrail.CustomerIdentity(null, "张三"));
        assertThrows(ToolGuardrailException.class,
            () -> guardrail.validate("查询客户", "客户张三目前有订单。", 1, missingCode));
    }
}
