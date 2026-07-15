package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.*;
import java.util.HashSet;
import java.util.Set;

/** 对多帧会话理解协议执行结构和依赖校验，拒绝不完整语义进入规划。 */
public class ConversationUnderstandingValidator {
    /** 返回稳定拒绝码；返回 null 表示可继续进行能力匹配。 */
    public String validate(ConversationUnderstandingResult result) {
        if (result == null || result.getFrames() == null || result.getFrames().isEmpty()) return "FRAME_REQUIRED";
        if (result.getFrames().size() > 3) return "FRAME_LIMIT_EXCEEDED";
        Set<String> ids = new HashSet<>();
        for (SemanticRequestFrame frame : result.getFrames()) {
            if (frame == null || frame.getGoal() == null || frame.getTargetEntity() == null || frame.getOutputShape() == null) return "FRAME_REQUIRED_FIELD_MISSING";
            if (frame.getFrameId() == null || !ids.add(frame.getFrameId())) return "FRAME_ID_INVALID";
            if (frame.getScope() != null && frame.getScope().getType() == SemanticScope.Type.CONTEXT_REFERENCE
                && frame.getScope().getResolvedHandleId() == null) return "CONTEXT_REFERENCE_MISSING";
        }
        for (SemanticRequestFrame frame : result.getFrames()) for (String dependency : frame.getDependsOnFrameIds()) {
            if (!ids.contains(dependency) || dependency.equals(frame.getFrameId())) return "FRAME_DEPENDENCY_INVALID";
        }
        return null;
    }
}
