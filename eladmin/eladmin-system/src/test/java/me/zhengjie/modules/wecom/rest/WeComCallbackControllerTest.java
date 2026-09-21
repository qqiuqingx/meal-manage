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
package me.zhengjie.modules.wecom.rest;

import me.zhengjie.annotation.rest.AnonymousAccess;
import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.modules.wecom.config.WeComCallbackProperties;
import me.zhengjie.modules.wecom.crypto.WeComMessageCryptor;
import me.zhengjie.modules.wecom.service.WeComCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 企业微信消息回调接口测试。
 *
 * 覆盖计划中的测试点：
 * 2. GET 合法签名返回正确明文；
 * 3. 错误签名返回 400；
 * 4. 错误 CorpID 返回 400；
 * 5. 缺少参数返回 400；
 * 7. POST 合法事件返回 success；
 * 8. POST 非法 XML 或错误签名返回 400；
 * 9. GET、POST 无 JWT 时可以进入 Controller（通过匿名标记验证，Spring Security 会据此放行）。
 *
 * 采用 MockMvc standalone 方式，只加载 Controller 与全局异常处理，不启动 Spring 容器，
 * 因此不访问数据库、不依赖 Redis，也不会产生需要清理的测试数据。
 *
 * @author qqx
 * @date 2026-09-21
 */
class WeComCallbackControllerTest {

    private static final String TOKEN = "QDG6eK";
    private static final String AES_KEY = "jWmYm7qr5nMoAUwZRjGtBxmz3KA1tkAj3ykkR6q2B2C";
    private static final String CORP_ID = "wx5823bf96d3bd56c7";

    private static final String OFFICIAL_TIMESTAMP = "1409659589";
    private static final String OFFICIAL_NONCE = "263014780";
    private static final String OFFICIAL_ECHOSTR =
            "P9nAzCzyDtyTWESHep1vC5X9xho/qYX3Zpb4yKa9SKld1DsH3Iyt3tP3zNdtp+4RPcs8TgAE7OaBO+FZXvnaqQ==";
    private static final String OFFICIAL_MSG_SIGNATURE = "5c45ff5e21c57e6ad56bac8758b79b1d9ac89fd3";
    private static final String OFFICIAL_PLAIN = "1616140317555161061";

    private static final String CALLBACK_URI = "/api/wecom/callback";

    private MockMvc mockMvc;
    private WeComMessageCryptor cryptor;

    @BeforeEach
    void setUp() {
        WeComCallbackProperties properties = new WeComCallbackProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setCorpId(CORP_ID);
        properties.setEncodingAesKey(AES_KEY);
        // 显式执行一次启动校验，确保测试使用的配置本身就是合法的
        properties.validateConfig();

        WeComCallbackService service = new WeComCallbackService(properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new WeComCallbackController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        cryptor = new WeComMessageCryptor(TOKEN, AES_KEY, CORP_ID);
    }

    @Test
    void shouldReturnPlainTextWhenOfficialUrlVerifyRequestArrives() throws Exception {
        mockMvc.perform(get(CALLBACK_URI)
                        .param("msg_signature", OFFICIAL_MSG_SIGNATURE)
                        .param("timestamp", OFFICIAL_TIMESTAMP)
                        .param("nonce", OFFICIAL_NONCE)
                        .param("echostr", OFFICIAL_ECHOSTR))
                .andExpect(status().isOk())
                // 必须是 text/plain，不能被 JSON 包装
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                // 明文必须原样返回，不含引号与额外换行
                .andExpect(content().string(OFFICIAL_PLAIN));
    }

    @Test
    void shouldReturnPlainTextWhenSignatureIsValid() throws Exception {
        String timestamp = "1758423000";
        String nonce = "10001";
        String plain = "verify-echostr-1234567890";
        String echoStr = cryptor.encrypt(plain);
        String signature = cryptor.signature(timestamp, nonce, echoStr);

        mockMvc.perform(get(CALLBACK_URI)
                        .param("msg_signature", signature)
                        .param("timestamp", timestamp)
                        .param("nonce", nonce)
                        .param("echostr", echoStr))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(plain));
    }

    @Test
    void shouldReturn400WhenSignatureIsWrong() throws Exception {
        mockMvc.perform(get(CALLBACK_URI)
                        .param("msg_signature", "5c45ff5e21c57e6ad56bac8758b79b1d9ac89fd3")
                        .param("timestamp", OFFICIAL_TIMESTAMP)
                        .param("nonce", OFFICIAL_NONCE)
                        .param("echostr", OFFICIAL_ECHOSTR.replace("P9nAz", "AAAAz")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenParamMissing() throws Exception {
        // 缺少 echostr
        mockMvc.perform(get(CALLBACK_URI)
                        .param("msg_signature", OFFICIAL_MSG_SIGNATURE)
                        .param("timestamp", OFFICIAL_TIMESTAMP)
                        .param("nonce", OFFICIAL_NONCE))
                .andExpect(status().isBadRequest());

        // 所有参数都缺失
        mockMvc.perform(get(CALLBACK_URI))
                .andExpect(status().isBadRequest());

        // POST 缺少 msg_signature
        mockMvc.perform(post(CALLBACK_URI)
                        .param("timestamp", OFFICIAL_TIMESTAMP)
                        .param("nonce", OFFICIAL_NONCE)
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<xml><Encrypt><![CDATA[abc]]></Encrypt></xml>"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenCorpIdMismatch() throws Exception {
        String timestamp = "1758423001";
        String nonce = "10002";
        // 使用其它企业的 CorpID 加密，签名仍然正确，但 CorpID 校验应当失败
        WeComMessageCryptor otherCorpCryptor = new WeComMessageCryptor(TOKEN, AES_KEY, "wx-other-corp-id");
        String echoStr = otherCorpCryptor.encrypt("verify-echostr");
        String signature = cryptor.signature(timestamp, nonce, echoStr);

        mockMvc.perform(get(CALLBACK_URI)
                        .param("msg_signature", signature)
                        .param("timestamp", timestamp)
                        .param("nonce", nonce)
                        .param("echostr", echoStr))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CorpID")));
    }

    @Test
    void shouldReturnSuccessWhenValidEventArrives() throws Exception {
        String timestamp = "1758423002";
        String nonce = "10003";
        String eventXml = "<xml><ToUserName><![CDATA[wx5823bf96d3bd56c7]]></ToUserName>"
                + "<FromUserName><![CDATA[sys]]></FromUserName><CreateTime>1758423002</CreateTime>"
                + "<MsgType><![CDATA[event]]></MsgType><Event><![CDATA[change_contact]]></Event>"
                + "<ChangeType><![CDATA[update_user]]></ChangeType><UserID><![CDATA[zhangsan]]></UserID></xml>";
        String encrypt = cryptor.encrypt(eventXml);
        String signature = cryptor.signature(timestamp, nonce, encrypt);
        String body = "<xml><ToUserName><![CDATA[wx5823bf96d3bd56c7]]></ToUserName>"
                + "<Encrypt><![CDATA[" + encrypt + "]]></Encrypt>"
                + "<AgentID><![CDATA[1000002]]></AgentID></xml>";

        mockMvc.perform(post(CALLBACK_URI)
                        .param("msg_signature", signature)
                        .param("timestamp", timestamp)
                        .param("nonce", nonce)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                // 企业微信要求返回纯文本 success，否则会重试推送
                .andExpect(content().string("success"));
    }

    @Test
    void shouldReturn400WhenEventSignatureIsWrong() throws Exception {
        String timestamp = "1758423003";
        String nonce = "10004";
        String encrypt = cryptor.encrypt("<xml><MsgType><![CDATA[event]]></MsgType></xml>");
        String body = "<xml><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>";

        mockMvc.perform(post(CALLBACK_URI)
                        .param("msg_signature", "ffffffffffffffffffffffffffffffffffffffff")
                        .param("timestamp", timestamp)
                        .param("nonce", nonce)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenEventXmlIllegal() throws Exception {
        String timestamp = "1758423004";
        String nonce = "10005";

        // 非法 XML：未闭合标签，签名无关紧要，解析阶段就会失败
        String illegalBody = "<xml><Encrypt><![CDATA[abc]";
        assertBadRequestMessage(
                mockMvc.perform(post(CALLBACK_URI)
                                .param("msg_signature", cryptor.signature(timestamp, nonce, "abc"))
                                .param("timestamp", timestamp)
                                .param("nonce", nonce)
                                .contentType(MediaType.APPLICATION_XML)
                                .content(illegalBody))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "格式非法");

        // 合法 XML 但缺少 Encrypt 节点
        String noEncryptBody = "<xml><ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName></xml>";
        assertBadRequestMessage(
                mockMvc.perform(post(CALLBACK_URI)
                                .param("msg_signature", cryptor.signature(timestamp, nonce, "abc"))
                                .param("timestamp", timestamp)
                                .param("nonce", nonce)
                                .contentType(MediaType.APPLICATION_XML)
                                .content(noEncryptBody))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "Encrypt");
    }

    /**
     * standalone MockMvc 下 JSON 响应未声明字符集，需显式按 UTF-8 读取响应体后再断言中文提示。
     */
    private void assertBadRequestMessage(MvcResult result, String expectedFragment) throws Exception {
        result.getResponse().setCharacterEncoding("UTF-8");
        assertTrue(result.getResponse().getContentAsString().contains(expectedFragment),
                "响应体未包含预期提示：" + expectedFragment);
    }

    @Test
    void shouldNotLeakSecretWhenRequestIllegal() throws Exception {
        mockMvc.perform(get(CALLBACK_URI)
                        .param("msg_signature", "bad-signature")
                        .param("timestamp", OFFICIAL_TIMESTAMP)
                        .param("nonce", OFFICIAL_NONCE)
                        .param("echostr", OFFICIAL_ECHOSTR))
                .andExpect(status().isBadRequest())
                // 响应体不能包含 Token 与 EncodingAESKey
                .andExpect(content().string(not(containsString(TOKEN))))
                .andExpect(content().string(not(containsString(AES_KEY))));
    }

    @Test
    void shouldBeAnonymousAccessibleWithoutJwt() throws Exception {
        // 匿名放行依赖 @AnonymousAccess 标记（由 @AnonymousGetMapping/@AnonymousPostMapping 携带），
        // SpringSecurityConfig 启动时通过 AnonTagUtils 扫描方法上的 @AnonymousAccess 并加入 permitAll 列表，
        // 因此无需修改 SpringSecurityConfig，也无需携带 JWT。
        Method verifyMethod = WeComCallbackController.class.getMethod(
                "verifyUrl", String.class, String.class, String.class, String.class);
        Method receiveMethod = WeComCallbackController.class.getMethod(
                "receiveEvent", String.class, String.class, String.class, String.class);

        assertNotNull(AnnotationUtils.findAnnotation(verifyMethod, AnonymousAccess.class));
        assertNotNull(AnnotationUtils.findAnnotation(receiveMethod, AnonymousAccess.class));

        // 且必须由 wecom.callback.enabled=true 控制，关闭时不注册该公网接口
        ConditionalOnProperty conditional =
                AnnotationUtils.findAnnotation(WeComCallbackController.class, ConditionalOnProperty.class);
        assertNotNull(conditional);
        assertTrue(conditional.havingValue().equalsIgnoreCase("true"));
    }

    @Test
    void shouldWireUpBeansWhenEnabled() {
        // 校验 Properties、Service、Controller 三者的条件注解一致，开启开关后能正常注入
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(WeComCallbackProperties.class, WeComCallbackService.class,
                        WeComCallbackController.class)
                .withPropertyValues(
                        "wecom.callback.enabled=true",
                        "wecom.callback.corp-id=" + CORP_ID,
                        "wecom.callback.token=" + TOKEN,
                        "wecom.callback.encoding-aes-key=" + AES_KEY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WeComCallbackProperties.class);
                    assertThat(context).hasSingleBean(WeComCallbackService.class);
                    assertThat(context).hasSingleBean(WeComCallbackController.class);
                });
    }
}