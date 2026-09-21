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
package me.zhengjie.modules.wecom.crypto;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 企业微信加解密组件测试。
 *
 * 覆盖计划中的测试点：
 * 1. 官方合法样例能够解密；（使用企业微信官方文档给出的 URL 验证样例向量）
 * 2. 签名计算与校验；
 * 3. 错误签名被拒绝；
 * 4. 错误 CorpID 被拒绝；
 * 5. 非法 EncodingAESKey 在构造阶段即失败。
 *
 * 测试不访问数据库，不产生需要清理的测试数据。
 *
 * @author qqx
 * @date 2026-09-21
 */
class WeComMessageCryptorTest {

    /** 企业微信官方文档「加解密方案说明」中的 URL 验证样例参数 */
    private static final String OFFICIAL_TOKEN = "QDG6eK";
    private static final String OFFICIAL_AES_KEY = "jWmYm7qr5nMoAUwZRjGtBxmz3KA1tkAj3ykkR6q2B2C";
    private static final String OFFICIAL_CORP_ID = "wx5823bf96d3bd56c7";
    private static final String OFFICIAL_TIMESTAMP = "1409659589";
    private static final String OFFICIAL_NONCE = "263014780";
    private static final String OFFICIAL_ECHOSTR =
            "P9nAzCzyDtyTWESHep1vC5X9xho/qYX3Zpb4yKa9SKld1DsH3Iyt3tP3zNdtp+4RPcs8TgAE7OaBO+FZXvnaqQ==";
    private static final String OFFICIAL_MSG_SIGNATURE = "5c45ff5e21c57e6ad56bac8758b79b1d9ac89fd3";
    /** 官方样例 echostr 解密后的明文 */
    private static final String OFFICIAL_PLAIN = "1616140317555161061";

    @Test
    void shouldDecryptOfficialSample() {
        WeComMessageCryptor cryptor = new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, OFFICIAL_CORP_ID);
        assertEquals(OFFICIAL_PLAIN, cryptor.decrypt(OFFICIAL_ECHOSTR));
    }

    @Test
    void shouldVerifyOfficialSignature() {
        WeComMessageCryptor cryptor = new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, OFFICIAL_CORP_ID);
        assertEquals(OFFICIAL_MSG_SIGNATURE, cryptor.signature(OFFICIAL_TIMESTAMP, OFFICIAL_NONCE, OFFICIAL_ECHOSTR));
        assertTrue(cryptor.verifySignature(OFFICIAL_MSG_SIGNATURE, OFFICIAL_TIMESTAMP, OFFICIAL_NONCE, OFFICIAL_ECHOSTR));
    }

    @Test
    void shouldRejectWrongSignature() {
        WeComMessageCryptor cryptor = new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, OFFICIAL_CORP_ID);
        assertFalse(cryptor.verifySignature("0000", OFFICIAL_TIMESTAMP, OFFICIAL_NONCE, OFFICIAL_ECHOSTR));
        assertFalse(cryptor.verifySignature("", OFFICIAL_TIMESTAMP, OFFICIAL_NONCE, OFFICIAL_ECHOSTR));
        assertFalse(cryptor.verifySignature(null, OFFICIAL_TIMESTAMP, OFFICIAL_NONCE, OFFICIAL_ECHOSTR));
    }

    @Test
    void shouldRoundTripEncryptAndDecrypt() {
        WeComMessageCryptor cryptor = new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, OFFICIAL_CORP_ID);
        String plain = "<xml><MsgType><![CDATA[event]]></MsgType></xml>";
        // 企业微信协议要求密文可被同一密钥解回原文
        assertEquals(plain, cryptor.decrypt(cryptor.encrypt(plain)));
    }

    @Test
    void shouldRejectCorpIdMismatch() {
        // 使用另一个 CorpID 加密，再用本企业 CorpID 解密，应当被拒绝
        WeComMessageCryptor other = new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, "wx-other-corp-id");
        String encrypt = other.encrypt(OFFICIAL_PLAIN);
        WeComMessageCryptor cryptor = new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, OFFICIAL_CORP_ID);
        assertThrows(WeComCorpIdMismatchException.class, () -> cryptor.decrypt(encrypt));
    }

    @Test
    void shouldRejectIllegalCipherText() {
        WeComMessageCryptor cryptor = new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, OFFICIAL_CORP_ID);
        assertThrows(WeComCryptoException.class, () -> cryptor.decrypt("not-a-base64-content!!"));
        assertThrows(WeComCryptoException.class, () -> cryptor.decrypt(""));
        assertThrows(WeComCryptoException.class, () -> cryptor.decrypt(null));
    }

    @Test
    void shouldDecodeValidAesKey() {
        byte[] key = WeComMessageCryptor.decodeAesKey(OFFICIAL_AES_KEY);
        assertEquals(32, key.length);
        assertEquals(32, Base64.getDecoder().decode(OFFICIAL_AES_KEY + "=").length);
    }

    @Test
    void shouldRejectIllegalAesKey() {
        // 空值
        assertThrows(IllegalArgumentException.class, () -> WeComMessageCryptor.decodeAesKey(""));
        assertThrows(IllegalArgumentException.class, () -> WeComMessageCryptor.decodeAesKey(null));
        // 长度不足 43 位
        assertThrows(IllegalArgumentException.class, () -> WeComMessageCryptor.decodeAesKey("abcd"));
        // 长度合法但不是合法 Base64
        assertThrows(IllegalArgumentException.class,
                () -> WeComMessageCryptor.decodeAesKey(StringUtils.repeat('!', 43)));
        // 构造器同样应当拒绝非法密钥
        assertThrows(IllegalArgumentException.class,
                () -> new WeComMessageCryptor(OFFICIAL_TOKEN, "abcd", OFFICIAL_CORP_ID));
        assertThrows(IllegalArgumentException.class,
                () -> new WeComMessageCryptor("", OFFICIAL_AES_KEY, OFFICIAL_CORP_ID));
        assertThrows(IllegalArgumentException.class,
                () -> new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, ""));
    }

    @Test
    void shouldHandleChinesePlainText() {
        WeComMessageCryptor cryptor = new WeComMessageCryptor(OFFICIAL_TOKEN, OFFICIAL_AES_KEY, OFFICIAL_CORP_ID);
        String plain = "企业微信中文消息";
        assertEquals(plain, cryptor.decrypt(cryptor.encrypt(plain)));
    }
}
