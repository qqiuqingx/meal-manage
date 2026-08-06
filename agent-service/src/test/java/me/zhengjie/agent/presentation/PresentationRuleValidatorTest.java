package me.zhengjie.agent.presentation;

import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static me.zhengjie.agent.presentation.PresentationDescriptor.Field;
import static me.zhengjie.agent.presentation.PresentationDescriptor.Format;
import static me.zhengjie.agent.presentation.PresentationRule.Template;

/** 系统展示规则启动期校验契约测试。 */
class PresentationRuleValidatorTest {

    private final PresentationFieldCatalog catalog = new PresentationFieldCatalog();
    private final PresentationRuleValidator validator = new PresentationRuleValidator(catalog);
    private final ToolRegistry toolRegistry = new ToolRegistry();

    /** 重复 cardType 配置必须阻止注册表启动。 */
    @Test
    void shouldRejectDuplicateRules() {
        PresentationRule rule = new PresentationRule("CUSTOMER_PROFILE_LIST",
            Template.table("客户", "items", List.of(new Field("customerCode", "客户编号", Format.TEXT))));

        assertThrows(IllegalStateException.class,
            () -> validator.validateAll(List.of(rule, rule), toolRegistry));
    }

    /** 未知字段不能通过安全字段目录校验。 */
    @Test
    void shouldRejectUnknownField() {
        PresentationRule rule = new PresentationRule("CUSTOMER_PROFILE_LIST",
            Template.table("客户", "items", List.of(new Field("notAllowed", "未知", Format.TEXT))));

        assertThrows(IllegalStateException.class, () -> validator.validateAll(List.of(rule), toolRegistry));
    }

    /** 默认视图不在白名单时必须阻止规则启动。 */
    @Test
    void shouldRejectIllegalView() {
        PresentationRule.Template template = new PresentationRule.Template(
            "客户", PresentationDescriptor.Layout.TABS, PresentationDescriptor.View.BAR,
            List.of(PresentationDescriptor.View.TEXT), null, null, null);
        PresentationRule rule = new PresentationRule("CUSTOMER_PROFILE_LIST", template);

        assertThrows(IllegalStateException.class, () -> validator.validateAll(List.of(rule), toolRegistry));
    }

    /** 表格列数超过 v1 上限时必须阻止规则启动。 */
    @Test
    void shouldRejectTooManyColumns() {
        List<Field> columns = new ArrayList<>();
        IntStream.range(0, PresentationDescriptor.MAX_TABLE_COLUMNS + 1)
            .forEach(index -> columns.add(new Field("customerCode", "客户编号", Format.TEXT)));
        PresentationRule rule = new PresentationRule("CUSTOMER_PROFILE_LIST",
            Template.table("客户", "items", columns));

        assertThrows(IllegalStateException.class, () -> validator.validateAll(List.of(rule), toolRegistry));
    }
}
