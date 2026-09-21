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
 * 解密结果中的 CorpID 与本地配置不一致时抛出。
 *
 * 单独成类的目的：调用方需要把「CorpID 不匹配」与「报文解密失败」区分开，
 * 前者通常意味着企业微信后台与服务器配置的不是同一个企业，便于快速定位。
 *
 * @author qqx
 * @date 2026-09-21
 */
public class WeComCorpIdMismatchException extends WeComCryptoException {

    public WeComCorpIdMismatchException() {
        super("企业微信消息 CorpID 校验失败");
    }
}
