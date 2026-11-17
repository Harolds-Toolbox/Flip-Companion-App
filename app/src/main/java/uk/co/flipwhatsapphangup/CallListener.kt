package uk.co.flipwhatsapphangup

import android.app.Notification
import android.content.ComponentName
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Locale
import kotlin.math.abs

class CallListener : NotificationListenerService(), SensorEventListener {
    companion object { var instance: CallListener? = null }
    private enum class Phase { INCOMING, ONGOING, OUTGOING }
    private enum class Kind { AUDIO, VIDEO, UNKNOWN }
    private data class Call(val sbn: StatusBarNotification, val phase: Phase, val kind: Kind)
    private var sensorManager: SensorManager? = null
    private var hinge: Sensor? = null
    private var listening = false
    private var call: Call? = null
    private var lastAngle = Float.NaN
    private var openArmed = false
    private var closedArmed = false
    private var hangupSentForKey: String? = null

    override fun onListenerConnected() {
        instance = this
        State.log(this, "Notification listener connected")
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        hinge = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        State.log(this, "Hinge sensor: ${hinge?.name ?: "unavailable"}")
        refresh()
    }
    override fun onListenerDisconnected() {
        stopSensor()
        instance = null
        State.log(this, "Notification listener disconnected")
        requestRebind(ComponentName(this, CallListener::class.java))
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) { if (sbn.packageName == "com.whatsapp") refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { if (sbn.packageName == "com.whatsapp") refresh() }
    fun refreshFromSettings() = refresh()

    private fun refresh() {
        val notifications = try { activeNotifications?.toList().orEmpty() }
        catch (e: Exception) { State.log(this, "Notification query failed: ${e.message}"); emptyList() }
        val calls = notifications.filter { it.packageName == "com.whatsapp" && it.notification.category == Notification.CATEGORY_CALL }
            .map { classify(it) }
        val selected = calls.firstOrNull { it.phase == Phase.ONGOING }
            ?: calls.firstOrNull { it.phase == Phase.INCOMING }
        val previous = call
        call = selected
        if (selected == null || selected.phase == Phase.INCOMING) hangupSentForKey = null
        if (selected != null) {
            val phase = selected.phase.name.lowercase(Locale.ROOT)
            val kind = selected.kind.name.lowercase(Locale.ROOT)
            State.put(this, "call", "$phase $kind WhatsApp call")
            if (previous?.phase != selected.phase || previous?.kind != selected.kind) State.log(this, "Call candidate: $phase $kind")
        } else {
            State.put(this, "call", "No active WhatsApp call")
            if (previous != null) State.log(this, "Call candidate cleared")
        }
        if (selected != null && selected.kind != Kind.UNKNOWN && configured(selected)) startSensor() else stopSensor()
        if (selected?.phase == Phase.ONGOING && previous?.phase != Phase.ONGOING &&
            lastAngle <= 10 && openArmed) {
            State.log(this, "Call became ongoing after the flip was closed")
            tryHangup(selected)
        }
    }
    private fun classify(sbn: StatusBarNotification): Call {
        val n = sbn.notification
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().lowercase(Locale.ROOT)
        val actions = n.actions?.joinToString { it.title?.toString().orEmpty() }.orEmpty()
        val actionText = actions.lowercase(Locale.ROOT)
        val kind = when {
            "video" in text -> Kind.VIDEO
            "voice" in text || "audio" in text -> Kind.AUDIO
            "video" in actionText -> Kind.VIDEO
            else -> Kind.UNKNOWN
        }
        val phase = when {
            "incoming" in text || "decline" in actionText || "answer" in actionText || "accept" in actionText -> Phase.INCOMING
            "ongoing" in text -> Phase.ONGOING
            else -> Phase.OUTGOING
        }
        State.log(this, "WhatsApp ${phase.name.lowercase(Locale.ROOT)} ${kind.name.lowercase(Locale.ROOT)} notification: text='$text' actions='$actions'")
        return Call(sbn, phase, kind)
    }
    private fun configured(c: Call): Boolean {
        val prefix = if (c.kind == Kind.AUDIO) "audio" else "video"
        val suffix = if (c.phase == Phase.INCOMING) "answer" else "hang_up"
        return c.phase != Phase.OUTGOING && State.option(this, "${prefix}_$suffix")
    }
    private fun startSensor() {
        if (listening || hinge == null) return
        listening = sensorManager?.registerListener(this, hinge, SensorManager.SENSOR_DELAY_NORMAL) == true
        State.log(this, "Hinge monitoring=$listening")
    }
    private fun stopSensor() {
        if (listening) sensorManager?.unregisterListener(this)
        listening = false
        lastAngle = Float.NaN
        openArmed = false
        closedArmed = false
    }
    override fun onSensorChanged(event: SensorEvent) {
        val angle = event.values[0]
        State.put(this, "fold", "${"%.1f".format(angle)}°")
        if (lastAngle.isNaN() || abs(angle - lastAngle) >= 10 || angle <= 12 && lastAngle > 12)
            State.log(this, "Hinge ${"%.1f".format(angle)}°, openArmed=$openArmed closedArmed=$closedArmed")
        lastAngle = angle
        if (angle >= 25) openArmed = true
        if (angle <= 10) closedArmed = true
        val selected = call
        if (selected != null && configured(selected)) {
            if (selected.phase == Phase.INCOMING && angle >= 160 && closedArmed) {
                closedArmed = false
                State.log(this, "Fully opened: ${selected.phase} ${selected.kind}")
                answer(selected)
            } else if (selected.phase == Phase.ONGOING && angle <= 10 && openArmed) {
                openArmed = false
                State.log(this, "Fully closed: ${selected.phase} ${selected.kind}")
                tryHangup(selected)
            }
        }
    }
    private fun tryHangup(c: Call) {
        if (hangupSentForKey == c.sbn.key) return
        if (hangup(c)) hangupSentForKey = c.sbn.key
    }
    private fun answer(c: Call) {
        val action = c.sbn.notification.actions?.firstOrNull {
            val label = it.title?.toString()?.lowercase(Locale.ROOT)?.trim().orEmpty()
            label.contains("answer") || label.contains("accept") || c.kind == Kind.VIDEO && label == "video"
        }
        if (action == null) { State.log(this, "No WhatsApp answer notification action"); return }
        try { action.actionIntent.send(); State.log(this, "Sent notification action: ${action.title}") }
        catch (e: Exception) { State.log(this, "Answer action failed: ${e.message}") }
    }
    private fun hangup(c: Call): Boolean {
        val action = c.sbn.notification.actions?.firstOrNull {
            val label = it.title?.toString()?.lowercase(Locale.ROOT).orEmpty()
            listOf("end", "hang up", "hangup", "disconnect").any { word -> word in label }
        }
        if (action != null) {
            try { action.actionIntent.send(); State.log(this, "Sent notification action: ${action.title}"); return true }
            catch (e: Exception) { State.log(this, "Hang-up action failed: ${e.message}") }
        }
        State.log(this, "No usable notification end action; trying accessibility")
        if (HangupAccessibility.instance?.tapEndCall() == true) return true
        State.log(this, "Accessibility could not find end-call button")
        return false
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onDestroy() { stopSensor(); instance = null; super.onDestroy() }
}
