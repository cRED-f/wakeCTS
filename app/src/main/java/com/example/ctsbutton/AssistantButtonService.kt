package com.example.ctsbutton

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import androidx.core.content.ContextCompat

class AssistantButtonService : AccessibilityService() {

    private val windowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private var floatingButton: ImageButton? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_UPDATE_SETTINGS) {
                updateButtonSide(AppPreferences.isButtonLeft)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        ContextCompat.registerReceiver(
            this,
            settingsReceiver,
            IntentFilter(ACTION_UPDATE_SETTINGS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        showFloatingButton()
    }

    private fun showFloatingButton() {
        if (floatingButton != null) {
            return
        }

        val button = ImageButton(this).apply {
            setImageResource(R.drawable.ic_cts)
            background = ContextCompat.getDrawable(
                this@AssistantButtonService,
                R.drawable.bg_floating_button
            )
            contentDescription = getString(R.string.cts_button_description)
            alpha = 0.92f
            elevation = dp(6).toFloat()

            setOnClickListener {
                handleButtonClick()
            }

            setOnTouchListener { view, event -> handleDrag(view, event)}
        }

        val params = WindowManager.LayoutParams(
            dp(56),
            dp(56),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (AppPreferences.isButtonLeft) {
                Gravity.START or Gravity.CENTER_VERTICAL
            } else {
                Gravity.END or Gravity.CENTER_VERTICAL
            }

            x = dp(16)
            y = 0
        }

        windowManager.addView(button, params)

        floatingButton = button
        layoutParams = params
    }

    private fun handleButtonClick() {
        if (AppPreferences.isHapticEnabled) {
            floatingButton?.performHapticFeedback(
                HapticFeedbackConstants.CONTEXT_CLICK
            )
        }

        val ctsTriggered = CtsTrigger.triggerCircleToSearch()

        if (!ctsTriggered && AppPreferences.isFallbackEnabled) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_ASSIST)
        }
    }

    private fun handleDrag(view: View, event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x.toFloat()
                initialY = params.y.toFloat()
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                val distance = deltaX * deltaX + deltaY * deltaY

                if (distance < 144f) {
                    return false
                }

                params.x = (initialX - deltaX).toInt()
                params.y = (initialY + deltaY).toInt()

                windowManager.updateViewLayout(view, params)
                return true
            }
        }

        return false
    }

    private fun updateButtonSide(onLeft: Boolean) {
        val params = layoutParams ?: return

        params.gravity = if (onLeft) {
            Gravity.START or Gravity.CENTER_VERTICAL
        } else {
            Gravity.END or Gravity.CENTER_VERTICAL
        }

        floatingButton?.let {
            windowManager.updateViewLayout(it, params)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching {
            unregisterReceiver(settingsReceiver)
        }

        floatingButton?.let {
            windowManager.removeView(it)
        }

        floatingButton = null
        layoutParams = null

        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val ACTION_UPDATE_SETTINGS =
            "com.example.ctsbutton.UPDATE_SETTINGS"
    }
}
