package me.zhengjie.agent.api.error;

/** 表示调用方提交了当前 Agent 不支持的服务间契约版本。 */
public class AgentContractException extends RuntimeException {
    public AgentContractException(String message) {
        super(message);
    }
}
