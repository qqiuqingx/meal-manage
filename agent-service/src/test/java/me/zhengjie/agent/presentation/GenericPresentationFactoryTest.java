package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 通用展示工厂只生成有限表格、摘要或安全文本，不复制业务值。 */
class GenericPresentationFactoryTest {
    @Test
    void shouldCreateSafeTableForUnknownArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var data = mapper.readTree("{\"items\":[{\"label\":\"A\",\"value\":1}],\"warnings\":[]}");
        var schema = new CardSchemaInspector().inspect(data);

        PresentationDescriptor descriptor = new GenericPresentationFactory().createFallback(
            "call-1", "UNKNOWN_CARD", data, schema);

        assertEquals(PresentationDescriptor.DecisionSource.SYSTEM, descriptor.decisionSource());
        assertEquals(PresentationDescriptor.View.TABLE, descriptor.defaultView());
        assertEquals("items", descriptor.table().dataPath());
        assertNotNull(descriptor.table().columns());
    }

    @Test
    void shouldCreateTextWhenNoSafeStructureCanBeShown() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var data = mapper.readTree("{\"customerId\":1,\"warnings\":[\"LIMITED\"]}");
        var schema = new CardSchemaInspector().inspect(data);

        PresentationDescriptor descriptor = new GenericPresentationFactory().createFallback(
            "call-1", "UNKNOWN_CARD", data, schema);

        assertEquals(PresentationDescriptor.View.TEXT, descriptor.defaultView());
        assertEquals("展示格式暂不可用", descriptor.title());
    }
}
