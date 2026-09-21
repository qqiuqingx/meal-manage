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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 企业微信消息加解密组件。
 *
 * 严格按照企业微信官方加解密方案实现（SHA1 签名 + AES-256-CBC + 32 字节 PKCS#7 填充 + CorpID 校验），
 * 不引入微信官方 SDK，也不自定义加密协议。
 *
 * 明文结构（企业微信规定）：16 字节随机串 + 4 字节网络字节序的消息长度 + 消息体 + CorpID + PKCS#7 填充。
 *
 * 参考文档：
 * https://developer.work.weixin.qq.com/document/path/90968
 * https://developer.work.weixin.qq.com/document/path/90307
 *
 * @author qqx
 * @date 2026-09-21
 */
public class WeComMessageCryptor {

    /** EncodingAESKey 固定长度（43 位 Base64 字符，解码后为 32 字节 AES 密钥） */
    public static final int ENCODING_AES_KEY_LENGTH = 43;

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String AES_ALGORITHM = "AES";
    /** 企业微信使用 AES-CBC 且自行处理填充，因此使用 NoPadding 手动做 PKCS#7 */
    private static final String AES_CIPHER_TRANSFORMATION = "AES/CBC/NoPadding";
    private static final int AES_KEY_SIZE = 32;
    private static final int IV_SIZE = 16;
    /** 明文头部随机串长度 */
    private static final int RANDOM_SIZE = 16;
    /** 明文头部消息长度字段长度 */
    private static final int LENGTH_FIELD_SIZE = 4;
    /** 企业微信 PKCS#7 填充块大小，固定 32 字节 */
    private static final int PKCS7_BLOCK_SIZE = 32;

    /** 企业微信回调 Token，参与签名计算 */
    private final String token;
    /** AES 密钥（EncodingAESKey 解码后的 32 字节） */
    private final byte[] aesKey;
    /** 企业 ID，解密后必须与明文尾部的 CorpID 一致 */
    private final String corpId;

    /**
     * 构造加解密组件。
     *
     * @param token          企业微信回调 Token
     * @param encodingAesKey 企业微信 EncodingAESKey（43 位）
     * @param corpId         企业 ID
     */
    public WeComMessageCryptor(String token, String encodingAesKey, String corpId) {
        if (StringUtils.isBlank(token)) {
            throw new IllegalArgumentException("企业微信回调 Token 不能为空");
        }
        if (StringUtils.isBlank(corpId)) {
            throw new IllegalArgumentException("企业微信 CorpID 不能为空");
        }
        this.token = token;
        this.aesKey = decodeAesKey(encodingAesKey);
        this.corpId = corpId;
    }

    /**
     * 校验并解码 EncodingAESKey。
     *
     * @param encodingAesKey 企业微信后台生成的 43 位 EncodingAESKey
     * @return 解码后的 32 字节 AES 密钥
     * @throws IllegalArgumentException 长度不为 43 位、不是合法 Base64 或解码后不是 32 字节
     */
    public static byte[] decodeAesKey(String encodingAesKey) {
        if (StringUtils.isBlank(encodingAesKey)) {
            throw new IllegalArgumentException("企业微信 EncodingAESKey 不能为空");
        }
        if (encodingAesKey.length() != ENCODING_AES_KEY_LENGTH) {
            throw new IllegalArgumentException("企业微信 EncodingAESKey 长度必须为 43 位，当前为 " + encodingAesKey.length() + " 位");
        }
        byte[] key;
        try {
            // 企业微信的 EncodingAESKey 省略了 Base64 的 '=' 补位，这里补回后再解码
            key = Base64.getDecoder().decode(encodingAesKey + "=");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("企业微信 EncodingAESKey 不是合法的 Base64 字符串");
        }
        if (key.length != AES_KEY_SIZE) {
            throw new IllegalArgumentException("企业微信 EncodingAESKey 解码后必须为 32 字节，当前为 " + key.length + " 字节");
        }
        return key;
    }

    /**
     * 计算企业微信消息签名。
     *
     * 规则：将 token、timestamp、nonce、encrypt 四个字符串按字典序排序后拼接，再做 SHA1 得到十六进制小写串。
     *
     * @param timestamp 时间戳
     * @param nonce     随机串
     * @param encrypt   加密内容（GET 为 echostr，POST 为 XML 中的 Encrypt 节点值）
     * @return 十六进制小写签名
     */
    public String signature(String timestamp, String nonce, String encrypt) {
        String[] source = new String[]{token, timestamp, nonce, encrypt};
        Arrays.sort(source);
        StringBuilder builder = new StringBuilder();
        for (String item : source) {
            builder.append(item);
        }
        return sha1(builder.toString());
    }

    /**
     * 校验企业微信请求签名是否合法。
     *
     * @param msgSignature 企业微信传入的 msg_signature
     * @param timestamp    时间戳
     * @param nonce        随机串
     * @param encrypt      加密内容
     * @return true 表示签名一致
     */
    public boolean verifySignature(String msgSignature, String timestamp, String nonce, String encrypt) {
        if (StringUtils.isBlank(msgSignature)) {
            return false;
        }
        String expected = signature(timestamp, nonce, encrypt);
        // 使用 MessageDigest.isEqual 做等长比较，避免提前返回
        return MessageDigest.isEqual(expected.getBytes(CHARSET), msgSignature.getBytes(CHARSET));
    }

    /**
     * 解密企业微信加密内容。
     *
     * @param encrypt Base64 编码的密文
     * @return 解密后的明文
     * @throws WeComCryptoException           密文格式非法、解密失败或填充非法
     * @throws WeComCorpIdMismatchException   明文中的 CorpID 与本企业配置不一致
     */
    public String decrypt(String encrypt) {
        if (StringUtils.isBlank(encrypt)) {
            throw new WeComCryptoException("企业微信密文不能为空");
        }
        byte[] cipherText;
        try {
            cipherText = Base64.getDecoder().decode(encrypt);
        } catch (IllegalArgumentException e) {
            throw new WeComCryptoException("企业微信密文不是合法的 Base64 字符串");
        }
        byte[] plainBytes;
        try {
            Cipher cipher = Cipher.getInstance(AES_CIPHER_TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, AES_ALGORITHM);
            // 企业微信规定 IV 取 AES 密钥的前 16 字节
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, IV_SIZE));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            plainBytes = cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new WeComCryptoException("企业微信消息解密失败");
        }
        return decodePlainBytes(plainBytes);
    }

    /**
     * 加密明文，主要供单元测试构造符合企业微信协议的报文。
     *
     * @param plainText 待加密明文
     * @return Base64 编码的密文
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            throw new WeComCryptoException("待加密的明文不能为空");
        }
        byte[] msgBytes = plainText.getBytes(CHARSET);
        byte[] corpIdBytes = corpId.getBytes(CHARSET);
        byte[] randomBytes = new byte[RANDOM_SIZE];
        new SecureRandom().nextBytes(randomBytes);

        ByteBuffer buffer = ByteBuffer.allocate(RANDOM_SIZE + LENGTH_FIELD_SIZE + msgBytes.length + corpIdBytes.length);
        buffer.put(randomBytes);
        // 消息长度使用网络字节序（大端）
        buffer.putInt(msgBytes.length);
        buffer.put(msgBytes);
        buffer.put(corpIdBytes);

        byte[] padded = pkcs7Pad(buffer.array());
        try {
            Cipher cipher = Cipher.getInstance(AES_CIPHER_TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, AES_ALGORITHM);
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, IV_SIZE));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
            return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
        } catch (Exception e) {
            throw new WeComCryptoException("企业微信消息加密失败");
        }
    }

    /**
     * 按企业微信协议解析解密后的字节数组：跳过随机串与长度字段，取出消息体并校验 CorpID。
     */
    private String decodePlainBytes(byte[] plainBytes) {
        byte[] content = pkcs7Unpad(plainBytes);
        if (content.length < RANDOM_SIZE + LENGTH_FIELD_SIZE) {
            throw new WeComCryptoException("企业微信消息解密内容长度非法");
        }
        ByteBuffer buffer = ByteBuffer.wrap(content);
        buffer.position(RANDOM_SIZE);
        int msgLength = buffer.getInt();
        if (msgLength < 0 || msgLength > buffer.remaining()) {
            throw new WeComCryptoException("企业微信消息解密内容长度非法");
        }
        byte[] msgBytes = new byte[msgLength];
        buffer.get(msgBytes);
        // 明文尾部剩余内容即为企业微信写入的 CorpID
        String receivedCorpId = new String(content, buffer.position(), buffer.remaining(), CHARSET);
        if (!corpId.equals(receivedCorpId)) {
            throw new WeComCorpIdMismatchException();
        }
        return new String(msgBytes, CHARSET);
    }

    /**
     * PKCS#7 填充，块大小为 32 字节。
     */
    private static byte[] pkcs7Pad(byte[] source) {
        int remainder = source.length % PKCS7_BLOCK_SIZE;
        int padLength = PKCS7_BLOCK_SIZE - remainder;
        byte[] padded = new byte[source.length + padLength];
        System.arraycopy(source, 0, padded, 0, source.length);
        for (int i = source.length; i < padded.length; i++) {
            padded[i] = (byte) padLength;
        }
        return padded;
    }

    /**
     * PKCS#7 去填充，块大小为 32 字节；填充字节不合法时抛出异常，避免把脏数据当明文返回。
     */
    private static byte[] pkcs7Unpad(byte[] source) {
        if (source.length == 0) {
            throw new WeComCryptoException("企业微信消息解密内容为空");
        }
        int padLength = source[source.length - 1] & 0xFF;
        if (padLength < 1 || padLength > PKCS7_BLOCK_SIZE || padLength > source.length) {
            throw new WeComCryptoException("企业微信消息填充格式非法");
        }
        for (int i = source.length - padLength; i < source.length; i++) {
            if ((source[i] & 0xFF) != padLength) {
                throw new WeComCryptoException("企业微信消息填充格式非法");
            }
        }
        return Arrays.copyOfRange(source, 0, source.length - padLength);
    }

    /**
     * SHA1 摘要，返回十六进制小写字符串。
     */
    private static String sha1(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(source.getBytes(CHARSET));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new WeComCryptoException("当前运行环境不支持 SHA-1 算法");
        }
    }
}
