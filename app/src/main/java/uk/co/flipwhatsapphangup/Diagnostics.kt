package uk.co.flipwhatsapphangup

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/** Structured, opt-in telemetry. The public build has no Internet permission or endpoint. */
object Diagnostics {
    private const val PREF = "diagnostics"
    private const val MAX_QUEUED = 120
    private val lock = Any()
    private val sender = Executors.newSingleThreadExecutor()

    fun available() = BuildConfig.DIAGNOSTICS_BUILD && BuildConfig.DIAGNOSTICS_ENDPOINT.startsWith("https://")
    fun enabled(context: Context) = available() && context.getSharedPreferences(PREF, 0).getBoolean("enabled", false)
    fun installationId(context: Context): String = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREF, 0)
        prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).commit()
        }
    }
    fun status(context: Context) = context.getSharedPreferences(PREF, 0)
        .getString("status", "Nothing sent yet") ?: "Nothing sent yet"

    fun setEnabled(context: Context, value: Boolean) {
        if (!available()) return
        val prefs = context.getSharedPreferences(PREF, 0)
        prefs.edit().putBoolean("enabled", value).apply()
        if (value) record(context, "diagnostics_enabled")
        else synchronized(lock) {
            prefs.edit().remove("queue").putString("status", "Sharing paused").apply()
        }
    }

    fun record(
        context: Context,
        event: String,
        service: CallService? = null,
        phase: String? = null,
        kind: String? = null,
        angle: Float? = null,
        result: String? = null
    ) {
        if (!enabled(context)) return
        val app = context.applicationContext
        val keyguard = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val item = JSONObject().apply {
            put("eventId", UUID.randomUUID().toString())
            put("clientTime", System.currentTimeMillis())
            put("event", event)
            put("service", service?.id ?: JSONObject.NULL)
            put("phase", phase ?: JSONObject.NULL)
            put("kind", kind ?: JSONObject.NULL)
            put("angle", angle?.toInt()?.coerceIn(0, 180) ?: JSONObject.NULL)
            put("result", result ?: JSONObject.NULL)
            put("locked", keyguard.isKeyguardLocked)
            put("interactive", power.isInteractive)
        }
        synchronized(lock) {
            val prefs = app.getSharedPreferences(PREF, 0)
            val previous = readQueue(prefs.getString("queue", null))
            val next = JSONArray()
            for (i in maxOf(0, previous.length() - MAX_QUEUED + 1) until previous.length())
                next.put(previous.getJSONObject(i))
            next.put(item)
            prefs.edit().putString("queue", next.toString()).putString("status", "Upload pending").apply()
        }
        flush(app)
    }

    fun flush(context: Context) {
        if (enabled(context)) sender.execute { flushNow(context.applicationContext) }
    }

    fun deleteUploaded(context: Context, callback: (Boolean) -> Unit) {
        val app = context.applicationContext
        if (!available()) { callback(false); return }
        setEnabled(app, false)
        sender.execute {
            val success = try {
                val endpoint = BuildConfig.DIAGNOSTICS_ENDPOINT.removeSuffix("/events") + "/installation"
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "DELETE"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.doOutput = true
                    val body = JSONObject().put("installationId", installationId(app)).toString()
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    connection.responseCode == 204
                } finally { connection.disconnect() }
            } catch (_: Exception) { false }
            app.getSharedPreferences(PREF, 0).edit().putString("status",
                if (success) "Uploaded diagnostics deleted" else "Deletion could not reach the server; try again online").apply()
            Handler(Looper.getMainLooper()).post { callback(success) }
        }
    }

    private fun flushNow(context: Context) {
        if (!enabled(context)) return
        val batch = synchronized(lock) {
            val queued = readQueue(context.getSharedPreferences(PREF, 0).getString("queue", null))
            JSONArray().apply { for (i in 0 until minOf(20, queued.length())) put(queued.getJSONObject(i)) }
        }
        if (batch.length() == 0) return
        val payload = JSONObject().apply {
            put("installationId", installationId(context))
            put("appVersion", BuildConfig.VERSION_NAME)
            put("androidApi", Build.VERSION.SDK_INT)
            put("deviceModel", Build.MODEL.take(80))
            put("events", batch)
        }
        val ok = try {
            if (!enabled(context)) return
            val connection = URL(BuildConfig.DIAGNOSTICS_ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                connection.responseCode == 202
            } finally { connection.disconnect() }
        } catch (_: Exception) { false }
        val prefs = context.getSharedPreferences(PREF, 0)
        if (!ok) {
            prefs.edit().putString("status", "Upload pending; will retry on the next call or app open").apply()
            return
        }
        val sent = (0 until batch.length()).map { batch.getJSONObject(it).getString("eventId") }.toSet()
        synchronized(lock) {
            val current = readQueue(prefs.getString("queue", null))
            val remaining = JSONArray()
            for (i in 0 until current.length()) {
                val item = current.getJSONObject(i)
                if (item.getString("eventId") !in sent) remaining.put(item)
            }
            prefs.edit().putString("queue", remaining.toString())
                .putString("status", "Last upload succeeded").apply()
        }
        if (enabled(context)) flush(context)
    }

    private fun readQueue(value: String?): JSONArray = try { JSONArray(value ?: "[]") }
        catch (_: Exception) { JSONArray() }
}
