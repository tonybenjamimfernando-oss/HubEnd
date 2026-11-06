package com.hubops.vislumbrops.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoProvider {

    private val secureRandom = SecureRandom()

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    fun sha256(text: String): String = sha256(text.toByteArray())

    fun hmacSha256(data: ByteArray, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    fun hmacSha256(data: ByteArray, key: SecretKey): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    /** Stable app-local HMAC key backed by Android Keystore (never exported as bytes). */
    fun getOrCreateSigningKey(alias: String = "vislumbrops.certification.hmac.v1"): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setDigests(KeyProperties.DIGEST_SHA256).build()
        )
        return generator.generateKey()
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    fun generateKey(sizeBytes: Int = 32): ByteArray =
        ByteArray(sizeBytes).also { secureRandom.nextBytes(it) }

    fun shannonEntropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val freq = s.groupingBy { it }.eachCount()
        val len = s.length.toDouble()
        return -freq.values.sumOf { (it / len) * (kotlin.math.ln(it / len) / kotlin.math.ln(2.0)) }
    }
}
