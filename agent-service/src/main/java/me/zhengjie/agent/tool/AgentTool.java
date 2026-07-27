package me.zhengjie.agent.tool;

/** 强类型内部工具 SPI；模型和业务层均不得绕过目录直接调用 HTTP 客户端。 */
public interface AgentTool<I, O> {
    ToolDescriptor descriptor();
    Class<I> inputType();
    Class<O> outputType();
    O execute(I input);
}
