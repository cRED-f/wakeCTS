package com.example.wakects

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class AssistantButtonService : AccessibilityService() {

    private val windowManager: WindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var floatingButton: ImageButton? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val touchSlop: Int by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingButton()
    }

    private fun showFloatingButton() {
        if (floatingButton != null) return

        val button = ImageButton(this).apply {
            setImageResource(R.drawable.ic_assistant)
            background = ContextCompat.getDrawable(
                this@AssistantButtonService,
                R.drawable.circle_button_background
            )
            contentDescription = getString(R.string.assistant_button_description)
            alpha = 0.85f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            setOnClickListener { wakeAssistant() }
            setOnTouchListener { view, event -> handleTouch(view, event) }
        }

        val params = WindowManager.LayoutParams(
            dp(54),
            dp(54),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(14)
            y = 0
        }

        windowManager.addView(button, params)
        floatingButton = button
        layoutParams = params
    }

    private fun wakeAssistant() {
        if (!performGlobalAction(GLOBAL_ACTION_ASSIST)) {
            Toast.makeText(this, R.string.assistant_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                    isDragging = true
                }
                if (isDragging) {
                    // X is measured from the right, because gravity uses END.
                    val maxX = (resources.displayMetrics.widthPixels - params.width)
                        .coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - params.height)
                        .coerceAtLeast(0) / 2
                    params.x = (initialX - dx).roundToInt().coerceIn(0, maxX)
                    params.y = (initialY + dy).roundToInt().coerceIn(-maxY, maxY)
                    windowManager.updateViewLayout(view, params)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) view.performClick()
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The service does not read screen content or inspect events.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        floatingButton?.let { windowManager.removeView(it) }
        floatingButton = null
        layoutParams = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
