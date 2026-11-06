package dev.hubops.core.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Configuração externa cifrada (tokens nunca ficam no código). */
class SecureConfig(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "hubops_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var githubToken: String
        get() = prefs.getString(K_GH_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(K_GH_TOKEN, v.trim()).apply()

    var wpSite: String
        get() = prefs.getString(K_WP_SITE, "") ?: ""
        set(v) = prefs.edit().putString(K_WP_SITE, v.trim()).apply()

    var wpUser: String
        get() = prefs.getString(K_WP_USER, "") ?: ""
        set(v) = prefs.edit().putString(K_WP_USER, v.trim()).apply()

    var wpAppPassword: String
        get() = prefs.getString(K_WP_PASS, "") ?: ""
        set(v) = prefs.edit().putString(K_WP_PASS, v.trim()).apply()

    var devUnlocked: Boolean
        get() = prefs.getBoolean(K_DEV, false)
        set(v) = prefs.edit().putBoolean(K_DEV, v).apply()

    private companion object {
        const val K_DEV = "dev_unlocked"
        const val K_GH_TOKEN = "github_token"
        const val K_WP_SITE = "wp_site"
        const val K_WP_USER = "wp_user"
        const val K_WP_PASS = "wp_app_password"
    }
}
