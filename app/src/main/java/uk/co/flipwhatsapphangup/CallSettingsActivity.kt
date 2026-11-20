package uk.co.flipwhatsapphangup

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class CallSettingsActivity : Activity() {
    private lateinit var service: CallService
    private data class Palette(val background: Int, val surface: Int, val raised: Int, val text: Int,
                               val muted: Int, val border: Int, val accent: Int, val accentText: Int, val green: Int)
    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val p get() = if (dark) Palette(
        hex("#11131c"), hex("#1b2030"), hex("#262c40"), hex("#f7f5ed"), hex("#bac8bc"),
        hex("#829384"), hex("#c9b5ff"), hex("#102426"), hex("#50e980")
    ) else Palette(
        hex("#eeeeee"), Color.WHITE, hex("#edf0f6"), hex("#14211b"), hex("#506058"),
        hex("#78857b"), hex("#19704b"), Color.WHITE, hex("#178c46")
    )
    private lateinit var permissionResult: TextView
    private var checked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = CallService.fromId(intent.getStringExtra(CallService.EXTRA_SERVICE))
        window.statusBarColor = p.background
        window.navigationBarColor = p.background
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.background) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(24), dp(22), dp(28)) }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.pabi_chevron)
            imageTintList = ColorStateList.valueOf(p.text)
            rotation = 180f
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            setOnClickListener { goBack() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })
        header.addView(label("${service.title} Calls", 27f, p.text, Typeface.BOLD).apply {
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(18, 27, 1, TypedValue.COMPLEX_UNIT_SP)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)

        if (!service.available) {
            content.addView(label("Coming soon — ${service.title} call controls are not available yet.", 14f, p.muted), spaced(dp(12)))
        }

        if (service == CallService.DISCORD) {
            content.addView(section("CALLS"), spaced(dp(26)))
            content.addView(toggle("Answer on open", "audio_answer"), spaced(dp(9)))
            content.addView(toggle("Hang up on close", "audio_hang_up"), spaced(dp(9)))
        } else {
            content.addView(section("AUDIO CALL"), spaced(dp(26)))
            content.addView(toggle("Answer on open", "audio_answer"), spaced(dp(9)))
            content.addView(toggle("Hang up on close", "audio_hang_up"), spaced(dp(9)))
            content.addView(section("VIDEO CALL"), spaced(dp(28)))
            content.addView(toggle("Answer on open", "video_answer"), spaced(dp(9)))
            content.addView(toggle("Hang up on close", "video_hang_up"), spaced(dp(9)))
        }

        content.addView(button("Enable permissions", true) { openNotificationAccess() }.apply {
            isEnabled = service.available
            if (!service.available) alpha = .5f
        }, spaced(dp(30)))
        if (service == CallService.INSTAGRAM || service == CallService.SNAPCHAT || service == CallService.DISCORD) {
            content.addView(label("${service.title} answering also needs Accessibility access to press its in-app Answer button.", 14f, p.muted), spaced(dp(14)))
            content.addView(button("Enable ${service.title} answer access", false) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }, spaced(dp(12)))
        }
        content.addView(button("Check permissions", false) { showPermissionResult() }.apply {
            isEnabled = service.available
            if (!service.available) alpha = .5f
        }, spaced(dp(10)))
        permissionResult = label("", 14f, p.muted).apply { visibility = View.GONE }
        content.addView(permissionResult, spaced(dp(12)))

        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { goBack() }
    }
    @Deprecated("Used for Android versions before predictive back")
    override fun onBackPressed() { goBack() }
    private fun goBack() { finish(); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right) }
    override fun onResume() { super.onResume(); if (::permissionResult.isInitialized && checked) showPermissionResult() }

    private fun toggle(title: String, key: String): LinearLayout {
        val row = card()
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val answering = key.endsWith("_answer")
        val icon = when {
            !answering -> R.drawable.wa_end_call
            key.startsWith("video") -> R.drawable.wa_answer_video
            else -> R.drawable.wa_answer_phone
        }
        row.addView(ImageView(this).apply {
            setImageResource(icon)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (answering) hex("#25D366") else hex("#E53935"))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(13) })
        row.addView(label(title, 16f, p.text), LinearLayout.LayoutParams(0, -2, 1f))
        val control = Switch(this).apply {
            contentDescription = "${service.title} ${if (service == CallService.DISCORD) "call" else if (key.startsWith("audio")) "audio call" else "video call"} $title${if (service.available) "" else ", coming soon"}"
            isChecked = service.available && State.callOption(this@CallSettingsActivity, service, key)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(if (dark) p.green else p.accent, p.muted))
            trackTintList = ColorStateList(states, intArrayOf(if (dark) hex("#355c47") else hex("#b9d7c4"), p.raised))
            isEnabled = service.available
            if (service.available) setOnCheckedChangeListener { _, value ->
                State.setCallOption(this@CallSettingsActivity, service, key, value)
                CallListener.instance?.refreshFromSettings()
            }
        }
        row.addView(control)
        row.isClickable = service.available
        row.isFocusable = service.available
        if (service.available) row.setOnClickListener { control.toggle() }
        return row
    }
    private fun openNotificationAccess() {
        val component = ComponentName(this, CallListener::class.java)
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
    private fun showPermissionResult() {
        checked = true
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val enabled = manager.isNotificationListenerAccessGranted(ComponentName(this, CallListener::class.java))
        val answerAccess = HangupAccessibility.instance != null
        val needsAnswerAccess = service in listOf(CallService.INSTAGRAM, CallService.SNAPCHAT, CallService.DISCORD) &&
            (State.callOption(this, service, "audio_answer") || State.callOption(this, service, "video_answer"))
        permissionResult.text = when {
            !enabled -> "Permissions disabled. Allow notification access for calls to be answered or ended on phone open / close."
            needsAnswerAccess && !answerAccess -> "Notification access enabled. Enable PABI Flip Companion under Accessibility to answer ${service.title} calls on open."
            else -> "Permissions enabled"
        }
        permissionResult.setTextColor(if (enabled && (!needsAnswerAccess || answerAccess)) p.green else p.muted)
        permissionResult.visibility = View.VISIBLE
        permissionResult.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private fun button(title: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = title; textSize = 15f; isAllCaps = false
        setTextColor(if (primary) p.accentText else p.accent)
        background = rounded(if (primary) p.accent else p.raised, p.border, 18)
        minHeight = dp(52)
        setOnClickListener { onClick() }
    }
    private fun card() = LinearLayout(this).apply {
        setPadding(dp(17), dp(15), dp(17), dp(15))
        background = rounded(p.surface, p.border, 22)
    }
    private fun section(text: String) = label(text, 12f, p.accent, Typeface.BOLD).apply { letterSpacing = .12f }
    private fun label(value: String, size: Float, color: Int, weight: Int = Typeface.NORMAL) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setTypeface(null, weight)
    }
    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun spaced(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun hex(value: String) = Color.parseColor(value)
}
