package com.university.timetable.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 的本地加密存储。
 *
 * 使用 **Android Keystore** 生成不可导出的 AES-256 密钥（GCM 模式），
 * 密文以 Base64 存入 DataStore。密钥本身保存在系统 Keystore 中，
 * 不上传、不写入应用私有文件，卸载应用即失效。
 *
 * 说明：这不是端到端加密——Key 仍需在发起请求时解密到内存中。
 * 请勿在 root 设备/备份中依赖其绝对安全。
 */
object SecureKeyStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "timetable_ai_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH = 12

    private fun secretKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: createKey()
    }.getOrNull()

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不要求用户认证，保证后台调用也能解密
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    /** 加密明文；失败时返回空串（调用方需处理）。 */
    fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            // 格式：base64(iv) + ":" + base64(cipher)
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }.getOrNull()
    }

    /** 解密；失败返回空串。 */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return runCatching {
            val parts = stored.split(":")
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrDefault("")
    }

    /** 生成用于展示的掩码，例如 `sk-1234••••abcd`。 */
    fun mask(key: String): String {
        if (key.isEmpty()) return "未设置"
        if (key.length <= 8) return "••••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }

    @Suppress("unused")
    private fun unusedContext(context: Context) = context
}
