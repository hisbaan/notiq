package com.hisbaan.notiq.fill

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The "Fill 482913 ✕" chip. An accessibility overlay window, which an accessibility service may
 * draw without the overlay permission. Sits just above the keyboard, or below the field when
 * there's no keyboard. Plain Views: Compose in a service window needs lifecycle plumbing this
 * doesn't warrant.
 */
class FillChip(
    private val service: AccessibilityService,
    private val onFill: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var label: TextView? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Never takes focus: the field keeps it, and the keyboard stays up.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        // Screen coordinates, whatever the insets.
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    val showing: Boolean get() = view != null

    /** [field] and [imeTop] are in screen coordinates; [imeTop] is null without a keyboard. */
    fun show(code: String, field: Rect, imeTop: Int?) {
        val chip = view ?: create().also {
            view = it
            windowManager.addView(it, params)
        }
        label?.text = "Fill $code"
        chip.contentDescription = "Fill one-time code $code"
        chip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val height = chip.measuredHeight
        val gap = dp(8)
        val screenHeight = windowManager.currentWindowMetrics.bounds.height()
        params.y = (if (imeTop != null) imeTop - height - gap else field.bottom + gap)
            .coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        windowManager.updateViewLayout(chip, params)
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        label = null
    }

    private fun create(): View {
        val night = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        // Material 3 primary container, from the system's dynamic palette.
        val background = service.getColor(if (night) android.R.color.system_accent1_700 else android.R.color.system_accent1_100)
        val foreground = service.getColor(if (night) android.R.color.system_accent1_100 else android.R.color.system_accent1_900)

        val fill = TextView(service).apply {
            setTextColor(foreground)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(20), dp(12), dp(12), dp(12))
            setOnClickListener { onFill() }
        }
        val close = TextView(service).apply {
            text = "✕"
            contentDescription = "Dismiss"
            setTextColor(foreground)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(12), dp(12), dp(16), dp(12))
            setOnClickListener { onDismiss() }
        }
        label = fill
        return LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            this.background = GradientDrawable().apply {
                setColor(background)
                cornerRadius = dp(24).toFloat()
            }
            addView(fill)
            addView(close)
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()
}
