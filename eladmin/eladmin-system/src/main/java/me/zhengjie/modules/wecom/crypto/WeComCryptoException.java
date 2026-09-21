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

/**
 * 企业微信消息加解密异常。
 *
 * 说明：异常信息只描述失败类型，不携带 Token、EncodingAESKey、密文或明文，
 * 避免敏感内容通过日志或接口响应外泄。
 *
 * @author qqx
 * @date 2026-09-21
 */
public class WeComCryptoException extends RuntimeException {

    public WeComCryptoException(String message) {
        super(message);
    }

    public WeComCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
