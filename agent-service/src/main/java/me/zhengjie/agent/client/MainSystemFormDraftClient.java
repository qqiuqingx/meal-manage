package me.zhengjie.agent.client;

import me.zhengjie.agent.tool.input.formdraft.SaveFormDraftInput;
import me.zhengjie.agent.tool.output.FormDraftToolOutput;

/** Agent 保存辅助表单草稿的固定主系统端口。 */
public interface MainSystemFormDraftClient {
    /** 创建或修订草稿，副作用幂等由主系统负责。 */
    FormDraftToolOutput save(SaveFormDraftInput input);
}
