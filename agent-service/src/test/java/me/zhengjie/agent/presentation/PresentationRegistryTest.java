package me.zhengjie.agent.presentation;

import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 系统展示注册表的卡片覆盖、列顺序和多工具路径契约测试。 */
class PresentationRegistryTest {

    private final ToolRegistry toolRegistry = new ToolRegistry();
    private final PresentationRegistry registry = new PresentationRegistry(toolRegistry);

    /** 当前工具目录的 11 类卡片必须全部由系统规则覆盖。 */
    @Test
    void shouldCoverEveryKnownCardType() {
        Set<String> cardTypes = toolRegistry.all().stream()
            .filter(spec -> spec.effect() == ToolRegistry.ToolEffect.READ_ONLY)
            .map(ToolRegistry.ToolSpec::cardType).collect(Collectors.toSet());

        assertEquals(11, cardTypes.size());
        assertEquals(cardTypes, registry.all().stream()
            .map(PresentationRule::cardType).collect(Collectors.toSet()));
        assertEquals(11, registry.ruleCount());
        assertTrue(registry.healthWarnings().isEmpty());
    }

    /** 服务客户展示必须以客户编号、姓名、订单编号和下单时间的固定顺序开头。 */
    @Test
    void shouldKeepServiceCustomerColumnOrder() {
        List<String> fields = registry.require("SERVICE_CUSTOMER_LIST").template().table().columns().stream()
            .map(PresentationDescriptor.Field::field).collect(Collectors.toList());

        assertEquals(List.of("customerCode", "customerName", "orderCode", "orderTime", "status", "parentPackageName"), fields);
    }

    /** 同一 DISH_LIST 卡片的两个工具必须引用各自真实的安全数据路径。 */
    @Test
    void shouldUseSeparateDishListVariants() {
        PresentationRule rule = registry.require("DISH_LIST");

        assertEquals("items", rule.templateFor(ToolRegistry.SEARCH_DISHES).table().dataPath());
        assertEquals("data.groups[].items", rule.templateFor(ToolRegistry.LIST_SCHEDULED_DISHES).table().dataPath());
        assertFalse(rule.templateFor(ToolRegistry.SEARCH_DISHES).table().columns().isEmpty());
        assertFalse(rule.templateFor(ToolRegistry.LIST_SCHEDULED_DISHES).table().columns().isEmpty());
    }

    /** 已知卡片直接由系统服务生成 SYSTEM 描述，不需要模型网关参与。 */
    @Test
    void shouldGenerateKnownCardDeterministically() {
        PresentationService service = new PresentationService(registry);
        PresentationService.PresentationResult result = service.present(
            "call-1", ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST", null);

        assertNotNull(result.descriptor());
        assertEquals(PresentationDescriptor.DecisionSource.SYSTEM, result.descriptor().decisionSource());
        assertEquals("call-1", result.descriptor().sourceToolCallId());
        assertTrue(result.warnings().isEmpty());
    }

    /** 指标在没有完整分组数据时只保留摘要视图，不能凭空生成图表。 */
    @Test
    void shouldHideMetricViewsWhenBreakdownIsIncomplete() {
        PresentationService service = new PresentationService(registry);
        PresentationService.PresentationResult result = service.present(
            "call-1", ToolRegistry.QUERY_BUSINESS_METRICS, "METRIC_RESULT", null);

        assertEquals(List.of(PresentationDescriptor.View.TEXT), result.descriptor().availableViews());
        assertEquals(PresentationDescriptor.View.TEXT, result.descriptor().defaultView());
        assertTrue(result.descriptor().chart() == null);
    }
}
