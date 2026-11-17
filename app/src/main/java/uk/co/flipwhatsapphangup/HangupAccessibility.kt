package uk.co.flipwhatsapphangup

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HangupAccessibility : AccessibilityService() {
    companion object { var instance: HangupAccessibility? = null }
    override fun onServiceConnected() { instance = this; State.log(this, "Accessibility connected") }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
    fun tapEndCall(packageName: String): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != packageName) return false
        return findAndClick(root)
    }
    fun tapInstagramAnswer(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != CallService.INSTAGRAM.packageName) return false
        val buttons = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/videocall_ring_answer_button")
        val button = buttons.firstOrNull {
            it.isClickable && it.isEnabled && it.contentDescription?.toString() == "Answer"
        } ?: return false
        val ok = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        State.log(this, "Instagram accessibility Answer click=$ok")
        return ok
    }
    fun tapDiscordAnswer(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != CallService.DISCORD.packageName) return false
        val button = root.findAccessibilityNodeInfosByViewId("com.discord:id/acceptCallContainer")
            .firstOrNull { it.isClickable && it.isEnabled && it.contentDescription?.toString() == "Join Call" }
            ?: return false
        val ok = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        State.log(this, "Discord accessibility Join Call click=$ok")
        return ok
    }
    fun tapSnapchatAnswer(): Boolean {
        val screen = resources.displayMetrics
        if (screen.widthPixels < 500 || screen.heightPixels < screen.widthPixels * 1.5f) return false
        return dispatchSnapchatTap(screen.widthPixels * 0.72f, screen.heightPixels * 0.79f, "Join position")
    }
    private fun dispatchSnapchatTap(x: Float, y: Float, target: String): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 160))
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                State.log(this@HangupAccessibility, "Snapchat $target gesture completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                State.log(this@HangupAccessibility, "Snapchat $target gesture cancelled")
            }
        }, null)
        State.log(this, "Snapchat $target gesture=$dispatched at ${x.toInt()},${y.toInt()}")
        return dispatched
    }
    private fun findAndClick(node: AccessibilityNodeInfo): Boolean {
        val label = (node.contentDescription?.toString().orEmpty() + " " + node.text?.toString().orEmpty()).lowercase()
        if (("end call" in label || "hang up" in label) && node.isClickable) {
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            State.log(this, "Accessibility end-call click=$ok label='$label'")
            return ok
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClick(child)) return true
        }
        return false
    }
}
