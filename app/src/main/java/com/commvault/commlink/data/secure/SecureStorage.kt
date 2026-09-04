package com.commvault.commlink.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                "secure_commlink_prefs",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback if Keystore or cryptography system encounters issues
            context.getSharedPreferences("secure_commlink_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveCommvaultEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    fun getCommvaultEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun saveCommvaultPassword(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun getCommvaultPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun clearCredentials() {
        prefs.edit().remove(KEY_EMAIL).remove(KEY_PASSWORD).apply()
    }

    fun isUserLoggedIn(): Boolean {
        return !getCommvaultEmail().isNullOrBlank() && !getCommvaultPassword().isNullOrBlank()
    }

    fun savePasswordVaultJson(json: String) {
        prefs.edit().putString(KEY_PASSWORD_VAULT_JSON, json).apply()
    }

    fun getPasswordVaultJson(): String = prefs.getString(KEY_PASSWORD_VAULT_JSON, "[]") ?: "[]"

    fun saveAlarmsJson(json: String) {
        prefs.edit().putString(KEY_ALARMS_JSON, json).apply()
    }

    fun getAlarmsJson(): String = prefs.getString(KEY_ALARMS_JSON, "[]") ?: "[]"

    fun saveTodosJson(json: String) {
        prefs.edit().putString(KEY_TODOS_JSON, json).apply()
    }

    fun getTodosJson(): String = prefs.getString(KEY_TODOS_JSON, "[]") ?: "[]"

    fun saveHydrationRemindersJson(json: String) {
        prefs.edit().putString(KEY_HYDRATION_REMINDERS_JSON, json).apply()
    }

    fun getHydrationRemindersJson(): String = prefs.getString(KEY_HYDRATION_REMINDERS_JSON, "[]") ?: "[]"

    fun saveThemeColor(hex: String) {
        prefs.edit().putString(KEY_THEME_COLOR, hex).apply()
    }

    fun getThemeColor(): String = prefs.getString(KEY_THEME_COLOR, "") ?: ""

    companion object {
        private const val KEY_EMAIL = "commvault_email"
        private const val KEY_PASSWORD = "commvault_password"
        private const val KEY_PASSWORD_VAULT_JSON = "password_vault_json"
        private const val KEY_ALARMS_JSON = "alarms_json"
        private const val KEY_TODOS_JSON = "todos_json"
        private const val KEY_HYDRATION_REMINDERS_JSON = "hydration_reminders_json"
        private const val KEY_THEME_COLOR = "theme_color_hex"
    }
}
