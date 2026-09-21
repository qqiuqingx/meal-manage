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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.annotation.rest.AnonymousGetMapping;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.modules.wecom.service.WeComCallbackService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 企业微信消息回调接口。
 *
 * 接口说明：
 * 1. 使用 @AnonymousGetMapping / @AnonymousPostMapping 实现匿名访问，不修改 SpringSecurityConfig；
 * 2. 虽然匿名放行，但每次请求都必须通过 msg_signature 校验、AES 解密与 CorpID 校验；
 * 3. 成功响应必须为 text/plain，不做 JSON 包装、不加引号、不加额外换行，否则企业微信会判定校验失败。
 *
 * 仅在 wecom.callback.enabled=true 时注册，配置关闭时不暴露该公网接口。
 *
 * @author qqx
 * @date 2026-09-21
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wecom/callback")
@ConditionalOnProperty(prefix = "wecom.callback", name = "enabled", havingValue = "true")
@Api(tags = "企业微信：消息回调接口")
public class WeComCallbackController {

    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private final WeComCallbackService weComCallbackService;

    /**
     * 企业微信「设置 API 接收」的 URL 验证接口。
     *
     * @param msgSignature 企业微信计算的签名
     * @param timestamp    时间戳
     * @param nonce        随机串
     * @param echoStr      加密的随机字符串
     * @return 解密后的明文，text/plain 原样返回
     */
    @AnonymousGetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @ApiOperation("企业微信回调 URL 验证")
    public ResponseEntity<String> verifyUrl(@RequestParam(value = "msg_signature", required = false) String msgSignature,
                                            @RequestParam(value = "timestamp", required = false) String timestamp,
                                            @RequestParam(value = "nonce", required = false) String nonce,
                                            @RequestParam(value = "echostr", required = false) String echoStr) {
        String plain = weComCallbackService.verifyUrl(msgSignature, timestamp, nonce, echoStr);
        return plainText(plain);
    }

    /**
     * 企业微信事件推送接收接口。
     *
     * @param msgSignature 企业微信计算的签名
     * @param timestamp    时间戳
     * @param nonce        随机串
     * @param body         原始 XML 报文
     * @return 固定返回 success
     */
    @AnonymousPostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @ApiOperation("企业微信事件推送接收")
    public ResponseEntity<String> receiveEvent(@RequestParam(value = "msg_signature", required = false) String msgSignature,
                                               @RequestParam(value = "timestamp", required = false) String timestamp,
                                               @RequestParam(value = "nonce", required = false) String nonce,
                                               @RequestBody(required = false) String body) {
        String result = weComCallbackService.handleEvent(msgSignature, timestamp, nonce, body);
        return plainText(result);
    }

    /**
     * 统一构造 text/plain 响应，避免被 JSON 转换器包装。
     */
    private ResponseEntity<String> plainText(String content) {
        return ResponseEntity.ok().contentType(TEXT_PLAIN_UTF8).body(content);
    }
}
