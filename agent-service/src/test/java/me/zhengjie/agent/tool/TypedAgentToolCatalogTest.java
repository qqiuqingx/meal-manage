package me.zhengjie.agent.tool;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 临时工具可被目录直接收集，不需要修改中心 Executor。 */
class TypedAgentToolCatalogTest {
    @Test
    void executesTemporaryToolWithoutExecutorBranch() {
        AgentTool<String, Integer> temporary = tool("temporaryCount", "customerProfile:list");
        TypedAgentToolCatalog catalog = new TypedAgentToolCatalog(List.of(temporary));
        @SuppressWarnings("unchecked") AgentTool<String, Integer> resolved = (AgentTool<String, Integer>) catalog.require("temporaryCount");
        assertEquals(3, resolved.execute("abc"));
        assertEquals(1, catalog.visibleTo(Set.of("temporaryCount")).size());
        assertEquals(0, catalog.visibleTo(Set.of()).size());
    }

    @Test
    void rejectsDuplicateToolNames() {
        assertThrows(IllegalStateException.class, () -> new TypedAgentToolCatalog(List.of(
            tool("duplicate", "customerProfile:list"), tool("duplicate", "customerOrder:list"))));
    }

    private AgentTool<String, Integer> tool(String name, String permission) {
        return new AgentTool<String, Integer>() {
            public ToolDescriptor descriptor() { return new ToolDescriptor(name, permission, 1, 1000, "INTERNAL", "String", "Integer"); }
            public Class<String> inputType() { return String.class; }
            public Class<Integer> outputType() { return Integer.class; }
            public Integer execute(String input) { return input.length(); }
        };
    }
}
