package uk.co.flipwhatsapphangup

import android.app.Notification
import android.content.ComponentName
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Locale
import kotlin.math.abs

class CallListener : NotificationListenerService(), SensorEventListener {
    companion object { var instance: CallListener? = null }
    private enum class Phase { INCOMING, ONGOING, OUTGOING }
    private enum class Kind { AUDIO, VIDEO, UNKNOWN }
    private data class Call(val sbn: StatusBarNotification, val service: CallService, val phase: Phase, val kind: Kind)
    private var sensorManager: SensorManager? = null
    private var hinge: Sensor? = null
    private var listening = false
    private var call: Call? = null
    private var lastAngle = Float.NaN
    private var openArmed = false
    private var closedArmed = false
    private var hangupSentForKey: String? = null
    private val recentKind = mutableMapOf<CallService, Pair<Kind, Long>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        instance = this
        State.log(this, "Notification listener connected")
        Diagnostics.record(this, "listener_connected")
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        hinge = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        State.log(this, "Hinge sensor: ${hinge?.name ?: "unavailable"}")
        Diagnostics.record(this, "hinge_sensor", result = if (hinge == null) "missing" else "available")
        refresh()
    }
    override fun onListenerDisconnected() {
        stopSensor()
        instance = null
        State.log(this, "Notification listener disconnected")
        Diagnostics.record(this, "listener_disconnected")
        requestRebind(ComponentName(this, CallListener::class.java))
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (CallService.fromPackageName(sbn.packageName) != null) {
            val service = CallService.fromPackageName(sbn.packageName)
            if (sbn.notification.category == Notification.CATEGORY_CALL ||
                sbn.notification.channelId?.contains("call", ignoreCase = true) == true ||
                sbn.notification.channelId?.contains("voip", ignoreCase = true) == true)
                Diagnostics.record(this, "notification_seen", service, result =
                    if (isCallNotification(sbn)) "classified" else "unclassified")
            if (sbn.packageName == CallService.MESSENGER.packageName && isCallNotification(sbn) ||
                sbn.packageName == CallService.INSTAGRAM.packageName &&
                (isCallNotification(sbn) || sbn.id == 20025) ||
                sbn.packageName == CallService.SNAPCHAT.packageName &&
                (isCallNotification(sbn) || sbn.notification.channelId?.contains("talk_group") == true) ||
                sbn.packageName == CallService.GOOGLE_MEET.packageName && isCallNotification(sbn) ||
                sbn.packageName == CallService.DISCORD.packageName &&
                (isCallNotification(sbn) || sbn.notification.channelId in listOf("calls", "mediaConnections"))) logCallNotification(sbn)
            refresh()
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (CallService.fromPackageName(sbn.packageName) != null) refresh()
    }
    fun refreshFromSettings() = refresh()

    private fun refresh() {
        val notifications = try { activeNotifications?.toList().orEmpty() }
        catch (e: Exception) { State.log(this, "Notification query failed: ${e.message}"); emptyList() }
        val calls = notifications.filter { isCallNotification(it) }
            .map { classify(it) }
        val selected = calls.firstOrNull { it.phase == Phase.ONGOING }
            ?: calls.firstOrNull { it.phase == Phase.INCOMING }
        val previous = call
        call = selected
        if (selected == null && previous != null &&
            previous.service in listOf(CallService.MESSENGER, CallService.INSTAGRAM, CallService.SNAPCHAT, CallService.GOOGLE_MEET, CallService.DISCORD)) {
            if (previous.phase == Phase.INCOMING && previous.kind != Kind.UNKNOWN) {
                recentKind[previous.service] = previous.kind to SystemClock.elapsedRealtime()
            } else if (previous.phase == Phase.ONGOING) {
                recentKind.remove(previous.service)
            }
        }
        if (selected == null || selected.phase == Phase.INCOMING) hangupSentForKey = null
        if (selected != null) {
            val phase = selected.phase.name.lowercase(Locale.ROOT)
            val kind = selected.kind.name.lowercase(Locale.ROOT)
            State.put(this, "call", "$phase $kind ${selected.service.title} call")
            if (previous?.sbn?.key != selected.sbn.key || previous?.phase != selected.phase || previous?.kind != selected.kind)
                State.log(this, "Call candidate: ${selected.service.id} $phase $kind")
            if (previous?.sbn?.key != selected.sbn.key || previous?.phase != selected.phase || previous?.kind != selected.kind)
                Diagnostics.record(this, "call_candidate", selected.service, phase, kind)
        } else {
            State.put(this, "call", "No active call")
            if (previous != null) {
                State.log(this, "Call candidate cleared")
                Diagnostics.record(this, "call_cleared", previous.service, previous.phase.name.lowercase(Locale.ROOT),
                    previous.kind.name.lowercase(Locale.ROOT))
            }
        }
        if (selected != null && selected.kind != Kind.UNKNOWN && configured(selected)) startSensor() else stopSensor()
        if (selected?.phase == Phase.ONGOING && previous?.phase != Phase.ONGOING &&
            lastAngle <= 10 && openArmed) {
            State.log(this, "Call became ongoing after the flip was closed")
            tryHangup(selected)
        }
    }
    private fun isCallNotification(sbn: StatusBarNotification): Boolean {
        val service = CallService.fromPackageName(sbn.packageName) ?: return false
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_CALL) {
            if (service != CallService.DISCORD || n.channelId == "calls") return true
        }
        return (service == CallService.INSTAGRAM && n.channelId == "ig_other" ||
            service == CallService.SNAPCHAT && n.channelId == "general_group_silent" ||
            service == CallService.GOOGLE_MEET && n.channelId?.contains("ONGOING_CALL") == true ||
            service == CallService.DISCORD && (n.channelId == "calls" ||
                n.channelId == "mediaConnections" &&
                n.extras.getCharSequence(Notification.EXTRA_TITLE)?.contains("call", ignoreCase = true) == true)) &&
            n.actions?.any { isHangupLabel(it.title?.toString().orEmpty()) } == true
    }
    private fun classify(sbn: StatusBarNotification): Call {
        val n = sbn.notification
        val service = CallService.fromPackageName(sbn.packageName) ?: CallService.WHATSAPP
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().lowercase(Locale.ROOT)
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().lowercase(Locale.ROOT)
        val actions = n.actions?.joinToString { it.title?.toString().orEmpty() }.orEmpty()
        val actionText = actions.lowercase(Locale.ROOT)
        val channel = n.channelId.orEmpty().lowercase(Locale.ROOT)
        var kind = when {
            n.extras.getBoolean("android.callIsVideo", false) -> Kind.VIDEO
            "video" in text || "video" in title -> Kind.VIDEO
            "voice" in text || "audio" in text || "voice" in title || "audio" in title -> Kind.AUDIO
            "video" in actionText || "video" in channel -> Kind.VIDEO
            service == CallService.DISCORD && n.category == Notification.CATEGORY_CALL -> Kind.AUDIO
            else -> Kind.UNKNOWN
        }
        val phase = when {
            "incoming" in text || "incoming" in channel || "decline" in actionText || "answer" in actionText || "accept" in actionText ||
                service == CallService.GOOGLE_MEET && "join" in actionText -> Phase.INCOMING
            "ongoing" in text || service == CallService.MESSENGER && "voip" in channel ||
                (service == CallService.INSTAGRAM || service == CallService.SNAPCHAT || service == CallService.GOOGLE_MEET || service == CallService.DISCORD) &&
                n.actions?.any { isHangupLabel(it.title?.toString().orEmpty()) } == true -> Phase.ONGOING
            else -> Phase.OUTGOING
        }
        if (service in listOf(CallService.MESSENGER, CallService.INSTAGRAM, CallService.SNAPCHAT, CallService.GOOGLE_MEET, CallService.DISCORD)) {
            if (phase == Phase.INCOMING && kind != Kind.UNKNOWN) {
                recentKind[service] = kind to SystemClock.elapsedRealtime()
            } else if (phase == Phase.ONGOING && kind == Kind.UNKNOWN &&
                recentKind[service]?.let { SystemClock.elapsedRealtime() - it.second < 5_000L } == true) {
                kind = recentKind[service]?.first ?: Kind.UNKNOWN
            }
        }
        State.log(this, "${service.title} ${phase.name.lowercase(Locale.ROOT)} ${kind.name.lowercase(Locale.ROOT)} call notification detected")
        return Call(sbn, service, phase, kind)
    }
    private fun logCallNotification(sbn: StatusBarNotification) {
        val n = sbn.notification
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().lowercase(Locale.ROOT)
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().lowercase(Locale.ROOT)
        val actions = n.actions?.joinToString { it.title?.toString().orEmpty() }.orEmpty()
        State.log(this, "${sbn.packageName} call notification id=${sbn.id} channel=${n.channelId} category=${n.category} actions=[$actions] " +
            "textVideo=${"video" in text} textAudio=${"audio" in text || "voice" in text} " +
            "titleVideo=${"video" in title} titleAudio=${"audio" in title || "voice" in title} " +
            "callIsVideo=${n.extras.getBoolean("android.callIsVideo", false)}")
    }
    private fun configured(c: Call): Boolean {
        val prefix = if (c.kind == Kind.AUDIO) "audio" else "video"
        val suffix = if (c.phase == Phase.INCOMING) "answer" else "hang_up"
        return c.phase != Phase.OUTGOING && State.callOption(this, c.service, "${prefix}_$suffix")
    }
    private fun startSensor() {
        if (listening || hinge == null) return
        listening = sensorManager?.registerListener(this, hinge, SensorManager.SENSOR_DELAY_NORMAL) == true
        State.log(this, "Hinge monitoring=$listening")
        Diagnostics.record(this, "hinge_monitoring", result = if (listening) "started" else "failed")
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
        val previousAngle = lastAngle
        State.put(this, "fold", "${"%.1f".format(angle)}°")
        if (lastAngle.isNaN() || abs(angle - lastAngle) >= 10 || angle <= 12 && lastAngle > 12)
            State.log(this, "Hinge ${"%.1f".format(angle)}°, openArmed=$openArmed closedArmed=$closedArmed")
        lastAngle = angle
        if (angle <= 10 && (previousAngle.isNaN() || previousAngle > 10))
            Diagnostics.record(this, "hinge_closed", call?.service, angle = angle)
        if (angle >= 160 && (previousAngle.isNaN() || previousAngle < 160))
            Diagnostics.record(this, "hinge_opened", call?.service, angle = angle)
        if (angle >= 25) openArmed = true
        if (angle <= 10) closedArmed = true
        val selected = call
        if (selected != null && configured(selected)) {
            if (selected.phase == Phase.INCOMING && angle >= 160 && closedArmed) {
                closedArmed = false
                State.log(this, "Fully opened: ${selected.phase} ${selected.kind}")
                Diagnostics.record(this, "answer_trigger", selected.service, "incoming",
                    selected.kind.name.lowercase(Locale.ROOT), angle)
                answer(selected)
            } else if (selected.phase == Phase.ONGOING && angle <= 10 && openArmed) {
                openArmed = false
                State.log(this, "Fully closed: ${selected.phase} ${selected.kind}")
                Diagnostics.record(this, "hangup_trigger", selected.service, "ongoing",
                    selected.kind.name.lowercase(Locale.ROOT), angle)
                tryHangup(selected)
            }
        }
    }
    private fun tryHangup(c: Call) {
        if (hangupSentForKey == c.sbn.key) return
        if (hangup(c)) hangupSentForKey = c.sbn.key
    }
    private fun answer(c: Call) {
        val action = answerAction(c)
        if (action == null) {
            State.log(this, "No ${c.service.title} answer notification action")
            Diagnostics.record(this, "answer_action", c.service, "incoming", c.kind.name.lowercase(Locale.ROOT), result = "missing")
            return
        }
        try {
            action.actionIntent.send()
            State.log(this, "Sent notification action: ${action.title}")
            Diagnostics.record(this, "answer_action", c.service, "incoming", c.kind.name.lowercase(Locale.ROOT), result = "sent")
        }
        catch (e: Exception) {
            State.log(this, "Answer action failed: ${e.javaClass.simpleName}: ${e.message}")
            Diagnostics.record(this, "answer_action", c.service, "incoming", c.kind.name.lowercase(Locale.ROOT),
                result = if (e is android.app.PendingIntent.CanceledException) "canceled" else "failed")
            if (c.service in listOf(CallService.MESSENGER, CallService.INSTAGRAM, CallService.SNAPCHAT, CallService.GOOGLE_MEET, CallService.DISCORD))
                retryAnswer(c.sbn.key, c.service)
        }
        if (c.service == CallService.INSTAGRAM) retryInstagramScreenAnswer(c.sbn.key)
        if (c.service == CallService.SNAPCHAT) retrySnapchatScreenAnswer(c.sbn.key)
        if (c.service == CallService.DISCORD) retryDiscordScreenAnswer()
    }
    private fun retryDiscordScreenAnswer(attempt: Int = 0) {
        mainHandler.postDelayed({
            if (lastAngle < 160) return@postDelayed
            if (HangupAccessibility.instance?.tapDiscordAnswer() == true) return@postDelayed
            if (attempt < 24) retryDiscordScreenAnswer(attempt + 1)
            else State.log(this, "Discord Join Call button unavailable; enable Accessibility for Discord calls")
        }, if (attempt == 0) 200L else 250L)
    }
    private fun retrySnapchatScreenAnswer(key: String, attempt: Int = 0) {
        mainHandler.postDelayed({
            val current = call
            if (current?.sbn?.key != key || current.phase != Phase.INCOMING || lastAngle < 160) return@postDelayed
            if (HangupAccessibility.instance?.tapSnapchatAnswer() == true) {
                if (attempt < 12) mainHandler.postDelayed({ retrySnapchatScreenAnswer(key, attempt + 4) }, 900L)
                return@postDelayed
            }
            if (attempt < 32) retrySnapchatScreenAnswer(key, attempt + 1)
            else State.log(this, "Snapchat Answer button unavailable; enable Accessibility for Snapchat calls")
        }, if (attempt == 0) 200L else 250L)
    }
    private fun retryInstagramScreenAnswer(key: String, attempt: Int = 0) {
        mainHandler.postDelayed({
            val current = call
            if (current?.sbn?.key != key || current.phase != Phase.INCOMING || lastAngle < 160) return@postDelayed
            if (HangupAccessibility.instance?.tapInstagramAnswer() == true) return@postDelayed
            if (attempt < 12) retryInstagramScreenAnswer(key, attempt + 1)
            else State.log(this, "Instagram Answer button unavailable; enable Accessibility for Instagram calls")
        }, if (attempt == 0) 200L else 250L)
    }
    private fun answerAction(c: Call): Notification.Action? = c.sbn.notification.actions?.firstOrNull {
            val label = it.title?.toString()?.lowercase(Locale.ROOT)?.trim().orEmpty()
            label.contains("answer") || label.contains("accept") || c.kind == Kind.VIDEO && label == "video" ||
                c.service in listOf(CallService.GOOGLE_MEET, CallService.DISCORD) && label.contains("join")
        }
    private fun retryAnswer(key: String, service: CallService) {
        mainHandler.postDelayed({
            val current = call
            if (current?.sbn?.key != key || current.phase != Phase.INCOMING || lastAngle < 160) return@postDelayed
            val latest = try { activeNotifications?.firstOrNull { it.key == key } } catch (_: Exception) { null }
            val action = latest?.let { answerAction(current.copy(sbn = it)) }
            if (action == null) {
                State.log(this, "${service.title} retry: no current Answer action")
                Diagnostics.record(this, "answer_retry", service, "incoming", current.kind.name.lowercase(Locale.ROOT), result = "missing")
                return@postDelayed
            }
            try {
                action.actionIntent.send()
                State.log(this, "${service.title} retry sent Answer action")
                Diagnostics.record(this, "answer_retry", service, "incoming", current.kind.name.lowercase(Locale.ROOT), result = "sent")
            }
            catch (e: Exception) {
                State.log(this, "${service.title} retry failed: ${e.javaClass.simpleName}: ${e.message}")
                Diagnostics.record(this, "answer_retry", service, "incoming", current.kind.name.lowercase(Locale.ROOT),
                    result = if (e is android.app.PendingIntent.CanceledException) "canceled" else "failed")
            }
        }, 500L)
    }
    private fun isHangupLabel(label: String): Boolean =
        listOf("end", "hang up", "hangup", "disconnect", "leave").any { it in label.lowercase(Locale.ROOT) }
    private fun hangup(c: Call): Boolean {
        val action = c.sbn.notification.actions?.firstOrNull {
            isHangupLabel(it.title?.toString().orEmpty())
        }
        if (action != null) {
            try {
                action.actionIntent.send()
                State.log(this, "Sent notification action: ${action.title}")
                Diagnostics.record(this, "hangup_action", c.service, "ongoing", c.kind.name.lowercase(Locale.ROOT), result = "sent")
                return true
            }
            catch (e: Exception) {
                State.log(this, "Hang-up action failed: ${e.message}")
                Diagnostics.record(this, "hangup_action", c.service, "ongoing", c.kind.name.lowercase(Locale.ROOT),
                    result = if (e is android.app.PendingIntent.CanceledException) "canceled" else "failed")
            }
        }
        State.log(this, "No usable notification end action; trying accessibility")
        if (HangupAccessibility.instance?.tapEndCall(c.service.packageName.orEmpty()) == true) {
            Diagnostics.record(this, "hangup_accessibility", c.service, "ongoing", c.kind.name.lowercase(Locale.ROOT), result = "clicked")
            return true
        }
        State.log(this, "Accessibility could not find end-call button")
        Diagnostics.record(this, "hangup_accessibility", c.service, "ongoing", c.kind.name.lowercase(Locale.ROOT), result = "missing")
        return false
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onDestroy() { stopSensor(); instance = null; super.onDestroy() }
}
