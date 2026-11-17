package uk.co.flipwhatsapphangup

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HangupAccessibility : AccessibilityService() {
    companion object { var instance: HangupAccessibility? = null }
    override fun onServiceConnected() { instance = this; State.log(this, "Accessibility connected") }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
    fun tapEndCall(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != "com.whatsapp") return false
        return findAndClick(root)
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
