package me.zhengjie.agent.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.analysis.domain.ConversationContextHandle;
import me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import java.util.List;
import java.util.Set;

/** LLM 会话理解适配器；模型只描述句柄类型，具体句柄必须由 Resolver 在服务端绑定。 */
public class LlmConversationUnderstandingService implements ConversationUnderstandingService {
    private static final Set<String> ROOT = Set.of("schemaVersion", "questionType", "interactionMode", "referenceTurn", "frames", "ambiguities", "modelConfidence", "requiresClarification", "clarificationCode", "clarificationQuestion", "unknownReason");
    private static final Set<String> FRAME = Set.of("frameId", "goal", "targetEntity", "scope", "measures", "dimensions", "operations", "constraints", "outputShape", "missingInformation", "dependsOnFrameIds", "confidence");
    private static final Set<String> SCOPE = Set.of("type", "requiredKind", "requiredEntityType");
    private final ChatClient client;
    private final ObjectMapper mapper;
    private final BeanOutputConverter<ConversationUnderstandingResult> converter;
    private final ConversationUnderstandingValidator validator;
    private final DeepSeekChatOptions options = DeepSeekChatOptions.builder().responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build()).temperature(0D).build();
    public LlmConversationUnderstandingService(ChatClient.Builder builder, ObjectMapper mapper, ConversationUnderstandingValidator validator) {
        this.client = builder.build(); this.mapper = mapper; this.converter = new BeanOutputConverter<>(ConversationUnderstandingResult.class, mapper); this.validator = validator;
    }
    /** {@inheritDoc} */
    @Override
    public ConversationUnderstandingResult understand(String message, DiagnosisSlots slots, List<ConversationContextHandle> handles) {
        try {
            String raw = client.prompt().system("你是内部客服会话理解器，只返回 JSON；不得输出 SQL、URL、工具名、表名或任意结果字段。上下文引用只能声明 requiredKind 和 requiredEntityType，禁止输出 resolvedHandleId。")
                .user("Schema:" + converter.getFormat() + "。frames 最多三个。当前消息：" + safe(message) + "；确定性槽位：" + mapper.writeValueAsString(slots) + "；可引用句柄摘要：" + mapper.writeValueAsString(handles == null ? List.of() : handles))
                .options(options).call().content();
            JsonNode node = mapper.readTree(stripFence(raw));
            if (!node.isObject() || node.fieldNames().hasNext() && hasUnknown(node) || hasUnsafeFrames(node) || containsForbidden(node)) return clarification("MODEL_INVALID");
            ConversationUnderstandingResult result = mapper.treeToValue(node, ConversationUnderstandingResult.class);
            String invalid = validator.validate(result);
            return invalid == null ? result : clarification(invalid);
        } catch (Exception ignored) { return clarification("MODEL_UNAVAILABLE"); }
    }
    private boolean hasUnknown(JsonNode node) { java.util.Iterator<String> fields = node.fieldNames(); while (fields.hasNext()) if (!ROOT.contains(fields.next())) return true; return false; }
    /** 帧和范围对象只允许协议声明字段，防止模型用嵌套 JSON 夹带执行指令。 */
    private boolean hasUnsafeFrames(JsonNode root) {
        JsonNode frames = root.get("frames");
        if (frames == null || frames.isNull()) return false;
        if (!frames.isArray() || frames.size() > 3) return true;
        for (JsonNode frame : frames) {
            if (!frame.isObject() || hasUnknown(frame, FRAME)) return true;
            JsonNode scope = frame.get("scope");
            if (scope != null && !scope.isNull() && (!scope.isObject() || hasUnknown(scope, SCOPE))) return true;
        }
        return false;
    }
    private boolean hasUnknown(JsonNode node, Set<String> allowed) { java.util.Iterator<String> fields = node.fieldNames(); while (fields.hasNext()) if (!allowed.contains(fields.next())) return true; return false; }
    private boolean containsForbidden(JsonNode node) { if (node.isTextual()) { String value = node.asText().toLowerCase(); return value.contains("select ") || value.contains("http://") || value.contains("https://") || value.contains("/api/"); } for (JsonNode child : node) if (containsForbidden(child)) return true; return false; }
    private ConversationUnderstandingResult clarification(String code) { ConversationUnderstandingResult result = new ConversationUnderstandingResult(); result.setRequiresClarification(true); result.setClarificationCode(code); result.setClarificationQuestion("请补充需要查询的业务对象或条件。"); return result; }
    private String safe(String value) { return value == null ? "" : value; }
    private String stripFence(String value) { return value == null ? "{}" : value.replace("```json", "").replace("```", "").trim(); }
}
