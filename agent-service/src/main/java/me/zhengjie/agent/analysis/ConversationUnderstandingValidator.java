package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.*;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
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
        Map<String, SemanticRequestFrame> byId = new HashMap<>();
        Map<String, Integer> sequence = new HashMap<>();
        for (int index = 0; index < result.getFrames().size(); index++) {
            SemanticRequestFrame frame = result.getFrames().get(index);
            byId.put(frame.getFrameId(), frame); sequence.put(frame.getFrameId(), index);
        }
        for (SemanticRequestFrame frame : result.getFrames()) for (String dependency : frame.getDependsOnFrameIds()) {
            if (!ids.contains(dependency) || dependency.equals(frame.getFrameId())) return "FRAME_DEPENDENCY_INVALID";
        }
        Set<String> visiting = new HashSet<>(); Set<String> visited = new HashSet<>();
        for (String id : ids) if (hasCycle(id, byId, visiting, visited)) return "FRAME_DEPENDENCY_CYCLE";
        for (int index = 0; index < result.getFrames().size(); index++) {
            for (String dependency : result.getFrames().get(index).getDependsOnFrameIds()) {
                if (sequence.get(dependency) >= index) return "FRAME_DEPENDENCY_ORDER_INVALID";
            }
        }
        return null;
    }

    /** 深度优先检测任意长度的依赖环，避免多请求顺序无法确定。 */
    private boolean hasCycle(String id, Map<String, SemanticRequestFrame> frames, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return false;
        if (!visiting.add(id)) return true;
        SemanticRequestFrame frame = frames.get(id);
        if (frame != null) for (String dependency : frame.getDependsOnFrameIds()) if (hasCycle(dependency, frames, visiting, visited)) return true;
        visiting.remove(id); visited.add(id); return false;
    }
}
