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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 企业微信回调配置校验测试。
 *
 * 覆盖计划中的测试点：
 * 6. 非法 AES Key 启动失败（配置缺失同样应当启动失败）；
 * 以及：关闭开关时不加载配置类，也不会因缺少密钥而阻断启动。
 *
 * 测试不访问数据库，不产生需要清理的测试数据。
 *
 * @author qqx
 * @date 2026-09-21
 */
class WeComCallbackPropertiesTest {

    private static final String AES_KEY = "jWmYm7qr5nMoAUwZRjGtBxmz3KA1tkAj3ykkR6q2B2C";
    private static final String TOKEN = "QDG6eK";
    private static final String CORP_ID = "wx5823bf96d3bd56c7";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(WeComCallbackProperties.class);

    @Test
    void shouldPassValidationWhenConfigComplete() {
        WeComCallbackProperties properties = buildProperties(true, CORP_ID, TOKEN, AES_KEY);
        assertDoesNotThrow(properties::validateConfig);
    }

    @Test
    void shouldRejectMissingToken() {
        WeComCallbackProperties properties = buildProperties(true, CORP_ID, "", AES_KEY);
        assertThrows(IllegalStateException.class, properties::validateConfig);
    }

    @Test
    void shouldRejectMissingCorpId() {
        WeComCallbackProperties properties = buildProperties(true, "", TOKEN, AES_KEY);
        assertThrows(IllegalStateException.class, properties::validateConfig);
    }

    @Test
    void shouldRejectIllegalAesKey() {
        // 长度不足
        assertThrows(IllegalStateException.class,
                () -> buildProperties(true, CORP_ID, TOKEN, "short-key").validateConfig());
        // 长度正确但不是合法 Base64
        assertThrows(IllegalStateException.class,
                () -> buildProperties(true, CORP_ID, TOKEN,
                        org.apache.commons.lang3.StringUtils.repeat('!', 43)).validateConfig());
        // 未配置
        assertThrows(IllegalStateException.class,
                () -> buildProperties(true, CORP_ID, TOKEN, null).validateConfig());
    }

    @Test
    void shouldSkipValidationWhenDisabled() {
        WeComCallbackProperties properties = buildProperties(false, "", "", "");
        assertDoesNotThrow(properties::validateConfig);
    }

    @Test
    void shouldNotLoadBeanWhenCallbackDisabled() {
        // 关闭开关时不加载配置类：既不会校验失败，也不会暴露回调接口
        runner.withPropertyValues("wecom.callback.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WeComCallbackProperties.class);
                });
    }

    @Test
    void shouldFailStartupWhenAesKeyIllegal() {
        // 启用回调但 EncodingAESKey 非法时，不允许应用启动
        runner.withPropertyValues(
                        "wecom.callback.enabled=true",
                        "wecom.callback.corp-id=" + CORP_ID,
                        "wecom.callback.token=" + TOKEN,
                        "wecom.callback.encoding-aes-key=illegal-aes-key")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailStartupWhenTokenMissing() {
        runner.withPropertyValues(
                        "wecom.callback.enabled=true",
                        "wecom.callback.corp-id=" + CORP_ID,
                        "wecom.callback.encoding-aes-key=" + AES_KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldStartUpWhenConfigComplete() {
        runner.withPropertyValues(
                        "wecom.callback.enabled=true",
                        "wecom.callback.corp-id=" + CORP_ID,
                        "wecom.callback.token=" + TOKEN,
                        "wecom.callback.encoding-aes-key=" + AES_KEY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WeComCallbackProperties.class);
                });
    }

    private WeComCallbackProperties buildProperties(boolean enabled, String corpId, String token, String encodingAesKey) {
        WeComCallbackProperties properties = new WeComCallbackProperties();
        properties.setEnabled(enabled);
        properties.setCorpId(corpId);
        properties.setToken(token);
        properties.setEncodingAesKey(encodingAesKey);
        return properties;
    }
}
