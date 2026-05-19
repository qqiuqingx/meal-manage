package me.zhengjie.agent.service;

import me.zhengjie.agent.domain.dto.LlmConnectivityRequest;
import me.zhengjie.agent.domain.dto.LlmConnectivityResponse;

public interface LlmConnectivityService {

    LlmConnectivityResponse test(LlmConnectivityRequest request);
}
