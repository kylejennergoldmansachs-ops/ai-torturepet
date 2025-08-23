package com.example.aitorture

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Storage {
    private const val PREF_FILE = "ai_torturepet_prefs"
    private const val KEY_API = "mistral_api_key"
    private const val KEY_PIXTRAL = "pixtral_agent_id"
    private const val KEY_SUMMARIZER = "summarizer_agent_id"

    private fun getPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun storeApiKey(context: Context, apiKey: String) {
        val p = getPrefs(context)
        p.edit().putString(KEY_API, apiKey).apply()
    }

    fun getApiKey(context: Context): String? {
        val p = getPrefs(context)
        return p.getString(KEY_API, null)
    }

    fun storeAgentIds(context: Context, pixtral: String, summarizer: String) {
        val p = getPrefs(context)
        p.edit().putString(KEY_PIXTRAL, pixtral).putString(KEY_SUMMARIZER, summarizer).apply()
    }

    fun getPixtralAgent(context: Context): String? {
        val p = getPrefs(context)
        return p.getString(KEY_PIXTRAL, null)
    }

    fun getSummarizerAgent(context: Context): String? {
        val p = getPrefs(context)
        return p.getString(KEY_SUMMARIZER, null)
    }
}
