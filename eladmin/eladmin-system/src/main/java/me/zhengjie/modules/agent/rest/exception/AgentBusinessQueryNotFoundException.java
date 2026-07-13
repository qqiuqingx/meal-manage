package me.zhengjie.modules.agent.rest.exception;

/** 受控业务对象不存在或不属于当前客户上下文时抛出的内部查询异常。 */
public class AgentBusinessQueryNotFoundException extends RuntimeException {
    public AgentBusinessQueryNotFoundException() {
        super("业务对象不存在或无权查看");
    }
}
