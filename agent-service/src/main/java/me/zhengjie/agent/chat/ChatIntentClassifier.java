package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.IntentClassificationRequest;
import me.zhengjie.agent.domain.dto.IntentClassificationResult;

/**
 * 聊天意图分类器统一接口。
 */
public interface ChatIntentClassifier {

    IntentClassificationResult classify(IntentClassificationRequest request);
}
