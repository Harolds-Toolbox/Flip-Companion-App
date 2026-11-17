package uk.co.flipwhatsapphangup

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object State {
    const val TAG = "FlipHangup"
    private const val PREF = "state"
    fun enabled(c: Context) = c.getSharedPreferences(PREF, 0).getBoolean("enabled", true)
    fun setEnabled(c: Context, value: Boolean) { c.getSharedPreferences(PREF, 0).edit().putBoolean("enabled", value).apply(); log(c, "Enabled=$value") }
    fun option(c: Context, name: String): Boolean {
        val prefs = c.getSharedPreferences(PREF, 0)
        if (!prefs.getBoolean("call_options_migrated", false)) {
            val previous = prefs.getBoolean("enabled", true)
            prefs.edit().putBoolean("audio_hang_up", previous).putBoolean("video_hang_up", previous)
                .putBoolean("audio_answer", false).putBoolean("video_answer", false)
                .putBoolean("call_options_migrated", true).apply()
        }
        return prefs.getBoolean(name, false)
    }
    fun setOption(c: Context, name: String, value: Boolean) {
        option(c, name)
        c.getSharedPreferences(PREF, 0).edit().putBoolean(name, value).apply()
        log(c, "$name=$value")
    }
    fun callOption(c: Context, service: CallService, name: String): Boolean = when (service) {
        CallService.WHATSAPP -> option(c, name)
        CallService.DISCORD -> {
            val other = if (name.startsWith("audio_")) name.replaceFirst("audio_", "video_")
                        else name.replaceFirst("video_", "audio_")
            val prefs = c.getSharedPreferences(PREF, 0)
            prefs.getBoolean("discord_$name", false) || prefs.getBoolean("discord_$other", false)
        }
        else -> c.getSharedPreferences(PREF, 0).getBoolean("${service.id}_$name", false)
    }
    fun setCallOption(c: Context, service: CallService, name: String, value: Boolean) {
        if (service == CallService.WHATSAPP) setOption(c, name, value)
        else if (service == CallService.DISCORD) {
            val other = if (name.startsWith("audio_")) name.replaceFirst("audio_", "video_")
                        else name.replaceFirst("video_", "audio_")
            c.getSharedPreferences(PREF, 0).edit()
                .putBoolean("discord_$name", value).putBoolean("discord_$other", value).apply()
            log(c, "discord_${name.removePrefix("audio_").removePrefix("video_")}=$value (audio and video)")
        }
        else {
            c.getSharedPreferences(PREF, 0).edit().putBoolean("${service.id}_$name", value).apply()
            log(c, "${service.id}_$name=$value")
        }
    }
    fun put(c: Context, key: String, value: String) { c.getSharedPreferences(PREF, 0).edit().putString(key, value).apply() }
    fun get(c: Context, key: String) = c.getSharedPreferences(PREF, 0).getString(key, "Unknown") ?: "Unknown"
    fun log(c: Context, message: String) {
        Log.i(TAG, message)
        val p = c.getSharedPreferences(PREF, 0)
        val time = SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date())
        val lines = ("$time  $message\n" + p.getString("log", "")).take(5000)
        p.edit().putString("log", lines).apply()
    }
}
