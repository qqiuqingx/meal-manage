/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.wecom.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.wecom.crypto.WeComMessageCryptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 企业微信消息回调参数配置。
 *
 * 说明：
 * 1. 只有 wecom.callback.enabled=true 时该配置类才被加载，Controller 与 Service 同样以该开关为条件，
 *    因此关闭回调时不会注册任何公网接口。
 * 2. 应用 Secret 不参与回调解密，不需要在此配置。
 *
 * @author qqx
 * @date 2026-09-21
 */
@Slf4j
@Data
@Configuration
@ConditionalOnProperty(prefix = "wecom.callback", name = "enabled", havingValue = "true")
@ConfigurationProperties(prefix = "wecom.callback")
public class WeComCallbackProperties {

    /**
     * 是否启用企业微信消息回调接口，默认关闭
     */
    private boolean enabled = false;

    /**
     * 企业 ID，用于校验解密结果中的 CorpID
     */
    private String corpId;

    /**
     * 企业微信后台「设置 API 接收」中填写的 Token，用于 SHA1 签名校验
     */
    private String token;

    /**
     * 企业微信后台生成的 EncodingAESKey，43 位，解码后为 32 字节 AES 密钥
     */
    private String encodingAesKey;

    /**
     * 启动时校验回调必需配置。
     *
     * 启用回调但配置缺失或 EncodingAESKey 非法时直接抛出异常阻断启动，
     * 避免对外暴露一个无法完成签名校验的公网接口。
     * 日志只输出非敏感信息，不打印 Token 与 EncodingAESKey。
     */
    @PostConstruct
    public void validateConfig() {
        if (!enabled) {
            return;
        }
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("企业微信回调配置错误：wecom.callback.token（WECOM_CALLBACK_TOKEN）未配置");
        }
        if (corpId == null || corpId.trim().isEmpty()) {
            throw new IllegalStateException("企业微信回调配置错误：wecom.callback.corp-id（WECOM_CORP_ID）未配置");
        }
        // 复用加解密组件中的校验逻辑：长度必须 43 位且解码后为 32 字节
        try {
            WeComMessageCryptor.decodeAesKey(encodingAesKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("企业微信回调配置错误：" + e.getMessage() + "，请检查 wecom.callback.encoding-aes-key（WECOM_CALLBACK_AES_KEY）");
        }
        log.info("企业微信消息回调接口已启用，corpId={}，回调请求参数、原始报文与解密后明文将完整打印到日志", corpId);
    }
}
