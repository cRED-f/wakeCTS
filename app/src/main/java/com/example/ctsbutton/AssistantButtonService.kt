package com.example.ctsbutton

import android.accessibilityservice.AccessibilityService
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class AssistantButtonService : AccessibilityService() {

    private val windowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private val handler = Handler(Looper.getMainLooper())

    private var floatingButton: ImageButton? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var oceanView: FrameLayout? = null

    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragDistance = 0f
    private var isDragging = false
    private var isLongPressed = false

    private val longPressRunnable = Runnable {
        if (floatingButton != null && !isDragging) {
            isLongPressed = true
            showOceanDropZone()
        }
    }

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_UPDATE_SETTINGS -> {
                    updateButtonSide(AppPreferences.isButtonLeft)
                }

                ACTION_TOGGLE_BUTTON -> {
                    if (AppPreferences.isWakeEnabled) {
                        showFloatingButton()
                    } else {
                        hideFloatingButton()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val filter = IntentFilter().apply {
            addAction(ACTION_UPDATE_SETTINGS)
            addAction(ACTION_TOGGLE_BUTTON)
        }

        ContextCompat.registerReceiver(
            this,
            settingsReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (AppPreferences.isWakeEnabled) {
            showFloatingButton()
        }
    }

    private fun showFloatingButton() {
        if (!AppPreferences.isWakeEnabled || floatingButton != null) {
            return
        }

        val button = ImageButton(this).apply {
            setImageResource(R.drawable.ic_search_pencil)
            background = ContextCompat.getDrawable(
                this@AssistantButtonService,
                R.drawable.bg_floating_button
            )
            contentDescription = getString(R.string.cts_button_description)
            alpha = 0.94f
            elevation = dp(6).toFloat()
            isLongClickable = true

            setOnClickListener {
                handleButtonClick()
            }

            setOnTouchListener { view, event -> handleDrag(view, event)
            }
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
            try {
                startActivity(
                    Intent(Intent.ACTION_ASSIST)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (error: ActivityNotFoundException) {
                Log.w("wakeCTS", "No Assistant is available", error)
            } catch (error: SecurityException) {
                Log.e("wakeCTS", "Launching Assistant was blocked", error)
            }
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
                dragDistance = 0f
                isDragging = false
                isLongPressed = false

                handler.postDelayed(longPressRunnable, 400L)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                dragDistance = deltaX * deltaX + deltaY * deltaY

                if (dragDistance < 144f) {
                    return false
                }

                handler.removeCallbacks(longPressRunnable)

                if (!isDragging) {
                    isDragging = true
                    showOceanDropZone()
                }

                params.x = (initialX - deltaX).toInt()
                params.y = (initialY + deltaY).toInt()

                windowManager.updateViewLayout(view, params)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)

                val screenHeight = resources.displayMetrics.heightPixels
                val buttonBottom = params.y + dp(56)
                val dropZoneTop = screenHeight - dp(190)

                val droppedInOcean = buttonBottom >= dropZoneTop

                if (droppedInOcean) {
                    playOceanEatAnimation(view, params)
                } else if (dragDistance < 144f) {
                    handleButtonClick()
                } else {
                    playWaterBounce(view, params)
                }

                if (!droppedInOcean) {
                    hideOceanDropZone()
                }

                dragDistance = 0f
                isDragging = false
                isLongPressed = false
                return true
            }
        }

        return false
    }

    private fun showOceanDropZone() {
        if (oceanView != null) {
            return
        }

        val ocean = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(
                this@AssistantButtonService,
                R.drawable.bg_ocean_drop_zone
            )
            alpha = 0f
        }

        val wave = View(this).apply {
            background = ContextCompat.getDrawable(
                this@AssistantButtonService,
                R.drawable.bg_ocean_wave
            )
        }

        val waveParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(90)
        ).apply {
            gravity = Gravity.BOTTOM
        }

        ocean.addView(wave, waveParams)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(190),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        windowManager.addView(ocean, params)

        ocean.animate()
            .alpha(1f)
            .setDuration(240)
            .start()

        ObjectAnimator.ofFloat(wave, View.TRANSLATION_X, -dp(80).toFloat(), dp(80).toFloat()).apply {
            duration = 1800
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        oceanView = ocean
    }

    private fun hideOceanDropZone() {
        oceanView?.let { ocean ->
            ocean.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    runCatching {
                        windowManager.removeView(ocean)
                    }
                }
                .start()
        }

        oceanView = null
    }

    private fun playOceanEatAnimation(view: View, params: WindowManager.LayoutParams) {
        AppPreferences.setWakeEnabled(this, false)

        val ocean = oceanView

        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.15f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.15f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
        val moveDown = ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_Y,
            0f,
            dp(80).toFloat()
        )

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha, moveDown)
            duration = 430
            interpolator = DecelerateInterpolator()
            start()
        }

        ocean?.animate()
            ?.alpha(1f)
            ?.setDuration(180)
            ?.withEndAction {
                ocean.animate()
                    .alpha(0f)
                    .setDuration(320)
                    .withEndAction {
                        runCatching {
                            windowManager.removeView(ocean)
                        }

                        oceanView = null
                    }
                    .start()
            }
            ?.start()

        handler.postDelayed({
            hideFloatingButton()
        }, 430L)
    }

    private fun playWaterBounce(view: View, params: WindowManager.LayoutParams) {
        val originalY = params.y
        val bounceY = originalY + dp(10)

        params.y = bounceY
        windowManager.updateViewLayout(view, params)

        val valueHolder = FloatValueHolder(bounceY.toFloat())

        val springAnimation = SpringAnimation(valueHolder)

        springAnimation.spring = SpringForce().apply {
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            stiffness = SpringForce.STIFFNESS_LOW
        }

        springAnimation.addUpdateListener { _, value, _ ->
            params.y = value.toInt()

            floatingButton?.let { button ->
                windowManager.updateViewLayout(button, params)
            }
        }

        springAnimation.animateToFinalPosition(originalY.toFloat())

        view.animate()
            .scaleX(0.90f)
            .scaleY(1.08f)
            .setDuration(90)
            .withEndAction {
                view.animate()
                    .scaleX(1.04f)
                    .scaleY(0.96f)
                    .setDuration(110)
                    .withEndAction {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(130)
                            .start()
                    }
                    .start()
            }
            .start()
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

    private fun hideFloatingButton() {
        floatingButton?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }

        floatingButton = null
        layoutParams = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(longPressRunnable)

        runCatching {
            unregisterReceiver(settingsReceiver)
        }

        oceanView?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }

        floatingButton?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }

        oceanView = null
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

        const val ACTION_TOGGLE_BUTTON =
            "com.example.ctsbutton.TOGGLE_BUTTON"
    }
}