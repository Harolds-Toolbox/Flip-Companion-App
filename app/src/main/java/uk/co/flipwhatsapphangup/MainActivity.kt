package uk.co.flipwhatsapphangup

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Button
import android.widget.TextView
import android.util.TypedValue

class MainActivity : Activity() {
    private data class SummaryViews(val indicators: LinearLayout, val divider: View, val card: LinearLayout)
    private val summaries = mutableMapOf<CallService, SummaryViews>()
    private data class Palette(val background: Int, val surface: Int, val text: Int,
                               val muted: Int, val border: Int, val accent: Int, val green: Int, val purple: Int)
    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val p get() = if (dark) Palette(
        hex("#11131c"), hex("#1b2030"), hex("#f7f5ed"), hex("#bac8bc"),
        hex("#829384"), hex("#c9b5ff"), hex("#50e980"), hex("#c9b5ff")
    ) else Palette(
        hex("#eeeeee"), Color.WHITE, hex("#14211b"), hex("#506058"),
        hex("#78857b"), hex("#19704b"), hex("#178c46"), hex("#7250a7")
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = p.background
        window.navigationBarColor = p.background
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.background) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(24), dp(22), dp(22)) }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)

        // PABI Finance onboarding brand row: icon, small brand label, title and coloured slogan.
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.pabi_icon); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "PABI Flip Companion"
        }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(14) })
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandText.addView(label("PABI ©", 11f, p.accent, Typeface.BOLD).apply { letterSpacing = .13f })
        brandText.addView(label("PABI Flip Companion", 25f, p.text, Typeface.BOLD).apply {
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(19, 25, 1, TypedValue.COMPLEX_UNIT_SP)
        })
        val slogan = SpannableString("Your flip, your way")
        slogan.setSpan(ForegroundColorSpan(p.green), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        slogan.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        slogan.setSpan(ForegroundColorSpan(p.purple), 11, 15, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        slogan.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 11, 15, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        brandText.addView(label("", 15f, p.muted).apply { text = slogan })
        brand.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(brand)

        content.addView(section("Answer & End calls by Opening & Closing your Flip"), spaced(dp(26)))
        CallService.entries.forEachIndexed { index, service ->
            content.addView(serviceCard(service), spaced(if (index == 0) dp(12) else dp(10)))
        }

        content.addView(coffeeCard(), spaced(dp(28)))
        if (BuildConfig.DIAGNOSTICS_BUILD) content.addView(diagnosticsCard(), spaced(dp(28)))

    }
    override fun onResume() {
        super.onResume()
        summaries.forEach { (service, views) -> fillSummary(service, views) }
        Diagnostics.flush(this)
    }

    private fun coffeeCard(): LinearLayout = card().apply {
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        row.addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.lucy_takeaway_cup)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(58), dp(88)).apply { marginEnd = dp(12) })
        val message = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        val heading = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label("Buy me a Coffee 🙏", 18f, p.text, Typeface.BOLD),
            LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.open_external)
            imageTintList = ColorStateList.valueOf(p.accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8) })
        message.addView(heading)
        message.addView(label(
            "If you love this app, and you want to support it, you can buy me a coffee to keep this kind of work going.",
            14f, p.muted), spaced(dp(12)))
        row.addView(message, LinearLayout.LayoutParams(0, -2, 1f))
        addView(row)
        addView(label("Even $1 goes a long way. No pressure, and thanks a lot for using the app!", 14f, p.muted), spaced(dp(18)))
    }

    private fun diagnosticsCard(): LinearLayout = card().apply {
        addView(section("DEVELOPMENT DIAGNOSTICS"))
        addView(label("Share call diagnostics", 18f, p.text, Typeface.BOLD), spaced(dp(12)))
        addView(label(
            "When enabled, this development build sends the calling app, audio/video state, fold angle, lock state, and actions attempted to PABI. It never sends caller names, notification text, messages, or audio. Records are kept for 30 days.",
            14f, p.muted), spaced(dp(8)))
        val toggleRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toggleRow.addView(label("Send diagnostics", 16f, p.text), LinearLayout.LayoutParams(0, -2, 1f))
        val status = label(Diagnostics.status(this@MainActivity), 13f, p.muted)
        val control = Switch(this@MainActivity).apply {
            isChecked = Diagnostics.enabled(this@MainActivity)
            isEnabled = Diagnostics.available()
            contentDescription = "Send development diagnostics"
            setOnCheckedChangeListener { _, enabled ->
                Diagnostics.setEnabled(this@MainActivity, enabled)
                status.text = Diagnostics.status(this@MainActivity)
            }
        }
        toggleRow.addView(control)
        addView(toggleRow, spaced(dp(14)))
        addView(label("Diagnostics ID: ${Diagnostics.installationId(this@MainActivity)}", 12f, p.muted).apply {
            setTextIsSelectable(true)
        }, spaced(dp(10)))
        addView(status, spaced(dp(8)))
        addView(Button(this@MainActivity).apply {
            text = "Delete uploaded diagnostics"
            isAllCaps = false
            setTextColor(p.accent)
            background = rounded(p.surface, p.border, 16)
            setOnClickListener {
                control.isChecked = false
                isEnabled = false
                status.text = "Deleting uploaded diagnostics…"
                Diagnostics.deleteUploaded(this@MainActivity) { success ->
                    isEnabled = true
                    status.text = if (success) "Uploaded diagnostics deleted" else Diagnostics.status(this@MainActivity)
                }
            }
        }, spaced(dp(12)))
    }
    private fun serviceCard(service: CallService): LinearLayout {
        val callsCard = card().apply {
            setPadding(dp(17), dp(14), dp(17), dp(14))
            isClickable = true
            isFocusable = true
            contentDescription = "${service.title} call settings${if (service.available) "" else ", coming soon"}"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CallSettingsActivity::class.java)
                    .putExtra(CallService.EXTRA_SERVICE, service.id))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
        val callsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
        }
        val identity = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        identity.addView(ImageView(this).apply {
            setImageResource(service.logo)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(hex(service.logoColor))
            }
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(8) })
        val title = label(service.title, 15f, p.text, Typeface.BOLD).apply {
            maxLines = 2
            setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        val titleLimit = when {
            resources.configuration.screenWidthDp < 360 -> 72
            resources.configuration.screenWidthDp < 390 -> 84
            else -> 92
        }
        val titleWidth = (title.paint.measureText(service.title).toInt() + dp(2))
            .coerceAtMost(dp(titleLimit))
        identity.addView(title, LinearLayout.LayoutParams(titleWidth, -2))
        callsRow.addView(identity)
        val divider = View(this).apply { setBackgroundColor(p.border) }
        val columnWidth = summaryColumnWidth()
        val indicators = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        // Keep the indicator area the same size for one or two call types while
        // letting the divider follow this app's own title width.
        fun gap() = callsRow.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        gap()
        callsRow.addView(divider, LinearLayout.LayoutParams(dp(1), dp(48)))
        gap()
        val indicatorSpacing = when {
            resources.configuration.screenWidthDp < 360 -> 4
            resources.configuration.screenWidthDp < 390 -> 12
            else -> 44
        }
        callsRow.addView(indicators, LinearLayout.LayoutParams(dp(columnWidth * 2 + indicatorSpacing), -2))
        gap()
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.pabi_chevron)
            imageTintList = ColorStateList.valueOf(p.muted)
            alpha = .45f
        }
        callsRow.addView(chevron, LinearLayout.LayoutParams(dp(12), dp(20)))
        callsCard.addView(callsRow)
        val views = SummaryViews(indicators, divider, callsCard)
        summaries[service] = views
        fillSummary(service, views)
        return callsCard
    }
    private fun fillSummary(service: CallService, views: SummaryViews) {
        val (indicators, divider, card) = views
        indicators.removeAllViews()
        val kinds = if (service == CallService.DISCORD) listOf("audio" to "CALLS")
                    else listOf("audio" to "AUDIO CALL", "video" to "VIDEO CALL")
        val enabled = kinds.mapNotNull { (kind, title) ->
            val answer = State.callOption(this, service, "${kind}_answer")
            val hangUp = State.callOption(this, service, "${kind}_hang_up")
            if (answer || hangUp) Triple(title, answer, hangUp) else null
        }
        divider.visibility = if (enabled.isEmpty()) View.GONE else View.VISIBLE
        indicators.visibility = if (enabled.isEmpty()) View.GONE else View.VISIBLE
        if (enabled.isNotEmpty()) indicators.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        val columnWidth = summaryColumnWidth()
        enabled.forEach { (title, answer, hangUp) ->
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            column.addView(section(title).apply {
                maxLines = 1
                gravity = Gravity.CENTER_HORIZONTAL
                setAutoSizeTextTypeUniformWithConfiguration(8, 9, 1, TypedValue.COMPLEX_UNIT_SP)
                letterSpacing = .08f
            })
            val icons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL }
            if (answer) icons.addView(summaryIcon(if (title == "VIDEO CALL") R.drawable.wa_answer_video else R.drawable.wa_answer_phone, true))
            if (hangUp) icons.addView(summaryIcon(R.drawable.wa_end_call, false),
                LinearLayout.LayoutParams(dp(26), dp(26)).apply { if (answer) marginStart = dp(3) })
            column.addView(icons, spaced(dp(5)))
            indicators.addView(column, LinearLayout.LayoutParams(dp(columnWidth), -2))
            indicators.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        }
        card.contentDescription = buildString {
            append("${service.title} call settings")
            enabled.forEach { (title, answer, hangUp) ->
                append(". $title:")
                if (answer) append(" Answer on open")
                if (hangUp) append(if (answer) ", Hang up on close" else " Hang up on close")
            }
        }
    }
    private fun summaryIcon(drawable: Int, answer: Boolean) = ImageView(this).apply {
        setImageResource(drawable)
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(hex(if (answer) "#25D366" else "#E53935"))
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
    }
    private fun summaryColumnWidth() = when {
        resources.configuration.screenWidthDp < 360 -> 50
        resources.configuration.screenWidthDp < 390 -> 58
        else -> 63
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(17), dp(17), dp(17))
        background = rounded(p.surface, p.border, 22)
    }
    private fun section(text: String) = label(text, 12f, p.accent, Typeface.BOLD).apply { letterSpacing = .12f }
    private fun label(value: String, size: Float, color: Int, weight: Int = Typeface.NORMAL) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setTypeface(null, weight)
    }
    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun spaced(top: Int = dp(16)) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun hex(value: String) = Color.parseColor(value)
}
