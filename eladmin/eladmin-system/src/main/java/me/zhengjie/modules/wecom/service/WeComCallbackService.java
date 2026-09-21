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

import lombok.extern.slf4j.Slf4j;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.wecom.config.WeComCallbackProperties;
import me.zhengjie.modules.wecom.crypto.WeComCorpIdMismatchException;
import me.zhengjie.modules.wecom.crypto.WeComCryptoException;
import me.zhengjie.modules.wecom.crypto.WeComMessageCryptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 企业微信消息回调服务。
 *
 * 职责：
 * 1. 校验请求参数完整性；
 * 2. 校验签名并解密 echostr 或事件报文；
 * 3. 把加解密异常统一转换成业务异常（对外只给出失败类型，不泄露密钥与解密细节）；
 * 4. 把请求参数、原始报文、解密后完整明文与全部明文字段完整打印到日志；
 * 5. POST 阶段仅记录日志，当前不落库、不做业务处理。
 *
 * @author qqx
 * @date 2026-09-21
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "wecom.callback", name = "enabled", havingValue = "true")
public class WeComCallbackService {

    /** 企业微信回调报文中承载密文的节点名 */
    private static final String ENCRYPT_TAG = "Encrypt";
    /** 成功应答，企业微信要求返回纯文本 success，否则会重试推送 */
    private static final String SUCCESS = "success";
    /** 用于提取最小事件元数据，兼容 CDATA 与非 CDATA 两种写法 */
    private static final String TAG_TEMPLATE = "<%s>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</%s>";

    private final WeComMessageCryptor cryptor;

    public WeComCallbackService(WeComCallbackProperties properties) {
        this.cryptor = new WeComMessageCryptor(properties.getToken(), properties.getEncodingAesKey(), properties.getCorpId());
    }

    /**
     * 处理企业微信「设置 API 接收」的 URL 验证请求。
     *
     * @param msgSignature 企业微信计算的签名
     * @param timestamp    时间戳
     * @param nonce        随机串
     * @param echoStr      加密的随机字符串
     * @return 解密后的明文，调用方需以 text/plain 原样返回
     */
    public String verifyUrl(String msgSignature, String timestamp, String nonce, String echoStr) {
        requireParams(msgSignature, timestamp, nonce, echoStr);
        logRequestDetail("URL 验证", msgSignature, timestamp, nonce, "echostr（密文）", echoStr);
        checkSignature(msgSignature, timestamp, nonce, echoStr);
        String plain = decrypt(echoStr, "URL 验证");
        logPlaintextDetail("URL 验证", plain);
        log.info("企业微信回调 URL 验证通过");
        return plain;
    }

    /**
     * 处理企业微信事件推送请求。
     *
     * 先做签名校验与解密，随后完整打印请求参数、原始密文报文、解密后明文以及明文中的全部字段。
     * 当前不写数据库。
     *
     * @param msgSignature 企业微信计算的签名
     * @param timestamp    时间戳
     * @param nonce        随机串
     * @param body         原始 XML 报文
     * @return 固定返回 success
     */
    public String handleEvent(String msgSignature, String timestamp, String nonce, String body) {
        requireParams(msgSignature, timestamp, nonce, body);
        logRequestDetail("事件消息", msgSignature, timestamp, nonce, "请求报文（密文 XML）", body);
        String encrypt = parseEncrypt(body);
        checkSignature(msgSignature, timestamp, nonce, encrypt);
        String plain = decrypt(encrypt, "事件消息");
        logEventMetadata(plain);
        logPlaintextDetail("事件消息", plain);
        return SUCCESS;
    }

    /**
     * 校验必填参数，任一为空即抛业务异常（对外响应 400）。
     */
    private void requireParams(String... params) {
        for (String param : params) {
            if (StringUtils.isBlank(param)) {
                throw new BadRequestException("企业微信回调请求参数不完整");
            }
        }
    }

    /**
     * 校验签名，失败时返回 400，不返回签名计算细节。
     */
    private void checkSignature(String msgSignature, String timestamp, String nonce, String encrypt) {
        if (!cryptor.verifySignature(msgSignature, timestamp, nonce, encrypt)) {
            log.warn("企业微信回调签名校验失败");
            throw new BadRequestException("企业微信回调签名校验失败");
        }
    }

    /**
     * 解密并转换异常，CorpID 不匹配与解密失败分别给出可定位的提示。
     */
    private String decrypt(String encrypt, String scene) {
        try {
            return cryptor.decrypt(encrypt);
        } catch (WeComCorpIdMismatchException e) {
            log.warn("企业微信回调 CorpID 校验失败");
            throw new BadRequestException("企业微信回调 CorpID 校验失败");
        } catch (WeComCryptoException e) {
            log.warn("企业微信回调报文解密失败，场景：{}", scene);
            throw new BadRequestException("企业微信回调报文解密失败");
        }
    }

    /**
     * 从回调 XML 中取出 Encrypt 节点内容。
     *
     * 解析时关闭 DTD 与外部实体，避免 XXE 攻击。
     */
    private String parseEncrypt(String body) {
        try {
            Document document = newDocumentBuilder()
                    .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = document.getElementsByTagName(ENCRYPT_TAG);
            if (nodes.getLength() == 0) {
                throw new BadRequestException("企业微信回调请求缺少 Encrypt 节点");
            }
            String encrypt = nodes.item(0).getTextContent();
            if (StringUtils.isBlank(encrypt)) {
                throw new BadRequestException("企业微信回调请求缺少 Encrypt 节点");
            }
            return encrypt.trim();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("企业微信回调报文解析失败：{}", e.getClass().getSimpleName());
            throw new BadRequestException("企业微信回调请求报文格式非法");
        }
    }

    /**
     * 构造关闭 DTD 与外部实体的 DocumentBuilder，防止 XXE 攻击。
     */
    private DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    /**
     * 打印回调请求的入口参数与原始报文。
     *
     * 记录的是企业微信传入的签名、时间戳、随机串与密文，均不含回调 Token 与 EncodingAESKey。
     *
     * @param scene       场景标识，用于区分 URL 验证与事件推送
     * @param payloadName 原始报文的中文名称
     */
    private void logRequestDetail(String scene, String msgSignature, String timestamp,
                                  String nonce, String payloadName, String payload) {
        log.info("[{}] 回调请求参数：msg_signature={}, timestamp={}, nonce={}", scene, msgSignature, timestamp, nonce);
        log.info("[{}] 回调原始{}：{}", scene, payloadName, payload);
    }

    /**
     * 打印解密后的完整明文与逐字段值。
     *
     * URL 验证场景的明文是 echostr 随机串而非 XML，没有字段结构，此时只输出明文本身。
     */
    private void logPlaintextDetail(String scene, String plain) {
        log.info("[{}] 解密后明文：{}", scene, plain);
        if (!plain.trim().startsWith("<")) {
            return;
        }
        Map<String, String> fields = parseFields(plain);
        if (fields.isEmpty()) {
            log.info("[{}] 明文字段解析为空，请以上面的明文原样内容为准", scene);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        log.info("[{}] 回调字段共 {} 项：{}", scene, fields.size(), builder);
    }

    /**
     * 解析解密后明文的一级子节点，返回有序的字段名与字段值。
     *
     * 按节点名动态解析而非硬编码字段，因此可覆盖企业微信全部事件与消息类型，
     * 新增事件类型无需改代码。解析失败时返回空结果，不影响正常应答。
     */
    private Map<String, String> parseFields(String plain) {
        Map<String, String> fields = new LinkedHashMap<>();
        try {
            Document document = newDocumentBuilder()
                    .parse(new ByteArrayInputStream(plain.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    fields.put(node.getNodeName(), node.getTextContent().trim());
                }
            }
        } catch (Exception e) {
            log.warn("企业微信回调明文字段解析失败：{}", e.getClass().getSimpleName());
        }
        return fields;
    }

    /**
     * 单独打印一行事件类型摘要，便于快速检索回调事件。
     *
     * 该行只含 MsgType/Event/ChangeType，作为日志锚点；完整字段见紧随其后的字段明细行。
     */
    private void logEventMetadata(String plain) {
        log.info("企业微信回调事件：MsgType={}, Event={}, ChangeType={}",
                extractTag(plain, "MsgType"),
                extractTag(plain, "Event"),
                extractTag(plain, "ChangeType"));
    }

    /**
     * 从明文报文中提取指定标签的值，取不到时返回空字符串，绝不返回整段明文。
     */
    private String extractTag(String plain, String tag) {
        Pattern pattern = Pattern.compile(String.format(TAG_TEMPLATE, tag, tag), Pattern.DOTALL);
        Matcher matcher = pattern.matcher(plain);
        return matcher.find() ? matcher.group(1).trim() : StringUtils.EMPTY;
    }
}
