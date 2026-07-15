package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.ConversationContextHandle;
import me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import java.util.List;

/** 将当前输入及受控会话摘要解析为组合语义帧。 */
public interface ConversationUnderstandingService {
    /** 仅返回受控协议，不绑定模型提供的工具、SQL、字段或上下文句柄 ID。 */
    ConversationUnderstandingResult understand(String message, DiagnosisSlots slots, List<ConversationContextHandle> handles);
}
