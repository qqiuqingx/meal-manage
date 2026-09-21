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
package me.zhengjie.modules.wecom.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import me.zhengjie.modules.wecom.config.WeComCallbackProperties;
import me.zhengjie.modules.wecom.crypto.WeComMessageCryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 企业微信回调全量日志测试。
 *
 * 回调日志始终全量输出，不需要任何开关：请求参数、原始密文报文、解密后完整明文、
 * 明文中的全部字段以及事件元数据摘要都必须落日志；任何路径都不允许出现 Token 与 EncodingAESKey。
 *
 * 通过挂载 logback ListAppender 直接断言日志内容，不启动 Spring 容器，
 * 因此不访问数据库、不依赖 Redis，也不会产生需要清理的测试数据。
 *
 * @author qqx
 * @date 2026-09-21
 */
class WeComCallbackServiceLogTest {

    private static final String TOKEN = "QDG6eK";
    private static final String AES_KEY = "jWmYm7qr5nMoAUwZRjGtBxmz3KA1tkAj3ykkR6q2B2C";
    private static final String CORP_ID = "wx5823bf96d3bd56c7";

    private static final String TIMESTAMP = "1758423000";
    private static final String NONCE = "10001";

    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;
    private WeComCallbackService service;
    private WeComMessageCryptor cryptor;

    @BeforeEach
    void setUp() {
        service = new WeComCallbackService(buildProperties());
        cryptor = new WeComMessageCryptor(TOKEN, AES_KEY, CORP_ID);

        serviceLogger = (Logger) LoggerFactory.getLogger(WeComCallbackService.class);
        // 测试环境的有效日志级别为 WARN，这里显式提升到 INFO 才能捕获被测日志，
        // 避免测试结果依赖外部 logging 配置
        originalLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.INFO);

        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(appender);
        appender.stop();
        serviceLogger.setLevel(originalLevel);
    }

    @Test
    void shouldLogRequestParamsRawBodyPlaintextAndAllFieldsForSubscribeEvent() {
        String plain = subscribePlainText();
        String encrypt = cryptor.encrypt(plain);
        String signature = cryptor.signature(TIMESTAMP, NONCE, encrypt);

        String result = service.handleEvent(signature, TIMESTAMP, NONCE, envelope(encrypt));

        assertThat(result).isEqualTo("success");

        // 1) 请求参数完整落日志
        assertThat(messages()).anyMatch(message -> message.contains("回调请求参数")
                && message.contains("msg_signature=" + signature)
                && message.contains("timestamp=" + TIMESTAMP)
                && message.contains("nonce=" + NONCE));

        // 2) 原始密文报文落日志
        assertThat(messages()).anyMatch(message -> message.contains("回调原始请求报文（密文 XML）")
                && message.contains(encrypt));

        // 3) 解密后的完整明文原样落日志，方便直接核对企业微信推送内容
        assertThat(messages()).anyMatch(message -> message.contains("解密后明文")
                && message.contains(plain));

        // 4) 明文中的全部字段逐个落日志
        assertThat(messages()).anyMatch(message -> message.contains("回调字段共 7 项"));
        assertThat(messages()).anyMatch(message -> message.contains("ToUserName=" + CORP_ID));
        assertThat(messages()).anyMatch(message -> message.contains("FromUserName=zhangsan"));
        assertThat(messages()).anyMatch(message -> message.contains("CreateTime=1348831860"));
        assertThat(messages()).anyMatch(message -> message.contains("MsgType=event"));
        assertThat(messages()).anyMatch(message -> message.contains("Event=subscribe"));
        assertThat(messages()).anyMatch(message -> message.contains("AgentID=1000002"));

        // 事件类型摘要行保留，作为快速检索回调事件的日志锚点
        assertThat(messages()).anyMatch(message -> message.contains("企业微信回调事件：MsgType=event, Event=subscribe"));
    }

    @Test
    void shouldLogFieldsOfAnyEventTypeWithoutHardcoding() {
        // 文本消息不属于事件推送，字段与 subscribe 完全不同，用于验证字段是按节点名动态解析的
        String plain = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[lisi]]></FromUserName>"
                + "<CreateTime>1348831860</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好，我想咨询订餐]]></Content>"
                + "<MsgId>1234567890123456</MsgId>"
                + "</xml>";
        String encrypt = cryptor.encrypt(plain);

        service.handleEvent(cryptor.signature(TIMESTAMP, NONCE, encrypt), TIMESTAMP, NONCE, envelope(encrypt));

        assertThat(messages()).anyMatch(message -> message.contains("回调字段共 6 项"));
        assertThat(messages()).anyMatch(message -> message.contains("Content=你好，我想咨询订餐"));
        assertThat(messages()).anyMatch(message -> message.contains("MsgId=1234567890123456"));
        assertThat(messages()).anyMatch(message -> message.contains("MsgType=text"));
    }

    @Test
    void shouldLogEchostrWithoutFieldParsingForUrlVerify() {
        String plain = "verify-echostr-1234567890";
        String echoStr = cryptor.encrypt(plain);

        String result = service.verifyUrl(cryptor.signature(TIMESTAMP, NONCE, echoStr), TIMESTAMP, NONCE, echoStr);

        assertThat(result).isEqualTo(plain);
        assertThat(messages()).anyMatch(message -> message.contains("回调原始echostr（密文）")
                && message.contains(echoStr));
        assertThat(messages()).anyMatch(message -> message.contains("解密后明文") && message.contains(plain));
        assertThat(messages()).anyMatch(message -> message.contains("企业微信回调 URL 验证通过"));
        // echostr 不是 XML，不应产生字段解析告警
        assertThat(messages()).noneMatch(message -> message.contains("明文字段解析失败"));
    }

    @Test
    void shouldNotLeakTokenOrAesKeyWhenSignatureInvalid() {
        String plain = subscribePlainText();
        String encrypt = cryptor.encrypt(plain);
        String badSignature = "bad-signature";

        // 生产代码内部会把异常转成 BadRequestException，这里直接捕获即可
        try {
            service.handleEvent(badSignature, TIMESTAMP, NONCE, envelope(encrypt));
        } catch (RuntimeException ignored) {
            // 签名校验失败属于预期路径
        }

        assertThat(messages()).anyMatch(message -> message.contains("企业微信回调签名校验失败"));
        // 签名失败时依然不能把 Token 与 EncodingAESKey 落到日志
        assertThat(messages()).noneMatch(message -> message.contains(TOKEN));
        assertThat(messages()).noneMatch(message -> message.contains(AES_KEY));
    }

    private WeComCallbackProperties buildProperties() {
        WeComCallbackProperties properties = new WeComCallbackProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setCorpId(CORP_ID);
        properties.setEncodingAesKey(AES_KEY);
        properties.validateConfig();
        return properties;
    }

    /**
     * subscribe 事件的真实明文结构，字段顺序与企业微信文档一致。
     */
    private String subscribePlainText() {
        return "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[zhangsan]]></FromUserName>"
                + "<CreateTime>1348831860</CreateTime>"
                + "<MsgType><![CDATA[event]]></MsgType>"
                + "<Event><![CDATA[subscribe]]></Event>"
                + "<EventKey><![CDATA[]]></EventKey>"
                + "<AgentID>1000002</AgentID>"
                + "</xml>";
    }

    /**
     * 构造企业微信推送的外层 XML 信封，Encrypt 为密文。
     */
    private String envelope(String encrypt) {
        return "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<Encrypt><![CDATA[" + encrypt + "]]></Encrypt>"
                + "<AgentID><![CDATA[1000002]]></AgentID>"
                + "</xml>";
    }

    private List<String> messages() {
        List<String> messages = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            messages.add(event.getFormattedMessage());
        }
        return messages;
    }
}
