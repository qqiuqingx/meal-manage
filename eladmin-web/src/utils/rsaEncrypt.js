import JSEncrypt from 'jsencrypt/bin/jsencrypt.min'

// 密钥对生成 http://web.chacuo.net/netrsakeypair

const publicKey = 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApJH4cI0xV5E5wLr2QhvF3I+lg9Y/u5esR4iu2NgH/Uj7Ya4tL/rHYmHNWaFApDHG4HXuz95fFxeJZEjnQ+AP8K0ID3f4Mc1mEsHJ+p+PGEgUlXeomvTETAot7vobGB0agGQRo1TOnrYSCvRXBqYE6Y9L9WQUQ8hUpdfhyh2kSDRst8Ia7lyDqwdzIutg8HfBi92GyacWfRltBfopqhB8VIILsX9iINEMxFvW12DBfvfh0870OjI+LtmZHUL7XxOV9Ro+gzHN80yopWfJVw1ya87L4btTpXVFTW9TECLL1U/N3hKpggM51pSKGlyAXPQdlJV7zoa2TYA6DT6GoiCZZwIDAQAB'

// 加密
export function encrypt(txt) {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey) // 设置公钥
  return encryptor.encrypt(txt) // 对需要加密的数据进行加密
}

