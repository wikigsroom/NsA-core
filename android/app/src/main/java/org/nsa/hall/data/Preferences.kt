package org.nsa.hall.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.nsa.hall.BuildConfig
import org.json.JSONObject

private val Context.preferences by preferencesDataStore("hall-settings")
class Preferences(private val context: Context) {
    val flow = context.preferences.data.map { p -> Settings(p[booleanPreferencesKey("welcome")] ?: false, p[booleanPreferencesKey("wifi")] ?: true, p[booleanPreferencesKey("notifications")] ?: true, p[booleanPreferencesKey("diagnostics")] ?: false, p[stringPreferencesKey("endpoint")] ?: BuildConfig.DEFAULT_ENDPOINT, p[stringPreferencesKey("player")] ?: "玩家", p[stringPreferencesKey("language")] ?: "zh-CN", p[booleanPreferencesKey("reduceMotion")] ?: false) }
    suspend fun get() = flow.first()
    suspend fun boolean(key: String, value: Boolean) { context.preferences.edit { it[booleanPreferencesKey(key)] = value } }
    suspend fun string(key: String, value: String) { context.preferences.edit { it[stringPreferencesKey(key)] = value } }
}
data class Settings(val welcomed: Boolean, val wifiOnly: Boolean, val notifications: Boolean, val diagnostics: Boolean, val endpoint: String, val player: String, val language: String, val reduceMotion: Boolean)
