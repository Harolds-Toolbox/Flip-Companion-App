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
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.util.TypedValue

class MainActivity : Activity() {
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
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(28), dp(22), dp(28)) }
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

        content.addView(section("Answer / End Calls by Opening / Closing Flip"), spaced(dp(32)))
        CallService.entries.forEachIndexed { index, service ->
            content.addView(serviceCard(service), spaced(if (index == 0) dp(12) else dp(10)))
        }

    }
    private fun serviceCard(service: CallService): LinearLayout {
        val callsCard = card().apply {
            isClickable = true
            isFocusable = true
            contentDescription = "${service.title} call settings${if (service.available) "" else ", coming soon"}"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CallSettingsActivity::class.java)
                    .putExtra(CallService.EXTRA_SERVICE, service.id))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
        val callsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        callsRow.addView(ImageView(this).apply {
            setImageResource(service.logo)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(hex(service.logoColor))
            }
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(14) })
        callsRow.addView(label(service.title, 17f, p.text, Typeface.BOLD),
            LinearLayout.LayoutParams(0, -2, 1f))
        callsRow.addView(ImageView(this).apply {
            setImageResource(R.drawable.pabi_chevron)
            imageTintList = ColorStateList.valueOf(p.muted)
            alpha = .45f
        }, LinearLayout.LayoutParams(dp(14), dp(20)).apply { marginStart = dp(12) })
        callsCard.addView(callsRow)
        return callsCard
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(17), dp(17), dp(17))
        background = rounded(p.surface, p.border, 22); elevation = dp(2).toFloat()
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
