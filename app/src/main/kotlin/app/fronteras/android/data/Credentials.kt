package app.fronteras.android.data

import android.content.Context

/**
 * Server address and access token, kept in app-private storage (excluded from backups and
 * from data export). The AI provider key is never stored on the device.
 */
class Credentials(context: Context, private val defaultUrl: String) {
    private val prefs = context.getSharedPreferences("fronteras_credentials", Context.MODE_PRIVATE)

    fun endpoint(): ServerEndpoint = ServerEndpoint(prefs.getString(KEY_URL, null) ?: defaultUrl, prefs.getString(KEY_TOKEN, null).orEmpty())

    fun save(url: String, token: String) {
        prefs.edit().putString(KEY_URL, url.trim()).putString(KEY_TOKEN, token.trim()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun deviceId(): String = prefs.getString(KEY_DEVICE, null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    fun lastPulledVersion(): Long = prefs.getLong(KEY_PULLED, 0)
    fun setLastPulledVersion(v: Long) = prefs.edit().putLong(KEY_PULLED, v).apply()

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_TOKEN = "access_token"
        const val KEY_DEVICE = "device_id"
        const val KEY_PULLED = "last_pulled_version"
    }
}
